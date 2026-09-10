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
        if not 0 <= wait_seconds <= 180:
            raise ValueError("Private window wait must be between zero and one hundred eighty seconds")
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
        ambiguous_since = {}
        while True:
            try:
                # The title varies by loader/version (and may be blank while
                # the client is first mapping). Some headless WMs leave a
                # continuously rendered GL window out of _NET_WM_STATE's
                # visible set, so use the private display's named clients as
                # the primary discovery source and retain the exact-one
                # invariant instead of assuming a title or WM hint.
                # `.*` also matches the private X root window on some xdotool
                # builds. Require a non-empty name so the root cannot become
                # a second apparent Minecraft client.
                windows = self.run("xdotool", "search", "--name", ".+").splitlines()
                # Do not capture a native client during the short interval
                # between XCreateWindow and XMapWindow.  Conversely, accept
                # clients whose WM metadata omits the EWMH visible hint.
                mapped = []
                for window in windows:
                    try:
                        info = self.run("xwininfo", "-id", window)
                    except (subprocess.CalledProcessError, StopIteration):
                        info = ""
                    # Xvfb clients without a window manager can report an
                    # ambiguous map state even after mapping. Hold those
                    # candidates for a short stability interval so a delayed
                    # XMapWindow cannot be captured, while long-lived clients
                    # remain discoverable without relying on EWMH metadata.
                    if "Map State:" not in info or "IsViewable" in info:
                        mapped.append(window)
                    elif "IsUnmapped" in info:
                        ambiguous_since.pop(window, None)
                    else:
                        # Bare Xvfb can report IsUnviewable for a mapped GL
                        # client. Hold that ambiguous state briefly so the
                        # delayed-map probe cannot be captured before mapping.
                        now = time.monotonic()
                        first_seen = ambiguous_since.setdefault(window, now)
                        if now - first_seen >= 1.0:
                            mapped.append(window)
                windows = mapped
                if not windows:
                    # Native clients may briefly expose a blank title. In
                    # that case enumerate all visible windows once and remove
                    # the display root by its authoritative X11 window ID.
                    try:
                        candidates = self.run("xdotool", "search", "--onlyvisible", "--name", ".*").splitlines()
                        # LWJGL/Fabric can leave WM_NAME empty while still
                        # exposing a mapped GL surface. Query the X11 class as
                        # an independent discovery signal before falling back
                        # to the raw tree; ownership and geometry checks below
                        # still reject unrelated/helper windows.
                        try:
                            # Fabric/LWJGL can omit EWMH visibility while
                            # still exposing a mapped top-level class window.
                            # Query class metadata without --onlyvisible and
                            # let the mapped-state checks below decide whether
                            # the candidate is capturable.
                            candidates.extend(self.run("xdotool", "search", "--class", ".*").splitlines())
                        except subprocess.CalledProcessError:
                            pass
                        tree = self.run("xwininfo", "-root", "-tree")
                        root_line = tree.splitlines()[0]
                        root = int(root_line.split("Window id:", 1)[1].split()[0], 16)
                        windows = [value for value in candidates if int(value) != root]
                        if not windows:
                            # Some Fabric clients expose no EWMH-visible name
                            # even after mapping. Inspect the X tree directly,
                            # but retain the mapped-only rule so delayed-map
                            # probes cannot select an unmapped child.
                            for value in re.findall(r"0x[0-9a-fA-F]+", tree):
                                window = str(int(value, 16))
                                if window == "0" or window == str(root):
                                    continue
                                try:
                                    info = self.run("xwininfo", "-id", window)
                                except subprocess.CalledProcessError:
                                    continue
                                if "Map State:" not in info or "IsViewable" in info:
                                    windows.append(window)
                                elif "IsUnmapped" not in info:
                                    now = time.monotonic()
                                    first_seen = ambiguous_since.setdefault(window, now)
                                    if now - first_seen >= 1.0:
                                        windows.append(window)
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
                    # Some native clients omit _NET_WM_PID. On a private
                    # display, identify the mapped game surface by its
                    # geometry when ancestry is unavailable; helper windows
                    # are smaller and remain excluded. Keep ambiguity
                    # rejection when geometry cannot disambiguate.
                    if owned_windows:
                        windows = owned_windows
                    else:
                        sized = []
                        for window in windows:
                            try:
                                info = self.run("xwininfo", "-id", window)
                            except subprocess.CalledProcessError:
                                continue
                            width = re.search(r"Width:\s+(\d+)", info)
                            height = re.search(r"Height:\s+(\d+)", info)
                            if width and height:
                                sized.append((int(width.group(1)) * int(height.group(1)), window))
                        windows = [max(sized)[1]] if sized else unresolved_windows
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
        # Fabric clients can finish their private-X bootstrap without taking
        # input focus. Activate the verified window before dispatching a
        # coordinate so widget acceptance probes reach the game window rather
        # than an unfocused X client.
        try:
            self.run("xdotool", "windowactivate", "--sync", self.window)
        except subprocess.CalledProcessError:
            # A bare Xvfb display has no window manager, so activation may
            # return nonzero even though coordinate input remains usable.
            pass
        self.run("xdotool", "mousemove", "--window", self.window, str(x), str(y), "click", "1")

    def capture(self, path):
        # A delayed XMapWindow can leave the selected client discoverable just
        # before ImageMagick can capture it. Retry the bounded capture while
        # preserving the same verified window identity.
        failure = None
        for _ in range(20):
            try:
                self.run("import", "-window", self.window, str(path))
                read_capture(path)
                return
            except subprocess.CalledProcessError as error:
                failure = error
                time.sleep(0.1)
        if failure is not None:
            raise failure

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
