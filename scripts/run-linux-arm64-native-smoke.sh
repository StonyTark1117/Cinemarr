#!/usr/bin/env bash
set -euo pipefail

# Provisions (once) and reuses a headless Debian arm64 QEMU guest on the
# Proxmox host. The guest disk and its dedicated SSH identity are retained;
# the transient QEMU unit is always stopped before this script returns.

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
host=${CINEMARR_HWTEST_HOST:-}
password_file=${CINEMARR_HWTEST_PASSWORD_FILE:-}
bundle="$repo_root/build/decoder-benchmark/bundle"
image_url=${CINEMARR_ARM64_IMAGE_URL:-https://cloud.debian.org/images/cloud/trixie/latest/debian-13-genericcloud-arm64.qcow2}
checksum_url=${CINEMARR_ARM64_CHECKSUM_URL:-https://cloud.debian.org/images/cloud/trixie/latest/SHA512SUMS}
state_dir=${CINEMARR_ARM64_VM_DIR:-/var/lib/cinemarr-hwtest/linux-arm64}
account_home=$(getent passwd "$(id -u)" | cut -d: -f6)
key_file=${CINEMARR_ARM64_KEY_FILE:-$account_home/.local/share/cinemarr-hwtest/linux-arm64/id_ed25519}
known_hosts_file=${key_file}.known_hosts

for tool in sshpass ssh scp ssh-keygen tar jq sha256sum python3 getent; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }
done
[[ -d "$bundle/classes" && -f "$bundle/lib/ffmpeg-8.1.2-1.5.14-linux-arm64.jar" ]] \
  || { echo 'Run ./gradlew prepareDecoderBenchmarkBundle first' >&2; exit 2; }
[[ -f "$password_file" && "$host" =~ ^[A-Za-z0-9.-]+$ ]] || exit 2
[[ "$state_dir" =~ ^/var/lib/cinemarr-hwtest/[A-Za-z0-9._/-]+$ && "$state_dir" != *'..'* ]] || exit 2
[[ "$key_file" == /* && "$key_file" != *'..'* ]] || exit 2

stamp=$(date -u +%Y%m%dT%H%M%SZ)
evidence_dir="$repo_root/build/native-smoke/linux-arm64/$stamp"
runtime_dir=$(mktemp -d "$repo_root/build/linux-arm64-smoke.XXXXXX")
manifest="$evidence_dir/proxmox-manifest.json"
mkdir -p "$evidence_dir"

ssh_options=(-F /dev/null -o BatchMode=no -o StrictHostKeyChecking=yes -o ConnectTimeout=10)
host_ssh=(sshpass -f "$password_file" ssh "${ssh_options[@]}" "root@$host")
guest_options=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new \
  -o UserKnownHostsFile="$known_hosts_file" -o HostKeyAlias=cinemarr-arm64-native-smoke \
  -o ConnectTimeout=10)
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
  for file in "$runtime_dir/user-data" "$runtime_dir/meta-data"; do
    [[ -f "$file" ]] && unlink -- "$file"
  done
  rmdir -- "$runtime_dir" 2>/dev/null || true
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

tag=$(python3 "$repo_root/scripts/proxmox-hwtest.py" init --manifest "$manifest" \
  --suffix "arm64-${stamp,,}")
unit="${tag}-arm64.service"
python3 "$repo_root/scripts/proxmox-hwtest.py" record --manifest "$manifest" \
  --host "$host" --kind unit --id "$unit"

provision=$("${host_ssh[@]}" bash -s -- "$state_dir" <<'REMOTE'
set -euo pipefail
state=$1 marker=$1/state.json disk=$1/guest.qcow2
parent=${state%/*}
mkdir -p -m 700 "$parent"
if pgrep -af qemu-system-aarch64 | grep -F -- "$disk" >/dev/null; then
  echo 'retained arm64 guest is already running' >&2
  exit 1
fi
if [[ -f "$marker" ]]; then
  python3 - "$marker" <<'PY'
import json, pathlib, sys
value = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert value == {"platform": "linux-arm64", "schema": 1, "state": "ready"}
PY
  [[ -f "$disk" && -f "$state/seed.iso" ]]
  result=false
elif [[ ! -e "$state" ]]; then
  mkdir -m 700 "$state"
  printf '{"platform":"linux-arm64","schema":1,"state":"provisioning"}\n' > "$marker"
  result=true
else
  echo 'retained arm64 directory exists without a valid ready marker' >&2
  exit 1
fi
printf '%s\n' "$result"
REMOTE
)
[[ "$provision" == true || "$provision" == false ]] || exit 1

key_dir=${key_file%/*}
mkdir -p -m 700 "$key_dir"
if [[ "$provision" == true && ! -f "$key_file" ]]; then
  ssh-keygen -q -t ed25519 -N '' -C cinemarr-arm64-native-smoke -f "$key_file"
fi
[[ -f "$key_file" && -f "${key_file}.pub" ]] || {
  echo "Retained arm64 guest key is missing: $key_file" >&2
  exit 1
}
chmod 600 "$key_file"
[[ -f "$known_hosts_file" ]] && chmod 600 "$known_hosts_file"

if [[ "$provision" == true ]]; then
  public_key=$(<"${key_file}.pub")
  cat > "$runtime_dir/user-data" <<EOF
#cloud-config
users:
  - name: cinemarr
    groups: [sudo]
    shell: /bin/bash
    sudo: ALL=(ALL) NOPASSWD:ALL
    ssh_authorized_keys:
      - $public_key
ssh_pwauth: false
package_update: true
packages:
  - openjdk-21-jre-headless
  - file
  - libva2
  - libva-drm2
  - libdrm2
  - libudev1
runcmd:
  - [sh, -c, 'touch /home/cinemarr/cinemarr-cloud-ready && chown cinemarr:cinemarr /home/cinemarr/cinemarr-cloud-ready']
EOF
  printf 'instance-id: cinemarr-retained-arm64\nlocal-hostname: cinemarr-arm64\n' > "$runtime_dir/meta-data"
  "${host_ssh[@]}" bash -s -- "$state_dir" "$image_url" "$checksum_url" <<'REMOTE'
set -euo pipefail
state=$1 image_url=$2 checksum_url=$3
cd "$state"
curl -fsSL "$checksum_url" -o SHA512SUMS
name=${image_url##*/}
expected=$(awk -v name="$name" '$2 == name || $2 == "*" name {print $1}' SHA512SUMS)
[[ "$expected" =~ ^[0-9a-f]{128}$ ]]
curl -fL --retry 4 --retry-all-errors "$image_url" -o "$name"
printf '%s  %s\n' "$expected" "$name" | sha512sum -c -
qemu-img create -q -f qcow2 -F qcow2 -b "$state/$name" "$state/guest.qcow2" 20G
REMOTE
  sshpass -f "$password_file" scp "${ssh_options[@]}" \
    "$runtime_dir/user-data" "$runtime_dir/meta-data" "root@$host:$state_dir/"
  "${host_ssh[@]}" bash -s -- "$state_dir" <<'REMOTE'
set -euo pipefail
state=$1
cd "$state"
genisoimage -quiet -output seed.iso -volid cidata -joliet -rock user-data meta-data
rm -- user-data meta-data
REMOTE
fi

port=$("${host_ssh[@]}" bash -s <<'REMOTE'
python3 - <<'PY'
import socket
sock = socket.socket()
sock.bind(("", 0))
print(sock.getsockname()[1])
sock.close()
PY
REMOTE
)
[[ "$port" =~ ^[0-9]+$ && "$port" -ge 1024 && "$port" -le 65535 ]] || exit 1

"${host_ssh[@]}" bash -s -- "$state_dir" "$unit" "$port" <<'REMOTE'
set -euo pipefail
state=$1 unit=$2 port=$3
systemd-run --unit="$unit" --collect --property=KillMode=mixed --property=RuntimeMaxSec=3600 \
  qemu-system-aarch64 -name cinemarr-arm64-smoke -machine virt,accel=tcg \
  -cpu max -smp 4 -m 6144 -nographic -no-reboot \
  -bios /usr/share/kvm/edk2-aarch64-code.fd \
  -drive if=none,id=hd0,file="$state/guest.qcow2",format=qcow2,cache=writeback \
  -device virtio-blk-device,drive=hd0 \
  -drive if=none,id=seed,file="$state/seed.iso",format=raw,readonly=on \
  -device virtio-blk-device,drive=seed \
  -netdev user,id=net0,hostfwd=tcp:0.0.0.0:"$port"-:22 \
  -device virtio-net-device,netdev=net0
REMOTE

deadline=$((SECONDS + 1200))
until ssh -p "$port" "${guest_options[@]}" -i "$key_file" \
    "cinemarr@$host" 'test -f ~/cinemarr-cloud-ready && command -v java >/dev/null'; do
  (( SECONDS < deadline )) || { echo 'arm64 guest did not finish cloud-init' >&2; exit 1; }
  sleep 10
done

ssh -p "$port" "${guest_options[@]}" -i "$key_file" "cinemarr@$host" \
  'rm -rf -- ~/bundle ~/evidence; mkdir -p ~/bundle ~/evidence'
tar -C "$bundle" -czf - . | ssh -p "$port" "${guest_options[@]}" \
  -i "$key_file" "cinemarr@$host" 'tar -xzf - -C ~/bundle'

ssh -p "$port" "${guest_options[@]}" -i "$key_file" \
  "cinemarr@$host" 'bash -s' -- "$stamp" <<'GUEST'
set -euo pipefail
run_id=$1
[[ $(uname -m) == aarch64 ]]
java_arch=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*os.arch = //p')
[[ "$java_arch" == aarch64 ]]
native_entry=org/bytedeco/ffmpeg/linux-arm64/libavcodec.so.62
python3 - "$native_entry" <<'PY'
import sys, zipfile
entry = sys.argv[1]
with zipfile.ZipFile('bundle/lib/ffmpeg-8.1.2-1.5.14-linux-arm64.jar') as archive:
    with archive.open(entry) as source, open('/tmp/cinemarr-libavcodec.so.62', 'wb') as target:
        target.write(source.read())
PY
file /tmp/cinemarr-libavcodec.so.62 | grep -Eq 'ARM aarch64|ARM64'
classpath='bundle/classes:bundle/lib/core-1.0.0.jar:bundle/lib/javacpp-1.5.14.jar:bundle/lib/javacpp-1.5.14-linux-arm64.jar:bundle/lib/ffmpeg-8.1.2-1.5.14.jar:bundle/lib/ffmpeg-8.1.2-1.5.14-linux-arm64.jar'
java -Xmx3g -cp "$classpath" stonytark.cinemarr.client.VideoDecoderBenchmark \
  --backend software --expected-effective software --output evidence \
  --warmups 0 --runs 1 --seconds 0.6 --require-hardware false \
  --expect-fallback false --fail-on-acceptance true --gpu none --driver none \
  --classifier linux-arm64 --minecraft-profile standalone-native-smoke \
  --gpu-utilization '' --fixture-dir bundle/fixtures --resolutions 144p,480p,1080p
{
  printf 'kernelMachine=%s\n' "$(uname -m)"
  printf 'javaArch=%s\n' "$java_arch"
  printf 'javaVersion=%s\n' "$(java -version 2>&1 | head -n 1)"
  file /tmp/cinemarr-libavcodec.so.62
} > evidence/system.txt
printf '%s\n' "$run_id" > evidence/run-id.txt
unlink -- /tmp/cinemarr-libavcodec.so.62
GUEST

for file in decoder-benchmark.json decoder-benchmark.csv system.txt run-id.txt; do
  scp -P "$port" "${guest_options[@]}" -i "$key_file" \
    "cinemarr@$host:~/evidence/$file" "$evidence_dir/"
done
[[ $(tr -d '\r\n' < "$evidence_dir/run-id.txt") == "$stamp" ]]
jq -e '.schema == 3 and .arch == "aarch64" and .ffmpegClassifier == "linux-arm64"
  and .requestedBackend == "software" and .expectedEffectiveBackend == "software"
  and (.rows | length == 3) and ([.rows[] | .accepted and .effectiveBackend == "software"] | all)' \
  "$evidence_dir/decoder-benchmark.json" >/dev/null
sha256sum "$evidence_dir/decoder-benchmark.json" "$evidence_dir/decoder-benchmark.csv" \
  "$evidence_dir/system.txt" "$evidence_dir/run-id.txt" > "$evidence_dir/SHA256SUMS"

ssh -p "$port" "${guest_options[@]}" -i "$key_file" "cinemarr@$host" \
  'sudo systemctl poweroff' >/dev/null 2>&1 || true
deadline=$((SECONDS + 180))
while "${host_ssh[@]}" systemctl is-active --quiet "$unit"; do
  (( SECONDS < deadline )) || { echo 'arm64 guest did not power off' >&2; exit 1; }
  sleep 3
done
python3 "$repo_root/scripts/proxmox-hwtest.py" audit --manifest "$manifest" \
  --password-file "$password_file" > "$evidence_dir/pre-cleanup-audit.json"
python3 "$repo_root/scripts/proxmox-hwtest.py" cleanup-only \
  --manifest "$manifest" --password-file "$password_file"
cleanup_complete=true

"${host_ssh[@]}" bash -s -- "$state_dir" "$provision" <<'REMOTE' \
  > "$evidence_dir/retained-vm-audit.json"
set -euo pipefail
state=$1 provision=$2 marker=$1/state.json disk=$1/guest.qcow2
if [[ "$provision" == true ]]; then
  printf '{"platform":"linux-arm64","schema":1,"state":"ready"}\n' > "$marker"
fi
if pgrep -af qemu-system-aarch64 | grep -F -- "$disk" >/dev/null; then
  echo 'retained arm64 guest remained running' >&2
  exit 1
fi
python3 - "$state" "$provision" <<'PY'
import json, pathlib, sys
state = pathlib.Path(sys.argv[1])
print(json.dumps({
    "platform": "linux-arm64",
    "provisionedThisRun": sys.argv[2] == "true",
    "stateDirectory": str(state),
    "stateMarker": json.loads((state / "state.json").read_text()),
    "diskBytes": (state / "guest.qcow2").stat().st_size,
    "poweredOn": False,
}, indent=2, sort_keys=True))
PY
REMOTE
printf 'Linux arm64 native decoder smoke passed; retained guest is stopped: %s\n' "$evidence_dir"
