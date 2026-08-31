#!/usr/bin/env bash
set -euo pipefail

# Installs a disposable native Windows 11 x86-64 VM under KVM, runs the
# packaged Windows FFmpeg classifier, retrieves evidence from a disposable FAT
# handoff disk, and removes every tagged remote resource.

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
host=${CINEMARR_HWTEST_HOST:-}
password_file=${CINEMARR_HWTEST_PASSWORD_FILE:-}
windows_iso=${CINEMARR_WINDOWS_ISO:-}
bundle="$repo_root/build/decoder-benchmark/bundle"
jre_api='https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse'

for tool in sshpass ssh scp curl jq sha256sum genisoimage python3 tr; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }
done
[[ -f "$windows_iso" && -f "$password_file" && -d "$bundle/classes" \
   && -f "$bundle/lib/ffmpeg-8.1.2-1.5.14-windows-x86_64.jar" ]] || exit 2
[[ "$host" =~ ^[A-Za-z0-9.-]+$ ]] || exit 2

stamp=$(date -u +%Y%m%dT%H%M%SZ)
evidence_dir="$repo_root/build/native-smoke/windows-x86_64/$stamp"
runtime_dir=$(mktemp -d "$repo_root/build/windows-x64-smoke.XXXXXX")
payload_dir="$runtime_dir/payload"
manifest="$evidence_dir/proxmox-manifest.json"
mkdir -p "$evidence_dir" "$payload_dir/bundle/classes" "$payload_dir/bundle/lib" "$payload_dir/bundle/fixtures"

ssh_options=(-F /dev/null -o BatchMode=no -o StrictHostKeyChecking=yes -o ConnectTimeout=15)
host_ssh=(sshpass -f "$password_file" ssh "${ssh_options[@]}" "root@$host")
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  set +e
  if [[ -f "$manifest" ]]; then
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
remote_dir="/tmp/${tag}-windows"
unit="${tag}-windows.service"
python3 "$repo_root/scripts/proxmox-hwtest.py" record --manifest "$manifest" \
  --host "$host" --kind directory --id "$remote_dir"
python3 "$repo_root/scripts/proxmox-hwtest.py" record --manifest "$manifest" \
  --host "$host" --kind unit --id "$unit"

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
cp "$repo_root/scripts/windows-native-decoder-smoke.ps1" "$payload_dir/"
cp "$repo_root/scripts/windows-native-smoke-autounattend.xml" "$payload_dir/Autounattend.xml"
genisoimage -quiet -iso-level 3 -J -joliet-long -R -V CINEMARR \
  -o "$runtime_dir/cinemarr-payload.iso" "$payload_dir"

windows_sha=$(sha256sum "$windows_iso" | awk '{print $1}')
payload_sha=$(sha256sum "$runtime_dir/cinemarr-payload.iso" | awk '{print $1}')
{
  printf '%s  %s\n' "$windows_sha" "$(basename "$windows_iso")"
  printf '%s  %s\n' "$payload_sha" 'cinemarr-payload.iso'
  printf '%s  %s\n' "$jre_sha" "$(basename "$jre_url")"
} > "$evidence_dir/input-SHA256SUMS"

"${host_ssh[@]}" mkdir -m 700 -- "$remote_dir"
sshpass -f "$password_file" scp "${ssh_options[@]}" "$windows_iso" \
  "root@$host:$remote_dir/windows.iso"
sshpass -f "$password_file" scp "${ssh_options[@]}" "$runtime_dir/cinemarr-payload.iso" \
  "root@$host:$remote_dir/payload.iso"
"${host_ssh[@]}" bash -s -- "$remote_dir" "$unit" <<'REMOTE'
set -euo pipefail
directory=$1 unit=$2
qemu-img create -q -f qcow2 "$directory/windows.qcow2" 80G
truncate -s 512M "$directory/evidence.img"
printf 'label: dos\n,511M,c,*\n' | sfdisk "$directory/evidence.img" >/dev/null
evidence_loop=$(losetup --find --show --partscan "$directory/evidence.img")
trap 'losetup -d "$evidence_loop" 2>/dev/null || true' EXIT
mkfs.vfat -F 32 -n CINEVIDENCE "${evidence_loop}p1" >/dev/null
losetup -d "$evidence_loop"
trap - EXIT
cp /usr/share/pve-edk2-firmware/OVMF_VARS_4M.fd "$directory/OVMF_VARS_4M.fd"
systemd-run --unit="$unit" --collect --property=KillMode=mixed --property=RuntimeMaxSec=5400 \
  qemu-system-x86_64 -name cinemarr-windows-native-smoke -machine q35,accel=kvm \
  -enable-kvm -cpu host -smp 8 -m 10240 -display none -vga std \
  -drive if=pflash,format=raw,readonly=on,file=/usr/share/pve-edk2-firmware/OVMF_CODE_4M.fd \
  -drive if=pflash,format=raw,file="$directory/OVMF_VARS_4M.fd" \
  -device ich9-ahci,id=ahci \
  -drive if=none,id=disk,file="$directory/windows.qcow2",format=qcow2,cache=writeback \
  -device ide-hd,drive=disk,bus=ahci.0,bootindex=2 \
  -drive if=none,id=install,file="$directory/windows.iso",format=raw,media=cdrom,readonly=on \
  -device ide-cd,drive=install,bus=ahci.1,bootindex=1 \
  -drive if=none,id=payload,file="$directory/payload.iso",format=raw,media=cdrom,readonly=on \
  -device ide-cd,drive=payload,bus=ahci.2 \
  -drive if=none,id=evidence,file="$directory/evidence.img",format=raw,cache=directsync \
  -device ide-hd,drive=evidence,bus=ahci.3 \
  -netdev user,id=net0 -device e1000e,netdev=net0 \
  -rtc base=localtime,clock=host -boot menu=off \
  -monitor unix:"$directory/monitor.sock",server=on,wait=off
REMOTE

# Microsoft boot media prompts for a key only on the first blank-disk boot.
# Send Enter during that bounded window; later installer reboots receive no key
# and therefore fall through to the new disk instead of restarting setup.
for delay in 5 7 9; do
  sleep "$delay"
  "${host_ssh[@]}" bash -s -- "$remote_dir/monitor.sock" <<'REMOTE'
set -euo pipefail
socket=$1
[[ -S "$socket" ]] && printf 'sendkey ret\n' | socat - "UNIX-CONNECT:$socket" >/dev/null
REMOTE
done

deadline=$((SECONDS + 3600))
while "${host_ssh[@]}" systemctl is-active --quiet "$unit"; do
  (( SECONDS < deadline )) || { echo 'Windows smoke timed out' >&2; exit 1; }
  sleep 10
done
"${host_ssh[@]}" journalctl -u "$unit" -n 80 --no-pager > "$evidence_dir/vm-final.log" || true
"${host_ssh[@]}" bash -s -- "$remote_dir" <<'REMOTE'
set -euo pipefail
directory=$1
mountpoint="$directory/evidence-mount"
output="$directory/evidence"
mkdir -m 700 "$mountpoint" "$output"
evidence_loop=$(losetup --find --show --partscan --read-only "$directory/evidence.img")
mount -o ro "${evidence_loop}p1" "$mountpoint"
trap 'umount "$mountpoint" 2>/dev/null || true; rmdir "$mountpoint" 2>/dev/null || true; losetup -d "$evidence_loop" 2>/dev/null || true' EXIT
find "$mountpoint" -maxdepth 1 -type f -exec cp -- {} "$output/" \;
REMOTE
sshpass -f "$password_file" scp -q "${ssh_options[@]}" \
  "root@$host:$remote_dir/evidence/*" "$evidence_dir/" 2>/dev/null || true
[[ -f "$evidence_dir/passed.txt" && ! -f "$evidence_dir/failed.txt" ]] \
  || { cat "$evidence_dir/failed.txt" >&2; exit 1; }

jq -e '.schema == 3 and (.os | startswith("Windows"))
  and (.arch == "amd64" or .arch == "x86_64") and .ffmpegClassifier == "windows-x86_64"
  and .requestedBackend == "software" and .expectedEffectiveBackend == "software"
  and (.rows | length == 3) and ([.rows[] | .accepted and .effectiveBackend == "software"] | all)' \
  "$evidence_dir/decoder-benchmark.json" >/dev/null
normalized_system=$(tr -d '\r' < "$evidence_dir/system.txt")
grep -Eq '^processorArchitecture=AMD64$' <<<"$normalized_system"
grep -Eq '^peMachine=0x8664$' <<<"$normalized_system"
sha256sum "$evidence_dir/decoder-benchmark.json" "$evidence_dir/decoder-benchmark.csv" \
  "$evidence_dir/system.txt" > "$evidence_dir/output-SHA256SUMS"
python3 "$repo_root/scripts/proxmox-hwtest.py" audit --manifest "$manifest" \
  --password-file "$password_file" > "$evidence_dir/pre-cleanup-audit.json"
printf 'Windows x86-64 native decoder smoke passed: %s\n' "$evidence_dir"
