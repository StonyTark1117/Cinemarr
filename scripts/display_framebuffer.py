"""Independent camera projection and pixel-color checks on original world captures."""
import hashlib
import math
from fractions import Fraction
from PIL import Image


def camera_basis(camera):
    x, y, z, yaw, pitch, fov = camera
    yaw, pitch = math.radians(yaw), math.radians(pitch)
    right = (-math.cos(yaw), 0, -math.sin(yaw))
    forward = (-math.sin(yaw)*math.cos(pitch), -math.sin(pitch), math.cos(yaw)*math.cos(pitch))
    up = (-math.sin(yaw)*math.sin(pitch), math.cos(pitch), math.cos(yaw)*math.sin(pitch))
    return (x, y, z), right, up, forward, fov


def dot(a, b):
    return sum(x*y for x, y in zip(a, b))


def project(point, camera, size):
    eye, right, up, forward, fov = camera_basis(camera)
    delta = tuple(a-b for a, b in zip(point, eye))
    depth = dot(delta, forward)
    if depth <= 0:
        raise ValueError('TV is behind the acceptance camera')
    focal = size[1] / (2*math.tan(math.radians(fov)/2))
    return size[0]/2 + focal*dot(delta, right)/depth, size[1]/2 - focal*dot(delta, up)/depth


def source_point(u, v, sw, sh, width, height, layout):
    """Centered normalized coordinates; independent of the Java transform."""
    aspect = Fraction(width*sh, height*sw)
    x, y = u/width-Fraction(1, 2), v/height-Fraction(1, 2)
    if layout == 'FIT':
        if aspect > 1: x *= aspect
        else: y /= aspect
    elif layout == 'FILL':
        if aspect > 1: y /= aspect
        else: x *= aspect
    elif layout != 'STRETCH':
        raise ValueError('Unknown layout')
    if abs(x) > Fraction(1, 2) or abs(y) > Fraction(1, 2):
        return None
    return (x+Fraction(1, 2))*sw, (y+Fraction(1, 2))*sh


def block_color(source, sw, sh, width, height, layout, x, y):
    point = source_point(Fraction(2*x+1, 2), Fraction(2*y+1, 2), sw, sh, width, height, layout)
    if point is None:
        return (0, 0, 0)
    sx, sy = (max(0, min(limit-1, p-Fraction(1, 2))) for p, limit in zip(point, (sw, sh)))
    x0, y0 = int(sx), int(sy)
    x1, y1 = min(sw-1, x0+1), min(sh-1, y0+1)
    fx, fy = sx-x0, sy-y0
    result = []
    for channel in range(3):
        value = sum(source[(yy*sw+xx)*4+channel]*weight for xx, yy, weight in (
            (x0, y0, (1-fx)*(1-fy)), (x1, y0, fx*(1-fy)),
            (x0, y1, (1-fx)*fy), (x1, y1, fx*fy)))
        result.append(math.floor(value+Fraction(1, 2)))
    return tuple(result)


def cell_samples(metadata, size):
    if metadata['facing'] != 'SOUTH':
        raise ValueError('Expected the controlled South-facing scene')
    width, height = metadata['width'], metadata['height']
    mask = int.from_bytes(bytes.fromhex(metadata['mask']), 'little')
    for y in range(height):
        for x in range(width):
            visible = bool(mask & (1 << (y*width+x)))
            for du, dv in ((.25,.25),(.75,.25),(.25,.75),(.75,.75)):
                point = (metadata['minimumU']+x+du,metadata['minimumV']+y+dv,metadata['plane']+1.002)
                px, py = (int(v) for v in project(point,metadata['camera'],size))
                if not 0 <= px < size[0] or not 0 <= py < size[1]:
                    raise ValueError('Screen cell is outside the original framebuffer')
                yield x,y,visible,px,py


def difference(a, b):
    return max(abs(x-y) for x,y in zip(a,b))


def verify_mask_frame(image_path, background_path, metadata, tolerance=4):
    image=Image.open(image_path).convert('RGB');background=Image.open(background_path).convert('RGB')
    if image.size != background.size:
        raise ValueError('Background capture has different dimensions')
    failures=[];holes=set();checks=changed=0
    for x,y,visible,px,py in cell_samples(metadata,image.size):
        actual=image.getpixel((px,py));expected=background.getpixel((px,py))
        delta=difference(actual,expected)
        if visible:
            if delta > tolerance: changed+=1
        else:
            holes.add((x,y));checks+=1
            if delta > tolerance:
                failures.append(dict(cell=[x,y],pixel=[px,py],expectedBackground=expected,actual=actual))
    if not holes or changed < 4:
        raise ValueError('Background receipt must contain holes and visibly omit the video quads')
    return dict(passed=not failures,hiddenCells=len(holes),holeChecks=checks,backgroundChangedSamples=changed,
                mismatches=len(failures),examples=failures[:12],tolerance=tolerance)


def verify_pixel_frame(image_path, metadata, source, tolerance=4, background_path=None):
    if metadata['mapping'] != 'ONE_PIXEL_PER_BLOCK':
        raise ValueError('Expected the controlled pixel-mode scene')
    sw,sh=metadata['sourceWidth'],metadata['sourceHeight']
    if len(source)!=sw*sh*4 or hashlib.sha256(source).hexdigest()!=metadata['sourceSha256']:
        raise ValueError('Source receipt hash or dimensions do not match')
    image=Image.open(image_path).convert('RGB');checks=0;failures=[];holes=set();colors={}
    for x,y,visible,px,py in cell_samples(metadata,image.size):
        if not visible:
            holes.add((x,y));continue
        if (x,y) not in colors:
            colors[x,y]=block_color(source,sw,sh,metadata['width'],metadata['height'],metadata['layout'],x,metadata['height']-1-y)
        expected=colors[x,y];actual=image.getpixel((px,py));checks+=1
        if difference(actual,expected)>tolerance:
            failures.append(dict(cell=[x,y],pixel=[px,py],expected=expected,actual=actual))
    if not checks:raise ValueError('No visible interior samples checked')
    mask=dict(passed=True,holeChecks=0,mismatches=0,examples=[])
    if holes:
        if background_path is None:raise ValueError('Masked scene requires an original background capture')
        mask=verify_mask_frame(image_path,background_path,metadata,tolerance)
    return dict(passed=not failures and mask['passed'],checks=checks,hiddenCells=len(holes),holeChecks=mask['holeChecks'],
                mismatches=len(failures)+mask['mismatches'],examples=(failures+mask['examples'])[:12],tolerance=tolerance,mask=mask)
