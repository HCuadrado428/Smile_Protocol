#!/usr/bin/env python3
"""
Texturas de los bloques de construccion del laboratorio (32x32, enlazan sin costuras) y de las
calcomanias de sangre y pintadas. Cada bloque tiene varias variantes que el blockstate elige al
azar para que no se note la repeticion.

    python3 tools/textures_lab.py              # escribe las texturas
    python3 tools/textures_lab.py preview.png  # ademas, una hoja de vista previa en mosaico
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from texlib import (Canvas, fbm, white, blur, smoothstep, jitter, grime, water_streaks, cracks, scratches,  # noqa: E402
                    chips, rust, splat_mask, apply_blood, drips, ao_edges, bevel, desaturate, stencil_text)

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/textures/block"
S = 32


def R(seed):
    return np.random.default_rng(seed)


# Suelos ------------------------------------------------------------------------------------------------

def tiles(seed, base=(186, 178, 156), grout=(64, 58, 50), n=2, dirt=0.45, crack_p=0.35, chip=4, tint=0.06,
          checker=None, blood=0.0):
    """Baldosas cuadradas n x n por bloque con junta de 1 px, mugre en las juntas y desgaste."""
    r = R(seed)
    cv = Canvas(S, S, base)
    t = S // n
    yy, xx = np.mgrid[0:S, 0:S]
    tx, ty = xx // t, yy // t
    # tono por baldosa (y damero opcional)
    for j in range(n):
        for i in range(n):
            m = (tx == i) & (ty == j)
            col = np.array(base, dtype=float)
            if checker is not None and (i + j) % 2 == 1:
                col = np.array(checker, dtype=float)
            col = col * (1 + r.uniform(-tint, tint))
            cv.c[m] = col
            # degradado suave dentro de la baldosa (no es plana del todo)
            g = r.uniform(-0.05, 0.05)
            cv.c[m] *= (1 + g * ((xx[m] % t) / t - 0.5))[:, None]
    # ruido fino y manchas grandes
    jitter(cv, r, 4, 1.2)
    cv.mul(0.92 + 0.16 * fbm(S, S, r, base=4, octaves=3))
    # junta y su sombra
    gm = ((xx % t) == 0) | ((yy % t) == 0)
    near = (((xx % t) == 1) | ((yy % t) == 1)) & ~gm
    far = (((xx % t) == t - 1) | ((yy % t) == t - 1)) & ~gm
    cv.mul(np.where(near, 0.86, 1.0))
    cv.mul(np.where(far, 1.06, 1.0))
    gcol = np.array(grout, dtype=float)
    cv.c[gm] = gcol * (0.85 + 0.3 * r.random((gm.sum(), 1)))
    # mugre acumulada cerca de las juntas
    dist = np.minimum(np.minimum(xx % t, t - xx % t), np.minimum(yy % t, t - yy % t))
    bias = np.clip(1 - dist / 5, 0, 1)
    grime(cv, r, dirt, (58, 48, 36), base=3, bias=bias)
    # desconchones en las esquinas de algunas baldosas
    corner = (dist <= 2) & ~gm
    chips(cv, r, chip, (104, 98, 90), (0.6, 1.6), mask=corner)
    # grietas
    for _ in range(3):
        if r.random() < crack_p:
            cracks(cv, r, 1, (6, 16), dark=0.55)
    # rozaduras de arrastre
    scratches(cv, r, 3, (3, 8), 0.12, (40, 34, 28))
    if blood and r.random() < blood:
        m = splat_mask(S, S, r, r.uniform(0, S), r.uniform(0, S), r.uniform(2.5, 5), drops=8, periodic=True)
        apply_blood(cv, r, m, age=0.75, opacity=0.85)
        # la sangre se mete en las juntas
        cv.mix((48, 10, 10), (gm & (blur(m, 2) > 0.1)) * 0.6)
    return cv


def linoleum(seed, base=(118, 124, 112)):
    """Linoleo moteado de hospital, con juntas soldadas, rozaduras negras y zonas gastadas."""
    r = R(seed)
    cv = Canvas(S, S, base)
    cv.mul(0.9 + 0.2 * fbm(S, S, r, base=2, octaves=4))
    # moteado: tres colores de mota
    w = white(S, S, r)
    cv.mix((150, 154, 140), (w > 0.86) * 0.5)
    cv.mix((70, 74, 66), (w < 0.1) * 0.5)
    cv.mix((168, 140, 100), (white(S, S, r) > 0.97) * 0.35)
    # junta soldada cada 32 px (en un lado)
    cv.c[:, 0] *= 0.7
    cv.c[:, 1] *= 1.06
    # zona pisada: mas clara y lisa por el centro del pasillo
    wear = fbm(S, S, r, base=1, octaves=3)
    cv.mix((150, 150, 136), smoothstep(0.6, 1.0, wear) * 0.3)
    # rozaduras negras de goma
    for _ in range(5):
        x, y = r.uniform(0, S), r.uniform(0, S)
        a = r.uniform(-0.4, 0.4) + (math.pi / 2 if r.random() < 0.5 else 0)
        for i in range(int(r.integers(3, 9))):
            cv.px(int(x + math.cos(a) * i), int(y + math.sin(a) * i), (26, 24, 22), r.uniform(0.35, 0.6))
    grime(cv, r, 0.4, (54, 48, 36), base=2)
    # burbujas/despegado del linoleo
    for _ in range(2):
        cx, cy = r.integers(S), r.integers(S)
        for dy in range(-2, 3):
            for dx in range(-3, 4):
                if dx * dx / 9 + dy * dy / 4 <= 1:
                    cv.shade_px(cx + dx, cy + dy, 1.08 if dy < 0 else 0.9)
    jitter(cv, r, 3, 1)
    return cv


def concrete(seed, base=(112, 110, 104), cracks_n=2, stains=0.5, oil=True, wall=False):
    """Hormigon manchado: poros, grietas, manchas de aceite/humedad."""
    r = R(seed)
    cv = Canvas(S, S, base)
    cv.mul(0.85 + 0.3 * fbm(S, S, r, base=2, octaves=5))
    w = white(S, S, r)
    cv.mul(np.where(w > 0.93, 0.72, 1.0))          # poros
    cv.mul(np.where(w < 0.04, 1.12, 1.0))          # arido
    grime(cv, r, stains, (60, 54, 46), base=2)
    if oil:
        m = splat_mask(S, S, r, r.uniform(0, S), r.uniform(0, S), r.uniform(3, 6), drops=2, periodic=True, lumpy=0.3)
        cv.mix((30, 30, 32), blur(m, 1) * 0.45)
    cracks(cv, r, cracks_n, (10, 26), dark=0.55)
    if wall:
        # encofrado: juntas horizontales y agujeros de los latiguillos
        cv.c[0, :] *= 0.82
        cv.c[16, :] *= 0.86
        for x, y in ((8, 8), (24, 8), (8, 24), (24, 24)):
            cv.rect(x - 1, y - 1, x, y, (44, 42, 40), 0.9)
            cv.shade_px(x - 1, y + 1, 1.15)
            cv.shade_px(x, y + 1, 1.15)
        water_streaks(cv, r, 4, (70, 66, 56), 0.3)
    jitter(cv, r, 4, 1)
    return cv


def grate(seed):
    """Rejilla de chapa sobre oscuridad (suelos de la sala de maquinas)."""
    r = R(seed)
    cv = Canvas(S, S, (14, 13, 12))
    yy, xx = np.mgrid[0:S, 0:S]
    bars = ((xx % 8) < 2) | ((yy % 8) < 2)
    metal = np.array([96, 94, 88], dtype=float)
    cv.c[bars] = metal
    cv.mul(np.where(bars & (((xx % 8) == 0) | ((yy % 8) == 0)), 1.2, 1.0))
    cv.mul(np.where(bars & (((xx % 8) == 1) & ((yy % 8) >= 2) | ((yy % 8) == 1) & ((xx % 8) >= 2)), 0.78, 1.0))
    # bajo la rejilla se intuye algo: tuberias en penumbra
    hole = ~bars
    cv.mix((34, 30, 26), hole * smoothstep(0.5, 0.9, fbm(S, S, r, base=2, octaves=2)) * 0.8)
    rust(cv, r, 0.45, base=3)
    cv.c[hole] = np.minimum(cv.c[hole], 40)
    jitter(cv, r, 3, 1)
    # marco cada bloque
    cv.c[0, :] = metal * 0.7
    cv.c[:, 0] = metal * 0.7
    return cv


def wood_floor(seed, base=(92, 64, 42)):
    """Parque viejo de tablas, con vetas, juntas oscuras y manchas de humedad."""
    r = R(seed)
    cv = Canvas(S, S, base)
    yy, xx = np.mgrid[0:S, 0:S]
    plank_h = 8
    row = yy // plank_h
    for k in range(S // plank_h):
        m = row == k
        cv.c[m] *= r.uniform(0.82, 1.12)
        off = r.integers(0, S)
        # junta vertical desplazada en cada fila
        cv.c[m & (((xx + off) % S) == 0)] *= 0.45
    grain = fbm(S, S, r, base=2, octaves=4, aspect=0.25)
    cv.mul(0.85 + 0.3 * np.sin(grain * 18 + yy * 0.2) * 0.5 + 0.15)
    cv.c[(yy % plank_h) == 0] *= 0.5
    cv.c[(yy % plank_h) == 1] *= 1.08
    # nudos
    for _ in range(2):
        cx, cy = r.integers(S), r.integers(S)
        for dy in range(-1, 2):
            for dx in range(-2, 3):
                cv.shade_px(cx + dx, cy + dy, 0.7 if abs(dx) < 2 else 0.85)
    grime(cv, r, 0.45, (40, 30, 22), base=2)
    # mancha de humedad clara (madera hinchada)
    m = smoothstep(0.75, 0.95, fbm(S, S, r, base=2, octaves=3))
    cv.mix((120, 108, 88), m * 0.35)
    jitter(cv, r, 4, 1.5)
    return cv


def moss_tiles(seed):
    """Baldosas invadidas por moho y musgo (invernadero)."""
    cv = tiles(seed, base=(150, 156, 132), grout=(46, 54, 36), dirt=0.55, crack_p=0.6, chip=6)
    r = R(seed + 99)
    n = fbm(S, S, r, base=3, octaves=4)
    moss = smoothstep(0.5, 0.75, n)
    w = white(S, S, r)
    cv.mix((56, 78, 34), moss * 0.85)
    cv.mix((84, 108, 44), moss * (w > 0.7) * 0.6)
    cv.mix((30, 44, 22), moss * (w < 0.15) * 0.6)
    return cv


# Paredes -----------------------------------------------------------------------------------------------

def plaster(seed, base=(170, 166, 150), under=(118, 114, 104), peel=0.35, streaks=6, low=False):
    """Pared enlucida y pintada: desconchada, con humedad que baja y mugre a la altura de la mano."""
    r = R(seed)
    cv = Canvas(S, S, base)
    cv.mul(0.93 + 0.12 * fbm(S, S, r, base=3, octaves=4))
    # textura de rodillo
    cv.mul(0.97 + 0.06 * fbm(S, S, r, base=8, octaves=2))
    # pintura levantada: desconchones irregulares donde se ve el yeso
    n = fbm(S, S, r, base=3, octaves=5) * 0.7 + fbm(S, S, r, base=8, octaves=2) * 0.3
    p = n > 1 - peel * 0.42
    under_c = np.array(under, dtype=float)[None, None, :] * (0.85 + 0.3 * fbm(S, S, r, base=4, octaves=2))[..., None]
    cv.c[p] = under_c[p]
    below = np.roll(p, -1, 0) & ~p
    above = np.roll(p, 1, 0) & ~p
    cv.c[above] *= 0.7                       # sombra del borde de la pintura (luz de arriba)
    cv.c[below] *= 1.1
    water_streaks(cv, r, streaks, (104, 90, 62), 0.32)
    grime(cv, r, 0.35, (70, 62, 48), base=2)
    if low:
        # sucio de abajo (salpicaduras de fregona y rozaduras)
        y = np.arange(S)[:, None] / S
        cv.mul(1 - 0.18 * smoothstep(0.5, 1.0, y) * np.ones((1, S)))
    scratches(cv, r, 2, (3, 7), 0.15, (90, 84, 74))
    jitter(cv, r, 3, 1)
    return cv


def wall_tiles(seed, base=(176, 182, 176), grout=(92, 92, 86), tw=8, th=8, offset=False, blood=0.0):
    """Azulejo pequeno de pared, con piezas que faltan, juntas sucias y alguna mancha."""
    r = R(seed)
    cv = Canvas(S, S, base)
    yy, xx = np.mgrid[0:S, 0:S]
    sx = (xx + ((yy // th) % 2) * (tw // 2 if offset else 0)) % S
    cx, cy = sx // tw, yy // th
    ids = cy * 100 + cx
    for i in np.unique(ids):
        m = ids == i
        cv.c[m] *= r.uniform(0.92, 1.06)
    jitter(cv, r, 3, 1)
    # brillo del vidriado: esquina superior izquierda de cada azulejo
    lx, ly = sx % tw, yy % th
    cv.mul(np.where((lx == 1) & (ly >= 1) | (ly == 1) & (lx >= 1), 1.07, 1.0))
    cv.mul(np.where((lx == tw - 1) | (ly == th - 1), 0.9, 1.0))
    g = (lx == 0) | (ly == 0)
    cv.c[g] = np.array(grout, dtype=float) * (0.8 + 0.3 * r.random((g.sum(), 1)))
    grime(cv, r, 0.4, (66, 60, 44), base=2, bias=g.astype(float))
    # azulejos que faltan: se ve el cemento con las marcas de la llana
    tiles_ids = np.unique(ids)
    for i in r.choice(tiles_ids, size=int(r.integers(0, 3)), replace=False):
        m = ids == i
        cv.c[m] = np.array([96, 92, 84]) * (0.85 + 0.3 * r.random((m.sum(), 1)))
        cv.c[m & (ly % 3 == 0)] *= 0.85
    # grietas que atraviesan azulejos
    if r.random() < 0.6:
        cracks(cv, r, 1, (8, 20), dark=0.5)
    water_streaks(cv, r, 3, (110, 96, 64), 0.25)
    if blood and r.random() < blood:
        m = splat_mask(S, S, r, r.uniform(4, S - 4), r.uniform(4, 14), r.uniform(2, 4), drops=6, periodic=True)
        apply_blood(cv, r, m, age=0.7)
        drips(cv, r, 3, (6, 26), int(r.integers(10, 16)), (4, 12), age=0.7)
    return cv


def metal_panels(seed, base=(88, 92, 86), paint=(140, 122, 52)):
    """Chapas remachadas pintadas, oxidadas por las juntas (planta de energia)."""
    r = R(seed)
    cv = Canvas(S, S, paint)
    cv.mul(0.9 + 0.2 * fbm(S, S, r, base=2, octaves=4))
    yy, xx = np.mgrid[0:S, 0:S]
    seam = (xx % 16 == 0) | (yy % 16 == 0)
    cv.c[seam] = np.array(base, dtype=float) * 0.55
    cv.mul(np.where(((xx % 16) == 1) | ((yy % 16) == 1), 1.12, 1.0))
    cv.mul(np.where(((xx % 16) == 15) | ((yy % 16) == 15), 0.82, 1.0))
    for y in (3, 12):
        for x in (3, 12):
            for ox in (0, 16):
                for oy in (0, 16):
                    cv.px(x + ox, y + oy, (60, 58, 52), 1)
                    cv.px(x + ox - 1, y + oy - 1, (190, 180, 150), 0.5)
                    cv.px(x + ox + 1, y + oy + 1, (30, 28, 24), 0.6)
    dist = np.minimum(np.minimum(xx % 16, 16 - xx % 16), np.minimum(yy % 16, 16 - yy % 16))
    rust(cv, r, 0.5, base=3, bias=np.clip(1 - dist / 6, 0, 1))
    chips(cv, r, 6, np.array(base) * 0.9, (0.8, 2))
    water_streaks(cv, r, 5, (96, 50, 24), 0.4)
    jitter(cv, r, 3, 1)
    return cv


def vent_duct(seed):
    """Chapa galvanizada de los conductos: costillas horizontales, remaches y oxido en las juntas."""
    r = R(seed)
    cv = Canvas(S, S, (150, 152, 150))
    yy, xx = np.mgrid[0:S, 0:S]
    spangle = fbm(S, S, r, base=8, octaves=2)
    cv.mul(0.82 + 0.3 * spangle)
    rib = (yy % 8)
    cv.mul(np.where(rib == 0, 0.62, np.where(rib == 1, 1.18, np.where(rib == 7, 0.85, 1.0))))
    cv.c[:, 0] *= 0.6
    cv.c[:, 31] *= 0.75
    for y in (4, 20):
        for x in (2, 29):
            cv.px(x, y, (80, 80, 78), 1)
            cv.px(x - 1, y - 1, (210, 210, 205), 0.6)
    rust(cv, r, 0.35, base=3, bias=np.clip(1 - np.minimum(xx, 31 - xx) / 5, 0, 1))
    grime(cv, r, 0.35)
    jitter(cv, r, 3, 1)
    return cv


def vent_grille(seed):
    """Rejilla de ventilacion: marco y lamas inclinadas con huecos (se ve la oscuridad del conducto)."""
    r = R(seed)
    cv = Canvas(S, S, (120, 122, 118))
    yy, xx = np.mgrid[0:S, 0:S]
    frame = (xx < 3) | (xx > 28) | (yy < 3) | (yy > 28)
    slat = ((yy - 3) % 5) < 3
    cv.c[slat] = (128, 130, 126)
    cv.mul(np.where(((yy - 3) % 5) == 0, 1.25, np.where(((yy - 3) % 5) == 2, 0.7, 1.0)))
    cv.c[frame] = (96, 98, 94)
    cv.mul(np.where(frame & ((xx == 1) | (yy == 1)), 1.2, 1.0))
    for (x, y) in ((1, 1), (30, 1), (1, 30), (30, 30)):
        cv.px(x, y, (60, 60, 58), 1)
    rust(cv, r, 0.5, base=3)
    grime(cv, r, 0.4)
    cv.a = np.where(frame | slat, 1.0, 0.0)
    return cv


def vent_fan(seed, spinning):
    """Ventilador del conducto: carcasa redonda y aspas (borrosas si gira)."""
    r = R(seed)
    cv = Canvas(S, S, (30, 30, 30))
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    d = np.hypot(xx - 15.5, yy - 15.5)
    ang = np.arctan2(yy - 15.5, xx - 15.5)
    housing = (d > 13) & (d < 16)
    hub = d < 3.2
    blades = (d < 13) & (np.abs(np.sin(2 * ang + d * 0.12)) > 0.75)
    cv.c[:] = (18, 18, 18)
    if spinning:
        cv.c[d < 13] = (64, 64, 62)
        cv.mul(np.where(d < 13, 0.8 + 0.3 * np.sin(d * 1.3), 1.0))
    else:
        cv.c[blades] = (110, 112, 108)
        cv.mul(np.where(blades, 0.8 + 0.4 * np.cos(ang * 2), 1.0))
    cv.c[housing] = (96, 98, 94)
    cv.c[hub] = (70, 70, 68)
    corner = d >= 16
    cv.c[corner] = (120, 122, 118)
    rust(cv, r, 0.35, base=3)
    return cv


def hazard(seed):
    r = R(seed)
    cv = Canvas(S, S, (20, 20, 18))
    yy, xx = np.mgrid[0:S, 0:S]
    band = ((xx + yy) // 8) % 2 == 0
    cv.c[band] = (178, 146, 32)
    cv.mul(0.9 + 0.2 * fbm(S, S, r, base=2, octaves=3))
    chips(cv, r, 14, (92, 90, 84), (0.8, 2))
    grime(cv, r, 0.5, (40, 34, 24))
    scratches(cv, r, 8, (3, 10), 0.3, (120, 118, 110))
    jitter(cv, r, 4, 1)
    return cv


# Techos ------------------------------------------------------------------------------------------------

def ceiling_tiles(seed, base=(150, 146, 134), stain=0.5, missing=False):
    """Falso techo de placas acusticas 16x16 con perfil metalico, manchas de agua y agujeros."""
    r = R(seed)
    cv = Canvas(S, S, base)
    yy, xx = np.mgrid[0:S, 0:S]
    # poros de la placa acustica
    w = white(S, S, r)
    cv.mul(np.where(w > 0.82, 0.86, 1.0))
    cv.mul(0.92 + 0.12 * fbm(S, S, r, base=4, octaves=3))
    # placas con tono propio
    for j in range(2):
        for i in range(2):
            m = (xx // 16 == i) & (yy // 16 == j)
            cv.c[m] *= r.uniform(0.9, 1.05)
    # manchas de agua: anillos marrones concentricos
    for _ in range(int(r.integers(1, 3))):
        if r.random() > stain:
            continue
        cx, cy, rad = r.uniform(0, S), r.uniform(0, S), r.uniform(4, 9)
        dx = (xx - cx + S / 2) % S - S / 2
        dy = (yy - cy + S / 2) % S - S / 2
        ang = np.arctan2(dy, dx)
        rr = rad * (1 + 0.25 * np.sin(3 * ang + r.uniform(0, 6)) + 0.15 * np.sin(5 * ang + r.uniform(0, 6)))
        d = np.hypot(dx, dy)
        inside = smoothstep(rr + 1, rr - 1, d)
        ring = np.exp(-((d - rr) ** 2) / 1.2) + 0.6 * np.exp(-((d - rr * 0.6) ** 2) / 0.8)
        cv.mix((128, 104, 64), inside * 0.25)
        cv.mix((92, 70, 38), np.clip(ring, 0, 1) * 0.55)
    grime(cv, r, 0.3, (60, 54, 44))
    if missing:
        # una placa caida: hueco negro con cables
        i, j = r.integers(0, 2, size=2)
        m = (xx // 16 == i) & (yy // 16 == j)
        cv.c[m] = (12, 11, 10)
        for _ in range(2):
            x = int(i * 16 + r.integers(3, 13))
            for y in range(j * 16 + 1, j * 16 + 15):
                x += int(r.integers(-1, 2))
                x = max(i * 16 + 1, min(i * 16 + 14, x))
                cv.px(x, y, (40, 36, 30), 1)
    # perfil metalico en T
    prof = (xx % 16 == 0) | (yy % 16 == 0)
    cv.c[prof] = (124, 124, 120)
    cv.mul(np.where(((xx % 16) == 1) | ((yy % 16) == 1), 0.8, 1.0))
    rust(cv, r, 0.2, base=4, bias=prof.astype(float))
    jitter(cv, r, 3, 1)
    return cv


# Mobiliario cubico -------------------------------------------------------------------------------------

def crate(seed, side=True):
    """Caja de madera de suministros con plantilla, para sustituir a los barriles."""
    r = R(seed)
    cv = Canvas(S, S, (126, 98, 62))
    yy, xx = np.mgrid[0:S, 0:S]
    grain = fbm(S, S, r, base=2, octaves=4, aspect=0.2)
    cv.mul(0.8 + 0.35 * grain)
    for y in range(0, S, 8):
        cv.c[y, :] *= 0.5
        cv.c[(y + 1) % S, :] *= 1.1
    # marco
    for i in range(3):
        cv.c[i, :] = cv.c[i, :] * 0 + np.array([96, 72, 44]) * (1 - 0.08 * i)
        cv.c[S - 1 - i, :] = np.array([88, 66, 40])
        cv.c[:, i] = np.array([96, 72, 44])
        cv.c[:, S - 1 - i] = np.array([84, 62, 38])
    bevel(cv, 0, 0, S - 1, S - 1)
    bevel(cv, 3, 3, S - 4, S - 4, 0.8, 1.15)
    if side:
        stencil_text(cv, 6, 11, "SP-", (30, 26, 22), 0.75, r)
        stencil_text(cv, 6, 18, "BIO", (110, 20, 18), 0.7, r)
    for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        cv.px(x, y, (56, 54, 50), 1)
    grime(cv, r, 0.45, (46, 36, 24), periodic=False)
    jitter(cv, r, 4, 1.5)
    return cv


def pipes(seed):
    """Haz de tuberias oxidadas (para los techos bajos por los que hay que agacharse)."""
    r = R(seed)
    cv = Canvas(S, S, (24, 22, 20))
    yy = np.arange(S)
    for y0, d, col in ((1, 10, (112, 104, 92)), (12, 8, (96, 100, 96)), (21, 10, (118, 92, 66))):
        for y in range(y0, min(S, y0 + d)):
            t = (y - y0) / (d - 1)
            shade = 0.45 + 0.85 * math.sin(t * math.pi) ** 0.7 + (0.25 if 0.2 < t < 0.35 else 0)
            cv.c[y, :] = np.array(col) * shade
        # bridas
        for x in (6, 22):
            cv.rect(x, y0 - 1 if y0 else 0, x + 1, min(S - 1, y0 + d), np.array(col) * 0.7)
    rust(cv, r, 0.5, base=3)
    water_streaks(cv, r, 4, (80, 40, 20), 0.35)
    jitter(cv, r, 3, 1)
    return cv


def debris(seed):
    """Cascotes y polvo (losa de escombros)."""
    r = R(seed)
    cv = concrete(seed, (104, 100, 94), cracks_n=4, oil=False)
    n = fbm(S, S, r, base=6, octaves=3)
    cv.mul(0.7 + 0.5 * n)
    w = white(S, S, r)
    cv.mix((150, 146, 136), (w > 0.9) * 0.5)
    cv.mix((36, 34, 32), (w < 0.08) * 0.6)
    return cv


def server_rack(seed):
    """Armario de servidores: unidades de rack con rejillas y LEDs (algunos rojos)."""
    r = R(seed)
    cv = Canvas(S, S, (36, 38, 40))
    cv.mul(0.9 + 0.2 * fbm(S, S, r, base=2, octaves=3))
    for y0 in range(2, S - 2, 5):
        cv.rect(2, y0, S - 3, y0 + 3, (54, 56, 60))
        bevel(cv, 2, y0, S - 3, y0 + 3, 1.25, 0.6)
        for x in range(5, 20, 2):
            cv.rect(x, y0 + 1, x, y0 + 2, (18, 18, 20))
        for i, x in enumerate((23, 25, 27)):
            v = r.random()
            col = (60, 200, 90) if v < 0.5 else (210, 40, 30) if v < 0.7 else (220, 160, 40) if v < 0.8 else (30, 34, 30)
            cv.px(x, y0 + 1, col, 1, wrap=False)
    cv.rect(0, 0, S - 1, 1, (24, 24, 26))
    cv.rect(0, S - 2, S - 1, S - 1, (24, 24, 26))
    cv.rect(0, 0, 1, S - 1, (28, 28, 30))
    cv.rect(S - 2, 0, S - 1, S - 1, (28, 28, 30))
    grime(cv, r, 0.35, (30, 26, 20), periodic=False)
    scratches(cv, r, 4, (3, 8), 0.15)
    jitter(cv, r, 2, 0.5)
    return cv


def machine(seed):
    """Carcasa de generador: chapa verde vieja con manometro, rejilla y aviso de peligro."""
    r = R(seed)
    cv = metal_panels(seed, base=(60, 66, 60), paint=(70, 86, 72))
    # rejilla de ventilacion
    cv.rect(4, 18, 27, 28, (20, 22, 20))
    for y in range(19, 28, 2):
        cv.rect(5, y, 26, y, (64, 72, 64))
    bevel(cv, 4, 18, 27, 28, 0.7, 1.2)
    # manometro
    cx, cy = 10, 9
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if d <= 5:
                cv.px(x, y, (40, 40, 36) if d > 4 else (196, 190, 170), 1, wrap=False)
    for t_ in np.linspace(0, 1, 6):
        cv.px(int(cx + math.cos(t_ * 3 + 0.4) * 3), int(cy - math.sin(t_ * 3 + 0.4) * 3), (40, 40, 36), 1, wrap=False)
    cv.px(cx, cy, (20, 20, 20), 1, wrap=False)
    cv.px(cx + 1, cy - 1, (180, 30, 24), 1, wrap=False)
    cv.px(cx + 2, cy - 2, (180, 30, 24), 1, wrap=False)
    # aviso
    cv.rect(18, 5, 27, 13, (196, 160, 36))
    stencil_text(cv, 21, 7, "!", (20, 18, 16), 1)
    bevel(cv, 18, 5, 27, 13)
    rust(cv, r, 0.3)
    return cv


# Calcomanias (con transparencia) ----------------------------------------------------------------------

def decal_blood_floor(seed, kind):
    r = R(seed)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    if kind == "pool":
        m = splat_mask(S, S, r, 16, 16, 8.5, drops=14, lumpy=0.35)
    elif kind == "splat":
        m = splat_mask(S, S, r, 16, 16, 4.5, drops=22, lumpy=0.6)
    elif kind == "drag":
        m = splat_mask(S, S, r, 16, 6, 4, drops=6, streak=(math.pi / 2 + r.uniform(-0.2, 0.2), 24))
        # marcas de dedos arrastrados
        for dx in (-3, -1, 1, 3):
            for y in range(8, 30):
                x = int(16 + dx + (y - 8) * 0.05)
                m[y, x] = max(m[y, x], 0.9 * (1 - (y - 8) / 30))
    elif kind == "drops":
        m = np.zeros((S, S))
        for _ in range(9):
            m = np.maximum(m, splat_mask(S, S, r, r.uniform(3, S - 3), r.uniform(3, S - 3), r.uniform(0.6, 1.8),
                                         drops=2, lumpy=0.2))
    else:  # footprints
        m = np.zeros((S, S))
        for i, (x, y) in enumerate(((10, 26), (20, 18), (11, 9), (21, 1))):
            for dy in range(-3, 4):
                for dx in range(-1, 2):
                    if abs(dx) + abs(dy) * 0.5 <= 2.2 and 0 <= y + dy < S and 0 <= x + dx < S:
                        m[y + dy, x + dx] = 0.9 - i * 0.18
            for dx in range(-1, 2):
                if 0 <= y + 5 < S:
                    m[y + 5, x + dx] = 0.8 - i * 0.18
    col = Canvas(S, S, (0, 0, 0))
    apply_blood(col, r, (m > 0.05).astype(float), age=r.uniform(0.3, 0.8))
    cv.c = col.c
    cv.a = np.clip(m * 1.4, 0, 0.94)
    cv.a[cv.a < 0.12] = 0
    return cv


def _supersample(draw_fn, n=4):
    from PIL import ImageDraw
    im = Image.new("L", (S * n, S * n), 0)
    draw_fn(ImageDraw.Draw(im), n)
    return np.asarray(im.resize((S, S), Image.BOX), dtype=float) / 255


def _hand(r):
    """Huella de mano ensangrentada que resbala pared abajo."""
    ang = r.uniform(-0.25, 0.25)
    cx, cy = 15.5, 15

    k = 1.55

    def rot(x, y):
        x, y = x * k, y * k
        return (cx + x * math.cos(ang) - y * math.sin(ang), cy + 2 + x * math.sin(ang) + y * math.cos(ang))

    def draw(d, n):
        def caps(x0, y0, x1, y1, w):
            a, b = rot(x0, y0), rot(x1, y1)
            w *= k
            d.line([(a[0] * n, a[1] * n), (b[0] * n, b[1] * n)], fill=255, width=int(w * n))
            for p in (a, b):
                d.ellipse([(p[0] - w / 2) * n, (p[1] - w / 2) * n, (p[0] + w / 2) * n, (p[1] + w / 2) * n], fill=255)
        pts = [rot(x, y) for x, y in ((-4.5, -1), (4.5, -1.5), (5, 5), (2, 8), (-3, 8), (-5.5, 4))]
        d.polygon([(x * n, y * n) for x, y in pts], fill=255)
        for fx, ln, w in ((-3.6, 6.2, 2.1), (-1.2, 7.6, 2.2), (1.3, 7.4, 2.2), (3.6, 5.6, 1.9)):
            caps(fx, -1.5, fx + fx * 0.12, -1.5 - ln, w)
        caps(-5.2, 4, -9, -0.5, 2.3)
        # resbalon: la mano se arrastra hacia abajo
        for j in range(5):
            x = -3.5 + j * 1.8
            caps(x, 6, x + r.uniform(-0.4, 0.4), 6 + r.uniform(2, 5), 1.0)
    m = _supersample(draw)
    m = (m > 0.45).astype(float)
    # huecos: la piel no deja sangre en los pliegues
    m *= (fbm(S, S, r, base=5, octaves=2, periodic=False) > 0.15) * (white(S, S, r) > 0.07)
    return m


def _smear(r):
    """Pasada de mano: varios dedos arrastrados en curva, que se van quedando sin sangre."""
    def draw(d, n):
        for k in range(4):
            off = k * 2.4 + r.uniform(-0.3, 0.3)
            pts = []
            for i in range(30):
                t = i / 29
                pts.append(((3 + t * 26) * n, (7 + off + math.sin(t * 2.6) * 6 + t * 4) * n))
            for i in range(len(pts) - 1):
                if r.random() < 0.9 - 0.5 * (i / len(pts)) ** 2:
                    d.line([pts[i], pts[i + 1]], fill=255, width=int((1.7 - 0.9 * i / len(pts)) * n))
    m = (_supersample(draw) > 0.4).astype(float)
    for _ in range(6):
        x = int(r.integers(4, 18))
        ys = np.where(m[:, x] > 0)[0]
        if len(ys):
            for y in range(ys.max(), min(S, ys.max() + int(r.integers(3, 12)))):
                m[y, x] = 1
    return m


def decal_wall(seed, kind):
    r = R(seed)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    m = np.zeros((S, S))
    blood = True
    if kind in ("hand", "smear"):
        m = _hand(r) if kind == "hand" else _smear(r)
    elif kind in ("smile", "sonrie", "help", "behind", "arrow"):
        if kind == "smile":
            xx, yy = np.mgrid[0:S, 0:S]
            # ojos y boca de una sonrisa pintada con el dedo
            for ex in (10, 21):
                m = np.maximum(m, smoothstep(2.6, 1.6, np.hypot(xx - 10, yy - ex)))
            for t in np.linspace(0.15, math.pi - 0.15, 80):
                x, y = 16 - math.cos(t) * 11, 15 + math.sin(t) * 9
                m = np.maximum(m, smoothstep(1.7, 0.9, np.hypot(yy - x, xx - y)))
        else:
            text = {"sonrie": "SONRÍE", "help": "AYUDA", "behind": "DETRÁS", "arrow": "->"}[kind]
            from texlib import FONT3x5
            tmp = Canvas(S, S, (0, 0, 0))
            y = 13
            scale = 1 if len(text) > 5 else 1
            width = len(text) * 4 * scale - 1
            stencil_text(tmp, (S - width) // 2, y, text.replace("Á", "A"), (255, 255, 255), 1.0, None,
                         FONT3x5, scale)
            m = (tmp.c[..., 0] > 128).astype(float)
            # trazo grueso e irregular (pintado con los dedos)
            m = np.maximum(m, np.roll(m, 1, 1) * (white(S, S, r) > 0.3))
            m = np.maximum(m, np.roll(m, 1, 0) * (white(S, S, r) > 0.5))
        # goterones de cada trazo
        cols = np.where(m.sum(0) > 0)[0]
        for x in r.choice(cols, size=min(len(cols), 7), replace=False) if len(cols) else []:
            ys = np.where(m[:, x] > 0.5)[0]
            if len(ys) == 0:
                continue
            y0 = ys.max()
            for y in range(y0, min(S, y0 + int(r.integers(2, 10)))):
                m[y, x] = 1
    elif kind == "scratches":
        blood = False
        for i in range(4):
            x0 = 8 + i * 4 + r.uniform(-0.5, 0.5)
            for y in range(4, 28):
                x = int(x0 + (y - 4) * 0.18 + math.sin(y * 0.3 + i) * 0.4)
                if r.random() < 0.92:
                    m[y, x] = 1
        cv.c[:] = (24, 22, 20)
        cv.a = m * 0.85
        # borde claro (yeso levantado)
        lift = np.roll(m, 1, 1) * (1 - m)
        cv.c[lift > 0] = (150, 144, 128)
        cv.a = np.maximum(cv.a, lift * 0.5)
        # algo de sangre en las unas
        tips = np.zeros((S, S))
        for i in range(4):
            tips[4:8, 8 + i * 4] = 1
        col = Canvas(S, S, (0, 0, 0))
        apply_blood(col, r, tips, 0.6)
        cv.c = np.where(tips[..., None] > 0, col.c, cv.c)
        return cv
    if blood:
        col = Canvas(S, S, (0, 0, 0))
        apply_blood(col, r, (m > 0.1).astype(float), age=r.uniform(0.35, 0.7))
        cv.c = col.c
        cv.a = np.clip(m * 1.3, 0, 0.95)
        cv.a[cv.a < 0.15] = 0
    return cv


FONT_DIR = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/font"


def decal_writing(seed, lines, size=15):
    """Pintada de sangre hecha con el dedo, 64x32 (ocupa dos bloques de ancho)."""
    from PIL import ImageDraw, ImageFont
    r = R(seed)
    W, H = 64, 32
    font = ImageFont.truetype(str(FONT_DIR / "rock_salt.ttf"), size)
    im = Image.new("L", (W * 4, H * 4), 0)
    d = ImageDraw.Draw(im)
    # tamano que cabe en el ancho (deja margen) y en el alto
    size = size * 4
    while True:
        f4 = ImageFont.truetype(str(FONT_DIR / "rock_salt.ttf"), size)
        widest = max(d.textlength(line, font=f4) for line in lines)
        if (widest <= W * 4 * 0.9 and len(lines) * size * 1.25 <= H * 4 * 0.95) or size < 16:
            break
        size -= 2
    size /= 4
    total = len(lines) * size * 4 * 1.05
    y = (H * 4 - total) / 2 - size * 1.4
    for line in lines:
        w = d.textlength(line, font=f4)
        d.text(((W * 4 - w) / 2 + r.uniform(-6, 6), y), line, font=f4, fill=255)
        y += size * 4 * 1.15
    im = im.rotate(r.uniform(-5, 5), resample=Image.BICUBIC)
    a = np.asarray(im.resize((W, H), Image.BOX), dtype=float) / 255
    m = (a > 0.22).astype(float)
    # trazo irregular: algun pixel de mas y huecos donde el dedo se queda sin sangre
    m = np.maximum(m, (np.roll(m, 1, 1) * (white(H, W, r) > 0.75)))
    m *= (white(H, W, r) > 0.06)
    # goterones
    cols = [x for x in range(W) if m[:, x].any()]
    for x in r.choice(cols, size=min(len(cols), 10), replace=False):
        y0 = np.where(m[:, x] > 0)[0].max()
        for yy in range(y0, min(H, y0 + int(r.integers(2, 9)))):
            m[yy, x] = 1
    cv = Canvas(W, H, (0, 0, 0), 0.0)
    col = Canvas(W, H, (0, 0, 0))
    apply_blood(col, r, m, age=r.uniform(0.35, 0.65))
    cv.c = col.c
    cv.a = m * 0.95
    return cv


WRITINGS = [["SONRÍE"], ["AYUDA"], ["DETRÁS", "DE TI"], ["NO", "MIRES"], ["TE VE"], ["SONRÍE", "SONRÍE"],
            ["NO GRITES"], ["CORRE"]]


# Lista ----------------------------------------------------------------------------------------------

def blocks():
    """nombre -> lista de lienzos (variantes)."""
    return {
        "lab_tile_floor": [tiles(100 + i) for i in range(4)],
        "lab_tile_floor_dark": [tiles(110 + i, base=(78, 84, 76), grout=(30, 30, 28), checker=(158, 152, 132), dirt=0.5)
                                for i in range(4)],
        "lab_linoleum": [linoleum(120 + i) for i in range(3)],
        "lab_concrete_floor": [concrete(130 + i) for i in range(4)],
        "lab_grate": [grate(140 + i) for i in range(2)],
        "lab_wood_floor": [wood_floor(150 + i) for i in range(3)],
        "lab_morgue_floor": [tiles(160 + i, base=(168, 172, 166), grout=(60, 64, 60), n=4, blood=0.6, crack_p=0.25)
                             for i in range(4)],
        "lab_moss_floor": [moss_tiles(170 + i) for i in range(3)],
        "lab_plaster": [plaster(200 + i) for i in range(4)],
        "lab_plaster_green": [plaster(210 + i, base=(120, 140, 120), under=(150, 146, 132)) for i in range(4)],
        "lab_plaster_blue": [plaster(215 + i, base=(118, 136, 146), under=(150, 146, 132)) for i in range(3)],
        "lab_wall_tiles": [wall_tiles(220 + i) for i in range(4)],
        "lab_wall_tiles_teal": [wall_tiles(230 + i, base=(92, 132, 128), grout=(60, 70, 66), blood=0.35)
                                for i in range(4)],
        "lab_wall_tiles_white": [wall_tiles(235 + i, base=(184, 186, 178), tw=16, th=8, offset=True, blood=0.25)
                                 for i in range(3)],
        "lab_concrete_wall": [concrete(240 + i, (118, 116, 110), cracks_n=1, oil=False, wall=True) for i in range(3)],
        "lab_metal_wall": [metal_panels(250 + i) for i in range(3)],
        "lab_hazard": [hazard(260)],
        "lab_ceiling": [ceiling_tiles(300 + i, missing=(i == 3)) for i in range(4)],
        "lab_ceiling_concrete": [concrete(310 + i, (70, 68, 64), cracks_n=1, oil=False) for i in range(2)],
        "lab_crate": [crate(400)],
        "lab_crate_top": [crate(401, side=False)],
        "lab_pipes": [pipes(410)],
        "lab_server": [server_rack(430 + i) for i in range(3)],
        "lab_machine": [machine(440 + i) for i in range(2)],
        "lab_debris": [debris(420)],
        "lab_vent_duct": [vent_duct(450 + i) for i in range(2)],
        "vent_grille": [vent_grille(460)],
        "vent_fan": [vent_fan(470, False)],
        "vent_fan_spin": [vent_fan(470, True)],
    }


DECALS_FLOOR = ["pool", "splat", "drag", "drops", "footprints", "splat"]
DECALS_WALL = ["hand", "smear", "smile", "scratches", "hand", "smear", "scratches", "smile"]


def main():
    out = []
    labels = []
    for name, variants in blocks().items():
        for i, cv in enumerate(variants):
            fn = name if i == 0 else f"{name}_{i}"
            cv.save(ROOT / f"{fn}.png")
            out.append(cv.image())
            labels.append(fn)
    for i, k in enumerate(DECALS_FLOOR):
        cv = decal_blood_floor(500 + i, k)
        cv.save(ROOT / f"blood_floor_{i}.png")
        out.append(cv.image())
        labels.append(f"blood_floor_{i}")
    for i, k in enumerate(DECALS_WALL):
        cv = decal_wall(600 + i, k)
        cv.save(ROOT / f"wall_decal_{i}.png")
        out.append(cv.image())
        labels.append(f"wall_decal_{i}")
    for i, lines in enumerate(WRITINGS):
        cv = decal_writing(700 + i, lines, 15 if len(lines) == 1 else 11)
        cv.save(ROOT / f"wall_writing_{i}.png")
        out.append(cv.image())
        labels.append(f"wall_writing_{i}")
    print(f"{len(out)} texturas")
    if len(sys.argv) > 1:
        tiled = []
        for im in out:
            t = Image.new("RGBA", (im.width * 2, im.height * 2), (120, 116, 106, 255))
            for y in range(2):
                for x in range(2):
                    t.alpha_composite(im, (x * im.width, y * im.height))
            tiled.append(t)
        from texlib import sheet
        sheet(tiled, 8, 3, labels=labels).save(sys.argv[1])


if __name__ == "__main__":
    main()
