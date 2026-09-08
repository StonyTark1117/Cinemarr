#!/usr/bin/env python3
"""Real concurrent display-allocation and scoped signal-cleanup regressions."""
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import threading
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

# A real X client with no Minecraft or window manager dependency. The native
# close protocol is received independently of the sender's Python structures.
WINDOW_CHILD=r'''
import ctypes as c,os,pathlib,signal,subprocess,sys
directory=pathlib.Path(sys.argv[1]);mode=sys.argv[2]
x=c.CDLL('libX11.so.6')
def signature(name,args,result):
    function=getattr(x,name);function.argtypes=args;function.restype=result;return function
ptr=c.c_void_p;word=c.c_ulong;integer=c.c_int
signature('XOpenDisplay',[c.c_char_p],ptr)
signature('XDefaultRootWindow',[ptr],word)
signature('XCreateSimpleWindow',[ptr,word,integer,integer,c.c_uint,c.c_uint,c.c_uint,word,word],word)
signature('XStoreName',[ptr,word,c.c_char_p],integer)
signature('XInternAtom',[ptr,c.c_char_p,integer],word)
signature('XSetWMProtocols',[ptr,word,c.POINTER(word),integer],integer)
signature('XMapWindow',[ptr,word],integer)
signature('XFlush',[ptr],integer)
signature('XNextEvent',[ptr,ptr],integer)
signature('XCloseDisplay',[ptr],integer)
display=x.XOpenDisplay(os.environ['DISPLAY'].encode());assert display
window=x.XCreateSimpleWindow(display,x.XDefaultRootWindow(display),0,0,320,240,0,0,0)
x.XStoreName(display,window,b'Minecraft shutdown probe')
delete=x.XInternAtom(display,b'WM_DELETE_WINDOW',False)
protocols=x.XInternAtom(display,b'WM_PROTOCOLS',False)
x.XSetWMProtocols(display,window,(word*1)(delete),1)
x.XMapWindow(display,window);x.XFlush(display)
(directory/'ready').touch()
event=(c.c_long*24)()
while True:
    x.XNextEvent(display,c.byref(event))
    # Linux LP64 XClientMessageEvent: type at byte 0, window at 32,
    # message_type at 40, format at 48 and first data long at 56.
    data=bytes(event)
    if (int.from_bytes(data[0:4],sys.byteorder)==33
            and int.from_bytes(data[32:40],sys.byteorder)==window
            and int.from_bytes(data[40:48],sys.byteorder)==protocols
            and int.from_bytes(data[48:52],sys.byteorder)==32
            and int.from_bytes(data[56:64],sys.byteorder)==delete):break
assert subprocess.run(['xrandr','--current'],stdout=subprocess.DEVNULL).returncode==0
(directory/'close-request').touch()
if mode=='segv':
    # No core artifact is produced: status alone must still reject this crash.
    libc=c.CDLL(None);assert libc.prctl(4,0,0,0,0)==0
    os.kill(os.getpid(),signal.SIGSEGV)
x.XCloseDisplay(display)
'''

class PrivateDisplayTest(unittest.TestCase):
    def test_normal_window_close_and_native_crash_have_distinct_exit_receipts(self):
        for geometry in ('640x480x24','1280x720x24'):
            for mode,expected in (('normal',0),('segv',139)):
                with self.subTest(geometry=geometry,mode=mode), tempfile.TemporaryDirectory(prefix='cinemarr-window-exit-') as temp:
                    directory=Path(temp);log=directory/'client.log'
                    with log.open('w') as stream:
                        process=subprocess.Popen(['bash',str(LAUNCHER),geometry,'python3','-c',WINDOW_CHILD,temp,mode],
                                                 start_new_session=True,stdout=stream,stderr=stream,text=True)
                        try:
                            deadline=time.monotonic()+10
                            while not (directory/'ready').exists():
                                if process.poll() is not None:self.fail(log.read_text())
                                if time.monotonic()>deadline:self.fail('X window never became ready')
                                time.sleep(.02)
                            close=subprocess.run(['python3',str(LAUNCHER.with_name('close-private-minecraft-window.py')),
                                '--gate-pid',str(process.pid),'--log',str(log)],capture_output=True,text=True,timeout=10)
                            self.assertEqual(0,close.returncode,close.stderr)
                            self.assertEqual(expected,process.wait(timeout=10),log.read_text())
                            self.assertTrue((directory/'close-request').exists())
                            text=log.read_text()
                            self.assertIn('Private X command exited: status='+str(expected),text)
                            self.assert_server_gone(text)
                            self.assertFalse(list(directory.glob('core*')))
                        finally:
                            if process.poll() is None:process.terminate()
                            process.wait(timeout=10)

    def test_gate_cleanup_keeps_x_alive_until_client_shutdown_finishes(self):
        source = LAUNCHER.with_name('run-dedicated-server-gate.sh').read_text()
        names = ('group_alive', 'stop_group', 'process_tree_pids',
                 'stop_process_tree', 'terminate_client_launch')
        functions = '\n'.join(re.search(r'(?ms)^' + name + r'\(\) \{\n.*?^\}', source).group()
                              for name in names)
        child = r'''
import pathlib,signal,subprocess,sys,time
directory=pathlib.Path(sys.argv[1])
def stop(signum,frame):
    time.sleep(.25)
    result=subprocess.run(['xrandr','--current'],capture_output=True)
    (directory/'shutdown').write_text(str(result.returncode))
    raise SystemExit(0)
signal.signal(signal.SIGTERM,stop)
(directory/'ready').touch()
while True:time.sleep(.02)
'''
        with tempfile.TemporaryDirectory(prefix='cinemarr-x-order-') as temp:
            directory=Path(temp)
            process=subprocess.Popen(['bash',str(LAUNCHER),'640x480x24','python3','-c',child,temp],
                                     start_new_session=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
            self.addCleanup(self.stop,process)
            deadline=time.monotonic()+10
            while not (directory/'ready').exists():
                if process.poll() is not None:self.fail(process.communicate()[1])
                if time.monotonic()>deadline:self.fail('shutdown probe never became ready')
                time.sleep(.02)
            # The extracted gate runs in a separate shell; reap our own direct
            # child so its zombie is not mistaken for a live launcher tree.
            reaper=threading.Thread(target=process.wait,daemon=True)
            reaper.start()
            result=subprocess.run(['bash','-c', 'active_server_group=""\nrepo_root=$2\n'+functions+
                                   '\nterminate_client_launch "$1" 10', 'cleanup-test',str(process.pid),str(LAUNCHER.parent.parent)],
                                  capture_output=True,text=True,timeout=30)
            _,stderr=process.communicate(timeout=10)
            reaper.join(timeout=5)
            self.assertTrue((directory/'shutdown').exists(),stderr)
            self.assertEqual('0',(directory/'shutdown').read_text(),
                             'The client lost its private X server during shutdown')
            self.assertEqual(0,result.returncode,result.stderr)
            self.assert_server_gone(stderr)

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
