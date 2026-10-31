#!/usr/bin/env python3
"""
Musica de cada planta: bucles de ambiente muy bajos (no melodias), uno por planta, que se funden al
cambiar de planta y se callan cuando suena el jefe. Cada uno tiene su caracter:

  start_corridor   recepcion: notas sueltas de piano apagado sobre un zumbido grave
  lab_storage      almacen: golpes de chapa lejanos con mucho eco y un tic de maquinaria
  lab_offices      oficinas: trozos de la nana en una caja de musica desafinada y teclas de maquina
  lab_morgue       morgue: coro frio sostenido (racimo de notas) y un latido muy debil
  lab_generators   energia: zumbido electrico de 50 Hz que late y tambores graves lentos
  lab_greenhouse   jardin: gotas que caen, viento y un colchon aireado
  final_room       criogenia: cristales agudos y barridos al reves
  smiley_lab       laboratorio cero (antes de que despierte): pulso grave y la nana muy lejos
  tutorial         entrenamiento: colchon tranquilo, casi neutro

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


def track(name):
    rng = np.random.default_rng(abs(hash(name)) % 10 ** 6 if False else sum(map(ord, name)) * 7919)
    buf = np.zeros(N)
    if name == "start_corridor":
        buf += drone(N, hz(33), 0.05, rng)
        scale = [57, 60, 64, 65, 52, 55]
        for k in range(10):
            f = hz(rng.choice(scale) - 12)
            add(buf, lp(box_note(f, 3.0, rng, bright=0.2), 1200) * 0.35, int((k * 4.8 + rng.uniform(0, 1.5)) * SR))
        buf = reverb(buf, 3.0, 0.5, 1)
    elif name == "lab_storage":
        buf += drone(N, hz(31), 0.06, rng)
        scatter(buf, lambda: lp(metal_hit(rng), 2200), 6, rng, 0.35)
        for k in range(int(SECS / 1.5)):
            add(buf, tick(rng, hi=False) * 0.12, int(k * 1.5 * SR))
        buf = reverb(buf, 4.0, 0.6, 2)
    elif name == "lab_offices":
        buf += pad([45, 52], rng, 0.18, 600, 2)
        beat = 0.62
        at = 0.0
        for k, (m, d) in enumerate(MELODY[:14]):
            if m is not None and rng.uniform() < 0.7:
                f = hz(m) * 2 ** (rng.normal(0, 25) / 1200)
                add(buf, box_note(f, d * beat, rng, bright=0.6) * 0.22, int((4 + at) * SR))
            at += d * beat
        scatter(buf, lambda: tick(rng) * 0.6, 40, rng, 0.25)
        buf = wow(buf, 30, 2, 8, rng)
        buf = reverb(buf, 2.5, 0.45, 3)
    elif name == "lab_morgue":
        for m, at in ((57, 0), (58, 6), (62, 12), (63, 20), (57, 28), (60, 36)):
            add(buf, voice(hz(m), 9.0, rng, "u", vib=4.0, breath=0.08) * 0.25, int(at * SR))
        buf += heart(N, 46, 0.12)
        buf += pad([33, 40], rng, 0.12, 300, 3)
        buf = reverb(buf, 4.5, 0.6, 4)
    elif name == "lab_generators":
        tt = np.arange(N) / SR
        hum = sum(np.sin(2 * np.pi * 50 * k * tt) / k for k in (1, 2, 3, 5, 7))
        throb = 0.6 + 0.4 * np.sin(np.arange(N) / N * 2 * np.pi * 24)
        buf += lp(hum, 600) * throb * 0.12
        for k in range(int(SECS / 2.4)):
            if k % 4 != 3:
                add(buf, tom(hz(31 + (k % 2) * 3)) * 0.35, int(k * 2.4 * SR))
        scatter(buf, lambda: tick(rng) * 0.5, 30, rng, 0.2)
        buf = reverb(buf, 3.0, 0.45, 5)
    elif name == "lab_greenhouse":
        buf += wind(rng, 0.05)
        buf += pad([50, 57, 64], rng, 0.15, 1400, 2, detune=14)
        scatter(buf, lambda: drop(rng, rng.uniform(900, 1700)), 26, rng, 0.25)
        buf = reverb(buf, 3.5, 0.55, 6)
    elif name == "final_room":
        buf += pad([69, 70, 76], rng, 0.08, 3000, 4, detune=20)
        scatter(buf, lambda: glass(rng, hz(rng.choice([81, 84, 86, 88, 91]))), 9, rng, 0.18)
        for at in (10, 30):
            add(buf, reverse_swell(3.0, rng, hz(69)) * 0.35, int((at - 3.0) * SR))
        buf += drone(N, hz(28), 0.04, rng)
        buf = reverb(buf, 5.0, 0.6, 7)
    elif name == "smiley_lab":
        buf += heart(N, 38, 0.08)
        buf += drone(N, hz(26), 0.07, rng)
        beat = 1.1
        at = 6.0
        for m, d in MELODY[:10]:
            if m is not None:
                add(buf, lp(box_note(hz(m), d * beat, rng, bright=0.3), 900) * 0.12, int(at * SR))
            at += d * beat
        buf = reverb(buf, 5.0, 0.7, 8)
    elif name == "tutorial":
        buf += pad([48, 55, 60, 64], rng, 0.2, 800, 2, detune=6)
        buf += pad([36], rng, 0.15, 200, 1)
        buf = reverb(buf, 3.0, 0.4, 9)
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
