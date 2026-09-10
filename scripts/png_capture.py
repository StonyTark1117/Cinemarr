"""Validate complete private-window PNG captures before retaining evidence."""
import argparse
from pathlib import Path
import struct
import zlib


MAX_FILE_BYTES = 32 * 1024 * 1024
MAX_PIXEL_BYTES = 64 * 1024 * 1024


def read_capture(path):
    """Return the exact validated bytes; never re-read a possibly mutable source."""
    with Path(path).open('rb') as stream:
        data = stream.read(MAX_FILE_BYTES + 1)
    if len(data) > MAX_FILE_BYTES or not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise ValueError('Capture is not a bounded PNG')
    offset = 8
    header = None
    palette = False
    pixels = bytearray()
    ended_pixels = False
    finished = False
    while offset < len(data):
        if len(data) - offset < 12:
            raise ValueError('Truncated PNG chunk')
        size, kind = struct.unpack_from('>I4s', data, offset)
        end = offset + size + 12
        if end > len(data):
            raise ValueError('Truncated PNG payload')
        payload = data[offset + 8:end - 4]
        crc, = struct.unpack_from('>I', data, end - 4)
        if zlib.crc32(kind + payload) != crc:
            raise ValueError('PNG chunk checksum mismatch')
        if header is None and kind != b'IHDR':
            raise ValueError('PNG header must be first')
        if pixels and kind != b'IDAT':
            ended_pixels = True
        if kind == b'IHDR':
            if header is not None or size != 13:
                raise ValueError('Invalid PNG header')
            header = struct.unpack('>IIBBBBB', payload)
        elif kind == b'PLTE':
            if palette or pixels or not 0 < size <= 768 or size % 3:
                raise ValueError('Invalid PNG palette')
            palette = True
        elif kind == b'IDAT':
            if ended_pixels:
                raise ValueError('Nonconsecutive PNG pixel chunks')
            pixels.extend(payload)
        elif kind == b'IEND':
            if size or end != len(data):
                raise ValueError('Invalid PNG end or trailing data')
            finished = True
        elif not kind[0] & 32:
            raise ValueError('Unsupported critical PNG chunk')
        offset = end
    if not finished or not header or not pixels:
        raise ValueError('Incomplete PNG capture')
    width, height, depth, color, compression, filtering, interlace = header
    depths = {0: (1, 2, 4, 8, 16), 2: (8, 16), 3: (1, 2, 4, 8),
              4: (8, 16), 6: (8, 16)}
    if (not 0 < width <= 8192 or not 0 < height <= 8192
            or depth not in depths.get(color, ())
            or compression or filtering or interlace or (color == 3 and not palette)):
        raise ValueError('Unsupported PNG capture dimensions or encoding')
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    stride = 1 + (width * channels * depth + 7) // 8
    expected = height * stride
    if expected > MAX_PIXEL_BYTES:
        raise ValueError('PNG pixel data exceeds capture limit')
    decoder = zlib.decompressobj()
    try:
        raw = decoder.decompress(pixels, expected + 1)
    except zlib.error as error:
        raise ValueError('Invalid PNG compressed pixels') from error
    if (len(raw) != expected or not decoder.eof or decoder.unused_data
            or decoder.unconsumed_tail or any(raw[row] > 4 for row in range(0, len(raw), stride))):
        raise ValueError('Incomplete or malformed PNG scanlines')
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--copy', type=Path)
    parser.add_argument('--closed-profile', help='Validate all retained PNGs for a closed gate profile')
    args = parser.parse_args()
    if args.closed_profile:
        if args.copy:
            parser.error('--copy cannot be used with --closed-profile')
        validate_closed_profile(args.source, args.closed_profile)
        return
    data = read_capture(args.source)
    if args.copy:
        # An existing evidence path is an error, including a previous failed run.
        with args.copy.open('xb') as destination:
            destination.write(data)


def validate_closed_profile(root, profile):
    """Run only after writers exit; never repair or discard malformed evidence."""
    if not profile or Path(profile).name != profile or any(c in profile for c in '*?[]'):
        raise ValueError('Invalid gate profile')
    paths = []
    for entry in Path(root).glob(profile + '.*'):
        paths.extend(entry.rglob('*') if entry.is_dir() else [entry])
    if any(p.name.startswith('.cinemarr-capture-') for p in paths):
        raise ValueError('Unpublished screenshot staging evidence remains')
    images = sorted(p for p in paths if p.is_file() and p.suffix == '.png')
    for role in ('leader', 'follower'):
        directory = Path(root) / (profile + '.audio-' + role) / 'screenshots'
        for name in ('cinemarr-video-acceptance.png', 'cinemarr-video-ui-acceptance.png'):
            if directory / name not in images:
                raise ValueError('Missing required closed-client screenshot')
    for image in images:
        try:
            read_capture(image)
        except ValueError as error:
            raise ValueError('Invalid retained screenshot: ' + str(image)) from error
    return len(images)


if __name__ == '__main__':
    main()
