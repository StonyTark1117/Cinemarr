#!/usr/bin/env python3
"""Post-reconnect capture contracts; no real displays, network or game clients."""
import importlib.util
import itertools
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('capture', Path(__file__).with_name('capture-post-reconnect-video.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


def frame(pts=100, tv='test-tv'):
    return 'Acceptance video rendered: television=' + tv + ' frameSha256=' + 'a' * 64 + ' ptsUs=' + str(pts) + ' rectangles=1\n'


class CaptureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.logs = {role: self.base / ('test.audio-' + role + '.console.log') for role in ('leader', 'follower')}
        for role, log in self.logs.items():
            log.write_text('ROLE:' + role + '\n' + frame())
            log.with_name(log.name.removesuffix('.console.log') + '.control').touch()
        self.before = self.base / 'test.audio-follower.pre-reconnect.console.log'
        self.before.write_text('Acceptance client media reset complete\n')
        self.output = self.base / 'capture'
        self.pts = 100
        self.events = []
        self.respond = True
        self.moving = True
        test = self

        class Desktop:
            def __init__(self, log, gate_pid):
                self.role = log.splitlines()[0].removeprefix('ROLE:')
                self.xpid = 100 if self.role == 'leader' else 200
                self.responded = False

            def validate(self):
                control = capture.control_path(test.logs[self.role], self.role)
                if test.respond and control.read_text() and not self.responded:
                    test.append(self.role, 'Acceptance video UI: width=320 height=240 widgets=20 clipped=0 canControl='
                                + ('true' if self.role == 'leader' else 'false') + ' overlaps=0\n')
                    self.responded = True

            def escape(self):
                test.assertTrue(self.responded, 'Escape requires fresh known controller state')
                test.events.append('escape-' + self.role)

            def capture(self, path):
                test.events.append('capture-' + self.role)
                path.write_bytes(b'mocked framebuffer')

        patcher = patch.object(capture, 'PrivateMinecraftWindow', Desktop)
        patcher.start(); self.addCleanup(patcher.stop)
        patcher = patch.object(capture.time, 'sleep', side_effect=self.tick)
        patcher.start(); self.addCleanup(patcher.stop)
        patcher = patch.object(capture.time, 'monotonic', side_effect=itertools.count(0, 1))
        patcher.start(); self.addCleanup(patcher.stop)

    def append(self, role, text):
        with self.logs[role].open('a') as stream:
            stream.write(text)

    def tick(self, _):
        if self.moving:
            self.pts += 100
            for role in self.logs:
                self.append(role, frame(self.pts))

    def collect(self):
        return capture.collect(self.logs, self.output, 42)

    def test_three_fixed_pairs_require_direct_review(self):
        result = self.collect()
        self.assertTrue(result['captureCompleted'])
        self.assertTrue(result['directVisualReviewPending'])
        self.assertNotIn('visualAccepted', result)
        self.assertEqual(6, len(result['captures']))
        self.assertEqual(['escape-leader', 'escape-follower'], self.events[:2])
        self.assertEqual([1, 1, 2, 2, 3, 3], [v['pair'] for v in result['captures']])

    def test_existing_output_never_overwritten(self):
        self.output.mkdir()
        with self.assertRaises(RuntimeError): self.collect()
        self.assertEqual([], self.events)

    def test_missing_pre_reconnect_reset_rejected(self):
        self.before.write_text('process exited only')
        with self.assertRaises(RuntimeError): self.collect()
        self.assertEqual([], self.events)

    def test_stale_ui_is_not_fresh_acknowledgement(self):
        self.respond = False
        for role in self.logs:
            self.append(role, 'Acceptance video UI: width=320 height=240 widgets=20 clipped=0 canControl='
                        + ('true' if role == 'leader' else 'false') + ' overlaps=0\n')
        with self.assertRaisesRegex(RuntimeError, 'No fresh'): self.collect()
        self.assertEqual([], self.events)

    def test_stalled_render_cannot_pass_from_old_frames(self):
        self.moving = False
        with self.assertRaisesRegex(RuntimeError, 'did not advance'): self.collect()
        self.assertFalse((self.output / 'result.json').exists())

    def test_control_symlink_rejected(self):
        control = capture.control_path(self.logs['leader'], 'leader')
        control.unlink(); control.symlink_to(self.before)
        with self.assertRaises(RuntimeError): self.collect()

    def test_wrong_log_role_rejected(self):
        with self.assertRaises(RuntimeError): capture.control_path(self.logs['leader'], 'follower')

    def test_missing_frame_rejected(self):
        with self.assertRaises(RuntimeError): capture.latest_frame('Acceptance video ready')

    def test_same_hash_does_not_mean_frozen_render_timestamp(self):
        self.assertTrue(capture.advanced(capture.latest_frame(frame()), capture.latest_frame(frame(101))))
        self.assertFalse(capture.advanced(capture.latest_frame(frame()), capture.latest_frame(frame(99))))

    def test_television_change_rejected(self):
        with self.assertRaises(RuntimeError):
            capture.advanced(capture.latest_frame(frame()), capture.latest_frame(frame(101, 'different-tv')))

    def test_hook_runs_only_in_video_path_after_physical_pcm(self):
        source = Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        before_video, video = source.split('run_two_client_video() {', 1)
        self.assertNotIn('scripts/capture-post-reconnect-video.py', before_video)
        self.assertEqual(1, video.count('scripts/capture-post-reconnect-video.py'))
        self.assertLess(video.index('capture_calibrated_audio_pair'), video.index('scripts/capture-post-reconnect-video.py'))
        self.assertIn('"$video_control_gate" == true && "$video_terminal_gate" != true && ( "$live_plex_gate" == true || "$label" == "1.7.10-forge" )', video)


if __name__ == '__main__': unittest.main()
