#!/usr/bin/env python3
"""Exercise the actual shell waiters with logs larger than a pipe buffer."""

import pathlib
import re
import subprocess
import tempfile
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
