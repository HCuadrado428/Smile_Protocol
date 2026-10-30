#!/usr/bin/env python3
"""
Musica del jefe (Smiley, docs/SMILERS.md): una nana de caja de musica deformada, la tension que
sube en cada fase y latidos. Tres bucles que encajan consigo mismos (fase 1, 2 y 3), estereo a
44,1 kHz en .ogg (la musica no se posiciona, asi que puede ser estereo).

  Fase 1: lenta y teatral. La caja de musica sola, un poco desafinada (cinta que se estira),
          un zumbido grave y un latido lento y lejano.
  Fase 2: la nana se dobla con otra caja medio tono desafinada, pulso de cuerdas graves en
          corcheas, rascadas metalicas y el latido mas rapido.
  Fase 3: la nana una octava abajo y rota (saturada y triturada), golpes graves en cada
          tiempo, un racimo de cuerdas agudas que chilla y el corazon desbocado.

    python3 tools/music.py [demo.wav]     # necesita ffmpeg con libvorbis
"""
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np
from scipy.signal import fftconvolve, lfilter, butter, sosfilt

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/sounds/music"

# La nana (original), 3/4, la menor: (nota MIDI, tiempos); None es silencio
MELODY = [
    (76, 2), (72, 1), (69, 2), (71, 1), (72, 1), (74, 1), (76, 1), (74, 3),
    (72, 2), (69, 1), (65, 2), (64, 1), (69, 1), (68, 1), (71, 1), (69, 3),
    (76, 2), (77, 1), (76, 2), (72, 1), (74, 1), (72, 1), (71, 1), (68, 3),
    (69, 2), (72, 1), (71, 2), (64, 1), (65, 1), (64, 1), (63, 1), (64, 2), (None, 1),
]
# un acorde por compas (graves)
CHORDS = [(45, 48, 52), (45, 48, 52), (45, 48, 52), (38, 50, 53), (45, 48, 52), (41, 48, 53), (40, 47, 52), (45, 48, 52),
          (45, 48, 52), (45, 48, 52), (38, 50, 53), (40, 47, 52), (41, 48, 53), (40, 47, 52), (38, 50, 53), (40, 47, 56)]


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def lp(x, fc, order=2):
    return sosfilt(butter(order, fc, "low", fs=SR, output="sos"), x)


def hp(x, fc, order=2):
    return sosfilt(butter(order, fc, "high", fs=SR, output="sos"), x)


def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], "band", fs=SR, output="sos"), x)


def add(buf, x, at):
    """Suma x en buf desde la muestra at; lo que se pasa del final vuelve al principio (bucle)."""
    n = len(buf)
    at %= n
    end = min(n, at + len(x))
    buf[at:end] += x[:end - at]
    rest = x[end - at:]
    while len(rest):
        k = min(n, len(rest))
        buf[:k] += rest[:k]
        rest = rest[k:]


def box_note(f, dur, rng, bright=1.0):
    """Lamina de caja de musica: parciales inarmonicos que caen rapido y un clic de puas."""
    n = int(SR * (dur + 2.2))
    tt = np.arange(n) / SR
    x = np.zeros(n)
    for ratio, g, d in ((1.0, 1.0, 1.6), (2.0, 0.22, 0.9), (5.4, 0.28 * bright, 0.35), (8.93, 0.12 * bright, 0.18)):
        ph = rng.uniform(0, 6.28)
        x += g * np.sin(2 * np.pi * f * ratio * tt + ph) * np.exp(-tt / d)
    click = rng.uniform(-1, 1, 160) * np.exp(-np.arange(160) / 30) * 0.25
    x[:160] += click
    return x * np.clip(tt / 0.002, 0, 1)


def wow(x, depth_cents, cycles, flutter=0.0, rng=None):
    """Cinta estirada: la altura sube y baja despacio (ciclos enteros, asi el bucle encaja)."""
    n = len(x)
    ph = np.arange(n) / n * 2 * np.pi
    rate = 2 ** (depth_cents * np.sin(ph * cycles) / 1200)
    if flutter and rng is not None:
        fl = lp(rng.normal(0, 1, n), 6, 1)
        fl = fl / (np.max(np.abs(fl)) + 1e-9)
        rate = rate * 2 ** (flutter * fl / 1200)
    rate *= n / np.sum(rate)
    idx = np.cumsum(rate) - rate[0]
    return np.interp(idx % n, np.arange(n), x, period=n)


def heart(n, bpm, gain, every=1):
    """Latido doble (lub-dub) cada pulso, en bucle."""
    buf = np.zeros(n)
    m = int(SR * 0.25)
    tm = np.arange(m) / SR
    beat = np.zeros(int(SR * 0.6))
    for k0, g, f in ((0.0, 1.0, 48), (0.2, 0.65, 55)):
        k = int(k0 * SR)
        beat[k:k + m] += np.sin(2 * np.pi * f * tm * (1 - 0.3 * tm)) * np.exp(-tm / 0.06) * np.clip(tm / 0.003, 0, 1) * g
    beat = lp(beat, 220)
    step = SR * 60 / bpm * every
    k = 0
    while k * step < n:
        add(buf, beat * gain, int(k * step))
        k += 1
    return buf


def drone(n, f, gain, rng):
    tt = np.arange(n) / SR
    x = np.sin(2 * np.pi * f * tt) + 0.6 * np.sin(2 * np.pi * (f * 1.004) * tt) + 0.3 * np.sin(2 * np.pi * f * 2.003 * tt)
    x += lp(rng.normal(0, 1, n), 120) * 3
    swell = 0.6 + 0.4 * np.sin(np.arange(n) / n * 2 * np.pi * 2)
    return x * swell * gain


def saw(f, tt, rng):
    ph = (f * tt + rng.uniform()) % 1.0
    return 2 * ph - 1


def reverb(x, secs, mix, seed):
    rng = np.random.default_rng(seed)
    m = int(SR * secs)
    ir = rng.normal(0, 1, m) * np.exp(-np.arange(m) / SR / (secs / 5))
    ir = lp(ir, 5000)
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x)
    wet = fftconvolve(np.concatenate([x, x]), ir)[n:2 * n]     # cola en bucle
    return x * (1 - mix) + wet * mix


def render_melody(n, beat, octave, rng, bright=1.0, swing=0.0):
    buf = np.zeros(n)
    t = 0.0
    for note, d in MELODY:
        if note is not None:
            late = rng.uniform(0, swing) * beat
            add(buf, box_note(hz(note + 12 * octave), d * beat, rng, bright), int((t + late) * SR))
        t += d * beat
    return buf


def phase(k):
    rng = np.random.default_rng(700 + k)
    bpm = (60, 66, 88)[k - 1]
    beat = 60 / bpm
    bars = len(CHORDS)
    n = int(round(SR * beat * 3 * bars))
    tt = np.arange(n) / SR
    L = np.zeros(n)
    R = np.zeros(n)

    def both(x, pan=0.0):
        nonlocal L, R
        L = L + x * (1 - max(0, pan))
        R = R + x * (1 + min(0, pan))

    # la nana
    if k == 1:
        mel = render_melody(n, beat, 0, rng, swing=0.04)
        mel = wow(mel, 22, 3, 8, rng)
        both(mel * 0.55, -0.15)
    elif k == 2:
        a = wow(render_melody(n, beat, 0, rng, swing=0.05), 30, 4, 12, rng)
        b = wow(render_melody(n, beat, 0, rng, swing=0.08), 30, 5, 12, rng)
        b = np.interp(np.arange(n) * 2 ** (55 / 1200) % n, np.arange(n), b, period=n)   # medio tono roto
        both(a * 0.5, -0.4)
        both(b * 0.3, 0.45)
    else:
        a = wow(render_melody(n, beat, -1, rng, bright=1.6, swing=0.03), 60, 6, 25, rng)
        a = np.tanh(a * 4.0) * 0.6
        a = np.round(a * 48) / 48                                                       # triturada
        a = lp(a, 3000)
        both(a * 0.6, 0.0)

    # zumbido grave
    both(drone(n, 55.0 if k < 3 else 41.2, (0.05, 0.07, 0.09)[k - 1], rng))

    # cuerdas graves: notas largas del acorde (fase 1), corcheas (fase 2 y 3)
    for bar, ch in enumerate(CHORDS):
        t0 = bar * 3 * beat
        if k == 1:
            m = int(SR * 3 * beat * 1.05)
            tm = np.arange(m) / SR
            x = sum(saw(hz(p - 12), tm, rng) for p in ch[:2])
            x = lp(x, 260) * np.sin(np.pi * np.clip(tm / (3 * beat), 0, 1)) * 0.07
            add(L, x, int(t0 * SR))
            add(R, x, int(t0 * SR) + 300)
        else:
            sub = 6 if k == 2 else 6
            for s in range(sub):
                m = int(SR * beat * 0.4)
                tm = np.arange(m) / SR
                p = ch[0] - 12 + (12 if s % 3 == 2 and k == 3 else 0)
                x = lp(saw(hz(p), tm, rng) + saw(hz(p) * 1.006, tm, rng), 400 if k == 2 else 700)
                x *= np.exp(-tm / 0.12) * (0.16 if k == 2 else 0.2) * (1.0 if s % 3 == 0 else 0.7)
                add(L, x, int((t0 + s * beat / 2) * SR))
                add(R, x, int((t0 + s * beat / 2) * SR) + 200)

    # latidos
    both(heart(n, (50, 80, 132)[k - 1], (0.35, 0.45, 0.55)[k - 1]))

    if k >= 2:
        # rascadas metalicas (como una lamina arrastrada)
        for j in range(4 if k == 2 else 8):
            at = rng.uniform(0, n / SR)
            m = int(SR * rng.uniform(0.8, 1.8))
            tm = np.arange(m) / SR
            f0 = rng.uniform(900, 1800)
            nz = rng.normal(0, 1, m)
            x = bp(nz, f0, f0 * 1.25, 2) * np.sin(np.pi * tm / tm[-1]) ** 2
            x *= 0.6 + 0.4 * np.sin(2 * np.pi * rng.uniform(9, 17) * tm)
            add(L if j % 2 else R, x * 0.12, int(at * SR))
    if k == 3:
        # golpes graves en cada tiempo (mas fuerte en el primero)
        for b in range(bars * 3):
            m = int(SR * 0.5)
            tm = np.arange(m) / SR
            x = np.sin(2 * np.pi * 52 * tm * (1 - 0.4 * tm)) * np.exp(-tm / 0.11)
            x += lp(rng.normal(0, 1, m), 900) * np.exp(-tm / 0.03) * 0.6
            x *= 0.55 if b % 3 == 0 else 0.3
            add(L, x, int(b * beat * SR))
            add(R, x, int(b * beat * SR))
        # racimo agudo que chilla con tremolo y crece cada 4 compases
        cl = sum(saw(hz(p), tt, rng) for p in (93, 94, 98, 99))
        cl = bp(cl, 1500, 5000, 2)
        trem = 0.6 + 0.4 * np.sin(2 * np.pi * 7.5 * tt)
        grow = (np.arange(n) % (n // 4)) / (n // 4)
        both(cl * trem * grow ** 2 * 0.03, 0.3)

    L = reverb(L, (3.5, 3.0, 2.2)[k - 1], (0.45, 0.4, 0.3)[k - 1], 11 + k)
    R = reverb(R, (3.5, 3.0, 2.2)[k - 1], (0.45, 0.4, 0.3)[k - 1], 21 + k)
    st = np.stack([hp(L, 30), hp(R, 30)], 1)
    st = np.tanh(st / (np.max(np.abs(st)) + 1e-9) * 1.4) * (0.6, 0.75, 0.9)[k - 1]
    return st


def write(path, st, ogg=True):
    pcm = (np.clip(st, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        if ogg:
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "5",
                            str(path)], check=True)
            Path(tmp.name).unlink()
        else:
            Path(tmp.name).replace(path)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    phases = [phase(k) for k in (1, 2, 3)]
    for k, st in enumerate(phases, 1):
        write(OUT / f"smiley_phase{k}.ogg", st)
        print(f"fase {k}: {len(st) / SR:.1f} s")
    if len(sys.argv) > 1:
        # demo: cada fase dos veces seguidas con un fundido corto entre fases
        parts = []
        for st in phases:
            parts.append(np.concatenate([st, st]))
        f = int(SR * 1.5)
        out = parts[0]
        for p in parts[1:]:
            x = np.linspace(0, 1, f)[:, None]
            out = np.concatenate([out[:-f], out[-f:] * (1 - x) + p[:f] * x, p[f:]])
        write(Path(sys.argv[1]), out, ogg=False)


if __name__ == "__main__":
    main()
