#!/usr/bin/env python3
"""Actual shell exit-status oracle, independent of native core-file storage."""
from pathlib import Path
import os
import shlex
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ClientExitTest(unittest.TestCase):
    def test_all_client_exec_boundaries_strip_server_credentials(self):
        source = (ROOT / 'scripts/run-dedicated-server-gate.sh').read_text()
        prefixes = re.findall(r'exec setsid (env[^\n]* XDG_SESSION_TYPE=x11)', source)
        self.assertEqual(4, len(prefixes), 'Protocol, command, packaged and development launch boundaries')
        secrets = ('CINEMARR_PLEX_TOKEN', 'CINEMARR_PLEX_URL', 'DISCOPANEL_TOKEN', 'DISCOPANEL_API_BASE')
        environment = dict(os.environ, **{key: 'test-only-canary' for key in secrets},
                           PULSE_SINK='cinemarr_test_sink')
        observed = secrets + ('PULSE_SINK', 'XDG_SESSION_TYPE')
        probe = 'python3 -c ' + shlex.quote('import os,json; print(json.dumps({key:os.environ[key] for key in '
                                          + repr(observed) + ' if key in os.environ}))')
        import json
        for prefix in prefixes:
            result = subprocess.run(['bash', '-c', prefix + ' ' + probe], env=environment,
                                    capture_output=True, text=True, check=True, timeout=5)
            child = json.loads(result.stdout)
            self.assertTrue(all(key not in child for key in secrets))
            self.assertEqual('cinemarr_test_sink', child['PULSE_SINK'])
            self.assertEqual('x11', child['XDG_SESSION_TYPE'])

    def test_probe_launchers_honor_every_manifest_cache_policy(self):
        source = (ROOT / 'scripts/run-dedicated-server-gate.sh').read_text()
        rows = subprocess.check_output(['python3', 'scripts/target-matrix.py', 'gate-lines'],
                                       cwd=ROOT, text=True).splitlines()
        self.assertEqual(len(rows), 21)
        for name in ('run_acceptance_client', 'run_command_client'):
            body = re.search(r'(?ms)^' + name + r'\(\) \{\n.*?^\}', source).group()
            # Exercise the maintained argument setup and actual Gradle command,
            # replacing only Gradle itself. No GUI, server, or network is started.
            setup = body[:body.index('  configure_acceptance_loader')]
            launch = re.search(r'(?ms)^      ./gradlew .*?^      > "\$client_console" 2>&1', body)
            self.assertIsNotNone(launch)
            for row in rows:
                label, _, _, _, client_task, _, disable_cache = row.split('|')
                with self.subTest(function=name, profile=label), tempfile.TemporaryDirectory(
                        prefix='cinemarr-probe-args-') as directory:
                    root = Path(directory)
                    gradle = root / 'gradlew'
                    gradle.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n')
                    gradle.chmod(0o700)
                    shell = '''
set -uo pipefail
output_root=$1
active_disable_configuration_cache=$2
active_client_task=$4
quilt_modmenu_gate=true
fabric_loader_version=0.19.2
acceptance_server_host=127.0.0.1
'''
                    function = setup + '\n  cd "$target_dir" || return 1\n' + launch.group() + '\n}\n'
                    result = subprocess.run(['bash', '-c', shell + function
                        + '\n' + name + ' "$3" "$1" /unused-java 25500 unused-server probe User options rejection false',
                        'probe-args-test', directory, disable_cache, label, client_task],
                        capture_output=True, text=True, timeout=5)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    scenario = 'probe' if name == 'run_acceptance_client' else 'command-client'
                    args = (root / (label + '.' + scenario + '.console.log')).read_text().splitlines()
                    self.assertEqual(args[0], client_task)
                    self.assertEqual(args.count('--no-configuration-cache'), int(disable_cache == 'true'))
                    self.assertEqual(args.count('-PcinemarrRuntimeLoader=quilt'), int(label.endswith('-quilt')))
                    self.assertEqual(args.count('-PcinemarrIncludeModMenu=true'), int(label.endswith('-quilt')))
                    self.assertEqual(args.count('-PcinemarrFabricLoaderVersion=0.19.2'), int(label.endswith('-fabric')))

    def test_required_gui_paths_use_the_clean_exit_oracle(self):
        source = (ROOT / 'scripts/run-dedicated-server-gate.sh').read_text()
        for name, call in (
                ('run_acceptance_client', 'finish_client_launch "$pid" 120 "$client_console"'),
                ('run_command_client', 'finish_client_launch "$pid" 120 "$client_console"'),
                ('run_video_terminal_scenarios', 'finish_client_launch "$follower_pid" 120 "$follower_log"'),
                ('run_video_segment_pressure_scenarios', 'finish_client_launch "$peer_pid" 120 "$peer_log"'),
                ('run_two_client_video', 'finish_client_launch "$leader_pid" 120 "$leader_log"'),
                ('run_two_client_video', 'finish_client_launch "$follower_pid" 120 "$follower_log"')):
            with self.subTest(name=name, call=call):
                body = re.search(r'(?ms)^' + name + r'\(\) \{\n.*?^\}', source).group()
                self.assertIn(call, body)
        self.assertIn("scripts/test-client-exit.py", (ROOT / '.github/workflows/ci.yml').read_text())
        self.assertIn("tasks.named('verifyClientExit')", (ROOT / 'build.gradle').read_text())

    def invoke(self, code, marker):
        source = (ROOT / 'scripts/run-dedicated-server-gate.sh').read_text()
        match = re.search(r'(?ms)^finish_client_launch\(\) \{\n.*?^\}', source)
        self.assertIsNotNone(match, 'A process disappearing is not clean-exit evidence')
        with tempfile.TemporaryDirectory(prefix='cinemarr-client-exit-') as directory:
            script = '''
set -uo pipefail
repo_root=$1
log=$2
code=$3
marker=$4
group_alive() { return 1; }
# This child is already terminal when inspected: wait still has to reject
# its nonzero status, even when its earlier log contains a success marker.
bash -c 'printf "%s\\n" "$1"; exit "$2"' child "$marker" "$code" > "$log" &
pid=$!
sleep .05
'''
            return subprocess.run(['bash', '-c', script + match.group()
                                   + '\nfinish_client_launch "$pid" 1 "$log"',
                                   'exit-test', str(ROOT), str(Path(directory) / 'client.log'),
                                   str(code), marker], capture_output=True, text=True, timeout=5)

    def test_nonzero_exit_rejected_even_with_success_marker_and_no_core_file(self):
        for code in (1, 17, 134, 139, 143):
            with self.subTest(code=code):
                result = self.invoke(code, 'Private X command exited: status=0')
                self.assertNotEqual(0, result.returncode)
                self.assertIn('did not exit cleanly', result.stderr)

    def test_zero_exit_requires_exactly_one_successful_command_receipt(self):
        for marker in ('', 'Private X command exited: status=139',
                       'Private X command exited: status=0\nPrivate X command exited: status=0',
                       'Private X command exited: status=139\nPrivate X command exited: status=0'):
            with self.subTest(marker=marker):
                self.assertNotEqual(0, self.invoke(0, marker).returncode)
        self.assertEqual(0, self.invoke(0, 'Private X command exited: status=0').returncode)

    def test_zero_exit_does_not_hide_decode_failures_during_late_teardown(self):
        for failure in ('Cinemarr rejected video segment 30 audio: cancellation',
                        'Cinemarr rejected legacy video segment 30: interrupted'):
            with self.subTest(failure=failure):
                result = self.invoke(0, failure + '\nPrivate X command exited: status=0')
                self.assertNotEqual(0, result.returncode)
                self.assertIn('reported a rejected video segment', result.stderr)


if __name__ == '__main__':
    unittest.main()
