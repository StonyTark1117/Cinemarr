#!/usr/bin/env python3
"""Populate Minecraft's asset cache with checksum-verified, retried downloads."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
ASSET_BASE_URL = "https://resources.download.minecraft.net"
SHA1 = re.compile(r"^[0-9a-f]{40}$")


def sha1_file(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verified(path: Path, expected_sha1: str, expected_size: int | None) -> bool:
    return (
        path.is_file()
        and (expected_size is None or path.stat().st_size == expected_size)
        and sha1_file(path) == expected_sha1
    )


def require_https(url: str, label: str) -> str:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc:
        raise RuntimeError(f"{label} does not declare an HTTPS URL")
    return url


def descriptor(document: dict[str, Any], label: str, *, size_required: bool) -> tuple[str, str, int | None]:
    url = require_https(str(document.get("url", "")), label)
    expected_sha1 = document.get("sha1")
    expected_size = document.get("size")
    if not isinstance(expected_sha1, str) or not SHA1.fullmatch(expected_sha1):
        raise RuntimeError(f"{label} does not declare a valid SHA-1")
    if expected_size is not None and (not isinstance(expected_size, int) or expected_size < 0):
        raise RuntimeError(f"{label} does not declare a valid size")
    if size_required and expected_size is None:
        raise RuntimeError(f"{label} does not declare a size")
    return url, expected_sha1, expected_size


def download(url: str, destination: Path, expected_sha1: str, expected_size: int | None,
             *, attempts: int = 3) -> bool:
    """Download atomically when absent or invalid; return whether bytes changed."""
    if verified(destination, expected_sha1, expected_size):
        return False
    destination.parent.mkdir(parents=True, exist_ok=True)
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        temporary: Path | None = None
        try:
            request = Request(url, headers={"User-Agent": "Cinemarr asset prefetch/1.0.0"})
            with urlopen(request, timeout=60) as response:
                status = getattr(response, "status", 200)
                if status != 200:
                    raise RuntimeError(f"HTTP {status} for {url}")
                with tempfile.NamedTemporaryFile(
                    dir=destination.parent, prefix=f".{destination.name}.", delete=False
                ) as output:
                    temporary = Path(output.name)
                    while chunk := response.read(1024 * 1024):
                        output.write(chunk)
            if not verified(temporary, expected_sha1, expected_size):
                raise RuntimeError(f"checksum or size mismatch for {url}")
            os.replace(temporary, destination)
            return True
        except (HTTPError, URLError, TimeoutError, OSError, RuntimeError) as error:
            last_error = error
            if temporary is not None:
                temporary.unlink(missing_ok=True)
            retryable = not isinstance(error, HTTPError) or (
                error.code in {408, 425, 429} or error.code >= 500
            )
            if not retryable or attempt == attempts:
                break
            time.sleep(attempt)
    raise RuntimeError(f"failed to fetch {url} after {attempts} attempts: {last_error}")


def json_bytes(url: str, *, attempts: int = 3) -> tuple[bytes, dict[str, Any]]:
    require_https(url, "JSON input")
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            request = Request(url, headers={"User-Agent": "Cinemarr asset prefetch/1.0.0"})
            with urlopen(request, timeout=60) as response:
                raw = response.read()
            break
        except (HTTPError, URLError, TimeoutError, OSError) as error:
            last_error = error
            retryable = not isinstance(error, HTTPError) or (
                error.code in {408, 425, 429} or error.code >= 500
            )
            if not retryable or attempt == attempts:
                raise RuntimeError(f"failed to fetch {url} after {attempt} attempts: {error}") from error
            time.sleep(attempt)
    else:
        raise RuntimeError(f"failed to fetch {url}: {last_error}")
    document = json.loads(raw)
    if not isinstance(document, dict):
        raise RuntimeError(f"JSON input is not an object: {url}")
    return raw, document


def prepare(version: str, assets_dir: Path, manifest_url: str = VERSION_MANIFEST_URL) -> dict[str, Any]:
    _, manifest = json_bytes(manifest_url)
    versions = manifest.get("versions")
    if not isinstance(versions, list):
        raise RuntimeError("Minecraft version manifest has no version list")
    matches = [entry for entry in versions if isinstance(entry, dict) and entry.get("id") == version]
    if len(matches) != 1:
        raise RuntimeError(f"Minecraft version manifest has {len(matches)} entries for {version}")

    version_url, version_sha1, _ = descriptor(matches[0], f"Minecraft {version}", size_required=False)
    raw_version, version_document = json_bytes(version_url)
    if hashlib.sha1(raw_version).hexdigest() != version_sha1:
        raise RuntimeError(f"Minecraft {version} metadata failed SHA-1 verification")

    index_value = version_document.get("assetIndex")
    if not isinstance(index_value, dict):
        raise RuntimeError(f"Minecraft {version} metadata has no asset index")
    index_id = index_value.get("id")
    if not isinstance(index_id, str) or not index_id:
        raise RuntimeError(f"Minecraft {version} asset index has no ID")
    index_url, index_sha1, index_size = descriptor(
        index_value, f"Minecraft {version} asset index", size_required=True
    )
    index_path = assets_dir / "indexes" / f"{index_id}.json"
    index_downloaded = download(index_url, index_path, index_sha1, index_size)
    index_document = json.loads(index_path.read_text(encoding="utf-8"))
    objects = index_document.get("objects")
    if not isinstance(objects, dict):
        raise RuntimeError(f"Minecraft {version} asset index has no object map")

    unique: dict[str, int] = {}
    for name, value in objects.items():
        if not isinstance(value, dict):
            raise RuntimeError(f"invalid metadata for asset {name}")
        expected_sha1 = value.get("hash")
        expected_size = value.get("size")
        if not isinstance(expected_sha1, str) or not SHA1.fullmatch(expected_sha1):
            raise RuntimeError(f"invalid SHA-1 for asset {name}")
        if not isinstance(expected_size, int) or expected_size < 0:
            raise RuntimeError(f"invalid size for asset {name}")
        unique[expected_sha1] = expected_size

    downloaded = 0
    for position, (expected_sha1, expected_size) in enumerate(sorted(unique.items()), start=1):
        path = assets_dir / "objects" / expected_sha1[:2] / expected_sha1
        url = f"{ASSET_BASE_URL}/{expected_sha1[:2]}/{expected_sha1}"
        if download(url, path, expected_sha1, expected_size):
            downloaded += 1
            if downloaded == 1 or downloaded % 100 == 0:
                print(f"Downloaded {downloaded} missing assets ({position}/{len(unique)} checked)", flush=True)

    return {
        "minecraftVersion": version,
        "assetIndex": index_id,
        "assetIndexDownloaded": index_downloaded,
        "assetObjectCount": len(unique),
        "assetObjectsDownloaded": downloaded,
        "allObjectsSha1AndSizeVerified": True,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-version", required=True)
    parser.add_argument("--assets-dir", required=True, type=Path)
    parser.add_argument("--manifest-url", default=VERSION_MANIFEST_URL)
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args()
    result = prepare(args.minecraft_version, args.assets_dir, args.manifest_url)
    if args.summary:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        args.summary.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
