#!/usr/bin/env python3
"""Overload evidence rejects stale/shared-pool/unsaturated and unbounded results."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('pressure',Path(__file__).with_name('observe-video-browse-pressure.py'))
probe=importlib.util.module_from_spec(spec); spec.loader.exec_module(probe)


def diagnostic(**changes):
    row=dict(workQueued=0,workActive=0,workRejected=0,browseQueued=16,browseActive=1,browseRejected=50)
    row.update(changes)
    return '; '.join(key+'='+str(value) for key,value in row.items())+'\n'


class PressureTests(unittest.TestCase):
    def test_actual_independent_saturation_is_required(self):
        self.assertEqual(3,len(probe.check_pressure_diagnostics(diagnostic()*3)))
        for text in ('',diagnostic()*2,diagnostic(browseQueued=0)*3,diagnostic(browseRejected=0)*3,
                     'workQueued=64; workActive=2; workRejected=100\n'*3):
            with self.assertRaises(RuntimeError): probe.check_pressure_diagnostics(text)

    def test_every_queue_and_active_worker_bound_is_checked(self):
        for key,value in [('workQueued',65),('workActive',3),('browseQueued',17),('browseActive',2),('workRejected',1)]:
            with self.assertRaises(RuntimeError): probe.check_pressure_diagnostics(diagnostic()*2+diagnostic(**{key:value}))

    def test_receipts_are_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            observer=probe.terminal.Observer.__new__(probe.terminal.Observer); observer.output=Path(directory)
            path=observer.output/'prepare.json'; path.write_text('old failure')
            with self.assertRaises(RuntimeError): probe.run(observer,'prepare',Path(directory)/'server.log')
            self.assertEqual('old failure',path.read_text())

    def test_legacy_capture_preparation_clears_debug_without_toggling_keys(self):
        path=Path(__file__).resolve().parents[1]/'platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/client/LegacyClientState.java'
        body=path.read_text().split('private void inspectAcceptanceVideoControl()',1)[1]
        self.assertLess(body.index('if (!ProtocolLimits.videoProbeEnabled()) return;'),body.index('showDebugInfo = false'))
        self.assertLess(body.index('showDebugInfo = false'),body.index('displayGuiScreen('))

    def test_all_manager_boundaries_route_browse_to_the_isolated_pool(self):
        root=Path(__file__).resolve().parents[1]
        paths=['src/main/java/stonytark/cinemarr/server/ServerVideoManager.java',
               'platforms/mc26/common/src/main/java/stonytark/cinemarr/server/ServerVideoManager.java',
               'platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/server/LegacyVideoManager.java']
        for path in paths:
            source=(root/path).read_text(); body=source.split('public void browse(',1)[1].split('public void command(',1)[0]
            self.assertIn('workers.browse(',body); self.assertNotIn('workers.supply(',body)
            self.assertIn('workers.browseDiagnostics()',source); self.assertIn('workers.close()',source)

    def test_harness_keeps_hold_beyond_prefetch_and_measures_during_and_after(self):
        source=Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        body=source.split('run_video_pressure_scenarios() {',1)[1].split('\n}\n',1)[0]
        self.assertIn('browse-held',body); self.assertIn('wait "$pressure_pid"',body)
        self.assertLess(body.index('during-leader-capture'),body.index('wait "$pressure_pid"'))
        self.assertLess(body.index("printf 'online"),body.index('(( result == 0 ))'))
        self.assertIn('recovered-leader-capture',body); self.assertIn('verify_video_health_reports',body)


if __name__=='__main__': unittest.main()
