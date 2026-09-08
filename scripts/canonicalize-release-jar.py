#!/usr/bin/env python3
"""Rewrite a release JAR into a host-independent canonical ZIP form."""

from __future__ import annotations

import argparse
import os
import stat
import tempfile
import zipfile
from pathlib import Path


ZIP_EPOCH = (1980, 2, 1, 0, 0, 0)
VOLATILE_FABRIC_ATTRIBUTES = {
    "fabric-loader-version",
    "fabric-mixin-version",
    "fabric-mixin-group",
}


def canonical_manifest(data: bytes) -> bytes:
    text = data.decode("utf-8")
    lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    output: list[str] = []
    in_main_section = True
    skip_continuation = False

    for line in lines:
        if in_main_section and line == "":
            in_main_section = False
            skip_continuation = False
            output.append(line)
            continue
        if in_main_section and line.startswith(" "):
            if not skip_continuation:
                output.append(line)
            continue

        skip_continuation = False
        if in_main_section and ":" in line:
            attribute = line.split(":", 1)[0].strip().lower()
            if attribute in VOLATILE_FABRIC_ATTRIBUTES:
                skip_continuation = True
                continue
        output.append(line)

    while output and output[-1] == "":
        output.pop()
    return ("\r\n".join(output) + "\r\n\r\n").encode("utf-8")


def entry_sort_key(name: str) -> tuple[int, str]:
    if name == "META-INF/":
        return (0, name)
    if name == "META-INF/MANIFEST.MF":
        return (1, name)
    # Forge 1.7.10's ASM 5 discovery stops scanning a candidate after it has
    # found Cinemarr's @Mod class. If a shaded Java 9 module descriptor appears
    # first, that old scanner rejects the entire JAR as corrupt. Keep the
    # project's Java 8 entrypoint ahead of shaded dependencies and place
    # multi-release metadata last while retaining a deterministic order.
    if name == "stonytark/cinemarr/Cinemarr.class":
        return (2, name)
    if name.startswith("stonytark/cinemarr/"):
        return (3, name)
    if not name.startswith("META-INF/"):
        return (4, name)
    if name.startswith("META-INF/versions/"):
        return (6, name)
    return (5, name)


def is_versioned_module_descriptor(name: str) -> bool:
    parts = name.split("/")
    return (
        len(parts) == 4
        and parts[0:2] == ["META-INF", "versions"]
        and parts[2].isdigit()
        and parts[3] == "module-info.class"
    )


def canonicalize(path: Path) -> None:
    if not path.is_file():
        raise SystemExit(f"Release JAR does not exist: {path}")

    with zipfile.ZipFile(path, "r") as source:
        entries = source.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise SystemExit(f"Release JAR contains duplicate entries: {path}")
        signatures = [
            name
            for name in names
            if name.upper().startswith("META-INF/")
            and name.upper().endswith((".SF", ".RSA", ".DSA", ".EC"))
        ]
        if signatures:
            raise SystemExit(
                f"Refusing to canonicalize signed release JAR {path}: {signatures}"
            )
        payloads = {entry.filename: source.read(entry) for entry in entries}

    # Shaded dependency module descriptors have no effect because Cinemarr's
    # fat JAR is not multi-release. Forge 1.7.10 nevertheless feeds them to
    # ASM 5, rejects their Java 9 bytecode, and reports the entire mod archive
    # as corrupt. Remove only those inert descriptors and their empty directory
    # markers; retain every executable multi-release entry if one is added.
    for name in tuple(payloads):
        if is_versioned_module_descriptor(name):
            del payloads[name]
    for directory in sorted(
        (name for name in payloads if name.endswith("/")), reverse=True
    ):
        if directory.startswith("META-INF/versions/") and not any(
            name != directory and name.startswith(directory) for name in payloads
        ):
            del payloads[directory]
    if "META-INF/versions/" in payloads and not any(
        name != "META-INF/versions/" and name.startswith("META-INF/versions/")
        for name in payloads
    ):
        del payloads["META-INF/versions/"]

    manifest_name = "META-INF/MANIFEST.MF"
    if manifest_name in payloads:
        payloads[manifest_name] = canonical_manifest(payloads[manifest_name])

    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".canonical", dir=path.parent
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        with zipfile.ZipFile(temporary, "w", allowZip64=True) as target:
            target.comment = b""
            for name in sorted(payloads, key=entry_sort_key):
                is_directory = name.endswith("/")
                info = zipfile.ZipInfo(name, ZIP_EPOCH)
                info.compress_type = zipfile.ZIP_STORED
                info.create_system = 3
                mode = stat.S_IFDIR | 0o755 if is_directory else stat.S_IFREG | 0o644
                info.external_attr = mode << 16
                if is_directory:
                    info.external_attr |= 0x10
                target.writestr(info, b"" if is_directory else payloads[name])
        os.replace(temporary, path)
        os.utime(path, (315532800, 315532800))
    finally:
        if temporary.exists():
            temporary.unlink()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", type=Path)
    args = parser.parse_args()
    canonicalize(args.jar.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
