#!/usr/bin/env python3
"""Live reload observer contracts, without opening displays or game clients."""
import importlib.util
import itertools
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('reload_probe', Path(__file__).with_name('exercise-video-resource-reload.py'))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


def state(generation=11, status='PLAYING'):
    return 'Acceptance video session: controller=1 session=test-session generation=' + str(generation) + ' status=' + status + ' item=123\n'


def frame(pts):
    return 'Acceptance video rendered: television=test-tv frameSha256=' + 'a' * 64 + ' ptsUs=' + str(pts) + ' rectangles=1\n'


class ReloadTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.base = Path(temp.name)
        self.logs = {role: self.base / ('1.7.10-forge.audio-' + role + '.console.log') for role in ('leader', 'follower')}
        for role, log in self.logs.items():
            log.write_text('ROLE:' + role + '\n' + state() + '\n'.join(probe.MARKERS) + '\n' + frame(100))
            probe_path = log.with_name(log.name.removesuffix('.console.log') + '.control')
            probe_path.touch()
        self.output = self.base / 'reload'
        self.pts = 100
        self.respond = True
        self.reloading = True
        self.advance = True
        self.mutate = False
        self.events = []
        test = self

        class Desktop:
            def __init__(self, text, gate_pid):
                self.role = text.splitlines()[0].removeprefix('ROLE:')
                self.xpid = 100 if self.role == 'leader' else 200
                self.responded = False
                self.last_control = None
                self.in_world = False

            def validate(self):
                control = probe.world.control_path(test.logs[self.role], self.role)
                value = control.read_text()
                if test.respond and value and value != self.last_control:
                    test.append(self.role, 'Acceptance video UI: width=320 height=240 widgets=20 clipped=0 canControl='
                                + ('true' if self.role == 'leader' else 'false') + ' overlaps=0\n')
                    self.responded = True
                    self.last_control = value

            def escape(self):
                test.assertTrue(self.responded)
                test.events.append('escape-' + self.role)
                self.in_world = True

            def reload_resources(self):
                test.assertTrue(self.responded)
                test.assertTrue(self.in_world, 'Every reload must start in a known world view, not the chat opened by T')
                self.in_world = False
                test.events.append('reload-' + self.role)
                if test.mutate:
                    test.append(self.role, state(12))
                if test.reloading:
                    test.append(self.role, '\n'.join(probe.MARKERS) + '\n')

        for target, name, value in ((probe.world, 'PrivateMinecraftWindow', Desktop),
                                     (probe.time, 'sleep', self.tick)):
            patcher = patch.object(target, name, value)
            patcher.start(); self.addCleanup(patcher.stop)
        patcher = patch.object(probe.time, 'monotonic', side_effect=itertools.count(0, 1))
        patcher.start(); self.addCleanup(patcher.stop)

    def append(self, role, text):
        with self.logs[role].open('a') as stream:
            stream.write(text)

    def tick(self, _):
        if self.advance:
            self.pts += 100
            for role in self.logs:
                self.append(role, frame(self.pts))

    def exercise(self):
        return probe.exercise(self.logs, self.output, 42)

    def test_two_reload_cycles_each_client_no_automatic_av_acceptance(self):
        result = self.exercise()
        self.assertTrue(result['reloadCompleted'])
        self.assertTrue(result['physicalAudioAndVisualReviewPending'])
        self.assertNotIn('passed', result)
        self.assertEqual([('leader', 1), ('leader', 2), ('follower', 1), ('follower', 2)],
                         [(row['role'], row['cycle']) for row in result['actions']])
        self.assertEqual(['escape-leader', 'reload-leader', 'escape-leader', 'reload-leader',
                          'escape-follower', 'reload-follower', 'escape-follower', 'reload-follower'], self.events)

    def test_initial_startup_markers_do_not_count_as_reload(self):
        self.reloading = False
        with self.assertRaisesRegex(RuntimeError, 'No fresh completed'): self.exercise()
        self.assertFalse((self.output / 'result.json').exists())
        self.assertEqual(1, self.events.count('reload-leader'), 'Failure must not trigger a second attempt')

    def test_old_frames_do_not_prove_post_reload_render(self):
        self.advance = False
        with self.assertRaisesRegex(RuntimeError, 'No fresh completed'): self.exercise()

    def test_generation_change_cannot_pass_as_reload_recovery(self):
        self.mutate = True
        with self.assertRaisesRegex(RuntimeError, 'Session or log changed'): self.exercise()

    def test_idle_session_rejected_before_input(self):
        self.append('leader', state(status='IDLE'))
        with self.assertRaisesRegex(RuntimeError, 'active video'): self.exercise()
        self.assertEqual([], self.events)

    def test_idle_packet_with_empty_item_cannot_hide_behind_old_playing_state(self):
        self.append('leader', 'Acceptance video session: controller=1 session=test-session generation=12 status=IDLE item= positionMs=0\n')
        with self.assertRaisesRegex(RuntimeError, 'active video'): self.exercise()
        self.assertEqual([], self.events)

    def test_mismatched_sessions_rejected(self):
        self.append('follower', state(12))
        with self.assertRaisesRegex(RuntimeError, 'same playing session'): self.exercise()

    def test_fresh_controller_required(self):
        self.respond = False
        with self.assertRaisesRegex(RuntimeError, 'No fresh correctly privileged'): self.exercise()
        self.assertEqual([], self.events)

    def test_existing_evidence_preserved(self):
        self.output.mkdir()
        with self.assertRaisesRegex(RuntimeError, 'overwrite'): self.exercise()
        self.assertEqual([], self.events)

    def test_resource_reload_without_sound_restart_is_incomplete(self):
        self.assertIsNone(probe.completed_reload(probe.MARKERS[0] + '\nSound engine started'))
        self.assertIsNone(probe.completed_reload('\n'.join(reversed(probe.MARKERS))))
        self.assertIsNotNone(probe.completed_reload('\n'.join(probe.MARKERS)))

    def test_live_hook_is_video_only_and_before_physical_pcm(self):
        source = Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        before, video = source.split('run_two_client_video() {', 1)
        self.assertNotIn('scripts/exercise-video-resource-reload.py', before)
        self.assertEqual(1, video.count('scripts/exercise-video-resource-reload.py'))
        self.assertLess(video.index('scripts/exercise-video-resource-reload.py'), video.index('capture_calibrated_audio_pair'))
        self.assertIn('"$label" == "1.7.10-forge" && "$video_control_gate" == true', video)
        self.assertLess(video.index('capture_calibrated_audio_pair'), video.index('scripts/capture-post-reconnect-video.py'))
        self.assertIn('"$video_control_gate" == true && "$video_terminal_gate" != true && ( "$live_plex_gate" == true || "$label" == "1.7.10-forge" )', video)


if __name__ == '__main__': unittest.main()
