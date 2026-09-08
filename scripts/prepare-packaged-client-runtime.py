"""Stage only verified public launcher inputs into a new task-owned directory.

No account stores, launcher settings, existing instances, game launches, or
development classpaths are used. Run separately from physical audio tests.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile
from urllib.request import Request, urlopen
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]


def digest(path, algorithm='sha256'):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def copy_verified(source, target, expected_sha1=None):
    assert source.is_file() and not source.is_symlink(), str(source)
    if expected_sha1:
        assert digest(source, 'sha1') == expected_sha1, str(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    assert not target.exists(), str(target)
    # Copy-on-write where supported, never hardlink back into user-owned caches.
    subprocess.run(['cp', '--reflink=auto', '--', str(source), str(target)], check=True)
    if expected_sha1:
        assert digest(target, 'sha1') == expected_sha1, str(target)
    else:
        assert digest(target) == digest(source), str(target)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('profile', choices=('1.7.10-forge', '1.20.1-quilt', '1.21.1-neoforge', '26.2-fabric'))
    parser.add_argument('--inventory', type=Path, required=True)
    parser.add_argument('--public-root', type=Path, required=True)
    parser.add_argument('--cache-dir', type=Path, required=True)
    parser.add_argument('--destination', type=Path, required=True, help='New runtime directory under repository build')
    parser.add_argument('--bootstrap', type=Path, required=True, help='Installed PrismLauncher NewLaunch.jar')
    args = parser.parse_args()
    if sys.flags.optimize:
        parser.error('Validation requires Python without optimization')
    public = args.public_root.resolve()
    cache = args.cache_dir.resolve()
    # This staging step performs substantial file I/O. Never contaminate live
    # physical-audio acceptance with it, including any supplementary case.
    modules = subprocess.check_output(['pactl', 'list', 'short', 'modules'], text=True)
    assert 'cinemarr_' not in modules, 'Live task audio resources remain'
    inventory_path = args.inventory.resolve()
    inventory = json.loads(inventory_path.read_text())
    assert inventory['profile'] == args.profile
    assert inventory['allLibraryInputsVerified'] and inventory['assetIndexVerified']
    for metadata in inventory['metadata']:
        source = Path(metadata['path'])
        assert source.is_relative_to(public / 'meta')
        assert digest(source) == metadata['sha256'], str(source)
    destination = args.destination.resolve()
    assert destination.is_relative_to(ROOT / 'build') and destination != ROOT / 'build'
    assert not destination.exists(), 'Preserve previously staged runtime'
    destination.mkdir(parents=True)
    classpath, natives, copied = [], [], {}
    for entry in inventory['libraries']:
        assert entry['verified']
        rel = Path(entry['relativePath'])
        assert not rel.is_absolute() and '..' not in rel.parts
        source = Path(entry.get('sourcePath', str(public / 'libraries' / rel)))
        assert source in (public / 'libraries' / rel, cache / rel)
        target = destination / 'libraries' / rel
        if str(rel) in copied:
            assert copied[str(rel)] == entry['expectedSha1']
        else:
            copy_verified(source, target, entry['expectedSha1'])
            copied[str(rel)] = entry['expectedSha1']
        if entry['role'] in ('classpath', 'minecraft'):
            classpath.append(str(target))
        elif entry['role'] == 'native-extract':
            natives.append(target)
    native_root = destination / 'natives'
    native_root.mkdir()
    for archive in natives:
        with zipfile.ZipFile(archive) as jar:
            for entry in jar.infolist():
                rel = Path(entry.filename)
                assert not rel.is_absolute() and '..' not in rel.parts
                if entry.is_dir() or entry.filename.startswith('META-INF/'):
                    continue
                target = native_root / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                raw = jar.read(entry)
                if target.exists():
                    assert target.read_bytes() == raw, 'Conflicting native ' + str(rel)
                else:
                    target.write_bytes(raw)
    index = inventory['launchMetadata']['assetIndex']
    index_source = public / 'assets/indexes' / (index['id'] + '.json')
    copy_verified(index_source, destination / 'assets/indexes' / index_source.name, index['sha1'])
    objects = json.loads(index_source.read_text())['objects']
    assert not json.loads(index_source.read_text()).get('virtual', False), 'Legacy virtual assets need explicit mapping'
    asset_hashes = set()
    for asset in objects.values():
        value = asset['hash']
        assert len(value) == 40 and all(char in '0123456789abcdef' for char in value)
        if value not in asset_hashes:
            rel = Path(value[:2]) / value
            copy_verified(public / 'assets/objects' / rel, destination / 'assets/objects' / rel, value)
            asset_hashes.add(value)
    bootstrap = destination / 'NewLaunch.jar'
    copy_verified(args.bootstrap.resolve(), bootstrap)
    logging_argument = None
    logging = inventory['launchMetadata'].get('logging')
    if logging:
        spec = logging['file']
        assert urlparse(spec['url']).scheme == 'https'
        assert urlparse(spec['url']).hostname in {'launcher.mojang.com', 'piston-data.mojang.com'}
        with urlopen(Request(spec['url'], headers={'User-Agent': 'Cinemarr-release-verification/1.0'}), timeout=20) as response:
            raw = response.read(1024 * 1024)
        assert len(raw) == spec['size'] and hashlib.sha1(raw).hexdigest() == spec['sha1']
        target = destination / 'logging.xml'
        target.write_bytes(raw)
        logging_argument = logging['argument'].replace('${path}', str(target))
    report = {'scope': 'Prepared production loader inputs only; no client launch or runtime acceptance yet',
              'profile': args.profile, 'inventoryPath': str(inventory_path),
              'inventorySha256': digest(inventory_path), 'launchMetadata': inventory['launchMetadata'],
              'classpath': [str(bootstrap)] + list(dict.fromkeys(classpath)),
              'librarySha1ByRelativePath': copied, 'nativeDirectory': str(native_root),
              'assetRoot': str(destination / 'assets'), 'assetObjects': len(asset_hashes),
              'loggingArgument': logging_argument,
              'bootstrapSha256': digest(bootstrap)}
    (destination / 'runtime.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'profile': args.profile, 'staged': True, 'launched': False,
                      'libraries': len(copied), 'assets': len(asset_hashes)}))


if __name__ == '__main__':
    main()
