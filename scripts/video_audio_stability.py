"""Identity-bound readiness for every playing TV in the display scenario."""
import re
from uuid import UUID


def fields(line):
    # Legacy Log4j can append the closing CDATA/XML tags directly to the last
    # value on a console line. Keep those transport delimiters out of the
    # identity rather than silently discarding otherwise valid telemetry.
    return dict(re.findall(r'\b([A-Za-z]+)=([^\s<\]]+)', line))


def identity(row):
    key = (str(UUID(row['timeline'])), int(row['timelineGeneration']),
           str(UUID(row['stream'])), int(row['streamGeneration']))
    if key[1] < 0 or key[3] < 0:
        raise ValueError('Negative generation')
    return key


def readings(text):
    states, timelines, events = {}, {}, {}
    for number, line in enumerate(text.splitlines(), 1):
        if any(marker in line for marker in ('Video segment request exceeds playback lead limit',
               'Cinemarr rejected video segment', 'Cinemarr rejected legacy video segment',
               'Invalid or excessive segment request')):
            raise ValueError('Rejected transfer in display evidence')
        if 'Acceptance TV display:' in line:
            row = fields(line)
            states[row['television']] = row
        match = re.search(r'Acceptance (legacy )?video audio (scheduled|rebuffer|timeline):', line)
        if not match:
            continue
        row = fields(line)
        try:
            key = identity(row)
        except (KeyError, ValueError):
            continue  # Unbound or historical telemetry cannot establish readiness.
        if match[2] == 'timeline':
            timelines[key] = (number, row, bool(match[1]))
        else:
            events[key] = (number, match[2])
    quick = [row for row in states.values() if row['origin'] == 'QUICK']
    if len(states) != 3 or len(quick) != 1 or quick[0]['status'] != 'PLAYING':
        raise ValueError('The three-TV scene and playing Quick primary are required')
    active = [identity(row) for row in states.values() if row['status'] == 'PLAYING']
    if len(set(active)) != len(active):
        raise ValueError('Different TVs must own different streams')
    result = {}
    for key in active:
        if key not in timelines or key not in events:
            raise ValueError('Active stream has no matching audio telemetry')
        number, row, legacy = timelines[key]
        target, video, drift = (int(row[name]) for name in ('targetMs', 'videoMs', 'driftMs'))
        if (events[key][1] != 'scheduled' or int(row['underruns']) != 0
                or video <= 0 or abs(video-target) > 250 or abs(drift) > 150
                or int(row['pendingFrames' if legacy else 'javaBufferMs']) <= 0
                or (legacy and row.get('started') != 'true')):
            raise ValueError('An active TV has unstable audio/video')
        result[key] = (number, events[key][0])
    return result


class Stability:
    def __init__(self, initial):
        self.baseline = {role: len(text.splitlines()) for role, text in initial.items()}
        self.last = {}
        self.signature = None
        self.since = None
        self.reason = 'Waiting for fresh telemetry'

    def update(self, texts, now):
        try:
            pair = {role: readings(text) for role, text in texts.items()}
            if pair['leader'].keys() != pair['follower'].keys():
                raise ValueError('Clients disagree on active stream identities')
            signature = tuple((role, key, event) for role in sorted(pair)
                              for key, (_, event) in sorted(pair[role].items()))
            if signature != self.signature:
                self.signature, self.since = signature, None
            for role, streams in pair.items():
                for key, (line, event) in streams.items():
                    if line <= self.baseline[role]:
                        raise ValueError('Audio telemetry predates this readiness call')
                    previous = self.last.get((role, key))
                    if previous is None or previous[0] != line:
                        self.last[(role, key)] = (line, now)
                    elif now-previous[1] > 2:
                        raise ValueError('An active TV stopped reporting fresh telemetry')
            if self.since is None:
                self.since = now
            self.reason = 'Waiting for eight continuous stable seconds'
            return now-self.since >= 8
        except (KeyError, ValueError) as error:
            self.since = None
            self.reason = str(error)
            return False
