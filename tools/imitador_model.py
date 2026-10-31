#!/usr/bin/env python3
"""
El Imitador (docs/SMILERS.md): 2,4 bloques, palido y descarnado, encorvado como si escuchara. No tiene
ojos: la cabeza es un huevo liso con una sonrisa humana enorme, llena de dientes de personas distintas,
y dos orejas grandes que se mueven. Las manos, largas, se ahuecan junto a la boca cuando imita una voz.

    python3 tools/imitador_model.py [preview.png]
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import geo_models as gm  # noqa: E402
import mob_models as mm  # noqa: E402
from geo_models import Geo, MAT  # noqa: E402
from mob_models import art_at, paint, plaster, claw, kf, loop_anim, wave, multi, enrich, render_pose, ROOT  # noqa: E402

# Sonrisa de dientes humanos, cada uno de un color y tamano (de gente distinta), encias rosas
IMITADOR_SMILE = [
    "R............R",
    ".GRRRRRRRRRRG.",
    "GTTtTTyTTtTTTG",
    "GKKKKKKKKKKKKG",
    "GtTTyTTtTTyTtG",
    ".GRRRRRRRRRRG.",
    "..R........R..",
]
COLORS = dict(mm.SMILE_COLORS)
COLORS.update({"G": (190, 96, 110), "y": (200, 180, 120)})


def face_mat():
    sk = plaster((170, 164, 156))

    def mask(f):
        if f.face != "north":
            return np.zeros((f.h, f.w))
        ch = art_at(f, IMITADOR_SMILE, 0, 8)
        return np.isin(ch, ["R", "T", "t", "y"]).astype(float) * 0.85

    def mat(f):
        c, a = sk(f)
        if f.face != "north":
            return c, a
        ch = art_at(f, IMITADOR_SMILE, 0, 8)
        c = paint(c, ch, COLORS)
        # donde irian los ojos, piel lisa y estirada (un poco mas brillante)
        w, h = f.w / gm.SCALE, f.h / gm.SCALE
        for ex in (0.3, 0.7):
            m = np.hypot((f.U - w * ex) / 1.3, (f.V - h * 0.3) / 0.8) < 1
            c = np.where(m[..., None], c * 1.12 + 6, c)
        return c, a

    mat.glowmask = mask
    return mat


MAT.update({"imi_skin": plaster((150, 146, 140)), "imi_skin_dk": plaster((118, 112, 106)), "imi_face": face_mat(),
            "imi_claw": claw()})


def build():
    g = Geo("imitador", 91, folder="entity", glow=("imi_face",), ao=False)
    g.bone("root")
    g.bone("hips", "root", (0, 21, 0))
    g.cube("hips", (-3.5, 19, -2), (7, 4, 4), "imi_skin_dk")
    g.bone("torso", "hips", (0, 23, 0))
    g.cube("torso", (-2.8, 23, -1.8), (5.6, 6, 3.6), "imi_skin")
    g.cube("torso", (-4, 29, -2.4), (8, 7, 4.8), "imi_skin")
    for i in range(4):
        g.cube("torso", (-4.2, 29.5 + i * 1.6, -2.7), (8.4, 0.6, 0.4), "imi_skin_dk")      # costillas
    for i in range(5):
        g.cube("torso", (-0.5, 24 + i * 2.4, 2.1), (1, 1.2, 0.8), "imi_skin_dk")          # espinazo
    g.bone("neck", "torso", (0, 35.5, -1))
    g.cube("neck", (-1.1, 35, -2.1), (2.2, 5, 2.2), "imi_skin")                           # cuello largo
    g.bone("head", "neck", (0, 40, -1))
    g.cube("head", (-3.5, 39.5, -5.5), (7, 8, 7), "imi_face")
    g.cube("head", (-3, 47.5, -5), (6, 1.2, 6), "imi_skin")                               # craneo alargado
    for side, sx in (("r", 1), ("l", -1)):
        g.bone(f"ear_{side}", "head", (sx * 3.5, 44, -2))
        g.cube(f"ear_{side}", (sx * 3.5 - (0 if sx > 0 else 0.8), 42, -3.5), (0.8, 5, 3), "imi_skin_dk")
    g.bone("jaw", "head", (0, 41, -2))
    g.cube("jaw", (-3.2, 38.6, -5.3), (6.4, 1.2, 6.4), "imi_skin")
    for side, sx in (("r", 1), ("l", -1)):
        g.bone(f"arm_{side}", "torso", (sx * 4.6, 35, 0))
        g.cube(f"arm_{side}", (sx * 4.6 - 0.9, 23, -0.9), (1.8, 12.5, 1.8), "imi_skin")
        g.bone(f"forearm_{side}", f"arm_{side}", (sx * 4.6, 23.5, 0))
        g.cube(f"forearm_{side}", (sx * 4.6 - 0.75, 11, -0.75), (1.5, 13, 1.5), "imi_skin")
        g.bone(f"hand_{side}", f"forearm_{side}", (sx * 4.6, 11.5, 0))
        g.cube(f"hand_{side}", (sx * 4.6 - 0.9, 8, -1.6), (1.8, 3.6, 3.2), "imi_skin_dk")
        for i, z in enumerate((-1.4, -0.3, 0.8)):
            g.bone(f"finger{i}_{side}", f"hand_{side}", (sx * 4.6, 8.2, z + 0.25))
            g.cube(f"finger{i}_{side}", (sx * 4.6 - 0.25, 2.5, z), (0.5, 5.8, 0.5), "imi_skin")
            g.cube(f"finger{i}_{side}", (sx * 4.6 - 0.2, 0.8, z + 0.05), (0.4, 1.8, 0.4), "imi_claw")
    for side, sx in (("r", 1), ("l", -1)):
        g.bone(f"thigh_{side}", "hips", (sx * 2, 20, 0))
        g.cube(f"thigh_{side}", (sx * 2 - 1, 11, -1), (2, 9.5, 2), "imi_skin")
        g.bone(f"shin_{side}", f"thigh_{side}", (sx * 2, 11.5, 0))
        g.cube(f"shin_{side}", (sx * 2 - 0.8, 1.5, -0.8), (1.6, 10, 1.6), "imi_skin")
        g.bone(f"foot_{side}", f"shin_{side}", (sx * 2, 1.5, 0))
        g.cube(f"foot_{side}", (sx * 2 - 0.9, 0, -3), (1.8, 1.5, 4), "imi_skin_dk")
    mm.finger_tips(g, n=3, tip_mat="imi_claw")
    return g.save()


def animations():
    """idle (escucha, ladeando la cabeza y moviendo las orejas), walk (lento, de puntillas), run
    (encorvado, a zancadas), mimic (manos junto a la boca, la mandibula imita el habla), grab y lunge."""
    H = 24

    def base(extra):
        b = {
            "torso": {"rotation": kf([(0, [H, 0, 0])])},
            "neck": {"rotation": kf([(0, [-30, 0, 0])])},
            "head": {"rotation": kf([(0, [8, 0, 0])])},
            "arm_r": {"rotation": kf([(0, [-H + 4, 0, -5])])}, "arm_l": {"rotation": kf([(0, [-H + 4, 0, 5])])},
            "forearm_r": {"rotation": kf([(0, [-12, 0, 0])])}, "forearm_l": {"rotation": kf([(0, [-12, 0, 0])])},
            "thigh_r": {"rotation": kf([(0, [-10, 0, 2])])}, "thigh_l": {"rotation": kf([(0, [-10, 0, -2])])},
            "shin_r": {"rotation": kf([(0, [16, 0, 0])])}, "shin_l": {"rotation": kf([(0, [16, 0, 0])])},
            "hips": {"position": kf([(0, [0, -0.8, 0])])},
        }
        for k, v in extra.items():
            b.setdefault(k, {}).update(v)
        return b

    A = {}
    L = 3.2
    A["idle"] = loop_anim(L, base({
        "head": {"rotation": kf([(0, [8, 0, 0]), (0.9, [8, 0, 0]), (1.1, [6, 0, 24]), (2.0, [6, 0, 24]), (2.2, [10, 0, -18]),
                                 (2.9, [10, 0, -18]), (L, [8, 0, 0])])},
        "ear_r": {"rotation": kf(wave(L, [0, -10, 0], 14, 0, 8, 1))}, "ear_l": {"rotation": kf(wave(L, [0, 10, 0], 14, 1, 8, 1))},
        "torso": {"rotation": kf(wave(L, [H, 0, 0], 2))},
    }))
    L = 1.6
    A["walk"] = loop_anim(L, base({
        "thigh_r": {"rotation": kf(wave(L, [-10, 0, 2], 20))}, "thigh_l": {"rotation": kf(wave(L, [-10, 0, -2], 20, 3.1416))},
        "shin_r": {"rotation": kf(wave(L, [16, 0, 0], 16, -1.2))}, "shin_l": {"rotation": kf(wave(L, [16, 0, 0], 16, 1.94))},
        "arm_r": {"rotation": kf(wave(L, [-H + 4, 0, -5], 10, 3.1416))}, "arm_l": {"rotation": kf(wave(L, [-H + 4, 0, 5], 10))},
        "head": {"rotation": kf(multi(L, [8, 0, 0], [2, 8, 4], [0, 0.6, 1.2]))},
        "hips": {"position": kf(wave(L, [0, -0.8, 0], 0.5, 0, 8, 1))},
    }))
    L = 0.7
    A["run"] = loop_anim(L, base({
        "torso": {"rotation": kf(wave(L, [48, 0, 0], 4))}, "neck": {"rotation": kf([(0, [-44, 0, 0])])},
        "thigh_r": {"rotation": kf(wave(L, [-24, 0, 2], 38))}, "thigh_l": {"rotation": kf(wave(L, [-24, 0, -2], 38, 3.1416))},
        "shin_r": {"rotation": kf(wave(L, [34, 0, 0], 30, -1))}, "shin_l": {"rotation": kf(wave(L, [34, 0, 0], 30, 2.14))},
        "arm_r": {"rotation": kf(wave(L, [-60, 0, -10], 26, 3.1416))}, "arm_l": {"rotation": kf(wave(L, [-60, 0, 10], 26))},
        "jaw": {"rotation": kf([(0, [18, 0, 0])])},
        "hips": {"position": kf(wave(L, [0, -2, 0], 1, 0, 8, 1))},
    }))
    L = 1.2
    A["mimic"] = loop_anim(L, base({
        "head": {"rotation": kf(multi(L, [2, 0, 10], [3, 4, 4], [0, 1, 2]))},
        "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.1, [16, 0, 0]), (0.25, [4, 0, 0]), (0.4, [20, 0, 0]), (0.55, [2, 0, 0]),
                                (0.7, [12, 0, 0]), (0.85, [24, 0, 0]), (1.0, [6, 0, 0]), (L, [0, 0, 0])])},
        "arm_r": {"rotation": kf([(0, [-50, 0, -6])])}, "arm_l": {"rotation": kf([(0, [-50, 0, 6])])},
        "forearm_r": {"rotation": kf([(0, [-100, 0, 0])])}, "forearm_l": {"rotation": kf([(0, [-100, 0, 0])])},
        "ear_r": {"rotation": kf([(0, [0, -25, 0])])}, "ear_l": {"rotation": kf([(0, [0, 25, 0])])},
    }))
    L = 0.6
    A["grab"] = {"loop": "hold_on_last_frame", "animation_length": L, "bones": base({
        "torso": {"rotation": kf([(0, [H, 0, 0]), (0.25, [40, 0, 0]), (L, [36, 0, 0])])},
        "arm_r": {"rotation": kf([(0, [-20, 0, -5]), (0.25, [-95, 0, -14]), (L, [-88, 0, -8])])},
        "arm_l": {"rotation": kf([(0, [-20, 0, 5]), (0.25, [-95, 0, 14]), (L, [-88, 0, 8])])},
        "jaw": {"rotation": kf([(0, [0, 0, 0]), (0.25, [32, 0, 0]), (L, [28, 0, 0])])},
    })}
    out = {"format_version": "1.8.0", "animations": A}
    p = ROOT / "animations/entity/imitador.animation.json"
    p.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
    mm.rewrite_anims("imitador")
    return json.loads(p.read_text())["animations"]


def main():
    tw, th, nf = build()
    A = animations()
    print(f"imitador textura {tw * gm.SCALE}x{th * gm.SCALE} caras={nf} animaciones={list(A)}")
    if len(sys.argv) > 1:
        from PIL import Image
        frames = [("idle", 0.0, 200), ("idle", 1.5, 150), ("walk", 0.4, 120), ("run", 0.2, 120),
                  ("mimic", 0.4, 180), ("grab", 0.6, 150)]
        ims = [render_pose(A[n], t, yaw=y, name="imitador", height=52) for n, t, y in frames]
        sheet = Image.new("RGB", (3 * 420, 2 * 420))
        for i, im in enumerate(ims):
            sheet.paste(im, ((i % 3) * 420, (i // 3) * 420))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
