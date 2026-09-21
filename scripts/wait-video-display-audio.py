#!/usr/bin/env python3
"""Keep existing A/V thresholds while excluding explicitly non-playing TVs."""
import argparse
from pathlib import Path
import subprocess
import time
from video_audio_stability import Stability


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--leader-log', type=Path, required=True)
    parser.add_argument('--follower-log', type=Path, required=True)
    parser.add_argument('--leader-pid', type=int, required=True)
    parser.add_argument('--follower-pid', type=int, required=True)
    args = parser.parse_args()
    def texts():
        return {role: getattr(args, role+'_log').read_text(errors='replace') for role in ('leader', 'follower')}
    stability = Stability(texts())
    deadline = time.monotonic()+180
    while time.monotonic() < deadline:
        groups = {int(parts[0]) for line in subprocess.check_output(['ps', '-eo', 'pgid=,stat='], text=True).splitlines()
                  if len(parts := line.split()) == 2 and not parts[1].startswith('Z')}
        if not {args.leader_pid, args.follower_pid} <= groups:
            raise SystemExit('A display client exited during A/V stabilization')
        if stability.update(texts(), time.monotonic()):
            print('Every playing TV maintained fresh, identity-bound A/V for eight seconds')
            return
        time.sleep(1)
    raise SystemExit('Display A/V did not stabilize: '+stability.reason)


if __name__ == '__main__':
    main()
