#!/usr/bin/env python3
import gzip
import base64
import importlib.util
import json
import pathlib
import struct
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("player_state", pathlib.Path(__file__).with_name("legacy-probe-player-state.py"))
state = importlib.util.module_from_spec(spec)
spec.loader.exec_module(state)


def tag(kind, key, value):
    name = key.encode()
    return bytes([kind]) + struct.pack(">H", len(name)) + name + value


def fixture(alive, modern=False, modern26=False):
    data = b"\x0a\x00\x00"
    fields = dict(state.FIELDS)
    if modern:
        fields.pop("HealF")
        fields["Health"] = (5, 20.0)
    if modern26:
        fields.pop("FallDistance")
        fields["fall_distance"] = (6, 0.0)
    for key, (kind, value) in fields.items():
        if key in ("Health", "HealF") and not alive:
            value = 0
        if key == "DeathTime" and not alive:
            value = 82
        data += tag(kind, key, struct.pack(">" + state.FORMATS[kind], value))
    # Nested health-like keys and arbitrary inventory bytes must not change.
    data += tag(10, "nested", tag(2, "Health", struct.pack(">h", 7)) + b"\x00")
    data += tag(7, "InventoryFixture", struct.pack(">i", 5) + b"abcde")
    data += b"\x00"
    return gzip.compress(data, mtime=0)


class PlayerStateTest(unittest.TestCase):
    def test_healthy_player_is_byte_identical(self):
        original = fixture(True)
        self.assertEqual(original, state.prepare(original))
        self.assertEqual(original, state.prepare(original, True))

    def test_dead_player_fails_unless_explicitly_prepared(self):
        original = fixture(False)
        with self.assertRaises(ValueError):
            state.prepare(original)
        prepared = state.prepare(original, True)
        self.assertEqual(gzip.decompress(fixture(True)), gzip.decompress(prepared))
        self.assertEqual(prepared, state.prepare(prepared))
        self.assertNotEqual(original, prepared)

    def test_corrupt_and_oversized_inputs_fail_closed(self):
        for original in [b"not gzip", gzip.compress(b"\x0a\x00\x00\x00"),
                         gzip.compress(b"x" * (state.LIMIT + 1)), fixture(False)[:-8]]:
            with self.assertRaises((ValueError, OSError, EOFError)):
                state.prepare(original, True)

    def test_trailing_data_is_not_rewritten(self):
        with self.assertRaises(ValueError):
            state.prepare(gzip.compress(gzip.decompress(fixture(False)) + b"unexpected"), True)

    def test_prior_fall_distance_is_cleared_before_teleport_not_during_playback(self):
        data = gzip.decompress(fixture(True))
        old = tag(5, "FallDistance", struct.pack(">f", 0))
        new = tag(5, "FallDistance", struct.pack(">f", 120))
        original = gzip.compress(data.replace(old, new), mtime=0)
        self.assertEqual(original, state.prepare(original))
        self.assertEqual(data, gzip.decompress(state.prepare(original, True)))

    def test_modern_float_health_and_saved_fall_are_prepared_without_other_changes(self):
        healthy = gzip.decompress(fixture(True, modern=True))
        dead = gzip.decompress(fixture(False, modern=True)).replace(
            tag(5, "FallDistance", struct.pack(">f", 0)),
            tag(5, "FallDistance", struct.pack(">f", 120)))
        original = gzip.compress(dead, mtime=0)
        with self.assertRaises(ValueError):
            state.prepare(original, modern=True)
        prepared = state.prepare(original, True, modern=True)
        self.assertEqual(healthy, gzip.decompress(prepared))
        self.assertEqual(prepared, state.prepare(prepared, modern=True))
        self.assertEqual(prepared, state.prepare(prepared, True, modern=True))
        with self.assertRaises(ValueError):
            state.prepare(original, True)  # Wrong NBT schema must fail closed.
        with self.assertRaises(ValueError):
            state.prepare(fixture(True), True, modern=True)

    def test_actual_modern_gate_prepares_only_stopped_probe_players_and_restores_bytes(self):
        self.check_gate_profile('1.21.1-neoforge', False)

    def test_actual_26_gate_uses_new_player_directory_and_restores_bytes(self):
        self.check_gate_profile('26.2-fabric', True)

    def test_26_double_fall_distance_is_cleared_without_touching_other_tags(self):
        healthy = gzip.decompress(fixture(True, modern=True, modern26=True))
        dead = gzip.decompress(fixture(False, modern=True, modern26=True)).replace(
            tag(6, "fall_distance", struct.pack(">d", 0)),
            tag(6, "fall_distance", struct.pack(">d", 120)))
        original = gzip.compress(dead, mtime=0)
        prepared = state.prepare(original, True, modern=True, modern26=True)
        self.assertEqual(healthy, gzip.decompress(prepared))
        self.assertEqual(prepared, state.prepare(prepared, modern=True, modern26=True))
        with self.assertRaises(ValueError):
            state.prepare(original, True, modern=True)

    def check_gate_profile(self, profile, modern26):
        scripts = pathlib.Path(__file__).parent
        source = (scripts / 'run-discopanel-real-plex-gate.sh').read_text()
        functions = source.split('legacy_probe_files=()', 1)[1].split('\nplex_cinemarr_session_count()', 1)[0]
        names = ['42ca340c-04ef-3fa1-b363-ebb5d33ee76d', 'ab770eaf-1e72-3375-9500-a23286375fad']
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            original = base64.b64encode(fixture(False, modern=True, modern26=modern26)).decode()
            player_dir = 'world/players/data' if modern26 else 'world/playerdata'
            mode = 'check-modern26' if modern26 else 'check-modern'
            files = [{'path': player_dir + '/' + name + '.dat'} for name in names]
            (root / 'files.json').write_text(json.dumps({'files': files}))
            for name in names:
                (root / (name + '.dat')).write_text(original)
            script = '''
set -euo pipefail
repo_root=$1; mock_root=$2; label=$3; check_mode=$4; expected_player_dir=$5; server_id=fixture
server_state=SERVER_STATUS_RUNNING
get_server() { jq -cn --arg status "$server_state" '{server:{status:$status}}'; }
api_call() {
  local path content
  case "$1" in
    */ListFiles)
      [[ $(jq -r '.path' <<<"$2") == "$expected_player_dir" ]] || return 1
      cat "$mock_root/files.json" ;;
    */GetFile)
      path=$(jq -r '.path' <<<"$2")
      jq -cn --arg content "$(cat "$mock_root/${path##*/}")" '{content:$content}' ;;
    */UpdateFile)
      path=$(jq -r '.path' <<<"$2"); content=$(jq -r '.content' <<<"$2")
      printf '%s' "$content" > "$mock_root/${path##*/}" ;;
    *) return 1 ;;
  esac
}
legacy_probe_files=()
''' + functions + '''
if prepare_legacy_probe_players; then exit 21; fi
server_state=SERVER_STATUS_STOPPED
prepare_legacy_probe_players
[[ ${#legacy_probe_files[@]} == 2 ]]
for path in "${legacy_probe_files[@]}"; do
  python3 "$repo_root/scripts/legacy-probe-player-state.py" "$check_mode" < "$mock_root/${path##*/}"
done
# Cleanup executes even if the runtime fails; it must restore both originals.
restore_legacy_probe_players
[[ ${#legacy_probe_files[@]} == 0 ]]
'''
            result = subprocess.run(['bash', '-c', script, 'probe-state-test', str(scripts.parent), directory, profile, mode, player_dir], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual([original, original], [(root / (name + '.dat')).read_text() for name in names])


if __name__ == "__main__":
    unittest.main()
