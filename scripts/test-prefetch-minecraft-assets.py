#!/usr/bin/env python3
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("prefetch-minecraft-assets.py")
SPEC = importlib.util.spec_from_file_location("prefetch_minecraft_assets", SCRIPT)
module = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(module)


class Response(io.BytesIO):
    status = 200
    def __enter__(self): return self
    def __exit__(self, *_): self.close()


def digest(value: bytes) -> str:
    return hashlib.sha1(value).hexdigest()


class AssetPrefetchTest(unittest.TestCase):
    def test_prepares_verified_cache_and_retries_one_timed_out_object(self):
        first = b"first asset"
        second = b"second asset"
        index = json.dumps({"objects": {
            "one": {"hash": digest(first), "size": len(first)},
            "two": {"hash": digest(second), "size": len(second)},
            "duplicate": {"hash": digest(first), "size": len(first)},
        }}, sort_keys=True).encode()
        version = json.dumps({"assetIndex": {
            "id": "test-index", "url": "https://example.test/index.json",
            "sha1": digest(index), "size": len(index),
        }}, sort_keys=True).encode()
        manifest = json.dumps({"versions": [{
            "id": "1.21.1", "url": "https://example.test/version.json",
            "sha1": digest(version),
        }]}).encode()
        values = {
            "https://example.test/manifest.json": manifest,
            "https://example.test/version.json": version,
            "https://example.test/index.json": index,
            f"{module.ASSET_BASE_URL}/{digest(first)[:2]}/{digest(first)}": first,
            f"{module.ASSET_BASE_URL}/{digest(second)[:2]}/{digest(second)}": second,
        }
        manifest_url = "https://example.test/manifest.json"
        timed_out = f"{module.ASSET_BASE_URL}/{digest(first)[:2]}/{digest(first)}"
        attempts = {manifest_url: 0, timed_out: 0}
        requested = []

        def open_request(request, timeout=0):
            url = request.full_url
            requested.append((url, timeout))
            if url in attempts and attempts[url] == 0:
                attempts[url] += 1
                raise TimeoutError("fixture timeout")
            return Response(values[url])

        with tempfile.TemporaryDirectory(prefix="cinemarr-assets-") as temporary, \
                patch.object(module, "urlopen", side_effect=open_request), \
                patch.object(module.time, "sleep"):
            assets = Path(temporary)
            result = module.prepare("1.21.1", assets, manifest_url)
            self.assertEqual(2, result["assetObjectCount"])
            self.assertEqual(2, result["assetObjectsDownloaded"])
            self.assertTrue(result["allObjectsSha1AndSizeVerified"])
            self.assertEqual(first, (assets / "objects" / digest(first)[:2] / digest(first)).read_bytes())
            self.assertEqual(second, (assets / "objects" / digest(second)[:2] / digest(second)).read_bytes())
            self.assertEqual(2, sum(url == timed_out for url, _ in requested))
            self.assertEqual(2, sum(url == manifest_url for url, _ in requested))

            requested.clear()
            again = module.prepare("1.21.1", assets, manifest_url)
            self.assertEqual(0, again["assetObjectsDownloaded"])
            self.assertFalse(any(url.startswith(module.ASSET_BASE_URL) for url, _ in requested))

    def test_rejects_non_https_metadata(self):
        with self.assertRaisesRegex(RuntimeError, "HTTPS"):
            module.require_https("http://example.test/manifest.json", "manifest")


if __name__ == "__main__":
    unittest.main()
