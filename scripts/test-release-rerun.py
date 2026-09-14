#!/usr/bin/env python3
"""Exercise production child-build argument configuration across cached invocations.

The fixture uses the real Gradle wrapper and the manifest-derived configuration
block from build.gradle. Its child commands only record arguments; no Minecraft
compilation, downloads, or clients are involved.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify():
    manifest_path = ROOT / 'gradle/targets.json'
    manifest = json.loads(manifest_path.read_text())
    names = []
    for target in manifest['artifacts']:
        if target['path'] == '.':
            continue
        names.append(target['task'])
        if target.get('quiltRuntime'):
            names.append(target['task'].replace('verifyFabric', 'verifyQuilt'))
    if not names or len(names) != len(set(names)):
        raise RuntimeError('Missing or ambiguous nested release gates')

    source = (ROOT / 'build.gradle').read_text()
    block = source[source.index('def releaseGateNames ='):source.index('def verifyGameTests =')]
    output = ROOT / 'build/release-rerun-tests'
    output.mkdir(parents=True, exist_ok=True)
    report = {'passed': False, 'nestedGates': names, 'runs': [],
              'buildSourceSha256': digest(ROOT / 'build.gradle'),
              'testSourceSha256': digest(Path(__file__)),
              'manifestSha256': digest(manifest_path)}
    try:
        with tempfile.TemporaryDirectory(prefix='fixture-', dir=output) as temporary:
            work = Path(temporary)
            (work / 'settings.gradle').write_text("rootProject.name = 'release-rerun-test'\n")
            # The fake child substitutes for each target's wrapper, so this
            # checks actual Exec arguments after configuration-cache handling.
            (work / 'capture.py').write_text(
                "import json,sys\nfrom pathlib import Path\n"
                "Path(sys.argv[1]+'.json').write_text(json.dumps(sys.argv[2:]))\n")
            (work / 'targets.json').write_bytes(manifest_path.read_bytes())
            registrations = '\n'.join(
                "tasks.register('" + name + "', Exec) { "
                "commandLine('python3', 'capture.py', '" + name + "') }"
                for name in names)
            (work / 'build.gradle').write_text(
                "import groovy.json.JsonSlurper\n"
                "def targetManifest = new JsonSlurper().parse(file('targets.json'))\n"
                + registrations + '\n' + block
                + "\ntasks.register('allNested') { dependsOn(isolatedGradleReleaseGates) }\n")
            for forced in (False, True):
                command = [str(ROOT / 'gradlew'), '-p', str(work), 'allNested',
                           '--configuration-cache', '--no-daemon', '--max-workers=1',
                           '--console=plain',
                           '-Dorg.gradle.jvmargs=-Xmx256m -XX:MaxMetaspaceSize=192m']
                if forced:
                    command.append('--rerun-tasks')
                log = output / ('forced.log' if forced else 'normal.log')
                with log.open('w') as stream:
                    completed = subprocess.run(command, cwd=work, env=os.environ.copy(),
                                               stdout=stream, stderr=subprocess.STDOUT, timeout=300)
                if completed.returncode:
                    raise RuntimeError('Fixture Gradle invocation failed; inspect ' + str(log))
                arguments = {name: json.loads((work / (name + '.json')).read_text()) for name in names}
                report['runs'].append({'forced': forced, 'arguments': arguments,
                                       'logSha256': digest(log)})
                expected = ['--rerun-tasks'] if forced else []
                wrong = [name for name, args in arguments.items() if args != expected]
                if wrong:
                    raise RuntimeError('Incorrect child rebuild arguments for: ' + ', '.join(wrong))
        report['passed'] = True
    finally:
        (output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Normal and forced child rebuild arguments passed for ' + str(len(names)) + ' nested gates')


if __name__ == '__main__':
    verify()
