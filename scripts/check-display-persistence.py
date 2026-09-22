#!/usr/bin/env python3
"""Compare settings restored by a fresh server with the preceding client scenario."""
import argparse
import hashlib
import json
from pathlib import Path
import re

KEYS = ('controller', 'tv', 'revision', 'origin', 'layout', 'mapping', 'requested', 'width', 'height')
MARKER = 'Acceptance display restored: '


def check(expected, text):
    if expected.get('passed') is not True:
        raise ValueError('The preceding display scenario did not pass')
    rows = expected['steps'][-1]['clients']['leader']
    if len(rows) != 3 or sorted(row['origin'] for row in rows.values()) != ['CUSTOM', 'CUSTOM', 'QUICK']:
        raise ValueError('Expected two edited custom TVs and one Quick TV')
    restored = {}
    for line in text.splitlines():
        if MARKER not in line:
            continue
        row = dict(re.findall(r'(\w+)=([^\s<>]+)', line.split(MARKER, 1)[1]))
        controller = row.get('controller')
        if controller not in rows:
            continue
        if any(key not in row or key not in rows[controller] or row[key] != rows[controller][key] for key in KEYS):
            raise ValueError('Restored display settings differ for controller ' + controller)
        if controller in restored:
            raise ValueError('Duplicate restore record for controller ' + controller)
        restored[controller] = {key: row[key] for key in KEYS}
    if set(restored) != set(rows):
        raise ValueError('Missing restored TV registrations: ' + ', '.join(sorted(set(rows) - set(restored))))
    return restored


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected', type=Path, required=True)
    parser.add_argument('--log', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = dict(passed=False, scope='Display settings restored into the registry by a fresh server process',
                  expectedSha256=hashlib.sha256(args.expected.read_bytes()).hexdigest(),
                  logSha256=hashlib.sha256(args.log.read_bytes()).hexdigest())
    try:
        result['restored'] = check(json.loads(args.expected.read_text()), args.log.read_text(errors='replace'))
        result['passed'] = True
    except (ValueError, KeyError, IndexError) as failure:
        result['error'] = str(failure)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    if not result['passed']:
        parser.exit(1, result['error'] + '\n')


if __name__ == '__main__':
    main()
