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
                t.set(x, 0, z, th["floor"])
                t.set(x, H + 1, z, th["ceiling"])
                for y in range(1, H + 1):
                    t.set(x, y, z, th["wall_low"] if y <= 2 else th["wall"])
        # Vestibulos de los ascensores
        # La fila de la cabina (z = 6 y z = NORTH_Z + 8) tambien es pared: el vestibulo es mas ancho que la cabina
        for z0, z1 in ((6, SOUTH_Z - 1), (NORTH_Z + 1, NORTH_Z + 8)):
            for x in range(-HALL_W - 1, HALL_W + 2):
                for z in range(z0, z1 + 1):
                    t.set(x, 0, z, th["hall_floor"])
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

    def gurney(self, x, z, f):
        """Camilla de dos bloques: la cabecera en (x, z) y los pies hacia `f`."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[f]
        if not (self.free(x, z) and self.free(x + dx, z + dz)):
            return False
        self.t.set(x, 1, z, f"{MOD}:gurney", facing=f, half="lower", open=False)
        self.t.set(x + dx, 1, z + dz, f"{MOD}:gurney", facing=f, half="upper", open=False)
        self.used.add((x, z))
        self.used.add((x + dx, z + dz))
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

    # Salas singulares: una por planta, la que le da su caracter ------------------------------------------
    def inner(self, room, m=1):
        """Celdas interiores dejando m de margen con las paredes."""
        return range(room["x0"] + m, room["x1"] - m + 1), range(room["z0"] + m, room["z1"] - m + 1)

    def setp(self, x, y, z, block, **props):
        """Coloca algo en la sala si la celda esta libre (no tapa puertas ni marcadores)."""
        if y == 1 and ((x, z) in self.used or self.solid(x, 1, z)):
            return False
        if y > 1 and (self.solid(x, y, z) or not self.solid(x, y - 1, z)):
            return False
        self.t.set(x, y, z, block, **props)
        if y == 1:
            self.used.add((x, z))
        return True

    def sp_reception(self, room):
        """Recepcion: mostrador en L con sillas detras, sala de espera con filas de sillas, plantas
        muertas en las esquinas y un rotulo de la empresa."""
        x0, z0, x1, z1 = room["x0"], room["z0"], room["x1"], room["z1"]
        cz = z0 + (z1 - z0) // 3
        for x in range(x0 + 3, x0 + 3 + min(8, x1 - x0 - 6)):
            self.setp(x, 1, cz, "minecraft:polished_diorite")
        for z in range(z0 + 2, cz):
            self.setp(x0 + 3, 1, z, "minecraft:polished_diorite")
        for x in range(x0 + 4, x0 + 3 + min(8, x1 - x0 - 6), 2):
            self.furn("office_chair", x, cz - 1, "south")
        self.furn("workstation", x0 + 5, z0 + 1, "south")
        self.furn("filing_cabinet", x0 + 6, z0 + 1, "south")
        self.furn("filing_cabinet", x0 + 7, z0 + 1, "south")
        # sala de espera: filas de sillas mirando al mostrador
        for z in range(cz + 3, z1 - 1, 3):
            for x in range(x0 + 2, x1 - 1):
                if (x - x0) % 6 != 5:
                    self.setp(x, 1, z, "minecraft:dark_oak_stairs", facing="north")
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            self.setp(x, 1, z, "minecraft:potted_dead_bush")
        # rotulo en la pared del fondo
        self.t.set((x0 + x1) // 2, 3, z0, "minecraft:birch_wall_sign", nbt=sign_nbt("SMILE", "PROTOCOL", "Recepcion"), facing="south")

    def sp_warehouse(self, room):
        """Nave del almacen: estanterias de cajas hasta el techo en filas con pasillos de dos, alguna
        caida en medio del pasillo y cadenas colgando."""
        r = self.rnd
        xs, zs = self.inner(room, 1)
        for z in list(zs)[::4]:
            for x in xs:
                if (x - room["x0"]) % 9 in (0, 1):          # cortes para cruzar entre filas
                    continue
                h = 3 if r.random() < 0.7 else 2
                for y in range(1, h + 1):
                    if not self.setp(x, y, z, f"{MOD}:lab_crate") and y == 1:
                        break
                if r.random() < 0.08:
                    self.setp(x, 1, z + 2, f"{MOD}:lab_crate")        # caida en el pasillo
                elif r.random() < 0.04:
                    self.furn("supply_box", x, z + 2, "north")
        for x in list(xs)[2::6]:
            for z in list(zs)[2::4]:
                if not self.solid(x, 4, z):
                    self.t.set(x, 4, z, "minecraft:chain", axis="y")

    def sp_openoffice(self, room):
        """Oficina diafana: cubiculos de mamparas de dos de alto con mesa y silla; se ve por encima
        agachado no."""
        xs, zs = self.inner(room, 2)
        xs, zs = list(xs), list(zs)
        for bx in xs[::4]:
            for bz in zs[::4]:
                if bx + 3 > room["x1"] - 1 or bz + 3 > room["z1"] - 1:
                    continue
                # mampara en L: lado norte y oeste, con hueco de entrada
                for k in range(3):
                    for y in (1, 2):
                        self.setp(bx + k, y, bz, "minecraft:light_gray_wool")
                        if k < 2:
                            self.setp(bx, y, bz + k + 1, "minecraft:light_gray_wool")
                self.furn(self.rnd.choice(["workstation", "desk"]), bx + 1, bz + 1, "south")
                self.furn("office_chair", bx + 1, bz + 2, "north")

    def sp_coldroom(self, room):
        """Camaras frigorificas: las paredes forradas de nichos de acero, mesas de autopsia en el
        centro con su desague, y escarcha por el suelo."""
        x0, z0, x1, z1 = room["x0"], room["z0"], room["x1"], room["z1"]
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                if (x, z) in self.used:
                    continue
                for y in (1, 2, 3):
                    self.t.set(x, y, z, "minecraft:iron_block" if (x + y) % 2 else f"{MOD}:lab_metal_wall")
                self.used.add((x, z))
        self.wall_furn(room, ["trolley", "sink", "medical_cabinet"], 4)
        for x in range(x0 + 3, x1 - 2, 4):
            self.gurney(x, (z0 + z1) // 2 - 1, "south")
            if (x, (z0 + z1) // 2 + 2) not in self.used:
                self.t.set(x, 0, (z0 + z1) // 2 + 2, "minecraft:iron_trapdoor", facing="north", half="top", open=False)
        for _ in range(int((x1 - x0) * (z1 - z0) * 0.03)):
            x, z = self.rnd.randint(x0 + 1, x1 - 1), self.rnd.randint(z0 + 1, z1 - 1)
            if self.free(x, z):
                self.t.set(x, 1, z, "minecraft:snow", layers=1)

    def sp_turbine(self, room):
        """Sala de turbinas: maquinas enormes de 2x2 y tres de alto con cadenas y tuberias por el
        techo, franjas de peligro alrededor."""
        xs, zs = self.inner(room, 2)
        for bx in list(xs)[::5]:
            for bz in list(zs)[::5]:
                if bx + 1 > room["x1"] - 2 or bz + 1 > room["z1"] - 2:
                    continue
                if any((bx + dx, bz + dz) in self.used for dx in (0, 1) for dz in (0, 1)):
                    continue
                for dx in (0, 1):
                    for dz in (0, 1):
                        self.t.fill(bx + dx, 1, bz + dz, bx + dx, 3, bz + dz, f"{MOD}:lab_machine")
                        self.t.set(bx + dx, 4, bz + dz, f"{MOD}:lab_pipes")
                        self.used.add((bx + dx, bz + dz))
                for dx in (-1, 2):
                    for dz in range(-1, 3):
                        if not self.solid(bx + dx, 1, bz + dz):
                            self.t.set(bx + dx, 0, bz + dz, f"{MOD}:lab_hazard")
        for x in range(room["x0"], room["x1"] + 1):
            if not self.solid(x, 4, room["z0"] + 1):
                self.t.set(x, 4, room["z0"] + 1, f"{MOD}:lab_pipes")
        self.wall_furn(room, ["tool_cart", "metal_shelf", "supply_box"], 4)

    def sp_garden(self, room):
        """Jardin del invernadero, comido por la vegetacion: parterres de tierra con helechos y
        azaleas, arbustos de hojas, enredaderas en las paredes y una fuente seca en el centro."""
        r = self.rnd
        x0, z0, x1, z1 = room["x0"], room["z0"], room["x1"], room["z1"]
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        for x in range(x0 + 2, x1 - 1):
            for z in range(z0 + 2, z1 - 1):
                if (x, z) in self.used or abs(x - cx) <= 1 and abs(z - cz) <= 1:
                    continue
                bed = ((x - x0) % 6 in (2, 3)) and ((z - z0) % 7 not in (0,))
                if bed:
                    self.t.set(x, 0, z, "minecraft:rooted_dirt")
                    p = r.random()
                    if p < 0.35:
                        self.setp(x, 1, z, "minecraft:fern")
                    elif p < 0.5:
                        self.setp(x, 1, z, "minecraft:azalea_leaves", persistent=True, distance=1, waterlogged=False)
                    elif p < 0.6:
                        self.setp(x, 1, z, "minecraft:flowering_azalea")
                    elif p < 0.7:
                        self.setp(x, 1, z, "minecraft:dead_bush")
                elif r.random() < 0.12 and self.free(x, z):
                    self.t.set(x, 1, z, "minecraft:moss_carpet")
        # fuente seca
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz:
                    self.setp(cx + dx, 1, cz + dz, "minecraft:mossy_stone_brick_slab", type="bottom")
        self.setp(cx, 1, cz, "minecraft:mossy_stone_brick_wall")
        # enredaderas colgando de las paredes
        for x in range(x0, x1 + 1):
            for (z, face) in ((z0, "north"), (z1, "south")):
                if r.random() < 0.4 and (x, z) not in self.used:
                    for y in range(4, 4 - r.randint(1, 3), -1):
                        if not self.solid(x, y, z):
                            self.t.set(x, y, z, "minecraft:vine", **{face: True})

    def sp_cryo(self, room):
        """Camara de criogenia: un anillo de capsulas alrededor de una consola, columnas de hielo y
        escarcha."""
        cx, cz = (room["x0"] + room["x1"]) // 2, (room["z0"] + room["z1"]) // 2
        rad = max(3, min(room["x1"] - room["x0"], room["z1"] - room["z0"]) // 2 - 2)
        import math as _m
        for k in range(10):
            a = k / 10 * 2 * _m.pi
            x, z = int(round(cx + _m.cos(a) * rad)), int(round(cz + _m.sin(a) * rad))
            if (x, z) in self.used or self.solid(x, 1, z):
                continue
            self.t.set(x, 0, z, "minecraft:sea_lantern")
            self.t.fill(x, 1, z, x, 3, z, "minecraft:light_blue_stained_glass")
            self.t.set(x, 4, z, f"{MOD}:lab_metal_wall")
            self.used.add((x, z))
        for dx in (0, 1):
            self.setp(cx + dx, 1, cz, f"{MOD}:lab_server")
        self.furn("workstation", cx, cz + 1, "south")
        self.furn("office_chair", cx, cz + 2, "north")
        self.wall_furn(room, ["medical_cabinet", "iv_stand"], 3)
        for (dx, dz) in ((-rad - 1, -rad - 1), (rad + 1, -rad - 1), (-rad - 1, rad + 1), (rad + 1, rad + 1)):
            x, z = cx + dx, cz + dz
            if room["x0"] < x < room["x1"] and room["z0"] < z < room["z1"] and (x, z) not in self.used:
                self.t.fill(x, 1, z, x, 4, z, "minecraft:packed_ice")
                self.used.add((x, z))
        for _ in range(30):
            x, z = self.rnd.randint(room["x0"], room["x1"]), self.rnd.randint(room["z0"], room["z1"])
            if self.free(x, z):
                self.t.set(x, 1, z, "minecraft:snow", layers=1)

    SEARCHABLE = {"desk", "filing_cabinet", "medical_cabinet", "tool_cart", "supply_box"}

    def furn(self, name, x, z, f):
        """Mueble (fase 24, tools/furniture.py) en el suelo, con el frente hacia f."""
        if not self.free(x, z):
            return False
        props = {"facing": f}
        if name in self.SEARCHABLE:
            props["open"] = False
        self.t.set(x, 1, z, f"{MOD}:{name}", **props)
        self.used.add((x, z))
        return True

    def wall_furn(self, room, names, n):
        """n muebles contra la pared de la sala, eligiendo al azar de la lista."""
        return self.take(self.wall_spots(room), n, lambda x, z, f: self.furn(self.rnd.choice(names), x, z, f))

    SCATTER = {
        "storage": (["metal_shelf", "metal_shelf", "supply_box", "tool_cart"], 11),
        "morgue": (["trolley", "iv_stand", "sink", "medical_cabinet", "trolley"], 13),
        "ward": (["iv_stand", "trolley", "wheelchair", "medical_cabinet", "iv_stand"], 13),
        "lab": (["workstation", "specimen_shelf", "trolley", "sink", "office_chair"], 15),
        "machine": (["tool_cart", "supply_box", "metal_shelf"], 15),
        "server": (["workstation", "office_chair"], 20),
        "lockers": (["supply_box", "office_chair"], 24),
        "break": (["office_chair", "sink", "workstation"], 16),
    }

    def scatter(self, room, names, n):
        """Muebles por el centro de la sala sin cerrar el paso: cada uno se quita si deja alguna
        celda de la sala sin acceso desde las puertas."""
        cells = [(x, z) for x in range(room["x0"] + 1, room["x1"]) for z in range(room["z0"] + 1, room["z1"])]
        self.rnd.shuffle(cells)
        base = len(self.reach(room))
        placed = 0
        for (x, z) in cells:
            if placed >= n:
                break
            if not self.free(x, z) or self.near_door(x, z):
                continue
            if not self.furn(self.rnd.choice(names), x, z, self.rnd.choice(["north", "south", "east", "west"])):
                continue
            now = len(self.reach(room))
            if now < base - 1:
                self.t.set(x, 1, z, "air")
                self.used.discard((x, z))
                continue
            base = now
            placed += 1
        return placed

    def furnish(self, room):
        kind, r = room["kind"], self.rnd
        self._furnish(room)
        if kind in self.SCATTER:
            names, per = self.SCATTER[kind]
            area = (room["x1"] - room["x0"] + 1) * (room["z1"] - room["z0"] + 1)
            self.scatter(room, names, max(1, area // per))

    def _furnish(self, room):
        kind, r = room["kind"], self.rnd
        if kind in LABELS_SPECIAL:
            getattr(self, "sp_" + kind)(room)
            return
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
            self.take(self.wall_spots(room), 1, lambda x, z, f: self.put(x, z, "minecraft:brewing_stand", y=1))
            self.wall_furn(room, ["workstation"], 1)
            self.wall_furn(room, ["specimen_shelf", "metal_shelf"], 2)
            self.wall_furn(room, ["sink"], 1)
            self.wall_furn(room, ["medical_cabinet", "filing_cabinet"], 1)
        elif kind == "office":
            for x in range(x0 + 1, x1, 3):
                for z in range(z0 + 1, z1, 3):
                    if r.random() < 0.7 and self.furn(r.choice(["desk", "desk", "workstation"]), x, z, "north"):
                        self.furn("office_chair", x, z + 1, "north")
            self.take(spots, 1, lambda x, z, f: self.tall(f"{MOD}:wardrobe", x, z, f))
            self.wall_furn(room, ["filing_cabinet"], 2)
            self.take(self.wall_spots(room), 1, lambda x, z, f: self.put(x, z, "minecraft:bookshelf"))
        elif kind == "storage":
            for x in range(x0 + 1, x1, 3):
                for z in range(z0 + 1, z1):
                    if r.random() < 0.75 and self.put(x, z, f"{MOD}:lab_crate"):
                        if r.random() < 0.6:
                            self.t.set(x, 2, z, f"{MOD}:lab_crate")
            self.take(spots, 2, lambda x, z, f: self.tall(f"{MOD}:waste_container", x, z, f))
            self.wall_furn(room, ["metal_shelf"], 3)
            self.wall_furn(room, ["supply_box", "tool_cart"], 2)
        elif kind == "morgue":
            for x in range(x0 + 1, x1, 2):
                if r.random() < 0.5:
                    self.gurney(x, z0 + 1, "south")
                else:
                    self.gurney(x, z1 - 1, "north")
            self.take(spots, 2, lambda x, z, f: self.tall(f"{MOD}:locker", x, z, f))
            self.wall_furn(room, ["trolley"], 2)
            self.wall_furn(room, ["sink", "medical_cabinet"], 2)
        elif kind == "lockers":
            self.take(spots, 8, lambda x, z, f: self.tall(f"{MOD}:locker", x, z, f))
            self.wall_furn(room, ["supply_box", "sink"], 1)
            for x in range(x0 + 2, x1 - 1, 3):
                self.put(x, (z0 + z1) // 2, "minecraft:spruce_slab", type="bottom")
        elif kind == "ward":
            for x in range(x0 + 1, x1, 3):
                self.gurney(x, z0 + 1, "south")
                self.tall(f"{MOD}:curtain", x + 1, z0 + 1, "east")
                self.furn("iv_stand", x + 2, z0 + 1, "south")
            self.wall_furn(room, ["medical_cabinet", "trolley", "wheelchair"], 3)
        elif kind == "server":
            for x in range(x0 + 1, x1, 2):
                for z in range(z0 + 1, z1, 1):
                    if (z - z0) % 3 != 0 and self.free(x, z):
                        self.t.fill(x, 1, z, x, 2, z, f"{MOD}:lab_server")
                        self.used.add((x, z))
            self.take(self.wall_spots(room), 1, lambda x, z, f: self.tall(f"{MOD}:locker", x, z, f))
            self.wall_furn(room, ["workstation"], 2)
            self.wall_furn(room, ["tool_cart"], 1)
        elif kind == "machine":
            for x in range(x0 + 2, x1 - 1, 4):
                for z in range(z0 + 2, z1 - 1, 4):
                    if self.free(x, z):
                        self.t.fill(x, 1, z, x, 2, z, f"{MOD}:lab_machine")
                        self.t.set(x, 3, z, "minecraft:chain", axis="y")
                        self.t.set(x, 4, z, "minecraft:chain", axis="y")
                        self.used.add((x, z))
            self.take(spots, 1, lambda x, z, f: self.tall(f"{MOD}:waste_container", x, z, f))
            self.wall_furn(room, ["tool_cart"], 2)
            self.wall_furn(room, ["metal_shelf", "supply_box"], 2)
        elif kind == "break":
            for x in range(x0 + 2, x1 - 1, 3):
                z = (z0 + z1) // 2
                if self.put(x, z, "minecraft:spruce_slab", type="top"):
                    self.furn("office_chair", x, z + 1, "north")
            self.take(spots, 1, lambda x, z, f: self.tall(f"{MOD}:wardrobe", x, z, f))
            self.wall_furn(room, ["sink", "metal_shelf"], 2)

    def bloody(self, amount):
        """Sangre en el suelo, huellas y manchas en las paredes y alguna pintada (texturas: tools/textures_lab.py)."""
        t, r = self.t, self.rnd
        walls = {self.theme["wall"], self.theme["wall_low"]}
        marks = {m[0] for m in t.markers}
        xs = [p[0] for p in t.blocks]
        zs = [p[2] for p in t.blocks]
        cells = [(x, z) for x in range(min(xs), max(xs) + 1) for z in range(min(zs), max(zs) + 1)
                 if t.solid(x, 0, z) and not t.solid(x, 1, z) and not t.solid(x, 2, z)
                 and (x, 1, z) not in marks and (x, 2, z) not in marks]
        r.shuffle(cells)
        area = len(cells)
        n_floor = int(area / 140 * amount)
        n_wall = int(area / 260 * amount)
        n_write = max(3, int(area / 1500 * amount))
        dirs = (("south", (0, -1)), ("north", (0, 1)), ("east", (-1, 0)), ("west", (1, 0)))

        def wall_at(x, y, z):
            return t.get(x, y, z) in walls

        # Charcos y rastros en el suelo, mas cerca unos de otros (algo paso por aqui)
        placed = 0
        for (x, z) in cells:
            if placed >= n_floor:
                break
            t.set(x, 1, z, f"{MOD}:blood_floor", variant=r.randrange(6))
            placed += 1
            if r.random() < 0.35:
                dx, dz = r.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
                if (x + dx, z + dz) in set(cells[:0]) or (t.solid(x + dx, 0, z + dz) and not t.solid(x + dx, 1, z + dz)
                                                          and (x + dx, 1, z + dz) not in marks):
                    t.set(x + dx, 1, z + dz, f"{MOD}:blood_floor", variant=r.randrange(6))
        # Manchas en las paredes (manos, pasadas, aranazos, sonrisas)
        placed = 0
        r.shuffle(cells)
        for (x, z) in cells:
            if placed >= n_wall:
                break
            y = r.choice((1, 2, 2))
            if t.solid(x, y, z) or (x, y, z) in marks:
                continue
            for f, (ox, oz) in dirs:
                if wall_at(x + ox, y, z + oz):
                    t.set(x, y, z, f"{MOD}:wall_decal", facing=f, variant=r.randrange(8))
                    placed += 1
                    break
        # Pintadas de dos bloques de ancho a la altura de los ojos
        placed = 0
        r.shuffle(cells)
        side = {"south": (1, 0), "north": (-1, 0), "east": (0, -1), "west": (0, 1)}
        for (x, z) in cells:
            if placed >= n_write:
                break
            y = 2
            for f, (ox, oz) in dirs:
                sx, sz = side[f]                     # hacia la derecha del que mira la pared
                x2, z2 = x + sx, z + sz
                if (wall_at(x + ox, y, z + oz) and wall_at(x2 + ox, y, z2 + oz) and not t.solid(x, y, z)
                        and not t.solid(x2, y, z2) and (x2, y, z2) not in marks and (x, y, z) not in marks):
                    v = r.randrange(8)
                    t.set(x, y, z, f"{MOD}:wall_writing", facing=f, variant=v, right=False)
                    t.set(x2, y, z2, f"{MOD}:wall_writing", facing=f, variant=v, right=True)
                    placed += 1
                    break

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

    PASS = ("_door", "slab", "carpet", "snow", "fern", "dead_bush", "blood_floor", "wall_decal", "wall_writing", "curtain")

    def reach(self, room):
        """Celdas de la sala a las que se llega andando desde sus puertas (los muebles pueden encerrar huecos)."""
        from collections import deque
        inward = {"north": (0, 1), "south": (0, -1), "west": (1, 0), "east": (-1, 0)}

        def open_(x, z):
            for y in (1, 2):
                b = self.t.get(x, y, z)
                if b != "minecraft:air" and not any(k in b for k in self.PASS):
                    return False
            return True

        start = []
        for d in room.get("doors") or ([(*room["door"], room["side"])] if room.get("door") else []):
            x, z, f = d
            dx, dz = inward.get(f, (0, 0))
            start.append((x + dx, z + dz))
        seen = {c for c in start if open_(*c)}
        q = deque(seen)
        while q:
            x, z = q.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, z + dz)
                if n in seen or not (room["x0"] <= n[0] <= room["x1"] and room["z0"] <= n[1] <= room["z1"]):
                    continue
                if open_(*n):
                    seen.add(n)
                    q.append(n)
        return seen

    def fuse_spots(self, per_room=1):
        for room in self.rooms:
            ok = self.reach(room)
            for _ in range(per_room):
                for _ in range(20):
                    x = self.rnd.randint(room["x0"], room["x1"])
                    z = self.rnd.randint(room["z0"], room["z1"])
                    if self.free(x, z) and (x, z) in ok:
                        self.t.marker(x, 1, z, "sp:fuse")
                        self.used.add((x, z))
                        break

    def item_in(self, room, spec):
        ok = self.reach(room)
        for _ in range(40):
            x = self.rnd.randint(room["x0"], room["x1"])
            z = self.rnd.randint(room["z0"], room["z1"])
            if self.free(x, z) and (x, z) in ok:
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

    # Conductos de ventilacion (README 12.9) ---------------------------------------------------------
    def vent_spot(self, room):
        """Hueco del suelo para una rejilla: libre, sin muebles encima y lejos de las puertas."""
        cells = [(x, z) for x in range(room["x0"], room["x1"] + 1) for z in range(room["z0"], room["z1"] + 1)
                 if self.free(x, z) and not self.solid(x, 2, z) and not self.near_door(x, z)]
        self.rnd.shuffle(cells)
        return cells[0] if cells else None

    def vent(self, a, b, fan=None, brake=False, seal=None):
        """Conducto de un bloque de alto bajo el suelo entre dos salas: rejillas en el suelo de cada
        una (y=0), canal en y=-1 con paredes de chapa y fondo en y=-2. `fan`: circuito del ventilador
        (a mitad de camino); `seal`: circuito que sella la rejilla de la sala `b`."""
        t, duct = self.t, f"{MOD}:lab_vent_duct"
        pa, pb = self.vent_spot(a), self.vent_spot(b)
        if not pa or not pb:
            return False
        path, (x, z) = [pa], pa
        while x != pb[0]:
            x += 1 if pb[0] > x else -1
            path.append((x, z))
        while z != pb[1]:
            z += 1 if pb[1] > z else -1
            path.append((x, z))
        if not hasattr(self, "vent_cells"):
            self.vent_cells = set()
        self.vent_cells |= set(path)
        for (x, z) in path:
            t.set(x, -1, z, "air")
            t.set(x, -2, z, duct)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
                n = (x + dx, z + dz)
                if n not in self.vent_cells:
                    t.set(n[0], -1, n[1], duct)
                    t.set(n[0], -2, n[1], duct)
        for (x, z), room, sealed in ((pa, a, None), (pb, b, seal)):
            f = "south" if (x, z) == path[0] and len(path) > 1 and path[1][1] > z else "north"
            nbt = be("vent", Circuit=sealed) if sealed else None
            t.set(x, 0, z, f"{MOD}:vent_grille", nbt=nbt, facing=f, open=False, sealed=bool(sealed))
            self.used.add((x, z))
        if fan and len(path) > 6:
            i = len(path) // 2
            (x, z), (nx, nz) = path[i], path[i + 1]
            f = "east" if nx != x else "south"
            t.set(x, -1, z, f"{MOD}:vent_fan", nbt=be("vent", Circuit=fan, Brake=brake), facing=f,
                  running=brake)
        self.vents = getattr(self, "vents", 0) + 1
        return True

    def vent_between(self, rooms, i, j, **kw):
        """Conducto desde la sala i (o una cercana) hasta la que mas se acerque a la j, a una
        distancia razonable (ni pegadas ni de punta a punta de la planta)."""
        n = len(rooms)
        for u in sorted(range(n), key=lambda k: abs(k - i))[:8]:
            a = rooms[u]
            for v in sorted(range(n), key=lambda k: abs(k - j)):
                b = rooms[v]
                if b is a or getattr(b, "vented", False):
                    continue
                d = abs((a["x0"] + a["x1"]) - (b["x0"] + b["x1"])) // 2 + abs((a["z0"] + a["z1"]) - (b["z0"] + b["z1"])) // 2
                if 14 <= d <= 48 and self.vent(a, b, **kw):
                    return True
        print(f"  {self.t.name}: sin sitio para un conducto desde la sala {i}")
        return False

    def retype(self, room, block, nbt):
        self.place_door(room, block, nbt)


# Plantas asimetricas (particion BSP) ------------------------------------------------------------------
#
# Cada planta se parte al azar: los primeros cortes son pasillos (de 5, 4 y 3 de ancho) y los
# siguientes, paredes entre salas. Asi cada planta tiene su propia red de pasillos, salas de tamanos
# muy distintos y los ascensores en sitios diferentes. Coordenadas: X de 0 a W-1, Z de 0 a L-1.

class Plan(Floor):
    def __init__(self, name, theme, seed):
        super().__init__(name, theme, seed)
        r = self.rnd
        self.W = r.randint(110, 150)
        self.L = r.randint(120, 170)
        self.corr = []          # rectangulos de pasillo (x0, z0, x1, z1, eje)
        self.leaves = []        # rectangulos de sala
        self.entry_x = self.exit_x = 0
        self.doors = []

    # Particion -------------------------------------------------------------------------------------
    def split(self, x0, z0, x1, z1, depth, force=None):
        r = self.rnd
        w, h = x1 - x0 + 1, z1 - z0 + 1
        minr = 9
        corridor = depth <= 2 or (depth == 3 and r.random() < 0.35)
        cw = (5, 4, 3, 3)[min(depth, 3)] if corridor else 1
        gap = cw + 2 if corridor else 1
        big = r.randint(14, 34)
        can_v = w >= 2 * minr + gap
        can_h = h >= 2 * minr + gap
        # Salas de tamanos muy distintos: unas se quedan grandes, otras se parten
        if not corridor and ((w <= big and h <= big and r.random() < 0.8) or w * h < 280):
            can_v = can_h = False
        if not can_v and not can_h:
            self.leaves.append((x0, z0, x1, z1))
            return
        if force:
            vertical = force == "v" and can_v or not can_h
        elif can_v and can_h:
            vertical = r.random() < w / (w + h)
        else:
            vertical = can_v
        if vertical:
            span = w - gap - 2 * minr
            p = x0 + minr + int(span * r.uniform(0.15, 0.85))
            if corridor:
                self.corr.append((p + 1, z0, p + cw, z1, "z"))
                self.split(x0, z0, p - 1, z1, depth + 1, "h" if depth == 0 else None)
                self.split(p + cw + 2, z0, x1, z1, depth + 1, "h" if depth == 0 else None)
            else:
                self.split(x0, z0, p - 1, z1, depth + 1)
                self.split(p + 1, z0, x1, z1, depth + 1)
        else:
            span = h - gap - 2 * minr
            p = z0 + minr + int(span * r.uniform(0.15, 0.85))
            if corridor:
                self.corr.append((x0, p + 1, x1, p + cw, "x"))
                self.split(x0, z0, x1, p - 1, depth + 1)
                self.split(x0, p + cw + 2, x1, z1, depth + 1)
            else:
                self.split(x0, z0, x1, p - 1, depth + 1)
                self.split(x0, p + 1, x1, z1, depth + 1)

    def layout(self, kinds):
        t, th = self.t, self.theme
        W, L = self.W, self.L
        # Todo macizo y luego se vacia (paredes exteriores en -1 y W/L)
        for x in range(-1, W + 1):
            for z in range(-1, L + 1):
                t.set(x, 0, z, th["floor"])
                t.set(x, H + 1, z, th["ceiling"])
                for y in range(1, H + 1):
                    t.set(x, y, z, th["wall_low"] if y <= 2 else th["wall"])
        self.split(0, 0, W - 1, L - 1, 0, force="v")
        for (x0, z0, x1, z1, _) in self.corr:
            self.carve(x0, z0, x1, z1, th["corridor_floor"])
        # Uniones: cada pasillo acaba contra la pared de su pasillo padre; se abre esa pared
        for (x0, z0, x1, z1, axis) in list(self.corr):
            if axis == "x":
                for wx, ox in ((x0 - 1, x0 - 2), (x1 + 1, x1 + 2)):
                    if self.corridor_at(ox, (z0 + z1) // 2):
                        self.carve(wx, z0, wx, z1, th["corridor_floor"])
            else:
                for wz, oz in ((z0 - 1, z0 - 2), (z1 + 1, z1 + 2)):
                    if self.corridor_at((x0 + x1) // 2, oz):
                        self.carve(x0, wz, x1, wz, th["corridor_floor"])
        for i, (x0, z0, x1, z1) in enumerate(self.leaves):
            kind = self.rnd.choice(kinds)
            area = (x1 - x0 + 1) * (z1 - z0 + 1)
            if area > 300:
                kind = self.rnd.choice(["storage", "machine", "lab", "ward"] if "storage" in kinds else kinds)
            self.carve(x0, z0, x1, z1, th["room_floor"].get(kind, th["floor"]), corridor=False)
            self.rooms.append({"id": i, "x0": x0, "z0": z0, "x1": x1, "z1": z1, "kind": kind,
                               "doors": [], "door": None, "side": "s"})
        self.pick_special()
        self.connect_rooms()
        self.place_cabins()

    def pick_special(self):
        """La sala singular de la planta (docs/PRUEBAS.md, mapa): la mas grande que tenga buena forma."""
        for kind in self.theme.get("special", []):
            ok = [r for r in self.rooms if r["kind"] not in LABELS_SPECIAL and r["x1"] - r["x0"] >= 10 and r["z1"] - r["z0"] >= 10]
            if not ok:
                continue
            room = max(ok, key=lambda r: (r["x1"] - r["x0"] + 1) * (r["z1"] - r["z0"] + 1))
            room["kind"] = kind
            fl = f"{MOD}:" + SPECIAL_FLOOR[kind]
            if fl:
                for x in range(room["x0"], room["x1"] + 1):
                    for z in range(room["z0"], room["z1"] + 1):
                        self.t.set(x, 0, z, fl)

    def corridor_at(self, x, z):
        for (x0, z0, x1, z1, _) in self.corr:
            if x0 <= x <= x1 and z0 <= z <= z1:
                return True
        return False

    def room_at(self, x, z):
        for r in self.rooms:
            if r["x0"] <= x <= r["x1"] and r["z0"] <= z <= r["z1"]:
                return r
        return None

    def connect_rooms(self):
        """Una o dos puertas al pasillo; las salas interiores se abren a una vecina conectada."""
        r = self.rnd
        connected = set()
        cands_by_room = {}
        for room in self.rooms:
            cands, neigh = [], []
            x0, z0, x1, z1 = room["x0"], room["z0"], room["x1"], room["z1"]
            for x in range(x0 + 1, x1):
                for (wz, oz, f) in ((z0 - 1, z0 - 2, "north"), (z1 + 1, z1 + 2, "south")):
                    if self.corridor_at(x, oz):
                        cands.append((x, wz, f))
                    else:
                        o = self.room_at(x, oz)
                        if o:
                            neigh.append((x, wz, f, o["id"]))
            for z in range(z0 + 1, z1):
                for (wx, ox, f) in ((x0 - 1, x0 - 2, "west"), (x1 + 1, x1 + 2, "east")):
                    if self.corridor_at(ox, z):
                        cands.append((wx, z, f))
                    else:
                        o = self.room_at(ox, z)
                        if o:
                            neigh.append((wx, z, f, o["id"]))
            cands_by_room[room["id"]] = (cands, neigh)
            if cands:
                r.shuffle(cands)
                n = 2 if (x1 - x0) * (z1 - z0) > 180 and len(cands) > 6 else 1
                picked = [cands[0]]
                for c in cands[1:]:
                    if len(picked) >= n:
                        break
                    if abs(c[0] - picked[0][0]) + abs(c[1] - picked[0][1]) > 6:
                        picked.append(c)
                for (x, z, f) in picked:
                    self.add_door(room, x, z, f)
                connected.add(room["id"])
        # Salas interiores: puerta a una vecina ya conectada (como una suite)
        for _ in range(len(self.rooms)):
            changed = False
            for room in self.rooms:
                if room["id"] in connected:
                    continue
                _, neigh = cands_by_room[room["id"]]
                opts = [n for n in neigh if n[3] in connected]
                if opts:
                    x, z, f, _ = r.choice(opts)
                    self.add_door(room, x, z, f)
                    connected.add(room["id"])
                    changed = True
            if not changed:
                break
        # Lo que quede aislado (raro) se descarta como sala: queda macizo
        self.rooms = [room for room in self.rooms if room["id"] in connected]
        for i, room in enumerate(self.rooms):
            room["id"] = i
            room["door"] = room["doors"][0][:2]
            self.sign(room)

    def add_door(self, room, x, z, f, block=None, nbt=None):
        block = block or f"{MOD}:lab_door"
        # f: lado de la sala donde esta la pared; la puerta mira hacia dentro de la sala
        inside = {"north": "south", "south": "north", "west": "east", "east": "west"}[f]
        self.t.clear(x, 1, z, x, 2, z)
        self.t.set(x, 1, z, block, nbt=nbt, facing=inside, half="lower", hinge="left", open=False, powered=False)
        self.t.set(x, 2, z, block, facing=inside, half="upper", hinge="left", open=False, powered=False)
        room["doors"].append((x, z, f))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                self.used.add((x + dx, z + dz))

    def place_door(self, room, block, nbt=None):
        for (x, z, f) in room["doors"]:
            inside = {"north": "south", "south": "north", "west": "east", "east": "west"}[f]
            self.t.set(x, 1, z, block, nbt=nbt, facing=inside, half="lower", hinge="left", open=False, powered=False)
            self.t.set(x, 2, z, block, facing=inside, half="upper", hinge="left", open=False, powered=False)

    def sign(self, room):
        x, z, f = room["doors"][0]
        # El cartel va en el lado de fuera, al lado de la puerta
        out = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}[f]
        side = (1, 0) if f in ("north", "south") else (0, 1)
        for s in (1, -1):
            sx, sz = x + side[0] * s + out[0], z + side[1] * s + out[1]
            wx, wz = x + side[0] * s, z + side[1] * s
            if self.solid(wx, 3, wz) and not self.solid(sx, 3, sz):
                label = LABELS.get(room["kind"], "SALA")
                self.t.set(sx, 3, sz, "minecraft:birch_wall_sign", nbt=sign_nbt(f"{label} {room['id'] + 1:02d}"), facing=f)
                return

    def place_cabins(self):
        t = self.t
        south = [c for c in self.corr if c[4] == "z" and c[1] == 0]
        north = [c for c in self.corr if c[4] == "z" and c[3] == self.L - 1]
        e = self.rnd.choice(south)
        self.entry_x = (e[0] + e[2]) // 2
        # La salida, lo mas lejos posible en X de la llegada
        north.sort(key=lambda c: -abs((c[0] + c[2]) // 2 - self.entry_x))
        x = north[0]
        self.exit_x = (x[0] + x[2]) // 2
        cabin(t, self.entry_x, 0, -7, facing_south=True, doors_open=True)
        t.marker(self.entry_x, 1, -4, "sp:entry 0")
        cabin(t, self.exit_x, 0, self.L, facing_south=False, doors_open=False, exit_markers=True)
        # Abrir la pared exterior delante de cada cabina (las puertas del ascensor la ocupan)
        self.entry = (self.entry_x, 0)

    def reception(self):
        pass

    def by_distance(self):
        ex = self.entry_x
        return sorted(self.rooms, key=lambda r: abs((r["x0"] + r["x1"]) / 2 - ex) + (r["z0"] + r["z1"]) / 2)

    def lights(self):
        for (x0, z0, x1, z1, axis) in self.corr:
            if axis == "z":
                x = (x0 + x1) // 2
                for z in range(z0 + 3, z1, 7):
                    self.light(x, z, self.pick_light())
            else:
                z = (z0 + z1) // 2
                for x in range(x0 + 3, x1, 7):
                    if not self.solid(x, H + 1, z) or self.t.get(x, H + 1, z) != f"{MOD}:fluorescent_light":
                        self.light(x, z, self.pick_light())
        for room in self.rooms:
            w, h = room["x1"] - room["x0"], room["z1"] - room["z0"]
            step = 8
            for x in range(room["x0"] + w % step // 2 + 2, room["x1"], step):
                for z in range(room["z0"] + h % step // 2 + 2, room["z1"], step):
                    self.light(x, z, self.pick_light(room=True))
            if w < 8 and h < 8:
                self.light((room["x0"] + room["x1"]) // 2, (room["z0"] + room["z1"]) // 2, self.pick_light(room=True))
        self.light(self.entry_x, 2, "flicker")
        self.light(self.exit_x, self.L - 3, "circuit")

    def corridor_dressing(self, lockers, debris, pipes):
        self.corridor_cells = [c for c in self.corridor_cells if 3 < c[1] < self.L - 3]
        cells = list(self.corridor_cells)
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
            if self.free(x, z) and not self.near_door(x, z) and abs(x - self.entry_x) > 3:
                self.put(x, z, self.rnd.choice(self.theme["debris"]))
                k += 1
        # Tuberias bajas cruzando pasillos (dive o agachado)
        corr = [c for c in self.corr if c[2] - c[0] >= 2 and c[3] - c[1] >= 2]
        self.rnd.shuffle(corr)
        for (x0, z0, x1, z1, axis) in corr[:pipes]:
            if axis == "x":
                x = self.rnd.randint(x0 + 4, max(x0 + 4, x1 - 4))
                rng = [(x, z) for z in range(z0, z1 + 1)]
            else:
                z = self.rnd.randint(z0 + 8, max(z0 + 8, z1 - 8))
                rng = [(x, z) for x in range(x0, x1 + 1)]
            for (x, z) in rng:
                if (x, z) in self.used:
                    continue
                self.t.set(x, 2, z, self.theme["pipe"])
                self.t.set(x, 3, z, "minecraft:iron_bars")
                self.used.add((x, z))

    def near_door(self, x, z):
        for room in self.rooms:
            for (dx, dz, _) in room["doors"]:
                if abs(dx - x) <= 2 and abs(dz - z) <= 2:
                    return True
        return False

    def mobs(self, n):
        cells = [c for c in self.corridor_cells if c[1] > self.L // 4]
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


def sign_nbt(*lines):
    msgs = [json.dumps({"text": l}) for l in lines] + [json.dumps({"text": ""})] * (4 - len(lines))
    text = Compound({"messages": List[String]([String(m) for m in msgs[:4]]), "color": String("black"),
                     "has_glowing_text": Byte(0)})
    empty = Compound({"messages": List[String]([String(json.dumps({"text": ""}))] * 4), "color": String("black"),
                      "has_glowing_text": Byte(0)})
    return Compound({"id": String("minecraft:sign"), "front_text": text, "back_text": empty, "is_waxed": Byte(1)})


SPECIAL_FLOOR = {"reception": "lab_tile_floor_dark", "warehouse": "lab_concrete_floor", "openoffice": "lab_linoleum",
                 "coldroom": "lab_morgue_floor", "turbine": "lab_grate", "garden": "lab_moss_floor", "cryo": "lab_tile_floor_dark"}
LABELS_SPECIAL = {"reception", "warehouse", "openoffice", "coldroom", "turbine", "garden", "cryo"}
LABELS = {"reception": "RECEPCION", "warehouse": "NAVE", "openoffice": "OFICINAS", "coldroom": "CAMARAS",
          "turbine": "TURBINAS", "garden": "JARDIN", "cryo": "CRIOGENIA",
          "lab": "LAB", "office": "DESPACHO", "storage": "ALMACEN", "morgue": "MORGUE", "lockers": "VESTUARIO",
          "ward": "SALA", "server": "SERVIDORES", "machine": "MAQUINAS", "break": "DESCANSO"}


# Temas ---------------------------------------------------------------------------------------------------

def theme(**kw):
    def lab(n):
        return f"{MOD}:{n}"
    base = dict(floor=lab("lab_tile_floor"), corridor_floor=lab("lab_linoleum"), hall_floor=lab("lab_tile_floor_dark"),
                wall=lab("lab_plaster"), wall_low=lab("lab_wall_tiles"), ceiling=lab("lab_ceiling"),
                pipe=lab("lab_pipes"), debris=(lab("lab_crate"), lab("lab_debris"), lab("lab_debris")),
                room_floor={}, blood=1.0)
    for k, v in kw.items():
        if isinstance(v, str):
            v = lab(v)
        elif k == "room_floor":
            v = {kk: lab(vv) for kk, vv in v.items()}
        base[k] = v
    return base


THEMES = {
    "start": theme(special=["reception"], room_floor={"office": "lab_wood_floor", "lockers": "lab_tile_floor_dark", "storage": "lab_concrete_floor"},
                   blood=0.6),
    "storage": theme(special=["warehouse"], wall="lab_concrete_wall", wall_low="lab_concrete_wall", ceiling="lab_ceiling_concrete",
                     corridor_floor="lab_concrete_floor", floor="lab_concrete_floor",
                     room_floor={"machine": "lab_grate", "office": "lab_linoleum"}),
    "offices": theme(special=["openoffice"], wall="lab_plaster_blue", wall_low="lab_wall_tiles_white",
                     room_floor={"office": "lab_wood_floor", "break": "lab_linoleum"}),
    "morgue": theme(special=["coldroom"], wall="lab_plaster_green", wall_low="lab_wall_tiles_teal", corridor_floor="lab_tile_floor_dark",
                    room_floor={"morgue": "lab_morgue_floor", "ward": "lab_morgue_floor"}, blood=1.6),
    "generators": theme(special=["turbine"], wall="lab_metal_wall", wall_low="lab_concrete_wall", ceiling="lab_ceiling_concrete",
                        corridor_floor="lab_concrete_floor", floor="lab_concrete_floor",
                        room_floor={"machine": "lab_grate", "server": "lab_tile_floor_dark"}),
    "greenhouse": theme(special=["garden"], wall="lab_plaster_green", wall_low="lab_wall_tiles", corridor_floor="lab_moss_floor",
                        floor="lab_moss_floor", room_floor={"office": "lab_wood_floor"}),
    "final": theme(special=["cryo"], wall="lab_wall_tiles_white", wall_low="lab_wall_tiles_teal", corridor_floor="lab_tile_floor_dark",
                   room_floor={"lab": "lab_morgue_floor"}, blood=2.0),
}


# Contenido especial de cada planta -------------------------------------------------------------------------

def apply_start(f):
    rooms = f.by_distance()
    f.fusebox(rooms[1], slots=1)
    f.fusebox(rooms[-1], slots=1)
    f.vent_between(rooms, 0, len(rooms) // 2)


def apply_storage(f):
    rooms = f.by_distance()
    locked = rooms[-2]
    f.retype(locked, f"{MOD}:security_door", be("lab_door", Type="locked", Param="red"))
    f.fusebox(locked)
    f.fusebox(rooms[len(rooms) // 2])
    f.item_in(rooms[len(rooms) // 3], "key red")
    f.vent_between(rooms, len(rooms) // 4, len(rooms) - 3)


def apply_offices(f):
    rooms = f.by_distance()
    sec = rooms[-1]
    f.retype(sec, f"{MOD}:security_door", be("lab_door", Type="keycard", Param="1"))
    f.fusebox(sec)
    f.fusebox(rooms[len(rooms) // 2])
    f.item_in(rooms[len(rooms) // 4], "keycard 1")
    f.vent_between(rooms, 1, len(rooms) // 2)
    f.vent_between(rooms, len(rooms) // 2 + 1, len(rooms) - 2)


def apply_morgue(f):
    rooms = f.by_distance()
    f.retype(rooms[-1], f"{MOD}:security_door", be("lab_door", Type="stuck"))
    f.fusebox(rooms[-1])
    locked = rooms[len(rooms) // 2]
    f.retype(locked, f"{MOD}:security_door", be("lab_door", Type="locked", Param="blue", Consume=True))
    f.fusebox(locked)
    f.item_in(rooms[len(rooms) // 4], "key blue")
    # atajo sellado: se abre con su propia caja de fusibles
    f.fusebox(rooms[len(rooms) // 3], circuit="vent", slots=1)
    f.vent_between(rooms, len(rooms) // 3, len(rooms) - 3, seal="vent")


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
    # el ventilador del conducto se frena con el circuito auxiliar
    f.vent_between(rooms, len(rooms) // 4, len(rooms) - 4, fan="aux", brake=True)


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
    f.vent_between(rooms, 1, len(rooms) // 2)
    f.vent_between(rooms, len(rooms) // 3, len(rooms) - 3)


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
                f.t.set(x, 4, z, f"{MOD}:lab_metal_wall")
                f.used.add((x, z))
    for r in big:
        f.fusebox(r)
    f.retype(rooms[-1], f"{MOD}:security_door", be("lab_door", Type="keycard", Param="3"))
    f.fusebox(rooms[-1])
    f.item_in(rooms[len(rooms) // 2], "keycard 3")
    f.vent_between(rooms, len(rooms) // 4, len(rooms) - 3)


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
     dict(lockers=10, debris=8, pipes=2, mobs=8), "intermediate", 4, "Criogenización y experimentos"),
]


def make_floor(name, theme_name, seed, kinds, apply, extra):
    f = Plan(name, THEMES[theme_name], seed)
    f.layout(kinds)
    for room in f.rooms:
        f.furnish(room)
    apply(f)
    f.lights()
    f.corridor_dressing(extra["lockers"], extra["debris"], extra["pipes"])
    f.fuse_spots(per_room=1)
    f.mobs(extra["mobs"])
    f.bloody(f.theme["blood"] * (0.6 + 0.25 * extra.get("diff", 1)))
    return f



def verify(path):
    """Comprueba la planta: sin huecos al vacio y todo alcanzable desde la llegada (salida, fusibles, objetos)."""
    from collections import deque
    import nbtlib
    t = nbtlib.load(path)
    pal = [str(p["Name"]) for p in t["palette"]]
    solid, marks, oy = {}, {}, 0
    for b in t["blocks"]:
        x, y, z = [int(v) for v in b["pos"]]
        if "nbt" in b and "metadata" in b["nbt"]:
            meta = str(b["nbt"]["metadata"]).split()[0]
            marks.setdefault(meta, []).append((x, z))
            if meta == "sp:entry":
                oy = y - 1      # las plantillas con conductos empiezan por debajo del suelo
            continue
        solid[(x, y, z)] = pal[int(b["state"])]
    solid = {(x, y - oy, z): v for (x, y, z), v in solid.items()}
    grilles = sum(1 for (x, y, z), v in solid.items() if y == 0 and v.endswith("vent_grille"))
    floor = {(x, z) for (x, y, z) in solid if y == 0}

    def ok(b):
        return b is None or any(k in b for k in ("_door", "slab", "barrel", "gurney", "curtain", "iron_block", "iron_bars",
                                                   "lab_crate", "lab_debris", "lab_pipes", "blood_floor", "wall_decal", "snow", "carpet", "fern",
                                                   "dead_bush",
                                                   "wall_writing"))

    def passable(x, z):
        return (x, z) in floor and ok(solid.get((x, 1, z))) and ok(solid.get((x, 2, z)))

    start = marks["sp:entry"][0]
    seen, q = {start}, deque([start])
    while q:
        x, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n not in seen and passable(*n):
                seen.add(n)
                q.append(n)
    problems = []
    e = marks["sp:exit_cabin"]
    if ((e[0][0] + e[1][0]) // 2, (e[0][1] + e[1][1]) // 2) not in seen:
        problems.append("salida inalcanzable")
    for k in ("sp:fuse", "sp:item"):
        lost = [c for c in marks.get(k, []) if c not in seen]
        if lost:
            problems.append(f"{len(lost)} {k} inalcanzables")
    for (x, z) in floor:
        if (x, 1, z) in solid:
            continue
        if (x, 5, z) not in solid:
            problems.append(f"sin techo en {x},{z}")
            break
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n not in floor and (n[0], 1, n[1]) not in solid and (n[0], 2, n[1]) not in solid:
                problems.append(f"hueco al vacio en {n[0]},{n[1]}")
                break
    if grilles and grilles % 2:
        problems.append(f"{grilles} rejillas (cada conducto tiene dos)")
    if problems:
        raise SystemExit(f"{path.name}: " + "; ".join(problems[:5]))
    return grilles

def main():
    pool = []
    for name, theme_name, seed, kinds, apply, extra, kind, diff, title in FLOORS:
        f = make_floor(name, theme_name, seed, kinds, apply, dict(extra, diff=diff))
        size, nb, nm = f.t.save()
        grilles = verify(ROOT / "structure/zones" / f"{name}.nbt")
        zone = {"template": f"{MOD}:zones/{name}", "kind": kind, "difficulty": diff, "entry_yaw": 0, "name": title}
        path = ROOT / "zone" / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(zone, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        if kind == "intermediate":
            pool.append(f"{MOD}:{name}")
        print(f"{name:16} {size[0]}x{size[1]}x{size[2]}  bloques={nb} marcadores={nm} salas={len(f.rooms)} conductos={grilles // 2}")
    (ROOT / "zone_pool").mkdir(parents=True, exist_ok=True)
    (ROOT / "zone_pool/default.json").write_text(json.dumps({"zones": pool}, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
