#!/usr/bin/env python3
"""
Planta de entrenamiento (ascensor TUTORIAL del lobby). Sin monstruos ni sustos: un recorrido en
linea recta, de sur a norte, con siete estaciones separadas por tabiques. Cada una ensena una
mecanica con carteles:

    1  Linterna y ruido      pasadizo bajo: solo se pasa agachado
    2  Registrar y lanzar    muebles registrables, latas y botellas
    3  Esconderse            taquillas, armario, camilla y cortina; que hacer si te atrapan
    4  Llaves y puertas      llave roja y puerta de seguridad
    5  Valvulas              volante, valvula y puerta hidraulica
    6  Conductos             un muro sin puerta: se cruza por la rejilla del suelo
    7  Fusibles y salida     fusibles (y uno fundido), caja y ascensor de salida

Marcadores: sp:entry, sp:exit_cabin, sp:fuse, sp:item ..., sp:tut_crouch, sp:tut_vent (pasos REACH).

    python3 tools/tutorial.py
"""
import json
from collections import deque

from zones import Template, be, cabin, sign_nbt, MOD, ROOT

X = 4                      # interior de x=-4 a 4; paredes en x=+-5
TOP = 5                    # techo
END = 81                   # ultima fila interior; la cabina de salida empieza en END + 1


def lab(n):
    return f"{MOD}:{n}"


def build():
    t = Template("tutorial")
    wall, floor, ceil = lab("lab_wall_tiles_white"), lab("lab_tile_floor"), lab("lab_ceiling")
    # caja
    for x in range(-X - 1, X + 2):
        for z in range(-1, END + 2):
            t.set(x, 0, z, floor)
            t.set(x, TOP, z, ceil)
            if abs(x) == X + 1 or z in (-1, END + 1):
                for y in range(1, TOP):
                    t.set(x, y, z, lab("lab_wall_tiles_teal") if y == 3 else wall)
    # linea de guia en el suelo
    for z in range(0, END + 1):
        t.set(0, 0, z, lab("lab_linoleum"))

    def sign(x, y, z, *lines, facing="north"):
        t.set(x, y, z, "minecraft:birch_wall_sign", nbt=sign_nbt(*lines), facing=facing)

    def partition(z, door=None, nbt=None):
        """Tabique a lo ancho en z; door: bloque de puerta en x=0 (o None: tabique ciego)."""
        for x in range(-X, X + 1):
            for y in range(1, TOP):
                t.set(x, y, z, wall)
        for x in range(-X, X + 1):
            t.set(x, 3, z, lab("lab_wall_tiles_teal"))
        if door:
            t.set(0, 1, z, door, nbt=nbt, facing="south", half="lower", hinge="left", open=False, powered=False)
            t.set(0, 2, z, door, facing="south", half="upper", hinge="left", open=False, powered=False)

    def title(z, n, name):
        """Cartel de la estacion en la pared de la izquierda al entrar."""
        sign(-X, 3, z, f"- {n} -", name, facing="east")

    def light(x, z, mode="lit"):
        t.set(x, TOP, z, lab("fluorescent_light"), lit=mode != "dead", flickers=mode == "flicker")

    for z in range(2, END, 6):
        light(0, z, "flicker" if z in (8, 44) else "lit")

    # Llegada
    cabin(t, 0, 0, -8, facing_south=True, doors_open=True)
    t.fill(-2, 1, -1, 2, 4, -1, "air")
    t.fill(-1, 0, -1, 1, 0, -1, lab("lab_hazard"))
    t.marker(0, 1, -5, "sp:entry 0")

    # 1 Linterna y ruido: pasadizo bajo (losas arriba): 1,5 bloques de alto, solo agachado ------------
    title(1, 1, "LINTERNA")
    sign(X, 2, 2, "Tu linterna", "gasta bateria.", "Las pilas la", "recargan.", facing="west")
    sign(X, 2, 4, "Correr hace", "ruido y ellos", "lo oyen.", "Camina o agachate", facing="west")
    sign(-2, 2, 5, "Agachate", "(Shift) para", "pasar por aqui", "v", facing="north")
    for z in range(6, 10):
        for x in range(-X, X + 1):
            if abs(x) <= 1:
                t.set(x, 2, z, "minecraft:smooth_stone_slab", type="top")
                t.fill(x, 3, z, x, TOP - 1, z, wall)
            else:
                t.fill(x, 1, z, x, TOP - 1, z, wall)
    for x in (-1, 0, 1):
        t.marker(x, 1, 11, "sp:tut_crouch")

    # 2 Registrar y lanzar ---------------------------------------------------------------------------
    title(11, 2, "REGISTRAR")
    sign(X, 2, 12, "Clic derecho", "en muebles para", "registrarlos.", "A veces hay algo", facing="west")
    sign(X, 2, 14, "Lanza latas y", "botellas lejos", "para distraer", "a los smilers.", facing="west")
    t.set(-X, 1, 14, lab("desk"), facing="east", open=False)
    t.set(-X, 1, 15, lab("filing_cabinet"), facing="east", open=False)
    t.set(-X, 1, 17, lab("supply_box"), facing="east", open=False)
    t.set(X, 1, 17, lab("tool_cart"), facing="west", open=False)
    t.set(X, 1, 19, lab("medical_cabinet"), facing="west", open=False)
    t.set(-X + 1, 1, 14, lab("office_chair"), facing="west")
    t.marker(-2, 1, 18, "sp:item can")
    t.marker(2, 1, 15, "sp:item bottle")
    t.marker(1, 1, 20, "sp:item battery")
    t.marker(-1, 1, 16, "sp:item decoy")
    t.marker(-3, 1, 20, "sp:item stunner")
    sign(X, 2, 16, "Despertador:", "dejalo en el", "suelo y sonara.", "Los atrae.", facing="west")
    sign(X, 2, 18, "Aturdidor: deja", "quieto a un", "smiler unos", "segundos.", facing="west")
    partition(22, lab("lab_door"))
    sign(-1, 2, 21, "Las puertas", "se abren con", "clic derecho", facing="north")

    # 3 Esconderse ----------------------------------------------------------------------------------
    title(23, 3, "ESCONDERSE")
    sign(X, 2, 24, "Entra en una", "taquilla o un", "armario con", "clic derecho", facing="west")
    sign(X, 2, 26, "Si algo se", "acerca, aguanta", "la respiracion", "(minijuego)", facing="west")
    for z in (26, 27, 28):
        t.set(-X, 1, z, lab("locker"), facing="east", half="lower", open=False)
        t.set(-X, 2, z, lab("locker"), facing="east", half="upper", open=False)
    t.set(-X, 1, 30, lab("wardrobe"), facing="east", half="lower", open=False)
    t.set(-X, 2, 30, lab("wardrobe"), facing="east", half="upper", open=False)
    t.set(X - 1, 1, 30, lab("gurney"), facing="east", half="lower", open=False)
    t.set(X, 1, 30, lab("gurney"), facing="east", half="upper", open=False)
    for x in (1, 2, 3, 4):
        t.set(x, 1, 32, lab("curtain"), facing="north", half="lower", open=False)
        t.set(x, 2, 32, lab("curtain"), facing="north", half="upper", open=False)
    sign(X, 2, 28, "Bajo la camilla", "y tras la", "cortina tambien", "te escondes", facing="west")
    sign(-1, 2, 33, "SI TE ATRAPAN", "pulsa las teclas", "que salen en", "pantalla", facing="north")
    sign(1, 3, 33, "Si caes, tus", "companeros", "pueden", "reanimarte", facing="north")
    partition(34, lab("lab_door"))

    # 4 Llaves y puertas ----------------------------------------------------------------------------
    title(35, 4, "LLAVES")
    sign(X, 2, 36, "Algunas puertas", "piden llave o", "tarjeta. Busca", "y abre.", facing="west")
    t.set(-X, 1, 40, lab("desk"), facing="east", open=False)
    t.set(-X, 1, 41, lab("desk"), facing="east", open=False)
    t.marker(-X + 1, 1, 41, "sp:item key red")
    t.set(X, 1, 40, lab("metal_shelf"), facing="west")
    t.set(X, 1, 41, lab("metal_shelf"), facing="west")
    partition(46, lab("security_door"), be("lab_door", Type="locked", Param="red"))
    sign(-1, 2, 45, "PUERTA ROJA", "Usa la llave", "roja", facing="north")

    # 5 Valvulas ------------------------------------------------------------------------------------
    title(47, 5, "VALVULAS")
    sign(X, 2, 48, "Coloca el", "volante en la", "valvula y", "giralo.", facing="west")
    t.marker(-2, 1, 50, "sp:item wheel")
    t.set(X, 2, 53, lab("valve"), nbt=be("valve", Group="tut", Order=1, Coop=False), facing="west",
          turning=False, turned=False, wheel=False)
    t.fill(X, 3, 52, X, 4, 54, lab("lab_pipes"))
    sign(X, 2, 55, "La valvula", "abre la puerta", "hidraulica", facing="west")
    partition(58, lab("security_door"), be("lab_door", Type="hydraulic", Param="tut"))

    # 6 Conductos: muro sin puerta, se pasa por debajo ----------------------------------------------
    title(59, 6, "CONDUCTOS")
    sign(X, 2, 60, "Abre la rejilla", "del suelo y", "metete. Por los", "conductos", facing="west")
    sign(X, 2, 62, "te mueves sin", "que te vean.", "Ojo: a veces", "hay ventiladores", facing="west")
    partition(64)
    duct = lab("lab_vent_duct")
    path = [(0, z) for z in range(61, 68)]
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
    t.set(0, 0, 61, lab("vent_grille"), facing="south", open=False, sealed=False)
    t.set(0, 0, 67, lab("vent_grille"), facing="north", open=False, sealed=False)
    for x in (-3, 0, 3):
        t.marker(x, 1, 69, "sp:tut_vent")

    # 7 Fusibles y salida ---------------------------------------------------------------------------
    title(70, 7, "FUSIBLES")
    sign(X, 2, 71, "Recoge fusibles", "y ponlos en la", "caja junto al", "ascensor.", facing="west")
    sign(X, 2, 73, "Cuidado: los", "fundidos (negros)", "no sirven.", facing="west")
    for (x, z) in ((-3, 72), (3, 75), (-2, 77), (2, 79)):
        t.marker(x, 1, z, "sp:fuse")
    t.set(-X, 1, 75, lab("tool_cart"), facing="east", open=False)
    t.set(X, 1, 77, lab("supply_box"), facing="west", open=False)
    t.set(-4, 2, END, lab("fuse_box"), nbt=be("fuse_box", Circuit="exit", Slots=2, Accepts=""),
          facing="north", filled=0, powered=False)
    sign(-4, 3, END, "CAJA DE", "FUSIBLES", facing="north")
    sign(3, 3, END, "Con corriente,", "entra en el", "ascensor para", "terminar", facing="north")
    cabin(t, 0, 0, END + 1, facing_south=False, doors_open=False, exit_markers=True)
    return t


# Comprobacion: se llega a todo a pie desde la llegada (las puertas y la rejilla se cruzan) -----------

def verify(t):
    def block(x, y, z):
        return t.get(x, y, z)

    def passable(x, y, z):
        b = block(x, y, z)
        return b == "minecraft:air" or "_door" in b or "sign" in b or "curtain" in b or "vent_grille" in b

    marks = {}
    for (pos, meta) in t.markers:
        marks.setdefault(meta.split()[0], []).append(pos)
    start = marks["sp:entry"][0]
    # estados (x, z, capa): capa 1 a pie, capa -1 dentro del conducto; se cambia en una rejilla
    s0 = (start[0], start[2], 1)
    seen3, q = {s0}, deque([s0])
    while q:
        x, z, layer = q.popleft()
        nxt = []
        if block(x, 0, z).endswith("vent_grille"):
            nxt.append((x, z, -layer))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if abs(nx) > X + 3 or not (-10 <= nz <= END + 8):
                continue
            if layer == 1 and passable(nx, 1, nz) and not block(nx, 0, nz) == "minecraft:air":
                nxt.append((nx, nz, 1))
            if layer == -1 and block(nx, -1, nz) == "minecraft:air" and block(nx, -2, nz).endswith("lab_vent_duct"):
                nxt.append((nx, nz, -1))
        for n in nxt:
            if n not in seen3:
                seen3.add(n)
                q.append(n)
    seen = {(x, z) for (x, z, l) in seen3 if l == 1}
    problems = []
    for k in ("sp:fuse", "sp:item", "sp:tut_crouch", "sp:tut_vent"):
        lost = [p for p in marks.get(k, []) if (p[0], p[2]) not in seen]
        if lost:
            problems.append(f"{k} inalcanzable: {lost}")
    e = marks["sp:exit_cabin"]
    if ((e[0][0] + e[1][0]) // 2, (e[0][2] + e[1][2]) // 2) not in seen:
        problems.append("salida inalcanzable")
    if problems:
        raise SystemExit("tutorial: " + "; ".join(problems))
    return len(seen)


def main():
    t = build()
    n = verify(t)
    size, nb, nm = t.save()
    zone = {"template": f"{MOD}:zones/tutorial", "kind": "tutorial", "difficulty": 0, "entry_yaw": 0,
            "name": "Planta de entrenamiento"}
    (ROOT / "zone/tutorial.json").write_text(json.dumps(zone, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"tutorial {size[0]}x{size[1]}x{size[2]} bloques={nb} marcadores={nm} celdas alcanzables={n}")


if __name__ == "__main__":
    main()
