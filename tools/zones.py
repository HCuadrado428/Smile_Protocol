#!/usr/bin/env python3
"""
Plantas PROVISIONALES del laboratorio (plantas NBT + JSON) hasta que se disene el mapa (README 22).

Cada ascensor baja a una planta entera: vestibulo de llegada, pasillo principal, pasillos
transversales y perimetrales, y salas a ambos lados (laboratorios, despachos, almacenes, morgue,
vestuarios, servidores...), cada una con su puerta y su cartel. Las cajas de fusibles del
circuito «exit» dan corriente al ascensor de salida del fondo. Hay puertas con llave, con tarjeta,
hidraulicas, electricas y atascadas, valvulas, escondites, escombros para el vault, tuberias
bajas para el dive y muy poca luz.

Marcadores (bloques de estructura en modo DATA):
    sp:entry <yaw>          donde aparece el grupo
    sp:exit_cabin           dos esquinas de la cabina de salida
    sp:mob_spawn            puntos de aparicion de mobs
    sp:fuse                 sitios posibles para los fusibles
    sp:item <id> [param]    objetos: key red, keycard 1, wheel...

    pip install nbtlib
    python3 tools/zones.py
"""
import json
import random
from pathlib import Path

from nbtlib import Byte, Compound, File, Int, List, String

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/data/smileprotocol"
DATA_VERSION = 3955  # Minecraft 1.21.1
MOD = "smileprotocol"


class Template:
    def __init__(self, name):
        self.name = name
        self.blocks = {}
        self.nbt = {}
        self.markers = []

    def set(self, x, y, z, block, nbt=None, **props):
        if block == "air":
            self.blocks.pop((x, y, z), None)
            self.nbt.pop((x, y, z), None)
            return
        if ":" not in block:
            block = "minecraft:" + block
        self.blocks[(x, y, z)] = (block, tuple(sorted((k, str(v).lower()) for k, v in props.items())))
        if nbt is not None:
            self.nbt[(x, y, z)] = nbt
        else:
            self.nbt.pop((x, y, z), None)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else "minecraft:air"

    def solid(self, x, y, z):
        return (x, y, z) in self.blocks

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

        def pos(x, y, z):
            return List[Int]([Int(x - minx), Int(y - miny), Int(z - minz)])

        for (x, y, z), key in self.blocks.items():
            b = Compound({"pos": pos(x, y, z), "state": Int(state_id(key))})
            if (x, y, z) in self.nbt:
                b["nbt"] = self.nbt[(x, y, z)]
            blocks.append(b)
        sb = ("minecraft:structure_block", (("mode", "data"),))
        for (x, y, z), meta in self.markers:
            blocks.append(Compound({
                "pos": pos(x, y, z), "state": Int(state_id(sb)),
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


def be(kind, **fields):
    c = Compound({"id": String(f"{MOD}:{kind}")})
    for k, v in fields.items():
        if isinstance(v, bool):
            c[k] = Byte(1 if v else 0)
        elif isinstance(v, int):
            c[k] = Int(v)
        else:
            c[k] = String(v)
    return c




def cabin(t, cx, y0, z0, facing_south, doors_open, exit_markers=False):
    """Cabina de ascensor de 5x4x5 con puertas correderas de 3 hojas (GeckoLib)."""
    x1, x2 = cx - 3, cx + 3
    z1, z2 = z0, z0 + 6
    t.fill(x1, y0, z1, x2, y0 + 5, z2, f"{MOD}:elevator_wall_panel")
    t.fill(x1 + 1, y0, z1 + 1, x2 - 1, y0, z2 - 1, f"{MOD}:elevator_floor")
    t.fill(x1, y0 + 5, z1, x2, y0 + 5, z2, f"{MOD}:elevator_frame")
    t.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 4, z2 - 1)
    t.set(cx, y0 + 5, z0 + 3, f"{MOD}:fluorescent_light", lit=True, flickers=False)
    door_z = z2 if facing_south else z1
    t.fill(cx - 2, y0 + 1, door_z, cx + 2, y0 + 4, door_z, f"{MOD}:elevator_frame")
    facing = "south" if facing_south else "north"
    for x in range(cx - 1, cx + 2):
        t.set(x, y0 + 1, door_z, f"{MOD}:elevator_door", facing=facing, half="lower", open=doors_open)
        t.set(x, y0 + 2, door_z, f"{MOD}:elevator_door", facing=facing, half="upper", open=doors_open)
    back = z1 + 1 if facing_south else z2 - 1
    for x in range(x1 + 1, x2):
        t.set(x, y0 + 2, back, "iron_trapdoor", facing="south" if facing_south else "north", half="top", open=False)
    if exit_markers:
        t.marker(x1 + 1, y0 + 1, z1 + 1, "sp:exit_cabin")
        t.marker(x2 - 1, y0 + 4, z2 - 1, "sp:exit_cabin")



def be(kind, **fields):
    c = Compound({"id": String(f"{MOD}:{kind}")})
    for k, v in fields.items():
        if isinstance(v, bool):
            c[k] = Byte(1 if v else 0)
        elif isinstance(v, int):
            c[k] = Int(v)
        else:
            c[k] = String(v)
    return c


# Plantas del laboratorio ----------------------------------------------------------------------------
#
# Cada zona es una PLANTA entera: vestibulo del ascensor de llegada, pasillo principal, pasillos
# transversales y perimetrales, y salas a ambos lados con su puerta y su cartel. Al fondo, el
# vestibulo del ascensor de salida. Coordenadas: X de -45 a 45, Z crece hacia el fondo, suelo y=0,
# techo y=5.

H = 4
OUT_X = 45          # paredes exteriores en x = +-45
MAIN = 2            # pasillo principal: x de -2 a 2
PERIM = (42, 44)    # pasillos perimetrales: |x| de 42 a 44
CROSS = [(30, 33), (58, 61), (86, 89)]
SOUTH_Z = 14        # empieza la planta (detras del vestibulo de llegada)
NORTH_Z = 103       # termina la planta (delante del vestibulo de salida)
HALL_W = 7


class Floor:
    def __init__(self, name, theme, seed):
        self.t = Template(name)
        self.theme = theme
        self.rnd = random.Random(seed)
        self.rooms = []
        self.used = set()
        self.corridor_cells = []

    # Utilidades -------------------------------------------------------------------------------
    def solid(self, x, y, z):
        return self.t.solid(x, y, z)

    def carve(self, x0, z0, x1, z1, floor=None, corridor=True):
        t, th = self.t, self.theme
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                t.clear(x, 1, z, x, H, z)
                if floor:
                    t.set(x, 0, z, floor[(x + z) % 2] if isinstance(floor, tuple) else floor)
                if corridor:
                    self.corridor_cells.append((x, z))

    def base(self):
        t, th = self.t, self.theme
        for x in range(-OUT_X, OUT_X + 1):
            for z in range(SOUTH_Z, NORTH_Z + 1):
                t.set(x, 0, z, th["floor"][(x + z) % 2])
                t.set(x, H + 1, z, th["ceiling"])
                for y in range(1, H + 1):
                    t.set(x, y, z, th["wall_low"] if y == 1 else th["wall"])
        # Vestibulos de los ascensores
        for z0, z1 in ((7, SOUTH_Z - 1), (NORTH_Z + 1, NORTH_Z + 7)):
            for x in range(-HALL_W - 1, HALL_W + 2):
                for z in range(z0, z1 + 1):
                    t.set(x, 0, z, th["hall_floor"][(x + z) % 2])
                    t.set(x, H + 1, z, th["ceiling"])
                    for y in range(1, H + 1):
                        t.set(x, y, z, th["wall"])

    def corridors(self):
        th = self.theme
        cf = th["corridor_floor"]
        self.carve(-HALL_W, 7, HALL_W, SOUTH_Z - 1, th["hall_floor"])              # vestibulo de llegada
        self.carve(-MAIN, SOUTH_Z, MAIN, NORTH_Z, cf)                               # principal
        self.carve(-HALL_W, NORTH_Z + 1, HALL_W, NORTH_Z + 7, th["hall_floor"])     # vestibulo de salida
        for z0, z1 in CROSS:
            self.carve(-PERIM[1], z0, PERIM[1], z1, cf)
        for sx in (-1, 1):
            a, b = sorted((sx * PERIM[0], sx * PERIM[1]))
            self.carve(a, CROSS[0][0], b, CROSS[-1][1], cf)

    # Salas ------------------------------------------------------------------------------------------
    def regions(self):
        """Bloques entre pasillos: (x0, z0, x1, z1, lados con pasillo). Los bordes son paredes."""
        R = []
        for sx in (-1, 1):
            def xs(a, b):
                return (min(sx * a, sx * b), max(sx * a, sx * b))
            # Sur (entre el vestibulo y el primer transversal)
            x0, x1 = xs(MAIN + 1, OUT_X)
            R.append((x0, SOUTH_Z, x1, CROSS[0][0] - 1, {"n"}))
            # Medios
            x0, x1 = xs(MAIN + 1, PERIM[0] - 1)
            for (a, _), (b, _) in zip(CROSS, CROSS[1:]):
                pass
            for i in range(len(CROSS) - 1):
                R.append((x0, CROSS[i][1] + 1, x1, CROSS[i + 1][0] - 1, {"s", "n"}))
            # Norte
            x0, x1 = xs(MAIN + 1, OUT_X)
            R.append((x0, CROSS[-1][1] + 1, x1, NORTH_Z, {"s"}))
        return R

    def split_rooms(self, kinds):
        for (x0, z0, x1, z1, sides) in self.regions():
            ix0, ix1, iz0, iz1 = x0 + 1, x1 - 1, z0 + 1, z1 - 1
            depth = iz1 - iz0 + 1
            rows = []
            if "s" in sides and "n" in sides and depth >= 15:
                mid = iz0 + depth // 2
                rows.append((iz0, mid - 1, "s"))
                rows.append((mid + 1, iz1, "n"))
            else:
                rows.append((iz0, iz1, "n" if "n" in sides else "s"))
            for (rz0, rz1, side) in rows:
                x = ix0
                while x <= ix1:
                    w = self.rnd.randint(7, 12)
                    if ix1 - (x + w) < 6:
                        w = ix1 - x + 1
                    rx0, rx1 = x, min(ix1, x + w - 1)
                    if rx1 - rx0 >= 3:
                        self.add_room(rx0, rz0, rx1, rz1, side, self.rnd.choice(kinds))
                    x = rx1 + 2

    def add_room(self, x0, z0, x1, z1, side, kind):
        th = self.theme
        self.carve(x0, z0, x1, z1, th["room_floor"].get(kind, th["floor"]), corridor=False)
        # Puerta en la pared que da al pasillo
        dz = z0 - 1 if side == "s" else z1 + 1
        dx = self.rnd.randint(x0 + 1, max(x0 + 1, x1 - 1))
        room = {"id": len(self.rooms), "x0": x0, "z0": z0, "x1": x1, "z1": z1, "kind": kind,
                "door": (dx, dz), "side": side}
        self.rooms.append(room)
        self.place_door(room, f"{MOD}:lab_door")
        self.sign(room)
        return room

    def place_door(self, room, block, nbt=None):
        dx, dz = room["door"]
        facing = "north" if room["side"] == "s" else "south"   # mirando hacia dentro de la sala
        self.t.clear(dx, 1, dz, dx, 2, dz)
        self.t.set(dx, 1, dz, block, nbt=nbt, facing=facing, half="lower", hinge="left", open=False, powered=False)
        self.t.set(dx, 2, dz, block, facing=facing, half="upper", hinge="left", open=False, powered=False)
        self.used.add((dx, dz))
        # Que no se tape la puerta por ningun lado
        for ddz in (-1, 1):
            self.used.add((dx, dz + ddz))

    def sign(self, room):
        dx, dz = room["door"]
        side = room["side"]
        sz = dz - 1 if side == "s" else dz + 1          # lado del pasillo
        facing = "south" if side == "s" else "north"    # el cartel mira al pasillo
        for sx in (dx + 1, dx - 1):
            if room["x0"] <= sx <= room["x1"] and not self.solid(sx, 3, sz):
                label = LABELS.get(room["kind"], "SALA")
                text = f"{label} {room['id'] + 1:02d}"
                self.t.set(sx, 3, sz, "minecraft:birch_wall_sign", nbt=sign_nbt(text), facing=facing)
                return

    # Mobiliario ----------------------------------------------------------------------------------------
    def free(self, x, z):
        return not self.solid(x, 1, z) and (x, z) not in self.used

    def wall_spots(self, room):
        out = []
        for x in range(room["x0"], room["x1"] + 1):
            for z in range(room["z0"], room["z1"] + 1):
                if not self.free(x, z):
                    continue
                for f, (ox, oz) in (("south", (0, -1)), ("north", (0, 1)), ("east", (-1, 0)), ("west", (1, 0))):
                    if self.solid(x + ox, 2, z + oz) and self.solid(x + ox, 1, z + oz):
                        out.append((x, z, f))
        self.rnd.shuffle(out)
        return out

    def tall(self, block, x, z, f, nbt=None):
        if self.solid(x, 1, z) or self.solid(x, 2, z) or (x, z) in self.used:
            return False
        self.t.set(x, 1, z, block, nbt=nbt, facing=f, half="lower", open=False)
        self.t.set(x, 2, z, block, facing=f, half="upper", open=False)
        self.used.add((x, z))
        return True

    def put(self, x, z, block, y=1, **props):
        if not self.free(x, z):
            return False
        self.t.set(x, y, z, block, **props)
        self.used.add((x, z))
        return True

    def take(self, spots, n, fn):
        k = 0
        for (x, z, f) in spots:
            if k >= n:
                break
            if (x, z) in self.used:
                continue
            if fn(x, z, f):
                k += 1
        return k

    def furnish(self, room):
        kind, r = room["kind"], self.rnd
        x0, z0, x1, z1 = room["x0"], room["z0"], room["x1"], room["z1"]
        spots = self.wall_spots(room)
        if kind == "lab":
            # Mesas de laboratorio en el centro, cortinas y una taquilla
            for x in range(x0 + 2, x1 - 1, 3):
                for z in range(z0 + 2, z1 - 1):
                    if r.random() < 0.8:
                        self.put(x, z, "minecraft:smooth_stone_slab", type="top")
            self.take(spots, 2, lambda x, z, f: self.tall(f"{MOD}:curtain", x, z, f))
            self.take(self.wall_spots(room), 1, lambda x, z, f: self.tall(f"{MOD}:locker", x, z, f))
            self.take(self.wall_spots(room), 2, lambda x, z, f: self.put(x, z, "minecraft:brewing_stand", y=1))
        elif kind == "office":
            for x in range(x0 + 1, x1, 3):
                for z in range(z0 + 1, z1, 3):
                    if r.random() < 0.7 and self.put(x, z, "minecraft:spruce_slab", type="top"):
                        self.put(x, z + 1, "minecraft:spruce_stairs", facing="north")
            self.take(spots, 2, lambda x, z, f: self.tall(f"{MOD}:wardrobe", x, z, f))
            self.take(self.wall_spots(room), 2, lambda x, z, f: self.put(x, z, "minecraft:bookshelf"))
        elif kind == "storage":
            for x in range(x0 + 1, x1, 3):
                for z in range(z0 + 1, z1):
                    if r.random() < 0.75 and self.put(x, z, "minecraft:barrel", facing="up"):
                        if r.random() < 0.6:
                            self.t.set(x, 2, z, "minecraft:barrel", facing="up")
            self.take(spots, 2, lambda x, z, f: self.tall(f"{MOD}:waste_container", x, z, f))
        elif kind == "morgue":
            for x in range(x0 + 1, x1, 2):
                z = z0 + 2 if r.random() < 0.5 else z1 - 2
                self.put(x, z, f"{MOD}:gurney", facing=r.choice(["north", "east"]), half="lower", open=False)
            self.take(spots, 2, lambda x, z, f: self.tall(f"{MOD}:locker", x, z, f))
        elif kind == "lockers":
            self.take(spots, 8, lambda x, z, f: self.tall(f"{MOD}:locker", x, z, f))
            for x in range(x0 + 2, x1 - 1, 3):
                self.put(x, (z0 + z1) // 2, "minecraft:spruce_slab", type="bottom")
        elif kind == "ward":
            for x in range(x0 + 1, x1, 3):
                self.put(x, z0 + 1, f"{MOD}:gurney", facing="north", half="lower", open=False)
                self.tall(f"{MOD}:curtain", x + 1, z0 + 1, "east")
        elif kind == "server":
            for x in range(x0 + 1, x1, 2):
                for z in range(z0 + 1, z1, 1):
                    if (z - z0) % 3 != 0 and self.free(x, z):
                        self.t.fill(x, 1, z, x, 2, z, "minecraft:iron_block")
                        self.used.add((x, z))
            self.take(self.wall_spots(room), 1, lambda x, z, f: self.tall(f"{MOD}:locker", x, z, f))
        elif kind == "machine":
            for x in range(x0 + 2, x1 - 1, 4):
                for z in range(z0 + 2, z1 - 1, 4):
                    if self.free(x, z):
                        self.t.fill(x, 1, z, x, 2, z, "minecraft:iron_block")
                        self.t.set(x, 3, z, "minecraft:chain", axis="y")
                        self.t.set(x, 4, z, "minecraft:chain", axis="y")
                        self.used.add((x, z))
            self.take(spots, 1, lambda x, z, f: self.tall(f"{MOD}:waste_container", x, z, f))
        elif kind == "break":
            for x in range(x0 + 2, x1 - 1, 3):
                z = (z0 + z1) // 2
                self.put(x, z, "minecraft:spruce_slab", type="top")
            self.take(spots, 1, lambda x, z, f: self.tall(f"{MOD}:wardrobe", x, z, f))

    def light(self, x, z, mode):
        if mode == "none":
            return
        nbt = be("fluorescent_light", Circuit="exit") if mode == "circuit" else None
        lit = mode in ("lit", "flicker")
        self.t.set(x, H + 1, z, f"{MOD}:fluorescent_light", nbt=nbt, lit=lit, flickers=mode == "flicker")

    def pick_light(self, room=False):
        r = self.rnd.random()
        if room:
            return "circuit" if r < 0.4 else "flicker" if r < 0.6 else "dead"
        return "lit" if r < 0.08 else "flicker" if r < 0.22 else "circuit" if r < 0.5 else "dead"

    def lights(self):
        for z in range(SOUTH_Z + 3, NORTH_Z, 7):
            self.light(0, z, self.pick_light())
        for z0, z1 in CROSS:
            for x in range(-PERIM[1] + 3, PERIM[1], 8):
                if abs(x) > MAIN + 1:
                    self.light(x, (z0 + z1) // 2, self.pick_light())
        for sx in (-1, 1):
            for z in range(CROSS[0][1] + 5, CROSS[-1][0], 8):
                self.light(sx * 43, z, self.pick_light())
        for room in self.rooms:
            self.light((room["x0"] + room["x1"]) // 2, (room["z0"] + room["z1"]) // 2, self.pick_light(room=True))
        # Vestibulos: algo de luz para orientarse
        self.light(0, 10, "flicker")
        self.light(0, NORTH_Z + 4, "circuit")

    def corridor_dressing(self, lockers, debris, pipes):
        """Taquillas en los pasillos, escombros para el vault y tuberias caidas para el dive."""
        cells = [c for c in self.corridor_cells if SOUTH_Z + 2 < c[1] < NORTH_Z - 1]
        self.rnd.shuffle(cells)
        k = 0
        for (x, z) in cells:
            if k >= lockers:
                break
            for f, (ox, oz) in (("south", (0, -1)), ("north", (0, 1)), ("east", (-1, 0)), ("west", (1, 0))):
                if self.solid(x + ox, 2, z + oz) and self.free(x, z) and self.free(x - ox, z - oz) \
                        and not self.near_door(x, z):
                    if self.tall(f"{MOD}:locker", x, z, f):
                        k += 1
                        break
        k = 0
        for (x, z) in cells:
            if k >= debris:
                break
            if self.free(x, z) and not self.near_door(x, z) and abs(x) > MAIN:
                self.put(x, z, self.rnd.choice(self.theme["debris"]))
                k += 1
        # Tuberias bajas: en tramos de los transversales (se pasa agachado o con dive)
        for i in range(pipes):
            z0, z1 = CROSS[i % len(CROSS)]
            x = self.rnd.choice([-1, 1]) * self.rnd.randint(8, 36)
            for z in range(z0, z1 + 1):
                for y in (2, 3):
                    self.t.set(x, y, z, "minecraft:iron_bars" if y == 3 else self.theme["pipe"])
                self.used.add((x, z))

    def near_door(self, x, z):
        for room in self.rooms:
            dx, dz = room["door"]
            if abs(dx - x) <= 1 and abs(dz - z) <= 2:
                return True
        return False

    def fuse_spots(self, per_room=1):
        for room in self.rooms:
            for _ in range(per_room):
                for _ in range(20):
                    x = self.rnd.randint(room["x0"], room["x1"])
                    z = self.rnd.randint(room["z0"], room["z1"])
                    if self.free(x, z):
                        self.t.marker(x, 1, z, "sp:fuse")
                        self.used.add((x, z))
                        break

    def item_in(self, room, spec):
        for _ in range(40):
            x = self.rnd.randint(room["x0"], room["x1"])
            z = self.rnd.randint(room["z0"], room["z1"])
            if self.free(x, z):
                self.t.marker(x, 1, z, f"sp:item {spec}")
                self.used.add((x, z))
                return True
        return False

    def fusebox(self, room, circuit="exit", slots=2, accepts=""):
        def put(x, z, f):
            nbt = be("fuse_box", Circuit=circuit, Slots=slots, Accepts=accepts)
            self.t.set(x, 2, z, f"{MOD}:fuse_box", nbt=nbt, facing=f, filled=0, powered=False)
            self.used.add((x, z))
            return True
        return self.take(self.wall_spots(room), 1, put)

    def valve(self, room, group, order=0, coop=False, wheel=True):
        def put(x, z, f):
            nbt = be("valve", Group=group, Order=order, Coop=coop)
            self.t.set(x, 2, z, f"{MOD}:valve", nbt=nbt, facing=f, turning=False, turned=False, wheel=wheel)
            self.used.add((x, z))
            return True
        return self.take(self.wall_spots(room), 1, put)

    def mobs(self, n):
        cells = [c for c in self.corridor_cells if c[1] > CROSS[0][0]]
        self.rnd.shuffle(cells)
        for room in self.rooms:
            cells.append(((room["x0"] + room["x1"]) // 2, (room["z0"] + room["z1"]) // 2))
        k = 0
        for (x, z) in cells:
            if k >= n:
                break
            if self.free(x, z):
                self.t.marker(x, 1, z, "sp:mob_spawn")
                self.used.add((x, z))
                k += 1

    def reception(self):
        """Mostrador y bancos en el vestibulo de llegada; cartel de la planta."""
        for x in range(-6, -2):
            self.put(x, 11, "minecraft:quartz_slab", type="top")
        for x in range(3, 7):
            self.put(x, 12, "minecraft:spruce_stairs", facing="north")
        self.t.set(-HALL_W, 3, 10, "minecraft:birch_wall_sign", nbt=sign_nbt("NIVEL", "RESTRINGIDO", "SOLO PERSONAL"), facing="east")
        self.t.set(HALL_W, 3, 10, "minecraft:birch_wall_sign", nbt=sign_nbt("ASCENSOR DE", "SERVICIO", "AL FONDO ^"), facing="west")

    def cabins(self):
        t = self.t
        cabin(t, 0, 0, 0, facing_south=True, doors_open=True)
        t.marker(0, 1, 3, "sp:entry 0")
        cabin(t, 0, 0, NORTH_Z + 8, facing_south=False, doors_open=False, exit_markers=True)

    def by_distance(self):
        return sorted(self.rooms, key=lambda r: r["z0"] + abs(r["x0"]) * 0.5)

    def retype(self, room, block, nbt):
        self.place_door(room, block, nbt)


def sign_nbt(*lines):
    msgs = [json.dumps({"text": l}) for l in lines] + [json.dumps({"text": ""})] * (4 - len(lines))
    text = Compound({"messages": List[String]([String(m) for m in msgs[:4]]), "color": String("black"),
                     "has_glowing_text": Byte(0)})
    empty = Compound({"messages": List[String]([String(json.dumps({"text": ""}))] * 4), "color": String("black"),
                      "has_glowing_text": Byte(0)})
    return Compound({"id": String("minecraft:sign"), "front_text": text, "back_text": empty, "is_waxed": Byte(1)})


LABELS = {"lab": "LAB", "office": "DESPACHO", "storage": "ALMACEN", "morgue": "MORGUE", "lockers": "VESTUARIO",
          "ward": "SALA", "server": "SERVIDORES", "machine": "MAQUINAS", "break": "DESCANSO"}


# Temas ---------------------------------------------------------------------------------------------------

def theme(**kw):
    base = dict(floor=("polished_andesite", "andesite"), corridor_floor=("white_concrete", "light_gray_concrete"),
                hall_floor=("smooth_quartz", "quartz_bricks"), wall="white_concrete", wall_low="light_gray_concrete",
                ceiling="smooth_quartz", pipe="iron_block", debris=("barrel", "smooth_stone_slab", "spruce_slab"),
                room_floor={})
    base.update(kw)
    return base


THEMES = {
    "start": theme(room_floor={"office": ("birch_planks", "stripped_birch_wood"), "lockers": ("smooth_stone", "stone")}),
    "storage": theme(wall="light_gray_concrete", wall_low="gray_concrete", ceiling="polished_deepslate",
                     corridor_floor=("polished_andesite", "smooth_stone"),
                     room_floor={"storage": ("stone_bricks", "cracked_stone_bricks"), "machine": ("polished_deepslate", "deepslate_tiles")}),
    "offices": theme(wall="white_terracotta", wall_low="light_gray_terracotta",
                     room_floor={"office": ("birch_planks", "stripped_birch_wood"), "break": ("spruce_planks", "stripped_spruce_wood")}),
    "morgue": theme(wall="light_blue_terracotta", wall_low="cyan_terracotta", corridor_floor=("white_concrete", "light_blue_concrete"),
                    room_floor={"morgue": ("diorite", "polished_diorite"), "ward": ("white_concrete", "white_concrete")}),
    "generators": theme(wall="gray_concrete", wall_low="yellow_terracotta", ceiling="polished_deepslate",
                        corridor_floor=("polished_tuff", "tuff_bricks"),
                        room_floor={"machine": ("polished_deepslate", "deepslate_tiles"), "server": ("polished_blackstone", "blackstone")}),
    "greenhouse": theme(wall="green_terracotta", wall_low="mossy_stone_bricks", corridor_floor=("moss_block", "mossy_stone_bricks"),
                        room_floor={"lab": ("mossy_cobblestone", "moss_block")}),
    "final": theme(wall="white_concrete", wall_low="cyan_terracotta", ceiling="smooth_quartz",
                   corridor_floor=("polished_deepslate", "deepslate_tiles"),
                   room_floor={"lab": ("polished_deepslate", "deepslate_tiles")}),
}


# Contenido especial de cada planta -------------------------------------------------------------------------

def apply_start(f):
    rooms = f.by_distance()
    f.fusebox(rooms[1], slots=1)
    f.fusebox(rooms[-1], slots=1)


def apply_storage(f):
    rooms = f.by_distance()
    locked = rooms[-2]
    f.retype(locked, f"{MOD}:security_door", be("lab_door", Type="locked", Param="red"))
    f.fusebox(locked)
    f.fusebox(rooms[len(rooms) // 2])
    f.item_in(rooms[len(rooms) // 3], "key red")


def apply_offices(f):
    rooms = f.by_distance()
    sec = rooms[-1]
    f.retype(sec, f"{MOD}:security_door", be("lab_door", Type="keycard", Param="1"))
    f.fusebox(sec)
    f.fusebox(rooms[len(rooms) // 2])
    f.item_in(rooms[len(rooms) // 4], "keycard 1")


def apply_morgue(f):
    rooms = f.by_distance()
    f.retype(rooms[-1], f"{MOD}:security_door", be("lab_door", Type="stuck"))
    f.fusebox(rooms[-1])
    locked = rooms[len(rooms) // 2]
    f.retype(locked, f"{MOD}:security_door", be("lab_door", Type="locked", Param="blue", Consume=True))
    f.fusebox(locked)
    f.item_in(rooms[len(rooms) // 4], "key blue")


def apply_generators(f):
    rooms = f.by_distance()
    hyd = rooms[-1]
    f.retype(hyd, f"{MOD}:security_door", be("lab_door", Type="hydraulic", Param="steam"))
    f.fusebox(hyd, slots=3)
    f.valve(rooms[len(rooms) // 3], "steam", order=1)
    f.valve(rooms[2 * len(rooms) // 3], "steam", order=2, wheel=False)
    f.item_in(rooms[len(rooms) // 5], "wheel")
    elec = rooms[-3]
    f.retype(elec, f"{MOD}:security_door", be("lab_door", Type="electric", Param="aux"))
    f.fusebox(rooms[len(rooms) // 2], circuit="aux", slots=1)
    f.fusebox(elec)


def apply_greenhouse(f):
    rooms = f.by_distance()
    hyd = rooms[-1]
    f.retype(hyd, f"{MOD}:security_door", be("lab_door", Type="hydraulic", Param="mist"))
    f.fusebox(hyd)
    f.valve(rooms[len(rooms) // 2], "mist", coop=True)
    f.valve(rooms[len(rooms) // 2], "mist", coop=True)
    sec = rooms[-2]
    f.retype(sec, f"{MOD}:security_door", be("lab_door", Type="keycard", Param="2"))
    f.fusebox(sec)
    f.item_in(rooms[len(rooms) // 3], "keycard 2")


def apply_final(f):
    rooms = f.by_distance()
    # Sala de criogenizacion: capsulas en las salas mas grandes
    big = sorted(rooms, key=lambda r: -(r["x1"] - r["x0"]) * (r["z1"] - r["z0"]))[:3]
    for r in big:
        for x in range(r["x0"] + 1, r["x1"], 3):
            z = r["z0"] + 2
            if f.free(x, z):
                f.t.set(x, 0, z, "minecraft:sea_lantern")
                f.t.fill(x, 1, z, x, 3, z, "minecraft:light_blue_stained_glass")
                f.t.set(x, 4, z, "minecraft:iron_block")
                f.used.add((x, z))
    for r in big:
        f.fusebox(r)
    f.retype(rooms[-1], f"{MOD}:security_door", be("lab_door", Type="keycard", Param="3"))
    f.fusebox(rooms[-1])
    f.item_in(rooms[len(rooms) // 2], "keycard 3")


FLOORS = [
    # nombre, tema, semilla, tipos de sala, aplicar, extra, kind, dificultad, nombre visible
    ("start_corridor", "start", 11, ["office", "lockers", "storage", "break"], apply_start,
     dict(lockers=6, debris=6, pipes=1, mobs=4), "start", 0, "Recepción de investigación"),
    ("lab_storage", "storage", 22, ["storage", "storage", "machine", "lockers", "office"], apply_storage,
     dict(lockers=10, debris=14, pipes=3, mobs=8), "intermediate", 1, "Almacén central"),
    ("lab_offices", "offices", 33, ["office", "office", "break", "lab", "lockers"], apply_offices,
     dict(lockers=8, debris=10, pipes=2, mobs=8), "intermediate", 1, "Administración"),
    ("lab_morgue", "morgue", 44, ["morgue", "ward", "ward", "lab", "lockers"], apply_morgue,
     dict(lockers=10, debris=10, pipes=3, mobs=9), "intermediate", 2, "Depósito y morgue"),
    ("lab_generators", "generators", 55, ["machine", "server", "machine", "storage", "lockers"], apply_generators,
     dict(lockers=10, debris=14, pipes=5, mobs=10), "intermediate", 2, "Planta de energía"),
    ("lab_greenhouse", "greenhouse", 66, ["lab", "lab", "storage", "office"], apply_greenhouse,
     dict(lockers=8, debris=12, pipes=3, mobs=10), "intermediate", 3, "Invernadero experimental"),
    ("final_room", "final", 77, ["lab", "lab", "server", "ward"], apply_final,
     dict(lockers=10, debris=8, pipes=2, mobs=8), "final", 4, "Criogenización y experimentos"),
]


def make_floor(name, theme_name, seed, kinds, apply, extra):
    f = Floor(name, THEMES[theme_name], seed)
    f.base()
    f.corridors()
    f.split_rooms(kinds)
    f.cabins()
    f.reception()
    for room in f.rooms:
        f.furnish(room)
    apply(f)
    f.lights()
    f.corridor_dressing(extra["lockers"], extra["debris"], extra["pipes"])
    f.fuse_spots(per_room=1)
    f.mobs(extra["mobs"])
    return f


def main():
    pool = []
    for name, theme_name, seed, kinds, apply, extra, kind, diff, title in FLOORS:
        f = make_floor(name, theme_name, seed, kinds, apply, extra)
        size, nb, nm = f.t.save()
        zone = {"template": f"{MOD}:zones/{name}", "kind": kind, "difficulty": diff, "entry_yaw": 0, "name": title}
        path = ROOT / "zone" / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(zone, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        if kind == "intermediate":
            pool.append(f"{MOD}:{name}")
        print(f"{name:16} {size[0]}x{size[1]}x{size[2]}  bloques={nb} marcadores={nm} salas={len(f.rooms)}")
    (ROOT / "zone_pool").mkdir(parents=True, exist_ok=True)
    (ROOT / "zone_pool/default.json").write_text(json.dumps({"zones": pool}, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
