#!/usr/bin/env python3
"""
Blockstates, modelos y nombres de los bloques de construccion del laboratorio y de las
calcomanias (sangre y pintadas). Las texturas las genera tools/textures_lab.py; este script
cuenta cuantas variantes hay de cada bloque mirando los PNG.

    python3 tools/models_lab.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol"
MOD = "smileprotocol"
FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}

NAMES = {
    "lab_vent_duct": ("Conducto de ventilación", "Ventilation duct"),
    "vent_grille": ("Rejilla de ventilación", "Vent grille"),
    "vent_fan": ("Ventilador del conducto", "Duct fan"),
    "lab_tile_floor": ("Baldosa sucia", "Dirty floor tile"),
    "lab_tile_floor_dark": ("Baldosa de damero", "Checkered floor tile"),
    "lab_linoleum": ("Linóleo gastado", "Worn linoleum"),
    "lab_concrete_floor": ("Suelo de hormigón", "Concrete floor"),
    "lab_grate": ("Rejilla de chapa", "Metal grate"),
    "lab_wood_floor": ("Parqué podrido", "Rotten parquet"),
    "lab_morgue_floor": ("Baldosa de la morgue", "Morgue tile"),
    "lab_moss_floor": ("Baldosa con moho", "Mouldy tile"),
    "lab_plaster": ("Pared desconchada", "Peeling wall"),
    "lab_plaster_green": ("Pared verde hospital", "Hospital green wall"),
    "lab_plaster_blue": ("Pared azul desconchada", "Peeling blue wall"),
    "lab_wall_tiles": ("Azulejo blanco", "White wall tile"),
    "lab_wall_tiles_teal": ("Azulejo verde azulado", "Teal wall tile"),
    "lab_wall_tiles_white": ("Azulejo de metro", "Subway wall tile"),
    "lab_concrete_wall": ("Muro de hormigón", "Concrete wall"),
    "lab_metal_wall": ("Chapa remachada", "Riveted metal wall"),
    "lab_hazard": ("Franjas de peligro", "Hazard stripes"),
    "lab_ceiling": ("Falso techo", "Drop ceiling"),
    "lab_ceiling_concrete": ("Techo de hormigón", "Concrete ceiling"),
    "lab_crate": ("Caja de suministros", "Supply crate"),
    "lab_pipes": ("Tuberías oxidadas", "Rusty pipes"),
    "lab_server": ("Armario de servidores", "Server rack"),
    "lab_machine": ("Carcasa de generador", "Generator housing"),
    "lab_debris": ("Escombros", "Rubble"),
    "blood_floor": ("Charco de sangre", "Blood pool"),
    "wall_decal": ("Mancha en la pared", "Wall stain"),
    "wall_writing": ("Pintada de sangre", "Blood writing"),
}

# Suelos y techos se pueden girar al azar; las paredes no (los chorretones caen hacia abajo)
ROTATE = {"lab_tile_floor", "lab_tile_floor_dark", "lab_concrete_floor", "lab_morgue_floor", "lab_moss_floor",
          "lab_ceiling", "lab_ceiling_concrete", "lab_grate"}
FLIP = {"lab_linoleum", "lab_wood_floor"}
# Peso de cada variante (la placa caida del techo, poco)
WEIGHTS = {"lab_ceiling": [6, 6, 6, 1]}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def variants(name):
    tex = ROOT / "textures/block"
    n = 1
    while (tex / f"{name}_{n}.png").exists():
        n += 1
    return [name] + [f"{name}_{i}" for i in range(1, n)]


def building(name):
    vs = variants(name)
    out = []
    w = WEIGHTS.get(name, [1] * len(vs))
    for i, v in enumerate(vs):
        if name == "lab_crate":
            model = {"parent": "block/cube_bottom_top",
                     "textures": {"side": f"{MOD}:block/lab_crate", "top": f"{MOD}:block/lab_crate_top",
                                  "bottom": f"{MOD}:block/lab_crate_top"}}
        else:
            model = {"parent": "block/cube_all", "textures": {"all": f"{MOD}:block/{v}"}}
        write(ROOT / "models/block" / f"{v}.json", model)
        rots = (0, 90, 180, 270) if name in ROTATE else (0, 180) if name in FLIP else (0,)
        for y in rots:
            e = {"model": f"{MOD}:block/{v}"}
            if y:
                e["y"] = y
            if w[i] != 1:
                e["weight"] = w[i]
            out.append(e)
    write(ROOT / "blockstates" / f"{name}.json", {"variants": {"": out if len(out) > 1 else out[0]}})
    write(ROOT / "models/item" / f"{name}.json", {"parent": f"{MOD}:block/{name}"})


def debris():
    t = f"{MOD}:block/lab_debris"
    write(ROOT / "models/block/lab_debris_slab.json",
          {"parent": "block/slab", "textures": {"bottom": t, "top": t, "side": t}})
    write(ROOT / "models/block/lab_debris_slab_top.json",
          {"parent": "block/slab_top", "textures": {"bottom": t, "top": t, "side": t}})
    write(ROOT / "models/block/lab_debris_double.json", {"parent": "block/cube_all", "textures": {"all": t}})
    write(ROOT / "blockstates/lab_debris.json", {"variants": {
        "type=bottom": [{"model": f"{MOD}:block/lab_debris_slab", "y": y} for y in (0, 90, 180, 270)],
        "type=top": {"model": f"{MOD}:block/lab_debris_slab_top"},
        "type=double": {"model": f"{MOD}:block/lab_debris_double"}}})
    write(ROOT / "models/item/lab_debris.json", {"parent": f"{MOD}:block/lab_debris_slab"})


def flat(texture, uv=(0, 0, 16, 16), floor=False):
    """Lamina de 0.1 px. En el suelo mira arriba; en la pared, hecha mirando al sur."""
    if floor:
        el = {"from": [0, 0.1, 0], "to": [16, 0.1, 16],
              "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#d"}}}
    else:
        el = {"from": [0, 0, 0.1], "to": [16, 16, 0.1],
              "faces": {"south": {"uv": list(uv), "texture": "#d"}}}
    return {"ambientocclusion": False, "render_type": "minecraft:cutout",
            "textures": {"d": texture, "particle": texture}, "elements": [el]}


def decals():
    tex = ROOT / "textures/block"
    floors = sorted(int(m.group(1)) for p in tex.glob("blood_floor_*.png") if (m := re.match(r"blood_floor_(\d+)", p.stem)))
    walls = sorted(int(m.group(1)) for p in tex.glob("wall_decal_*.png") if (m := re.match(r"wall_decal_(\d+)", p.stem)))
    writes = sorted(int(m.group(1)) for p in tex.glob("wall_writing_*.png") if (m := re.match(r"wall_writing_(\d+)", p.stem)))
    assert len(floors) == 6 and len(walls) == 8 and len(writes) == 8, (floors, walls, writes)

    bs = {}
    for i in floors:
        write(ROOT / f"models/block/blood_floor_{i}.json", flat(f"{MOD}:block/blood_floor_{i}", floor=True))
        bs[f"variant={i}"] = [{"model": f"{MOD}:block/blood_floor_{i}", **({"y": y} if y else {})} for y in (0, 90, 180, 270)]
    write(ROOT / "blockstates/blood_floor.json", {"variants": bs})
    write(ROOT / "models/item/blood_floor.json", {"parent": "item/generated", "textures": {"layer0": f"{MOD}:block/blood_floor_0"}})

    bs = {}
    for i in walls:
        write(ROOT / f"models/block/wall_decal_{i}.json", flat(f"{MOD}:block/wall_decal_{i}"))
        for f, y in FACING_Y.items():
            bs[f"facing={f},variant={i}"] = {"model": f"{MOD}:block/wall_decal_{i}", **({"y": y} if y else {})}
    write(ROOT / "blockstates/wall_decal.json", {"variants": bs})
    write(ROOT / "models/item/wall_decal.json", {"parent": "item/generated", "textures": {"layer0": f"{MOD}:block/wall_decal_0"}})

    bs = {}
    for i in writes:
        for side, uv in (("l", (0, 0, 8, 16)), ("r", (8, 0, 16, 16))):
            write(ROOT / f"models/block/wall_writing_{i}_{side}.json", flat(f"{MOD}:block/wall_writing_{i}", uv))
        for f, y in FACING_Y.items():
            for right in ("false", "true"):
                side = "r" if right == "true" else "l"
                bs[f"facing={f},right={right},variant={i}"] = {"model": f"{MOD}:block/wall_writing_{i}_{side}",
                                                              **({"y": y} if y else {})}
    write(ROOT / "blockstates/wall_writing.json", {"variants": bs})
    write(ROOT / "models/item/wall_writing.json", {"parent": "item/generated", "textures": {"layer0": f"{MOD}:block/wall_writing_0"}})


def vents():
    """Rejilla del suelo (cerrada: chapa arriba; abierta: colgando dentro del hueco) y ventilador."""
    g = f"{MOD}:block/vent_grille"
    write(ROOT / "models/block/vent_grille_closed.json", {
        "parent": "block/block", "render_type": "cutout", "textures": {"g": g, "particle": g},
        "elements": [{"from": [0, 13, 0], "to": [16, 16, 16], "faces": {
            "up": {"texture": "#g"}, "down": {"texture": "#g"},
            "north": {"texture": "#g", "uv": [0, 0, 16, 3]}, "south": {"texture": "#g", "uv": [0, 0, 16, 3]},
            "east": {"texture": "#g", "uv": [0, 0, 16, 3]}, "west": {"texture": "#g", "uv": [0, 0, 16, 3]}}}]})
    write(ROOT / "models/block/vent_grille_open.json", {
        "parent": "block/block", "render_type": "cutout", "textures": {"g": g, "particle": g},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 3], "faces": {
            "north": {"texture": "#g"}, "south": {"texture": "#g"},
            "up": {"texture": "#g", "uv": [0, 0, 16, 3]}, "down": {"texture": "#g", "uv": [0, 0, 16, 3]},
            "east": {"texture": "#g", "uv": [0, 0, 3, 16]}, "west": {"texture": "#g", "uv": [0, 0, 3, 16]}}}]})
    bs = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for o in ("true", "false"):
            for sealed in ("true", "false"):
                e = {"model": f"{MOD}:block/vent_grille_{'open' if o == 'true' else 'closed'}"}
                if y:
                    e["y"] = y
                bs[f"facing={f},open={o},sealed={sealed}"] = e
    write(ROOT / "blockstates/vent_grille.json", {"variants": bs})
    write(ROOT / "models/item/vent_grille.json", {"parent": "item/generated", "textures": {"layer0": g}})
    d = f"{MOD}:block/lab_vent_duct"
    for run in (False, True):
        fan = f"{MOD}:block/vent_fan{'_spin' if run else ''}"
        write(ROOT / f"models/block/vent_fan{'_on' if run else '_off'}.json", {
            "parent": "block/block", "render_type": "cutout", "textures": {"fan": fan, "d": d, "particle": d},
            "elements": [
                {"from": [0, 0, 7], "to": [16, 16, 9], "faces": {"north": {"texture": "#fan"}, "south": {"texture": "#fan"}}},
                {"from": [0, 0, 0], "to": [1, 16, 16], "faces": {"east": {"texture": "#d"}, "west": {"texture": "#d"}}},
                {"from": [15, 0, 0], "to": [16, 16, 16], "faces": {"east": {"texture": "#d"}, "west": {"texture": "#d"}}}]})
    bs = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for run in ("true", "false"):
            e = {"model": f"{MOD}:block/vent_fan_{'on' if run == 'true' else 'off'}"}
            if y:
                e["y"] = y
            bs[f"facing={f},running={run}"] = e
    write(ROOT / "blockstates/vent_fan.json", {"variants": bs})
    write(ROOT / "models/item/vent_fan.json", {"parent": f"{MOD}:block/vent_fan_on"})


def lang():
    for fn, idx in (("es_es.json", 0), ("en_us.json", 1)):
        p = ROOT / "lang" / fn
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in NAMES.items():
            d[f"block.{MOD}.{k}"] = v[idx]
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    for name in NAMES:
        if name.startswith("lab_") and name != "lab_debris":
            building(name)
    debris()
    decals()
    vents()
    lang()
    print("ok")


if __name__ == "__main__":
    main()
