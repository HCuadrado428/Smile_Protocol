#!/usr/bin/env python3
"""
Genera los blockstates, modelos de bloque e item, el modelo GeckoLib de la puerta y las
tablas de botin de los bloques del lobby. Las texturas las genera tools/textures.py.

    python3 tools/models.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
ASSETS = ROOT / "assets/smileprotocol"
DATA = ROOT / "data/smileprotocol"
MOD = "smileprotocol"

# Rotacion Y del blockstate para un modelo hecho mirando al sur
FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}
# Cara emisiva (Forge): se ve a plena luz aunque este a oscuras
GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
DIRS = ("north", "south", "east", "west", "up", "down")


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def tex(name):
    return f"{MOD}:block/{name}"


def block_model(name, obj):
    write(ASSETS / "models/block" / f"{name}.json", obj)


def item_from_block(name, model=None):
    write(ASSETS / "models/item" / f"{name}.json", {"parent": f"{MOD}:block/{model or name}"})


def facing_variants(model, extra=""):
    out = {}
    for f, y in FACING_Y.items():
        v = {"model": f"{MOD}:block/{model}"}
        if y:
            v["y"] = y
        out[f"facing={f}{extra}"] = v
    return out


def cube(name, texture=None):
    block_model(name, {"parent": "block/cube_all", "textures": {"all": tex(texture or name)}})
    write(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
    item_from_block(name)


def main():
    # Acabados de cabina
    cube("elevator_frame")
    cube("elevator_wall_panel")
    cube("elevator_floor")

    # Puerta: la dibuja GeckoLib, el modelo de bloque solo aporta las particulas
    block_model("elevator_door_particle", {"textures": {"particle": tex("elevator_frame")}})
    variants = {}
    for f in FACING_Y:
        for half in ("lower", "upper"):
            for open_ in ("false", "true"):
                variants[f"facing={f},half={half},open={open_}"] = {"model": f"{MOD}:block/elevator_door_particle"}
    write(ASSETS / "blockstates/elevator_door.json", {"variants": variants})
    write(ASSETS / "models/item/elevator_door.json",
          {"parent": "item/generated", "textures": {"layer0": f"{MOD}:item/elevator_door"}})

    # Modelo GeckoLib de la hoja (unidades UV 64x32; la textura es 128x64, a 2 px por unidad)
    write(ASSETS / "geo/block/elevator_door.geo.json", {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.elevator_door",
                "texture_width": 64, "texture_height": 32,
                "visible_bounds_width": 8, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0],
            },
            "bones": [
                {"name": "root", "pivot": [0, 0, 0]},
                {"name": "leaf", "parent": "root", "pivot": [0, 0, 0], "cubes": [{
                    "origin": [-8, 0, -1], "size": [16, 32, 2],
                    "uv": {
                        "south": {"uv": [0, 0], "uv_size": [16, 32]},
                        "north": {"uv": [16, 0], "uv_size": [16, 32]},
                        "east": {"uv": [32, 0], "uv_size": [2, 32]},
                        "west": {"uv": [34, 0], "uv_size": [2, 32]},
                        "up": {"uv": [36, 0], "uv_size": [16, 2]},
                        "down": {"uv": [36, 2], "uv_size": [16, 2]},
                    },
                }]},
            ],
        }],
    })
    write(ASSETS / "animations/block/elevator_door.animation.json",
          {"format_version": "1.8.0", "animations": {}})

    # Boton: placa con tornillos y una capa emisiva para el aro y el boton
    block_model("elevator_button", {
        "parent": "block/block",
        "render_type": "minecraft:cutout",
        "textures": {"plate": tex("elevator_button"), "glow": tex("elevator_button_glow"),
                     "side": tex("elevator_panel"), "particle": tex("elevator_panel")},
        "elements": [
            {"from": [2, 2, 0], "to": [14, 14, 1.5], "faces": {
                "south": {"texture": "#plate", "uv": [0, 0, 16, 16]},
                "north": {"texture": "#side", "uv": [2, 2, 14, 14]},
                "east": {"texture": "#side", "uv": [0, 2, 1.5, 14]},
                "west": {"texture": "#side", "uv": [0, 2, 1.5, 14]},
                "up": {"texture": "#side", "uv": [2, 0, 14, 1.5]},
                "down": {"texture": "#side", "uv": [2, 0, 14, 1.5]},
            }},
            {"from": [2, 2, 1.55], "to": [14, 14, 1.55], "shade": False, "faces": {
                "south": {"texture": "#glow", "uv": [0, 0, 16, 16], "forge_data": GLOW},
            }},
        ],
    })
    write(ASSETS / "blockstates/elevator_button.json", {"variants": facing_variants("elevator_button")})
    item_from_block("elevator_button")

    # Pantalla: placa fina con el cristal emisivo delante
    block_model("elevator_board", {
        "parent": "block/block",
        "textures": {"front": tex("elevator_screen"), "side": tex("elevator_panel"), "particle": tex("elevator_panel")},
        "elements": [{"from": [0, 2, 0], "to": [16, 14, 2], "faces": {
            "south": {"texture": "#front", "uv": [0, 2, 16, 14], "forge_data": GLOW},
            "north": {"texture": "#side", "uv": [0, 2, 16, 14]},
            "east": {"texture": "#side", "uv": [0, 2, 2, 14]},
            "west": {"texture": "#side", "uv": [0, 2, 2, 14]},
            "up": {"texture": "#side", "uv": [0, 0, 16, 2]},
            "down": {"texture": "#side", "uv": [0, 0, 16, 2]},
        }}],
    })
    write(ASSETS / "blockstates/elevator_board.json", {"variants": facing_variants("elevator_board")})
    item_from_block("elevator_board")

    # Fluorescente: encendido emisivo en todas las caras
    block_model("fluorescent_light_on", {
        "parent": "block/block",
        "ambientocclusion": False,
        "textures": {"all": tex("fluorescent_light_on"), "particle": tex("fluorescent_light_on")},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            d: {"texture": "#all", "cullface": d, "forge_data": GLOW} for d in DIRS}}],
    })
    block_model("fluorescent_light_off", {"parent": "block/cube_all", "textures": {"all": tex("fluorescent_light_off")}})
    write(ASSETS / "blockstates/fluorescent_light.json", {"variants": {
        f"flickers={fl},lit={lit}": {"model": f"{MOD}:block/fluorescent_light_{'on' if lit == 'true' else 'off'}"}
        for fl in ("false", "true") for lit in ("false", "true")}})
    item_from_block("fluorescent_light", "fluorescent_light_on")

    # El fluorescente se puede romper y se recupera
    write(DATA / "loot_tables/blocks/fluorescent_light.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:fluorescent_light"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })
    print("Modelos generados en", ASSETS)


if __name__ == "__main__":
    main()
