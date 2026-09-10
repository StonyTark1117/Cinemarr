#!/usr/bin/env python3
"""Synchronous private-X widget gate with screen dispatch/error probe markers.

Run only while the owning gate holds owner commands idle. Screenshots require
direct review; markers do not certify visible text. Never target host DISPLAY.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import time
from private_minecraft_window import PrivateMinecraftWindow


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--gate-pid", type=int, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise RuntimeError("Refusing to overwrite an existing widget review directory")

    def log():
        return args.log.read_text(errors="replace")

    initial = log()
    if not re.search(r"Acceptance video UI: width=320 height=240 widgets=[1-9][0-9]* clipped=0 canControl=false overlaps=0", initial):
        raise RuntimeError("No verified non-owner scaled controller")
    # The client may unmap/remap its native window while the controller page
    # is being rebuilt. Use the helper's bounded wait instead of treating that
    # transition as a missing display.
    desktop = PrivateMinecraftWindow(initial, args.gate_pid, wait_seconds=10)
    args.output.mkdir(parents=True)
    captures = []
    actions = []

    def capture(name):
        path = args.output / (name + ".png")
        desktop.capture(path)
        captures.append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})

    def wait_new(offset, pattern, seconds=10):
        deadline = time.monotonic() + seconds
        while True:
            desktop.validate()
            found = re.search(pattern, log()[offset:])
            if found:
                return found.group(0)
            if time.monotonic() >= deadline:
                raise RuntimeError("Expected fresh widget evidence not observed: " + pattern)
            time.sleep(0.1)

    def click(name, x, y, action, dispatched):
        offset = len(log())
        desktop.click(x, y)
        marker = wait_new(offset, "Acceptance video widget command: action=" + action
                          + " dispatched=" + str(dispatched).lower() + " canControl=false")
        time.sleep(0.3)
        capture(name)
        actions.append({"name": name, "marker": marker})
        return offset

    def generation(value):
        found = re.findall(r"Acceptance video session:.*?generation=([0-9]+).*?canControl=false", value)
        if not found:
            raise RuntimeError("No authoritative non-owner session generation")
        return int(found[-1])

    baseline = len(log())
    before_generation = generation(initial)
    capture("initial-status")
    play_offset = click("play-denied", 460, 180, "PLAY", False)
    # Leave one denial up through normal packet-driven UI refreshes and its TTL.
    # Session snapshots are event-driven, not guaranteed periodic while owner is idle.
    time.sleep(2)
    denial_updates = log()[play_offset:].count("Acceptance video UI: width=320 height=240 ")
    capture("play-denial-after-updates")
    time.sleep(3.3)
    capture("play-denial-expired")
    click("queue-denied", 570, 180, "QUEUE", False)
    click("audio-denied", 125, 300, "SET_STREAMS", False)
    click("subtitles-denied", 360, 300, "SET_STREAMS", False)
    error_offset = click("tune-requested", 255, 448, "TUNE", True)
    error_marker = wait_new(error_offset, r"Acceptance video controller error displayed")
    time.sleep(0.3)
    capture("server-error-visible")
    time.sleep(5.3)
    capture("server-error-expired")
    final = log()
    after_generation = generation(final)
    observed = re.findall(r"Acceptance video widget command: action=([A-Z_]+) dispatched=(true|false) canControl=(true|false)", final[baseline:])
    expected = [("PLAY", "false", "false"), ("QUEUE", "false", "false"),
                ("SET_STREAMS", "false", "false"), ("SET_STREAMS", "false", "false"),
                ("TUNE", "true", "false")]
    if observed != expected or before_generation != after_generation:
        raise RuntimeError("Unexpected command dispatch, permission, or session-generation mutation")
    updates = final[baseline:].count("Acceptance video session:")
    report = {"status": "automated-passed-direct-review-required", "display": desktop.display,
              "xvfbPid": desktop.xpid, "gatePid": args.gate_pid, "actions": actions,
              "serverErrorMarker": error_marker, "generationBefore": before_generation,
              "generationAfter": after_generation, "sessionSnapshots": updates,
              "uiRefreshesDuringDenial": denial_updates,
              "captures": captures}
    (args.output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print("Widget dispatch and error-route checks passed; framebuffer review still required")


if __name__ == "__main__":
    main()
