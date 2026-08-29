#!/usr/bin/env python3
"""Small credential-free PUT receiver for disposable decoder-test guests."""

from __future__ import annotations

import argparse
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


SAFE_NAME = re.compile(r"[A-Za-z0-9_.-]{1,160}$")


def handler(output: Path, maximum: int):
    class EvidenceHandler(BaseHTTPRequestHandler):
        server_version = "CinemarrEvidence/1"

        def do_PUT(self) -> None:  # noqa: N802 - HTTP method spelling
            name = self.path.lstrip("/")
            length_text = self.headers.get("Content-Length", "")
            if not SAFE_NAME.fullmatch(name) or not length_text.isdigit():
                self.send_error(400)
                return
            length = int(length_text)
            if length < 0 or length > maximum:
                self.send_error(413)
                return
            destination = output / name
            temporary = output / (name + ".partial")
            remaining = length
            try:
                with temporary.open("wb") as stream:
                    while remaining:
                        block = self.rfile.read(min(1024 * 1024, remaining))
                        if not block:
                            raise EOFError("request ended before Content-Length")
                        stream.write(block)
                        remaining -= len(block)
                os.replace(temporary, destination)
            except (OSError, EOFError):
                temporary.unlink(missing_ok=True)
                self.send_error(500)
                return
            self.send_response(201)
            self.end_headers()

        def log_message(self, pattern: str, *args: object) -> None:
            print(f"{self.address_string()} {pattern % args}", flush=True)

    return EvidenceHandler


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--maximum-bytes", type=int, default=2 * 1024 * 1024 * 1024)
    args = parser.parse_args()
    if args.port < 1024 or args.port > 65535 or args.maximum_bytes < 1:
        parser.error("invalid port or maximum size")
    args.output.mkdir(parents=True, exist_ok=True)
    ThreadingHTTPServer((args.bind, args.port), handler(args.output, args.maximum_bytes)).serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
