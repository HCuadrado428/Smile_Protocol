#!/usr/bin/env python3
"""
Zonas PROVISIONALES (plantillas NBT + JSON) hasta que se disene el mapa (README 22).

Cada zona es un laberinto oscuro de pasillos de 3 bloques con salas, generado con una semilla
fija: puertas (normales, con llave, con tarjeta, hidraulicas, atascadas), cajas de fusibles del
circuito «exit» que da corriente al ascensor de salida, valvulas, escondites (taquillas,
armarios, camillas, cortinas, contenedores), obstaculos para el vault, tuberias bajas para el
dive y muy pocas luces (casi todas apagadas o ligadas al circuito).

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
C = 4          # tamano de celda: 3 de pasillo + 1 de pared
H = 4          # altura de las paredes (techo en y = 5)
DIRS = [(1, 0), (-1, 0), (0, 1), (0, -1)]
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


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


# Laberinto ---------------------------------------------------------------------------------------

class Maze:
    """Laberinto de n x m celdas con salas, enlaces (pasillos abiertos) y puertas."""

    def __init__(self, n, m, rnd, loops=0.14):
        self.n, self.m, self.rnd = n, m, rnd
        self.links = set()
        self.doors = {}            # enlace -> sala
        self.room_of = {}
        self.rooms = []
        stack = [(n // 2, 0)]
        seen = {stack[0]}
        while stack:
            c = stack[-1]
            opts = [(c[0] + dx, c[1] + dz) for dx, dz in DIRS
                    if 0 <= c[0] + dx < n and 0 <= c[1] + dz < m and (c[0] + dx, c[1] + dz) not in seen]
            if not opts:
                stack.pop()
                continue
            nxt = rnd.choice(opts)
            self.links.add(frozenset((c, nxt)))
            seen.add(nxt)
            stack.append(nxt)
        # Bucles: rutas alternativas para huir
        for i in range(n):
            for j in range(m):
                for dx, dz in ((1, 0), (0, 1)):
                    o = (i + dx, j + dz)
                    if o[0] < n and o[1] < m and rnd.random() < loops:
                        self.links.add(frozenset(((i, j), o)))

    def add_room(self, i, j, w, h, kind):
        cells = [(a, b) for a in range(i, i + w) for b in range(j, j + h)]
        if any(c in self.room_of for c in cells):
            return None
        room = {"id": len(self.rooms), "i": i, "j": j, "w": w, "h": h, "kind": kind, "cells": cells, "doors": []}
        for c in cells:
            self.room_of[c] = room["id"]
        self.rooms.append(room)
        return room

    def finalize(self, max_doors=2, entry=(0, 0)):
        rnd = self.rnd
        # Dentro de una sala todo abierto
        for r in self.rooms:
            for c in r["cells"]:
                for dx, dz in DIRS:
                    o = (c[0] + dx, c[1] + dz)
                    if self.room_of.get(o) == r["id"]:
                        self.links.add(frozenset((c, o)))
        # Cruces de borde de sala: unos pocos son puertas, el resto pared
        for r in self.rooms:
            crossing = [l for l in self.links if self._crosses(l, r["id"])]
            rnd.shuffle(crossing)
            keep = crossing[:max_doors]
            for l in crossing[max_doors:]:
                self.links.discard(l)
            for l in keep:
                self.doors[l] = r["id"]
                r["doors"].append(l)
        # Que todo siga conectado
        self._connect(entry)

    def _crosses(self, link, rid):
        a, b = tuple(link)
        ra, rb = self.room_of.get(a), self.room_of.get(b)
        return (ra == rid) != (rb == rid)

    def _reach(self, start):
        seen = {start}
        q = [start]
        while q:
            c = q.pop()
            for dx, dz in DIRS:
                o = (c[0] + dx, c[1] + dz)
                if o not in seen and frozenset((c, o)) in self.links:
                    seen.add(o)
                    q.append(o)
        return seen

    def _connect(self, entry):
        all_cells = {(i, j) for i in range(self.n) for j in range(self.m)}
        for _ in range(500):
            reach = self._reach(entry)
            if reach >= all_cells:
                return
            cands = []
            for c in reach:
                for dx, dz in DIRS:
                    o = (c[0] + dx, c[1] + dz)
                    if o in all_cells and o not in reach:
                        rc, ro = self.room_of.get(c), self.room_of.get(o)
                        if rc is None and ro is None:
                            cands.append((0, c, o))
                        else:
                            cands.append((1, c, o))
            cands.sort(key=lambda t: t[0])
            best = [t for t in cands if t[0] == cands[0][0]]
            _, c, o = self.rnd.choice(best)
            l = frozenset((c, o))
            self.links.add(l)
            rid = self.room_of.get(o) if self.room_of.get(o) is not None else self.room_of.get(c)
            if rid is not None:
                self.doors[l] = rid
                self.rooms[rid]["doors"].append(l)

    def dead_ends(self):
        out = []
        for i in range(self.n):
            for j in range(self.m):
                if (i, j) in self.room_of:
                    continue
                k = sum(1 for dx, dz in DIRS if frozenset(((i, j), (i + dx, j + dz))) in self.links)
                if k == 1:
                    out.append((i, j))
        return out


class Zone:
    """Rasteriza un laberinto con su tema y coloca mecanismos."""

    def __init__(self, name, n, m, theme, seed):
        self.t = Template(name)
        self.rnd = random.Random(seed)
        self.n, self.m = n, m
        self.theme = theme
        self.cx = 0
        self.x0 = self.cx - 2 - C * (n // 2)
        self.z0 = 6
        self.maze = Maze(n, m, self.rnd)
        self.used = set()       # posiciones ocupadas por mobiliario
        self.fuse_spots = []

    # Coordenadas
    def cell_min(self, i, j):
        return self.x0 + 1 + C * i, self.z0 + 1 + C * j

    def cell_center(self, i, j):
        x, z = self.cell_min(i, j)
        return x + 1, z + 1

    def build(self):
        t, th = self.t, self.theme
        W = C * self.n + 1
        L = C * self.m + 1
        x1, z1 = self.x0 + W - 1, self.z0 + L - 1
        # Bloque macizo y luego se vacia
        for x in range(self.x0, x1 + 1):
            for z in range(self.z0, z1 + 1):
                t.set(x, 0, z, th["floor"][(x + z) % 2])
                t.set(x, H + 1, z, th["ceiling"])
                for y in range(1, H + 1):
                    t.set(x, y, z, th["wall_low"] if y <= 1 else th["wall"])
        for i in range(self.n):
            for j in range(self.m):
                x, z = self.cell_min(i, j)
                t.clear(x, 1, z, x + 2, H, z + 2)
        for link in self.maze.links:
            a, b = sorted(link)
            self._open(a, b, link in self.maze.doors)
        # Salas: sin postes interiores
        for r in self.maze.rooms:
            xa, za = self.cell_min(r["i"], r["j"])
            xb, zb = self.cell_min(r["i"] + r["w"] - 1, r["j"] + r["h"] - 1)
            t.clear(xa, 1, za, xb + 2, H, zb + 2)
            for x in range(xa, xb + 3):
                for z in range(za, zb + 3):
                    t.set(x, 0, z, th["room_floor"][(x // 2 + z // 2) % 2])

    def _open(self, a, b, door):
        t = self.t
        (i, j), (k, l) = a, b
        if k != i:  # pared en x
            wx = self.x0 + C * max(i, k)
            _, z = self.cell_min(i, j)
            if door:
                t.clear(wx, 1, z, wx, H, z + 2)
                for zz in (z, z + 2):
                    for y in range(1, H + 1):
                        t.set(wx, y, zz, self.theme["frame"])
                t.fill(wx, 3, z + 1, wx, H, z + 1, self.theme["frame"])
                self.door_slots.append((wx, z + 1, "x", a, b))
            else:
                t.clear(wx, 1, z, wx, H, z + 2)
        else:       # pared en z
            wz = self.z0 + C * max(j, l)
            x, _ = self.cell_min(i, j)
            if door:
                t.clear(x, 1, wz, x + 2, H, wz)
                for xx in (x, x + 2):
                    for y in range(1, H + 1):
                        t.set(xx, y, wz, self.theme["frame"])
                t.fill(x + 1, 3, wz, x + 1, H, wz, self.theme["frame"])
                self.door_slots.append((x + 1, wz, "z", a, b))
            else:
                t.clear(x, 1, wz, x + 2, H, wz)

    door_slots = None

    def place_door(self, slot, block, nbt=None):
        x, z, axis, a, b = slot
        rid = self.maze.doors.get(frozenset((a, b)))
        inside = a if self.maze.room_of.get(a) == rid else b
        if axis == "x":
            facing = "east" if inside[0] > min(a[0], b[0]) else "west"
        else:
            facing = "south" if inside[1] > min(a[1], b[1]) else "north"
        self.t.set(x, 1, z, block, nbt=nbt, facing=facing, half="lower", hinge="left", open=False, powered=False)
        self.t.set(x, 2, z, block, facing=facing, half="upper", hinge="left", open=False, powered=False)
        self.used.add((x, z))

    # Mobiliario --------------------------------------------------------------------------------------

    def wall_spots(self, cells, need_free_front=True):
        """Huecos junto a una pared dentro de esas celdas: (x, z, facing hacia dentro)."""
        out = []
        t = self.t
        for (i, j) in cells:
            x0, z0 = self.cell_min(i, j)
            for x in range(x0, x0 + 3):
                for z in range(z0, z0 + 3):
                    if t.solid(x, 1, z) or (x, z) in self.used:
                        continue
                    for f, (dx, dz) in (("south", (0, -1)), ("north", (0, 1)), ("east", (-1, 0)), ("west", (1, 0))):
                        if t.solid(x + dx, 2, z + dz) and t.solid(x + dx, 1, z + dz):
                            fx, fz = x - dx, z - dz
                            if need_free_front and (t.solid(fx, 1, fz) or (fx, fz) in self.used):
                                continue
                            out.append((x, z, f))
        self.rnd.shuffle(out)
        return out

    def take(self, spots, n, fn):
        placed = 0
        for (x, z, f) in spots:
            if placed >= n:
                break
            if (x, z) in self.used:
                continue
            if fn(x, z, f):
                self.used.add((x, z))
                placed += 1
        return placed

    def tall(self, block, x, z, f, nbt=None):
        if self.t.solid(x, 1, z) or self.t.solid(x, 2, z):
            return False
        self.t.set(x, 1, z, block, nbt=nbt, facing=f, half="lower", open=False)
        self.t.set(x, 2, z, block, facing=f, half="upper", open=False)
        return True

    def light(self, x, z, lit, flickers=False, circuit=None):
        nbt = be("fluorescent_light", Circuit=circuit) if circuit else None
        self.t.set(x, H + 1, z, f"{MOD}:fluorescent_light", nbt=nbt, lit=lit, flickers=flickers)

    def lights(self, p_circuit=0.06, p_flicker=0.035, p_dead=0.12):
        for i in range(self.n):
            for j in range(self.m):
                if (i, j) in self.maze.room_of:
                    continue
                x, z = self.cell_center(i, j)
                r = self.rnd.random()
                if r < p_circuit:
                    self.light(x, z, False, circuit="exit")
                elif r < p_circuit + p_flicker:
                    self.light(x, z, True, flickers=True)
                elif r < p_circuit + p_flicker + p_dead:
                    self.light(x, z, False)
        for r in self.maze.rooms:
            xa, za = self.cell_min(r["i"], r["j"])
            xb, zb = self.cell_min(r["i"] + r["w"] - 1, r["j"] + r["h"] - 1)
            x, z = (xa + xb + 2) // 2, (za + zb + 2) // 2
            k = self.rnd.random()
            if k < 0.45:
                self.light(x, z, False, circuit="exit")
            elif k < 0.7:
                self.light(x, z, True, flickers=True)
            else:
                self.light(x, z, False)

    def fusebox(self, room, circuit="exit", slots=2, accepts=""):
        spots = self.wall_spots(room["cells"])
        def put(x, z, f):
            nbt = be("fuse_box", Circuit=circuit, Slots=slots, Accepts=accepts)
            self.t.set(x, 2, z, f"{MOD}:fuse_box", nbt=nbt, facing=f, filled=0, powered=False)
            return True
        return self.take(spots, 1, put)

    def valve(self, room, group, order=0, coop=False, wheel=True):
        spots = self.wall_spots(room["cells"])
        def put(x, z, f):
            nbt = be("valve", Group=group, Order=order, Coop=coop)
            self.t.set(x, 2, z, f"{MOD}:valve", nbt=nbt, facing=f, turning=False, turned=False, wheel=wheel)
            return True
        return self.take(spots, 1, put)

    def item(self, cell, spec):
        x, z = self.cell_center(*cell)
        for dx, dz in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
            if not self.t.solid(x + dx, 1, z + dz) and (x + dx, z + dz) not in self.used:
                self.t.marker(x + dx, 1, z + dz, f"sp:item {spec}")
                self.used.add((x + dx, z + dz))
                return True
        return False

    def fuse_spot(self, cell):
        x, z = self.cell_center(*cell)
        for dx, dz in ((1, 1), (-1, -1), (1, -1), (-1, 1), (0, 0)):
            if not self.t.solid(x + dx, 1, z + dz) and (x + dx, z + dz) not in self.used:
                self.t.marker(x + dx, 1, z + dz, "sp:fuse")
                self.used.add((x + dx, z + dz))
                return True
        return False

    def obstacles(self, n):
        cells = [(i, j) for i in range(self.n) for j in range(1, self.m - 1) if (i, j) not in self.maze.room_of]
        self.rnd.shuffle(cells)
        for (i, j) in cells[:n]:
            x, z = self.cell_center(i, j)
            if (x, z) in self.used:
                continue
            # Obstaculo de 1 bloque en medio del pasillo: se rodea o se salta (vault)
            self.t.set(x, 1, z, self.rnd.choice(self.theme["obstacles"]), **self.theme.get("obstacle_props", {}))
            self.used.add((x, z))

    def low_pipes(self, n):
        """Tuberias a la altura del pecho que obligan a pasar agachado o con dive."""
        done = 0
        links = [l for l in self.maze.links if l not in self.maze.doors]
        self.rnd.shuffle(links)
        for l in links:
            if done >= n:
                break
            a, b = sorted(l)
            if a in self.maze.room_of or b in self.maze.room_of:
                continue
            (i, j), (k, _) = a, b
            if k != i:
                wx = self.x0 + C * max(i, k)
                _, z = self.cell_min(i, j)
                self.t.fill(wx, 2, z, wx, H, z + 2, self.theme["pipe"], **self.theme.get("pipe_props_x", {}))
            else:
                wz = self.z0 + C * (j + 1)
                x, _ = self.cell_min(i, j)
                self.t.fill(x, 2, wz, x + 2, H, wz, self.theme["pipe"], **self.theme.get("pipe_props_z", {}))
            done += 1

    def lockers(self, n, block=None):
        cells = [(i, j) for i in range(self.n) for j in range(1, self.m) if (i, j) not in self.maze.room_of]
        spots = self.wall_spots(cells)
        return self.take(spots, n, lambda x, z, f: self.tall(block or f"{MOD}:locker", x, z, f))

    def mobs(self, n):
        cells = [(i, j) for i in range(self.n) for j in range(self.m // 3, self.m)]
        self.rnd.shuffle(cells)
        k = 0
        for c in cells:
            if k >= n:
                break
            x, z = self.cell_center(*c)
            if not self.t.solid(x, 1, z) and (x, z) not in self.used:
                self.t.marker(x, 1, z, "sp:mob_spawn")
                self.used.add((x, z))
                k += 1

    def cabins(self, exit_cabin=True):
        cx, t = self.cx, self.t
        cabin(t, cx, 0, 0, facing_south=True, doors_open=True)
        t.marker(cx, 1, 3, "sp:entry 0")
        if exit_cabin:
            ez = self.z0 + C * self.m
            # celda de arriba en el centro: abrir hacia la cabina
            cabin(t, cx, 0, ez, facing_south=False, doors_open=False, exit_markers=True)


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


# Temas -----------------------------------------------------------------------------------------------

THEMES = {
    "corridor": dict(floor=("polished_andesite", "smooth_stone"), room_floor=("white_concrete", "light_gray_concrete"),
                     wall="white_concrete", wall_low="light_gray_concrete", ceiling="smooth_quartz", frame="light_gray_concrete",
                     obstacles=("barrel", "smooth_stone_slab"), obstacle_props={}, pipe="iron_bars"),
    "storage": dict(floor=("polished_andesite", "andesite"), room_floor=("stone_bricks", "cracked_stone_bricks"),
                    wall="light_gray_concrete", wall_low="gray_concrete", ceiling="polished_deepslate", frame="gray_concrete",
                    obstacles=("barrel", "barrel", "spruce_slab"), pipe="iron_bars"),
    "offices": dict(floor=("light_gray_concrete", "white_concrete"), room_floor=("birch_planks", "stripped_birch_wood"),
                    wall="white_terracotta", wall_low="light_gray_terracotta", ceiling="smooth_quartz", frame="light_gray_terracotta",
                    obstacles=("spruce_slab", "barrel"), pipe="iron_bars"),
    "morgue": dict(floor=("white_concrete", "light_gray_concrete"), room_floor=("diorite", "polished_diorite"),
                   wall="light_blue_terracotta", wall_low="cyan_terracotta", ceiling="smooth_quartz", frame="cyan_terracotta",
                   obstacles=("smooth_stone_slab", "barrel"), pipe="iron_bars"),
    "generators": dict(floor=("polished_tuff", "tuff_bricks"), room_floor=("polished_deepslate", "deepslate_tiles"),
                       wall="gray_concrete", wall_low="yellow_terracotta", ceiling="polished_deepslate", frame="iron_block",
                       obstacles=("barrel", "smooth_stone_slab"), pipe="iron_bars"),
    "greenhouse": dict(floor=("moss_block", "mossy_stone_bricks"), room_floor=("mossy_cobblestone", "moss_block"),
                       wall="green_terracotta", wall_low="mossy_stone_bricks", ceiling="glass", frame="mossy_stone_bricks",
                       obstacles=("mossy_cobblestone_slab", "barrel"), pipe="iron_bars"),
    "final": dict(floor=("polished_deepslate", "deepslate_tiles"), room_floor=("polished_deepslate", "deepslate_tiles"),
                  wall="white_concrete", wall_low="cyan_terracotta", ceiling="smooth_quartz", frame="iron_block",
                  obstacles=("barrel", "smooth_stone_slab"), pipe="iron_bars"),
}


def furnish(z, room, kind):
    """Mobiliario y escondites de una sala segun su tipo."""
    t, rnd = z.t, z.rnd
    spots = z.wall_spots(room["cells"])
    if kind == "storage":
        z.take(spots, 2, lambda x, zz, f: z.tall(f"{MOD}:waste_container", x, zz, f))
        z.take(z.wall_spots(room["cells"]), 6, lambda x, zz, f: (t.set(x, 1, zz, "barrel", facing="up"), True)[1])
    elif kind == "office":
        z.take(spots, 2, lambda x, zz, f: z.tall(f"{MOD}:wardrobe", x, zz, f))
        z.take(z.wall_spots(room["cells"]), 3, lambda x, zz, f: (t.set(x, 1, zz, "spruce_slab", type="top"), True)[1])
    elif kind == "morgue":
        for c in room["cells"]:
            x, zz = z.cell_center(*c)
            if (x, zz) not in z.used and rnd.random() < 0.7:
                t.set(x, 1, zz, f"{MOD}:gurney", facing=rnd.choice(["north", "east"]), half="lower", open=False)
                z.used.add((x, zz))
        z.take(spots, 1, lambda x, zz, f: z.tall(f"{MOD}:locker", x, zz, f))
    elif kind == "lab":
        z.take(spots, 2, lambda x, zz, f: z.tall(f"{MOD}:curtain", x, zz, f))
        for c in room["cells"][:2]:
            x, zz = z.cell_center(*c)
            if (x, zz) not in z.used:
                t.set(x, 1, zz, "smooth_stone_slab", type="top")
                z.used.add((x, zz))
    elif kind == "machine":
        for c in room["cells"]:
            x, zz = z.cell_center(*c)
            if (x, zz) not in z.used and rnd.random() < 0.5:
                t.fill(x, 1, zz, x, 2, zz, "iron_block")
                t.set(x, 3, zz, "chain", axis="y")
                z.used.add((x, zz))
        z.take(spots, 1, lambda x, zz, f: z.tall(f"{MOD}:locker", x, zz, f))


def place_rooms(z, specs):
    """specs: lista de (ancho, alto, tipo). Devuelve las salas colocadas."""
    out = []
    for w, h, kind in specs:
        for _ in range(200):
            i = z.rnd.randint(0, z.n - w)
            j = z.rnd.randint(2, z.m - h - 2)
            if abs((i + w / 2) - z.n / 2) < 2 and j < 3:
                continue
            r = z.maze.add_room(i, j, w, h, kind)
            if r:
                out.append(r)
                break
    return out


def make_zone(name, n, m, theme, seed, rooms, extra):
    z = Zone(name, n, m, THEMES[theme], seed)
    placed = place_rooms(z, rooms)
    z.maze.finalize(max_doors=2, entry=(n // 2, 0))
    # La celda central de arriba conecta con la cabina de salida
    z.door_slots = []
    z.build()
    z.cabins()
    t = z.t
    # abrir el borde de arriba hacia la cabina de salida
    ex, _ = z.cell_min(n // 2, m - 1)
    t.clear(ex, 1, z.z0 + C * m, ex + 2, 2, z.z0 + C * m)
    # y el de abajo hacia la de entrada
    t.clear(ex, 1, z.z0, ex + 2, 2, z.z0)
    for x in range(ex, ex + 3):
        t.set(x, 1, z.z0, f"{MOD}:elevator_door", facing="south", half="lower", open=True)
        t.set(x, 2, z.z0, f"{MOD}:elevator_door", facing="south", half="upper", open=True)
        t.set(x, 1, z.z0 + C * m, f"{MOD}:elevator_door", facing="north", half="lower", open=False)
        t.set(x, 2, z.z0 + C * m, f"{MOD}:elevator_door", facing="north", half="upper", open=False)
    # Puertas normales en todas las entradas de sala (algunas especiales en extra)
    z.special_doors = {}
    for slot in z.door_slots:
        z.place_door(slot, f"{MOD}:lab_door")
    for r in placed:
        furnish(z, r, r["kind"])
    z.lights()
    z.lockers(extra.get("lockers", 10))
    z.obstacles(extra.get("obstacles", 8))
    z.low_pipes(extra.get("pipes", 3))
    extra["apply"](z, placed)
    z.mobs(extra.get("mobs", 8))
    # Sitios para fusibles: callejones sin salida y salas
    spots = z.maze.dead_ends()
    z.rnd.shuffle(spots)
    for c in spots[:12]:
        z.fuse_spot(c)
    for r in placed:
        z.fuse_spot(z.rnd.choice(r["cells"]))
    return z


def door_of(z, room):
    """Primera puerta de una sala (para cambiarle el tipo)."""
    for slot in z.door_slots:
        l = frozenset((slot[3], slot[4]))
        if z.maze.doors.get(l) == room["id"]:
            return slot
    return None


def retype(z, room, block, nbt):
    for slot in z.door_slots:
        l = frozenset((slot[3], slot[4]))
        if z.maze.doors.get(l) == room["id"]:
            z.place_door(slot, block, nbt)


def far_cell(z, avoid_rooms=True):
    cells = z.maze.dead_ends() or [(i, j) for i in range(z.n) for j in range(z.m)]
    cells.sort(key=lambda c: -(abs(c[0] - z.n // 2) + c[1]))
    for c in cells[:8]:
        if c not in z.maze.room_of or not avoid_rooms:
            return c
    return cells[0]


# Contenido especial de cada zona ----------------------------------------------------------------------

def apply_corridor(z, rooms):
    # Pasillo inicial: una caja de un fusible y el fusible cerca; una taquilla; aprender lo basico
    z.fusebox(rooms[0], slots=1)
    z.fuse_spot(z.rnd.choice(rooms[1]["cells"]))


def apply_storage(z, rooms):
    # Sala cerrada con llave roja (con una caja dentro); la llave en un callejon lejano
    locked = rooms[0]
    retype(z, locked, f"{MOD}:security_door", be("lab_door", Type="locked", Param="red", Consume=False))
    z.fusebox(locked)
    z.fusebox(rooms[1])
    z.item(far_cell(z), "key red")


def apply_offices(z, rooms):
    # Tarjeta de nivel 1 en un despacho; la sala de la tarjeta guarda una caja
    secure = rooms[0]
    retype(z, secure, f"{MOD}:security_door", be("lab_door", Type="keycard", Param="1"))
    z.fusebox(secure)
    z.fusebox(rooms[2])
    z.item(z.rnd.choice(rooms[1]["cells"]), "keycard 1")


def apply_morgue(z, rooms):
    # Puerta atascada (2 jugadores) y llave azul
    retype(z, rooms[0], f"{MOD}:security_door", be("lab_door", Type="stuck"))
    z.fusebox(rooms[0])
    locked = rooms[1]
    retype(z, locked, f"{MOD}:security_door", be("lab_door", Type="locked", Param="blue", Consume=True))
    z.fusebox(locked)
    z.item(far_cell(z), "key blue")


def apply_generators(z, rooms):
    # Valvulas en orden que abren la puerta hidraulica de la sala de la caja; a una le falta la rueda
    hyd = rooms[0]
    retype(z, hyd, f"{MOD}:security_door", be("lab_door", Type="hydraulic", Param="steam"))
    z.fusebox(hyd, slots=3)
    z.valve(rooms[1], "steam", order=1)
    z.valve(rooms[2], "steam", order=2, wheel=False)
    z.item(far_cell(z), "wheel")
    # Una puerta electrica de otro circuito con su propia caja
    retype(z, rooms[3], f"{MOD}:security_door", be("lab_door", Type="electric", Param="aux"))
    z.fusebox(rooms[1], circuit="aux", slots=1)
    z.fusebox(rooms[3])


def apply_greenhouse(z, rooms):
    # Dos valvulas a la vez (cooperativas) y una puerta con tarjeta de nivel 2
    hyd = rooms[0]
    retype(z, hyd, f"{MOD}:security_door", be("lab_door", Type="hydraulic", Param="mist"))
    z.fusebox(hyd)
    z.valve(rooms[1], "mist", coop=True)
    z.valve(rooms[1], "mist", coop=True)
    sec = rooms[2]
    retype(z, sec, f"{MOD}:security_door", be("lab_door", Type="keycard", Param="2"))
    z.fusebox(sec)
    z.item(far_cell(z), "keycard 2")


def apply_final(z, rooms):
    # Sala de criogenizacion: capsulas en la sala grande; tres cajas para la salida
    big = rooms[0]
    t = z.t
    for c in big["cells"]:
        if z.rnd.random() < 0.35:
            x, zz = z.cell_center(*c)
            if (x, zz) in z.used:
                continue
            t.set(x, 0, zz, "sea_lantern")
            t.fill(x, 1, zz, x, 3, zz, "light_blue_stained_glass")
            t.set(x, 4, zz, "iron_block")
            z.used.add((x, zz))
    z.fusebox(big, slots=2)
    z.fusebox(rooms[1], slots=2)
    retype(z, rooms[2], f"{MOD}:security_door", be("lab_door", Type="keycard", Param="3"))
    z.fusebox(rooms[2], slots=2)
    z.item(far_cell(z), "keycard 3")


ZONES = [
    ("start_corridor", 9, 11, "corridor", 101, [(2, 2, "office"), (2, 2, "storage")],
     dict(apply=apply_corridor, lockers=5, obstacles=4, pipes=1, mobs=3), "start", 0),
    ("lab_storage", 23, 23, "storage", 202, [(3, 3, "storage"), (3, 2, "storage"), (2, 3, "machine"), (2, 2, "storage")],
     dict(apply=apply_storage, lockers=12, obstacles=12, pipes=4, mobs=8), "intermediate", 1),
    ("lab_offices", 23, 23, "offices", 303, [(3, 2, "office"), (2, 2, "office"), (3, 3, "office"), (2, 2, "lab")],
     dict(apply=apply_offices, lockers=10, obstacles=10, pipes=3, mobs=8), "intermediate", 1),
    ("lab_morgue", 23, 25, "morgue", 404, [(3, 3, "morgue"), (2, 3, "morgue"), (2, 2, "lab"), (3, 2, "morgue")],
     dict(apply=apply_morgue, lockers=12, obstacles=10, pipes=4, mobs=9), "intermediate", 2),
    ("lab_generators", 25, 25, "generators", 505, [(3, 3, "machine"), (2, 2, "machine"), (2, 2, "machine"), (2, 3, "machine")],
     dict(apply=apply_generators, lockers=12, obstacles=12, pipes=5, mobs=10), "intermediate", 2),
    ("lab_greenhouse", 25, 25, "greenhouse", 606, [(3, 3, "lab"), (3, 2, "lab"), (2, 2, "office"), (2, 2, "lab")],
     dict(apply=apply_greenhouse, lockers=10, obstacles=14, pipes=4, mobs=10), "intermediate", 3),
    ("final_room", 21, 21, "final", 707, [(7, 7, "lab"), (2, 2, "machine"), (2, 2, "lab")],
     dict(apply=apply_final, lockers=12, obstacles=8, pipes=3, mobs=8), "final", 4),
]


def main():
    pool = []
    for name, n, m, theme, seed, rooms, extra, kind, diff in ZONES:
        z = make_zone(name, n, m, theme, seed, rooms, extra)
        size, nb, nm = z.t.save()
        zone = {"template": f"{MOD}:zones/{name}", "kind": kind, "difficulty": diff, "entry_yaw": 0}
        path = ROOT / "zone" / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(zone, indent=2) + "\n", encoding="utf-8")
        if kind == "intermediate":
            pool.append(f"{MOD}:{name}")
        print(f"{name:16} {size[0]}x{size[1]}x{size[2]}  bloques={nb} marcadores={nm} salas={len(z.maze.rooms)}")
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
