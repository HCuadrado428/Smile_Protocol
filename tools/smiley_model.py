#!/usr/bin/env python3
"""
Smiley, el jefe (docs/SMILERS.md): silueta negra pura de unos 4 bloques, delgadisimo, con brazos
que le llegan a las rodillas y dedos largos y afilados, pelo largo y lacio. Solo brillan el ojo
derecho y la sonrisa, cada uno en su placa (huesos eye y smile) para que la intro los encienda y la
sonrisa se abra escalandola.

    python3 tools/smiley_model.py [preview.png]
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mob_models as mm  # noqa: E402  (pone gm.SCALE = 4 y registra los materiales de los smilers)
from mob_models import Geo, MAT, ART, kf, wave, multi, loop_anim, split, finger_tips, enrich, render_pose, ROOT  # noqa: E402

# Sonrisa: muy ancha, torcida (la comisura derecha sube mas), dos filas de dientes desiguales con
# colmillos largos, encias rojas y huecos negros
SMILE = [
    "RR..................RR",
    ".RR................RR.",
    "..RRR............RRR..",
    "...RRRRRRRRRRRRRRRR...",
    "...RTTKTVTTKTTVTKTTR..",
    "...RKVKKKTKKKKTKVKKR..",
    "....RKTKKKKKKKKKTKR...",
    ".....RTKTVTKTTKVTR....",
    "......RRRRRRRRRRR.....",
]
# Ojo: rojo encendido con la pupila rasgada en vertical
EYE = [
    "..RRR..",
    ".RYWYR.",
    "RYWKWYR",
    ".RYWYR.",
    "..RRR..",
]
COLORS = {"R": (196, 12, 18), "T": (236, 210, 200), "V": (200, 170, 150), "K": (3, 0, 1), "Y": (226, 14, 16),
          "W": (255, 34, 26)}             # el ojo, todo rojo (mas vivo en el centro) salvo la pupila negra


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


def void_crack():
    """Piel con grietas finas que brillan como brasas por dentro (solo en el pecho y los hombros):
    asi se le adivina el cuerpo en la oscuridad."""
    sk = void()
    from mob_models import noise3 as n3

    def cracks(f):
        cr = np.abs(n3(f.X * 0.55, f.Y * 0.35, f.Z * 0.55, 1.0, 701) - 0.5)
        thin = cr < 0.018
        branch = (np.abs(n3(f.X * 1.3, f.Y * 0.9, f.Z * 1.3, 1.0, 702) - 0.5) < 0.012) & (f.n(0.4, 2, 703) > 0.55)
        return (thin | branch).astype(float)

    def m(f):
        c, a = sk(f)
        k = cracks(f)
        c = c * (1 - k[..., None]) + np.array([150, 18, 12]) * k[..., None]
        return c, a

    m.glowmask = lambda f: cracks(f) * 0.75
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
    "smiley_smile": art(SMILE, "RTVW"),
    "smiley_eye": art(EYE, "RYW"),
    "smiley_drip": art(["R"], "R"),
    "smiley_pin": art(["Y"], "Y"),
    "void_crack": void_crack(),
})


def smiley():
    """Esqueleto: cadera > cintura > pecho > (hombros > brazo > antebrazo > mano > dedos/pulgar)
    y (cuello > cuello2 > cabeza > mandibula/sonrisa/ojo/pelo); piernas: muslo > espinilla > pie > dedos."""
    g = Geo("smiley", 99, folder="entity", glow=("smiley_smile", "smiley_eye", "void_crack", "smiley_drip", "smiley_pin"),
            ao=False)
    g.bone("root")
    g.bone("hips", "root", (0, 28, 0))
    g.cube("hips", (-2.6, 26, -1.5), (5.2, 4, 3), "void")
    # columna en tres tramos que se doblan por separado
    g.bone("torso", "hips", (0, 30, 0))
    g.cube("torso", (-1.7, 30, -1.2), (3.4, 8.5, 2.4), "void")                      # cintura imposible
    g.cube("torso", (-2.4, 33.5, -1.45), (4.8, 1.2, 0.3), "void_hi")               # vientre hundido, con un pliegue
    for i in range(3):
        g.cube("torso", (-0.6, 31 + i * 2.5, 1.2), (1.2, 1.2, 0.8), "void_hi")      # espinazo
    g.bone("chest", "torso", (0, 38, 0))
    g.cube("chest", (-3.2, 38, -1.8), (6.4, 8, 3.6), "void_crack")                  # pecho estrecho, agrietado
    for i in range(5):
        g.cube("chest", (-3.5 + i * 0.1, 38.6 + i * 1.5, -2.15), (7.0 - i * 0.2, 0.75, 0.5), "void_hi")   # costillas
    for i in range(3, 6):
        g.cube("chest", (-0.6, 31 + i * 2.5, 1.2), (1.2, 1.2, 0.8), "void_hi")
    for x, ln in ((-2.2, 7), (-0.6, 9.5), (1.2, 6), (2.6, 8.5)):
        g.cube("chest", (x, 44 - ln, 1.6), (0.35, ln, 0.3), "void")                 # jirones de la espalda
    g.cube("chest", (-1.6, 45, -1.5), (3.2, 2.3, 3), "void_crack")                  # centro de la percha
    # hombros: huesos propios para encogerlos, dejarlos caer o tirar hacia atras
    for side, sx in (("r", 1), ("l", -1)):
        g.bone(f"shoulder_{side}", "chest", (sx * 1.6, 45.8, 0))
        g.cube(f"shoulder_{side}", (1.4 if sx > 0 else -4.6, 45, -1.5), (3.2, 2.3, 3), "void_crack")  # hombros como perchas
    g.cube("shoulder_l", (-5.1, 46.4, -0.6), (1.6, 0.6, 0.6), "nail")                 # clavicula rota que asoma
    # cuello en dos tramos: se curva en S y la cabeza puede quedarse quieta mientras el cuerpo se mueve
    g.bone("neck", "chest", (0, 46.5, -0.2))
    g.cube("neck", (-0.9, 46.5, -1.1), (1.8, 3.2, 1.8), "void")
    g.bone("neck2", "neck", (0, 49.5, -0.3))
    g.cube("neck2", (-0.85, 49.4, -1.15), (1.7, 3.4, 1.7), "void")
    g.bone("head", "neck2", (0, 52.3, -0.4))
    g.cube("head", (-3.4, 53, -4), (6.8, 9.5, 7), "void_face")                      # cabeza alargada
    g.cube("head", (-3, 51.5, -3.7), (6, 1.8, 6), "void")                           # mandibula
    g.bone("smile", "head", (0, 55.6, -4.1))
    g.cube("smile", (-5.5, 53.5, -4.25), (11, 4.5, 0.2), "smiley_smile")          # mas ancha que la cara
    for x, ln in ((-4.6, 2.6), (-3.1, 1.4), (4.2, 3.4), (1.5, 1.0)):
        g.cube("smile", (x, 53.6 - ln, -4.24), (0.35, ln, 0.15), "smiley_drip")   # gotas de las comisuras
    g.bone("eye", "head", (2.35, 59.55, -4.1))
    g.cube("eye", (0.6, 58.3, -4.2), (3.5, 2.5, 0.2), "smiley_eye")               # ojo derecho
    g.cube("eye", (2.0, 56.0, -4.19), (0.3, 2.3, 0.15), "smiley_drip")            # lagrima roja
    g.cube("head", (-2.6, 59.2, -4.12), (0.35, 0.35, 0.1), "smiley_pin")         # chispa en la cuenca vacia
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
        g.bone(f"arm_{side}", f"shoulder_{side}", (sx * 4.6, 46, 0))
        g.cube(f"arm_{side}", (sx * 4.6 - 0.9, 28, -0.9), (1.8, 18.5, 1.8), "void")
        g.bone(f"forearm_{side}", f"arm_{side}", (sx * 4.6, 28.5, 0))
        g.cube(f"forearm_{side}", (sx * 4.6 - 0.75, 11.5, -0.75), (1.5, 17.5, 1.5), "void")
        g.cube(f"forearm_{side}", (sx * 4.6 - 1, 27, -1), (2, 2, 2), "void_hi")          # codo
        for dz, ln in ((-0.6, 4.5), (0.4, 6.5)):
            g.cube(f"forearm_{side}", (sx * 4.6 + sx * 0.75 - (0.3 if sx > 0 else 0), 24 - ln, dz), (0.3, ln, 0.3), "void")  # jirones
        g.bone(f"hand_{side}", f"forearm_{side}", (sx * 4.6, 12, 0))
        g.cube(f"hand_{side}", (sx * 4.6 - 1.1, 8.5, -1.9), (2.2, 3.5, 3.6), "void")
        g.cube(f"hand_{side}", (sx * 4.6 - 1.25, 8.6, -1.8), (2.5, 0.8, 3.4), "void_hi")      # nudillos
        for i, z in enumerate((-1.7, -0.6, 0.5, 1.5)):
            ln = (10, 11.5, 11, 9)[i]
            g.bone(f"finger{i}_{side}", f"hand_{side}", (sx * 4.6, 8.8, z + 0.25))
            g.cube(f"finger{i}_{side}", (sx * 4.6 - 0.3, 8.8 - ln * 0.55, z), (0.6, ln * 0.55, 0.55), "void")
            g.cube(f"finger{i}_{side}", (sx * 4.6 - 0.22, 8.8 - ln, z + 0.04), (0.44, ln * 0.45 + 0.1, 0.47), "nail")
        g.bone(f"thumb_{side}", f"hand_{side}", (sx * 3.1, 10, -2.0))
        g.cube(f"thumb_{side}", (sx * 3.1 - 0.3, 6, -2.2), (0.6, 4, 0.6), "nail")           # pulgar
    for side, sx in (("r", 1), ("l", -1)):
        g.bone(f"thigh_{side}", "hips", (sx * 1.8, 27, 0))
        g.cube(f"thigh_{side}", (sx * 1.8 - 1, 14, -1), (2, 13.5, 2), "void")
        g.bone(f"shin_{side}", f"thigh_{side}", (sx * 1.8, 14.5, 0))
        g.cube(f"shin_{side}", (sx * 1.8 - 0.85, 1.2, -0.85), (1.7, 13.5, 1.7), "void")
        g.cube(f"shin_{side}", (sx * 1.8 - 1.1, 13.4, -1.1), (2.2, 2, 2.2), "void_hi")  # rodilla
        g.bone(f"foot_{side}", f"shin_{side}", (sx * 1.8, 1.2, 0))
        g.cube(f"foot_{side}", (sx * 1.8 - 0.9, 0, -1.8), (1.8, 1.3, 2.8), "void")     # talon
        g.bone(f"toe_{side}", f"foot_{side}", (sx * 1.8, 0.6, -1.8))
        g.cube(f"toe_{side}", (sx * 1.8 - 0.85, 0, -4.6), (1.7, 1.0, 2.85), "void")     # dedos largos
        g.cube(f"toe_{side}", (sx * 1.8 - 0.5, 0, -5.05), (1.0, 0.55, 0.5), "nail")
    split(g, "head", "jaw", (0, 53, 0.5), lambda c: abs(c["origin"][1] - 51.5) < 0.01)
    split(g, "hair", "hair_back", (0, 62.5, 2.8), lambda c: c["origin"][2] > 2.7)
    split(g, "hair", "hair_l", (-3.8, 62.5, -1), lambda c: c["origin"][0] < -3.85 and c["origin"][1] < 62)
    split(g, "hair", "hair_r", (3.8, 62.5, -1), lambda c: c["origin"][0] > 3.25 and c["origin"][1] < 62)
    split(g, "hair", "hair_front", (-1, 62.5, -4.6), lambda c: c["origin"][2] < -4.6)
    finger_tips(g, tip_mat="nail")
    return g.save()


# Animaciones ---------------------------------------------------------------------------------------------
#
# idle     de pie, respira, carga el peso de una pierna a otra; ladea la cabeza despacio y la endereza de golpe,
#          parpadea y tamborilea con los dedos
# walk     fase 1: pasos largos y teatrales; cadera y pecho giran al reves, los brazos se balancean con retraso
#          en antebrazo, mano y dedos, el pie apoya el talon y empuja con los dedos; la cabeza no se mueve del sitio
# run      fase 3: encorvado y a zancadas, brazos por delante con las garras abiertas; la cabeza fija en ti
# intro    (5 s) aparece encorvado; se enciende el ojo, se endereza vertebra a vertebra, la sonrisa se abre
#          y abre los brazos haciendo crujir los dedos uno a uno
# scream   (2 s) coge aire agachandose, se echa atras con la mandibula abierta y los brazos abiertos: aturde
# summon   (2 s) levanta los brazos (el antebrazo llega despues) y los dedos se retuercen
# retreat  bucle: dos linternas o mas le dan: se cubre la cara, se encoge y tiembla
# smear_in (1 s) se aplasta contra la pared y se deshace (teletransporte)
# smear_out(1 s) sale de una mancha en la pared, desplegandose
# grab     bucle: agarra a la victima a la altura de la cara (QTE), aprieta y la examina
# execute_smiley (3,5 s) la levanta (de puntillas), abre la sonrisa enorme, coge impulso y la parte (2,25 s)
# sealed   (3 s) derrota: el ojo parpadea y se apaga, se le doblan las rodillas y se queda colgando
# drag     bucle: arrastra al ultimo jugador cogido del tobillo, inclinado contra el peso

ARM = 4
TAU = math.tau


def smooth(keys, loop=False, fps=16):
    """Poses clave -> fotogramas densos con curva cubica monotona (sin pasarse de la pose, y las poses
    repetidas se quedan quietas). GeckoLib interpola en linea recta entre fotogramas: asi el movimiento
    acelera y frena como un cuerpo en vez de ir a tirones."""
    keys = sorted(((float(t), [float(x) for x in v]) for t, v in keys), key=lambda k: k[0])
    if len(keys) == 1:
        return kf(keys)
    T = np.array([k[0] for k in keys])
    V = np.array([k[1] for k in keys])
    H = np.diff(T)
    D = np.diff(V, axis=0) / H[:, None]
    M = np.zeros_like(V)

    def tangent(a, b):
        with np.errstate(divide="ignore", invalid="ignore"):
            return np.where(a * b > 0, 2 * a * b / (a + b), 0.0)

    for i in range(1, len(T) - 1):
        M[i] = tangent(D[i - 1], D[i])
    if loop and len(T) > 2:
        M[0] = M[-1] = tangent(D[-1], D[0])
    out = []
    for i in range(len(T) - 1):
        n = max(1, math.ceil(H[i] * fps))
        for s in range(n):
            u = s / n
            h00, h10, h01, h11 = 2 * u ** 3 - 3 * u ** 2 + 1, u ** 3 - 2 * u ** 2 + u, -2 * u ** 3 + 3 * u ** 2, u ** 3 - u ** 2
            out.append((T[i] + u * H[i], list(h00 * V[i] + h10 * H[i] * M[i] + h01 * V[i + 1] + h11 * H[i] * M[i + 1])))
    out.append((T[-1], list(V[-1])))
    return kf(out)


def R(*keys, loop=False):
    return {"rotation": smooth(keys, loop)}


def mirror(keys):
    return [(t, [v[0], -v[1], -v[2]]) for t, v in keys]


def both(name, *keys, loop=False):
    """El mismo gesto en los dos lados (el izquierdo en espejo)."""
    return {f"{name}_r": R(*keys, loop=loop), f"{name}_l": R(*mirror(keys), loop=loop)}


def fingers(fn, sides="rl"):
    """fn(i, lado) -> canal de rotacion de cada dedo."""
    return {f"finger{i}_{s}": fn(i, s) for i in range(4) for s in sides}


def cyc(L, f, steps=24):
    """Bucle procedural: f(fase en radianes) -> [x, y, z]."""
    return kf([(L * i / steps, f(TAU * i / steps)) for i in range(steps + 1)])


def pos(f):
    return lambda p: list(f(p))


def gait(L, stride, knee, lift=0.0, lean=0.0, sides="rl"):
    """Piernas de un ciclo de paso. Fase 0 = la pierna derecha pasa por debajo hacia delante; en
    pi/2 apoya el talon delante; en 3pi/2 empuja con los dedos detras."""
    out = {}
    for s, off in (("r", 0.0), ("l", math.pi)):
        if s not in sides:
            continue
        out[f"thigh_{s}"] = {"rotation": cyc(L, lambda p, o=off: [lean - stride * math.sin(p + o), 0, 0])}
        out[f"shin_{s}"] = {"rotation": cyc(L, lambda p, o=off: [lift + knee * max(0.0, math.cos(p + o - 0.35)) ** 1.5 +
                                                                 0.15 * knee * max(0.0, math.sin(p + o - 1.6)), 0, 0])}
        out[f"foot_{s}"] = {"rotation": cyc(L, lambda p, o=off: [-0.5 * stride * max(0.0, math.sin(p + o)) ** 2 +
                                                                 0.9 * stride * max(0.0, -math.sin(p + o + 0.5)) ** 3 -
                                                                 lean * 0.5 - lift * 0.6, 0, 0])}
        out[f"toe_{s}"] = {"rotation": cyc(L, lambda p, o=off: [-1.3 * stride * max(0.0, -math.sin(p + o + 0.3)) ** 2 +
                                                                0.4 * stride * max(0.0, math.cos(p + o)), 0, 0])}
    return out


def hang_hair(A):
    """El pelo cuelga: cuando la cabeza se inclina, los mechones giran al reves para seguir hacia abajo."""
    for an in A.values():
        bb = an["bones"]
        chans = [bb[b]["rotation"] for b in ("hips", "torso", "chest", "neck", "neck2", "head") if "rotation" in bb.get(b, {})]
        if not chans:
            continue
        times = sorted({float(t) for c in chans for t in c})
        if len(times) > 60:
            step = max(1, len(times) // 60)
            times = sorted(set(times[::step]) | {times[-1]})
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
    s, c = math.sin, math.cos

    # idle: respira (pecho y hombros), carga el peso de un lado a otro con la cadera y el pecho
    # compensando; ladea la cabeza despacio, la mantiene y la endereza de un tiron; parpadea
    L = 4.8
    A["idle"] = loop_anim(L, {
        "root": {"position": cyc(L, lambda p: [0.35 * s(p), 0.12 * c(2 * p), 0])},
        "hips": {"rotation": cyc(L, lambda p: [0, 2 * s(p), 2.2 * s(p)])},
        "torso": {"rotation": cyc(L, lambda p: [1 + 1.2 * s(2 * p), 0, -1.2 * s(p)])},
        "chest": {"rotation": cyc(L, lambda p: [2 + 2.2 * s(2 * p), -1.5 * s(p), -1.6 * s(p)])},
        **{f"shoulder_{sd}": {"rotation": cyc(L, lambda p, k=k: [0, 0, k * (1.5 + 2.0 * s(2 * p - 0.3))])}
           for sd, k in (("r", -1), ("l", 1))},
        "neck": {"rotation": cyc(L, lambda p: [-2 - 1.5 * s(2 * p + 0.4), 0, 0])},
        "neck2": R((0, [2, 0, 0]), (1.3, [2, 0, 0]), (2.2, [-2, 4, 8]), (3.25, [-2, 4, 8]), (3.4, [4, -2, -3]), (3.9, [2, 0, 0]),
                   (L, [2, 0, 0]), loop=True),
        "head": R((0, [0, 0, 0]), (1.3, [0, 0, 0]), (2.3, [5, 9, 20]), (3.25, [5, 10, 22]), (3.38, [-3, -5, -8]),
                  (3.7, [1, 1, 2]), (L, [0, 0, 0]), loop=True),
        "jaw": {"rotation": cyc(L, lambda p: [2 + 1.5 * s(2 * p), 0, 0])},
        "eye": {"scale": kf([(0, [1, 1, 1]), (1.0, [1, 1, 1]), (1.05, [1, 0.1, 1]), (1.12, [1, 1, 1]), (4.0, [1, 1, 1]),
                             (4.04, [1, 0.15, 1]), (4.1, [1, 1, 1]), (L, [1, 1, 1])])},
        "arm_r": {"rotation": cyc(L, lambda p: [ARM + 1.5 * s(p + 0.6), 0, -4 - 1.2 * s(p)])},
        "arm_l": {"rotation": cyc(L, lambda p: [ARM - 1.5 * s(p + 0.6), 0, 4 - 1.2 * s(p)])},
        "forearm_r": {"rotation": cyc(L, lambda p: [-6 + 2 * s(p - 0.4), 0, 0])},
        "forearm_l": {"rotation": cyc(L, lambda p: [-6 - 2 * s(p - 0.4), 0, 0])},
        "hand_r": {"rotation": cyc(L, lambda p: [3 + 2 * s(p - 1.0), 0, 0])},
        "hand_l": {"rotation": cyc(L, lambda p: [3 - 2 * s(p - 1.0), 0, 0])},
        # tamborileo: cada dedo se encoge y golpea, uno tras otro, dos veces por ciclo
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i, sd=sd: [
            6 + 26 * max(0.0, s(4 * p - i * 0.9 - (0 if sd == "r" else 2.2))) ** 6, 0, 0], 48)}),
        **both("thumb", (0, [0, 0, 0])),
        **gait(L, 0, 0),
        "thigh_r": {"rotation": cyc(L, lambda p: [0, 0, -2.2 * s(p)])}, "thigh_l": {"rotation": cyc(L, lambda p: [0, 0, -2.2 * s(p)])},
        "shin_r": {"rotation": cyc(L, lambda p: [1.5 + 1.5 * s(p), 0, 0])}, "shin_l": {"rotation": cyc(L, lambda p: [1.5 - 1.5 * s(p), 0, 0])},
        "foot_r": {"rotation": cyc(L, lambda p: [-1.5 - 1.5 * s(p), 0, 0])}, "foot_l": {"rotation": cyc(L, lambda p: [-1.5 + 1.5 * s(p), 0, 0])},
    })

    # walk: fase 1, teatral; todo el cuerpo responde al paso
    L = 2.4
    A["walk"] = loop_anim(L, {
        "root": {"position": cyc(L, lambda p: [0.45 * s(p), 0.7 * c(2 * p) - 0.3, 0])},
        "hips": {"rotation": cyc(L, lambda p: [2, 8 * s(p), 3.5 * c(p)])},
        "torso": {"rotation": cyc(L, lambda p: [4 + c(2 * p), -3 * s(p), -1.5 * c(p)])},
        "chest": {"rotation": cyc(L, lambda p: [6 + 1.5 * c(2 * p + 0.5), -10 * s(p), -2 * c(p)])},
        "neck": {"rotation": cyc(L, lambda p: [-3 - 1.5 * c(2 * p + 1.0), 0, 0])},
        "neck2": {"rotation": cyc(L, lambda p: [-1, 2 * s(p), 0])},
        "head": {"rotation": cyc(L, lambda p: [3 + 2 * c(2 * p + 1.4), 4 * s(p), 7 + 3 * c(p)])},
        "jaw": {"rotation": cyc(L, lambda p: [3 + 2 * s(2 * p), 0, 0])},
        **{f"shoulder_{sd}": {"rotation": cyc(L, lambda p: [0, 0, 2.5 * s(p)])} for sd in "rl"},
        "arm_r": {"rotation": cyc(L, lambda p: [ARM + 18 * s(p), 0, -5])},
        "arm_l": {"rotation": cyc(L, lambda p: [ARM - 18 * s(p), 0, 5])},
        "forearm_r": {"rotation": cyc(L, lambda p: [-11 + 9 * s(p - 0.9), 0, 0])},
        "forearm_l": {"rotation": cyc(L, lambda p: [-11 - 9 * s(p - 0.9), 0, 0])},
        "hand_r": {"rotation": cyc(L, lambda p: [4 + 8 * s(p - 1.7), 0, 0])},
        "hand_l": {"rotation": cyc(L, lambda p: [4 - 8 * s(p - 1.7), 0, 0])},
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i, k=(1 if sd == "r" else -1): [
            8 + 9 * k * s(p - 2.3 - i * 0.25), 0, 0])}),
        **gait(L, 22, 46, 4),
    })

    # run: fase 3, encorvado, zancadas largas, brazos por delante; la cabeza no se aparta de ti
    L = 0.9
    A["run"] = loop_anim(L, {
        "root": {"position": cyc(L, lambda p: [0.6 * s(p), -5 + 1.8 * c(2 * p), 0])},
        "hips": {"rotation": cyc(L, lambda p: [10, 10 * s(p), 4 * c(p)])},
        "torso": {"rotation": cyc(L, lambda p: [22 + 3 * c(2 * p), -4 * s(p), 0])},
        "chest": {"rotation": cyc(L, lambda p: [14 + 4 * c(2 * p - 0.6), -13 * s(p), -3 * c(p)])},
        "neck": {"rotation": kf([(0, [-26, 0, 0])])},
        "neck2": {"rotation": cyc(L, lambda p: [-14 - 3 * c(2 * p - 1.2), 0, 0])},
        "head": {"rotation": cyc(L, lambda p: [-12 - 2 * c(2 * p - 1.6), 7 * s(p), 4 * c(p)])},
        "jaw": {"rotation": cyc(L, lambda p: [18 + 7 * s(2 * p), 0, 0])},
        **{f"shoulder_{sd}": {"rotation": cyc(L, lambda p, k=k: [0, 0, k * (4 + 3 * s(p) * k)])}
           for sd, k in (("r", -1), ("l", 1))},
        "arm_r": {"rotation": cyc(L, lambda p: [-92 + 24 * s(p), -10, -12])},
        "arm_l": {"rotation": cyc(L, lambda p: [-92 - 24 * s(p), 10, 12])},
        "forearm_r": {"rotation": cyc(L, lambda p: [-22 + 14 * s(p - 1.0), 0, 0])},
        "forearm_l": {"rotation": cyc(L, lambda p: [-22 - 14 * s(p - 1.0), 0, 0])},
        "hand_r": {"rotation": cyc(L, lambda p: [-12 + 10 * s(p - 1.8), 0, 0])},
        "hand_l": {"rotation": cyc(L, lambda p: [-12 - 10 * s(p - 1.8), 0, 0])},
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i, k=(1 if sd == "r" else -1): [
            -26 + 12 * s(p * 2 - 2.2 - i * 0.4), 0, (i - 1.5) * 9 * k])}),
        **both("thumb", (0, [-20, 0, -15])),
        **gait(L, 40, 75, 18, -22),
    })

    # intro (5 s): encorvado y a oscuras; se enciende el ojo (cabeceo), se endereza de abajo arriba
    # (cintura, pecho, cuello, cabeza: cada tramo despues del anterior), la sonrisa se abre y abre los
    # brazos; los dedos crujen uno a uno
    L = 5.0
    A["intro"] = loop_anim(L, {
        "torso": R((0, [14, 0, 0]), (2.6, [14, 0, 0]), (3.1, [-2, 0, 0]), (3.6, [0, 0, 0]), (L, [0, 0, 0])),
        "chest": R((0, [22, 0, 4]), (2.7, [22, 0, 4]), (3.25, [-5, 0, 0]), (3.8, [1, 0, 0]), (L, [0, 0, 0])),
        "neck": R((0, [30, 0, 0]), (2.8, [30, 0, 0]), (3.35, [-6, 0, 0]), (3.9, [0, 0, 0]), (L, [0, 0, 0])),
        "neck2": R((0, [24, 0, 0]), (2.9, [24, 0, 0]), (3.45, [-8, 0, 6]), (4.0, [0, 0, 4]), (L, [0, 0, 0])),
        "head": R((0, [20, 0, 6]), (1.9, [20, 0, 6]), (2.05, [14, 0, 2]), (2.3, [20, 0, 6]), (3.0, [20, 0, 6]),
                  (3.55, [-4, 6, 30]), (4.3, [0, 4, 20]), (L, [0, 0, 4])),
        "eye": {"scale": kf([(0, [0, 0, 1]), (1.6, [0, 0, 1]), (1.7, [1, 0.2, 1]), (1.9, [1, 1, 1]), (2.1, [1, 0.1, 1]),
                             (2.2, [1, 1, 1]), (L, [1, 1, 1])])},
        "smile": {"scale": smooth([(0, [0.2, 0, 1]), (2.6, [0.2, 0, 1]), (3.0, [0.5, 0.15, 1]), (3.6, [1, 1, 1]),
                                   (4.2, [1.15, 1.2, 1]), (L, [1, 1, 1])])},
        "jaw": R((0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.6, [18, 0, 0]), (4.4, [26, 0, 0]), (L, [4, 0, 0])),
        **both("shoulder", (0, [0, 0, 6]), (2.8, [0, 0, 6]), (3.3, [0, 0, -5]), (3.9, [0, 0, 0]), (L, [0, 0, 0])),
        **both("arm", (0, [-32, 0, -1]), (2.6, [-32, 0, -1]), (3.3, [ARM, 0, -1]), (3.4, [ARM, 0, -1]), (4.2, [ARM - 6, 0, -24]), (4.6, [ARM - 4, 0, -20]),
               (L, [ARM, 0, -5])),
        **both("forearm", (0, [-4, 0, 0]), (3.4, [-4, 0, 0]), (4.25, [-18, 0, 0]), (L, [-6, 0, 0])),
        **both("hand", (0, [0, 0, 0]), (3.5, [0, 0, 0]), (4.2, [-22, 0, 0]), (L, [2, 0, 0])),
        **fingers(lambda i, sd: R(*[(t, [v[0], 0, v[2] * (1 if sd == "r" else -1)]) for t, v in (
            (0, [40, 0, 0]), (3.7 + i * 0.12, [40, 0, 0]), (3.82 + i * 0.12, [-38, 0, (i - 1.5) * 10]),
            (4.6, [-25, 0, (i - 1.5) * 8]), (L, [6, 0, 0]))])),
        **both("thumb", (0, [30, 0, 0]), (3.65, [30, 0, 0]), (3.8, [-20, 0, -20]), (L, [0, 0, 0])),
        **both("thigh", (0, [-4, 0, 0]), (2.6, [-4, 0, 0]), (3.2, [0, 0, 0])),
        **both("shin", (0, [8, 0, 0]), (2.6, [8, 0, 0]), (3.2, [0, 0, 0])),
        **both("foot", (0, [-4, 0, 0]), (2.6, [-4, 0, 0]), (3.2, [0, 0, 0])),
        "root": {"position": smooth([(0, [0, -0.6, 0]), (2.6, [0, -0.6, 0]), (3.2, [0, 0.3, 0]), (3.7, [0, 0, 0])])},
    }, loop="hold_on_last_frame")

    # scream (2 s): coge aire (se agacha y se cierra), estalla hacia atras; la cabeza tiembla
    L = 2.0
    shake = [(0.5 + k * 0.1, [-14, 7 * (-1) ** k, 5 * (-1) ** k]) for k in range(9)]
    A["scream"] = loop_anim(L, {
        "root": {"position": smooth([(0, [0, 0, 0]), (0.22, [0, -1.2, 0]), (0.4, [0, 0.4, 0]), (1.6, [0, 0.2, 0]), (L, [0, 0, 0])])},
        "torso": R((0, [0, 0, 0]), (0.22, [8, 0, 0]), (0.4, [-6, 0, 0]), (1.6, [-5, 0, 0]), (L, [0, 0, 0])),
        "chest": R((0, [0, 0, 0]), (0.22, [14, 0, 0]), (0.4, [-14, 0, 0]), (1.6, [-12, 0, 0]), (L, [0, 0, 0])),
        "neck": R((0, [0, 0, 0]), (0.22, [10, 0, 0]), (0.4, [-12, 0, 0]), (1.6, [-10, 0, 0]), (L, [0, 0, 0])),
        "neck2": R((0, [0, 0, 0]), (0.22, [8, 0, 0]), (0.4, [-10, 0, 0]), (1.6, [-8, 0, 0]), (L, [0, 0, 0])),
        "head": R((0, [0, 0, 0]), (0.22, [14, 0, 0]), (0.4, [-14, 0, 0]), *shake, (1.6, [-12, 0, 0]), (L, [0, 0, 0])),
        "jaw": R((0, [0, 0, 0]), (0.22, [0, 0, 0]), (0.35, [50, 0, 0]), (1.0, [44, 0, 0]), (1.6, [48, 0, 0]), (L, [0, 0, 0])),
        "smile": {"scale": smooth([(0, [1, 1, 1]), (0.22, [0.9, 0.7, 1]), (0.35, [1.25, 1.9, 1]), (1.6, [1.2, 1.8, 1]),
                                   (L, [1, 1, 1])])},
        "eye": {"scale": smooth([(0, [1, 1, 1]), (0.22, [1, 0.5, 1]), (0.35, [1.25, 1.25, 1]), (1.6, [1.2, 1.2, 1]),
                                 (L, [1, 1, 1])])},
        **both("shoulder", (0, [0, 0, 0]), (0.22, [0, 0, 3]), (0.4, [0, 0, -8]), (1.6, [0, 0, -7]), (L, [0, 0, 0])),
        **both("arm", (0, [ARM, 0, -4]), (0.22, [10, 0, -2]), (0.42, [-24, 0, -88]), (1.6, [-20, 0, -84]), (L, [ARM, 0, -4])),
        **both("forearm", (0, [-6, 0, 0]), (0.22, [-30, 0, 0]), (0.48, [-4, 0, 0]), (1.6, [-8, 0, 0]), (L, [-6, 0, 0])),
        **both("hand", (0, [0, 0, 0]), (0.22, [20, 0, 0]), (0.5, [-30, 0, 0]), (1.6, [-25, 0, 0]), (L, [0, 0, 0])),
        **fingers(lambda i, sd: R(*[(t, [v, 0, sp * (i - 1.5) * (1 if sd == "r" else -1)]) for t, v, sp in (
            (0, 6, 0), (0.22, 50, 0), (0.45, -40, 10), (1.6, -38, 10), (L, 6, 0))])),
        **both("thumb", (0, [0, 0, 0]), (0.22, [30, 0, 0]), (0.45, [-30, 0, -25]), (1.6, [-30, 0, -25]), (L, [0, 0, 0])),
        **both("thigh", (0, [0, 0, 0]), (0.22, [-8, 0, 0]), (0.4, [0, 0, -3]), (1.6, [0, 0, -3]), (L, [0, 0, 0])),
        **both("shin", (0, [0, 0, 0]), (0.22, [14, 0, 0]), (0.4, [0, 0, 0])),
        **both("foot", (0, [0, 0, 0]), (0.22, [-6, 0, 0]), (0.4, [0, 0, 3]), (1.6, [0, 0, 3]), (L, [0, 0, 0])),
    }, loop=False)

    # summon (2 s): se agacha un instante y alza los brazos; el antebrazo y la mano llegan despues
    A["summon"] = loop_anim(L, {
        "root": {"position": smooth([(0, [0, 0, 0]), (0.3, [0, -0.8, 0]), (0.6, [0, 0.3, 0]), (1.6, [0, 0.3, 0]), (L, [0, 0, 0])])},
        "chest": R((0, [0, 0, 0]), (0.3, [6, 0, 0]), (0.6, [-12, 0, 0]), (1.6, [-10, 0, 0]), (L, [0, 0, 0])),
        "neck2": R((0, [0, 0, 0]), (0.3, [6, 0, 0]), (0.65, [-8, 0, 0]), (1.6, [-8, 0, 4]), (L, [0, 0, 0])),
        "head": R((0, [0, 0, 0]), (0.3, [10, 0, 0]), (0.7, [-22, 0, 0]), (1.6, [-20, 0, 10]), (L, [0, 0, 0])),
        "jaw": R((0, [2, 0, 0]), (0.6, [20, 0, 0]), (1.6, [26, 0, 0]), (L, [2, 0, 0])),
        "eye": {"scale": smooth([(0, [1, 1, 1]), (0.6, [1.3, 1.3, 1]), (1.6, [1.2, 1.2, 1]), (L, [1, 1, 1])])},
        **both("shoulder", (0, [0, 0, 0]), (0.6, [0, 0, -9]), (1.6, [0, 0, -9]), (L, [0, 0, 0])),
        **both("arm", (0, [ARM, 0, -4]), (0.3, [14, 0, -8]), (0.7, [-160, 0, -24]), (1.6, [-166, 0, -30]), (L, [ARM, 0, -4])),
        **both("forearm", (0, [-6, 0, 0]), (0.3, [-6, 0, 0]), (0.55, [-50, 0, 0]), (0.85, [-8, 0, 0]), (1.6, [-6, 0, 0]),
               (L, [-6, 0, 0])),
        **both("hand", (0, [0, 0, 0]), (0.55, [30, 0, 0]), (0.9, [-20, 0, 0]), (1.6, [-15, 0, 0]), (L, [0, 0, 0])),
        **fingers(lambda i, sd: {"rotation": kf(wave(L, [10, 0, 0], 40, i * 1.5 + (0 if sd == "r" else 0.8), 16))}),
        **both("thumb", (0, [0, 0, 0]), (0.7, [-25, 0, -20]), (1.6, [-25, 0, -20]), (L, [0, 0, 0])),
        **both("thigh", (0, [0, 0, 0]), (0.3, [-6, 0, 0]), (0.6, [0, 0, 0])),
        **both("shin", (0, [0, 0, 0]), (0.3, [10, 0, 0]), (0.6, [0, 0, 0])),
        **both("foot", (0, [0, 0, 0]), (0.3, [-4, 0, 0]), (0.6, [0, 0, 0])),
    }, loop=False)

    # retreat: se tapa la cara, encogido y temblando
    L = 0.8
    A["retreat"] = loop_anim(L, {
        "root": {"position": cyc(L, lambda p: [0.15 * s(5 * p), -2.2 + 0.15 * s(3 * p), 0])},
        "hips": {"rotation": kf([(0, [6, 0, 0])])},
        "torso": {"rotation": cyc(L, lambda p: [-12 + 3 * s(p), 0, 1.2 * s(4 * p)])},
        "chest": {"rotation": cyc(L, lambda p: [22 + 3 * s(p + 1), 1.5 * s(5 * p), 1.5 * s(3 * p)])},
        "neck": {"rotation": kf([(0, [10, 0, 0])])},
        "neck2": {"rotation": kf([(0, [8, 0, 0])])},
        "head": {"rotation": cyc(L, lambda p: [22 + 4 * s(p), 8 * s(3 * p), 6 * s(5 * p + 1)])},
        "eye": {"scale": kf([(0, [1, 0.25, 1])])},
        **{f"shoulder_{sd}": {"rotation": cyc(L, lambda p, k=k: [0, 0, k * (8 + 1.5 * s(4 * p))])} for sd, k in (("r", -1), ("l", 1))},
        "arm_r": {"rotation": cyc(L, lambda p: [-72 + 3 * s(3 * p), -14, 6])},
        "arm_l": {"rotation": cyc(L, lambda p: [-72 + 3 * s(3 * p + 1), 14, -6])},
        "forearm_r": {"rotation": cyc(L, lambda p: [-146 + 5 * s(p), 0, 0])},
        "forearm_l": {"rotation": cyc(L, lambda p: [-146 + 5 * s(p + 1), 0, 0])},
        **both("hand", (0, [-18, 0, 0])),
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i: [38 + 10 * s(4 * p + i), 0, 0])}),
        **both("thigh", (0, [-16, 0, 0])), **both("shin", (0, [30, 0, 0])), **both("foot", (0, [-14, 0, 0])),
    })

    # smear_in (1 s): se aplasta contra la pared; la cabeza se tumba y los brazos se abren pegados
    L = 1.0
    A["smear_in"] = loop_anim(L, {
        "root": {"scale": smooth([(0, [1, 1, 1]), (0.4, [1.3, 1.05, 0.5]), (0.8, [1.8, 0.7, 0.05]), (L, [0.1, 0.1, 0.02])]),
                 "position": smooth([(0, [0, 0, 0]), (L, [0, 0, 6])])},
        "chest": R((0, [0, 0, 0]), (0.5, [-10, 0, 0]), (L, [-14, 0, 0])),
        "neck2": R((0, [0, 0, 0]), (0.6, [-10, 0, 30]), (L, [-10, 0, 40])),
        "head": R((0, [0, 0, 0]), (0.6, [-6, 0, 30]), (L, [-10, 0, 40])),
        "jaw": R((0, [4, 0, 0]), (0.5, [30, 0, 0]), (L, [40, 0, 0])),
        **both("shoulder", (0, [0, 0, 0]), (0.6, [0, 0, -10])),
        **both("arm", (0, [ARM, 0, -4]), (0.6, [0, 0, -70]), (L, [0, 0, -90])),
        **both("forearm", (0, [-6, 0, 0]), (0.6, [0, 0, 0])),
        **fingers(lambda i, sd: R((0, [6, 0, 0]), (0.6, [-30, 0, (i - 1.5) * 12 * (1 if sd == "r" else -1)]))),
        **both("thigh", (0, [0, 0, 0]), (0.6, [0, 0, -14])),
        **both("foot", (0, [0, 0, 0]), (0.6, [0, 0, 14])),
    }, loop="hold_on_last_frame")
    A["smear_out"] = loop_anim(L, {
        "root": {"scale": smooth([(0, [1.8, 0.7, 0.05]), (0.5, [1.3, 1.05, 0.5]), (L, [1, 1, 1])]),
                 "position": smooth([(0, [0, 0, 6]), (L, [0, 0, 0])])},
        "chest": R((0, [-14, 0, 0]), (0.5, [6, 0, 0]), (L, [0, 0, 0])),
        "neck": R((0, [30, 0, 0]), (0.7, [10, 0, 0]), (L, [0, 0, 0])),
        "neck2": R((0, [10, 0, 30]), (0.75, [6, 0, 10]), (L, [0, 0, 0])),
        "head": R((0, [0, 0, 60]), (0.7, [0, 0, 30]), (0.85, [0, 0, -6]), (L, [0, 0, 0])),
        **both("shoulder", (0, [0, 0, -10]), (0.6, [0, 0, 4]), (L, [0, 0, 0])),
        **both("arm", (0, [0, 0, -90]), (0.6, [-20, 0, -40]), (L, [ARM, 0, -4])),
        **both("forearm", (0, [0, 0, 0]), (0.5, [-40, 0, 0]), (L, [-6, 0, 0])),
        **both("hand", (0, [0, 0, 0]), (0.6, [30, 0, 0]), (L, [0, 0, 0])),
        **fingers(lambda i, sd: R((0, [-30, 0, 0]), (0.55 + i * 0.06, [50, 0, 0]), (L, [6, 0, 0]))),
        **both("thigh", (0, [0, 0, -14]), (0.7, [0, 0, 0])),
        **both("foot", (0, [0, 0, 14]), (0.7, [0, 0, 0])),
    }, loop="hold_on_last_frame")

    # grab: la tiene cogida a la altura de la cara; la aprieta a pulsos, la examina ladeando la cabeza
    L = 1.6
    A["grab"] = loop_anim(L, {
        "root": {"position": cyc(L, lambda p: [0, -0.6 + 0.2 * s(p), 0])},
        "hips": {"rotation": kf([(0, [4, 0, 0])])},
        "torso": {"rotation": cyc(L, lambda p: [6 + 1.5 * s(p), 0, 0])},
        "chest": {"rotation": cyc(L, lambda p: [14 + 2 * s(p + 0.5), 2 * s(p), 0])},
        "neck": {"rotation": kf([(0, [16, 0, 0])])},
        "neck2": {"rotation": cyc(L, lambda p: [-4, 4 * s(p + 1), 6 * s(p)])},
        "head": {"rotation": cyc(L, lambda p: [8 + 3 * s(2 * p), 8 * s(p + 1), 16 * s(p)])},
        "jaw": {"rotation": cyc(L, lambda p: [14 + 9 * s(4 * p), 0, 0], 32)},
        "smile": {"scale": cyc(L, lambda p: [1.05 + 0.05 * s(2 * p), 1.1 + 0.1 * s(2 * p), 1])},
        **both("shoulder", (0, [0, 0, -4])),
        "arm_r": {"rotation": cyc(L, lambda p: [-70 + 2 * s(p), -20, -6])},
        "arm_l": {"rotation": cyc(L, lambda p: [-70 + 2 * s(p + 0.6), 20, 6])},
        "forearm_r": {"rotation": cyc(L, lambda p: [-50 + 4 * s(p - 0.8), 0, 0])},
        "forearm_l": {"rotation": cyc(L, lambda p: [-50 + 4 * s(p - 0.2), 0, 0])},
        "hand_r": {"rotation": cyc(L, lambda p: [8 + 3 * s(p - 1.4), 0, 0])},
        "hand_l": {"rotation": cyc(L, lambda p: [8 + 3 * s(p - 0.8), 0, 0])},
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i: [66 + 8 * max(0.0, s(2 * p - i * 0.4)) ** 2, 0, 0])}),
        **both("thumb", (0, [40, 0, 10])),
        **both("thigh", (0, [-4, 0, -5])), **both("shin", (0, [8, 0, 0])), **both("foot", (0, [-4, 0, 5])),
    })

    # remate: la levanta del cuello hasta su cara, de puntillas (0-1,2); abre la sonrisa enorme
    # (1,2-1,9), echa la cabeza atras para coger impulso (1,9-2,1) y la parte (2,25)
    L = 3.5
    A["execute_smiley"] = loop_anim(L, {
        "root": {"position": smooth([(0, [0, -0.6, 0]), (1.2, [0, 1.0, 0]), (2.1, [0, 1.0, 0]), (2.25, [0, -0.8, 0]),
                                     (L, [0, -0.5, 0])])},
        "hips": R((0, [4, 0, 0]), (1.2, [0, 0, 0]), (2.1, [0, 0, 0]), (2.25, [4, 0, 0])),
        "torso": R((0, [7, 0, 0]), (1.2, [0, 0, 0]), (2.1, [-4, 0, 0]), (2.25, [10, 0, 0]), (L, [8, 0, 0])),
        "chest": R((0, [15, 0, 0]), (1.2, [6, 0, 0]), (2.1, [6, 0, 0]), (2.25, [24, 0, 0]), (L, [18, 0, 0])),
        "neck": R((0, [16, 0, 0]), (1.2, [0, 0, 0]), (2.0, [-6, 0, 0]), (2.25, [22, 0, 0]), (L, [16, 0, 0])),
        "neck2": R((0, [-4, 0, 0]), (1.2, [-6, 0, 0]), (1.9, [-10, 0, 6]), (2.25, [14, 0, 0]), (L, [10, 0, 0])),
        "head": R((0, [8, 0, 0]), (1.2, [0, 0, 20]), (1.9, [-6, 0, 30]), (2.08, [-12, 0, 26]), (2.25, [16, 0, 4]),
                  (L, [12, 0, 10])),
        "jaw": R((0, [10, 0, 0]), (1.2, [6, 0, 0]), (1.9, [50, 0, 0]), (2.15, [58, 0, 0]), (2.27, [6, 0, 0]), (L, [12, 0, 0])),
        "smile": {"scale": smooth([(0, [1, 1, 1]), (1.2, [1, 1, 1]), (1.9, [1.4, 2.4, 1]), (2.2, [1.45, 2.5, 1]),
                                   (2.3, [1.05, 1, 1]), (L, [1.1, 1.1, 1])])},
        "eye": {"scale": smooth([(0, [1, 1, 1]), (1.9, [1.2, 1.2, 1]), (2.25, [1.35, 0.6, 1]), (2.5, [1.1, 1.1, 1]),
                                 (L, [1, 1, 1])])},
        **both("shoulder", (0, [0, 0, -4]), (1.2, [0, 0, -7]), (2.1, [0, 0, -8]), (2.25, [0, 0, 4]), (L, [0, 0, 2])),
        **both("arm", (0, [-70, -20, -6]), (1.2, [-110, -18, -4]), (2.1, [-104, -18, -4]), (2.25, [-60, -40, -30]),
               (L, [-40, -30, -30])),
        **both("forearm", (0, [-50, 0, 0]), (1.2, [-20, 0, 0]), (2.1, [-22, 0, 0]), (2.25, [-10, 0, 0]), (L, [-10, 0, 0])),
        **both("hand", (0, [8, 0, 0]), (1.2, [20, 0, 0]), (2.1, [24, 0, 0]), (2.25, [-30, 0, 0]), (L, [0, 0, 0])),
        **fingers(lambda i, sd: R((0, [65, 0, 0]), (2.1, [72, 0, 0]), (2.25 + i * 0.02, [-24, 0, 0]), (L, [10, 0, 0]))),
        **both("thumb", (0, [40, 0, 10]), (2.1, [45, 0, 10]), (2.25, [-20, 0, -20]), (L, [0, 0, 0])),
        **both("thigh", (0, [-4, 0, -5]), (1.2, [0, 0, -4]), (2.1, [0, 0, -4]), (2.25, [-10, 0, -6]), (L, [-6, 0, -5])),
        **both("shin", (0, [8, 0, 0]), (1.2, [0, 0, 0]), (2.1, [0, 0, 0]), (2.25, [16, 0, 0]), (L, [10, 0, 0])),
        **both("foot", (0, [-4, 0, 5]), (1.2, [16, 0, 4]), (2.1, [16, 0, 4]), (2.25, [-6, 0, 6]), (L, [-4, 0, 5])),
        **both("toe", (0, [0, 0, 0]), (1.2, [-16, 0, 0]), (2.1, [-16, 0, 0]), (2.25, [0, 0, 0])),
    }, loop="hold_on_last_frame")

    # sealed (3 s): el ojo parpadea y se apaga; se le doblan las rodillas, se queda colgando de si mismo
    L = 3.0
    A["sealed"] = loop_anim(L, {
        "eye": {"scale": kf([(0, [1, 1, 1]), (0.4, [1, 0.3, 1]), (0.5, [1, 1, 1]), (0.8, [1, 0.2, 1]), (0.85, [1, 0.9, 1]),
                             (1.0, [1, 0.6, 1]), (1.4, [1, 0, 1]), (L, [0, 0, 1])])},
        "root": {"position": smooth([(0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [0, -4, 0]), (2.6, [0, -3.6, 0]), (L, [0, -4.2, 0])])},
        "torso": R((0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [12, 0, 2]), (L, [13, 0, 3])),
        "chest": R((0, [4, 0, 0]), (1.4, [4, 0, 0]), (2.45, [24, 0, 0]), (2.65, [20, 0, 0]), (L, [26, 0, 0])),
        "neck": R((0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.5, [40, 0, 0]), (L, [42, 0, 0])),
        "neck2": R((0, [0, 0, 0]), (1.45, [0, 0, 0]), (2.55, [20, 0, 10]), (L, [22, 0, 12])),
        "head": R((0, [0, 0, 0]), (1.5, [0, 0, 0]), (2.6, [16, 0, 24]), (2.75, [12, 0, 20]), (L, [18, 0, 28])),
        "jaw": R((0, [4, 0, 0]), (1.4, [4, 0, 0]), (2.5, [24, 0, 0]), (L, [26, 0, 0])),
        **both("shoulder", (0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [0, 0, 8]), (L, [0, 0, 9])),
        **both("arm", (0, [ARM, 0, -4]), (1.4, [ARM, 0, -4]), (2.5, [-30, 0, -1]), (2.7, [-38, 0, -1]), (L, [-34, 0, 0])),
        **both("forearm", (0, [-6, 0, 0]), (1.5, [-6, 0, 0]), (2.6, [-1, 0, 0]), (L, [-2, 0, 0])),
        **both("hand", (0, [0, 0, 0]), (1.5, [0, 0, 0]), (2.7, [12, 0, 0]), (L, [10, 0, 0])),
        **fingers(lambda i, sd: R((0, [6, 0, 0]), (1.5, [6, 0, 0]), (2.6 + i * 0.05, [32, 0, 0]), (L, [30, 0, 0]))),
        **both("thigh", (0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [-20, 0, -2]), (L, [-21, 0, -2])),
        **both("shin", (0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [38, 0, 0]), (L, [40, 0, 0])),
        **both("foot", (0, [0, 0, 0]), (1.4, [0, 0, 0]), (2.4, [-18, 0, 2]), (L, [-19, 0, 2])),
    }, loop="hold_on_last_frame")

    # drag: arrastra al ultimo del tobillo con la derecha, inclinado contra el peso; a mitad de paso
    # gira la cabeza del todo para mirarte
    L = 2.4
    A["drag"] = loop_anim(L, {
        "root": {"position": cyc(L, lambda p: [0.3 * s(p), 0.5 * c(2 * p) - 1.0, 0])},
        "hips": {"rotation": cyc(L, lambda p: [6, 5 * s(p), 3 * c(p)])},
        "torso": {"rotation": cyc(L, lambda p: [6, -6, 0])},
        "chest": {"rotation": cyc(L, lambda p: [12 + 2 * c(2 * p), -12 - 5 * s(p), -3])},
        "neck2": R((0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.2, [0, 40, 0]), (1.6, [0, 40, 0]), (1.85, [0, 0, 0]), (L, [0, 0, 0]),
                   loop=True),
        "head": R((0, [4, 0, 6]), (1.0, [4, 0, 6]), (1.2, [4, 110, 10]), (1.6, [4, 110, 14]), (1.85, [4, 0, 6]), (L, [4, 0, 6]),
                  loop=True),
        "shoulder_r": {"rotation": kf([(0, [0, 10, 6])])},            # tirado hacia atras por el peso
        "shoulder_l": {"rotation": cyc(L, lambda p: [0, 0, 2 * s(p)])},
        "arm_r": {"rotation": cyc(L, lambda p: [22 + 2 * s(2 * p), 0, -8])},
        "forearm_r": {"rotation": kf([(0, [10, 0, 0])])},
        "hand_r": {"rotation": kf([(0, [20, 0, 0])])},
        **fingers(lambda i, sd: {"rotation": kf([(0, [72, 0, 0])])}, "r"),
        **both("thumb", (0, [40, 0, 10])),
        "arm_l": {"rotation": cyc(L, lambda p: [-18 - 14 * s(p), 0, 5])},
        "forearm_l": {"rotation": cyc(L, lambda p: [-10 - 8 * s(p - 0.9), 0, 0])},
        "hand_l": {"rotation": cyc(L, lambda p: [4 - 6 * s(p - 1.7), 0, 0])},
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i: [8 - 8 * s(p - 2.3 - i * 0.25), 0, 0])}, "l"),
        **gait(L, 18, 38, 6, -4),
    })
    hang_hair(A)
    enrich(A, mm.bones_of("smiley"))
    out = {"format_version": "1.8.0", "animations": A}
    (ROOT / "animations/entity/smiley.animation.json").write_text(json.dumps(out, separators=(",", ":")) + "\n", encoding="utf-8")
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
