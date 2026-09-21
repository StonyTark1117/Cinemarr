#!/usr/bin/env python3
"""Independent-TV runtime assertions; retain original private-window captures for review."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import time
from private_minecraft_window import PrivateMinecraftWindow

MARKER = re.compile(r'Acceptance TV display: ([^\r\n<]+)')


def states(text):
    latest = {}
    for match in MARKER.finditer(text):
        fields = dict(re.findall(r'(\w+)=([^\s\]]+)', match[1]))
        required = {'controller', 'television', 'timeline', 'timelineGeneration', 'stream', 'streamGeneration',
                    'status', 'owner', 'origin', 'revision', 'layout', 'mapping', 'requested', 'effective', 'screen'}
        if not required <= fields.keys():
            raise ValueError('Incomplete display evidence')
        for key in ('controller', 'timelineGeneration', 'streamGeneration', 'revision'):
            fields[key] = int(fields[key])
        latest[fields['television']] = fields
    return latest


def matching(leader, follower, status):
    if len(leader) != 3 or leader.keys() != follower.keys():
        return False
    for tv, left in leader.items():
        right = follower[tv]
        if left['owner'] != 'true' or right['owner'] != 'false':
            return False
        if any(left[k] != right[k] for k in left if k != 'owner') or left['status'] != status:
            return False
    values = list(leader.values())
    return (len({(s['timeline'], s['timelineGeneration']) for s in values}) == 1
            and len({s['stream'] for s in values}) == 3)


def unchanged_siblings(before, after, target):
    return before.keys() == after.keys() and all(
        all(before[tv][key] == after[tv][key] for key in ('timeline', 'timelineGeneration', 'stream', 'streamGeneration', 'revision'))
        for tv in before if tv != target)



def rendered(text):
    latest = {}
    pattern = re.compile(r'Acceptance video rendered: television=(\S+) frameSha256=([0-9a-f]{64}) '
                         r'ptsUs=(\d+) rectangles=(\d+) revision=(\d+) raster=(\d+)x(\d+) decoded=(\d+)x(\d+)')
    for m in pattern.finditer(text):
        latest[m[1]] = dict(zip(('sha256', 'ptsUs', 'rectangles', 'revision', 'width', 'height', 'decodedWidth', 'decodedHeight'),
                               [m[2]] + [int(m[i]) for i in range(3, 10)]))
    return latest


def render_matches(current, receipts):
    for tv, state in current.items():
        receipt = receipts.get(tv)
        if not receipt or receipt['revision'] != state['revision'] or receipt['rectangles'] < 1:
            return False
        expected = (tuple(map(int, state['screen'].split('x'))) if state['mapping'] == 'ONE_PIXEL_PER_BLOCK'
                    else (receipt['decodedWidth'], receipt['decodedHeight']))
        if (receipt['width'], receipt['height']) != expected or min(expected) < 1:
            return False
        if state['effective'] != '0x0' and state['effective'] != str(receipt['decodedWidth']) + 'x' + str(receipt['decodedHeight']):
            return False
    return True


def unchanged_stream(before, after, target):
    return all(before[target][key] == after[target][key]
               for key in ('timeline', 'timelineGeneration', 'stream', 'streamGeneration'))


def publish(path, command):
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix=path.name + '.')
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write('display-' + str(time.monotonic_ns()) + '|' + command + '\n')
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--leader-log', type=Path, required=True)
    parser.add_argument('--follower-log', type=Path, required=True)
    parser.add_argument('--gate-pid', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    logs = {'leader': args.leader_log, 'follower': args.follower_log}
    desktops = {}
    for role, log in logs.items():
        text = log.read_text(errors='replace')
        bindings = re.findall(r'Private Xvfb ready: display=:[0-9]+ pid=[0-9]+ geometry=(640x480x24|1280x720x24)(?:\s|$)', text)
        if len(bindings) != 1: raise RuntimeError('Expected one owned private display binding')
        desktops[role] = PrivateMinecraftWindow(text, args.gate_pid, geometry=bindings[0], wait_seconds=120)
    if desktops['leader'].xpid == desktops['follower'].xpid:
        raise RuntimeError('Display acceptance requires two independent clients')
    control = args.leader_log.with_name(args.leader_log.name.removesuffix('.console.log') + '.control')
    if control.is_symlink() or not control.is_file(): raise RuntimeError('Missing owned leader control file')
    captures, phases = [], []

    def wait(status, predicate=lambda value: True):
        deadline = time.monotonic() + 120
        while True:
            for desktop in desktops.values(): desktop.validate()
            texts = {r: p.read_text(errors='replace') for r, p in logs.items()}
            pair = {r: states(t) for r, t in texts.items()}
            if matching(pair['leader'], pair['follower'], status) and predicate(pair['leader']) and all(
                    render_matches(pair[r], rendered(texts[r])) for r in texts):
                return pair['leader']
            if time.monotonic() >= deadline: raise RuntimeError('Independent-TV phase timed out: ' + status)
            time.sleep(.1)

    def capture(name, current):
        phases.append({'phase': name, 'televisions': current,
                       'rendered': {r: rendered(p.read_text(errors='replace')) for r, p in logs.items()}})
        for role, desktop in desktops.items():
            path = args.output / (name + '-' + role + '.png')
            desktop.capture(path)
            captures.append({'path': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        (args.output / (name + '.json')).write_text(json.dumps(current, indent=2) + '\n')

    initial = wait('PLAYING')
    customs = sorted((s for s in initial.values() if s['origin'] == 'CUSTOM'), key=lambda s: s['controller'])
    quick = [s for s in initial.values() if s['origin'] == 'QUICK']
    if (len(customs) != 2 or len(quick) != 1 or quick[0]['requested'] != '144p'
            or quick[0]['mapping'] != 'DETAILED' or quick[0]['screen'] != '16x9'
            or {s['requested'] for s in customs} != {'144p', '480p'}
            or {s['mapping'] for s in customs} != {'DETAILED', 'ONE_PIXEL_PER_BLOCK'}):
        raise RuntimeError('Wrong independent-TV acceptance scene')
    capture('initial', initial)
    target = customs[0]['television']
    publish(control, 'video:display:0:quality')
    changed = wait('PLAYING', lambda x: x[target]['requested'] == '240p'
                   and x[target]['revision'] == initial[target]['revision'] + 1
                   and x[target]['streamGeneration'] > initial[target]['streamGeneration'])
    if not unchanged_siblings(initial, changed, target): raise RuntimeError('Quality change restarted a sibling TV')
    capture('quality', changed)
    publish(control, 'video:pause')
    paused = wait('PAUSED', lambda x: x[target]['timelineGeneration'] > changed[target]['timelineGeneration'])
    capture('paused', paused)
    publish(control, 'video:display:0:mapping')
    mapped = wait('PAUSED', lambda x: x[target]['revision'] == paused[target]['revision'] + 1
                  and x[target]['mapping'] != paused[target]['mapping'])
    if not unchanged_siblings(paused, mapped, target) or not unchanged_stream(paused, mapped, target): raise RuntimeError('Paused mapping change mutated sibling TV')
    capture('paused-mapping', mapped)
    for index in range(3):
        publish(control, 'video:display:0:layout')
        next_state = wait('PAUSED', lambda x: x[target]['revision'] == mapped[target]['revision'] + 1)
        if not unchanged_siblings(mapped, next_state, target) or not unchanged_stream(mapped, next_state, target): raise RuntimeError('Layout change mutated sibling TV')
        mapped = next_state
        capture('paused-layout-' + str(index), mapped)
    publish(control, 'video:resume')
    resumed = wait('PLAYING', lambda x: x[target]['timelineGeneration'] > mapped[target]['timelineGeneration'])
    capture('resumed', resumed)
    # Exercise the real controller entry point at the minimum scaled viewport.
    # These UI captures also satisfy the enclosing gate's closed-client audit.
    for role, desktop in desktops.items():
        desktop.run('xdotool', 'windowsize', '--sync', desktop.window, '640', '480')
        offset = len(logs[role].read_text(errors='replace'))
        role_control = logs[role].with_name(logs[role].name.removesuffix('.console.log') + '.control')
        if role_control.is_symlink() or not role_control.is_file():
            raise RuntimeError('Missing owned display controller file')
        publish(role_control, 'video:open-display-controller')
        deadline = time.monotonic() + 30
        while 'Acceptance video UI screenshot:' not in logs[role].read_text(errors='replace')[offset:]:
            desktop.validate()
            if time.monotonic() >= deadline: raise RuntimeError('Controller capture did not complete: ' + role)
            time.sleep(.1)
        offset = len(logs[role].read_text(errors='replace'))
        desktop.click(70, 30)
        expected = 'Acceptance display UI: width=320 height=240 editable=' + ('true' if role == 'leader' else 'false')
        deadline = time.monotonic() + 20
        while expected not in logs[role].read_text(errors='replace')[offset:]:
            desktop.validate()
            if time.monotonic() >= deadline: raise RuntimeError('Minimum display page did not open: ' + role)
            time.sleep(.1)
        time.sleep(.3)
        path = args.output / ('display-ui-' + role + '.png')
        desktop.capture(path)
        captures.append({'path': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        if role == 'leader':
            desktop.click(300, 76)  # Actual Layout widget.
            desktop.click(385, 444)  # Apply through the revisioned editor.
            applied = wait('PLAYING', lambda x: x[target]['revision'] == resumed[target]['revision'] + 1)
            if not unchanged_siblings(resumed, applied, target) or not unchanged_stream(resumed, applied, target):
                raise RuntimeError('UI layout Apply restarted media')
            phases.append({'phase': 'ui-apply', 'televisions': applied})
            time.sleep(.3)
        else:
            desktop.escape()  # Read-only Display Settings -> controller.
            time.sleep(.3)
        desktop.escape()  # Controller -> world.
    report = {'phases': phases, 'captures': captures, 'directVisualReviewPending': True,
              'physicalAudioMeasuredByEnclosingGate': True}
    (args.output / 'display.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Independent TV stream/settings assertions passed; original image review remains required')


if __name__ == '__main__': main()
