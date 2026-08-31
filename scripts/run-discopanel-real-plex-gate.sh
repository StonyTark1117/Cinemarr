#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
api_base=${DISCOPANEL_API_BASE:-}
server_host=${DISCOPANEL_SERVER_HOST:-}
label=${1:-}

if [[ -z "${DISCOPANEL_TOKEN:-}" ]]; then
  echo "DISCOPANEL_TOKEN is required" >&2
  exit 2
fi
if [[ ! "$api_base" =~ ^https?://[A-Za-z0-9._:-]+$ ]] \
    || [[ ! "$server_host" =~ ^[A-Za-z0-9._:-]+$ ]]; then
  echo "DiscPanel API or server host is unsafe" >&2
  exit 2
fi

case "$label" in
  1.7.10-forge)
    server_name='Jammarr 1.7.10 Forge Test'
    target_dir="$repo_root/platforms/mc1.7.10/forge"
    java_home=${CINEMARR_JAVA26_HOME:-/usr/lib/jvm/java-26-openjdk}
    expected_jar='cinemarr-1.0.0+mc1.7.10-forge.jar'
    ;;
  1.20.1-quilt)
    server_name='Jammarr 1.20.1 Quilt Test'
    target_dir="$repo_root/platforms/mc1.20.1/fabric"
    java_home=${CINEMARR_JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk}
    expected_jar='cinemarr-1.0.0+mc1.20.1-fabric.jar'
    ;;
  1.21.1-neoforge)
    server_name='Jammarr 1.21.1 NeoForge Test'
    target_dir="$repo_root"
    java_home=${CINEMARR_JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk}
    expected_jar='cinemarr-1.0.0+mc1.21.1-neoforge.jar'
    ;;
  26.2-fabric)
    server_name='Jammarr 26.2 Fabric Test'
    target_dir="$repo_root/platforms/mc26.2/fabric"
    java_home=${CINEMARR_JAVA26_HOME:-/usr/lib/jvm/java-26-openjdk}
    expected_jar='cinemarr-1.0.0+mc26.2-fabric.jar'
    ;;
  *)
    echo "Usage: $0 {1.7.10-forge|1.20.1-quilt|1.21.1-neoforge|26.2-fabric}" >&2
    exit 2
    ;;
esac

for tool in curl jq base64 pactl parec ffmpeg xvfb-run sha256sum; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }
done
[[ -x "$java_home/bin/java" ]] || { echo "Java home is unavailable: $java_home" >&2; exit 2; }

api_call() {
  local endpoint=$1 body=$2 response status attempt
  for attempt in 1 2 3 4 5; do
    response=$(curl -sS -w $'\n%{http_code}' \
      -H "Authorization: Bearer $DISCOPANEL_TOKEN" \
      -H 'Content-Type: application/json' \
      --data-binary @- "$api_base/$endpoint" <<<"$body") || true
    status=${response##*$'\n'}
    response=${response%$'\n'*}
    if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then
      printf '%s' "$response"
      return 0
    fi
    if [[ "$status" != 000 && "$status" != 400 && "$status" != 409 && "$status" != 429 && ! "$status" =~ ^5 ]]; then
      break
    fi
    sleep "$attempt"
  done
  echo "DiscPanel request failed for $endpoint (HTTP ${status:-unavailable})" >&2
  return 1
}

get_server() {
  api_call discopanel.v1.ServerService/GetServer "$(jq -cn --arg id "$server_id" '{id:$id}')"
}

wait_for_status() {
  local wanted=$1 deadline=$((SECONDS + 180)) state
  while (( SECONDS < deadline )); do
    state=$(get_server | jq -r '.server.status')
    [[ "$state" == "$wanted" ]] && return 0
    sleep 2
  done
  echo "$server_name did not reach $wanted" >&2
  return 1
}

send_command() {
  local command=$1
  api_call discopanel.v1.ServerService/SendCommand \
    "$(jq -cn --arg id "$server_id" --arg command "$command" '{id:$id,command:$command,silent:false}')" >/dev/null
}

command_output() {
  local command=$1 response
  response=$(api_call discopanel.v1.ServerService/SendCommand \
    "$(jq -cn --arg id "$server_id" --arg command "$command" '{id:$id,command:$command,silent:false}')")
  [[ $(jq -r '.success // false' <<<"$response") == true ]] || {
    echo "DiscPanel command failed: $command" >&2
    jq -r '.error // .output // "unknown command failure"' <<<"$response" >&2
    return 1
  }
  jq -r '.output // empty' <<<"$response"
}

new_logs() {
  api_call discopanel.v1.ServerService/GetServerLogs \
    "$(jq -cn --arg id "$server_id" '{id:$id,tail:1000}')" \
    | jq -r --arg started "$started_at" '.logs[] | select(.timestamp >= $started) | .message'
}

wait_for_log() {
  local pattern=$1 timeout=${2:-240} deadline
  deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    new_logs | grep -Eq "$pattern" && return 0
    sleep 3
  done
  echo "$server_name did not log expected readiness: $pattern" >&2
  return 1
}

update_overrides() {
  local overrides=$1
  api_call discopanel.v1.ServerService/UpdateServer \
    "$(jq -cn --arg id "$server_id" --argjson overrides "$overrides" \
      '{id:$id,dockerOverrides:$overrides,autoStart:false}')" >/dev/null
}

restore_properties() {
  api_call discopanel.v1.FileService/UpdateFile \
    "$(jq -cn --arg id "$server_id" --arg content "$original_properties" \
      '{serverId:$id,path:"server.properties",content:$content}')" >/dev/null
}

plex_cinemarr_session_count() {
  local path=$1
  curl -fsS -H 'Accept: application/json' -H "X-Plex-Token: $CINEMARR_PLEX_TOKEN" \
    "${CINEMARR_PLEX_URL%/}$path" \
    | jq '[.. | objects | select(.clientIdentifier? == "c4c216b757884ecb91dce7f33dbe9f31")] | length'
}

remote_prepared=0
remote_started=0
acceptance_tv_id=''
acceptance_forceload=0
cleanup_remote() {
  local cleanup_status=$?
  trap - EXIT INT TERM
  set +e
  if declare -F cleanup_all >/dev/null; then cleanup_all; fi
  if (( remote_started )); then
    if [[ -n "$acceptance_tv_id" ]]; then send_command "cinemarr tv unregister $acceptance_tv_id"; fi
    send_command 'setblock -1 100 -1 air'
    if (( acceptance_forceload )); then
      command_output 'forceload remove -16 -16 15 15' >/dev/null
      acceptance_forceload=0
    fi
    sleep 2
    api_call discopanel.v1.ServerService/StopServer "$(jq -cn --arg id "$server_id" '{id:$id}')" >/dev/null
    wait_for_status SERVER_STATUS_STOPPED
  fi
  if (( remote_prepared )); then
    restore_properties
    update_overrides "$original_overrides"
  fi
  unset CINEMARR_PLEX_TOKEN CINEMARR_PLEX_URL DISCOPANEL_TOKEN
  exit "$cleanup_status"
}
trap cleanup_remote EXIT
trap 'exit 130' INT TERM

servers=$(api_call discopanel.v1.ServerService/ListServers '{}')
matches=$(jq --arg name "$server_name" '[.servers[] | select(.name == $name)] | length' <<<"$servers")
[[ "$matches" == 1 ]] || { echo "Expected one DiscPanel server named $server_name; found $matches" >&2; exit 1; }
server_id=$(jq -r --arg name "$server_name" '.servers[] | select(.name == $name) | .id' <<<"$servers")
server=$(get_server)
[[ $(jq -r '.server.status' <<<"$server") == SERVER_STATUS_STOPPED ]] \
  || { echo "$server_name must be stopped before the gate" >&2; exit 1; }
[[ $(jq -r '.server.autoStart // false' <<<"$server") == false ]] \
  || { echo "$server_name must have autostart disabled" >&2; exit 1; }
port=$(jq -r '.server.port' <<<"$server")
[[ "$port" =~ ^[0-9]+$ ]] || { echo "$server_name has no valid game port" >&2; exit 1; }
original_overrides=$(jq -c '.server.dockerOverrides // {}' <<<"$server")
CINEMARR_PLEX_TOKEN=$(jq -r '.server.dockerOverrides.environment.CINEMARR_PLEX_TOKEN // empty' <<<"$server")
[[ -n "$CINEMARR_PLEX_TOKEN" ]] || { echo "$server_name has no process-local Plex credential" >&2; exit 1; }

mods=$(api_call discopanel.v1.ModService/ListMods \
  "$(jq -cn --arg id "$server_id" '{serverId:$id}')")
cinemarr_matches=$(jq '[.mods[] | select(.enabled == true and (.fileName | ascii_downcase | startswith("cinemarr-") and endswith(".jar")))] | length' <<<"$mods")
[[ "$cinemarr_matches" == 1 ]] \
  || { echo "$server_name must have exactly one enabled Cinemarr JAR; found $cinemarr_matches" >&2; exit 1; }
cinemarr_name=$(jq -r '.mods[] | select(.enabled == true and (.fileName | ascii_downcase | startswith("cinemarr-") and endswith(".jar"))) | .fileName' <<<"$mods")
[[ "$cinemarr_name" == "$expected_jar" && -f "$repo_root/build/releases/$expected_jar" ]] \
  || { echo "$label has unexpected active or local Cinemarr artifact $cinemarr_name" >&2; exit 1; }
remote_cinemarr_sha=$(api_call discopanel.v1.FileService/GetFile \
  "$(jq -cn --arg id "$server_id" --arg path "mods/$cinemarr_name" '{serverId:$id,path:$path}')" \
  | jq -r '.content' | base64 -d | sha256sum | awk '{print $1}')
local_cinemarr_sha=$(sha256sum "$repo_root/build/releases/$expected_jar" | awk '{print $1}')
[[ "$remote_cinemarr_sha" == "$local_cinemarr_sha" ]] \
  || { echo "$label remote Cinemarr JAR does not match the indexed artifact" >&2; exit 1; }
jammarr_matches=$(jq '[.mods[] | select(.enabled == true and (.fileName | ascii_downcase | startswith("jammarr-") and endswith(".jar")))] | length' <<<"$mods")
[[ "$jammarr_matches" == 1 ]] \
  || { echo "$server_name must have exactly one enabled Jammarr JAR; found $jammarr_matches" >&2; exit 1; }
jammarr_name=$(jq -r '.mods[] | select(.enabled == true and (.fileName | ascii_downcase | startswith("jammarr-") and endswith(".jar"))) | .fileName' <<<"$mods")
jammarr_response=$(api_call discopanel.v1.FileService/GetFile \
  "$(jq -cn --arg id "$server_id" --arg path "mods/$jammarr_name" '{serverId:$id,path:$path}')")
jammarr_content=$(jq -r '.content' <<<"$jammarr_response")
for role in leader follower; do
  client_mod_dir="$repo_root/build/discopanel-real-plex/$label/$label.audio-$role/mods"
  mkdir -p "$client_mod_dir"
  # These game directories are deliberately retained as evidence between
  # runs. Remove the prior Jammarr candidate before installing the single
  # server-matched artifact, otherwise legacy Forge rejects duplicate mod IDs
  # before either acceptance client can initialize.
  find "$client_mod_dir" -maxdepth 1 -type f \
    \( -iname 'jammarr-*.jar' -o -iname 'jammarr-*.jar.production-reference' \) -delete
  base64 -d <<<"$jammarr_content" > "$client_mod_dir/$jammarr_name"
done
leader_jammarr_sha=$(sha256sum "$repo_root/build/discopanel-real-plex/$label/$label.audio-leader/mods/$jammarr_name" | awk '{print $1}')
follower_jammarr_sha=$(sha256sum "$repo_root/build/discopanel-real-plex/$label/$label.audio-follower/mods/$jammarr_name" | awk '{print $1}')
[[ "$leader_jammarr_sha" == "$follower_jammarr_sha" ]] \
  || { echo "Temporary clients received different Jammarr artifacts" >&2; exit 1; }
server_jammarr_sha=$leader_jammarr_sha
client_jammarr_name=$jammarr_name
if [[ "$label" == 1.7.10-forge ]]; then
  jammarr_build_dir=${JAMMARR_LEGACY_BUILD_DIR:-/home/braydon/PAmpMod/platforms/mc1.7.10/forge/build/libs}
  local_production_jar="$jammarr_build_dir/$jammarr_name"
  local_development_jar="$jammarr_build_dir/${jammarr_name%.jar}-dev.jar"
  [[ -f "$local_production_jar" && -f "$local_development_jar" ]] \
    || { echo "Matching Jammarr 1.7.10 production/development artifacts are required" >&2; exit 1; }
  [[ $(sha256sum "$local_production_jar" | awk '{print $1}') == "$server_jammarr_sha" ]] \
    || { echo "Local Jammarr production artifact does not match the DiscPanel server" >&2; exit 1; }
  client_jammarr_name=$(basename "$local_development_jar")
  for role in leader follower; do
    client_mod_dir="$repo_root/build/discopanel-real-plex/$label/$label.audio-$role/mods"
    mv "$client_mod_dir/$jammarr_name" "$client_mod_dir/$jammarr_name.production-reference"
    cp "$local_development_jar" "$client_mod_dir/$client_jammarr_name"
  done
  leader_jammarr_sha=$(sha256sum "$repo_root/build/discopanel-real-plex/$label/$label.audio-leader/mods/$client_jammarr_name" | awk '{print $1}')
  follower_jammarr_sha=$(sha256sum "$repo_root/build/discopanel-real-plex/$label/$label.audio-follower/mods/$client_jammarr_name" | awk '{print $1}')
  [[ "$leader_jammarr_sha" == "$follower_jammarr_sha" ]] \
    || { echo "Temporary legacy clients received different deobfuscated Jammarr artifacts" >&2; exit 1; }
fi
unset jammarr_content jammarr_response

file_response=$(api_call discopanel.v1.FileService/GetFile \
  "$(jq -cn --arg id "$server_id" '{serverId:$id,path:"server.properties"}')")
original_properties=$(jq -r '.content' <<<"$file_response")
config_response=$(api_call discopanel.v1.FileService/GetFile \
  "$(jq -cn --arg id "$server_id" '{serverId:$id,path:"world/serverconfig/cinemarr-server.toml"}')")
CINEMARR_PLEX_URL=$(jq -r '.content' <<<"$config_response" | base64 -d \
  | sed -n 's/^[[:space:]]*plexUrl[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)
[[ "$CINEMARR_PLEX_URL" =~ ^https?:// ]] || { echo "$server_name has no valid Plex URL" >&2; exit 1; }
export CINEMARR_PLEX_TOKEN CINEMARR_PLEX_URL

initial_transcodes=$(plex_cinemarr_session_count /transcode/sessions)
initial_sessions=$(plex_cinemarr_session_count /status/sessions)
[[ "$initial_transcodes" == 0 && "$initial_sessions" == 0 ]] \
  || { echo "Plex already has a Cinemarr session before $label" >&2; exit 1; }

acceptance_overrides=$(jq -c '
  .environment = (.environment // {})
  | .environment.JAVA_TOOL_OPTIONS = (((.environment.JAVA_TOOL_OPTIONS // "") + " -Dcinemarr.acceptance.enabled=true -Dcinemarr.acceptance.videoProbe=true") | ltrimstr(" "))
  | .environment.ONLINE_MODE = "FALSE"
  | .environment.ENFORCE_SECURE_PROFILE = "FALSE"
  | .environment.SPAWN_MONSTERS = "FALSE"
  | .environment.VIEW_DISTANCE = "3"
  | .environment.SIMULATION_DISTANCE = "3"
  ' <<<"$original_overrides")
remote_prepared=1
update_overrides "$acceptance_overrides"

started_at=$(date -u +%Y-%m-%dT%H:%M:%S.%NZ)
api_call discopanel.v1.ServerService/StartServer "$(jq -cn --arg id "$server_id" '{id:$id}')" >/dev/null
remote_started=1
wait_for_status SERVER_STATUS_RUNNING
wait_for_log 'Done \(|For help, type "help"' 300
wait_for_log 'Validated 1 allowed Plex video libraries' 180
baseline_diagnostics=$(command_output 'cinemarr diagnostics')
[[ "$baseline_diagnostics" == *'Plex=ready;'* && "$baseline_diagnostics" == *'activeStreams=0/'* ]] \
  || { echo "$label did not start from an idle, ready video service: $baseline_diagnostics" >&2; exit 1; }
baseline_registered_tvs=$(sed -n 's/.*registeredTvs=\([0-9][0-9]*\);.*/\1/p' <<<"$baseline_diagnostics")
[[ "$baseline_registered_tvs" =~ ^[0-9]+$ ]] \
  || { echo "$label did not expose its baseline TV count" >&2; exit 1; }
send_command 'setblock -1 100 -1 air'
sleep 2
if [[ "$label" != 1.7.10-forge ]]; then
  # Modern clients can reconnect from the distant lifecycle-probe position;
  # keep only the four acceptance-TV chunks resident until teardown so the
  # asynchronous Plex start cannot race the teleport's chunk-tracking update.
  command_output 'forceload add -16 -16 15 15' >/dev/null
  acceptance_forceload=1
fi

export CINEMARR_GATE_LIBRARY_ONLY=true
export CINEMARR_VIDEO_CLIENT_GATE=true
export CINEMARR_VIDEO_CONTROL_GATE=${CINEMARR_VIDEO_CONTROL_GATE:-true}
export CINEMARR_VIDEO_FOLLOWER_SMALL_WINDOW=${CINEMARR_VIDEO_FOLLOWER_SMALL_WINDOW:-true}
export CINEMARR_LIVE_PLEX_GATE=true
export CINEMARR_ACCEPTANCE_SERVER_HOST="$server_host"
export CINEMARR_GATE_OUTPUT_ROOT="$repo_root/build/discopanel-real-plex/$label"
export CINEMARR_LIVE_VIDEO_SECTION_ID=${CINEMARR_LIVE_VIDEO_SECTION_ID:-1}
# shellcheck source=run-dedicated-server-gate.sh
source "$repo_root/scripts/run-dedicated-server-gate.sh" "$label"
trap cleanup_remote EXIT
trap 'exit 130' INT TERM

if ! run_two_client_video "$label" "$target_dir" "$java_home" "$port"; then
  echo "$label failed real-Plex two-client playback" >&2
  exit 1
fi
acceptance_tv_id=$(sed -n \
  '/Acceptance video rendered:/ { s/.*television=\([0-9a-f-]\{36\}\).*/\1/p; q; }' \
  "$CINEMARR_GATE_OUTPUT_ROOT/$label.audio-leader.console.log")
[[ "$acceptance_tv_id" =~ ^[0-9a-f-]{36}$ ]] \
  || { echo "$label did not expose the rendered acceptance TV identifier" >&2; exit 1; }
wait_for_log 'Acceptance Quick TV:' 60

command_output "cinemarr tv unregister $acceptance_tv_id" >/dev/null
command_output 'setblock -1 100 -1 air' >/dev/null
sleep 3
for _ in {1..20}; do
  diagnostics=$(command_output 'cinemarr diagnostics')
  if [[ "$diagnostics" == *"registeredTvs=$baseline_registered_tvs;"* && "$diagnostics" == *'activeStreams=0/'* ]]; then
    break
  fi
  sleep 2
done
[[ "$diagnostics" == *"registeredTvs=$baseline_registered_tvs;"* && "$diagnostics" == *'activeStreams=0/'* ]] \
  || { echo "$label did not restore its baseline TV and stream state: $diagnostics" >&2; exit 1; }

for _ in {1..20}; do
  final_transcodes=$(plex_cinemarr_session_count /transcode/sessions)
  final_sessions=$(plex_cinemarr_session_count /status/sessions)
  [[ "$final_transcodes" == 0 && "$final_sessions" == 0 ]] && break
  sleep 2
done
[[ "$final_transcodes" == 0 && "$final_sessions" == 0 ]] \
  || { echo "$label left a Cinemarr Plex session behind" >&2; exit 1; }

mkdir -p "$CINEMARR_GATE_OUTPUT_ROOT"
{
  printf 'DiscPanel server Cinemarr artifact: %s\n' "$cinemarr_name"
  printf 'DiscPanel server Cinemarr SHA-256: %s\n' "$remote_cinemarr_sha"
  printf 'DiscPanel server Jammarr artifact: %s\n' "$jammarr_name"
  printf 'DiscPanel server Jammarr SHA-256: %s\n' "$server_jammarr_sha"
  printf 'Temporary client Jammarr artifact: %s\n' "$client_jammarr_name"
  printf 'Temporary client Jammarr SHA-256: %s\n' "$leader_jammarr_sha"
  printf 'Baseline server diagnostics: %s\n' "$baseline_diagnostics"
  printf 'Final server diagnostics: %s\n' "$diagnostics"
  new_logs | grep -E 'Acceptance Quick TV:|Plex=ready;|CinemarrVideo[AB].*(joined|left) the game' || true
} > "$CINEMARR_GATE_OUTPUT_ROOT/$label.remote-server.evidence.txt"

if (( acceptance_forceload )); then
  command_output 'forceload remove -16 -16 15 15' >/dev/null
  acceptance_forceload=0
fi

api_call discopanel.v1.ServerService/StopServer "$(jq -cn --arg id "$server_id" '{id:$id}')" >/dev/null
wait_for_status SERVER_STATUS_STOPPED
remote_started=0
restore_properties
update_overrides "$original_overrides"
remote_prepared=0

final_server=$(get_server)
[[ $(jq -cS '.server.dockerOverrides // {}' <<<"$final_server") == $(jq -cS . <<<"$original_overrides") ]] \
  || { echo "$server_name overrides were not restored exactly" >&2; exit 1; }
[[ $(jq -r '.server.status' <<<"$final_server") == SERVER_STATUS_STOPPED ]] \
  || { echo "$server_name was not left stopped" >&2; exit 1; }
[[ $(jq -r '.server.autoStart // false' <<<"$final_server") == false ]] \
  || { echo "$server_name autostart was not left disabled" >&2; exit 1; }

restored_properties=$(api_call discopanel.v1.FileService/GetFile \
  "$(jq -cn --arg id "$server_id" '{serverId:$id,path:"server.properties"}')" | jq -r '.content')
[[ "$restored_properties" == "$original_properties" ]] \
  || { echo "$server_name server.properties was not restored exactly" >&2; exit 1; }

echo "$label: controller-selected real Plex video, two-client A/V, and clean teardown passed"
