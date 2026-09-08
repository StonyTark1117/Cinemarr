#!/usr/bin/env python3
"""Reject successful launches that silently selected a different Fabric loader."""
import argparse
import json
from pathlib import Path
import re
import unittest


def check(text, minecraft, loader):
    observed = re.findall(r'Loading Minecraft (\S+) with Fabric Loader (\S+)', text)
    expected = (minecraft, loader)
    if observed != [expected]:
        raise ValueError(f'Expected one Minecraft/Fabric launch {expected!r}; observed {observed!r}')
    return {'passed': True, 'minecraft': minecraft, 'fabricLoader': loader}


class Tests(unittest.TestCase):
    def line(self, minecraft='1.20.1', loader='0.19.2'):
        return f'[main/INFO] (FabricLoader/GameProvider) Loading Minecraft {minecraft} with Fabric Loader {loader}\n'

    def test_exact_minimum(self):
        self.assertTrue(check(self.line(), '1.20.1', '0.19.2')['passed'])

    def test_new_minecraft(self):
        self.assertTrue(check(self.line('26.2'), '26.2', '0.19.2')['passed'])

    def test_normal_pinned_loader(self):
        self.assertTrue(check(self.line(loader='0.19.3'), '1.20.1', '0.19.3')['passed'])

    def test_silent_lock_upgrade(self):
        with self.assertRaises(ValueError):
            check(self.line(loader='0.19.3'), '1.20.1', '0.19.2')

    def test_wrong_minecraft(self):
        with self.assertRaises(ValueError):
            check(self.line('1.20.2'), '1.20.1', '0.19.2')

    def test_no_launch_marker(self):
        with self.assertRaises(ValueError):
            check('BUILD SUCCESSFUL\nDone (1s)! For help\n', '1.20.1', '0.19.2')

    def test_dependency_declaration_is_not_launch(self):
        with self.assertRaises(ValueError):
            check('net.fabricmc:fabric-loader:0.19.2 (forced)\n', '1.20.1', '0.19.2')

    def test_mixed_launches(self):
        with self.assertRaises(ValueError):
            check(self.line() + self.line(loader='0.19.3'), '1.20.1', '0.19.2')

    def test_duplicate_launch(self):
        with self.assertRaises(ValueError):
            check(self.line() * 2, '1.20.1', '0.19.2')

    def test_prefix_is_not_exact_version(self):
        with self.assertRaises(ValueError):
            check(self.line(loader='0.19.20'), '1.20.1', '0.19.2')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', nargs='?', type=Path)
    parser.add_argument('--minecraft')
    parser.add_argument('--loader')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(Tests)
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    if not args.log or not args.minecraft or not args.loader:
        parser.error('log, --minecraft and --loader are required')
    try:
        result = check(args.log.read_text(errors='replace'), args.minecraft, args.loader)
    except (OSError, ValueError) as error:
        print(json.dumps({'passed': False, 'error': str(error)}))
        return 1
    print(json.dumps(result))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
