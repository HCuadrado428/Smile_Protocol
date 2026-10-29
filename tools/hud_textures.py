#!/usr/bin/env python3
"""
Sprites del HUD (README 8.1), estilo monitor medico de laboratorio. Van en el atlas de GUI
(textures/gui/sprites/hud/) y los paneles se escalan en nueve partes (nine-slice).

    python3 tools/hud_textures.py [preview.png]
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/textures/gui/sprites/hud"

ACCENT = (86, 224, 200)       # fosforo verde azulado
ACCENT_DIM = (36, 110, 100)
DANGER = (226, 44, 58)
GLASS = (9, 15, 14)
EDGE = (0, 0, 0)


def canvas(w, h):
    return np.zeros((h, w, 4), dtype=np.float64)


def put(a, x, y, rgb, alpha):
    h, w = a.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        src = np.array([*rgb, alpha], dtype=np.float64)
        da = a[y, x, 3] / 255.0
        sa = alpha / 255.0
        out_a = sa + da * (1 - sa)
        if out_a <= 0:
            return
        a[y, x, :3] = (src[:3] * sa + a[y, x, :3] * da * (1 - sa)) / out_a
        a[y, x, 3] = out_a * 255


def save(name, a, nine=None):
    ROOT.mkdir(parents=True, exist_ok=True)
    img = Image.fromarray(np.clip(a, 0, 255).round().astype(np.uint8), "RGBA")
    img.save(ROOT / f"{name}.png")
    meta = ROOT / f"{name}.png.mcmeta"
    if nine:
        w, h, border = nine
        meta.write_text(json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}, indent=2) + "\n")
    elif meta.exists():
        meta.unlink()
    return img


def corner_brackets(a, x0, y0, x1, y1, length, rgb, alpha):
    for i in range(length):
        for (x, y) in ((x0 + i, y0), (x0, y0 + i), (x1 - i, y0), (x1, y0 + i),
                       (x0 + i, y1), (x0, y1 - i), (x1 - i, y1), (x1, y1 - i)):
            put(a, x, y, rgb, alpha)


def panel(size=32, accent=ACCENT):
    """Cristal oscuro con borde, linea interior y esquinas de acento."""
    a = canvas(size, size)
    for y in range(size):
        g = 1 - y / size * 0.35
        for x in range(size):
            base = tuple(int(c * (0.8 + 0.4 * g)) for c in GLASS)
            alpha = 205 if y % 2 == 0 else 190  # lineas de barrido
            put(a, x, y, base, alpha)
    # Borde exterior negro y brillo superior
    for i in range(size):
        for p in ((i, 0), (i, size - 1), (0, i), (size - 1, i)):
            put(a, *p, EDGE, 230)
    for i in range(2, size - 2):
        put(a, i, 1, (60, 90, 84), 120)
    # Linea interior tenue
    for i in range(3, size - 3):
        for p in ((i, 3), (i, size - 4), (3, i), (size - 4, i)):
            put(a, *p, ACCENT_DIM, 90)
    corner_brackets(a, 1, 1, size - 2, size - 2, 6, accent, 235)
    return a


def slot(selected):
    s = 24
    a = canvas(s, s)
    for y in range(s):
        for x in range(s):
            put(a, x, y, (8, 13, 12), 185 if y % 2 else 200)
    for i in range(s):
        for p in ((i, 0), (i, s - 1), (0, i), (s - 1, i)):
            put(a, *p, EDGE, 230)
    if selected:
        # Halo interior
        for y in range(1, s - 1):
            for x in range(1, s - 1):
                d = min(x - 1, y - 1, s - 2 - x, s - 2 - y)
                if d < 4:
                    put(a, x, y, ACCENT, 70 * (1 - d / 4))
        for i in range(1, s - 1):
            for p in ((i, 1), (i, s - 2), (1, i), (s - 2, i)):
                put(a, *p, ACCENT, 230)
        corner_brackets(a, 0, 0, s - 1, s - 1, 5, (190, 255, 240), 255)
    else:
        for i in range(2, s - 2):
            for p in ((i, 1), (i, s - 2), (1, i), (s - 2, i)):
                put(a, *p, (54, 74, 70), 160)
        corner_brackets(a, 1, 1, s - 2, s - 2, 3, ACCENT_DIM, 200)
    return a


HEART = [
    "..XX...XX..",
    ".XXXX.XXXX.",
    "XXXXXXXXXXX",
    "XXXXXXXXXXX",
    "XXXXXXXXXXX",
    ".XXXXXXXXX.",
    "..XXXXXXX..",
    "...XXXXX...",
    "....XXX....",
    ".....X.....",
]


def heart(color):
    a = canvas(13, 12)
    # Contorno oscuro
    for y, row in enumerate(HEART):
        for x, c in enumerate(row):
            if c == "X":
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    put(a, x + 1 + dx, y + 1 + dy, (20, 4, 6), 230)
    for y, row in enumerate(HEART):
        for x, c in enumerate(row):
            if c == "X":
                shade = 1.0 - 0.35 * (y / len(HEART))
                put(a, x + 1, y + 1, tuple(int(v * shade) for v in color), 255)
    # Brillo
    for p in ((3, 2), (4, 2), (3, 3), (2, 3)):
        put(a, *p, (255, 210, 210), 200)
    return a


def pip(on):
    a = canvas(7, 7)
    for y in range(7):
        for x in range(7):
            d = math.hypot(x - 3, y - 3)
            if on:
                if d <= 2.2:
                    put(a, x, y, ACCENT, 255)
                elif d <= 3.4:
                    put(a, x, y, ACCENT, 90)
            else:
                if 1.6 <= d <= 2.6:
                    put(a, x, y, (90, 120, 114), 200)
    return a


def vignette(size=128):
    """Degradado radial: transparente en el centro y opaco en los bordes (se tinta en el juego)."""
    a = canvas(size, size)
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot((x - c) / c, (y - c) / c) / math.sqrt(2)
            k = max(0.0, (d - 0.35) / 0.65)
            put(a, x, y, (255, 255, 255), 255 * (k ** 1.6))
    return a


def main():
    save("panel", panel(), nine=(32, 32, 8))
    save("panel_alert", panel(accent=DANGER), nine=(32, 32, 8))
    save("slot", slot(False))
    save("slot_selected", slot(True))
    save("heart", heart((232, 48, 62)))
    save("pip_on", pip(True))
    save("pip_off", pip(False))
    save("vignette", vignette())
    print("Sprites del HUD en", ROOT)
    if len(sys.argv) > 1:
        preview(sys.argv[1])


# Vista previa aproximada (fuente y escalado no son los del juego) ------------------------

def preview(path):
    from PIL import ImageDraw, ImageFont
    S = 3
    W, H = 427, 240  # GUI 1280x720 a escala 3
    bg = Image.new("RGBA", (W, H), (30, 34, 32, 255))
    # Fondo oscuro de laboratorio con un poco de luz
    d = ImageDraw.Draw(bg)
    for y in range(H):
        v = int(18 + 22 * (1 - abs(y - H * 0.45) / H))
        d.line([(0, y), (W, y)], fill=(v, v + 4, v + 2, 255))
    sp = lambda n: Image.open(ROOT / f"{n}.png").convert("RGBA")

    def nine(img, w, h, b=8):
        out = Image.new("RGBA", (w, h))
        iw, ih = img.size
        boxes = [(0, b), (b, iw - b), (iw - b, iw)]
        ys = [(0, b), (b, ih - b), (ih - b, ih)]
        tx = [(0, b), (b, w - b), (w - b, w)]
        ty = [(0, b), (b, h - b), (h - b, h)]
        for j in range(3):
            for i in range(3):
                part = img.crop((boxes[i][0], ys[j][0], boxes[i][1], ys[j][1]))
                tw, th = tx[i][1] - tx[i][0], ty[j][1] - ty[j][0]
                if tw > 0 and th > 0:
                    out.paste(part.resize((tw, th), Image.NEAREST), (tx[i][0], ty[j][0]))
        return out

    vig = sp("vignette").resize((W, H), Image.BILINEAR)
    r, g, b, al = vig.split()
    dark = Image.merge("RGBA", (r.point(lambda v: 0), g.point(lambda v: 0), b.point(lambda v: 0), al.point(lambda v: int(v * 0.5))))
    bg.alpha_composite(dark)
    try:
        f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 7)
        fb = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", 14)
        fs = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 6)
    except OSError:
        f = fb = fs = ImageFont.load_default()
    d = ImageDraw.Draw(bg)

    # Panel de constantes vitales
    px, py, pw, ph = 8, H - 38, 138, 30
    bg.alpha_composite(nine(sp("panel"), pw, ph), (px, py))
    bg.alpha_composite(sp("heart"), (px + 7, py + 9))
    d.text((px + 25, py + 4), "CONSTANTES", fill=(130, 150, 145), font=fs)
    d.text((px + pw - 26, py + 4), "72%", fill=(220, 232, 228), font=fs)
    # ECG
    ex, ey, ew, eh = px + 25, py + 12, pw - 33, 9
    def ecg(p):
        if 0.10 < p < 0.18: return 0.15 * math.sin((p - 0.10) / 0.08 * math.pi)
        if 0.26 < p < 0.29: return -0.25
        if 0.29 <= p < 0.33: return 1.0
        if 0.33 <= p < 0.36: return -0.45
        if 0.50 < p < 0.66: return 0.25 * math.sin((p - 0.50) / 0.16 * math.pi)
        return 0.0
    prev = None
    for x in range(ew):
        p = ((x / 46.0) + 0.4) % 1.0
        y = int(ey + eh / 2 - ecg(p) * (eh / 2))
        fade = 0.35 + 0.65 * (x / ew)
        col = tuple(int(c * fade) for c in ACCENT) + (255,)
        if prev is not None:
            for yy in range(min(prev, y), max(prev, y) + 1):
                d.point((ex + x, yy), fill=col)
        prev = y
    # Barra segmentada
    bx, by = px + 25, py + 23
    for i in range(10):
        col = (226, 44, 58, 255) if i < 7 else (40, 18, 20, 255)
        d.rectangle([bx + i * 10, by, bx + i * 10 + 8, by + 3], fill=col)

    # Huecos
    sx = W // 2 - (4 * 24 + 3 * 4) // 2
    sy = H - 32
    for i in range(4):
        bg.alpha_composite(sp("slot_selected" if i == 1 else "slot"), (sx + i * 28, sy))
        d.text((sx + i * 28 + 3, sy + 2), str(i + 1), fill=(130, 150, 145) if i != 1 else (190, 255, 240), font=fs)
    d.text((W // 2, sy - 8), "Linterna", fill=(220, 232, 228), font=f, anchor="mm")

    # Punto de mira
    d.rectangle([W // 2 - 1, H // 2 - 1, W // 2 + 1, H // 2 + 1], fill=(0, 0, 0, 150))
    d.point((W // 2, H // 2), fill=(240, 240, 240, 255))

    # Panel del ascensor
    rw, rh = 170, 56
    rx, ry = W // 2 - rw // 2, 8
    bg.alpha_composite(nine(sp("panel"), rw, rh), (rx, ry))
    d.text((W // 2, ry + 8), "ASCENSOR 1", fill=ACCENT + (255,), font=fs, anchor="mm")
    d.text((W // 2, ry + 21), "3/4", fill=(240, 224, 64, 255), font=fb, anchor="mm")
    for i in range(4):
        bg.alpha_composite(sp("pip_on" if i < 3 else "pip_off"), (W // 2 - 16 + i * 9, ry + 32))
    d.text((W // 2, ry + 47), "Pulsa el botón cuando estés listo", fill=(180, 196, 190, 255), font=fs, anchor="mm")

    bg = bg.resize((W * S, H * S), Image.NEAREST)
    bg.save(path)


if __name__ == "__main__":
    main()
