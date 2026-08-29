#!/usr/bin/env python3
"""Manifest-backed lifecycle guard for disposable Cinemarr Proxmox resources.

The tool deliberately does not create guests.  Creation commands may record a
resource only after applying the exact manifest tag.  Cleanup then refuses to
touch anything whose live Proxmox configuration does not carry that tag.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import secrets
import subprocess
import sys
from pathlib import Path


TAG = re.compile(r"cinemarr-hwtest-[a-z0-9][a-z0-9-]{7,63}$")
HOST = re.compile(r"[A-Za-z0-9.-]+$")
VOLUME = re.compile(r"[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+$")
PCI_BINDING = re.compile(r"(0000:[0-9a-f]{2}:[0-9a-f]{2}\.[0-7])@([A-Za-z0-9_-]+)$")
SYSTEMD_UNIT = re.compile(r"[A-Za-z0-9_.@-]+\.service$")
SAFE_FILE_ROOTS = ("/var/lib/vz/template/iso/", "/tmp/")


def now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def read_manifest(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("schema") != 1 or not TAG.fullmatch(value.get("tag", "")):
        raise ValueError("invalid Cinemarr hardware-test manifest")
    if not isinstance(value.get("resources"), list):
        raise ValueError("manifest resources must be a list")
    return value


def write_manifest(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def ssh_prefix(password_file: Path, host: str) -> list[str]:
    if not password_file.is_file():
        raise ValueError("SSH password file does not exist")
    if not HOST.fullmatch(host):
        raise ValueError("unsafe host name")
    return [
        "sshpass", "-f", str(password_file), "ssh", "-F", "/dev/null",
        "-o", "BatchMode=no", "-o", "StrictHostKeyChecking=yes",
        "-o", "ConnectTimeout=10", f"root@{host}",
    ]


def remote_script(password_file: Path, host: str, script: str, *args: str,
                  check: bool = True) -> subprocess.CompletedProcess[str]:
    for value in args:
        if "\n" in value or "\r" in value or "\x00" in value:
            raise ValueError("unsafe remote argument")
    command = ssh_prefix(password_file, host) + ["bash", "-s", "--", *args]
    return subprocess.run(command, input=script, text=True, capture_output=True, check=check)


def validate_resource(kind: str, identifier: str) -> None:
    if kind in {"qemu", "lxc"}:
        number = int(identifier)
        if number < 100 or number > 999_999_999:
            raise ValueError("guest ID is outside the permitted Proxmox range")
    elif kind == "volume":
        if not VOLUME.fullmatch(identifier) or ".." in identifier:
            raise ValueError("unsafe Proxmox volume ID")
    elif kind == "pci":
        if not PCI_BINDING.fullmatch(identifier):
            raise ValueError("unsafe PCI binding record")
    elif kind == "unit":
        if not SYSTEMD_UNIT.fullmatch(identifier):
            raise ValueError("unsafe systemd unit name")
    elif kind in {"file", "directory"}:
        if not identifier.startswith(SAFE_FILE_ROOTS) or ".." in Path(identifier).parts:
            raise ValueError("temporary file is outside the cleanup allowlist")
    else:
        raise ValueError("unsupported resource kind")


def live_guest_config(password_file: Path, resource: dict) -> str:
    tool = "qm" if resource["kind"] == "qemu" else "pct"
    result = remote_script(password_file, resource["host"], """
set -euo pipefail
tool=$1
guest=$2
if ! command -v "$tool" >/dev/null; then exit 4; fi
"$tool" config "$guest"
""", tool, resource["id"], check=False)
    if result.returncode != 0:
        return ""
    return result.stdout


def has_exact_tag(config: str, tag: str) -> bool:
    for line in config.splitlines():
        key, separator, value = line.partition(":")
        if not separator:
            continue
        if key.strip() == "tags" and tag in {item.strip() for item in value.split(";")}:
            return True
        if key.strip() == "description" and f"cinemarr-hwtest-tag={tag}" in value:
            return True
    return False


def cleanup_resource(password_file: Path, manifest_tag: str, resource: dict) -> str:
    kind, identifier, host = resource["kind"], resource["id"], resource["host"]
    validate_resource(kind, identifier)
    if resource.get("tag") != manifest_tag:
        raise RuntimeError(f"refusing {kind} {identifier}: recorded tag does not match manifest")
    if kind in {"qemu", "lxc"}:
        config = live_guest_config(password_file, resource)
        if not config:
            return "already-absent"
        if not has_exact_tag(config, manifest_tag):
            raise RuntimeError(f"refusing {kind} {identifier}: exact live tag is absent")
        tool = "qm" if kind == "qemu" else "pct"
        result = remote_script(password_file, host, """
set -euo pipefail
tool=$1
guest=$2
if "$tool" status "$guest" | grep -q 'status: running'; then
  "$tool" shutdown "$guest" --timeout 60 || "$tool" stop "$guest"
fi
"$tool" destroy "$guest" --purge 1
""", tool, identifier, check=False)
        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or f"failed to destroy {kind} {identifier}")
        return "destroyed"
    if kind == "volume":
        # Volumes are recorded separately so orphan cleanup never relies on a wildcard.
        result = remote_script(password_file, host, """
set -euo pipefail
volume=$1
if pvesm path "$volume" >/dev/null 2>&1; then pvesm free "$volume"; else exit 3; fi
""", identifier, check=False)
        if result.returncode == 3:
            return "already-absent"
        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or f"failed to free volume {identifier}")
        return "freed"
    if kind == "pci":
        match = PCI_BINDING.fullmatch(identifier)
        assert match is not None
        address, original = match.groups()
        result = remote_script(password_file, host, """
set -euo pipefail
address=$1
original=$2
device=/sys/bus/pci/devices/$address
test -d "$device"
current=""
if test -L "$device/driver"; then current=$(basename "$(readlink -f "$device/driver")"); fi
if test "$current" = "$original"; then exit 3; fi
if test -n "$current"; then echo "$address" > "$device/driver/unbind"; fi
: > "$device/driver_override"
modprobe "$original"
echo "$address" > /sys/bus/pci/drivers_probe
test "$(basename "$(readlink -f "$device/driver")")" = "$original"
""", address, original, check=False)
        if result.returncode == 3:
            return "already-original"
        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or f"failed to restore PCI binding {identifier}")
        return "rebound"
    if kind == "unit":
        if not identifier.startswith(manifest_tag):
            raise RuntimeError(f"refusing unit {identifier}: name lacks exact manifest tag")
        result = remote_script(password_file, host, """
set -euo pipefail
unit=$1
if ! systemctl status "$unit" >/dev/null 2>&1 && ! systemctl is-failed "$unit" >/dev/null 2>&1; then exit 3; fi
systemctl stop "$unit" || true
systemctl reset-failed "$unit" || true
""", identifier, check=False)
        if result.returncode == 3:
            return "already-absent"
        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or f"failed to stop unit {identifier}")
        return "stopped"
    if kind in {"file", "directory"}:
        if not Path(identifier).name.startswith(manifest_tag):
            raise RuntimeError(f"refusing {kind} {identifier}: basename lacks exact manifest tag")
        result = remote_script(password_file, host, """
set -euo pipefail
target=$1
kind=$2
if ! test -e "$target"; then exit 3; fi
if test "$kind" = directory; then
  test -d "$target"
  rm -r -- "$target"
else
  test -f "$target"
  rm -- "$target"
fi
""", identifier, kind, check=False)
        if result.returncode == 3:
            return "already-absent"
        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or f"failed to remove file {identifier}")
        return "removed"
    raise AssertionError(kind)


def command_init(args: argparse.Namespace) -> int:
    if args.manifest.exists():
        raise ValueError("manifest already exists")
    suffix = args.suffix or secrets.token_hex(6)
    tag = f"cinemarr-hwtest-{suffix.lower()}"
    if not TAG.fullmatch(tag):
        raise ValueError("suffix does not produce a valid cinemarr-hwtest tag")
    value = {"schema": 1, "tag": tag, "createdAt": now(), "state": "active", "resources": []}
    write_manifest(args.manifest, value)
    print(tag)
    return 0


def command_record(args: argparse.Namespace) -> int:
    value = read_manifest(args.manifest)
    if value.get("state") not in {"active", "clean"}:
        raise ValueError("cannot add resources while manifest cleanup has failures")
    validate_resource(args.kind, args.id)
    if not HOST.fullmatch(args.host):
        raise ValueError("unsafe host name")
    if args.kind in {"file", "directory", "unit"} and not Path(args.id).name.startswith(value["tag"]):
        raise ValueError("temporary path basename must begin with the exact manifest tag")
    key = (args.host, args.kind, args.id)
    if any((item["host"], item["kind"], item["id"]) == key for item in value["resources"]):
        raise ValueError("resource is already recorded")
    value["resources"].append({
        "host": args.host, "kind": args.kind, "id": args.id,
        "tag": value["tag"], "recordedAt": now(), "cleanup": "pending",
    })
    value["state"] = "active"
    write_manifest(args.manifest, value)
    return 0


def command_audit(args: argparse.Namespace) -> int:
    value = read_manifest(args.manifest)
    report = []
    for resource in value["resources"]:
        validate_resource(resource["kind"], resource["id"])
        if resource["kind"] in {"qemu", "lxc"}:
            config = live_guest_config(args.password_file, resource)
            status = "absent" if not config else ("tagged" if has_exact_tag(config, value["tag"]) else "untagged")
        else:
            status = "recorded"
        report.append({"host": resource["host"], "kind": resource["kind"], "id": resource["id"], "status": status})
    print(json.dumps({"tag": value["tag"], "state": value["state"], "resources": report}, indent=2))
    return 1 if any(item["status"] == "untagged" for item in report) else 0


def command_cleanup(args: argparse.Namespace) -> int:
    value = read_manifest(args.manifest)
    failures = []
    # Stop transient helpers first, then destroy guests before removing any
    # attached media or disks. Restore pass-through devices only after every
    # guest which could own them is gone.
    priority = {"unit": 0, "qemu": 1, "lxc": 1, "volume": 2, "file": 3, "directory": 3, "pci": 4}
    resources = sorted(enumerate(value["resources"]),
                       key=lambda item: (priority[item[1]["kind"]], -item[0]))
    for _, resource in resources:
        if resource.get("cleanup") in {"destroyed", "freed", "removed", "already-absent"}:
            continue
        try:
            resource["cleanup"] = cleanup_resource(args.password_file, value["tag"], resource)
            resource["cleanedAt"] = now()
        except Exception as failure:  # Preserve progress for recovery mode.
            resource["cleanup"] = "failed"
            resource["cleanupError"] = str(failure)
            failures.append(str(failure))
        write_manifest(args.manifest, value)
    value["state"] = "cleanup-failed" if failures else "clean"
    value["updatedAt"] = now()
    write_manifest(args.manifest, value)
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    sub = result.add_subparsers(dest="command", required=True)
    initialize = sub.add_parser("init")
    initialize.add_argument("--manifest", type=Path, required=True)
    initialize.add_argument("--suffix")
    initialize.set_defaults(action=command_init)
    record = sub.add_parser("record")
    record.add_argument("--manifest", type=Path, required=True)
    record.add_argument("--host", required=True)
    record.add_argument("--kind", choices=("qemu", "lxc", "volume", "file", "directory", "pci", "unit"), required=True)
    record.add_argument("--id", required=True)
    record.set_defaults(action=command_record)
    for name, action in (("audit", command_audit), ("cleanup-only", command_cleanup)):
        command = sub.add_parser(name)
        command.add_argument("--manifest", type=Path, required=True)
        command.add_argument("--password-file", type=Path, required=True)
        command.set_defaults(action=action)
    return result


def main() -> int:
    try:
        args = parser().parse_args()
        return args.action(args)
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as failure:
        print(f"proxmox-hwtest: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
