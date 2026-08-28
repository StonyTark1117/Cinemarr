#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
modern_blocks="$root/src/main/resources/assets/cinemarr/textures/block"
modern_items="$root/src/main/resources/assets/cinemarr/textures/item"
legacy_blocks="$root/platforms/mc1.7.10/forge/src/main/resources/assets/cinemarr/textures/blocks"
legacy_items="$root/platforms/mc1.7.10/forge/src/main/resources/assets/cinemarr/textures/items"
mkdir -p "$modern_blocks" "$modern_items" "$legacy_blocks" "$legacy_items"

pixel() {
    magick -size 16x16 xc:none +antialias "$@"
}

pixel \
    -fill '#242a31' -draw 'rectangle 0,0 15,15' \
    -fill '#59616b' -draw 'rectangle 1,1 14,14' \
    -fill '#06090d' -draw 'rectangle 2,2 13,13' \
    -fill '#101a22' -draw 'rectangle 3,4 12,4 rectangle 3,7 12,7 rectangle 3,10 12,10' \
    -fill '#b3222c' -draw 'rectangle 11,11 12,12' \
    "$modern_blocks/screen_pixel.png"

pixel \
    -fill '#171a1f' -draw 'rectangle 0,0 15,15' \
    -fill '#444b54' -draw 'rectangle 1,1 14,14' \
    -fill '#20252b' -draw 'rectangle 2,2 13,13' \
    -fill '#090b0e' -draw 'rectangle 3,3 12,8' \
    -fill '#bf2633' -draw 'rectangle 4,4 5,5' \
    -fill '#bec5cc' -draw 'rectangle 8,4 11,4 rectangle 8,6 10,6' \
    -fill '#0f1216' -draw 'rectangle 3,10 12,12' \
    -fill '#777f88' -draw 'rectangle 4,11 6,11 rectangle 9,11 11,11' \
    "$modern_blocks/tv_controller.png"

pixel \
    -fill '#111419' -draw 'rectangle 0,0 15,15' \
    -fill '#30363e' -draw 'rectangle 1,1 14,14' \
    -fill '#1c2026' -draw 'rectangle 3,3 12,12' \
    -fill '#0b0d10' -draw 'rectangle 4,4 11,11' \
    -fill '#69717a' -draw 'point 2,2 point 13,2 point 2,13 point 13,13' \
    -fill '#9c202a' -draw 'rectangle 4,13 11,13' \
    "$modern_blocks/tv_casing.png"

pixel \
    -fill '#15181d' -draw 'rectangle 0,0 15,15' \
    -fill '#383e46' -draw 'rectangle 1,1 14,14' \
    -fill '#0a0c0f' -draw 'circle 8,8 13,8' \
    -fill '#252b32' -draw 'circle 8,8 11,8' \
    -fill '#07090b' -draw 'circle 8,8 9,8' \
    -fill '#a12630' -draw 'point 8,8' \
    -fill '#6d747d' -draw 'point 2,2 point 13,2 point 2,13 point 13,13' \
    "$modern_blocks/tv_speaker.png"

pixel \
    -fill '#15191e' -draw 'rectangle 0,0 15,15' \
    -fill '#39414a' -draw 'rectangle 1,1 14,14' \
    -fill '#101419' -draw 'rectangle 2,2 13,13' \
    -fill '#b72632' -draw 'rectangle 7,2 8,12 rectangle 3,7 12,8' \
    -fill '#e0525c' -draw 'rectangle 3,3 4,4 rectangle 11,3 12,4 rectangle 3,11 4,12 rectangle 11,11 12,12' \
    -fill '#6c737c' -draw 'point 2,2 point 13,2 point 2,13 point 13,13' \
    "$modern_blocks/redstone_receiver.png"

make_kit() {
    local name=$1 accent=$2 bars=$3
    local draw=''
    local x
    for ((x=0; x<bars; x++)); do draw+=" rectangle $((3+x*2)),12 $((3+x*2)),13"; done
    pixel \
        -fill '#15181d' -draw 'rectangle 0,0 15,15' \
        -fill '#454c55' -draw 'rectangle 1,1 14,14' \
        -fill '#0a0d11' -draw 'rectangle 2,2 13,10' \
        -fill '#18242d' -draw 'rectangle 3,3 12,9' \
        -fill "$accent" -draw "rectangle 2,10 13,11$draw" \
        -fill '#9aa1a9' -draw 'point 2,2 point 13,2' \
        "$modern_blocks/$name.png"
}

make_kit quick_tv_144p  '#7a2630' 1
make_kit quick_tv_240p  '#9a303b' 2
make_kit quick_tv_480p  '#bc3945' 3
make_kit quick_tv_720p  '#d34a55' 4
make_kit quick_tv_1080p '#e55c66' 5
make_kit quick_tv_1440p '#f07178' 5
make_kit quick_tv_4k    '#f08d92' 6
make_kit quick_tv_8k    '#f5adb0' 6

pixel \
    -fill '#0c0f13' -draw 'roundrectangle 4,0 11,15 2,2' \
    -fill '#3d444d' -draw 'roundrectangle 5,1 10,14 1,1' \
    -fill '#12161b' -draw 'rectangle 6,2 9,13' \
    -fill '#d52d3b' -draw 'rectangle 7,2 8,3' \
    -fill '#c6ccd2' -draw 'rectangle 7,5 8,6 point 6,8 point 9,8 point 7,10 point 8,10' \
    -fill '#747c85' -draw 'rectangle 7,12 8,12' \
    "$modern_items/tv_remote.png"

for texture in "$modern_blocks"/*.png; do
    install -Dm644 "$texture" "$legacy_blocks/$(basename "$texture")"
done
install -Dm644 "$modern_items/tv_remote.png" "$legacy_items/tv_remote.png"
