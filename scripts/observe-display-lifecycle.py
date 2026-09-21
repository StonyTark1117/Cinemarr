#!/usr/bin/env python3
"""Seek, reload and reconnect checks for the three-TV scene on private X."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import time

from private_minecraft_window import PrivateMinecraftWindow
from video_audio_stability import Stability

spec = importlib.util.spec_from_file_location('display_probe', Path(__file__).with_name('observe-video-display.py'))
display = importlib.util.module_from_spec(spec)
spec.loader.exec_module(display)


def reload_complete(text, legacy):
    markers = (('Reloading ResourceManager:', 'SoundSystem shutting down...',
                'Starting up SoundSystem...', 'OpenAL initialized.', 'Sound engine started')
               if legacy else ('Reloading ResourceManager:', 'OpenAL initialized', 'Sound engine started'))
    cursor = 0
    for marker in markers:
        found = text.find(marker, cursor)
        if found < 0:
            return None
        cursor = found + len(marker)
    return cursor


def all_advanced(before, after):
    return before.keys() == after.keys() and all(after[tv]['ptsUs'] > before[tv]['ptsUs'] for tv in before)


def seek_replaced(before, after):
    stable = ('timeline', 'stream', 'revision', 'layout', 'mapping', 'requested', 'screen', 'origin')
    return before.keys() == after.keys() and all(
        after[tv]['timelineGeneration'] > before[tv]['timelineGeneration']
        and after[tv]['streamGeneration'] > before[tv]['streamGeneration']
        and all(before[tv][key] == after[tv][key] for key in stable) for tv in before)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--leader-log', type=Path, required=True)
    parser.add_argument('--follower-log', type=Path, required=True)
    parser.add_argument('--gate-pid', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--after-reconnect', type=Path)
    args = parser.parse_args()
    args.output.mkdir(exist_ok=False, parents=True)
    logs = dict(leader=args.leader_log, follower=args.follower_log)
    texts = lambda: {role: log.read_text(errors='replace') for role, log in logs.items()}
    desktops = {}
    for role, text in texts().items():
        bindings = re.findall(r'Private Xvfb ready: display=:[0-9]+ pid=[0-9]+ geometry=(640x480x24|1280x720x24)(?:\s|$)', text)
        if len(bindings) != 1:
            raise RuntimeError('Expected one owned private display binding')
        desktops[role] = PrivateMinecraftWindow(text, args.gate_pid, geometry=bindings[0], wait_seconds=120)
    if desktops['leader'].xpid == desktops['follower'].xpid:
        raise RuntimeError('Clients must own independent private displays')
    controls = {role: log.with_name(log.name.removesuffix('.console.log') + '.control') for role, log in logs.items()}
    if any(p.is_symlink() or not p.is_file() for p in controls.values()):
        raise RuntimeError('Missing owned control files')
    phases = []

    def wait(predicate=lambda pair: True):
        stable = Stability(texts())
        deadline = time.monotonic() + 120
        while True:
            for desktop in desktops.values():
                desktop.validate()
            current = texts()
            display.require_clean_transfer_logs(current)
            pair = {role: display.states(text) for role, text in current.items()}
            ready = (display.matching(pair['leader'], pair['follower'], 'PLAYING')
                     and predicate(pair) and all(display.render_matches(pair[role], display.rendered(text),
                                                                          display.decoded(text))
                                                for role, text in current.items()))
            audio_ready = stable.update(current, time.monotonic())
            if ready and audio_ready:
                return pair
            if time.monotonic() >= deadline:
                raise RuntimeError('Three-TV lifecycle did not recover: ' + stable.reason)
            time.sleep(.1)

    def world_view(role):
        offset = len(logs[role].read_text(errors='replace'))
        display.publish(controls[role], 'video:world-view')
        deadline = time.monotonic() + 30
        while 'Acceptance video world view: screen=none' not in logs[role].read_text(errors='replace')[offset:]:
            desktops[role].validate()
            if time.monotonic() >= deadline:
                raise RuntimeError('No fresh world-view acknowledgement before capture/reload')
            time.sleep(.1)
        time.sleep(.3)

    def capture(name, pair):
        captures = []
        for role, desktop in desktops.items():
            world_view(role)
            path = args.output / (name + '-' + role + '.png')
            desktop.capture(path)
            captures.append(dict(path=path.name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        phases.append(dict(phase=name, televisions=pair, captures=captures))
        (args.output / 'progress.json').write_text(json.dumps(dict(completed=False, phases=phases), indent=2) + '\n')

    initial = wait()
    if args.after_reconnect:
        previous = json.loads(args.after_reconnect.read_text())
        if not previous['completed'] or initial != previous['finalTelevisions']:
            raise RuntimeError('Reconnect changed the shared timeline, streams or display settings')
        old_log = args.follower_log.with_name(args.follower_log.name.replace('.console.log', '.pre-reconnect.console.log'))
        if old_log.is_symlink() or 'Acceptance client media reset complete' not in old_log.read_text(errors='replace'):
            raise RuntimeError('Reconnect is missing prior client cleanup')
        capture('reconnected', initial)
    else:
        display.publish(controls['leader'], 'video:seek-forward')
        after = wait(lambda pair: seek_replaced(initial['leader'], pair['leader']))
        capture('seek', after)
        for role in logs:
            for cycle in (1, 2):
                world_view(role)
                before_text = logs[role].read_text(errors='replace')
                before_frames = display.rendered(before_text)
                offset = len(before_text)
                desktops[role].reload_resources()
                legacy = args.leader_log.name.startswith('1.7.10-forge.')

                def recovered(pair):
                    fresh = logs[role].read_text(errors='replace')[offset:]
                    end = reload_complete(fresh, legacy)
                    if pair != after:
                        raise RuntimeError('Resource reload changed a TV stream, timeline or settings')
                    return end is not None and all_advanced(before_frames, display.rendered(fresh[end:]))

                wait(recovered)
                capture('reload-' + role + '-' + str(cycle), after)
        initial = after
    result = dict(completed=True, finalTelevisions=initial, phases=phases,
                  directVisualReviewPending=True, physicalAudioRequiresEnclosingGate=True)
    (args.output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print('Three-TV lifecycle checks completed; original captures and enclosing PCM require review')


if __name__ == '__main__':
    main()
