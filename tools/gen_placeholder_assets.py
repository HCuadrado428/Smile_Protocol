#!/usr/bin/env python3
"""
Genera las texturas provisionales (16x16) y los JSON de bloques del lobby.

Todas las texturas de este script son PROVISIONALES: se sustituyen por arte definitivo
cuando se quiera (README, seccion «Implementacion»). Ejecutar desde la raiz del repositorio:

    python3 tools/gen_placeholder_assets.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
ASSETS = ROOT / "assets/smileprotocol"
DATA = ROOT / "data/smileprotocol"
MOD = "smileprotocol"


def save_png(name, img):
    path = ASSETS / "textures/block" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def steel(seed, base=(150, 156, 160)):
    """Acero cepillado: vetas horizontales con ruido."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        row = rnd.randint(-8, 8)
        for x in range(16):
            n = row + rnd.randint(-5, 5)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))
    return img


# Texturas ----------------------------------------------------------------------

def door():
    img = steel(1)
    for y in range(16):
        img.putpixel((7, y), (96, 100, 104, 255))   # junta entre hojas
        img.putpixel((8, y), (186, 190, 194, 255))
    for x in range(16):
        img.putpixel((x, 0), (110, 114, 118, 255))
        img.putpixel((x, 15), (110, 114, 118, 255))
    return img


def panel():
    img = steel(2, (120, 126, 130))
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, (80, 84, 88, 255))
    return img


def button():
    img = panel()
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 3.2:
                img.putpixel((x, y), (60, 200, 110, 255) if d < 2.2 else (30, 120, 60, 255))
            elif d <= 4.2:
                img.putpixel((x, y), (60, 64, 68, 255))
    return img


def screen():
    rnd = random.Random(3)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = 10 + rnd.randint(0, 6) + (4 if y % 2 == 0 else 0)  # lineas de barrido
            img.putpixel((x, y), (v, v + 4, v + 2, 255))
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, (60, 66, 64, 255))
    return img


def fluorescent(on):
    img = Image.new("RGBA", (16, 16))
    frame = (170, 172, 168, 255)
    tube = (246, 250, 238, 255) if on else (120, 124, 120, 255)
    glow = (220, 236, 226, 255) if on else (96, 100, 98, 255)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), frame)
    for y in range(2, 14):
        for x in range(2, 14):
            img.putpixel((x, y), glow)
    for x in (4, 5, 10, 11):
        for y in range(2, 14):
            img.putpixel((x, y), tube)
    return img


# Modelos y blockstates -----------------------------------------------------------

FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}


def block_model(name, obj):
    write_json(ASSETS / "models/block" / f"{name}.json", obj)


def item_model(name, parent=None):
    write_json(ASSETS / "models/item" / f"{name}.json", {"parent": parent or f"{MOD}:block/{name}"})


def faces(tex, front=None, front_dir="south"):
    out = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        out[d] = {"texture": f"#{'front' if (front and d == front_dir) else 'side'}"}
    return out


def main():
    save_png("elevator_door", door())
    save_png("elevator_panel", panel())
    save_png("elevator_button", button())
    save_png("elevator_screen", screen())
    save_png("fluorescent_light_on", fluorescent(True))
    save_png("fluorescent_light_off", fluorescent(False))

    # Puerta: hoja fina centrada (2 px), el eje se gira segun FACING
    block_model("elevator_door", {
        "parent": "block/block",
        "textures": {"side": f"{MOD}:block/elevator_door", "particle": f"{MOD}:block/elevator_door"},
        "elements": [{"from": [0, 0, 7], "to": [16, 16, 9], "faces": faces("side")}],
    })
    variants = {}
    for facing, y in FACING_Y.items():
        for half in ("lower", "upper"):
            for open_ in ("false", "true"):
                v = {"model": f"{MOD}:block/elevator_door"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},half={half},open={open_}"] = v
    write_json(ASSETS / "blockstates/elevator_door.json", {"variants": variants})
    item_model("elevator_door")

    # Boton: panel en la pared y el boton saliente (modelo mirando al sur)
    block_model("elevator_button", {
        "parent": "block/block",
        "textures": {"side": f"{MOD}:block/elevator_panel", "front": f"{MOD}:block/elevator_button",
                     "particle": f"{MOD}:block/elevator_panel"},
        "elements": [
            {"from": [4, 3, 0], "to": [12, 13, 2], "faces": faces("side", front=True)},
            {"from": [6, 6, 2], "to": [10, 10, 3], "faces": {
                d: {"texture": "#front", "uv": [6, 6, 10, 10]} for d in ("north", "south", "east", "west", "up", "down")}},
        ],
    })
    write_json(ASSETS / "blockstates/elevator_button.json", {"variants": {
        f"facing={f}": ({"model": f"{MOD}:block/elevator_button", "y": y} if y else {"model": f"{MOD}:block/elevator_button"})
        for f, y in FACING_Y.items()}})
    item_model("elevator_button")

    # Pantalla: placa fina con la cara delantera negra (modelo mirando al sur)
    block_model("elevator_board", {
        "parent": "block/block",
        "textures": {"side": f"{MOD}:block/elevator_panel", "front": f"{MOD}:block/elevator_screen",
                     "particle": f"{MOD}:block/elevator_panel"},
        "elements": [{"from": [0, 2, 0], "to": [16, 14, 2], "faces": faces("side", front=True)}],
    })
    write_json(ASSETS / "blockstates/elevator_board.json", {"variants": {
        f"facing={f}": ({"model": f"{MOD}:block/elevator_board", "y": y} if y else {"model": f"{MOD}:block/elevator_board"})
        for f, y in FACING_Y.items()}})
    item_model("elevator_board")

    # Fluorescente
    for state in ("on", "off"):
        block_model(f"fluorescent_light_{state}", {
            "parent": "block/cube_all",
            "textures": {"all": f"{MOD}:block/fluorescent_light_{state}"},
        })
    write_json(ASSETS / "blockstates/fluorescent_light.json", {"variants": {
        f"flickers={fl},lit={lit}": {"model": f"{MOD}:block/fluorescent_light_{'on' if lit == 'true' else 'off'}"}
        for fl in ("false", "true") for lit in ("false", "true")}})
    item_model("fluorescent_light", f"{MOD}:block/fluorescent_light_on")

    # El fluorescente se puede romper y se recupera
    write_json(DATA / "loot_tables/blocks/fluorescent_light.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:fluorescent_light"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })
    print("Recursos provisionales generados en", ROOT)


if __name__ == "__main__":
    main()
