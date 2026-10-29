#!/usr/bin/env python3
"""
Texturas provisionales detalladas del lobby (pixel art procedural, 32x32 para bloques y
128x64 para la puerta animada de GeckoLib). Se pueden sustituir por arte definitivo.

    python3 tools/textures.py            # escribe las texturas
    python3 tools/textures.py preview.png  # ademas, una hoja de vista previa
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/smileprotocol/textures"


# Utilidades ----------------------------------------------------------------------

class Tex:
    """Lienzo RGBA en coma flotante (0-255)."""

    def __init__(self, w, h, color=(0, 0, 0), alpha=255):
        self.w, self.h = w, h
        self.rgb = np.zeros((h, w, 3), dtype=np.float64)
        self.rgb[:, :] = color
        self.a = np.full((h, w), float(alpha))

    def px(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.rgb[y, x] = c
            self.a[y, x] = a

    def blend(self, x, y, c, k):
        """Mezcla el color c con intensidad k (0-1)."""
        if 0 <= x < self.w and 0 <= y < self.h:
            self.rgb[y, x] = self.rgb[y, x] * (1 - k) + np.array(c, dtype=np.float64) * k

    def shade(self, x, y, d):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.rgb[y, x] = np.clip(self.rgb[y, x] + d, 0, 255)

    def rect(self, x0, y0, x1, y1, c, a=255):
        self.rgb[y0:y1 + 1, x0:x1 + 1] = c
        self.a[y0:y1 + 1, x0:x1 + 1] = a

    def image(self):
        out = np.dstack([np.clip(self.rgb, 0, 255), np.clip(self.a, 0, 255)]).round().astype(np.uint8)
        return Image.fromarray(out, "RGBA")

    def save(self, rel):
        path = ROOT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image().save(path)
        return path


def rng(seed):
    return np.random.default_rng(seed)


def smooth1d(v, k=3):
    kernel = np.ones(k) / k
    return np.convolve(np.pad(v, k, mode="wrap"), kernel, mode="same")[k:-k]


def brushed(t, base, seed, axis="h", strength=10, fine=4, x0=0, y0=0, x1=None, y1=None):
    """Acero cepillado: vetas largas en una direccion mas ruido fino."""
    x1 = t.w - 1 if x1 is None else x1
    y1 = t.h - 1 if y1 is None else y1
    r = rng(seed)
    n = (y1 - y0 + 1) if axis == "h" else (x1 - x0 + 1)
    streak = smooth1d(r.normal(0, 1, n), 2) * strength
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            s = streak[y - y0] if axis == "h" else streak[x - x0]
            t.rgb[y, x] = np.clip(np.array(base) + s + r.normal(0, fine), 0, 255)
            t.a[y, x] = 255


def bevel(t, x0, y0, x1, y1, light=28, dark=-34, width=1):
    """Borde en relieve: luz arriba e izquierda, sombra abajo y derecha."""
    for i in range(width):
        for x in range(x0 + i, x1 - i + 1):
            t.shade(x, y0 + i, light)
            t.shade(x, y1 - i, dark)
        for y in range(y0 + i, y1 - i + 1):
            t.shade(x0 + i, y, light)
            t.shade(x1 - i, y, dark)


def groove(t, x0, y0, x1, y1):
    """Ranura grabada: linea oscura con brillo debajo/derecha."""
    if y0 == y1:
        for x in range(x0, x1 + 1):
            t.shade(x, y0, -45)
            t.shade(x, y0 + 1, 22)
    else:
        for y in range(y0, y1 + 1):
            t.shade(x0, y, -45)
            t.shade(x0 + 1, y, 22)


def rivet(t, x, y):
    t.blend(x, y, (232, 236, 238), 0.9)
    t.blend(x + 1, y, (176, 180, 184), 0.9)
    t.blend(x, y + 1, (150, 154, 158), 0.9)
    t.blend(x + 1, y + 1, (70, 74, 78), 0.9)
    t.shade(x + 2, y + 1, -18)
    t.shade(x + 1, y + 2, -18)


def screw(t, cx, cy):
    """Tornillo de 3x3 con ranura."""
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if abs(dx) + abs(dy) == 2:
                continue
            t.blend(cx + dx, cy + dy, (190, 194, 198), 0.95)
    t.blend(cx - 1, cy, (80, 84, 88), 0.9)
    t.blend(cx, cy, (70, 74, 78), 0.9)
    t.blend(cx + 1, cy, (80, 84, 88), 0.9)
    t.shade(cx - 1, cy - 1, 30)
    t.shade(cx + 1, cy + 1, -40)


def blotches(w, h, seed, scale=6):
    """Ruido suave (0-1) a baja frecuencia, interpolado: manchas en vez de puntos."""
    r = rng(seed)
    gw, gh = w // scale + 2, h // scale + 2
    grid = r.random((gh, gw))
    out = np.zeros((h, w))
    for y in range(h):
        for x in range(w):
            gx, gy = x / scale, y / scale
            x0, y0 = int(gx), int(gy)
            fx, fy = gx - x0, gy - y0
            fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            a = grid[y0, x0] * (1 - fx) + grid[y0, x0 + 1] * fx
            b = grid[y0 + 1, x0] * (1 - fx) + grid[y0 + 1, x0 + 1] * fx
            out[y, x] = a * (1 - fy) + b * fy
    return out


def grime(t, seed, amount=0.35, color=(70, 58, 44), y_start=0.55):
    """Suciedad que se acumula abajo, en manchas (laboratorio abandonado)."""
    field = blotches(t.w, t.h, seed, max(3, t.w // 6))
    fine = rng(seed + 7)
    for y in range(t.h):
        f = max(0.0, (y / t.h - y_start) / (1 - y_start))
        for x in range(t.w):
            if t.a[y, x] <= 0:
                continue
            k = max(0.0, field[y, x] - 0.45) * 1.8 * f * amount * 2.2
            k += fine.random() * 0.06 * f
            if k > 0:
                t.blend(x, y, color, min(0.55, k))


def scratches(t, seed, n=4, x0=0, y0=0, x1=None, y1=None):
    x1 = t.w - 1 if x1 is None else x1
    y1 = t.h - 1 if y1 is None else y1
    r = rng(seed)
    for _ in range(n):
        x, y = r.integers(x0, x1), r.integers(y0, y1)
        length = r.integers(3, 7)
        dx = r.choice([-1, 1])
        for i in range(length):
            t.shade(x + i * dx, y + i // 2, 26)


def diamond_plate(t, base, seed, x0=0, y0=0, x1=None, y1=None, cell=8):
    """Chapa estriada: rombos en relieve alternando orientacion."""
    x1 = t.w - 1 if x1 is None else x1
    y1 = t.h - 1 if y1 is None else y1
    brushed(t, base, seed, "h", 5, 4, x0, y0, x1, y1)
    for cy in range(y0, y1 + 1, cell):
        for cx in range(x0, x1 + 1, cell):
            flip = ((cx - x0) // cell + (cy - y0) // cell) % 2
            for i in range(4):
                if flip:
                    px, py = cx + 2 + i, cy + 1 + i
                else:
                    px, py = cx + 5 - i, cy + 1 + i
                if x0 <= px <= x1 and y0 <= py <= y1:
                    t.shade(px, py, 38)
                    t.shade(px + 1, py, 14)
                    t.shade(px, py + 1, -34)


def disc(t, cx, cy, r, fn):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if d <= r:
                fn(x, y, d)


# Texturas -------------------------------------------------------------------------

STEEL = (150, 156, 162)


def elevator_frame():
    t = Tex(32, 32)
    brushed(t, (160, 165, 170), 11, "v", 9, 3)
    bevel(t, 0, 0, 31, 31, 36, -46, 2)
    groove(t, 5, 5, 26, 5)
    groove(t, 5, 26, 26, 26)
    groove(t, 5, 5, 5, 26)
    groove(t, 26, 5, 26, 26)
    for x, y in ((2, 2), (28, 2), (2, 28), (28, 28)):
        rivet(t, x, y)
    grime(t, 13, 0.2, (66, 58, 48), 0.5)
    return t


def elevator_wall_panel():
    t = Tex(32, 32)
    brushed(t, (138, 144, 150), 21, "h", 7, 3)
    # Junta vertical entre paneles y su brillo
    for y in range(32):
        t.shade(0, y, -40)
        t.shade(1, y, 18)
        t.shade(31, y, -22)
    # Paneles horizontales
    groove(t, 2, 15, 30, 15)
    for x, y in ((3, 2), (27, 2), (3, 28), (27, 28)):
        rivet(t, x, y)
    grime(t, 22, 0.25, (64, 56, 46), 0.65)
    scratches(t, 23, 5, 3, 3, 28, 28)
    return t


def elevator_floor():
    t = Tex(32, 32)
    diamond_plate(t, (104, 108, 112), 31)
    grime(t, 32, 0.22, (58, 50, 40), 0.0)
    return t


def elevator_panel():
    t = Tex(32, 32)
    brushed(t, (96, 101, 106), 41, "h", 6, 3)
    bevel(t, 0, 0, 31, 31, 24, -30, 1)
    return t


def elevator_button():
    """Placa con un boton redondo, aro iluminado y flecha de bajada."""
    t = Tex(32, 32)
    brushed(t, (168, 173, 178), 51, "h", 8, 3)
    bevel(t, 0, 0, 31, 31, 34, -44, 2)
    for x, y in ((4, 4), (27, 4), (4, 27), (27, 27)):
        screw(t, x, y)
    cx, cy = 16, 18
    # Bisel cromado
    disc(t, cx, cy, 8.2, lambda x, y, d: t.blend(x, y, (95, 100, 106), 0.9))
    disc(t, cx, cy, 7.4, lambda x, y, d: t.blend(
        x, y, (205, 210, 214) if (x - cx) + (y - cy) < 0 else (120, 125, 130), 0.9))
    # Aro de luz (apagado en la textura base; el brillo va en la capa _glow)
    disc(t, cx, cy, 6.2, lambda x, y, d: t.blend(x, y, (120, 86, 30), 0.95))
    disc(t, cx, cy, 5.0, lambda x, y, d: t.blend(x, y, (60, 64, 68), 1.0))
    # Capuchon del boton
    disc(t, cx, cy, 4.2, lambda x, y, d: t.blend(x, y, (64, 150, 96), 1.0))
    disc(t, cx - 1.2, cy - 1.2, 1.6, lambda x, y, d: t.blend(x, y, (170, 236, 196), 0.8))
    # Flecha hacia abajo grabada
    for i, w in enumerate((3, 2, 1, 0)):
        for dx in range(-w, w + 1):
            t.blend(cx + dx - 1 + 1, 6 + i, (60, 64, 68), 0.85)
    return t


def elevator_button_glow():
    """Capa emisiva del boton: solo el aro y el capuchon, el resto transparente."""
    t = Tex(32, 32, alpha=0)
    cx, cy = 16, 18
    disc(t, cx, cy, 6.2, lambda x, y, d: t.px(x, y, (255, 178, 64)) if d > 5.0 else None)
    disc(t, cx, cy, 4.2, lambda x, y, d: t.px(x, y, (88, 255, 150) if d > 1.4 else (210, 255, 226)))
    return t


def elevator_screen():
    """Monitor viejo: marco de plastico con tornillos y cristal con fosforo verde."""
    t = Tex(32, 32)
    brushed(t, (38, 41, 44), 61, "h", 3, 2)
    bevel(t, 0, 0, 31, 31, 22, -16, 1)
    for x, y in ((2, 2), (29, 2), (2, 29), (29, 29)):
        screw(t, x, y)
    r = rng(62)
    x0, y0, x1, y1 = 3, 4, 28, 27
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            # Vineta en las esquinas
            dx = (x - (x0 + x1) / 2) / ((x1 - x0) / 2)
            dy = (y - (y0 + y1) / 2) / ((y1 - y0) / 2)
            v = 1 - 0.55 * min(1, (dx * dx + dy * dy) / 1.6)
            line = 1.25 if y % 2 == 0 else 0.85
            g = (10 + r.normal(0, 2)) * v * line
            t.rgb[y, x] = (g * 0.55, g * 1.7 + 6 * v, g * 1.05)
    # Reflejo diagonal tenue
    for i in range(14):
        t.blend(x0 + 4 + i, y0 + 1 + i // 2, (90, 120, 104), 0.18)
        t.blend(x0 + 5 + i, y0 + 1 + i // 2, (90, 120, 104), 0.10)
    # Marco interior del cristal
    bevel(t, x0 - 1, y0 - 1, x1 + 1, y1 + 1, -14, 20, 1)
    # Piloto de encendido
    t.px(26, 30, (90, 230, 120))
    t.px(27, 30, (60, 170, 90))
    return t


def fluorescent(on):
    """Pantalla de techo con rejilla parabolica (4 x 2 celdas) y tubos detras."""
    t = Tex(32, 32)
    brushed(t, (196, 198, 194), 71, "h", 5, 2)
    bevel(t, 0, 0, 31, 31, 24, -36, 2)
    x0, y0, x1, y1 = 3, 3, 28, 28
    cols, rows = 4, 2
    cw = (x1 - x0 + 1) / cols
    rh = (y1 - y0 + 1) / rows
    r = rng(72 if on else 73)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cx = (x - x0) % cw
            cy = (y - y0) % rh
            wall = cx < 1 or cy < 1
            if wall:
                t.rgb[y, x] = (214, 216, 212) if on else (150, 152, 150)
                continue
            # Centro de la celda mas brillante (el tubo pasa por debajo, en horizontal)
            fy = abs(cy - rh / 2) / (rh / 2)
            fx = abs(cx - cw / 2) / (cw / 2)
            k = 1 - 0.45 * fy - 0.15 * fx
            if on:
                base = np.array((228, 242, 232)) * k + np.array((255, 255, 248)) * (1 - fy) * 0.25
                t.rgb[y, x] = np.clip(base + r.normal(0, 1.5), 0, 255)
            else:
                t.rgb[y, x] = np.clip(np.array((92, 98, 96)) * k + r.normal(0, 3), 0, 255)
    # Bordes de la rejilla en relieve
    for i in range(1, cols):
        x = int(round(x0 + i * cw))
        for y in range(y0, y1 + 1):
            t.shade(x + 1, y, -20 if on else -14)
    for i in range(1, rows):
        y = int(round(y0 + i * rh))
        for x in range(x0, x1 + 1):
            t.shade(x, y + 1, -20 if on else -14)
    if not on:
        grime(t, 74, 0.2, (80, 72, 60), 0.4)
    return t


def elevator_door_geo():
    """
    Textura de la hoja de puerta (GeckoLib). Unidades UV 64x32 a 2 px por unidad (128x64).
      frontal  u 0-16   v 0-32  -> px   0-31, 0-63
      trasera  u 16-32  v 0-32  -> px  32-63
      lado E   u 32-34            -> px  64-67
      lado O   u 34-36            -> px  68-71
      arriba   u 36-52  v 0-2   -> px  72-103, 0-3
      abajo    u 36-52  v 2-4   -> px  72-103, 4-7
    """
    t = Tex(128, 64, alpha=0)

    def leaf(x0, seed, outside):
        x1 = x0 + 31
        brushed(t, (172, 176, 181) if outside else (150, 155, 160), seed, "v", 10, 3, x0, 0, x1, 63)
        bevel(t, x0, 0, x1, 63, 22, -34, 1)
        # Juntas de goma en los cantos verticales
        for y in range(64):
            for x in (x0 + 1, x1 - 1):
                t.blend(x, y, (34, 34, 36), 0.85)
        # Paneles
        groove(t, x0 + 3, 18, x1 - 3, 18)
        groove(t, x0 + 3, 40, x1 - 3, 40)
        if outside:
            # Banda de aviso amarilla y negra
            for y in range(47, 51):
                for x in range(x0 + 3, x1 - 2):
                    stripe = ((x - x0) + y) // 3 % 2 == 0
                    t.px(x, y, (214, 172, 38) if stripe else (30, 30, 30))
            for x in range(x0 + 3, x1 - 2):
                t.shade(x, 46, -40)
                t.shade(x, 51, 24)
        # Zocalo de chapa estriada
        diamond_plate(t, (112, 116, 120), seed + 1, x0 + 2, 55, x1 - 2, 62, cell=6)
        groove(t, x0 + 2, 54, x1 - 2, 54)
        scratches(t, seed + 2, 6, x0 + 3, 3, x1 - 3, 52)
        sub = Tex(32, 64)
        sub.rgb = t.rgb[:, x0:x1 + 1].copy()
        sub.a = t.a[:, x0:x1 + 1].copy()
        grime(sub, seed + 3, 0.30, (62, 54, 42), 0.6)
        t.rgb[:, x0:x1 + 1] = sub.rgb

    leaf(0, 81, True)
    leaf(32, 91, False)
    # Cantos: goma negra
    for x in range(64, 72):
        for y in range(64):
            t.px(x, y, (36, 36, 38))
            if y % 6 == 0:
                t.shade(x, y, 14)
    # Arriba y abajo: acero
    brushed(t, (140, 145, 150), 95, "h", 6, 3, 72, 0, 103, 7)
    return t


def elevator_door_icon():
    """Icono del item de la puerta (dos hojas cerradas con marco)."""
    t = Tex(32, 32, alpha=0)
    brushed(t, (160, 165, 170), 101, "v", 8, 3, 4, 1, 27, 30)
    bevel(t, 4, 1, 27, 30, 30, -40, 1)
    for y in range(2, 30):
        t.blend(15, y, (40, 40, 42), 0.9)
        t.blend(16, y, (200, 204, 208), 0.6)
    for y in range(22, 24):
        for x in range(5, 27):
            t.px(x, y, (214, 172, 38) if (x + y) // 2 % 2 == 0 else (30, 30, 30))
    return t


ALL = {
    "block/elevator_frame.png": elevator_frame,
    "block/elevator_wall_panel.png": elevator_wall_panel,
    "block/elevator_floor.png": elevator_floor,
    "block/elevator_panel.png": elevator_panel,
    "block/elevator_button.png": elevator_button,
    "block/elevator_button_glow.png": elevator_button_glow,
    "block/elevator_screen.png": elevator_screen,
    "block/fluorescent_light_on.png": lambda: fluorescent(True),
    "block/fluorescent_light_off.png": lambda: fluorescent(False),
    "block/elevator_door.png": elevator_door_geo,
    "item/elevator_door.png": elevator_door_icon,
}


def preview(path):
    from PIL import ImageDraw, ImageFont
    items = [(k, Image.open(ROOT / k).convert("RGBA")) for k in ALL]
    S, pad, lab = 192, 22, 30
    cols = 4
    rows = (len(items) + cols - 1) // cols
    W = cols * (S + pad) + pad
    H = rows * (S + pad + lab) + pad
    sheet = Image.new("RGBA", (W, H), (28, 32, 30, 255))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 14)
    except OSError:
        font = ImageFont.load_default()
    for i, (name, img) in enumerate(items):
        x = pad + (i % cols) * (S + pad)
        y = pad + (i // cols) * (S + pad + lab)
        # Fondo de cuadros para ver la transparencia
        for cy in range(0, S, 12):
            for cx in range(0, S, 12):
                c = (52, 56, 54, 255) if (cx + cy) // 12 % 2 else (40, 44, 42, 255)
                d.rectangle([x + cx, y + cy, x + cx + 11, y + cy + 11], fill=c)
        scale = min(S / img.width, S / img.height)
        im = img.resize((int(img.width * scale), int(img.height * scale)), Image.NEAREST)
        sheet.alpha_composite(im, (x + (S - im.width) // 2, y + (S - im.height) // 2))
        d.text((x, y + S + 8), f"{name.split('/')[-1][:-4]} ({img.width}x{img.height})",
               fill=(216, 228, 222, 255), font=font)
    sheet.save(path)


def main():
    for rel, fn in ALL.items():
        fn().save(rel)
    print("Texturas escritas en", ROOT)
    if len(sys.argv) > 1:
        preview(sys.argv[1])
        print("Vista previa:", sys.argv[1])


if __name__ == "__main__":
    main()
