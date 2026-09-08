#!/usr/bin/env python3
"""Inspect or prepare a saved legacy test player's health; base64 NBT stays on stdin/stdout.

Only top-level health/death/fall fields change. The caller must own the test
identity, keep the server stopped, and restore the original bytes afterward.
"""
import base64
import gzip
import io
import math
import struct
import sys

LIMIT = 1024 * 1024
FIELDS = {"Health": (2, 20), "HealF": (5, 20.0), "DeathTime": (2, 0),
          "HurtTime": (2, 0), "FallDistance": (5, 0.0)}
FORMATS = {1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}


def prepare(compressed, repair=False):
    if len(compressed) > LIMIT:
        raise ValueError("oversized player data")
    with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
        data = bytearray(stream.read(LIMIT + 1))
    if len(data) > LIMIT:
        raise ValueError("oversized expanded player data")
    position = 0
    fields = {}

    def read(size):
        nonlocal position
        if size < 0 or position + size > len(data):
            raise ValueError("truncated player data")
        result = bytes(data[position:position + size])
        position += size
        return result

    def number(fmt):
        return struct.unpack(">" + fmt, read(struct.calcsize(">" + fmt)))[0]

    def name():
        return read(number("H")).decode("utf-8")

    def payload(kind, depth, key=None):
        if depth > 32:
            raise ValueError("player data nesting exceeds limit")
        if kind in FORMATS:
            start = position
            value = number(FORMATS[kind])
            if depth == 1 and key in FIELDS:
                if key in fields or kind != FIELDS[key][0] or not math.isfinite(value):
                    raise ValueError("invalid legacy health field")
                fields[key] = (value, start)
        elif kind in (7, 11, 12):
            read(number("i") * {7: 1, 11: 4, 12: 8}[kind])
        elif kind == 8:
            read(number("H"))
        elif kind == 9:
            item_kind, length = number("B"), number("i")
            if length < 0 or length > LIMIT or (item_kind == 0 and length):
                raise ValueError("invalid player list")
            for _ in range(length):
                payload(item_kind, depth + 1)
        elif kind == 10:
            while True:
                item_kind = number("B")
                if item_kind == 0:
                    break
                payload(item_kind, depth + 1, name())
        else:
            raise ValueError("unknown player tag")

    if number("B") != 10:
        raise ValueError("player root must be a compound")
    name()
    payload(10, 0)
    if position != len(data) or set(fields) != set(FIELDS):
        raise ValueError("legacy player health fields missing or trailing data")
    alive = fields["Health"][0] > 0 and fields["HealF"][0] > 0 and fields["DeathTime"][0] == 0
    if not repair:
        if not alive:
            raise ValueError("saved test player is dead")
        return compressed
    if all(fields[key][0] == value for key, (_, value) in FIELDS.items()):
        return compressed
    for key, (kind, value) in FIELDS.items():
        struct.pack_into(">" + FORMATS[kind], data, fields[key][1], value)
    return gzip.compress(data, mtime=0)


def main():
    if sys.argv[1:] not in (["check"], ["prepare"]):
        raise ValueError("expected check or prepare")
    encoded = sys.stdin.buffer.read(LIMIT * 2 + 1)
    if len(encoded) > LIMIT * 2:
        raise ValueError("oversized encoded player data")
    data = prepare(base64.b64decode(encoded.strip(), validate=True), sys.argv[1] == "prepare")
    if sys.argv[1] == "prepare":
        print(base64.b64encode(data).decode("ascii"))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, EOFError, struct.error):
        print("Legacy test-player state is dead or malformed; acceptance cannot pass.", file=sys.stderr)
        sys.exit(1)
