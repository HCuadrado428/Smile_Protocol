#!/usr/bin/env python3
"""
Modelos GeckoLib de los mobs (geo.json, textura, capa emisiva y animaciones).

Berserker (README 20.2): variacion de Smiley. Silueta negra, unos 2,5 bloques encorvado, brazos
largos que casi arrastran con manos grandes de dedos afilados, piernas largas, pelo largo, la
sonrisa roja (lo unico que brilla, en berserker_glowmask.png) y el ojo derecho cosido y apagado.

El modelo se construye de pie y las animaciones lo encorvan. Convenio de ejes: el frente es -Z;
rotacion X positiva inclina hacia delante (torso), negativa levanta los brazos hacia delante.

    python3 tools/mob_models.py [preview.png]
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import geo_models as gm  # noqa: E402
from geo_models import Geo, MAT, sstep, noise3  # noqa: E402

ROOT = gm.ROOT


# Materiales de los smilers -----------------------------------------------------------------------------

def skin(base=(16, 14, 15)):
    """Piel negra: casi plana, con un brillo grasiento leve para que se lea el volumen."""
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + base
        c *= (0.75 + 0.5 * f.n(0.6, 3, 201))[..., None]
        sheen = sstep(0.62, 0.9, f.n(0.35, 2, 202))
        c = c + sheen[..., None] * 10
        c += (f.white()[..., None] - 0.5) * 3
        return c, np.ones((f.h, f.w))
    return m


def hair():
    def m(f):
        c = np.zeros((f.h, f.w, 3)) + (12, 11, 12)
        ax = f.X + f.Z if f.side else f.X
        strands = 0.6 + 0.6 * (0.5 + 0.5 * np.sin(ax * 9.0 + noise3(f.X, f.Y * 0.2, f.Z, 0.8, 211) * 6))
        c *= strands[..., None]
        c = c + sstep(0.75, 1, f.n(0.9, 2, 212))[..., None] * 14
        a = np.ones((f.h, f.w))
        if f.side:
            # puntas desiguales abajo
            low = f.Y - f.Y.min()
            cut = 0.6 + 2.4 * noise3(ax * 2.2, 0, 0, 1.0, 213)
            a = np.where(low < cut, 0, a)
        return c, a
    return m


def claw():
    """Garras: negras en la base, grises y afiladas en la punta."""
    def m(f):
        t = sstep(f.Y.max() if f.Y.size else 1, (f.Y.min() if f.Y.size else 0), f.Y)
        c = np.zeros((f.h, f.w, 3)) + (20, 18, 19)
        c = c * (1 - t[..., None]) + np.array([96, 90, 86]) * t[..., None] * 0.9
        return c, np.ones((f.h, f.w))
    return m


def face():
    """Cara: piel negra, sonrisa roja enorme (brilla) y el ojo derecho cosido y apagado."""
    sk = skin()

    def smile_mask(f):
        if f.face != "north":
            return np.zeros((f.h, f.w))
        u, v = f.U, f.V
        w, h = f.w / gm.SCALE, f.h / gm.SCALE
        # curva de oreja a oreja: centro bajo, extremos que suben
        x = (u - w / 2) / (w / 2)
        mid = h * 0.66
        top = mid - (x ** 2) * h * 0.34
        thick = 0.7 + 1.1 * (1 - x ** 2)
        m = (v > top - thick * 0.35) & (v < top + thick) & (np.abs(x) < 0.97)
        return m.astype(float)

    def mat(f):
        c, a = sk(f)
        if f.face != "north":
            return c, a
        sm = smile_mask(f)
        c = np.where(sm[..., None] > 0, np.array([206, 18, 22]), c)
        # dientes: lineas oscuras verticales dentro de la sonrisa
        teeth = (sm > 0) & (np.floor(f.U * gm.SCALE) % 3 == 0)
        c = np.where(teeth[..., None], np.array([70, 6, 8]), c)
        # ojo derecho (lado +X, a la izquierda de quien lo mira): cosido, rojo apagado, no brilla
        w, h = f.w / gm.SCALE, f.h / gm.SCALE
        ex, ey = w * 0.27, h * 0.36
        slit = (np.abs(f.V - ey) < 0.28) & (np.abs(f.U - ex) < 1.4)
        c = np.where(slit[..., None], np.array([58, 8, 10]), c)
        for k in (-1.0, 0.0, 1.0):
            st = (np.abs(f.U - (ex + k)) < 0.22) & (np.abs(f.V - ey) < 0.75)
            c = np.where(st[..., None], np.array([92, 84, 78]), c)
        # cuenca vacia en el otro lado: solo una sombra
        hollow = np.hypot((f.U - w * 0.73) / 1.2, (f.V - ey) / 0.8) < 1
        c = np.where(hollow[..., None], c * 0.45, c)
        return c, a

    mat.glowmask = smile_mask
    return mat


MAT.update({"skin": skin(), "skin_hi": skin((22, 20, 21)), "hair": hair(), "claw": claw(), "face": face()})


# Berserker ----------------------------------------------------------------------------------------------

def berserker():
    g = Geo("berserker", 77, folder="entity", glow=("face",), ao=False)
    g.bone("root")
    g.bone("hips", "root", (0, 22, 0))
    g.cube("hips", (-4, 19.5, -2.5), (8, 5, 5), "skin")
    # torso: vientre hundido, pecho ancho, costillas y espinazo marcados
    g.bone("torso", "hips", (0, 24, 0))
    g.cube("torso", (-3.5, 24, -2), (7, 7, 4.5), "skin")
    g.cube("torso", (-6, 31, -3.2), (12, 9, 6.5), "skin")
    for i in range(3):
        g.cube("torso", (-5.6, 32 + i * 2.4, -3.6), (11.2, 0.9, 0.6), "skin_hi")     # costillas
    for i in range(5):
        g.cube("torso", (-0.8, 25 + i * 3, 2.3 + (0.6 if i > 1 else 0)), (1.6, 1.6, 1.2), "skin_hi")  # vertebras
    g.cube("torso", (-7, 37.5, -2.5), (14, 3, 5), "skin")                            # hombros huesudos
    # cuello y cabeza (algo pequena para el cuerpo, colgando hacia delante)
    g.bone("neck", "torso", (0, 40, -1))
    g.cube("neck", (-1.5, 39.5, -2.5), (3, 4, 3), "skin")
    g.bone("head", "neck", (0, 43, -1.5))
    g.cube("head", (-4, 43, -5.5), (8, 9, 8), "face")
    g.cube("head", (-3.6, 41.6, -5.2), (7.2, 2, 6), "skin")                          # mandibula
    # pelo largo: casquete y mechones que caen por detras y por los lados de la cara
    g.bone("hair", "head", (0, 51, -1.5))
    g.cube("hair", (-4.4, 50.5, -5.9), (8.8, 2.2, 8.8), "hair")
    g.cube("hair", (-4.5, 33, 2.2), (9, 18, 1.2), "hair")
    for x, ln in ((-4.7, 15), (3.9, 13)):
        g.cube("hair", (x, 51.5 - ln, -5.6), (0.8, ln, 7.6), "hair")
    for x, ln in ((-3.8, 6), (-2.2, 4), (1.6, 5)):
        g.cube("hair", (x, 51.5 - ln, -6.1), (1.2, ln, 0.6), "hair")                  # flequillo sobre la cara

    # brazos largos con manos enormes y dedos afilados
    for side, sx in (("r", 1), ("l", -1)):
        sh = (sx * 7.5, 38.5, -0.5)
        g.bone(f"arm_{side}", "torso", sh)
        g.cube(f"arm_{side}", (sx * 7.5 - 1.5, 24.5, -2), (3, 15, 3), "skin")
        g.bone(f"forearm_{side}", f"arm_{side}", (sx * 7.5, 25, -0.5))
        g.cube(f"forearm_{side}", (sx * 7.5 - 1.2, 10, -1.7), (2.4, 15.5, 2.4), "skin")
        g.cube(f"forearm_{side}", (sx * 7.5 - 1.4, 22.5, -1.9), (2.8, 2.5, 2.8), "skin_hi")  # codo
        g.bone(f"hand_{side}", f"forearm_{side}", (sx * 7.5, 10.5, -0.5))
        g.cube(f"hand_{side}", (sx * 7.5 - 2, 6.5, -3), (4, 4.5, 5), "skin")
        for i, z in enumerate((-2.7, -1.4, -0.1, 1.2)):
            ln = (7.5, 8.5, 8.0, 6.5)[i]
            g.bone(f"finger{i}_{side}", f"hand_{side}", (sx * 7.5, 6.8, z + 0.4))
            g.cube(f"finger{i}_{side}", (sx * 7.5 - 0.4, 6.8 - ln * 0.55, z), (0.8, ln * 0.55, 0.8), "skin")
            g.cube(f"finger{i}_{side}", (sx * 7.5 - 0.3, 6.8 - ln, z + 0.05), (0.6, ln * 0.45 + 0.1, 0.7), "claw")
        g.cube(f"hand_{side}", (sx * 7.5 - sx * 2.6 - 0.4, 6, -3.2), (0.8, 4, 0.8), "claw")  # pulgar

    # piernas largas y delgadas, pies con garras
    for side, sx in (("r", 1), ("l", -1)):
        g.bone(f"thigh_{side}", "hips", (sx * 2.6, 21, 0))
        g.cube(f"thigh_{side}", (sx * 2.6 - 1.6, 11, -1.6), (3.2, 10.5, 3.2), "skin")
        g.bone(f"shin_{side}", f"thigh_{side}", (sx * 2.6, 11.5, 0))
        g.cube(f"shin_{side}", (sx * 2.6 - 1.3, 1.5, -1.2), (2.6, 10.5, 2.6), "skin")
        g.cube(f"shin_{side}", (sx * 2.6 - 1.5, 10.2, -1.6), (3, 2, 3), "skin_hi")     # rodilla
        g.bone(f"foot_{side}", f"shin_{side}", (sx * 2.6, 1.5, 0))
        g.cube(f"foot_{side}", (sx * 2.6 - 1.6, 0, -4.5), (3.2, 1.6, 6), "skin")
        for i in range(3):
            g.cube(f"foot_{side}", (sx * 2.6 - 1.4 + i * 1.1, 0, -6.3), (0.7, 0.9, 2), "claw")
    return g.save()


# Animaciones ---------------------------------------------------------------------------------------------

def kf(times_values):
    return {f"{t:.4g}": [round(v, 2) for v in vals] for t, vals in times_values}


def loop_anim(length, bones, loop=True):
    return {"loop": loop, "animation_length": length, "bones": bones}


def wave(length, base, amp, phase=0.0, steps=8, axis=0):
    """Oscilacion suave en un eje: lista de (t, [x, y, z])."""
    out = []
    for i in range(steps + 1):
        t = length * i / steps
        v = list(base)
        v[axis] += amp * math.sin(2 * math.pi * (i / steps) + phase)
        out.append((t, v))
    return out


def multi(length, base, amps, phases, steps=8):
    out = []
    for i in range(steps + 1):
        t = length * i / steps
        v = [base[k] + amps[k] * math.sin(2 * math.pi * (i / steps) + phases[k]) for k in range(3)]
        out.append((t, v))
    return out


HUNCH = 42       # inclinacion del torso
NECK = -22       # el cuello compensa en parte: la cabeza cuelga hacia delante
ARM_HANG = -HUNCH + 6


def pose_bones(extra):
    """Postura base encorvada; `extra` sobrescribe o anade canales."""
    b = {
        "torso": {"rotation": kf([(0, [HUNCH, 0, 0])])},
        "neck": {"rotation": kf([(0, [NECK, 0, 0])])},
        "head": {"rotation": kf([(0, [-12, 0, 0])])},
        "arm_r": {"rotation": kf([(0, [ARM_HANG, 0, -6])])},
        "arm_l": {"rotation": kf([(0, [ARM_HANG, 0, 6])])},
        "forearm_r": {"rotation": kf([(0, [-12, 0, 0])])},
        "forearm_l": {"rotation": kf([(0, [-12, 0, 0])])},
        "thigh_r": {"rotation": kf([(0, [-26, 0, 4])])},
        "thigh_l": {"rotation": kf([(0, [-26, 0, -4])])},
        "shin_r": {"rotation": kf([(0, [40, 0, 0])])},
        "shin_l": {"rotation": kf([(0, [40, 0, 0])])},
        "foot_r": {"rotation": kf([(0, [-14, 0, 0])])},
        "foot_l": {"rotation": kf([(0, [-14, 0, 0])])},
        "hips": {"position": kf([(0, [0, -2.5, 0])])},
    }
    for k, v in extra.items():
        b.setdefault(k, {}).update(v)
    return b


def animations():
    A = {}
    # IDLE: respiracion pesada, tics de la cabeza y dedos que se mueven
    L = 3.2
    A["idle"] = loop_anim(L, pose_bones({
        "torso": {"rotation": kf(wave(L, [HUNCH, 0, 0], 2.5))},
        "head": {"rotation": kf([(0, [-12, 0, 0]), (1.0, [-12, 0, 0]), (1.08, [-6, 18, 8]), (1.5, [-6, 18, 8]),
                                 (1.6, [-12, 0, 0]), (2.4, [-14, -6, -4]), (L, [-12, 0, 0])])},
        "arm_r": {"rotation": kf(wave(L, [ARM_HANG, 0, -6], 3, 0.5))},
        "arm_l": {"rotation": kf(wave(L, [ARM_HANG, 0, 6], 3, 1.7))},
        "finger0_r": {"rotation": kf(wave(L, [10, 0, 0], 12, 0.3))},
        "finger2_l": {"rotation": kf(wave(L, [10, 0, 0], 14, 2.0))},
        "hips": {"position": kf(wave(L, [0, -2.5, 0], 0.4, 0, 8, 1))},
    }))
    # WALK: pasos largos y pesados, brazos arrastrando, la cabeza oscila
    L = 1.6
    A["walk"] = loop_anim(L, pose_bones({
        "thigh_r": {"rotation": kf(wave(L, [-26, 0, 4], 26))},
        "thigh_l": {"rotation": kf(wave(L, [-26, 0, -4], 26, math.pi))},
        "shin_r": {"rotation": kf(wave(L, [40, 0, 0], 22, -1.2))},
        "shin_l": {"rotation": kf(wave(L, [40, 0, 0], 22, math.pi - 1.2))},
        "arm_r": {"rotation": kf(wave(L, [ARM_HANG, 0, -6], 14, math.pi))},
        "arm_l": {"rotation": kf(wave(L, [ARM_HANG, 0, 6], 14))},
        "torso": {"rotation": kf(multi(L, [HUNCH, 0, 0], [3, 6, 4], [0, 0, math.pi / 2], 8))},
        "head": {"rotation": kf(multi(L, [-12, 0, 0], [4, 8, 6], [0.4, math.pi, 1.0], 8))},
        "hips": {"position": kf(wave(L, [0, -2.5, 0], 0.8, 0, 8, 1))},
    }))
    # RUN: carrera desgarbada a cuatro patas casi, mucho mas inclinado
    L = 0.7
    A["run"] = loop_anim(L, pose_bones({
        "torso": {"rotation": kf(wave(L, [HUNCH + 22, 0, 0], 5))},
        "neck": {"rotation": kf([(0, [NECK - 18, 0, 0])])},
        "thigh_r": {"rotation": kf(wave(L, [-34, 0, 4], 42))},
        "thigh_l": {"rotation": kf(wave(L, [-34, 0, -4], 42, math.pi))},
        "shin_r": {"rotation": kf(wave(L, [46, 0, 0], 32, -1.3))},
        "shin_l": {"rotation": kf(wave(L, [46, 0, 0], 32, math.pi - 1.3))},
        "arm_r": {"rotation": kf(wave(L, [ARM_HANG - 30, 0, -10], 50, math.pi))},
        "arm_l": {"rotation": kf(wave(L, [ARM_HANG - 30, 0, 10], 50))},
        "forearm_r": {"rotation": kf(wave(L, [-25, 0, 0], 20, math.pi))},
        "forearm_l": {"rotation": kf(wave(L, [-25, 0, 0], 20))},
        "hips": {"position": kf(wave(L, [0, -3.5, 0], 1.2, 0, 8, 1))},
    }))
    # STRIKE: 0,6 s de aviso (se yergue, abre los brazos) y barrido contra el suelo
    L = 1.0
    A["strike"] = loop_anim(L, pose_bones({
        "torso": {"rotation": kf([(0, [HUNCH, 0, 0]), (0.5, [5, 0, 0]), (0.6, [8, 0, 0]), (0.68, [HUNCH + 35, 0, 0]),
                                  (1.0, [HUNCH + 30, 0, 0])])},
        "neck": {"rotation": kf([(0, [NECK, 0, 0]), (0.5, [-5, 0, 0]), (0.68, [NECK - 10, 0, 0])])},
        "head": {"rotation": kf([(0, [-12, 0, 0]), (0.5, [-35, 0, 0]), (0.6, [-38, 0, 0]), (0.68, [-10, 0, 0])])},
        "arm_r": {"rotation": kf([(0, [ARM_HANG, 0, -6]), (0.5, [-165, 0, -35]), (0.6, [-170, 0, -38]),
                                  (0.68, [-60, 0, -5]), (1.0, [-70, 0, -8])])},
        "arm_l": {"rotation": kf([(0, [ARM_HANG, 0, 6]), (0.5, [-165, 0, 35]), (0.6, [-170, 0, 38]),
                                  (0.68, [-60, 0, 5]), (1.0, [-70, 0, 8])])},
        "forearm_r": {"rotation": kf([(0, [-12, 0, 0]), (0.5, [-40, 0, 0]), (0.68, [-5, 0, 0])])},
        "forearm_l": {"rotation": kf([(0, [-12, 0, 0]), (0.5, [-40, 0, 0]), (0.68, [-5, 0, 0])])},
        "hips": {"position": kf([(0, [0, -2.5, 0]), (0.5, [0, 0.5, 0]), (0.68, [0, -5, 0]), (1.0, [0, -4.5, 0])])},
        "thigh_r": {"rotation": kf([(0, [-26, 0, 4]), (0.68, [-50, 0, 10])])},
        "thigh_l": {"rotation": kf([(0, [-26, 0, -4]), (0.68, [-20, 0, -10])])},
        "shin_r": {"rotation": kf([(0, [40, 0, 0]), (0.68, [70, 0, 0])])},
        "shin_l": {"rotation": kf([(0, [40, 0, 0]), (0.68, [50, 0, 0])])},
    }), loop=False)
    A["strike"]["loop"] = "hold_on_last_frame"
    # RECOVER: con las manos en el suelo, jadeando, se va levantando
    L = 1.5
    A["recover"] = loop_anim(L, pose_bones({
        "torso": {"rotation": kf([(0, [HUNCH + 30, 0, 0]), (L, [HUNCH + 5, 0, 0])])},
        "arm_r": {"rotation": kf([(0, [-70, 0, -8]), (L, [ARM_HANG, 0, -6])])},
        "arm_l": {"rotation": kf([(0, [-70, 0, 8]), (L, [ARM_HANG, 0, 6])])},
        "hips": {"position": kf([(0, [0, -4.5, 0]), (L, [0, -2.5, 0])])},
        "head": {"rotation": kf(wave(L, [-15, 0, 0], 4, 0, 6))},
    }), loop=True)
    # SEARCH: quieto, la cabeza ladeada escucha a un lado y a otro, olfatea
    L = 4.0
    A["search"] = loop_anim(L, pose_bones({
        "head": {"rotation": kf([(0, [-14, 0, 0]), (0.6, [-8, 35, 22]), (1.6, [-8, 38, 25]), (2.0, [-14, 0, 0]),
                                 (2.6, [-8, -35, -22]), (3.6, [-8, -38, -25]), (L, [-14, 0, 0])])},
        "neck": {"rotation": kf(wave(L, [NECK, 0, 0], 4, 0, 8))},
        "torso": {"rotation": kf(wave(L, [HUNCH - 6, 0, 0], 2, 0, 8))},
        "arm_r": {"rotation": kf(wave(L, [ARM_HANG - 10, 0, -14], 4, 0.3))},
        "arm_l": {"rotation": kf(wave(L, [ARM_HANG - 10, 0, 14], 4, 2.2))},
        "finger1_r": {"rotation": kf(wave(L, [15, 0, 0], 18, 1.0, 12))},
        "finger3_l": {"rotation": kf(wave(L, [15, 0, 0], 18, 2.0, 12))},
    }))
    # GRAB (mob de pruebas): sujeta a la victima por los hombros pegada al pecho (las manos quedan a
    # ~1,17 bloques delante y 1,3 de alto, donde el QTE coloca al jugador) y la sacude
    L = 0.8
    A["grab"] = loop_anim(L, pose_bones({
        "torso": {"rotation": kf(wave(L, [25, 0, 0], 4))},
        "neck": {"rotation": kf([(0, [-10, 0, 0])])},
        "arm_r": {"rotation": kf(multi(L, [-2, 0, 0], [5, 3, 0], [0, 1.0, 0]))},
        "arm_l": {"rotation": kf(multi(L, [-2, 0, 0], [5, -3, 0], [0, 1.0, 0]))},
        "forearm_r": {"rotation": kf(wave(L, [-108, 0, 0], 4, 0.5))},
        "forearm_l": {"rotation": kf(wave(L, [-108, 0, 0], 4, 0.5))},
        "head": {"rotation": kf(multi(L, [-8, 0, 0], [6, 12, 10], [0, 1.3, 2.1], 8))},
        **{f"finger{i}_{s}": {"rotation": kf([(0, [70 - 6 * i, 0, 0])])} for i in range(4) for s in "rl"},
    }))
    # STUNNED: se tambalea hacia atras con la cabeza dando sacudidas
    L = 1.2
    A["stunned"] = loop_anim(L, pose_bones({
        "torso": {"rotation": kf(multi(L, [HUNCH - 25, 0, 0], [6, 12, 10], [0, 0.7, 1.9], 8))},
        "head": {"rotation": kf(multi(L, [-30, 0, 0], [10, 25, 20], [0.5, 2.0, 1.1], 8))},
        "arm_r": {"rotation": kf(wave(L, [ARM_HANG + 20, 0, -25], 15))},
        "arm_l": {"rotation": kf(wave(L, [ARM_HANG + 20, 0, 25], 15, math.pi))},
    }))
    out = {"format_version": "1.8.0", "animations": A}
    p = ROOT / "animations/entity/berserker.animation.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
    return A


# Vista previa con huesos y animacion ---------------------------------------------------------------

def _rot(rx, ry, rz):
    """Igual que GeckoLib: X e Y invertidas, orden Z * Y * X."""
    rx, ry, rz = map(math.radians, (-rx, -ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X


def sample(channel, t):
    if not channel:
        return None
    keys = sorted((float(k), v) for k, v in channel.items())
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / (t1 - t0 + 1e-9)
            return [a + (b - a) * k for a, b in zip(v0, v1)]
    return keys[-1][1]


def render_pose(anim, t, size=420, yaw=200, pitch=-12):
    geo = json.loads((ROOT / "geo/entity/berserker.geo.json").read_text())["minecraft:geometry"][0]
    tex = np.asarray(Image.open(ROOT / "textures/entity/berserker.png").convert("RGBA"))
    bones = {b["name"]: b for b in geo["bones"]}
    cache = {}

    def world(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        a = anim["bones"].get(name, {}) if anim else {}
        r = sample(a.get("rotation"), t) or [0, 0, 0]
        p = sample(a.get("position"), t) or [0, 0, 0]
        R = _rot(*r)
        piv = np.array(b["pivot"], dtype=float)
        off = np.array([-p[0], p[1], p[2]], dtype=float)
        if "parent" in b:
            PR, PT = world(b["parent"])
        else:
            PR, PT = np.eye(3), np.zeros(3)
        # local: x -> R (x - piv) + piv + off ; mundo: PR local + PT
        M = PR @ R
        T = PR @ (piv - R @ piv + off) + PT
        cache[name] = (M, T)
        return cache[name]

    import geo_preview as gp
    cam = gp.rot_matrix(pitch, 0, 0) @ gp.rot_matrix(0, yaw, 0)
    ldir = np.array([0.3, 0.8, -0.5])
    ldir /= np.linalg.norm(ldir)
    quads = []
    for name, b in bones.items():
        M, T = world(name)
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            for fname, uvd in c["uv"].items():
                u0, v0 = uvd["uv"]
                rw, rh = uvd["uv_size"]
                n = M @ np.array(gp.NORMALS[fname], dtype=float)
                if (cam @ n)[2] > 0.05:
                    continue
                shade = 0.55 + 0.6 * max(0, n @ ldir)
                nu, nv = max(1, int(round(rw * gm.SCALE))), max(1, int(round(rh * gm.SCALE)))
                for j in range(nv):
                    for i in range(nu):
                        px = tex[int(v0 * gm.SCALE) + j, int(u0 * gm.SCALE) + i]
                        if px[3] < 128:
                            continue
                        pts = []
                        for du, dv in ((0, 0), (1, 0), (1, 1), (0, 1)):
                            q = np.array(gp.face_points(o, s, fname, (i + du) / gm.SCALE, (j + dv) / gm.SCALE))
                            pts.append(cam @ (M @ q + T))
                        z = np.mean([q[2] for q in pts])
                        lum = 1.0 if px[0] > 150 and px[1] < 60 else shade      # la sonrisa no se sombrea
                        col = tuple(int(min(255, k * lum * 2.2 + 18)) for k in px[:3])
                        quads.append((z, [(-q[0], -q[1]) for q in pts], col))
    quads.sort(key=lambda q: -q[0])
    img = Image.new("RGB", (size, size), (70, 74, 78))
    d = ImageDraw.Draw(img)
    k = size / 60
    for z, pts, col in quads:
        d.polygon([(x * k + size / 2, y * k + size * 0.93) for x, y in pts], fill=col, outline=col)
    # suelo y escala (bloques)
    for yb in (0, 16, 32):
        d.line([(0, size * 0.93 - yb * k), (12, size * 0.93 - yb * k)], fill=(200, 200, 200))
    return img


def smiler_test():
    """Variante del mob de pruebas: mismo modelo, el ojo derecho abierto y brillando."""
    geo = json.loads((ROOT / "geo/entity/berserker.geo.json").read_text())["minecraft:geometry"][0]
    head = next(b for b in geo["bones"] if b["name"] == "head")["cubes"][0]
    u, v = head["uv"]["north"]["uv"]
    w, h = head["uv"]["north"]["uv_size"]
    S = gm.SCALE
    for name in ("berserker", "berserker_glowmask"):
        im = Image.open(ROOT / f"textures/entity/{name}.png").convert("RGBA")
        px = im.load()
        ex, ey = u + w * 0.27, v + h * 0.36
        for y in range(int((ey - 1.2) * S), int((ey + 1.2) * S) + 1):
            for x in range(int((ex - 1.5) * S), int((ex + 1.5) * S) + 1):
                d = math.hypot((x / S - ex) / 1.5, (y / S - ey) / 1.1)
                if d <= 1:
                    px[x, y] = (255, 40, 30, 255) if d < 0.45 else (190, 14, 18, 255)
                elif name == "berserker_glowmask" and d <= 1.2:
                    pass
        im.save(ROOT / f"textures/entity/{name.replace('berserker', 'smiler_test')}.png")


def main():
    tw, th, nf = berserker()
    smiler_test()
    A = animations()
    print(f"berserker textura {tw * gm.SCALE}x{th * gm.SCALE} caras={nf} animaciones={list(A)}")
    if len(sys.argv) > 1:
        frames = [("idle", 0.0, 200), ("idle", 0.0, 110), ("walk", 0.4, 160), ("run", 0.2, 120),
                  ("strike", 0.6, 160), ("strike", 0.75, 110), ("search", 1.0, 200), ("recover", 0.2, 140)]
        ims = [render_pose(A[n], t, yaw=y) for n, t, y in frames]
        sheet = Image.new("RGB", (4 * 420, 2 * 420))
        for i, im in enumerate(ims):
            sheet.paste(im, ((i % 4) * 420, (i // 4) * 420))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
