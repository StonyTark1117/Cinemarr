#!/usr/bin/env python3
"""MODERN closed-log check: no media state before an explicit JOIN reset marker.

A generic reset can belong to a pre-connect LoggingOut event. It is not proof
that the new connection reset successfully before sending its hello.

Legacy currently emits this marker only on disconnect, so this check must not
be applied to its logs without first adding an explicit legacy JOIN marker.
"""
import argparse
import json
from pathlib import Path


def inspect(value):
    resets = []
    joins = []
    media = []
    for number, line in enumerate(value.splitlines(), 1):
        if "Acceptance client media reset complete" in line:
            resets.append(number)
        if "Acceptance client JOIN reset complete" in line:
            joins.append(number)
        if "Acceptance video session:" in line or "Acceptance video manifest:" in line:
            media.append(number)
    return {"passed": bool(resets and joins and media and resets[0] < joins[0] < media[0]),
            "firstResetLine": resets[0] if resets else None,
            "firstJoinResetLine": joins[0] if joins else None,
            "firstMediaLine": media[0] if media else None,
            "resetCount": len(resets), "mediaCount": len(media)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", nargs="?", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        reset = "Acceptance client media reset complete\n"
        joined = "Acceptance client JOIN reset complete\n"
        session = "Acceptance video session: generation=5\n"
        manifest = "Acceptance video manifest: generation=5\n"
        for value, passed in [(reset + joined + session + manifest + reset, True),
                              (reset + session + manifest + reset, False),
                              (reset + session + joined, False),
                              (joined + reset + session, False),
                              (joined + session, False),
                              (session + manifest + reset, False),
                              (manifest + reset + session, False),
                              (session, False), (reset, False), ("", False)]:
            assert inspect(value)["passed"] is passed
        print("Ten join-order fixtures passed")
    if args.log:
        result = inspect(args.log.read_text(errors="replace"))
        print(json.dumps(result))
        raise SystemExit(0 if result["passed"] else 1)
    if not args.self_test:
        parser.error("provide a log or --self-test")


if __name__ == "__main__":
    main()
