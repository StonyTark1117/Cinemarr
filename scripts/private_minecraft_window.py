"""Owned private-X Minecraft window; no fallback to the user's desktop."""
import os
from pathlib import Path
import re
import subprocess
import time


class PrivateMinecraftWindow:
    def __init__(self, log, gate_pid, geometry="640x480x24"):
        if geometry not in ("640x480x24", "1280x720x24"):
            raise RuntimeError("Unsupported private Minecraft geometry")
        self.geometry = geometry
        self.gate_pid = gate_pid
        self.gate_identity = self.identity(gate_pid)[1]
        bindings = re.findall(r"Private Xvfb ready: display=(:[0-9]+) pid=([0-9]+) geometry="
                              + re.escape(geometry) + r"(?:\s|$)", log)
        if len(bindings) != 1:
            raise RuntimeError("Expected exactly one " + geometry + " private-X binding")
        self.display, raw_pid = bindings[0]
        self.xpid = int(raw_pid)
        self.x_identity = self.identity(self.xpid)[1]
        self.env = dict(os.environ)
        self.env.pop("WAYLAND_DISPLAY", None)
        self.env["DISPLAY"] = self.display
        self.env["XAUTHORITY"] = "/tmp/nonexistent-cinemarr-xauthority"
        windows = self.run("xdotool", "search", "--onlyvisible", "--name", "Minecraft").splitlines()
        if len(windows) != 1:
            raise RuntimeError("Expected exactly one Minecraft window on the private X server")
        self.window = windows[0]

    @staticmethod
    def identity(pid):
        fields = Path("/proc", str(pid), "stat").read_text().rsplit(")", 1)[1].split()
        return int(fields[1]), fields[19]

    def owned(self, pid):
        for _ in range(40):
            parent, started = self.identity(pid)
            if pid == self.gate_pid:
                return started == self.gate_identity
            if parent <= 1:
                return False
            pid = parent
        return False

    def validate(self):
        argv = Path("/proc", str(self.xpid), "cmdline").read_bytes().split(b"\0")
        if (not self.owned(self.xpid) or self.identity(self.xpid)[1] != self.x_identity
                or Path(os.fsdecode(argv[0])).name != "Xvfb"
                or self.display.encode() not in argv or self.geometry.encode() not in argv):
            raise RuntimeError("Private X server no longer has the required ownership and identity")

    def run(self, *command):
        self.validate()
        return subprocess.run(command, env=self.env, capture_output=True, text=True,
                              check=True, timeout=6).stdout.strip()

    def click(self, x, y):
        self.run("xdotool", "mousemove", "--window", self.window, str(x), str(y), "click", "1")

    def capture(self, path):
        self.run("import", "-window", self.window, str(path))

    def escape(self):
        self.run("xdotool", "key", "--clearmodifiers", "Escape")

    def reload_resources(self):
        # Legacy Minecraft checks the currently held F3 state while consuming
        # the T event. An instantaneous chord may be released between ticks.
        self.run("xdotool", "keydown", "F3")
        try:
            time.sleep(0.35)
            self.run("xdotool", "key", "t")
            time.sleep(0.35)
        finally:
            self.run("xdotool", "keyup", "F3")
