#!/usr/bin/env python3
"""Remove process-local deployment values from retained text evidence.

Sensitive literals are accepted only on stdin as NUL-separated UTF-8 values.
Paths are ordinary files or directory roots supplied as arguments. Rotated gzip
logs are decoded and recompressed. Saved acceptance-client server lists receive
same-byte-length masking to preserve NBT framing. Other binary files are left
untouched, and the values themselves are never printed.
"""

from __future__ import annotations

import pathlib
import sys
import gzip


REPLACEMENT = "[REDACTED_RELEASE_ENDPOINT]"


def iter_files(paths: list[pathlib.Path]):
    for path in paths:
        if path.is_symlink():
            continue
        if path.is_file():
            yield path
        elif path.is_dir():
            for candidate in sorted(path.rglob("*")):
                if candidate.is_file() and not candidate.is_symlink():
                    yield candidate


def main() -> int:
    if len(sys.argv) < 2:
        print(f"Usage: {pathlib.Path(sys.argv[0]).name} PATH...", file=sys.stderr)
        return 2

    raw_values = sys.stdin.buffer.read().split(b"\0")
    values: list[str] = []
    for raw in raw_values:
        if not raw:
            continue
        try:
            value = raw.decode("utf-8")
        except UnicodeDecodeError:
            print("Redaction input was not valid UTF-8", file=sys.stderr)
            return 2
        if value not in values:
            values.append(value)
    values.sort(key=len, reverse=True)
    if not values:
        print("No release evidence values were supplied", file=sys.stderr)
        return 2

    changed_files = 0
    replacements = 0
    for path in iter_files([pathlib.Path(value) for value in sys.argv[1:]]):
        data = path.read_bytes()
        server_list = path.name in ("servers.dat", "servers.dat_old")
        compressed = data.startswith(b"\x1f\x8b") and (path.name.endswith(".log.gz") or server_list)
        if compressed:
            data = gzip.decompress(data)
        if server_list and data.startswith(b"\x0a\x00\x00"):
            updated_data = data
            file_replacements = 0
            for value in values:
                encoded = value.encode("utf-8")
                file_replacements += updated_data.count(encoded)
                updated_data = updated_data.replace(encoded, b"x" * len(encoded))
            if file_replacements:
                path.write_bytes(gzip.compress(updated_data, mtime=0) if compressed else updated_data)
                changed_files += 1
                replacements += file_replacements
            continue
        if b"\0" in data:
            continue
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            continue
        updated = text
        file_replacements = 0
        for value in values:
            occurrences = updated.count(value)
            if occurrences:
                updated = updated.replace(value, REPLACEMENT)
                file_replacements += occurrences
        if file_replacements:
            updated_data = updated.encode("utf-8")
            path.write_bytes(gzip.compress(updated_data, mtime=0) if compressed else updated_data)
            changed_files += 1
            replacements += file_replacements

    print(
        f"Redacted {replacements} literal occurrence(s) across "
        f"{changed_files} evidence file(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
