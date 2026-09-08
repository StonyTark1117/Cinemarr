#!/usr/bin/env python3
"""Private-window ownership regressions; all GUI commands are mocked."""
import os
from pathlib import Path
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from private_minecraft_window import PrivateMinecraftWindow


class PrivateWindowTests(unittest.TestCase):
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


if __name__ == "__main__": unittest.main()
