"""Independent framebuffer oracle for annotated, original block-raster captures."""
import math


def bind_evidence(annotation, report):
    phases = [p for p in report['phases'] if p['phase'] == annotation['phase']]
    if len(phases) != 1 or annotation['role'] not in ('leader', 'follower'):
        raise ValueError('Exactly one recorded phase and a known client role are required')
    phase = phases[0]
    tv = annotation['television']
    state, receipt = phase['televisions'][tv], phase['rendered'][annotation['role']][tv]
    captures = [c for c in report['captures'] if c['path'] == annotation['capture']]
    expected_name = annotation['phase'] + '-' + annotation['role'] + '.png'
    if (len(captures) != 1 or annotation['capture'] != expected_name
            or annotation['captureSha256'] != captures[0]['sha256']
            or annotation['sourceSha256'] != receipt['sha256']
            or state['status'] != 'PAUSED' or state['mapping'] != 'ONE_PIXEL_PER_BLOCK'
            or state['revision'] != receipt['revision'] or state['layout'] != annotation['layout']
            or (annotation['sourceWidth'], annotation['sourceHeight']) != (receipt['decodedWidth'], receipt['decodedHeight'])
            or (annotation['screenWidth'], annotation['screenHeight']) != (receipt['width'], receipt['height'])
            or state['screen'] != str(annotation['screenWidth']) + 'x' + str(annotation['screenHeight'])):
        raise ValueError('Annotation does not match the original paused pixel-mode evidence')


def projective_map(corners):
    """Map normalized TV coordinates to an annotated TL, TR, BR, BL quad."""
    if len(corners) != 4 or any(len(p) != 2 or not all(math.isfinite(v) for v in p) for p in corners):
        raise ValueError('Four finite screen corners are required')
    for i in range(4):
        a, b, c = corners[i], corners[(i+1)%4], corners[(i+2)%4]
        if (b[0]-a[0])*(c[1]-b[1])-(b[1]-a[1])*(c[0]-b[0]) <= 0:
            raise ValueError('Screen corners must form a convex clockwise image quad')
    rows = []
    for (u, v), (x, y) in zip(((0, 0), (1, 0), (1, 1), (0, 1)), corners):
        rows += [[u, v, 1, 0, 0, 0, -x*u, -x*v, x],
                 [0, 0, 0, u, v, 1, -y*u, -y*v, y]]
    for column in range(8):
        pivot = max(range(column, 8), key=lambda i: abs(rows[i][column]))
        rows[column], rows[pivot] = rows[pivot], rows[column]
        divisor = rows[column][column]
        if abs(divisor) < 1e-10:
            raise ValueError('Degenerate screen annotation')
        rows[column] = [value/divisor for value in rows[column]]
        for index in range(8):
            if index != column:
                factor = rows[index][column]
                rows[index] = [a-factor*b for a, b in zip(rows[index], rows[column])]
    h = [row[-1] for row in rows]
    def point(u, v):
        denominator = h[6]*u + h[7]*v + 1
        if denominator <= 0:
            raise ValueError('Screen annotation crosses projection plane')
        return ((h[0]*u+h[1]*v+h[2])/denominator,
                (h[3]*u+h[4]*v+h[5])/denominator)
    return point


def expected_cell(rgba, sw, sh, width, height, layout, x, y):
    """Calculate source-space cell centers independently of the Java sampler."""
    if layout == 'STRETCH':
        scale_x, scale_y = width/sw, height/sh
    elif layout in ('FIT', 'FILL'):
        scale_x = scale_y = (min if layout == 'FIT' else max)(width/sw, height/sh)
    else:
        raise ValueError('Unknown layout')
    left, top = (width-sw*scale_x)/2, (height-sh*scale_y)/2
    px, py = (x+.5-left)/scale_x, (y+.5-top)/scale_y
    if not (0 <= px < sw and 0 <= py < sh):
        return (0, 0, 0)
    px, py = min(sw-1, max(0, px-.5)), min(sh-1, max(0, py-.5))
    ix, iy = math.floor(px), math.floor(py)
    result = []
    for channel in range(3):
        value = 0
        for sy, wy in ((iy, 1-(py-iy)), (min(sh-1, iy+1), py-iy)):
            for sx, wx in ((ix, 1-(px-ix)), (min(sw-1, ix+1), px-ix)):
                value += rgba[4*(sy*sw+sx)+channel]*wx*wy
        result.append(math.floor(value+.5))
    return tuple(result)


def inspect_capture(rgb, image_width, image_height, rgba, source_width, source_height,
                    width, height, layout, corners, excluded=(), tolerance=10):
    if not (0 <= tolerance <= 12):
        raise ValueError('Color tolerance must be between zero and twelve')
    if min(image_width, image_height, source_width, source_height, width, height) < 1:
        raise ValueError('Positive dimensions required')
    if len(rgb) != image_width*image_height*3 or len(rgba) != source_width*source_height*4:
        raise ValueError('Pixel extent does not match declared dimensions')
    if width*height > 65536:
        raise ValueError('Capture oracle supports at most 65536 cells')
    if any(not (0 <= x < image_width and 0 <= y < image_height) for x, y in corners):
        raise ValueError('Screen corners must be inside the original capture')
    omitted = {}
    for item in excluded:
        cell = tuple(item['cell'])
        if (len(cell) != 2 or cell in omitted or not str(item.get('reason', '')).strip()
                or not (0 <= cell[0] < width and 0 <= cell[1] < height)):
            raise ValueError('Every excluded cell needs a unique coordinate and an occlusion reason')
        omitted[cell] = item['reason']
    if len(omitted)*4 > width*height:
        raise ValueError('At least three quarters of the screen must be inspected')
    project = projective_map(corners)
    cells, failures = [], []
    for y in range(height):
        for x in range(width):
            if (x, y) in omitted:
                continue
            expected = expected_cell(rgba, source_width, source_height, width, height, layout, x, y)
            points = [tuple(round(v) for v in project((x+u)/width, (y+w)/height))
                      for u, w in ((.3, .3), (.7, .3), (.5, .5), (.3, .7), (.7, .7))]
            if len(set(points)) != 5 or any(not (0 <= px < image_width and 0 <= py < image_height) for px, py in points):
                raise ValueError('Insufficient visible block interior pixels for five independent samples')
            colors = [tuple(rgb[3*(py*image_width+px):3*(py*image_width+px)+3]) for px, py in points]
            error = max(abs(color[c]-expected[c]) for color in colors for c in range(3))
            variation = max(max(color[c] for color in colors)-min(color[c] for color in colors) for c in range(3))
            cell = {'cell': [x, y], 'expected': expected, 'points': points, 'observed': colors,
                    'maximumColorError': error, 'withinCellVariation': variation}
            cells.append(cell)
            if error > tolerance or variation > 2:
                failures.append([x, y])
    return {'accepted': not failures, 'cells': cells, 'failedCells': failures,
            'excludedCells': list(excluded), 'colorTolerance': tolerance, 'uniformityTolerance': 2}
