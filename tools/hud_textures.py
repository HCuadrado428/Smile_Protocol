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


def heart():
    """Corazon anatomico de 16x16: auriculas, aorta y venas, en rojo oscuro."""
    r = R(5)
    S = 16
    n = 6
    im = Image.new("L", (S * n, S * n), 0)
    d = ImageDraw.Draw(im)
    d.ellipse([2.5 * n, 5 * n, 13.5 * n, 15 * n], fill=255)
    d.polygon([(3 * n, 10 * n), (8.5 * n, 15.8 * n), (13 * n, 9 * n)], fill=255)
    d.ellipse([2 * n, 3.5 * n, 7.5 * n, 9 * n], fill=255)                 # auricula
    d.rectangle([7 * n, 0.5 * n, 10 * n, 6 * n], fill=255)                # aorta
    d.rectangle([10.5 * n, 1.5 * n, 12.5 * n, 6.5 * n], fill=255)         # pulmonar
    d.rectangle([4 * n, 1.5 * n, 6 * n, 5 * n], fill=255)
    m = np.asarray(im.resize((S, S), Image.BOX), dtype=float) / 255
    cv = Canvas(S, S, (120, 16, 20), 0)
    cv.a = (m > 0.45).astype(float)
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    cv.c *= (1.25 - 0.05 * (xx + yy - 10))[..., None].clip(0.5, 1.5)
    # venas coronarias oscuras y brillo humedo
    for (x0, y0), (x1, y1) in (((8, 7), (6, 14)), ((9, 7), (12, 12)), ((7, 9), (4, 11))):
        for t in np.linspace(0, 1, 12):
            x, y = int(round(x0 + (x1 - x0) * t + math.sin(t * 6) * 0.6)), int(round(y0 + (y1 - y0) * t))
            if 0 <= x < S and 0 <= y < S and cv.a[y, x]:
                cv.c[y, x] = (60, 10, 30)
    cv.c[6, 4:6] = (230, 120, 120)
    cv.c[7, 4] = (200, 90, 90)
    cv.c[1:4, 7:10] = (150, 60, 60)
    cv.c[1, 7:10] = (190, 110, 100)
    # contorno
    a = cv.a > 0.5
    ring = np.zeros_like(a)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ring |= np.roll(np.roll(a, dy, 0), dx, 1)
    ring &= ~a
    cv.c[ring] = (20, 4, 4)
    cv.a[ring] = 1
    return save(cv, "heart")


def bulb():
    """Icono de bombilla (bateria de la linterna), 12x12."""
    S = 12
    cv = Canvas(S, S, (210, 190, 120), 0)
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    glass = np.hypot(xx - 5.5, yy - 4.5) <= 4.3
    base = (yy >= 8) & (yy <= 10) & (np.abs(xx - 5.5) <= 2)
    cv.a = (glass | base).astype(float)
    cv.c[glass] = np.array([220, 200, 130]) * (1.2 - 0.05 * (xx + yy)[glass])[:, None].clip(0.6, 1.3)
    cv.c[base] = (120, 116, 106)
    cv.c[9, 3:9] = (70, 66, 60)
    for x, y in ((4, 4), (5, 5), (6, 4), (7, 5)):
        cv.c[y, x] = (130, 90, 40)                                         # filamento
    cv.c[2, 3:5] = (255, 250, 220)
    a = cv.a > 0.5
    ring = np.zeros_like(a)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ring |= np.roll(np.roll(a, dy, 0), dx, 1)
    ring &= ~a
    cv.c[ring] = (10, 8, 6)
    cv.a[ring] = 0.9
    return save(cv, "bulb")


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
    """Susto: la cara de Smiley pegada a la pantalla. Negro con una sonrisa roja enorme y un ojo."""
    r = R(77)
    S = 256
    cv = Canvas(S, S, (0, 0, 0), 0)
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    n = fbm(S, S, r, base=5, octaves=5, periodic=False)
    # cabeza alargada y deforme que se sale por los bordes
    d = np.hypot((xx - S / 2) / (S * 0.46), (yy - S * 0.55) / (S * 0.62))
    head = smoothstep(1.05, 0.9, d + (n - 0.5) * 0.25)
    cv.a = head
    cv.c[:] = (8, 6, 7)
    cv.c = cv.c + (n[..., None] - 0.5) * 18
    # sonrisa: arco ancho de dientes rojos
    cy = S * 0.66
    smile = (np.abs((yy - cy) + 0.0028 * (xx - S / 2) ** 2) < 16) & (np.abs(xx - S / 2) < S * 0.36)
    teeth = smile & (((xx // 11) % 2) == 0)
    cv.c[smile] = (90, 4, 6)
    cv.c[teeth] = (225, 30, 26)
    gums = smile & (np.abs((yy - cy) + 0.0028 * (xx - S / 2) ** 2) > 11)
    cv.c[gums] = (150, 10, 14)
    # un ojo abierto, rojo y brillante; el otro, una rendija
    eye = np.hypot((xx - S * 0.36) / 15, (yy - S * 0.4) / 11) < 1
    cv.c[eye] = (255, 50, 40)
    cv.c[np.hypot((xx - S * 0.36) / 5, (yy - S * 0.4) / 5) < 1] = (40, 0, 0)
    slit = (np.abs(yy - S * 0.41) < 2) & (np.abs(xx - S * 0.65) < 16)
    cv.c[slit] = (160, 20, 18)
    # chorretones de sangre desde la boca
    for i in range(7):
        x0 = S / 2 + r.uniform(-S * 0.3, S * 0.3)
        y0 = cy - 0.0028 * (x0 - S / 2) ** 2 + 8
        ln = r.uniform(20, 70)
        band = (np.abs(xx - x0) < r.uniform(1.5, 3.5)) & (yy > y0) & (yy < y0 + ln)
        cv.c[band & (head > 0.5)] = (120, 6, 8)
    return save(cv, "scare_face")


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    made = [vignette(), blood_vignette(), grain(), smear("smear", 20), smear("smear_small", 21, 64, 24, border=8),
            bar("bar_blood", 30, 100, 8, (120, 8, 10)), bar("bar_empty", 31, 100, 8, (40, 14, 12), empty=True),
            bar("bar_light", 32, 64, 6, (196, 180, 104)), bar("bar_light_empty", 33, 64, 6, (40, 38, 30), empty=True),
            heart(), bulb(), slot(False), slot(True), tape(), note(), panel("panel", 0), panel("panel_alert", 1),
            splatter(0), splatter(1), splatter(2), splatter(3), scare_face()]
    pips()
    print(len(made) + 2, "sprites")
    if len(sys.argv) > 1:
        from texlib import sheet
        ims = [c.image() for c in made]
        sheet(ims, 6, 2, labels=None, bg=(120, 116, 104)).save(sys.argv[1])


if __name__ == "__main__":
    main()
