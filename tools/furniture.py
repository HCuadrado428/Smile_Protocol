#!/usr/bin/env python3
"""
Muebles del laboratorio (fase 24): bloques de decoracion y muebles que se registran.

Se modelan con las mismas piezas y materiales que los mecanismos (geo_models.Geo: cubos pintados en
coordenadas del mundo, mugre abajo, oxido y sangre), pero se exportan como modelos de bloque
normales (JSON con elementos) para que cuesten lo mismo que un bloque: no llevan entidad de bloque.
Los que se registran tienen dos modelos: cerrado y abierto (cajon fuera, puertas abiertas, tapa
quitada). El frente mira a +Z (sur), como el resto.

Escribe ademas block/FurnitureDefs.java con los nombres, cuales se registran y su caja de colision,
para que el codigo y los modelos no se separen nunca.

    python3 tools/furniture.py [preview.png]
"""
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import geo_models as gm  # noqa: E402
from geo_models import Geo, MAT, painted, metal, glass, label, dark, fabric  # noqa: E402

ROOT = gm.ROOT
JAVA = ROOT.parent.parent.parent / "java/com/smileprotocol/block/FurnitureDefs.java"
MOD = "smileprotocol"
FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}

MAT.update({
    "paint_red": painted((134, 30, 26), under=(90, 70, 60), chip=0.35, grime=0.5, rust_amt=0.4),
    "paint_cream": painted((188, 180, 156), chip=0.3, grime=0.6, rust_amt=0.2),
    "paint_olive": painted((92, 96, 64), chip=0.4, grime=0.6, rust_amt=0.4),
    "paint_blue": painted((64, 84, 110), chip=0.3, grime=0.5, rust_amt=0.3),
    "paper": painted((214, 208, 190), under=(200, 194, 176), chip=0.0, grime=0.4, rust_amt=0, streak=0, scratch=0),
    "screen": dark((14, 22, 18)),
    "screen_on": gm.lamp((70, 150, 110), glow=False),
    "jar": glass(tint=(80, 96, 70), wire=False, dirt=0.5),
    "specimen": painted((168, 140, 120), under=(150, 120, 100), chip=0, grime=0.2, rust_amt=0, streak=0, scratch=0),
    "label_med": label("+", (170, 20, 20)),
    "label_file": label("A-F"),
    "crate_stencil": label("SP-24", (50, 46, 40)),
    "foam": fabric((54, 56, 58), folds=False, dirt=0.4, fade=0.1),
})

DEFS = []      # (nombre, se registra, caja)


def export(g, name, variants, searchable, moves=None):
    """Pinta la textura con Geo y escribe un modelo JSON por variante ({sufijo: bones que se quitan}).
    moves: {sufijo: {bone: (dx, dy, dz)}} desplaza las piezas de ese bone en esa variante (cajon fuera).

    Los que se registran ademas guardan el modelo GeckoLib (se dibujan animados: SPGeoRenderer mueve
    «slide», «swing_l», «swing_r» y «flip» segun lo abierto; «full» solo se ve cerrado, «empty» solo abierto
    y «json_open» solo existe para el modelo JSON abierto)."""
    g.save()
    if not searchable:
        for p in (ROOT / f"geo/block/{name}.geo.json", ROOT / f"animations/block/{name}.animation.json"):
            if p.exists():
                p.unlink()
    moves = moves or {}
    tex = Image.open(ROOT / f"textures/block/{name}.png")
    TW, TH = tex.size
    lo = [99, 99, 99]
    hi = [-99, -99, -99]
    for suffix, drop in variants.items():
        elements = []
        for b in g.bones:
            if b["name"] in drop:
                continue
            off = moves.get(suffix, {}).get(b["name"], (0, 0, 0))
            for c in b["cubes"]:
                ox, oy, oz = c["origin"]
                ox, oy, oz = ox + off[0], oy + off[1], oz + off[2]
                sx, sy, sz = c["size"]
                fr = [ox + 8, oy, oz + 8]
                to = [ox + sx + 8, oy + sy, oz + sz + 8]
                if suffix == "":
                    for k in range(3):
                        lo[k] = min(lo[k], fr[k])
                        hi[k] = max(hi[k], to[k])
                faces = {}
                for f, (u, v, rw, rh, _, _) in c["uv"].items():
                    u0, v0 = u * gm.SCALE / TW * 16, v * gm.SCALE / TH * 16
                    u1, v1 = (u + rw) * gm.SCALE / TW * 16, (v + rh) * gm.SCALE / TH * 16
                    faces[f] = {"uv": [round(u0, 4), round(v0, 4), round(u1, 4), round(v1, 4)], "texture": "#t"}
                elements.append({"from": [round(x, 3) for x in fr], "to": [round(x, 3) for x in to], "faces": faces})
        model = {"parent": "block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                 "textures": {"t": f"{MOD}:block/{name}", "particle": f"{MOD}:block/{name}"}, "elements": elements}
        (ROOT / "models/block" / f"{name}{suffix}.json").write_text(json.dumps(model) + "\n", encoding="utf-8")
    variants_bs = {}
    for f, y in FACING_Y.items():
        if searchable:
            for o in ("false", "true"):
                mv = {"model": f"{MOD}:block/{name}{'_open' if o == 'true' else ''}"}
                if y:
                    mv["y"] = y
                variants_bs[f"facing={f},open={o}"] = mv
        else:
            mv = {"model": f"{MOD}:block/{name}"}
            if y:
                mv["y"] = y
            variants_bs[f"facing={f}"] = mv
    (ROOT / "blockstates" / f"{name}.json").write_text(json.dumps({"variants": variants_bs}, indent=1) + "\n", encoding="utf-8")
    (ROOT / "models/item" / f"{name}.json").write_text(json.dumps({"parent": f"{MOD}:block/{name}"}) + "\n", encoding="utf-8")
    box = [max(0, min(16, round(v))) for v in lo] + [max(0, min(16, round(v))) for v in hi]
    if box[4] - box[1] < 2:
        box[4] = box[1] + 2
    DEFS.append((name, searchable, box))


# Muebles que se registran ---------------------------------------------------------------------------------

def desk():
    """Escritorio de despacho: tablero de madera, faldon, cajonera a la derecha con tres cajones;
    al registrarlo, el de arriba sale con papeles revueltos."""
    g = Geo("desk", 31)
    g.bone("root")
    g.bone("slide", "root")
    g.cube("root", (-8, 11, -8), (16, 1.5, 16), "wood")                     # tablero
    g.cube("root", (-7.5, 3, -7.5), (0.8, 8, 15), "wood_dark")              # lateral izquierdo
    g.cube("root", (-7.5, 4, -7.5), (8, 7, 0.8), "wood_dark")               # faldon trasero
    g.cube("root", (0.5, 0, -7.5), (7, 7.5, 15), "wood_dark")               # cajonera (sin el hueco de arriba)
    g.cube("root", (0.5, 7.5, -7.5), (7, 3.5, 10), "wood_dark")
    g.cube("root", (0.5, 7.5, 2.5), (0.3, 3.5, 5), "wood_dark")
    g.cube("root", (7.2, 7.5, 2.5), (0.3, 3.5, 5), "wood_dark")
    for y in (4.2, 0.8):
        g.cube("root", (0.8, y, 7.4), (6.4, 3.0, 0.6), "wood")
        g.cube("root", (3.2, y + 1.2, 8), (1.6, 0.6, 0.6), "chrome")
    g.cube("slide", (0.8, 7.6, 7.4), (6.4, 3.0, 0.6), "wood")               # cajon de arriba
    g.cube("slide", (0.8, 7.6, 2.4), (6.4, 2.8, 5.0), "wood_dark", skip=("up",))
    g.cube("slide", (1.3, 9.0, 3), (5.4, 0.6, 4), "paper")
    g.cube("slide", (3.2, 8.8, 8), (1.6, 0.6, 0.6), "chrome")
    g.cube("root", (-6, 12.5, -4), (6, 0.3, 4.5), "paper")                  # papeles encima
    g.cube("root", (-5.5, 12.8, -3.5), (5, 0.3, 4), "paper")
    g.cube("root", (2, 12.5, -6), (1.2, 3, 1.2), "steel_dark")             # flexo
    g.cube("root", (2, 15.2, -6), (4, 0.8, 1.2), "steel_dark")
    g.blood("splat", (-3, 12.6, 2), 1.6)
    export(g, "desk", {"": set(), "_open": set()}, True, moves={"_open": {"slide": (0, 0, 5)}})


def filing_cabinet():
    """Archivador metalico de cuatro cajones con etiquetas; al registrarlo, el segundo sale con carpetas."""
    g = Geo("filing_cabinet", 32)
    g.bone("root")
    g.bone("slide", "root")
    g.cube("root", (-6, 0, -7), (12, 8.2, 14), "paint_olive")
    g.cube("root", (-6, 11.6, -7), (12, 4.4, 14), "paint_olive")
    g.cube("root", (-6, 8.2, -7), (12, 3.4, 8), "paint_olive")
    g.cube("root", (-6, 8.2, 1), (0.4, 3.4, 6), "paint_olive")
    g.cube("root", (5.6, 8.2, 1), (0.4, 3.4, 6), "paint_olive")
    for i, y in enumerate((12, 8.2, 4.4, 0.6)):
        bone = "slide" if i == 1 else "root"
        g.cube(bone, (-5.6, y, 7), (11.2, 3.4, 0.6), "paint_olive")
        g.cube(bone, (-1.5, y + 2.3, 7.6), (3, 0.6, 0.5), "chrome")
        g.cube(bone, (-1.2, y + 0.6, 7.6), (2.4, 1.2, 0.2), "label_file")
    g.cube("slide", (-5.6, 8.2, 1), (11.2, 3.2, 6), "steel_dark", skip=("up",))
    for k in range(5):
        g.cube("slide", (-5 + k * 2.2, 8.6, 2), (1.8, 3.0, 4.6), "paper")
    g.blood("drip", 4, 7.6, 16, 9)
    export(g, "filing_cabinet", {"": set(), "_open": set()}, True, moves={"_open": {"slide": (0, 0, 6)}})


def medical_cabinet():
    """Armario de botiquin: blanco con dos puertas de cristal, baldas con frascos y bolsas de suero
    y una cruz roja; al registrarlo, las puertas se abren y falta la mitad de lo de dentro."""
    g = Geo("medical_cabinet", 33)
    g.bone("root")
    g.bone("full", "root")
    g.bone("empty", "root")
    g.bone("swing_l", "root", pivot=(-7, 0, 6.3))
    g.bone("swing_r", "root", pivot=(7, 0, 6.3))
    g.bone("json_open", "root")
    g.cube("root", (-7, 0, -6), (14, 16, 12), "paint_white", skip=("south",))
    g.cube("root", (-6.5, 0.5, -5.5), (13, 15, 0.3), "locker_inner")
    for y in (5, 10):
        g.cube("root", (-6.5, y, -5.5), (13, 0.5, 11), "paint_white_dark")
    for x in (-5.5, -3.5, 2, 4):
        g.cube("full", (x, 5.5, -3), (1.4, 2.6, 1.4), "amber")
    g.cube("full", (-1, 10.5, -3), (2.4, 3.2, 1.2), "blood_bag")
    g.cube("root", (-5, 10.5, -2), (1.4, 2.0, 1.4), "jar")
    g.cube("root", (3.5, 0.6, -4), (2.6, 3.5, 3), "steel_dark")
    g.cube("swing_l", (-7, 0.5, 6), (7, 15, 0.6), "glass")
    g.cube("swing_l", (-1.5, 7, 6.6), (1, 2, 0.5), "chrome")
    g.cube("swing_r", (0, 0.5, 6), (7, 15, 0.6), "glass")
    g.cube("swing_r", (0.5, 7, 6.6), (1, 2, 0.5), "chrome")
    g.cube("json_open", (-7.6, 0.5, 6), (0.6, 15, 7), "glass")
    g.cube("json_open", (7, 0.5, 6), (0.6, 15, 7), "glass")
    g.cube("empty", (-4, 5.5, -3), (1.4, 2.6, 1.4), "amber_dead")
    g.cube("root", (-1.5, 13.6, 6.1), (3, 2, 0.4), "label_med")
    g.blood("smear", (2, 9, 6.6), 1.0, 5)
    export(g, "medical_cabinet", {"": {"empty", "json_open"}, "_open": {"full", "swing_l", "swing_r"}}, True)


def tool_cart():
    """Carro de herramientas rojo con ruedas y cajones; al registrarlo, el de arriba sale con llaves."""
    g = Geo("tool_cart", 34)
    g.bone("root")
    g.bone("slide", "root")
    g.cube("root", (-7, 2, -6), (14, 8, 12), "paint_red")
    g.cube("root", (-7, 10, -6), (14, 3, 7), "paint_red")
    g.cube("root", (-7, 10, 1), (0.4, 3, 5), "paint_red")
    g.cube("root", (6.6, 10, 1), (0.4, 3, 5), "paint_red")
    g.cube("root", (-7.3, 13, -6.3), (14.6, 0.8, 12.6), "steel_dark")
    for x in (-6.5, 5.5):
        for z in (-5.5, 4.5):
            g.cube("root", (x - 0.2, 0, z - 0.2), (1.4, 2, 1.4), "rubber")
    for i, y in enumerate((10, 6.5, 3)):
        bone = "slide" if i == 0 else "root"
        g.cube(bone, (-6.6, y, 6), (13.2, 3, 0.5), "paint_red")
        g.cube(bone, (-3, y + 1.8, 6.5), (6, 0.5, 0.5), "chrome")
    g.cube("slide", (-6.6, 10, 1), (13.2, 2.8, 5), "steel_dark", skip=("up",))
    g.cube("slide", (-5, 11.2, 2), (4, 0.6, 1), "chrome")
    g.cube("slide", (0, 11.2, 3.5), (5, 0.6, 1), "steel")
    g.cube("root", (8, 6, -4), (0.6, 7, 0.6), "chrome")                     # asa
    g.cube("root", (8, 12.4, -4), (0.6, 0.6, 8), "chrome")
    g.cube("root", (8, 6, 3.4), (0.6, 7, 0.6), "chrome")
    g.cube("root", (-5, 13.8, -4), (5, 1, 2), "steel")                      # caja de llaves encima
    export(g, "tool_cart", {"": set(), "_open": set()}, True, moves={"_open": {"slide": (0, 0, 5)}})


def supply_box():
    """Caja de suministros con la tapa atornillada y estarcido; al registrarla, la tapa se levanta y
    dentro queda gomaespuma con huecos."""
    g = Geo("supply_box", 35)
    g.bone("root")
    g.bone("flip", "root", pivot=(0, 8, -6.2))
    g.bone("empty", "root")
    g.bone("json_open", "root")
    g.cube("root", (-7, 0, -6), (14, 8, 12), "paint_olive", skip=("up",))
    g.cube("root", (-6.5, 0.5, -5.5), (13, 6.5, 11), "foam")
    g.cube("root", (-7.4, 1, -6.4), (14.8, 1, 12.8), "steel_dark")
    g.cube("root", (-3, 3, 6), (6, 3, 0.3), "crate_stencil")
    for x in (-7.6, 7):
        g.cube("root", (x, 4, -2), (0.6, 1.2, 4), "steel")                # asas
    g.cube("flip", (-7.2, 8, -6.2), (14.4, 1.4, 12.4), "paint_olive")
    g.cube("json_open", (-8, 0, -6.5), (1.2, 12, 13), "paint_olive")
    g.cube("empty", (-3, 6.8, -3), (2, 1, 6), "black")
    g.cube("empty", (1, 6.8, -3), (3, 1, 2), "black")
    export(g, "supply_box", {"": {"empty", "json_open"}, "_open": {"flip"}}, True)


# Decoracion --------------------------------------------------------------------------------------------

def metal_shelf():
    """Estanteria metalica alta con cajas, archivadores y frascos."""
    g = Geo("metal_shelf", 41)
    g.bone("root")
    for x in (-7.5, 6.5):
        for z in (-6.5, 4.5):
            g.cube("root", (x, 0, z), (1, 16, 1), "steel_dark")
    for y in (0.5, 5.5, 10.5, 15):
        g.cube("root", (-7.5, y, -6.5), (15, 0.6, 12), "steel")
    g.cube("root", (-6.5, 1.1, -5), (5, 4, 6), "paper")
    g.cube("root", (0, 1.1, -5), (6, 3, 8), "paint_olive")
    for k in range(5):
        g.cube("root", (-6.5 + k * 1.6, 6.1, -4), (1.4, 4, 6), "paint_blue" if k % 2 else "paint_red")
    g.cube("root", (2, 6.1, -3), (2, 3, 2), "jar")
    g.cube("root", (4.5, 6.1, -2), (2, 2.5, 2), "amber")
    g.cube("root", (-5, 11.1, -4), (7, 3.5, 7), "paint_cream")
    g.cube("root", (3, 11.1, -4), (3, 2, 3), "steel")
    g.blood("drip", 1, 5.5, 16, 8)
    export(g, "metal_shelf", {"": set()}, False)


def workstation():
    """Puesto de trabajo: mesa con un monitor de tubo apagado (alguno con texto verde), teclado,
    taza y papeles."""
    g = Geo("workstation", 42)
    g.bone("root")
    g.cube("root", (-8, 11, -8), (16, 1.5, 16), "paint_cream")
    for x in (-7.5, 6.5):
        for z in (-7.5, 6.5):
            g.cube("root", (x, 0, z), (1, 11, 1), "steel_dark")
    g.cube("root", (-5, 12.5, -6), (10, 8, 8), "paint_cream")             # monitor de tubo
    g.cube("root", (-4, 13.5, 2), (8, 6, 0.4), "screen_on")
    g.cube("root", (-1.5, 12.5, -7), (3, 1, 1), "black")
    g.cube("root", (-5, 12.5, 3.5), (10, 0.8, 3.5), "paint_white_dark")   # teclado
    g.cube("root", (5.5, 12.5, 2), (1.6, 2, 1.6), "paint_white")          # taza
    g.cube("root", (-7.5, 12.5, 0), (3, 0.3, 4), "paper")
    export(g, "workstation", {"": set()}, False)


def office_chair():
    """Silla de oficina giratoria con ruedas, el respaldo rajado."""
    g = Geo("office_chair", 43)
    g.bone("root")
    for (x, z) in ((-5, 0), (5, 0), (0, -5), (0, 5)):
        g.cube("root", (min(0, x) - 0.5 if x else -0.5, 0.8, min(0, z) - 0.5 if z else -0.5),
               (abs(x) + 1 if x else 1, 0.8, abs(z) + 1 if z else 1), "black")
        g.cube("root", (x - 0.6, 0, z - 0.6), (1.2, 0.9, 1.2), "rubber")
    g.cube("root", (-0.7, 1.6, -0.7), (1.4, 5, 1.4), "chrome")
    g.cube("root", (-5, 6.5, -5), (10, 1.8, 10), "leather")
    g.cube("root", (-5, 8.3, -6), (10, 9, 1.6), "leather")
    g.cube("root", (-1, 6.6, -6.5), (2, 3, 1), "chrome")
    for x in (-6, 5):
        g.cube("root", (x, 8.3, -3), (1, 3.5, 1), "black")
        g.cube("root", (x, 11.5, -4), (1, 0.8, 6), "black")
    g.blood("splat", (1, 8.4, 0), 1.4)
    export(g, "office_chair", {"": set()}, False)


def iv_stand():
    """Gotero: pie con ruedas, barra cromada y una bolsa de sangre a medio vaciar."""
    g = Geo("iv_stand", 44)
    g.bone("root")
    g.cube("root", (-4, 0.8, -0.5), (8, 0.8, 1), "chrome")
    g.cube("root", (-0.5, 0.8, -4), (1, 0.8, 8), "chrome")
    for (x, z) in ((-4, 0), (4, 0), (0, -4), (0, 4)):
        g.cube("root", (x - 0.6, 0, z - 0.6), (1.2, 0.9, 1.2), "rubber")
    g.cube("root", (-0.4, 1.6, -0.4), (0.8, 26, 0.8), "chrome")
    g.cube("root", (-3, 27, -0.3), (6, 0.5, 0.6), "chrome")
    g.cube("root", (1.5, 21, -0.8), (3, 5.5, 1.6), "blood_bag")
    g.cube("root", (2.8, 10, -0.2), (0.3, 11, 0.3), "blood_bag")
    g.cube("root", (-4, 22, -0.6), (2.4, 4.5, 1.2), "glass")
    export(g, "iv_stand", {"": set()}, False)


def wheelchair():
    """Silla de ruedas oxidada con el asiento de cuero gastado."""
    g = Geo("wheelchair", 45)
    g.bone("root")
    for x in (-7, 6.2):
        g.cube("root", (x, 0, -6), (0.8, 11, 11), "rubber", skip=())
        g.cube("root", (x + (0.8 if x < 0 else -0.6), 4, -1.5), (0.6, 2, 2), "chrome")
    g.cube("root", (-6, 6, -5), (12, 1.2, 10), "leather")
    g.cube("root", (-6, 7.2, -6), (12, 9, 1), "leather")
    for x in (-6, 5.2):
        g.cube("root", (x, 6, -6), (0.8, 12, 0.8), "chrome")
        g.cube("root", (x, 0.5, 5), (0.8, 6, 0.8), "chrome")
        g.cube("root", (x - 0.2, 0, 5.5), (1.2, 1.5, 1.5), "rubber")
    g.cube("root", (-5, 1, 6), (10, 0.6, 2), "steel_dark")
    g.blood("splat", (0, 7.3, 1), 1.8)
    export(g, "wheelchair", {"": set()}, False)


def specimen_shelf():
    """Balda de especimenes: tarros con cosas flotando (organos palidos, un ojo, algo con dientes)."""
    g = Geo("specimen_shelf", 46)
    g.bone("root")
    g.cube("root", (-8, 0, -6), (16, 1, 12), "wood_dark")
    g.cube("root", (-8, 8, -6), (16, 0.8, 12), "wood_dark")
    for x in (-8, 7):
        g.cube("root", (x, 0, -6), (1, 15, 12), "wood_dark")
    g.cube("root", (-8, 0, -6.5), (16, 15, 0.5), "wood")
    for (x, y, s) in ((-6, 1, 3.5), (-1.5, 1, 4), (3, 1, 3), (-6, 8.8, 3), (-2, 8.8, 3.5), (2.5, 8.8, 4)):
        g.cube("root", (x, y, -3), (s, s * 1.4, s), "jar")
        g.cube("root", (x + 0.6, y + 0.6, -2.4), (s - 1.2, s * 0.9, s - 1.2), "specimen")
    g.cube("root", (-5.6, 3.6, -1.5), (1, 1, 0.2), "lamp_red")             # un ojo
    export(g, "specimen_shelf", {"": set()}, False)


def sink():
    """Lavabo de hospital sobre pie, grifo cromado y la pila manchada de sangre."""
    g = Geo("sink", 47)
    g.bone("root")
    g.cube("root", (-2, 0, -5), (4, 10, 4), "paint_white")
    g.cube("root", (-7, 10, -7), (14, 3, 10), "paint_white", skip=("up",))
    g.cube("root", (-6, 11, -6), (12, 2, 8), "steel_dark")
    g.cube("root", (-7, 13, -7), (14, 0.6, 1.5), "paint_white")
    g.cube("root", (-7, 13, 1.5), (14, 0.6, 1.5), "paint_white")
    g.cube("root", (-7, 13, -7), (1.5, 0.6, 10), "paint_white")
    g.cube("root", (5.5, 13, -7), (1.5, 0.6, 10), "paint_white")
    g.cube("root", (-0.6, 13, -6.8), (1.2, 3.5, 1.2), "chrome")
    g.cube("root", (-0.6, 15.5, -6.8), (1.2, 1, 3.5), "chrome")
    g.cube("root", (-7, 14, -8), (14, 2, 0.5), "glass_cracked")            # espejo roto encima
    g.blood("splat", (0, 11.2, -2), 3.0)
    g.blood("drip", 3, 10, 2.9, 7)
    export(g, "sink", {"": set()}, False)


def trolley():
    """Carro de curas de acero con dos bandejas, instrumental y gasas manchadas."""
    g = Geo("trolley", 48)
    g.bone("root")
    for x in (-7, 6):
        for z in (-5, 4):
            g.cube("root", (x, 1, z), (1, 12, 1), "chrome")
            g.cube("root", (x - 0.2, 0, z - 0.2), (1.4, 1.2, 1.4), "rubber")
    for y in (4, 12.5):
        g.cube("root", (-7.5, y, -5.5), (15, 0.8, 11), "steel")
    g.cube("root", (-5, 13.3, -3), (4, 0.4, 1), "chrome")
    g.cube("root", (-4, 13.3, 0), (5, 0.4, 0.8), "chrome")
    g.cube("root", (2, 13.3, -4), (4, 0.6, 4), "sheet")
    g.cube("root", (-5, 4.8, -4), (6, 2.5, 5), "paint_white")
    g.cube("root", (2, 4.8, -2), (2, 3, 2), "amber")
    g.blood("splat", (3, 13.5, -2), 2.2)
    g.blood("drip", 6.8, 4.8, 12.5, 6)
    export(g, "trolley", {"": set()}, False)


MODELS = [desk, filing_cabinet, medical_cabinet, tool_cart, supply_box,
          metal_shelf, workstation, office_chair, iv_stand, wheelchair, specimen_shelf, sink, trolley]


def write_java():
    rows = []
    for name, searchable, box in DEFS:
        rows.append(f'            new Def("{name}", {str(searchable).lower()}, new int[]{{{", ".join(str(v) for v in box)}}}),')
    src = f"""package com.smileprotocol.block;

/**
 * Muebles del laboratorio (fase 24). GENERADO por tools/furniture.py: nombre, si se registra y su caja
 * de colision (pixeles, mirando al sur). No editar a mano.
 */
public final class FurnitureDefs {{

    public record Def(String name, boolean searchable, int[] box) {{}}

    public static final Def[] ALL = {{
{chr(10).join(rows)}
    }};

    private FurnitureDefs() {{}}
}}
"""
    JAVA.write_text(src, encoding="utf-8")


def main():
    for fn in MODELS:
        fn()
    write_java()
    print(f"{len(DEFS)} muebles: " + ", ".join(f"{n}{'*' if s else ''}" for n, s, _ in DEFS))


if __name__ == "__main__":
    main()
