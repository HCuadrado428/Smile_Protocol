#!/usr/bin/env python3
"""
Modelos GeckoLib (geo.json + textura) de los mecanismos y escondites:
puertas de laboratorio y de seguridad, taquilla, armario, contenedor, camilla, cortina,
valvula y caja de fusibles.

Cada modelo se construye con cubos y materiales; el script reparte las caras en la textura
(sin solapes) y escribe el geo.json con la UV exacta de cada cara, asi modelo y textura siempre
encajan. Los materiales se pintan en coordenadas del MUNDO (cada pixel sabe donde esta en el
modelo), de modo que la mugre se acumula abajo, los chorretones bajan de arriba, el oxido y el
ruido siguen de una cara a la otra y las manchas de sangre cruzan varias piezas.
Resolucion: 2 pixeles de textura por pixel de modelo (32x por bloque).

Las piezas que se mueven (hoja de puerta, rueda, tapa...) usan los mismos pivotes que el
renderer (client/SPGeoRenderer).

    python3 tools/geo_models.py [preview.png]
"""
import json
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from texlib import stencil_text  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol"
SCALE = 2  # pixeles de textura por unidad de modelo
FACES = ("north", "south", "east", "west", "up", "down")


# Ruido 3D en coordenadas del modelo -------------------------------------------------------------------

def _hash(ix, iy, iz, seed):
    h = np.sin(ix * 127.1 + iy * 311.7 + iz * 74.7 + seed * 13.37) * 43758.5453
    return h - np.floor(h)


def noise3(x, y, z, freq, seed):
    x, y, z = x * freq, y * freq, z * freq
    ix, iy, iz = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out = out + w * _hash(ix + dx, iy + dy, iz + dz, seed)
    return out


def fbm3(x, y, z, freq, seed, octaves=4):
    out, amp, tot = 0, 1.0, 0
    for o in range(octaves):
        out = out + noise3(x, y, z, freq * 2 ** o, seed + o * 17) * amp
        tot += amp
        amp *= 0.5
    return out / tot


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a + 1e-9), 0, 1)
    return t * t * (3 - 2 * t)


class Face:
    """Region de textura de una cara: coordenadas del modelo de cada pixel y del borde de la cara."""

    def __init__(self, cube, face, w, h, seed):
        self.face, self.w, self.h, self.seed = face, w, h, seed
        ox, oy, oz = cube["origin"]
        sx, sy, sz = cube["size"]
        u = (np.arange(w) + 0.5) / SCALE
        v = (np.arange(h) + 0.5) / SCALE
        U, V = np.meshgrid(u, v)
        if face == "north":
            X, Y, Z = ox + sx - U, oy + sy - V, np.full_like(U, oz)
        elif face == "south":
            X, Y, Z = ox + U, oy + sy - V, np.full_like(U, oz + sz)
        elif face == "east":
            X, Y, Z = np.full_like(U, ox + sx), oy + sy - V, oz + sz - U
        elif face == "west":
            X, Y, Z = np.full_like(U, ox), oy + sy - V, oz + U
        elif face == "up":
            X, Y, Z = ox + U, np.full_like(U, oy + sy), oz + V
        else:
            X, Y, Z = ox + U, np.full_like(U, oy), oz + sz - V
        self.X, self.Y, self.Z = X, Y, Z
        # distancia al borde de la cara (en pixeles de textura)
        ii, jj = np.meshgrid(np.arange(w), np.arange(h))
        self.edge = np.minimum(np.minimum(ii, w - 1 - ii), np.minimum(jj, h - 1 - jj))
        self.U, self.V = U, V
        self.rng = np.random.default_rng(seed)

    def n(self, freq, octaves=4, salt=0):
        return fbm3(self.X, self.Y, self.Z, freq, self.seed // 1000 * 1000 + salt, octaves)

    def white(self):
        return self.rng.random((self.h, self.w))

    @property
    def side(self):
        return self.face not in ("up", "down")


def mix(c, col, k):
    k = np.asarray(k, dtype=float)
    if k.ndim == 2:
        k = k[..., None]
    return c * (1 - k) + np.asarray(col, dtype=float) * k


# Materiales -------------------------------------------------------------------------------------------
# Un material es f(face) -> (rgb HxWx3, alfa HxW)

def painted(color, under=(132, 130, 124), chip=0.25, grime=0.5, rust_amt=0.25, streak=0.3, scratch=6, gloss=0.0,
            dent=0.0):
    """Metal o madera pintados y gastados: desconchones con oxido, mugre abajo, chorretones y aranazos."""
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + color
        c *= (0.92 + 0.12 * f.n(0.35, 3, 1))[..., None]
        c += (f.white()[..., None] - 0.5) * 7
        if dent:
            d = f.n(0.5, 2, 9)
            c *= (1 + (d - 0.5) * dent)[..., None]
        # desconchones: mas en los cantos y abajo
        wear = f.n(0.6, 4, 2) + sstep(3, 0, f.edge) * 0.25 + sstep(6, 0, f.Y) * 0.15
        chipm = wear > 1 - chip * 0.55
        under_c = np.zeros_like(c) + under
        under_c = mix(under_c, (112, 58, 30), sstep(0.45, 0.8, f.n(0.9, 3, 3)) * rust_amt * 2.2)
        rim = (wear > 1 - chip * 0.55 - 0.04) & ~chipm
        c = np.where(chipm[..., None], under_c, c)
        c = np.where(rim[..., None], c * 0.8, c)
        # oxido que sale de los desconchones y de los cantos bajos
        r = sstep(0.55, 0.85, f.n(0.5, 4, 4) * 0.7 + sstep(4, 0, f.Y) * 0.3) * rust_amt
        c = mix(c, (110, 56, 28), r * 0.8)
        # mugre: abajo y en los cantos
        g = sstep(0.4, 1.0, f.n(0.25, 3, 5) * 0.6 + sstep(10, 0, f.Y) * 0.35 + sstep(2, 0, f.edge) * 0.2) * grime
        c = mix(c, (48, 40, 30), g * 0.75)
        # chorretones de oxido/agua desde arriba (caras laterales)
        if f.side and streak:
            sx = f.X + f.Z
            col = np.floor(sx * SCALE)
            hsh = _hash(col, 0, 0, 7)
            top = (f.Y.max() if f.Y.size else 0)
            ln = 2 + hsh * 10
            s = (hsh > 1 - streak * 0.5) & (top - f.Y < ln)
            k = np.clip(1 - (top - f.Y) / ln, 0, 1) * 0.45
            c = mix(c, (96, 58, 32), s * k)
        if scratch:
            sc = np.zeros((f.h, f.w))
            for _ in range(int(scratch * f.w * f.h / 400) + 1):
                x, y = f.rng.integers(f.w), f.rng.integers(f.h)
                a = f.rng.uniform(0, math.pi)
                for i in range(int(f.rng.integers(2, 7))):
                    xx, yy = int(x + math.cos(a) * i), int(y + math.sin(a) * i)
                    if 0 <= xx < f.w and 0 <= yy < f.h:
                        sc[yy, xx] = 1
            c = mix(c, np.minimum(np.asarray(color) * 1.35 + 20, 255), sc * 0.35)
        if gloss:
            c = c * (1 + gloss * sstep(2, 0, f.edge)[..., None] * (f.V < f.V.mean())[..., None])
        # bisel: los cantos de arriba/izquierda cogen luz
        c = np.where((f.edge == 0)[..., None], c * 0.82, c)
        return c, np.ones((f.h, f.w))
    return m


def metal(color, brushed=True, tarnish=0.4, rust_amt=0.15, grime=0.4):
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + color
        if brushed:
            ax = f.X if f.face in ("up", "down", "north", "south") else f.Z
            c *= (0.93 + 0.1 * noise3(ax * 0.2, f.Y * 6, ax * 0.1, 1.0, 3))[..., None]
        c += (f.white()[..., None] - 0.5) * 6
        t = sstep(0.4, 0.9, f.n(0.4, 4, 6)) * tarnish
        c = mix(c, (88, 82, 70), t * 0.6)
        r = sstep(0.6, 0.9, f.n(0.7, 4, 7) * 0.75 + sstep(3, 0, f.Y) * 0.25) * rust_amt
        c = mix(c, (112, 58, 28), r)
        g = sstep(0.45, 1, f.n(0.3, 3, 8) * 0.6 + sstep(6, 0, f.Y) * 0.4) * grime
        c = mix(c, (44, 38, 30), g * 0.6)
        # brillo en el canto superior
        c = np.where((f.edge == 0)[..., None] & (f.V < 0.6)[..., None], c * 1.18, c)
        c = np.where((f.edge == 0)[..., None] & (f.V >= 0.6)[..., None], c * 0.8, c)
        return c, np.ones((f.h, f.w))
    return m


def rubber():
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + (30, 29, 28)
        c *= (0.85 + 0.3 * f.n(0.8, 2, 11))[..., None]
        c = mix(c, (60, 52, 40), sstep(0.6, 1, f.n(0.5, 3, 12)) * 0.5)
        return c, np.ones((f.h, f.w))
    return m


def dark(color=(16, 15, 14)):
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + color
        c *= (0.8 + 0.4 * f.n(0.5, 2, 13))[..., None]
        return c, np.ones((f.h, f.w))
    return m


def vents(base=(70, 82, 90)):
    def m(f):
        c, a = painted(base, chip=0.15, grime=0.6, rust_amt=0.3, scratch=0)(f)
        slot = (np.floor(f.V * SCALE).astype(int) % 3) == 1
        c = np.where(slot[..., None], np.array([10, 10, 11]), c)
        under = (np.floor(f.V * SCALE).astype(int) % 3) == 2
        c = np.where(under[..., None], c * 1.15, c)
        return c, a
    return m


def wood(color=(96, 66, 42), varnish=0.3):
    def m(f):
        ax = f.Y if f.side else f.Z
        grain = noise3(f.X * 0.6 + f.Z * 0.6, ax * 0.12, f.X * 0.2, 1.0, 21) * 0.6 + f.n(1.2, 2, 22) * 0.4
        c = np.zeros((f.h, f.w, 3)) + color
        c *= (0.84 + 0.28 * (0.5 + 0.5 * np.sin(grain * 22)))[..., None]
        c += (f.white()[..., None] - 0.5) * 4
        wear = sstep(0.55, 0.9, f.n(0.5, 3, 23) + sstep(3, 0, f.edge) * 0.2)
        c = mix(c, np.asarray(color) * 1.35, wear * varnish)
        g = sstep(0.4, 1, f.n(0.3, 3, 24) * 0.6 + sstep(8, 0, f.Y) * 0.4)
        c = mix(c, (36, 26, 18), g * 0.6)
        # moho verdoso en la parte baja
        c = mix(c, (56, 66, 40), sstep(0.7, 1, f.n(0.6, 3, 25) * 0.6 + sstep(4, 0, f.Y) * 0.4) * 0.6)
        c = np.where((f.edge == 0)[..., None], c * 0.78, c)
        return c, np.ones((f.h, f.w))
    return m


def fabric(color, folds=True, dirt=0.5, torn=False, fade=0.25):
    """Tela de cortina o sabana: pliegues, decoloracion, manchas y bajo deshilachado."""
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + color
        weave = ((np.floor(f.U * SCALE) + np.floor(f.V * SCALE)) % 2) * 0.05
        c *= (0.95 + weave)[..., None]
        if folds and f.side:
            ax = f.X + f.Z
            c *= (0.82 + 0.22 * np.sin(ax * 1.6 + 0.6 * np.sin(f.Y * 0.2)))[..., None]
        c = mix(c, (190, 186, 160), sstep(0.5, 1, f.n(0.2, 3, 31)) * fade)
        # cerco amarillento y mugre que sube desde abajo
        c = mix(c, (120, 104, 62), sstep(0.55, 0.95, f.n(0.35, 4, 32) * 0.6 + sstep(8, 0, f.Y) * 0.4) * dirt)
        c += (f.white()[..., None] - 0.5) * 6
        a = np.ones((f.h, f.w))
        if torn and f.side:
            # bajo roto: dientes y agujeros
            low = f.Y - f.Y.min() if f.Y.size else f.Y
            jag = 1.2 + 2.5 * noise3(f.X * 1.5 + f.Z * 1.5, 0, 0, 1.0, 33)
            a = np.where(low < jag, 0, a)
            holes = (f.n(1.1, 2, 34) > 0.82) & (f.Y < 12)
            a = np.where(holes, 0, a)
            fray = (low < jag + 0.6) & (a > 0)
            c = np.where(fray[..., None], c * 0.7, c)
        return c, a
    return m


def mattress():
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + (150, 152, 138)
        if f.face == "up":
            quilt = ((np.floor(f.X) % 5 == 0) | (np.floor(f.Z) % 5 == 0))
            c = np.where(quilt[..., None], c * 0.82, c)
        c *= (0.9 + 0.15 * f.n(0.3, 3, 41))[..., None]
        # vinilo amarillento y cuarteado
        c = mix(c, (150, 128, 76), sstep(0.45, 0.95, f.n(0.25, 4, 42)) * 0.6)
        cr = (np.abs(f.n(0.9, 3, 43) - 0.5) < 0.025)
        c = np.where(cr[..., None], c * 0.65, c)
        c += (f.white()[..., None] - 0.5) * 6
        return c, np.ones((f.h, f.w))
    return m


def leather():
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + (78, 50, 32)
        c *= (0.8 + 0.35 * f.n(0.9, 3, 51))[..., None]
        c = np.where((f.edge == 0)[..., None], c * 0.65, c)
        c = mix(c, (120, 90, 60), sstep(0.7, 1, f.n(1.2, 2, 52)) * 0.5)
        return c, np.ones((f.h, f.w))
    return m


def glass(tint=(36, 48, 50), wire=True, cracked=0.0, dirt=0.6):
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + tint
        c *= (0.75 + 0.5 * f.n(0.3, 3, 61))[..., None]
        # reflejo diagonal
        refl = np.abs(((f.U + f.V) * 0.7) % 6 - 1.5) < 0.6
        c = np.where(refl[..., None], c * 1.45 + 12, c)
        if wire:
            g = ((np.floor(f.U * SCALE) % 5 == 0) | (np.floor(f.V * SCALE) % 5 == 0))
            c = np.where(g[..., None], np.array([82, 88, 86]), c)
        c = mix(c, (92, 86, 64), sstep(0.5, 1, f.n(0.3, 3, 62) * 0.6 + sstep(4, 0, f.edge) * 0.4) * dirt)
        if cracked:
            ang = np.arctan2(f.V - f.V.mean() - 1, f.U - f.U.mean() + 0.5)
            spokes = (np.abs(np.sin(ang * 5 + 0.7)) < 0.08) & (f.n(0.8, 2, 63) < 0.4 + cracked)
            rings = np.abs(np.hypot(f.V - f.V.mean() - 1, f.U - f.U.mean() + 0.5) % 2.2 - 1.1) < 0.12
            c = np.where((spokes | (rings & (f.n(1.2, 2, 64) > 0.55)))[..., None], np.array([170, 178, 176]), c)
        return c, np.ones((f.h, f.w))
    return m


def stripes(c1=(176, 144, 34), c2=(24, 22, 20)):
    def m(f):
        ax = f.X + f.Z + f.Y
        band = (np.floor(ax / 2.0) % 2) == 0
        c = np.where(band[..., None], np.array(c1, dtype=float), np.array(c2, dtype=float))
        c *= (0.88 + 0.2 * f.n(0.5, 3, 71))[..., None]
        chip = f.n(0.9, 3, 72) > 0.72
        c = np.where(chip[..., None], np.array([84, 82, 78]), c)
        c = mix(c, (40, 34, 24), sstep(0.5, 1, f.n(0.3, 3, 73)) * 0.5)
        return c, np.ones((f.h, f.w))
    return m


def biohazard():
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + (196, 160, 36)
        cx, cy = f.U.mean(), f.V.mean()
        r = np.hypot(f.U - cx, f.V - cy)
        ang = np.arctan2(f.V - cy, f.U - cx)
        sym = ((r > 0.6) & (r < 2.2) & (np.cos(3 * ang) > 0.25)) | (r < 0.45) | ((np.abs(r - 1.5) < 0.18))
        c = np.where(sym[..., None], np.array([22, 20, 18]), c)
        c = np.where((f.edge == 0)[..., None], np.array([22, 20, 18]), c)
        c = mix(c, (90, 80, 40), sstep(0.55, 1, f.n(0.7, 3, 74)) * 0.6)
        return c, np.ones((f.h, f.w))
    return m


def lamp(color, glow=True):
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + color
        cx, cy = f.U.mean(), f.V.mean()
        r = np.hypot(f.U - cx, f.V - cy)
        c *= (1.25 - 0.4 * np.clip(r / max(cx, 0.5), 0, 1))[..., None]
        c = np.where((r < 0.4)[..., None], np.minimum(c * 1.4 + 30, 255), c)
        return c, np.ones((f.h, f.w))
    return m


def label(text="", ink=(40, 36, 30)):
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + (196, 186, 150)
        c *= (0.88 + 0.15 * f.n(0.6, 3, 81))[..., None]
        c = mix(c, (130, 100, 56), sstep(0.5, 1, f.n(0.5, 3, 82)) * 0.6)
        if text and f.face in ("south", "north"):
            from texlib import Canvas
            cv = Canvas(f.w, f.h)
            cv.c = c.copy()
            tw = len(text) * 4 - 1
            stencil_text(cv, max(0, (f.w - tw) // 2), max(0, (f.h - 5) // 2), text, ink, 0.9, f.rng)
            c = cv.c
        return c, np.ones((f.h, f.w))
    return m


def keypad():
    def m(f):
        c, a = metal((60, 62, 64), False)(f)
        if f.face == "south":
            for j in range(4):
                for i in range(3):
                    x0, y0 = 2 + i * 3, 4 + j * 3
                    if y0 + 1 < f.h and x0 + 1 < f.w:
                        c[y0:y0 + 2, x0:x0 + 2] = (120, 122, 118)
            c[1:3, 2:9] = (30, 70, 40)
        return c, a
    return m


MAT = {
    "paint_white": painted((176, 178, 168), chip=0.3, grime=0.6, rust_amt=0.3),
    "paint_white_dark": painted((140, 142, 134), chip=0.3, grime=0.6),
    "paint_green": painted((96, 116, 100), chip=0.35, grime=0.6, rust_amt=0.35),
    "steel": metal((134, 138, 140)),
    "steel_dark": painted((70, 76, 78), under=(110, 108, 102), chip=0.3, grime=0.6, rust_amt=0.45, dent=0.3),
    "chrome": metal((178, 182, 184), tarnish=0.6, rust_amt=0.25),
    "rubber": rubber(),
    "locker": painted((76, 92, 104), chip=0.35, grime=0.65, rust_amt=0.5, dent=0.35),
    "locker_door": painted((88, 106, 118), chip=0.35, grime=0.6, rust_amt=0.45, dent=0.4),
    "locker_inner": dark((22, 22, 22)),
    "vents": vents(),
    "wood": wood(),
    "wood_dark": wood((70, 46, 30)),
    "bin_green": painted((54, 86, 54), chip=0.2, grime=0.7, rust_amt=0.1, streak=0.6),
    "bin_lid": painted((44, 72, 44), chip=0.2, grime=0.6, rust_amt=0.1),
    "biohazard": biohazard(),
    "mattress": mattress(),
    "sheet": fabric((176, 176, 166), folds=False, dirt=0.6, fade=0.0),
    "sheet_torn": fabric((176, 176, 166), folds=True, dirt=0.7, torn=True, fade=0.0),
    "cloth": fabric((104, 132, 118), torn=True),
    "leather": leather(),
    "wheel_red": painted((138, 28, 24), under=(90, 70, 60), chip=0.4, grime=0.4, rust_amt=0.5, streak=0.1),
    "pipe_rust": painted((90, 84, 76), under=(100, 52, 26), chip=0.6, grime=0.6, rust_amt=0.9, streak=0.7),
    "amber": lamp((220, 140, 36)),
    "amber_dead": lamp((90, 60, 30)),
    "lamp_green": lamp((60, 220, 100)),
    "lamp_red": lamp((210, 30, 30)),
    "stripes": stripes(),
    "glass": glass(),
    "glass_cracked": glass(cracked=0.4),
    "glass_blood": glass(tint=(30, 30, 32), wire=False, dirt=0.4),
    "blood_bag": painted((96, 10, 12), under=(70, 8, 8), chip=0.0, grime=0.1, rust_amt=0, streak=0, scratch=0, gloss=0.4),
    "black": dark(),
    "label": label(),
    "label_lab": label("LAB"),
    "label_bio": label("BIO"),
    "label_num": label("13"),
    "label_danger": label("SP", (130, 20, 16)),
    "keypad": keypad(),
}


# Sangre en el espacio del modelo -----------------------------------------------------------------------

def blood_layer(f, splats):
    """Manchas 3D: ('splat', centro, radio), ('drip', x, z, y0, largo), ('hand', centro, normal_eje)."""
    k = np.zeros((f.h, f.w))
    P = np.stack([f.X, f.Y, f.Z], -1)
    for s in splats:
        if s[0] == "splat":
            _, cen, rad = s[:3]
            d = np.linalg.norm(P - np.asarray(cen), axis=-1)
            edge = rad * (0.75 + 0.5 * noise3(f.X, f.Y, f.Z, 0.9, 91))
            k = np.maximum(k, sstep(edge + 0.3, edge - 0.4, d))
            # gotitas alrededor
            drops = (noise3(f.X, f.Y, f.Z, 2.5, 92) > 0.86) & (d < rad * 2.2)
            k = np.maximum(k, drops.astype(float))
        elif s[0] == "drip" and f.side:
            _, x, z, y0, ln = s
            d = np.hypot(f.X - x, f.Z - z)
            w = 0.35 + 0.25 * (f.Y > y0 - 1)
            ok = (f.Y <= y0) & (f.Y >= y0 - ln) & (d < w + 0.2)
            k = np.maximum(k, ok.astype(float))
        elif s[0] == "smear" and f.side:
            _, cen, rad, ln = s
            dx = f.X + f.Z - (cen[0] + cen[2])
            dy = f.Y - cen[1]
            ok = (np.abs(dx) < rad) & (dy < 0.5) & (dy > -ln * (0.5 + 0.5 * noise3(f.X + f.Z, 0, 0, 1.3, 93)))
            near = np.abs(np.where(f.face in ("north", "south"), f.Z - cen[2], f.X - cen[0])) < 1.5
            k = np.maximum(k, (ok & near).astype(float) * (noise3(f.X, f.Y, f.Z, 2.0, 94) > 0.25))
    return k


def apply_blood(c, k, f, age=0.5):
    if not k.any():
        return c
    fresh = np.array([104, 8, 10]) * (1 - age) + np.array([64, 22, 16]) * age
    col = np.zeros_like(c) + fresh
    col *= (0.7 + 0.5 * noise3(f.X, f.Y, f.Z, 1.5, 95))[..., None]
    return mix(c, col, np.clip(k, 0, 1) * 0.92)


# Modelo ----------------------------------------------------------------------------------------------

class Geo:
    def __init__(self, name, seed=0):
        self.name = name
        self.bones = []
        self.seed = seed
        self.splats = []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, mat, skip=(), rotation=None, pivot=None, blood=True, inflate=0):
        b = next(x for x in self.bones if x["name"] == bone)
        c = {"origin": list(origin), "size": list(size), "mat": mat, "skip": set(skip), "blood": blood}
        if rotation:
            c["rotation"] = list(rotation)
            c["pivot"] = list(pivot or [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2])
        if inflate:
            c["inflate"] = inflate
        b["cubes"].append(c)

    def blood(self, *splat):
        self.splats.append(splat)

    def save(self):
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
                x += w
                row = max(row, h)
            Ht = y + row
            if ok and Ht <= W * 2:
                break
            W *= 2
        TW, TH = W, max(16, 1 << math.ceil(math.log2(max(1, Ht))))
        img = np.zeros((TH * SCALE, TW * SCALE, 4), dtype=np.float64)
        for n, (c, f, w, h, rw, rh) in enumerate(faces):
            u, v, _, _, pw, ph = c["uv"][f]
            face = Face(c, f, pw * SCALE, ph * SCALE, self.seed * 1000 + n)
            col, a = MAT[c["mat"]](face)
            if c["blood"] and self.splats:
                col = apply_blood(col, blood_layer(face, self.splats), face)
            # oclusion: las caras de abajo y lo que queda cerca del suelo, mas oscuro
            if f == "down":
                col = col * 0.6
            col = col * (0.82 + 0.18 * sstep(0, 6, face.Y))[..., None]
            img[v * SCALE:(v + ph) * SCALE, u * SCALE:(u + pw) * SCALE, :3] = np.clip(col, 0, 255)
            img[v * SCALE:(v + ph) * SCALE, u * SCALE:(u + pw) * SCALE, 3] = a * 255

        bones_out = []
        for b in self.bones:
            out = {"name": b["name"], "pivot": b["pivot"]}
            if "parent" in b:
                out["parent"] = b["parent"]
            if "rotation" in b:
                out["rotation"] = b["rotation"]
            cubes = []
            for c in b["cubes"]:
                uv = {f: {"uv": [u, v], "uv_size": [rw, rh]} for f, (u, v, rw, rh, _, _) in c["uv"].items()}
                cc = {"origin": c["origin"], "size": c["size"], "uv": uv}
                if "rotation" in c:
                    cc["rotation"] = c["rotation"]
                    cc["pivot"] = c["pivot"]
                if "inflate" in c:
                    cc["inflate"] = c["inflate"]
                cubes.append(cc)
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
# Coordenadas en pixeles: el bloque va de -8 a 8 en X y Z y de 0 a 16 en Y. El frente es +Z (sur).

def lab_door(name, security):
    g = Geo(name, 3 if security else 2)
    g.bone("root")
    g.bone("leaf", "root")
    z0, t = -8, 3
    if security:
        # Hoja blindada: chapa con refuerzos, franjas, mirilla y cerradura
        g.cube("leaf", (-8, 0, z0), (16, 32, t), "steel_dark")
        for y in (8, 22):
            g.cube("leaf", (-7, y, z0 - 0.4), (14, 1.5, t + 0.8), "steel")              # refuerzos
        for x in (-7, 6):
            g.cube("leaf", (x, 1, z0 - 0.3), (1, 30, t + 0.6), "steel")
        g.cube("leaf", (-3, 24, z0 - 0.1), (6, 5, t + 0.2), "glass_cracked")
        g.cube("leaf", (-3.5, 23.5, z0 - 0.25), (7, 0.5, t + 0.5), "steel")
        g.cube("leaf", (-3.5, 29, z0 - 0.25), (7, 0.5, t + 0.5), "steel")
        g.cube("leaf", (-6, 13, z0 - 0.9), (12, 1.5, t + 1.8), "chrome")                # barra antipanico
        g.cube("leaf", (-6.5, 12.3, z0 - 0.6), (1, 3, t + 1.2), "steel")
        g.cube("leaf", (5.5, 12.3, z0 - 0.6), (1, 3, t + 1.2), "steel")
        g.cube("leaf", (-8, 0, z0 - 0.15), (16, 5, t + 0.3), "stripes")
        g.cube("leaf", (-2, 17.5, z0 - 0.2), (4, 2.5, t + 0.4), "label_danger")
        for x in (-6.5, 5.5):
            for y in (2, 9.5, 20.5, 29.5):
                g.cube("leaf", (x, y, z0 - 0.5), (1, 1, t + 1.0), "chrome")              # remaches
        g.blood("smear", (5, 16, -4.5), 1.6, 9)
        g.blood("splat", (-3, 6, -4.6), 1.4)
    else:
        # Puerta de hospital: hoja pintada, ventanuco con malla, placa de empuje y zocalo
        g.cube("leaf", (-8, 0, z0), (16, 32, t), "paint_white")
        g.cube("leaf", (-7, 4, z0 - 0.2), (14, 1, t + 0.4), "paint_white_dark")
        g.cube("leaf", (-5, 16, z0 - 0.1), (10, 12, t + 0.2), "glass_cracked")
        for y in (15.5, 28):
            g.cube("leaf", (-5.5, y, z0 - 0.3), (11, 0.5, t + 0.6), "paint_white_dark")
        for x in (-5.5, 5):
            g.cube("leaf", (x, 15.5, z0 - 0.3), (0.5, 13, t + 0.6), "paint_white_dark")
        g.cube("leaf", (-2.5, 12, z0 - 0.3), (5, 3, t + 0.6), "chrome")
        g.cube("leaf", (-8, 0, z0 - 0.15), (16, 4, t + 0.3), "steel")
        g.cube("leaf", (-1.5, 29, z0 - 0.2), (3, 2, t + 0.4), "label_lab")
        g.blood("smear", (2, 14, -4.6), 1.8, 10)
        g.blood("splat", (-5, 2, -4.6), 1.6)
        g.blood("drip", -4, -4.6, 22, 8)
    return g.save()


def locker(name, body, door, wood_=False):
    g = Geo(name, 5 if not wood_ else 6)
    g.bone("root")
    g.bone("body", "root")
    g.bone("door", "root", (7, 0, 7))
    inner = "locker_inner"
    g.cube("body", (-8, 0.5, -8), (16, 31, 1), body)                 # fondo
    g.cube("body", (-8, 0.5, -7), (1, 31, 15), body)                 # lados
    g.cube("body", (7, 0.5, -7), (1, 31, 15), body)
    g.cube("body", (-8, 31.5, -8), (16, 0.5, 16), body)              # techo
    g.cube("body", (-7, 0.5, -7), (14, 1, 15), body)                 # suelo
    g.cube("body", (-7, 1.5, -7), (14, 30, 0.2), inner)              # interior oscuro
    g.cube("body", (-7, 22, -6), (14, 0.5, 6), inner)                # balda
    for x in (-7.5, 6.5):                                            # patas
        for z in (-7.5, 6.5):
            g.cube("body", (x, 0, z), (1, 0.5, 1), "black")
    if not wood_:
        g.cube("body", (-7, 1.5, -6.8), (1.2, 8, 1.2), inner)          # bulto colgado (bata)
        g.cube("door", (-7, 1.5, 7), (14, 30, 1), door)
        g.cube("door", (-4.5, 21, 7.6), (9, 7, 0.6), "vents")
        g.cube("door", (-4.5, 4, 7.6), (9, 4, 0.6), "vents")
        g.cube("door", (-2, 28.5, 7.7), (4, 2, 0.5), "label_num")
        g.cube("door", (-1, 13, 8), (2, 4, 0.8), "chrome")             # tirador
        g.cube("door", (-0.6, 11.6, 8.2), (1.2, 1.2, 0.6), "steel")    # candado roto
        for y in (4, 26):
            g.cube("door", (6.4, y, 7.8), (0.8, 2, 0.6), "steel")       # bisagras
        g.blood("smear", (-1, 17, 8), 1.5, 9)
        g.blood("drip", 3, 8.3, 30, 12)
    else:
        g.cube("door", (-7, 1.5, 7), (14, 30, 1), "wood")
        for y0 in (3, 17):
            g.cube("door", (-6, y0, 7.6), (12, 12, 0.4), "wood_dark")                # cuarterones
            g.cube("door", (-5, y0 + 1, 7.8), (10, 10, 0.4), "wood")
        g.cube("door", (-1, 15.5, 8.1), (2, 1, 0.9), "chrome")
        g.cube("body", (-8.5, 31.5, -8.5), (17, 1, 17), "wood_dark")               # cornisa
        g.cube("body", (-8.3, 0, -8.3), (16.6, 1.5, 16.6), "wood_dark")            # zocalo
        g.cube("door", (-6.5, 29.5, 7.6), (2.5, 1.5, 0.3), "label")
        g.blood("splat", (-3, 6, 8.1), 1.3)
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
    g.cube("body", (-7.5, 24, -7.5), (15, 1, 15), "bin_lid", skip=("up",))
    g.cube("body", (-3, 12, 7), (6, 6, 0.4), "biohazard")
    g.cube("body", (-3, 19, 7), (6, 2, 0.4), "label_bio")
    g.cube("body", (-7.5, 6, -7.5), (15, 1, 15), "steel_dark", skip=("up", "down"))
    for x, z in ((-6.5, -6.5), (4.5, -6.5), (-6.5, 4.5), (4.5, 4.5)):
        g.cube("body", (x, 0, z), (2, 1.5, 2), "rubber")
    g.cube("body", (-2, 0.5, 7), (4, 1, 1.5), "steel")              # pedal
    g.cube("lid", (-7.5, 25, -7.5), (15, 1.5, 15), "bin_lid")
    g.cube("lid", (-3, 26.5, 5), (6, 1, 1.5), "black")
    # algo asoma por debajo de la tapa y gotea
    g.cube("body", (-1, 24.5, 6.8), (3, 1, 1.2), "sheet", blood=True)
    g.blood("drip", 0.5, 7.4, 25, 14)
    g.blood("drip", -0.5, 7.4, 25, 9)
    g.blood("splat", (0.5, 1, 9), 2.0)
    return g.save()


def gurney():
    g = Geo("gurney", 8)
    g.bone("root")
    # bastidor y patas con ruedas
    g.cube("root", (-7.5, 8.5, -7.5), (15, 1, 15), "steel")
    for x, z in ((-7, -7), (6, -7), (-7, 6), (6, 6)):
        g.cube("root", (x, 1.8, z), (1, 6.7, 1), "chrome")
        g.cube("root", (x - 0.25, 1.2, z - 0.25), (1.5, 0.6, 1.5), "steel")
        g.cube("root", (x - 0.2, 0, z - 0.6), (1.4, 1.6, 2.2), "rubber")
    g.cube("root", (-7, 3.5, -0.5), (14, 1, 1), "steel")
    g.cube("root", (-0.5, 3.5, -7), (1, 1, 14), "steel")
    # colchon con cabecero levantado
    g.cube("root", (-7, 9.5, -7), (14, 2, 10), "mattress")
    g.cube("root", (-7, 9.5, 3), (14, 2, 4.2), "mattress", rotation=(-25, 0, 0), pivot=(0, 10.5, 3))
    # sabana arrugada que cuelga por un lado
    g.cube("root", (-7.3, 11.4, -7.2), (14.6, 0.5, 9.5), "sheet")
    g.cube("root", (-7.6, 6.5, -6), (0.4, 5.4, 8), "sheet_torn")
    # correas de sujecion con hebilla
    for z in (-4.5, 0.5):
        g.cube("root", (-7.4, 11.5, z), (14.8, 0.7, 1.6), "leather", blood=False)
        g.cube("root", (5.2, 11.6, z + 0.1), (1.2, 0.9, 1.4), "chrome", blood=False)
    # barandillas abatibles
    for x in (-8, 7.2):
        g.cube("root", (x, 13.5, -6), (0.8, 0.8, 10), "chrome")
        for z in (-6, 3.2):
            g.cube("root", (x, 9.5, z), (0.8, 4, 0.8), "chrome")
    # gotero con bolsa de sangre
    g.cube("root", (6.4, 9.5, 6.4), (0.6, 18, 0.6), "chrome")
    g.cube("root", (3, 27, 6.4), (4, 0.5, 0.5), "chrome")
    g.cube("root", (3, 22, 5.9), (2.2, 4.5, 1.4), "blood_bag", blood=False)
    g.cube("root", (3.9, 15, 6.4), (0.3, 7, 0.3), "blood_bag", blood=False)
    # la mancha: empapa el colchon, la sabana y gotea al suelo
    g.blood("splat", (-1, 12, -2), 4.5)
    g.blood("splat", (2, 11.5, 2.5), 2.2)
    g.blood("drip", -7.4, -1, 11.5, 7)
    g.blood("drip", -7.4, -3, 11, 5)
    return g.save()


def curtain():
    g = Geo("curtain", 9)
    g.bone("root")
    g.bone("cloth", "root", (0, 31, 0))
    g.cube("root", (-8, 31, -0.5), (16, 1, 1), "chrome")
    # pliegues: tiras de ancho y fondo distintos (la tela ondula)
    xs = [-8, -6.5, -4.6, -3, -1.2, 0.6, 2.4, 4, 5.8, 8]
    for i in range(len(xs) - 1):
        z = [-1.2, 0.1, -0.6, 0.5, -1.0, 0.2, -0.8, 0.4, -0.3][i]
        g.cube("cloth", (xs[i], 0.6, z), (xs[i + 1] - xs[i], 30.4, 0.9), "cloth")
    for i in range(5):
        g.cube("root", (-7.3 + 3.6 * i, 30, -0.7), (0.8, 1.5, 1.4), "steel")
    g.blood("splat", (2, 18, 0.5), 2.6)
    g.blood("drip", 1.5, 0.9, 16, 9)
    g.blood("drip", 3.2, 0.9, 17, 6)
    g.blood("smear", (-4, 9, 0.9), 1.2, 6)
    return g.save()


def valve():
    g = Geo("valve", 10)
    g.bone("root")
    g.bone("wheel", "root", (0, 8, -2))
    g.cube("root", (-5, 3, -8), (10, 10, 2), "pipe_rust")
    for x, y in ((-4, 4), (3, 4), (-4, 11), (3, 11)):
        g.cube("root", (x, y, -6.2), (1, 1, 0.4), "chrome")
    g.cube("root", (-1.5, 6.5, -6), (3, 3, 4.5), "steel")
    g.cube("root", (-8, 6, -8), (3, 4, 3), "pipe_rust")
    g.cube("root", (5, 6, -8), (3, 4, 3), "pipe_rust")
    g.cube("root", (-8.2, 5.6, -8), (1, 4.8, 3.4), "steel_dark")
    g.cube("root", (7.2, 5.6, -8), (1, 4.8, 3.4), "steel_dark")
    g.cube("root", (-2.5, 13.5, -7.8), (5, 2, 0.4), "label_danger")
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
    g.cube("root", (-5.5, 2, -4.1), (11, 9.5, 0.2), "black")
    g.cube("root", (-5.5, 12.3, -4.2), (11, 1.8, 0.3), "stripes")
    g.cube("root", (-6.8, 0.8, -8), (0.6, 14.4, 4.3), "steel")
    g.cube("root", (6.2, 0.8, -8), (0.6, 14.4, 4.3), "steel")
    # cables que salen por arriba y una etiqueta
    for x in (-4, -2.5, 3):
        g.cube("root", (x, 15, -7), (0.8, 1, 0.8), "black")
    g.cube("root", (-5.6, 12.4, -4.5), (3, 1.6, 0.3), "label")
    for i in range(4):
        x = -4.6 + i * 2.6
        g.cube("root", (x, 3, -4.4), (1.6, 0.8, 0.5), "chrome")
        g.cube("root", (x, 9.4, -4.4), (1.6, 0.8, 0.5), "chrome")
        b = g.bone(f"fuse{i}", "root")
        g.cube(b, (x, 3.8, -4.6), (1.6, 5.6, 1.2), "amber")
        g.cube(b, (x - 0.1, 3.6, -4.7), (1.8, 0.8, 1.4), "chrome")
        g.cube(b, (x - 0.1, 8.8, -4.7), (1.8, 0.8, 1.4), "chrome")
    g.bone("lamp_on", "root")
    g.cube("lamp_on", (3.5, 12.4, -4.6), (1.6, 1.6, 0.8), "lamp_green")
    g.bone("lamp_off", "root")
    g.cube("lamp_off", (3.5, 12.4, -4.6), (1.6, 1.6, 0.8), "lamp_red")
    g.blood("smear", (-1, 9, -4.2), 1.3, 7)
    return g.save()


MODELS = (("lab_door", lambda: lab_door("lab_door", False)),
          ("security_door", lambda: lab_door("security_door", True)),
          ("locker", lambda: locker("locker", "locker", "locker_door")),
          ("wardrobe", lambda: locker("wardrobe", "wood", "wood", wood_=True)),
          ("waste_container", waste_container),
          ("gurney", gurney), ("curtain", curtain), ("valve", valve), ("fuse_box", fuse_box))


def main():
    for name, fn in MODELS:
        tw, th, nf = fn()
        print(f"{name:16} textura {tw * SCALE}x{th * SCALE}  caras={nf}")
    if len(sys.argv) > 1:
        from texlib import sheet
        ims = [Image.open(ROOT / "textures/block" / f"{n}.png") for n, _ in MODELS]
        sheet(ims, 3, 2, labels=[n for n, _ in MODELS], bg=(60, 60, 64)).save(sys.argv[1])


if __name__ == "__main__":
    main()
