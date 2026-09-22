#!/usr/bin/env python3
import unittest
from video_audio_stability import Stability, readings


def key(index=1, generation=1):
    return ('timeline=00000000-0000-0000-0000-000000000010 timelineGeneration=1 '
            f'stream=00000000-0000-0000-0000-{index:012d} streamGeneration={generation}')


def scene(legacy=False):
    text = ''
    for index in (1, 2, 3):
        text += (f'Acceptance TV display: television={index} origin={"QUICK" if index==1 else "CUSTOM"} '
                 f'status={"BUFFERING" if index==3 else "PLAYING"} streamState={"WAITING" if index==3 else "READY"} '
                 +key(index)+'\n')
    prefix = 'Acceptance '+('legacy ' if legacy else '')+'video audio '
    for index in (1, 2):
        text += prefix+'scheduled: '+key(index)+'\n'
    return text


def sample(index=1, legacy=False, generation=1, **changes):
    row = dict(targetMs=10000, videoMs=10000, driftMs=0, underruns=0,
               javaBufferMs=500, pendingFrames=5, started='true')
    row.update(changes)
    return ('Acceptance '+('legacy ' if legacy else '')+'video audio timeline: '
            +' '.join(f'{k}={v}' for k,v in row.items())+' '+key(index,generation)+'\n')


class AudioStabilityTests(unittest.TestCase):
    def test_legacy_log4j_xml_suffix_preserves_stream_identity(self):
        text = scene(True)
        text += sample(1, True).rstrip() + ']]></log4j:Message>\n'
        text += sample(2, True).rstrip() + ']]></log4j:Message>\n'
        self.assertEqual(2, len(readings(text)))

    def test_waiting_tv_does_not_hide_either_active_tv(self):
        for legacy in (False, True):
            texts = {role:scene(legacy) for role in ('leader','follower')}
            waiter = Stability(texts)
            for tick in range(9):
                for role in texts:
                    texts[role] += sample(1,legacy)+sample(2,legacy)+sample(3,legacy,videoMs=0,driftMs=-10000,javaBufferMs=0,pendingFrames=0)
                self.assertEqual(tick==8,waiter.update(texts,tick))

    def test_every_playing_tv_must_have_matching_generation(self):
        for index in (1,2):
            text=scene()+sample(1)+sample(2,generation=2 if index==2 else 1)
            if index==1: text=scene()+sample(1,generation=2)+sample(2)
            with self.assertRaises(ValueError): readings(text)

    def test_active_empty_audio_and_threshold_violations_are_rejected(self):
        for bad in (dict(javaBufferMs=0),dict(videoMs=0),dict(underruns=1),
                    dict(driftMs=151),dict(driftMs=-151),dict(videoMs=10251),dict(videoMs=9749)):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                readings(scene()+sample(1)+sample(2,**bad))

    def test_legacy_requires_started_backend_and_pending_frames(self):
        for bad in (dict(started='false'),dict(pendingFrames=0)):
            with self.assertRaises(ValueError):readings(scene(True)+sample(1,True)+sample(2,True,**bad))

    def test_historical_and_stalled_rows_cannot_establish_readiness(self):
        text=scene()+sample(1)+sample(2)
        texts=dict(leader=text,follower=text)
        waiter=Stability(texts)
        for now in (0,4,8,20):self.assertFalse(waiter.update(texts,now))
        texts={role:text+sample(1)+sample(2) for role in texts}
        self.assertFalse(waiter.update(texts,21))
        self.assertFalse(waiter.update(texts,29))

    def test_one_stalled_sibling_cannot_borrow_fresh_primary_rows(self):
        texts={role:scene() for role in ('leader','follower')};waiter=Stability(texts)
        for tick in range(12):
            for role in texts:texts[role]+=sample(1)+(sample(2) if tick==0 else '')
            self.assertFalse(waiter.update(texts,tick))

    def test_rebuffer_and_new_schedule_restart_continuous_interval(self):
        texts={role:scene() for role in ('leader','follower')};waiter=Stability(texts)
        for tick in range(16):
            if tick==4:texts['follower']+='Acceptance video audio rebuffer: '+key(2)+'\n'
            if tick==6:texts['follower']+='Acceptance video audio scheduled: '+key(2)+'\n'
            for role in texts:texts[role]+=sample(1)+sample(2)
            self.assertEqual(tick>=14,waiter.update(texts,tick))

    def test_wrong_timeline_and_disagreeing_viewers_fail_closed(self):
        text=scene()+sample(1)+sample(2)
        wrong=text.replace('timelineGeneration=1','timelineGeneration=2')
        waiter=Stability(dict(leader='',follower=''))
        self.assertFalse(waiter.update(dict(leader=text,follower=wrong),0))
        with self.assertRaises(ValueError):readings(scene()+sample(1)+sample(2).replace('timelineGeneration=1','timelineGeneration=2'))

    def test_unbound_telemetry_cannot_certify_playback(self):
        with self.assertRaises(ValueError):readings(scene()+sample(1)+sample(2).replace(key(2),'identity=unbound'))

    def test_waiting_primary_and_duplicate_streams_are_rejected(self):
        text=scene()+sample(1)+sample(2)
        with self.assertRaises(ValueError):readings(text.replace('origin=QUICK status=PLAYING','origin=QUICK status=BUFFERING'))
        with self.assertRaises(ValueError):readings(text.replace(key(2),key(1)))

    def test_transfer_rejection_is_not_hidden_by_healthy_audio(self):
        with self.assertRaises(ValueError):readings(scene()+sample(1)+sample(2)+'Invalid or excessive segment request\n')


if __name__ == '__main__':unittest.main()
