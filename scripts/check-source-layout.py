#!/usr/bin/env python3
"""Reject copied platform sources that should live in a family-shared source set."""

from __future__ import annotations

import hashlib
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VERSION_COMMON = (
    ROOT / "platforms/mc1.20.1/common/src/main/java",
    ROOT / "platforms/mc1.20.2/common/src/main/java",
    ROOT / "platforms/mc26.1.2/common/src/main/java",
    ROOT / "platforms/mc26.2/common/src/main/java",
)
FAMILY_REFERENCES = {
    "mc1.20": ROOT / "platforms/mc1.20/common/src/main/java",
    "mc26": ROOT / "platforms/mc26/common/src/main/java",
}
ACCEPTANCE_VIDEO_SCREENS = (
    ROOT / "src/main/java/stonytark/cinemarr/client/CinemarrVideoScreen.java",
    ROOT / "platforms/mc1.20.1/common/src/main/java/stonytark/cinemarr/client/CinemarrVideoScreen.java",
    ROOT / "platforms/mc26/common/src/main/java/stonytark/cinemarr/client/CinemarrVideoScreen.java",
    ROOT / "platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/client/LegacyVideoScreen.java",
)


def main() -> None:
    hashes: dict[str, list[Path]] = defaultdict(list)
    for directory in VERSION_COMMON:
        if not directory.is_dir():
            continue
        for source in directory.rglob("*.java"):
            hashes[hashlib.sha256(source.read_bytes()).hexdigest()].append(source)
    duplicates = [paths for paths in hashes.values() if len(paths) > 1]
    if duplicates:
        rendered = "; ".join(", ".join(str(path.relative_to(ROOT)) for path in paths)
                             for paths in duplicates)
        raise SystemExit(f"identical version-specific Java sources must be family-shared: {rendered}")

    for family, directory in FAMILY_REFERENCES.items():
        if not any(directory.rglob("*.java")):
            raise SystemExit(f"family source set is empty: {directory.relative_to(ROOT)}")
        expected = f"../../{family}/common/src/main"
        versions = ("mc1.20.1", "mc1.20.2") if family == "mc1.20" else ("mc26.1.2", "mc26.2")
        for version in versions:
            for loader in ("fabric", "forge", "neoforge"):
                build = ROOT / f"platforms/{version}/{loader}/build.gradle"
                if expected not in build.read_text("utf-8"):
                    raise SystemExit(f"{build.relative_to(ROOT)} does not include {family} shared sources")

    for source in ACCEPTANCE_VIDEO_SCREENS:
        text = source.read_text("utf-8")
        if "Acceptance video UI:" not in text or "clipped=" not in text or "canControl=" not in text:
            raise SystemExit(f"{source.relative_to(ROOT)} lacks release UI acceptance instrumentation")
    print("family-shared source layout verification passed")


if __name__ == "__main__":
    main()
