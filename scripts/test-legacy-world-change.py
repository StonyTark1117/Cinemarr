#!/usr/bin/env python3
"""World-change evidence contracts without starting Minecraft or opening a GUI."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('world_probe',Path(__file__).with_name('observe-legacy-world-change.py'))
probe=importlib.util.module_from_spec(spec); spec.loader.exec_module(probe)


def world(pid=101,dimension=-1,count=0,threads=None):
    if threads is None: threads=count
    return (f'Acceptance legacy world state: clientPid={pid} dimension={dimension} televisions={count} '
            f'streams={count} pipelines={count} audioSources={count} decoderThreads={threads}\n')


def session(owner):
    return ('Acceptance video session: controller=1 session=party generation=5 status=PLAYING item=9001 '
            f'positionMs=0 canControl={str(owner).lower()} streams=1 audio=-1 subtitle=-1 durationMs=300000 message=Playing\n')


class WorldTests(unittest.TestCase):
    def setUp(self):
        directory=tempfile.TemporaryDirectory(); self.addCleanup(directory.cleanup)
        self.output=Path(directory.name)
        self.values=dict(leader=session(True)+world(100,0,1),follower=session(False)+world(101,0,1))
        self.observer=probe.terminal.Observer.__new__(probe.terminal.Observer)
        self.observer.output=self.output; self.observer.text=lambda:self.values
        class Desktop:
            def owned(self,pid): return pid in (100,101)
            def identity(self,pid): return (1,'start-'+str(pid))
            def capture(self,path): path.write_bytes(b'test image placeholder, never visual evidence')
        self.observer.desktops=dict(leader=Desktop(),follower=Desktop())
        def wait(predicate,offsets=None,timeout=90):
            values={r:t[offsets[r]:] if offsets else t for r,t in self.values.items()}
            if not predicate(values): raise RuntimeError('Missing fresh phase state')
            return values
        self.observer.wait=wait

    def away(self):
        probe.run(self.observer,'begin',1)
        self.values['follower']+='Acceptance legacy world unloaded: dimension=0\n'+world()+world()
        return probe.run(self.observer,'away',1)

    def test_two_empty_samples_required_and_decoder_threads_must_exit(self):
        self.assertFalse(probe.idle_in_nether(world(),101))
        self.assertTrue(probe.idle_in_nether(world()+world(),101))
        for last in (world(dimension=0),world(count=1),world(threads=1),world(pid=102)):
            self.assertFalse(probe.idle_in_nether(world()+last,101))

    def test_same_jvm_round_trip_does_not_claim_physical_acceptance(self):
        away=self.away()
        self.assertTrue(away['physicalAudioAndVisualReviewPending'])
        self.values['follower']+='Acceptance legacy world unloaded: dimension=-1\n'+world(101,0,1)+session(False)
        result=probe.run(self.observer,'returned',1)
        self.assertTrue(result['sameClientProcesses']); self.assertTrue(result['physicalAudioAndVisualReviewPending'])
        self.assertNotIn('passed',result)

    def test_old_overworld_playback_cannot_prove_return(self):
        self.away()
        with self.assertRaises(RuntimeError): probe.run(self.observer,'returned',1)

    def test_return_rejects_abandoned_transfer_stall_even_after_playback_recovers(self):
        self.away()
        self.values['follower'] += ('Acceptance legacy world unloaded: dimension=-1\n'
            '[CHAT] Cinemarr: A video transfer window is already awaiting acknowledgement\n'
            + world(101,0,1) + session(False))
        with self.assertRaisesRegex(RuntimeError, 'abandoned transfer'):
            probe.run(self.observer,'returned',1)
        self.assertFalse((self.output/'world-returned-1.json').exists())

    def test_replaced_jvm_cannot_prove_return(self):
        self.away()
        self.values['follower']+='Acceptance legacy world unloaded: dimension=-1\n'+world(102,0,1)+session(False)
        with self.assertRaises(RuntimeError): probe.run(self.observer,'returned',1)

    def test_post_unload_video_render_is_rejected(self):
        probe.run(self.observer,'begin',1)
        self.values['follower']+='Acceptance legacy world unloaded: dimension=0\n'+world()+world()
        self.values['follower']+='Acceptance video rendered: television=tv frameSha256='+'a'*64+' ptsUs=1000 rectangles=1\n'
        with self.assertRaises(RuntimeError): probe.run(self.observer,'away',1)

    def test_existing_phase_evidence_is_preserved(self):
        path=self.output/'world-begin-1.json'; path.write_text('old failure')
        with self.assertRaises(RuntimeError): probe.run(self.observer,'begin',1)
        self.assertEqual('old failure',path.read_text())

    def test_real_shell_uses_dimension_commands_not_follower_relaunch(self):
        source=Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        body=source.split('run_legacy_world_change_scenarios() {',1)[1].split('\n}\n',1)[0]
        self.assertIn('for cycle in 1 2',body)
        self.assertIn('cinemarr acceptance-dimension -1',body); self.assertIn('cinemarr acceptance-dimension 0',body)
        self.assertNotIn('start_audio_client',body); self.assertNotIn('terminate_client_launch',body)
        self.assertIn('real_video_capture_is_silent',body); self.assertIn('capture_calibrated_audio_pair',body)
        self.assertEqual(2,source.count("+=' -Dcinemarr.acceptance.worldChangeProbe=true'"))


if __name__=='__main__': unittest.main()
