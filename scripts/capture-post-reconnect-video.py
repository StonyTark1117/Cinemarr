#!/usr/bin/env python3
"""Fixed post-reconnect private-X captures; pictures still require direct review."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import time
from private_minecraft_window import PrivateMinecraftWindow


UI = re.compile(r"Acceptance video UI: width=320 height=240 widgets=[1-9][0-9]* clipped=0 canControl=(true|false) overlaps=0")
FRAME = re.compile(r"Acceptance video rendered: television=([^ ]+) frameSha256=([0-9a-f]{64}) ptsUs=([0-9]+) rectangles=([1-9][0-9]*)")


def latest_frame(text):
    values = FRAME.findall(text)
    if not values:
        raise RuntimeError("No rendered frame in the current client log")
    tv, sha, pts, rectangles = values[-1]
    return dict(television=tv, frameSha256=sha, ptsUs=int(pts), rectangles=int(rectangles))


def advanced(previous, current):
    if current['television'] != previous['television']:
        raise RuntimeError("Television changed during post-reconnect capture")
    return current['ptsUs'] > previous['ptsUs']


def control_path(log, role):
    suffix = '.audio-' + role + '.console.log'
    if not log.name.endswith(suffix) or log.is_symlink() or not log.is_file():
        raise RuntimeError("Expected the exact current " + role + " client log")
    control = log.with_name(log.name.removesuffix('.console.log') + '.control')
    if not control.is_file() or control.is_symlink():
        raise RuntimeError("Expected existing nonsymlink client control file")
    return control


def collect(logs, output, gate_pid):
    if output.exists():
        raise RuntimeError("Refusing to overwrite post-reconnect evidence")
    controls = {role: control_path(log, role) for role, log in logs.items()}
    if (logs['leader'].parent != logs['follower'].parent
            or logs['leader'].name.removesuffix('.audio-leader.console.log')
            != logs['follower'].name.removesuffix('.audio-follower.console.log')):
        raise RuntimeError("Client logs must belong to the same gate case")
    before = logs['follower'].with_name(logs['follower'].name.replace('.console.log', '.pre-reconnect.console.log'))
    if not before.is_file() or before.is_symlink() or 'Acceptance client media reset complete' not in before.read_text(errors='replace'):
        raise RuntimeError("Missing completed pre-reconnect client cleanup")
    desktops = {role: PrivateMinecraftWindow(log.read_text(errors='replace'), gate_pid) for role, log in logs.items()}
    if desktops['leader'].xpid == desktops['follower'].xpid:
        raise RuntimeError("Clients must use distinct owned private X servers")
    output.mkdir(parents=True)
    captures = []
    # Establish a known UI state for BOTH clients before Escape; blindly sending
    # Escape to the freshly rejoined world would open the pause menu instead.
    for role, desktop in desktops.items():
        offset = len(logs[role].read_text(errors='replace'))
        desktop.validate()
        controls[role].write_text('post-reconnect-' + str(time.monotonic_ns()) + '|video:open-ui\n')
        deadline = time.monotonic() + 15
        while True:
            desktop.validate()
            matches = UI.findall(logs[role].read_text(errors='replace')[offset:])
            if matches and matches[-1] == ('true' if role == 'leader' else 'false'):
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("No fresh correctly privileged controller for " + role)
            time.sleep(0.1)
        time.sleep(0.3)
        desktop.escape()
    previous = {role: latest_frame(log.read_text(errors='replace')) for role, log in logs.items()}
    if previous['leader']['television'] != previous['follower']['television']:
        raise RuntimeError("Clients are rendering different televisions")
    # Three fixed pairs, no content-based retries or best-frame selection.
    for index in range(3):
        time.sleep(1)
        deadline = time.monotonic() + 10
        while True:
            for desktop in desktops.values():
                desktop.validate()
            current = {role: latest_frame(log.read_text(errors='replace')) for role, log in logs.items()}
            if all(advanced(previous[role], current[role]) for role in logs):
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("Rendered timestamps did not advance for both clients")
            time.sleep(0.1)
        for role, desktop in desktops.items():
            path = output / (str(index + 1) + '-' + role + '.png')
            desktop.capture(path)
            captures.append(dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                                 role=role, pair=index + 1, frame=current[role], capturedMonotonic=time.monotonic()))
        previous = current
    result = dict(captureCompleted=True, directVisualReviewPending=True,
                  scope='Three fixed world-view pairs after reconnect and PCM capture; no automatic visual acceptance',
                  captures=captures, privateX={role: desktop.xpid for role, desktop in desktops.items()},
                  preReconnectLogSha256=hashlib.sha256(before.read_bytes()).hexdigest())
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--leader-log', type=Path, required=True)
    parser.add_argument('--follower-log', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--gate-pid', type=int, required=True)
    args = parser.parse_args()
    collect(dict(leader=args.leader_log, follower=args.follower_log), args.output, args.gate_pid)
    print('Six post-reconnect world captures saved; direct visual review remains required.')


if __name__ == '__main__':
    main()
