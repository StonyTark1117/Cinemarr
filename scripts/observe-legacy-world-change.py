#!/usr/bin/env python3
"""Require a same-JVM dimension round trip, with no retained media while away."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re

spec = importlib.util.spec_from_file_location('terminal', Path(__file__).with_name('observe-video-terminal.py'))
terminal = importlib.util.module_from_spec(spec); spec.loader.exec_module(terminal)
WORLD = re.compile(r'Acceptance legacy world state: clientPid=(\d+) dimension=(-?\d+) televisions=(\d+) streams=(\d+) pipelines=(\d+) audioSources=(\d+) decoderThreads=(\d+)')
FIELDS = ('clientPid','dimension','televisions','streams','pipelines','audioSources','decoderThreads')


def worlds(text):
    return [dict(zip(FIELDS,map(int,row))) for row in WORLD.findall(text)]


def idle_in_nether(text, pid):
    rows = worlds(text)
    return (len(rows) >= 2 and all(row['clientPid'] == pid and row['dimension'] == -1
            and all(row[key] == 0 for key in FIELDS[2:]) for row in rows[-2:]))


def run(observer, phase, cycle):
    report = observer.output / ('world-' + phase + '-' + str(cycle) + '.json')
    if report.exists(): raise RuntimeError('Refusing to overwrite world-change evidence')
    if phase == 'begin':
        pair = observer.latest()
        if not terminal.same_playback(pair,'PLAYING'):
            raise RuntimeError('World change requires both clients playing one generation')
        values = observer.text(); rows = {role: worlds(value) for role,value in values.items()}
        if not all(rows.values()): raise RuntimeError('Missing real world-state probes')
        state = {role: value[-1] for role,value in rows.items()}
        if any(row['dimension'] != 0 or row['pipelines'] != 1 or row['audioSources'] != 1 for row in state.values()):
            raise RuntimeError('Unexpected initial world/media ownership')
        identities = {}
        for role,row in state.items():
            desktop = observer.desktops[role]
            if not desktop.owned(row['clientPid']): raise RuntimeError('Client JVM is not owned by this gate')
            identities[role] = desktop.identity(row['clientPid'])[1]
        result = dict(session=pair['leader'][-1], worlds=state, processStartIdentities=identities,
                      offsets={role:len(value) for role,value in values.items()})
    else:
        before = json.loads((observer.output / ('world-begin-' + str(cycle) + '.json')).read_text())
        for role,row in before['worlds'].items():
            desktop = observer.desktops[role]
            if not desktop.owned(row['clientPid']) or desktop.identity(row['clientPid'])[1] != before['processStartIdentities'][role]:
                raise RuntimeError('World-change case replaced or lost a client JVM')
        pid = before['worlds']['follower']['clientPid']
        if phase == 'away':
            values = observer.wait(lambda texts: idle_in_nether(texts['follower'],pid),before['offsets'])
            marker = 'Acceptance legacy world unloaded: dimension=0'
            if marker not in values['follower']: raise RuntimeError('No real overworld unload event')
            after_unload = values['follower'].rsplit(marker,1)[1]
            if terminal.world.FRAME.search(after_unload): raise RuntimeError('Old-world video rendered after unload')
            image = observer.output / ('world-away-' + str(cycle) + '.png')
            if image.exists(): raise RuntimeError('Refusing to replace world-change image')
            observer.desktops['follower'].capture(image)
            result = dict(world=worlds(values['follower'])[-1], image=str(image), imageSha256=hashlib.sha256(image.read_bytes()).hexdigest(),
                          returnOffset=len(observer.text()['follower']))
        elif phase == 'returned':
            away = json.loads((observer.output / ('world-away-' + str(cycle) + '.json')).read_text())
            def returned(values):
                fresh = values['follower'][away['returnOffset']:]
                rows = worlds(fresh); sessions = terminal.states(fresh)
                return (bool(rows) and bool(sessions) and rows[-1]['clientPid'] == pid and rows[-1]['dimension'] == 0
                    and all(rows[-1][key] == 1 for key in FIELDS[2:])
                    and sessions[-1]['session'] == before['session']['session']
                    and sessions[-1]['generation'] == before['session']['generation']
                    and sessions[-1]['status'] == 'PLAYING')
            values = observer.wait(returned)
            pair = {role:terminal.states(value) for role,value in values.items()}
            if not terminal.same_playback(pair,'PLAYING',item=before['session']['item'],session=before['session']['session']):
                raise RuntimeError('Dimension return changed shared playback identity')
            if 'Acceptance legacy world unloaded: dimension=-1' not in values['follower'][away['returnOffset']:]:
                raise RuntimeError('No real Nether unload event')
            result = dict(world=worlds(values['follower'])[-1], session=pair['follower'][-1], sameClientProcesses=True)
        else: raise ValueError('Unsupported world phase')
    result.update(phase=phase,cycle=cycle,physicalAudioAndVisualReviewPending=True)
    report.write_text(json.dumps(result,indent=2)+'\n')
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase',choices=('begin','away','returned'))
    parser.add_argument('--cycle',type=int,choices=(1,2),required=True)
    parser.add_argument('--leader-log',type=Path,required=True)
    parser.add_argument('--follower-log',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--gate-pid',type=int,required=True)
    args=parser.parse_args()
    observer=terminal.Observer(dict(leader=args.leader_log,follower=args.follower_log),args.output,args.gate_pid)
    run(observer,args.phase,args.cycle)
    print('Legacy world-change '+args.phase+' cycle '+str(args.cycle)+' observed; physical A/V review remains separate.')


if __name__=='__main__': main()
