#!/usr/bin/env python3
"""Focused local-runtime checks without a Minecraft/Gradle build or network downloads."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--dependencies', type=Path, required=True,
                    help='Local directory containing pinned JavaCPP/JavaCV/FFmpeg JARs, native JARs, gson.jar and junit-console.jar')
parser.add_argument('--java8', type=Path, default=Path('/usr/lib/jvm/java-8-openjdk'))
parser.add_argument('--java21', type=Path, default=Path('/usr/lib/jvm/java-21-openjdk'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
deps = args.dependencies.resolve()
base = [deps / name for name in ('javacpp-1.5.14.jar', 'javacv-1.5.14.jar', 'ffmpeg-8.1.2-1.5.14.jar')]
for path in base + [deps / 'gson.jar', deps / 'junit-console.jar']:
    if not path.is_file():
        parser.error(f'Missing local dependency: {path}')


def cp(paths):
    return os.pathsep.join(str(path) for path in paths)


def run(command):
    subprocess.run([str(arg) for arg in command], cwd=root, check=True)


with tempfile.TemporaryDirectory(prefix='cinemarr-external-runtime-') as temp:
    work = Path(temp)
    classes = work / 'core'
    classes.mkdir()
    resources = [root / 'core/src/main/resources', root / 'src/main/resources']
    source_list = work / 'sources.txt'
    source_list.write_text('\n'.join(str(p) for p in sorted((root / 'core/src/main/java').rglob('*.java'))))
    run([args.java8 / 'bin/javac', '-cp', cp(base + [deps / 'gson.jar']), '-d', classes, '@' + str(source_list)])
    tests = [root / 'core/src/test/java/stonytark/cinemarr/core/client/runtime/LocalRuntimeResolverTest.java',
             root / 'test-fixtures/java/runtime/ExternalRuntimeSmoke.java',
             root / 'test-fixtures/java/runtime/ExternalRuntimeImportSmoke.java']
    run([args.java8 / 'bin/javac', '-cp', cp([classes, deps / 'junit-console.jar'] + base), '-d', classes] + tests)
    run([args.java8 / 'bin/java', '-jar', deps / 'junit-console.jar', 'execute', '--class-path',
         cp([classes] + resources), '--select-class',
         'stonytark.cinemarr.core.client.runtime.LocalRuntimeResolverTest', '--details', 'summary'])
    run([args.java8 / 'bin/java', '-cp', cp([classes] + resources), 'ExternalRuntimeImportSmoke', deps, work / 'imports'])
    for legacy, java in ((True, args.java8), (False, args.java21)):
        name = 'legacy' if legacy else 'modern'
        output = work / name
        output.mkdir()
        client = root / ('platforms/mc1.7.10/forge/src/main/java' if legacy else 'src/main/java') / 'stonytark/cinemarr/client'
        prefix = 'Legacy' if legacy else ''
        source_names = [prefix + item + '.java' for item in ('DecodedAudioFrame', 'DecodedVideoFrame', 'DecodedMediaSegment', 'MediaSegmentDecoder', 'FfmpegVideoDecoder')]
        sources = [client / filename for filename in source_names]
        sources += [root / 'src/main/java/stonytark/cinemarr/client/FfmpegHardwareVideoDecoder.java']
        if legacy:
            sources += [client / 'DecodedVideoFrame.java']
        run([java / 'bin/javac', '-cp', cp([classes] + base), '-d', output] + sources)
        run([java / 'bin/java', '-Duser.home=' + str(work / 'empty-home'),
             '-Djava.library.path=' + str(work / 'empty-library-path'),
             '-Dorg.bytedeco.javacpp.cachedir=' + str(work / ('empty-javacpp-cache-' + name)),
             '-cp', cp([classes, output] + resources + base), 'ExternalRuntimeSmoke',
             work / ('game-' + name), deps, str(legacy).lower()])
        run([java / 'bin/java', '-Duser.home=' + str(work / 'empty-home'),
             '-Djava.library.path=' + str(work / 'empty-library-path'),
             '-Dorg.bytedeco.javacpp.cachedir=' + str(work / ('empty-javacpp-cache-' + name)),
             '-cp', cp([classes, output] + resources + base), 'ExternalRuntimeSmoke',
             work / ('game-' + name), deps, str(legacy).lower(), 'cached'])
        run([java / 'bin/java', '-Duser.home=' + str(work / 'empty-home'),
             '-Djava.library.path=' + str(work / 'empty-library-path'),
             '-Dorg.bytedeco.javacpp.cachedir=' + str(work / ('empty-javacpp-cache-' + name)),
             '-cp', cp([classes, output] + resources + base), 'ExternalRuntimeSmoke',
             work / ('game-' + name), deps, str(legacy).lower(), 'conflict'])
        test = root / ('platforms/mc1.7.10/forge/src/test/java' if legacy else 'src/test/java') / 'stonytark/cinemarr/client' / (prefix + 'FfmpegVideoDecoderTest.java')
        run([java / 'bin/javac', '-cp', cp([classes, output, deps / 'junit-console.jar'] + base), '-d', output, test])
        run([java / 'bin/java', '-Dcinemarr.test.runtimeGameDirectory=' + str(work / ('game-' + name)),
             '-jar', deps / 'junit-console.jar', 'execute', '--class-path', cp([classes, output] + resources + base),
             '--select-class', 'stonytark.cinemarr.client.' + prefix + 'FfmpegVideoDecoderTest', '--details', 'summary'])
