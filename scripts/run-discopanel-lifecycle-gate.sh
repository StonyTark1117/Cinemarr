#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
api_base=${DISCOPANEL_API_BASE:-}
server_host=${DISCOPANEL_SERVER_HOST:-}
label=${1:-}

[[ -n "${DISCOPANEL_TOKEN:-}" ]] || { echo "DISCOPANEL_TOKEN is required" >&2; exit 2; }
[[ "$api_base" =~ ^https?://[A-Za-z0-9._:-]+$ && "$server_host" =~ ^[A-Za-z0-9._:-]+$ ]] \
  || { echo "DiscPanel API and server host are required process-local inputs" >&2; exit 2; }
case "$label" in
  1.7.10-forge)
    server_name='Jammarr 1.7.10 Forge Test'
    target_dir="$repo_root/platforms/mc1.7.10/forge"
    java_home=${CINEMARR_JAVA26_HOME:-/usr/lib/jvm/java-26-openjdk}
    expected_jar='cinemarr-1.0.0+mc1.7.10-forge.jar'
    ;;
  1.21.1-neoforge)
    server_name='Jammarr 1.21.1 NeoForge Test'
    target_dir="$repo_root"
    java_home=${CINEMARR_JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk}
    expected_jar='cinemarr-1.0.0+mc1.21.1-neoforge.jar'
    ;;
  *) echo "Usage: $0 {1.7.10-forge|1.21.1-neoforge}" >&2; exit 2 ;;
esac

for tool in curl jq base64 pactl xvfb-run sha256sum; do command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }; done
[[ -x "$java_home/bin/java" && -f "$repo_root/build/releases/$expected_jar" ]] \
  || { echo "$label requires its indexed release JAR and Java runtime" >&2; exit 2; }

api_call() {
  local endpoint=$1 body=$2 response status attempt
  for attempt in 1 2 3 4 5; do
    response=$(curl -sS -w $'\n%{http_code}' -H "Authorization: Bearer $DISCOPANEL_TOKEN" \
      -H 'Content-Type: application/json' --data-binary @- "$api_base/$endpoint" <<<"$body") || true
    status=${response##*$'\n'}; response=${response%$'\n'*}
    if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then printf '%s' "$response"; return 0; fi
    sleep "$attempt"
  done
  echo "DiscPanel request failed for $endpoint (HTTP ${status:-unavailable})" >&2; return 1
}

get_server() { api_call discopanel.v1.ServerService/GetServer "$(jq -cn --arg id "$server_id" '{id:$id}')"; }
wait_for_status() {
  local wanted=$1 deadline=$((SECONDS+180)) state
  while (( SECONDS < deadline )); do state=$(get_server | jq -r '.server.status'); [[ "$state" == "$wanted" ]] && return 0; sleep 2; done
  echo "$server_name did not reach $wanted" >&2; return 1
}
new_logs() {
  api_call discopanel.v1.ServerService/GetServerLogs "$(jq -cn --arg id "$server_id" '{id:$id,tail:1000}')" \
    | jq -r --arg started "$started_at" '.logs[]|select(.timestamp >= $started)|.message'
}
wait_for_log() {
  local pattern=$1 timeout=${2:-240} deadline
  deadline=$((SECONDS+timeout))
  while (( SECONDS < deadline )); do new_logs | grep -Eq "$pattern" && return 0; sleep 2; done
  echo "$server_name did not log $pattern" >&2; return 1
}
send_command() {
  api_call discopanel.v1.ServerService/SendCommand \
    "$(jq -cn --arg id "$server_id" --arg command "$1" '{id:$id,command:$command,silent:false}')" >/dev/null
}
command_output() {
  local response
  response=$(api_call discopanel.v1.ServerService/SendCommand \
    "$(jq -cn --arg id "$server_id" --arg command "$1" '{id:$id,command:$command,silent:false}')")
  [[ $(jq -r '.success // false' <<<"$response") == true ]] || return 1
  jq -r '.output // empty' <<<"$response"
}
update_overrides() {
  api_call discopanel.v1.ServerService/UpdateServer \
    "$(jq -cn --arg id "$server_id" --argjson value "$1" '{id:$id,dockerOverrides:$value,autoStart:false}')" >/dev/null
}
start_remote() {
  started_at=$(date -u +%Y-%m-%dT%H:%M:%S.%NZ)
  api_call discopanel.v1.ServerService/StartServer "$(jq -cn --arg id "$server_id" '{id:$id}')" >/dev/null
  remote_started=1
  wait_for_status SERVER_STATUS_RUNNING
  wait_for_log 'Done \(|For help, type "help"' 300
  wait_for_log 'Validated 1 allowed Plex video libraries' 180
}
stop_remote() {
  api_call discopanel.v1.ServerService/StopServer "$(jq -cn --arg id "$server_id" '{id:$id}')" >/dev/null
  wait_for_status SERVER_STATUS_STOPPED; remote_started=0
}

remote_started=0; remote_prepared=0; modern_forceload=0; cleanup_owner_pid=$BASHPID
cleanup() {
  local status=$?
  [[ $BASHPID == "$cleanup_owner_pid" ]] || return 0
  trap - EXIT INT TERM; set +e
  if declare -F cleanup_audio_processes >/dev/null; then cleanup_audio_processes; fi
  if (( modern_forceload && remote_started )); then
    command_output 'forceload remove 1968 2048 2128 2048' >/dev/null
    modern_forceload=0
  fi
  (( remote_started )) && stop_remote
  (( remote_prepared )) && update_overrides "$original_overrides"
  unset DISCOPANEL_TOKEN
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

servers=$(api_call discopanel.v1.ServerService/ListServers '{}')
server_id=$(jq -r --arg name "$server_name" '.servers[]|select(.name==$name)|.id' <<<"$servers")
[[ -n "$server_id" ]] || { echo "Missing DiscPanel server $server_name" >&2; exit 1; }
server=$(get_server)
[[ $(jq -r '.server.status' <<<"$server") == SERVER_STATUS_STOPPED \
   && $(jq -r '.server.autoStart // false' <<<"$server") == false ]] \
  || { echo "$server_name must begin stopped with autostart disabled" >&2; exit 1; }
port=$(jq -r '.server.port' <<<"$server")
original_overrides=$(jq -c '.server.dockerOverrides // {}' <<<"$server")

mods=$(api_call discopanel.v1.ModService/ListMods "$(jq -cn --arg id "$server_id" '{serverId:$id}')")
cinemarr_name=$(jq -r '.mods[]|select(.enabled==true and (.fileName|ascii_downcase|startswith("cinemarr-") and endswith(".jar")))|.fileName' <<<"$mods")
[[ "$cinemarr_name" == "$expected_jar" ]] || { echo "$label has unexpected active Cinemarr artifact $cinemarr_name" >&2; exit 1; }
remote_sha=$(api_call discopanel.v1.FileService/GetFile "$(jq -cn --arg id "$server_id" --arg path "mods/$cinemarr_name" '{serverId:$id,path:$path}')" \
  | jq -r '.content' | base64 -d | sha256sum | awk '{print $1}')
local_sha=$(sha256sum "$repo_root/build/releases/$expected_jar" | awk '{print $1}')
[[ "$remote_sha" == "$local_sha" ]] || { echo "$label remote Cinemarr JAR does not match the indexed artifact" >&2; exit 1; }

output_root="$repo_root/build/discopanel-lifecycle/$label"
mkdir -p "$output_root"
jammarr_name=$(jq -r '.mods[]|select(.enabled==true and (.fileName|ascii_downcase|startswith("jammarr-") and endswith(".jar")))|.fileName' <<<"$mods")
[[ -n "$jammarr_name" ]] || { echo "$label has no enabled Jammarr dependency" >&2; exit 1; }
if [[ "$label" == 1.7.10-forge ]]; then
  jammarr_source="/home/braydon/PAmpMod/platforms/mc1.7.10/forge/build/libs/${jammarr_name%.jar}-dev.jar"
  [[ -f "$jammarr_source" ]] || { echo "Legacy Jammarr development JAR is unavailable" >&2; exit 1; }
  for role in leader follower; do
    mods_dir="$output_root/$label.audio-$role/mods"
    mkdir -p "$mods_dir"
    # The lifecycle workspace is intentionally reusable, but the active
    # Jammarr development filename changes with its version. Remove only the
    # task-owned legacy dev JARs so a prior run cannot create a duplicate mod.
    find "$mods_dir" -maxdepth 1 -type f -name 'jammarr-*-dev.jar' -delete
    cp -- "$jammarr_source" "$mods_dir/"
  done
else
  jammarr_content=$(api_call discopanel.v1.FileService/GetFile "$(jq -cn --arg id "$server_id" --arg path "mods/$jammarr_name" '{serverId:$id,path:$path}')" | jq -r '.content')
  for role in leader follower; do mkdir -p "$output_root/$label.audio-$role/mods"; base64 -d <<<"$jammarr_content" > "$output_root/$label.audio-$role/mods/$jammarr_name"; done
  unset jammarr_content
fi

lifecycle_overrides=$(jq -c '
  .environment=(.environment//{})
  | .environment.JAVA_TOOL_OPTIONS=(((.environment.JAVA_TOOL_OPTIONS//"")+" -Dcinemarr.acceptance.enabled=true -Dcinemarr.acceptance.videoProbe=true -Dcinemarr.acceptance.lifecycleProbe=true")|ltrimstr(" "))
  | .environment.ONLINE_MODE="FALSE"
  | .environment.ENFORCE_SECURE_PROFILE="FALSE"
  | .environment.SPAWN_MONSTERS="FALSE"
  | .environment.VIEW_DISTANCE="8"
  | .environment.SIMULATION_DISTANCE="8"' <<<"$original_overrides")
update_overrides "$lifecycle_overrides"; remote_prepared=1

export CINEMARR_GATE_LIBRARY_ONLY=true CINEMARR_VIDEO_CLIENT_GATE=true CINEMARR_VIDEO_CONTROL_GATE=false
export CINEMARR_ACCEPTANCE_SERVER_HOST="$server_host" CINEMARR_GATE_OUTPUT_ROOT="$output_root"
# shellcheck source=run-dedicated-server-gate.sh
source "$repo_root/scripts/run-dedicated-server-gate.sh" "$label"
trap cleanup EXIT; trap 'exit 130' INT TERM

start_remote
if [[ "$label" == 1.21.1-neoforge ]]; then
  command_output 'forceload add 1968 2048 2128 2048' >/dev/null
  modern_forceload=1
fi
sink="cinemarr_${BASHPID}_${label//[^a-zA-Z0-9]/_}_lifecycle"
module=$(pactl load-module module-null-sink sink_name="$sink" rate=48000 channels=2)
active_audio_modules+=("$module")
start_audio_client "$label" "$target_dir" "$java_home" "$port" leader CinemarrVideoA "$sink"
builder_pid=$started_audio_client_pid
wait_for_log 'Acceptance Quick TV lifecycle checkpoint:.*placed=256.*remaining=8960' 300
checkpoint_log=$(new_logs | grep 'Acceptance Quick TV lifecycle checkpoint:' | tail -n 1)
terminate_client_launch "$builder_pid" 20
if (( modern_forceload )); then
  command_output 'forceload remove 1968 2048 2128 2048' >/dev/null
  modern_forceload=0
fi
stop_remote

start_remote
wait_for_log 'Acceptance Quick TV lifecycle recovery:.*remaining=[1-9][0-9]*.*complete=false' 180
partial_recovery=$(new_logs | grep 'Acceptance Quick TV lifecycle recovery:' | head -n 1)

if [[ "$label" == 1.21.1-neoforge ]]; then
  command_output 'forceload add 1968 2048 2128 2048' >/dev/null
  modern_forceload=1
  command_output 'cinemarr tv prune' >/dev/null
else
  start_audio_client "$label" "$target_dir" "$java_home" "$port" follower CinemarrRecovery "$sink"
  recovery_pid=$started_audio_client_pid
  wait_for_log 'CinemarrRecovery joined the game' 180
  sleep 5
  command_output 'cinemarr tv prune' >/dev/null
fi
wait_for_log 'Acceptance Quick TV lifecycle recovery:.*remaining=0.*complete=true' 180
complete_recovery=$(new_logs | grep 'Acceptance Quick TV lifecycle recovery:.*complete=true' | tail -n 1)

if [[ "$label" == 1.21.1-neoforge ]]; then
  probe=$(command_output 'execute if block 2111 100 2048 air run time query daytime')
  [[ -n "$probe" ]] || { echo "$label did not prove the first placed recovery pixel became air" >&2; exit 1; }
  command_output 'forceload remove 1968 2048 2128 2048' >/dev/null
  modern_forceload=0
else
  probe=$(command_output 'testforblock 2111 100 2048 air')
  [[ "$probe" == *'Successfully found'* || "$probe" == *'successfully found'* ]] \
    || { echo "$label did not prove the first placed recovery pixel became air: $probe" >&2; exit 1; }
fi
command_output 'setblock 2047 100 2047 air' >/dev/null
cleanup_audio_processes
stop_remote
update_overrides "$original_overrides"; remote_prepared=0

final=$(get_server)
[[ $(jq -r '.server.status' <<<"$final") == SERVER_STATUS_STOPPED \
   && $(jq -r '.server.autoStart // false' <<<"$final") == false \
   && $(jq -cS '.server.dockerOverrides // {}' <<<"$final") == $(jq -cS . <<<"$original_overrides") ]] \
  || { echo "$label did not restore its stopped/autostart-off/original-override state" >&2; exit 1; }

{
  printf 'Cinemarr artifact: %s\nSHA-256: %s\n' "$cinemarr_name" "$remote_sha"
  printf '%s\n%s\n%s\n' "$checkpoint_log" "$partial_recovery" "$complete_recovery"
  printf 'First placed pixel was air after unloaded-footprint recovery.\n'
} > "$output_root/$label.lifecycle-recovery.evidence.txt"

echo "$label: restart-mid-build and unloaded-chunk Quick TV recovery passed"
