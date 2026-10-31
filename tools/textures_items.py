#!/usr/bin/env python3
"""
Iconos de los objetos (32x32). Los de los bloques GeckoLib salen de renderizar el propio modelo
(tools/geo_preview.py), asi icono y bloque son iguales. El resto se pinta aqui: linterna, llave,
tarjetas, fusibles y rueda de valvula, todo gastado y sucio.

    python3 tools/textures_items.py [preview.png]
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from geo_preview import render  # noqa: E402
from texlib import Canvas, fbm, white, smoothstep, stencil_text, splat_mask, apply_blood  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/textures/item"
S = 32


def R(seed):
    return np.random.default_rng(seed)


def outline(cv, color=(10, 9, 8), alpha=0.9):
    """Contorno de 1 px alrededor de lo opaco, para que el icono se lea sobre cualquier fondo."""
    a = cv.a > 0.5
    near = np.zeros_like(a)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(a, dy, 0), dx, 1)
    ring = near & ~a
    cv.c[ring] = color
    cv.a[ring] = alpha


def shape(draw_fn, n=4):
    """Mascara suavizada dibujada a n aumentos."""
    im = Image.new("L", (S * n, S * n), 0)
    draw_fn(ImageDraw.Draw(im), n)
    return np.asarray(im.resize((S, S), Image.BOX), dtype=float) / 255


def grime(cv, r, k=0.4, color=(50, 42, 32)):
    n = fbm(S, S, r, base=3, octaves=3, periodic=False)
    cv.mix(color, smoothstep(0.55, 1.0, n) * k * (cv.a > 0))


def model_icon(name, yaw=35, pitch=-25, skip=()):
    im = render(name, 256, yaw, pitch, bg=(0, 0, 0, 0), skip_bones=skip)
    bbox = im.getbbox()
    im = im.crop(bbox)
    side = max(im.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    small = sq.resize((S - 2, S - 2), Image.LANCZOS)
    arr = np.asarray(small, dtype=float)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    cv.c[1:-1, 1:-1] = arr[..., :3]
    cv.a[1:-1, 1:-1] = (arr[..., 3] > 110).astype(float)
    # un poco mas de contraste: a 32 px el modelo se emborrona
    cv.c = np.clip((cv.c - 128) * 1.12 + 128, 0, 255)
    outline(cv)
    return cv


# Objetos pintados -------------------------------------------------------------------------------------

def flashlight():
    r = R(1)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    ang = -math.pi / 4  # en diagonal, cabeza arriba a la derecha

    def body(d, n):
        def seg(t0, t1, w):
            cx0, cy0 = 16 + math.cos(ang) * t0, 16 + math.sin(ang) * t0
            cx1, cy1 = 16 + math.cos(ang) * t1, 16 + math.sin(ang) * t1
            d.line([(cx0 * n, cy0 * n), (cx1 * n, cy1 * n)], fill=255, width=int(w * n))
        seg(-12, 6, 6)
        seg(5, 12, 9.5)
    m = shape(body)
    cv.a = (m > 0.5).astype(float)
    yy, xx = np.mgrid[0:S, 0:S]
    # coordenada a lo largo (t) y a lo ancho (s) de la linterna
    t = (xx - 16) * math.cos(ang) + (yy - 16) * math.sin(ang)
    sidec = -(xx - 16) * math.sin(ang) + (yy - 16) * math.cos(ang)
    base = np.array([70, 72, 70], dtype=float)
    cv.c[:] = base
    cv.c *= (1.15 - 0.09 * sidec)[..., None].clip(0.55, 1.5)     # cilindro: luz arriba
    grip = (t > -10) & (t < 3) & (np.abs(np.round(t) % 2) == 0)
    cv.c[grip] *= 0.72                                           # moleteado
    tape = (t > -6) & (t < -2)
    cv.c[tape] = np.array([150, 132, 70]) * (1.1 - 0.06 * sidec[tape])[:, None].clip(0.6, 1.4)  # cinta aislante vieja
    head = t > 5
    cv.c[head] = np.array([110, 114, 110]) * (1.2 - 0.07 * sidec[head])[:, None].clip(0.5, 1.6)
    lens = t > 10.6
    cv.c[lens] = (230, 214, 150)
    cv.c[lens & (sidec > 1.5)] = (180, 160, 100)
    # boton
    bx, by = 16 + math.cos(ang) * 2 + math.sin(ang) * 3, 16 + math.sin(ang) * 2 - math.cos(ang) * 3
    cv.px(int(bx), int(by), (150, 26, 22), 1, wrap=False)
    grime(cv, r, 0.5)
    m = splat_mask(S, S, r, 12, 21, 2.2, drops=3)
    tmp = Canvas(S, S)
    tmp.c = cv.c.copy()
    apply_blood(tmp, r, m * (cv.a > 0), age=0.6)
    cv.c = tmp.c
    outline(cv)
    return cv


def key():
    r = R(2)
    cv = Canvas(S, S, (0, 0, 0), 0.0)

    def k(d, n):
        d.ellipse([4 * n, 4 * n, 15 * n, 15 * n], fill=255)
        d.line([(12 * n, 12 * n), (26 * n, 26 * n)], fill=255, width=int(3.2 * n))
        for t in (21, 24.5):
            d.line([(t * n, t * n), ((t + 3.5) * n, (t - 3.5) * n)], fill=255, width=int(2.6 * n))
    m = shape(k)
    hole = shape(lambda d, n: d.ellipse([7.5 * n, 7.5 * n, 11.5 * n, 11.5 * n], fill=255))
    cv.a = ((m > 0.5) & (hole < 0.5)).astype(float)
    yy, xx = np.mgrid[0:S, 0:S]
    cv.c[:] = (150, 120, 60)                                    # laton
    cv.c *= (1.2 - 0.025 * (xx + yy - 20))[..., None].clip(0.6, 1.4)
    n = fbm(S, S, r, base=4, octaves=3, periodic=False)
    cv.mix((70, 96, 70), smoothstep(0.6, 0.85, n) * 0.6)        # cardenillo
    cv.mix((60, 40, 24), smoothstep(0.5, 1, white(S, S, r)) * 0.25)
    # etiqueta de carton atada
    tag = np.zeros((S, S), bool)
    tag[1:7, 18:28] = True
    cv.c[tag] = (196, 180, 140)
    cv.a[tag] = 1
    stencil_text(cv, 19, 2, "SP", (120, 20, 16), 0.9)
    cv.c[7, 17:19] = (90, 80, 60)
    cv.a[7, 17:19] = 1
    outline(cv)
    return cv


def keycard(level):
    r = R(10 + level)
    col = {1: (60, 140, 70), 2: (196, 150, 40), 3: (170, 34, 30)}[level]
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    x0, y0, x1, y1 = 3, 7, 28, 24
    cv.a[y0:y1 + 1, x0:x1 + 1] = 1
    cv.a[y0, x0] = cv.a[y0, x1] = cv.a[y1, x0] = cv.a[y1, x1] = 0
    cv.c[:] = (206, 204, 194)
    cv.c[y0:y0 + 4, x0:x1 + 1] = col                            # franja de nivel
    stencil_text(cv, x0 + 2, y0 - 0 + 0, "", (0, 0, 0))
    # foto (silueta), lineas de texto y chip
    cv.rect(x0 + 2, y0 + 6, x0 + 8, y0 + 14, (70, 74, 80))
    cv.rect(x0 + 4, y0 + 7, x0 + 6, y0 + 9, (150, 136, 120))
    cv.rect(x0 + 3, y0 + 10, x0 + 7, y0 + 14, (40, 44, 50))
    # la cara de la foto tachada con una sonrisa
    cv.px(x0 + 4, y0 + 9, (110, 10, 10), 1, wrap=False)
    cv.px(x0 + 5, y0 + 9, (110, 10, 10), 1, wrap=False)
    cv.px(x0 + 6, y0 + 9, (110, 10, 10), 1, wrap=False)
    for i, w in enumerate((12, 9, 11)):
        cv.rect(x0 + 11, y0 + 7 + i * 3, x0 + 11 + w, y0 + 7 + i * 3, (90, 90, 86))
    cv.rect(x1 - 5, y1 - 5, x1 - 2, y1 - 3, (190, 160, 70))
    stencil_text(cv, x1 - 4 - 4 * len(str(level)), y0, f"N{level}", (240, 240, 230), 0.95)
    # bordes, mugre, rozaduras y sangre
    cv.c[y1, x0:x1 + 1] *= 0.7
    cv.c[y0:y1 + 1, x1] *= 0.75
    grime(cv, r, 0.45)
    m = splat_mask(S, S, r, x1 - 3, y0 + 3, 2.6, drops=4)
    apply_blood(cv, r, m * cv.a, age=0.55)
    outline(cv)
    return cv


def fuse(blown):
    r = R(20 + blown)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    ang = -math.pi / 4
    yy, xx = np.mgrid[0:S, 0:S]
    t = (xx - 16) * math.cos(ang) + (yy - 16) * math.sin(ang)
    s = -(xx - 16) * math.sin(ang) + (yy - 16) * math.cos(ang)
    body = (np.abs(t) <= 12) & (np.abs(s) <= 4.2)
    cv.a = body.astype(float)
    caps = body & (np.abs(t) > 7.5)
    glass = body & ~caps
    cv.c[caps] = np.array([170, 172, 170]) * (1.2 - 0.09 * s[caps])[:, None].clip(0.5, 1.5)
    cv.c[caps & (np.abs(t) > 11)] *= 0.8
    g = np.array([150, 140, 110]) if not blown else np.array([70, 60, 50])
    cv.c[glass] = g * (1.15 - 0.06 * s[glass])[:, None].clip(0.6, 1.5)
    cv.c[glass & (s < -2.5)] = (236, 232, 210) if not blown else (120, 110, 100)   # reflejo
    # filamento
    for tt in np.linspace(-7.5, 7.5, 40):
        ss = 1.2 * math.sin(tt * 1.4)
        if blown and abs(tt) < 2:
            continue
        x = int(round(16 + tt * math.cos(ang) - ss * math.sin(ang)))
        y = int(round(16 + tt * math.sin(ang) + ss * math.cos(ang)))
        cv.px(x, y, (230, 150, 40) if not blown else (40, 34, 30), 1, wrap=False)
    if blown:
        cv.mix((20, 18, 16), (glass & (np.abs(t) < 4)) * smoothstep(4, 0, np.abs(t)) * 0.8)   # hollin
        for i in range(4):
            cv.px(16 + i - 2, 15 - i, (200, 196, 180), 0.8, wrap=False)                     # grieta del cristal
    grime(cv, r, 0.35)
    outline(cv)
    return cv


def valve_wheel():
    r = R(30)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.hypot(xx - 15.5, yy - 15.5)
    ang = np.arctan2(yy - 15.5, xx - 15.5)
    rim = (d >= 10.5) & (d <= 14)
    spokes = (d < 11) & (np.min([np.abs(np.sin(ang - k * math.pi / 3 * 0)) for k in range(1)], 0) < 0) # placeholder
    spokes = (d < 11) & ((np.abs(xx - 15.5) < 1.3) | (np.abs(yy - 15.5) < 1.3))
    hub = d < 3.6
    m = rim | spokes | hub
    cv.a = m.astype(float)
    cv.c[:] = (150, 30, 26)
    light = (1.15 - 0.012 * (xx + yy - 31))[..., None]
    cv.c *= light.clip(0.6, 1.4)
    cv.c[rim & (d > 13.2)] *= 0.7
    cv.c[rim & (d < 11.2)] *= 0.8
    cv.c[hub] = (150, 152, 150)
    cv.c[(d < 1.5)] = (60, 60, 58)
    n = fbm(S, S, r, base=4, octaves=3, periodic=False)
    cv.mix((96, 60, 40), smoothstep(0.5, 0.8, n) * 0.7 * m)       # pintura saltada
    cv.mix((110, 54, 26), smoothstep(0.72, 0.9, n) * 0.8 * m)     # oxido
    outline(cv)
    return cv


def medkit():
    """Botiquin de metal abollado, cruz roja medio borrada, cierre y sangre seca."""
    r = R(40)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    x0, y0, x1, y1 = 4, 9, 27, 26
    cv.a[y0:y1 + 1, x0:x1 + 1] = 1
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        cv.a[y, x] = 0
    yy, xx = np.mgrid[0:S, 0:S]
    cv.c[:] = (196, 192, 178)
    cv.c *= (1.15 - 0.02 * (yy - y0))[..., None].clip(0.6, 1.3)
    cv.c[y0:y0 + 2, x0:x1 + 1] *= 1.08
    cv.c[y1 - 1:y1 + 1, x0:x1 + 1] *= 0.7
    # asa
    cv.rect(12, 5, 19, 6, (60, 58, 54))
    cv.rect(12, 6, 13, 8, (60, 58, 54))
    cv.rect(18, 6, 19, 8, (60, 58, 54))
    # cruz roja
    cross = ((np.abs(xx - 15.5) <= 1.6) & (np.abs(yy - 17.5) <= 5.5)) | ((np.abs(yy - 17.5) <= 1.6) & (np.abs(xx - 15.5) <= 5.5))
    cv.c[cross] = (176, 26, 24)
    n = fbm(S, S, r, base=5, octaves=3, periodic=False)
    cv.mix((196, 190, 176), cross * smoothstep(0.62, 0.8, n) * 0.9)     # pintura saltada
    cv.rect(x0, 16, x0 + 1, 18, (90, 88, 82))                             # bisagra
    cv.rect(x1 - 1, 16, x1, 18, (120, 116, 108))                          # cierre
    grime(cv, r, 0.5)
    m = splat_mask(S, S, r, 8, 23, 2.4, drops=5)
    apply_blood(cv, r, m * cv.a, age=0.7)
    outline(cv)
    return cv


def battery():
    """Pila grande de linterna, etiqueta despegada y polo oxidado."""
    r = R(41)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    ang = -math.pi / 4
    yy, xx = np.mgrid[0:S, 0:S]
    t = (xx - 16) * math.cos(ang) + (yy - 16) * math.sin(ang)
    s = -(xx - 16) * math.sin(ang) + (yy - 16) * math.cos(ang)
    body = (np.abs(t) <= 10) & (np.abs(s) <= 5.5)
    tip = (t > 10) & (t <= 12) & (np.abs(s) <= 2)
    cv.a = (body | tip).astype(float)
    shade = (1.25 - 0.08 * s).clip(0.5, 1.5)[..., None]
    cv.c[:] = (36, 34, 32)
    cv.c[body & (t > 2)] = (190, 150, 40)                                  # etiqueta amarilla
    cv.c[body & (t > 6)] = (40, 38, 34)
    cv.c *= shade
    cv.c[tip] = (170, 170, 166)
    stencil_text(cv, 12, 13, "+", (20, 18, 16), 0.9)
    n = fbm(S, S, r, base=4, octaves=3, periodic=False)
    cv.mix((110, 70, 30), (body & (t < -7)) * smoothstep(0.45, 0.8, n) * 0.8)   # oxido en el polo
    cv.mix((60, 54, 40), body * smoothstep(0.6, 0.9, n) * 0.4)
    outline(cv)
    return cv


def flare():
    """Bengala de emergencia: tubo rojo con capuchon y mecha."""
    r = R(42)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    ang = -math.pi / 4
    yy, xx = np.mgrid[0:S, 0:S]
    t = (xx - 16) * math.cos(ang) + (yy - 16) * math.sin(ang)
    s = -(xx - 16) * math.sin(ang) + (yy - 16) * math.cos(ang)
    body = (np.abs(t) <= 12) & (np.abs(s) <= 3)
    cv.a = body.astype(float)
    cv.c[:] = (190, 30, 26)
    cv.c[body & (t > 7)] = (60, 56, 52)                                    # capuchon
    cv.c[body & (t < -10.5)] = (150, 140, 120)                             # mecha
    cv.c *= (1.3 - 0.14 * s).clip(0.5, 1.6)[..., None]
    for k in (-5, 0, 4):                                                   # bandas
        cv.mix((230, 220, 200), (body & (np.abs(t - k) < 0.7)) * 0.7)
    grime(cv, r, 0.35)
    outline(cv)
    return cv


def syringe():
    """Jeringa de adrenalina: liquido ambar, embolo y aguja; cinta con letras."""
    r = R(43)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    ang = -math.pi / 4
    yy, xx = np.mgrid[0:S, 0:S]
    t = (xx - 16) * math.cos(ang) + (yy - 16) * math.sin(ang)
    s = -(xx - 16) * math.sin(ang) + (yy - 16) * math.cos(ang)
    barrel = (np.abs(t) <= 7) & (np.abs(s) <= 3.2)
    plunger = (t < -7) & (t >= -12) & (np.abs(s) <= 1)
    cap = (t < -12) & (t >= -13.5) & (np.abs(s) <= 3.2)
    needle = (t > 7) & (t <= 14) & (np.abs(s) <= 0.55)
    cv.a = (barrel | plunger | cap | needle).astype(float)
    cv.c[barrel] = (210, 214, 210)
    cv.c[barrel & (t > -2)] = (220, 140, 30)                               # adrenalina
    cv.c[barrel & (np.abs(s) > 2.6)] *= 0.75
    cv.c[plunger | cap] = (70, 72, 76)
    cv.c[needle] = (200, 204, 210)
    cv.c[barrel & (s < -1.8)] = (250, 250, 240)                            # reflejo
    for k in (-5, -3, -1, 1, 3, 5):
        cv.mix((40, 40, 40), (barrel & (np.abs(t - k) < 0.4) & (s > 1.5)) * 0.9)   # marcas
    outline(cv)
    return cv


def can():
    """Lata de conserva vacia, abollada y sin etiqueta casi."""
    r = R(44)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    yy, xx = np.mgrid[0:S, 0:S]
    body = (np.abs(xx - 15.5) <= 7) & (yy >= 8) & (yy <= 26)
    top = ((xx - 15.5) / 7.2) ** 2 + ((yy - 8) / 2.4) ** 2 <= 1
    cv.a = (body | top).astype(float)
    shade = (1.35 - np.abs(xx - 12) * 0.07).clip(0.5, 1.5)[..., None]
    cv.c[:] = (170, 172, 168)
    cv.c[body & (yy > 12) & (yy < 22)] = (150, 40, 34)                    # restos de etiqueta
    cv.c *= shade
    cv.c[top] = (120, 122, 118)
    cv.c[((xx - 15.5) / 5.5) ** 2 + ((yy - 8) / 1.6) ** 2 <= 1] = (60, 60, 58)
    for y in (10, 24):
        cv.c[y, :] *= 0.75
    n = fbm(S, S, r, base=4, octaves=3, periodic=False)
    cv.mix((200, 196, 190), body * (yy > 12) * (yy < 22) * smoothstep(0.55, 0.8, n) * 0.8)
    cv.mix((110, 70, 40), smoothstep(0.7, 0.9, n) * 0.6 * cv.a)
    cv.c[16:19, 19:22] *= 0.6                                              # abolladura
    outline(cv)
    return cv


def bottle():
    """Botella de cristal verde sucio con tapon."""
    r = R(45)
    cv = Canvas(S, S, (0, 0, 0), 0.0)

    def b(d, n):
        d.rounded_rectangle([10 * n, 13 * n, 21 * n, 29 * n], radius=3 * n, fill=255)
        d.polygon([(10 * n, 15 * n), (13.5 * n, 8 * n), (17.5 * n, 8 * n), (21 * n, 15 * n)], fill=255)
        d.rectangle([13.5 * n, 3 * n, 17.5 * n, 9 * n], fill=255)
    m = shape(b)
    cv.a = (m > 0.5).astype(float) * 0.92
    yy, xx = np.mgrid[0:S, 0:S]
    cv.c[:] = (52, 100, 56)
    cv.c *= (1.3 - np.abs(xx - 13) * 0.06).clip(0.5, 1.5)[..., None]
    cv.c[(xx >= 12) & (xx <= 13) & (yy > 14) & (yy < 27) & (m > 0.5)] = (170, 210, 170)   # brillo
    cv.c[(yy <= 4) & (m > 0.5)] = (120, 90, 60)                                           # tapon de corcho
    cv.rect(10, 18, 21, 23, (180, 170, 140))                                              # etiqueta
    stencil_text(cv, 11, 19, "XX", (60, 20, 16), 0.8)
    n = fbm(S, S, r, base=4, octaves=3, periodic=False)
    cv.mix((60, 54, 40), (m > 0.5) * smoothstep(0.55, 0.85, n) * 0.5)
    cv.a[(yy <= 4) & (m > 0.5)] = 1
    cv.a[(yy >= 18) & (yy <= 23) & (xx >= 10) & (xx <= 21)] = 1
    outline(cv)
    return cv


def walkie():
    """Walkie-talkie militar: cuerpo negro rozado, antena, rejilla del altavoz y piloto verde."""
    r = R(53)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    m = shape(lambda d, n: (d.rounded_rectangle([9 * n, 9 * n, 22 * n, 30 * n], radius=2 * n, fill=255),
                            d.rectangle([18 * n, 1 * n, 20 * n, 10 * n], fill=255),
                            d.rectangle([12 * n, 6 * n, 14 * n, 9 * n], fill=255)))
    cv.a = (m > 0.5).astype(float)
    yy, xx = np.mgrid[0:S, 0:S]
    cv.c[:] = (34, 34, 32)
    cv.c *= (1.25 - 0.03 * (xx - 9)).clip(0.6, 1.4)[..., None]
    grille = (xx >= 11) & (xx <= 20) & (yy >= 19) & (yy <= 27) & ((yy % 2) == 0)
    cv.c[grille & (cv.a > 0)] = (14, 14, 13)
    cv.c[(yy >= 12) & (yy <= 16) & (xx >= 11) & (xx <= 20)] = (70, 86, 64)        # pantallita
    cv.c[(yy == 11) & (xx >= 11) & (xx <= 12)] = (90, 230, 90)                    # piloto
    cv.c[(xx >= 18) & (xx <= 20) & (yy < 10)] = (24, 24, 22)                      # antena
    grime(cv, r, 0.5, (90, 84, 70))
    outline(cv)
    return cv


def alarm_clock():
    """Senuelo: despertador de cuerda con dos campanas, esfera blanca y agujas."""
    r = R(44)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    yy, xx = np.mgrid[0:S, 0:S]
    cx, cy = 16, 18
    d = np.hypot(xx - cx, yy - cy)
    body = d <= 10.5
    bells = (np.hypot(xx - 8, yy - 7) <= 4.2) | (np.hypot(xx - 24, yy - 7) <= 4.2)
    legs = ((np.abs(xx - 9) <= 1) | (np.abs(xx - 23) <= 1)) & (yy >= 27) & (yy <= 30)
    hammer = (np.abs(xx - 16) <= 0.8) & (yy >= 4) & (yy <= 8)
    cv.a = (body | bells | legs | hammer).astype(float)
    cv.c[:] = (176, 34, 30)
    cv.c[bells] = (196, 170, 90)
    cv.c[legs | hammer] = (60, 56, 52)
    face = d <= 7.8
    cv.c[face] = (232, 226, 206)
    ang = np.arctan2(yy - cy, xx - cx)
    for k in range(12):                                                    # marcas de las horas
        a = k * math.pi / 6
        m = (np.abs(d - 6.6) < 0.8) & (np.abs(np.angle(np.exp(1j * (ang - a)))) < 0.12)
        cv.c[m] = (40, 36, 32)
    hand1 = (d <= 5.5) & (np.abs(np.angle(np.exp(1j * (ang + math.pi / 2)))) < 0.16)
    hand2 = (d <= 4.0) & (np.abs(np.angle(np.exp(1j * (ang - 0.5)))) < 0.22)
    cv.c[hand1 | hand2] = (24, 22, 20)
    cv.c[d <= 1.0] = (150, 30, 26)
    rim = body & (d > 7.8)
    cv.c[rim] *= (1.25 - 0.06 * (xx[rim] - cx)).clip(0.6, 1.5)[..., None]
    cv.mix((255, 255, 240), (np.hypot(xx - 12, yy - 13) <= 2) * face * 0.5)   # reflejo del cristal
    grime(cv, r, 0.45)
    outline(cv)
    return cv


def stunner():
    """Aturdidor electrico: mango negro con franjas amarillas, dos puntas y una chispa azul."""
    r = R(45)
    cv = Canvas(S, S, (0, 0, 0), 0.0)
    ang = -math.pi / 4
    yy, xx = np.mgrid[0:S, 0:S]
    t = (xx - 16) * math.cos(ang) + (yy - 16) * math.sin(ang)
    s = -(xx - 16) * math.sin(ang) + (yy - 16) * math.cos(ang)
    grip = (t >= -13) & (t <= 3) & (np.abs(s) <= 3.4)
    head = (t > 3) & (t <= 8) & (np.abs(s) <= 4.4)
    prongs = (t > 8) & (t <= 12) & ((np.abs(s - 2.4) <= 0.6) | (np.abs(s + 2.4) <= 0.6))
    cv.a = (grip | head | prongs).astype(float)
    cv.c[grip] = (34, 34, 36)
    cv.c[head] = (58, 58, 62)
    cv.c[prongs] = (200, 200, 196)
    for k in (-9, -5):                                                     # franjas de aviso
        cv.c[grip & (np.abs(t - k) < 1.1)] = (220, 180, 30)
    cv.c[grip & (np.abs(t + 1) < 1.2) & (np.abs(s) < 1.2)] = (190, 40, 30)   # boton
    cv.c *= (1.3 - 0.12 * s).clip(0.5, 1.6)[..., None]
    spark = (t > 11) & (t <= 15) & (np.abs(s - 1.6 * np.sin(t * 2.3)) <= 0.9)
    cv.c[spark] = (150, 210, 255)
    cv.a[spark] = 1.0
    cv.mix((230, 245, 255), spark * 0.5)
    grime(cv, r, 0.3)
    outline(cv)
    return cv


def main():
    out = {}
    out["walkie"] = walkie()
    out["flashlight"] = flashlight()
    out["key"] = key()
    for lvl in (1, 2, 3):
        out[f"keycard_{lvl}"] = keycard(lvl)
    out["fuse"] = fuse(False)
    out["fuse_blown"] = fuse(True)
    out["valve_wheel"] = valve_wheel()
    out["medkit"] = medkit()
    out["battery"] = battery()
    out["flare"] = flare()
    out["adrenaline"] = syringe()
    out["can"] = can()
    out["bottle"] = bottle()
    out["decoy"] = alarm_clock()
    out["stunner"] = stunner()
    for name, kw in (("lab_door", {}), ("security_door", {}), ("locker", {"yaw": -145}),
                     ("wardrobe", {"yaw": -145}), ("waste_container", {"yaw": -145}), ("gurney", {"yaw": 145}),
                     ("curtain", {"yaw": -150}), ("valve", {"yaw": -145}), ("fuse_box", {"yaw": -150, "skip": ("lamp_on",)})):
        out[name] = model_icon(name, **kw)
    for name, cv in out.items():
        cv.save(ROOT / f"{name}.png")
    print(len(out), "iconos")
    if len(sys.argv) > 1:
        from texlib import sheet
        sheet([cv.image() for cv in out.values()], 6, 6, labels=list(out), bg=(70, 70, 74)).save(sys.argv[1])


if __name__ == "__main__":
    main()
