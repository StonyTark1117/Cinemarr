#!/usr/bin/env python3
"""Bind retained-guest results to every file in the current native payload."""
import json
from pathlib import Path
import re
import sys


def indexed_files(document):
    rows = document['files']
    assert rows, 'Empty bundle evidence'
    result = {}
    for row in rows:
        name, digest = row['path'], row['sha256']
        assert name and not name.startswith('/') and '\\' not in name and ':' not in name
        assert '..' not in name.split('/') and name not in result, 'Invalid or duplicate bundle path'
        assert re.fullmatch('[0-9a-f]{64}', digest), 'Invalid bundle digest'
        result[name] = digest
    return result


def verify(expected, used, benchmark):
    assert expected['schema'] == used['schema'] == 1
    run_id = expected['runId']
    assert re.fullmatch(r'\d{8}T\d{6}Z', run_id) and used['runId'] == run_id, 'Stale run identity'
    assert used['workDirectory'] == 'C:\\CinemarrNativeSmoke\\runs\\' + run_id, 'Shared or stale work directory'
    inputs, actual = indexed_files(expected), indexed_files(used)
    assert actual == inputs, 'Used native bundle differs from the current payload'
    assert 'lib/core-1.0.0.jar' in inputs and any(name.startswith('classes/') for name in inputs)
    rows = benchmark['rows']
    assert [row['resolution'] for row in rows] == ['144p', '480p', '1080p']
    for row in rows:
        assert row['fixtureSha256'] == inputs['fixtures/' + row['resolution'] + '.ts'], 'Benchmark used a stale fixture'


def main():
    if len(sys.argv) != 4:
        print('Usage: verify-native-bundle-evidence.py INPUT_MANIFEST USED_MANIFEST BENCHMARK', file=sys.stderr)
        return 2
    try:
        verify(*(json.loads(Path(name).read_text()) for name in sys.argv[1:]))
    except (AssertionError, KeyError, TypeError, ValueError, OSError):
        print('Native payload evidence is missing, stale, malformed, or differs from the current bundle', file=sys.stderr)
        return 1
    print('Current native bundle and benchmark fixture identities verified')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
