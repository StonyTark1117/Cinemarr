#!/usr/bin/env python3
"""Real-client terminal/queue assertions. Physical A/V and image review are separate gates."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import time

spec = importlib.util.spec_from_file_location('world', Path(__file__).with_name('capture-post-reconnect-video.py'))
world = importlib.util.module_from_spec(spec)
spec.loader.exec_module(world)
STATE = re.compile(r'Acceptance video session: controller=\S+ session=(\S+) generation=(\d+) status=(\S+) item=([^\s\]]*) positionMs=(\d+) canControl=(true|false).*? durationMs=(\d+) message=([^\r\n<]*)')
QUEUE = re.compile(r'Acceptance video queue: session=(\S+) generation=(\d+) entries=(\d+) firstItem=([^\s\]]*)')


def states(text):
    return [dict(session=m[0], generation=int(m[1]), status=m[2], item=m[3], position=int(m[4]),
                 owner=m[5] == 'true', duration=int(m[6]), message=m[7].split(']]>', 1)[0]) for m in STATE.findall(text)]


def queues(text):
    return [dict(session=m[0], generation=int(m[1]), entries=int(m[2]), firstItem=m[3]) for m in QUEUE.findall(text)]


def same_playback(pair, status, after_generation=-1, item=None, session=None):
    if not all(pair.values()):
        return False
    leader, follower = pair['leader'][-1], pair['follower'][-1]
    return (leader['session'] == follower['session'] and leader['generation'] == follower['generation']
            and leader['generation'] > after_generation and leader['status'] == follower['status'] == status
            and leader['owner'] and not follower['owner'] and leader['item'] == follower['item']
            and (item is None or leader['item'] == item)
            and (session is None or leader['session'] == session))


class Observer:
    def __init__(self, logs, output, gate_pid):
        self.logs, self.output = logs, output
        self.controls = {role: world.control_path(path, role) for role, path in logs.items()}
        if logs['leader'].parent != logs['follower'].parent:
            raise RuntimeError('Terminal clients must belong to one gate case')
        if logs['leader'].name.removesuffix('.audio-leader.console.log') != logs['follower'].name.removesuffix('.audio-follower.console.log'):
            raise RuntimeError('Terminal client profile names differ')
        self.desktops = {role: world.PrivateMinecraftWindow(path.read_text(errors='replace'), gate_pid) for role, path in logs.items()}
        if self.desktops['leader'].xpid == self.desktops['follower'].xpid:
            raise RuntimeError('Terminal clients must have separate owned displays')
        output.mkdir(parents=True, exist_ok=True)

    def text(self):
        for desktop in self.desktops.values(): desktop.validate()
        return {role: path.read_text(errors='replace') for role, path in self.logs.items()}

    def latest(self):
        return {role: states(text) for role, text in self.text().items()}

    def wait(self, predicate, offsets=None, timeout=90):
        deadline = time.monotonic() + timeout
        while True:
            text = self.text()
            if offsets: text = {role: value[offsets[role]:] for role, value in text.items()}
            if predicate(text): return text
            if time.monotonic() >= deadline:
                raise RuntimeError('No fresh authoritative terminal-phase result before timeout')
            time.sleep(0.1)

    def send(self, operation):
        before = self.text()
        self.controls['leader'].write_text('terminal-' + str(time.monotonic_ns()) + '|video:' + operation + '\n')
        return {role: len(text) for role, text in before.items()}

    def seek_near_end(self):
        before = self.latest()
        if not same_playback(before, 'PLAYING'):
            raise RuntimeError('Near-EOS seek requires both clients playing the same generation')
        state = before['leader'][-1]
        if state['duration'] < 30_000:
            raise RuntimeError('Terminal fixture must have at least thirty seconds of duration')
        offsets = self.send('seek-near-end')
        text = self.wait(lambda values: same_playback({r: states(t) for r, t in values.items()},
                                                     'PLAYING', state['generation'], state['item']), offsets)
        after = {role: states(value)[-1] for role, value in text.items()}
        if any(row['session'] != state['session'] for row in after.values()):
            raise RuntimeError('Near-EOS seek changed the watch-party identity')
        if any(abs(row['position'] - (state['duration'] - 12_000)) > 1_000 for row in after.values()):
            raise RuntimeError('Near-EOS seek did not reach the final twelve seconds')
        return dict(before=state, after=after, offsets=offsets, observedMonotonic=time.monotonic())

    def run(self, phase):
        report_path = self.output / (phase + '.json')
        if report_path.exists(): raise RuntimeError('Refusing to overwrite terminal evidence')
        if phase == 'queue':
            before = self.latest()
            if not same_playback(before, 'PLAYING'):
                raise RuntimeError('Queue case requires shared active playback')
            expected = before['leader'][-1]
            offsets = self.send('queue-current')
            def queued(values):
                rows = {role: queues(value) for role, value in values.items()}
                return all(row and row[-1]['session'] == expected['session']
                           and row[-1]['generation'] == expected['generation']
                           and row[-1]['entries'] == 1 and row[-1]['firstItem'] == expected['item'] for row in rows.values())
            self.wait(queued, offsets)
            seek = self.seek_near_end()
            generation = seek['after']['leader']['generation']
            def advanced(values):
                rows = {role: states(value) for role, value in values.items()}
                if not same_playback(rows, 'PLAYING', generation, expected['item'], expected['session']): return False
                return all(row[-1]['position'] < 3_000 and row[-1]['message'] == 'Playing next queued video' for row in rows.values())
            text = self.wait(advanced, seek['offsets'])
            current = {r: states(t)[-1] for r, t in text.items()}
            self.wait(lambda values: all(queues(value) and queues(value)[-1]['entries'] == 0
                and queues(value)[-1]['session'] == expected['session']
                and queues(value)[-1]['generation'] == current[role]['generation'] for role, value in values.items()), seek['offsets'])
            result = dict(queueAdvanced=True, seek=seek, after=current)
        elif phase == 'near-end':
            result = self.seek_near_end()
        elif phase == 'ended':
            seek = json.loads((self.output / 'near-end.json').read_text())
            generation = seek['after']['leader']['generation']
            self.wait(lambda values: same_playback({r: states(t) for r, t in values.items()}, 'IDLE', generation, '', seek['before']['session']))
            current = {r: s[-1] for r, s in self.latest().items()}
            if any(row['position'] != 0 for row in current.values()):
                raise RuntimeError('Finished queue retained a playback cursor')
            # The shell gate separately proves the old client's reset/exit and
            # a fresh follower launch. An IDLE state alone cannot prove reconnect.
            result = dict(emptyQueueReachedIdle=True, after=current)
        elif phase in ('restart', 'stop'):
            before = self.latest()
            expected_status = 'IDLE' if phase == 'restart' else 'PLAYING'
            if not same_playback(before, expected_status): raise RuntimeError('Wrong starting state for ' + phase)
            old = before['leader'][-1]
            offsets = self.send('replay' if phase == 'restart' else 'stop')
            status = 'PLAYING' if phase == 'restart' else 'IDLE'
            item = json.loads((self.output / 'queue.json').read_text())['seek']['before']['item'] if phase == 'restart' else ''
            self.wait(lambda values: same_playback({r: states(t) for r, t in values.items()}, status, old['generation'], item, old['session']), offsets)
            result = dict(before=old, after={r: s[-1] for r, s in self.latest().items()})
        elif phase in ('capture-queue', 'capture-restarted', 'capture-world-return-1', 'capture-world-return-2'):
            if not same_playback(self.latest(), 'PLAYING'): raise RuntimeError('Capture requires shared playback')
            image_dir = self.output / phase
            if image_dir.exists(): raise RuntimeError('Refusing to replace terminal captures')
            image_dir.mkdir()
            for role, desktop in self.desktops.items():
                offset = len(self.logs[role].read_text(errors='replace'))
                self.controls[role].write_text('terminal-world-' + str(time.monotonic_ns()) + '|video:open-ui\n')
                self.wait(lambda values: ('true' if role == 'leader' else 'false') in world.UI.findall(values[role][offset:]))
                time.sleep(0.3); desktop.escape()
            captures = []
            previous = {r: world.latest_frame(t) for r, t in self.text().items()}
            if previous['leader']['television'] != previous['follower']['television']:
                raise RuntimeError('Different televisions in terminal capture')
            for index in range(3):
                time.sleep(1)
                text = self.wait(lambda values: all(world.advanced(previous[r], world.latest_frame(t)) for r, t in values.items()), timeout=10)
                current = {r: world.latest_frame(t) for r, t in text.items()}
                for role, desktop in self.desktops.items():
                    path = image_dir / (str(index + 1) + '-' + role + '.png')
                    if path.exists(): raise RuntimeError('Refusing to replace terminal image')
                    desktop.capture(path)
                    captures.append(dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(), role=role, pair=index + 1, frame=current[role]))
                previous = current
            result = dict(captureCompleted=True, captures=captures, directVisualReviewPending=True)
        else:
            raise ValueError('Unsupported terminal phase')
        result.update(phase=phase, physicalAudioAndVisualReviewPending=True)
        report_path.write_text(json.dumps(result, indent=2) + '\n')
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('queue', 'near-end', 'ended', 'restart', 'stop', 'capture-queue', 'capture-restarted', 'capture-world-return-1', 'capture-world-return-2'))
    parser.add_argument('--leader-log', type=Path, required=True)
    parser.add_argument('--follower-log', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--gate-pid', type=int, required=True)
    args = parser.parse_args()
    Observer(dict(leader=args.leader_log, follower=args.follower_log), args.output, args.gate_pid).run(args.phase)
    print('Video terminal phase ' + args.phase + ' reached its authoritative state; downstream A/V checks remain required.')


if __name__ == '__main__': main()
