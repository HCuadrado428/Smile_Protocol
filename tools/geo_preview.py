#!/usr/bin/env python3
"""
Vista previa rapida (sin Minecraft) de los modelos GeckoLib: dibuja cada texel como un
cuadradito en 3D con el algoritmo del pintor y una luz sencilla.

    python3 tools/geo_preview.py salida.png gurney curtain locker ...
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol"
SCALE = 2


def rot_matrix(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X


def face_points(o, s, face, u, v):
    """Punto 3D de la cara para (u, v) en unidades de modelo (misma convencion que geo_models.Face)."""
    ox, oy, oz = o
    sx, sy, sz = s
    if face == "north":
        return (ox + sx - u, oy + sy - v, oz)
    if face == "south":
        return (ox + u, oy + sy - v, oz + sz)
    if face == "east":
        return (ox + sx, oy + sy - v, oz + sz - u)
    if face == "west":
        return (ox, oy + sy - v, oz + u)
    if face == "up":
        return (ox + u, oy + sy, oz + v)
    return (ox + u, oy, oz + sz - v)


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
           "down": (0, -1, 0)}


def render(name, size=360, yaw=35, pitch=-25, light=1.0):
    geo = json.loads((ROOT / "geo/block" / f"{name}.geo.json").read_text())["minecraft:geometry"][0]
    tex = np.asarray(Image.open(ROOT / "textures/block" / f"{name}.png").convert("RGBA"))
    cam = rot_matrix(pitch, 0, 0) @ rot_matrix(0, yaw, 0)
    ldir = np.array([0.4, 0.8, 0.5])
    ldir /= np.linalg.norm(ldir)
    quads = []
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            R = np.eye(3)
            piv = np.zeros(3)
            if "rotation" in c:
                rx, ry, rz = c["rotation"]
                R = rot_matrix(-rx, -ry, rz)
                piv = np.array(c["pivot"])
            for face, uvd in c["uv"].items():
                u0, v0 = uvd["uv"]
                rw, rh = uvd["uv_size"]
                n = R @ np.array(NORMALS[face], dtype=float)
                if (cam @ n)[2] > 0.05:  # de espaldas a la camara
                    continue
                shade = 0.45 + 0.55 * max(0, n @ ldir) * light
                nu, nv = max(1, int(round(rw * SCALE))), max(1, int(round(rh * SCALE)))
                for j in range(nv):
                    for i in range(nu):
                        px = tex[int(v0 * SCALE) + j, int(u0 * SCALE) + i]
                        if px[3] < 128:
                            continue
                        pts = []
                        for du, dv in ((0, 0), (1, 0), (1, 1), (0, 1)):
                            p = np.array(face_points(o, s, face, (i + du) / SCALE, (j + dv) / SCALE))
                            p = R @ (p - piv) + piv
                            pts.append(cam @ p)
                        z = np.mean([p[2] for p in pts])
                        col = tuple(int(min(255, k * shade)) for k in px[:3])
                        quads.append((z, [(-p[0], -p[1]) for p in pts], col))
    quads.sort(key=lambda q: -q[0])
    allp = np.array([p for q in quads for p in q[1]])
    mn, mx = allp.min(0), allp.max(0)
    k = (size - 20) / max(mx - mn)
    img = Image.new("RGB", (size, size), (34, 34, 38))
    d = ImageDraw.Draw(img)
    off = (size - (mx - mn) * k) / 2
    for z, pts, col in quads:
        d.polygon([((x - mn[0]) * k + off[0], (y - mn[1]) * k + off[1]) for x, y in pts], fill=col)
    return img


def main():
    out, names = sys.argv[1], sys.argv[2:]
    ims = []
    for n in names:
        ims.append(render(n, yaw=35))
        ims.append(render(n, yaw=-140))
    cols = 4
    rows = (len(ims) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 360, rows * 360), (20, 20, 20))
    for i, im in enumerate(ims):
        sheet.paste(im, ((i % cols) * 360, (i // cols) * 360))
    sheet.save(out)


if __name__ == "__main__":
    main()
