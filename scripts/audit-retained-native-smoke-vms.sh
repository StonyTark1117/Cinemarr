#!/usr/bin/env bash
set -euo pipefail

# Read-only audit for the two retained native-smoke guests. A successful audit
# proves their identity markers and disks exist and neither QEMU process runs.

host=${CINEMARR_HWTEST_HOST:-}
password_file=${CINEMARR_HWTEST_PASSWORD_FILE:-}
windows_dir=${CINEMARR_WINDOWS_VM_DIR:-/var/lib/cinemarr-hwtest/windows-x86_64}
arm64_dir=${CINEMARR_ARM64_VM_DIR:-/var/lib/cinemarr-hwtest/linux-arm64}

for tool in sshpass ssh; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }
done
[[ -f "$password_file" && "$host" =~ ^[A-Za-z0-9.-]+$ ]] || exit 2
for directory in "$windows_dir" "$arm64_dir"; do
  [[ "$directory" =~ ^/var/lib/cinemarr-hwtest/[A-Za-z0-9._/-]+$ && "$directory" != *'..'* ]] || exit 2
done

sshpass -f "$password_file" ssh -F /dev/null -o BatchMode=no \
  -o StrictHostKeyChecking=yes -o ConnectTimeout=10 "root@$host" \
  bash -s -- "$windows_dir" "$arm64_dir" <<'REMOTE'
set -euo pipefail
windows=$1 arm64=$2
python3 - "$windows" "$arm64" <<'PY'
import json
import pathlib
import subprocess
import sys

specs = (
    ("windows-x86_64", pathlib.Path(sys.argv[1]), "windows.qcow2", "qemu-system-x86_64"),
    ("linux-arm64", pathlib.Path(sys.argv[2]), "guest.qcow2", "qemu-system-aarch64"),
)
report = []
for platform, state, disk_name, executable in specs:
    marker_path = state / "state.json"
    disk = state / disk_name
    expected = {"platform": platform, "schema": 1, "state": "ready"}
    marker = json.loads(marker_path.read_text())
    if marker != expected or not disk.is_file():
        raise SystemExit(f"invalid retained guest state: {platform}")
    processes = subprocess.run(
        ["pgrep", "-af", executable], text=True, capture_output=True, check=False
    ).stdout.splitlines()
    powered_on = any(str(disk) in process for process in processes)
    if powered_on:
        raise SystemExit(f"retained guest is running: {platform}")
    report.append({
        "diskBytes": disk.stat().st_size,
        "platform": platform,
        "poweredOn": False,
        "stateDirectory": str(state),
        "stateMarker": marker,
    })
print(json.dumps({"schema": 1, "guests": report}, indent=2, sort_keys=True))
PY
REMOTE
