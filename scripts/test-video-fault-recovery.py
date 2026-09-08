#!/usr/bin/env python3
"""Exercise the actual shell recovery gates with deterministic collaborators."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'scripts/run-dedicated-server-gate.sh').read_text()


def function(name):
    return name + '() {' + SOURCE.split(name + '() {', 1)[1].split('\n}\n', 1)[0] + '\n}\n'


class RecoveryTests(unittest.TestCase):
    def run_shell(self, script, **environment):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(['bash', '-uo', 'pipefail'], input=script, text=True,
                                    cwd=directory, capture_output=True,
                                    env=dict(os.environ, **environment), timeout=10)
            files = {str(p.relative_to(directory)): p.read_text()
                     for p in Path(directory).rglob('*') if p.is_file()}
            return result, files

    def capture(self, **environment):
        return self.run_shell(r'''
output_root=$PWD
repo_root=/fixture
generation_value=12
trace=$PWD/trace
latest_video_generation() { printf '%s\n' "$generation_value"; }
python3() {
  printf 'python %s\n' "$*" >> "$trace"
  if [[ "$1" == */observe-video-terminal.py ]]; then
    [[ "${FAIL:-}" != frames ]] || return 1
    while [[ "$1" != --output ]]; do shift; done
    mkdir -p "$2"
  else
    [[ "${FAIL:-}" != sync ]] || return 1
    printf '{"passed":true}\n'
  fi
}
capture_calibrated_audio_pair() {
  printf 'pcm %s\n' "$*" >> "$trace"
  [[ "${FAIL:-}" != pcm ]] || return 1
  if [[ "${FAIL:-}" == generation ]]; then generation_value=13; fi
}
audio_capture_is_audible() {
  printf 'audible %s\n' "$*" >> "$trace"
  [[ "${FAIL:-}" != silence ]]
}
verify_video_health_reports() { [[ "${FAIL:-}" != health ]]; }
''' + function('capture_video_fault_recovery') + r'''
if [[ "${PREEXISTING:-}" == true ]]; then
  mkdir -p case.video-adverse-network/transient
  printf 'retained failure\n' > case.video-adverse-network/transient/evidence.txt
fi
capture_video_fault_recovery case "${PHASE:-transient}" owned_leader owned_follower 12
''', **environment)

    def test_real_capture_function_requires_fresh_frames_then_both_physical_outputs(self):
        result, files = self.capture()
        self.assertEqual(0, result.returncode, result.stderr)
        trace = files['trace']
        self.assertLess(trace.index('capture-restarted'), trace.index('pcm owned_leader owned_follower'))
        self.assertIn('--gate-pid ', trace)
        for role in ('leader', 'follower'):
            self.assertIn('/transient/' + role + '.s16le', trace)
        self.assertIn('compare-pcm-sync.py', trace)
        self.assertIn('direct visual review remains required', files['case.video-adverse-network/transient/evidence.txt'])

    def test_every_physical_or_health_failure_propagates_without_success_receipt(self):
        for failure in ('frames', 'pcm', 'silence', 'sync', 'health', 'generation'):
            with self.subTest(failure=failure):
                result, files = self.capture(FAIL=failure)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn('case.video-adverse-network/transient/evidence.txt', files)

    def test_existing_failed_evidence_is_preserved_and_no_capture_retried(self):
        result, files = self.capture(PREEXISTING='true')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('trace', files)
        self.assertEqual('retained failure\n', files['case.video-adverse-network/transient/evidence.txt'])

    def test_unknown_phase_cannot_escape_evidence_directory(self):
        result, files = self.capture(PHASE='../other')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(files)

    def scenario(self, **environment):
        return self.run_shell(r'''
output_root=$PWD
fake_plex_state=$PWD/state
fake_plex_token=DO_NOT_PRINT_TEST_TOKEN
trace=$PWD/trace
printf '1\n' > generation
touch case.audio-leader.console.log case.audio-follower.console.log case.console.log
latest_video_generation() { read -r value < generation; printf '%s\n' "$value"; }
send_audio_control() {
  read -r value < generation; printf '%s\n' "$((value+1))" > generation
  if [[ "${LEAK:-}" == true && "$value" == 3 ]]; then
    # More than a pipe buffer of matching lines protects against grep -q/SIGPIPE
    # regressions as well as accidentally echoing the sensitive failed check.
    for ((i=0;i<4000;i++)); do printf '%s\n' "$fake_plex_token"; done >> case.console.log
  fi
}
wait_for_pattern_after() { return 0; }
wait_for_fault_segment_requests() { printf 'requests %s\n' "$1" >> "$trace"; printf '6\n'; }
wait_for_video_audio_pair_stable() { printf 'stable\n' >> "$trace"; }
capture_video_fault_recovery() {
  read -r state < "$fake_plex_state"
  printf 'physical %s %s %s %s %s\n' "$2" "$state" "$3" "$4" "$5" >> "$trace"
  [[ "$2" != "${FAIL_PHASE:-}" ]]
}
''' + function('run_video_adverse_network_scenarios') + r'''
run_video_adverse_network_scenarios case 123 456 owned_leader owned_follower
''', **environment)

    def test_all_three_cases_require_physical_output_after_actual_fault_and_stability(self):
        result, files = self.scenario()
        self.assertEqual(0, result.returncode, result.stderr)
        lines = files['trace'].splitlines()
        for phase in ('transient', 'slow', 'exhausted'):
            index = next(i for i, line in enumerate(lines) if line.startswith('physical ' + phase))
            self.assertEqual('stable', lines[index-1])
            self.assertTrue(lines[index-2].startswith('requests segments-'))
            self.assertIn('owned_leader owned_follower', lines[index])
        self.assertIn('physical transient segments-transient-', files['trace'])
        self.assertIn('physical slow segments-slow-', files['trace'])
        self.assertIn('physical exhausted online ', files['trace'])
        self.assertEqual('online\n', files['state'])

    def test_every_failed_phase_restores_service_and_cannot_be_recorded_as_pass(self):
        for phase, success in [('transient', 'Transient segment fault:'),
                               ('slow', 'Slow segment delivery:'), ('exhausted', 'Exhausted segment fault:')]:
            result, files = self.scenario(FAIL_PHASE=phase)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual('online\n', files['state'])
            self.assertNotIn(success, files['case.video-adverse-network.evidence.txt'])

    def test_sensitive_failed_redaction_check_is_silent_and_fails_with_large_logs(self):
        result, files = self.scenario(LEAK='true')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('DO_NOT_PRINT_TEST_TOKEN', result.stdout + result.stderr)
        self.assertNotIn('physical exhausted', files['trace'])
        self.assertEqual('online\n', files['state'])

    def test_release_entrypoint_requires_full_matrix_and_supplements_without_duplicate_main_run(self):
        gradle = (ROOT / 'build.gradle').read_text()
        entry = gradle.split("tasks.register('releaseMatrixGate') {", 1)[1].split('\n}', 1)[0]
        for task in ('inspectReleaseArtifacts', 'verifyGameTests', 'verifyRuntimeMatrix',
                     'verifyQuiltModMenuRuntimes', 'verifyFabricLoaderMinimumRuntimes'):
            self.assertIn(task, entry)
        self.assertNotIn('verifyDedicatedServers', entry)
        matrix = gradle.split("tasks.register('verifyRuntimeMatrix', Exec) {", 1)[1].split('\n}', 1)[0]
        self.assertIn("'scripts/run-dedicated-server-gate.sh', 'all'", matrix)
        for task in ('verifyVideoTerminalRuntimes', 'verifyVideoPressureRuntimes', 'verifyVideoFaultRecoveryRuntimes'):
            self.assertIn(task, matrix)

    def test_ci_and_local_gate_require_both_representatives_and_retain_physical_evidence(self):
        ci = (ROOT / '.github/workflows/ci.yml').read_text()
        step = ci.split('- name: Validate physical A/V after', 1)[1].split('\n      - name:', 1)[0]
        for profile in ('1.7.10-forge', '1.21.1-neoforge'):
            self.assertIn(profile, step)
        self.assertIn("CINEMARR_VIDEO_ADVERSE_NETWORK_GATE: 'true'", step)
        for extension in ('png', 's16le', 'json', 'log', 'txt', 'tsv'):
            self.assertIn('build/video-fault-recovery-gate/**/*.' + extension, ci)
        self.assertIn('scripts/test-video-fault-recovery.py', ci)
        self.assertIn('"$leader_pid" "$follower_pid" "$sink_leader" "$sink_follower"', SOURCE)


if __name__ == '__main__':
    unittest.main()
