#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('capacity', ROOT/'observe-video-capacity.py')
capacity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capacity)


class CapacityEvidenceTests(unittest.TestCase):
    def test_both_viewers_must_agree_on_every_tv_and_queue_state(self):
        leader = {str(i): {'owner':'true', 'stream':str(i), 'streamState':'WAITING' if i==2 else 'READY'} for i in range(3)}
        follower = {tv:dict(state,owner='false') for tv,state in leader.items()}
        self.assertTrue(capacity.compatible(leader,follower))
        follower['2']['streamState']='PREPARING'
        self.assertFalse(capacity.compatible(leader,follower))
        follower['2']=dict(leader['2'])
        self.assertFalse(capacity.compatible(leader,follower))
        del follower['2']
        self.assertFalse(capacity.compatible(leader,follower))

    def test_display_revision_does_not_excuse_a_sibling_media_restart(self):
        before={'tv':{'timeline':'party','timelineGeneration':2,'stream':'media','streamGeneration':3,'revision':1}}
        after={'tv':dict(before['tv'],revision=2)}
        self.assertTrue(capacity.stream_unchanged(before,after,'tv'))
        for field,value in [('timeline','other'),('timelineGeneration',3),('stream','other'),('streamGeneration',4)]:
            changed={'tv':dict(after['tv'],**{field:value})}
            self.assertFalse(capacity.stream_unchanged(before,changed,'tv'))

    def test_replacement_fault_rejects_only_new_starts_and_recovers_explicitly(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary)
            (directory/'media.m3u8').write_text('#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:1,\nsegment000.ts\n#EXT-X-ENDLIST\n')
            (directory/'segment000.ts').write_bytes(b'controlled-existing-segment')
            fault=directory/'state'; fault.write_text('starts-rejected\n')
            port=directory/'port'
            process=subprocess.Popen([sys.executable,str(ROOT/'fake-plex-server.py'),'--port-file',str(port),
                '--request-log',str(directory/'requests'),'--token','test-only-token','--state-file',str(fault),
                '--video-directory',str(directory),'--track-duration-ms','1000'],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
            try:
                deadline=time.monotonic()+10
                while not port.exists():
                    if process.poll() is not None or time.monotonic()>deadline: self.fail('Fake Plex did not start')
                    time.sleep(.02)
                base='http://127.0.0.1:'+port.read_text()+'/video/:/transcode/universal/'
                def request(path):
                    with urlopen(Request(base+path,headers={'X-Plex-Token':'test-only-token'}),timeout=3) as response:
                        return response.read()
                with self.assertRaises(HTTPError) as error: request('start.m3u8')
                self.assertEqual(503,error.exception.code)
                self.assertEqual(b'controlled-existing-segment',request('segment000.ts'))
                self.assertIn(b'#EXTM3U',request('media.m3u8'))
                self.assertEqual(b'',request('stop'))
                fault.write_text('online\n')
                self.assertIn(b'RESOLUTION=160x90',request('start.m3u8'))
            finally:
                process.terminate()
                try: process.communicate(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill(); process.communicate(); self.fail('Fake Plex did not stop cleanly')


if __name__ == '__main__': unittest.main()
