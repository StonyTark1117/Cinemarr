#!/usr/bin/env python3
import base64
import gzip
import importlib.util
import json
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('weather', SCRIPTS / 'acceptance-weather-state.py')
weather = importlib.util.module_from_spec(spec)
spec.loader.exec_module(weather)


def tag(kind, name, data):
    key = name.encode()
    return bytes([kind]) + struct.pack('>H', len(key)) + key + data


def fixture(schema, progress=12):
    fields = weather.schema_fields(schema)
    parent = next(iter(fields))[0]
    values = b''.join(tag(kind, key, struct.pack('>' + weather.FORMATS[kind],
                                              1 if kind == 1 else 12345))
                      for (_, key), kind in fields.items())
    # Same-named nested fields, arbitrary binary payloads and live world progress
    # are unrelated data. Restoration must leave their current bytes intact.
    values += tag(10, 'nested', tag(1, 'raining', b'\x01') + b'\x00')
    values += tag(7, 'payload', struct.pack('>i', 5) + b'abcde')
    values += tag(4, 'Time', struct.pack('>q', progress))
    return gzip.compress(b'\x0a\x00\x00' + tag(10, parent, values + b'\x00') + b'\x00', mtime=0)


class WeatherTest(unittest.TestCase):
    def test_each_saved_schema_restores_weather_without_rolling_back_progress(self):
        for schema in ('legacy', 'modern', '26'):
            original = fixture(schema)
            prepared, snapshot, proof = weather.transform(original, schema)
            _, fields = weather.parse(prepared, schema)
            self.assertTrue(proof['nonWeatherBytesUnchanged'])
            self.assertTrue(all(value == (0 if kind == 1 else weather.CLEAR_TICKS)
                                for kind, value, _ in fields.values()))
            # Simulate a later world save, then merge the original weather only.
            later, _, _ = weather.transform(fixture(schema, 900), schema)
            restored, _, restore_proof = weather.transform(later, schema, snapshot)
            self.assertEqual(gzip.decompress(restored), gzip.decompress(fixture(schema, 900)))
            self.assertNotEqual(gzip.decompress(restored), gzip.decompress(original))
            self.assertTrue(restore_proof['nonWeatherBytesUnchanged'])
            self.assertEqual(weather.transform(prepared, schema)[0], prepared)

    def test_malformed_or_wrong_schema_never_produces_an_update(self):
        good = fixture('modern')
        raw = gzip.decompress(good)
        invalid = [b'broken', gzip.compress(raw + b'trailing'), good[:-8],
                   gzip.compress(raw.replace(tag(1, 'raining', b'\x01'),
                                             tag(1, 'raining', b'\x02'), 1)),
                   gzip.compress(raw.replace(tag(1, 'raining', b'\x01'), b'', 1)),
                   gzip.compress(raw.replace(tag(1, 'raining', b'\x01'),
                                             tag(1, 'raining', b'\x01') * 2, 1))]
        for data in invalid:
            with self.assertRaises((ValueError, OSError, EOFError, struct.error)):
                weather.transform(data, 'modern')
        with self.assertRaises(ValueError):
            weather.transform(good, '26')
        with self.assertRaises(ValueError):
            weather.transform(good, 'modern', {'schema': 'modern', 'fields': {'raining': 0}})
        with self.assertRaises(ValueError):
            weather.transform(gzip.compress(b'x' * (weather.LIMIT + 1)), 'modern')

    def test_actual_shell_prepare_restore_and_lost_response_cleanup(self):
        for profile, schema, filename in [
                ('1.7.10-forge', 'legacy', 'world/level.dat'),
                ('1.20.1-quilt', 'modern', 'world/level.dat'),
                ('26.2-fabric', '26', 'world/data/minecraft/weather.dat')]:
            for lost_response in (False, True):
                with self.subTest(profile=profile, lost_response=lost_response), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    original = fixture(schema)
                    (root / 'content').write_text(base64.b64encode(original).decode())
                    if lost_response:
                        (root / 'lose-update-response').touch()
                    script = r'''
set -euo pipefail
repo_root=$1; mock_root=$2; label=$3; expected_path=$4; server_id=fixture
CINEMARR_GATE_OUTPUT_ROOT="$mock_root/evidence"
server_state=SERVER_STATUS_RUNNING
get_server() { jq -cn --arg status "$server_state" '{server:{status:$status}}'; }
api_call() {
  [[ $(jq -r '.path' <<<"$2") == "$expected_path" ]] || return 1
  case "$1" in
    */GetFile) jq -cn --arg content "$(cat "$mock_root/content")" '{content:$content}' ;;
    */UpdateFile)
      jq -r '.content' <<<"$2" | tr -d '\n' > "$mock_root/content"
      if [[ -f "$mock_root/lose-update-response" ]]; then
        rm "$mock_root/lose-update-response"; return 1
      fi ;;
    *) return 1 ;;
  esac
}
source "$repo_root/scripts/acceptance-weather-state.sh"
if prepare_acceptance_weather; then exit 21; fi
server_state=SERVER_STATUS_STOPPED
if [[ -f "$mock_root/lose-update-response" ]]; then
  if prepare_acceptance_weather; then exit 22; fi
else
  prepare_acceptance_weather
fi
[[ -n "$acceptance_weather_original" ]]
server_state=SERVER_STATUS_RUNNING
if restore_acceptance_weather; then exit 23; fi
server_state=SERVER_STATUS_STOPPED
restore_acceptance_weather
[[ -z "$acceptance_weather_original" ]]
restore_acceptance_weather
'''
                    result = subprocess.run(['bash', '-c', script, 'weather-test', str(SCRIPTS.parent),
                                             directory, profile, filename], capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(gzip.decompress(base64.b64decode((root / 'content').read_text())),
                                     gzip.decompress(original))
                    proof = json.loads((root / 'evidence' / (profile + '.weather-state.json')).read_text())
                    self.assertTrue(proof['restored'])
                    self.assertEqual(proof['original']['fields'], proof['restoration']['weather'])


if __name__ == '__main__':
    unittest.main()
