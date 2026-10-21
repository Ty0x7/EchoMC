"""Resting camera-space geometry check, not an in-game rendering test."""
from pathlib import Path
import itertools
import json
import math

root = Path(__file__).resolve().parents[1]
model = json.loads((root / 'src/main/resources/assets/echo/models/item/cube.json').read_text())
results = []
for hand, sign in [('right', 1), ('left', -1)]:
    t = model['display'][f'firstperson_{hand}hand']
    x, y, z = map(math.radians, t['rotation'])
    y *= sign
    z *= sign
    def rotate(v):
        # Minecraft Transformation uses JOML rotationXYZ: Rx * Ry * Rz.
        a, b, c = v
        a, b = math.cos(z)*a-math.sin(z)*b, math.sin(z)*a+math.cos(z)*b
        a, c = math.cos(y)*a+math.sin(y)*c, -math.sin(y)*a+math.cos(y)*c
        b, c = math.cos(x)*b-math.sin(x)*c, math.sin(x)*b+math.cos(x)*c
        return a, b, c
    center = (sign*(.56+t['translation'][0]/16), -.52+t['translation'][1]/16, -.72+t['translation'][2]/16)
    camera = tuple(-v for v in center)
    extent = 5 / 32 * t['scale'][0]
    def facing(normal):
        return sum(a*b for a,b in zip(rotate(normal), camera))-extent
    front, bottom = facing((0,0,-1)), facing((0,-1,0))
    assert front > 0 and bottom < 0, (hand, front, bottom)
    corners = []
    for corner in itertools.product([-extent, extent], repeat=3):
        point = tuple(a+b for a,b in zip(center,rotate(corner)))
        corners.append((point[0]/-point[2], point[1]/-point[2]))
    # Native first-person item projection uses 70 degrees; desktop 16:9 framing.
    vertical = math.tan(math.radians(35))
    bounds = [min(p[0] for p in corners), max(p[0] for p in corners), min(p[1] for p in corners), max(p[1] for p in corners)]
    assert max(abs(bounds[0]),abs(bounds[1])) < vertical*16/9, bounds
    assert max(abs(bounds[2]),abs(bounds[3])) < vertical, bounds
    results.append(dict(hand=hand,front_visible=front>0,bottom_visible=bottom>0,scale=t['scale'][0],projection_bounds=bounds))
(root/'build/held-pose-check.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results,indent=2))
