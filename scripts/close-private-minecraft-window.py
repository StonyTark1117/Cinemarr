#!/usr/bin/env python3
"""Request normal application shutdown on an identity-checked private X server.

Send ICCCM WM_DELETE_WINDOW directly: _NET_CLOSE_WINDOW requires a window
manager, which the private Xvfb does not run. Never destroy/kill the window.
The calling gate must separately wait for and check the actual launcher exit.
"""
import argparse
import ctypes as c
from pathlib import Path
import re

from private_minecraft_window import PrivateMinecraftWindow


class MessageData(c.Union):
    _fields_ = [('b', c.c_char * 20), ('s', c.c_short * 10), ('l', c.c_long * 5)]


class ClientMessage(c.Structure):
    _fields_ = [('type', c.c_int), ('serial', c.c_ulong), ('send_event', c.c_int),
                ('display', c.c_void_p), ('window', c.c_ulong),
                ('message_type', c.c_ulong), ('format', c.c_int), ('data', MessageData)]


class XEvent(c.Union):
    _fields_ = [('client', ClientMessage), ('pad', c.c_long * 24)]


def request_close(window):
    window.validate()
    x = c.CDLL('libX11.so.6')
    x.XOpenDisplay.argtypes = [c.c_char_p]
    x.XOpenDisplay.restype = c.c_void_p
    x.XInternAtom.argtypes = [c.c_void_p, c.c_char_p, c.c_int]
    x.XInternAtom.restype = c.c_ulong
    x.XGetWMProtocols.argtypes = [c.c_void_p, c.c_ulong,
                                  c.POINTER(c.POINTER(c.c_ulong)), c.POINTER(c.c_int)]
    x.XGetWMProtocols.restype = c.c_int
    x.XFree.argtypes = [c.c_void_p]
    x.XSendEvent.argtypes = [c.c_void_p, c.c_ulong, c.c_int, c.c_long, c.POINTER(XEvent)]
    x.XSendEvent.restype = c.c_int
    x.XSync.argtypes = [c.c_void_p, c.c_int]
    x.XCloseDisplay.argtypes = [c.c_void_p]
    display = x.XOpenDisplay(window.display.encode())
    if not display:
        raise RuntimeError('Cannot connect to the verified private display')
    try:
        target = int(window.window)
        delete = x.XInternAtom(display, b'WM_DELETE_WINDOW', False)
        protocols = c.POINTER(c.c_ulong)()
        count = c.c_int()
        found = x.XGetWMProtocols(display, target, c.byref(protocols), c.byref(count))
        try:
            if not found or delete not in list(protocols[:count.value]):
                raise RuntimeError('Minecraft window does not support normal close requests')
        finally:
            if protocols:
                x.XFree(protocols)
        event = XEvent()
        event.client.type = 33  # ClientMessage
        event.client.display = display
        event.client.window = target
        event.client.message_type = x.XInternAtom(display, b'WM_PROTOCOLS', False)
        event.client.format = 32
        event.client.data.l[0] = delete
        event.client.data.l[1] = 0  # CurrentTime
        window.validate()
        if not x.XSendEvent(display, target, False, 0, c.byref(event)):
            raise RuntimeError('Normal Minecraft close request was not sent')
        x.XSync(display, False)
    finally:
        x.XCloseDisplay(display)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log', type=Path, required=True)
    parser.add_argument('--gate-pid', type=int, required=True)
    args = parser.parse_args()
    log = args.log.read_text(errors='replace')
    geometry = re.findall(r'Private Xvfb ready: display=:\d+ pid=\d+ geometry=(\S+)', log)
    if len(geometry) != 1:
        raise RuntimeError('Expected exactly one private display launch')
    request_close(PrivateMinecraftWindow(log, args.gate_pid, geometry=geometry[0], wait_seconds=10))
    print('Requested normal Minecraft window close on verified private X')


if __name__ == '__main__':
    main()
