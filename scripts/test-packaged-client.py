#!/usr/bin/env python3
"""Production-launch input regressions. No network, real Java or GUI calls."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parent


def module(name):
    spec = importlib.util.spec_from_file_location(name.replace('-', '_'), SCRIPTS / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


launch = module('launch-packaged-client')
inventory = module('inventory-packaged-client')
stage = module('prepare-packaged-client-runtime')


class LaunchTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.profile = '1.21.1-neoforge'
        self.runtime_root = self.root / 'build/runtimes'
        self.runtime = self.runtime_root / self.profile
        self.runtime.mkdir(parents=True)
        self.game = self.root / 'build/packaged-client-focus/test'
        self.bundle = self.root / 'build/releases'
        self.bundle.mkdir()
        self.jar = self.bundle / 'cinemarr-test.jar'
        self.jar.write_bytes(b'candidate')
        self.sha = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        (self.bundle / 'SHA256SUMS').write_text(self.sha + '  cinemarr-test.jar\n')
        (self.root / 'gradle').mkdir()
        (self.root / 'gradle/targets.json').write_text(json.dumps({'artifacts': [{
            'runtime': {'name': self.profile}, 'minecraft': '1.21.1', 'loader': 'neoforge',
            'artifact': self.jar.name, 'runtimeJava': 21}]}))
        bootstrap = self.runtime / 'NewLaunch.jar'
        bootstrap.write_bytes(b'test-bootstrap')
        library = self.runtime / 'libraries/test/library.jar'
        library.parent.mkdir(parents=True)
        library.write_bytes(b'test-library')
        self.inventory = self.root / 'build/inventory.json'
        metadata = {'mainClass': 'production.Main', 'assetIndex': {'id': 'test'},
                    'minecraftArguments': '--username ${auth_player_name} --gameDir ${game_directory}'}
        self.inventory.write_text(json.dumps({'profile': self.profile, 'launchMetadata': metadata,
            'allLibraryInputsVerified': True, 'assetIndexVerified': True}))
        self.data = {'profile': self.profile, 'inventoryPath': str(self.inventory),
            'inventorySha256': launch.digest(self.inventory), 'launchMetadata': metadata,
            'bootstrapSha256': launch.digest(bootstrap), 'classpath': [str(bootstrap), str(library)],
            'librarySha1ByRelativePath': {'test/library.jar': launch.digest(library, 'sha1')},
            'nativeDirectory': str(self.runtime / 'natives'), 'assetRoot': str(self.runtime / 'assets')}
        self.args = ['launcher', self.profile, '--runtime-root', str(self.runtime_root),
            '--game-dir', str(self.game), '--bundle-dir', str(self.bundle),
            '--username', 'CinemarrVideoA', '--server', '127.0.0.1:25566',
            '--expected-server-host', '127.0.0.1', '--java-home', str(self.root / 'java')]
        for p in (patch.object(launch, 'ROOT', self.root), patch.dict(os.environ, {}, clear=True)):
            p.start(); self.addCleanup(p.stop)
        p = patch.object(launch.subprocess, 'check_output', return_value='openjdk version "21.0.8"')
        self.java = p.start(); self.addCleanup(p.stop)
        p = patch.object(launch.subprocess, 'run', return_value=SimpleNamespace(returncode=0))
        self.run = p.start(); self.addCleanup(p.stop)

    def invoke(self, preflight=True):
        (self.runtime / 'runtime.json').write_text(json.dumps(self.data))
        with patch.object(sys, 'argv', self.args + (['--check-only'] if preflight else [])):
            return launch.main()

    def rejected(self):
        with self.assertRaises((AssertionError, ValueError, KeyError)):
            self.invoke()
        self.run.assert_not_called()

    def test_read_only_preflight_does_not_create_game_or_open_x(self):
        self.assertEqual(0, self.invoke())
        self.assertFalse(self.game.exists())
        self.run.assert_not_called()

    def test_launch_copies_exact_candidate_and_uses_private_x_not_gradle(self):
        self.assertEqual(0, self.invoke(preflight=False))
        self.assertEqual(self.sha, launch.digest(self.game / 'mods' / self.jar.name))
        command = self.run.call_args.args[0]
        self.assertEqual('run-private-xvfb.sh', Path(command[1]).name)
        self.assertNotIn('runClient', command)
        self.assertIn('org.prismlauncher.EntryPoint', command)
        records = list((self.game / 'packaged-launch-records').glob('*.json'))
        self.assertEqual(1, len(records))
        self.assertEqual(self.sha, json.loads(records[0].read_text())['candidateSha256'])

    def test_wrong_candidate_hash_rejected(self):
        self.jar.write_bytes(b'changed')
        self.rejected()

    def test_duplicate_checksum_rejected(self):
        sums = self.bundle / 'SHA256SUMS'
        sums.write_text(sums.read_text() * 2)
        self.rejected()

    def test_changed_library_rejected(self):
        (self.runtime / 'libraries/test/library.jar').write_bytes(b'changed')
        self.rejected()

    def test_unverified_classpath_jar_rejected(self):
        extra = self.runtime / 'libraries/extra.jar'
        extra.write_bytes(b'extra')
        self.data['classpath'].append(str(extra))
        self.rejected()

    def test_changed_launch_metadata_rejected(self):
        self.data['launchMetadata']['mainClass'] = 'development.Main'
        self.rejected()

    def test_changed_inventory_rejected(self):
        self.inventory.write_text('{}')
        self.rejected()

    def test_wrong_java_rejected(self):
        self.java.return_value = 'openjdk version "17.0.8"'
        self.rejected()

    def test_development_flags_rejected(self):
        for env_name in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
            with self.subTest(env_name=env_name), patch.dict(os.environ, {env_name: '-Dfabric.development=true'}):
                self.rejected()

    def test_wrong_server_rejected(self):
        self.args[self.args.index('--server') + 1] = 'unexpected.example:25566'
        self.rejected()

    def test_unowned_game_path_rejected(self):
        self.args[self.args.index('--game-dir') + 1] = str(self.root / 'user-instance')
        self.rejected()

    def test_existing_development_mod_rejected(self):
        (self.game / 'mods').mkdir(parents=True)
        (self.game / 'mods/jammarr-test-dev.jar').write_bytes(b'dev')
        self.rejected()

    def test_conflicting_installed_candidate_preserved(self):
        (self.game / 'mods').mkdir(parents=True)
        installed = self.game / 'mods' / self.jar.name
        installed.write_bytes(b'user-change')
        self.rejected()
        self.assertEqual(b'user-change', installed.read_bytes())

    def test_mods_symlink_escape_rejected(self):
        self.game.mkdir(parents=True)
        outside = self.root / 'user-mods'
        outside.mkdir()
        (self.game / 'mods').symlink_to(outside, target_is_directory=True)
        self.rejected()
        self.assertEqual([], list(outside.iterdir()))


class PreparationTests(unittest.TestCase):
    def test_maven_path_and_classifier(self):
        self.assertEqual('org/test/lib/1.0/lib-1.0-linux.jar', inventory.maven_path('org.test:lib:1.0', 'linux'))

    def test_maven_traversal_rejected(self):
        with self.assertRaises(AssertionError): inventory.maven_path('org.test:../../bad:1')

    def test_linux_rules_reject_windows_only(self):
        self.assertFalse(inventory.allowed({'rules': [{'action': 'allow', 'os': {'name': 'windows'}}]}))
        self.assertTrue(inventory.allowed({'rules': [{'action': 'allow', 'os': {'name': 'linux'}}]}))

    def test_unknown_rule_fails_closed(self):
        with self.assertRaises(AssertionError): inventory.allowed({'rules': [{'action': 'allow', 'features': {}}]})

    def test_staging_rejects_source_symlink(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / 'input').write_bytes(b'input')
            (root / 'link').symlink_to(root / 'input')
            with self.assertRaises(AssertionError): stage.copy_verified(root / 'link', root / 'output')
            self.assertFalse((root / 'output').exists())


if __name__ == '__main__':
    unittest.main()
