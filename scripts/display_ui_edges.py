"""Actual Display Settings edge cases at the required 320x240 viewport.

The deterministic local fixture also delays acknowledgement using a bounded
pause of its owned Java server. Live Plex runs exercise the other widgets;
local boundary receipts provide the controlled pending-state evidence.
"""
import hashlib,json,os,re,signal,subprocess,sys,time
from pathlib import Path

def exercise(observer, custom):
    oid=custom['controller']; baseline=observer.snapshot('leader')[oid]
    record={'scope':'Additional actual-widget 320x240 review; original captures need direct review','passed':False,'steps':[],'localPendingStateProbe':observer.fixture_state is not None}
    output=observer.output/'extended-ui.json'; start=len(observer.captures)
    def state():return observer.snapshot('leader')[oid]
    def unchanged(before,label):
        after=state();assert all(after[k]==before[k] for k in ('revision','layout','mapping','requested')),label
        record['steps'].append({'scenario':label,'state':after})
    def cap(name):time.sleep(.25);observer.capture(desktop,'extended-'+name)
    def click(x,y):desktop.click(x,y);time.sleep(.15)
    def field(x,value):
        click(x,222)
        desktop.run('xdotool','key','--clearmodifiers','ctrl+a')
        # LWJGL 2 consumes the selection shortcut on a later client tick.
        # Let it settle before typing, or selection can erase the first digit.
        time.sleep(.3)
        desktop.run('xdotool','type','--clearmodifiers','--delay','80',value)
        time.sleep(.2)
    def apply():click(320,436)
    try:
        desktop=observer.page(baseline,'leader','extended-initial')
        # Baseline from the normal page smoke is Auto. Each named preset is visible.
        assert baseline['requested']=='Auto'
        for name in ('144p','240p','480p','720p','1080p','1440p','4k','8k','custom'):
            click(320,172);cap('quality-'+name)
        for w,h,label in [('oops','240','malformed'),('1','240','below-minimum'),('8193','240','above-maximum'),('8192','8192','frame-budget')]:
            field(180,w);field(480,h);apply();cap('error-'+label);unchanged(baseline,label+'-not-sent')
        field(180,'12345');cap('four-character-limit')
        field(180,'32');time.sleep(1.2);desktop.run('xdotool','type','--clearmodifiers','--delay','80','0')
        field(480,'180');cap('focused-draft-after-ticks')
        if observer.fixture_state is not None:
            # Briefly suspend only this owned Java server to make an acknowledgement observable.
            profile=observer.logs['leader'].name.split('.audio-')[0]
            manifest=json.loads((Path(__file__).resolve().parents[1]/'gradle/targets.json').read_text())
            candidates=[p for a in manifest['artifacts'] for p in [a['runtime'],a.get('quiltRuntime',{})] if p.get('name')==profile]
            assert len(candidates)==1
            listing=subprocess.check_output(['ss','-ltnp','sport = :'+str(candidates[0]['port'])],text=True)
            pids=set(map(int,re.findall(r'pid=(\d+)',listing)));assert len(pids)==1
            pid=pids.pop();identity=desktop.identity(pid)[1]
            assert desktop.owned(pid) and 'java' in Path(os.fsdecode(Path('/proc',str(pid),'cmdline').read_bytes().split(b'\0')[0])).name.lower()
            # Independent bounded resume survives an observer exception or termination.
            watchdog=subprocess.Popen([sys.executable,'-c',"import os,signal,sys,time;from pathlib import Path;time.sleep(4);p=int(sys.argv[1]);s=Path('/proc',str(p),'stat');os.kill(p,signal.SIGCONT) if s.exists() and s.read_text().rsplit(')',1)[1].split()[19]==sys.argv[2] else None",str(pid),identity],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            os.kill(pid,signal.SIGSTOP)
            try:
                apply();cap('applying-pending');apply();cap('pending-repeat-disabled')
            finally:
                if desktop.identity(pid)[1]==identity:os.kill(pid,signal.SIGCONT)
                watchdog.wait(timeout=6)
        else:
            apply()
        row=observer.wait(lambda pair:all(rows[oid]['requested']=='320x180' and int(rows[oid]['revision'])==int(baseline['revision'])+1 for rows in pair.values()),'extended-custom-acknowledged')[oid]
        # This is a separate supplement, keep the normative feature scenario list unchanged.
        observer.steps.pop();record['steps'].append({'scenario':'custom-focus-single-ack','state':row});cap('saved-custom')
        for w,h in [('8192','2'),('2','8192'),('320','180')]:
            field(180,w);field(480,h);apply();expected_revision=int(row['revision'])+1
            row=observer.wait(lambda pair:all(rows[oid]['requested']==w+'x'+h and int(rows[oid]['revision'])==expected_revision for rows in pair.values()),'extended-valid-boundary')[oid]
            observer.steps.pop();record['steps'].append({'scenario':'valid-custom-'+w+'x'+h,'state':row});cap('valid-custom-'+w+'x'+h)
        click(320,76);field(180,'426');cap('cancel-draft');click(532,436);unchanged(row,'cancel-preserved-authoritative-settings')
        desktop=observer.page(row,'leader','extended-reopened-after-cancel')
        # Real concurrent writer changes the server while this page retains its old draft.
        changed=observer.display(row,'STRETCH' if row['layout']!='STRETCH' else 'FIT',row['mapping'],'640x360');observer.steps.pop()
        cap('old-draft-after-concurrent-update');apply();time.sleep(.5);cap('stale-error');unchanged(changed,'stale-page-apply-rejected')
        click(112,436);cap('reloaded-current-settings');observer.close_page(desktop)
        restored=observer.display(changed,baseline['layout'],baseline['mapping'],'auto');observer.steps.pop()
        record['steps'].append({'scenario':'restored-original-display-choices','state':restored});record['passed']=True
    finally:
        record['captures']=observer.captures[start:];record['sourceSha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        output.write_text(json.dumps(record,indent=2)+'\n')
