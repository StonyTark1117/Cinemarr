#!/usr/bin/env python3
"""Compare two captured 48 kHz stereo programs without assuming a test tone."""

import argparse
import json
import math
import subprocess
from pathlib import Path


FEATURE_RATE = 100
SPECTRAL_MEASURES = ("centroid", "spread", "entropy", "flatness", "flux")


def spectral_features(path: Path) -> list[list[float]]:
    """Return gain-resistant spectral features at 100 frames per second.

    Positional OpenAL output can apply different gain, panning, and filtering to
    two listeners even when they hear the same synchronized program. Comparing
    broadband amplitude alone therefore produces false negatives. FFmpeg's
    spectral statistics preserve the program's time-varying fingerprint while
    remaining insensitive to those listener-specific spatial changes.
    """
    measures = "+".join(SPECTRAL_MEASURES)
    command = [
        "ffmpeg", "-hide_banner", "-loglevel", "error",
        "-f", "s16le", "-ar", "48000", "-ac", "2", "-i", str(path),
        "-af", (
            "aspectralstats=win_size=4096:overlap=0.8828125:"
            f"measure={measures},ametadata=print:file=-"
        ),
        "-f", "null", "-",
    ]
    completed = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, check=False)
    if completed.returncode != 0:
        detail = completed.stderr.strip().splitlines()
        raise RuntimeError(detail[-1] if detail else "ffmpeg spectral analysis failed")

    frames: list[dict[str, float]] = []
    current: dict[str, float] = {}
    for line in completed.stdout.splitlines():
        if line.startswith("frame:"):
            if current:
                frames.append(current)
            current = {}
            continue
        if not line.startswith("lavfi.aspectralstats.") or "=" not in line:
            continue
        key, raw_value = line.split("=", 1)
        parts = key.split(".")
        if len(parts) != 4 or parts[3] not in SPECTRAL_MEASURES:
            continue
        current[f"{parts[2]}.{parts[3]}"] = math.log1p(max(0.0, float(raw_value)))
    if current:
        frames.append(current)

    keys = [f"{channel}.{measure}" for channel in ("1", "2")
            for measure in SPECTRAL_MEASURES]
    complete = [frame for frame in frames if all(key in frame for key in keys)]
    if len(complete) < 300:
        raise RuntimeError("audio capture has fewer than three seconds of spectral evidence")
    return [[frame[key] for key in keys] for frame in complete]


def correlation(left: list[list[float]], right: list[list[float]], lag: int) -> float:
    left_start = max(0, lag)
    right_start = max(0, -lag)
    count = min(len(left) - left_start, len(right) - right_start)
    if count < 300:
        return -1.0
    left_slice = left[left_start:left_start + count]
    right_slice = right[right_start:right_start + count]
    feature_correlations: list[float] = []
    for feature in range(len(left_slice[0])):
        left_values = [frame[feature] for frame in left_slice]
        right_values = [frame[feature] for frame in right_slice]
        left_mean = sum(left_values) / count
        right_mean = sum(right_values) / count
        numerator = left_power = right_power = 0.0
        for first, second in zip(left_values, right_values):
            first -= left_mean
            second -= right_mean
            numerator += first * second
            left_power += first * first
            right_power += second * second
        denominator = math.sqrt(left_power * right_power)
        feature_correlations.append(numerator / denominator if denominator else -1.0)
    return sum(feature_correlations) / len(feature_correlations)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("left", type=Path)
    parser.add_argument("right", type=Path)
    parser.add_argument("--maximum-lag-ms", type=int, default=300)
    parser.add_argument("--required-correlation", type=float, default=0.45)
    parser.add_argument("--required-lag-ms", type=float, default=150.0)
    args = parser.parse_args()

    try:
        left = spectral_features(args.left)
        right = spectral_features(args.right)
    except (OSError, RuntimeError, ValueError) as error:
        print(json.dumps({"error": str(error), "method": "broadband-spectral-fingerprint",
                          "passed": False}, sort_keys=True))
        raise SystemExit(1)
    maximum_lag = round(args.maximum_lag_ms * FEATURE_RATE / 1_000)
    best_lag, best_value = max(
        ((lag, correlation(left, right, lag))
         for lag in range(-maximum_lag, maximum_lag + 1)),
        key=lambda value: value[1],
    )
    lag_ms = best_lag * 1_000.0 / FEATURE_RATE
    passed = best_value >= args.required_correlation and abs(lag_ms) <= args.required_lag_ms
    print(json.dumps({
        "correlation": round(best_value, 6),
        "lag_ms": round(lag_ms, 3),
        "left_duration_seconds": round(len(left) / FEATURE_RATE, 3),
        "method": "broadband-spectral-fingerprint",
        "passed": passed,
        "right_duration_seconds": round(len(right) / FEATURE_RATE, 3),
    }, sort_keys=True))
    raise SystemExit(0 if passed else 1)


if __name__ == "__main__":
    main()
