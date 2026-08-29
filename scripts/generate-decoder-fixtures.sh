#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z "$1" ]]; then
  echo "Usage: $0 OUTPUT_DIRECTORY" >&2
  exit 2
fi
if ! command -v ffmpeg >/dev/null || ! command -v ffprobe >/dev/null || ! command -v sha256sum >/dev/null; then
  echo "ffmpeg, ffprobe, and sha256sum are required" >&2
  exit 1
fi

output_dir=$1
mkdir -p -- "$output_dir"
labels=(144p 240p 480p 720p 1080p 1440p 4k)
sizes=(256x144 426x240 854x480 1280x720 1920x1080 2560x1440 3840x2160)
manifest_tmp=$(mktemp --tmpdir="$output_dir" cinemarr-hwtest-manifest.XXXXXX)
cleanup_manifest() { rm -f -- "$manifest_tmp"; }
trap cleanup_manifest EXIT

printf '{\n  "schema": 1,\n  "format": "H.264 High/AAC LC MPEG-TS",\n  "durationSeconds": 0.6,\n  "fixtures": [\n' >"$manifest_tmp"
for index in "${!labels[@]}"; do
  label=${labels[$index]}
  size=${sizes[$index]}
  fixture_tmp=$(mktemp --tmpdir="$output_dir" "cinemarr-hwtest-${label}.XXXXXX.ts")
  ffmpeg -hide_banner -loglevel error -y -threads 1 \
    -f lavfi -i "testsrc2=size=${size}:rate=30" \
    -f lavfi -i 'sine=frequency=997:sample_rate=48000' \
    -t 0.6 -map 0:v:0 -map 1:a:0 -pix_fmt yuv420p -c:v libx264 -profile:v high \
    -preset medium -g 30 -keyint_min 30 -sc_threshold 0 -c:a aac -b:a 128k -ac 2 -ar 48000 \
    -metadata service_name=CinemarrDecoderFixture -metadata service_provider=Cinemarr \
    -f mpegts "$fixture_tmp"
  profile=$(ffprobe -v error -select_streams v:0 -show_entries stream=profile -of default=nw=1:nk=1 "$fixture_tmp" | head -n 1)
  video_codec=$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of default=nw=1:nk=1 "$fixture_tmp" | head -n 1)
  audio_codec=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of default=nw=1:nk=1 "$fixture_tmp" | head -n 1)
  if [[ "$profile" != High || "$video_codec" != h264 || "$audio_codec" != aac ]]; then
    rm -f -- "$fixture_tmp"
    echo "Generated $label fixture does not satisfy H.264 High/AAC" >&2
    exit 1
  fi
  mv -f -- "$fixture_tmp" "$output_dir/$label.ts"
  digest=$(sha256sum "$output_dir/$label.ts")
  digest=${digest%% *}
  comma=,
  if (( index + 1 == ${#labels[@]} )); then comma=; fi
  printf '    {"resolution":"%s","dimensions":"%s","sha256":"%s"}%s\n' \
    "$label" "$size" "$digest" "$comma" >>"$manifest_tmp"
done
printf '  ]\n}\n' >>"$manifest_tmp"
mv -f -- "$manifest_tmp" "$output_dir/manifest.json"
trap - EXIT
