#!/usr/bin/env python3
"""Compare five interior pixels per visible block with an independent RGBA oracle.

Annotation JSON names capture, sourceRgba (relative to the annotation), their
SHA-256 hashes, sourceWidth/Height, screenWidth/Height, layout, and corners
(top-left, top-right, bottom-right, bottom-left in original capture pixels).
Optional excludedCells must document physical occlusion. Never annotate from
expected colors: mark the physical screen boundaries in the original capture.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
from block_raster_capture import bind_evidence, inspect_capture
from png_capture import read_capture


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('annotation', type=Path)
    parser.add_argument('--evidence', type=Path, required=True, help='Original display.json from the enclosing gate')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    annotation_bytes = args.annotation.read_bytes()
    spec = json.loads(annotation_bytes)
    evidence_bytes = args.evidence.read_bytes()
    bind_evidence(spec, json.loads(evidence_bytes))
    capture = read_capture(args.annotation.parent/spec['capture'])
    iw, ih = struct.unpack_from('>II', capture, 16)
    source_path = args.annotation.parent/spec['sourceRgba']
    expected_bytes = spec['sourceWidth']*spec['sourceHeight']*4
    if not 0 < expected_bytes <= 64*1024*1024 or source_path.stat().st_size != expected_bytes:
        raise ValueError('Source raster is not bounded or has the wrong extent')
    source = source_path.read_bytes()
    for label, value in (('captureSha256', capture), ('sourceSha256', source)):
        if hashlib.sha256(value).hexdigest() != spec[label]:
            raise ValueError('Original evidence hash mismatch: ' + label)
    pixels = subprocess.run(['ffmpeg', '-v', 'error', '-i', 'pipe:0', '-frames:v', '1',
                             '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'], input=capture,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30, check=True).stdout
    result = inspect_capture(pixels, iw, ih, source, spec['sourceWidth'], spec['sourceHeight'],
                             spec['screenWidth'], spec['screenHeight'], spec['layout'], spec['corners'],
                             spec.get('excludedCells', ()))
    result.update({'annotationSha256': hashlib.sha256(annotation_bytes).hexdigest(),
                   'displayEvidenceSha256': hashlib.sha256(evidence_bytes).hexdigest(),
                   'captureSha256': spec['captureSha256'], 'sourceSha256': spec['sourceSha256'],
                   'annotationRequiresDirectReview': True})
    with args.output.open('x') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    if not result['accepted']:
        raise SystemExit('Raster capture failed; see retained cell measurements')


if __name__ == '__main__':
    main()
