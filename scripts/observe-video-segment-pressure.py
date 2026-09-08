#!/usr/bin/env python3
"""Real network-only third peer; two ordinary viewers remain the A/V oracle."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import time

spec = importlib.util.spec_from_file_location('terminal', Path(__file__).with_name('observe-video-terminal.py'))
terminal = importlib.util.module_from_spec(spec); spec.loader.exec_module(terminal)
PEER = re.compile(r'Acceptance segment peer: ready=(true|false) running=(true|false) finished=(true|false) elapsedMs=(\d+) requests=(\d+) chunks=(\d+) bytes=(\d+) acknowledgements=(\d+) rateRejections=(\d+) windowRejections=(\d+) unexpectedErrors=(\d+)')
KEYS = ('ready', 'running', 'finished', 'elapsedMs', 'requests', 'chunks', 'bytes', 'acknowledgements', 'rateRejections', 'windowRejections', 'unexpectedErrors')


def peer_rows(text):
    return [dict(zip(KEYS, [v == 'true' for v in row[:3]] + [int(v) for v in row[3:]])) for row in PEER.findall(text)]


def check_peer(text):
    rows = peer_rows(text)
    if not rows: raise RuntimeError('Missing fresh real-peer traffic')
    row = rows[-1]
    if not row['finished'] or row['running'] or not row['ready'] or not 40000 <= row['elapsedMs'] <= 45000:
        raise RuntimeError('Pressure did not finish its bounded forty-second run')
    if not 2000 <= row['requests'] <= 4000 or row['chunks'] < 50 or row['bytes'] < 1_000_000 or row['acknowledgements'] < 50:
        raise RuntimeError('Insufficient actual requests, delivered media, or acknowledgements')
    if row['rateRejections'] < 100 or row['windowRejections'] < 100 or any(r['unexpectedErrors'] for r in rows):
        raise RuntimeError('Missing both rejection paths or unexpected peer errors')
    active = [r for r in rows if r['running']]
    if len(active) < 30 or active[-1]['elapsedMs'] - active[0]['elapsedMs'] < 35000:
        raise RuntimeError('Pressure was not sustained across fresh samples')
    if terminal.world.FRAME.search(text) or re.search(r'Acceptance (?:legacy )?video audio timeline:', text):
        raise RuntimeError('Network-only peer unexpectedly rendered video')
    return row


def check_diagnostics(text, recovered=False):
    keys = ('trackingClients', 'workQueued', 'workActive', 'workRejected', 'transferGrants', 'egressItems', 'egressBytes', 'egressRejected', 'browseRateClients', 'segmentRateClients',
            'orphanedTransferGrants', 'orphanedEgressItems', 'orphanedEgressBytes')
    rows = []
    for line in text.splitlines():
        if 'workQueued=' not in line: continue
        row = {k:int(v) for k,v in re.findall(r'\b(' + '|'.join(keys) + r')=(\d+)', line)}
        if len(row) != len(keys): raise RuntimeError('Missing server transfer diagnostics')
        maximum = 2 if recovered else 3
        if (row['trackingClients'] != maximum or row['transferGrants'] > maximum or row['workQueued'] > 64 or row['workActive'] > 2
                or row['browseRateClients'] > maximum or row['segmentRateClients'] > maximum):
            raise RuntimeError('Tracking, grants, or playback work exceeded bounds')
        if row['egressItems'] > 256 or row['egressBytes'] > 8*1024*1024 or row['workRejected'] or row['egressRejected']:
            raise RuntimeError('Watch-party egress exceeded bounds or rejected work')
        if any(row[k] for k in ('orphanedTransferGrants', 'orphanedEgressItems', 'orphanedEgressBytes')):
            raise RuntimeError('Disconnected client still owns transfer resources')
        rows.append(row)
    if len(rows) < 3: raise RuntimeError('Insufficient fresh diagnostics')
    # The two viewers keep streaming. Require the disconnected owners' work
    # to be zero in EVERY sample, not an accidental global-idle sample.
    return rows


def recovered_diagnostics(text, departed):
    return check_diagnostics(text[departed['serverOffset']:], recovered=True)


def run(observer, phase, peer_log, server_log, gate_pid):
    report = observer.output / (phase + '.json')
    if report.exists(): raise RuntimeError('Refusing to overwrite segment-pressure evidence')
    if phase == 'prepare':
        peer_desktop = terminal.world.PrivateMinecraftWindow(peer_log.read_text(errors='replace'), gate_pid)
        if peer_desktop.xpid in {d.xpid for d in observer.desktops.values()}:
            raise RuntimeError('Peer must have a separate private display')
        rows = peer_rows(peer_log.read_text(errors='replace'))
        if not rows or not rows[-1]['ready'] or rows[-1]['requests']:
            raise RuntimeError('Peer is not freshly ready')
        observer.run('capture-queue')
        pair = observer.latest()
        if not terminal.same_playback(pair, 'PLAYING'): raise RuntimeError('Viewers are not playing together')
        result = dict(session=pair['leader'][-1], peerOffset=len(peer_log.read_text(errors='replace')),
                      serverOffset=len(server_log.read_text(errors='replace')))
    elif phase == 'flood':
        before = json.loads((observer.output / 'prepare.json').read_text())
        desktop = terminal.world.PrivateMinecraftWindow(peer_log.read_text(errors='replace'), gate_pid)
        control = peer_log.with_name(peer_log.name.replace('.console.log', '.control'))
        control.write_text('segment-start|video:segment-pressure-start\n')
        started = time.monotonic(); captures = []; frames = []; next_capture = 18
        while time.monotonic() - started < 42:
            values = observer.text(); desktop.validate()
            if time.monotonic() - started >= next_capture:
                frames.append({r:terminal.world.latest_frame(t) for r,t in values.items()})
                for role,view in observer.desktops.items():
                    path = observer.output / ('during-' + str(next_capture) + '-' + role + '.png')
                    if path.exists(): raise RuntimeError('Refusing to replace segment capture')
                    view.capture(path)
                    captures.append(dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(), role=role))
                next_capture += 10
            time.sleep(.1)
        peer = check_peer(peer_log.read_text(errors='replace')[before['peerOffset']:])
        advanced = len(frames) == 3 and all(terminal.world.advanced(frames[i-1][r],frames[i][r]) for i in (1,2) for r in ('leader','follower'))
        result = dict(seconds=time.monotonic()-started, peer=peer, captures=captures,
                      sustainedFrames=advanced, automatedPassed=advanced and len(captures)==6)
    elif phase == 'loaded':
        before = json.loads((observer.output / 'prepare.json').read_text())
        text = server_log.read_text(errors='replace')
        result = dict(diagnostics=check_diagnostics(text[before['serverOffset']:]), serverOffset=len(text),
                      peerOffset=len(peer_log.read_text(errors='replace')))
    elif phase == 'departed':
        loaded = json.loads((observer.output / 'loaded.json').read_text())
        if 'Acceptance client media reset complete' not in peer_log.read_text(errors='replace')[loaded['peerOffset']:]:
            raise RuntimeError('No fresh peer disconnect cleanup acknowledgement')
        # The legacy diagnostics command is queued through stdin. A last
        # pre-kick response may be written after loaded.json. This cursor is
        # taken only after the shell observes leave/reset and closes the peer.
        result = dict(serverOffset=len(server_log.read_text(errors='replace')), peerResetAcknowledged=True)
    elif phase == 'recovered':
        before = json.loads((observer.output / 'prepare.json').read_text())
        departed = json.loads((observer.output / 'departed.json').read_text())
        pair = observer.latest()
        if not terminal.same_playback(pair, 'PLAYING', session=before['session']['session'], item=before['session']['item']):
            raise RuntimeError('Peer pressure changed shared playback')
        if pair['leader'][-1]['generation'] != before['session']['generation']:
            raise RuntimeError('Peer pressure replaced the playback generation')
        diagnostics = recovered_diagnostics(server_log.read_text(errors='replace'), departed)
        observer.run('capture-restarted')
        result = dict(after={r:v[-1] for r,v in pair.items()}, diagnostics=diagnostics, peerResetAcknowledged=True)
    else: raise ValueError('Unsupported phase')
    result.update(phase=phase, physicalAudioAndVisualReviewPending=True)
    report.write_text(json.dumps(result,indent=2)+'\n')
    if result.get('automatedPassed') is False: raise RuntimeError('Honest viewers did not keep advancing')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', nargs='?', choices=('prepare','flood','loaded','departed','recovered'))
    parser.add_argument('--check-peer-log', type=Path)
    for name in ('leader-log','follower-log','peer-log','server-log','output'):
        parser.add_argument('--'+name, type=Path)
    parser.add_argument('--gate-pid', type=int)
    args = parser.parse_args()
    if args.check_peer_log:
        text = args.check_peer_log.read_text(errors='replace')
        # A later disconnect resets counters. Preserve every earlier active
        # sample, and never permit media on this explicitly network-only role.
        if terminal.world.FRAME.search(text) or re.search(r'Acceptance (?:legacy )?video audio timeline:', text):
            raise RuntimeError('Network-only role started a media pipeline')
        matches = list(PEER.finditer(text))
        completed = [m for m in matches if m.group(3) == 'true']
        if not completed: raise RuntimeError('Peer never completed its pressure run')
        check_peer(text[:completed[-1].end()])
        if any(row['unexpectedErrors'] for row in peer_rows(text)):
            raise RuntimeError('Peer reported an unexpected error')
        print('Network-only peer completed pressure without a media pipeline.')
        return
    if not all((args.phase,args.leader_log,args.follower_log,args.peer_log,args.server_log,args.output,args.gate_pid)):
        parser.error('runtime phases require all logs, output and gate PID')
    observer = terminal.Observer(dict(leader=args.leader_log,follower=args.follower_log),args.output,args.gate_pid)
    run(observer,args.phase,args.peer_log,args.server_log,args.gate_pid)
    print('Segment pressure '+args.phase+' observed; physical A/V review remains separate.')


if __name__ == '__main__': main()
