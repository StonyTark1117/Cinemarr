#!/usr/bin/env python3
"""Reject weak segment-pressure evidence and protect the ordinary viewer oracle."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('segment',Path(__file__).with_name('observe-video-segment-pressure.py'))
probe=importlib.util.module_from_spec(spec); spec.loader.exec_module(probe)


def samples(**changes):
    lines=[]
    for n in range(41):
        row=dict(ready='true',running='true' if n<40 else 'false',finished='false' if n<40 else 'true',
                 elapsedMs=n*1000,requests=n*100,chunks=n*24,bytes=n*100000,acknowledgements=n*3,
                 rateRejections=n*60,windowRejections=n*30,unexpectedErrors=0)
        if n==40: row.update(changes)
        lines.append('Acceptance segment peer: '+' '.join(k+'='+str(v) for k,v in row.items()))
    return '\n'.join(lines)


def diagnostics(**changes):
    row=dict(trackingClients=3,workQueued=0,workActive=0,workRejected=0,transferGrants=1,
             egressItems=8,egressBytes=200000,egressRejected=0,browseRateClients=2,segmentRateClients=2,
             orphanedTransferGrants=0,orphanedEgressItems=0,orphanedEgressBytes=0)
    row.update(changes)
    return '; '.join(k+'='+str(v) for k,v in row.items())+'\n'


class SegmentTests(unittest.TestCase):
    def test_all_managers_release_untracked_windows_before_publishing_returned_screens(self):
        root=Path(__file__).resolve().parents[1]
        for path in ('src/main/java/stonytark/cinemarr/server/ServerVideoManager.java',
                     'platforms/mc26/common/src/main/java/stonytark/cinemarr/server/ServerVideoManager.java',
                     'platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/server/LegacyVideoManager.java'):
            with self.subTest(path=path):
                source=(root/path).read_text()
                tracking=source.split('private void refreshTracking(',1)[1].split('\n    }',1)[0]
                self.assertIn('trackedScreenSessions.put(television.id(), state.id())', tracking)
                cleanup='if (transferGrants.releaseUntracked(playerId, trackedScreenSessions, previousTvs.keySet())) egress.remove(playerId);'
                self.assertIn(cleanup, tracking)
                self.assertLess(tracking.index(cleanup),tracking.index('sendCurrent(player,'))

    def test_requires_sustained_actual_network_traffic_and_both_rejection_paths(self):
        self.assertEqual(4000,probe.check_peer(samples())['requests'])
        for change in (dict(requests=1999),dict(requests=4001),dict(chunks=49),dict(bytes=999999),
                       dict(acknowledgements=49),dict(rateRejections=0),dict(windowRejections=0),
                       dict(finished='false'),dict(running='true'),dict(ready='false'),dict(elapsedMs=39999)):
            with self.assertRaises(RuntimeError): probe.check_peer(samples(**change))
        with self.assertRaises(RuntimeError): probe.check_peer(samples().splitlines()[-1])

    def test_prior_errors_cannot_be_hidden_by_later_zero_counter(self):
        text=samples().replace('unexpectedErrors=0','unexpectedErrors=1',1)
        with self.assertRaises(RuntimeError): probe.check_peer(text)

    def test_network_only_peer_cannot_satisfy_checks_with_real_playback(self):
        for extra in ('Acceptance video rendered: television=1 frameSha256='+'a'*64+' ptsUs=1 rectangles=1',
                      'Acceptance video audio timeline:', 'Acceptance legacy video audio timeline:'):
            with self.assertRaises(RuntimeError): probe.check_peer(samples()+'\n'+extra)

    def test_every_server_bound_and_real_third_client_are_required(self):
        self.assertEqual(3,len(probe.check_diagnostics(diagnostics()*3)))
        for key,value in [('trackingClients',2),('transferGrants',4),('workQueued',65),('workActive',3),
                          ('workRejected',1),('egressItems',257),('egressBytes',8*1024*1024+1),('egressRejected',1),
                          ('browseRateClients',4),('segmentRateClients',4)]:
            with self.assertRaises(RuntimeError): probe.check_diagnostics(diagnostics()*2+diagnostics(**{key:value}))
        with self.assertRaises(RuntimeError): probe.check_diagnostics(diagnostics()*2)

    def test_recovery_requires_two_remaining_viewers_and_departed_ownership_drained(self):
        quiet=diagnostics(trackingClients=2,transferGrants=0,egressItems=0,egressBytes=0)
        self.assertEqual(3,len(probe.check_diagnostics(quiet*3,recovered=True)))
        self.assertEqual(3,len(probe.check_diagnostics(diagnostics(trackingClients=2)*3,recovered=True)))
        for value in (diagnostics()*3,):
            with self.assertRaises(RuntimeError): probe.check_diagnostics(value,recovered=True)
        for key in ('orphanedTransferGrants','orphanedEgressItems','orphanedEgressBytes'):
            for recovered in (False, True):
                text=diagnostics(trackingClients=2 if recovered else 3, **{key:1})
                with self.assertRaises(RuntimeError): probe.check_diagnostics(text*3,recovered=recovered)
            with self.assertRaises(RuntimeError):
                probe.check_diagnostics(quiet.replace('; '+key+'=0','')*3,recovered=True)

    def test_delayed_pre_departure_diagnostic_cannot_contaminate_recovery(self):
        before=diagnostics()*4
        after=diagnostics(trackingClients=2,transferGrants=0,egressItems=0,egressBytes=0)*5
        cursor=dict(serverOffset=len(before))
        self.assertEqual(5,len(probe.recovered_diagnostics(before+after,cursor)))
        with self.assertRaises(RuntimeError): probe.recovered_diagnostics(before+after+diagnostics(),cursor)
        source=Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        body=source.split('run_video_segment_pressure_scenarios() {',1)[1].split('\n}\n',1)[0]
        self.assertLess(body.index('finish_client_launch "$peer_pid"'),body.index('"${observer[@]}" departed'))
        self.assertLess(body.index('"${observer[@]}" departed'),body.index('for iteration in 1 2 3 4 5'))

    def test_evidence_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            observer=probe.terminal.Observer.__new__(probe.terminal.Observer); observer.output=Path(directory)
            report=observer.output/'prepare.json'; report.write_text('old failure')
            with self.assertRaises(RuntimeError): probe.run(observer,'prepare',None,None,1)
            self.assertEqual('old failure',report.read_text())

    def test_all_managers_release_rate_limits_at_disconnect_and_close(self):
        root=Path(__file__).resolve().parents[1]
        for path in ('src/main/java/stonytark/cinemarr/server/ServerVideoManager.java',
                     'platforms/mc26/common/src/main/java/stonytark/cinemarr/server/ServerVideoManager.java',
                     'platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/server/LegacyVideoManager.java'):
            source=(root/path).read_text()
            left=source.split('public void playerLeft(',1)[1].split('\n    }',1)[0]
            closed=source.split('@Override public void close()',1)[1]
            for limiter in ('browseLimiter','segmentLimiter'):
                self.assertIn(limiter+'.remove(id)',left)
                self.assertIn(limiter+'.clear()',closed)
                self.assertIn(limiter+'.trackedSubjects()',source)
            self.assertIn('transferGrants.countOutside(connected)',source)
            self.assertIn('egress.backlogItemsOutside(connected)',source)
            self.assertIn('egress.backlogBytesOutside(connected)',source)
            self.assertIn('transferOwnershipDiagnostics()',source.split('public String diagnostics()',1)[1])
            self.assertIn('player : players()' if 'Legacy' in path else 'player : server.getPlayerList().getPlayers()',source)

    def test_peer_does_not_weaken_ordinary_viewer_underrun_check(self):
        source=Path(__file__).with_name('run-dedicated-server-gate.sh').read_text()
        body=source.split('verify_video_health_reports() {',1)[1].split('\n}\n',1)[0]
        self.assertIn('"${video_pressure_gate:-false}" == true && "$log" == "$output_root/$label.audio-peer.console.log"',body)
        self.assertIn('--check-peer-log "$log" || return 1',body)
        self.assertIn('check-video-underruns.py" "$log" || return 1',body)
        body=source.split('run_video_segment_pressure_scenarios() {',1)[1].split('\n}\n',1)[0]
        self.assertIn('peer CinemarrVideoC "$sink_peer"',body)
        self.assertLess(body.index('during-leader-capture'),body.index('wait "$pressure_pid"'))
        self.assertIn('during-peer.s16le',body)
        self.assertIn('finish_client_launch "$peer_pid"',body)
        self.assertIn('recovered-leader-capture',body)


if __name__=='__main__': unittest.main()
