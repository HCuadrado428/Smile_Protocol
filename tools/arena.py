#!/usr/bin/env python3
"""
Laboratorio Cero: la sala de Smiley (docs/SMILERS.md). Zona final de la partida.

Una nave de laboratorio de tres niveles: el suelo (y=0), un anillo de pasarelas pegado a las
paredes con un puente central (y=6) y pasarelas altas al oeste y al norte (y=11), techo en y=16.
Escaleras entre niveles (Smiley cabe: 4 bloques libres) y escaleras de mano que solo usan los
jugadores. Tres generadores (circuitos gen1, gen2 y gen3), uno por nivel; cada uno enciende las luces
de su nivel. El ascensor de salida esta al norte y no baja hasta encender los tres y aguantar.

Marcadores:
    sp:entry 0          llegada (cabina sur)
    sp:exit_cabin       cabina de salida (norte)
    sp:arena            al pasar por aqui empieza la intro
    sp:smiley           donde aparece Smiley en la intro
    sp:cell             centro de la celda donde queda encerrado al final
    sp:summon           donde aparecen los smilers que invoca
    sp:fuse             sitios para los fusibles

    python3 tools/arena.py
"""
import json
from collections import deque

from zones import Template, be, cabin, sign_nbt, MOD, ROOT

X = 18                 # interior de x=-18 a 18; paredes en x=+-19
Z0, Z1 = 0, 40         # interior de z=0 a 40; paredes en z=-1 y z=41
L1, L2, TOP = 6, 11, 16


def lab(n):
    return f"{MOD}:{n}"


def build():
    t = Template("smiley_lab")
    wall, floor, ceil = lab("lab_metal_wall"), lab("lab_concrete_floor"), lab("lab_ceiling_concrete")
    grate, hazard = lab("lab_grate"), lab("lab_hazard")
    # caja
    for x in range(-X - 1, X + 2):
        for z in range(Z0 - 1, Z1 + 2):
            t.set(x, 0, z, floor)
            t.set(x, TOP, z, ceil)
            if abs(x) == X + 1 or z in (Z0 - 1, Z1 + 1):
                for y in range(1, TOP):
                    t.set(x, y, z, wall if y > 2 else lab("lab_concrete_wall"))
    # franjas de peligro en el suelo, como en una nave de pruebas
    for x in range(-X, X + 1):
        for z in (8, 32):
            t.set(x, 0, z, hazard)

    cells = {}

    def walk(x, y, z, block=grate):
        t.set(x, y, z, block)
        cells[(x, z, y)] = True

    # Nivel 1: anillo (4 de ancho) y puente central
    for x in range(-X, X + 1):
        for z in range(Z0, Z1 + 1):
            if abs(x) >= X - 3 or z <= Z0 + 3 or z >= Z1 - 3 or 19 <= z <= 21:
                walk(x, L1, z)
    # Nivel 2: plataforma oeste, puente norte y plataforma este
    for x in range(-X, X + 1):
        for z in range(Z0, Z1 + 1):
            if (x <= -X + 3 and 19 <= z <= 31) or 29 <= z <= 31 or (x >= X - 3 and 26 <= z <= 31):
                walk(x, L2, z)
    # Barandillas en los bordes de las pasarelas
    for (x, z, y) in list(cells):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if abs(nx) > X or nz < Z0 or nz > Z1:
                continue
            if (nx, nz, y) not in cells:
                t.set(x, y + 1, z, "minecraft:iron_bars")
                break

    # Pilares que sujetan las pasarelas (fuera del paso y de la celda)
    for x in (-X + 4, X - 4):
        for z in (4, 12, 28, 36):
            if not t.solid(x, 1, z):
                t.fill(x, 1, z, x, L1 - 1, z, wall)

    def stairs(xs, z_start, y_from, n, facing="north"):
        """Tramo de n peldanos hacia el norte desde (z_start, y_from + 1)."""
        for k in range(1, n + 1):
            z = z_start + k - 1
            y = y_from + k
            for x in xs:
                for yy in range(y_from + 1, y):
                    t.set(x, yy, z, wall)
                t.set(x, y, z, "minecraft:polished_deepslate_stairs", facing=facing, half="bottom", shape="straight")
                for yy in range(y + 1, y + 5):           # sin barandillas ni nada encima
                    t.set(x, yy, z, "air")

    # Suelo -> nivel 1: al este, de z=5 a z=10, desembarca en un rellano que da al anillo
    stairs((X - 5, X - 4), 5, 0, L1)
    for x in (X - 5, X - 4):
        for z in (11, 12):
            walk(x, L1, z)
            t.set(x, L1 + 1, z, "air")
    for x in (X - 5, X - 4):
        t.set(x, L1 + 1, 13, "minecraft:iron_bars")
    t.set(X - 6, L1 + 1, 11, "minecraft:iron_bars")
    t.set(X - 6, L1 + 1, 12, "minecraft:iron_bars")
    # el pilar de x=14, z=12 sobra donde va el rellano
    t.fill(X - 4, 1, 12, X - 4, L1 - 1, 12, "air")
    # Nivel 1 -> nivel 2: en el anillo oeste, de z=14 a z=18
    stairs((-X + 1, -X + 2), 14, L1, L2 - L1)
    # Escaleras de mano (solo jugadores)
    for y in range(1, L1 + 2):
        t.set(-X, y, 33, "minecraft:ladder", facing="east")
    t.set(-X, L1 + 1, 33, "air")
    t.set(-X, L1, 33, "minecraft:ladder", facing="east")
    for y in range(L1 + 1, L2 + 1):
        t.set(X, y, 24, "minecraft:ladder", facing="west")
    for y in range(L1 + 1, L2 + 2):
        t.set(X, y, 24, "minecraft:ladder", facing="west")
    walk(X, L2, 25)
    walk(X - 1, L2, 25)
    t.set(X, L2, 24, "minecraft:ladder", facing="west")
    t.set(X - 1, L2 + 1, 25, "minecraft:iron_bars")

    # Generadores: caja de fusibles (gen1, gen2, gen3) y maquinas al lado
    def generator(x, y, z, facing, circuit, machines):
        t.set(x, y + 2, z, lab("fuse_box"), nbt=be("fuse_box", Circuit=circuit, Slots=1, Accepts=""),
              facing=facing, filled=0, powered=False)
        for (mx, mz) in machines:
            t.fill(mx, y + 1, mz, mx, y + 2, mz, lab("lab_machine"))
            t.set(mx, y + 3, mz, "minecraft:chain", axis="y")
        t.set(x, y + 3, z, "minecraft:birch_wall_sign", nbt=sign_nbt(f"GENERADOR {circuit[-1]}"),
              facing={"north": "south", "south": "north", "east": "west", "west": "east"}[facing])

    generator(-8, 0, Z1, "north", "gen1", [(-10, Z1), (-11, Z1), (-6, Z1)])
    generator(X, L1, 6, "east", "gen2", [(X, 8), (X, 9)])
    generator(-X, L2, 27, "west", "gen3", [(-X, 25), (-X, 24)])

    # Luces: sobre cada nivel, ligadas a su generador; algunas parpadean sin corriente
    def light(x, y, z, mode):
        nbt = be("fluorescent_light", Circuit=mode) if mode.startswith("gen") else None
        t.set(x, y, z, lab("fluorescent_light"), nbt=nbt, lit=mode in ("lit", "flicker"), flickers=mode == "flicker")

    for z in range(4, Z1, 8):
        for x in (-X + 2, X - 2):
            light(x, L1, z, "gen1")                     # bajo el anillo: alumbran el suelo
    for x in range(-10, 11, 10):
        light(x, L1, 20, "gen1")                         # bajo el puente
    for z in range(3, Z1, 8):
        for x in (-X + 1, X - 1):
            light(x, TOP, z, "gen2")                     # techo sobre el anillo
    for x in range(-12, 13, 8):
        light(x, L2, 30, "gen2")
    for z in range(21, 32, 5):
        light(-X + 1, TOP, z, "gen3")
    for x in range(-12, 13, 8):
        light(x, TOP, 30, "gen3")
    light(0, TOP, 12, "flicker")
    light(0, L1, 2, "flicker")
    light(6, L1, 38, "flicker")
    light(-14, TOP, 36, "dead")

    # Ascensores
    cabin(t, 0, 0, -7, facing_south=True, doors_open=True)
    t.marker(0, 1, -4, "sp:entry 0")
    cabin(t, 0, 0, Z1 + 1, facing_south=False, doors_open=False, exit_markers=True)

    # Escondites, cajas y escombros
    for (x, z, f) in ((-X, 4, "west"), (-X, 5, "west"), (X, 16, "east"), (X, 17, "east"), (-12, Z1, "north"),
                      (12, Z1, "north")):
        if not t.solid(x, 1, z):
            t.set(x, 1, z, lab("locker"), facing=f, half="lower", open=False)
            t.set(x, 2, z, lab("locker"), facing=f, half="upper", open=False)
    for (x, z, f) in ((-X + 1, 2, "west"), (X, 35, "east"), (-X, 10, "west")):
        y = L1
        if not t.solid(x, y + 1, z):
            t.set(x, y + 1, z, lab("locker"), facing=f, half="lower", open=False)
            t.set(x, y + 2, z, lab("locker"), facing=f, half="upper", open=False)
    for (x, z) in ((-6, 6), (-5, 6), (7, 15), (-9, 26), (-8, 26), (9, 35), (4, 24), (-12, 16)):
        t.set(x, 1, z, lab("lab_crate"))
    for (x, z) in ((-3, 15), (3, 36), (11, 25)):
        t.set(x, 1, z, lab("lab_debris"))
    # Muebles (fase 24): puestos de control, estanterias y carros junto a los generadores
    furniture = [("workstation", -4, 1, 39, "north"), ("workstation", -3, 1, 39, "north"), ("office_chair", -4, 1, 38, "north"),
                 ("tool_cart", -13, 1, 39, "north"), ("supply_box", -14, 1, 37, "east"), ("metal_shelf", 18, 1, 21, "west"),
                 ("metal_shelf", 18, 1, 22, "west"), ("specimen_shelf", -18, 1, 14, "east"), ("trolley", 8, 1, 30, "west"),
                 ("medical_cabinet", -18, 1, 20, "east"), ("filing_cabinet", 18, 1, 33, "west"),
                 ("tool_cart", 17, L1 + 1, 4, "west"), ("supply_box", 17, L1 + 1, 10, "west"), ("metal_shelf", -18, L1 + 1, 26, "east"),
                 ("tool_cart", -17, L2 + 1, 29, "east"), ("workstation", -17, L2 + 1, 22, "east"), ("supply_box", 18, L2 + 1, 30, "west")]
    for name, x, y, z, f in furniture:
        if t.solid(x, y, z):
            continue
        props = {"facing": f}
        if name in ("desk", "filing_cabinet", "medical_cabinet", "tool_cart", "supply_box"):
            props["open"] = False
        t.set(x, y, z, lab(name), **props)
    # tuberias por las paredes
    for z in range(Z0, Z1 + 1):
        for x in (-X, X):
            if not t.solid(x, 4, z):
                t.set(x, 4, z, lab("lab_pipes"))

    # Conducto bajo el suelo de oeste a este: los jugadores pueden huir por el; Smiley no cabe
    duct = lab("lab_vent_duct")
    path = [(x, 26) for x in range(-15, 16)] + [(15, z) for z in range(25, 17, -1)]
    cells = set(path)
    for (x, z) in path:
        t.set(x, -1, z, "air")
        t.set(x, -2, z, duct)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (x + dx, z + dz)
                if n not in cells:
                    t.set(n[0], -1, n[1], duct)
                    t.set(n[0], -2, n[1], duct)
    t.set(-15, 0, 26, lab("vent_grille"), facing="east", open=False, sealed=False)
    t.set(15, 0, 18, lab("vent_grille"), facing="south", open=False, sealed=False)

    # Marcadores
    t.marker(0, 1, 12, "sp:arena")
    t.marker(0, 1, 26, "sp:smiley")
    t.marker(0, 1, 30, "sp:cell")
    for (x, y, z) in ((-14, 1, 30), (14, 1, 22), (-12, 1, 10), (13, 1, 36), (0, L1 + 1, 38), (-16, L1 + 1, 8),
                      (16, L1 + 1, 30), (-16, L2 + 1, 24), (8, L2 + 1, 30)):
        t.marker(x, y, z, "sp:summon")
    for (x, y, z) in ((-15, 1, 22), (15, 1, 4), (6, 1, 30), (-4, 1, 37), (-10, 1, 18), (10, 1, 12),
                      (-16, L1 + 1, 30), (16, L1 + 1, 20), (-6, L1 + 1, 20), (6, L1 + 1, 2), (10, L1 + 1, 38),
                      (-16, L2 + 1, 20), (16, L2 + 1, 27), (0, L2 + 1, 30)):
        t.marker(x, y, z, "sp:fuse")
    return t


# Comprobacion: todo alcanzable a pie desde la llegada ------------------------------------------------------

def verify(t):
    def block(x, y, z):
        return t.get(x, y, z)

    def solid(x, y, z):
        b = block(x, y, z)
        return b != "minecraft:air" and "ladder" not in b and "iron_bars" not in b and "_door" not in b

    def standable(x, y, z):
        below = block(x, y - 1, z)
        ok_below = below != "minecraft:air" and ("ladder" in below or solid(x, y - 1, z) or "iron_bars" in below)
        return ok_below and not solid(x, y, z) and not solid(x, y + 1, z) and "iron_bars" not in block(x, y, z)

    marks = {}
    for (pos, meta) in t.markers:
        marks.setdefault(meta.split()[0], []).append(pos)
    start = marks["sp:entry"][0]
    seen, q = {start}, deque([start])
    while q:
        x, y, z = q.popleft()
        nxt = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1, -2):
                nxt.append((x + dx, y + dy, z + dz))
        if "ladder" in block(x, y, z) or "ladder" in block(x, y - 1, z):
            nxt += [(x, y + 1, z), (x, y - 1, z)]
        for n in nxt:
            if n in seen or not (-X - 1 <= n[0] <= X + 1 and 0 < n[1] < TOP and Z0 - 8 <= n[2] <= Z1 + 8):
                continue
            onladder = "ladder" in block(*n)
            if not (standable(*n) or onladder):
                continue
            if n[1] > y and not ("stairs" in block(x, n[1] - 1, z) or "stairs" in block(n[0], n[1] - 1, n[2])
                                  or onladder or "ladder" in block(x, y, z) or n[1] - y == 1):
                continue
            seen.add(n)
            q.append(n)
    problems = []
    for k in ("sp:fuse", "sp:summon", "sp:arena", "sp:smiley", "sp:cell"):
        lost = [p for p in marks.get(k, []) if p not in seen]
        if lost:
            problems.append(f"{k} inalcanzable: {lost}")
    e = marks["sp:exit_cabin"]
    mid = ((e[0][0] + e[1][0]) // 2, 1, (e[0][2] + e[1][2]) // 2)
    if mid not in seen:
        problems.append("salida inalcanzable")
    # generadores: delante de cada caja tiene que haber sitio
    for (pos, (name, props)) in t.blocks.items():
        if name.endswith("fuse_box"):
            x, y, z = pos
            f = dict(props)["facing"]
            dx, dz = {"north": (0, -1), "south": (0, 1), "east": (-1, 0), "west": (1, 0)}[f]
            front = (x + dx, y - 1, z + dz)
            if front not in seen:
                problems.append(f"caja {pos} sin acceso por delante {front}")
    if problems:
        raise SystemExit("smiley_lab: " + "; ".join(problems))
    return len(seen)


def main():
    t = build()
    n = verify(t)
    size, nb, nm = t.save()
    zone = {"template": f"{MOD}:zones/smiley_lab", "kind": "final", "difficulty": 4, "entry_yaw": 0, "name": "Laboratorio Cero"}
    (ROOT / "zone/smiley_lab.json").write_text(json.dumps(zone, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"smiley_lab {size[0]}x{size[1]}x{size[2]} bloques={nb} marcadores={nm} celdas alcanzables={n}")


if __name__ == "__main__":
    main()
