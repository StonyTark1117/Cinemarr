#!/usr/bin/env python3

from __future__ import annotations

import json
import gzip
import hashlib
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

    with tempfile.TemporaryDirectory(prefix="cinemarr-client-address-redaction-") as raw:
        root = pathlib.Path(raw)
        client4, client6 = '192.0.2.91', '2001:db8::beef'
        text = (f'CinemarrVideoA[/{client4}:34567] logged in with entity id 181\n'
                f'CinemarrVideoB[/[{client6}]:23456] logged in with entity id 182\n'
                'UnrelatedPlayer[/198.51.100.4:23456] logged in with entity id 183\n'
                'version 1.2.3.4 remains intact\n')
        log = root / 'remote-server.log'
        log.write_text(text)
        zipped = root / 'old.log.gz'
        zipped.write_bytes(gzip.compress(text.encode()))
        before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (log, zipped)}
        receipt = root / 'redaction.json'
        result = subprocess.run(['python3', str(SCRIPT), '--minecraft-client-addresses',
                                 '--receipt', str(receipt), str(root)], input=b'configured-secret\0',
                                capture_output=True, check=True)
        for payload in (log.read_bytes(), gzip.decompress(zipped.read_bytes()), receipt.read_bytes(),
                        result.stdout + result.stderr):
            assert client4.encode() not in payload and client6.encode() not in payload
        assert 'UnrelatedPlayer[/198.51.100.4:23456]' in log.read_text()
        assert 'version 1.2.3.4 remains intact' in log.read_text()
        proof = json.loads(receipt.read_text())
        assert proof['schema'] == 1 and proof['replacements'] == 4
        assert len(proof['changedFiles']) == 2
        for row in proof['changedFiles']:
            assert row['beforeSha256'] == before[row['path']]
            assert row['afterSha256'] == hashlib.sha256((root / row['path']).read_bytes()).hexdigest()
        # An existing proof must never be silently overwritten on a second pass.
        repeated = subprocess.run(['python3', str(SCRIPT), '--receipt', str(receipt), str(root)],
                                  input=b'configured-secret\0', capture_output=True)
        assert repeated.returncode != 0

    print("Release evidence redaction tests passed")


if __name__ == "__main__":
    main()
