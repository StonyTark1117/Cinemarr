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
import argparse
import hashlib
import ipaddress
import json
import re


REPLACEMENT = "[REDACTED_RELEASE_ENDPOINT]"
CLIENT_LOGIN = re.compile(r'CinemarrVideo[AB]\[/([\[\]0-9a-fA-F:.]+):([0-9]{1,5})\] logged in\b')


def probe_addresses(paths):
    """Discover only the two gate-owned players' literal login addresses."""
    values = set()
    for path in paths:
        if not (path.name.endswith(('.log', '.log.gz', '.txt'))):
            continue
        data = path.read_bytes()
        if path.name.endswith('.log.gz') and data.startswith(b'\x1f\x8b'):
            data = gzip.decompress(data)
        text = data.decode('utf-8', errors='replace')
        for raw, port in CLIENT_LOGIN.findall(text):
            if not 0 < int(port) <= 65535:
                continue
            address = raw.strip('[]')
            try:
                ipaddress.ip_address(address)
            except ValueError:
                continue
            values.add(address)
    return values


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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft-client-addresses', action='store_true')
    parser.add_argument('--receipt', type=pathlib.Path)
    parser.add_argument('paths', nargs='+', type=pathlib.Path)
    args = parser.parse_args()
    paths = list(dict.fromkeys(iter_files(args.paths)))
    if args.receipt:
        if args.receipt.exists():
            parser.error('Redaction receipt already exists')
        parent = args.receipt.parent.resolve()
        if any(not path.resolve().is_relative_to(parent) for path in paths):
            parser.error('Redaction receipt must contain the evidence paths beneath its directory')

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
    if args.minecraft_client_addresses:
        values.extend(probe_addresses(paths) - set(values))
    values.sort(key=len, reverse=True)
    if not values:
        print("No release evidence values were supplied", file=sys.stderr)
        return 2

    changed_files = 0
    replacements = 0
    changes = []

    def record(path, original, updated, count):
        if args.receipt:
            changes.append(dict(path=str(path.resolve().relative_to(args.receipt.parent.resolve())),
                                beforeSha256=hashlib.sha256(original).hexdigest(),
                                afterSha256=hashlib.sha256(updated).hexdigest(), replacements=count))

    for path in paths:
        data = path.read_bytes()
        original_file = data
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
                updated_file = gzip.compress(updated_data, mtime=0) if compressed else updated_data
                path.write_bytes(updated_file)
                record(path, original_file, updated_file, file_replacements)
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
            updated_file = gzip.compress(updated_data, mtime=0) if compressed else updated_data
            path.write_bytes(updated_file)
            record(path, original_file, updated_file, file_replacements)
            changed_files += 1
            replacements += file_replacements

    if args.receipt:
        args.receipt.write_text(json.dumps(dict(schema=1, changedFiles=changes,
                                              replacements=replacements), indent=2) + '\n')

    print(
        f"Redacted {replacements} literal occurrence(s) across "
        f"{changed_files} evidence file(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
