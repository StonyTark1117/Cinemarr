#!/usr/bin/env python3
"""Bounded real follower browse traffic; never substitutes decode logs for visible A/V."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import time

spec = importlib.util.spec_from_file_location('terminal', Path(__file__).with_name('observe-video-terminal.py'))
terminal = importlib.util.module_from_spec(spec); spec.loader.exec_module(terminal)
SENT = re.compile(r'Acceptance browse pressure sent: query=(pressure-\d+)')


def check_pressure_diagnostics(text):
    rows = []
    for line in text.splitlines():
        if 'workQueued=' not in line: continue
        row = {key:int(value) for key,value in re.findall(r'\b(workQueued|workActive|workRejected|browseQueued|browseActive|browseRejected)=(\d+)', line)}
        if len(row) != 6: raise RuntimeError('Missing independent browse/playback diagnostics')
        if row['workQueued'] > 64 or row['workActive'] > 2 or row['browseQueued'] > 16 or row['browseActive'] > 1:
            raise RuntimeError('Work exceeded its configured bound')
        if row['workRejected'] != 0: raise RuntimeError('Browsing caused playback work rejection')
        rows.append(row)
    if len(rows) < 3 or sum(row['browseQueued']==16 and row['browseActive']==1 for row in rows)<2:
        raise RuntimeError('Browse overload was not sustained in real server diagnostics')
    if not any(row['browseRejected']>0 for row in rows): raise RuntimeError('No actual browse rejection')
    return rows


def run(observer, phase, server_log):
    report = observer.output / (phase + '.json')
    if report.exists(): raise RuntimeError('Refusing to overwrite browse-pressure evidence')
    if phase == 'prepare':
        observer.run('capture-queue')
        before = observer.latest()
        if not terminal.same_playback(before, 'PLAYING'): raise RuntimeError('Missing initial shared playback')
        result = dict(session=before['leader'][-1], offsets={r:len(t) for r,t in observer.text().items()},
                      serverOffset=len(server_log.read_text(errors='replace')))
    elif phase == 'flood':
        before = json.loads((observer.output / 'prepare.json').read_text())
        started = time.monotonic(); count = 0; captures = []; frames = []
        next_capture = 18
        while time.monotonic() - started < 40:
            values = observer.text()  # validates live private-display ownership
            observer.controls['follower'].write_text('pressure-' + str(count) + '|video:browse-pressure:pressure-' + str(count) + '\n')
            count += 1
            if time.monotonic() - started >= next_capture:
                current = {r:terminal.world.latest_frame(t) for r,t in values.items()}
                frames.append(current)
                for role,desktop in observer.desktops.items():
                    path = observer.output / ('during-' + str(next_capture) + '-' + role + '.png')
                    if path.exists(): raise RuntimeError('Refusing to replace pressure capture')
                    desktop.capture(path)
                    captures.append(dict(path=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),role=role))
                next_capture += 10
            time.sleep(.15)
        time.sleep(.3)
        sent = SENT.findall(observer.text()['follower'][before['offsets']['follower']:])
        result = dict(seconds=time.monotonic()-started, commandsWritten=count, actualRequests=len(sent), captures=captures,
            sustainedFrames=len(frames)==3 and all(terminal.world.advanced(frames[i-1][r],frames[i][r])
                for i in (1,2) for r in ('leader','follower')))
        result['automatedPassed'] = len(sent) >= 180 and len(captures)==6 and result['sustainedFrames']
    elif phase == 'recovered':
        before = json.loads((observer.output / 'prepare.json').read_text())
        diagnostics = check_pressure_diagnostics(server_log.read_text(errors='replace')[before['serverOffset']:])
        pair = observer.latest()
        if not terminal.same_playback(pair, 'PLAYING', item=before['session']['item'], session=before['session']['session']):
            raise RuntimeError('Pressure changed the shared playback identity')
        offset = len(observer.text()['follower'])
        observer.controls['follower'].write_text('pressure-recovered|video:browse-pressure:pressure-recovered\n')
        observer.wait(lambda values:'Acceptance browse result: query=pressure-recovered' in values['follower'][offset:])
        time.sleep(2)
        fresh = observer.text()['follower'][offset:]
        after_result = fresh.split('Acceptance browse result: query=pressure-recovered',1)[1]
        if re.search(r'Acceptance browse result: query=pressure-\d+',after_result):
            raise RuntimeError('Superseded browse published after the recovered result')
        observer.run('capture-restarted')
        result = dict(after={r:v[-1] for r,v in pair.items()},diagnostics=diagnostics, freshBrowseRecovered=True,
                      staleBrowseNotPublishedAfterRecovery=True)
    else: raise ValueError('Unsupported pressure phase')
    result.update(phase=phase, physicalAudioAndVisualReviewPending=True)
    report.write_text(json.dumps(result,indent=2)+'\n')
    if result.get('automatedPassed') is False: raise RuntimeError('Pressure lacked sustained frames or actual client requests')
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase',choices=('prepare','flood','recovered'))
    parser.add_argument('--leader-log',type=Path,required=True)
    parser.add_argument('--follower-log',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--gate-pid',type=int,required=True)
    parser.add_argument('--server-log',type=Path,required=True)
    args=parser.parse_args()
    run(terminal.Observer(dict(leader=args.leader_log,follower=args.follower_log),args.output,args.gate_pid),args.phase,args.server_log)
    print('Browse pressure '+args.phase+' observed; physical A/V review remains separate.')


if __name__=='__main__': main()
