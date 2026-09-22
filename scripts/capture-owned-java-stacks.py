#!/usr/bin/env python3
"""Capture bounded failure diagnostics before the gate cleans up its own JVMs."""
import argparse
import json
from pathlib import Path
import subprocess


def identity(pid):
    stat = (Path('/proc') / str(pid) / 'stat').read_text()
    fields = stat[stat.rfind(')') + 2:].split()
    return int(fields[1]), fields[19]


def owned(pid, roots, snapshot):
    visited = set()
    while pid in snapshot and pid not in visited:
        if pid in roots:
            return True
        visited.add(pid)
        pid = snapshot[pid][0]
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('roots', nargs='+', type=int)
    args = parser.parse_args()
    snapshot = {}
    for proc in Path('/proc').iterdir():
        if proc.name.isdigit():
            try:
                snapshot[int(proc.name)] = identity(int(proc.name))
            except (OSError, ValueError, IndexError):
                pass
    args.output.mkdir(parents=True, exist_ok=True)
    recorded = []
    for pid in snapshot:
        if not owned(pid, args.roots, snapshot):
            continue
        try:
            proc = Path('/proc') / str(pid)
            executable = (proc / 'exe').resolve(strict=True)
            if executable.name != 'java' or identity(pid) != snapshot[pid]:
                continue
            # Use the same JDK as the selected process. Never record argv or env.
            command = executable.with_name('jcmd')
            if not command.is_file():
                continue
            recorded.append({'pid': pid, 'parent': snapshot[pid][0],
                             'startTicks': snapshot[pid][1], 'java': str(executable)})
            with (args.output / f'{pid}.threads.txt').open('w') as output:
                try:
                    subprocess.run([str(command), str(pid), 'Thread.print'],
                                   stdout=output, stderr=subprocess.STDOUT, timeout=10)
                except subprocess.TimeoutExpired:
                    output.write('\nThread attachment timed out after ten seconds.\n')
        except (OSError, ValueError, IndexError):
            continue
    (args.output / 'processes.json').write_text(json.dumps(recorded, indent=2) + '\n')


if __name__ == '__main__':
    main()
