#!/usr/bin/env bash
set -euo pipefail

# Provisions (once) and reuses a headless Windows 11 x86-64 QEMU guest on the
# Proxmox host. Each run attaches fresh read-only payload media and a fresh
# evidence disk. The installed guest is retained, but its transient systemd
# unit is always stopped before this script returns.

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
host=${CINEMARR_HWTEST_HOST:-}
password_file=${CINEMARR_HWTEST_PASSWORD_FILE:-}
windows_iso=${CINEMARR_WINDOWS_ISO:-}
state_dir=${CINEMARR_WINDOWS_VM_DIR:-/var/lib/cinemarr-hwtest/windows-x86_64}
bundle="$repo_root/build/decoder-benchmark/bundle"
jre_api='https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse'

for tool in sshpass ssh scp curl jq sha256sum genisoimage python3 tr; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }
done
[[ -f "$windows_iso" && -f "$password_file" && -d "$bundle/classes" \
   && -f "$bundle/lib/ffmpeg-8.1.2-1.5.14-windows-x86_64.jar" ]] || exit 2
[[ "$host" =~ ^[A-Za-z0-9.-]+$ ]] || exit 2
[[ "$state_dir" =~ ^/var/lib/cinemarr-hwtest/[A-Za-z0-9._/-]+$ && "$state_dir" != *'..'* ]] || exit 2

stamp=$(date -u +%Y%m%dT%H%M%SZ)
evidence_dir="$repo_root/build/native-smoke/windows-x86_64/$stamp"
runtime_dir=$(mktemp -d "$repo_root/build/windows-x64-smoke.XXXXXX")
payload_dir="$runtime_dir/payload"
manifest="$evidence_dir/proxmox-manifest.json"
run_dir="$state_dir/runs/$stamp"
mkdir -p "$evidence_dir" "$payload_dir/bundle/classes" "$payload_dir/bundle/lib" "$payload_dir/bundle/fixtures"

ssh_options=(-F /dev/null -o BatchMode=no -o StrictHostKeyChecking=yes -o ConnectTimeout=15)
host_ssh=(sshpass -f "$password_file" ssh "${ssh_options[@]}" "root@$host")
cleanup_complete=false
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  set +e
  if [[ "$cleanup_complete" != true && -f "$manifest" ]]; then
    python3 "$repo_root/scripts/proxmox-hwtest.py" cleanup-only \
      --manifest "$manifest" --password-file "$password_file"
    cleanup_status=$?
    (( cleanup_status == 0 )) || status=$cleanup_status
  fi
  case "$runtime_dir" in "$repo_root"/build/windows-x64-smoke.*) rm -r -- "$runtime_dir" ;; esac
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

tag=$(python3 "$repo_root/scripts/proxmox-hwtest.py" init --manifest "$manifest" \
  --suffix "win64-${stamp,,}")
unit="${tag}-windows.service"
python3 "$repo_root/scripts/proxmox-hwtest.py" record --manifest "$manifest" \
  --host "$host" --kind unit --id "$unit"

provision=$("${host_ssh[@]}" bash -s -- "$state_dir" "$run_dir" <<'REMOTE'
set -euo pipefail
state=$1 run=$2 marker=$1/state.json disk=$1/windows.qcow2 vars=$1/OVMF_VARS_4M.fd
parent=${state%/*}
mkdir -p -m 700 "$parent"
if pgrep -af qemu-system-x86_64 | grep -F -- "$disk" >/dev/null; then
  echo 'retained Windows guest is already running' >&2
  exit 1
fi
if [[ -f "$marker" ]]; then
  python3 - "$marker" <<'PY'
import json, pathlib, sys
value = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert value == {"platform": "windows-x86_64", "schema": 1, "state": "ready"}
PY
  [[ -f "$disk" && -f "$vars" ]]
  result=false
elif [[ ! -e "$state" ]]; then
  mkdir -m 700 "$state"
  printf '{"platform":"windows-x86_64","schema":1,"state":"provisioning"}\n' > "$marker"
  result=true
else
  echo 'retained Windows directory exists without a valid ready marker' >&2
  exit 1
fi
mkdir -p -m 700 "$state/runs" "$run"
printf '%s\n' "$result"
REMOTE
)
[[ "$provision" == true || "$provision" == false ]] || exit 1

jre_metadata=$(curl -fsSL "$jre_api")
jre_url=$(jq -r '.[0].binary.package.link' <<<"$jre_metadata")
jre_sha=$(jq -r '.[0].binary.package.checksum' <<<"$jre_metadata")
[[ "$jre_url" =~ ^https:// && "$jre_sha" =~ ^[0-9a-f]{64}$ ]] || exit 1
curl -fL --retry 4 --retry-all-errors "$jre_url" -o "$payload_dir/jre.zip"
printf '%s  %s\n' "$jre_sha" "$payload_dir/jre.zip" | sha256sum -c -

cp -a "$bundle/classes/." "$payload_dir/bundle/classes/"
cp -a "$bundle/fixtures/." "$payload_dir/bundle/fixtures/"
for library in core-1.0.0.jar javacpp-1.5.14.jar javacpp-1.5.14-windows-x86_64.jar \
    ffmpeg-8.1.2-1.5.14.jar ffmpeg-8.1.2-1.5.14-windows-x86_64.jar; do
  cp "$bundle/lib/$library" "$payload_dir/bundle/lib/"
done
printf '%s\n' "$stamp" > "$payload_dir/run-id.txt"
cp "$repo_root/scripts/windows-native-decoder-smoke.ps1" "$payload_dir/"
if [[ "$provision" == true ]]; then
  cp "$repo_root/scripts/windows-native-smoke-autounattend.xml" "$payload_dir/Autounattend.xml"
fi
genisoimage -quiet -iso-level 3 -J -joliet-long -R -V CINEMARR \
  -o "$runtime_dir/cinemarr-payload.iso" "$payload_dir"

windows_sha=$(sha256sum "$windows_iso" | awk '{print $1}')
payload_sha=$(sha256sum "$runtime_dir/cinemarr-payload.iso" | awk '{print $1}')
{
  printf '%s  %s\n' "$windows_sha" "$(basename "$windows_iso")"
  printf '%s  %s\n' "$payload_sha" 'cinemarr-payload.iso'
  printf '%s  %s\n' "$jre_sha" "$(basename "$jre_url")"
} > "$evidence_dir/input-SHA256SUMS"

sshpass -f "$password_file" scp "${ssh_options[@]}" "$runtime_dir/cinemarr-payload.iso" \
  "root@$host:$run_dir/payload.iso"
if [[ "$provision" == true ]]; then
  sshpass -f "$password_file" scp "${ssh_options[@]}" "$windows_iso" \
    "root@$host:$state_dir/windows.iso"
fi
"${host_ssh[@]}" bash -s -- "$state_dir" "$run_dir" "$unit" "$provision" <<'REMOTE'
set -euo pipefail
state=$1 run=$2 unit=$3 provision=$4
if [[ "$provision" == true ]]; then
  qemu-img create -q -f qcow2 "$state/windows.qcow2" 80G
  cp /usr/share/pve-edk2-firmware/OVMF_VARS_4M.fd "$state/OVMF_VARS_4M.fd"
fi
truncate -s 512M "$run/evidence.img"
printf 'label: dos\n,511M,c,*\n' | sfdisk "$run/evidence.img" >/dev/null
evidence_loop=$(losetup --find --show --partscan "$run/evidence.img")
trap 'losetup -d "$evidence_loop" 2>/dev/null || true' EXIT
mkfs.vfat -F 32 -n CINEVIDENCE "${evidence_loop}p1" >/dev/null
losetup -d "$evidence_loop"
trap - EXIT
args=(qemu-system-x86_64 -name cinemarr-windows-native-smoke -machine q35,accel=kvm
  -enable-kvm -cpu host -smp 8 -m 10240 -display none -vga std
  -drive if=pflash,format=raw,readonly=on,file=/usr/share/pve-edk2-firmware/OVMF_CODE_4M.fd
  -drive if=pflash,format=raw,file="$state/OVMF_VARS_4M.fd"
  -device ich9-ahci,id=ahci
  -drive if=none,id=disk,file="$state/windows.qcow2",format=qcow2,cache=writeback
  -device ide-hd,drive=disk,bus=ahci.0,bootindex=2)
if [[ "$provision" == true ]]; then
  args+=( -drive if=none,id=install,file="$state/windows.iso",format=raw,media=cdrom,readonly=on
    -device ide-cd,drive=install,bus=ahci.1,bootindex=1 )
fi
args+=( -drive if=none,id=payload,file="$run/payload.iso",format=raw,media=cdrom,readonly=on
  -device ide-cd,drive=payload,bus=ahci.2
  -drive if=none,id=evidence,file="$run/evidence.img",format=raw,cache=directsync
  -device ide-hd,drive=evidence,bus=ahci.3
  -netdev user,id=net0 -device e1000e,netdev=net0
  -rtc base=localtime,clock=host -boot menu=off
  -monitor unix:"$run/monitor.sock",server=on,wait=off )
systemd-run --unit="$unit" --collect --property=KillMode=mixed --property=RuntimeMaxSec=5400 "${args[@]}"
REMOTE

if [[ "$provision" == true ]]; then
  # Microsoft boot media prompts for a key only on the first blank-disk boot.
  for delay in 5 7 9; do
    sleep "$delay"
    "${host_ssh[@]}" bash -s -- "$run_dir/monitor.sock" <<'REMOTE'
set -euo pipefail
socket=$1
[[ -S "$socket" ]] && printf 'sendkey ret\n' | socat - "UNIX-CONNECT:$socket" >/dev/null
REMOTE
  done
fi

deadline=$((SECONDS + 3600))
while "${host_ssh[@]}" systemctl is-active --quiet "$unit"; do
  (( SECONDS < deadline )) || { echo 'Windows smoke timed out' >&2; exit 1; }
  sleep 10
done
"${host_ssh[@]}" journalctl -u "$unit" -n 100 --no-pager > "$evidence_dir/vm-final.log" || true
"${host_ssh[@]}" bash -s -- "$run_dir" <<'REMOTE'
set -euo pipefail
run=$1 mountpoint=$1/evidence-mount output=$1/evidence
mkdir -m 700 "$mountpoint" "$output"
evidence_loop=$(losetup --find --show --partscan --read-only "$run/evidence.img")
mount -o ro "${evidence_loop}p1" "$mountpoint"
trap 'umount "$mountpoint" 2>/dev/null || true; rmdir "$mountpoint" 2>/dev/null || true; losetup -d "$evidence_loop" 2>/dev/null || true' EXIT
find "$mountpoint" -maxdepth 1 -type f -exec cp -- {} "$output/" \;
REMOTE
sshpass -f "$password_file" scp -q "${ssh_options[@]}" \
  "root@$host:$run_dir/evidence/*" "$evidence_dir/" 2>/dev/null || true
[[ -f "$evidence_dir/passed.txt" && ! -f "$evidence_dir/failed.txt" ]] \
  || { [[ -f "$evidence_dir/failed.txt" ]] && cat "$evidence_dir/failed.txt" >&2; exit 1; }
[[ $(tr -d '\r\n' < "$evidence_dir/run-id.txt") == "$stamp" ]]

jq -e '.schema == 3 and (.os | startswith("Windows"))
  and (.arch == "amd64" or .arch == "x86_64") and .ffmpegClassifier == "windows-x86_64"
  and .requestedBackend == "software" and .expectedEffectiveBackend == "software"
  and (.rows | length == 3) and ([.rows[] | .accepted and .effectiveBackend == "software"] | all)' \
  "$evidence_dir/decoder-benchmark.json" >/dev/null
normalized_system=$(tr -d '\r' < "$evidence_dir/system.txt")
grep -Eq '^processorArchitecture=AMD64$' <<<"$normalized_system"
grep -Eq '^peMachine=0x8664$' <<<"$normalized_system"
sha256sum "$evidence_dir/decoder-benchmark.json" "$evidence_dir/decoder-benchmark.csv" \
  "$evidence_dir/system.txt" "$evidence_dir/run-id.txt" > "$evidence_dir/output-SHA256SUMS"
python3 "$repo_root/scripts/proxmox-hwtest.py" audit --manifest "$manifest" \
  --password-file "$password_file" > "$evidence_dir/pre-cleanup-audit.json"
python3 "$repo_root/scripts/proxmox-hwtest.py" cleanup-only \
  --manifest "$manifest" --password-file "$password_file"
cleanup_complete=true

"${host_ssh[@]}" bash -s -- "$state_dir" "$run_dir" "$provision" <<'REMOTE' \
  > "$evidence_dir/retained-vm-audit.json"
set -euo pipefail
state=$1 run=$2 provision=$3 marker=$1/state.json disk=$1/windows.qcow2
if [[ "$provision" == true ]]; then
  printf '{"platform":"windows-x86_64","schema":1,"state":"ready"}\n' > "$marker"
  rm -- "$state/windows.iso"
fi
if pgrep -af qemu-system-x86_64 | grep -F -- "$disk" >/dev/null; then
  echo 'retained Windows guest remained running' >&2
  exit 1
fi
python3 - "$state" "$provision" <<'PY'
import json, os, pathlib, sys
state = pathlib.Path(sys.argv[1])
print(json.dumps({
    "platform": "windows-x86_64",
    "provisionedThisRun": sys.argv[2] == "true",
    "stateDirectory": str(state),
    "stateMarker": json.loads((state / "state.json").read_text()),
    "diskBytes": (state / "windows.qcow2").stat().st_size,
    "poweredOn": False,
}, indent=2, sort_keys=True))
PY
rm -r -- "$run"
REMOTE
printf 'Windows x86-64 native decoder smoke passed; retained guest is stopped: %s\n' "$evidence_dir"
