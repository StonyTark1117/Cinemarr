#!/usr/bin/env python3
"""Regression checks for cross-host release JAR canonicalization."""

from __future__ import annotations

import hashlib
import importlib.util
import tempfile
import warnings
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("canonicalize-release-jar.py")
SPEC = importlib.util.spec_from_file_location("canonicalize_release_jar", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def write_fixture(path: Path, reverse: bool, discovered: bool) -> None:
    names = [
        "z-last.txt",
        "META-INF/versions/9/module-info.class",
        "META-INF/versions/9/compat.class",
        "stonytark/cinemarr/Cinemarr.class",
        "META-INF/MANIFEST.MF",
        "META-INF/",
        "a-first.txt",
    ]
    if reverse:
        names.reverse()
    manifest = (
        "Manifest-Version: 1.0\n"
        "Fabric-Mapping-Namespace: intermediary\n"
        f"Fabric-Loader-Version: {'0.19.3' if discovered else 'unknown'}\n"
        f"Fabric-Mixin-Version: {'0.17.3+mixin.0.8.7' if discovered else 'unknown'}\n"
        f"Fabric-Mixin-Group: {'net.fabricmc' if discovered else 'unknown'}\n\n"
    ).encode()
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            if name == "META-INF/":
                archive.writestr(name, b"")
            elif name == "META-INF/MANIFEST.MF":
                archive.writestr(name, manifest)
            else:
                archive.writestr(name, name.encode())


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def assert_rejected(path: Path, expected: str) -> None:
    try:
        MODULE.canonicalize(path)
    except SystemExit as failure:
        assert expected in str(failure), failure
    else:
        raise AssertionError(f"canonicalizer accepted forbidden fixture: {path}")


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="cinemarr-canonicalizer-") as directory:
        root = Path(directory)
        first = root / "first.jar"
        second = root / "second.jar"
        write_fixture(first, reverse=False, discovered=False)
        write_fixture(second, reverse=True, discovered=True)
        MODULE.canonicalize(first)
        MODULE.canonicalize(second)
        assert digest(first) == digest(second)
        initial = digest(first)
        MODULE.canonicalize(first)
        assert digest(first) == initial
        with zipfile.ZipFile(first) as archive:
            assert all(entry.compress_type == zipfile.ZIP_STORED for entry in archive.infolist())
            assert all(entry.date_time == MODULE.ZIP_EPOCH for entry in archive.infolist())
            assert all(entry.create_system == 3 for entry in archive.infolist())
            assert all((entry.external_attr >> 16) & 0o777 == (0o755 if entry.is_dir() else 0o644)
                       for entry in archive.infolist())
            manifest = archive.read("META-INF/MANIFEST.MF").decode()
            assert "Fabric-Loader-Version" not in manifest
            assert "Fabric-Mapping-Namespace: intermediary" in manifest
            names = archive.namelist()
            assert "stonytark/cinemarr/Cinemarr.class" in names
            assert "META-INF/versions/9/module-info.class" not in names
            assert "META-INF/versions/9/compat.class" in names

        signed = root / "signed.jar"
        with zipfile.ZipFile(signed, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n\n")
            archive.writestr("META-INF/CINEMARR.SF", b"signature metadata")
        assert_rejected(signed, "signed release JAR")

        duplicate = root / "duplicate.jar"
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(duplicate, "w") as archive:
                archive.writestr("duplicate.txt", b"first")
                archive.writestr("duplicate.txt", b"second")
        assert_rejected(duplicate, "duplicate entries")
    print("release JAR canonicalization tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
