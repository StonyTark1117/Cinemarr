#!/usr/bin/env python3
"""Exercise two live legacy video resource/sound reloads on owned private X.

This verifies fresh reload and render events, not physical A/V acceptance. The
caller must subsequently record PCM and review fixed world-view captures.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import time

spec = importlib.util.spec_from_file_location('world_capture', Path(__file__).with_name('capture-post-reconnect-video.py'))
world = importlib.util.module_from_spec(spec)
spec.loader.exec_module(world)

MARKERS = ('Reloading ResourceManager:', 'SoundSystem shutting down...',
           'Starting up SoundSystem...', 'OpenAL initialized.', 'Sound engine started')
SESSION = re.compile(r'Acceptance video session: controller=\S+ session=(\S+) generation=([0-9]+) status=(\S+) item=(\S*)')


def session(text):
    values = SESSION.findall(text)
    if not values or values[-1][2] != 'PLAYING':
        raise RuntimeError('Resource reload requires an active video session')
    return values[-1]


def completed_reload(text):
    cursor = 0
    for marker in MARKERS:
        index = text.find(marker, cursor)
        if index < 0:
            return None
        cursor = index + len(marker)
    return cursor


def exercise(logs, output, gate_pid):
    if output.exists():
        raise RuntimeError('Refusing to overwrite reload evidence')
    controls = {role: world.control_path(log, role) for role, log in logs.items()}
    if (logs['leader'].parent != logs['follower'].parent
            or any(log.name != '1.7.10-forge.audio-' + role + '.console.log' for role, log in logs.items())):
        raise RuntimeError('Expected the paired legacy video case logs')
    desktops = {role: world.PrivateMinecraftWindow(log.read_text(errors='replace'), gate_pid) for role, log in logs.items()}
    if desktops['leader'].xpid == desktops['follower'].xpid:
        raise RuntimeError('Reload clients must use distinct owned private displays')
    initial = {role: session(log.read_text(errors='replace')) for role, log in logs.items()}
    if initial['leader'] != initial['follower']:
        raise RuntimeError('Reload clients must share the same playing session')
    output.mkdir(parents=True)
    actions = []
    for role, desktop in desktops.items():
        log = logs[role]
        for cycle in range(1, 3):
            # In legacy Minecraft, T can also leave chat open after F3+T.
            # Establish the world view for EVERY cycle, never send the next
            # reload into a screen or guess whether Escape would open a menu.
            offset = len(log.read_text(errors='replace'))
            desktop.validate()
            controls[role].write_text('resource-reload-' + str(time.monotonic_ns()) + '|video:open-ui\n')
            deadline = time.monotonic() + 15
            while True:
                desktop.validate()
                matches = world.UI.findall(log.read_text(errors='replace')[offset:])
                if matches and matches[-1] == ('true' if role == 'leader' else 'false'):
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError('No fresh correctly privileged reload controller for ' + role)
                time.sleep(0.1)
            time.sleep(0.3)
            desktop.escape()
            before = log.read_text(errors='replace')
            before_frame = world.latest_frame(before)
            if session(before) != initial[role]:
                raise RuntimeError('Session changed before reload')
            offset = len(before)
            desktop.reload_resources()
            deadline = time.monotonic() + 60
            while True:
                desktop.validate()
                current = log.read_text(errors='replace')
                if len(current) < offset or session(current) != initial[role]:
                    raise RuntimeError('Session or log changed during reload')
                fresh = current[offset:]
                end = completed_reload(fresh)
                frames = world.FRAME.findall(fresh[end:]) if end is not None else []
                if frames:
                    after_frame = world.latest_frame(fresh[end:])
                    if world.advanced(before_frame, after_frame):
                        break
                if time.monotonic() >= deadline:
                    raise RuntimeError('No fresh completed sound/resource reload and advancing video for ' + role)
                time.sleep(0.1)
            actions.append(dict(role=role, cycle=cycle, logOffset=offset,
                                session=initial[role], beforeFrame=before_frame, afterFrame=after_frame,
                                freshEvidenceSha256=hashlib.sha256(fresh.encode()).hexdigest()))
            (output / 'progress.json').write_text(json.dumps(dict(reloadCompleted=False,
                physicalAudioAndVisualReviewPending=True, actions=actions), indent=2) + '\n')
            # A second deliberate reload tests source teardown/recreation again;
            # this is not a retry after a failed first action.
            time.sleep(2)
    result = dict(reloadCompleted=True, physicalAudioAndVisualReviewPending=True,
                  scope='Two F3+T resource/sound reloads per client during the same active legacy video session',
                  actions=actions, privateX={role: desktop.xpid for role, desktop in desktops.items()})
    (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--leader-log', type=Path, required=True)
    parser.add_argument('--follower-log', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--gate-pid', type=int, required=True)
    args = parser.parse_args()
    exercise(dict(leader=args.leader_log, follower=args.follower_log), args.output, args.gate_pid)
    print('Both clients completed two video resource/sound reloads; post-reload physical A/V review remains required.')


if __name__ == '__main__':
    main()
