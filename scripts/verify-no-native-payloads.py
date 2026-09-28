#!/usr/bin/env python3
"""Reject native content, including renamed binaries inside nested archives."""
import sys
from native_payloads import inspect_archive

if len(sys.argv) < 2:
    sys.exit('Usage: verify-no-native-payloads.py ARTIFACT.jar [...]')
try:
    failures = [entry for path in sys.argv[1:] for entry in inspect_archive(path)]
except Exception as error:
    sys.exit(f'Native payload inspection failed: {error}')
if failures:
    sys.exit('Native payloads are forbidden:\n' + '\n'.join(failures))
print(f'PASS: no native payloads in {len(sys.argv) - 1} artifact(s), including nested archives')
