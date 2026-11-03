#!/usr/bin/env python3
"""
Smiley jugable (fuera del mod): animaciones extra para llevarlo como modelo de jugador (agachado,
andar agachado, golpear la pared, abrir la boca) y exportadores a Figura y a Custom Player Models.

    python3 tools/smiley_playable.py anims  <preview.png>   # solo las animaciones nuevas, con vista previa
    python3 tools/smiley_playable.py export <carpeta>        # avatar de Figura y proyecto de CPM

No toca nada del mod: lee el modelo, la textura y las animaciones de Smiley que ya genera
smiley_model.py y escribe en la carpeta que se le de.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mob_models as mm  # noqa: E402
import smiley_model as sm  # noqa: E402
from mob_models import kf, loop_anim, render_pose, ROOT  # noqa: E402
from smiley_model import ARM, R, both, cyc, fingers, gait  # noqa: E402

s, c = math.sin, math.cos

# agachado: muslos hacia delante, espinillas hacia atras, el pie plano; la cadera baja ~9,5
THIGH, SHIN, FOOT, DROP = -55, 95, -40, -9.5
# el cuerpo se echa aun mas hacia delante y la cabeza se levanta para seguir mirando al frente
LEAN = {"torso": 15, "chest": 18, "neck": 5, "head": -36}


def crouch_body(L, k=1.0):
    """Tronco, cabeza y brazos agachado (respiracion lenta y la cabeza que se ladea)."""
    return {
        "root": {"position": cyc(L, lambda p: [0.3 * s(p), DROP + 0.25 * c(2 * p), 0])},
        "hips": {"rotation": cyc(L, lambda p: [6, 3 * s(p) * k, 2 * s(p)])},
        "torso": {"rotation": cyc(L, lambda p: [LEAN["torso"] + 1.2 * s(2 * p), -2 * s(p) * k, -1 * s(p)])},
        "chest": {"rotation": cyc(L, lambda p: [LEAN["chest"] + 2 * s(2 * p), -4 * s(p) * k, -1.5 * s(p)])},
        "neck": {"rotation": cyc(L, lambda p: [LEAN["neck"] - 1.5 * s(2 * p + 0.4), 0, 0])},
        **{f"shoulder_{sd}": {"rotation": cyc(L, lambda p, kk=kk: [0, 0, kk * (2 + 1.5 * s(2 * p - 0.3))])}
           for sd, kk in (("r", -1), ("l", 1))},
    }


def animations():
    A = {}

    # crouch: en cuclillas, enorme y encogido; los brazos cuelgan hasta el suelo y las garras
    # tamborilean en el; la cabeza se ladea despacio y vuelve de un tiron
    L = 4.0
    A["crouch"] = loop_anim(L, {
        **crouch_body(L, 0),
        "neck2": R((0, [2, 0, 0]), (1.2, [2, 0, 0]), (2.0, [-2, 4, 10]), (2.9, [-2, 4, 10]), (3.05, [3, -2, -3]), (L, [2, 0, 0]),
                   loop=True),
        "head": R((0, [LEAN["head"], 0, 0]), (1.2, [LEAN["head"], 0, 0]), (2.1, [LEAN["head"] + 4, 8, 24]),
                  (2.9, [LEAN["head"] + 4, 9, 26]), (3.03, [LEAN["head"] - 3, -5, -8]), (3.4, [LEAN["head"], 1, 2]),
                  (L, [LEAN["head"], 0, 0]), loop=True),
        "jaw": {"rotation": cyc(L, lambda p: [3 + 2 * s(2 * p), 0, 0])},
        "eye": {"scale": kf([(0, [1, 1, 1]), (1.6, [1, 1, 1]), (1.65, [1, 0.1, 1]), (1.72, [1, 1, 1]), (L, [1, 1, 1])])},
        "arm_r": {"rotation": cyc(L, lambda p: [-14 + 1.5 * s(p + 0.6), 0, -6])},
        "arm_l": {"rotation": cyc(L, lambda p: [-14 - 1.5 * s(p + 0.6), 0, 6])},
        "forearm_r": {"rotation": cyc(L, lambda p: [-18 + 2 * s(p - 0.4), 0, 0])},
        "forearm_l": {"rotation": cyc(L, lambda p: [-18 - 2 * s(p - 0.4), 0, 0])},
        "hand_r": {"rotation": cyc(L, lambda p: [-20 + 2 * s(p - 1.0), 0, 0])},
        "hand_l": {"rotation": cyc(L, lambda p: [-20 - 2 * s(p - 1.0), 0, 0])},
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i, sd=sd: [
            10 + 30 * max(0.0, s(4 * p - i * 0.9 - (0 if sd == "r" else 2.2))) ** 6, 0, 0], 48)}),
        **both("thumb", (0, [10, 0, 0])),
        **both("thigh", (0, [THIGH, 0, -9])),
        **both("shin", (0, [SHIN, 0, 0])),
        **both("foot", (0, [FOOT, 0, 9])),
        **both("toe", (0, [6, 0, 0])),
    })

    # crouch_walk: avanza a gatas sobre los pies, despacio y a saltitos, el cuerpo casi quieto y la
    # cabeza clavada al frente; los brazos se arrastran hacia delante uno tras otro
    L = 2.0
    A["crouch_walk"] = loop_anim(L, {
        **crouch_body(L),
        "root": {"position": cyc(L, lambda p: [0.4 * s(p), DROP + 0.8 * c(2 * p) - 0.4, 0])},
        "neck2": {"rotation": cyc(L, lambda p: [-1, -2 * s(p), 0])},
        "head": {"rotation": cyc(L, lambda p: [LEAN["head"] + 1.5 * c(2 * p + 1.4), -3 * s(p), 4 + 2 * c(p)])},
        "jaw": {"rotation": cyc(L, lambda p: [4 + 2 * s(2 * p), 0, 0])},
        "arm_r": {"rotation": cyc(L, lambda p: [-22 - 16 * s(p), 0, -6])},
        "arm_l": {"rotation": cyc(L, lambda p: [-22 + 16 * s(p), 0, 6])},
        "forearm_r": {"rotation": cyc(L, lambda p: [-16 - 8 * max(0.0, s(p - 0.6)), 0, 0])},
        "forearm_l": {"rotation": cyc(L, lambda p: [-16 - 8 * max(0.0, -s(p - 0.6)), 0, 0])},
        "hand_r": {"rotation": cyc(L, lambda p: [-14 + 10 * s(p - 1.4), 0, 0])},
        "hand_l": {"rotation": cyc(L, lambda p: [-14 - 10 * s(p - 1.4), 0, 0])},
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i, k=(1 if sd == "r" else -1): [
            14 + 12 * k * s(p - 2.0 - i * 0.3), 0, 0])}),
        **both("thumb", (0, [12, 0, 0])),
        **gait(L, 16, 26, SHIN, THIGH),
        **{f"thigh_{sd}": {"rotation": cyc(L, lambda p, o=o, kk=kk: [THIGH - 16 * s(p + o), 0, -9 * kk])}
           for sd, o, kk in (("r", 0.0, 1), ("l", math.pi, -1))},
    })

    # tap: levanta la mano hasta la cara y da tres golpes con los nudillos (en la pared, en la
    # puerta, en el cristal); ladea la cabeza escuchando entre golpe y golpe
    L = 2.2
    knock = [(0.55, -86, -40), (0.68, -86, -62), (0.86, -86, -40), (0.99, -86, -62), (1.17, -86, -40), (1.30, -86, -62)]
    A["tap"] = loop_anim(L, {
        "torso": R((0, [0, 0, 0]), (0.4, [4, -6, 0]), (1.4, [4, -6, 0]), (L, [0, 0, 0])),
        "chest": R((0, [0, 0, 0]), (0.4, [6, -10, 0]), (1.4, [6, -10, 0]), (L, [0, 0, 0])),
        "neck2": R((0, [0, 0, 0]), (0.5, [0, 4, 10]), (1.45, [0, 4, 14]), (1.7, [0, 4, 24]), (L, [0, 0, 0])),
        "head": R((0, [0, 0, 0]), (0.5, [2, 8, 18]), (1.45, [2, 8, 20]), (1.75, [4, 10, 38]), (L, [0, 0, 0])),
        "jaw": R((0, [2, 0, 0]), (1.45, [2, 0, 0]), (1.8, [10, 0, 0]), (L, [2, 0, 0])),
        "shoulder_r": R((0, [0, 0, 0]), (0.45, [0, 0, -6]), (1.4, [0, 0, -6]), (L, [0, 0, 0])),
        "arm_r": R((0, [ARM, 0, -4]), (0.45, [-86, 0, 18]), *[(t, [a, 0, 18]) for t, a, _ in knock], (1.45, [-86, 0, 18]),
                   (L, [ARM, 0, -4])),
        "forearm_r": R((0, [-6, 0, 0]), (0.45, [-40, 0, 0]), *[(t, [f, 0, 0]) for t, _, f in knock], (1.45, [-40, 0, 0]),
                       (L, [-6, 0, 0])),
        "hand_r": R((0, [0, 0, 0]), (0.45, [-30, 0, 0]), (1.45, [-30, 0, 0]), (L, [0, 0, 0])),
        **fingers(lambda i, sd: R((0, [6, 0, 0]), (0.45, [80, 0, 0]), (1.45, [80, 0, 0]), (L, [6, 0, 0])), "r"),
        "thumb_r": R((0, [0, 0, 0]), (0.45, [40, 0, 10]), (1.45, [40, 0, 10]), (L, [0, 0, 0])),
        "arm_l": {"rotation": cyc(L, lambda p: [ARM + 1.5 * s(p), 0, 4])},
        "forearm_l": {"rotation": kf([(0, [-6, 0, 0])])},
        **fingers(lambda i, sd: {"rotation": cyc(L, lambda p, i=i: [6 + 26 * max(0.0, s(4 * p - i * 0.9)) ** 6, 0, 0], 48)}, "l"),
    }, loop=False)

    sm.HUNCH.update({"crouch": 1, "crouch_walk": 1, "tap": 0.6})
    sm.outward(A)
    sm.hunch(A)
    sm.hang_hair(A)
    mm.enrich(A, mm.bones_of("smiley"))
    return A


def overlays():
    """Capas que se suman encima (sin postura encorvada ni relleno): abrir la boca."""
    A = {}
    # jaw_open: abre la boca despacio, del todo, y se queda asi (la cabeza un poco hacia atras, el
    # ojo muy abierto); el script la cierra al soltar
    L = 1.2
    A["jaw_open"] = loop_anim(L, {
        "chest": R((0, [0, 0, 0]), (0.8, [-4, 0, 0]), (L, [-4, 0, 0])),
        "neck2": R((0, [0, 0, 0]), (0.8, [-6, 0, 0]), (L, [-6, 0, 0])),
        "head": R((0, [0, 0, 0]), (0.9, [-12, 0, 4]), (L, [-12, 0, 4])),
        "jaw": R((0, [2, 0, 0]), (0.25, [6, 0, 0]), (0.9, [58, 0, 0]), (L, [56, 0, 0])),
    }, loop="hold_on_last_frame")
    return A


def all_animations():
    """Las del mod que sirven para el jugador mas las nuevas."""
    mod = json.loads((ROOT / "animations/entity/smiley.animation.json").read_text())["animations"]
    A = {k: mod[k] for k in ("idle", "walk", "run", "peek", "scream")}
    A.update(animations())
    return A


def preview(out):
    A = all_animations()
    frames = [("crouch", 0.0, 20), ("crouch", 2.5, 90), ("crouch_walk", 0.5, 30), ("crouch_walk", 1.5, 90),
              ("tap", 0.68, 40), ("tap", 1.0, 90), ("idle", 0.0, 20), ("idle", 0.0, 90)]
    ims = [render_pose(A[n], t, yaw=y, name="smiley", height=80, bright=3.0, lift=26) for n, t, y in frames]
    sheet = Image.new("RGB", (4 * 420, 2 * 420))
    for i, im in enumerate(ims):
        sheet.paste(im, ((i % 4) * 420, (i // 4) * 420))
    sheet.save(out)


if __name__ == "__main__":
    if sys.argv[1] == "anims":
        preview(sys.argv[2])
    else:
        import smiley_export
        smiley_export.main(Path(sys.argv[2]), all_animations(), overlays())
