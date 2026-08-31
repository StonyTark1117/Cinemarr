#!/usr/bin/env bash
set -uo pipefail

if (( $# == 0 )); then
  echo "Usage: $0 <gradle command...>" >&2
  exit 2
fi

retry_sleep=${CINEMARR_GRADLE_RETRY_SLEEP_SECONDS:-15}
[[ "$retry_sleep" =~ ^[0-9]+$ ]] || {
  echo "CINEMARR_GRADLE_RETRY_SLEEP_SECONDS must be a non-negative integer" >&2
  exit 2
}

log_file=$(mktemp "${TMPDIR:-/tmp}/cinemarr-gradle-download.XXXXXX.log")
cleanup() {
  local status=$?
  trap - EXIT
  rm -f -- "$log_file"
  exit "$status"
}
trap cleanup EXIT

for attempt in 1 2; do
  "$@" 2>&1 | tee "$log_file"
  status=${PIPESTATUS[0]}
  (( status == 0 )) && exit 0

  # Forge's Mavenizer currently hard-codes a five-second HTTP timeout. Retry
  # only a positively identified upstream artifact-download failure; compile,
  # test, inspection, and runtime failures remain terminal on their first run.
  if (( attempt == 1 )) \
      && grep -Eq 'Failed to download https://(piston-data|piston-meta|launcher\.mojang|maven\.minecraftforge)\.' "$log_file" \
      && grep -Eq '(connect timed out|Connection reset|HTTP response code: 5[0-9][0-9])' "$log_file"; then
    echo "Upstream Minecraft/Forge artifact download failed; retrying once with the populated cache."
    sleep "$retry_sleep"
    continue
  fi

  exit "$status"
done

exit "$status"
