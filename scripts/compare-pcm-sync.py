#!/usr/bin/env python3
"""Compare two 48 kHz stereo s16le captures without third-party modules."""

import argparse
import json
import math
from array import array
from pathlib import Path


def envelope(path: Path, window_frames: int = 480, frequency: float = 997.0) -> list[float]:
    samples = array("h")
    samples.frombytes(path.read_bytes())
    if samples.itemsize != 2:
        raise RuntimeError("unexpected PCM sample width")
    values = []
    window_samples = window_frames * 2
    angular = 2.0 * math.pi * frequency / 48000.0
    for start in range(0, len(samples) - window_samples + 1, window_samples):
        # Quadrature demodulation isolates the synthetic program tone from
        # ordinary game audio, then retains its deliberately varying envelope.
        in_phase = quadrature = 0.0
        for frame in range(window_frames):
            index = start + frame * 2
            mono = (samples[index] + samples[index + 1]) * 0.5
            in_phase += mono * math.cos(angular * frame)
            quadrature += mono * math.sin(angular * frame)
        values.append(math.hypot(in_phase, quadrature) / window_frames)
    return values


def correlation(left: list[float], right: list[float], lag: int) -> float:
    left_start = max(0, lag)
    right_start = max(0, -lag)
    count = min(len(left) - left_start, len(right) - right_start)
    if count < 300:
        return -1.0
    left_slice = left[left_start:left_start + count]
    right_slice = right[right_start:right_start + count]
    left_mean = sum(left_slice) / count
    right_mean = sum(right_slice) / count
    numerator = left_power = right_power = 0.0
    for first, second in zip(left_slice, right_slice):
        first -= left_mean
        second -= right_mean
        numerator += first * second
        left_power += first * first
        right_power += second * second
    denominator = math.sqrt(left_power * right_power)
    return numerator / denominator if denominator else -1.0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("left", type=Path)
    parser.add_argument("right", type=Path)
    parser.add_argument("--maximum-lag-ms", type=int, default=200)
    parser.add_argument("--required-correlation", type=float, default=0.55)
    parser.add_argument("--required-lag-ms", type=float, default=150.0)
    args = parser.parse_args()

    left = envelope(args.left)
    right = envelope(args.right)
    sample_rate = 48000 / 480
    maximum_lag = round(args.maximum_lag_ms * sample_rate / 1000)
    best_lag, best_value = max(
        ((lag, correlation(left, right, lag)) for lag in range(-maximum_lag, maximum_lag + 1)),
        key=lambda value: value[1],
    )
    lag_ms = best_lag * 1000.0 / sample_rate
    passed = best_value >= args.required_correlation and abs(lag_ms) <= args.required_lag_ms
    print(json.dumps({
        "correlation": round(best_value, 6),
        "lag_ms": round(lag_ms, 3),
        "left_duration_seconds": round(len(left) / sample_rate, 3),
        "right_duration_seconds": round(len(right) / sample_rate, 3),
        "passed": passed,
    }, sort_keys=True))
    raise SystemExit(0 if passed else 1)


if __name__ == "__main__":
    main()
