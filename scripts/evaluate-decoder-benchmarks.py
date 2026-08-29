#!/usr/bin/env python3
"""Compare same-machine software/auto decoder evidence against default-enable criteria."""

from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path


HIGH_RESOLUTIONS = ("1080p", "4k")
LOW_RESOLUTIONS = ("144p", "240p", "480p", "720p")
ALL_RESOLUTIONS = ("144p", "240p", "480p", "720p", "1080p", "1440p", "4k")
IDENTITY_FIELDS = ("os", "arch", "java", "ffmpeg", "ffmpegClassifier", "gpu", "driver", "minecraftProfile")


def load(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("schema") != 3 or not isinstance(value.get("rows"), list):
        raise ValueError(f"{path} is not schema-3 decoder evidence")
    return value


def grouped(value: dict, minimum_runs: int) -> dict[str, list[dict]]:
    result: dict[str, list[dict]] = {}
    for row in value["rows"]:
        result.setdefault(row["resolution"], []).append(row)
    for resolution, rows in result.items():
        if len(rows) < minimum_runs:
            raise ValueError(f"{resolution} has {len(rows)} measured runs, expected at least {minimum_runs}")
    return result


def median(rows: list[dict], field: str) -> float:
    return statistics.median(float(row[field]) for row in rows)


def per_frame(rows: list[dict], field: str) -> float:
    return statistics.median(float(row[field]) / max(1, int(row["frames"])) for row in rows)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("software", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--minimum-runs", type=int, default=5)
    parser.add_argument("--require-recommendation", action="store_true")
    args = parser.parse_args()

    software = load(args.software)
    candidate = load(args.candidate)
    mismatched = [field for field in IDENTITY_FIELDS if software.get(field) != candidate.get(field)]
    if mismatched:
        raise ValueError("benchmark identity differs for: " + ", ".join(mismatched))
    if software.get("requestedBackend") != "software":
        raise ValueError("baseline did not request software")
    if candidate.get("requestedBackend") != "auto":
        raise ValueError("candidate did not request auto")

    software_rows = grouped(software, args.minimum_runs)
    candidate_rows = grouped(candidate, args.minimum_runs)
    required = set(ALL_RESOLUTIONS)
    missing = required - software_rows.keys() | required - candidate_rows.keys()
    if missing:
        raise ValueError("required resolutions are missing: " + ", ".join(sorted(missing)))

    reasons: list[str] = []
    measurements: dict[str, dict[str, float]] = {}
    for resolution in sorted(required):
        baseline = software_rows[resolution]
        tested = candidate_rows[resolution]
        if not all(bool(row.get("accepted")) for row in baseline + tested):
            reasons.append(f"{resolution} has a failed correctness/memory/A-V gate")
        if any(int(row.get("fallbackCount", 0)) != 0 for row in tested):
            reasons.append(f"{resolution} has an unexpected auto fallback")
        expected_effective = candidate.get("expectedEffectiveBackend")
        if not expected_effective or any(row.get("effectiveBackend") != expected_effective for row in tested):
            reasons.append(f"{resolution} did not consistently select the expected backend")

        software_cpu = median(baseline, "decoderThreadCpuNanos")
        candidate_cpu = median(tested, "decoderThreadCpuNanos")
        cpu_reduction = 1.0 - candidate_cpu / software_cpu if software_cpu > 0 else float("-inf")
        software_frame = per_frame(baseline, "segmentWallNanos")
        candidate_frame = per_frame(tested, "segmentWallNanos")
        wall_regression = candidate_frame / software_frame - 1.0 if software_frame > 0 else float("inf")
        measurements[resolution] = {
            "softwareMedianCpuNanos": software_cpu,
            "candidateMedianCpuNanos": candidate_cpu,
            "cpuReductionFraction": cpu_reduction,
            "softwareMedianWallNanosPerFrame": software_frame,
            "candidateMedianWallNanosPerFrame": candidate_frame,
            "wallRegressionFraction": wall_regression,
        }
        if resolution in HIGH_RESOLUTIONS and cpu_reduction < 0.20:
            reasons.append(f"{resolution} decoder-thread CPU reduction is below 20%")
        if resolution in LOW_RESOLUTIONS and wall_regression > 0.05:
            reasons.append(f"{resolution} wall time per frame regressed by more than 5%")

    result = {
        "schema": 1,
        "identity": {field: software.get(field) for field in IDENTITY_FIELDS},
        "candidateBackend": candidate.get("expectedEffectiveBackend"),
        "recommendDefault": not reasons,
        "reasons": reasons,
        "measurements": measurements,
    }
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 1 if args.require_recommendation and reasons else 0


if __name__ == "__main__":
    raise SystemExit(main())
