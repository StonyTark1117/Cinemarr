"""Read only public installed launcher metadata/libraries, never account stores.

This is preparation evidence, not a runtime pass. Staging/launching is deliberately
separate so missing inputs cannot silently fall back to development classpaths.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
from urllib.request import Request, urlopen
from urllib.parse import urlparse
from urllib.error import HTTPError

ROOT = Path(__file__).resolve().parents[1]
PUBLIC = None
TASK_CACHE = None
PROFILES = {
    '1.7.10-forge': [('net.minecraft', '1.7.10'), ('org.lwjgl', '2.9.4-nightly-20150209'),
                     ('net.minecraftforge', '10.13.4.1614')],
    '1.20.1-quilt': [('net.minecraft', '1.20.1'), ('org.lwjgl3', '3.3.1'),
                     ('net.fabricmc.intermediary', '1.20.1'), ('org.quiltmc.quilt-loader', '0.30.0')],
    '1.21.1-neoforge': [('net.minecraft', '1.21.1'), ('org.lwjgl3', '3.3.3'),
                        ('net.neoforged', '21.1.248')],
    '26.2-fabric': [('net.minecraft', '26.2'), ('org.lwjgl3', '3.4.1'),
                    ('net.fabricmc.intermediary', '26.2'), ('net.fabricmc.fabric-loader', '0.19.3')],
}


def public_request(url):
    return Request(url, headers={'User-Agent': 'Cinemarr-release-verification/1.0'})


def maven_path(name, classifier=None):
    parts = name.split(':')
    assert len(parts) in (3, 4), name
    group, artifact, version = parts[:3]
    classifier = classifier or (parts[3] if len(parts) == 4 else None)
    for part in parts:
        assert re.fullmatch(r'[A-Za-z0-9_.+\-]+', part), name
    return '/'.join([group.replace('.', '/'), artifact, version,
                     artifact + '-' + version + ('-' + classifier if classifier else '') + '.jar'])


def allowed(lib):
    rules = lib.get('rules')
    if rules is None:
        return True
    result = False
    for rule in rules:
        assert set(rule) <= {'action', 'os'}, 'Unsupported library rule: ' + str(rule)
        os_rule = rule.get('os', {})
        assert set(os_rule) <= {'name', 'arch', 'version'}, os_rule
        if os_rule.get('name', 'linux') != 'linux':
            continue
        if os_rule.get('arch', 'x86_64') not in ('x86_64', 'amd64'):
            continue
        assert 'version' not in os_rule, 'Explicit OS-version rules need evaluation'
        assert rule['action'] in ('allow', 'disallow')
        result = rule['action'] == 'allow'
    return result


def profile_inventory(profile, verify_upstream=False, fetch_missing=False):
    metadata = []
    for uid, version in PROFILES[profile]:
        path = PUBLIC / 'meta' / uid / (version + '.json')
        assert not path.is_symlink()
        raw = path.read_bytes()
        data = json.loads(raw)
        assert data['uid'] == uid and data['version'] == version
        assert data['formatVersion'] == 1
        metadata.append((data, {'path': str(path), 'sha256': hashlib.sha256(raw).hexdigest()}))
    metadata.sort(key=lambda pair: pair[0]['order'])
    libraries, install_files, merged = {}, {}, {}
    for data, _ in metadata:
        for key in ('mainClass', 'mainJar', 'minecraftArguments', 'assetIndex', 'logging', 'compatibleJavaMajors'):
            if key in data:
                merged[key] = data[key]
        for key in ('+tweakers', '+traits'):
            merged.setdefault(key, []).extend(data.get(key, []))
        for field, target in [('libraries', libraries), ('mavenFiles', install_files)]:
            for lib in data.get(field, []):
                if allowed(lib):
                    parts = lib['name'].split(':')
                    identity = tuple(parts[:2] + parts[3:])
                    target[identity] = lib
    inputs = []
    for role, libs in [('classpath', libraries.values()), ('installer', install_files.values()),
                       ('minecraft', [merged['mainJar']])]:
        for lib in libs:
            downloads = lib.get('downloads', {})
            choices = []
            if not downloads:
                rel = maven_path(lib['name'])
                choices.append((role, {'path': rel, 'url': lib.get('url', 'https://libraries.minecraft.net/').rstrip('/') + '/' + rel}, None))
            if 'artifact' in downloads:
                choices.append((role, downloads['artifact'], None))
            if 'natives' in lib and 'linux' in lib['natives']:
                classifier = lib['natives']['linux'].replace('${arch}', '64')
                choices.append(('native-extract', downloads['classifiers'][classifier], classifier))
            assert choices, 'No Linux payload for ' + lib['name']
            for payload_role, payload, classifier in choices:
                rel = payload.get('path') or maven_path(lib['name'], classifier)
                assert not Path(rel).is_absolute() and '..' not in Path(rel).parts
                path = PUBLIC / 'libraries' / rel
                assert not path.is_symlink(), str(path)
                sha1 = payload.get('sha1')
                provenance = 'launcher-metadata' if sha1 else 'missing-upstream-checksum'
                if not sha1 and verify_upstream:
                    url = payload['url'] + '.sha1'
                    assert urlparse(url).scheme == 'https'
                    assert urlparse(url).hostname in {'libraries.minecraft.net', 'maven.minecraftforge.net',
                        'maven.fabricmc.net', 'maven.quiltmc.org'}, url
                    try:
                        with urlopen(public_request(url), timeout=20) as response:
                            checksum = response.read(256).decode().strip().split()[0]
                        assert re.fullmatch(r'[0-9a-fA-F]{40}', checksum), url
                        sha1 = checksum.lower()
                        provenance = url
                    except HTTPError as error:
                        if error.code not in (403, 404):
                            raise
                        # Old Mojang Maven entries may have no checksum sidecar.
                        # Compare with the actual public artifact instead, without
                        # modifying or replacing the user's cached library.
                        artifact_url = payload['url']
                        try:
                            response = urlopen(public_request(artifact_url), timeout=20)
                        except HTTPError as artifact_error:
                            if artifact_error.code not in (403, 404) or urlparse(artifact_url).hostname != 'libraries.minecraft.net':
                                raise RuntimeError('Public artifact unavailable: ' + artifact_url) from artifact_error
                            artifact_url = 'https://maven.minecraftforge.net/' + rel
                            response = urlopen(public_request(artifact_url), timeout=20)
                        with response:
                            raw_artifact = response.read(100 * 1024 * 1024 + 1)
                        assert len(raw_artifact) <= 100 * 1024 * 1024, artifact_url
                        assert raw_artifact.startswith(b'PK'), artifact_url
                        sha1 = hashlib.sha1(raw_artifact).hexdigest()
                        provenance = 'artifact-body:' + artifact_url
                if not path.is_file():
                    cached = TASK_CACHE / rel
                    assert not cached.is_symlink(), str(cached)
                    if not cached.exists() and fetch_missing:
                        assert sha1, 'Missing input needs a verified upstream checksum'
                        with urlopen(public_request(payload['url']), timeout=20) as response:
                            raw = response.read(100 * 1024 * 1024 + 1)
                        assert len(raw) <= 100 * 1024 * 1024
                        assert hashlib.sha1(raw).hexdigest() == sha1, payload['url']
                        if payload.get('size') is not None:
                            assert len(raw) == payload['size']
                        cached.parent.mkdir(parents=True, exist_ok=True)
                        with cached.open('xb') as stream:
                            stream.write(raw)
                    if cached.is_file():
                        path = cached
                row = {'role': payload_role, 'name': lib['name'], 'relativePath': rel,
                       'sourcePath': str(path),
                       'url': payload.get('url'), 'expectedSha1': sha1,
                       'checksumSource': provenance,
                       'expectedSize': payload.get('size'), 'exists': path.is_file()}
                if path.is_file():
                    with path.open('rb') as stream:
                        row['actualSha1'] = hashlib.file_digest(stream, 'sha1').hexdigest()
                    row['actualSize'] = path.stat().st_size
                row['verified'] = bool(sha1 and row.get('actualSha1') == sha1 and
                                       (payload.get('size') is None or row.get('actualSize') == payload['size']))
                inputs.append(row)
    index = merged['assetIndex']
    index_path = PUBLIC / 'assets/indexes' / (index['id'] + '.json')
    index_ok = index_path.is_file() and hashlib.sha1(index_path.read_bytes()).hexdigest() == index['sha1']
    return {'profile': profile, 'scope': 'Public cached launcher inputs only; not a launch or runtime pass',
            'metadata': [evidence for _, evidence in metadata], 'launchMetadata': merged,
            'libraries': inputs, 'assetIndexVerified': index_ok,
            'allLibraryInputsVerified': all(row['verified'] for row in inputs)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('profile', choices=PROFILES)
    parser.add_argument('--public-root', type=Path, required=True, help='Launcher public meta/libraries/assets root; account stores are never read')
    parser.add_argument('--cache-dir', type=Path, required=True, help='New/download cache under this repository build directory')
    parser.add_argument('--json', action='store_true')
    parser.add_argument('--verify-upstream', action='store_true')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--fetch-missing', action='store_true')
    args = parser.parse_args()
    if sys.flags.optimize:
        parser.error('Validation requires Python without optimization')
    PUBLIC = args.public_root.resolve()
    TASK_CACHE = args.cache_dir.resolve()
    assert TASK_CACHE.is_relative_to(ROOT / 'build') and TASK_CACHE != ROOT / 'build'
    result = profile_inventory(args.profile, args.verify_upstream, args.fetch_missing)
    if args.output:
        output = args.output.resolve()
        assert output.is_relative_to(ROOT / 'build') and output != ROOT / 'build'
        assert not output.exists(), 'Preserve previous inventory evidence'
        assert result['allLibraryInputsVerified'] and result['assetIndexVerified']
        output.write_text(json.dumps(result, indent=2) + '\n')
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(json.dumps({'profile': args.profile, 'libraries': len(result['libraries']),
                          'assetIndexVerified': result['assetIndexVerified'],
                          'unverified': [r for r in result['libraries'] if not r['verified']]}))
