#!/usr/bin/env python3
"""
Exporta el Smiley del mod como modelo de jugador: un avatar de Figura (carpeta con .bbmodel, texturas,
script y sonidos) y un proyecto de Custom Player Models (.cpmproject). Lo llama smiley_playable.py.

Conversion de coordenadas (comprobada con las fuentes de GeckoLib, Blockbench/Figura y CPM):
  * Figura lee el .bbmodel como Blockbench 4: cubos y pivotes con la X invertida respecto al
    .geo.json (como al importar un modelo de Bedrock) y los fotogramas tal cual (valores de Bedrock).
  * CPM usa el espacio de ModelPart de Java (Y hacia abajo, suelo en Y=24): x = x, y = 24 - y, z = z;
    las rotaciones son los mismos numeros que en la animacion de GeckoLib (orden Z*Y*X) y las
    posiciones de la animacion pasan como (x, -y, z). Las caras este y oeste se cruzan.
"""
import base64
import io
import json
import math
import shutil
import subprocess
import uuid
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol"
NS = uuid.UUID("6b1e9a3c-5a52-4f7e-9d1e-3a7c2f1b0e11")
TEXEL = 4                     # pixeles de textura por unidad de modelo (la textura es 256x512 para 64x128)
LOOK = {"neck2": 0.35, "head": 0.65}  # reparto del giro de la cabeza (igual que SmileyRenderer)
GESTURES = ("peek", "tap", "scream")
OVERLAYS = ("jaw_open",)
LOCO = ("idle", "walk", "run", "crouch", "crouch_walk")


def uid(*parts):
    return str(uuid.uuid5(NS, "/".join(map(str, parts))))


def sid(*parts):
    """storeID estable (positivo, 63 bits) para CPM."""
    return int(uuid.uuid5(NS, "/".join(map(str, parts))).int >> 65)


# Datos de origen ----------------------------------------------------------------------------------

def load_geo():
    geo = json.loads((ROOT / "geo/entity/smiley.geo.json").read_text())["minecraft:geometry"][0]
    return geo["bones"]


def channel_keys(ch):
    return sorted(((float(t), [float(x) for x in v]) for t, v in ch.items()), key=lambda k: k[0])


def sample(keys, t):
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / (t1 - t0 + 1e-9)
            return [a + (b - a) * k for a, b in zip(v0, v1)]
    return keys[-1][1]


def simplify(keys, tol):
    """Quita fotogramas que la interpolacion lineal de sus vecinos ya da (Douglas-Peucker en el tiempo)."""
    if len(keys) <= 2:
        if len(keys) == 2 and max(abs(a - b) for a, b in zip(keys[0][1], keys[1][1])) < tol:
            return keys[:1]
        return keys

    def rec(i, j):
        t0, v0 = keys[i]
        t1, v1 = keys[j]
        worst, at = 0.0, -1
        for k in range(i + 1, j):
            t, v = keys[k]
            u = (t - t0) / (t1 - t0 + 1e-9)
            err = max(abs(v[d] - (v0[d] + (v1[d] - v0[d]) * u)) for d in range(3))
            if err > worst:
                worst, at = err, k
        if worst <= tol:
            return [i, j]
        left = rec(i, at)
        return left[:-1] + rec(at, j)

    out = [keys[i] for i in rec(0, len(keys) - 1)]
    if all(max(abs(a - b) for a, b in zip(k[1], out[0][1])) < tol for k in out):
        return out[:1]
    return out


TOL = {"rotation": 0.6, "position": 0.06, "scale": 0.015}


def prepared(A):
    """{anim: {"length", "loop", "bones": {bone: {canal: [(t, v)]}}}} con fotogramas simplificados."""
    out = {}
    for name, an in A.items():
        L = float(an["animation_length"])
        bones = {}
        for b, chans in an["bones"].items():
            for ch, data in chans.items():
                if not isinstance(data, dict):
                    data = {"0": data}
                keys = simplify(channel_keys(data), TOL[ch])
                rest = [1, 1, 1] if ch == "scale" else [0, 0, 0]
                if len(keys) == 1 and max(abs(a - r) for a, r in zip(keys[0][1], rest)) < TOL[ch]:
                    continue
                bones.setdefault(b, {})[ch] = keys
        loop = an.get("loop", True)
        out[name] = {"length": L, "loop": "loop" if loop is True else "hold" if loop == "hold_on_last_frame" else "once",
                     "bones": bones}
    return out


def textures(dst):
    """Texturas reducidas a paleta (el ruido del negro pesa mucho y Figura limita el tamano)."""
    def shrink(src, out):
        im = Image.open(src).convert("RGBA")
        im.quantize(256, method=Image.Quantize.FASTOCTREE).save(out, "PNG", optimize=True)
    shrink(ROOT / "textures/entity/smiley.png", dst / "smiley.png")
    shrink(ROOT / "textures/entity/smiley_glowmask.png", dst / "smiley_e.png")


# Figura ---------------------------------------------------------------------------------------------

def bbmodel(bones, P, tex_dir):
    elements, groups = [], {}
    for b in bones:
        piv = b["pivot"]
        g = {"name": b["name"], "origin": [-piv[0], piv[1], piv[2]], "rotation": [0, 0, 0], "color": 0,
             "uuid": uid("group", b["name"]), "export": True, "mirror_uv": False, "isOpen": False, "locked": False,
             "visibility": True, "autouv": 0, "children": []}
        groups[b["name"]] = g
        for i, cu in enumerate(b.get("cubes", [])):
            o, s = cu["origin"], cu["size"]
            faces = {}
            for f, d in cu["uv"].items():
                u, v = d["uv"]
                su, sv = d["uv_size"]
                faces[f] = {"uv": [u, v, u + su, v + sv], "texture": 0}
            e = {"name": f"{b['name']}_{i}", "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
                 "allow_mirror_modeling": True, "from": [-(o[0] + s[0]), o[1], o[2]], "to": [-o[0], o[1] + s[1], o[2] + s[2]],
                 "autouv": 0, "color": 0, "origin": [-piv[0], piv[1], piv[2]], "faces": faces, "type": "cube",
                 "uuid": uid("cube", b["name"], i)}
            elements.append(e)
            g["children"].append(e["uuid"])
    outliner = []
    for b in bones:
        g = groups[b["name"]]
        if b.get("parent"):
            groups[b["parent"]]["children"].append(g)
        else:
            outliner.append(g)

    def tex(name, tid):
        data = (tex_dir / name).read_bytes()
        return {"path": "", "name": name, "folder": "", "namespace": "", "id": str(tid), "width": 256, "height": 512,
                "uv_width": 64, "uv_height": 128, "particle": False, "layers_enabled": False, "render_mode": "default",
                "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
                "visible": True, "internal": True, "saved": True, "uuid": uid("tex", name), "relative_path": name,
                "source": "data:image/png;base64," + base64.b64encode(data).decode()}

    anims = []
    for name, an in P.items():
        animators = {}
        for b, chans in an["bones"].items():
            kfs = []
            for ch, keys in chans.items():
                for t, v in keys:
                    kfs.append({"channel": ch, "data_points": [{"x": f"{v[0]:.3g}", "y": f"{v[1]:.3g}", "z": f"{v[2]:.3g}"}],
                                "uuid": uid("kf", name, b, ch, t), "time": round(t, 4), "color": -1, "interpolation": "linear"})
            animators[groups[b]["uuid"]] = {"name": b, "type": "bone", "keyframes": kfs}
        anims.append({"uuid": uid("anim", name), "name": name, "loop": an["loop"], "override": False, "length": an["length"],
                      "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
                      "loop_delay": "", "animators": animators})
    return {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": "smiley",
            "model_identifier": "", "visible_box": [5, 5, 2], "variable_placeholders": "", "variable_placeholder_buttons": [],
            "timeline_setups": [], "unhandled_root_fields": {}, "resolution": {"width": 64, "height": 128},
            "elements": elements, "outliner": outliner,
            "textures": [tex("smiley.png", 0), tex("smiley_e.png", 1)], "animations": anims}


def lua_path(bones, name):
    by = {b["name"]: b for b in bones}
    chain = []
    while name:
        chain.append(name)
        name = by[name].get("parent")
    return "models.smiley." + ".".join(reversed(chain))


SOUNDS = {"giggle": "giggle", "tap": "tap", "scream": "scream", "breath": "breath"}


def sounds(dst):
    """Sonidos del mod en mono y calidad baja: el avatar entero tiene que caber en el limite de Figura."""
    for out, src in SOUNDS.items():
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(ROOT / f"sounds/smiley/{src}.ogg"), "-ac", "1",
                        "-ar", "22050", "-c:a", "libvorbis", "-q:a", "0", str(dst / f"{out}.ogg")], check=True)


def figura(bones, P, dst):
    dst.mkdir(parents=True, exist_ok=True)
    textures(dst)
    sounds(dst)
    (dst / "smiley.bbmodel").write_text(json.dumps(bbmodel(bones, P, dst), separators=(",", ":")))
    (dst / "avatar.json").write_text(json.dumps({
        "name": "Smiley", "description": "Smiley de Smile Protocol, jugable. Rueda de acciones: asomarse, golpear, gritar, "
                                         "abrir la boca y reirse.",
        "authors": ["Smile Protocol"], "color": "#c40c12"}, indent=2, ensure_ascii=False) + "\n")
    script = (Path(__file__).with_name("smiley_figura.lua")).read_text()
    script = script.replace("@NECK2@", lua_path(bones, "neck2")).replace("@HEAD@", lua_path(bones, "head"))
    script = script.replace("@LENGTHS@", ", ".join(f'{k} = {P[k]["length"]}' for k in GESTURES))
    (dst / "script.lua").write_text(script)


# Custom Player Models --------------------------------------------------------------------------------

V = lambda x, y, z: {"x": round(float(x), 4), "y": round(float(y), 4), "z": round(float(z), 4)}  # noqa: E731
FACE = {"north": "north", "south": "south", "up": "up", "down": "down", "east": "west", "west": "east"}


def cpm_elements(bones, glow):
    """Arbol de CPM colgado del cuerpo; cada hueso es un elemento vacio y cada cubo un hijo suyo."""
    by = {b["name"]: b for b in bones}
    gm = np.asarray(glow)[..., 3].astype(float)

    def jpiv(name):
        p = by[name]["pivot"]
        return np.array([p[0], 24 - p[1], p[2]])

    def cube(b, i, cu):
        o, s = cu["origin"], cu["size"]
        piv = b["pivot"]
        faces, lit = {}, []
        for f, d in cu["uv"].items():
            u, v = d["uv"]
            su, sv = d["uv_size"]
            x0, y0, x1, y1 = (round(u * TEXEL), round(v * TEXEL), round((u + su) * TEXEL), round((v + sv) * TEXEL))
            faces[FACE[f]] = {"sx": x0, "sy": y0, "ex": x1, "ey": y1, "rot": "0", "autoUV": False}
            reg = gm[min(y0, y1):max(y0, y1), min(x0, x1):max(x0, x1)]
            if reg.size:
                lit.append((reg > 60).mean())
        # CPM solo enciende cubos enteros: se enciende todo cubo con algo que brille (lo demas es negro y
        # sigue negro a plena luz)
        glowing = bool(lit) and bool(max(lit) > 0.02)
        return {"name": f"{b['name']}_{i}", "show": True, "texture": True, "textureSize": 1,
                "offset": V(o[0] - piv[0], piv[1] - o[1] - s[1], o[2] - piv[2]), "pos": V(0, 0, 0), "rotation": V(0, 0, 0),
                "size": V(*s), "rscale": V(1, 1, 1), "scale": V(1, 1, 1), "u": 0, "v": 0, "color": "0", "mirror": False,
                "mcScale": 0.0, "glow": glowing, "recolor": False, "hidden": False, "singleTex": False, "extrude": False,
                "locked": False, "nameColor": 0, "faceUV": faces, "storeID": sid("cube", b["name"], i)}

    def bone(name):
        b = by[name]
        parent = b.get("parent")
        rel = jpiv(name) - (jpiv(parent) if parent else np.zeros(3))
        children = [cube(b, i, cu) for i, cu in enumerate(b.get("cubes", []))]
        children += [bone(c["name"]) for c in bones if c.get("parent") == name]
        return {"name": name, "show": True, "texture": False, "textureSize": 1, "offset": V(0, 0, 0), "pos": V(*rel),
                "rotation": V(0, 0, 0), "size": V(0, 0, 0), "rscale": V(1, 1, 1), "scale": V(1, 1, 1), "u": 0, "v": 0,
                "color": "0", "mirror": False, "mcScale": 0.0, "glow": False, "recolor": False, "hidden": False,
                "singleTex": False, "extrude": False, "locked": False, "nameColor": 0, "storeID": sid("bone", name),
                "children": children}

    roots = [b["name"] for b in bones if not b.get("parent")]
    parts = []
    for part in ("head", "body", "left_arm", "right_arm", "left_leg", "right_leg"):
        e = {"id": part, "show": False, "showInEditor": True, "locked": False, "pos": V(0, 0, 0), "rotation": V(0, 0, 0),
             "dup": False, "disableVanillaAnim": part != "head", "name": "", "nameColor": 0}
        if part == "body":
            e["children"] = [bone(r) for r in roots]
        parts.append(e)
    return parts


def cpm_frames(an, bones, n, single):
    """Fotogramas equiespaciados (CPM no tiene tiempos por fotograma)."""
    by = {b["name"]: b for b in bones}
    L = an["length"]
    frames = []
    for i in range(n):
        t = L * i / (n - 1) if single else L * i / n
        comps = []
        for b, chans in an["bones"].items():
            r = sample(chans["rotation"], t) if "rotation" in chans else [0, 0, 0]
            p = sample(chans["position"], t) if "position" in chans else [0, 0, 0]
            s = sample(chans["scale"], t) if "scale" in chans else [1, 1, 1]
            comps.append((b, r, p, s))
        frames.append(comps)
    return frames


def cpm_anim(name, an, bones, kind, absolute=True):
    by = {b["name"]: b for b in bones}
    parent_piv = {}
    for b in bones:
        p = b["pivot"]
        pp = by[b["parent"]]["pivot"] if b.get("parent") else None
        parent_piv[b["name"]] = (np.array([p[0], 24 - p[1], p[2]]) - (np.array([pp[0], 24 - pp[1], pp[2]]) if pp else 0))
    single = an["loop"] != "loop"
    fps = 12
    n = max(2, int(round(an["length"] * fps)) + (1 if single else 0))
    frames = []
    for comps in cpm_frames(an, bones, n, single):
        out = []
        for b, r, p, s in comps:
            base = parent_piv[b] if absolute else np.zeros(3)
            out.append({"storeID": sid("bone", b), "pos": V(base[0] + p[0], base[1] - p[1], base[2] + p[2]),
                        "rotation": V(*r), "scale": V(*s), "color": "0", "show": True})
        frames.append({"components": out})
    data = {"additive": not absolute, "name": kind.get("name", ""), "duration": int(round(an["length"] * 1000)),
            "priority": kind.get("priority", 0), "loop": an["loop"] == "loop" or kind.get("loop", False),
            "interpolator": "linear_single" if single else "linear_loop", "layerDefault": 0.0, "order": kind.get("order", 0),
            "isProperty": False, "command": False, "layerControlled": kind.get("layerControlled", True), "maxValue": 100,
            "interpolateVal": True, "mustFinish": False, "hidden": False, "frames": frames}
    return data


def look_anim(axis, bones):
    """Pose de valor de CPM: giro de la cabeza (de -90 a 90) repartido entre neck2 y head."""
    frames = []
    for val in (-90, 90):
        comps = []
        for b, k in LOOK.items():
            r = [0, 0, 0]
            r[axis] = val * k
            comps.append({"storeID": sid("bone", b), "pos": V(0, 0, 0), "rotation": V(*r), "scale": V(1, 1, 1),
                          "color": "0", "show": True})
        frames.append({"components": comps})
    return {"additive": True, "name": "", "duration": 1000, "priority": 0, "loop": False, "interpolator": "linear_single",
            "layerDefault": 0.0, "order": 0, "isProperty": False, "command": False, "layerControlled": True, "maxValue": 100,
            "interpolateVal": True, "mustFinish": False, "hidden": False, "frames": frames}


def scaling():
    """Caja, ojos y camara a juego con los ~4 bloques (CPM lo aplica con su modulo del servidor y Pehkui)."""
    keys = ("mining_speed", "hitbox_width", "reach", "attack_speed", "jump_height", "step_height", "attack_knockback", "defense",
            "hitbox_height", "fall_damage", "attack_dmg", "height", "projectile_dmg", "eye_height", "third_person", "motion",
            "flight_speed", "mob_visibility", "health", "view_bobbing", "explosion_dmg", "width")
    sc = {k: 1.0 for k in keys}
    sc.update({"hitbox_height": 2.2, "eye_height": 2.2, "third_person": 1.8})
    for k in ("render_rotation", "render_scale", "render_position"):
        sc[k] = V(0, 0, 0)
    return sc


def cpm(bones, P, overlay_raw, dst):
    dst.parent.mkdir(parents=True, exist_ok=True)
    glow = Image.open(ROOT / "textures/entity/smiley_glowmask.png").convert("RGBA")
    skin = io.BytesIO()
    Image.open(ROOT / "textures/entity/smiley.png").convert("RGBA").save(skin, "PNG", optimize=True)
    config = {
        "removeBedOffset": False, "scaling": 0.0, "enableInvisGlow": False, "version": 1, "skinType": "default",
        "modelId": "smileprotocol-smiley", "armor1Tex": False, "armor2Tex": False, "hideHeadIfSkull": True,
        "removeArmorOffset": True, "skinSize": {"x": 256, "y": 512}, "scalingEx": scaling(), "textures": {"skin": {"customGridSize": False, "anim": []}},
        "firstPersonHand": {h: {"rotation": V(0, 0, 0), "scale": V(0, 0, 0), "position": V(0, 0, 0)} for h in ("left", "right")},
        "elements": cpm_elements(bones, glow),
    }
    files = {"config.json": json.dumps(config, indent=1), "skin.png": skin.getvalue(),
             "description.json": json.dumps({"name": "Smiley", "desc": "Smiley de Smile Protocol, jugable. Gestos: asomarse, "
                                             "golpear, gritar; capa: abrir la boca.",
                                             "cam": {"pos": V(0.5, 1.6, 0.5), "zoom": 40.0, "look": V(0.25, 0.5, 0.25),
                                                     "copyProt": "normal"}}, indent=1)}
    poses = {"standing": "idle", "walking": "walk", "running": "run", "sneaking": "crouch", "sneak_walk": "crouch_walk",
             "jumping": "idle", "falling": "idle", "on_ladder": "idle", "climbing_on_ladder": "walk"}
    for pose, an in poses.items():
        files[f"animations/v_{pose}_{an}_{uid('cpm', pose)}.json"] = json.dumps(cpm_anim(an, P[an], bones, {}))
    files[f"animations/v_head_rotation_yaw__{uid('cpm', 'yaw')}.json"] = json.dumps(look_anim(1, bones))
    files[f"animations/v_head_rotation_pitch__{uid('cpm', 'pitch')}.json"] = json.dumps(look_anim(0, bones))
    titles = {"peek": "Asomarse", "tap": "Golpear", "scream": "Gritar"}
    for i, g in enumerate(GESTURES):
        files[f"animations/g_{titles[g]}_{uid('cpm', g)}.json"] = json.dumps(
            cpm_anim(g, P[g], bones, {"name": titles[g], "priority": 10, "order": i, "loop": g == "peek",
                                      "layerControlled": g != "peek"}))
    # abrir la boca: capa que se enciende y se apaga (un solo fotograma, sumado a lo que este haciendo)
    jaw = {"length": 1.0, "loop": "once", "bones": {b: {ch: [(0.0, keys[-1][1])] for ch, keys in chans.items()}
                                                     for b, chans in overlay_raw["jaw_open"]["bones"].items()}}
    lay = cpm_anim("jaw_open", jaw, bones, {"name": "$layer$Abrir la boca", "order": 3}, absolute=False)
    lay["frames"] = lay["frames"][-1:]
    files[f"animations/g_layerAbrirlaboca_{uid('cpm', 'jaw')}.json"] = json.dumps(lay)
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as z:
        for n, data in files.items():
            z.writestr(n, data)


def main(out, A, overlays):
    bones = load_geo()
    P = prepared(A)
    O = prepared(overlays)
    for k, v in O.items():
        P[k] = v
    for g in ("tap", "scream"):
        P[g] = dict(P[g], loop="hold")    # se quedan en la ultima pose mientras el script los funde
    if out.exists():
        shutil.rmtree(out)
    figura(bones, P, out / "figura/smiley")
    cpm(bones, P, O, out / "cpm/smiley.cpmproject")
    nkf = sum(len(k) for an in P.values() for ch in an["bones"].values() for k in ch.values())
    print("fotogramas en Figura:", nkf)
