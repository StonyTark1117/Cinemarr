#!/usr/bin/env python3
"""Terminal state and closed-log contracts; these tests never open a display."""
import importlib.util
import itertools
from pathlib import Path
import tempfile
import subprocess
import unittest
from unittest.mock import patch


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


probe = module('observe-video-terminal')
health = module('check-video-underruns')


def state(owner=True, generation=5, status='PLAYING', item='9001', session='party'):
    return (f'Acceptance video session: controller=1 session={session} generation={generation} status={status} '
            f'item={item} positionMs=0 canControl={str(owner).lower()} streams=1 audio=-1 subtitle=-1 '
            'durationMs=300000 message=Playing next queued video\n')


def pair(**kwargs):
    return {role: probe.states(state(role == 'leader', **kwargs)) for role in ('leader', 'follower')}


class TerminalTests(unittest.TestCase):
    def test_idle_empty_item_is_latest_not_hidden_by_old_playing(self):
        rows = probe.states(state() + state(status='IDLE', item='', generation=6))
        self.assertEqual(2, len(rows))
        self.assertEqual('', rows[-1]['item'])
        self.assertEqual('IDLE', rows[-1]['status'])

    def test_xml_log_suffix_not_part_of_message_or_queue_item(self):
        row = probe.states(state().rstrip() + ']]></Message>')[0]
        self.assertEqual('Playing next queued video', row['message'])
        self.assertEqual('', probe.queues('Acceptance video queue: session=party generation=6 entries=0 firstItem=]]>')[0]['firstItem'])

    def test_requires_new_matching_generation_status_item_owner_and_session(self):
        self.assertTrue(probe.same_playback(pair(), 'PLAYING', 4, '9001', 'party'))
        self.assertFalse(probe.same_playback(pair(), 'PLAYING', 5))
        for key, value in [('generation', 6), ('session', 'other'), ('status', 'IDLE'), ('item', 'other'), ('owner', True)]:
            rows = pair(); rows['follower'][-1][key] = value
            self.assertFalse(probe.same_playback(rows, 'PLAYING'), key)
        self.assertFalse(probe.same_playback(pair(session='other'), 'PLAYING', 4, '9001', 'party'))
        self.assertFalse(probe.same_playback({'leader': [], 'follower': []}, 'IDLE'))

    def test_wait_cannot_reuse_old_success_before_command_offset(self):
        observer = probe.Observer.__new__(probe.Observer)
        values = {role: state(role == 'leader') for role in ('leader', 'follower')}
        observer.text = lambda: values
        with patch.object(probe.time, 'monotonic', side_effect=itertools.count()), patch.object(probe.time, 'sleep'):
            with self.assertRaises(RuntimeError):
                observer.wait(lambda texts: probe.same_playback({r: probe.states(t) for r, t in texts.items()}, 'PLAYING'),
                              {r: len(t) for r, t in values.items()}, timeout=2)

    def test_existing_report_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            observer = probe.Observer.__new__(probe.Observer)
            observer.output = Path(directory)
            report = observer.output / 'queue.json'; report.write_text('original failure')
            with self.assertRaises(RuntimeError): observer.run('queue')
            self.assertEqual('original failure', report.read_text())

    def test_idle_report_does_not_claim_reconnect_or_physical_acceptance(self):
        import json
        with tempfile.TemporaryDirectory() as directory:
            observer = probe.Observer.__new__(probe.Observer)
            observer.output = Path(directory)
            (observer.output / 'near-end.json').write_text(json.dumps(dict(before=dict(session='party'), after=dict(leader=dict(generation=4)))))
            texts = {r: state(r == 'leader', status='IDLE', item='') for r in ('leader', 'follower')}
            observer.text = lambda: texts
            result = observer.run('ended')
            self.assertTrue(result['emptyQueueReachedIdle'])
            self.assertTrue(result['physicalAudioAndVisualReviewPending'])
            self.assertNotIn('followerReconnectAcrossEOS', result)
            self.assertNotIn('passed', result)

    def test_closed_log_rejects_active_underrun_even_after_counter_reset(self):
        healthy = 'Acceptance video audio timeline: starvations=0 underruns=0\n'
        for failure in ['Acceptance video audio timeline: underruns=1\n',
                        'Acceptance legacy video audio active underrun: underruns=1\n',
                        'audioUnderruns=12\n']:
            with self.assertRaises(ValueError): health.check(healthy + failure + healthy)

    def test_terminal_backend_exhaustion_is_not_active_underrun(self):
        health.check('Acceptance video audio terminal drain: targetMs=300000\n'
                     'Acceptance video audio timeline: starvations=1 underruns=0\n')
        health.check('Acceptance legacy video audio timeline: underruns=0\n')
        with self.assertRaises(ValueError): health.check('process running')

    def test_actual_shell_health_gate_turns_red_on_injected_active_starvation(self):
        source = Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        function = 'verify_video_health_reports() {' + source.split('verify_video_health_reports() {', 1)[1].split('\n}\n', 1)[0] + '\n}\n'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            healthy = 'Acceptance legacy video audio timeline: pendingFrames=8 underruns=0\n'
            for role in ('leader', 'follower'):
                (root / ('1.7.10-forge.audio-' + role + '.console.log')).write_text(healthy)
            command = ['bash', '-c', 'set -uo pipefail\noutput_root=$1\nrepo_root=$2\n' + function
                       + '\nverify_video_health_reports 1.7.10-forge', 'health-contract', directory,
                       str(Path(__file__).resolve().parents[1])]
            self.assertEqual(0, subprocess.run(command, capture_output=True).returncode)
            failed = root / '1.7.10-forge.audio-follower.pre-reconnect.console.log'
            failed.write_text(healthy + 'Acceptance legacy video audio active underrun: underruns=1\n' + healthy)
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Active video audio underrun at log line 2', result.stderr)

    def test_shell_routes_terminal_as_supplement_not_normal_widget_coverage(self):
        shell = Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        body = shell.split('run_two_client_video() {', 1)[1].split('\n}\n', 1)[0]
        self.assertIn('"$video_control_gate" == true && "$video_terminal_gate" != true', body)
        self.assertLess(body.index('> "$evidence"'), body.index('run_video_terminal_scenarios'))
        self.assertLess(body.index('run_video_terminal_scenarios'), body.index('# Exercise the real disconnect cleanup'))
        terminal = shell.split('run_video_terminal_scenarios() {', 1)[1].split('\n}\n', 1)[0]
        self.assertLess(terminal.index('terminate_client_launch'), terminal.index('mv -- "$follower_log"'))
        self.assertLess(terminal.index('mv -- "$follower_log"'), terminal.index('start_audio_client'))
        self.assertIn('video_terminal_follower_pid=$follower_pid', terminal)
        self.assertIn('real_video_capture_is_silent', terminal)


if __name__ == '__main__': unittest.main()
