#!/usr/bin/env python3
"""
Smiley, el jefe (docs/SMILERS.md): silueta negra pura de unos 4 bloques, delgadisimo, con brazos
que le llegan a las rodillas y dedos largos y afilados, pelo largo y lacio. Solo brillan el ojo
derecho y la sonrisa, cada uno en su placa (huesos eye y smile) para que la intro los encienda y la
sonrisa se abra escalandola.

    python3 tools/smiley_model.py [preview.png]
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mob_models as mm  # noqa: E402  (pone gm.SCALE = 4 y registra los materiales de los smilers)
from mob_models import Geo, MAT, ART, kf, wave, multi, loop_anim, split, finger_tips, enrich, render_pose, ROOT  # noqa: E402

SMILE = [
    "R..................R",
    "RR................RR",
    ".RR..............RR.",
    "..RRRRRRRRRRRRRRRR..",
    "..RTKTKTKTKTKTKTKR..",
    "...RKKKKKKKKKKKKR...",
    "....RKTKTKTKTKTR....",
    ".....RRRRRRRRRR.....",
]
EYE = [
    "..RRR..",
    ".RYYYR.",
    "RYWWWYR",
    ".RYYYR.",
    "..RRR..",
]
COLORS = {"R": (196, 12, 18), "T": (236, 210, 200), "K": (3, 0, 1), "Y": (255, 58, 40), "W": (255, 214, 200)}


def art(rows, glowing):
    for r in rows:
        assert len(r) == len(rows[0]), r

    def cells(f):
        px = np.clip(np.floor(f.U * ART).astype(int), 0, len(rows[0]) - 1)
        py = np.clip(np.floor(f.V * ART).astype(int), 0, len(rows) - 1)
        return np.vectorize(lambda x, y: rows[y][x])(px, py)

    def mask(f):
        if f.face != "north":
            return np.zeros((f.h, f.w))
        return np.isin(cells(f), list(glowing)).astype(float)

    def mat(f):
        if f.face != "north":
            return np.zeros((f.h, f.w, 3)), np.zeros((f.h, f.w))
        ch = cells(f)
        c = np.zeros((f.h, f.w, 3))
        for k, col in COLORS.items():
            c = np.where((ch == k)[..., None], np.array(col), c)
        return c, (ch != ".").astype(float)

    mat.glowmask = mask
    return mat


def void(base=(9, 8, 10)):
    """Piel de Smiley: negro casi puro, pero no plano. Brillo aceitoso que se mueve con la luz,
    tendones algo mas claros bajo la piel, estrias de piel estirada a lo largo de los miembros y
    manchas mas negras que el negro (los huecos por donde se funde en las paredes)."""
    from mob_models import veins, sstep as ss, noise3 as n3

    def m(f):
        c = np.zeros((f.h, f.w, 3)) + base
        c *= (0.7 + 0.6 * f.n(0.5, 3, 501))[..., None]
        # estrias: lineas finas y algo onduladas a lo largo del miembro
        stretch = np.abs(np.sin(f.Y * 0.35 + n3(f.X, f.Y * 0.1, f.Z, 0.7, 502) * 5 + (f.X + f.Z) * 3.1))
        c *= (1 - 0.35 * ss(0.12, 0.0, stretch))[..., None]
        # tendones y venas un poco mas claros (solo se ven con luz)
        v = veins(f, 503, freq=0.9, width=0.025)
        c = c + v[..., None] * np.array([9, 8, 11])
        # brillo grasiento azulado
        sheen = ss(0.6, 0.85, f.n(0.3, 2, 504)) * (0.6 + 0.4 * f.n(2.0, 1, 505))
        c = c + sheen[..., None] * np.array([10, 11, 15])
        # manchas de vacio: mas negras, con bordes que gotean
        hole = ss(0.7, 0.78, f.n(0.25, 3, 506))
        c *= (1 - 0.75 * hole)[..., None]
        c += (f.white()[..., None] - 0.5) * 1.2
        return np.clip(c, 0, 255), np.ones((f.h, f.w))
    return m


def void_face():
    """Cabeza: la misma piel, con la cuenca del ojo hundida, regueros rojo negruzco que bajan del ojo
    (no brillan) y la piel tirante alrededor de la boca."""
    sk = void((10, 9, 11))
    from mob_models import sstep as ss

    def m(f):
        c, a = sk(f)
        if f.face != "north":
            return c, a
        w, h = f.w / mm.gm.SCALE, f.h / mm.gm.SCALE
        # el ojo (lado +X): en la cara north de la cabeza U crece hacia -X, asi que el ojo esta a la izquierda (U pequeno)
        ex, ey = w * 0.22, h * 0.32
        r = np.hypot((f.U - ex) / 2.2, (f.V - ey) / 1.6)
        c *= (0.45 + 0.55 * ss(0.6, 1.2, r))[..., None]                         # cuenca hundida
        # regueros desde el ojo hacia abajo
        for k, (dx, ln) in enumerate(((-0.6, 4.2), (0.2, 6.0), (0.9, 3.0))):
            x0 = ex + dx + 0.15 * np.sin(f.V * 1.7 + k)
            on = (np.abs(f.U - x0) < 0.18 + 0.08 * (f.V - ey) / ln) & (f.V > ey + 0.6) & (f.V < ey + 0.6 + ln)
            fade = np.clip(1 - (f.V - ey - 0.6) / ln, 0, 1)
            c = np.where(on[..., None], c * (1 - fade[..., None]) + np.array([46, 4, 7]) * fade[..., None], c)
        # arrugas de tirantez que salen de la boca hacia las comisuras
        mouth_y = h * 0.62
        for k in range(4):
            yy = mouth_y + (k - 1.5) * 0.7 - 0.25 * ((f.U - w / 2) / (w / 2)) ** 2 * 3
            line = (np.abs(f.V - yy) < 0.1) & (np.abs(f.U - w / 2) > w * 0.36)
            c = np.where(line[..., None], c * 0.55, c)
        return c, a
    return m


def void_hair():
    """Pelo: mechones finos con raices mas oscuras, reflejos fríos y puntas abiertas."""
    from mob_models import sstep as ss, noise3 as n3

    def m(f):
        c = np.zeros((f.h, f.w, 3)) + (11, 10, 12)
        ax = f.X + f.Z if f.side else f.X
        fine = 0.5 + 0.5 * np.sin(ax * 14.0 + n3(f.X, f.Y * 0.15, f.Z, 0.9, 611) * 7)
        coarse = 0.5 + 0.5 * np.sin(ax * 4.3 + n3(f.X, f.Y * 0.1, f.Z, 0.5, 612) * 4)
        c *= (0.55 + 0.35 * fine + 0.3 * coarse)[..., None]
        # reflejo frio en franja (como pelo mojado)
        band = ss(0.55, 0.9, n3(f.X * 0.3, f.Y * 0.6, f.Z * 0.3, 1.0, 613)) * fine
        c = c + band[..., None] * np.array([14, 16, 22])
        a = np.ones((f.h, f.w))
        if f.side:
            low = f.Y - f.Y.min()
            cut = 0.3 + 1.6 * n3(ax * 3.0, 0, 0, 1.0, 614) + 0.6 * (fine < 0.3)
            a = np.where(low < cut, 0, a)
            c *= (0.75 + 0.25 * ss(0, 3, low))[..., None]
        return np.clip(c, 0, 255), a
    return m


def nail():
    """Unas largas: negras y brillantes, el filo algo mas claro."""
    from mob_models import sstep as ss

    def m(f):
        t = ss(f.Y.max() if f.Y.size else 1, f.Y.min() if f.Y.size else 0, f.Y)
        c = np.zeros((f.h, f.w, 3)) + (14, 13, 15)
        c = c + (t ** 2)[..., None] * np.array([34, 32, 36])
        c = c + ss(0.7, 0.95, f.n(1.5, 1, 621))[..., None] * 10
        return c, np.ones((f.h, f.w))
    return m


MAT.update({
    "void": void(),            # mas negro que los demas: es un hueco en la luz
    "void_hi": void((15, 14, 16)),
    "void_face": void_face(),
    "nail": nail(),
    "void_hair": void_hair(),
    "smiley_smile": art(SMILE, "RTW"),
    "smiley_eye": art(EYE, "RYW"),
})


def smiley():
    g = Geo("smiley", 99, folder="entity", glow=("smiley_smile", "smiley_eye"), ao=False)
    g.bone("root")
    g.bone("hips", "root", (0, 28, 0))
    g.cube("hips", (-2.6, 26, -1.5), (5.2, 4, 3), "void")
    g.bone("torso", "hips", (0, 30, 0))
    g.cube("torso", (-1.7, 30, -1.2), (3.4, 8.5, 2.4), "void")                      # cintura imposible
    g.cube("torso", (-3.2, 38, -1.8), (6.4, 8, 3.6), "void")                        # pecho estrecho
    for i in range(4):
        g.cube("torso", (-3.4, 39 + i * 1.8, -2.0), (6.8, 0.7, 0.4), "void_hi")        # costillas apenas
    g.cube("torso", (-4.6, 45, -1.5), (9.2, 2.3, 3), "void")                        # hombros como perchas
    for i in range(6):
        g.cube("torso", (-0.6, 31 + i * 2.5, 1.2), (1.2, 1.2, 0.8), "void_hi")         # espinazo
    g.bone("neck", "torso", (0, 47, -0.2))
    g.cube("neck", (-0.9, 46.5, -1.1), (1.8, 6, 1.8), "void")                       # cuello largo
    g.bone("head", "neck", (0, 52.3, -0.4))
    g.cube("head", (-3.4, 53, -4), (6.8, 9.5, 7), "void_face")                           # cabeza alargada
    g.cube("head", (-3, 51.5, -3.7), (6, 1.8, 6), "void")                           # mandibula
    g.bone("smile", "head", (0, 55.6, -4.1))
    g.cube("smile", (-5, 53.8, -4.25), (10, 4, 0.2), "smiley_smile")              # mas ancha que la cara
    g.bone("eye", "head", (2.35, 59.55, -4.1))
    g.cube("eye", (0.6, 58.3, -4.2), (3.5, 2.5, 0.2), "smiley_eye")               # ojo derecho
    # melena lacia por los hombros, en mechones desiguales que se afinan en la punta; el flequillo
    # cae sobre el lado izquierdo (el del ojo que no tiene) y deja ver el ojo
    g.bone("hair", "head", (0, 62.5, -0.5))
    g.cube("hair", (-3.8, 62, -4.4), (7.6, 1.4, 7.8), "void_hair")
    g.cube("hair", (-3.2, 63.2, -3.4), (6.4, 0.6, 6.0), "void_hair")                  # coronilla

    def strand(x, z, ln, w, d, back=0.0):
        top = ln * 0.62
        g.cube("hair", (x, 62.5 - top, z), (w, top, d), "void_hair")
        g.cube("hair", (x + w * 0.2, 62.5 - ln, z + back), (w * 0.6, ln - top + 0.2, d * 0.7), "void_hair")

    for k, ln in enumerate((11, 13.5, 12, 14, 12.5, 10)):
        strand(-3.9 + k * 1.3, 2.8 + (k % 2) * 0.25, ln, 1.3, 1.0, 0.25)               # espalda
    for k, (z, ln) in enumerate(((-4.2, 9), (-1.8, 11.5), (0.6, 10.5))):
        strand(-4.1, z, ln, 0.8, 2.4, 0.3)                                              # lado izquierdo
    for k, (z, ln) in enumerate(((-4.2, 5.5), (-1.8, 8), (0.6, 9))):
        strand(3.3, z, ln, 0.8, 2.4, 0.3)                                               # lado derecho, corto delante del ojo
    for x, ln in ((-3.6, 6), (-2.4, 4.5), (-1.3, 2.8)):
        g.cube("hair", (x, 62.5 - ln, -4.75), (1.2, ln, 0.4), "void_hair")              # flequillo
        g.cube("hair", (x + 0.3, 62.5 - ln - 1.2, -4.7), (0.6, 1.3, 0.3), "void_hair")

    for side, sx in (("r", 1), ("l", -1)):
        g.bone(f"arm_{side}", "torso", (sx * 4.6, 46, 0))
        g.cube(f"arm_{side}", (sx * 4.6 - 0.9, 28, -0.9), (1.8, 18.5, 1.8), "void")
        g.bone(f"forearm_{side}", f"arm_{side}", (sx * 4.6, 28.5, 0))
        g.cube(f"forearm_{side}", (sx * 4.6 - 0.75, 11.5, -0.75), (1.5, 17.5, 1.5), "void")
        g.cube(f"forearm_{side}", (sx * 4.6 - 1, 27, -1), (2, 2, 2), "void_hi")          # codo
        g.bone(f"hand_{side}", f"forearm_{side}", (sx * 4.6, 12, 0))
        g.cube(f"hand_{side}", (sx * 4.6 - 1.1, 8.5, -1.9), (2.2, 3.5, 3.6), "void")
        for i, z in enumerate((-1.7, -0.6, 0.5, 1.5)):
            ln = (10, 11.5, 11, 9)[i]
            g.bone(f"finger{i}_{side}", f"hand_{side}", (sx * 4.6, 8.8, z + 0.25))
            g.cube(f"finger{i}_{side}", (sx * 4.6 - 0.3, 8.8 - ln * 0.55, z), (0.6, ln * 0.55, 0.55), "void")
            g.cube(f"finger{i}_{side}", (sx * 4.6 - 0.22, 8.8 - ln, z + 0.04), (0.44, ln * 0.45 + 0.1, 0.47), "nail")
        g.cube(f"hand_{side}", (sx * 4.6 - sx * 1.5 - 0.3, 6, -2.2), (0.6, 4, 0.6), "nail")  # pulgar
    for side, sx in (("r", 1), ("l", -1)):
        g.bone(f"thigh_{side}", "hips", (sx * 1.8, 27, 0))
        g.cube(f"thigh_{side}", (sx * 1.8 - 1, 14, -1), (2, 13.5, 2), "void")
        g.bone(f"shin_{side}", f"thigh_{side}", (sx * 1.8, 14.5, 0))
        g.cube(f"shin_{side}", (sx * 1.8 - 0.85, 1.2, -0.85), (1.7, 13.5, 1.7), "void")
        g.cube(f"shin_{side}", (sx * 1.8 - 1.1, 13.4, -1.1), (2.2, 2, 2.2), "void_hi")  # rodilla
        g.bone(f"foot_{side}", f"shin_{side}", (sx * 1.8, 1.2, 0))
        g.cube(f"foot_{side}", (sx * 1.8 - 0.9, 0, -4.4), (1.8, 1.3, 5.4), "void")
    split(g, "torso", "chest", (0, 38, 0), lambda c: c["origin"][1] >= 38)
    for b in ("neck", "arm_r", "arm_l"):
        mm.reparent(g, b, "chest")
    split(g, "head", "jaw", (0, 53, 0.5), lambda c: abs(c["origin"][1] - 51.5) < 0.01)
    split(g, "hair", "hair_back", (0, 62.5, 2.8), lambda c: c["origin"][2] > 2.7)
    split(g, "hair", "hair_l", (-3.8, 62.5, -1), lambda c: c["origin"][0] < -3.85 and c["origin"][1] < 62)
    split(g, "hair", "hair_r", (3.8, 62.5, -1), lambda c: c["origin"][0] > 3.25 and c["origin"][1] < 62)
    split(g, "hair", "hair_front", (-1, 62.5, -4.6), lambda c: c["origin"][2] < -4.6)
    finger_tips(g, tip_mat="nail")
    return g.save()


# Animaciones ---------------------------------------------------------------------------------------------
#
# idle     de pie, enorme, ladea la cabeza despacio; los dedos tamborilean
# walk     fase 1: pasos largos y teatrales, los brazos colgando casi al suelo
# run      fase 3: encorvado y a zancadas, brazos por delante con las garras abiertas
# intro    (5 s) oscuridad: aparece de pie con la cabeza gacha, se enciende el ojo, la sonrisa se abre
# scream   (2 s) cabeza atras, mandibula abierta y brazos abiertos: aturde
# summon   (2 s) levanta los brazos y los dedos se retuercen
# retreat  bucle: dos linternas o mas le dan: se cubre la cara y se encoge
# smear_in (1 s) se aplasta contra la pared y se deshace (teletransporte)
# smear_out(1 s) sale de una mancha en la pared, desplegandose
# grab     bucle: agarra a la victima a la altura de la cara (QTE)
# execute_smiley (3,5 s) la levanta, abre la sonrisa enorme y la parte
# sealed   (3 s) derrota: se le apaga el ojo, se queda colgando y la sonrisa sigue
# drag     bucle: arrastra al ultimo jugador cogido del tobillo

ARM = 4


def base(extra=None):
    b = {
        "arm_r": {"rotation": kf([(0, [ARM, 0, -4])])}, "arm_l": {"rotation": kf([(0, [ARM, 0, 4])])},
        "forearm_r": {"rotation": kf([(0, [-6, 0, 0])])}, "forearm_l": {"rotation": kf([(0, [-6, 0, 0])])},
    }
    for k, v in (extra or {}).items():
        b.setdefault(k, {}).update(v)
    return b


def hang_hair(A):
    """El pelo cuelga: cuando la cabeza se inclina, los mechones giran al reves para seguir hacia abajo."""
    for an in A.values():
        bb = an["bones"]
        chans = [bb[b]["rotation"] for b in ("chest", "neck", "head") if "rotation" in bb.get(b, {})]
        if not chans:
            continue
        times = sorted({float(t) for c in chans for t in c})
        for hb, k in (("hair_back", 0.9), ("hair_l", 0.8), ("hair_r", 0.8)):
            if hb in bb:
                continue
            vals = []
            for t in times:
                pitch = sum(mm.sample(c, t)[0] for c in chans)
                roll = sum(mm.sample(c, t)[2] for c in chans)
                vals.append((t, [-pitch * k + 3, 0, -roll * k * 0.7]))
            bb[hb] = {"rotation": kf(vals)}


def animations():
    A = {}
    L = 4.0
    A["idle"] = loop_anim(L, base({
        "chest": {"rotation": kf(wave(L, [4, 0, 0], 1.5))},
        "neck": {"rotation": kf(wave(L, [8, 0, 0], 2, 1))},
        "head": {"rotation": kf([(0, [0, 0, 0]), (1.2, [0, 0, 0]), (2.0, [4, 10, 24]), (3.2, [4, 10, 24]), (L, [0, 0, 0])])},
        "arm_r": {"rotation": kf(wave(L, [ARM, 0, -4], 3, 0, 8, 2))}, "arm_l": {"rotation": kf(wave(L, [ARM, 0, 4], 3, 2, 8, 2))},
        **{f"finger{i}_{s}": {"rotation": kf(wave(L / 2, [8, 0, 0], 18, i * 1.2 + (0 if s == "r" else 3)))}
           for i in range(4) for s in "rl"},
    }))
    L = 2.4
    A["walk"] = loop_anim(L, base({
        "root": {"position": kf(wave(L, [0, 0, 0], 0.8, 0, 8, 1))},
        "chest": {"rotation": kf(wave(L, [8, 0, 0], 2))},
        "head": {"rotation": kf(multi(L, [6, 0, 0], [2, 6, 8], [0, 1, 2]))},
        "thigh_r": {"rotation": kf(wave(L, [0, 0, 0], 26))}, "thigh_l": {"rotation": kf(wave(L, [0, 0, 0], 26, 3.14))},
        "shin_r": {"rotation": kf(wave(L, [16, 0, 0], 16, 1.6))}, "shin_l": {"rotation": kf(wave(L, [16, 0, 0], 16, 4.7))},
        "arm_r": {"rotation": kf(wave(L, [ARM, 0, -4], 12, 3.14))}, "arm_l": {"rotation": kf(wave(L, [ARM, 0, 4], 12))},
        "forearm_r": {"rotation": kf(wave(L, [-10, 0, 0], 6, 2.5))}, "forearm_l": {"rotation": kf(wave(L, [-10, 0, 0], 6, 5.6))},
    }))
    L = 0.9
    A["run"] = loop_anim(L, base({
        "root": {"position": kf(wave(L, [0, -6, 0], 1.4, 0, 8, 1))},
        "hips": {"rotation": kf([(0, [8, 0, 0])])},
        "torso": {"rotation": kf(wave(L, [26, 0, 0], 4))},
        "chest": {"rotation": kf(wave(L, [16, 0, 0], 3, 1))},
        "neck": {"rotation": kf([(0, [-30, 0, 0])])},
        "head": {"rotation": kf(multi(L, [-12, 0, 0], [5, 8, 10], [0, 1, 2]))},
        "thigh_r": {"rotation": kf(wave(L, [-30, 0, 0], 40))}, "thigh_l": {"rotation": kf(wave(L, [-30, 0, 0], 40, 3.14))},
        "shin_r": {"rotation": kf(wave(L, [45, 0, 0], 30, 1.6))}, "shin_l": {"rotation": kf(wave(L, [45, 0, 0], 30, 4.7))},
        "arm_r": {"rotation": kf(wave(L, [-70, -10, -10], 30, 3.14))}, "arm_l": {"rotation": kf(wave(L, [-70, 10, 10], 30))},
        "forearm_r": {"rotation": kf([(0, [-20, 0, 0])])}, "forearm_l": {"rotation": kf([(0, [-20, 0, 0])])},
        **{f"finger{i}_{s}": {"rotation": kf([(0, [-25, 0, (i - 1.5) * 8 * (1 if s == "r" else -1)])])} for i in range(4) for s in "rl"},
    }))
    L = 5.0
    A["intro"] = loop_anim(L, base({
        "neck": {"rotation": kf([(0, [40, 0, 0]), (2.6, [40, 0, 0]), (3.4, [0, 0, 0]), (L, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [30, 0, 0]), (2.6, [30, 0, 0]), (3.4, [0, 0, 28]), (4.2, [0, 0, 18]), (L, [0, 0, 0])])},
        "eye": {"scale": kf([(0, [0, 0, 1]), (1.6, [0, 0, 1]), (1.7, [1, 0.2, 1]), (1.9, [1, 1, 1]), (2.1, [1, 0.1, 1]),
                             (2.2, [1, 1, 1]), (L, [1, 1, 1])])},
        "smile": {"scale": kf([(0, [0.2, 0, 1]), (2.6, [0.2, 0, 1]), (3.0, [0.5, 0.15, 1]), (3.6, [1, 1, 1]),
                               (4.2, [1.15, 1.2, 1]), (L, [1, 1, 1])])},
        "jaw": {"rotation": kf([(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.6, [18, 0, 0]), (4.4, [26, 0, 0]), (L, [4, 0, 0])])},
        "arm_r": {"rotation": kf([(0, [ARM, 0, -2]), (3.4, [ARM, 0, -2]), (4.2, [ARM, 0, -20]), (L, [ARM, 0, -4])])},
        "arm_l": {"rotation": kf([(0, [ARM, 0, 2]), (3.4, [ARM, 0, 2]), (4.2, [ARM, 0, 20]), (L, [ARM, 0, 4])])},
        **{f"finger{i}_{s}": {"rotation": kf([(0, [40, 0, 0]), (3.6, [40, 0, 0]), (4.2, [-30, 0, (i - 1.5) * 10 * (1 if s == "r" else -1)]),
                                               (L, [6, 0, 0])])} for i in range(4) for s in "rl"},
    }), loop="hold_on_last_frame")
    L = 2.0
    A["scream"] = loop_anim(L, base({
        "chest": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-10, 0, 0]), (1.6, [-12, 0, 0]), (L, [0, 0, 0])])},
        "neck": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-12, 0, 0]), (1.6, [-14, 0, 0]), (L, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-14, 0, 0]), (0.4, [-14, 6, 4]), (0.5, [-14, -6, -4]), (0.6, [-14, 6, 4]),
                                 (0.7, [-14, -6, -4]), (1.6, [-14, 0, 0]), (L, [0, 0, 0])])},
        "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.3, [45, 0, 0]), (1.6, [45, 0, 0]), (L, [0, 0, 0])])},
        "smile": {"scale": kf([(0, [1, 1, 1]), (0.3, [1.2, 1.8, 1]), (1.6, [1.2, 1.8, 1]), (L, [1, 1, 1])])},
        "arm_r": {"rotation": kf([(0, [ARM, 0, -4]), (0.3, [-20, 0, -80]), (1.6, [-20, 0, -85]), (L, [ARM, 0, -4])])},
        "arm_l": {"rotation": kf([(0, [ARM, 0, 4]), (0.3, [-20, 0, 80]), (1.6, [-20, 0, 85]), (L, [ARM, 0, 4])])},
        **{f"finger{i}_{s}": {"rotation": kf([(0, [6, 0, 0]), (0.3, [-35, 0, 0]), (1.6, [-35, 0, 0]), (L, [6, 0, 0])])}
           for i in range(4) for s in "rl"},
    }), loop=False)
    A["summon"] = loop_anim(L, base({
        "chest": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-8, 0, 0]), (1.6, [-8, 0, 0]), (L, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-24, 0, 0]), (1.6, [-24, 0, 0]), (L, [0, 0, 0])])},
        "arm_r": {"rotation": kf([(0, [ARM, 0, -4]), (0.5, [-160, 0, -25]), (1.6, [-165, 0, -30]), (L, [ARM, 0, -4])])},
        "arm_l": {"rotation": kf([(0, [ARM, 0, 4]), (0.5, [-160, 0, 25]), (1.6, [-165, 0, 30]), (L, [ARM, 0, 4])])},
        **{f"finger{i}_{s}": {"rotation": kf(wave(L, [10, 0, 0], 40, i * 1.5 + (0 if s == "r" else 0.8), 16))}
           for i in range(4) for s in "rl"},
    }), loop=False)
    L = 0.8
    A["retreat"] = loop_anim(L, base({
        "torso": {"rotation": kf(wave(L, [-12, 0, 0], 3))},
        "chest": {"rotation": kf(wave(L, [20, 0, 0], 3, 1))},
        "head": {"rotation": kf(multi(L, [25, 0, 0], [5, 12, 8], [0, 1, 2]))},
        "arm_r": {"rotation": kf([(0, [-140, -35, 10])])}, "arm_l": {"rotation": kf([(0, [-140, 35, -10])])},
        "forearm_r": {"rotation": kf(wave(L, [-95, 0, 0], 8))}, "forearm_l": {"rotation": kf(wave(L, [-95, 0, 0], 8, 1))},
        "eye": {"scale": kf([(0, [1, 0.25, 1])])},
        "thigh_r": {"rotation": kf([(0, [-14, 0, 0])])}, "thigh_l": {"rotation": kf([(0, [-14, 0, 0])])},
        "shin_r": {"rotation": kf([(0, [26, 0, 0])])}, "shin_l": {"rotation": kf([(0, [26, 0, 0])])},
        "root": {"position": kf([(0, [0, -2, 0])])},
    }))
    L = 1.0
    A["smear_in"] = loop_anim(L, base({
        "root": {"scale": kf([(0, [1, 1, 1]), (0.4, [1.3, 1.05, 0.5]), (0.8, [1.8, 0.7, 0.05]), (L, [0.1, 0.1, 0.02])]),
                 "position": kf([(0, [0, 0, 0]), (L, [0, 0, 6])])},
        "arm_r": {"rotation": kf([(0, [ARM, 0, -4]), (0.6, [0, 0, -70]), (L, [0, 0, -90])])},
        "arm_l": {"rotation": kf([(0, [ARM, 0, 4]), (0.6, [0, 0, 70]), (L, [0, 0, 90])])},
    }), loop="hold_on_last_frame")
    A["smear_out"] = loop_anim(L, base({
        "root": {"scale": kf([(0, [1.8, 0.7, 0.05]), (0.5, [1.3, 1.05, 0.5]), (L, [1, 1, 1])]),
                 "position": kf([(0, [0, 0, 6]), (L, [0, 0, 0])])},
        "arm_r": {"rotation": kf([(0, [0, 0, -90]), (0.6, [-20, 0, -40]), (L, [ARM, 0, -4])])},
        "arm_l": {"rotation": kf([(0, [0, 0, 90]), (0.6, [-20, 0, 40]), (L, [ARM, 0, 4])])},
        "neck": {"rotation": kf([(0, [30, 0, 0]), (0.7, [10, 0, 0]), (L, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 60]), (0.7, [0, 0, 30]), (L, [0, 0, 0])])},
    }), loop="hold_on_last_frame")
    L = 1.0
    A["grab"] = loop_anim(L, base({
        "chest": {"rotation": kf(wave(L, [22, 0, 0], 3))},
        "neck": {"rotation": kf([(0, [20, 0, 0])])},
        "head": {"rotation": kf(multi(L, [10, 0, 0], [4, 10, 14], [0, 1, 2]))},
        "jaw": {"rotation": kf(wave(L, [14, 0, 0], 8, 0, 8))},
        "arm_r": {"rotation": kf(wave(L, [-62, -20, -6], 5))}, "arm_l": {"rotation": kf(wave(L, [-62, 20, 6], 5, 1))},
        "forearm_r": {"rotation": kf([(0, [-50, 0, 0])])}, "forearm_l": {"rotation": kf([(0, [-50, 0, 0])])},
        **{f"finger{i}_{s}": {"rotation": kf([(0, [65, 0, 0])])} for i in range(4) for s in "rl"},
    }))
    # remate: la levanta del cuello hasta su cara (0-1,2), abre la sonrisa enorme (1,2-2,0) y la parte (2,2)
    L = 3.5
    A["execute_smiley"] = loop_anim(L, base({
        "chest": {"rotation": kf([(0, [22, 0, 0]), (1.2, [6, 0, 0]), (2.1, [10, 0, 0]), (2.25, [26, 0, 0]), (L, [20, 0, 0])])},
        "neck": {"rotation": kf([(0, [20, 0, 0]), (1.2, [0, 0, 0]), (2.0, [-6, 0, 0]), (2.25, [24, 0, 0]), (L, [18, 0, 0])])},
        "head": {"rotation": kf([(0, [10, 0, 0]), (1.2, [0, 0, 20]), (1.9, [-6, 0, 30]), (2.25, [16, 0, 4]), (L, [12, 0, 10])])},
        "jaw": {"rotation": kf([(0, [10, 0, 0]), (1.2, [6, 0, 0]), (1.9, [50, 0, 0]), (2.2, [55, 0, 0]), (2.3, [6, 0, 0]),
                                (L, [12, 0, 0])])},
        "smile": {"scale": kf([(0, [1, 1, 1]), (1.2, [1, 1, 1]), (1.9, [1.4, 2.4, 1]), (2.2, [1.4, 2.4, 1]), (2.3, [1.05, 1, 1]),
                               (L, [1.1, 1.1, 1])])},
        "arm_r": {"rotation": kf([(0, [-62, -20, -6]), (1.2, [-110, -18, -4]), (2.1, [-112, -18, -4]), (2.25, [-60, -40, -30]),
                                  (L, [-40, -30, -30])])},
        "arm_l": {"rotation": kf([(0, [-62, 20, 6]), (1.2, [-110, 18, 4]), (2.1, [-112, 18, 4]), (2.25, [-60, 40, 30]),
                                  (L, [-40, 30, 30])])},
        "forearm_r": {"rotation": kf([(0, [-50, 0, 0]), (1.2, [-20, 0, 0]), (2.25, [-10, 0, 0]), (L, [-10, 0, 0])])},
        "forearm_l": {"rotation": kf([(0, [-50, 0, 0]), (1.2, [-20, 0, 0]), (2.25, [-10, 0, 0]), (L, [-10, 0, 0])])},
        **{f"finger{i}_{s}": {"rotation": kf([(0, [65, 0, 0]), (2.1, [70, 0, 0]), (2.25, [-20, 0, 0]), (L, [10, 0, 0])])}
           for i in range(4) for s in "rl"},
    }), loop="hold_on_last_frame")
    L = 3.0
    A["sealed"] = loop_anim(L, base({
        "eye": {"scale": kf([(0, [1, 1, 1]), (0.4, [1, 0.3, 1]), (0.5, [1, 1, 1]), (1.0, [1, 0.6, 1]), (1.4, [1, 0, 1]), (L, [0, 0, 1])])},
        "chest": {"rotation": kf([(0, [4, 0, 0]), (1.4, [4, 0, 0]), (2.4, [30, 0, 0]), (L, [32, 0, 0])])},
        "neck": {"rotation": kf([(0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [45, 0, 0]), (L, [48, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [20, 0, 26]), (L, [22, 0, 30])])},
        "arm_r": {"rotation": kf([(0, [ARM, 0, -4]), (2.4, [10, 0, -1]), (L, [12, 0, 0])])},
        "arm_l": {"rotation": kf([(0, [ARM, 0, 4]), (2.4, [10, 0, 1]), (L, [12, 0, 0])])},
        "thigh_r": {"rotation": kf([(0, [0, 0, 0]), (2.4, [-20, 0, 0])])}, "thigh_l": {"rotation": kf([(0, [0, 0, 0]), (2.4, [-20, 0, 0])])},
        "shin_r": {"rotation": kf([(0, [0, 0, 0]), (2.4, [36, 0, 0])])}, "shin_l": {"rotation": kf([(0, [0, 0, 0]), (2.4, [36, 0, 0])])},
        "root": {"position": kf([(0, [0, 0, 0]), (2.4, [0, -3, 0])])},
        **{f"finger{i}_{s}": {"rotation": kf([(0, [6, 0, 0]), (2.4, [30, 0, 0])])} for i in range(4) for s in "rl"},
    }), loop="hold_on_last_frame")
    L = 2.4
    A["drag"] = loop_anim(L, base({
        "root": {"position": kf(wave(L, [0, 0, 0], 0.6, 0, 8, 1))},
        "chest": {"rotation": kf(wave(L, [10, 0, 0], 2))},
        "head": {"rotation": kf([(0, [4, 0, 0]), (1.2, [4, 150, 0]), (1.6, [4, 150, 0]), (L, [4, 0, 0])])},  # mira atras, a ti
        "thigh_r": {"rotation": kf(wave(L, [0, 0, 0], 20))}, "thigh_l": {"rotation": kf(wave(L, [0, 0, 0], 20, 3.14))},
        "shin_r": {"rotation": kf(wave(L, [14, 0, 0], 12, 1.6))}, "shin_l": {"rotation": kf(wave(L, [14, 0, 0], 12, 4.7))},
        "arm_r": {"rotation": kf([(0, [30, 0, -8])])},       # brazo hacia atras: arrastra al jugador del tobillo
        "forearm_r": {"rotation": kf([(0, [10, 0, 0])])},
        **{f"finger{i}_r": {"rotation": kf([(0, [70, 0, 0])])} for i in range(4)},
    }))
    hang_hair(A)
    enrich(A, mm.bones_of("smiley"))
    out = {"format_version": "1.8.0", "animations": A}
    (ROOT / "animations/entity/smiley.animation.json").write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
    return A


def main():
    tw, th, nf = smiley()
    A = animations()
    print(f"smiley textura {tw * mm.gm.SCALE}x{th * mm.gm.SCALE} caras={nf} animaciones={list(A)}")
    if len(sys.argv) > 1:
        frames = [("idle", 0.0, 0), ("idle", 2.5, 25), ("walk", 0.6, 60), ("run", 0.2, 50),
                  ("intro", 1.0, 0), ("intro", 4.3, 15), ("scream", 1.0, 20), ("summon", 1.0, 340),
                  ("retreat", 0.2, 20), ("sealed", 3.0, 30), ("grab", 0.3, 40), ("execute_smiley", 2.0, 30)]
        ims = [render_pose(A[n], t, yaw=y, name="smiley", height=72, bright=3.0, lift=26) for n, t, y in frames]
        sheet = Image.new("RGB", (4 * 420, 3 * 420))
        for i, im in enumerate(ims):
            sheet.paste(im, ((i % 4) * 420, (i // 4) * 420))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
