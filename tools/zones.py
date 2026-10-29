#!/usr/bin/env python3
"""
Genera las zonas PROVISIONALES de la fase 6 (plantillas NBT + definiciones JSON) para probar
partidas, grupos e instancias hasta que se disene el mapa (README 22).

Cada plantilla lleva sus marcadores como bloques de estructura en modo DATA:
    sp:entry <yaw>   donde aparece el grupo
    sp:exit_cabin    dos esquinas de la cabina de salida
    sp:mob_spawn     puntos de aparicion de mobs (se escalan por tamano de grupo)
    sp:fuse          huecos de fusible (se escalan por tamano de grupo)

    pip install nbtlib
    python3 tools/zones.py
"""
import json
import random
from pathlib import Path

from nbtlib import Compound, File, Int, List, String

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/data/smileprotocol"
DATA_VERSION = 3955  # Minecraft 1.21.1
MOD = "smileprotocol"


class Template:
    def __init__(self, name):
        self.name = name
        self.blocks = {}
        self.markers = []

    def set(self, x, y, z, block, **props):
        if block == "air":
            self.blocks.pop((x, y, z), None)
            return
        if ":" not in block:
            block = "minecraft:" + block
        self.blocks[(x, y, z)] = (block, tuple(sorted((k, str(v).lower()) for k, v in props.items())))

    def fill(self, x1, y1, z1, x2, y2, z2, block, **props):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, block, **props)

    def clear(self, x1, y1, z1, x2, y2, z2):
        self.fill(x1, y1, z1, x2, y2, z2, "air")

    def marker(self, x, y, z, meta):
        self.blocks.pop((x, y, z), None)
        self.markers.append(((x, y, z), meta))

    def save(self):
        coords = list(self.blocks) + [m[0] for m in self.markers]
        minx = min(c[0] for c in coords); miny = min(c[1] for c in coords); minz = min(c[2] for c in coords)
        maxx = max(c[0] for c in coords); maxy = max(c[1] for c in coords); maxz = max(c[2] for c in coords)
        palette, index, blocks = [], {}, []

        def state_id(key):
            if key not in index:
                name, props = key
                entry = Compound({"Name": String(name)})
                if props:
                    entry["Properties"] = Compound({k: String(v) for k, v in props})
                index[key] = len(palette)
                palette.append(entry)
            return index[key]

        for (x, y, z), key in self.blocks.items():
            blocks.append(Compound({"pos": List[Int]([Int(x - minx), Int(y - miny), Int(z - minz)]),
                                    "state": Int(state_id(key))}))
        sb = ("minecraft:structure_block", (("mode", "data"),))
        for (x, y, z), meta in self.markers:
            blocks.append(Compound({
                "pos": List[Int]([Int(x - minx), Int(y - miny), Int(z - minz)]),
                "state": Int(state_id(sb)),
                "nbt": Compound({"id": String("minecraft:structure_block"), "mode": String("DATA"),
                                 "metadata": String(meta), "name": String("")}),
            }))
        root = Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(maxx - minx + 1), Int(maxy - miny + 1), Int(maxz - minz + 1)]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound]([]),
        })
        path = ROOT / "structure/zones" / f"{self.name}.nbt"
        path.parent.mkdir(parents=True, exist_ok=True)
        File(root, gzipped=True).save(path)
        return (maxx - minx + 1, maxy - miny + 1, maxz - minz + 1), len(self.blocks), len(self.markers)


# Piezas comunes -----------------------------------------------------------------------------

def light(t, x, y, z, lit=True, flickers=False):
    t.set(x, y, z, f"{MOD}:fluorescent_light", lit=lit, flickers=flickers)


def cabin(t, cx, y0, z0, facing_south, exit_markers=False):
    """Cabina de ascensor de 5x4x5 por dentro. facing_south: la abertura mira a +z."""
    x1, x2 = cx - 3, cx + 3
    z1, z2 = z0, z0 + 6
    t.fill(x1, y0, z1, x2, y0 + 5, z2, f"{MOD}:elevator_wall_panel")
    t.fill(x1 + 1, y0, z1 + 1, x2 - 1, y0, z2 - 1, f"{MOD}:elevator_floor")
    t.fill(x1, y0 + 5, z1, x2, y0 + 5, z2, f"{MOD}:elevator_frame")
    t.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 4, z2 - 1)
    light(t, cx, y0 + 5, z0 + 3)
    door_z = z2 if facing_south else z1
    t.fill(cx - 2, y0 + 1, door_z, cx + 2, y0 + 4, door_z, f"{MOD}:elevator_frame")
    t.clear(cx - 1, y0 + 1, door_z, cx + 1, y0 + 3, door_z)
    # Pasamanos
    back = z1 + 1 if facing_south else z2 - 1
    for x in range(x1 + 1, x2):
        t.set(x, y0 + 2, back, "iron_trapdoor", facing="south" if facing_south else "north", half="top", open=False)
    if exit_markers:
        t.marker(x1 + 1, y0 + 1, z1 + 1, "sp:exit_cabin")
        t.marker(x2 - 1, y0 + 4, z2 - 1, "sp:exit_cabin")


def lab_shell(t, x1, z1, x2, z2, y0, h, wall="white_concrete", stripe="light_gray_concrete",
              floor=("polished_andesite", "smooth_stone"), ceiling="smooth_quartz"):
    t.fill(x1, y0, z1, x2, y0 + h + 1, z2, wall)
    t.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + h, z2 - 1)
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            t.set(x, y0, z, floor[(x + z) % 2])
    t.fill(x1, y0 + h + 1, z1, x2, y0 + h + 1, z2, ceiling)
    for x in range(x1, x2 + 1):
        t.set(x, y0 + 1, z1, stripe); t.set(x, y0 + 1, z2, stripe)
    for z in range(z1, z2 + 1):
        t.set(x1, y0 + 1, z, stripe); t.set(x2, y0 + 1, z, stripe)


def ceiling_lights(t, x1, z1, x2, z2, y, step, rnd, broken=0.12, flicker=0.2):
    for x in range(x1, x2 + 1, step):
        for z in range(z1, z2 + 1, step):
            r = rnd.random()
            light(t, x, y, z, lit=r > broken, flickers=broken < r < broken + flicker)


def opening(t, x, y, z, w, h, along_x=True):
    for i in range(-(w // 2), w // 2 + 1):
        for j in range(h):
            if along_x:
                t.set(x + i, y + j, z, "air")
            else:
                t.set(x, y + j, z + i, "air")


def with_cabins(t, cx, y0, entry_z, exit_z):
    """Cabina de entrada (abre al sur) y de salida (abre al norte)."""
    cabin(t, cx, y0, entry_z, facing_south=True)
    t.marker(cx, y0 + 1, entry_z + 3, "sp:entry 0")
    cabin(t, cx, y0, exit_z, facing_south=False, exit_markers=True)


# Zonas ----------------------------------------------------------------------------------------

def start_corridor():
    t, rnd = Template("start_corridor"), random.Random(1)
    lab_shell(t, -4, 6, 4, 54, 0, 5)
    with_cabins(t, 0, 0, 0, 54)
    opening(t, 0, 1, 6, 3, 3)
    opening(t, 0, 1, 54, 3, 3)
    ceiling_lights(t, 0, 10, 0, 50, 6, 6, rnd, broken=0.15, flicker=0.35)
    for z in range(9, 52, 8):
        t.fill(-4, 2, z, -4, 4, z + 2, "glass")       # ventanas a salas vacias
        t.fill(4, 3, z + 4, 4, 3, z + 4, "iron_bars")
        t.set(-3, 1, z + 4, "barrel", facing="up")
    for z in (20, 21, 36):
        t.set(3, 1, z, "smooth_stone_slab", type="bottom")
    t.set(2, 1, 28, "potted_dead_bush")
    t.fill(-3, 5, 30, 3, 5, 30, "chain", axis="x")
    for z in (14, 30, 46):
        t.marker(-2 if z % 2 else 2, 1, z, "sp:mob_spawn")
    for z in (18, 42):
        t.marker(3, 2, z, "sp:fuse")
    return t, {"kind": "start", "difficulty": 0}


def lab_storage():
    t, rnd = Template("lab_storage"), random.Random(2)
    lab_shell(t, -16, 6, 16, 40, 0, 6, wall="light_gray_concrete", stripe="gray_concrete")
    with_cabins(t, 0, 0, 0, 40)
    opening(t, 0, 1, 6, 3, 3)
    opening(t, 0, 1, 40, 3, 3)
    ceiling_lights(t, -12, 10, 12, 36, 6, 7, rnd)
    for x in (-12, -7, 7, 12):
        for z in range(11, 36):
            if z % 9 == 0:
                continue
            t.set(x, 1, z, "barrel", facing="up")
            t.set(x, 2, z, "bookshelf" if (z + x) % 3 else "barrel", facing="up")
            if rnd.random() < 0.6:
                t.set(x, 3, z, "barrel", facing="up")
    for x, z in ((-15, 10), (15, 10), (-15, 36), (15, 36)):
        t.fill(x, 1, z, x, 6, z, "iron_block")
    t.set(3, 1, 20, "cobweb"); t.set(-3, 6, 30, "cobweb")
    for x, z in ((-10, 14), (10, 22), (-4, 33), (4, 13), (-14, 28)):
        t.marker(x, 1, z, "sp:mob_spawn")
    for x, z in ((-15, 18), (15, 26), (-15, 33)):
        t.marker(x + (1 if x < 0 else -1), 2, z, "sp:fuse")
    return t, {"kind": "intermediate", "difficulty": 1}


def lab_offices():
    t, rnd = Template("lab_offices"), random.Random(3)
    lab_shell(t, -14, 6, 14, 38, 0, 5, floor=("light_gray_concrete", "white_concrete"))
    with_cabins(t, 0, 0, 0, 38)
    opening(t, 0, 1, 6, 3, 3)
    opening(t, 0, 1, 38, 3, 3)
    # Pasillo central y despachos a los lados
    for side in (-1, 1):
        wx = 3 * side
        t.fill(wx, 1, 7, wx, 5, 37, "white_concrete")
        for z0 in range(7, 37, 8):
            t.fill(wx, 1, z0, wx + 11 * side, 5, z0, "white_concrete")
            t.clear(wx, 1, z0 + 4, wx, 3, z0 + 4)
            dz = z0 + 3
            t.set(wx + 6 * side, 1, dz, "spruce_slab", type="top")
            t.set(wx + 7 * side, 1, dz, "spruce_slab", type="top")
            t.set(wx + 6 * side, 1, dz + 2, "spruce_stairs", facing="north")
            if rnd.random() < 0.5:
                t.set(wx + 9 * side, 1, z0 + 2, "barrel", facing="up")
            light(t, wx + 6 * side, 6, z0 + 4, lit=rnd.random() > 0.25, flickers=rnd.random() < 0.3)
    ceiling_lights(t, 0, 10, 0, 34, 0, 6, rnd, broken=0.2, flicker=0.3)
    for x, z in ((-9, 12), (9, 20), (-9, 28), (9, 34), (0, 22)):
        t.marker(x, 1, z, "sp:mob_spawn")
    for x, z in ((-13, 9), (13, 17), (-13, 25), (13, 33)):
        t.marker(x, 2, z, "sp:fuse")
    return t, {"kind": "intermediate", "difficulty": 1}


def lab_morgue():
    t, rnd = Template("lab_morgue"), random.Random(4)
    lab_shell(t, -13, 6, 13, 36, 0, 5, wall="light_blue_terracotta", stripe="cyan_terracotta",
              floor=("white_concrete", "light_gray_concrete"))
    with_cabins(t, 0, 0, 0, 36)
    opening(t, 0, 1, 6, 3, 3)
    opening(t, 0, 1, 36, 3, 3)
    ceiling_lights(t, -8, 11, 8, 32, 8, 7, rnd, broken=0.25, flicker=0.35)
    # Camaras frigorificas en las paredes
    for z in range(9, 34, 2):
        for y in (1, 2, 3):
            t.set(-12, y, z, "iron_trapdoor", facing="east", half="bottom", open=True)
            t.set(12, y, z, "iron_trapdoor", facing="west", half="bottom", open=True)
    # Mesas de autopsia
    for x in (-5, 5):
        for z in (14, 22, 30):
            t.fill(x, 1, z, x, 1, z + 2, "smooth_stone_slab", type="top")
            t.set(x, 2, z + 1, "white_carpet")
    t.set(0, 1, 18, "red_carpet"); t.set(1, 1, 19, "red_carpet")
    for x, z in ((-9, 12), (9, 18), (0, 26), (-9, 31), (9, 31), (0, 12)):
        t.marker(x, 1, z, "sp:mob_spawn")
    for x, z in ((-12, 16), (12, 27), (-12, 30)):
        t.marker(x + (1 if x < 0 else -1), 2, z, "sp:fuse")
    return t, {"kind": "intermediate", "difficulty": 2}


def lab_generators():
    t, rnd = Template("lab_generators"), random.Random(5)
    lab_shell(t, -18, 6, 18, 44, 0, 8, wall="gray_concrete", stripe="yellow_terracotta",
              floor=("polished_tuff", "tuff_bricks"), ceiling="polished_deepslate")
    with_cabins(t, 0, 0, 0, 44)
    opening(t, 0, 1, 6, 3, 3)
    opening(t, 0, 1, 44, 3, 3)
    ceiling_lights(t, -14, 10, 14, 40, 7, 9, rnd, broken=0.3, flicker=0.3)
    # Generadores
    for x in (-10, 10):
        for z in (14, 26, 36):
            t.fill(x - 2, 1, z - 2, x + 2, 4, z + 2, "iron_block")
            t.fill(x - 1, 5, z - 1, x + 1, 5, z + 1, "polished_deepslate")
            t.set(x, 6, z, "chain", axis="y"); t.set(x, 7, z, "chain", axis="y"); t.set(x, 8, z, "chain", axis="y")
    # Tuberias
    for z in range(8, 43):
        t.set(-17, 7, z, "chain", axis="z"); t.set(17, 7, z, "chain", axis="z")
    t.fill(-3, 1, 30, 3, 1, 30, "yellow_terracotta")
    for x, z in ((-6, 10), (6, 20), (-6, 30), (6, 40), (0, 24), (-15, 38)):
        t.marker(x, 1, z, "sp:mob_spawn")
    for x, z in ((-17, 12), (17, 20), (-17, 30), (17, 40)):
        t.marker(x + (1 if x < 0 else -1), 2, z, "sp:fuse")
    return t, {"kind": "intermediate", "difficulty": 2}


def lab_greenhouse():
    t, rnd = Template("lab_greenhouse"), random.Random(6)
    lab_shell(t, -15, 6, 15, 40, 0, 7, wall="green_terracotta", stripe="moss_block",
              floor=("moss_block", "mossy_stone_bricks"), ceiling="glass")
    with_cabins(t, 0, 0, 0, 40)
    opening(t, 0, 1, 6, 3, 3)
    opening(t, 0, 1, 40, 3, 3)
    for x in range(-12, 13, 6):
        for z in range(11, 37, 6):
            light(t, x, 8, z, lit=rnd.random() > 0.35, flickers=rnd.random() < 0.3)
    # Bancales con plantas muertas
    for x in (-9, 9):
        for z in range(10, 37):
            t.set(x, 1, z, "mossy_stone_bricks")
            if rnd.random() < 0.5:
                t.set(x, 2, z, "potted_dead_bush")
    for _ in range(40):
        x, z = rnd.randint(-14, 14), rnd.randint(8, 38)
        if abs(x) > 2 and (x, 1, z) not in t.blocks:
            t.set(x, 1, z, "moss_carpet")
    for x, z in ((-12, 13), (12, 19), (-5, 27), (5, 35), (0, 16), (12, 33)):
        t.marker(x, 1, z, "sp:mob_spawn")
    for x, z in ((-15, 15), (15, 25), (-15, 35)):
        t.marker(x + (1 if x < 0 else -1), 2, z, "sp:fuse")
    return t, {"kind": "intermediate", "difficulty": 3}


def final_room():
    t, rnd = Template("final_room"), random.Random(7)
    lab_shell(t, -20, 6, 20, 48, 0, 12, wall="white_concrete", stripe="cyan_terracotta",
              floor=("polished_deepslate", "deepslate_tiles"), ceiling="smooth_quartz")
    cabin(t, 0, 0, 0, facing_south=True)
    t.marker(0, 1, 3, "sp:entry 0")
    opening(t, 0, 1, 6, 3, 3)
    ceiling_lights(t, -16, 10, 16, 44, 13, 8, rnd, broken=0.2, flicker=0.4)
    # Plataforma central y capsulas de criogenizacion
    t.fill(-6, 1, 22, 6, 1, 34, "polished_deepslate")
    t.fill(-2, 2, 26, 2, 2, 30, "iron_block")
    for i, (x, z) in enumerate(((-14, 14), (14, 14), (-14, 26), (14, 26), (-14, 38), (14, 38))):
        t.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, "iron_block")
        t.set(x, 1, z, "sea_lantern")
        t.fill(x - 1, 2, z - 1, x + 1, 4, z + 1, "light_blue_stained_glass")
        t.clear(x, 2, z, x, 3, z)
        t.fill(x - 1, 5, z - 1, x + 1, 5, z + 1, "iron_block")
        if i % 2:
            t.set(x, 2, z, "cobweb")
    for z in range(10, 46, 4):
        t.fill(-19, 9, z, 19, 9, z, "chain", axis="x")
    for x, z in ((-8, 16), (8, 16), (0, 40), (-10, 32), (10, 32)):
        t.marker(x, 1, z, "sp:mob_spawn")
    for x, z in ((-19, 20), (19, 20), (-19, 34), (19, 34)):
        t.marker(x + (1 if x < 0 else -1), 2, z, "sp:fuse")
    return t, {"kind": "final", "difficulty": 4}


ZONES = [start_corridor, lab_storage, lab_offices, lab_morgue, lab_generators, lab_greenhouse, final_room]


def main():
    pool = []
    for fn in ZONES:
        t, meta = fn()
        size, n, m = t.save()
        zone = {"template": f"{MOD}:zones/{t.name}", **meta, "entry_yaw": 0}
        path = ROOT / "zone" / f"{t.name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(zone, indent=2) + "\n", encoding="utf-8")
        if meta["kind"] == "intermediate":
            pool.append(f"{MOD}:{t.name}")
        print(f"{t.name:16} {size[0]}x{size[1]}x{size[2]}  bloques={n} marcadores={m}")
    (ROOT / "zone_pool").mkdir(parents=True, exist_ok=True)
    (ROOT / "zone_pool/default.json").write_text(json.dumps({"zones": pool}, indent=2) + "\n", encoding="utf-8")

    scaling = {
        "_comment": "README 6.3: multiplicadores por tamano de grupo (base 4). Ajustar en pruebas.",
        "min_fuses": 1,
        "sizes": {
            "2": {"fuses": 0.5, "mobs": 0.6, "qte_time": 1.4},
            "3": {"fuses": 0.75, "mobs": 0.8, "qte_time": 1.2},
            "4": {"fuses": 1.0, "mobs": 1.0, "qte_time": 1.0},
            "5": {"fuses": 1.0, "mobs": 1.15, "qte_time": 1.0},
            "6": {"fuses": 1.25, "mobs": 1.3, "qte_time": 1.0},
        },
    }
    (ROOT / "difficulty").mkdir(parents=True, exist_ok=True)
    (ROOT / "difficulty/group_scaling.json").write_text(json.dumps(scaling, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
