#!/usr/bin/env python3
"""Align independent stereo PCM captures to a shared high-frequency marker."""

import argparse
import json
import math
from array import array
from pathlib import Path


SAMPLE_RATE = 48_000
CHANNELS = 2
SAMPLE_WIDTH = 2
FRAME_BYTES = CHANNELS * SAMPLE_WIDTH
WINDOW_FRAMES = 240  # Five milliseconds.


def read_samples(path: Path) -> tuple[bytes, array]:
    raw = path.read_bytes()
    if len(raw) % FRAME_BYTES:
        raise RuntimeError(f"{path} is not complete stereo s16le PCM")
    samples = array("h")
    samples.frombytes(raw)
    if samples.itemsize != SAMPLE_WIDTH:
        raise RuntimeError("unexpected PCM sample width")
    return raw, samples


def marker_envelope(samples: array, frequency: float) -> list[float]:
    values: list[float] = []
    window_samples = WINDOW_FRAMES * CHANNELS
    angular = 2.0 * math.pi * frequency / SAMPLE_RATE
    cosines = [math.cos(angular * frame) for frame in range(WINDOW_FRAMES)]
    sines = [math.sin(angular * frame) for frame in range(WINDOW_FRAMES)]
    for start in range(0, len(samples) - window_samples + 1, window_samples):
        in_phase = quadrature = 0.0
        for frame in range(WINDOW_FRAMES):
            index = start + frame * CHANNELS
            mono = (samples[index] + samples[index + 1]) * 0.5
            in_phase += mono * cosines[frame]
            quadrature += mono * sines[frame]
        values.append(math.hypot(in_phase, quadrature) / WINDOW_FRAMES)
    return values


def locate_marker(samples: array, frequency: float, minimum_marker_ms: int) -> tuple[int, int, float]:
    envelope = marker_envelope(samples, frequency)
    if not envelope:
        raise RuntimeError("capture is empty")
    peak = max(envelope)
    # The injected marker is deliberately much louder than leakage from the
    # program band. Requiring both an absolute floor and a fraction of the
    # observed peak rejects silence, ordinary game output, and single-window
    # transients without depending on a particular listener gain.
    threshold = max(500.0, peak * 0.35)
    minimum_windows = math.ceil(minimum_marker_ms * SAMPLE_RATE / 1000 / WINDOW_FRAMES)
    run_start = None
    runs: list[tuple[int, int]] = []
    for index, value in enumerate(envelope + [0.0]):
        if value >= threshold and run_start is None:
            run_start = index
        elif value < threshold and run_start is not None:
            if index - run_start >= minimum_windows:
                runs.append((run_start, index))
            run_start = None
    if len(runs) != 1:
        raise RuntimeError(
            f"expected exactly one calibration marker, found {len(runs)} "
            f"(peak={peak:.1f}, threshold={threshold:.1f})"
        )
    start_window, end_window = runs[0]
    return start_window * WINDOW_FRAMES, end_window * WINDOW_FRAMES, peak


def trim(raw: bytes, start_frame: int, duration_frames: int, output: Path) -> None:
    start = start_frame * FRAME_BYTES
    end = (start_frame + duration_frames) * FRAME_BYTES
    if start < 0 or end > len(raw):
        available_ms = max(0, len(raw) // FRAME_BYTES - start_frame) * 1000 / SAMPLE_RATE
        raise RuntimeError(
            f"capture has only {available_ms:.1f} ms after its aligned start; "
            f"need {duration_frames * 1000 / SAMPLE_RATE:.1f} ms"
        )
    output.write_bytes(raw[start:end])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("left_capture", type=Path)
    parser.add_argument("right_capture", type=Path)
    parser.add_argument("left_output", type=Path)
    parser.add_argument("right_output", type=Path)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--calibration-frequency", type=float, default=4093.0)
    parser.add_argument("--minimum-marker-ms", type=int, default=200)
    parser.add_argument("--offset-after-marker-ms", type=int, default=500)
    parser.add_argument("--duration-ms", type=int, default=8000)
    args = parser.parse_args()

    evidence: dict[str, object] = {
        "calibration_frequency_hz": args.calibration_frequency,
        "method": "shared-marker-independent-pcm-capture",
        "passed": False,
        "sample_rate_hz": SAMPLE_RATE,
        "schema": 1,
    }
    try:
        left_raw, left_samples = read_samples(args.left_capture)
        right_raw, right_samples = read_samples(args.right_capture)
        left_start, left_end, left_peak = locate_marker(
            left_samples, args.calibration_frequency, args.minimum_marker_ms
        )
        right_start, right_end, right_peak = locate_marker(
            right_samples, args.calibration_frequency, args.minimum_marker_ms
        )
        offset_frames = round(args.offset_after_marker_ms * SAMPLE_RATE / 1000)
        duration_frames = round(args.duration_ms * SAMPLE_RATE / 1000)
        trim(left_raw, left_end + offset_frames, duration_frames, args.left_output)
        trim(right_raw, right_end + offset_frames, duration_frames, args.right_output)
        evidence.update({
            "aligned_duration_ms": args.duration_ms,
            "left_marker_end_ms": round(left_end * 1000 / SAMPLE_RATE, 3),
            "left_marker_peak": round(left_peak, 3),
            "left_marker_start_ms": round(left_start * 1000 / SAMPLE_RATE, 3),
            "marker_origin_skew_ms": round((left_start - right_start) * 1000 / SAMPLE_RATE, 3),
            "offset_after_marker_ms": args.offset_after_marker_ms,
            "passed": True,
            "right_marker_end_ms": round(right_end * 1000 / SAMPLE_RATE, 3),
            "right_marker_peak": round(right_peak, 3),
            "right_marker_start_ms": round(right_start * 1000 / SAMPLE_RATE, 3),
        })
    except (OSError, RuntimeError, ValueError) as error:
        evidence["error"] = str(error)
    args.evidence.write_text(json.dumps(evidence, sort_keys=True) + "\n", encoding="utf-8")
    raise SystemExit(0 if evidence["passed"] else 1)


if __name__ == "__main__":
    main()
