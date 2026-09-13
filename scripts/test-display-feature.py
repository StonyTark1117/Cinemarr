#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import tempfile
import unittest
import threading
import time
import subprocess
import sys
import urllib.request
import urllib.error
import hashlib
from types import SimpleNamespace
from unittest.mock import patch
from PIL import Image, ImageDraw
from display_framebuffer import block_color, project, verify_pixel_frame
from display_server import DisplayServer

spec=importlib.util.spec_from_file_location('display',Path(__file__).with_name('observe-display-feature.py'))
probe=importlib.util.module_from_spec(spec);spec.loader.exec_module(probe)
spec=importlib.util.spec_from_file_location('persistence',Path(__file__).with_name('check-display-persistence.py'))
persistence=importlib.util.module_from_spec(spec);spec.loader.exec_module(persistence)

class DisplayFeatureTests(unittest.TestCase):
    def test_restart_requires_every_saved_setting_and_all_three_registrations(self):
        rows={str(i): dict(controller=str(i),tv='tv-'+str(i),revision=str(i+4),
              origin='QUICK' if i==0 else 'CUSTOM',layout='FIT',mapping='DETAILED',
              requested='144p' if i==0 else '640x360',width='17',height='11') for i in range(3)}
        expected=dict(passed=True,steps=[dict(clients=dict(leader=rows))])
        def lines(values):
            return '\n'.join(persistence.MARKER+' '.join(k+'='+v for k,v in row.items()) for row in values)
        text=lines(rows.values())
        self.assertEqual(rows,persistence.check(expected,text))
        for key in persistence.KEYS:
            changed={k:dict(v) for k,v in rows.items()};changed['1'][key]='reset'
            with self.assertRaises(ValueError,msg=key):persistence.check(expected,lines(changed.values()))
            del changed['1'][key]
            with self.assertRaises(ValueError,msg='missing '+key):persistence.check(expected,lines(changed.values()))
        with self.assertRaises(ValueError):persistence.check(expected,lines(list(rows.values())[:2]))
        with self.assertRaises(ValueError):persistence.check(expected,text+'\n'+lines([rows['1']]))
        with self.assertRaises(ValueError):persistence.check(dict(expected,passed=False),text)

    def test_preserved_settings_allow_transport_progress_but_reject_lost_edits(self):
        expected={key:'value' for key in probe.DISPLAY_KEYS}
        current=dict(expected,status='PLAYING',streamGeneration='99')
        self.assertTrue(probe.same_display(expected,current))
        for key in probe.DISPLAY_KEYS:
            changed=dict(current);changed[key]='reset'
            self.assertFalse(probe.same_display(expected,changed))
        self.assertFalse(probe.same_display({},{}))
    def test_legacy_camera_transport_uses_supported_coordinate_only_teleport(self):
        for label,expected in [('1.7.10-forge','tp CinemarrVideoA 22.5 101 7.5'),
                               ('1.21.1-neoforge','tp CinemarrVideoA 22.5 101 7.5 180 0')]:
            args=SimpleNamespace(rcon_port=1234,rcon_password='fixture',server_fifo=None,
                                 discopanel_server_id=None,leader_log=Path(label+'.audio-leader.console.log'))
            server=DisplayServer(args)
            with patch('display_server.subprocess.run') as run:
                server.command('tp CinemarrVideoA 22.5 101 7.5 180 0')
                self.assertEqual(expected,run.call_args.args[0][-1])
            with self.assertRaises(ValueError):server.command('tp OtherPlayer 22.5 101 7.5 180 0')

    def test_independent_camera_projection_uses_the_framebuffer_aspect(self):
        camera=(0,0,10,180,0,90)
        for point,expected in [((0,0,0),(320,240)),((10,0,0),(560,240)),((0,10,0),(320,0))]:
            for actual,wanted in zip(project(point,camera,(640,480)),expected):self.assertAlmostEqual(wanted,actual)

    def test_independent_cell_sampler_has_exact_half_ties_and_opaque_bars(self):
        corners=bytes([255,0,0,255,0,255,0,255,0,0,255,255,255,255,255,255])
        self.assertEqual((128,128,128),block_color(corners,2,2,1,1,'STRETCH',0,0))
        self.assertEqual((0,0,0),block_color(corners[:8],2,1,4,4,'FIT',0,0))

    def test_original_framebuffer_color_mismatch_cannot_pass(self):
        source=bytes([255,0,0,255]);metadata=dict(mapping='ONE_PIXEL_PER_BLOCK',facing='SOUTH',
            sourceWidth=1,sourceHeight=1,sourceSha256=hashlib.sha256(source).hexdigest(),width=4,height=4,
            mask='ffff',layout='STRETCH',minimumU=-2,minimumV=-2,plane=0,camera=(0,0,11.002,180,0,90))
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'frame.png';frame=Image.new('RGB',(640,480))
            ImageDraw.Draw(frame).rectangle((272,192,368,288),fill=(255,0,0));frame.save(path)
            self.assertTrue(verify_pixel_frame(path,metadata,source)['passed'])
            ImageDraw.Draw(frame).rectangle((272,192,368,288),fill=(128,0,0));frame.save(path)
            self.assertFalse(verify_pixel_frame(path,metadata,source)['passed'])

    def test_failed_start_keeps_established_playlist_and_segments_available(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);port=base/'port';state=base/'fake-plex.state'
            state.write_text('starts-fail-test')
            (base/'media.m3u8').write_text('#EXTM3U\n#EXTINF:1,\nsegment.ts\n#EXT-X-ENDLIST\n')
            (base/'segment.ts').write_bytes(b'established-program')
            process=subprocess.Popen([sys.executable,str(Path(__file__).with_name('fake-plex-server.py')),
                '--port-file',str(port),'--request-log',str(base/'requests'),'--token','fixture-test',
                '--video-directory',str(base),'--state-file',str(state),'--track-duration-ms','1000'],
                stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            try:
                deadline=time.monotonic()+5
                while not port.exists() and time.monotonic()<deadline:time.sleep(.02)
                origin='http://127.0.0.1:'+port.read_text().strip()+'/video/:/transcode/universal/'
                def get(name):
                    req=urllib.request.Request(origin+name,headers={'X-Plex-Token':'fixture-test'})
                    with urllib.request.urlopen(req,timeout=2) as response:return response.read()
                with self.assertRaises(urllib.error.HTTPError) as error:get('start.m3u8')
                self.assertEqual(503,error.exception.code)
                error.exception.close()
                self.assertIn(b'#EXTM3U',get('media.m3u8'))
                self.assertEqual(b'established-program',get('segment.ts'))
                state.write_text('online');self.assertIn(b'#EXTM3U',get('start.m3u8'))
            finally:
                process.terminate();process.wait(timeout=5)

    def test_mask_holes_must_match_the_same_pixels_in_an_original_background_capture(self):
        source=bytes([255,0,0,255]);metadata=dict(mapping='ONE_PIXEL_PER_BLOCK',facing='SOUTH',
            sourceWidth=1,sourceHeight=1,sourceSha256=hashlib.sha256(source).hexdigest(),width=4,height=4,
            mask='ff33',layout='STRETCH',minimumU=-2,minimumV=-2,plane=0,camera=(0,0,11.002,180,0,90))
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'frame.png';frame=Image.new('RGB',(640,480))
            frame.putdata([(x*255//640,y*255//480,255) for y in range(480) for x in range(640)])
            original_background=frame.copy()
            background=Path(directory)/'background.png';frame.save(background)
            draw=ImageDraw.Draw(frame);draw.rectangle((272,192,368,288),fill=(255,0,0))
            frame.paste(original_background.crop((320,192,369,240)),(320,192));frame.save(path)
            result=verify_pixel_frame(path,metadata,source,background_path=background)
            self.assertTrue(result['passed']);self.assertEqual(16,result['holeChecks'])
            draw.rectangle((320,192,368,239),fill=(255,0,0));frame.save(path)
            result=verify_pixel_frame(path,metadata,source,background_path=background)
            self.assertFalse(result['passed']);self.assertEqual(16,result['mismatches'])
            with self.assertRaises(ValueError):verify_pixel_frame(path,metadata,source,background_path=path)
    def test_send_waits_for_its_own_consumption_before_reusing_slot(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);log=base/'case.audio-leader.console.log';log.touch()
            control=base/'case.audio-leader.control';control.touch()
            receipt=base/'case.audio-leader.control.received';receipt.write_text('stale')
            observer=probe.Observer({'leader':log},base/'output')
            sender=threading.Thread(target=observer.send,args=('video:pause',))
            sender.start()
            deadline=time.monotonic()+2
            while not control.read_text() and time.monotonic()<deadline:time.sleep(.01)
            self.assertTrue(sender.is_alive())
            sequence,operation=control.read_text().strip().split('|')
            self.assertEqual('video:pause',operation)
            receipt.write_text(sequence);sender.join(2)
            self.assertFalse(sender.is_alive())
    def test_snapshot_requires_exact_fresh_request_and_preserves_both_identities(self):
        row='controller=123 timeline=party timelineGeneration=7 stream=tvstream streamGeneration=12 actual=160x90'
        text='Acceptance display snapshot: request=old '+row+'\nAcceptance display snapshot: request=new '+row
        self.assertEqual({},probe.snapshots(text,'missing'))
        state=probe.snapshots(text,'new')['123']
        self.assertEqual(('tvstream','12'),probe.stream(state));self.assertEqual('7',state['timelineGeneration'])
        self.assertEqual('160x90',state['actual'])
    def test_failed_or_existing_attempt_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(RuntimeError):probe.Observer({},Path(directory))
    def test_current_role_and_control_file_are_required(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);log=base/'case.audio-leader.console.log';log.touch()
            with self.assertRaises(RuntimeError):probe.Observer({'leader':log},base/'output')
    def test_every_main_profile_gate_enables_feature_and_observer(self):
        root=Path(__file__).resolve().parents[1]
        shell=(root/'scripts/run-dedicated-server-gate.sh').read_text()
        self.assertIn('display_feature_gate=${CINEMARR_DISPLAY_FEATURE_GATE:-$video_control_gate}',shell)
        self.assertIn('scripts/observe-display-feature.py',shell)
        self.assertIn('-Dcinemarr.acceptance.displayProbe=true',shell)
        self.assertIn('CINEMARR_DISPLAY_FEATURE_GATE', (root/'build.gradle').read_text())
        self.assertIn('CINEMARR_DISPLAY_FEATURE_GATE', (root/'.github/workflows/ci.yml').read_text())
        self.assertIn('export CINEMARR_DISPLAY_FEATURE_GATE=true',(root/'scripts/run-discopanel-real-plex-gate.sh').read_text())

if __name__=='__main__':unittest.main()
