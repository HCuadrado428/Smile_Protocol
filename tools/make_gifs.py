#!/usr/bin/env python3
"""
Monta los GIF de las animaciones a partir de los fotogramas que graba la prueba en el cliente
(SMILEPROTOCOL_ANIMTEST=1): screenshots/anim-<nombre>-NNN.png -> <salida>/<nombre>.gif

    python3 tools/make_gifs.py <carpeta de capturas> <carpeta de salida>
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

from PIL import Image


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    groups = defaultdict(list)
    for p in src.glob("anim-*.png"):
        m = re.match(r"anim-(.+)-(\d+)\.png", p.name)
        if m:
            groups[m.group(1)].append((int(m.group(2)), p))
    for name, frames in sorted(groups.items()):
        frames.sort()
        ims = []
        for _, p in frames:
            im = Image.open(p).convert("RGB")
            w, h = im.size
            # recorte central 4:3 y a 480x360
            cw = int(h * 4 / 3)
            im = im.crop(((w - cw) // 2, 0, (w + cw) // 2, h)).resize((480, 360), Image.LANCZOS)
            ims.append(im)
        pal = ims[len(ims) // 2].quantize(colors=128, method=Image.Quantize.MEDIANCUT)
        q = [im.quantize(palette=pal, dither=Image.Dither.NONE) for im in ims]
        q[0].save(out / f"{name}.gif", save_all=True, append_images=q[1:], duration=50, loop=0, optimize=True)
        print(f"{name}: {len(ims)} fotogramas")


if __name__ == "__main__":
    main()
