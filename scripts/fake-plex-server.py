#!/usr/bin/env python3
"""Deterministic loopback Plex subset used by the all-target runtime gate."""

import argparse
import json
import os
import signal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


def looped_hls_playlist(path: Path, duration_ms: int) -> bytes:
    lines = path.read_text(encoding="utf-8").splitlines()
    header = [line for line in lines if line.startswith("#") and not line.startswith("#EXTINF:")
              and line != "#EXT-X-ENDLIST"]
    segments = []
    duration = None
    for line in lines:
        if line.startswith("#EXTINF:"):
            duration = line
        elif line and not line.startswith("#") and duration is not None:
            segments.append((duration, line))
            duration = None
    if not segments:
        raise ValueError(f"video playlist has no segments: {path}")
    target_seconds = max(1.0, duration_ms / 1000.0)
    output = header
    elapsed = 0.0
    index = 0
    while elapsed < target_seconds:
        extinf, name = segments[index % len(segments)]
        output.extend((extinf, name))
        elapsed += float(extinf.removeprefix("#EXTINF:").rstrip(","))
        index += 1
    output.append("#EXT-X-ENDLIST")
    return ("\n".join(output) + "\n").encode("utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port-file", required=True)
    parser.add_argument("--request-log", required=True)
    parser.add_argument("--token", required=True)
    parser.add_argument("--audio-file")
    parser.add_argument("--video-directory")
    parser.add_argument("--track-duration-ms", type=int, default=120000)
    parser.add_argument("--state-file")
    args = parser.parse_args()

    audio_file = Path(args.audio_file) if args.audio_file else None
    if audio_file is not None and not audio_file.is_file():
        parser.error(f"audio file does not exist: {audio_file}")
    state_file = Path(args.state_file) if args.state_file else None
    video_directory = Path(args.video_directory) if args.video_directory else None
    if video_directory is not None and not (video_directory / "media.m3u8").is_file():
        parser.error(f"video directory has no media.m3u8: {video_directory}")
    video_manifest = looped_hls_playlist(video_directory / "media.m3u8", args.track_duration_ms) \
        if video_directory is not None else b""
    movie = {
        "type": "movie", "ratingKey": "9001", "title": "Cinemarr A/V Acceptance",
        "contentRating": "G", "duration": args.track_duration_ms,
        "Media": [{"Part": [{"Stream": [
            {"streamType": 2, "id": 101, "language": "English", "languageCode": "eng",
             "codec": "aac", "selected": 1}
        ]}]}],
    }
    tracks = [
        {
            "type": "track", "ratingKey": str(key),
            "title": f"Gate Track {key}",
            "grandparentTitle": f"Gate Artist {key % 3 + 1}",
            "parentTitle": f"Gate Album {key % 2 + 1}",
            "duration": args.track_duration_ms, "musicAnalysisVersion": 1,
        }
        for key in range(42, 50)
    ]
    tracks_by_key = {track["ratingKey"]: track for track in tracks}

    port_file = Path(args.port_file)
    request_log = Path(args.request_log)
    request_log.parent.mkdir(parents=True, exist_ok=True)
    request_log.write_text("", encoding="utf-8")

    class Handler(BaseHTTPRequestHandler):
        server_version = "CinemarrFakePlex/1"

        def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
            request = urlsplit(self.path)
            token = self.headers.get("X-Plex-Token", "")
            if not token:
                token = parse_qs(request.query).get("X-Plex-Token", [""])[0]
            state = "offline" if state_file is not None and state_file.exists() \
                and state_file.read_text(encoding="utf-8").strip() == "offline" else "online"
            with request_log.open("a", encoding="utf-8") as stream:
                stream.write(f"GET\t{request.path}\t{token}\t{state}\n")
            if token != args.token:
                self.respond(401, {})
                return
            if state == "offline":
                self.respond(503, {})
                return
            path = request.path
            if path == "/library/sections":
                body = {"MediaContainer": {"Directory": [
                    {"type": "movie", "key": "1", "title": "Movies"}
                ]}}
            elif path == "/":
                body = {"MediaContainer": {
                    "version": "1.41.0", "machineIdentifier": "cinemarr-fake-plex",
                    "myPlexSubscription": True
                }}
            elif path == "/library/sections/1/all":
                body = {"MediaContainer": {"Metadata": [movie] if video_directory is not None else tracks}}
            elif path.startswith("/library/metadata/") and path.endswith("/nearest"):
                key = path.removeprefix("/library/metadata/").removesuffix("/nearest")
                if key not in tracks_by_key:
                    self.respond(404, {})
                    return
                nearest = []
                for index, candidate in enumerate(tracks):
                    if candidate["ratingKey"] == key:
                        continue
                    value = dict(candidate)
                    value["distance"] = round(0.05 + index * 0.02, 3)
                    nearest.append(value)
                body = {"MediaContainer": {"Metadata": nearest}}
            elif path == "/library/sections/1/computePath":
                query = parse_qs(request.query)
                start = query.get("startID", ["42"])[0]
                end = query.get("endID", ["43"])[0]
                if start not in tracks_by_key or end not in tracks_by_key:
                    self.respond(404, {})
                    return
                middle = next(track for track in tracks if track["ratingKey"] not in (start, end))
                body = {"MediaContainer": {"Metadata": [tracks_by_key[start], middle, tracks_by_key[end]]}}
            elif path.startswith("/library/metadata/"):
                key = path.removeprefix("/library/metadata/")
                if video_directory is not None and key == movie["ratingKey"]:
                    body = {"MediaContainer": {"Metadata": [movie]}}
                    self.respond(200, body)
                    return
                track = tracks_by_key.get(key)
                if track is None:
                    self.respond(404, {})
                    return
                body = {"MediaContainer": {"Metadata": [track]}}
            elif path == "/music/:/transcode/universal/start.mp3" and audio_file is not None:
                self.respond_bytes(200, audio_file.read_bytes(), "audio/mpeg")
                return
            elif path == "/video/:/transcode/universal/start.m3u8" and video_directory is not None:
                master = ("#EXTM3U\n#EXT-X-VERSION:3\n"
                          "#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=160x90,"
                          "CODECS=\"avc1.42e01e,mp4a.40.2\"\nmedia.m3u8\n")
                self.respond_bytes(200, master.encode("utf-8"), "application/vnd.apple.mpegurl")
                return
            elif path == "/video/:/transcode/universal/stop" and video_directory is not None:
                self.respond_bytes(200, b"", "application/octet-stream")
                return
            elif path.startswith("/video/:/transcode/universal/") and video_directory is not None:
                name = path.removeprefix("/video/:/transcode/universal/")
                if name == "media.m3u8":
                    self.respond_bytes(200, video_manifest, "application/vnd.apple.mpegurl")
                    return
                candidate = video_directory / name
                if candidate.parent != video_directory or not candidate.is_file():
                    self.respond(404, {})
                    return
                content_type = "application/vnd.apple.mpegurl" if candidate.suffix == ".m3u8" else "video/mp2t"
                self.respond_bytes(200, candidate.read_bytes(), content_type)
                return
            else:
                self.respond(404, {})
                return
            self.respond(200, body)

        def respond(self, status: int, body: dict) -> None:
            payload = json.dumps(body, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def respond_bytes(self, status: int, payload: bytes, content_type: str) -> None:
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_args: object) -> None:
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
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
