#!/usr/bin/env python3
"""Reject tracked private-network endpoints from release source and tooling."""

from __future__ import annotations

import ipaddress
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
IPV4 = re.compile(rb"(?<![0-9.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9.])")
PRIVATE_NETWORKS = tuple(
    ipaddress.ip_network(value) for value in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
)


def tracked_files() -> list[Path]:
    output = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [ROOT / value.decode("utf-8") for value in output.split(b"\0") if value]


def main() -> int:
    failures: list[str] = []
    for path in tracked_files():
        # A dirty implementation checkout can contain staged-for-removal paths;
        # the committed tree used by CI will not list them.
        if not path.is_file():
            continue
        data = path.read_bytes()
        if b"\0" in data:
            continue
        for match in IPV4.finditer(data):
            try:
                address = ipaddress.ip_address(match.group().decode("ascii"))
            except ValueError:
                continue
            if any(address in network for network in PRIVATE_NETWORKS):
                line = data.count(b"\n", 0, match.start()) + 1
                failures.append(f"{path.relative_to(ROOT)}:{line}: private endpoint {address}")
    if failures:
        raise SystemExit("Tracked release hygiene failed:\n" + "\n".join(failures))
    print("Tracked release hygiene passed: no private-network IPv4 endpoints")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
