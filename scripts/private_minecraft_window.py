"""Owned private-X Minecraft window; no fallback to the user's desktop."""
import os
from pathlib import Path
import re
import subprocess
import time
from png_capture import read_capture


class PrivateMinecraftWindow:
    def __init__(self, log, gate_pid, geometry="640x480x24", wait_seconds=0):
        if geometry not in ("640x480x24", "1280x720x24"):
            raise RuntimeError("Unsupported private Minecraft geometry")
        if not 0 <= wait_seconds <= 10:
            raise ValueError("Private window wait must be between zero and ten seconds")
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
        deadline = time.monotonic() + wait_seconds
        while True:
            try:
                # The title varies by loader/version (and may be blank while
                # the client is first mapping). The private X server is owned
                # exclusively by this gate, so enumerate visible windows and
                # retain the exact-one invariant instead of assuming a title.
                # `.*` also matches the private X root window on some xdotool
                # builds. Require a non-empty name so the root cannot become
                # a second apparent Minecraft client.
                windows = self.run("xdotool", "search", "--onlyvisible", "--name", ".+").splitlines()
                if not windows:
                    # Native clients may briefly expose a blank title. In
                    # that case enumerate all visible windows once and remove
                    # the display root by its authoritative X11 window ID.
                    try:
                        candidates = self.run("xdotool", "search", "--onlyvisible", "--name", ".*").splitlines()
                        root_line = self.run("xwininfo", "-root").splitlines()[0]
                        root = int(root_line.split("Window id:", 1)[1].split()[0], 16)
                        windows = [value for value in candidates if int(value) != root]
                    except (IndexError, ValueError, subprocess.CalledProcessError):
                        windows = []
                if len(windows) > 1:
                    # CI runners can expose transient helper windows on the
                    # same display. Keep only X clients descended from this
                    # gate so unrelated windows cannot violate exact-one.
                    owned_windows = []
                    unresolved_windows = []
                    for window in windows:
                        try:
                            owner = int(self.run("xdotool", "getwindowpid", window))
                        except subprocess.CalledProcessError:
                            # A window can disappear between search and PID
                            # lookup (notably during delayed mapping); retry
                            # the bounded discovery loop.
                            unresolved_windows.append(window)
                            continue
                        except (ValueError, KeyError, StopIteration) as error:
                            raise RuntimeError("Unable to identify private Minecraft window owner") from error
                        if self.owned(owner):
                            owned_windows.append(window)
                    # Some native X clients omit _NET_WM_PID. If ownership
                    # data is unavailable, a single surviving candidate is
                    # still safe under the private-display boundary; keep
                    # ambiguity rejection when multiple candidates remain.
                    windows = owned_windows or unresolved_windows
            except subprocess.CalledProcessError as error:
                if error.returncode != 1:
                    raise
                windows = []
            if len(windows) > 1:
                raise RuntimeError("Expected exactly one Minecraft window on the private X server")
            if windows:
                self.window = windows[0]
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("No visible Minecraft window on the private X server")
            # A disconnect can be logged before the render thread maps the
            # window. Revalidate the owned X server on every bounded lookup.
            time.sleep(0.1)

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
        read_capture(path)

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
