#!/usr/bin/env python3
"""Regression tests for calibrated dual-sink PCM capture alignment."""

import json
import math
import subprocess
import tempfile
from array import array
from pathlib import Path


SAMPLE_RATE = 48_000


def capture(capture_start_ms: int, program_delay_ms: int) -> bytes:
    duration_ms = 10_500
    samples = array("h")
    for frame in range(round(duration_ms * SAMPLE_RATE / 1000)):
        absolute_ms = capture_start_ms + frame * 1000 / SAMPLE_RATE
        value = 0.0
        if 600 <= absolute_ms < 1000:
            value += 12_000 * math.sin(2 * math.pi * 4093 * absolute_ms / 1000)
        program_ms = absolute_ms - 1700 - program_delay_ms
        if 0 <= program_ms < 8500:
            # A nonrepeating stepped envelope makes the retained program lag
            # independently measurable after the marker aligns capture origins.
            step = int(program_ms // 170)
            amplitude = 1800 + ((step * 7919 + step * step * 101) % 10_000)
            value += amplitude * math.sin(2 * math.pi * 997 * absolute_ms / 1000)
        sample = max(-32768, min(32767, round(value)))
        samples.extend((sample, sample))
    return samples.tobytes()


def run_case(root: Path, name: str, delay_ms: int, expected_pass: bool) -> None:
    left_capture = root / f"{name}-left-capture.s16le"
    right_capture = root / f"{name}-right-capture.s16le"
    left = root / f"{name}-left.s16le"
    right = root / f"{name}-right.s16le"
    evidence = root / f"{name}-alignment.json"
    left_capture.write_bytes(capture(0, 0))
    right_capture.write_bytes(capture(125, delay_ms))
    subprocess.run([
        "python3", "scripts/align-pcm-captures.py",
        str(left_capture), str(right_capture), str(left), str(right),
        "--evidence", str(evidence),
    ], check=True)
    alignment = json.loads(evidence.read_text(encoding="utf-8"))
    assert alignment["passed"] is True
    assert alignment["marker_origin_skew_ms"] == 125.0
    compared = subprocess.run([
        "python3", "scripts/compare-pcm-sync.py", str(left), str(right),
        "--maximum-lag-ms", "300",
    ], check=False, text=True, stdout=subprocess.PIPE)
    result = json.loads(compared.stdout)
    assert result["passed"] is expected_pass, result
    assert abs(abs(result["lag_ms"]) - delay_ms) <= 10, result


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="cinemarr-pcm-alignment-") as temporary:
        root = Path(temporary)
        run_case(root, "within-policy", 80, True)
        run_case(root, "outside-policy", 210, False)
    print("Calibrated PCM capture alignment tests passed")


if __name__ == "__main__":
    main()
