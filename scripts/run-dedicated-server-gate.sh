#!/usr/bin/env bash
set -uo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
output_root=${CINEMARR_GATE_OUTPUT_ROOT:-"$repo_root/build/dedicated-server-gate"}
mkdir -p "$output_root"
gate_lock="${output_root}.lock"
exec 9>"$gate_lock"
if ! flock -n 9; then
  echo "Another Cinemarr dedicated-server gate is already using the shared runtime evidence directory" >&2
  exit 2
fi
fake_plex_token="cinemarr-dedicated-gate-token"
fake_plex_port_file="$output_root/fake-plex.port"
fake_plex_request_log="$output_root/fake-plex.requests.tsv"
fake_plex_audio="$output_root/fake-plex-tone.mp3"
fake_plex_video_dir=""
fake_plex_state="$output_root/fake-plex.state"
fake_audio_duration_seconds=${CINEMARR_GATE_AUDIO_DURATION_SECONDS:-600}
# Cold CI clients can spend close to a minute loading Minecraft and FFmpeg.
# Keep enough deterministic program runway for both clients to start and then
# satisfy the steady-playback window without colliding with natural EOS.
fake_video_duration_seconds=${CINEMARR_GATE_VIDEO_DURATION_SECONDS:-300}
fake_plex_pid=""
active_client_pid=""
active_server_pid=""
active_server_group=""
active_game_port=""
active_rcon_port=""
active_audio_client_pids=()
active_audio_recorder_pids=()
active_audio_modules=()
active_config=""
active_config_backup=""
active_config_existed=0
active_libraries=""
active_libraries_backup=""
active_libraries_existed=0
active_properties=""
active_properties_backup=""
active_cache_dir=""
active_cache_backup_root=""
active_cache_existed=0
active_world_dir=""
active_world_backup_root=""
active_world_existed=0

java21_home=${CINEMARR_JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk}
java26_home=${CINEMARR_JAVA26_HOME:-/usr/lib/jvm/java-26-openjdk}

targets=()
while IFS='|' read -r target_name target_path target_build_java target_port target_client_task target_server_task target_disable_cache; do
  case "$target_build_java" in
    21) target_java_home=$java21_home ;;
    26) target_java_home=$java26_home ;;
    *) echo "Target manifest selected unsupported build Java $target_build_java for $target_name" >&2; exit 2 ;;
  esac
  targets+=("$target_name|$target_path|$target_java_home|$target_port|$target_client_task|$target_server_task|$target_disable_cache")
done < <(python3 "$repo_root/scripts/target-matrix.py" gate-lines "$repo_root/gradle/targets.json")
if [[ ${#targets[@]} -ne 21 ]]; then
  echo "Target manifest must generate exactly 21 maintained runtimes; found ${#targets[@]}" >&2
  exit 2
fi

requested=${1:-all}
protocol_client_gate=${CINEMARR_PROTOCOL_CLIENT_GATE:-false}
active_client_task=runClient
active_server_task=runServer
active_disable_configuration_cache=false
command_client_gate=${CINEMARR_COMMAND_CLIENT_GATE:-false}
audio_client_gate=${CINEMARR_AUDIO_CLIENT_GATE:-false}
audio_scenario_gate=${CINEMARR_AUDIO_SCENARIO_GATE:-false}
video_client_gate=${CINEMARR_VIDEO_CLIENT_GATE:-false}
video_control_gate=${CINEMARR_VIDEO_CONTROL_GATE:-false}
video_adverse_network_gate=${CINEMARR_VIDEO_ADVERSE_NETWORK_GATE:-false}
video_follower_first_gate=${CINEMARR_VIDEO_FOLLOWER_FIRST_GATE:-false}
external_video_client_gate=${CINEMARR_EXTERNAL_VIDEO_CLIENT_GATE:-false}
live_plex_gate=${CINEMARR_LIVE_PLEX_GATE:-false}
acceptance_server_host=${CINEMARR_ACCEPTANCE_SERVER_HOST:-127.0.0.1}
video_decoder_backend=${CINEMARR_VIDEO_DECODER_BACKEND:-software}
video_decoder_device=${CINEMARR_VIDEO_DECODER_DEVICE:-}
video_decoder_expected_effective=${CINEMARR_VIDEO_DECODER_EXPECTED_EFFECTIVE:-$video_decoder_backend}
video_decoder_expect_fallback=${CINEMARR_VIDEO_DECODER_EXPECT_FALLBACK:-false}
fabric_loader_version=${CINEMARR_FABRIC_LOADER_VERSION:-}
quilt_modmenu_gate=${CINEMARR_QUILT_MODMENU_GATE:-false}

case "$video_decoder_backend" in
  software|auto|vaapi|qsv|cuda|d3d11va|dxva2) ;;
  *) echo "Unsupported CINEMARR_VIDEO_DECODER_BACKEND '$video_decoder_backend'" >&2; exit 2 ;;
esac
case "$video_decoder_expected_effective" in
  software|vaapi|qsv|cuda|d3d11va|dxva2) ;;
  *) echo "Unsupported CINEMARR_VIDEO_DECODER_EXPECTED_EFFECTIVE '$video_decoder_expected_effective'" >&2; exit 2 ;;
esac
if [[ "$video_decoder_device" == *$'\n'* || "$video_decoder_device" == *$'\r'* || "$video_decoder_device" == *'"'* ]]; then
  echo "CINEMARR_VIDEO_DECODER_DEVICE contains an unsafe TOML character" >&2
  exit 2
fi
if [[ "$video_decoder_expect_fallback" != true && "$video_decoder_expect_fallback" != false ]]; then
  echo "CINEMARR_VIDEO_DECODER_EXPECT_FALLBACK must be true or false" >&2
  exit 2
fi
if [[ "$external_video_client_gate" != true && "$external_video_client_gate" != false ]]; then
  echo "CINEMARR_EXTERNAL_VIDEO_CLIENT_GATE must be true or false" >&2
  exit 2
fi
if [[ "$video_control_gate" != true && "$video_control_gate" != false ]]; then
  echo "CINEMARR_VIDEO_CONTROL_GATE must be true or false" >&2
  exit 2
fi
if [[ "$video_control_gate" == true && "$video_client_gate" != true ]]; then
  echo "CINEMARR_VIDEO_CONTROL_GATE requires CINEMARR_VIDEO_CLIENT_GATE=true" >&2
  exit 2
fi
if [[ "$video_adverse_network_gate" != true && "$video_adverse_network_gate" != false ]]; then
  echo "CINEMARR_VIDEO_ADVERSE_NETWORK_GATE must be true or false" >&2
  exit 2
fi
if [[ "$video_adverse_network_gate" == true && "$video_client_gate" != true ]]; then
  echo "CINEMARR_VIDEO_ADVERSE_NETWORK_GATE requires CINEMARR_VIDEO_CLIENT_GATE=true" >&2
  exit 2
fi
if [[ "$video_adverse_network_gate" == true && "$live_plex_gate" == true ]]; then
  echo "CINEMARR_VIDEO_ADVERSE_NETWORK_GATE requires the deterministic fault-injection Plex service" >&2
  exit 2
fi
if [[ "$video_adverse_network_gate" == true && -z "${CINEMARR_GATE_VIDEO_DURATION_SECONDS+x}" ]]; then
  fake_video_duration_seconds=300
fi
if [[ "$video_control_gate" == true && -z "${CINEMARR_GATE_VIDEO_DURATION_SECONDS+x}" ]]; then
  # The control sequence deliberately waits for stable playback between pause,
  # resume, seek, stream replacement, and reconnect. Keep the deterministic
  # fixture alive long enough that those checks cannot run into its natural end.
  fake_video_duration_seconds=300
fi
if [[ "$external_video_client_gate" == true && "$video_client_gate" != true ]]; then
  echo "CINEMARR_EXTERNAL_VIDEO_CLIENT_GATE requires CINEMARR_VIDEO_CLIENT_GATE=true" >&2
  exit 2
fi
if [[ "$external_video_client_gate" == true && -z "${CINEMARR_GATE_VIDEO_DURATION_SECONDS+x}" ]]; then
  # A cold native Windows JVM may spend close to a minute loading FFmpeg,
  # probing the GPU, and preparing an older Minecraft workspace. Keep the
  # deterministic program alive long enough to assess steady playback.
  fake_video_duration_seconds=600
fi
if [[ "$live_plex_gate" != true && "$live_plex_gate" != false ]]; then
  echo "CINEMARR_LIVE_PLEX_GATE must be true or false" >&2
  exit 2
fi
if [[ "$live_plex_gate" == true ]]; then
  if [[ "$video_client_gate" != true ]]; then
    echo "CINEMARR_LIVE_PLEX_GATE requires CINEMARR_VIDEO_CLIENT_GATE=true" >&2
    exit 2
  fi
  if [[ "$requested" == all ]]; then
    echo "CINEMARR_LIVE_PLEX_GATE requires one explicit runtime target" >&2
    exit 2
  fi
  if [[ ! "${CINEMARR_PLEX_URL:-}" =~ ^https?:// ]] \
      || [[ "${CINEMARR_PLEX_URL:-}" == *$'\n'* \
      || "${CINEMARR_PLEX_URL:-}" == *$'\r'* \
      || "${CINEMARR_PLEX_URL:-}" == *'"'* ]]; then
    echo "CINEMARR_LIVE_PLEX_GATE requires a safe CINEMARR_PLEX_URL" >&2
    exit 2
  fi
  if [[ -z "${CINEMARR_PLEX_TOKEN:-}" ]]; then
    echo "CINEMARR_LIVE_PLEX_GATE requires CINEMARR_PLEX_TOKEN" >&2
    exit 2
  fi
  if [[ ! "${CINEMARR_LIVE_VIDEO_SECTION_ID:-}" =~ ^[0-9]+$ ]]; then
    echo "CINEMARR_LIVE_PLEX_GATE requires a numeric CINEMARR_LIVE_VIDEO_SECTION_ID" >&2
    exit 2
  fi
fi
if [[ ! "$acceptance_server_host" =~ ^[A-Za-z0-9._:-]+$ ]] \
    || [[ "$acceptance_server_host" == *$'\n'* || "$acceptance_server_host" == *$'\r'* ]]; then
  echo "CINEMARR_ACCEPTANCE_SERVER_HOST contains an unsafe server host" >&2
  exit 2
fi

restore_server_config() {
  if [[ -z "$active_config" ]]; then return; fi
  if (( active_config_existed )); then
    cp -- "$active_config_backup" "$active_config"
  else
    rm -f -- "$active_config"
  fi
  rm -f -- "$active_config_backup"
  active_config=""
  active_config_backup=""
  active_config_existed=0
  if [[ -n "$active_libraries" ]]; then
    if (( active_libraries_existed )); then
      cp -- "$active_libraries_backup" "$active_libraries"
    else
      rm -f -- "$active_libraries"
    fi
    rm -f -- "$active_libraries_backup"
    active_libraries=""
    active_libraries_backup=""
    active_libraries_existed=0
  fi
}

restore_server_properties() {
  if [[ -z "$active_properties" ]]; then return; fi
  cp -- "$active_properties_backup" "$active_properties"
  rm -f -- "$active_properties_backup"
  active_properties=""
  active_properties_backup=""
}

restore_audio_cache() {
  if [[ -z "$active_cache_dir" ]]; then return; fi
  if [[ -d "$active_cache_dir" ]]; then
    mv -- "$active_cache_dir" "$active_cache_backup_root/generated-cache"
  fi
  if (( active_cache_existed )); then
    mv -- "$active_cache_backup_root/original-cache" "$active_cache_dir"
  fi
  active_cache_dir=""
  active_cache_backup_root=""
  active_cache_existed=0
}

restore_gate_world() {
  if [[ -z "$active_world_dir" ]]; then return; fi
  if [[ -d "$active_world_dir" ]]; then
    mv -- "$active_world_dir" "$active_world_backup_root/generated-world"
  fi
  if (( active_world_existed )); then
    mv -- "$active_world_backup_root/original-world" "$active_world_dir"
  fi
  active_world_dir=""
  active_world_backup_root=""
  active_world_existed=0
}

isolate_gate_world() {
  local run_dir=$1
  local label=$2
  local level_name=$3
  active_world_dir="$run_dir/$level_name"
  active_world_backup_root=$(mktemp -d "$output_root/$label.world.XXXXXX")
  active_world_existed=0
  if [[ -d "$active_world_dir" ]]; then
    mv -- "$active_world_dir" "$active_world_backup_root/original-world"
    active_world_existed=1
  fi
}

isolate_audio_cache() {
  local run_dir=$1
  local label=$2
  active_cache_dir="$run_dir/cinemarr-cache"
  active_cache_backup_root=$(mktemp -d "$output_root/$label.audio-cache.XXXXXX")
  active_cache_existed=0
  if [[ -d "$active_cache_dir" ]]; then
    mv -- "$active_cache_dir" "$active_cache_backup_root/original-cache"
    active_cache_existed=1
  fi
}

cleanup_all() {
  cleanup_audio_processes
  if [[ -n "$active_client_pid" ]]; then
    terminate_client_launch "$active_client_pid" 10 || true
    active_client_pid=""
  fi
  if [[ -n "$active_server_pid" ]]; then
    stop_process_tree "$active_server_pid" TERM
    wait_for_process_tree_exit "$active_server_pid" 10 || stop_process_tree "$active_server_pid" KILL
    active_server_pid=""
  fi
  if [[ -n "$active_server_group" ]]; then
    stop_group "$active_server_group" TERM
    wait_for_group_exit "$active_server_group" 10 || stop_group "$active_server_group" KILL
    active_server_group=""
  fi
  active_game_port=""
  active_rcon_port=""
  restore_server_config
  restore_server_properties
  restore_audio_cache
  restore_gate_world
  if [[ -n "$fake_plex_pid" ]]; then
    kill "$fake_plex_pid" 2>/dev/null || true
    wait "$fake_plex_pid" 2>/dev/null || true
  fi
  rm -f -- "$fake_plex_port_file"
  rm -f -- "$fake_plex_state"
  if [[ -n "$fake_plex_video_dir" && -d "$fake_plex_video_dir" ]]; then
    rm -rf -- "$fake_plex_video_dir"
    fake_plex_video_dir=""
  fi
}

cleanup_audio_processes() {
  local pid module
  for pid in "${active_audio_client_pids[@]}"; do
    terminate_client_launch "$pid" 10 || true
  done
  for pid in "${active_audio_recorder_pids[@]}"; do
    kill -TERM "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done
  for module in "${active_audio_modules[@]}"; do
    pactl unload-module "$module" > /dev/null 2>&1 || true
  done
  active_audio_client_pids=()
  active_audio_recorder_pids=()
  active_audio_modules=()
}

trap cleanup_all EXIT
trap 'exit 130' INT TERM

start_fake_plex() {
  rm -f -- "$fake_plex_port_file"
  if [[ "$live_plex_gate" == true ]]; then
    : > "$fake_plex_request_log"
    printf 'online\n' > "$fake_plex_state"
    printf '0\n' > "$fake_plex_port_file"
    return 0
  fi
  local -a audio_args=()
  local -a video_args=()
  if [[ "$audio_client_gate" == "true" ]]; then
    if ! command -v ffmpeg > /dev/null || ! command -v pactl > /dev/null \
        || ! command -v parec > /dev/null; then
      echo "Two-client audio acceptance requires ffmpeg, pactl, and parec" >&2
      return 1
    fi
    if [[ ! "$fake_audio_duration_seconds" =~ ^[0-9]+$ ]] \
        || (( fake_audio_duration_seconds < 300 )); then
      echo "CINEMARR_GATE_AUDIO_DURATION_SECONDS must be an integer of at least 300" >&2
      return 1
    fi
    ffmpeg -hide_banner -loglevel error -y -f lavfi \
      -i "sine=frequency=997:sample_rate=44100:duration=${fake_audio_duration_seconds}" -ac 2 \
      -codec:a libmp3lame -b:a 160k -write_xing 0 "$fake_plex_audio" || return 1
    audio_args+=(--audio-file "$fake_plex_audio" \
      --track-duration-ms "$((fake_audio_duration_seconds * 1000))")
  fi
  if [[ "$video_client_gate" == "true" ]]; then
    if ! command -v ffmpeg > /dev/null || ! command -v pactl > /dev/null \
        || ! command -v parec > /dev/null; then
      echo "Two-client video acceptance requires ffmpeg, pactl, and parec" >&2
      return 1
    fi
    if [[ ! "$fake_video_duration_seconds" =~ ^[0-9]+$ ]] \
        || (( fake_video_duration_seconds < 60 )); then
      echo "CINEMARR_GATE_VIDEO_DURATION_SECONDS must be an integer of at least 60" >&2
      return 1
    fi
    fake_plex_video_dir=$(mktemp -d "$output_root/fake-plex-video.XXXXXX")
    ffmpeg -hide_banner -loglevel error -y \
      -f lavfi -i "testsrc2=size=160x90:rate=5:duration=${fake_video_duration_seconds}" \
      -f lavfi -i "sine=frequency=997:sample_rate=48000:duration=${fake_video_duration_seconds}" \
      -filter_complex "[1:a]volume='if(lt(mod(t,3),2.25),1.4,0.3)':eval=frame[a]" \
      -map 0:v -map '[a]' -c:v libx264 -preset ultrafast -tune zerolatency \
      -profile:v baseline -pix_fmt yuv420p -g 5 -keyint_min 5 -sc_threshold 0 \
      -c:a aac -b:a 128k -ac 2 -ar 48000 -f hls -hls_time 1 -hls_playlist_type vod \
      -hls_segment_filename "$fake_plex_video_dir/segment%03d.ts" \
      "$fake_plex_video_dir/media.m3u8" || return 1
    video_args+=(--video-directory "$fake_plex_video_dir" \
      --track-duration-ms "$((fake_video_duration_seconds * 1000))")
  fi
  python3 "$repo_root/scripts/fake-plex-server.py" \
    --port-file "$fake_plex_port_file" --request-log "$fake_plex_request_log" \
    --token "$fake_plex_token" --state-file "$fake_plex_state" "${audio_args[@]}" "${video_args[@]}" &
  fake_plex_pid=$!
  local deadline=$((SECONDS + 10))
  while [[ ! -s "$fake_plex_port_file" ]]; do
    if ! kill -0 "$fake_plex_pid" 2>/dev/null; then
      echo "Fake Plex service exited before publishing its port" >&2
      return 1
    fi
    if (( SECONDS >= deadline )); then
      echo "Fake Plex service did not become ready" >&2
      return 1
    fi
    sleep 0.1
  done
}

fake_plex_requests_complete() {
  if [[ "$live_plex_gate" == true ]]; then return 0; fi
  local first_line=$1
  awk -F '\t' -v first="$first_line" -v token="$fake_plex_token" '
    NR > first && $3 == token && $2 == "/library/sections" { sections = 1 }
    END { exit !sections }
  ' "$fake_plex_request_log"
}

missing_client_rejection_logged() {
  local latest_log=$1
  local console_log=$2
  grep -Eiq 'Cinemarr is required on the client|Cinemarr protocol (handshake )?timed out|This server requires Fabric Loader and Fabric API installed on your client|Disconnecting VANILLA connection attempt:.*require (Forge|NeoForge)|incompatible.*Cinemarr' \
    "$latest_log" "$console_log" 2>/dev/null
}

client_rejection_logged() {
  local console_log=$1
  local rejection=$2
  local allow_generic=$3
  grep -Fq "Client disconnected with reason: $rejection" "$console_log" 2>/dev/null \
    || { [[ "$allow_generic" == true ]] \
      && grep -Fq 'Client disconnected with reason: Disconnected' "$console_log" 2>/dev/null; }
}

rejection_observed() {
  local server_console=$1
  local client_console=$2
  local rejection=$3
  local allow_generic=$4
  grep -Fq "Client disconnected with reason: $rejection" "$client_console" 2>/dev/null \
    || { grep -Fq "$rejection" "$server_console" 2>/dev/null \
      && client_rejection_logged "$client_console" "$rejection" "$allow_generic"; }
}

client_bootstrap_failed() {
  local console_log=$1
  grep -Eq 'Timed out trying to setup the Game Window|Failed to initialize the mod loading system and display|ArrayIndexOutOfBoundsException: 0' \
    "$console_log" 2>/dev/null
}

run_wrong_protocol_client() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local server_console=$5
  local attempt
  for attempt in 1 2; do
    if run_acceptance_client "$label" "$target_dir" "$java_home" "$port" "$server_console" \
        wrong-protocol-client CinemarrMismatch \
        '-Dcinemarr.acceptance.enabled=true -Dcinemarr.acceptance.clientProtocol=4 -Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true' \
        'Cinemarr protocol mismatch: server requires' true; then
      return 0
    fi
    if (( attempt == 1 )); then
      echo "$label: retrying the wrong-protocol client once after a failed headless launch" >&2
    fi
  done
  return 1
}

run_missing_hello_client() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local server_console=$5
  run_acceptance_client "$label" "$target_dir" "$java_home" "$port" "$server_console" \
    missing-client CinemarrMissing \
    '-Dcinemarr.acceptance.enabled=true -Dcinemarr.acceptance.suppressClientHello=true -Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true' \
    'Cinemarr protocol handshake timed out'
}

run_acceptance_client() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local server_console=$5
  local scenario=$6
  local username=$7
  local java_tool_options=$8
  local rejection=$9
  local allow_generic_client_rejection=${10:-false}
  local client_dir="$output_root/$label.$scenario"
  local client_console="$output_root/$label.$scenario.console.log"
  local evidence="$output_root/$label.$scenario.server.txt"
  local pid deadline exit_grace_deadline result=0
  local -a runtime_args=()
  [[ "$label" == *-quilt ]] && runtime_args+=(-PcinemarrRuntimeLoader=quilt)
  [[ "$label" == *-quilt && "$quilt_modmenu_gate" == true ]] && runtime_args+=(-PcinemarrIncludeModMenu=true)
  [[ "$label" == *-fabric && -n "$fabric_loader_version" ]] && runtime_args+=(-PcinemarrFabricLoaderVersion="$fabric_loader_version")

  mkdir -p "$client_dir"
  : > "$client_console"
  printf '%s\n' \
    'onboardAccessibility:false' \
    'skipMultiplayerWarning:true' \
    'joinedFirstServer:true' \
    'narrator:0' > "$client_dir/options.txt"
  (
    cd "$target_dir" || exit 1
    exec setsid env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 \
      xvfb-run -a -s '-screen 0 1280x720x24 -ac +extension GLX +render -noreset' env \
      JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
      JAVA_TOOL_OPTIONS="$java_tool_options" \
      LIBGL_ALWAYS_SOFTWARE=1 \
      ./gradlew "$active_client_task" --no-daemon --max-workers=1 --console=plain \
      "${runtime_args[@]}" \
      -PcinemarrAcceptanceUsername="$username" \
      -PcinemarrAcceptanceServer="${acceptance_server_host}:${port}" \
      -PcinemarrAcceptanceGameDir="$client_dir" \
      > "$client_console" 2>&1
  ) &
  pid=$!
  active_client_pid=$pid

  deadline=$((SECONDS + 600))
  while ! rejection_observed "$server_console" "$client_console" "$rejection" \
      "$allow_generic_client_rejection"; do
    if client_bootstrap_failed "$client_console"; then
      echo "$label: $scenario could not initialize its headless display; see $client_console" >&2
      result=1
      break
    fi
    if ! group_alive "$pid"; then
      # The cold Forge 1.7.10 client can terminate immediately after receiving
      # the disconnect while its client and server log writers are still
      # flushing. Give the exact client reason plus the server-side disconnect
      # a short bounded grace period; a genuine launcher crash still fails.
      exit_grace_deadline=$((SECONDS + 60))
      while (( SECONDS < exit_grace_deadline )); do
        if rejection_observed "$server_console" "$client_console" "$rejection" \
            "$allow_generic_client_rejection"; then
          break 2
        fi
        sleep 1
      done
      echo "$label: $scenario exited before the server rejected it; see $client_console" >&2
      result=1
      break
    fi
    if (( SECONDS >= deadline )); then
      echo "$label: $scenario was not rejected within 600 seconds; see $client_console" >&2
      result=1
      break
    fi
    sleep 1
  done

  if (( result == 0 )); then
    {
      grep -F -e "$rejection" -e "$username lost connection:" -e "$username left the game" \
        "$server_console" | tail -n 1
      grep -F -e "Client disconnected with reason: $rejection" \
        -e 'Client disconnected with reason: Disconnected' "$client_console" | tail -n 1
    } > "$evidence"
  fi
  terminate_client_launch "$pid" 20 || result=1
  active_client_pid=""
  return "$result"
}

run_command_client() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local server_console=$5
  local rcon_port=$6
  local rcon_password=$7
  local fifo_fd=$8
  local scenario=command-client username=CinemarrCommand
  local client_dir="$output_root/$label.$scenario"
  local client_console="$output_root/$label.$scenario.console.log"
  local diagnostics="$output_root/$label.$scenario.diagnostics.txt"
  local evidence="$output_root/$label.$scenario.evidence.txt"
  local pid deadline result=0
  local -a runtime_args=()
  [[ "$label" == *-quilt ]] && runtime_args+=(-PcinemarrRuntimeLoader=quilt)
  [[ "$label" == *-quilt && "$quilt_modmenu_gate" == true ]] && runtime_args+=(-PcinemarrIncludeModMenu=true)
  [[ "$label" == *-fabric && -n "$fabric_loader_version" ]] && runtime_args+=(-PcinemarrFabricLoaderVersion="$fabric_loader_version")

  mkdir -p "$client_dir"
  : > "$client_console"
  : > "$diagnostics"
  printf '%s\n' \
    'onboardAccessibility:false' \
    'skipMultiplayerWarning:true' \
    'joinedFirstServer:true' \
    'narrator:0' > "$client_dir/options.txt"

  if [[ "$label" == "1.7.10-forge" ]]; then
    printf 'deop %s\n' "$username" >&"$fifo_fd"
  elif ! python3 "$repo_root/scripts/minecraft-rcon.py" 127.0.0.1 "$rcon_port" "$rcon_password" \
      "deop $username" > /dev/null 2>&1; then
    # A never-before-seen player is not in ops.json, which some versions report
    # as a command failure. The real non-operator command tree below is authority.
    true
  fi

  (
    cd "$target_dir" || exit 1
    exec setsid env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 \
      xvfb-run -a -s '-screen 0 1280x720x24 -ac +extension GLX +render -noreset' env \
      JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
      JAVA_TOOL_OPTIONS='-Dcinemarr.acceptance.enabled=true -Dcinemarr.acceptance.commandProbe=true -Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true' \
      LIBGL_ALWAYS_SOFTWARE=1 \
      ./gradlew "$active_client_task" --no-daemon --max-workers=1 --console=plain \
      "${runtime_args[@]}" \
      -PcinemarrAcceptanceUsername="$username" \
      -PcinemarrAcceptanceServer="${acceptance_server_host}:${port}" \
      -PcinemarrAcceptanceGameDir="$client_dir" \
      > "$client_console" 2>&1
  ) &
  pid=$!
  active_client_pid=$pid

  deadline=$((SECONDS + 600))
  if [[ "$label" == "1.7.10-forge" ]]; then
    while ! grep -Fq 'Acceptance command response: Cinemarr video:' "$client_console" 2>/dev/null \
        || ! grep -Fq 'Acceptance command response: Operator permission is required' "$client_console" 2>/dev/null; do
      if ! group_alive "$pid" || (( SECONDS >= deadline )); then
        echo "$label: legacy non-operator command responses were not observed; see $client_console" >&2
        result=1
        break
      fi
      sleep 1
    done
  else
    while ! grep -Fq 'Acceptance command permissions: non-operator public=true operator=false' \
        "$client_console" 2>/dev/null; do
      if client_bootstrap_failed "$client_console"; then
        echo "$label: command client could not initialize its headless display; see $client_console" >&2
        result=1
        break
      fi
      if ! group_alive "$pid" || (( SECONDS >= deadline )); then
        echo "$label: non-operator command tree was not observed; see $client_console" >&2
        result=1
        break
      fi
      sleep 1
    done
  fi

  if (( result == 0 )); then
    if [[ "$label" == "1.7.10-forge" ]]; then
      printf 'op %s\n' "$username" >&"$fifo_fd"
      sleep 1
      printf 'tell %s CINEMARR_ACCEPTANCE_OPERATOR_READY\n' "$username" >&"$fifo_fd"
      deadline=$((SECONDS + 60))
      while ! grep -Fq 'Acceptance command response: Plex=' "$client_console" 2>/dev/null; do
        if ! group_alive "$pid" || (( SECONDS >= deadline )); then
          echo "$label: operator diagnostics response was not observed; see $client_console" >&2
          result=1
          break
        fi
        sleep 1
      done
      if (( result == 0 )); then
        grep -F 'Acceptance command response:' "$client_console" > "$diagnostics"
      fi
      printf 'deop %s\n' "$username" >&"$fifo_fd"
    else
      if ! python3 "$repo_root/scripts/minecraft-rcon.py" 127.0.0.1 "$rcon_port" "$rcon_password" \
          "op $username" > /dev/null; then
        echo "$label: unable to promote the real command-probe client" >&2
        result=1
      fi
      deadline=$((SECONDS + 60))
      while (( result == 0 )) && ! grep -Fq \
          'Acceptance command permissions: operator public=true operator=true' "$client_console" 2>/dev/null; do
        if ! group_alive "$pid" || (( SECONDS >= deadline )); then
          echo "$label: operator command tree was not observed; see $client_console" >&2
          result=1
          break
        fi
        sleep 1
      done
      if (( result == 0 )); then
        deadline=$((SECONDS + 30))
        while ! grep -Fq '[CHAT] Plex=' "$client_console" 2>/dev/null; do
          if ! group_alive "$pid" || (( SECONDS >= deadline )); then
            echo "$label: real operator client did not receive sanitized /cinemarr diagnostics output" >&2
            result=1
            break
          fi
          sleep 1
        done
      fi
      if (( result == 0 )); then
        grep -F '[CHAT] Plex=' "$client_console" | tail -n 1 > "$diagnostics"
        if ! python3 "$repo_root/scripts/minecraft-rcon.py" 127.0.0.1 "$rcon_port" "$rcon_password" \
            'cinemarr diagnostics' >> "$diagnostics"; then
          echo "$label: diagnostics command failed over authenticated server administration" >&2
          result=1
        fi
      fi
      python3 "$repo_root/scripts/minecraft-rcon.py" 127.0.0.1 "$rcon_port" "$rcon_password" \
        "deop $username" > /dev/null 2>&1 || true
    fi
  fi

  if (( result == 0 )) && ! grep -Fq 'Plex=' "$diagnostics"; then
    echo "$label: diagnostics output is missing the expected sanitized health summary" >&2
    result=1
  fi
  if grep -Fq "$fake_plex_token" "$diagnostics" \
      || { [[ "$live_plex_gate" == true ]] \
        && grep -Fq "$CINEMARR_PLEX_TOKEN" "$diagnostics"; } \
      || grep -Eq 'https?://|127\.0\.0\.1|localhost|X-Plex-Token' "$diagnostics"; then
    echo "$label: player/operator diagnostics exposed a credential or server address" >&2
    result=1
  fi
  if (( result == 0 )); then
    {
      grep -E 'Acceptance command permissions:|Acceptance command response: (Cinemarr video:|Operator permission is required|Plex=)' \
        "$client_console" || true
      cat "$diagnostics"
    } > "$evidence"
  fi

  terminate_client_launch "$pid" 20 || result=1
  active_client_pid=""
  return "$result"
}

started_audio_client_pid=""
ready_audio_client_pid=""

start_audio_client() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local role=$5
  local username=$6
  local sink=$7
  local pcm_type=${CINEMARR_ALSA_PCM_TYPE:-pipewire}
  local client_dir="$output_root/$label.audio-$role"
  local client_console="$output_root/$label.audio-$role.console.log"
  local control_file="$output_root/$label.audio-$role.control"
  local leader=false
  local xvfb_geometry=1280x720x24
  local java_options='-Dcinemarr.acceptance.enabled=true -Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true'
  local -a cache_args=()
  local -a runtime_args=()
  [[ "$label" == *-quilt ]] && runtime_args+=(-PcinemarrRuntimeLoader=quilt)
  [[ "$label" == *-quilt && "$quilt_modmenu_gate" == true ]] && runtime_args+=(-PcinemarrIncludeModMenu=true)
  [[ "$label" == *-fabric && -n "$fabric_loader_version" ]] && runtime_args+=(-PcinemarrFabricLoaderVersion="$fabric_loader_version")
  [[ "$role" == "leader" ]] && leader=true
  if [[ "$audio_client_gate" == "true" ]]; then
    java_options+=" -Dcinemarr.acceptance.audioProbe=true -Dcinemarr.acceptance.audioLeader=$leader -Dcinemarr.acceptance.audioControlFile=$control_file"
  fi
  if [[ "$video_client_gate" == "true" ]]; then
    java_options+=" -Dcinemarr.acceptance.videoProbe=true -Dcinemarr.acceptance.videoLeader=$leader -Dcinemarr.acceptance.audioControlFile=$control_file"
  fi
  [[ "$active_disable_configuration_cache" == true ]] && cache_args+=(--no-configuration-cache)

  mkdir -p "$client_dir/config"
  : > "$client_console"
  : > "$control_file"
  if [[ "$video_client_gate" == "true" ]]; then
    rm -f -- "$client_dir/screenshots/cinemarr-video-acceptance.png"
  fi
  printf '%s\n' \
    'onboardAccessibility:false' \
    'skipMultiplayerWarning:true' \
    'joinedFirstServer:true' \
    'narrator:0' \
    'guiScale:1' \
    'soundCategory_master:1.0' \
    'soundCategory_music:0.0' \
    'soundCategory_weather:0.0' \
    'soundCategory_block:0.0' \
    'soundCategory_hostile:0.0' \
    'soundCategory_neutral:0.0' \
    'soundCategory_player:0.0' \
    'soundCategory_ambient:0.0' \
    'soundCategory_voice:0.0' \
    'soundCategory_record:1.0' > "$client_dir/options.txt"
  printf '%s\n' '# Generated by the two-client audio acceptance gate.' \
    'enabled = true' 'volume = 1.0' \
    "videoDecoderBackend = \"$video_decoder_backend\"" \
    "videoDecoderDevice = \"$video_decoder_device\"" > "$client_dir/config/cinemarr-client.toml"
  case "$pcm_type" in
    pipewire)
      printf '%s\n' \
        'pcm.!default {' \
        '  type pipewire' \
        "  playback_node \"$sink\"" \
        '}' \
        'ctl.!default {' \
        '  type pipewire' \
        '}' > "$client_dir/alsa.conf"
      ;;
    pulse)
      printf '%s\n' \
        'pcm.!default {' \
        '  type pulse' \
        "  device \"$sink\"" \
        '}' \
        'ctl.!default {' \
        '  type pulse' \
        '}' > "$client_dir/alsa.conf"
      ;;
    *)
      echo "Unsupported CINEMARR_ALSA_PCM_TYPE '$pcm_type'" >&2
      return 1
      ;;
  esac
  # Give OpenAL Soft enough mix-ahead to keep two software-rendered clients
  # moving at the same device rate when a hosted runner is briefly CPU-bound.
  # The sink monitors still measure real output and the sync gate still applies
  # its 150 ms physical lag limit; this only prevents backend mixer starvation.
  printf '%s\n' \
    '[general]' \
    'period_size = 512' \
    'periods = 8' > "$client_dir/alsoft.conf"
  if [[ "$video_client_gate" == "true" && "$role" == "follower" \
      && "${CINEMARR_VIDEO_FOLLOWER_SMALL_WINDOW:-false}" == "true" ]]; then
    xvfb_geometry=640x360x24
    runtime_args+=(-PcinemarrAcceptanceWidth=640 -PcinemarrAcceptanceHeight=360)
  fi
  (
    cd "$target_dir" || exit 1
    exec setsid env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 \
      xvfb-run -a -s "-screen 0 $xvfb_geometry -ac +extension GLX +render -noreset" env \
      JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
      JAVA_TOOL_OPTIONS="$java_options" \
      ALSA_CONFIG_PATH="$client_dir/alsa.conf" ALSOFT_CONF="$client_dir/alsoft.conf" \
      ALSOFT_DRIVERS=alsa LIBGL_ALWAYS_SOFTWARE=1 \
      ./gradlew "$active_client_task" --no-daemon --max-workers=1 --console=plain "${cache_args[@]}" \
      "${runtime_args[@]}" \
      -PcinemarrAcceptanceUsername="$username" \
      -PcinemarrAcceptanceServer="${acceptance_server_host}:${port}" \
      -PcinemarrAcceptanceGameDir="$client_dir" \
      > "$client_console" 2>&1
  ) &
  started_audio_client_pid=$!
  active_audio_client_pids+=("$started_audio_client_pid")
}

wait_for_audio_playing() {
  local label=$1
  local role=$2
  local pid=$3
  local client_console="$output_root/$label.audio-$role.console.log"
  local initialized=0
  local initialization_deadline=$((SECONDS + 180))
  local deadline=$((SECONDS + 600))
  local ready_marker='Acceptance audio state: PLAYING'
  [[ "$video_client_gate" == "true" ]] && ready_marker='Acceptance video ready:'
  while ! grep -Fq "$ready_marker" "$client_console" 2>/dev/null; do
    if grep -Eq 'Acceptance (audio state:|video session:|video libraries:)' "$client_console" 2>/dev/null; then initialized=1; fi
    if client_bootstrap_failed "$client_console"; then
      echo "$label: $role client could not initialize its headless display; see $client_console" >&2
      return 1
    fi
    if grep -Eq 'Acceptance audio state: ERROR|Cinemarr rejected video segment|Client disconnected with reason:|Connection reset by peer|Couldn.t connect to server|Failed to open OpenAL device|Error starting SoundSystem|NoClassDefFoundError: (javazoom|de/sciss)' \
        "$client_console" 2>/dev/null; then
      echo "$label: $role client failed before playback; see $client_console" >&2
      return 2
    fi
    if ! group_alive "$pid"; then
      echo "$label: $role client did not reach the real-client acceptance-ready state; see $client_console" >&2
      (( initialized == 0 )) && return 1 || return 2
    fi
    if (( initialized == 0 && SECONDS >= initialization_deadline )); then
      echo "$label: $role client did not initialize Cinemarr within 180 seconds; see $client_console" >&2
      return 1
    fi
    if (( SECONDS >= deadline )); then
      echo "$label: $role client initialized but did not reach the real-client acceptance-ready state; see $client_console" >&2
      return 2
    fi
    sleep 1
  done
}

wait_for_video_audio_pair_stable() {
  local label=$1
  local leader_pid=$2
  local follower_pid=$3
  local leader_log="$output_root/$label.audio-leader.console.log"
  local follower_log="$output_root/$label.audio-follower.console.log"
  local deadline=$((SECONDS + 180))
  local stable_since=$SECONDS
  local signature="" previous_signature=""
  local leader_event follower_event leader_timeline follower_timeline
  local event_pattern='Acceptance video audio (scheduled|rebuffer):'
  local timeline_marker='Acceptance video audio timeline:'
  local buffer_pattern='javaBufferMs=[1-9][0-9]*'
  if [[ "$label" == "1.7.10-forge" ]]; then
    event_pattern='Acceptance legacy video audio (scheduled|rebuffer):'
    timeline_marker='Acceptance legacy video audio timeline:'
    buffer_pattern='pendingFrames=[1-9][0-9]*'
  fi

  while (( SECONDS < deadline )); do
    if ! group_alive "$leader_pid" || ! group_alive "$follower_pid"; then
      echo "$label: a video client exited while its audio backend was stabilizing" >&2
      return 1
    fi
    if grep -Eq 'Video segment request exceeds playback lead limit|Cinemarr rejected video segment' \
        "$leader_log" "$follower_log" 2>/dev/null; then
      echo "$label: a video client hit a rejected segment request during A/V stabilization" >&2
      return 1
    fi
    leader_event=$(grep -nE "$event_pattern" "$leader_log" 2>/dev/null | tail -n 1)
    follower_event=$(grep -nE "$event_pattern" "$follower_log" 2>/dev/null | tail -n 1)
    signature="$leader_event|$follower_event"
    if [[ "$signature" != "$previous_signature" ]]; then
      previous_signature=$signature
      stable_since=$SECONDS
    fi
    leader_timeline=$(grep -F "$timeline_marker" "$leader_log" 2>/dev/null | tail -n 1)
    follower_timeline=$(grep -F "$timeline_marker" "$follower_log" 2>/dev/null | tail -n 1)
    if [[ "$leader_event" == *'audio scheduled:'* && "$follower_event" == *'audio scheduled:'* \
        && "$leader_timeline" == *'underruns=0'* && "$follower_timeline" == *'underruns=0'* \
        && "$leader_timeline" =~ $buffer_pattern \
        && "$follower_timeline" =~ $buffer_pattern ]] \
        && video_audio_timeline_within_bounds "$leader_timeline" \
        && video_audio_timeline_within_bounds "$follower_timeline"; then
      if (( SECONDS - stable_since >= 8 )); then return 0; fi
    else
      stable_since=$SECONDS
    fi
    sleep 1
  done
  echo "$label: video audio did not remain rebuffer-free and within A/V timeline bounds on both real clients for 8 seconds" >&2
  [[ -n "$leader_timeline" ]] && echo "$label: latest leader timeline: $leader_timeline" >&2
  [[ -n "$follower_timeline" ]] && echo "$label: latest follower timeline: $follower_timeline" >&2
  return 1
}

video_audio_timeline_within_bounds() {
  local timeline=$1
  local target_ms video_ms audio_drift_ms video_drift_ms
  if [[ "$timeline" =~ targetMs=(-?[0-9]+) ]]; then target_ms=${BASH_REMATCH[1]}; else return 1; fi
  if [[ "$timeline" =~ videoMs=(-?[0-9]+) ]]; then video_ms=${BASH_REMATCH[1]}; else return 1; fi
  if [[ "$timeline" =~ driftMs=(-?[0-9]+) ]]; then audio_drift_ms=${BASH_REMATCH[1]}; else return 1; fi
  (( video_ms > 0 )) || return 1
  video_drift_ms=$((video_ms - target_ms))
  (( audio_drift_ms >= -150 && audio_drift_ms <= 150 \
      && video_drift_ms >= -250 && video_drift_ms <= 250 ))
}

launch_audio_client() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local role=$5
  local username=$6
  local sink=$7
  local attempt pid status existing
  local -a remaining=()

  ready_audio_client_pid=""
  for attempt in 1 2; do
    start_audio_client "$label" "$target_dir" "$java_home" "$port" "$role" "$username" "$sink"
    pid=$started_audio_client_pid
    wait_for_audio_playing "$label" "$role" "$pid"
    status=$?
    if (( status == 0 )); then
      ready_audio_client_pid=$pid
      return 0
    fi
    if (( attempt == 2 )); then return 1; fi

    # Headless OpenAL and the client bootstrap can fail transiently on a loaded
    # hosted runner. Retry the complete clean client launch once, but still
    # require the replacement process to reach real Cinemarr PLAYING state.
    echo "$label: retrying $role client once after a pre-playback failure" >&2
    terminate_client_launch "$pid" 20 || return 1
    remaining=()
    for existing in "${active_audio_client_pids[@]}"; do
      if [[ "$existing" != "$pid" ]]; then remaining+=("$existing"); fi
    done
    active_audio_client_pids=("${remaining[@]}")
  done
  return 1
}

audio_capture_is_audible() {
  local raw=$1
  local metrics=$2
  local mean samples
  if [[ "$live_plex_gate" == true ]]; then
    ffmpeg -hide_banner -loglevel info -f s16le -ar 48000 -ac 2 -i "$raw" \
      -af 'silenceremove=start_periods=1:start_duration=0.5:start_threshold=-55dB:stop_periods=-1:stop_duration=1:stop_threshold=-55dB,volumedetect' \
      -f null - > /dev/null 2> "$metrics" || return 1
  else
    ffmpeg -hide_banner -loglevel info -f s16le -ar 48000 -ac 2 -i "$raw" \
      -af 'highpass=f=970,lowpass=f=1025,silenceremove=start_periods=1:start_duration=1:start_threshold=-55dB:stop_periods=-1:stop_duration=1:stop_threshold=-55dB,volumedetect' \
      -f null - > /dev/null 2> "$metrics" || return 1
  fi
  mean=$(sed -n 's/.*mean_volume: \([^ ]*\) dB.*/\1/p' "$metrics" | tail -n 1)
  samples=$(sed -n 's/.*n_samples: \([0-9][0-9]*\).*/\1/p' "$metrics" | tail -n 1)
  if [[ -z "$mean" || "$mean" == "-inf" ]]; then return 1; fi
  if [[ -z "$samples" ]]; then return 1; fi
  if [[ "$live_plex_gate" == true ]]; then
    awk -v value="$mean" -v samples="$samples" \
      'BEGIN { exit !(value > -50.0 && samples >= 96000) }'
  else
    awk -v value="$mean" -v samples="$samples" \
      'BEGIN { exit !(value > -45.0 && samples >= 192000) }'
  fi
}

audio_capture_is_silent() {
  local raw=$1
  local metrics=$2
  local samples
  # Ignore the recorder's bounded startup tail and isolate the synthetic Plex
  # program tone. Require a sustained tone before declaring a leak so bounded
  # transition tails and ordinary broadband game sounds cannot masquerade as playback.
  ffmpeg -hide_banner -loglevel info -ss 1 -f s16le -ar 48000 -ac 2 -i "$raw" \
    -af 'bandpass=f=1000:w=10,silenceremove=start_periods=1:start_duration=1.5:start_threshold=-50dB,volumedetect' -f null - \
    > /dev/null 2> "$metrics" || return 1
  samples=$(sed -n 's/.*n_samples: \([0-9][0-9]*\).*/\1/p' "$metrics" | tail -n 1)
  [[ "$samples" == "0" ]]
}

audio_capture_is_attenuated() {
  local raw=$1
  local metrics=$2
  local reference_metrics=$3
  local mean reference samples
  ffmpeg -hide_banner -loglevel info -f s16le -ar 48000 -ac 2 -i "$raw" \
    -af 'highpass=f=970,lowpass=f=1025,silenceremove=start_periods=1:start_duration=1:start_threshold=-60dB:stop_periods=-1:stop_duration=1:stop_threshold=-60dB,volumedetect' \
    -f null - > /dev/null 2> "$metrics" || return 1
  mean=$(sed -n 's/.*mean_volume: \([^ ]*\) dB.*/\1/p' "$metrics" | tail -n 1)
  reference=$(sed -n 's/.*mean_volume: \([^ ]*\) dB.*/\1/p' "$reference_metrics" | tail -n 1)
  samples=$(sed -n 's/.*n_samples: \([0-9][0-9]*\).*/\1/p' "$metrics" | tail -n 1)
  if [[ -z "$mean" || -z "$reference" || -z "$samples" || "$mean" == "-inf" ]]; then return 1; fi
  awk -v value="$mean" -v reference="$reference" -v samples="$samples" \
    'BEGIN { attenuation = reference - value; exit !(value > -60.0 && samples >= 192000 && attenuation >= 8.0 && attenuation <= 20.0) }'
}

capture_audio_sink() {
  local sink=$1
  local raw=$2
  local seconds=${3:-3}
  local recorder
  : > "$raw"
  parec --raw --latency-msec=50 --device="${sink}.monitor" \
    --format=s16le --rate=48000 --channels=2 > "$raw" &
  recorder=$!
  sleep "$seconds"
  kill -TERM "$recorder" 2>/dev/null || true
  wait "$recorder" 2>/dev/null || true
}

audio_control_sequence=0
send_audio_control() {
  local label=$1
  local role=$2
  local command=$3
  audio_control_sequence=$((audio_control_sequence + 1))
  printf '%s|%s\n' "$audio_control_sequence" "$command" \
    > "$output_root/$label.audio-$role.control"
}

wait_for_pattern_after() {
  local file=$1 first_line=$2 pattern=$3 timeout=${4:-90}
  local deadline=$((SECONDS + timeout))
  while ! tail -n "+$((first_line + 1))" "$file" 2>/dev/null | grep -Eq "$pattern"; do
    if (( SECONDS >= deadline )); then return 1; fi
    sleep 1
  done
}

real_video_capture_is_silent() {
  local raw=$1 metrics=$2 mean maximum
  ffmpeg -hide_banner -loglevel info -ss 1 -f s16le -ar 48000 -ac 2 -i "$raw" \
    -af volumedetect -f null - > /dev/null 2> "$metrics" || return 1
  mean=$(sed -n 's/.*mean_volume: \([^ ]*\) dB.*/\1/p' "$metrics" | tail -n 1)
  maximum=$(sed -n 's/.*max_volume: \([^ ]*\) dB.*/\1/p' "$metrics" | tail -n 1)
  [[ "$mean" == "-inf" || "$maximum" == "-inf" ]] && return 0
  [[ -n "$mean" && -n "$maximum" ]] || return 1
  awk -v mean="$mean" -v maximum="$maximum" 'BEGIN { exit !(mean <= -50.0 && maximum <= -35.0) }'
}

latest_video_generation() {
  sed -n 's/.*Acceptance video session:.*generation=\([0-9][0-9]*\).*/\1/p' "$1" | tail -n 1
}

run_video_control_scenarios() {
  local label=$1 target_dir=$2 java_home=$3 port=$4 sink_leader=$5 sink_follower=$6
  local leader_pid=$7 follower_pid=$8
  local leader_log="$output_root/$label.audio-leader.console.log"
  local follower_log="$output_root/$label.audio-follower.console.log"
  local leader_control="$output_root/$label.audio-leader.control"
  local follower_ui="$output_root/$label.audio-follower/screenshots/cinemarr-video-ui-acceptance.png"
  local evidence="$output_root/$label.video-controls.evidence.txt"
  local raw="$output_root/$label.video-control.s16le" metrics="$output_root/$label.video-control.metrics.txt"
  local first_leader first_follower old_generation new_generation action target_audio target_subtitle
  : > "$evidence"

  first_follower=$(wc -l < "$follower_log")
  send_audio_control "$label" follower 'video:open-ui'
  if ! wait_for_pattern_after "$follower_log" "$first_follower" \
      'Acceptance video UI: width=640 height=360 widgets=[1-9][0-9]* clipped=0 canControl=false' 90; then
    echo "$label: non-owner small-window controller UI was clipped, missing, or incorrectly privileged" >&2
    return 1
  fi
  for _ in {1..60}; do [[ -s "$follower_ui" ]] && break; sleep 1; done
  [[ -s "$follower_ui" ]] || { echo "$label: non-owner small-window UI screenshot was not saved" >&2; return 1; }
  printf 'Non-owner controller UI rendered at 640x360 with zero clipped widgets. Screenshot SHA-256: ' >> "$evidence"
  sha256sum "$follower_ui" | awk '{print $1}' >> "$evidence"

  first_follower=$(wc -l < "$follower_log")
  send_audio_control "$label" follower 'video:pause'
  if ! wait_for_pattern_after "$follower_log" "$first_follower" \
      'Acceptance video control denied locally: operation=video:pause canControl=false' 30; then
    echo "$label: non-owner mutation was not rejected by the real client" >&2
    return 1
  fi
  printf 'Non-owner pause mutation was rejected locally from authoritative canControl=false state.\n' >> "$evidence"

  first_leader=$(wc -l < "$leader_log"); first_follower=$(wc -l < "$follower_log")
  send_audio_control "$label" leader 'video:pause'
  wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video action: PAUSE ' 30 \
    && wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video session:.*status=PAUSED' 60 \
    && wait_for_pattern_after "$follower_log" "$first_follower" 'Acceptance video session:.*status=PAUSED' 60 \
    || { echo "$label: pause did not reach both real clients" >&2; return 1; }
  sleep 2; capture_audio_sink "$sink_leader" "$raw" 4
  real_video_capture_is_silent "$raw" "$metrics" \
    || { echo "$label: paused real-Plex session continued producing program audio" >&2; return 1; }
  printf 'Pause reached both clients and produced captured silence.\n' >> "$evidence"

  first_leader=$(wc -l < "$leader_log"); first_follower=$(wc -l < "$follower_log")
  send_audio_control "$label" leader 'video:resume'
  wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video action: RESUME ' 30 \
    && wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video session:.*status=PLAYING' 60 \
    && wait_for_pattern_after "$follower_log" "$first_follower" 'Acceptance video session:.*status=PLAYING' 60 \
    && wait_for_video_audio_pair_stable "$label" "$leader_pid" "$follower_pid" \
    || { echo "$label: resume did not restore synchronized playback" >&2; return 1; }
  capture_audio_sink "$sink_leader" "$raw" 4
  audio_capture_is_audible "$raw" "$metrics" \
    || { echo "$label: resumed real-Plex session was not audible" >&2; return 1; }
  printf 'Resume restored synchronized audible playback.\n' >> "$evidence"

  old_generation=$(latest_video_generation "$leader_log")
  [[ "$old_generation" =~ ^[0-9]+$ ]] || return 1
  first_leader=$(wc -l < "$leader_log"); first_follower=$(wc -l < "$follower_log")
  send_audio_control "$label" leader 'video:seek-forward'
  if ! wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video action: SEEK ' 30; then
    echo "$label: seek action was not issued" >&2; return 1
  fi
  if ! wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video session:.*status=PLAYING' 120 \
      || ! wait_for_pattern_after "$follower_log" "$first_follower" 'Acceptance video session:.*status=PLAYING' 120; then
    echo "$label: seek did not publish replacement playback to both clients" >&2; return 1
  fi
  new_generation=$(latest_video_generation "$leader_log")
  [[ "$new_generation" =~ ^[0-9]+$ ]] && (( new_generation > old_generation )) \
    || { echo "$label: seek did not advance the playback generation" >&2; return 1; }
  wait_for_video_audio_pair_stable "$label" "$leader_pid" "$follower_pid" \
    || { echo "$label: seek replacement did not stabilize" >&2; return 1; }
  printf 'Seek advanced generation %s to %s and restored both clients.\n' "$old_generation" "$new_generation" >> "$evidence"

  old_generation=$new_generation
  first_leader=$(wc -l < "$leader_log"); first_follower=$(wc -l < "$follower_log")
  send_audio_control "$label" leader 'video:cycle-stream'
  if ! wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video action: SET_STREAMS ' 30; then
    if tail -n "+$((first_leader + 1))" "$leader_log" | grep -Fq 'Acceptance video stream selection unavailable:'; then
      echo "$label: selected real Plex item has no alternate audio or subtitle stream" >&2
    else
      echo "$label: stream selection action was not issued" >&2
    fi
    return 1
  fi
  action=$(tail -n "+$((first_leader + 1))" "$leader_log" | grep -F 'Acceptance video action: SET_STREAMS ' | tail -n 1)
  target_audio=$(sed -n 's/.* audio=\(-\{0,1\}[0-9][0-9]*\) subtitle=.*/\1/p' <<<"$action")
  target_subtitle=$(sed -n 's/.* subtitle=\(-\{0,1\}[0-9][0-9]*\).*/\1/p' <<<"$action")
  [[ "$target_audio" =~ ^-?[0-9]+$ && "$target_subtitle" =~ ^-?[0-9]+$ ]] || return 1
  wait_for_pattern_after "$leader_log" "$first_leader" "Acceptance video session:.*status=PLAYING.*audio=$target_audio subtitle=$target_subtitle" 180 \
    && wait_for_pattern_after "$follower_log" "$first_follower" "Acceptance video session:.*status=PLAYING.*audio=$target_audio subtitle=$target_subtitle" 180 \
    || { echo "$label: selected stream IDs were not authoritative on both clients" >&2; return 1; }
  new_generation=$(latest_video_generation "$leader_log")
  [[ "$new_generation" =~ ^[0-9]+$ ]] && (( new_generation > old_generation )) \
    || { echo "$label: stream selection did not restart playback generation" >&2; return 1; }
  wait_for_video_audio_pair_stable "$label" "$leader_pid" "$follower_pid" \
    || { echo "$label: stream-selected playback did not stabilize" >&2; return 1; }
  printf 'Stream selection chose audio=%s subtitle=%s and advanced generation %s to %s.\n' \
    "$target_audio" "$target_subtitle" "$old_generation" "$new_generation" >> "$evidence"

  cp -- "$follower_log" "$output_root/$label.audio-follower.pre-reconnect.console.log"
  cp -- "$follower_ui" "$output_root/$label.non-owner-small-window-ui.png"
  terminate_client_launch "$follower_pid" 20 \
    || { echo "$label: follower did not disconnect cleanly" >&2; return 1; }
  if ! launch_audio_client "$label" "$target_dir" "$java_home" "$port" follower CinemarrVideoB "$sink_follower"; then
    echo "$label: follower did not reconnect to the live session" >&2; return 1
  fi
  video_scenario_follower_pid=$ready_audio_client_pid
  wait_for_video_audio_pair_stable "$label" "$leader_pid" "$video_scenario_follower_pid" \
    || { echo "$label: reconnected follower did not restore synchronized playback" >&2; return 1; }
  printf 'Follower disconnect/reconnect restored the selected generation with visible, audible playback.\n' >> "$evidence"
}

wait_for_fault_segment_requests() {
  local state=$1 minimum=$2 timeout=$3 deadline count
  deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    count=$(awk -F '\t' -v expected="$state" '
      $2 ~ /^\/video\/:\/transcode\/universal\/segment[0-9]+\.ts$/ && $4 == expected { count++ }
      END { print count + 0 }
    ' "$fake_plex_request_log")
    if (( count >= minimum )); then printf '%s\n' "$count"; return 0; fi
    sleep 1
  done
  return 1
}

run_video_adverse_network_scenarios() {
  local label=$1 leader_pid=$2 follower_pid=$3
  local leader_log="$output_root/$label.audio-leader.console.log"
  local follower_log="$output_root/$label.audio-follower.console.log"
  local server_log="$output_root/$label.console.log"
  local evidence="$output_root/$label.video-adverse-network.evidence.txt"
  local first_leader first_follower first_server old_generation new_generation
  local transient_state slow_state offline_state transient_requests slow_requests offline_requests
  : > "$evidence"

  transient_state="segments-transient-${BASHPID}-${RANDOM}"
  old_generation=$(latest_video_generation "$leader_log")
  first_leader=$(wc -l < "$leader_log"); first_follower=$(wc -l < "$follower_log")
  printf '%s\n' "$transient_state" > "$fake_plex_state"
  send_audio_control "$label" leader 'video:seek-forward'
  if ! wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video action: SEEK ' 30 \
      || ! wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video session:.*status=PLAYING' 120 \
      || ! wait_for_pattern_after "$follower_log" "$first_follower" 'Acceptance video session:.*status=PLAYING' 120; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: transient segment fault did not publish replacement playback" >&2; return 1
  fi
  new_generation=$(latest_video_generation "$leader_log")
  if [[ ! "$old_generation" =~ ^[0-9]+$ || ! "$new_generation" =~ ^[0-9]+$ ]] \
      || (( new_generation <= old_generation )); then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: transient segment fault did not advance generation" >&2; return 1
  fi
  if ! transient_requests=$(wait_for_fault_segment_requests "$transient_state" 3 90) \
      || ! wait_for_video_audio_pair_stable "$label" "$leader_pid" "$follower_pid"; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: two injected HTTP 503 segment responses did not recover inside the bounded server retry" >&2; return 1
  fi
  printf 'Transient segment fault: generation %s to %s, HTTP attempts=%s, playback remained synchronized.\n' \
    "$old_generation" "$new_generation" "$transient_requests" >> "$evidence"

  slow_state="segments-slow-${BASHPID}-${RANDOM}"
  old_generation=$new_generation
  first_leader=$(wc -l < "$leader_log"); first_follower=$(wc -l < "$follower_log")
  printf '%s\n' "$slow_state" > "$fake_plex_state"
  send_audio_control "$label" leader 'video:seek-forward'
  if ! wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video action: SEEK ' 30 \
      || ! wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video session:.*status=PLAYING' 180 \
      || ! wait_for_pattern_after "$follower_log" "$first_follower" 'Acceptance video session:.*status=PLAYING' 180; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: delayed segment delivery did not publish replacement playback" >&2; return 1
  fi
  new_generation=$(latest_video_generation "$leader_log")
  if [[ ! "$old_generation" =~ ^[0-9]+$ || ! "$new_generation" =~ ^[0-9]+$ ]] \
      || (( new_generation <= old_generation )); then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: delayed segment delivery did not advance generation" >&2; return 1
  fi
  if ! slow_requests=$(wait_for_fault_segment_requests "$slow_state" 3 120) \
      || ! wait_for_video_audio_pair_stable "$label" "$leader_pid" "$follower_pid"; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: bounded slow segment delivery did not maintain synchronized playback" >&2; return 1
  fi
  printf 'Slow segment delivery: generation %s to %s, delayed responses=%s, playback remained synchronized.\n' \
    "$old_generation" "$new_generation" "$slow_requests" >> "$evidence"

  offline_state="segments-offline-${BASHPID}-${RANDOM}"
  old_generation=$new_generation
  first_leader=$(wc -l < "$leader_log"); first_follower=$(wc -l < "$follower_log")
  first_server=$(wc -l < "$server_log")
  printf '%s\n' "$offline_state" > "$fake_plex_state"
  send_audio_control "$label" leader 'video:seek-forward'
  if ! wait_for_pattern_after "$leader_log" "$first_leader" 'Acceptance video action: SEEK ' 30 \
      || ! wait_for_pattern_after "$server_log" "$first_server" 'Cinemarr video request failed:' 120 \
      || ! offline_requests=$(wait_for_fault_segment_requests "$offline_state" 6 120); then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: segment exhaustion did not reach the bounded client/server retry path" >&2; return 1
  fi
  if tail -n "+$((first_server + 1))" "$server_log" \
      | grep -F -e "$fake_plex_token" -e "http://127.0.0.1:" -e 'X-Plex-Token'; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: exhausted segment diagnostics exposed a credential or Plex endpoint" >&2; return 1
  fi
  printf 'online\n' > "$fake_plex_state"
  # "Acceptance video ready" is a one-shot per playback generation. A segment
  # refill after transport recovery deliberately keeps that same generation,
  # so continuous two-client A/V stability is the authoritative recovery gate.
  if ! wait_for_video_audio_pair_stable "$label" "$leader_pid" "$follower_pid"; then
    echo "$label: clients did not recover in-session after exhausted segment fetches were restored" >&2; return 1
  fi
  new_generation=$(latest_video_generation "$leader_log")
  [[ "$new_generation" =~ ^[0-9]+$ ]] && (( new_generation > old_generation )) \
    || { echo "$label: exhaustion recovery lost the replacement playback generation" >&2; return 1; }
  printf 'Exhausted segment fault: generation %s to %s, HTTP attempts=%s, redacted failure observed, in-session recovery passed.\n' \
    "$old_generation" "$new_generation" "$offline_requests" >> "$evidence"
}

wait_for_marker_after() {
  local file=$1
  local first_line=$2
  local marker=$3
  local timeout=${4:-60}
  local deadline=$((SECONDS + timeout))
  while ! tail -n "+$((first_line + 1))" "$file" 2>/dev/null | grep -Fq "$marker"; do
    if (( SECONDS >= deadline )); then return 1; fi
    sleep 1
  done
}

run_audio_control_scenarios() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local sink_leader=$5
  local sink_follower=$6
  local leader_pid=$7
  local follower_pid=$8
  local rcon_port=$9
  local rcon_password=${10}
  local fifo_fd=${11}
  local leader_log="$output_root/$label.audio-leader.console.log"
  local follower_log="$output_root/$label.audio-follower.console.log"
  local scenario_evidence="$output_root/$label.audio-scenarios.evidence.txt"
  local raw="$output_root/$label.audio-scenario.s16le"
  local metrics="$output_root/$label.audio-scenario.metrics.txt"
  local first result=0

  : > "$scenario_evidence"
  if [[ "$label" == "1.7.10-forge" ]]; then
    printf 'op CinemarrAudioA\n' >&"$fifo_fd"
  elif ! python3 "$repo_root/scripts/minecraft-rcon.py" 127.0.0.1 "$rcon_port" "$rcon_password" \
      'op CinemarrAudioA' > /dev/null; then
    echo "$label: unable to promote the audio scenario leader" >&2
    return 1
  fi

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'queue:43'
  if ! wait_for_marker_after "$leader_log" "$first" 'queue=42,43' 60; then
    echo "$label: queue scenario did not append track 43" >&2; return 1
  fi
  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'queue:44'
  if ! wait_for_marker_after "$leader_log" "$first" 'queue=42,43,44' 60; then
    echo "$label: queue scenario did not append track 44" >&2; return 1
  fi
  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'control:move_down:1:43'
  if ! wait_for_marker_after "$leader_log" "$first" 'queue=42,44,43' 60; then
    echo "$label: real-client reorder was not reflected by server state" >&2; return 1
  fi

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'control:pause'
  if ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PAUSED' 60; then
    echo "$label: pause did not reach the client audio backend" >&2; return 1
  fi
  # Legacy Paulscode applies pause on its command thread. Exclude that bounded
  # transition from the silence window while keeping the silence threshold
  # strict for the complete capture.
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_silent "$raw" "$metrics"; then
    echo "$label: paused leader still emitted program audio" >&2; return 1
  fi
  printf 'Pause produced captured silence.\n' >> "$scenario_evidence"

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'control:resume'
  if ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 60; then
    echo "$label: resume did not restore client playback" >&2; return 1
  fi
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: resumed leader did not emit program audio" >&2; return 1
  fi
  printf 'Resume restored captured program audio.\n' >> "$scenario_evidence"

  send_audio_control "$label" leader 'volume:0.2'
  sleep 2
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_attenuated "$raw" "$metrics" \
      "$output_root/$label.audio-leader.metrics.txt"; then
    echo "$label: reduced local volume did not produce a sustained attenuated signal" >&2; return 1
  fi
  grep -E 'mean_volume:|max_volume:' "$metrics" | tail -n 2 >> "$scenario_evidence"

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'mute'
  if ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: DISABLED' 60; then
    echo "$label: local mute did not disable the client backend" >&2; return 1
  fi
  capture_audio_sink "$sink_leader" "$raw" 3
  if ! audio_capture_is_silent "$raw" "$metrics"; then
    echo "$label: locally muted leader still emitted program audio" >&2; return 1
  fi
  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'unmute'
  if ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 60; then
    echo "$label: local unmute did not restore playback" >&2; return 1
  fi
  send_audio_control "$label" leader 'volume:1.0'
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: unmuted leader did not emit program audio" >&2; return 1
  fi
  printf 'Local mute produced silence and unmute restored audio.\n' >> "$scenario_evidence"

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'reload'
  if ! wait_for_marker_after "$leader_log" "$first" 'Acceptance resource reload complete: success=true' 120 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 120; then
    echo "$label: sound/resource reload did not recover to PLAYING" >&2; return 1
  fi
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: sound/resource reload recovered state without audible output" >&2; return 1
  fi
  printf 'Resource and sound-engine reload recovered audible playback.\n' >> "$scenario_evidence"

  local server_log="$output_root/$label.console.log"
  local transcodes_before transcodes_after
  first=$(wc -l < "$server_log")
  printf 'offline\n' > "$fake_plex_state"
  if [[ "$label" == "1.7.10-forge" ]]; then
    printf 'cinemarr reload\n' >&"$fifo_fd"
  else
    if ! python3 "$repo_root/scripts/minecraft-rcon.py" 127.0.0.1 "$rcon_port" "$rcon_password" \
        'cinemarr reload' > /dev/null; then
      printf 'online\n' > "$fake_plex_state"
      return 1
    fi
  fi
  if ! wait_for_marker_after "$server_log" "$first" 'Cinemarr Plex validation failed' 60; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: fake Plex outage was not observed by the live server" >&2; return 1
  fi
  transcodes_before=$(awk -F '\t' '$2 == "/music/:/transcode/universal/start.mp3" { count++ } END { print count + 0 }' \
    "$fake_plex_request_log")
  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'control:skip'
  if ! wait_for_marker_after "$leader_log" "$first" \
      'title=Gate Track 44 origin=MANUAL queue=44,43' 120 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 120; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: skip did not advance into the cached pending track during Plex outage" >&2; return 1
  fi
  transcodes_after=$(awk -F '\t' '$2 == "/music/:/transcode/universal/start.mp3" { count++ } END { print count + 0 }' \
    "$fake_plex_request_log")
  if [[ "$transcodes_after" != "$transcodes_before" ]]; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: cache-backed outage playback unexpectedly requested a new Plex transcode" >&2; return 1
  fi
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    printf 'online\n' > "$fake_plex_state"
    echo "$label: cached outage track reached PLAYING without audible output" >&2; return 1
  fi
  printf 'Plex outage observed; skip used cached track with no new transcode and remained audible.\n' \
    >> "$scenario_evidence"

  first=$(wc -l < "$server_log")
  printf 'online\n' > "$fake_plex_state"
  if [[ "$label" == "1.7.10-forge" ]]; then
    printf 'cinemarr reload\n' >&"$fifo_fd"
  else
    python3 "$repo_root/scripts/minecraft-rcon.py" 127.0.0.1 "$rcon_port" "$rcon_password" \
      'cinemarr reload' > /dev/null || return 1
  fi
  if ! wait_for_marker_after "$server_log" "$first" 'Cinemarr connected to Plex; sonic capability is READY' 60; then
    echo "$label: server did not recover Plex and sonic readiness after the controlled outage" >&2; return 1
  fi

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'control:clear'
  if ! wait_for_marker_after "$leader_log" "$first" \
      'Acceptance playback state: status=IDLE' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: NO_STREAM' 60; then
    echo "$label: clear did not stop shared playback and the client stream" >&2; return 1
  fi

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'station:library-shuffle'
  if ! wait_for_marker_after "$leader_log" "$first" \
      'Acceptance station state: type=LIBRARY_SHUFFLE active=true' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'origin=STATION' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 60; then
    echo "$label: general Library Shuffle station did not generate audible playback" >&2; return 1
  fi
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: Library Shuffle reached PLAYING without audible output" >&2; return 1
  fi
  printf 'General Library Shuffle generated audible station playback.\n' >> "$scenario_evidence"

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'adventure:42:49'
  if ! wait_for_marker_after "$leader_log" "$first" \
      'Acceptance station state: type=SONIC_ADVENTURE active=true' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'origin=ADVENTURE' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 60; then
    echo "$label: Sonic Adventure did not generate audible waypoint playback" >&2; return 1
  fi
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: Sonic Adventure reached PLAYING without audible output" >&2; return 1
  fi
  printf 'Sonic Adventure generated an audible analyzed-track path.\n' >> "$scenario_evidence"

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'fault:underrun'
  if ! wait_for_marker_after "$leader_log" "$first" 'acceptance decoder starvation' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: RECOVERING' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 120; then
    echo "$label: deterministic decoder starvation did not recover through RECOVERING to PLAYING" >&2
    return 1
  fi
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: underrun recovery reached PLAYING without audible output" >&2; return 1
  fi
  printf 'Injected decoder starvation recovered through RECOVERING to audible playback.\n' \
    >> "$scenario_evidence"

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'fault:drift'
  if ! wait_for_marker_after "$leader_log" "$first" 'Acceptance clock drift injected beyond' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'clock drift' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: RECOVERING' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 120; then
    echo "$label: deterministic clock drift did not rebuffer and return to PLAYING" >&2
    return 1
  fi
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: drift correction reached PLAYING without audible output" >&2; return 1
  fi
  printf 'Injected clock drift exceeded policy and recovered to audible synchronized playback.\n' \
    >> "$scenario_evidence"

  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'fault:exhaust-retries'
  if ! wait_for_marker_after "$leader_log" "$first" 'acceptance forced recovery failure' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: ERROR' 60; then
    echo "$label: forced consecutive recovery failures did not reach final ERROR state" >&2
    return 1
  fi
  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'retry'
  if ! wait_for_marker_after "$leader_log" "$first" 'manual retry' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: PLAYING' 120; then
    echo "$label: manual retry did not recover final ERROR state to PLAYING" >&2
    return 1
  fi
  sleep 1
  capture_audio_sink "$sink_leader" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: manual retry reached PLAYING without audible output" >&2; return 1
  fi
  printf 'Consecutive failures reached final ERROR; manual retry restored audible playback.\n' \
    >> "$scenario_evidence"

  terminate_client_launch "$follower_pid" 20 || result=1
  active_audio_client_pids=("$leader_pid")
  if ! launch_audio_client "$label" "$target_dir" "$java_home" "$port" follower CinemarrAudioB "$sink_follower"; then return 1; fi
  follower_pid=$ready_audio_client_pid
  sleep 1
  capture_audio_sink "$sink_follower" "$raw" 4
  if ! audio_capture_is_audible "$raw" "$metrics"; then
    echo "$label: reconnected follower reached PLAYING without audible output" >&2; return 1
  fi
  printf 'Follower reconnect restored synchronized audible playback.\n' >> "$scenario_evidence"
  first=$(wc -l < "$leader_log")
  send_audio_control "$label" leader 'control:clear'
  if ! wait_for_marker_after "$leader_log" "$first" 'Acceptance playback state: status=IDLE' 60 \
      || ! wait_for_marker_after "$leader_log" "$first" 'Acceptance audio state: NO_STREAM' 60; then
    echo "$label: final clear did not leave the shared queue and audio stream idle" >&2; return 1
  fi
  printf 'Final clear left shared playback and client audio idle.\n' >> "$scenario_evidence"
  return "$result"
}

run_two_client_audio() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local rcon_port=$5
  local rcon_password=$6
  local fifo_fd=$7
  local sink_prefix="cinemarr_${BASHPID}_${label//[^a-zA-Z0-9]/_}"
  local sink_leader="${sink_prefix}_leader" sink_follower="${sink_prefix}_follower"
  local raw_leader="$output_root/$label.audio-leader.s16le"
  local raw_follower="$output_root/$label.audio-follower.s16le"
  local metrics_leader="$output_root/$label.audio-leader.metrics.txt"
  local metrics_follower="$output_root/$label.audio-follower.metrics.txt"
  local evidence="$output_root/$label.two-client-audio.evidence.txt"
  local module leader_pid follower_pid recorder_pid result=0

  module=$(pactl load-module module-null-sink sink_name="$sink_leader" rate=48000 channels=2) || return 1
  active_audio_modules+=("$module")
  module=$(pactl load-module module-null-sink sink_name="$sink_follower" rate=48000 channels=2) || return 1
  active_audio_modules+=("$module")
  : > "$raw_leader"
  : > "$raw_follower"
  parec --raw --latency-msec=50 --device="${sink_leader}.monitor" --format=s16le --rate=48000 --channels=2 \
    > "$raw_leader" &
  recorder_pid=$!; active_audio_recorder_pids+=("$recorder_pid")
  parec --raw --latency-msec=50 --device="${sink_follower}.monitor" --format=s16le --rate=48000 --channels=2 \
    > "$raw_follower" &
  recorder_pid=$!; active_audio_recorder_pids+=("$recorder_pid")

  if launch_audio_client "$label" "$target_dir" "$java_home" "$port" leader CinemarrAudioA "$sink_leader"; then
    leader_pid=$ready_audio_client_pid
  else
    result=1
  fi
  if (( result == 0 )); then
    if launch_audio_client "$label" "$target_dir" "$java_home" "$port" follower CinemarrAudioB "$sink_follower"; then
      follower_pid=$ready_audio_client_pid
    else
      result=1
    fi
  fi
  if (( result == 0 )); then sleep 5; fi

  for recorder_pid in "${active_audio_recorder_pids[@]}"; do
    kill -TERM "$recorder_pid" 2>/dev/null || true
    wait "$recorder_pid" 2>/dev/null || true
  done
  active_audio_recorder_pids=()
  if (( result == 0 )) && ! audio_capture_is_audible "$raw_leader" "$metrics_leader"; then
    echo "$label: leader sink did not contain observable 997 Hz program audio" >&2
    result=1
  fi
  if (( result == 0 )) && ! audio_capture_is_audible "$raw_follower" "$metrics_follower"; then
    echo "$label: late-join follower sink did not contain observable 997 Hz program audio" >&2
    result=1
  fi
  if (( result == 0 )) && ! awk -F '\t' '$2 == "/music/:/transcode/universal/start.mp3" { found = 1 } END { exit !found }' \
      "$fake_plex_request_log"; then
    echo "$label: fake Plex did not serve the real MP3 transcode" >&2
    result=1
  fi
  if (( result == 0 )); then
    {
      grep -F 'Acceptance audio state: PLAYING' "$output_root/$label.audio-leader.console.log" | tail -n 1
      grep -F 'Acceptance audio state: PLAYING' "$output_root/$label.audio-follower.console.log" | tail -n 1
      grep -E 'mean_volume:|max_volume:' "$metrics_leader" | tail -n 2
      grep -E 'mean_volume:|max_volume:' "$metrics_follower" | tail -n 2
      printf 'Fake Plex transcode served; follower joined after leader reached PLAYING.\n'
    } > "$evidence"
  fi
  if (( result == 0 )) && [[ "$audio_scenario_gate" == "true" ]]; then
    if ! run_audio_control_scenarios "$label" "$target_dir" "$java_home" "$port" \
        "$sink_leader" "$sink_follower" "$leader_pid" "$follower_pid" \
        "$rcon_port" "$rcon_password" "$fifo_fd"; then
      result=1
    fi
  fi
  cleanup_audio_processes
  return "$result"
}

run_two_client_video() {
  local label=$1
  local target_dir=$2
  local java_home=$3
  local port=$4
  local sink_prefix="cinemarr_${BASHPID}_${label//[^a-zA-Z0-9]/_}_video"
  local sink_leader="${sink_prefix}_leader" sink_follower="${sink_prefix}_follower"
  local raw_leader="$output_root/$label.video-leader.s16le"
  local raw_follower="$output_root/$label.video-follower.s16le"
  local paired_capture="$output_root/$label.video-paired.wav"
  local metrics_leader="$output_root/$label.video-leader.metrics.txt"
  local metrics_follower="$output_root/$label.video-follower.metrics.txt"
  local sync_evidence="$output_root/$label.video-audio-sync.json"
  local evidence="$output_root/$label.two-client-video.evidence.txt"
  local leader_log="$output_root/$label.audio-leader.console.log"
  local follower_log="$output_root/$label.audio-follower.console.log"
  local leader_shot="$output_root/$label.audio-leader/screenshots/cinemarr-video-acceptance.png"
  local follower_shot="$output_root/$label.audio-follower/screenshots/cinemarr-video-acceptance.png"
  local server_log="$output_root/$label.console.log"
  local module leader_pid follower_pid common_frame result=0 clients_ready=0
  local sync_script="$repo_root/scripts/compare-pcm-sync.py"
  if [[ "$live_plex_gate" == true ]]; then
    sync_script="$repo_root/scripts/compare-live-pcm-sync.py"
  fi

  module=$(pactl load-module module-null-sink sink_name="$sink_leader" rate=48000 channels=2) || return 1
  active_audio_modules+=("$module")
  module=$(pactl load-module module-null-sink sink_name="$sink_follower" rate=48000 channels=2) || return 1
  active_audio_modules+=("$module")

  if [[ "$video_follower_first_gate" == "true" ]]; then
    # Deterministically cover construction inside chunks an existing viewer is
    # already tracking. The non-owner cannot create the acceptance TV, so its
    # completed login proves the owner builds it afterward.
    start_audio_client "$label" "$target_dir" "$java_home" "$port" follower CinemarrVideoB "$sink_follower"
    follower_pid=$started_audio_client_pid
    if ! wait_for_marker_after "$server_log" 0 'CinemarrVideoB joined the game' 180; then
      echo "$label: follower-first client did not join before owner construction" >&2
      result=1
    fi
    if (( result == 0 )); then
      start_audio_client "$label" "$target_dir" "$java_home" "$port" leader CinemarrVideoA "$sink_leader"
      leader_pid=$started_audio_client_pid
    fi
  elif [[ "$label" == "1.21.1-neoforge" || "$label" == "1.7.10-forge" ]]; then
    # Two cold NeoGradle clients can retain the same project lock, while two
    # simultaneous Forge 1.7.10 handshakes can race inside FML's shared network
    # dispatcher. Launch these profiles sequentially through the bounded retry
    # path; both remain connected for the shared-frame and sync evidence window.
    if launch_audio_client "$label" "$target_dir" "$java_home" "$port" leader CinemarrVideoA "$sink_leader"; then
      leader_pid=$ready_audio_client_pid
    else
      result=1
    fi
    if (( result == 0 )); then
      if launch_audio_client "$label" "$target_dir" "$java_home" "$port" follower CinemarrVideoB "$sink_follower"; then
        follower_pid=$ready_audio_client_pid
        clients_ready=1
      else
        result=1
      fi
    fi
  else
    # Start both GUI clients before waiting for either one. This makes the probe
    # exercise actual simultaneous co-viewing instead of serial client startup.
    start_audio_client "$label" "$target_dir" "$java_home" "$port" leader CinemarrVideoA "$sink_leader"
    leader_pid=$started_audio_client_pid
    start_audio_client "$label" "$target_dir" "$java_home" "$port" follower CinemarrVideoB "$sink_follower"
    follower_pid=$started_audio_client_pid
  fi
  if (( result == 0 && clients_ready == 0 )); then wait_for_audio_playing "$label" leader "$leader_pid" || result=1; fi
  if (( result == 0 && clients_ready == 0 )); then wait_for_audio_playing "$label" follower "$follower_pid" || result=1; fi
  if (( result == 0 )); then
    wait_for_video_audio_pair_stable "$label" "$leader_pid" "$follower_pid" || result=1
  fi

  if (( result == 0 )) && [[ "$video_control_gate" == true ]]; then
    video_scenario_follower_pid=""
    if run_video_control_scenarios "$label" "$target_dir" "$java_home" "$port" \
        "$sink_leader" "$sink_follower" "$leader_pid" "$follower_pid"; then
      follower_pid=$video_scenario_follower_pid
    else
      result=1
    fi
  fi

  if (( result == 0 )) && [[ "$video_adverse_network_gate" == true ]]; then
    if ! run_video_adverse_network_scenarios "$label" "$leader_pid" "$follower_pid"; then
      result=1
    fi
  fi

  if (( result == 0 )); then
    if [[ "$video_decoder_expect_fallback" == true ]]; then
      if ! grep -Fq "video decoder requested=$video_decoder_backend" "$leader_log" \
          || ! grep -Fq 'fell back permanently to software:' "$leader_log" \
          || ! grep -Fq "video decoder requested=$video_decoder_backend" "$follower_log" \
          || ! grep -Fq 'fell back permanently to software:' "$follower_log"; then
        echo "$label: expected decoder fallback was not logged by both clients" >&2
        result=1
      fi
    elif ! grep -Fq "video decoder requested=$video_decoder_backend effective=$video_decoder_expected_effective" "$leader_log" \
        || ! grep -Fq "video decoder requested=$video_decoder_backend effective=$video_decoder_expected_effective" "$follower_log"; then
      echo "$label: clients did not select expected decoder backend $video_decoder_expected_effective" >&2
      result=1
    elif grep -Fq 'fell back permanently to software:' "$leader_log" \
        || grep -Fq 'fell back permanently to software:' "$follower_log"; then
      echo "$label: unexpected decoder fallback was logged" >&2
      result=1
    fi
  fi

  if (( result == 0 )); then
    sleep 1
    : > "$raw_leader"
    : > "$raw_follower"
    rm -f -- "$paired_capture"
    if ! ffmpeg -hide_banner -loglevel error -y -copyts -start_at_zero \
        -f pulse -i "${sink_leader}.monitor" -f pulse -i "${sink_follower}.monitor" \
        -filter_complex '[0:a][1:a]join=inputs=2:channel_layout=quad:map=0.0-FL|0.1-FR|1.0-BL|1.1-BR[out]' \
        -map '[out]' -t 8 -c:a pcm_s16le "$paired_capture"; then
      echo "$label: shared-clock two-client audio capture failed" >&2
      result=1
    elif ! ffmpeg -hide_banner -loglevel error -y -i "$paired_capture" \
        -af 'pan=stereo|c0=c0|c1=c1' -f s16le "$raw_leader" \
      || ! ffmpeg -hide_banner -loglevel error -y -i "$paired_capture" \
        -af 'pan=stereo|c0=c2|c1=c3' -f s16le "$raw_follower"; then
      echo "$label: shared-clock two-client audio channels could not be separated" >&2
      result=1
    fi
  fi

  if (( result == 0 )); then
    wait_for_video_audio_pair_stable "$label" "$leader_pid" "$follower_pid" || result=1
  fi

  if (( result == 0 )) && ! audio_capture_is_audible "$raw_leader" "$metrics_leader"; then
    echo "$label: leader video client did not emit audible program audio" >&2
    result=1
  fi
  if (( result == 0 )) && ! audio_capture_is_audible "$raw_follower" "$metrics_follower"; then
    echo "$label: follower video client did not emit audible program audio" >&2
    result=1
  fi
  if (( result == 0 )) && ! python3 "$sync_script" \
      "$raw_leader" "$raw_follower" > "$sync_evidence"; then
    echo "$label: client video-program audio captures were not synchronized; see $sync_evidence" >&2
    result=1
  fi
  if (( result == 0 )); then
    common_frame=$(comm -12 \
      <(sed -n 's/.*Acceptance video rendered:.*frameSha256=\([0-9a-f]\{64\}\).*/\1/p' "$leader_log" | sort -u) \
      <(sed -n 's/.*Acceptance video rendered:.*frameSha256=\([0-9a-f]\{64\}\).*/\1/p' "$follower_log" | sort -u) \
      | head -n 1)
    if [[ -z "$common_frame" ]]; then
      echo "$label: clients did not render a common identifiable decoded frame" >&2
      result=1
    fi
  fi
  if (( result == 0 )) && [[ ! -s "$leader_shot" || ! -s "$follower_shot" ]]; then
    echo "$label: one or both real clients did not save the rendered-TV screenshot" >&2
    result=1
  fi
  if (( result == 0 )) && [[ "$live_plex_gate" != true ]] && ! awk -F '\t' '
      $2 == "/video/:/transcode/universal/start.m3u8" { start = 1 }
      $2 == "/video/:/transcode/universal/media.m3u8" { manifest = 1 }
      $2 ~ /^\/video\/:\/transcode\/universal\/segment[0-9]+\.ts$/ { segment = 1 }
      END { exit !(start && manifest && segment) }' "$fake_plex_request_log"; then
    echo "$label: fake Plex did not serve the complete HLS path" >&2
    result=1
  fi
  if (( result == 0 )); then
    {
      printf 'Common rendered frame SHA-256: %s\n' "$common_frame"
      grep -F 'Acceptance video ready:' "$leader_log" | tail -n 1
      grep -F 'Acceptance video ready:' "$follower_log" | tail -n 1
      printf 'Leader screenshot SHA-256: '; sha256sum "$leader_shot" | awk '{print $1}'
      printf 'Follower screenshot SHA-256: '; sha256sum "$follower_shot" | awk '{print $1}'
      cat "$sync_evidence"
      printf 'Decoder requested=%s expectedEffective=%s expectedFallback=%s\n' \
        "$video_decoder_backend" "$video_decoder_expected_effective" "$video_decoder_expect_fallback"
      grep -E 'Cinemarr (legacy )?video decoder requested=' "$leader_log" | tail -n 1 || true
      grep -E 'Cinemarr (legacy )?video decoder requested=' "$follower_log" | tail -n 1 || true
      grep -F 'Acceptance decoder metrics:' "$leader_log" | tail -n 1 || true
      grep -F 'Acceptance decoder metrics:' "$follower_log" | tail -n 1 || true
      if [[ "$live_plex_gate" == true ]]; then
        printf 'Credentialed live Plex served controller-selected HLS video and audio to both clients.\n'
      else
        printf 'Fake Plex served master playlist, media playlist, and MPEG-TS program segments.\n'
      fi
      if [[ "$video_control_gate" == true ]]; then cat "$output_root/$label.video-controls.evidence.txt"; fi
      if [[ "$video_adverse_network_gate" == true ]]; then cat "$output_root/$label.video-adverse-network.evidence.txt"; fi
    } > "$evidence"
  fi
  cleanup_audio_processes
  return "$result"
}

install_fake_plex_config() {
  local run_dir=$1
  local label=$2
  local level_name=$3
  local fake_plex_port=$4
  active_config="$run_dir/$level_name/serverconfig/cinemarr-server.toml"
  active_config_backup=$(mktemp "$output_root/$label.config.XXXXXX")
  active_config_existed=0
  mkdir -p "$(dirname "$active_config")"
  if [[ -f "$active_config" ]]; then
    cp -- "$active_config" "$active_config_backup"
    active_config_existed=1
  fi
  active_libraries="$run_dir/$level_name/serverconfig/cinemarr-libraries.toml"
  active_libraries_backup=$(mktemp "$output_root/$label.libraries.XXXXXX")
  active_libraries_existed=0
  if [[ -f "$active_libraries" ]]; then
    cp -- "$active_libraries" "$active_libraries_backup"
    active_libraries_existed=1
  fi
  if [[ "$live_plex_gate" == true ]]; then
    printf '%s\n' \
      '# Generated temporarily by the credentialed Cinemarr live-Plex gate.' \
      "plexUrl = \"${CINEMARR_PLEX_URL}\"" \
      'plexToken = ""' \
      'operatorPermissionLevel = 2' \
      'queueLimit = 500' \
      'quickTvBuildMode = "bounded"' \
      'maximumVideoWidth = 3840' \
      'maximumVideoHeight = 2160' \
      'maximumVideoBitrateKbps = 20000' > "$active_config"
    printf '%s\n' \
      '# Generated temporarily by the credentialed Cinemarr live-Plex gate.' \
      '[[libraries]]' \
      'id = "live_video"' \
      "section = \"${CINEMARR_LIVE_VIDEO_SECTION_ID}\"" \
      'displayName = "Live Video"' \
      'allowMovies = true' \
      'allowShows = true' \
      'permissionLevel = 0' > "$active_libraries"
  else
    printf '%s\n' \
      '# Generated temporarily by the Cinemarr dedicated-server gate.' \
      "plexUrl = \"http://127.0.0.1:${fake_plex_port}\"" \
      'plexToken = ""' \
      'operatorPermissionLevel = 2' \
      'queueLimit = 500' \
      'quickTvBuildMode = "bounded"' \
      'maximumVideoWidth = 3840' \
      'maximumVideoHeight = 2160' \
      'maximumVideoBitrateKbps = 20000' > "$active_config"
    printf '%s\n' \
      '# Generated temporarily by the Cinemarr dedicated-server gate.' \
      '[[libraries]]' \
      'id = "gate_movies"' \
      'section = "Movies"' \
      'displayName = "Gate Movies"' \
      'allowMovies = true' \
      'allowShows = false' \
      'permissionLevel = 0' > "$active_libraries"
  fi
}

install_invalid_config() {
  local run_dir=$1
  local label=$2
  local level_name=$3
  active_config="$run_dir/$level_name/serverconfig/cinemarr-server.toml"
  active_config_backup=$(mktemp "$output_root/$label.invalid-config.XXXXXX")
  active_config_existed=0
  mkdir -p "$(dirname "$active_config")"
  if [[ -f "$active_config" ]]; then
    cp -- "$active_config" "$active_config_backup"
    active_config_existed=1
  fi
  printf '%s\n' \
    '# Intentionally invalid; installed temporarily by the Cinemarr dedicated-server gate.' \
    'plexUrl = "http://private-user:private-pass@127.0.0.1:32400"' > "$active_config"
}

group_alive() {
  local group_id=$1
  ps -eo pgid=,stat= | awk -v expected="$group_id" \
    '$1 == expected && $2 !~ /^Z/ { found = 1 } END { exit !found }'
}

stop_group() {
  local group_id=$1
  local signal=$2
  kill "-$signal" -- "-$group_id" 2>/dev/null || true
}

wait_for_group_exit() {
  local group_id=$1
  local seconds=$2
  local deadline=$((SECONDS + seconds))
  while group_alive "$group_id"; do
    if (( SECONDS >= deadline )); then return 1; fi
    sleep 1
  done
}

process_tree_pids() {
  local root=$1
  ps -eo pid=,ppid= | awk -v root="$root" '
    { parent[$1] = $2 }
    END {
      for (pid in parent) {
        current = pid
        while (current in parent && current != 1) {
          if (current == root) { print pid; break }
          current = parent[current]
        }
      }
    }
  ' | sort -rn
}

stop_process_tree() {
  local root=$1
  local signal=$2
  local -a tree=()
  mapfile -t tree < <(process_tree_pids "$root")
  if (( ${#tree[@]} != 0 )); then kill "-$signal" -- "${tree[@]}" 2>/dev/null || true; fi
}

wait_for_process_tree_exit() {
  local root=$1
  local seconds=$2
  local deadline=$((SECONDS + seconds))
  while [[ -n "$(process_tree_pids "$root")" ]]; do
    if (( SECONDS >= deadline )); then return 1; fi
    sleep 1
  done
}

terminate_client_launch() {
  local root=$1
  local seconds=$2
  local pid group deadline live result=0
  local gate_group
  local -a pids=() groups=()
  gate_group=$(ps -o pgid= -p "$$" 2>/dev/null | tr -d ' ')
  mapfile -t pids < <({ printf '%s\n' "$root"; process_tree_pids "$root"; } | sort -un)
  for pid in "${pids[@]}"; do
    group=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')
    if [[ -n "$group" && "$group" != "$gate_group" && "$group" != "$active_server_group" ]]; then
      groups+=("$group")
    fi
  done
  mapfile -t groups < <(printf '%s\n' "${groups[@]}" | sed '/^$/d' | sort -unr)

  for group in "${groups[@]}"; do stop_group "$group" TERM; done
  deadline=$((SECONDS + seconds))
  while true; do
    live=0
    for group in "${groups[@]}"; do group_alive "$group" && live=1; done
    if (( live == 0 )); then break; fi
    if (( SECONDS >= deadline )); then result=1; break; fi
    sleep 1
  done
  if (( result != 0 )); then
    for group in "${groups[@]}"; do stop_group "$group" KILL; done
    deadline=$((SECONDS + 10))
    while true; do
      live=0
      for group in "${groups[@]}"; do group_alive "$group" && live=1; done
      if (( live == 0 )); then result=0; break; fi
      if (( SECONDS >= deadline )); then break; fi
      sleep 1
    done
  fi
  wait "$root" 2>/dev/null || true
  return "$result"
}

ensure_runtime_files() {
  local run_dir=$1
  local default_port=$2
  mkdir -p "$run_dir"
  if [[ ! -f "$run_dir/eula.txt" ]]; then
    printf 'eula=true\n' > "$run_dir/eula.txt"
  elif ! grep -Eq '^eula=true$' "$run_dir/eula.txt"; then
    printf 'eula=true\n' > "$run_dir/eula.txt"
  fi
  if [[ ! -f "$run_dir/server.properties" ]]; then
    printf 'allow-flight=true\nonline-mode=false\nserver-port=%s\nlevel-name=world\nmotd=Cinemarr dedicated-server gate\n' \
      "$default_port" > "$run_dir/server.properties"
  elif grep -Eq '^server-port=[0-9]+$' "$run_dir/server.properties"; then
    sed -i -E "s/^server-port=[0-9]+$/server-port=$default_port/" "$run_dir/server.properties"
  else
    printf '\nserver-port=%s\n' "$default_port" >> "$run_dir/server.properties"
  fi
}

run_invalid_config_check() {
  local label=$1
  local target_dir=$2
  local run_dir=$3
  local java_home=$4
  local port=$5
  local level_name=$6
  local console_log="$output_root/$label.invalid-config.console.log"
  local latest_log="$run_dir/logs/latest.log"
  local pid server_pid server_group result=0 rejection_seen=0
  local -a cache_args=()
  local -a runtime_args=(-PcinemarrServerGameDir="$run_dir")
  [[ "$label" == *-quilt ]] && runtime_args+=(-PcinemarrRuntimeLoader=quilt)
  [[ "$label" == *-fabric && -n "$fabric_loader_version" ]] && runtime_args+=(-PcinemarrFabricLoaderVersion="$fabric_loader_version")
  [[ "$active_disable_configuration_cache" == true ]] && cache_args+=(--no-configuration-cache)

  install_invalid_config "$run_dir" "$label" "$level_name"
  (
    cd "$target_dir" || exit 1
    exec setsid env JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
      CINEMARR_PLEX_TOKEN="$fake_plex_token" \
      ./gradlew "$active_server_task" --no-daemon --max-workers=1 --console=plain "${cache_args[@]}" \
      "${runtime_args[@]}" \
      < /dev/null > "$console_log" 2>&1
  ) &
  pid=$!
  active_server_pid=$pid

  local deadline=$((SECONDS + 600))
  while (( SECONDS < deadline )); do
    if [[ -z "$active_server_group" ]]; then
      server_pid=$(ss -ltnp "sport = :$port" \
        | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | head -n 1)
      server_group=""
      if [[ -n "$server_pid" ]]; then
        server_group=$(ps -o pgid= -p "$server_pid" 2>/dev/null | tr -d '[:space:]')
      fi
      if [[ "$server_group" =~ ^[0-9]+$ ]] && (( server_group > 1 )); then
        active_server_group=$server_group
      fi
    fi
    if grep -Fq 'Invalid Cinemarr configuration value for plexUrl' "$latest_log" "$console_log" 2>/dev/null; then
      rejection_seen=1
      break
    fi
    # Some Gradle versions fork a single-use daemon into a different process
    # group while downloading Minecraft assets. Only treat an exited wrapper
    # as terminal once its server port is also closed and Gradle logged an end.
    if ! group_alive "$pid" && ! ss -ltnH "sport = :$port" | grep -q . \
        && grep -Eq 'BUILD (FAILED|SUCCESSFUL)|FAILURE:' "$console_log" 2>/dev/null; then
      break
    fi
    sleep 1
  done

  if (( rejection_seen == 0 )); then
    echo "$label: invalid-configuration rejection timed out or the launcher exited without the rejected key" >&2
    result=1
  fi
  local close_deadline=$((SECONDS + 120))
  while ss -ltnH "sport = :$port" | grep -q . && (( SECONDS < close_deadline )); do sleep 1; done
  if ss -ltnH "sport = :$port" | grep -q .; then
    echo "$label: server did not close port $port after rejecting invalid Cinemarr configuration" >&2
    result=1
  fi
  if [[ -n "$active_server_group" ]]; then
    if ! wait_for_group_exit "$active_server_group" 60; then
      stop_group "$active_server_group" TERM
      wait_for_group_exit "$active_server_group" 10 || stop_group "$active_server_group" KILL
      result=1
    fi
    active_server_group=""
  fi
  if group_alive "$pid" && ! wait_for_group_exit "$pid" 60; then
    stop_group "$pid" TERM
    wait_for_group_exit "$pid" 10 || stop_group "$pid" KILL
    result=1
  fi
  wait "$pid" 2>/dev/null || true
  active_server_pid=""
  if ! grep -Fq 'Invalid Cinemarr configuration value for plexUrl' "$latest_log" "$console_log" 2>/dev/null; then
    echo "$label: invalid configuration failure did not identify the rejected key" >&2
    result=1
  fi
  if grep -Fq 'private-pass' "$latest_log" "$console_log" 2>/dev/null; then
    echo "$label: invalid configuration diagnostics leaked a credential" >&2
    result=1
  fi
  restore_server_config
  if (( result == 0 )); then
    echo "$label: invalid configuration rejected without leaking its value"
  fi
  return "$result"
}

set_property() {
  local file=$1
  local key=$2
  local value=$3
  if grep -q "^${key}=" "$file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

backup_server_properties() {
  local properties=$1
  local label=$2
  active_properties="$properties"
  active_properties_backup=$(mktemp "$output_root/$label.server-properties.XXXXXX")
  cp -- "$active_properties" "$active_properties_backup"
}

wait_for_external_video_client() {
  local label=$1
  local server_pid=$2
  local server_log=$3
  local marker="$output_root/$label.external-client.result"
  local deadline=$((SECONDS + 3600))
  rm -f -- "$marker"
  printf '%s\n' "$label: waiting for an externally launched native client; write pass or fail to $marker"
  while [[ ! -s "$marker" ]]; do
    if ! kill -0 "$server_pid" 2>/dev/null; then
      echo "$label: server exited while waiting for the external client" >&2
      return 1
    fi
    if (( SECONDS >= deadline )); then
      echo "$label: external native-client result did not arrive within 3600 seconds" >&2
      return 1
    fi
    sleep 2
  done
  local result
  result=$(tr -d '[:space:]' < "$marker")
  if [[ "$result" != pass ]]; then
    echo "$label: external native-client gate reported failure" >&2
    return 1
  fi
  if ! grep -Fq 'CinemarrVideoA joined the game' "$server_log" \
      || ! grep -Fq 'Acceptance Quick TV: controller=-3996 preset=144p dimensions=16x9 rendition=256x144 owner=CinemarrVideoA' "$server_log"; then
    echo "$label: external client did not prove login and Quick TV construction" >&2
    return 1
  fi
  return 0
}

run_target() {
  local label=$1
  local relative_dir=$2
  local java_home=$3
  local default_port=$4
  local target_dir="$repo_root/$relative_dir"
  local run_dir="$target_dir/run"
  [[ "$label" == *-quilt ]] && run_dir="$target_dir/run-quilt"
  local latest_log="$run_dir/logs/latest.log"
  local console_log="$output_root/$label.console.log"
  local fifo_dir fifo fifo_fd port rcon_port rcon_password result=0
  local pid="" server_pid="" server_group=""
  local fake_plex_port fake_request_start plex_deadline level_name probe_output
  local server_java_options=""
  local plex_runtime_token="$fake_plex_token"
  local -a probe_args=()
  local -a cache_args=()
  local -a runtime_args=(-PcinemarrServerGameDir="$run_dir")
  [[ "$label" == *-quilt ]] && runtime_args+=(-PcinemarrRuntimeLoader=quilt)
  [[ "$label" == *-fabric && -n "$fabric_loader_version" ]] && runtime_args+=(-PcinemarrFabricLoaderVersion="$fabric_loader_version")
  if [[ "$video_client_gate" == "true" ]]; then
    server_java_options='-Dcinemarr.acceptance.enabled=true -Dcinemarr.acceptance.videoProbe=true'
  fi
  if [[ "$live_plex_gate" == true ]]; then
    plex_runtime_token="$CINEMARR_PLEX_TOKEN"
  fi

  # Every target must start from a healthy fake Plex service. In particular,
  # an earlier audio assertion may have failed during its intentional outage;
  # never allow that scoped failure to poison the rest of the release matrix.
  printf 'online\n' > "$fake_plex_state"

  if [[ ! -x "$java_home/bin/java" ]]; then
    echo "$label: missing Java runtime $java_home" >&2
    return 1
  fi
  ensure_runtime_files "$run_dir" "$default_port"
  port=$(sed -n 's/^server-port=\([0-9][0-9]*\)$/\1/p' "$run_dir/server.properties" | tail -n 1)
  if [[ -z "$port" ]]; then
    echo "$label: unable to resolve server port" >&2
    return 1
  fi
  if ss -ltnH "sport = :$port" | grep -q .; then
    echo "$label: port $port is already in use" >&2
    return 1
  fi
  rcon_port=$((default_port + 1000))
  rcon_password="cinemarr-gate-${default_port}"
  if [[ "$label" != "1.7.10-forge" ]] && ss -ltnH "sport = :$rcon_port" | grep -q .; then
    echo "$label: RCON port $rcon_port is already in use" >&2
    return 1
  fi
  active_game_port=$port
  if [[ "$label" != "1.7.10-forge" ]]; then active_rcon_port=$rcon_port; fi
  backup_server_properties "$run_dir/server.properties" "$label"
  # Keep the gate isolated from developer worlds and from damage left by an
  # interrupted prior run. The run directories are ignored build state.
  level_name=cinemarr-gate-world
  set_property "$run_dir/server.properties" level-name "$level_name"
  set_property "$run_dir/server.properties" online-mode false
  set_property "$run_dir/server.properties" enforce-secure-profile false
  set_property "$run_dir/server.properties" sync-chunk-writes false
  # Keep hostile mobs from moving or killing the two physical-audio probes.
  # Listener displacement changes positional gain and invalidates the capture.
  set_property "$run_dir/server.properties" spawn-monsters false
  # The acceptance scene fits within one chunk. Keep the isolated world small
  # so software-rendered multi-client runs do not leave hundreds of generated
  # chunks for the server to drain during the bounded clean-shutdown gate.
  set_property "$run_dir/server.properties" view-distance 3
  set_property "$run_dir/server.properties" simulation-distance 3
  isolate_gate_world "$run_dir" "$label" "$level_name"
  if [[ "$label" == "1.7.10-forge" ]]; then
    # Vanilla 1.7.10 closes an RCON connection after its authentication packet,
    # so use its working console input instead of weakening clean-shutdown proof.
    set_property "$run_dir/server.properties" enable-rcon false
  else
    set_property "$run_dir/server.properties" enable-rcon true
    set_property "$run_dir/server.properties" rcon.port "$rcon_port"
    set_property "$run_dir/server.properties" rcon.password "$rcon_password"
  fi
  [[ "$active_disable_configuration_cache" == true ]] && cache_args+=(--no-configuration-cache)

  if ! run_invalid_config_check "$label" "$target_dir" "$run_dir" "$java_home" "$port" "$level_name"; then
    restore_server_properties
    restore_gate_world
    return 1
  fi
  # Another local gate can claim a port while the cold invalid-config launch
  # is running. Recheck immediately before the real launch and never treat an
  # unrelated listener as a Cinemarr process during shutdown cleanup.
  if ss -ltnH "sport = :$port" | grep -q . \
      || { [[ "$label" != "1.7.10-forge" ]] && ss -ltnH "sport = :$rcon_port" | grep -q .; }; then
    echo "$label: a required port became occupied during the cold-launch check" >&2
    active_game_port=""
    active_rcon_port=""
    restore_server_properties
    restore_gate_world
    return 1
  fi

  fake_plex_port=$(<"$fake_plex_port_file")
  fake_request_start=$(wc -l < "$fake_plex_request_log")
  install_fake_plex_config "$run_dir" "$label" "$level_name" "$fake_plex_port"
  if [[ "$audio_client_gate" == "true" || "$video_client_gate" == "true" ]]; then
    isolate_audio_cache "$run_dir" "$label"
  fi

  fifo_dir=$(mktemp -d "$output_root/$label.fifo.XXXXXX")
  fifo="$fifo_dir/stdin"
  mkfifo "$fifo"
  exec {fifo_fd}<>"$fifo"
  echo "$label: starting on port $port"
  (
    cd "$target_dir" || exit 1
    export CINEMARR_PLEX_TOKEN="$plex_runtime_token"
    exec setsid env JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" \
      JAVA_TOOL_OPTIONS="$server_java_options" \
      ./gradlew "$active_server_task" --no-daemon --max-workers=1 --console=plain "${cache_args[@]}" \
      "${runtime_args[@]}" \
      < "$fifo" > "$console_log" 2>&1
  ) &
  pid=$!
  active_server_pid=$pid

  local startup_deadline=$((SECONDS + 180))
  while :; do
    if grep -Eq 'Done \([^)]*\)! For help' "$console_log" 2>/dev/null; then
      break
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "$label: server process exited before readiness" >&2
      result=1
      break
    fi
    if (( SECONDS >= startup_deadline )); then
      echo "$label: server did not become ready within 180 seconds" >&2
      result=1
      break
    fi
    sleep 1
  done

  if (( result == 0 )); then
    plex_deadline=$((SECONDS + 30))
    while ! fake_plex_requests_complete "$fake_request_start"; do
      if ! kill -0 "$pid" 2>/dev/null || (( SECONDS >= plex_deadline )); then
        echo "$label: did not complete authenticated Plex video-library validation" >&2
        result=1
        break
      fi
      sleep 1
    done
  fi

  # Gradle's --no-daemon mode still launches a single-use daemon. Depending on
  # setsid's fork behavior, that daemon's process group can differ from the
  # background launcher PID. Record the group that actually owns Minecraft's
  # listening socket so shutdown and interrupt cleanup cannot strand it.
  server_pid=$(ss -ltnp "sport = :$port" \
    | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | head -n 1)
  server_group=""
  if [[ -n "$server_pid" ]]; then
    server_group=$(ps -o pgid= -p "$server_pid" | tr -d '[:space:]')
  fi
  if [[ "$server_group" =~ ^[0-9]+$ ]] && (( server_group > 1 )); then
    active_server_group=$server_group
  else
    server_group=""
  fi

  if (( result == 0 )) && [[ "$protocol_client_gate" == "true" ]]; then
    if ! run_wrong_protocol_client "$label" "$target_dir" "$java_home" "$port" "$console_log"; then
      result=1
    fi
  fi

  if (( result == 0 )) && [[ "$command_client_gate" == "true" ]]; then
    if ! run_command_client "$label" "$target_dir" "$java_home" "$port" "$console_log" \
        "$rcon_port" "$rcon_password" "$fifo_fd"; then
      result=1
    fi
  fi

  if (( result == 0 )) && [[ "$video_client_gate" == "true" ]]; then
    if [[ "$external_video_client_gate" == true ]]; then
      wait_for_external_video_client "$label" "$pid" "$console_log" || result=1
    elif ! run_two_client_video "$label" "$target_dir" "$java_home" "$port"; then
      result=1
    fi
    if (( result == 0 )) && ! grep -Fq 'Acceptance Quick TV: controller=-3996 preset=144p dimensions=16x9 rendition=256x144 owner=CinemarrVideoA' \
        "$console_log"; then
      echo "$label: client scene did not prove actual 144p Quick TV construction and persistence" >&2
      result=1
    fi
  elif (( result == 0 )) && [[ "$audio_client_gate" == "true" ]]; then
    if ! run_two_client_audio "$label" "$target_dir" "$java_home" "$port" \
        "$rcon_port" "$rcon_password" "$fifo_fd"; then
      result=1
    fi
  fi

  if (( result == 0 )) && [[ "$label" == "1.7.10-forge" ]]; then
    if ! run_missing_hello_client "$label" "$target_dir" "$java_home" "$port" "$console_log"; then
      result=1
    fi
  elif (( result == 0 )); then
    probe_output="$output_root/$label.missing-client.json"
    case "$label" in
      26.1.2-*)
        # Minecraft 26.1 can close legacy protocol -1 status queries before a
        # response; use the protocol declared by these pinned target builds.
        probe_args+=(--protocol 775 --version 26.1.2)
        ;;
      26.2-*)
        probe_args+=(--protocol 776 --version 26.2)
        ;;
    esac
    if ! python3 "$repo_root/scripts/minecraft-login-probe.py" 127.0.0.1 "$port" --timeout 35 \
        "${probe_args[@]}" \
        > "$probe_output" 2>&1; then
      if grep -Fq '"closed": true' "$probe_output" \
          && missing_client_rejection_logged "$latest_log" "$console_log"; then
        printf '%s\n' 'Socket closure paired with the server client-facing required-mod rejection log.' \
          > "$output_root/$label.missing-client.server.txt"
      else
        echo "$label: missing-client probe did not observe a clear required-mod disconnect; see $probe_output" >&2
        result=1
      fi
    fi
    # Let the server finish its disconnect/player-removal tick before the
    # shutdown probe begins; newer versions otherwise may overlap chunk unload
    # with the immediate save-all performed by stop.
    sleep 2
  fi

  if [[ "$label" == "1.7.10-forge" ]]; then
    printf 'stop\n' >&"$fifo_fd"
  elif ! python3 "$repo_root/scripts/minecraft-rcon.py" 127.0.0.1 "$rcon_port" "$rcon_password" stop \
      >> "$console_log" 2>&1; then
    printf 'stop\n' >&"$fifo_fd"
  fi
  if [[ -n "$server_group" ]]; then
    wait_for_group_exit "$server_group" 60
  else
    wait_for_process_tree_exit "$pid" 60
  fi
  if (( $? != 0 )); then
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
      if [[ -x "$java_home/bin/jcmd" ]]; then
        "$java_home/bin/jcmd" "$server_pid" Thread.print \
          > "$output_root/$label.shutdown-timeout.threads.txt" 2>&1 || true
      elif [[ -x "$java_home/bin/jstack" ]]; then
        "$java_home/bin/jstack" "$server_pid" \
          > "$output_root/$label.shutdown-timeout.threads.txt" 2>&1 || true
      fi
    fi
    if [[ -z "$server_pid" ]] || ! kill -0 "$server_pid" 2>/dev/null; then
      server_pid=$(ss -ltnp "sport = :$port or sport = :$rcon_port" \
        | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | head -n 1)
    fi
    if [[ -n "$server_pid" ]]; then
      kill -TERM "$server_pid" 2>/dev/null || true
    else
      echo "$label: console stop timed out and the listening server process could not be identified" >&2
      stop_process_tree "$pid" TERM
      result=1
    fi
    if [[ -n "$server_group" ]]; then
      wait_for_group_exit "$server_group" 30
    else
      wait_for_process_tree_exit "$pid" 30
    fi
    if (( $? != 0 )); then
      echo "$label: graceful shutdown timed out" >&2
      if [[ -n "$server_group" ]]; then
        stop_group "$server_group" KILL
        wait_for_group_exit "$server_group" 10 || true
      else
        stop_process_tree "$pid" KILL
        wait_for_process_tree_exit "$pid" 10 || true
      fi
      result=1
    fi
  fi
  wait "$pid" 2>/dev/null || true
  active_server_pid=""
  active_server_group=""
  exec {fifo_fd}>&-
  rm -f -- "$fifo"
  rmdir -- "$fifo_dir"

  if [[ ! -f "$latest_log" ]] || ! grep -Eq 'Stopping (the )?server' "$latest_log" \
      || ! grep -q 'Saving players' "$latest_log"; then
    echo "$label: log does not prove a clean Minecraft shutdown" >&2
    result=1
  fi
  if [[ "$label" == "1.7.10-forge" ]]; then
    if ! grep -q 'Initializing Cinemarr 1.0.0 for Forge 1.7.10 protocol 10' "$run_dir/logs/fml-server-latest.log"; then
      echo "$label: FML log does not prove Cinemarr initialized" >&2
      result=1
    fi
  elif ! grep -Eiq 'cinemarr' "$latest_log"; then
    echo "$label: server log does not prove Cinemarr loaded" >&2
    result=1
  fi
  if grep -Eiq 'Failed to start the minecraft server|ModLoadingException|Preparing crash report|Encountered an unexpected exception' \
      "$latest_log" "$console_log"; then
    echo "$label: fatal startup marker found; see $console_log" >&2
    result=1
  fi
  if ss -ltnH "sport = :$port" | grep -q .; then
    echo "$label: port $port remains open after shutdown" >&2
    result=1
  fi
  if [[ -n "$server_group" ]] && group_alive "$server_group"; then
    echo "$label: process group $server_group remains alive after shutdown" >&2
    result=1
  fi
  active_game_port=""
  active_rcon_port=""

  restore_server_config
  restore_server_properties
  restore_audio_cache
  restore_gate_world

  if (( result == 0 )); then
    echo "$label: ready, clean shutdown, no lingering process or port"
  fi
  return "$result"
}

if [[ "${CINEMARR_GATE_LIBRARY_ONLY:-false}" == true ]]; then
  return 0 2>/dev/null || exit 0
fi

matched=0
failed=0
start_fake_plex || exit 1
for target in "${targets[@]}"; do
  IFS='|' read -r label relative_dir java_home port active_client_task active_server_task active_disable_configuration_cache <<< "$target"
  if [[ "$requested" != "all" && "$requested" != "$label"
      && !( "$requested" == "quilt" && "$label" == *-quilt )
      && !( "$requested" == "fabric" && "$label" == *-fabric ) ]]; then
    continue
  fi
  matched=1
  run_target "$label" "$relative_dir" "$java_home" "$port" || failed=1
done

if [[ "$live_plex_gate" == true ]]; then
  if grep -rIlF --exclude='*.s16le' --exclude='*.png' -- "$CINEMARR_PLEX_TOKEN" "$output_root" \
      > /dev/null 2>&1 \
      || grep -rIlF --exclude='*.s16le' --exclude='*.png' -- "$CINEMARR_PLEX_URL" "$output_root" \
      > /dev/null 2>&1; then
    echo "Credentialed live-Plex gate evidence exposed a credential or server address" >&2
    failed=1
  fi
fi

if (( matched == 0 )); then
  echo "Unknown target '$requested'" >&2
  exit 2
fi
if (( failed != 0 )); then
  exit 1
fi
echo "Dedicated-server gate passed for $requested"
