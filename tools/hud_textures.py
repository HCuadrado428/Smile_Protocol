#!/usr/bin/env python3
"""
Sprites de la HUD de terror (textures/gui/sprites/hud): vineta sucia, venas de sangre para la
vida baja, grano de pelicula, manchas de fondo a brochazos, corazon anatomico, barras de sangre
y de luz, huecos de inventario, cinta adhesiva para las teclas y papel rasgado para el lobby.

    python3 tools/hud_textures.py [preview.png]
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from texlib import Canvas, fbm, white, blur, smoothstep, splat_mask, apply_blood  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/textures/gui/sprites/hud"


def R(seed):
    return np.random.default_rng(seed)


def save(cv, name, nine=None):
    p = ROOT / f"{name}.png"
    cv.save(p)
    meta = p.with_suffix(".png.mcmeta")
    if nine:
        w, h, b = nine
        meta.write_text(json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": b}}},
                                   indent=2) + "\n")
    elif meta.exists():
        meta.unlink()
    return cv


def ragged_rect(w, h, r, edge=4.0, rough=0.8, base=6):
    """Mascara de un rectangulo con bordes de brochazo."""
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    d = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
    n = fbm(h, w, r, base=base, octaves=4, periodic=False)
    return smoothstep(0, edge, d - (n - 0.5) * edge * 2 * rough)


# Pantalla completa -------------------------------------------------------------------------------------

def vignette():
    r = R(1)
    S = 256
    cv = Canvas(S, S, (0, 0, 0), 0)
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    d = np.hypot((xx - S / 2) / (S / 2), (yy - S / 2) / (S / 2))
    n = fbm(S, S, r, base=5, octaves=5, periodic=False)
    a = smoothstep(0.45, 1.25, d + (n - 0.5) * 0.35)
    cv.a = np.clip(a ** 1.3, 0, 1)
    # algo de mancha en las esquinas (lente sucia)
    cv.c[:] = 0
    return save(cv, "vignette")


def blood_vignette():
    """Venas y sangre que entran desde los bordes; se tine y late con la vida baja."""
    r = R(2)
    S = 256
    cv = Canvas(S, S, (0, 0, 0), 0)
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    d = np.hypot((xx - S / 2) / (S / 2), (yy - S / 2) / (S / 2))
    m = smoothstep(0.75, 1.3, d + (fbm(S, S, r, base=4, octaves=5, periodic=False) - 0.5) * 0.5)
    # venas: paseos aleatorios desde el borde hacia dentro, ramificandose
    veins = Image.new("L", (S, S), 0)
    dr = ImageDraw.Draw(veins)
    for i in range(34):
        side = i % 4
        t = r.uniform(0, S)
        x, y = [(t, 0), (S - 1, t), (t, S - 1), (0, t)][side]
        ang = math.atan2(S / 2 - y, S / 2 - x) + r.uniform(-0.5, 0.5)
        stack = [(x, y, ang, r.uniform(40, 90), 3.0)]
        while stack:
            x, y, ang, ln, wd = stack.pop()
            for _ in range(int(ln)):
                ang += r.uniform(-0.35, 0.35)
                nx, ny = x + math.cos(ang) * 2, y + math.sin(ang) * 2
                dr.line([(x, y), (nx, ny)], fill=255, width=max(1, int(wd)))
                x, y = nx, ny
                wd *= 0.985
                if r.random() < 0.04 and wd > 1.2:
                    stack.append((x, y, ang + r.choice([-1, 1]) * r.uniform(0.5, 1.1), ln * 0.5, wd * 0.7))
    v = np.asarray(veins, dtype=float) / 255
    v *= smoothstep(0.35, 0.95, d)
    a = np.maximum(m * 0.5, v * 0.85)
    cv.a = np.clip(a, 0, 1)
    col = np.zeros((S, S, 3)) + (120, 6, 8)
    col = col * (0.6 + 0.6 * fbm(S, S, r, base=6, octaves=3, periodic=False))[..., None]
    col[v > 0.5] *= 0.7
    cv.c = col
    return save(cv, "blood_vignette")


def grain():
    r = R(3)
    S = 128
    cv = Canvas(S, S, (0, 0, 0), 0)
    w = white(S, S, r)
    cv.c[:] = (w[..., None] > 0.5) * 255
    cv.a = np.abs(w - 0.5) * 2
    # rayas verticales de pelicula vieja
    for _ in range(3):
        x = r.integers(S)
        cv.a[:, x] = np.maximum(cv.a[:, x], 0.6 * (white(S, 1, r)[:, 0] > 0.3))
        cv.c[:, x] = 220
    return save(cv, "grain")


# Fondos y barras -----------------------------------------------------------------------------------

def smear(name, seed, w=128, h=40, color=(8, 6, 6), alpha=0.82, border=12):
    """Fondo de brochazo oscuro, con borde irregular y estrias del pincel."""
    r = R(seed)
    cv = Canvas(w, h, color, 0)
    m = ragged_rect(w, h, r, edge=7, rough=1.0, base=6)
    yy = np.arange(h)[:, None]
    stri = 0.88 + 0.12 * np.sin(yy * 1.7 + fbm(h, w, r, base=3, octaves=2, periodic=False) * 6)
    cv.a = np.clip(m * stri * alpha, 0, 1)
    cv.c = cv.c + (fbm(h, w, r, base=4, octaves=3, periodic=False)[..., None] * 18)
    return save(cv, name, nine=(w, h, border))


def bar(name, seed, w, h, color, dry=False, empty=False):
    """Trazo de pincel para las barras: sangre (vida), luz (bateria) o su hueco vacio."""
    r = R(seed)
    cv = Canvas(w, h, color, 0)
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    n = fbm(h, w, r, base=8, octaves=3, periodic=False)
    top = 1 + n * 1.3
    bot = h - 1.5 - (1 - n) * 1.3
    m = smoothstep(top - 0.6, top + 0.6, yy) * smoothstep(bot + 0.6, bot - 0.6, yy)
    m *= smoothstep(-1, 2, xx) * smoothstep(w + 1, w - 3, xx)
    stri = np.sin(yy * 3.1 + n * 4) > -0.7
    cv.a = np.clip(m * (0.75 + 0.25 * stri), 0, 1)
    if empty:
        cv.a *= 0.55
        cv.c = cv.c * (0.8 + 0.4 * fbm(h, w, r, base=6, octaves=2, periodic=False))[..., None]
        return save(cv, name)
    if color[0] > color[1] * 2:  # sangre
        tmp = Canvas(w, h)
        apply_blood(tmp, r, (m > 0.2).astype(float), age=0.25 if not dry else 0.8)
        cv.c = tmp.c * 1.25
        cv.c[(yy < top + 1.5) & (m > 0.4)] *= 1.35                   # brillo humedo arriba
    else:
        cv.c = np.zeros((h, w, 3)) + color
        cv.c *= (0.85 + 0.3 * fbm(h, w, r, base=6, octaves=2, periodic=False))[..., None]
        cv.c[(yy < top + 1.2) & (m > 0.4)] *= 1.25
    return save(cv, name)


def _hires(col, alpha, S):
    from PIL import Image as _I
    img = np.dstack([np.clip(col, 0, 255), np.clip(alpha, 0, 1) * 255]).astype(np.uint8)
    im = _I.fromarray(img, "RGBA").resize((S, S), _I.LANCZOS)
    arr = np.asarray(im, dtype=float)
    cv = Canvas(S, S, (0, 0, 0), 0)
    cv.c, cv.a = arr[..., :3], arr[..., 3] / 255
    return cv


def _outline(alpha, px):
    from PIL import Image as _I, ImageFilter
    m = _I.fromarray((np.clip(alpha, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(px * 2 + 1))
    return np.asarray(m, dtype=float) / 255


def heart():
    """Corazon anatomico (64 px, se dibuja a 16): ventriculos carnosos con relieve, auricula, aorta
    y pulmonar cortadas, grasa amarillenta en el surco, coronarias y brillo humedo."""
    r = R(5)
    S, W = 64, 256
    yy, xx = np.mgrid[0:W, 0:W].astype(float) / W
    n = fbm(W, W, r, base=8, octaves=4, periodic=False)

    def blob(cx, cy, rx, ry, rot=0.0):
        c, s_ = math.cos(rot), math.sin(rot)
        dx, dy = xx - cx, yy - cy
        u, v = dx * c + dy * s_, -dx * s_ + dy * c
        return np.hypot(u / rx, v / ry)

    vent = np.minimum(blob(0.52, 0.6, 0.3, 0.3, 0.5), blob(0.5, 0.78, 0.14, 0.17, 0.3) * 1.05)
    atr = blob(0.28, 0.36, 0.14, 0.12, -0.3)
    aorta = (np.abs(xx - 0.5 - 0.06 * np.sin((yy - 0.05) * 6)) < 0.075) & (yy > 0.04) & (yy < 0.42)
    arch = (blob(0.6, 0.12, 0.14, 0.09) < 1) & (blob(0.6, 0.12, 0.07, 0.035) > 1) & (yy < 0.16)
    pulm = (np.abs(xx - 0.72) < 0.06) & (yy > 0.12) & (yy < 0.42)
    vena = (np.abs(xx - 0.3) < 0.045) & (yy > 0.08) & (yy < 0.3)
    body = (vent < 1) | (atr < 1)
    vessels = aorta | arch | pulm | vena
    alpha = (body | vessels).astype(float)
    # color de musculo con relieve (luz arriba a la izquierda)
    shade = np.clip(1.25 - 1.1 * np.maximum(vent - 0.2, 0) - 0.5 * (xx + yy - 0.8), 0.35, 1.4)
    col = np.zeros((W, W, 3)) + (128, 18, 24)
    col *= (shade * (0.8 + 0.4 * n))[..., None]
    col = np.where((atr < 1)[..., None] & ~(vent < 1)[..., None], col * 0.8 + np.array([20, 0, 10]), col)
    # vasos: palidos y rosados, con el corte abierto arriba
    vcol = np.array([170, 70, 74]) * (0.8 + 0.4 * n[..., None])
    col = np.where(vessels[..., None], vcol, col)
    pulm_c = np.array([84, 40, 70])
    col = np.where((pulm | vena)[..., None], pulm_c * (0.8 + 0.4 * n[..., None]), col)
    for (cx, cy, rx) in ((0.5, 0.05, 0.075), (0.72, 0.12, 0.06), (0.3, 0.08, 0.045)):
        hole = blob(cx, cy, rx * 0.8, 0.025) < 1
        col = np.where(hole[..., None], np.array([30, 0, 4]), col)
    # grasa en el surco y coronarias
    groove = np.abs(blob(0.3, 0.3, 0.35, 0.3, -0.2) - 1) < 0.07
    fat = groove & body & (fbm(W, W, r, base=16, octaves=2, periodic=False) > 0.45)
    col = np.where(fat[..., None], np.array([206, 176, 96]) * (0.8 + 0.3 * n[..., None]), col)
    for (x0, y0, x1, y1, wv) in ((0.46, 0.38, 0.62, 0.92, 0.012), (0.5, 0.4, 0.3, 0.75, 0.01), (0.55, 0.45, 0.78, 0.7, 0.009)):
        t = np.clip(((xx - x0) * (x1 - x0) + (yy - y0) * (y1 - y0)) / ((x1 - x0) ** 2 + (y1 - y0) ** 2), 0, 1)
        px_, py_ = x0 + (x1 - x0) * t + 0.02 * np.sin(t * 9), y0 + (y1 - y0) * t
        line = (np.hypot(xx - px_, yy - py_) < wv * (1.2 - t * 0.6)) & body
        col = np.where(line[..., None], np.array([54, 8, 40]), col)
    # brillo humedo
    gl = np.exp(-(((xx - 0.4) / 0.045) ** 2 + ((yy - 0.47) / 0.025) ** 2)) * body
    col += gl[..., None] * np.array([120, 90, 90])
    col += (smoothstep(0.7, 0.85, n) * body * 30)[..., None]
    # contorno oscuro para que se lea sobre cualquier fondo
    ring = _outline(alpha, 6) * (1 - alpha)
    col = col * alpha[..., None] + np.array([12, 4, 4]) * ring[..., None]
    return save(_hires(col, np.maximum(alpha, ring * 0.9), S), "heart")


def bulb():
    """Icono de bombilla (bateria de la linterna), 48 px (se dibuja a 12): cristal sucio con reflejo,
    filamento encendido, casquillo de rosca."""
    S, W = 48, 192
    yy, xx = np.mgrid[0:W, 0:W].astype(float) / W
    r = R(9)
    n = fbm(W, W, r, base=6, octaves=3, periodic=False)
    glass_d = np.hypot((xx - 0.5) / 0.36, (yy - 0.38) / 0.34)
    neck = (np.abs(xx - 0.5) < 0.2 - 0.12 * np.clip((yy - 0.55) / 0.15, 0, 1)) & (yy > 0.5) & (yy < 0.7)
    glass = (glass_d < 1) | neck
    base = (np.abs(xx - 0.5) < 0.17) & (yy >= 0.68) & (yy < 0.92)
    tip = (np.hypot((xx - 0.5) / 0.08, (yy - 0.93) / 0.04) < 1)
    alpha = (glass | base | tip).astype(float)
    col = np.zeros((W, W, 3)) + (196, 176, 108)
    col *= (1.15 - 0.45 * np.clip(glass_d, 0, 1) ** 2)[..., None]
    col *= (0.85 + 0.25 * n)[..., None]
    # suciedad en el cristal
    col = np.where((glass & (n > 0.62))[..., None], col * 0.75 + np.array([50, 40, 20]) * 0.25, col)
    # filamento en zigzag que brilla
    fy = 0.38 + 0.04 * np.sign(np.sin((xx - 0.36) * 2 * math.pi / 0.07))
    fil = (np.abs(yy - fy) < 0.03) & (np.abs(xx - 0.5) < 0.14)
    posts = (np.abs(np.abs(xx - 0.5) - 0.14) < 0.02) & (yy > 0.38) & (yy < 0.62)
    halo = np.exp(-(((xx - 0.5) / 0.2) ** 2 + ((yy - 0.38) / 0.12) ** 2))
    col += (halo * glass)[..., None] * np.array([90, 70, 20])
    col = np.where(fil[..., None], np.array([255, 236, 170]), col)
    col = np.where(posts[..., None], np.array([90, 80, 60]), col)
    # rosca del casquillo
    thread = 0.5 + 0.5 * np.sin((yy - 0.68) * 2 * math.pi / 0.055 + (xx - 0.5) * 3)
    bcol = np.array([150, 144, 132]) * (0.55 + 0.5 * thread)[..., None] * (1.2 - 1.5 * np.abs(xx - 0.45))[..., None]
    col = np.where(base[..., None], bcol, col)
    col = np.where(tip[..., None], np.array([60, 56, 50]), col)
    # reflejo
    refl = np.exp(-(((xx - 0.36) / 0.05) ** 2 + ((yy - 0.24) / 0.08) ** 2)) * glass
    col += refl[..., None] * 140
    ring = _outline(alpha, 5) * (1 - alpha)
    col = col * alpha[..., None] + np.array([10, 8, 6]) * ring[..., None]
    return save(_hires(col, np.maximum(alpha, ring * 0.9), S), "bulb")


def slot(selected):
    """Hueco de inventario: chapa sucia y aranada; el elegido, con marco de sangre."""
    r = R(6 + selected)
    S = 24
    cv = Canvas(S, S, (16, 14, 13), 0.78)
    cv.c = cv.c * (0.8 + 0.5 * fbm(S, S, r, base=3, octaves=3, periodic=False))[..., None]
    m = ragged_rect(S, S, r, edge=1.6, rough=0.7, base=5)
    cv.a = np.clip(m, 0, 1) * 0.8
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.minimum(np.minimum(xx, S - 1 - xx), np.minimum(yy, S - 1 - yy))
    rim = (d >= 1) & (d <= 1)
    if selected:
        fr = Canvas(S, S)
        mm = ((d >= 1) & (d <= 2)).astype(float) * (white(S, S, r) > 0.12)
        apply_blood(fr, r, mm, age=0.2)
        cv.c = np.where(mm[..., None] > 0, fr.c * 1.4, cv.c)
        cv.a = np.where(mm > 0, 1.0, cv.a)
        # goterones del marco
        for x in (5, 13, 19):
            for y in range(S - 3, S):
                cv.c[y, x] = (110, 10, 12)
                cv.a[y, x] = 1
    else:
        cv.c[rim] = (70, 66, 60)
        cv.a[rim & (white(S, S, r) > 0.25)] = 0.9
    # aranazos
    for _ in range(4):
        x, y = r.integers(3, S - 3), r.integers(3, S - 3)
        a = r.uniform(0, math.pi)
        for i in range(int(r.integers(2, 6))):
            xx_, yy_ = int(x + math.cos(a) * i), int(y + math.sin(a) * i)
            if 2 <= xx_ < S - 2 and 2 <= yy_ < S - 2:
                cv.c[yy_, xx_] = cv.c[yy_, xx_] * 0.5 + 40
    return save(cv, "slot_selected" if selected else "slot")


def tape():
    """Trozo de cinta de carrocero para las teclas (nine-slice)."""
    r = R(8)
    w, h = 32, 14
    cv = Canvas(w, h, (178, 164, 124), 0.92)
    cv.c = cv.c * (0.85 + 0.25 * fbm(h, w, r, base=4, octaves=3, periodic=False))[..., None]
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    # bordes cortados en dientes de sierra a izquierda y derecha
    teeth = (np.abs((yy % 3) - 1)) * 0.9
    m = (xx >= teeth) & (xx <= w - 1 - teeth)
    cv.a = np.where(m, 0.94, 0)
    cv.c[0, :] *= 0.85
    cv.c[h - 1, :] *= 0.75
    cv.mix((90, 70, 40), smoothstep(0.6, 1, fbm(h, w, r, base=3, octaves=2, periodic=False)) * 0.5)
    return save(cv, "tape", nine=(w, h, 4))


def note():
    """Papel rasgado y manchado (panel del ascensor del lobby), nine-slice."""
    r = R(9)
    w, h = 96, 64
    cv = Canvas(w, h, (172, 162, 132), 0)
    cv.c = cv.c * (0.82 + 0.25 * fbm(h, w, r, base=5, octaves=4, periodic=False))[..., None]
    m = ragged_rect(w, h, r, edge=3, rough=1.2, base=10)
    cv.a = (m > 0.5) * 0.96
    yy, xx = np.mgrid[0:h, 0:w]
    # lineas de libreta y cerco de cafe/sangre
    cv.c[(yy % 8 == 7) & (xx > 10)] *= 0.86
    cv.mix((110, 80, 40), smoothstep(0.6, 1, fbm(h, w, r, base=3, octaves=3, periodic=False)) * 0.45)
    s = splat_mask(h, w, r, w - 14, 12, 4, drops=8)
    tmp = Canvas(w, h)
    tmp.c = cv.c.copy()
    apply_blood(tmp, r, s, age=0.7)
    cv.c = tmp.c
    # se oscurece hacia el borde
    d = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
    cv.c *= (0.7 + 0.3 * smoothstep(0, 8, d))[..., None]
    return save(cv, "note", nine=(w, h, 14))


def panel(name, alert):
    """Panel oscuro (depuracion): ahora sucio, sin el aspecto de monitor."""
    r = R(10 + alert)
    S = 32
    cv = Canvas(S, S, (14, 10, 10) if not alert else (36, 6, 8), 0.85)
    m = ragged_rect(S, S, r, edge=3, rough=0.8, base=4)
    cv.a = m * 0.86
    cv.c = cv.c * (0.8 + 0.5 * fbm(S, S, r, base=3, octaves=3, periodic=False))[..., None]
    return save(cv, name, nine=(S, S, 8))


def pips():
    r = R(12)
    S = 7
    on = Canvas(S, S, (0, 0, 0), 0)
    m = splat_mask(S, S, r, 3, 3.4, 2.4, drops=0, lumpy=0.2)
    apply_blood(on, r, (m > 0.4).astype(float), age=0.2)
    on.a = (m > 0.4).astype(float)
    on.c[1, 2] = (200, 90, 90)
    save(on, "pip_on")
    off = Canvas(S, S, (150, 140, 120), 0)
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.hypot(xx - 3, yy - 3)
    off.a = ((d > 2) & (d < 3.2) & (white(S, S, r) > 0.15)).astype(float) * 0.85
    save(off, "pip_off")


def splatter(i):
    """Salpicadura de sangre en la pantalla (remates): mancha con gotas y regueros que caen."""
    r = R(60 + i)
    S = 128
    cv = Canvas(S, S, (0, 0, 0), 0)
    m = splat_mask(S, S, r, S / 2 + r.uniform(-8, 8), S * 0.42, r.uniform(16, 24), drops=26,
                   streak=(math.pi / 2 + r.uniform(-0.3, 0.3), r.uniform(30, 55)), lumpy=0.6)
    # regueros que bajan desde la mancha
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    for _ in range(5):
        x0 = S / 2 + r.uniform(-20, 20)
        y0 = S * 0.42 + r.uniform(0, 10)
        ln = r.uniform(20, 60)
        wd = r.uniform(1.2, 2.6)
        band = smoothstep(wd + 0.6, wd - 0.4, np.abs(xx - x0 - np.sin((yy - y0) * 0.08) * 1.5))
        band *= (yy > y0) * (yy < y0 + ln)
        tip = smoothstep(wd + 1.6, wd + 0.2, np.hypot(xx - x0, yy - y0 - ln))
        m = np.maximum(m, np.maximum(band, tip))
    m = np.clip(m, 0, 1)
    apply_blood(cv, r, m, age=0.15, opacity=1.0, gloss=True)
    cv.c = cv.c * 0.85
    cv.a = m * 0.93
    return save(cv, f"splatter{i}")


def scare_face():
    """Susto: la cara de Smiley pegada a la pantalla (512 px). Piel negra con brillo aceitoso y venas,
    pelo lacio enmarcando la cara, el ojo derecho enorme, inyectado en sangre y brillando, la cuenca
    del otro vacia, y la sonrisa de oreja a oreja con dientes irregulares, encias en carne viva, babas y
    sangre. Todo lo rojo lleva un halo, como si quemara la retina."""
    from PIL import ImageFilter
    r = R(77)
    S, K = 512, 2                       # se pinta al doble y se reduce (bordes suaves)
    W = S * K
    yy, xx = np.mgrid[0:W, 0:W].astype(float)
    cx = W / 2
    n1 = fbm(W, W, r, base=4, octaves=5, periodic=False)
    n2 = fbm(W, W, r, base=14, octaves=4, periodic=False)
    # cabeza alargada, mas ancha en los pomulos, que se sale por abajo
    d = np.hypot((xx - cx) / (W * 0.43), (yy - W * 0.56) / (W * 0.66))
    head = smoothstep(1.02, 0.94, d + (n1 - 0.5) * 0.12)
    col = np.zeros((W, W, 3)) + (10, 8, 10)
    col *= (0.6 + 0.8 * n2)[..., None]
    # relieve: pomulos, frente y menton cogen algo de luz fria
    for (px, py, rx, ry, g) in ((0.3, 0.5, 0.12, 0.08, 16), (0.7, 0.5, 0.12, 0.08, 16), (0.5, 0.2, 0.25, 0.1, 10),
                                (0.5, 0.9, 0.15, 0.06, 8)):
        b = np.exp(-(((xx - W * px) / (W * rx)) ** 2 + ((yy - W * py) / (W * ry)) ** 2))
        col += b[..., None] * np.array([g, g, g * 1.3])
    sheen = smoothstep(0.62, 0.8, fbm(W, W, r, base=6, octaves=3, periodic=False)) * 22
    col += sheen[..., None] * np.array([0.8, 0.9, 1.2])
    # venas algo mas claras
    v = np.abs(fbm(W, W, r, base=9, octaves=3, periodic=False) - 0.5)
    col += (smoothstep(0.02, 0.0, v) * 14)[..., None]
    glow = np.zeros((W, W))                    # cuanto brilla cada punto (para el halo)
    glowcol = np.zeros((W, W, 3))

    def put(mask, c, g=0.0):
        nonlocal col
        m = mask[..., None]
        col = col * (1 - m) + np.array(c) * m
        if g:
            glow[:] = np.maximum(glow, mask * g)
            glowcol[:] = glowcol * (1 - m) + np.array(c) * m

    # cuenca vacia (lado izquierdo de la imagen es el ojo derecho de Smiley... el ojo va a la izquierda)
    ex, ey = W * 0.33, W * 0.36
    hx, hy = W * 0.69, W * 0.37
    hollow = smoothstep(1.0, 0.6, np.hypot((xx - hx) / (W * 0.1), (yy - hy) / (W * 0.065)))
    col *= (1 - 0.8 * hollow)[..., None]
    socket = smoothstep(1.0, 0.7, np.hypot((xx - ex) / (W * 0.13), (yy - ey) / (W * 0.095)))
    col *= (1 - 0.6 * socket)[..., None]
    # el ojo: esclerotica roja con venas, iris encendido, pupila diminuta
    eyed = np.hypot((xx - ex) / (W * 0.105), (yy - ey) / (W * 0.075))
    eye = smoothstep(1.0, 0.94, eyed)
    ang = np.arctan2(yy - ey, xx - ex)
    rays = 0.5 + 0.5 * np.sin(ang * 23 + fbm(W, W, r, base=20, octaves=2, periodic=False) * 9)
    sclera = np.array([150, 14, 18]) * (0.7 + 0.5 * rays[..., None] * smoothstep(0.3, 1, eyed)[..., None])
    put(eye, (0, 0, 0))
    col += eye[..., None] * sclera
    glow[:] = np.maximum(glow, eye * 0.7)
    glowcol[:] = glowcol * (1 - eye[..., None]) + eye[..., None] * np.array([255, 40, 30])
    iris = smoothstep(0.5, 0.44, eyed)
    put(iris, (255, 70, 40), 1.0)
    put(smoothstep(0.32, 0.26, eyed), (255, 214, 170), 1.0)
    put(smoothstep(0.1, 0.07, np.hypot((xx - ex) / (W * 0.012), (yy - ey) / (W * 0.03))), (10, 0, 0))
    # regueros bajo el ojo
    for k in range(4):
        x0 = ex + (k - 1.5) * W * 0.03 + r.uniform(-8, 8)
        ln = r.uniform(0.08, 0.2) * W
        wav = x0 + 6 * np.sin(yy / 23 + k)
        band = (np.abs(xx - wav) < (4 + 3 * (1 - (yy - ey) / ln))) & (yy > ey + W * 0.06) & (yy < ey + W * 0.06 + ln)
        put(band.astype(float) * 0.9, (70, 4, 8))
    # sonrisa: media luna de oreja a oreja que sube hasta los ojos
    my = W * 0.68
    up = lambda x: my - ((x - cx) / (W * 0.4)) ** 2 * W * 0.2
    top = up(xx)
    low = my + W * 0.09 * (1 - ((xx - cx) / (W * 0.4)) ** 2) - ((xx - cx) / (W * 0.4)) ** 2 * W * 0.13
    inx = np.abs(xx - cx) < W * 0.41
    mouth = (yy > top) & (yy < low) & inx
    lips = ((yy > top - 9) & (yy < low + 9) & inx) & ~mouth
    put(lips.astype(float), (190, 14, 20), 0.9)
    put(mouth.astype(float), (6, 0, 1))
    # encias
    gum_t = mouth & (yy < top + W * 0.025)
    gum_b = mouth & (yy > low - W * 0.022)
    put((gum_t | gum_b).astype(float), (176, 18, 26), 0.8)
    # dientes irregulares: arriba cuelgan, abajo suben, todos afilados y de distinto tamano
    nt = 17
    for row in (0, 1):
        for k in range(nt + row):
            t = (k + 0.5 + 0.5 * row) / (nt + row)
            x = cx + (t - 0.5) * 2 * W * 0.39 + r.uniform(-5, 5)
            wd = W * (0.017 + 0.008 * r.random()) * (1 - 0.4 * abs(t - 0.5) * 2)
            if row == 0:
                y0 = up(x) + W * 0.02
                ln = W * (0.05 + 0.035 * r.random()) * (1 - 0.5 * abs(t - 0.5) * 2)
                tooth = (yy > y0) & (yy < y0 + ln) & (np.abs(xx - x) < wd * (1 - (yy - y0) / ln))
            else:
                yb = my + W * 0.09 * (1 - ((x - cx) / (W * 0.4)) ** 2) - ((x - cx) / (W * 0.4)) ** 2 * W * 0.13 - W * 0.018
                ln = W * (0.04 + 0.03 * r.random()) * (1 - 0.5 * abs(t - 0.5) * 2)
                tooth = (yy < yb) & (yy > yb - ln) & (np.abs(xx - x) < wd * (1 - (yb - yy) / ln))
            shade = 0.75 + 0.25 * r.random()
            yel = r.random() * 0.3
            put(tooth.astype(float), (232 * shade, (214 - 50 * yel) * shade, (196 - 90 * yel) * shade), 0.35)
    # babas y sangre entre los dientes
    for k in range(9):
        x0 = cx + r.uniform(-0.3, 0.3) * W
        y0 = up(x0) + W * 0.06
        y1 = y0 + r.uniform(0.03, 0.09) * W
        sag = (yy - y0) / max(1, y1 - y0)
        strand = (np.abs(xx - x0 - 4 * np.sin(sag * 3)) < 2.2) & (yy > y0) & (yy < y1)
        put(strand.astype(float) * 0.8, (150, 20, 28) if k % 2 else (90, 80, 84), 0.2 if k % 2 else 0)
    for k in range(10):
        x0 = cx + r.uniform(-0.36, 0.36) * W
        y0 = my + W * 0.09 * (1 - ((x0 - cx) / (W * 0.4)) ** 2) - ((x0 - cx) / (W * 0.4)) ** 2 * W * 0.13 + 6
        ln = r.uniform(0.05, 0.25) * W
        wd = r.uniform(3, 7)
        drip = (np.abs(xx - x0) < wd * (1 - 0.4 * (yy - y0) / ln)) & (yy > y0) & (yy < y0 + ln)
        tipd = np.hypot(xx - x0, yy - y0 - ln) < wd * 1.2
        put((drip | tipd).astype(float) * head, (120, 6, 10))
    # pelo: mechones negros que caen por los lados y por la frente, sobre la cuenca vacia
    hair = np.zeros((W, W))
    for k in range(40):
        side = -1 if k % 2 else 1
        x0 = cx + side * W * r.uniform(0.25, 0.47)
        if k % 5 == 0:
            x0 = cx + W * r.uniform(0.1, 0.34)                          # flequillo sobre la cuenca
        y1 = r.uniform(0.3, 0.75) * W if k % 5 else r.uniform(0.32, 0.46) * W
        wd = r.uniform(6, 16)
        curve = x0 + side * 0.00005 * (yy - 0) ** 2 * (0.5 + r.random())
        taper = np.clip(1 - yy / y1, 0, 1) ** 0.4
        hair = np.maximum(hair, ((np.abs(xx - curve) < wd * taper) & (yy < y1)).astype(float))
    cap = smoothstep(0.75, 0.72, np.hypot((xx - cx) / (W * 0.47), (yy - W * 0.3) / (W * 0.36))) * (yy < W * 0.2)
    hair = np.maximum(hair, cap)
    strands = 0.5 + 0.5 * np.sin(xx * 0.35 + fbm(W, W, r, base=12, octaves=2, periodic=False) * 10)
    hc = np.zeros((W, W, 3)) + 6
    hc += (strands * 14)[..., None] * np.array([0.8, 0.9, 1.2])
    col = col * (1 - hair[..., None]) + hc * hair[..., None]
    glow *= (1 - hair)
    alpha = np.maximum(head, hair * smoothstep(0.0, 0.05, 1 - yy / W))
    # halo rojo de lo que brilla
    gl = Image.fromarray(np.clip(glowcol * glow[..., None], 0, 255).astype(np.uint8))
    halo = np.asarray(gl.filter(ImageFilter.GaussianBlur(W * 0.025)), dtype=float)
    halo2 = np.asarray(gl.filter(ImageFilter.GaussianBlur(W * 0.07)), dtype=float)
    col = col + halo * 0.9 + halo2 * 0.6
    alpha = np.clip(np.maximum(alpha, (halo2.max(axis=2) / 255) * 1.4), 0, 1)
    # grano
    col += (r.random((W, W)) - 0.5)[..., None] * 10
    img = np.dstack([np.clip(col, 0, 255), np.clip(alpha, 0, 1) * 255]).astype(np.uint8)
    im = Image.fromarray(img, "RGBA").resize((S, S), Image.LANCZOS)
    cv = Canvas(S, S, (0, 0, 0), 0)
    arr = np.asarray(im, dtype=float)
    cv.c = arr[..., :3]
    cv.a = arr[..., 3] / 255
    return save(cv, "scare_face")


def markers():
    """Marcadores de equipo (README 8.4): cada tipo con su forma (no solo color, 24.2). Blancos con
    borde negro para tenirlos con el color del jugador."""
    made = []
    for name, draw in (
            ("marker_attention", lambda d, n: (d.ellipse([5 * n, 5 * n, 27 * n, 27 * n], fill=255),
                                              d.rectangle([14 * n, 9 * n, 18 * n, 19 * n], fill=0),
                                              d.rectangle([14 * n, 21 * n, 18 * n, 24 * n], fill=0))),
            ("marker_danger", lambda d, n: (d.polygon([(16 * n, 3 * n), (30 * n, 28 * n), (2 * n, 28 * n)], fill=255),
                                           d.rectangle([14 * n, 11 * n, 18 * n, 20 * n], fill=0),
                                           d.rectangle([14 * n, 22 * n, 18 * n, 25 * n], fill=0))),
            ("marker_go", lambda d, n: d.polygon([(4 * n, 12 * n), (17 * n, 12 * n), (17 * n, 5 * n), (29 * n, 16 * n),
                                                 (17 * n, 27 * n), (17 * n, 20 * n), (4 * n, 20 * n)], fill=255)),
            ("marker_help", lambda d, n: (d.rectangle([12 * n, 3 * n, 20 * n, 29 * n], fill=255),
                                         d.rectangle([3 * n, 12 * n, 29 * n, 20 * n], fill=255))),
            ("marker_clear", lambda d, n: d.line([(5 * n, 17 * n), (13 * n, 25 * n), (27 * n, 7 * n)], fill=255, width=6 * n)),
    ):
        n = 4
        im = Image.new("L", (32 * n, 32 * n), 0)
        draw(ImageDraw.Draw(im), n)
        m = np.asarray(im.resize((32, 32), Image.BOX), dtype=float) / 255
        ring = blur(m, 2, periodic=False)
        cv = Canvas(32, 32, (0, 0, 0), 0)
        cv.a = np.clip(np.maximum(m, (ring > 0.04) * 0.85), 0, 1)
        cv.c[:] = (0, 0, 0)
        cv.c[m > 0.5] = (255, 255, 255)
        made.append(save(cv, name))
    # walkie en la HUD: altavoz con ondas
    n = 4
    im = Image.new("L", (32 * n, 32 * n), 0)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([9 * n, 8 * n, 19 * n, 28 * n], radius=2 * n, fill=255)
    d.rectangle([16 * n, 2 * n, 18 * n, 9 * n], fill=255)
    for k, rr in enumerate((6, 10)):
        d.arc([(19 - rr) * n, (17 - rr) * n, (19 + rr) * n, (17 + rr) * n], -50, 50, fill=255, width=2 * n)
    m = np.asarray(im.resize((32, 32), Image.BOX), dtype=float) / 255
    cv = Canvas(32, 32, (0, 0, 0), 0)
    cv.a = np.clip(np.maximum(m, (blur(m, 1, periodic=False) > 0.05) * 0.8), 0, 1)
    cv.c[:] = (0, 0, 0)
    cv.c[m > 0.5] = (255, 255, 255)
    made.append(save(cv, "walkie"))
    return made


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    made = [vignette(), blood_vignette(), grain(), smear("smear", 20), smear("smear_small", 21, 64, 24, border=8),
            bar("bar_blood", 30, 100, 8, (120, 8, 10)), bar("bar_empty", 31, 100, 8, (40, 14, 12), empty=True),
            bar("bar_light", 32, 64, 6, (196, 180, 104)), bar("bar_light_empty", 33, 64, 6, (40, 38, 30), empty=True),
            heart(), bulb(), slot(False), slot(True), tape(), note(), panel("panel", 0), panel("panel_alert", 1),
            splatter(0), splatter(1), splatter(2), splatter(3), scare_face()] + markers()
    pips()
    print(len(made) + 2, "sprites")
    if len(sys.argv) > 1:
        from texlib import sheet
        ims = [c.image() for c in made]
        sheet(ims, 6, 2, labels=None, bg=(120, 116, 104)).save(sys.argv[1])


if __name__ == "__main__":
    main()
