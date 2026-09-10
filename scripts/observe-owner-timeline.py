#!/usr/bin/env python3
"""Actual private-X owner controls, checked against fresh server session packets."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import time
from private_minecraft_window import PrivateMinecraftWindow
from controller_edit_probe import ControllerEditProbe


def stream_control(kind):
    if kind == "audio":
        return 125, "audio", "subtitle"
    if kind == "subtitle":
        return 350, "subtitle", "audio"
    raise ValueError("Unsupported owner stream control")


def verify_stream_change(before, after, kind, expected_position, tolerance=0):
    _, selected, other = stream_control(kind)
    if (after[selected] == before[selected] or after[other] != before[other]
            or after["status"] != before["status"]
            or after["generation"] <= before["generation"]
            or abs(after["positionMs"] - expected_position) > tolerance):
        raise RuntimeError("Stream change must change the requested " + kind
                           + " selection, preserve the other stream and retain the playback cursor/state")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--gate-pid", type=int, required=True)
    parser.add_argument("--follower-log", type=Path, required=True)
    parser.add_argument("--edit-output", type=Path, required=True)
    parser.add_argument("--stream-kind", choices=("audio", "subtitle"), default="audio",
                        help="Required alternate-stream widget for the selected fixture; no automatic skip/fallback")
    args = parser.parse_args()
    stream_x, _, _ = stream_control(args.stream_kind)
    if args.output.exists():
        raise RuntimeError("Refusing to overwrite an owner-timeline attempt")

    def log():
        return args.log.read_text(errors="replace")

    initial = log()
    if not re.search(r"Acceptance video UI: width=320 height=240 widgets=[1-9][0-9]* clipped=0 canControl=true overlaps=0", initial):
        raise RuntimeError("No verified owner controller at the required logical size")
    desktop = PrivateMinecraftWindow(initial, args.gate_pid)
    args.output.mkdir(parents=True)
    captures, actions = [], []
    control = args.log.with_name(args.log.name.removesuffix(".console.log") + ".control")
    if not args.log.name.endswith(".audio-leader.console.log") or not control.is_file() or control.is_symlink():
        raise RuntimeError("Owner UI reopening requires the exact existing leader control file")
    pattern = re.compile(r"Acceptance video session:.*?generation=([0-9]+) status=([A-Z_]+) item=.*? positionMs=([0-9]+) canControl=true streams=([0-9]+) audio=(-?[0-9]+) subtitle=(-?[0-9]+)")

    def states(value):
        return [{"generation": int(m[0]), "status": m[1], "positionMs": int(m[2]),
                 "streams": int(m[3]), "audio": int(m[4]), "subtitle": int(m[5])}
                for m in pattern.findall(value)]

    def capture(name):
        path = args.output / (name + ".png")
        desktop.capture(path)
        captures.append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})

    def click(action, x, y, status, previous):
        offset = len(log())
        desktop.click(x, y)
        deadline = time.monotonic() + 60
        marker = "Acceptance video widget command: action=" + action + " dispatched=true canControl=true"
        while True:
            desktop.validate()
            value = log()[offset:]
            if "Acceptance video controller error displayed" in value:
                raise RuntimeError("Owner command produced a server error: " + action)
            current = states(value)
            # A seek is an authoritative state transition even when the
            # server keeps the same playback generation. Require a fresh
            # packet whose state differs from the prior snapshot; this still
            # rejects stale/replayed packets without assuming every command
            # allocates a new generation.
            if marker in value and current and current[-1]["status"] == status and current[-1] != previous:
                result = current[-1]
                actions.append({"action": action, "marker": marker, "before": previous, "after": result})
                return result, time.monotonic()
            if time.monotonic() >= deadline:
                raise RuntimeError("No fresh authoritative result for owner widget: " + action)
            time.sleep(0.1)

    def retained_frame(generation):
        deadline = time.monotonic() + 10
        pattern = r"Acceptance paused frame retained: generation=" + str(generation) + r" frameSha256=([0-9a-f]{64}) ptsUs=([0-9]+)"
        while True:
            desktop.validate()
            match = re.search(pattern, log())
            if match:
                return match.group(1)
            if time.monotonic() >= deadline:
                raise RuntimeError("Paused generation lost the previously displayed frame")
            time.sleep(0.1)

    def paused_world_pair():
        desktop.escape()
        time.sleep(0.3)
        capture("paused-world-before")
        time.sleep(3.2)
        capture("paused-world-after-three-seconds")
        offset = len(log())
        desktop.validate()
        control.write_text("paused-frame-" + str(time.monotonic_ns()) + "|video:open-ui\n")
        deadline = time.monotonic() + 10
        while not re.search(r"Acceptance video UI: width=320 height=240 widgets=[1-9][0-9]* clipped=0 canControl=true overlaps=0", log()[offset:]):
            desktop.validate()
            if time.monotonic() >= deadline:
                raise RuntimeError("Owner controller did not reopen after paused world capture")
            time.sleep(0.1)
        time.sleep(0.3)

    current = states(initial)[-1]
    if current["status"] != "PLAYING":
        raise RuntimeError("Owner timeline gate requires active initial playback")
    edits = ControllerEditProbe(args.follower_log, args.edit_output, args.gate_pid)
    try:
        edits.prepare()
        capture("playing-before")
        time.sleep(3.2)
        capture("playing-after-three-seconds")
        paused, _ = click("PAUSE", 85, 350, "PAUSED", current)
        paused_frame = retained_frame(paused["generation"])
        capture("paused-before")
        paused_world_pair()
        capture("paused-after-three-seconds")
        sought, _ = click("SEEK", 328, 350, "PAUSED", paused)
        if sought["positionMs"] != paused["positionMs"] + 30_000:
            raise RuntimeError("Paused +30s did not preserve pause and move exactly thirty seconds")
        if retained_frame(sought["generation"]) != paused_frame:
            raise RuntimeError("Paused seek lost the held program frame")
        capture("paused-seek")
        changed, _ = click("SET_STREAMS", stream_x, 300, "PAUSED", sought)
        verify_stream_change(sought, changed, args.stream_kind, sought["positionMs"])
        if retained_frame(changed["generation"]) != paused_frame:
            raise RuntimeError("Paused stream change lost the held program frame")
        capture("paused-stream-change")
        resumed, resume_at = click("RESUME", 85, 350, "PLAYING", changed)
        if abs(resumed["positionMs"] - changed["positionMs"]) > 2_000:
            raise RuntimeError("Resume did not start near the paused cursor")
        time.sleep(3.2)
        capture("resumed-clock")
        elapsed = (time.monotonic() - resume_at) * 1000
        sought, seek_at = click("SEEK", 328, 350, "PLAYING", resumed)
        expected = resumed["positionMs"] + elapsed + 30_000
        if abs(sought["positionMs"] - expected) > 2_000:
            raise RuntimeError("Playing +30s used a stale snapshot instead of the advancing clock")
        capture("playing-seek")
        time.sleep(3.2)
        elapsed = (time.monotonic() - seek_at) * 1000
        changed, _ = click("SET_STREAMS", stream_x, 300, "PLAYING", sought)
        verify_stream_change(sought, changed, args.stream_kind, sought["positionMs"] + elapsed, 5_000)
        capture("playing-stream-change")
        edits.finish()
        report = {"status": "automated-passed-direct-review-required", "display": desktop.display,
                  "xvfbPid": desktop.xpid, "gatePid": args.gate_pid,
                  "actions": actions, "streamKind": args.stream_kind,
                  "pausedFrameSha256": paused_frame, "captures": captures}
        (args.output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
        print("Actual owner pause/seek/stream/resume timeline checks passed; image review required")
    finally:
        desktop.escape()


if __name__ == "__main__":
    main()
