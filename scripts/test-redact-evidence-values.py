#!/usr/bin/env python3

from __future__ import annotations

import json
import gzip
import pathlib
import subprocess
import tempfile


SCRIPT = pathlib.Path(__file__).with_name("redact-evidence-values.py")


def main() -> None:
    secret = "process-local-token-value"
    endpoint = "192.0.2.44"
    with tempfile.TemporaryDirectory(prefix="cinemarr-redaction-test-") as raw:
        root = pathlib.Path(raw)
        log = root / "client.log"
        document = root / "manifest.json"
        binary = root / "capture.bin"
        compressed = root / "2026-09-06-1.log.gz"
        server_list = root / "servers.dat"
        log.write_text(f"connect {endpoint}; token={secret}\n", encoding="utf-8")
        document.write_text(
            json.dumps({"host": endpoint, "credential": secret}) + "\n",
            encoding="utf-8",
        )
        binary.write_bytes(b"\x00" + secret.encode("utf-8"))
        compressed.write_bytes(gzip.compress(f"connect {endpoint}; token={secret}\n".encode()))
        # Root compound with an ordinary NBT string. Masking the payload must
        # preserve the unsigned-short UTF-8 byte length and surrounding tags.
        server_bytes = b"\x0a\x00\x00\x08\x00\x02ip" + len(endpoint).to_bytes(2, "big") + endpoint.encode() + b"\x00"
        server_list.write_bytes(server_bytes)

        result = subprocess.run(
            ["python3", str(SCRIPT), str(root)],
            input=f"{secret}\0{endpoint}\0".encode("utf-8"),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )
        assert secret.encode("utf-8") not in result.stdout + result.stderr
        assert endpoint.encode("utf-8") not in result.stdout + result.stderr
        assert secret not in log.read_text(encoding="utf-8")
        assert endpoint not in log.read_text(encoding="utf-8")
        parsed = json.loads(document.read_text(encoding="utf-8"))
        assert parsed["host"] == "[REDACTED_RELEASE_ENDPOINT]"
        assert parsed["credential"] == "[REDACTED_RELEASE_ENDPOINT]"
        assert binary.read_bytes() == b"\x00" + secret.encode("utf-8")
        decompressed = gzip.decompress(compressed.read_bytes())
        assert secret.encode() not in decompressed and endpoint.encode() not in decompressed
        assert server_list.read_bytes() == server_bytes.replace(endpoint.encode(), b"x" * len(endpoint))

    print("Release evidence redaction tests passed")


if __name__ == "__main__":
    main()
