#!/usr/bin/env python3
"""Exercise the actual shell waiters with logs larger than a pipe buffer."""

import pathlib
import re
import subprocess
import tempfile
import tomllib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
TIMELINE = "Acceptance video audio timeline: underruns=0\n"
WAITERS = (
    ("run-dedicated-server-gate.sh", "wait_for_marker_after", True),
    ("run-dedicated-server-gate.sh", "wait_for_pattern_after", True),
    ("run-discopanel-real-plex-gate.sh", "wait_for_log", False),
    ("run-discopanel-lifecycle-gate.sh", "wait_for_log", False),
    ("run-discopanel-plex-recovery-gate.sh", "wait_log", False),
)


class LogMarkerPollingTest(unittest.TestCase):
    def test_protocol_mismatch_launch_runs_once_on_success_or_failure(self):
        source = (ROOT / "scripts/run-dedicated-server-gate.sh").read_text()
        function = re.search(r"(?ms)^run_wrong_protocol_client\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(function)
        profiles = subprocess.check_output(
            ["python3", "scripts/target-matrix.py", "gate-lines"], cwd=ROOT, text=True)
        for label in (line.split("|")[0] for line in profiles.splitlines()):
            for status in (0, 27):
                with self.subTest(label=label, status=status):
                    shell = 'status=$2\nrun_acceptance_client() { echo "launch:$1:$6"; return "$status"; }\n'
                    result = subprocess.run(["bash", "-c", shell + function.group()
                                             + '\nrun_wrong_protocol_client "$1" target java 1 server.log',
                                             "mismatch-launch", label, str(status)],
                                            capture_output=True, text=True, timeout=5)
                    self.assertEqual(result.returncode, status, result.stderr)
                    self.assertEqual(result.stdout.splitlines(), [f"launch:{label}:wrong-protocol-client"])

    def test_neoforge_splash_workaround_is_scoped_preserving_and_wired(self):
        source = (ROOT / "scripts/run-dedicated-server-gate.sh").read_text()
        match = re.search(r"(?ms)^configure_acceptance_loader\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(match)
        for name in ("run_acceptance_client", "run_command_client", "start_audio_client"):
            function = re.search(r"(?ms)^" + name + r"\(\) \{\n.*?^\}", source)
            self.assertIsNotNone(function, name)
            self.assertIn('configure_acceptance_loader "$label" "$client_dir" || return 1', function.group())
            self.assertLess(function.group().index('configure_acceptance_loader'),
                            function.group().index('exec setsid'))
        with tempfile.TemporaryDirectory(prefix="cinemarr-loader-config-") as temporary:
            root = pathlib.Path(temporary)
            def invoke(label, directory):
                return subprocess.run(["bash", "-c", 'set -euo pipefail\n' + match.group()
                                       + '\nconfigure_acceptance_loader "$1" "$2"',
                                       "loader-config", label, str(directory)],
                                      capture_output=True, text=True, timeout=5)
            client = root / "client"
            self.assertEqual(invoke("1.20.2-neoforge", client).returncode, 0)
            config = client / "config/fml.toml"
            self.assertEqual(tomllib.loads(config.read_text()), {"earlyWindowControl": False})
            config.write_text(config.read_text() + 'maxThreads = 7\n')
            previous = config.read_bytes()
            self.assertEqual(invoke("1.20.2-neoforge", client).returncode, 0)
            self.assertEqual(config.read_bytes(), previous)
            config.write_text('earlyWindowControl = true\n')
            previous = config.read_bytes()
            self.assertNotEqual(invoke("1.20.2-neoforge", client).returncode, 0)
            self.assertEqual(config.read_bytes(), previous, "Do not overwrite conflicting config")
            config.unlink()
            outside = root / "outside.toml"
            outside.write_text('earlyWindowControl = true\n')
            config.symlink_to(outside)
            self.assertNotEqual(invoke("1.20.2-neoforge", client).returncode, 0)
            self.assertEqual(outside.read_text(), 'earlyWindowControl = true\n')
            profiles = subprocess.check_output(
                ["python3", "scripts/target-matrix.py", "gate-lines"], cwd=ROOT, text=True)
            for label in (line.split("|")[0] for line in profiles.splitlines()):
                if label == "1.20.2-neoforge":
                    continue
                untouched = root / label
                self.assertEqual(invoke(label, untouched).returncode, 0)
                self.assertFalse(untouched.exists(), label)

    def invoke_audio_stability(self, label, mode, wall_clock_pause=False):
        source = (ROOT / "scripts/run-dedicated-server-gate.sh").read_text()
        functions = []
        for name in ("wait_for_video_audio_pair_stable", "video_audio_timeline_within_bounds"):
            match = re.search(r"(?ms)^" + name + r"\(\) \{\n.*?^\}", source)
            self.assertIsNotNone(match)
            functions.append(match.group())
        with tempfile.TemporaryDirectory(prefix="cinemarr-audio-stability-") as temporary:
            shell = r'''
set -uo pipefail
output_root=$1
label=$2
mode=$3
wall_clock_pause=$4
# Bash SECONDS otherwise keeps advancing with real elapsed time even after
# assignment. Remove its special behavior only in this mocked-clock fixture;
# the extracted production waiter and its real eight-second limit are unchanged.
unset SECONDS
SECONDS=0
tick=0
prefix='Acceptance video audio'
buffer='javaBufferMs=500'
started=''
if [[ "$label" == 1.7.10-forge ]]; then
  prefix='Acceptance legacy video audio'
  buffer='pendingFrames=500'
  started='started=true'
fi
emit() {
  local role=$1 position=$((10000 + tick * 1000))
  printf '%s timeline: targetMs=%s videoMs=%s driftMs=0 %s %s underruns=0\n' \
    "$prefix" "$position" "$position" "$started" "$buffer" >> "$output_root/$label.audio-$role.console.log"
}
for role in leader follower; do
  printf '%s scheduled: framePtsMs=10000\n' "$prefix" > "$output_root/$label.audio-$role.console.log"
  emit "$role"
done
group_alive() { return 0; }
sleep() {
  tick=$((tick + 1))
  SECONDS=$tick
  emit leader
  case "$mode" in
    fresh) emit follower ;;
    delayed) if (( tick >= 20 )); then emit follower; fi ;;
    stalled) if (( tick <= 4 )); then emit follower; fi ;;
    stale) : ;;
  esac
  # Cross a real clock boundary while the synthetic clock is still at 27.
  if [[ "$wall_clock_pause" == true ]] && (( tick == 27 )); then command sleep 1.1; fi
}
'''
            return subprocess.run(["bash", "-c", shell + "\n".join(functions)
                                   + '\nwait_for_video_audio_pair_stable "$label" 1 2\n'
                                     'result=$?\necho "ticks=$tick"\nexit "$result"',
                                   "stability-test", temporary, label, mode, str(wall_clock_pause).lower()],
                                  capture_output=True, text=True, timeout=15)

    def test_audio_stability_rejects_stale_and_stalled_timelines(self):
        for label in ("1.7.10-forge", "1.21.1-neoforge"):
            for mode in ("stale", "stalled"):
                with self.subTest(label=label, mode=mode):
                    result = self.invoke_audio_stability(label, mode)
                    self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_audio_stability_requires_eight_seconds_after_fresh_audio_returns(self):
        for label in ("1.7.10-forge", "1.21.1-neoforge"):
            for mode, minimum in (("fresh", 9), ("delayed", 28)):
                with self.subTest(label=label, mode=mode):
                    result = self.invoke_audio_stability(label, mode)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertGreaterEqual(int(re.search(r"ticks=(\d+)", result.stdout)[1]), minimum)

    def test_synthetic_audio_clock_does_not_advance_with_wall_time(self):
        for label in ("1.7.10-forge", "1.21.1-neoforge"):
            with self.subTest(label=label):
                result = self.invoke_audio_stability(label, "delayed", wall_clock_pause=True)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual(int(re.search(r"ticks=(\d+)", result.stdout)[1]), 28,
                                 "Only synthetic ticks may advance this test's clock")

    def invoke_video_startup(self, label, fail_role=""):
        source = (ROOT / "scripts/run-dedicated-server-gate.sh").read_text()
        match = re.search(r"(?ms)^run_two_client_video\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(match)
        boundary = '  if (( result == 0 )); then\n    wait_for_video_audio_pair_stable'
        self.assertIn(boundary, match.group())
        # Execute the actual startup branch, replacing only external processes.
        # A second start before the first finishes models Loom rebuilding a
        # remap classpath while Quilt is still consuming it.
        startup = match.group().split(boundary, 1)[0] + '\n  [[ "$launch_race" == false ]] || return 98\n  return "$result"\n}\n'
        launcher = re.search(r"(?ms)^launch_audio_client\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(launcher)
        shell = r'''
set -uo pipefail
output_root=/unused
repo_root=/unused
live_plex_gate=false
video_follower_first_gate=false
active_audio_modules=()
started_audio_client_pid=""
ready_audio_client_pid=""
pending=""
launch_race=false
fail_role=$2
rm() { :; }
pactl() { echo 123; }
start_audio_client() {
  echo "start:$5"
  [[ -z "$pending" ]] || launch_race=true
  pending=$5
  started_audio_client_pid=$5
}
wait_for_audio_playing() {
  echo "ready:$2"
  pending=""
  [[ "$2" != "$fail_role" ]]
}
'''
        return subprocess.run(["bash", "-c", shell + launcher.group() + "\n" + startup
                               + '\nrun_two_client_video "$1" unused unused 1 2 unused 3',
                               "startup-test", label, fail_role],
                              capture_output=True, text=True, timeout=5)

    def test_every_quilt_profile_finishes_first_start_before_second_remap(self):
        profiles = subprocess.check_output(
            ["python3", "scripts/target-matrix.py", "gate-lines"], cwd=ROOT, text=True)
        labels = [line.split("|")[0] for line in profiles.splitlines()
                  if line.split("|")[0].endswith("-quilt")]
        self.assertEqual(len(labels), 5)
        for label in labels + ["1.21.1-neoforge", "1.7.10-forge"]:
            with self.subTest(label=label):
                result = self.invoke_video_startup(label)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual(result.stdout.splitlines(),
                                 ["start:leader", "ready:leader", "start:follower", "ready:follower"])

    def test_sequential_startup_failure_is_terminal_without_retry(self):
        for role, events in (("leader", ["start:leader", "ready:leader"]),
                             ("follower", ["start:leader", "ready:leader", "start:follower", "ready:follower"])):
            with self.subTest(role=role):
                result = self.invoke_video_startup("1.20.1-quilt", role)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout.splitlines(), events)

    def test_quilt_fatal_loader_dialog_is_detected_before_bootstrap_timeout(self):
        source = (ROOT / "scripts/run-dedicated-server-gate.sh").read_text()
        match = re.search(r"(?ms)^client_bootstrap_failed\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(match)
        with tempfile.TemporaryDirectory(prefix="cinemarr-loader-fatal-") as temporary:
            log = pathlib.Path(temporary) / "client.log"
            for content, expected in (
                ('[22:38:18] [main/ERROR] (Quilt Loader) Uncaught exception in thread "main"\n'
                 'java.lang.RuntimeException: java.nio.file.NoSuchFileException: missing.jar\n', True),
                ('[main/INFO] (Quilt Loader) Loading Minecraft\n', False),
                ('Optional asset warning: java.nio.file.NoSuchFileException\n', False),
                ('[pool-2-thread-1/ERROR] [EARLYDISPLAY/]: BARF java.nio.file.FileSystemNotFoundException: null\n', True),
                ('[Render thread/ERROR] [EARLYDISPLAY/]: BARF java.lang.IllegalStateException: Already building.\n', True),
                ('[Render thread/INFO] [EARLYDISPLAY/]: Requested GL version 4.6 got version 4.6\n', False),
            ):
                with self.subTest(content=content):
                    log.write_text(content)
                    result = subprocess.run(["bash", "-c", match.group() + '\nclient_bootstrap_failed "$1"',
                                             "bootstrap-test", str(log)], capture_output=True, timeout=5)
                    self.assertEqual(result.returncode == 0, expected)

    def test_gametest_gate_requires_all_ten_tests_not_a_passing_subset(self):
        source = (ROOT / "scripts/run-gametest-gate.sh").read_text()
        match = re.search(r"(?ms)^gametests_passed\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(match)
        self.assertIn('if gametests_passed "$log_file"; then', source)
        for count in (0, 1, 9, 10, 11, 100):
            with self.subTest(count=count), tempfile.TemporaryDirectory(prefix="cinemarr-gametest-count-") as temporary:
                path = pathlib.Path(temporary) / "gametest.log"
                path.write_text(f"[GameTestServer] All {count} required tests passed\n")
                result = subprocess.run(["bash", "-c", "set -euo pipefail\n" + match.group()
                                         + '\ngametests_passed "$1"', "gametest-count", str(path)],
                                        capture_output=True, text=True, timeout=5)
                self.assertEqual(result.returncode == 0, count == 10)

    def invoke(self, script, function, file_waiter, log, marker, timeout, first_line=0):
        source = (ROOT / "scripts" / script).read_text()
        match = re.search(r"(?ms)^" + re.escape(function) + r"\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(match, f"Cannot find {function} in {script}")
        with tempfile.TemporaryDirectory(prefix="cinemarr-log-poll-") as temporary:
            path = pathlib.Path(temporary) / "fixture.log"
            path.write_text(log)
            invocation = (
                f'{function} "$fixture" {first_line} "$1" "$2"'
                if file_waiter else f'{function} "$1" "$2"'
            )
            shell = (
                'set -uo pipefail\nfixture=$3\nserver_name=fixture\n'
                'new_logs() { cat -- "$fixture"; }\n'
                + match.group() + "\n" + invocation
            )
            return subprocess.run(
                ["bash", "-c", shell, "poll-test", marker, str(timeout), str(path)],
                capture_output=True, text=True, timeout=10,
            )

    def test_early_marker_survives_large_trailing_log(self):
        log = "MATCH\n" + "unrelated trailing log line\n" * 100_000
        for script, function, file_waiter in WAITERS:
            with self.subTest(script=script, function=function):
                result = self.invoke(script, function, file_waiter, log, "MATCH", 2)
                self.assertEqual(result.returncode, 0, result.stderr)

    def test_missing_marker_is_rejected(self):
        for script, function, file_waiter in WAITERS:
            with self.subTest(script=script, function=function):
                result = self.invoke(script, function, file_waiter, "unrelated\n", "MATCH", 0)
                self.assertNotEqual(result.returncode, 0)

    def test_file_waiters_reject_markers_before_checkpoint(self):
        for script, function, file_waiter in WAITERS:
            if file_waiter:
                with self.subTest(function=function):
                    result = self.invoke(script, function, True, "MATCH\nunrelated\n", "MATCH", 0, 1)
                    self.assertNotEqual(result.returncode, 0)

    def invoke_health_logs(self, root, label="fixture"):
        source = (ROOT / "scripts/run-dedicated-server-gate.sh").read_text()
        match = re.search(r"(?ms)^verify_video_health_reports\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(match)
        return subprocess.run(["bash", "-c", 'set -euo pipefail\noutput_root=$1\nrepo_root=$2\n'
                               + match.group() + '\nverify_video_health_reports "$3"',
                               "health-test", str(root), str(ROOT), label],
                              capture_output=True, text=True, timeout=10)

    def test_video_health_gate_checks_current_and_pre_reconnect_logs(self):
        with tempfile.TemporaryDirectory(prefix="cinemarr-health-log-") as temporary:
            root = pathlib.Path(temporary)
            leader = root / "fixture.audio-leader.console.log"
            follower = root / "fixture.audio-follower.console.log"
            prior = root / "fixture.audio-follower.pre-reconnect.console.log"
            def run():
                return self.invoke_health_logs(root)
            valid = ("Acceptance client media reset complete\nAcceptance client JOIN reset complete\n"
                     "Acceptance video session: generation=1\n" + TIMELINE)
            missing = run()
            self.assertNotEqual(missing.returncode, 0)
            self.assertIn("missing health-report evidence", missing.stderr)
            leader.write_text(valid)
            follower.write_text(valid)
            healthy = run()
            self.assertEqual(healthy.returncode, 0, healthy.stderr)
            for path in (leader, follower, prior):
                for failure in ("Cinemarr: Invalid video health report",
                                "java.lang.IllegalStateException: Rendersystem called from wrong thread",
                                "Failed to close texture cinemarr:dynamic/video_frame_1"):
                    with self.subTest(path=path.name, failure=failure):
                        path.write_text(valid + failure + "\n" + "normal\n" * 100_000)
                        rejected = run()
                        self.assertNotEqual(rejected.returncode, 0)
                        expected = "ordinary playback emitted an invalid-health error" if "health" in failure else "client render-resource cleanup failed"
                        self.assertIn(expected, rejected.stderr)
                        self.assertIn(str(path), rejected.stderr)
                        path.write_text(valid)
            leader.write_text(valid + "[ALSOFT] (EE) control open (hw:5): No such file or directory\n")
            healthy = run()
            self.assertEqual(healthy.returncode, 0, healthy.stderr)

    def test_modern_join_order_rejects_missing_or_late_reset_in_every_closed_log(self):
        with tempfile.TemporaryDirectory(prefix="cinemarr-join-log-") as temporary:
            root = pathlib.Path(temporary)
            paths = [root / ("fixture." + role + ".console.log") for role in
                     ("audio-leader", "audio-follower", "audio-follower.pre-reconnect")]
            reset = "Acceptance client media reset complete\n"
            joined = "Acceptance client JOIN reset complete\n"
            session = "Acceptance video session: generation=1\n"
            manifest = "Acceptance video manifest: generation=1\n"
            for path in paths:
                path.write_text(reset + joined + session + TIMELINE)
            for path in paths:
                for invalid in (session, session + reset, manifest + reset + session, reset,
                                reset + session, reset + session + joined, joined + reset + session):
                    with self.subTest(path=path.name, log=invalid):
                        path.write_text(invalid + TIMELINE)
                        rejected = self.invoke_health_logs(root)
                        self.assertNotEqual(rejected.returncode, 0)
                        self.assertIn("client received media before its JOIN reset", rejected.stderr)
                        self.assertIn(str(path), rejected.stderr)
                        path.write_text(reset + joined + session + TIMELINE)
            healthy = self.invoke_health_logs(root)
            self.assertEqual(healthy.returncode, 0, healthy.stderr)

    def test_legacy_disconnect_marker_is_not_mistaken_for_a_late_join_reset(self):
        with tempfile.TemporaryDirectory(prefix="cinemarr-legacy-health-log-") as temporary:
            root = pathlib.Path(temporary)
            for role in ("audio-leader", "audio-follower", "audio-follower.pre-reconnect"):
                path = root / ("1.7.10-forge." + role + ".console.log")
                path.write_text("Acceptance video session: generation=1\n"
                                "Acceptance legacy video audio timeline: underruns=0\n"
                                "Acceptance client media reset complete\n")
            healthy = self.invoke_health_logs(root, "1.7.10-forge")
            self.assertEqual(healthy.returncode, 0, healthy.stderr)

    def test_closed_health_logs_reject_missing_timeline_and_active_underruns(self):
        with tempfile.TemporaryDirectory(prefix="cinemarr-health-timeline-") as temporary:
            root = pathlib.Path(temporary)
            paths = [root / ("fixture." + role + ".console.log") for role in
                     ("audio-leader", "audio-follower", "audio-follower.pre-reconnect")]
            prefix = ("Acceptance client media reset complete\n"
                      "Acceptance client JOIN reset complete\n"
                      "Acceptance video session: generation=1\n")
            for path in paths:
                path.write_text(prefix + TIMELINE)
            for path in paths:
                for damaged, reason in (
                    (prefix, "Missing real video audio timeline"),
                    (prefix + TIMELINE.replace("underruns=0", "underruns=1"), "Active video audio underrun"),
                    (prefix + TIMELINE + "audio active underrun: source stopped\n"
                     + "Acceptance client media reset complete\n", "Active video audio underrun"),
                ):
                    with self.subTest(path=path.name, reason=reason):
                        path.write_text(damaged)
                        rejected = self.invoke_health_logs(root)
                        self.assertNotEqual(rejected.returncode, 0)
                        self.assertIn(reason, rejected.stderr)
                        self.assertIn(str(path), rejected.stderr)
                        path.write_text(prefix + TIMELINE)
            healthy = self.invoke_health_logs(root)
            self.assertEqual(healthy.returncode, 0, healthy.stderr)

    def test_media_cleanup_requires_zero_pending_retiring_and_failed_handles(self):
        source = (ROOT / "scripts/run-dedicated-server-gate.sh").read_text()
        match = re.search(r"(?ms)^video_media_cleanup_idle\(\) \{\n.*?^\}", source)
        self.assertIsNotNone(match)
        clean = "Plex=ready; mediaStarts=0; mediaRetiring=0; mediaCloseFailures=0; healthReports=2"
        cases = [(clean, True), ("Plex=ready; activeStreams=0/4", False)]
        cases.extend((clean.replace(field + "=0", field + "=1"), False)
                     for field in ("mediaStarts", "mediaRetiring", "mediaCloseFailures"))
        for diagnostics, expected in cases:
            result = subprocess.run(["bash", "-c", 'set -euo pipefail\n' + match.group()
                                     + '\nvideo_media_cleanup_idle "$1"', "cleanup-test", diagnostics],
                                    capture_output=True, timeout=10)
            self.assertEqual(result.returncode == 0, expected)


if __name__ == "__main__":
    unittest.main()
