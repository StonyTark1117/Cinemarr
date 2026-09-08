#!/usr/bin/env python3
"""Real concurrent display-allocation and scoped signal-cleanup regressions."""
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import time
import unittest

LAUNCHER=Path(__file__).with_name('run-private-xvfb.sh')
CHILD=r'''
import json,os,pathlib,subprocess,sys,time
directory=pathlib.Path(sys.argv[1]);name=sys.argv[2]
screen=subprocess.check_output(['xrandr','--current'],text=True)
(directory/name).write_text(json.dumps({'display':os.environ['DISPLAY'],'pid':os.getpid(),'screen':screen}))
deadline=time.monotonic()+8
while not (directory/'release').exists():
 if time.monotonic()>deadline:raise SystemExit(12)
 time.sleep(.02)
'''

class PrivateDisplayTest(unittest.TestCase):
    def start(self,directory,name,geometry):
        process=subprocess.Popen(['bash',str(LAUNCHER),geometry,'python3','-c',CHILD,str(directory),name],
                                 stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        self.addCleanup(self.stop,process)
        return process

    @staticmethod
    def stop(process):
        if process.poll() is None:process.terminate()
        try:process.communicate(timeout=8)
        except subprocess.TimeoutExpired:
            process.kill();process.communicate(timeout=5)

    def ready(self,directory,name,process):
        deadline=time.monotonic()+10
        while not (directory/name).exists():
            if process.poll() is not None:self.fail(process.communicate()[1])
            if time.monotonic()>deadline:self.fail('private display child never became ready')
            time.sleep(.02)
        return json.loads((directory/name).read_text())

    def assert_server_gone(self,stderr):
        pid=int(re.search(r'Private Xvfb ready: display=:\d+ pid=(\d+)',stderr).group(1))
        with self.assertRaises(ProcessLookupError):os.kill(pid,0)

    def test_concurrent_clients_get_distinct_live_displays_and_geometry(self):
        with tempfile.TemporaryDirectory(prefix='cinemarr-xvfb-test-') as temp:
            directory=Path(temp)
            first=self.start(directory,'first','640x480x24')
            second=self.start(directory,'second','1280x720x24')
            a=self.ready(directory,'first',first);b=self.ready(directory,'second',second)
            self.assertNotEqual(a['display'],b['display'])
            self.assertGreaterEqual(int(a['display'][1:]),90)
            self.assertGreaterEqual(int(b['display'][1:]),90)
            self.assertRegex(a['screen'],r'current 640 x 480')
            self.assertRegex(b['screen'],r'current 1280 x 720')
            (directory/'release').touch()
            for process in (first,second):
                _,stderr=process.communicate(timeout=10)
                self.assertEqual(0,process.returncode,stderr);self.assert_server_gone(stderr)

    def test_termination_stops_only_its_owned_server_and_child(self):
        with tempfile.TemporaryDirectory(prefix='cinemarr-xvfb-test-') as temp:
            directory=Path(temp);process=self.start(directory,'child','640x480x24')
            other=self.start(directory,'other','1280x720x24')
            child=self.ready(directory,'child',process)
            survivor=self.ready(directory,'other',other)
            process.send_signal(signal.SIGTERM)
            _,stderr=process.communicate(timeout=10)
            self.assertEqual(143,process.returncode,stderr);self.assert_server_gone(stderr)
            with self.assertRaises(ProcessLookupError):os.kill(child['pid'],0)
            self.assertIsNone(other.poll());os.kill(survivor['pid'],0)
            (directory/'release').touch()
            _,stderr=other.communicate(timeout=10)
            self.assertEqual(0,other.returncode,stderr);self.assert_server_gone(stderr)

    def test_child_failure_is_not_reported_as_success(self):
        result=subprocess.run(['bash',str(LAUNCHER),'640x480x24','bash','-c','exit 17'],capture_output=True,text=True,timeout=10)
        self.assertEqual(17,result.returncode,result.stderr);self.assert_server_gone(result.stderr)

if __name__=='__main__':unittest.main()
