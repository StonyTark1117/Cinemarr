#!/usr/bin/env bash
# Sourced by the managed real-Plex gate, which owns api_call/get_server and its
# fresh output root. Restore runs only after the server has stopped.
acceptance_weather_original=''
acceptance_weather_path='world/level.dat'
acceptance_weather_schema=modern
[[ "$label" != 1.7.10-forge ]] || acceptance_weather_schema=legacy
if [[ "$label" == 26.* ]]; then
  acceptance_weather_path='world/data/minecraft/weather.dat'
  acceptance_weather_schema=26
fi

read_acceptance_weather() {
  api_call discopanel.v1.FileService/GetFile \
    "$(jq -cn --arg id "$server_id" --arg path "$acceptance_weather_path" '{serverId:$id,path:$path}')"
}

write_acceptance_weather() {
  local prepared=$1 actual expected
  expected=$(jq -er '.content' <<<"$prepared") || return 1
  api_call discopanel.v1.FileService/UpdateFile \
    "$(jq -c --arg id "$server_id" --arg path "$acceptance_weather_path" \
      '{serverId:$id,path:$path,content:.content}' <<<"$prepared")" >/dev/null || return 1
  actual=$(read_acceptance_weather | jq -er '.content') || return 1
  [[ "$actual" == "$expected" ]]
}

prepare_acceptance_weather() {
  local current prepared receipt="$CINEMARR_GATE_OUTPUT_ROOT/$label.weather-state.json"
  [[ -z "$acceptance_weather_original" && ! -e "$receipt" ]] || return 1
  [[ $(get_server | jq -r '.server.status') == SERVER_STATUS_STOPPED ]] || return 1
  current=$(read_acceptance_weather) || return 1
  prepared=$(printf '%s' "$current" | python3 "$repo_root/scripts/acceptance-weather-state.py" \
    prepare "$acceptance_weather_schema") || return 1
  # Persist the numeric snapshot before the first remote write, including when
  # a successful update's response is lost. It also permits explicit recovery
  # after an interrupted harness without restoring an obsolete whole world.
  mkdir -p "$CINEMARR_GATE_OUTPUT_ROOT" || return 1
  jq --arg path "$acceptance_weather_path" \
    '{path:$path,original:.original,preparation:.receipt,restored:false}' \
    <<<"$prepared" > "$receipt" || return 1
  acceptance_weather_original=$(jq -c '.original' <<<"$prepared") || return 1
  write_acceptance_weather "$prepared"
}

restore_acceptance_weather() {
  [[ -n "$acceptance_weather_original" ]] || return 0
  local current restored receipt="$CINEMARR_GATE_OUTPUT_ROOT/$label.weather-state.json"
  [[ $(get_server | jq -r '.server.status') == SERVER_STATUS_STOPPED ]] || return 1
  current=$(read_acceptance_weather) || return 1
  restored=$(jq --argjson original "$acceptance_weather_original" '. + {original:$original}' \
    <<<"$current" | python3 "$repo_root/scripts/acceptance-weather-state.py" \
      restore "$acceptance_weather_schema") || return 1
  write_acceptance_weather "$restored" || return 1
  jq --argjson result "$(jq -c '.receipt' <<<"$restored")" \
    '. + {restored:true,restoration:$result}' "$receipt" > "$receipt.pending" || return 1
  mv "$receipt.pending" "$receipt" || return 1
  acceptance_weather_original=''
}
