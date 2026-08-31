#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
api_base=${DISCOPANEL_API_BASE:-}
[[ -n "${DISCOPANEL_TOKEN:-}" ]] || { echo "DISCOPANEL_TOKEN is required" >&2; exit 2; }
[[ "$api_base" =~ ^https?://[A-Za-z0-9._:-]+$ ]] \
  || { echo "DISCOPANEL_API_BASE is required and must be a safe HTTP(S) origin" >&2; exit 2; }
for tool in curl jq base64 sha256sum; do command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }; done

tmpdir=$(mktemp -d /tmp/cinemarr-acceptance-deploy.XXXXXX)
cleanup() {
  local status=${1:-$?}
  trap - EXIT
  find "$tmpdir" -maxdepth 1 -type f -delete 2>/dev/null || true
  rmdir "$tmpdir" 2>/dev/null || true
  unset DISCOPANEL_TOKEN
  exit "$status"
}
trap cleanup EXIT

api_call() {
  local endpoint=$1 body=$2 response status attempt
  for attempt in 1 2 3 4 5; do
    response=$(curl -sS -w $'\n%{http_code}' -H "Authorization: Bearer $DISCOPANEL_TOKEN" \
      -H 'Content-Type: application/json' --data-binary @- "$api_base/$endpoint" <<<"$body") || true
    status=${response##*$'\n'}; response=${response%$'\n'*}
    if [[ "$status" =~ ^2[0-9][0-9]$ ]]; then printf '%s' "$response"; return 0; fi
    sleep "$attempt"
  done
  echo "DiscPanel request failed for $endpoint (HTTP ${status:-unavailable})" >&2
  return 1
}

get_mods() {
  api_call discopanel.v1.ModService/ListMods "$(jq -cn --arg id "$1" '{serverId:$id}')"
}

file_sha() {
  api_call discopanel.v1.FileService/GetFile \
    "$(jq -cn --arg id "$1" --arg path "mods/$2" '{serverId:$id,path:$path}')" \
    | jq -r '.content' | base64 -d | sha256sum | awk '{print $1}'
}

upload_file() {
  local path=$1 filename=$2 size response session
  size=$(stat -c %s "$path")
  response=$(api_call discopanel.v1.UploadService/InitUpload \
    "$(jq -cn --arg filename "$filename" --arg size "$size" '{filename:$filename,totalSize:$size,chunkSize:"0"}')")
  session=$(jq -r '.sessionId' <<<"$response")
  [[ -n "$session" && "$session" != null ]] || { echo "No upload session for $filename" >&2; return 1; }
  response=$(curl -fsS -X PUT -H "Authorization: Bearer $DISCOPANEL_TOKEN" \
    -H 'Content-Type: application/octet-stream' --data-binary @"$path" "$api_base/api/v1/upload/$session")
  [[ $(jq -r '.completed // false' <<<"$response") == true ]] \
    || { echo "Upload did not complete for $filename" >&2; return 1; }
  printf '%s' "$session"
}

set_mod_enabled() {
  local server_id=$1 mod_id=$2 enabled=$3 display=$4 description=$5
  api_call discopanel.v1.ModService/UpdateMod \
    "$(jq -cn --arg sid "$server_id" --arg mid "$mod_id" --argjson enabled "$enabled" \
      --arg display "$display" --arg description "$description" \
      '{serverId:$sid,modId:$mid,enabled:$enabled,displayName:$display,description:$description}')" >/dev/null
}

restore_verified_backup() {
  local server_id=$1 backup_id=$2 backup_display=$3 failed_display=$4
  local mods mod_id enabled_count enabled_id
  mods=$(get_mods "$server_id") || return 1
  while IFS= read -r mod_id; do
    [[ -n "$mod_id" && "$mod_id" != "$backup_id" ]] || continue
    set_mod_enabled "$server_id" "$mod_id" false "$failed_display" \
      'Disabled after failed acceptance-artifact deployment' || return 1
  done < <(jq -r '.mods[]
    | select(.enabled == true)
    | select((.fileName | ascii_downcase | startswith("cinemarr-"))
        and (.fileName | ascii_downcase | endswith(".jar")))
    | .id
  ' <<<"$mods")
  set_mod_enabled "$server_id" "$backup_id" true "$backup_display" \
    'Automatic rollback after failed acceptance-artifact deployment' || return 1
  mods=$(get_mods "$server_id") || return 1
  enabled_count=$(jq '[.mods[]
    | select(.enabled == true)
    | select((.fileName | ascii_downcase | startswith("cinemarr-"))
        and (.fileName | ascii_downcase | endswith(".jar")))
  ] | length' <<<"$mods")
  enabled_id=$(jq -r '.mods[]
    | select(.enabled == true)
    | select((.fileName | ascii_downcase | startswith("cinemarr-"))
        and (.fileName | ascii_downcase | endswith(".jar")))
    | .id
  ' <<<"$mods")
  [[ "$enabled_count" == 1 && "$enabled_id" == "$backup_id" ]] || return 1
  echo 'Verified rollback artifact re-enabled after deployment failure.' >&2
}

rollback_after_error() {
  local status=${1:-$?}
  (( status != 0 )) || status=1
  trap - ERR
  set +e
  if ! restore_verified_backup "$server_id" "$backup_id" "$backup_display" "$active_display"; then
    echo "$server_name failed to restore its verified rollback; manual recovery is required" >&2
  fi
  cleanup "$status"
}

servers=$(api_call discopanel.v1.ServerService/ListServers '{}')
for spec in \
  "Jammarr 1.7.10 Forge Test|cinemarr-1.0.0+mc1.7.10-forge.jar|$repo_root/build/releases/cinemarr-1.0.0+mc1.7.10-forge.jar" \
  "Jammarr 1.20.1 Quilt Test|cinemarr-1.0.0+mc1.20.1-fabric.jar|$repo_root/build/releases/cinemarr-1.0.0+mc1.20.1-fabric.jar" \
  "Jammarr 1.21.1 NeoForge Test|cinemarr-1.0.0+mc1.21.1-neoforge.jar|$repo_root/build/releases/cinemarr-1.0.0+mc1.21.1-neoforge.jar" \
  "Jammarr 26.2 Fabric Test|cinemarr-1.0.0+mc26.2-fabric.jar|$repo_root/build/releases/cinemarr-1.0.0+mc26.2-fabric.jar"
do
  IFS='|' read -r server_name canonical local_jar <<<"$spec"
  [[ -f "$local_jar" ]] || { echo "Missing indexed artifact $local_jar" >&2; exit 1; }
  server_id=$(jq -r --arg name "$server_name" '.servers[]|select(.name==$name)|.id' <<<"$servers")
  [[ -n "$server_id" ]] || { echo "Missing DiscPanel server $server_name" >&2; exit 1; }
  server=$(api_call discopanel.v1.ServerService/GetServer "$(jq -cn --arg id "$server_id" '{id:$id}')")
  [[ $(jq -r '.server.status' <<<"$server") == SERVER_STATUS_STOPPED \
     && $(jq -r '.server.autoStart // false' <<<"$server") == false ]] \
    || { echo "$server_name must begin stopped with autostart disabled" >&2; exit 1; }

  mods=$(get_mods "$server_id")
  active_cinemarr_count=$(jq '[.mods[]
    | select(.enabled == true)
    | .fileName
    | ascii_downcase
    | select(startswith("cinemarr-") and endswith(".jar"))
  ] | length' <<<"$mods")
  [[ "$active_cinemarr_count" == 1 ]] || {
    echo "$server_name must have exactly one enabled Cinemarr artifact; found $active_cinemarr_count" >&2
    exit 1
  }
  active_id=$(jq -r --arg name "$canonical" '.mods[]|select(.fileName==$name and .enabled==true)|.id' <<<"$mods")
  active_display=$(jq -r --arg name "$canonical" '.mods[]|select(.fileName==$name and .enabled==true)|.displayName' <<<"$mods")
  [[ -n "$active_id" ]] || { echo "$server_name has no enabled canonical Cinemarr artifact" >&2; exit 1; }

  old_file="$tmpdir/old-$server_id.jar"
  api_call discopanel.v1.FileService/GetFile \
    "$(jq -cn --arg id "$server_id" --arg path "mods/$canonical" '{serverId:$id,path:$path}')" \
    | jq -r '.content' | base64 -d > "$old_file"
  old_sha=$(sha256sum "$old_file" | awk '{print $1}')
  new_sha=$(sha256sum "$local_jar" | awk '{print $1}')
  stem=${canonical%.jar}
  if [[ "$old_sha" == "$new_sha" ]]; then
    verified_rollback=''
    while IFS= read -r rollback; do
      [[ -n "$rollback" ]] || continue
      rollback_prefix=${rollback%.jar}
      rollback_prefix=${rollback_prefix##*.pre-final-}
      [[ "$rollback_prefix" =~ ^[0-9a-f]{12}$ ]] || continue
      rollback_sha=$(file_sha "$server_id" "$rollback")
      if [[ "$rollback_sha" == "$rollback_prefix"* ]]; then
        verified_rollback="$rollback $rollback_sha"
        break
      fi
    done < <(jq -r --arg prefix "$stem.pre-final-" '
      .mods[]
      | select(.enabled == false)
      | .fileName
      | select(startswith($prefix) and endswith(".jar"))
    ' <<<"$mods")
    [[ -n "$verified_rollback" ]] || {
      echo "$server_name has the exact artifact but no disabled hash-verified rollback" >&2
      exit 1
    }
    echo "$server_name: already has exact indexed artifact $new_sha; disabled rollback $verified_rollback"
    continue
  fi

  backup="$stem.pre-final-${old_sha:0:12}.jar"
  backup_display=${backup%.jar}
  if jq -e --arg name "$backup" '.mods[]|select(.fileName==$name)' <<<"$mods" >/dev/null; then
    backup_id=$(jq -r --arg name "$backup" '.mods[]|select(.fileName==$name)|.id' <<<"$mods")
  else
    echo "$server_name: uploading rollback $backup"
    if ! session=$(upload_file "$old_file" "$backup"); then
      echo "$server_name rollback upload failed; canonical artifact was not changed" >&2
      exit 1
    fi
    if ! api_call discopanel.v1.ModService/ImportUploadedMod \
      "$(jq -cn --arg sid "$server_id" --arg upload "$session" --arg display "$backup_display" \
        '{serverId:$sid,uploadSessionId:$upload,displayName:$display,description:"Rollback of immediately preceding Cinemarr acceptance artifact"}')" >/dev/null; then
      echo "$server_name rollback import failed; canonical artifact was not changed" >&2
      exit 1
    fi
    mods=$(get_mods "$server_id")
    backup_id=$(jq -r --arg name "$backup" '.mods[]|select(.fileName==$name)|.id' <<<"$mods")
  fi
  [[ -n "$backup_id" && $(file_sha "$server_id" "$backup") == "$old_sha" ]] \
    || { echo "$server_name rollback hash verification failed" >&2; exit 1; }
  set_mod_enabled "$server_id" "$backup_id" false "$backup_display" \
    'Rollback of immediately preceding Cinemarr acceptance artifact'
  [[ $(jq -r --arg name "$backup" '.mods[]|select(.fileName==$name)|.enabled // false' <<<"$(get_mods "$server_id")") == false ]] \
    || { echo "$server_name rollback remained enabled" >&2; exit 1; }

  echo "$server_name: replacing canonical artifact"
  api_call discopanel.v1.ModService/DeleteMod \
    "$(jq -cn --arg sid "$server_id" --arg mid "$active_id" '{serverId:$sid,modId:$mid}')" >/dev/null
  trap 'rollback_after_error $?' ERR
  if ! session=$(upload_file "$local_jar" "$canonical"); then
    echo "$server_name canonical upload failed; restoring verified rollback" >&2
    rollback_after_error 1
  fi
  if ! api_call discopanel.v1.ModService/ImportUploadedMod \
    "$(jq -cn --arg sid "$server_id" --arg upload "$session" --arg display "$active_display" \
      '{serverId:$sid,uploadSessionId:$upload,displayName:$display,description:"Exact indexed 1.0 acceptance artifact"}')" >/dev/null; then
    echo "$server_name canonical import failed; restoring verified rollback" >&2
    rollback_after_error 1
  fi
  mods=$(get_mods "$server_id")
  new_id=$(jq -r --arg name "$canonical" '.mods[]|select(.fileName==$name)|.id' <<<"$mods")
  if [[ -z "$new_id" ]]; then
    echo "$server_name imported artifact is missing" >&2
    rollback_after_error 1
  fi
  set_mod_enabled "$server_id" "$new_id" true "$active_display" \
    "Exact indexed 1.0 acceptance artifact; SHA-256 $new_sha"

  mods=$(get_mods "$server_id")
  enabled_count=$(jq '[.mods[]|select(.enabled==true and (.fileName|ascii_downcase|startswith("cinemarr-") and endswith(".jar")))]|length' <<<"$mods")
  if [[ "$enabled_count" != 1 \
     || $(jq -r '.mods[]|select(.enabled==true and (.fileName|ascii_downcase|startswith("cinemarr-") and endswith(".jar")))|.fileName' <<<"$mods") != "$canonical" \
     || $(file_sha "$server_id" "$canonical") != "$new_sha" ]]; then
    echo "$server_name final artifact verification failed" >&2
    rollback_after_error 1
  fi
  trap - ERR
  trap cleanup EXIT
  printf '%s: active %s %s; disabled rollback %s %s\n' \
    "$server_name" "$canonical" "$new_sha" "$backup" "$old_sha"
done
