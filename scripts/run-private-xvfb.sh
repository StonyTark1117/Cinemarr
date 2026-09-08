#!/usr/bin/env bash
# Atomically reserve a private display; never inherit the user's desktop.
set -euo pipefail
if (( $# < 2 )) || [[ ! "$1" =~ ^[1-9][0-9]{1,3}x[1-9][0-9]{1,3}x24$ ]]; then
  echo 'Usage: run-private-xvfb.sh WIDTHxHEIGHTx24 COMMAND [ARGS...]' >&2
  exit 2
fi
geometry=$1
shift
private_dir=$(mktemp -d -t cinemarr-xvfb.XXXXXXXX)
display_file="$private_dir/display"
error_file="$private_dir/server.log"
xvfb_pid=''
command_pid=''
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$command_pid" ]] && kill -0 -- "-$command_pid" 2>/dev/null; then
    kill -TERM -- "-$command_pid" 2>/dev/null || true
    for _ in {1..40}; do kill -0 -- "-$command_pid" 2>/dev/null || break; sleep .05; done
    kill -KILL -- "-$command_pid" 2>/dev/null || true
    wait "$command_pid" 2>/dev/null || true
  fi
  if [[ -n "$xvfb_pid" ]]; then
    kill -TERM "$xvfb_pid" 2>/dev/null || true
    for _ in {1..40}; do kill -0 "$xvfb_pid" 2>/dev/null || break; sleep .05; done
    kill -KILL "$xvfb_pid" 2>/dev/null || true
    wait "$xvfb_pid" 2>/dev/null || true
  fi
  rm -f -- "$display_file" "$error_file"
  rmdir -- "$private_dir"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Stay clear of low display numbers used by desktop/Xwayland startup. Binding
# is performed by Xvfb itself, not by a racy pre-check of /tmp/.X*-lock files.
# A collision must fail before another number is tried; an existing X server
# is never used as the command's display.
display_number=''
for display_candidate in {90..190}; do
  env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY Xvfb ":$display_candidate" -displayfd 3 \
    -screen 0 "$geometry" -ac +extension GLX +render -noreset -nolisten tcp \
    3>"$display_file" >"$error_file" 2>&1 &
  xvfb_pid=$!
  for _ in {1..200}; do
    [[ -s "$display_file" ]] && break
    kill -0 "$xvfb_pid" 2>/dev/null || break
    sleep .05
  done
  display_number=$(<"$display_file")
  if [[ "$display_number" == "$display_candidate" ]] && kill -0 "$xvfb_pid" 2>/dev/null; then break; fi
  kill -TERM "$xvfb_pid" 2>/dev/null || true
  for _ in {1..40}; do kill -0 "$xvfb_pid" 2>/dev/null || break; sleep .05; done
  kill -KILL "$xvfb_pid" 2>/dev/null || true
  wait "$xvfb_pid" 2>/dev/null || true
  xvfb_pid='';display_number=''
done
if [[ -z "$display_number" || -z "$xvfb_pid" ]]; then
  echo 'Private Xvfb did not allocate a live display' >&2
  exit 1
fi
echo "Private Xvfb ready: display=:$display_number pid=$xvfb_pid geometry=$geometry" >&2
# A separate command group lets signal cleanup include its descendants without
# signalling the caller, the other test client, or unrelated desktop processes.
setsid env -u WAYLAND_DISPLAY -u XAUTHORITY DISPLAY=":$display_number" XDG_SESSION_TYPE=x11 "$@" &
command_pid=$!
set +e
wait "$command_pid"
status=$?
echo "Private X command exited: status=$status" >&2
exit "$status"
