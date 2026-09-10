#!/usr/bin/env python3
"""Private-window ownership regressions; all GUI commands are mocked."""
import os
import subprocess
import importlib.util
import re
import struct
import tempfile
import zlib
from pathlib import Path
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from private_minecraft_window import PrivateMinecraftWindow
from png_capture import read_capture, validate_closed_profile


class PrivateWindowTests(unittest.TestCase):
    def test_close_can_wait_for_a_window_to_become_visible(self):
        self.run.side_effect = [subprocess.CalledProcessError(1, ['xdotool']),
                                SimpleNamespace(stdout='111\n')]
        with patch('private_minecraft_window.time.sleep'):
            window = PrivateMinecraftWindow(self.log, 42, wait_seconds=10)
        self.assertEqual('111', window.window)
        self.assertEqual(2, self.run.call_count)

    def test_window_wait_does_not_retry_ambiguity_or_x_failure(self):
        for result in (SimpleNamespace(stdout='111\n222\n'),
                       subprocess.CalledProcessError(2, ['xdotool'])):
            self.run.side_effect = [result]
            with self.assertRaises((RuntimeError, subprocess.CalledProcessError)):
                PrivateMinecraftWindow(self.log, 42, wait_seconds=10)

    def test_window_wait_is_bounded_and_revalidates_ownership(self):
        self.run.side_effect = subprocess.CalledProcessError(1, ['xdotool'])
        with patch('private_minecraft_window.time.monotonic', side_effect=[0, 11]):
            with self.assertRaisesRegex(RuntimeError, 'visible'):
                PrivateMinecraftWindow(self.log, 42, wait_seconds=10)
        def replace_x(_):
            self.identities[51] = (42, 'reused')
        with patch('private_minecraft_window.time.sleep', side_effect=replace_x):
            with self.assertRaisesRegex(RuntimeError, 'identity'):
                PrivateMinecraftWindow(self.log, 42, wait_seconds=10)

    def test_close_revalidates_ownership_before_opening_xlib(self):
        spec = importlib.util.spec_from_file_location('private_close', Path(__file__).with_name('close-private-minecraft-window.py'))
        close = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(close)
        window = PrivateMinecraftWindow(self.log, 42)
        self.identities[51] = (1, "x-start")
        with patch.object(close.c, 'CDLL') as library:
            with self.assertRaises(RuntimeError): close.request_close(window)
            library.assert_not_called()

    def test_full_size_geometry_is_explicit_and_still_identity_checked(self):
        self.argv = self.argv.replace(b'640x480x24', b'1280x720x24')
        log = self.log.replace('640x480x24', '1280x720x24')
        window = PrivateMinecraftWindow(log, 42, geometry='1280x720x24')
        window.validate()
        self.identities[51] = (42, 'replacement')
        with self.assertRaises(RuntimeError): window.validate()
        with self.assertRaises(RuntimeError): PrivateMinecraftWindow(log, 42, geometry='1920x1080x24')

    def setUp(self):
        self.identities = {42: (1, "gate-start"), 51: (42, "x-start")}
        self.argv = b"\0".join((b"/usr/bin/Xvfb", b":91", b"-screen", b"0", b"640x480x24"))
        self.identity = patch.object(PrivateMinecraftWindow, "identity", side_effect=lambda pid: self.identities[pid])
        self.read = patch.object(Path, "read_bytes", side_effect=lambda: self.argv)
        self.process = patch("private_minecraft_window.subprocess.run", return_value=SimpleNamespace(stdout="111\n"))
        self.identity.start(); self.read.start(); self.run = self.process.start()
        self.addCleanup(self.identity.stop); self.addCleanup(self.read.stop); self.addCleanup(self.process.stop)
        self.log = "Private Xvfb ready: display=:91 pid=51 geometry=640x480x24"

    def test_input_uses_private_environment_not_host_display(self):
        with patch.dict(os.environ, DISPLAY=":0", WAYLAND_DISPLAY="wayland-1"):
            window = PrivateMinecraftWindow(self.log, 42)
            window.click(12, 34)
        kwargs = self.run.call_args.kwargs
        self.assertEqual(":91", kwargs["env"]["DISPLAY"])
        self.assertNotIn("WAYLAND_DISPLAY", kwargs["env"])
        self.assertEqual(6, kwargs["timeout"])
        self.assertIn("111", self.run.call_args.args[0])

    def test_unowned_xserver_rejected_before_gui_command(self):
        self.identities[51] = (1, "x-start")
        with self.assertRaises(RuntimeError): PrivateMinecraftWindow(self.log, 42)
        self.run.assert_not_called()

    def test_reused_xserver_pid_rejected_before_capture(self):
        window = PrivateMinecraftWindow(self.log, 42)
        self.identities[51] = (42, "replacement")
        with self.assertRaises(RuntimeError): window.capture(Path("never-created.png"))
        self.assertEqual(1, self.run.call_count)

    def test_reused_gate_pid_rejected_before_input(self):
        window = PrivateMinecraftWindow(self.log, 42)
        self.identities[42] = (1, "replacement")
        with self.assertRaises(RuntimeError): window.escape()
        self.assertEqual(1, self.run.call_count)

    def test_wrong_display_or_geometry_or_binary_rejected(self):
        for argv in ((b"Xvfb", b":0", b"640x480x24"),
                     (b"Xvfb", b":91", b"1280x720x24"),
                     (b"Xorg", b":91", b"640x480x24")):
            self.argv = b"\0".join(argv)
            with self.assertRaises(RuntimeError): PrivateMinecraftWindow(self.log, 42)
        self.run.assert_not_called()

    def test_missing_or_ambiguous_bindings_rejected(self):
        for log in ("", self.log + "\n" + self.log):
            with self.assertRaises(RuntimeError): PrivateMinecraftWindow(log, 42)
        self.run.assert_not_called()

    def test_multiple_minecraft_windows_rejected(self):
        self.run.return_value.stdout = "111\n222\n"
        with self.assertRaises(RuntimeError): PrivateMinecraftWindow(self.log, 42)
        self.assertEqual(1, self.run.call_count)

    def test_reload_holds_f3_across_game_ticks_and_releases_it(self):
        window = PrivateMinecraftWindow(self.log, 42)
        events = []
        with patch.object(window, 'run', side_effect=lambda *args: events.append(args)), \
                patch('private_minecraft_window.time.sleep', side_effect=lambda delay: events.append(('wait', delay))):
            window.reload_resources()
        self.assertEqual([('xdotool', 'keydown', 'F3'), ('wait', 0.35),
                          ('xdotool', 'key', 't'), ('wait', 0.35),
                          ('xdotool', 'keyup', 'F3')], events)

    def test_reload_releases_f3_when_t_delivery_fails(self):
        window = PrivateMinecraftWindow(self.log, 42)
        events = []
        def command(*args):
            events.append(args)
            if args == ('xdotool', 'key', 't'):
                raise RuntimeError('injected input failure')
        with patch.object(window, 'run', side_effect=command), patch('private_minecraft_window.time.sleep'):
            with self.assertRaisesRegex(RuntimeError, 'injected input failure'):
                window.reload_resources()
        self.assertEqual(('xdotool', 'keyup', 'F3'), events[-1])


def sample_png(raw=b'\0\xff\0\0\0\xff\0', width=2, height=1):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw))
            + chunk(b'IEND', b''))


class CaptureIntegrityTests(unittest.TestCase):
    def closed_profile(self, root, profile='1.20.2-fabric'):
        for role in ('leader', 'follower'):
            directory = root / (profile + '.audio-' + role) / 'screenshots'
            directory.mkdir(parents=True)
            for name in ('cinemarr-video-acceptance.png', 'cinemarr-video-ui-acceptance.png'):
                (directory / name).write_bytes(sample_png())
        return profile

    def test_closed_profile_rejects_trailing_bytes_in_automatic_capture_without_repair(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile = self.closed_profile(root)
            path = root / (profile + '.audio-follower/screenshots/cinemarr-video-ui-acceptance.png')
            malformed = sample_png() + b'leftover concurrent writer bytes'
            path.write_bytes(malformed)
            with self.assertRaisesRegex(ValueError, 'Invalid retained screenshot'):
                validate_closed_profile(root, profile)
            self.assertEqual(malformed, path.read_bytes())

    def test_closed_profile_requires_both_clients_and_rejects_unpublished_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile = self.closed_profile(root)
            self.assertEqual(4, validate_closed_profile(root, profile))
            pending = root / (profile + '.audio-leader/screenshots/.cinemarr-capture-failed.png')
            pending.write_bytes(sample_png())
            with self.assertRaisesRegex(ValueError, 'Unpublished screenshot'):
                validate_closed_profile(root, profile)
            pending.unlink()
            (root / (profile + '.audio-follower/screenshots/cinemarr-video-acceptance.png')).unlink()
            with self.assertRaisesRegex(ValueError, 'Missing required'):
                validate_closed_profile(root, profile)

    def test_closed_profile_checks_nested_captures_without_reading_another_live_profile(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile = self.closed_profile(root)
            (root / '1.20.2-quilt.live.png').write_bytes(b'')
            self.assertEqual(4, validate_closed_profile(root, profile))
            nested = root / (profile + '.owner-timeline') / 'paused.png'
            nested.parent.mkdir()
            nested.write_bytes(b'')
            with self.assertRaisesRegex(ValueError, 'Invalid retained screenshot'):
                validate_closed_profile(root, profile)

    def test_gate_validates_automatic_capture_after_last_writer_cleanup(self):
        source = Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        function = re.search(r'(?ms)^run_two_client_video\(\) \{\n.*?^\}', source).group()
        tail = function[function.rindex('  cleanup_audio_processes\n'):]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile = self.closed_profile(root)
            shell = r'''
repo_root=$1; output_root=$2; label=1.20.2-fabric; result=0
leader_dir="$output_root/$label.audio-leader"
follower_dir="$output_root/$label.audio-follower"
evidence="$output_root/$label.evidence.txt"
cleanup_audio_processes() {
  printf 'trailing bytes' >> "$follower_dir/screenshots/cinemarr-video-ui-acceptance.png"
}
verify_video_health_reports() { return 0; }
run_closed_tail() {
'''
            shell += tail + '\nrun_closed_tail\n'
            result = subprocess.run(['bash', '-c', shell, 'closed-capture-regression',
                                     str(Path(__file__).resolve().parents[1]), str(root)],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(1, result.returncode, result.stderr)
            self.assertIn('closed screenshot evidence failed integrity validation', result.stderr)

    def test_png_checksums_pixel_extent_and_scanline_filters_are_required(self):
        damaged = bytearray(sample_png())
        damaged[29] ^= 1
        cases = {
            'checksum': damaged,
            'missing-end': sample_png()[:-12],
            'trailing-data': sample_png() + b'junk',
            'short-pixels': sample_png(b'\0\xff\0\0'),
            'extra-pixels': sample_png(b'\0\xff\0\0\0\xff\0\0'),
            'invalid-filter': sample_png(b'\5\xff\0\0\0\xff\0'),
            'unbounded-dimensions': sample_png(width=100000),
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'capture.png'
            for name, content in cases.items():
                with self.subTest(name=name):
                    path.write_bytes(content)
                    with self.assertRaises(ValueError): read_capture(path)

    def test_copy_rejects_invalid_input_and_preserves_existing_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.png'
            destination = Path(directory) / 'retained.png'
            command = ['python3', str(Path(__file__).with_name('png_capture.py')),
                       str(source), '--copy', str(destination)]
            source.write_bytes(b'')
            result = subprocess.run(command, capture_output=True, timeout=10)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse(destination.exists())
            source.write_bytes(sample_png())
            result = subprocess.run(command, capture_output=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
            source.write_bytes(sample_png(b'\0\0\0\0\0\0\0'))
            result = subprocess.run(command, capture_output=True, timeout=10)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(sample_png(), destination.read_bytes())

    def test_successful_capture_command_cannot_publish_empty_or_truncated_png(self):
        for content in (b'', sample_png()[:-5]):
            with self.subTest(size=len(content)), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'capture.png'
                window = object.__new__(PrivateMinecraftWindow)
                window.window = '111'
                with patch.object(window, 'run', side_effect=lambda *args: path.write_bytes(content)):
                    with self.assertRaises((ValueError, RuntimeError)):
                        window.capture(path)

    def test_complete_capture_is_retained_without_changing_pixels(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'capture.png'
            window = object.__new__(PrivateMinecraftWindow)
            window.window = '111'
            with patch.object(window, 'run', side_effect=lambda *args: path.write_bytes(sample_png())):
                window.capture(path)
            self.assertEqual(sample_png(), path.read_bytes())

    def test_non_owner_evidence_survives_live_ui_file_truncation(self):
        # Exercise the real shell control flow. The client screenshot is valid
        # at the initial check, then truncated by the simulated UI writer while
        # the observer retains its independent, immutable window capture.
        source = Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        function = re.search(r'(?ms)^run_video_control_scenarios\(\) \{\n.*?^\}', source).group()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = root / 'original.png'
            fixture.write_bytes(sample_png())
            profile = '1.20.1-fabric'
            for role in ('leader', 'follower'):
                (root / (profile + '.audio-' + role + '.console.log')).write_text('ready\n')
            ui = root / (profile + '.audio-follower/screenshots/cinemarr-video-ui-acceptance.png')
            ui.parent.mkdir(parents=True)
            ui.write_bytes(sample_png())
            shell = r'''
repo_root=$1; output_root=$2; label=1.20.1-fabric; rcon_port=''; fifo_fd=''
sleep() { :; }
wait_for_pattern_after() { return 0; }
wait_for_video_audio_pair_stable() { return 0; }
capture_audio_sink() { :; }
real_video_capture_is_silent() { return 0; }
audio_capture_is_audible() { return 0; }
latest_video_generation() {
  local n=0
  [[ ! -f "$output_root/generation" ]] || read -r n < "$output_root/generation"
  n=$((n+1)); echo "$n" > "$output_root/generation"; echo "$n"
}
send_audio_control() {
  if [[ "$3" == video:cycle-stream ]]; then
    echo 'Acceptance video action: SET_STREAMS audio=2 subtitle=-1' >> "$output_root/$label.audio-leader.console.log"
  fi
}
python3() {
  case "$1" in
    */observe-controller-feedback.py)
      mkdir -p "$output_root/$label.widget-feedback"
      command cp "$output_root/original.png" "$output_root/$label.widget-feedback/initial-status.png"
      : > "$output_root/$label.audio-follower/screenshots/cinemarr-video-ui-acceptance.png" ;;
    */observe-owner-timeline.py) return 0 ;;
    *) command python3 "$@" ;;
  esac
}
# End at the reconnect boundary, after the non-owner snapshot must be saved.
command_output() { return 1; }
'''
            shell += function + '\nrun_video_control_scenarios "$label" . . 25571 sinkA sinkB 42 43\n'
            result = subprocess.run(['bash', '-c', shell, 'capture-regression',
                                     str(Path(__file__).resolve().parents[1]), str(root)],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(1, result.returncode, result.stderr)
            self.assertEqual(b'', ui.read_bytes())
            saved = root / (profile + '.non-owner-small-window-ui.png')
            self.assertEqual(sample_png(), saved.read_bytes(), 'Gate copied a mutable/truncated client UI file')


if __name__ == "__main__": unittest.main()
