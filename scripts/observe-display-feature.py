#!/usr/bin/env python3
"""Exercise actual draft -> packet -> server display changes on three TVs and two clients.

This records state, original framebuffer and local lifecycle evidence. Final
candidate, remote recovery and native certification remain separate receipts.
"""
import argparse
import json
import re
import time
import hashlib
from pathlib import Path
from private_minecraft_window import PrivateMinecraftWindow
from display_framebuffer import verify_pixel_frame, verify_mask_frame
from display_server import DisplayServer
from display_ui_edges import exercise as exercise_ui_edges


def snapshots(text, request):
    rows = {}
    for line in text.splitlines():
        marker = 'Acceptance display snapshot: request=' + request + ' '
        if marker not in line:
            continue
        fields = dict(re.findall(r'(\w+)=([^\s<>]+)', line.split(marker, 1)[1]))
        if 'controller' in fields:
            rows[fields['controller']] = fields
    return rows


def stream(row):
    return row['stream'], row['streamGeneration']


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


DISPLAY_KEYS = ('tv','revision','origin','layout','mapping','requested','width','height')


def same_display(expected, actual):
    return all(key in expected and key in actual and expected[key] == actual[key] for key in DISPLAY_KEYS)


class Observer:
    def __init__(self, logs, output, gate_pid=None, fixture_state=None, server=None):
        require(not output.exists(), 'Refusing to overwrite display-feature evidence')
        self.logs, self.output, self.steps = logs, output, []
        self.gate_pid, self.captures = gate_pid, []
        self.fixture_state = fixture_state
        self.server, self.frame_checks = server, []
        if fixture_state is not None:
            require(fixture_state.name == 'fake-plex.state' and fixture_state.is_file()
                    and not fixture_state.is_symlink(), 'Missing owned deterministic fixture state')
        self.controls = {}
        for role, path in logs.items():
            require(path.is_file() and not path.is_symlink(), 'Missing current client log')
            require(path.name.endswith('.audio-' + role + '.console.log'), 'Wrong client role log')
            control = path.with_name(path.name.removesuffix('.console.log') + '.control')
            require(control.is_file() and not control.is_symlink(), 'Missing current client control file')
            self.controls[role] = control
        output.mkdir(parents=True)

    def send(self, operation, role='leader'):
        sequence = str(time.monotonic_ns())
        control = self.controls[role]
        pending = control.with_name(control.name + '.pending')
        pending.write_text(sequence + '|' + operation + '\n')
        pending.replace(control)
        receipt = control.with_name(control.name + '.received')
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if receipt.is_file() and receipt.read_text() == sequence:
                return
            time.sleep(.05)
        raise RuntimeError('Client did not consume display scenario command: ' + operation)

    def snapshot(self, role):
        request = str(time.monotonic_ns())
        self.send('video:display-snapshot:' + request, role)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            rows = snapshots(self.logs[role].read_text(errors='replace'), request)
            if len(rows) == 3:
                return rows
            time.sleep(.1)
        raise RuntimeError('No fresh three-TV display snapshot from ' + role)

    def wait(self, check, label, timeout=90):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            pair = {role: self.snapshot(role) for role in self.logs}
            if check(pair):
                self.steps.append(dict(scenario=label, clients=pair))
                return pair['leader']
            time.sleep(.25)
        raise RuntimeError('Display acceptance timeout: ' + label)

    def display(self, row, layout, mapping, quality):
        self.send('video:display:' + row['controller'] + ':' + layout + ':' + mapping + ':' + quality)
        revision = int(row['revision']) + 1
        return self.wait(lambda pair: all(int(rows[row['controller']]['revision']) == revision
                         and rows[row['controller']]['layout'] == layout and rows[row['controller']]['mapping'] == mapping
                         and rows[row['controller']]['requested'] == ('Auto' if quality == 'auto' else quality)
                     for rows in pair.values()), 'SET_DISPLAY ' + layout + '/' + mapping)[row['controller']]

    def page(self, row, role, name):
        desktop = PrivateMinecraftWindow(self.logs[role].read_text(), self.gate_pid, wait_seconds=30)
        # Search decoded text using a character offset. Packaged legacy logs
        # contain CRLF, and library titles can contain multibyte characters.
        offset = len(self.logs[role].read_text(errors='replace'))
        self.send('video:display-open:' + row['controller'], role)
        deadline = time.monotonic() + 10
        marker = 'Acceptance display UI: width=320 height=240 controller=' + row['controller']
        while marker not in self.logs[role].read_text(errors='replace')[offset:]:
            require(time.monotonic() < deadline, 'Display page did not open at 320x240')
            time.sleep(.1)
        time.sleep(.3)
        self.capture(desktop, name)
        return desktop

    def capture(self, desktop, name):
        path = self.output / (name + '.png')
        desktop.capture(path)
        self.captures.append(dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest()))

    def capture_page(self, row, name):
        if self.gate_pid is not None:
            desktop = self.page(row, 'leader', name)
            self.close_page(desktop)

    @staticmethod
    def close_page(desktop):
        # Legacy screen initialization drains queued key events. Let each
        # transition finish before dispatching Escape to its new parent.
        for _ in range(2):
            desktop.escape()
            time.sleep(.2)

    def source(self, row):
        nonce = str(time.monotonic_ns())
        self.send('video:display-frame:' + row['controller'] + ':' + nonce)
        base = Path(str(self.controls['leader']) + '.frame-' + nonce)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            metadata = Path(str(base) + '.json')
            if metadata.is_file():
                try:
                    value = json.loads(metadata.read_text())
                except json.JSONDecodeError:
                    time.sleep(.05)
                    continue
                require(value['nonce'] == nonce and str(value['controller']) == row['controller'], 'Wrong frame receipt')
                return value, Path(str(base) + '.rgba').read_bytes()
            time.sleep(.05)
        raise RuntimeError('No current rendered source receipt')

    def world_frame(self, row, name):
        desktop = PrivateMinecraftWindow(self.logs['leader'].read_text(), self.gate_pid, wait_seconds=30)
        initial, _ = self.source(row)
        require(not initial['guiOpen'] and not initial['videoSuppressed'], 'A GUI or suppressed TV prevents the world capture')
        if not initial['hudHidden']:
            desktop.run('xdotool', 'key', '--clearmodifiers', 'F1')
        try:
            time.sleep(.2)
            before, source = self.source(row)
            require(not before['guiOpen'] and before['hudHidden'], 'World capture must hide both GUI and HUD')
            self.capture(desktop, name)
            after, _ = self.source(row)
            try:
                self.send('video:display-background:' + row['controller'] + ':true')
                time.sleep(.2)
                background_before, _ = self.source(row)
                self.capture(desktop, name + '-background')
                background_after, _ = self.source(row)
            finally:
                self.send('video:display-background:' + row['controller'] + ':false')
                time.sleep(.2)
        finally:
            if not initial['hudHidden']:
                desktop.run('xdotool', 'key', '--clearmodifiers', 'F1')
                time.sleep(.2)
        for frame, suppressed in ((before,False),(after,False),(background_before,True),(background_after,True)):
            require(not frame['guiOpen'] and frame['hudHidden'] and frame['videoSuppressed'] == suppressed,
                    'World capture GUI or video-suppression state changed')
            require(all(before[key] == frame[key] for key in ('sourceSha256','camera','layout','mapping','mask')),
                    'Source, camera or presentation moved during paused framebuffer capture')
        width, height = before['width'], before['height']
        require((width,height) in ((4,4),(17,11)), 'Unexpected masked fixture dimensions')
        expected_mask = sum(1 << (y*width+x) for y in range(height) for x in range(width)
                            if not ((x>=2 and y>=2) if width==4 else (7<=x<=9 and 4<=y<=6)))
        require(int.from_bytes(bytes.fromhex(before['mask']), 'little') == expected_mask,
                'The runtime fixture does not have the required L or central-hole geometry')
        if before['mapping'] == 'ONE_PIXEL_PER_BLOCK':
            check = verify_pixel_frame(self.output / (name + '.png'), before, source,
                                       background_path=self.output / (name + '-background.png'))
            self.frame_checks.append(dict(name=name, metadata=before, backgroundMetadata=background_before, result=check))
            require(check['passed'], 'World pixel colors differ from independent samples: ' + json.dumps(check['examples']))
        else:
            require(before['retainedBytes'] == len(source) and before['derivedTextures'] == 0,
                    'Detailed mode retained derived CPU or GPU ownership')
            mask = verify_mask_frame(self.output / (name + '.png'), self.output / (name + '-background.png'), before)
            self.frame_checks.append(dict(name=name, metadata=before, backgroundMetadata=background_before,
                                         detailedStorageReleased=True, mask=mask))
            require(mask['passed'], 'Detailed mode painted over a missing screen cell')

    def move_camera(self, command, row):
        self.server.command(command)
        if not self.server.legacy:
            return
        fields = command.split()
        x, y, z = map(float, fields[2:5])
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            frame, _ = self.source(row)
            if all(abs(a-b) < .001 for a,b in zip((frame['camera'][0],frame['feetY'],frame['camera'][2]),(x,y,z))):
                self.send('video:display-look:' + ':'.join(fields[5:7]))
                return
            time.sleep(.1)
        raise RuntimeError('Legacy acceptance camera did not receive its server teleport')

    def reload_active_tvs(self, active, controllers):
        for role, log in self.logs.items():
            nonce = str(time.monotonic_ns())
            offset = len(log.read_text(errors='replace'))
            self.send('video:display-reload:' + nonce, role)
            marker = 'Acceptance display reload: request=' + nonce + ' complete='
            deadline = time.monotonic() + 90
            while True:
                fresh = log.read_text(errors='replace')[offset:]
                require(marker+'false' not in fresh, 'Client resource reload failed')
                if marker+'true' in fresh: break
                require(time.monotonic() < deadline, 'No fresh resource reload completion')
                time.sleep(.1)
            self.wait(lambda pair: all(pos in rows and same_display(active[pos],rows[pos])
                      and rows[pos]['status']=='PLAYING' and rows[pos]['actual']!='unknown'
                      for rows in pair.values() for pos in controllers), 'two-active-TVs-after-reload-' + role)
            self.steps[-1]['reload'] = dict(role=role,request=nonce,freshLogSha256=hashlib.sha256(fresh.encode()).hexdigest())

    def verify_preserved(self, result_path):
        result = json.loads(result_path.read_text())
        require(result.get('passed') is True, 'Prior display scenario did not pass')
        expected = result['steps'][-1]['clients']['leader']
        custom = {pos:row for pos,row in expected.items() if row['origin']=='CUSTOM'}
        require(len(custom)==2, 'Missing edited custom TV expectations')
        try:
            self.wait(lambda pair: all(pos in rows and same_display(row,rows[pos])
                      for rows in pair.values() for pos,row in custom.items()), 'edited-settings-after-reconnect-and-reload')
        except Exception as failure:
            (self.output/'result.json').write_text(json.dumps(dict(passed=False,error=str(failure)),indent=2)+'\n')
            raise
        (self.output/'result.json').write_text(json.dumps(dict(passed=True,scope='Custom display settings after the existing control/reconnect/reload stages',
            expectedResultSha256=hashlib.sha256(result_path.read_bytes()).hexdigest(),steps=self.steps),indent=2)+'\n')

    def pages(self, quick, custom):
        for row, name in ((quick, 'quick'), (custom, 'custom')):
            for role in self.logs:
                desktop = self.page(row, role, name + '-' + role)
                if role == 'follower':
                    before = self.snapshot(role)[row['controller']]
                    for x, y in ((320, 76), (320, 124), (320, 172), (320, 436)):
                        desktop.click(x, y)
                    after = self.snapshot(role)[row['controller']]
                    require(all(before[key] == after[key] for key in ('revision', 'layout', 'mapping', 'requested')),
                            'Read-only display widgets changed authoritative settings')
                    self.capture(desktop, name + '-read-only-attempt')
                if row is custom and role == 'leader':
                    desktop.click(320, 76)  # Layout, at the required 2x UI scale.
                    time.sleep(.2)  # Capture the next rendered draft, not the pre-click framebuffer.
                    self.capture(desktop, 'custom-edited')
                    desktop.click(320, 436)  # Apply through the actual widget.
                    self.wait(lambda pair: all(int(rows[row['controller']]['revision']) == int(row['revision']) + 1
                                        and rows[row['controller']]['layout'] != row['layout'] for rows in pair.values()),
                                        'actual-display-page-apply')
                    time.sleep(.3)
                    self.capture(desktop, 'custom-acknowledged')
                self.close_page(desktop)
        exercise_ui_edges(self, custom)

    def run(self):
        try:
            initial = self.snapshot('leader')
            quick = next(row for row in initial.values() if row['origin'] == 'QUICK')
            custom = sorted((r for r in initial.values() if r['origin'] == 'CUSTOM'), key=lambda r: int(r['width']))
            require(len(custom) == 2, 'Two custom TVs are required')
            a, b = custom; qid, aid, bid = quick['controller'], a['controller'], b['controller']
            require(quick['requested'] == '144p' and quick['mapping'] == 'DETAILED', 'Quick preset changed')
            if self.gate_pid is not None:
                self.pages(quick, a)
                a = self.snapshot('leader')[aid]
            a = self.display(a, 'FIT', 'ONE_PIXEL_PER_BLOCK', '320x180')
            b = self.display(b, 'FIT', 'DETAILED', '640x360')
            self.send('video:display-tune:' + aid + ':cinemarr-acceptance')
            active = self.wait(lambda pair: all(rows[aid]['status'] == 'PLAYING' and rows[aid]['actual'] != 'unknown'
                               and rows[aid]['timeline'] == rows[qid]['timeline'] and stream(rows[aid]) != stream(rows[qid])
                               for rows in pair.values()) and stream(pair['leader'][aid]) == stream(pair['follower'][aid]),
                               'independent-streams-and-same-TV-viewers')
            self.capture_page(active[aid], 'custom-pixel-active')
            self.reload_active_tvs(active, (qid, aid))
            sibling = stream(active[qid]); timeline = active[qid]['timelineGeneration']
            self.send('video:display-tune:' + bid + ':cinemarr-acceptance')
            self.wait(lambda pair: all(rows[bid]['waiting'] == 'true' and stream(rows[qid]) == sibling for rows in pair.values()), 'capacity-wait')
            self.send('video:display-tune:' + bid + ':display-cancelled')
            self.wait(lambda pair: all(rows[bid]['status'] == 'IDLE' and rows[bid]['waiting'] == 'false'
                      and stream(rows[qid]) == sibling for rows in pair.values()), 'capacity-wait-cancellation')
            self.send('video:display-tune:' + bid + ':cinemarr-acceptance')
            self.wait(lambda pair: all(rows[bid]['waiting'] == 'true' and stream(rows[qid]) == sibling for rows in pair.values()), 'capacity-requeue')
            before = stream(active[aid])
            if self.fixture_state is not None:
                self.fixture_state.write_text('starts-fail-' + str(time.monotonic_ns()) + '\n')
                try:
                    a = self.display(active[aid], 'FIT', 'ONE_PIXEL_PER_BLOCK', '720p')
                    active = self.wait(lambda pair: all(rows[aid]['failed'] == 'true'
                                       and rows[aid]['status'] == 'PLAYING' and rows[aid]['actual'] != 'unknown'
                                       and stream(rows[aid]) == before and stream(rows[qid]) == sibling
                                       and rows[qid]['timelineGeneration'] == timeline for rows in pair.values()),
                                       'failed-replacement-preserves-TV-and-sibling')
                finally:
                    self.fixture_state.write_text('online\n')
            a = self.display(active[aid], 'FIT', 'ONE_PIXEL_PER_BLOCK', '480p')
            active = self.wait(lambda pair: all(rows[aid]['status'] == 'PLAYING' and stream(rows[aid]) != before
                               and stream(rows[qid]) == sibling and rows[qid]['timelineGeneration'] == timeline
                               for rows in pair.values()), 'independent-live-replacement')
            # Pause releases transcodes. Keep only these two TVs eligible for
            # the resume check; FIFO may legitimately admit a third waiting TV
            # before either former owner when all three resume together.
            self.send('video:display-tune:' + bid + ':display-pause-isolation')
            self.wait(lambda pair: all(rows[bid]['status'] == 'IDLE' for rows in pair.values()), 'isolate-two-TV-pause')
            self.send('video:pause')
            paused = self.wait(lambda pair: all(rows[aid]['status'] == 'PAUSED' and rows[qid]['status'] == 'PAUSED' for rows in pair.values()), 'shared-pause')
            a = paused[aid]
            original_camera = None
            if self.server is not None:
                original_camera, _ = self.source(paused[qid])
                self.server.command('setblock 22 100 7 stone')
                self.move_camera('tp CinemarrVideoA 22.5 101 7.5 180 0', a)
                time.sleep(1)
            for layout in ('FILL', 'STRETCH', 'FIT'):
                a = self.display(a, layout, 'ONE_PIXEL_PER_BLOCK', '480p')
                require(a['status'] == 'PAUSED' and a['actual'] != 'unknown', 'Paused edit lost its decoded source')
                self.capture_page(a, 'paused-pixel-' + layout.lower())
                if self.server is not None: self.world_frame(a, 'world-pixel-' + layout.lower())
            a = self.display(a, 'FIT', 'DETAILED', '480p')
            self.capture_page(a, 'paused-detailed')
            if self.server is not None: self.world_frame(a, 'world-detailed')
            a = self.display(a, 'FIT', 'ONE_PIXEL_PER_BLOCK', '480p')
            if original_camera is not None:
                camera = original_camera['camera']
                self.move_camera('tp CinemarrVideoA ' + ' '.join(format(v, '.6f') for v in
                    (camera[0], original_camera['feetY'], camera[2], camera[3], camera[4])), a)
                self.server.command('setblock 22 100 7 air')
                time.sleep(1)
            self.send('video:resume')
            self.wait(lambda pair: all(rows[qid]['status'] == rows[aid]['status'] == 'PLAYING' for rows in pair.values()), 'shared-resume')
            self.send('video:display-tune:' + bid + ':cinemarr-acceptance')
            self.wait(lambda pair: all(rows[bid]['waiting'] == 'true' for rows in pair.values()), 'capacity-wait-before-admission')
            self.send('video:display-tune:' + qid + ':display-idle')
            admitted = self.wait(lambda pair: all(rows[aid]['status'] == rows[bid]['status'] == 'PLAYING'
                                 and rows[bid]['actual'] != 'unknown' and rows[bid]['waiting'] == 'false'
                                 and rows[aid]['timeline'] == rows[bid]['timeline'] and stream(rows[aid]) != stream(rows[bid])
                                 for rows in pair.values()), 'capacity-admission')
            require(admitted[aid]['requested'] != admitted[bid]['requested'], 'Custom quality choices must differ')
            if self.server is not None:
                self.send('video:display-transport:' + bid + ':PAUSE')
                odd = self.wait(lambda pair: all(rows[aid]['status'] == rows[bid]['status'] == 'PAUSED'
                                and rows[bid]['actual'] != 'unknown' for rows in pair.values()), 'odd-screen-pause')[bid]
                self.server.command('setblock 52 103 14 stone')
                self.move_camera('tp CinemarrVideoA 52.5 104 14.5 180 0', odd)
                time.sleep(1)
                for layout in ('FILL', 'STRETCH', 'FIT'):
                    odd = self.display(odd, layout, 'ONE_PIXEL_PER_BLOCK', '640x360')
                    self.capture_page(odd, 'odd-paused-pixel-' + layout.lower())
                    self.world_frame(odd, 'world-odd-pixel-' + layout.lower())
                odd = self.display(odd, 'FIT', 'DETAILED', '640x360')
                self.world_frame(odd, 'world-odd-detailed')
                camera = original_camera['camera']
                self.move_camera('tp CinemarrVideoA ' + ' '.join(format(v, '.6f') for v in
                    (camera[0], original_camera['feetY'], camera[2], camera[3], camera[4])), odd)
                self.server.command('setblock 52 103 14 air')
                time.sleep(1)
                self.send('video:display-transport:' + bid + ':RESUME')
                self.wait(lambda pair: all(rows[aid]['status'] == rows[bid]['status'] == 'PLAYING'
                          for rows in pair.values()), 'odd-screen-resume')
            self.send('video:display-tune:' + aid + ':display-idle-a')
            self.wait(lambda pair: all(rows[aid]['status'] == 'IDLE' for rows in pair.values()), 'first-custom-detach')
            self.send('video:display-tune:' + bid + ':display-idle-b')
            self.wait(lambda pair: all(rows[bid]['status'] == 'IDLE' for rows in pair.values()), 'second-custom-detach')
            self.send('video:display-tune:' + qid + ':cinemarr-acceptance')
            self.wait(lambda pair: all(rows[qid]['status'] == 'PAUSED' for rows in pair.values()), 'detached-party-restores-paused')
            self.send('video:resume')
            self.wait(lambda pair: all(rows[qid]['status'] == 'PLAYING' and rows[qid]['requested'] == '144p'
                      and rows[qid]['mapping'] == 'DETAILED' and rows[qid]['actual'] != 'unknown'
                      for rows in pair.values()), 'Quick-preset-restored')
        except Exception as error:
            (self.output / 'result.json').write_text(json.dumps(dict(passed=False, steps=self.steps, captures=self.captures, frameChecks=self.frame_checks, error=str(error)), indent=2)+'\n')
            raise
        (self.output / 'result.json').write_text(json.dumps(dict(passed=True, scope='runtime-state-and-pixel-colors', steps=self.steps, captures=self.captures, frameChecks=self.frame_checks,
                      framebufferReviewPending=True, realPlexCertificationPending=True), indent=2)+'\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--leader-log', type=Path, required=True)
    parser.add_argument('--follower-log', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--gate-pid', type=int, required=True)
    parser.add_argument('--fixture-state', type=Path)
    parser.add_argument('--rcon-port', type=int)
    parser.add_argument('--rcon-password', default='')
    parser.add_argument('--server-fifo', type=Path)
    parser.add_argument('--discopanel-server-id')
    parser.add_argument('--verify-preserved', type=Path)
    args = parser.parse_args()
    observer=Observer(dict(leader=args.leader_log, follower=args.follower_log), args.output, args.gate_pid, args.fixture_state, DisplayServer(args))
    if args.verify_preserved: observer.verify_preserved(args.verify_preserved)
    else: observer.run()


if __name__ == '__main__':
    main()
