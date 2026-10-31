#!/usr/bin/env python3
"""
Ambiente de cada planta: bucles muy bajos de sonido (zumbidos, aire, crujidos, gotas), sin melodias, uno por planta, que se funden al
cambiar de planta y se callan cuando suena el jefe. Cada uno tiene su caracter:

  start_corridor   recepcion: tubos que zumban, ventilacion y crujidos del edificio
  lab_storage      almacen: nave enorme, chapas lejanas que se asientan
  lab_offices      oficinas: fluorescentes, aire acondicionado viejo, golpes tras una puerta
  lab_morgue       morgue: camaras frigorificas, un soplo helado y un latido casi inaudible
  lab_generators   energia: transformador que late y turbinas de fondo
  lab_greenhouse   jardin: viento entre las plantas, gotas y humedad
  final_room       criogenia: compresores, escarcha que cruje y silbido de gas
  smiley_lab       laboratorio cero: algo enorme respira en la nave y un pulso grave
  tutorial         entrenamiento: ventilacion suave y un colchon muy grave

Mono, 48 s, en .ogg de calidad baja (es ambiente).

    python3 tools/floor_music.py [demo.wav]
"""
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

from music import SR, hz, lp, hp, bp, add, box_note, wow, voice, heart, drone, reverse_swell, tick, metal_hit, tom, reverb, \
    compress, MELODY

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/sounds/music/floor"
SECS = 48
N = SR * SECS


def pad(notes, rng, gain=1.0, cutoff=900, cycles=3, detune=8):
    """Colchon: dientes de sierra desafinados y filtrados que suben y bajan (ciclos enteros: bucle)."""
    tt = np.arange(N) / SR
    x = np.zeros(N)
    for m in notes:
        for v in range(3):
            f = hz(m) * 2 ** (rng.normal(0, detune) / 1200)
            ph = (f * tt + rng.uniform()) % 1.0
            x += 2 * ph - 1
    x = lp(x, cutoff, 2)
    swell = 0.55 + 0.45 * np.sin(np.arange(N) / N * 2 * np.pi * cycles + rng.uniform(0, 6))
    return x * swell * gain / max(1, len(notes))


def wind(rng, gain=1.0):
    n = rng.normal(0, 1, N)
    sweep = 0.5 + 0.5 * np.sin(np.arange(N) / N * 2 * np.pi * 5)
    return (bp(n, 200, 900, 1) * (0.4 + sweep) + bp(n, 900, 2500, 1) * sweep * 0.3) * gain


def drop(rng, f0):
    m = int(SR * 0.4)
    tm = np.arange(m) / SR
    f = f0 * (1 + 1.5 * np.exp(-tm / 0.01))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tm / 0.07)


def glass(rng, f):
    m = int(SR * 4)
    tm = np.arange(m) / SR
    x = sum(np.sin(2 * np.pi * f * r * tm + rng.uniform(0, 6)) * g * np.exp(-tm / d)
            for r, g, d in ((1, 1, 2.5), (2.76, 0.4, 1.2), (5.4, 0.2, 0.5)))
    return x * np.clip(tm / 0.01, 0, 1)


def scatter(buf, make, count, rng, gain, spread=None):
    for _ in range(count):
        at = int(rng.uniform(0, N)) if spread is None else int(spread(rng))
        add(buf, make() * gain, at)


def rumble(rng, gain, fc=60):
    """Retumbar del edificio: ruido muy grave que sube y baja."""
    n = lp(rng.normal(0, 1, N), fc, 2)
    sw = 0.6 + 0.4 * np.sin(np.arange(N) / N * 2 * np.pi * 3 + rng.uniform(0, 6))
    return n / (np.max(np.abs(n)) + 1e-9) * sw * gain


def air(rng, gain, lo=300, hi=2500, cycles=4):
    """Aire de ventilacion: ruido filtrado que respira."""
    n = bp(rng.normal(0, 1, N), lo, hi, 1)
    sw = 0.5 + 0.5 * np.sin(np.arange(N) / N * 2 * np.pi * cycles + rng.uniform(0, 6))
    return n / (np.max(np.abs(n)) + 1e-9) * (0.4 + 0.6 * sw) * gain


def hum(f, gain, cycles=6):
    """Zumbido electrico de la red: fundamental y armonicos, con un latido leve."""
    tt = np.arange(N) / SR
    x = sum(np.sin(2 * np.pi * f * k * tt) / k for k in (1, 2, 3, 4))
    return lp(x, 400) * (0.75 + 0.25 * np.sin(np.arange(N) / N * 2 * np.pi * cycles)) * gain


def creak(rng):
    """Crujido de metal o madera lejos: un barrido grave con aspereza."""
    m = int(SR * rng.uniform(0.6, 1.4))
    tm = np.arange(m) / SR
    f = rng.uniform(70, 160) * (1 + 0.3 * np.sin(tm * rng.uniform(3, 9)))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * (1 + 0.6 * rng.normal(0, 1, m))
    return bp(x, 80, 900, 1) * np.sin(np.pi * tm / tm[-1]) ** 2


def knock(rng):
    m = int(SR * 0.3)
    tm = np.arange(m) / SR
    return lp(rng.normal(0, 1, m), 400) * np.exp(-tm / 0.03) + np.sin(2 * np.pi * 90 * tm) * np.exp(-tm / 0.05)


def track(name):
    rng = np.random.default_rng(sum(map(ord, name)) * 7919)
    buf = np.zeros(N)
    if name == "start_corridor":
        # recepcion: tubos que zumban, aire de la ventilacion y algun crujido del edificio
        buf += hum(50, 0.05) + air(rng, 0.10, 200, 1800) + rumble(rng, 0.10)
        buf += drone(N, hz(33), 0.03, rng)
        scatter(buf, lambda: creak(rng), 4, rng, 0.25)
        buf = reverb(buf, 3.0, 0.5, 1)
    elif name == "lab_storage":
        # almacen: nave grande, chapas que se asientan muy lejos, goteo de maquinaria
        buf += rumble(rng, 0.14, 45) + air(rng, 0.06, 150, 900, 2)
        scatter(buf, lambda: lp(metal_hit(rng), 900), 4, rng, 0.18)
        scatter(buf, lambda: creak(rng), 5, rng, 0.3)
        buf = reverb(buf, 5.0, 0.7, 2)
    elif name == "lab_offices":
        # oficinas: fluorescentes, un aire acondicionado viejo, algun golpe tras una puerta
        buf += hum(60, 0.06, 9) + air(rng, 0.12, 400, 3000, 6) + rumble(rng, 0.06)
        scatter(buf, lambda: knock(rng), 3, rng, 0.3)
        scatter(buf, lambda: tick(rng) * 0.5, 14, rng, 0.15)
        buf = reverb(buf, 2.5, 0.45, 3)
    elif name == "lab_morgue":
        # morgue: camaras frigorificas, un soplo helado y un latido que casi no se oye
        buf += hum(47, 0.07, 3) + air(rng, 0.10, 600, 4000, 3) + rumble(rng, 0.08, 40)
        breath = air(np.random.default_rng(44), 0.08, 250, 700, 2)
        buf += breath * (0.5 + 0.5 * np.sin(np.arange(N) / N * 2 * np.pi * 6))
        buf += heart(N, 44, 0.06)
        buf = reverb(buf, 4.5, 0.6, 4)
    elif name == "lab_generators":
        # energia: zumbido grave de transformador que late, turbinas de fondo
        tt = np.arange(N) / SR
        buf += hum(50, 0.12, 24) + rumble(rng, 0.12, 80)
        buf += bp(rng.normal(0, 1, N), 900, 1600, 2) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.5 * tt)) * 0.04
        scatter(buf, lambda: tick(rng) * 0.5, 20, rng, 0.15)
        buf = reverb(buf, 3.0, 0.45, 5)
    elif name == "lab_greenhouse":
        # jardin: viento entre las plantas, gotas y humedad
        buf += wind(rng, 0.06) + air(rng, 0.05, 1500, 6000, 5)
        scatter(buf, lambda: drop(rng, rng.uniform(900, 1700)), 30, rng, 0.2)
        buf = reverb(buf, 3.5, 0.55, 6)
    elif name == "final_room":
        # criogenia: compresores, escarcha que cruje y un silbido de gas
        buf += hum(43, 0.07, 4) + rumble(rng, 0.08, 50)
        buf += air(rng, 0.06, 3000, 9000, 7)
        scatter(buf, lambda: lp(glass(rng, rng.uniform(1800, 3200)), 4000) * 0.4, 6, rng, 0.12)
        add(buf, reverse_swell(3.0, rng) * 0.25, int(25 * SR))
        buf = reverb(buf, 5.0, 0.6, 7)
    elif name == "smiley_lab":
        # laboratorio cero: algo enorme respira en la nave; un pulso grave
        buf += heart(N, 36, 0.07) + rumble(rng, 0.14, 35) + drone(N, hz(26), 0.04, rng)
        breath = air(np.random.default_rng(88), 0.10, 120, 500, 4)
        buf += breath * (0.5 + 0.5 * np.sin(np.arange(N) / N * 2 * np.pi * 8)) ** 2
        scatter(buf, lambda: creak(rng), 3, rng, 0.25)
        buf = reverb(buf, 5.0, 0.7, 8)
    elif name == "tutorial":
        # entrenamiento: sala tranquila, ventilacion suave y un colchon muy grave
        buf += air(rng, 0.08, 250, 1500, 2) + hum(50, 0.03) + pad([36, 43], rng, 0.08, 220, 1)
        buf = reverb(buf, 2.5, 0.4, 9)
    buf = hp(buf, 25)
    buf = compress(buf / (np.max(np.abs(buf)) + 1e-9), 0.6, 2.5)
    buf = buf / (np.max(np.abs(buf)) + 1e-9) * 0.8
    # que el bucle encaje sin chasquido: fundido cruzado de 0,25 s del final con el principio
    f = int(SR * 0.25)
    x = np.linspace(0, 1, f)
    buf[:f] = buf[:f] * x + buf[-f:] * (1 - x)
    return buf


NAMES = ["start_corridor", "lab_storage", "lab_offices", "lab_morgue", "lab_generators", "lab_greenhouse", "final_room",
         "smiley_lab", "tutorial"]


def write_mono(path, x, ogg=True):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        if ogg:
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "0",
                            str(path)], check=True)
            Path(tmp.name).unlink()
        else:
            Path(tmp.name).replace(path)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    demo = []
    for name in NAMES:
        x = track(name)
        write_mono(OUT / f"{name}.ogg", x)
        demo.append(x[: SR * 12])
        print(name, f"{(OUT / f'{name}.ogg').stat().st_size // 1024} KB")
    if len(sys.argv) > 1:
        write_mono(Path(sys.argv[1]), np.concatenate(demo), ogg=sys.argv[1].endswith(".ogg"))


if __name__ == "__main__":
    main()
