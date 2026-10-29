"""
Utilidades de pintura procedural para las texturas de Smile Protocol.

Todo trabaja con arrays numpy en coma flotante: color (H, W, 3) en 0-255 y alfa (H, W) en 0-1.
El ruido es periodico (las texturas de bloque enlazan sin costuras) y sale de una semilla, asi
que cada ejecucion genera exactamente las mismas imagenes.
"""
import math

import numpy as np
from PIL import Image


# Ruido -----------------------------------------------------------------------------------------------

def _smooth(t):
    return t * t * (3 - 2 * t)


def value_noise(h, w, cells_y, cells_x, rng, periodic=True):
    """Ruido de valor suave con `cells` celdas por lado. Periodico si se pide."""
    gy, gx = cells_y + (0 if periodic else 1), cells_x + (0 if periodic else 1)
    grid = rng.random((gy, gx))
    ys = np.arange(h) / h * cells_y
    xs = np.arange(w) / w * cells_x
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = _smooth(ys - y0)[:, None]
    fx = _smooth(xs - x0)[None, :]
    y1, x1 = y0 + 1, x0 + 1
    if periodic:
        y0, y1, x0, x1 = y0 % gy, y1 % gy, x0 % gx, x1 % gx
    else:
        y1, x1 = np.minimum(y1, gy - 1), np.minimum(x1, gx - 1)
    a = grid[np.ix_(y0, x0)]
    b = grid[np.ix_(y0, x1)]
    c = grid[np.ix_(y1, x0)]
    d = grid[np.ix_(y1, x1)]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(h, w, rng, base=2, octaves=4, gain=0.5, periodic=True, aspect=1.0):
    """Ruido fractal normalizado a 0-1."""
    out = np.zeros((h, w))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        c = base * (2 ** o)
        out += value_noise(h, w, max(1, int(round(c * aspect))), c, rng, periodic) * amp
        total += amp
        amp *= gain
    out /= total
    lo, hi = out.min(), out.max()
    return (out - lo) / (hi - lo + 1e-9)


def white(h, w, rng):
    return rng.random((h, w))


def blur(a, r=1, periodic=True):
    """Desenfoque de caja (r pasadas de 3x3)."""
    out = a.astype(np.float64)
    for _ in range(r):
        if periodic:
            acc = sum(np.roll(np.roll(out, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1))
        else:
            p = np.pad(out, [(1, 1), (1, 1)] + [(0, 0)] * (out.ndim - 2), mode="edge")
            acc = sum(p[1 + dy:1 + dy + out.shape[0], 1 + dx:1 + dx + out.shape[1]]
                      for dy in (-1, 0, 1) for dx in (-1, 0, 1))
        out = acc / 9.0
    return out


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0 + 1e-9), 0, 1)
    return t * t * (3 - 2 * t)


# Lienzo ----------------------------------------------------------------------------------------------

class Canvas:
    def __init__(self, w, h, color=(0, 0, 0), alpha=1.0):
        self.w, self.h = w, h
        self.c = np.zeros((h, w, 3))
        self.c[:] = color
        self.a = np.full((h, w), float(alpha))

    # Mezclas ----------------------------------------------------------------
    def mix(self, color, k):
        """Mezcla un color (tupla o array HxWx3) con intensidad k (escalar o HxW)."""
        k = np.asarray(k, dtype=np.float64)
        if k.ndim == 2:
            k = k[..., None]
        self.c = self.c * (1 - k) + np.asarray(color, dtype=np.float64) * k

    def mul(self, f):
        f = np.asarray(f, dtype=np.float64)
        if f.ndim == 2:
            f = f[..., None]
        self.c = self.c * f

    def add(self, d):
        d = np.asarray(d, dtype=np.float64)
        if d.ndim == 2:
            d = d[..., None]
        self.c = self.c + d

    def rect(self, x0, y0, x1, y1, color, k=1.0):
        """Rectangulo inclusivo con mezcla."""
        x0, x1 = max(0, x0), min(self.w - 1, x1)
        y0, y1 = max(0, y0), min(self.h - 1, y1)
        if x1 < x0 or y1 < y0:
            return
        reg = self.c[y0:y1 + 1, x0:x1 + 1]
        self.c[y0:y1 + 1, x0:x1 + 1] = reg * (1 - k) + np.asarray(color, dtype=np.float64) * k

    def px(self, x, y, color, k=1.0, wrap=True):
        if wrap:
            x, y = x % self.w, y % self.h
        elif not (0 <= x < self.w and 0 <= y < self.h):
            return
        self.c[y, x] = self.c[y, x] * (1 - k) + np.asarray(color, dtype=np.float64) * k

    def shade_px(self, x, y, f, wrap=True):
        if wrap:
            x, y = x % self.w, y % self.h
        elif not (0 <= x < self.w and 0 <= y < self.h):
            return
        self.c[y, x] *= f

    def lum(self):
        return self.c @ np.array([0.299, 0.587, 0.114])

    # Salida -----------------------------------------------------------------
    def image(self):
        rgb = np.clip(self.c, 0, 255)
        a = np.clip(self.a * 255, 0, 255)
        return Image.fromarray(np.dstack([rgb, a]).round().astype(np.uint8), "RGBA")

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image().save(path)
        return path


# Efectos ---------------------------------------------------------------------------------------------

def jitter(cv, rng, amt=5.0, chroma=1.5):
    """Variacion por pixel (luminancia y un poco de color)."""
    n = (rng.random((cv.h, cv.w)) - 0.5) * 2 * amt
    cv.add(n)
    if chroma:
        cv.c += (rng.random((cv.h, cv.w, 3)) - 0.5) * 2 * chroma


def grime(cv, rng, amount=0.4, color=(52, 44, 34), base=2, bias=None, periodic=True):
    """Mugre a manchas: fbm umbralizado. `bias` (HxW 0-1) concentra la mugre."""
    n = fbm(cv.h, cv.w, rng, base=base, octaves=4, periodic=periodic)
    if bias is not None:
        n = n * 0.6 + bias * 0.4
    k = smoothstep(1 - amount, 1.0, n) * 0.75
    cv.mix(color, k)
    return k


def water_streaks(cv, rng, n=6, color=(96, 80, 56), strength=0.35, from_top=True, periodic=True):
    """Chorretones de humedad que bajan desde arriba (o suben de abajo), con borde oscuro."""
    for _ in range(n):
        x = rng.integers(cv.w)
        width = rng.integers(1, 4)
        length = rng.integers(cv.h // 3, cv.h)
        k0 = rng.uniform(0.4, 1.0) * strength
        drift = 0.0
        for i in range(length):
            y = i if from_top else cv.h - 1 - i
            drift += rng.uniform(-0.3, 0.3)
            fade = (1 - i / length) ** 0.8
            for dx in range(width):
                xx = int(x + drift + dx)
                cv.px(xx, y, color, k0 * fade, wrap=periodic)
            # borde de la marca, algo mas oscuro
            cv.px(int(x + drift - 1), y, color, k0 * fade * 0.35, wrap=periodic)
            cv.px(int(x + drift + width), y, color, k0 * fade * 0.35, wrap=periodic)


def cracks(cv, rng, n=2, length=(8, 20), dark=0.45, light=1.12, periodic=True, start=None, branch=0.15):
    """Grietas con paseo aleatorio; el borde inferior se aclara un poco (relieve)."""
    pts = []
    for i in range(n):
        if start is not None:
            x, y = start
        else:
            x, y = rng.uniform(0, cv.w), rng.uniform(0, cv.h)
        ang = rng.uniform(0, 2 * math.pi)
        ln = rng.integers(*length)
        stack = [(x, y, ang, ln)]
        while stack:
            x, y, ang, ln = stack.pop()
            for _ in range(int(ln)):
                ang += rng.uniform(-0.6, 0.6)
                x += math.cos(ang)
                y += math.sin(ang)
                ix, iy = int(x), int(y)
                if not periodic and not (0 <= ix < cv.w and 0 <= iy < cv.h):
                    break
                cv.shade_px(ix, iy, dark, wrap=periodic)
                cv.shade_px(ix + 1, iy + 1, light, wrap=periodic)
                pts.append((ix, iy))
                if rng.random() < branch * 0.1:
                    stack.append((x, y, ang + rng.choice([-1, 1]) * rng.uniform(0.6, 1.2), ln * 0.4))
    return pts


def scratches(cv, rng, n=5, length=(3, 9), k=0.25, color=(230, 226, 214), periodic=True, angle=None):
    for _ in range(n):
        x, y = rng.uniform(0, cv.w), rng.uniform(0, cv.h)
        a = angle if angle is not None else rng.uniform(0, math.pi)
        a += rng.uniform(-0.15, 0.15)
        for i in range(int(rng.integers(*length))):
            cv.px(int(x + math.cos(a) * i), int(y + math.sin(a) * i), color, k, wrap=periodic)


def chips(cv, rng, n, under, size=(1, 3), mask=None, periodic=True):
    """Desconchones: zonas de pintura saltada que dejan ver `under` (color o HxWx3)."""
    under = np.asarray(under, dtype=np.float64)
    for _ in range(n):
        for _ in range(20):
            x, y = int(rng.integers(cv.w)), int(rng.integers(cv.h))
            if mask is None or mask[y, x]:
                break
        r = rng.uniform(*size)
        for dy in range(-int(r) - 1, int(r) + 2):
            for dx in range(-int(r) - 1, int(r) + 2):
                d = math.hypot(dx * rng.uniform(0.8, 1.2), dy)
                if d <= r:
                    xx, yy = (x + dx) % cv.w if periodic else x + dx, (y + dy) % cv.h if periodic else y + dy
                    if not (0 <= xx < cv.w and 0 <= yy < cv.h):
                        continue
                    col = under if under.ndim == 1 else under[yy, xx]
                    cv.c[yy, xx] = col
                elif d <= r + 1:
                    xx, yy = (x + dx) % cv.w if periodic else x + dx, (y + dy) % cv.h if periodic else y + dy
                    if 0 <= xx < cv.w and 0 <= yy < cv.h:
                        cv.c[yy, xx] *= 0.82  # sombra del borde de la pintura


def rust(cv, rng, amount=0.3, base=3, bias=None, periodic=True):
    """Oxido en manchas con nucleo oscuro y halo naranja."""
    n = fbm(cv.h, cv.w, rng, base=base, octaves=4, periodic=periodic)
    if bias is not None:
        n = n * 0.55 + bias * 0.45
    halo = smoothstep(1 - amount, 1 - amount * 0.55, n)
    core = smoothstep(1 - amount * 0.55, 1 - amount * 0.15, n)
    speck = white(cv.h, cv.w, rng)
    cv.mix((128, 66, 30), halo * 0.65)
    cv.mix((78, 38, 20), core * 0.8)
    cv.mix((160, 92, 44), (core * (speck > 0.8)) * 0.6)
    return halo


BLOOD_FRESH = np.array([112, 8, 10], dtype=np.float64)
BLOOD_DARK = np.array([58, 6, 8], dtype=np.float64)
BLOOD_DRY = np.array([66, 26, 18], dtype=np.float64)


def blood_color(h, w, rng, age=0.5):
    """Campo de color de sangre: centro oscuro, bordes algo mas vivos, secado segun `age`."""
    n = fbm(h, w, rng, base=2, octaves=3, periodic=False)
    fresh = BLOOD_FRESH * (1 - age) + BLOOD_DRY * age
    col = fresh[None, None, :] * (1 - n[..., None] * 0.5) + BLOOD_DARK[None, None, :] * (n[..., None] * 0.5)
    return col


def splat_mask(h, w, rng, cx, cy, r, drops=10, streak=None, periodic=False, lumpy=0.45):
    """Mascara (0-1) de una salpicadura: mancha central irregular, gotas y rastros."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    dx, dy = xx - cx, yy - cy
    if periodic:
        dx = (dx + w / 2) % w - w / 2
        dy = (dy + h / 2) % h - h / 2
    ang = np.arctan2(dy, dx)
    dist = np.hypot(dx, dy)
    # contorno irregular: suma de armonicos aleatorios
    rad = np.full_like(ang, r)
    for k in range(2, 8):
        rad += r * lumpy / k * np.sin(k * ang + rng.uniform(0, 2 * math.pi)) * rng.uniform(0.3, 1.0)
    m = smoothstep(rad + 0.8, rad - 0.6, dist)
    # gotas alrededor
    for _ in range(drops):
        a = rng.uniform(0, 2 * math.pi)
        d = r * rng.uniform(1.1, 2.6)
        dr = rng.uniform(0.4, max(0.6, r * 0.22))
        gx, gy = cx + math.cos(a) * d, cy + math.sin(a) * d
        ddx, ddy = xx - gx, yy - gy
        if periodic:
            ddx = (ddx + w / 2) % w - w / 2
            ddy = (ddy + h / 2) % h - h / 2
        m = np.maximum(m, smoothstep(dr + 0.7, dr - 0.3, np.hypot(ddx, ddy)))
    # rastro (direccion en radianes): una cola que se estrecha
    if streak is not None:
        a, ln = streak
        for i in range(int(ln)):
            t = i / ln
            gx, gy = cx + math.cos(a) * (r * 0.6 + i), cy + math.sin(a) * (r * 0.6 + i)
            dr = max(0.35, r * 0.55 * (1 - t))
            ddx, ddy = xx - gx, yy - gy
            if periodic:
                ddx = (ddx + w / 2) % w - w / 2
                ddy = (ddy + h / 2) % h - h / 2
            m = np.maximum(m, smoothstep(dr + 0.6, dr - 0.3, np.hypot(ddx, ddy)))
    return np.clip(m, 0, 1)


def apply_blood(cv, rng, mask, age=0.5, opacity=0.92, gloss=True):
    """Pinta sangre con la mascara: borde mas oscuro (sangre que se seca antes por fuera)."""
    col = blood_color(cv.h, cv.w, rng, age)
    edge = np.clip(mask - blur(mask, 1, periodic=False) * 0.0, 0, 1)
    inner = blur(mask, 1, periodic=False)
    rim = np.clip(mask - inner, 0, 1) * 2.0 + (mask > 0.05) * (inner < 0.75) * 0.5
    col = col * (1 - np.clip(rim, 0, 1)[..., None] * 0.45)
    cv.mix(col, edge * opacity)
    if gloss:
        g = (mask > 0.9) * (white(cv.h, cv.w, rng) > 0.93) * (1 - age)
        cv.mix((168, 60, 56), g * 0.6)


def drips(cv, rng, n, x_range, y0, length=(4, 14), width=(1, 2), age=0.4, periodic=False):
    """Goterones que caen desde y0 con gota al final."""
    m = np.zeros((cv.h, cv.w))
    for _ in range(n):
        x = int(rng.integers(*x_range))
        ln = int(rng.integers(*length))
        wd = int(rng.integers(width[0], width[1] + 1))
        for i in range(ln):
            for dx in range(wd):
                yy, xx = y0 + i, x + dx
                if periodic:
                    yy, xx = yy % cv.h, xx % cv.w
                if 0 <= yy < cv.h and 0 <= xx < cv.w:
                    m[yy, xx] = 1
        yy = y0 + ln
        if 0 <= yy < cv.h and 0 <= x < cv.w:
            m[yy, x] = 1
    apply_blood(cv, rng, m, age=age, gloss=False)
    return m


def ao_edges(h, w, width=2, strength=0.25, sides="nsew"):
    """Factor multiplicativo que oscurece los bordes (oclusion ambiental falsa)."""
    f = np.ones((h, w))
    for i in range(width):
        k = 1 - strength * (1 - i / width)
        if "n" in sides:
            f[i, :] = np.minimum(f[i, :], k)
        if "s" in sides:
            f[h - 1 - i, :] = np.minimum(f[h - 1 - i, :], k)
        if "w" in sides:
            f[:, i] = np.minimum(f[:, i], k)
        if "e" in sides:
            f[:, w - 1 - i] = np.minimum(f[:, w - 1 - i], k)
    return f


def bevel(cv, x0, y0, x1, y1, light=1.18, dark=0.72):
    """Biselado de un rectangulo: arriba/izquierda mas claro, abajo/derecha mas oscuro."""
    cv.c[y0, x0:x1 + 1] *= light
    cv.c[y0:y1 + 1, x0] *= (light + 1) / 2 + 0.02
    cv.c[y1, x0:x1 + 1] *= dark
    cv.c[y0:y1 + 1, x1] *= (dark + 1) / 2 - 0.02


def desaturate(cv, k=0.3, tint=None):
    l = cv.lum()[..., None]
    cv.c = cv.c * (1 - k) + l * k
    if tint is not None:
        cv.c = cv.c * np.asarray(tint)


def posterize(cv, levels=24):
    step = 255 / levels
    cv.c = np.round(cv.c / step) * step


def stencil_text(cv, x, y, text, color, k=0.85, rng=None, font=None, scale=1):
    """Texto de 3x5 pixeles (plantilla) con algo de desgaste."""
    font = font or FONT3x5
    cx = x
    for ch in text.upper():
        g = font.get(ch, font[" "])
        for gy, row in enumerate(g):
            for gx, bit in enumerate(row):
                if bit == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            if rng is None or rng.random() > 0.12:
                                cv.px(cx + gx * scale + sx, y + gy * scale + sy, color, k, wrap=False)
        cx += (len(g[0]) + 1) * scale
    return cx


FONT3x5 = {
    " ": ["...", "...", "...", "...", "..."],
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    "C": [".##", "#..", "#..", "#..", ".##"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "E": ["###", "#..", "##.", "#..", "###"], "F": ["###", "#..", "##.", "#..", "#.."],
    "G": [".##", "#..", "#.#", "#.#", ".##"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "J": ["..#", "..#", "..#", "#.#", ".#."],
    "K": ["#.#", "#.#", "##.", "#.#", "#.#"], "L": ["#..", "#..", "#..", "#..", "###"],
    "M": ["#.#", "###", "###", "#.#", "#.#"], "N": ["##.", "#.#", "#.#", "#.#", "#.#"],
    "O": [".#.", "#.#", "#.#", "#.#", ".#."], "P": ["##.", "#.#", "##.", "#..", "#.."],
    "Q": [".#.", "#.#", "#.#", "##.", ".##"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "U": ["#.#", "#.#", "#.#", "#.#", "###"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "W": ["#.#", "#.#", "###", "###", "#.#"], "X": ["#.#", "#.#", ".#.", "#.#", "#.#"],
    "Y": ["#.#", "#.#", ".#.", ".#.", ".#."], "Z": ["###", "..#", ".#.", "#..", "###"],
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["##.", "..#", ".#.", "#..", "###"], "3": ["##.", "..#", ".#.", "..#", "##."],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "##.", "..#", "##."],
    "6": [".##", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "##."],
    "-": ["...", "...", "###", "...", "..."], "!": [".#.", ".#.", ".#.", "...", ".#."],
    "?": ["##.", "..#", ".#.", "...", ".#."], ".": ["...", "...", "...", "...", ".#."],
    "Í": [".#.", "###", ".#.", ".#.", "###"], "É": [".#.", "###", "##.", "#..", "###"],
    "Ó": [".#.", "###", "#.#", "#.#", ".#."], "Ñ": ["###", "...", "##.", "#.#", "#.#"],
    "/": ["..#", "..#", ".#.", "#..", "#.."], ":": ["...", ".#.", "...", ".#.", "..."],
}


def sheet(images, cols, scale=4, pad=4, bg=(20, 20, 22), labels=None):
    """Hoja de vista previa: cada imagen ampliada y en mosaico 2x2 si es de bloque."""
    from PIL import ImageDraw
    cells = []
    for im in images:
        cells.append(im.resize((im.width * scale, im.height * scale), Image.NEAREST))
    cw = max(c.width for c in cells)
    ch = max(c.height for c in cells) + (10 if labels else 0)
    rows = (len(cells) + cols - 1) // cols
    out = Image.new("RGBA", (cols * (cw + pad) + pad, rows * (ch + pad) + pad), (*bg, 255))
    d = ImageDraw.Draw(out)
    for i, c in enumerate(cells):
        x = pad + (i % cols) * (cw + pad)
        y = pad + (i // cols) * (ch + pad)
        out.alpha_composite(c, (x, y))
        if labels:
            d.text((x, y + c.height + 1), labels[i], fill=(180, 180, 180, 255))
    return out
