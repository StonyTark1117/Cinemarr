#!/usr/bin/env python3
"""Prepare or restore only weather fields in a stopped acceptance world's NBT.

Input/output JSON carries base64 file content on stdin/stdout. Restore merges the
saved weather into the current file; it never rolls back other world progress.
"""
import base64
import gzip
import hashlib
import io
import json
import struct
import sys

LIMIT = 16 * 1024 * 1024
CLEAR_TICKS = 10_000_000
FORMATS = {1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}


def schema_fields(schema):
    if schema not in ('legacy', 'modern', '26'):
        raise ValueError('unknown weather schema')
    prefix = 'data' if schema == '26' else 'Data'
    names = {'raining': 1, 'thundering': 1}
    names.update({'rain_time': 3, 'thunder_time': 3, 'clear_weather_time': 3}
                 if schema == '26' else {'rainTime': 3, 'thunderTime': 3})
    if schema == 'modern':
        names['clearWeatherTime'] = 3
    return {(prefix, name): kind for name, kind in names.items()}


def parse(compressed, schema):
    expected = schema_fields(schema)
    if len(compressed) > LIMIT:
        raise ValueError('oversized compressed weather input')
    with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
        data = bytearray(stream.read(LIMIT + 1))
    if len(data) > LIMIT:
        raise ValueError('oversized expanded weather input')
    position = 0
    fields = {}

    def read(size):
        nonlocal position
        if size < 0 or position + size > len(data):
            raise ValueError('truncated NBT')
        result = bytes(data[position:position + size])
        position += size
        return result

    def number(fmt):
        return struct.unpack('>' + fmt, read(struct.calcsize('>' + fmt)))[0]

    def name():
        return read(number('H')).decode('utf-8')

    def payload(kind, path):
        if len(path) > 64:
            raise ValueError('excessive NBT depth')
        if path in expected and kind != expected[path]:
            raise ValueError('wrong weather field type')
        if kind in FORMATS:
            start = position
            value = number(FORMATS[kind])
            if path in expected:
                if path in fields or (kind == 1 and value not in (0, 1)):
                    raise ValueError('invalid weather field')
                fields[path] = (kind, value, start)
        elif kind in (7, 11, 12):
            read(number('i') * {7: 1, 11: 4, 12: 8}[kind])
        elif kind == 8:
            read(number('H'))
        elif kind == 9:
            item_kind, length = number('B'), number('i')
            if not 0 <= item_kind <= 12 or not 0 <= length <= len(data) or (item_kind == 0 and length):
                raise ValueError('invalid NBT list')
            for _ in range(length):
                payload(item_kind, path + ('[]',))
        elif kind == 10:
            keys = set()
            while (item_kind := number('B')):
                key = name()
                if key in keys:
                    raise ValueError('duplicate compound key')
                keys.add(key)
                payload(item_kind, path + (key,))
        else:
            raise ValueError('unknown NBT tag')

    if number('B') != 10:
        raise ValueError('NBT root must be a compound')
    name()
    payload(10, ())
    if position != len(data) or set(fields) != set(expected):
        raise ValueError('missing weather fields or trailing NBT')
    return data, fields


def transform(compressed, schema, original=None):
    data, fields = parse(compressed, schema)
    snapshot = {'schema': schema, 'fields': {path[-1]: value for path, (_, value, _) in fields.items()}}
    desired = {path[-1]: 0 if kind == 1 else CLEAR_TICKS for path, (kind, _, _) in fields.items()}
    if original is not None:
        if (set(original) != {'schema', 'fields'} or original['schema'] != schema
                or set(original['fields']) != set(desired)):
            raise ValueError('weather snapshot schema mismatch')
        desired = original['fields']
    before = bytes(data)
    masked = bytearray(data)
    for path, (kind, _, offset) in fields.items():
        value = desired[path[-1]]
        if type(value) is not int or (kind == 1 and value not in (0, 1)):
            raise ValueError('invalid saved weather value')
        struct.pack_into('>' + FORMATS[kind], data, offset, value)
        struct.pack_into('>' + FORMATS[kind], masked, offset, 0)
    after_masked = bytearray(data)
    for kind, _, offset in fields.values():
        struct.pack_into('>' + FORMATS[kind], after_masked, offset, 0)
    if masked != after_masked:
        raise ValueError('non-weather bytes changed')
    updated = compressed if data == before else gzip.compress(data, mtime=0)
    return updated, snapshot, {
        'schema': schema,
        'beforeSha256': hashlib.sha256(compressed).hexdigest(),
        'afterSha256': hashlib.sha256(updated).hexdigest(),
        'nonWeatherPayloadSha256': hashlib.sha256(masked).hexdigest(),
        'nonWeatherBytesUnchanged': True,
        'weather': desired,
    }


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ('prepare', 'restore'):
        raise ValueError('expected prepare/restore and legacy/modern/26 schema')
    raw = sys.stdin.buffer.read(LIMIT * 2 + 1)
    if len(raw) > LIMIT * 2:
        raise ValueError('oversized JSON input')
    request = json.loads(raw)
    compressed = base64.b64decode(request['content'], validate=True)
    original = request['original'] if sys.argv[1] == 'restore' else None
    updated, snapshot, receipt = transform(compressed, sys.argv[2], original)
    print(json.dumps(dict(content=base64.b64encode(updated).decode(),
                          original=snapshot, receipt=receipt)))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, EOFError, KeyError, TypeError, struct.error):
        print('Weather state is malformed or unsupported; acceptance cannot continue.', file=sys.stderr)
        sys.exit(1)
