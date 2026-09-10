"""Launch an exact candidate mod through installed production loader components.

Task-owned offline test players and private X only. No launcher-account files,
Gradle runClient, development remapping flags, or class-directory fallbacks.
Production startup is not itself evidence of playback or successful teardown.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tomllib
import uuid

ROOT = Path(__file__).resolve().parents[1]


def client_environment():
    environment = dict(os.environ)
    for name in ('CINEMARR_PLEX_TOKEN', 'CINEMARR_PLEX_URL', 'DISCOPANEL_TOKEN', 'DISCOPANEL_API_BASE'):
        environment.pop(name, None)
    return environment


def digest(path, algorithm='sha256'):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def install_mod(source, target, expected):
    assert source.is_file() and not source.is_symlink()
    assert digest(source) == expected
    if target.exists():
        assert not target.is_symlink() and digest(target) == expected
    else:
        shutil.copyfile(source, target)
    assert digest(target) == expected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('profile')
    parser.add_argument('--game-dir', type=Path, required=True)
    parser.add_argument('--username', choices=('CinemarrVideoA', 'CinemarrVideoB', 'CinemarrRecovery'), required=True)
    parser.add_argument('--server', required=True)
    parser.add_argument('--expected-server-host', required=True, help='Explicitly authorized acceptance host; must match --server')
    parser.add_argument('--runtime-root', type=Path, default=os.environ.get('CINEMARR_PACKAGED_CLIENT_RUNTIME_ROOT'),
                        help='Parent containing prepared profile/runtime.json; alternatively CINEMARR_PACKAGED_CLIENT_RUNTIME_ROOT')
    parser.add_argument('--java-home', type=Path, help='Otherwise CINEMARR_PACKAGED_JAVA<runtimeJava>_HOME or system OpenJDK')
    parser.add_argument('--check-only', action='store_true', help='Validate inputs without writing game files or opening X')
    parser.add_argument('--bundle-dir', type=Path, default=ROOT / 'build/releases')
    parser.add_argument('--gradle-cache', type=Path, default=Path.home() / '.gradle/caches')
    parser.add_argument('--width', type=int, default=640)
    parser.add_argument('--height', type=int, default=480)
    args = parser.parse_args()
    if sys.flags.optimize:
        parser.error('Validation requires Python without optimization')
    if args.runtime_root is None:
        parser.error('A prepared production runtime root is required; no development fallback')
    assert re.fullmatch(r'[A-Za-z0-9.-]+', args.expected_server_host)
    match = re.fullmatch(r'([A-Za-z0-9.-]+):([0-9]{1,5})', args.server)
    assert match and match[1] == args.expected_server_host, 'Unexpected acceptance host'
    assert match and 1 <= int(match[2]) <= 65535, 'Only owned offline acceptance servers'
    assert 640 <= args.width <= 1920 and 480 <= args.height <= 1080
    game = args.game_dir.resolve()
    assert game.is_relative_to(ROOT / 'build') and game != ROOT / 'build'
    assert '.audio-' in game.name or game.parent.name == 'packaged-client-focus'
    for option in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
        value = os.environ.get(option, '')
        assert not any(marker in value for marker in ('loader.development', 'fabric.development',
                       'remapClasspath', 'dev-launch-injector', 'fabric.dli', 'classPathGroups'))
    targets = json.loads((ROOT / 'gradle/targets.json').read_text())['artifacts']
    matches = [target for target in targets if args.profile in
               (target['runtime']['name'], target.get('quiltRuntime', {}).get('name'))]
    assert len(matches) == 1
    target = matches[0]
    runtime_root = args.runtime_root.resolve() / args.profile
    assert runtime_root.is_relative_to(ROOT / 'build')
    runtime_file = runtime_root / 'runtime.json'
    runtime = json.loads(runtime_file.read_text())
    assert runtime['profile'] == args.profile
    inventory_file = Path(runtime['inventoryPath'])
    assert inventory_file.resolve().is_relative_to(ROOT / 'build')
    assert digest(inventory_file) == runtime['inventorySha256']
    inventory = json.loads(inventory_file.read_text())
    assert inventory['profile'] == args.profile and inventory['allLibraryInputsVerified'] and inventory['assetIndexVerified']
    assert runtime['launchMetadata'] == inventory['launchMetadata'], 'Launch metadata changed after inventory'
    for rel, expected in runtime['librarySha1ByRelativePath'].items():
        path = runtime_root / 'libraries' / rel
        assert path.resolve().is_relative_to(runtime_root) and digest(path, 'sha1') == expected
    bootstrap = runtime_root / 'NewLaunch.jar'
    assert digest(bootstrap) == runtime['bootstrapSha256']
    verified_classpath = {str(runtime_root / 'libraries' / rel)
                          for rel in runtime['librarySha1ByRelativePath']} | {str(bootstrap)}
    assert runtime['classpath'] and len(runtime['classpath']) == len(set(runtime['classpath']))
    for entry in runtime['classpath']:
        path = Path(entry)
        assert entry in verified_classpath, 'Classpath contains an unverified library'
        assert path.is_file() and path.suffix == '.jar' and path.resolve().is_relative_to(runtime_root)
        assert not any(s in entry for s in ('dev-launch-injector', '/classes/', '/remapped_mods/'))
    assert Path(runtime['nativeDirectory']).resolve() == runtime_root / 'natives'
    assert Path(runtime['assetRoot']).resolve() == runtime_root / 'assets'
    bundle = args.bundle_dir.resolve()
    candidate = bundle / target['artifact']
    checksums = {}
    for line in (bundle / 'SHA256SUMS').read_text().splitlines():
        checksum, name = line.split(maxsplit=1)
        name = name.lstrip('*')
        assert re.fullmatch(r'[0-9a-f]{64}', checksum) and Path(name).name == name
        assert name not in checksums, 'Duplicate bundle hash entry'
        checksums[name] = checksum
    expected = checksums[target['artifact']]
    mods = game / 'mods'
    assert mods.resolve().is_relative_to(game), 'Mods directory must stay in the owned game directory'
    existing = list(mods.glob('cinemarr-*.jar'))
    assert not existing or existing == [mods / target['artifact']], 'Unexpected previous Cinemarr artifact'
    planned_mods = [(candidate, mods / target['artifact'], expected)]
    if target['loader'] == 'fabric':
        catalog = tomllib.loads((ROOT / 'platforms' / ('mc' + target['minecraft']) / 'gradle/libs.versions.toml').read_text())
        version = catalog['versions']['fabric-api']
        cache = args.gradle_cache.resolve() / 'modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api' / version
        matches = list(cache.glob('*/fabric-api-' + version + '.jar'))
        assert len(matches) == 1, 'Require the pinned unremapped Fabric API artifact'
        planned_mods.append((matches[0], mods / matches[0].name, digest(matches[0])))
    assert not list(mods.glob('*-dev.jar')), 'Production runs must not substitute development Jammarr'
    for source, destination, checksum in planned_mods:
        assert source.is_file() and not source.is_symlink() and digest(source) == checksum
        if destination.exists():
            assert not destination.is_symlink() and digest(destination) == checksum, 'Conflicting installed mod'
    metadata = runtime['launchMetadata']
    offline_uuid = uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + args.username).encode()).digest(), version=3)
    values = {'auth_player_name': args.username, 'version_name': target['minecraft'],
              'game_directory': str(game), 'assets_root': runtime['assetRoot'],
              'assets_index_name': metadata['assetIndex']['id'], 'auth_uuid': offline_uuid.hex,
              'auth_access_token': '0', 'auth_session': '0', 'user_properties': '{}',
              'user_type': 'legacy', 'version_type': 'release'}
    params = []
    for param in shlex.split(metadata['minecraftArguments']):
        param = re.sub(r'\$\{([^}]+)\}', lambda m: values[m[1]], param)
        assert '\n' not in param and '\r' not in param
        params.append(param)
    for tweaker in metadata.get('+tweakers', []):
        params.extend(['--tweakClass', tweaker])
    launch = ['mainClass ' + metadata['mainClass'], 'windowParams ' + str(args.width) + 'x' + str(args.height),
              'serverAddress ' + match[1], 'serverPort ' + match[2],
              'launcherBrand CinemarrAcceptance', 'launcherVersion 1.0']
    launch += ['param ' + param for param in params]
    launch += ['traits ' + trait for trait in metadata.get('+traits', [])]
    launch += ['launcher standard', 'launch', '']
    java_home = args.java_home or Path(os.environ.get('CINEMARR_PACKAGED_JAVA' + str(target['runtimeJava']) + '_HOME',
                                                     '/usr/lib/jvm/java-' + str(target['runtimeJava']) + '-openjdk'))
    java = str(java_home.resolve() / 'bin/java')
    environment = client_environment()
    java_version = subprocess.check_output([java, '-version'], stderr=subprocess.STDOUT, text=True, env=environment)
    assert re.search(r'version "(?:1\.)?' + str(target['runtimeJava']) + r'(?:[.\-"+])', java_version)
    options = ['-Xms256m', '-Xmx1536m', '-Djava.library.path=' + runtime['nativeDirectory'],
               '-Dorg.lwjgl.librarypath=' + runtime['nativeDirectory'], '-Dlog4j2.formatMsgNoLookups=true']
    if runtime.get('loggingArgument'):
        options.append(runtime['loggingArgument'])
    if 'forgewrapper' in metadata['mainClass']:
        options.append('-Dforgewrapper.librariesDir=' + str(runtime_root / 'libraries'))
    command = [java] + options + ['-cp', ':'.join(runtime['classpath']), 'org.prismlauncher.EntryPoint']
    if args.check_only:
        print(json.dumps({'profile': args.profile, 'preflightPassed': True, 'launched': False,
                          'candidateSha256': expected, 'runtimeJava': target['runtimeJava']}))
        return 0
    game.mkdir(parents=True, exist_ok=True)
    mods.mkdir(exist_ok=True)
    for source, destination, checksum in planned_mods:
        install_mod(source, destination, checksum)
    records = game / 'packaged-launch-records'
    records.mkdir(exist_ok=True)
    record = records / ('launch-' + uuid.uuid4().hex + '.json')
    record.write_text(json.dumps({'scope': 'Production launch recipe; launch success and runtime acceptance are separate',
        'profile': args.profile, 'runtimeManifestSha256': digest(runtime_file), 'candidateSha256': expected,
        'classpath': runtime['classpath'], 'java': java, 'modsSha256': {p.name: digest(p) for p in mods.glob('*.jar')}}, indent=2) + '\n')
    print('Packaged Cinemarr client recipe: ' + args.profile + ' candidateSha256=' + expected, flush=True)
    private = ['bash', str(ROOT / 'scripts/run-private-xvfb.sh'),
               str(args.width) + 'x' + str(args.height) + 'x24']
    launch_input = record.with_suffix('.stdin')
    launch_input.write_text('\n'.join(launch))
    # The X wrapper backgrounds its child group; open the input inside that
    # child rather than relying on inherited stdin through a background shell.
    feeder = ['bash', '-c', 'exec "${@:2}" < "$1"', 'cinemarr-packaged-input', str(launch_input)]
    result = subprocess.run(private + feeder + command, cwd=game, env=environment)
    assert digest(candidate) == expected and digest(mods / candidate.name) == expected
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
