#!/usr/bin/env python3
"""
Modelos, blockstates y datos de los mecanismos y escondites (fases 9 y 10).

    python3 tools/models_mech.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources"
ASSETS = ROOT / "assets/smileprotocol"
DATA = ROOT / "data/smileprotocol"
MOD = "smileprotocol"
FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}
GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def tex(n):
    return f"{MOD}:block/{n}"


def model(name, obj):
    write(ASSETS / "models/block" / f"{name}.json", obj)


def item_block(name, model_name=None):
    write(ASSETS / "models/item" / f"{name}.json", {"parent": f"{MOD}:block/{model_name or name}"})


def item_flat(name, texture=None):
    write(ASSETS / "models/item" / f"{name}.json",
          {"parent": "item/generated", "textures": {"layer0": f"{MOD}:item/{texture or name}"}})


def blockstate(name, variants):
    write(ASSETS / "blockstates" / f"{name}.json", {"variants": variants})


def v(model_name, y=0):
    out = {"model": f"{MOD}:block/{model_name}"}
    if y:
        out["y"] = y
    return out


# Caja de fusibles ---------------------------------------------------------------------------------

def fuse_box():
    for filled in range(5):
        for powered in (False, True):
            name = f"fuse_box_{filled}_{'on' if powered else 'off'}"
            lamp = {"texture": "#lamp", "uv": [0, 0, 16, 16]}
            if powered:
                lamp["neoforge_data"] = GLOW
            model(name, {
                "parent": "block/block",
                "render_type": "minecraft:cutout",
                "textures": {"front": tex(f"fuse_box_front_{filled}"), "side": tex("fuse_box_side"),
                             "lamp": tex(f"fuse_box_lamp_{'on' if powered else 'off'}"), "particle": tex("fuse_box_side")},
                "elements": [
                    {"from": [2, 1, 0], "to": [14, 15, 4], "faces": {
                        "south": {"texture": "#front", "uv": [0, 0, 16, 16]},
                        "north": {"texture": "#side", "uv": [2, 1, 14, 15], "cullface": "north"},
                        "east": {"texture": "#side", "uv": [0, 1, 4, 15]},
                        "west": {"texture": "#side", "uv": [12, 1, 16, 15]},
                        "up": {"texture": "#side", "uv": [2, 0, 14, 4]},
                        "down": {"texture": "#side", "uv": [2, 12, 14, 16]}}},
                    {"from": [11.5, 12.5, 4], "to": [13, 14, 4.6], "faces": {
                        "south": lamp, "up": {"texture": "#lamp"}, "east": {"texture": "#lamp"},
                        "west": {"texture": "#lamp"}, "down": {"texture": "#lamp"}}},
                ],
            })
    variants = {}
    for f, y in FACING_Y.items():
        for filled in range(5):
            for powered in ("false", "true"):
                variants[f"facing={f},filled={filled},powered={powered}"] = v(
                    f"fuse_box_{filled}_{'on' if powered == 'true' else 'off'}", y)
    blockstate("fuse_box", variants)
    item_block("fuse_box", "fuse_box_2_off")


# Puertas (como las vanilla) ------------------------------------------------------------------------

def door(name):
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                 "top_left", "top_left_open", "top_right", "top_right_open"):
        model(f"{name}_{part}", {"parent": f"minecraft:block/door_{part}", "render_type": "minecraft:cutout",
                                  "textures": {"bottom": tex(f"{name}_bottom"), "top": tex(f"{name}_top")}})
    rot = {  # facing: (cerrada, izquierda abierta, derecha abierta)
        "east": (0, 90, 270), "north": (270, 0, 180), "south": (90, 180, 0), "west": (180, 270, 90)}
    variants = {}
    for f, (closed, lo, ro) in rot.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                variants[f"facing={f},half={half},hinge={hinge},open=false"] = v(f"{name}_{part}_{hinge}", closed)
                variants[f"facing={f},half={half},hinge={hinge},open=true"] = v(
                    f"{name}_{part}_{hinge}_open", lo if hinge == "left" else ro)
    blockstate(name, variants)
    item_flat(name)


# Valvula ---------------------------------------------------------------------------------------------

def valve():
    model("valve", {
        "parent": "block/block",
        "render_type": "minecraft:cutout",
        "textures": {"base": tex("valve_base"), "pipe": tex("fuse_box_side"), "particle": tex("valve_base")},
        "elements": [
            {"from": [3, 3, 0], "to": [13, 13, 2], "faces": {
                "south": {"texture": "#base", "uv": [2, 2, 14, 14]}, "north": {"texture": "#base", "cullface": "north"},
                "east": {"texture": "#pipe"}, "west": {"texture": "#pipe"}, "up": {"texture": "#pipe"}, "down": {"texture": "#pipe"}}},
            {"from": [6.5, 6.5, 2], "to": [9.5, 9.5, 7], "faces": {
                "south": {"texture": "#pipe"}, "east": {"texture": "#pipe"}, "west": {"texture": "#pipe"},
                "up": {"texture": "#pipe"}, "down": {"texture": "#pipe"}}},
        ],
    })
    variants = {}
    for f, y in FACING_Y.items():
        for a in ("false", "true"):
            for b in ("false", "true"):
                for c in ("false", "true"):
                    variants[f"facing={f},turned={a},turning={b},wheel={c}"] = v("valve", y)
    blockstate("valve", variants)
    item_block("valve")
    # La rueda es un objeto plano con algo de grosor (la dibuja ValveRenderer)
    write(ASSETS / "models/item/valve_wheel.json", {
        "parent": "item/generated", "textures": {"layer0": f"{MOD}:item/valve_wheel"},
        "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 1.6]}}})


# Escondites ------------------------------------------------------------------------------------------

def tall_hide(name):
    for half in ("lower", "upper"):
        for opened in (False, True):
            front = tex(f"{name}{'_open' if opened else ''}_{half}")
            faces = {
                "south": {"texture": "#front"},
                "north": {"texture": "#side", "cullface": "north"},
                "east": {"texture": "#side", "cullface": "east"},
                "west": {"texture": "#side", "cullface": "west"},
            }
            if half == "upper":
                faces["up"] = {"texture": "#top", "cullface": "up"}
            else:
                faces["down"] = {"texture": "#top", "cullface": "down"}
            model(f"{name}_{half}{'_open' if opened else ''}", {
                "parent": "block/block",
                "textures": {"front": front, "side": tex(f"{name}_side"), "top": tex(f"{name}_top"),
                             "particle": tex(f"{name}_side")},
                "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}],
            })
    variants = {}
    for f, y in FACING_Y.items():
        for half in ("lower", "upper"):
            for o in ("false", "true"):
                variants[f"facing={f},half={half},open={o}"] = v(f"{name}_{half}{'_open' if o == 'true' else ''}", y)
    blockstate(name, variants)
    # Icono: la mitad de arriba en pequeno
    write(ASSETS / "models/item" / f"{name}.json", {"parent": f"{MOD}:block/{name}_upper"})


def gurney():
    leg = {"texture": "#frame"}
    elements = [
        {"from": [0, 10, 0], "to": [16, 12, 16], "faces": {
            "up": {"texture": "#top"}, "down": {"texture": "#frame"},
            "north": {"texture": "#frame"}, "south": {"texture": "#frame"},
            "east": {"texture": "#frame"}, "west": {"texture": "#frame"}}},
        {"from": [0.5, 12, 0.5], "to": [15.5, 13, 15.5], "faces": {
            "up": {"texture": "#top"}, "north": {"texture": "#top"}, "south": {"texture": "#top"},
            "east": {"texture": "#top"}, "west": {"texture": "#top"}}},
    ]
    for x, z in ((0, 0), (15, 0), (0, 15), (15, 15)):
        elements.append({"from": [x, 1, z], "to": [x + 1, 10, z + 1], "faces": {
            "north": leg, "south": leg, "east": leg, "west": leg}})
        elements.append({"from": [x - 0.5 if x else 0, 0, z - 0.5 if z else 0],
                         "to": [x + 1.5 if not x else 16, 1.5, z + 1.5 if not z else 16],
                         "faces": {"north": leg, "south": leg, "east": leg, "west": leg, "up": leg, "down": leg}})
    model("gurney", {"parent": "block/block", "render_type": "minecraft:cutout",
                     "textures": {"top": tex("gurney_top"), "frame": tex("gurney_frame"), "particle": tex("gurney_frame")},
                     "elements": elements})
    variants = {}
    for f, y in FACING_Y.items():
        for half in ("lower", "upper"):
            for o in ("false", "true"):
                variants[f"facing={f},half={half},open={o}"] = v("gurney", y)
    blockstate("gurney", variants)
    item_block("gurney")


def curtain():
    model("curtain", {"parent": "block/block", "render_type": "minecraft:cutout",
                      "textures": {"c": tex("curtain"), "particle": tex("curtain")},
                      "elements": [{"from": [0, 0, 7.5], "to": [16, 16, 8.5], "faces": {
                          "north": {"texture": "#c"}, "south": {"texture": "#c"},
                          "east": {"texture": "#c", "uv": [7, 0, 8, 16]}, "west": {"texture": "#c", "uv": [7, 0, 8, 16]}}}]})
    variants = {}
    for f, y in FACING_Y.items():
        for half in ("lower", "upper"):
            for o in ("false", "true"):
                variants[f"facing={f},half={half},open={o}"] = v("curtain", y)
    blockstate("curtain", variants)
    write(ASSETS / "models/item/curtain.json", {"parent": "item/generated", "textures": {"layer0": tex("curtain")}})


# Datos -------------------------------------------------------------------------------------------------

HIDES = {
    # README 15.1 y 15.2
    "locker": dict(capacity=1, enterTime=16, exitTime=14, noiseOnEnter=6, viewCone=70, breathDrain=0.12,
                   searchPriority=1.5, canBeLocked=False, overlay="slits"),
    "wardrobe": dict(capacity=2, enterTime=20, exitTime=16, noiseOnEnter=8, viewCone=80, breathDrain=0.12,
                     searchPriority=1.2, canBeLocked=False, overlay="crack"),
    "gurney": dict(capacity=1, enterTime=14, exitTime=16, noiseOnEnter=3, viewCone=120, breathDrain=0.14,
                   searchPriority=0.8, low=True, overlay="low"),
    "curtain": dict(capacity=1, enterTime=8, exitTime=8, noiseOnEnter=2, viewCone=140, breathDrain=0,
                    searchPriority=0.6, partial=True, overlay="cloth"),
    "container": dict(capacity=1, enterTime=18, exitTime=16, noiseOnEnter=3, viewCone=60, breathDrain=0.1,
                      searchPriority=1.0, overlay="crack"),
}


def data():
    for k, o in HIDES.items():
        write(DATA / "hiding_spots" / f"{k}.json", {**o, "animationId": k, "soundSet": k})
    for name in ("fuse_box", "lab_door", "security_door", "valve", "locker", "wardrobe", "waste_container",
                 "gurney", "curtain"):
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}]}
        if name in ("lab_door", "security_door", "locker", "wardrobe", "waste_container", "curtain"):
            pool["conditions"] = [{"condition": "minecraft:block_state_property", "block": f"{MOD}:{name}",
                                   "properties": {"half": "lower"}}]
        write(DATA / "loot_table/blocks" / f"{name}.json", {"type": "minecraft:block", "pools": [pool]})


def items():
    for n in ("fuse", "fuse_blown", "key", "keycard_1", "keycard_2", "keycard_3"):
        item_flat(n)


def geckolib(name, props):
    """Bloque dibujado por GeckoLib: el blockstate solo aporta las particulas."""
    model(f"{name}_particle", {"textures": {"particle": tex(name)}})
    import itertools
    keys = list(props)
    variants = {}
    for combo in itertools.product(*[props[k] for k in keys]):
        variants[",".join(f"{k}={v}" for k, v in zip(keys, combo))] = v(f"{name}_particle")
    blockstate(name, variants)
    item_flat(name)


def main():
    F = ["north", "south", "east", "west"]
    B = ["false", "true"]
    geckolib("fuse_box", {"facing": F, "filled": [str(i) for i in range(5)], "powered": B})
    for d in ("lab_door", "security_door"):
        geckolib(d, {"facing": F, "half": ["lower", "upper"], "hinge": ["left", "right"], "open": B, "powered": B})
    geckolib("valve", {"facing": F, "turned": B, "turning": B, "wheel": B})
    for h in ("locker", "wardrobe", "waste_container", "gurney", "curtain"):
        geckolib(h, {"facing": F, "half": ["lower", "upper"], "open": B})
    write(ASSETS / "models/item/valve_wheel.json", {
        "parent": "item/generated", "textures": {"layer0": f"{MOD}:item/valve_wheel"}})
    data()
    items()
    print("Modelos de mecanismos generados")


if __name__ == "__main__":
    main()
