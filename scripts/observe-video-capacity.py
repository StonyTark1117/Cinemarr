#!/usr/bin/env python3
"""Exercise bounded TV admission and failed replacement with two physical clients."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import time
from private_minecraft_window import PrivateMinecraftWindow

spec = importlib.util.spec_from_file_location('display', Path(__file__).with_name('observe-video-display.py'))
display = importlib.util.module_from_spec(spec)
spec.loader.exec_module(display)


def compatible(leader, follower):
    return (len(leader) == 3 and leader.keys() == follower.keys()
            and all(left['owner'] == 'true' and follower[tv]['owner'] == 'false'
                    and all(value == follower[tv][key] for key, value in left.items() if key != 'owner')
                    for tv, left in leader.items()))


def stream_unchanged(before, after, tv):
    return all(before[tv][key] == after[tv][key]
               for key in ('timeline', 'timelineGeneration', 'stream', 'streamGeneration'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--leader-log', type=Path, required=True)
    parser.add_argument('--follower-log', type=Path, required=True)
    parser.add_argument('--gate-pid', type=int, required=True)
    parser.add_argument('--fault-state', type=Path, required=True)
    parser.add_argument('--request-log', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    if args.fault_state.is_symlink() or not args.fault_state.is_file():
        raise RuntimeError('Owned fake-Plex fault control is required')
    logs = {'leader': args.leader_log, 'follower': args.follower_log}
    desktops = {}
    for role, path in logs.items():
        text = path.read_text(errors='replace')
        geometry = re.findall(r'Private Xvfb ready: display=:[0-9]+ pid=[0-9]+ geometry=(640x480x24|1280x720x24)', text)
        if len(geometry) != 1: raise RuntimeError('Expected exactly one owned private display')
        desktops[role] = PrivateMinecraftWindow(text, args.gate_pid, geometry=geometry[0], wait_seconds=120)
    if desktops['leader'].xpid == desktops['follower'].xpid:
        raise RuntimeError('Capacity evidence needs separate clients')
    control = args.leader_log.with_name(args.leader_log.name.removesuffix('.console.log') + '.control')
    phases, captures = [], []
    completed = False

    def texts(): return {role: path.read_text(errors='replace') for role, path in logs.items()}

    def wait(predicate, seconds=120):
        deadline = time.monotonic() + seconds
        while True:
            for desktop in desktops.values(): desktop.validate()
            current_logs = texts()
            display.require_clean_transfer_logs(current_logs)
            pair = {role: display.states(text) for role, text in current_logs.items()}
            if compatible(pair['leader'], pair['follower']) and predicate(pair['leader']):
                active = {tv: s for tv, s in pair['leader'].items() if s['status'] == 'PLAYING'}
                if len(active) > 2: raise RuntimeError('Configured two-stream limit exceeded')
                if all(display.render_matches(active, display.rendered(text), display.decoded(text))
                       for text in current_logs.values()): return pair['leader']
            if time.monotonic() >= deadline: raise RuntimeError('Capacity/failure phase did not converge')
            time.sleep(.1)

    def capture(name, current):
        phases.append({'phase': name, 'televisions': current,
                       'rendered': {role: display.rendered(text) for role, text in texts().items()}})
        for role, desktop in desktops.items():
            path = args.output/(name+'-'+role+'.png')
            desktop.capture(path)
            captures.append({'path': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})

    def controller_capture(name, index, controller):
        offset = len(args.leader_log.read_text(errors='replace'))
        display.publish(control, 'video:open-display-controller' if index == 0 else 'video:open-second-display-controller')
        deadline = time.monotonic() + 30
        marker = 'Acceptance video UI request: controller=' + str(controller)
        while marker not in args.leader_log.read_text(errors='replace')[offset:]:
            desktops['leader'].validate()
            if time.monotonic() >= deadline: raise RuntimeError('Capacity controller did not open')
            time.sleep(.1)
        time.sleep(.5)
        path = args.output/(name+'-controller.png')
        desktops['leader'].capture(path)
        captures.append({'path':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        desktops['leader'].escape()
        time.sleep(.3)

    def starts():
        return sum(line.split('\t')[1:2] == ['/video/:/transcode/universal/start.m3u8']
                   for line in args.request_log.read_text().splitlines())

    def command(index, operation): display.publish(control, 'video:display:'+str(index)+':'+operation)

    try:
        initial = wait(lambda s: sum(v['status']=='PLAYING' for v in s.values()) == 2
                       and sum(v['streamState']=='WAITING' for v in s.values()) == 1)
        quick = next(tv for tv,s in initial.items() if s['origin']=='QUICK')
        if initial[quick]['status'] != 'PLAYING': raise RuntimeError('Quick primary must remain audible through this supplement')
        customs = sorted((tv for tv,s in initial.items() if s['origin']=='CUSTOM'), key=lambda tv: initial[tv]['controller'])
        queued = next(tv for tv in customs if initial[tv]['streamState']=='WAITING')
        active = next(tv for tv in customs if initial[tv]['status']=='PLAYING')
        if len({s['timeline'] for s in initial.values()}) != 1: raise RuntimeError('Expected one watch party')
        capture('capacity-full', initial)
        controller_capture('capacity-full', customs.index(queued), initial[queued]['controller'])
        baseline_starts = starts()
        current = initial
        for preset in ('240p', '360p', '480p'):
            command(customs.index(queued), 'quality-'+preset)
            after = wait(lambda s: s[queued]['revision'] == current[queued]['revision']+1
                         and s[queued]['requested']==preset and s[queued]['streamState']=='WAITING')
            if not all(stream_unchanged(current, after, tv) for tv in (quick, active)) or starts()!=baseline_starts:
                raise RuntimeError('Queued quality changes disturbed active media or started work without capacity')
            current = after
        capture('queued-latest-quality', current)
        command(customs.index(active), 'tune-idle')
        admitted = wait(lambda s: s[active]['status']=='IDLE' and s[queued]['status']=='PLAYING'
                        and s[queued]['requested']=='480p' and s[queued]['streamGeneration']>current[queued]['streamGeneration'])
        if not stream_unchanged(current, admitted, quick): raise RuntimeError('Admission restarted healthy sibling')
        for role,text in texts().items():
            rendered=display.rendered(text)
            if abs(rendered[queued]['ptsUs']-rendered[quick]['ptsUs']) > 300_000:
                raise RuntimeError('Capacity admission did not catch the current shared position: '+role)
        capture('automatic-admission', admitted)
        command(customs.index(active), 'tune-party')
        waiting = wait(lambda s: s[active]['streamState']=='WAITING')
        command(customs.index(active), 'tune-idle')
        cancelled = wait(lambda s: s[active]['status']=='IDLE' and s[active]['streamState']!='WAITING')
        if not all(stream_unchanged(admitted, cancelled, tv) for tv in (quick, queued)):
            raise RuntimeError('Cancelling a queued TV changed active streams')
        capture('queue-cancelled', cancelled)
        args.fault_state.write_text('starts-rejected\n')
        command(customs.index(queued), 'quality-240p')
        failed = wait(lambda s: s[queued]['streamState']=='FAILED' and s[queued]['requested']=='240p')
        if not all(stream_unchanged(cancelled, failed, tv) for tv in (quick, queued)) or failed[queued]['status']!='PLAYING':
            raise RuntimeError('Failed replacement discarded working media or affected its sibling')
        failed_starts=starts()
        old_frames={role: display.rendered(text)[queued]['ptsUs'] for role,text in texts().items()}
        deadline=time.monotonic()+5
        while time.monotonic()<deadline:
            wait(lambda s: s[queued]['streamState']=='FAILED' and all(stream_unchanged(failed,s,tv) for tv in (quick,queued)))
            if starts()!=failed_starts: raise RuntimeError('Failed replacement is retrying without a user change')
            time.sleep(.1)
        if any(display.rendered(text)[queued]['ptsUs'] <= old_frames[role] for role,text in texts().items()):
            raise RuntimeError('Retained working stream stopped presenting after replacement failure')
        capture('failed-replacement-keeps-playing', failed)
        controller_capture('failed-replacement', customs.index(queued), failed[queued]['controller'])
        args.fault_state.write_text('online\n')
        command(customs.index(queued), 'quality-360p')
        recovered=wait(lambda s: s[queued]['streamState']=='READY' and s[queued]['requested']=='360p'
                       and s[queued]['streamGeneration']>failed[queued]['streamGeneration'])
        if not stream_unchanged(failed,recovered,quick): raise RuntimeError('Explicit replacement recovery restarted its sibling')
        capture('explicit-replacement-recovery', recovered)
        completed=True
    finally:
        args.fault_state.write_text('online\n')
        (args.output/'capacity.json').write_text(json.dumps({'completed':completed,'phases':phases,'captures':captures,
            'configuredStreamLimit':2,'directVisualReviewPending':True,'physicalAudioRequiresEnclosingGate':True},indent=2)+'\n')
    print('TV capacity, cancellation, failed replacement and recovery assertions passed; original review remains required')


if __name__ == '__main__': main()
