"""Private follower edit retention during the owner's real state transitions."""
import hashlib
import json
from pathlib import Path
import time
from private_minecraft_window import PrivateMinecraftWindow


class ControllerEditProbe:
    def __init__(self, log_path, output, gate_pid):
        self.log_path = Path(log_path)
        self.output = Path(output)
        if self.output.exists():
            raise RuntimeError("Refusing to overwrite a controller editing attempt")
        self.desktop = PrivateMinecraftWindow(self.log(), gate_pid)
        self.output.mkdir(parents=True)
        self.captures = []
        self.baseline = 0

    def log(self):
        return self.log_path.read_text(errors="replace")

    def capture(self, name):
        path = self.output / (name + ".png")
        self.desktop.capture(path)
        self.captures.append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})

    def clear_field(self, x, y):
        self.desktop.click(x, y)
        # LWJGL2 polls physical modifiers; hold Ctrl across frames.
        self.desktop.run("xdotool", "keydown", "Control_L")
        try:
            time.sleep(0.2)
            self.desktop.run("xdotool", "key", "a")
            time.sleep(0.2)
        finally:
            self.desktop.run("xdotool", "keyup", "Control_L")
        self.desktop.run("xdotool", "key", "BackSpace")
        # Let the focused field consume Backspace before any following mouse
        # click changes focus (LWJGL2 drains mouse and keyboard separately).
        time.sleep(0.3)

    def prepare(self):
        self.capture("before-edit")
        self.clear_field(70, 130)
        self.desktop.run("xdotool", "type", "--clearmodifiers", "--delay", "150", "draft123")
        self.desktop.run("xdotool", "key", "--clearmodifiers", "--delay", "150", "Home", "Right", "Right")
        self.desktop.run("xdotool", "keydown", "Shift_L")
        try:
            time.sleep(0.2)
            self.desktop.run("xdotool", "key", "--delay", "150", "Right", "Right", "Right")
            time.sleep(0.2)
        finally:
            self.desktop.run("xdotool", "keyup", "Shift_L")
        self.capture("selection-before-state-changes")
        self.baseline = len(self.log())

    def finish(self):
        # The caller has now exercised six authoritative owner transitions.
        # Require actual packets and rebuilds, not merely elapsed time.
        deadline = time.monotonic() + 10
        while True:
            self.desktop.validate()
            fresh = self.log()[self.baseline:]
            sessions = fresh.count("Acceptance video session:")
            rebuilds = fresh.count("Acceptance video UI: width=320 height=240 ")
            if sessions >= 2 and rebuilds >= 2:
                break
            if time.monotonic() >= deadline:
                raise RuntimeError("Edit retention lacks two fresh session packets and UI rebuilds")
            time.sleep(0.1)
        self.capture("selection-after-state-changes")
        self.desktop.run("xdotool", "type", "--clearmodifiers", "Z")
        time.sleep(0.3)
        self.capture("selection-replaced")
        self.clear_field(70, 450)
        self.desktop.run("xdotool", "type", "--clearmodifiers", "--delay", "150", "session456")
        self.desktop.click(560, 130)
        time.sleep(0.3)
        self.capture("queue-drafts")
        self.desktop.click(560, 130)
        time.sleep(0.3)
        self.capture("browse-drafts")
        report = {"status": "captured-direct-review-required", "display": self.desktop.display,
                  "xvfbPid": self.desktop.xpid, "gatePid": self.desktop.gate_pid,
                  "interveningSessionPackets": sessions, "interveningUiRebuilds": rebuilds,
                  "expectedSearchSelection": "draft123 with aft selected",
                  "expectedSearchAfterReplacement": "drZ123",
                  "expectedSessionAfterQueueToggle": "session456", "captures": self.captures}
        # Never submit either draft or change the session; leave normal controls
        # with empty fields after recording their preservation.
        self.clear_field(70, 130)
        self.clear_field(70, 450)
        self.capture("cleared-drafts")
        (self.output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
        print("Controller edit retention captured across authoritative state changes; direct review required")
