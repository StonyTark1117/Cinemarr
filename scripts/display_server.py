"""Narrow camera control for the existing owned acceptance server."""
import json
import os
import re
import stat
import subprocess
import sys
import urllib.request
from pathlib import Path


class DisplayServer:
    def __init__(self, args):
        self.port, self.password, self.fifo, self.server_id = args.rcon_port, args.rcon_password, args.server_fifo, args.discopanel_server_id
        self.legacy = args.leader_log.name.startswith('1.7.10-forge.')
        if sum(bool(x) for x in (self.port, self.fifo, self.server_id)) != 1:
            raise ValueError('Exactly one owned acceptance server transport is required')
        if self.fifo and (self.fifo.is_symlink() or not stat.S_ISFIFO(self.fifo.stat().st_mode)
                          or not self.fifo.resolve().is_relative_to(args.leader_log.parent.resolve())):
            raise ValueError('Server FIFO is outside this acceptance attempt')

    def command(self, command):
        number = r'-?\d+(?:\.\d+)?'
        if not re.fullmatch(r'tp CinemarrVideoA (?:'+number+r' ){4}'+number
                            +r'|setblock (?:22 100 7|52 103 14) (?:stone|air)', command):
            raise ValueError('Command is outside the display camera fixture')
        if self.legacy and command.startswith('tp '):
            # 1.7.10 supports coordinates only. The owned client supplies look
            # angles after its teleport packet has arrived.
            command = ' '.join(command.split()[:5])
        if self.fifo:
            fd=os.open(self.fifo, os.O_WRONLY | os.O_NONBLOCK)
            try: os.write(fd, (command+'\n').encode())
            finally: os.close(fd)
        elif self.port:
            subprocess.run([sys.executable,str(Path(__file__).with_name('minecraft-rcon.py')),
                            '127.0.0.1',str(self.port),self.password,command],
                           check=True,stdout=subprocess.DEVNULL,timeout=20)
        else:
            base=os.environ['DISCOPANEL_API_BASE'];token=os.environ['DISCOPANEL_TOKEN']
            if not re.fullmatch(r'https?://[A-Za-z0-9._:-]+',base):
                raise ValueError('Invalid DiscPanel origin')
            request=urllib.request.Request(base+'/discopanel.v1.ServerService/SendCommand',
                data=json.dumps(dict(id=self.server_id,command=command,silent=False)).encode(),
                headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
            with urllib.request.urlopen(request,timeout=30) as response:
                if not json.load(response).get('success'):
                    raise RuntimeError('Acceptance camera command was rejected')
