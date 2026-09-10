#!/usr/bin/env python3
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('verifier', Path(__file__).with_name('verify-native-bundle-evidence.py'))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class NativeBundleEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.run = '20260909T023852Z'
        self.expected = {'schema': 1, 'runId': self.run, 'files': [
            {'path': name, 'sha256': str(i) * 64} for i, name in enumerate([
                'lib/core-1.0.0.jar', 'classes/Benchmark.class',
                'fixtures/144p.ts', 'fixtures/480p.ts', 'fixtures/1080p.ts'])]}
        self.used = copy.deepcopy(self.expected)
        self.used['workDirectory'] = 'C:\\CinemarrNativeSmoke\\runs\\' + self.run
        self.benchmark = {'rows': [{'resolution': resolution, 'fixtureSha256': str(i) * 64}
                                  for i, resolution in enumerate(['144p', '480p', '1080p'], 2)]}

    def test_current_complete_bundle(self):
        verifier.verify(self.expected, self.used, self.benchmark)

    def test_stale_core_rejected_even_when_benchmark_passes(self):
        self.used['files'][0]['sha256'] = 'f' * 64
        with self.assertRaises(AssertionError): verifier.verify(self.expected, self.used, self.benchmark)

    def test_original_reuse_failure_stale_fixture_rejected(self):
        self.benchmark['rows'][0]['fixtureSha256'] = 'f' * 64
        with self.assertRaises(AssertionError): verifier.verify(self.expected, self.used, self.benchmark)

    def test_stale_run_or_shared_work_directory(self):
        for change in ({'runId': '20260908T023852Z'}, {'workDirectory': 'C:\\CinemarrNativeSmoke'}):
            used = self.used | change
            with self.assertRaises(AssertionError): verifier.verify(self.expected, used, self.benchmark)

    def test_missing_or_duplicate_files(self):
        for files in (self.used['files'][:-1], self.used['files'] + [self.used['files'][0]]):
            used = self.used | {'files': files}
            with self.assertRaises(AssertionError): verifier.verify(self.expected, used, self.benchmark)


if __name__ == '__main__': unittest.main()
