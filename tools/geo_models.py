#!/usr/bin/env python3
"""
Modelos GeckoLib (geo.json + textura) de los mecanismos y escondites:
puertas de laboratorio y de seguridad, taquilla, armario, contenedor, camilla, cortina,
valvula y caja de fusibles.

Cada modelo se construye con cubos y materiales; el script reparte las caras en la textura
(sin solapes), las pinta con el material y escribe el geo.json con la UV exacta de cada cara.
Asi el modelo y la textura siempre encajan. Resolucion: 2 pixeles de textura por pixel de modelo.

Las piezas que se mueven (hoja de puerta, rueda, tapa...) son simetricas en X: el renderer
las gira desde codigo alrededor de su bisagra.

    python3 tools/geo_models.py
"""
import json
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol"
SCALE = 2  # pixeles de textura por unidad de modelo
FACES = ("north", "south", "east", "west", "up", "down")


# Materiales ---------------------------------------------------------------------------------------

def _noise(h, w, rnd, amt):
    return np.array([[rnd.uniform(-amt, amt) for _ in range(w)] for _ in range(h)])


def solid(color, noise=6, grime=0.0, streak=0.0, brushed=False, bevel=True):
    def paint(a, face, rnd):
        h, w = a.shape[:2]
        a[:, :, :3] = color
        a[:, :, 3] = 255
        a[:, :, :3] += _noise(h, w, rnd, noise)[..., None]
        if brushed:
            for y in range(0, h, 2):
                a[y, :, :3] *= 0.96
        if grime:
            for _ in range(int(w * h * grime)):
                x, y = rnd.randrange(w), rnd.randrange(h)
                k = rnd.uniform(0.15, 0.45)
                a[y, x, :3] = a[y, x, :3] * (1 - k) + np.array([45, 38, 30]) * k
        if streak and face not in ("up", "down"):
            for _ in range(max(1, int(w * streak))):
                x = rnd.randrange(w)
                y0 = rnd.randrange(max(1, h // 3))
                ln = rnd.randint(2, max(3, h // 2))
                for i in range(ln):
                    if y0 + i < h:
                        k = 0.35 * (1 - i / ln)
                        a[y0 + i, x, :3] = a[y0 + i, x, :3] * (1 - k) + np.array([110, 64, 32]) * k
        if bevel and h > 3 and w > 3:
            a[0, :, :3] *= 1.15
            a[:, 0, :3] *= 1.1
            a[h - 1, :, :3] *= 0.75
            a[:, w - 1, :3] *= 0.8
    return paint


def stripes(c1=(222, 182, 32), c2=(30, 28, 26)):
    def paint(a, face, rnd):
        h, w = a.shape[:2]
        for y in range(h):
            for x in range(w):
                a[y, x, :3] = c1 if ((x + y) // 3) % 2 == 0 else c2
        a[:, :, 3] = 255
        a[:, :, :3] += _noise(h, w, rnd, 8)[..., None]
    return paint


def wire_glass(base=(34, 46, 52), wire=(70, 84, 90)):
    def paint(a, face, rnd):
        h, w = a.shape[:2]
        a[:, :, :3] = base
        a[:, :, 3] = 255
        for y in range(h):
            for x in range(w):
                if x % 4 == 0 or y % 4 == 0:
                    a[y, x, :3] = wire
                # reflejo diagonal
                if 0 <= (x - y) - w // 4 <= 1:
                    a[y, x, :3] = np.minimum(255, a[y, x, :3] + 40)
    return paint


def vents(base=(98, 112, 122)):
    def paint(a, face, rnd):
        solid(base, 5, 0.02)(a, face, rnd)
        h, w = a.shape[:2]
        if face in ("north", "south"):
            for y in range(1, h - 1, 3):
                a[y, 1:w - 1, :3] = (14, 14, 16)
                if y + 1 < h:
                    a[y + 1, 1:w - 1, :3] = np.minimum(255, np.array(base) * 1.2)
    return paint


def biohazard():
    def paint(a, face, rnd):
        h, w = a.shape[:2]
        a[:, :, :3] = (230, 200, 40)
        a[:, :, 3] = 255
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                d = math.hypot(x - cx, y - cy)
                ang = math.atan2(y - cy, x - cx)
                if d < min(w, h) * 0.12:
                    a[y, x, :3] = (20, 20, 20)
                elif d < min(w, h) * 0.42 and math.cos(3 * ang) > 0.35:
                    a[y, x, :3] = (20, 20, 20)
        a[0, :, :3] = a[-1, :, :3] = (20, 20, 20)
        a[:, 0, :3] = a[:, -1, :3] = (20, 20, 20)
    return paint


def cloth(color=(150, 178, 160)):
    def paint(a, face, rnd):
        h, w = a.shape[:2]
        for x in range(w):
            k = 0.85 + 0.15 * math.cos(x / max(1, w) * math.pi * 2)
            a[:, x, :3] = np.array(color) * k
        a[:, :, 3] = 255
        a[:, :, :3] += _noise(h, w, rnd, 5)[..., None]
        for _ in range(w * h // 40):
            x, y = rnd.randrange(w), rnd.randrange(h)
            a[y, x, :3] *= 0.8
    return paint


def mattress():
    def paint(a, face, rnd):
        h, w = a.shape[:2]
        a[:, :, :3] = (200, 204, 200)
        a[:, :, 3] = 255
        a[:, :, :3] += _noise(h, w, rnd, 5)[..., None]
        for y in range(0, h, 6):
            a[y, :, :3] *= 0.85
        if face == "up":
            for _ in range(w * h // 18):
                x = rnd.randint(w // 3, 2 * w // 3)
                y = rnd.randint(h // 3, 2 * h // 3)
                a[y, x, :3] = (112, 54, 46)
    return paint


def lamp(color):
    def paint(a, face, rnd):
        a[:, :, :3] = color
        a[:, :, 3] = 255
        h, w = a.shape[:2]
        a[: max(1, h // 2), : max(1, w // 2), :3] = np.minimum(255, np.array(color) + 70)
    return paint


MAT = {
    "paint_white": solid((210, 214, 208), 5, 0.03, 0.08),
    "paint_white_dark": solid((170, 174, 168), 5, 0.03),
    "steel": solid((150, 156, 160), 6, 0.02, 0.05, brushed=True),
    "steel_dark": solid((84, 92, 96), 6, 0.03, 0.12, brushed=True),
    "chrome": solid((196, 200, 204), 4, 0.0, brushed=True),
    "rubber": solid((34, 34, 36), 3),
    "locker": solid((88, 104, 116), 6, 0.03, 0.1),
    "locker_door": solid((104, 122, 136), 6, 0.03, 0.08),
    "locker_inner": solid((44, 50, 56), 4, 0.02, bevel=False),
    "vents": vents(),
    "wood": solid((112, 80, 52), 9, 0.03),
    "wood_dark": solid((84, 58, 38), 8, 0.03),
    "bin_green": solid((62, 104, 64), 7, 0.04, 0.1),
    "bin_lid": solid((48, 84, 50), 6, 0.04),
    "biohazard": biohazard(),
    "mattress": mattress(),
    "sheet": cloth((214, 218, 212)),
    "cloth": cloth(),
    "wheel_red": solid((172, 34, 30), 6, 0.02),
    "pipe_rust": solid((112, 92, 76), 9, 0.06, 0.4),
    "amber": lamp((230, 150, 40)),
    "lamp_green": lamp((60, 230, 110)),
    "lamp_red": lamp((200, 36, 36)),
    "stripes": stripes(),
    "glass": wire_glass(),
    "black": solid((16, 16, 18), 2, bevel=False),
    "label": solid((232, 228, 206), 3),
}


# Modelo ----------------------------------------------------------------------------------------------

class Geo:
    def __init__(self, name, seed=0):
        self.name = name
        self.bones = []
        self.rnd = random.Random(seed)

    def bone(self, name, parent=None, pivot=(0, 0, 0)):
        b = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent:
            b["parent"] = parent
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, mat, skip=()):
        b = next(x for x in self.bones if x["name"] == bone)
        b["cubes"].append({"origin": list(origin), "size": list(size), "mat": mat, "skip": set(skip)})

    def save(self):
        # Caras a repartir: (bone, cubo, cara, ancho, alto) en unidades de modelo
        faces = []
        for b in self.bones:
            for c in b["cubes"]:
                sx, sy, sz = c["size"]
                dims = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
                        "up": (sx, sz), "down": (sx, sz)}
                c["uv"] = {}
                for f in FACES:
                    if f in c["skip"]:
                        continue
                    w, h = dims[f]
                    faces.append((c, f, max(1, math.ceil(w)), max(1, math.ceil(h)), w, h))
        # Estantes por altura
        faces.sort(key=lambda t: -t[3])
        W = 64
        while True:
            x = y = row = 0
            ok = True
            for c, f, w, h, rw, rh in faces:
                if w > W:
                    ok = False
                    break
                if x + w > W:
                    x, y, row = 0, y + row, 0
                c["uv"][f] = (x, y, rw, rh, w, h)
                x += w + 0
                row = max(row, h)
            Ht = y + row
            if ok and Ht <= W * 2:
                break
            W *= 2
        TW, TH = W, max(16, 1 << math.ceil(math.log2(max(1, Ht))))
        img = np.zeros((TH * SCALE, TW * SCALE, 4), dtype=np.float32)
        for c, f, w, h, rw, rh in faces:
            u, v, _, _, pw, ph = c["uv"][f]
            reg = np.zeros((ph * SCALE, pw * SCALE, 4), dtype=np.float32)
            MAT[c["mat"]](reg, f, self.rnd)
            np.clip(reg, 0, 255, out=reg)
            img[v * SCALE:(v + ph) * SCALE, u * SCALE:(u + pw) * SCALE] = reg

        bones_out = []
        for b in self.bones:
            out = {"name": b["name"], "pivot": b["pivot"]}
            if "parent" in b:
                out["parent"] = b["parent"]
            cubes = []
            for c in b["cubes"]:
                uv = {}
                for f, (u, v, rw, rh, _, _) in c["uv"].items():
                    uv[f] = {"uv": [u, v], "uv_size": [rw, rh]}
                cubes.append({"origin": c["origin"], "size": c["size"], "uv": uv})
            if cubes:
                out["cubes"] = cubes
            bones_out.append(out)
        geo = {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {"identifier": f"geometry.{self.name}", "texture_width": TW, "texture_height": TH,
                                "visible_bounds_width": 4, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
                "bones": bones_out,
            }],
        }
        (ROOT / "geo/block").mkdir(parents=True, exist_ok=True)
        (ROOT / "geo/block" / f"{self.name}.geo.json").write_text(json.dumps(geo, indent=1) + "\n", encoding="utf-8")
        (ROOT / "animations/block").mkdir(parents=True, exist_ok=True)
        (ROOT / "animations/block" / f"{self.name}.animation.json").write_text(
            json.dumps({"format_version": "1.8.0", "animations": {}}) + "\n", encoding="utf-8")
        Image.fromarray(img.astype(np.uint8), "RGBA").save(ROOT / "textures/block" / f"{self.name}.png")
        return TW, TH, len(faces)


# Modelos ---------------------------------------------------------------------------------------------
# Coordenadas en pixeles: el bloque va de -8 a 8 en X y Z y de 0 a 16 en Y. El frente es +Z.

def lab_door(name, security):
    g = Geo(name, 3 if security else 2)
    g.bone("root")
    g.bone("leaf", "root")
    body = "steel_dark" if security else "paint_white"
    z0, t = -8, 3
    # Marco de la hoja y paneles
    g.cube("leaf", (-8, 0, z0), (16, 32, t), body)
    g.cube("leaf", (-7, 4, z0 - 0.2), (14, 1, t + 0.4), "paint_white_dark" if not security else "steel")
    # Ventana con malla de alambre
    if security:
        g.cube("leaf", (-3, 20, z0 - 0.1), (6, 7, t + 0.2), "glass")
    else:
        g.cube("leaf", (-5, 16, z0 - 0.1), (10, 12, t + 0.2), "glass")
        g.cube("leaf", (-5.5, 15.5, z0 - 0.3), (11, 0.5, t + 0.6), "paint_white_dark")
        g.cube("leaf", (-5.5, 28, z0 - 0.3), (11, 0.5, t + 0.6), "paint_white_dark")
    # Barra o placa de empuje (simetrica)
    if security:
        g.cube("leaf", (-6, 13, z0 - 0.8), (12, 1.5, t + 1.6), "chrome")
        g.cube("leaf", (-6.5, 12.5, z0 - 0.5), (1, 2.5, t + 1.0), "steel")
        g.cube("leaf", (5.5, 12.5, z0 - 0.5), (1, 2.5, t + 1.0), "steel")
    else:
        g.cube("leaf", (-2.5, 13, z0 - 0.3), (5, 3, t + 0.6), "chrome")
    # Placa de patada
    g.cube("leaf", (-8, 0, z0 - 0.15), (16, 4, t + 0.3), "stripes" if security else "steel")
    return g.save()


def locker(name, body, door, wood=False):
    g = Geo(name, 5)
    g.bone("root")
    g.bone("body", "root")
    g.bone("door", "root", (7, 0, 7))
    inner = "locker_inner"
    g.cube("body", (-8, 0, -8), (16, 32, 1), body)                 # fondo
    g.cube("body", (-8, 0, -7), (1, 32, 15), body)                 # lados
    g.cube("body", (7, 0, -7), (1, 32, 15), body)
    g.cube("body", (-7, 31, -7), (14, 1, 15), body)                # techo
    g.cube("body", (-7, 0, -7), (14, 1, 15), body)                 # suelo
    g.cube("body", (-7, 1, -7), (14, 30, 0.2), inner)              # interior oscuro
    g.cube("body", (-7, 22, -6), (14, 0.5, 6), inner)              # balda
    # Puerta (simetrica en X)
    g.cube("door", (-7, 1, 7), (14, 30, 1), door)
    if not wood:
        g.cube("door", (-4.5, 20, 7.6), (9, 8, 0.6), "vents")
        g.cube("door", (-4.5, 3, 7.6), (9, 5, 0.6), "vents")
        g.cube("door", (-1.5, 27.5, 7.7), (3, 2, 0.5), "label")
    else:
        g.cube("door", (-6, 3, 7.4), (12, 12, 0.6), "wood_dark")
        g.cube("door", (-6, 17, 7.4), (12, 12, 0.6), "wood_dark")
    g.cube("door", (-1, 13, 8), (2, 4, 0.8), "chrome")
    return g.save()


def waste_container():
    g = Geo("waste_container", 7)
    g.bone("root")
    g.bone("body", "root")
    g.bone("lid", "root", (0, 26, -7.5))
    g.cube("body", (-7, 1.5, -7), (14, 24.5, 1), "bin_green")
    g.cube("body", (-7, 1.5, 6), (14, 24.5, 1), "bin_green")
    g.cube("body", (-7, 1.5, -6), (1, 24.5, 12), "bin_green")
    g.cube("body", (6, 1.5, -6), (1, 24.5, 12), "bin_green")
    g.cube("body", (-6, 1.5, -6), (12, 1, 12), "locker_inner")
    g.cube("body", (-7.5, 24, -7.5), (15, 1, 15), "bin_lid", skip=("up",))   # borde
    g.cube("body", (-3, 12, 7), (6, 6, 0.4), "biohazard")
    g.cube("body", (-7.5, 6, -7.5), (15, 1, 15), "steel_dark", skip=("up", "down"))  # refuerzo
    for x, z in ((-6, -6), (4, -6), (-6, 4), (4, 4)):
        g.cube("body", (x, 0, z), (2, 1.5, 2), "rubber")
    g.cube("lid", (-7.5, 25, -7.5), (15, 1.5, 15), "bin_lid")
    g.cube("lid", (-3, 26.5, 5), (6, 1, 1.5), "black")             # asa
    return g.save()


def gurney():
    g = Geo("gurney", 8)
    g.bone("root")
    g.cube("root", (-7.5, 9, -7.5), (15, 1, 15), "steel")              # bandeja
    g.cube("root", (-7, 10, -7), (14, 2, 14), "mattress")
    g.cube("root", (-6.5, 12, 3), (13, 1.5, 4), "sheet")               # almohada
    g.cube("root", (-7.2, 11.8, -7.2), (14.4, 0.6, 9), "sheet")        # sabana
    for x in (-7.5, 6.5):                                              # barandillas
        g.cube("root", (x, 13, -6), (1, 1, 12), "chrome")
        g.cube("root", (x, 10, -6), (1, 3, 1), "chrome")
        g.cube("root", (x, 10, 5), (1, 3, 1), "chrome")
    for x, z in ((-7, -7), (6, -7), (-7, 6), (6, 6)):                  # patas y ruedas
        g.cube("root", (x, 1.5, z), (1, 7.5, 1), "chrome")
        g.cube("root", (x - 0.5, 0, z - 0.5), (2, 1.5, 2), "rubber")
    g.cube("root", (-7, 4, -0.5), (14, 1, 1), "steel")                 # travesano
    return g.save()


def curtain():
    g = Geo("curtain", 9)
    g.bone("root")
    g.bone("cloth", "root", (0, 31, 0))
    g.cube("root", (-8, 31, -0.5), (16, 1, 1), "chrome")
    for i in range(8):
        z = -1.2 if i % 2 == 0 else 0.2
        g.cube("cloth", (-8 + 2 * i, 1, z), (2, 30, 1), "cloth")
    for i in range(4):
        g.cube("root", (-7 + 4 * i, 30, -0.7), (1, 1.5, 1.4), "steel")  # anillas
    return g.save()


def valve():
    g = Geo("valve", 10)
    g.bone("root")
    g.bone("wheel", "root", (0, 8, -2))
    g.cube("root", (-5, 3, -8), (10, 10, 2), "pipe_rust")
    for x, y in ((-4, 4), (3, 4), (-4, 11), (3, 11)):
        g.cube("root", (x, y, -6.2), (1, 1, 0.4), "chrome")              # tornillos
    g.cube("root", (-1.5, 6.5, -6), (3, 3, 4.5), "steel")
    g.cube("root", (-8, 6, -8), (3, 4, 3), "pipe_rust")                  # tuberia a la pared
    g.cube("root", (5, 6, -8), (3, 4, 3), "pipe_rust")
    # Rueda
    g.cube("wheel", (-5, 12, -2.5), (10, 1.5, 1.5), "wheel_red")
    g.cube("wheel", (-5, 2.5, -2.5), (10, 1.5, 1.5), "wheel_red")
    g.cube("wheel", (-6, 3, -2.5), (1.5, 10, 1.5), "wheel_red")
    g.cube("wheel", (4.5, 3, -2.5), (1.5, 10, 1.5), "wheel_red")
    g.cube("wheel", (-4.5, 7.5, -2.2), (9, 1, 1), "wheel_red")
    g.cube("wheel", (-0.5, 3.5, -2.2), (1, 9, 1), "wheel_red")
    g.cube("wheel", (-1.5, 6.5, -2.8), (3, 3, 2), "chrome")
    return g.save()


def fuse_box():
    g = Geo("fuse_box", 11)
    g.bone("root")
    g.cube("root", (-6.5, 1, -8), (13, 14, 4), "steel_dark")
    g.cube("root", (-5.5, 2, -4.1), (11, 9.5, 0.2), "black")            # hueco interior
    g.cube("root", (-5.5, 12.3, -4.2), (11, 1.8, 0.3), "stripes")
    g.cube("root", (-6.8, 0.8, -8), (0.6, 14.4, 4.3), "steel")          # bisagras/canto
    g.cube("root", (6.2, 0.8, -8), (0.6, 14.4, 4.3), "steel")
    for i in range(4):
        x = -4.6 + i * 2.6
        g.cube("root", (x, 3, -4.4), (1.6, 0.8, 0.5), "chrome")         # contactos
        g.cube("root", (x, 9.4, -4.4), (1.6, 0.8, 0.5), "chrome")
        b = g.bone(f"fuse{i}", "root")
        g.cube(b, (x, 3.8, -4.6), (1.6, 5.6, 1.2), "amber")
        g.cube(b, (x - 0.1, 3.6, -4.7), (1.8, 0.8, 1.4), "chrome")
        g.cube(b, (x - 0.1, 8.8, -4.7), (1.8, 0.8, 1.4), "chrome")
    g.bone("lamp_on", "root")
    g.cube("lamp_on", (3.5, 12.4, -4.6), (1.6, 1.6, 0.8), "lamp_green")
    g.bone("lamp_off", "root")
    g.cube("lamp_off", (3.5, 12.4, -4.6), (1.6, 1.6, 0.8), "lamp_red")
    return g.save()


# Iconos de objeto (16x16) ------------------------------------------------------------------------------

def icon(name, draw):
    a = np.zeros((16, 16, 4), dtype=np.float32)
    draw(a)
    (ROOT / "textures/item").mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(ROOT / "textures/item" / f"{name}.png")


def box_icon(body, front_detail):
    def d(a):
        a[1:15, 4:12] = (*body, 255)
        a[1:15, 4] = (*[int(c * 1.2) for c in body], 255)
        a[1:15, 11] = (*[int(c * 0.7) for c in body], 255)
        front_detail(a)
    return d


def main():
    for name, fn in (("lab_door", lambda: lab_door("lab_door", False)),
                     ("security_door", lambda: lab_door("security_door", True)),
                     ("locker", lambda: locker("locker", "locker", "locker_door")),
                     ("wardrobe", lambda: locker("wardrobe", "wood", "wood", wood=True)),
                     ("waste_container", waste_container),
                     ("gurney", gurney), ("curtain", curtain), ("valve", valve), ("fuse_box", fuse_box)):
        tw, th, nf = fn()
        print(f"{name:16} textura {tw * SCALE}x{th * SCALE}  caras={nf}")

    def vents_d(a):
        for y in (3, 5, 7):
            a[y, 6:10] = (14, 14, 16, 255)
        a[9:11, 7:9] = (200, 200, 200, 255)
    icon("locker", box_icon((104, 122, 136), vents_d))
    icon("wardrobe", box_icon((112, 80, 52), lambda a: a.__setitem__((slice(8, 10), slice(7, 9)), (200, 180, 90, 255))))

    def bin_d(a):
        a[2:4, 3:13] = (48, 84, 50, 255)
        a[7:11, 6:10] = (230, 200, 40, 255)
        a[8:10, 7:9] = (20, 20, 20, 255)
    icon("waste_container", box_icon((62, 104, 64), bin_d))

    def gurney_d(a):
        a[6:8, 1:15] = (200, 204, 200, 255)
        a[8, 1:15] = (150, 156, 160, 255)
        for x in (2, 13):
            a[9:13, x] = (190, 194, 198, 255)
            a[13, x - 1:x + 2] = (34, 34, 36, 255)
    icon("gurney", gurney_d)

    def curtain_d(a):
        a[1, 1:15] = (190, 194, 198, 255)
        for x in range(1, 15):
            k = 0.85 + 0.15 * math.cos(x * 1.4)
            a[2:15, x] = (int(150 * k), int(178 * k), int(160 * k), 255)
    icon("curtain", curtain_d)

    def valve_d(a):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 5 <= d <= 6.8:
                    a[y, x] = (172, 34, 30, 255)
        a[7:9, 2:14] = (150, 30, 26, 255)
        a[2:14, 7:9] = (150, 30, 26, 255)
        a[6:10, 6:10] = (196, 200, 204, 255)
    icon("valve", valve_d)

    def fuse_d(a):
        a[2:14, 3:13] = (84, 92, 96, 255)
        a[4:12, 4:12] = (16, 16, 18, 255)
        for x in (5, 8, 10):
            a[5:11, x] = (230, 150, 40, 255)
        a[3, 10:12] = (60, 230, 110, 255)
    icon("fuse_box", fuse_d)


if __name__ == "__main__":
    main()
