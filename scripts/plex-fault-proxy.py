#!/usr/bin/env python3
"""Credential-transparent Plex reverse proxy with a file-controlled outage mode.

The proxy never logs request headers or URLs. It exists only for release
acceptance: point Cinemarr at it, set the control file to ``offline`` to return
HTTP 503, then restore ``online`` to exercise manual and automatic reconnects.
"""

from __future__ import annotations

import argparse
import http.client
import os
import signal
import ssl
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


HOP_HEADERS = {
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade",
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--port-file", required=True)
    parser.add_argument("--control-file", required=True)
    parser.add_argument("--upstream", default=os.environ.get("CINEMARR_PLEX_UPSTREAM", ""))
    args = parser.parse_args()

    upstream = urlsplit(args.upstream)
    if upstream.scheme not in {"http", "https"} or not upstream.hostname \
            or upstream.username or upstream.password or upstream.query or upstream.fragment:
        parser.error("upstream must be an HTTP(S) origin without credentials or query data")
    control_file = Path(args.control_file)
    port_file = Path(args.port_file)
    base_path = upstream.path.rstrip("/")

    class Handler(BaseHTTPRequestHandler):
        server_version = "CinemarrPlexFaultProxy/1"

        def do_GET(self) -> None:  # noqa: N802
            self.forward()

        def do_POST(self) -> None:  # noqa: N802
            self.forward()

        def do_DELETE(self) -> None:  # noqa: N802
            self.forward()

        def forward(self) -> None:
            mode = control_file.read_text(encoding="utf-8").strip() \
                if control_file.exists() else "offline"
            if mode != "online":
                self.send_error(503, "acceptance outage")
                return
            content_length = self.headers.get("Content-Length", "0")
            if not content_length.isdigit() or int(content_length) > 16 * 1024 * 1024:
                self.send_error(413)
                return
            body = self.rfile.read(int(content_length)) if int(content_length) else None
            headers = {
                name: value for name, value in self.headers.items()
                if name.lower() not in HOP_HEADERS and name.lower() not in {"host", "content-length"}
            }
            connection_class = http.client.HTTPSConnection if upstream.scheme == "https" \
                else http.client.HTTPConnection
            kwargs = {"timeout": 20}
            if upstream.scheme == "https":
                kwargs["context"] = ssl.create_default_context()
            connection = connection_class(upstream.hostname, upstream.port, **kwargs)
            try:
                connection.request(self.command, base_path + self.path, body=body, headers=headers)
                response = connection.getresponse()
                payload = response.read(64 * 1024 * 1024 + 1)
                if len(payload) > 64 * 1024 * 1024:
                    self.send_error(502, "upstream response exceeded acceptance proxy limit")
                    return
                self.send_response(response.status)
                for name, value in response.getheaders():
                    if name.lower() not in HOP_HEADERS and name.lower() != "content-length":
                        self.send_header(name, value)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            except (OSError, http.client.HTTPException):
                self.send_error(502, "upstream unavailable")
            finally:
                connection.close()

        def log_message(self, *_args: object) -> None:
            pass

    server = ThreadingHTTPServer((args.bind, args.port), Handler)
    port_file.parent.mkdir(parents=True, exist_ok=True)
    temporary = port_file.with_suffix(port_file.suffix + ".tmp")
    temporary.write_text(str(server.server_address[1]), encoding="ascii")
    os.replace(temporary, port_file)

    def stop(_signal: int, _frame: object) -> None:
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        server.serve_forever(poll_interval=0.1)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        port_file.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
