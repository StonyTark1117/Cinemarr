#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
api_base=${DISCOPANEL_API_BASE:-}
proxy_host=${CINEMARR_ACCEPTANCE_PROXY_HOST:-}
label=${1:-}

[[ -n "${DISCOPANEL_TOKEN:-}" ]] || { echo 'DISCOPANEL_TOKEN is required' >&2; exit 2; }
[[ "$api_base" =~ ^https?://[A-Za-z0-9._:-]+$ && "$proxy_host" =~ ^[A-Za-z0-9._:-]+$ ]] \
  || { echo 'Unsafe acceptance endpoint' >&2; exit 2; }

case "$label" in
  1.7.10-forge) server_name='Jammarr 1.7.10 Forge Test'; expected_jar='cinemarr-1.0.0+mc1.7.10-forge.jar' ;;
  1.20.1-quilt) server_name='Jammarr 1.20.1 Quilt Test'; expected_jar='cinemarr-1.0.0+mc1.20.1-fabric.jar' ;;
  1.21.1-neoforge) server_name='Jammarr 1.21.1 NeoForge Test'; expected_jar='cinemarr-1.0.0+mc1.21.1-neoforge.jar' ;;
  26.2-fabric) server_name='Jammarr 26.2 Fabric Test'; expected_jar='cinemarr-1.0.0+mc26.2-fabric.jar' ;;
  *) echo "Usage: $0 {1.7.10-forge|1.20.1-quilt|1.21.1-neoforge|26.2-fabric}" >&2; exit 2 ;;
esac

for tool in curl jq base64 sha256sum python3; do command -v "$tool" >/dev/null || exit 2; done

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
update_overrides() { api_call discopanel.v1.ServerService/UpdateServer \
  "$(jq -cn --arg id "$server_id" --argjson value "$1" '{id:$id,dockerOverrides:$value,autoStart:false}')" >/dev/null; }
update_config() {
  local encoded
  # DiscPanel's protobuf schema models file content as bytes, so its JSON form
  # must be base64. Keep the cleartext configuration inside this process only.
  encoded=$(printf '%s' "$1" | base64 -w0)
  api_call discopanel.v1.FileService/UpdateFile \
    "$(jq -cn --arg id "$server_id" --arg value "$encoded" '{serverId:$id,path:"world/serverconfig/cinemarr-server.toml",content:$value}')" >/dev/null
}
wait_status() {
  local wanted=$1 deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do [[ $(get_server | jq -r '.server.status') == "$wanted" ]] && return 0; sleep 2; done
  return 1
}
send_command() {
  api_call discopanel.v1.ServerService/SendCommand \
    "$(jq -cn --arg id "$server_id" --arg command "$1" '{id:$id,command:$command,silent:false}')"
}
command_output() {
  local response
  response=$(send_command "$1")
  [[ $(jq -r '.success // false' <<<"$response") == true ]] || return 1
  jq -r '.output // empty' <<<"$response"
}
new_logs() {
  api_call discopanel.v1.ServerService/GetServerLogs \
    "$(jq -cn --arg id "$server_id" '{id:$id,tail:1000}')" \
    | jq -r --arg started "$started_at" '.logs[] | select(.timestamp >= $started) | .message'
}
wait_log() {
  local pattern=$1 timeout=$2 deadline
  deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do new_logs | grep -Eq "$pattern" && return 0; sleep 3; done
  return 1
}
wait_diagnostics() {
  local state=$1 timeout=$2 deadline value
  deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    value=$(command_output 'cinemarr diagnostics' 2>/dev/null || true)
    if [[ "$value" == *"Plex=$state;"* ]]; then printf '%s' "$value"; return 0; fi
    sleep 2
  done
  return 1
}
stop_server() {
  api_call discopanel.v1.ServerService/StopServer "$(jq -cn --arg id "$server_id" '{id:$id}')" >/dev/null
  wait_status SERVER_STATUS_STOPPED
}
start_server() {
  started_at=$(date -u +%Y-%m-%dT%H:%M:%S.%NZ)
  api_call discopanel.v1.ServerService/StartServer "$(jq -cn --arg id "$server_id" '{id:$id}')" >/dev/null
  remote_started=1; wait_status SERVER_STATUS_RUNNING
  wait_log 'Done \(|For help, type "help"' 300
}

work=$(mktemp -d "$repo_root/build/plex-recovery.${label}.XXXXXX")
proxy_control="$work/proxy.state"; proxy_port_file="$work/proxy.port"; proxy_log="$work/proxy.console.log"
proxy_pid=''; remote_started=0; remote_prepared=0; all_logs=''
cleanup_owner_pid=$BASHPID
cleanup() {
  local status=$?
  [[ $BASHPID == "$cleanup_owner_pid" ]] || return 0
  trap - EXIT INT TERM; set +e
  if (( remote_started )); then stop_server; fi
  if (( remote_prepared )); then update_config "$original_config"; update_overrides "$original_overrides"; fi
  if [[ -n "$proxy_pid" ]]; then kill -TERM "$proxy_pid" 2>/dev/null; wait "$proxy_pid" 2>/dev/null; fi
  # The proxy workspace contains only this gate's bounded control/port/log
  # files. Remove it on both success and failure so a credentialed acceptance
  # run never leaves an endpoint-bearing scratch directory behind.
  for file in "$proxy_control" "$proxy_port_file" "$proxy_log"; do
    [[ -f "$file" ]] && unlink -- "$file"
  done
  rmdir -- "$work" 2>/dev/null || true
  unset DISCOPANEL_TOKEN CINEMARR_PLEX_UPSTREAM plex_token
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

servers=$(api_call discopanel.v1.ServerService/ListServers '{}')
server_id=$(jq -r --arg name "$server_name" '.servers[] | select(.name==$name) | .id' <<<"$servers")
[[ -n "$server_id" ]] || { echo "$server_name not found" >&2; exit 1; }
server=$(get_server)
[[ $(jq -r '.server.status' <<<"$server") == SERVER_STATUS_STOPPED \
   && $(jq -r '.server.autoStart // false' <<<"$server") == false ]] \
  || { echo "$server_name must be stopped with autostart disabled" >&2; exit 1; }
original_overrides=$(jq -c '.server.dockerOverrides // {}' <<<"$server")
plex_token=$(jq -r '.server.dockerOverrides.environment.CINEMARR_PLEX_TOKEN // empty' <<<"$server")
[[ -n "$plex_token" ]] || { echo "$server_name has no process-local Plex credential" >&2; exit 1; }

config_response=$(api_call discopanel.v1.FileService/GetFile \
  "$(jq -cn --arg id "$server_id" '{serverId:$id,path:"world/serverconfig/cinemarr-server.toml"}')")
original_config=$(jq -r '.content' <<<"$config_response" | base64 -d)
plex_url=$(sed -n 's/^[[:space:]]*plexUrl[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$original_config" | head -n 1)
file_token=$(sed -n 's/^[[:space:]]*plexToken[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$original_config" | head -n 1)
[[ "$plex_url" =~ ^https?:// && -z "$file_token" ]] \
  || { echo "$server_name requires a URL and an empty file credential for this reversible gate" >&2; exit 1; }

mods=$(api_call discopanel.v1.ModService/ListMods "$(jq -cn --arg id "$server_id" '{serverId:$id}')")
active_name=$(jq -r '.mods[] | select(.enabled==true and (.fileName|ascii_downcase|startswith("cinemarr-") and endswith(".jar"))) | .fileName' <<<"$mods")
[[ "$active_name" == "$expected_jar" && -f "$repo_root/build/releases/$expected_jar" ]] \
  || { echo "$label has unexpected active or local artifact: ${active_name:-none}" >&2; exit 1; }
remote_sha=$(api_call discopanel.v1.FileService/GetFile \
  "$(jq -cn --arg id "$server_id" --arg path "mods/$active_name" '{serverId:$id,path:$path}')" \
  | jq -r '.content' | base64 -d | sha256sum | awk '{print $1}')
local_sha=$(sha256sum "$repo_root/build/releases/$expected_jar" | awk '{print $1}')
[[ "$remote_sha" == "$local_sha" ]] || { echo "$label artifact mismatch" >&2; exit 1; }

export CINEMARR_PLEX_UPSTREAM="$plex_url"
printf 'offline\n' > "$proxy_control"
python3 "$repo_root/scripts/plex-fault-proxy.py" --bind 0.0.0.0 --port-file "$proxy_port_file" \
  --control-file "$proxy_control" >"$proxy_log" 2>&1 &
proxy_pid=$!
deadline=$((SECONDS + 10)); while [[ ! -s "$proxy_port_file" ]]; do
  kill -0 "$proxy_pid" 2>/dev/null || { echo 'Plex fault proxy exited' >&2; exit 1; }
  (( SECONDS < deadline )) || exit 1; sleep 0.1
done
proxy_port=$(<"$proxy_port_file"); proxy_url="http://$proxy_host:$proxy_port"
proxy_config=$(awk -v value="$proxy_url" '
  /^[[:space:]]*plexUrl[[:space:]]*=/ { print "plexUrl = \"" value "\""; next } { print }
' <<<"$original_config")
update_config "$proxy_config"; remote_prepared=1

disabled_overrides=$(jq -c 'del(.environment.CINEMARR_PLEX_TOKEN)' <<<"$original_overrides")
update_overrides "$disabled_overrides"; start_server
disabled_diagnostics=$(wait_diagnostics disabled 60) \
  || { echo "$label did not expose Plex=disabled without its environment credential" >&2; exit 1; }
all_logs+=$'\n'"$(new_logs)"
stop_server; remote_started=0

update_overrides "$original_overrides"; printf 'offline\n' > "$proxy_control"; start_server
manual_degraded=$(wait_diagnostics degraded 90) \
  || { echo "$label did not enter degraded state during the controlled outage" >&2; exit 1; }
printf 'online\n' > "$proxy_control"
command_output 'cinemarr retry' >/dev/null \
  || { echo "$label operator retry command failed" >&2; exit 1; }
manual_ready=$(wait_diagnostics ready 90) \
  || { echo "$label did not recover after operator retry" >&2; exit 1; }
wait_log 'Validated [1-9][0-9]* allowed Plex video libraries after manual retry' 60 \
  || { echo "$label did not identify manual recovery" >&2; exit 1; }
all_logs+=$'\n'"$(new_logs)"
stop_server; remote_started=0

printf 'offline\n' > "$proxy_control"; start_server
automatic_degraded=$(wait_diagnostics degraded 90) \
  || { echo "$label did not re-enter degraded state for automatic retry" >&2; exit 1; }
printf 'online\n' > "$proxy_control"
automatic_ready=$(wait_diagnostics ready 90) \
  || { echo "$label did not recover through its bounded automatic retry" >&2; exit 1; }

all_logs+=$'\n'"$(new_logs)"
if grep -F -e "$plex_token" -e "$plex_url" -e "$proxy_url" -e 'X-Plex-Token' <<<"$all_logs" >/dev/null; then
  echo "$label recovery logs exposed a credential or Plex endpoint" >&2; exit 1
fi
stop_server; remote_started=0
update_config "$original_config"; update_overrides "$original_overrides"; remote_prepared=0

final=$(get_server)
[[ $(jq -r '.server.status' <<<"$final") == SERVER_STATUS_STOPPED \
   && $(jq -r '.server.autoStart // false' <<<"$final") == false \
   && $(jq -cS '.server.dockerOverrides // {}' <<<"$final") == $(jq -cS . <<<"$original_overrides") ]] || exit 1
restored=$(api_call discopanel.v1.FileService/GetFile \
  "$(jq -cn --arg id "$server_id" '{serverId:$id,path:"world/serverconfig/cinemarr-server.toml"}')" \
  | jq -r '.content' | base64 -d)
[[ "$restored" == "$original_config" ]] || exit 1

evidence_dir="$repo_root/build/discopanel-plex-recovery/$label"; mkdir -p "$evidence_dir"
{
  printf 'Artifact: %s\nSHA-256: %s\n' "$active_name" "$remote_sha"
  printf 'Disabled diagnostics: %s\n' "$disabled_diagnostics"
  printf 'Manual outage diagnostics: %s\nManual recovery diagnostics: %s\n' "$manual_degraded" "$manual_ready"
  printf 'Automatic outage diagnostics: %s\nAutomatic recovery diagnostics: %s\n' "$automatic_degraded" "$automatic_ready"
  printf 'Credential, upstream URL, proxy URL, and credential header were absent from captured server logs.\n'
  printf 'Server configuration/overrides restored; server stopped; autostart disabled.\n'
} > "$evidence_dir/$label.plex-recovery.evidence.txt"

echo "$label: disabled, degraded, manual retry, automatic retry, redaction, and teardown passed"
