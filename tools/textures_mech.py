#!/usr/bin/env python3
"""
Texturas de los mecanismos y escondites (fases 9 y 10): caja de fusibles, puertas, valvula,
taquilla, armario, contenedor, camilla, cortina y los objetos (fusible, llave, tarjetas, rueda).
Procedurales, 32x32 (los objetos 16x16), con desgaste y suciedad de laboratorio abandonado.

    python3 tools/textures_mech.py
"""
import random
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/textures"
BLOCK = ROOT / "block"
ITEM = ROOT / "item"


def rgba(c, a=255):
    return np.array([c[0], c[1], c[2], a], dtype=np.float32)


class Tex:
    def __init__(self, w, h=None, base=(0, 0, 0, 0), seed=0):
        self.w, self.h = w, h or w
        self.a = np.zeros((self.h, self.w, 4), dtype=np.float32)
        self.a[:, :] = base if len(base) == 4 else (*base, 255)
        self.r = random.Random(seed)

    def rect(self, x0, y0, x1, y1, c, a=255):
        self.a[max(0, y0):min(self.h, y1), max(0, x0):min(self.w, x1)] = rgba(c, a)

    def px(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = rgba(c, a)

    def shade(self, x0, y0, x1, y1, k):
        reg = self.a[max(0, y0):min(self.h, y1), max(0, x0):min(self.w, x1), :3]
        reg *= k
        np.clip(reg, 0, 255, out=reg)

    def noise(self, amount, x0=0, y0=0, x1=None, y1=None):
        x1 = x1 or self.w
        y1 = y1 or self.h
        n = np.array([[self.r.uniform(-amount, amount) for _ in range(x1 - x0)] for _ in range(y1 - y0)])
        reg = self.a[y0:y1, x0:x1, :3]
        reg += n[..., None]
        np.clip(reg, 0, 255, out=reg)

    def grime(self, count, color=(40, 34, 28), alpha=(30, 90), size=(1, 3)):
        for _ in range(count):
            x, y = self.r.randrange(self.w), self.r.randrange(self.h)
            s = self.r.randint(*size)
            a = self.r.randint(*alpha) / 255
            for yy in range(y, min(self.h, y + s)):
                for xx in range(x, min(self.w, x + s)):
                    if self.a[yy, xx, 3] > 0:
                        self.a[yy, xx, :3] = self.a[yy, xx, :3] * (1 - a) + np.array(color) * a

    def streaks(self, count, color=(70, 50, 30), alpha=60):
        """Chorretones de oxido o suciedad hacia abajo."""
        for _ in range(count):
            x = self.r.randrange(self.w)
            y = self.r.randrange(self.h // 2)
            ln = self.r.randint(3, self.h // 2)
            for i in range(ln):
                yy = y + i
                if yy >= self.h or self.a[yy, x, 3] == 0:
                    continue
                k = alpha / 255 * (1 - i / ln)
                self.a[yy, x, :3] = self.a[yy, x, :3] * (1 - k) + np.array(color) * k

    def bevel(self, x0, y0, x1, y1, light=1.18, dark=0.72):
        self.shade(x0, y0, x1, y0 + 1, light)
        self.shade(x0, y0, x0 + 1, y1, light)
        self.shade(x0, y1 - 1, x1, y1, dark)
        self.shade(x1 - 1, y0, x1, y1, dark)

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray(np.clip(self.a, 0, 255).astype(np.uint8), "RGBA").save(path)


# Metal y pintura ----------------------------------------------------------------------------

def metal(w, seed, base=(118, 124, 128), rust=0.3):
    t = Tex(w, base=(*base, 255), seed=seed)
    t.noise(9)
    for y in range(0, w, 2):  # cepillado
        t.shade(0, y, w, y + 1, 0.97)
    t.grime(int(w * 1.5), color=(60, 46, 34))
    t.streaks(int(w * rust), color=(110, 60, 30), alpha=70)
    return t


def fuse_box():
    side = metal(32, 11, base=(92, 98, 100))
    side.bevel(0, 0, 32, 32)
    for y in (5, 26):
        for x in (4, 27):
            side.rect(x, y, x + 2, y + 2, (150, 154, 156))
            side.px(x, y, (190, 194, 196))
    side.save(BLOCK / "fuse_box_side.png")

    for filled in range(5):
        t = metal(32, 12, base=(70, 76, 78), rust=0.2)
        t.bevel(0, 0, 32, 32, 1.25, 0.6)
        t.rect(2, 2, 30, 30, (46, 50, 52))
        t.bevel(2, 2, 30, 30, 0.7, 1.2)
        # Placa de aviso amarilla arriba
        t.rect(5, 4, 27, 8, (196, 160, 40))
        for x in range(5, 27, 4):
            t.rect(x, 4, x + 2, 8, (34, 30, 26))
        # 4 ranuras
        for i in range(4):
            x0 = 5 + i * 6
            t.rect(x0, 11, x0 + 4, 27, (18, 18, 20))
            t.bevel(x0, 11, x0 + 4, 27, 0.6, 1.4)
            t.rect(x0 + 1, 12, x0 + 3, 13, (150, 140, 110))   # contacto de arriba
            t.rect(x0 + 1, 25, x0 + 3, 26, (150, 140, 110))   # contacto de abajo
            if i < filled:
                # Fusible de cristal ambar con casquillos
                t.rect(x0, 13, x0 + 4, 15, (176, 176, 170))
                t.rect(x0, 23, x0 + 4, 25, (176, 176, 170))
                t.rect(x0 + 1, 15, x0 + 3, 23, (226, 150, 40))
                t.px(x0 + 1, 16, (255, 220, 140))
                t.px(x0 + 1, 17, (255, 200, 110))
                t.rect(x0 + 2, 15, x0 + 3, 23, (170, 100, 20))
        t.grime(25, color=(30, 26, 22))
        t.save(BLOCK / f"fuse_box_front_{filled}.png")

    for name, c, glow in (("on", (60, 230, 110), (180, 255, 200)), ("off", (200, 40, 40), (255, 140, 140))):
        t = Tex(16, base=(30, 30, 30, 255), seed=13)
        t.rect(2, 2, 14, 14, c)
        t.rect(4, 4, 8, 7, glow)
        t.bevel(0, 0, 16, 16, 1.3, 0.5)
        t.save(BLOCK / f"fuse_box_lamp_{name}.png")


def door(name, seed, paint, window, stripes):
    for half in ("bottom", "top"):
        t = Tex(32, base=(*paint, 255), seed=seed + (half == "top"))
        t.noise(6)
        t.rect(0, 0, 2, 32, tuple(int(v * 0.75) for v in paint))      # canto
        t.rect(30, 0, 32, 32, tuple(int(v * 0.8) for v in paint))
        # Paneles
        if half == "top":
            if window:
                t.rect(7, 5, 25, 24, (30, 40, 44))
                t.bevel(6, 4, 26, 25, 0.7, 1.25)
                for x in range(8, 24, 3):     # malla de alambre del cristal
                    t.rect(x, 6, x + 1, 23, (64, 76, 80))
                for y in range(7, 23, 3):
                    t.rect(8, y, 24, y + 1, (64, 76, 80))
                t.px(9, 7, (120, 150, 160))
                t.px(10, 8, (100, 130, 140))
            else:
                t.bevel(6, 4, 26, 28)
            t.rect(4, 0, 28, 1, tuple(int(v * 0.85) for v in paint))
        else:
            t.bevel(6, 4, 26, 22)
            # Manilla y cerradura
            t.rect(23, 1, 28, 4, (170, 172, 168))
            t.rect(26, 4, 28, 7, (120, 122, 118))
            t.px(24, 2, (220, 222, 218))
            # Placa de patada
            if stripes:
                for x in range(2, 30):
                    for y in range(24, 31):
                        c = (220, 180, 30) if ((x + y) // 3) % 2 == 0 else (28, 26, 24)
                        t.px(x, y, c)
            else:
                t.rect(2, 25, 30, 31, (150, 154, 156))
                t.bevel(2, 25, 30, 31)
        t.grime(60 if half == "bottom" else 30)
        t.streaks(6, color=(80, 70, 55), alpha=45)
        t.save(BLOCK / f"{name}_{half}.png")
    # Icono
    it = Tex(16, seed=seed + 5)
    it.rect(4, 1, 12, 15, paint)
    it.bevel(4, 1, 12, 15)
    if window:
        it.rect(6, 3, 10, 7, (40, 52, 56))
    it.rect(10, 9, 11, 10, (190, 190, 186))
    if stripes:
        for x in range(4, 12):
            it.px(x, 13, (220, 180, 30) if x % 2 else (28, 26, 24))
    it.save(ITEM / f"{name}.png")


def valve():
    t = metal(32, 21, base=(84, 86, 84), rust=0.8)
    t.rect(4, 4, 28, 28, (98, 92, 84))
    t.bevel(4, 4, 28, 28)
    for x, y in ((6, 6), (24, 6), (6, 24), (24, 24)):
        t.rect(x, y, x + 2, y + 2, (150, 146, 140))
    t.rect(11, 11, 21, 21, (70, 66, 60))
    t.bevel(11, 11, 21, 21, 0.7, 1.3)
    t.streaks(20, color=(120, 64, 30), alpha=90)
    t.save(BLOCK / "valve_base.png")

    # Rueda (objeto)
    w = Tex(16, seed=22)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 5.6 <= d <= 7.4:
                k = 1.15 if (x + y) < 14 else 0.85
                w.px(x, y, (int(176 * k), int(34 * k), int(30 * k)))
            elif d <= 1.8:
                w.px(x, y, (150, 150, 150))
    for i in range(16):
        for (dx, dy) in ((1, 0), (0, 1), (0.7, 0.7), (0.7, -0.7)):
            pass
    for k in range(-5, 6):
        w.px(int(cx + k), int(cy), (150, 30, 26))
        w.px(int(cx), int(cy + k), (150, 30, 26))
    w.px(7, 7, (210, 210, 210))
    w.save(ITEM / "valve_wheel.png")


def cabinet(name, seed, body, door_c, handle, vents, label=None, opened=False):
    """Taquilla, armario o contenedor: frontal de abajo y de arriba, lateral y tapa."""
    for half in ("lower", "upper"):
        t = Tex(32, base=(*body, 255), seed=seed + (half == "upper"))
        t.noise(7)
        if opened:
            t.rect(3, 0 if half == "upper" else 2, 29, 30 if half == "upper" else 32, (14, 14, 16))
            t.shade(3, 0, 29, 32, 1.0)
            t.rect(3, 0, 6, 32, door_c)    # puerta entreabierta (canto)
            t.bevel(3, 0, 6, 32)
        else:
            t.rect(3, 2 if half == "lower" else 0, 29, 32 if half == "lower" else 30, door_c)
            t.bevel(3, 2 if half == "lower" else 0, 29, 32 if half == "lower" else 30)
            if vents and half == "upper":
                for y in range(5, 18, 3):
                    t.rect(8, y, 24, y + 1, (14, 14, 16))
                    t.rect(8, y + 1, 24, y + 2, tuple(min(255, int(v * 1.15)) for v in door_c))
            if half == "lower":
                t.rect(24, 1, 26, 9, handle)
                t.px(24, 1, (230, 230, 226))
            if label and half == "upper":
                lx, ly, lc = label
                t.rect(lx, ly, lx + 10, ly + 7, (230, 226, 200))
                t.rect(lx + 1, ly + 1, lx + 9, ly + 6, lc)
        t.grime(50)
        t.streaks(5, color=(70, 56, 40), alpha=50)
        t.save(BLOCK / f"{name}{'_open' if opened else ''}_{half}.png")
    if not opened:
        side = Tex(32, base=(*body, 255), seed=seed + 9)
        side.noise(7)
        side.bevel(0, 0, 32, 32)
        for y in range(4, 30, 8):
            side.shade(0, y, 32, y + 1, 0.85)
        side.grime(40)
        side.save(BLOCK / f"{name}_side.png")
        top = Tex(32, base=(*body, 255), seed=seed + 10)
        top.noise(8)
        top.bevel(0, 0, 32, 32)
        top.grime(70, color=(50, 44, 36), size=(1, 4))
        top.save(BLOCK / f"{name}_top.png")


def gurney():
    t = Tex(32, base=(196, 200, 196, 255), seed=31)   # colchon
    t.noise(5)
    for y in range(0, 32, 8):
        t.shade(0, y, 32, y + 1, 0.85)
    t.rect(0, 0, 32, 2, (150, 154, 156))
    t.rect(0, 30, 32, 32, (150, 154, 156))
    # Mancha antigua
    for _ in range(50):
        x, y = t.r.randint(12, 20), t.r.randint(10, 18)
        t.px(x, y, (110, 60, 50), 200)
    t.grime(40)
    t.save(BLOCK / "gurney_top.png")
    s = metal(32, 32, base=(160, 164, 166), rust=0.2)
    s.save(BLOCK / "gurney_frame.png")


def curtain():
    t = Tex(32, base=(0, 0, 0, 0), seed=41)
    for x in range(32):
        k = 0.82 + 0.18 * np.cos(x / 32 * np.pi * 6)
        for y in range(2, 32):
            t.px(x, y, (int(150 * k), int(178 * k), int(160 * k)), 235)
    t.rect(0, 0, 32, 2, (170, 172, 170))
    for x in range(1, 32, 4):
        t.rect(x, 1, x + 2, 3, (120, 122, 120))
    t.grime(60, color=(90, 80, 60))
    t.save(BLOCK / "curtain.png")


def items():
    # Fusible
    for name, glass, glow in (("fuse", (226, 150, 40), (255, 220, 140)), ("fuse_blown", (70, 52, 36), (110, 90, 70))):
        t = Tex(16, seed=51)
        t.rect(6, 1, 10, 4, (190, 190, 186))
        t.rect(6, 12, 10, 15, (190, 190, 186))
        t.rect(6, 4, 10, 12, glass)
        t.rect(7, 5, 8, 9, glow)
        t.bevel(6, 1, 10, 15)
        if name == "fuse_blown":
            t.px(8, 7, (20, 20, 20))
            t.px(9, 8, (20, 20, 20))
        t.save(ITEM / f"{name}.png")
    # Llave
    k = Tex(16, seed=52)
    for y in range(16):
        for x in range(16):
            d = ((x - 4.5) ** 2 + (y - 4.5) ** 2) ** 0.5
            if 1.6 <= d <= 3.4:
                k.px(x, y, (200, 170, 70))
    for i in range(6, 14):
        k.px(i, i, (200, 170, 70))
        k.px(i + 1, i, (150, 120, 40))
    k.px(11, 13, (200, 170, 70))
    k.px(12, 12, (200, 170, 70))
    k.px(13, 11, (200, 170, 70))
    k.save(ITEM / "key.png")
    # Tarjetas
    for lvl, c in ((1, (70, 150, 220)), (2, (230, 170, 40)), (3, (210, 50, 50))):
        t = Tex(16, seed=53 + lvl)
        t.rect(1, 4, 15, 13, (236, 236, 232))
        t.rect(1, 4, 15, 7, c)
        t.rect(3, 8, 6, 11, (200, 180, 90))     # chip
        for i in range(lvl):
            t.rect(9 + i * 2, 9, 10 + i * 2, 11, c)
        t.bevel(1, 4, 15, 13)
        t.save(ITEM / f"keycard_{lvl}.png")


def main():
    # Los bloques son modelos GeckoLib (tools/geo_models.py); aqui solo quedan los objetos
    global BLOCK
    real = BLOCK
    BLOCK = Path("/tmp/smileprotocol_unused_textures")
    door("lab_door", 61, (206, 210, 204), window=True, stripes=False)
    door("security_door", 63, (96, 104, 108), window=True, stripes=True)
    valve()
    BLOCK = real
    items()
    print("Texturas de mecanismos generadas")


if __name__ == "__main__":
    main()
