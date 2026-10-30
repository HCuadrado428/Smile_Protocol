#!/usr/bin/env python3
"""
Musica del jefe (Smiley, docs/SMILERS.md): una nana de caja de musica deformada, la tension que
sube en cada fase y latidos. Tres bucles que encajan consigo mismos (fase 1, 2 y 3), estereo a
44,1 kHz en .ogg (la musica no se posiciona, asi que puede ser estereo).

  Fase 1: lenta y teatral. La caja de musica sola, con acordes y el mecanismo que cruje, la cinta
          que se estira, un coro de ninos lejano que tararea la nana en la segunda mitad, un
          zumbido grave que respira, latido lento y barridos al reves que anuncian cada frase.
  Fase 2: la caja empieza a fallar (notas que se caen, otra caja medio tono por debajo), pizzicato
          de cuerdas en corcheas, un reloj que hace tic-tac, respiraciones y susurros, rascadas
          metalicas y el latido mas rapido. El coro ya no canta: susurra.
  Fase 3: la nana una octava abajo, rota y saturada; tambores (bombo, golpes metalicos y toms),
          cuerdas graves en semicorcheas, violines que chillan en glissando, el coro gritando un
          acorde y el corazon desbocado.

    python3 tools/music.py [demo.wav]     # necesita ffmpeg con libvorbis
"""
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np
from scipy.signal import fftconvolve, butter, sosfilt

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
    return sosfilt(butter(order, min(fc, SR * 0.45), "low", fs=SR, output="sos"), x)


def hp(x, fc, order=2):
    return sosfilt(butter(order, fc, "high", fs=SR, output="sos"), x)


def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, min(hi, SR * 0.45)], "band", fs=SR, output="sos"), x)


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


# Instrumentos ------------------------------------------------------------------------------------------

def box_note(f, dur, rng, bright=1.0, vel=1.0):
    """Lamina de caja de musica: parciales inarmonicos que caen rapido, un clic de pua y la
    resonancia de la caja de madera."""
    n = int(SR * (dur + 2.4))
    tt = np.arange(n) / SR
    x = np.zeros(n)
    for ratio, g, d in ((1.0, 1.0, 1.7), (2.0, 0.18, 0.9), (3.0, 0.05, 0.4), (5.4, 0.26 * bright, 0.32),
                        (8.93, 0.11 * bright, 0.16), (13.3, 0.04 * bright, 0.07)):
        det = 1 + rng.normal(0, 0.0007)
        x += g * np.sin(2 * np.pi * f * ratio * det * tt + rng.uniform(0, 6.28)) * np.exp(-tt / d)
    k = 200
    x[:k] += rng.uniform(-1, 1, k) * np.exp(-np.arange(k) / 25) * 0.3
    body = bp(x, 180, 900, 1) * 0.25                    # la caja resuena en graves y medios
    return (x + body) * np.clip(tt / 0.0015, 0, 1) * vel


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


def saw(f, tt, rng):
    ph = (f * tt + rng.uniform()) % 1.0
    return 2 * ph - 1


def voice(f, dur, rng, vowel="u", vib=5.0, breath=0.15):
    """Una voz de nino tarareando: diente de sierra con vibrato filtrado por los formantes de la
    vocal, y algo de aire."""
    n = int(SR * dur)
    tt = np.arange(n) / SR
    vibr = 1 + 0.006 * np.sin(2 * np.pi * vib * tt + rng.uniform(0, 6)) * np.clip(tt / 0.6, 0, 1)
    ph = np.cumsum(f * vibr) / SR
    src = 2 * (ph % 1.0) - 1
    src = src + rng.normal(0, 1, n) * breath
    forms = {"u": ((320, 80), (800, 120), (2500, 200)), "a": ((750, 100), (1200, 130), (2600, 200)),
             "o": ((450, 80), (850, 110), (2500, 200))}[vowel]
    y = sum(bp(src, fc - bw, fc + bw, 2) * g for (fc, bw), g in zip(forms, (1.0, 0.6, 0.15)))
    env = np.clip(tt / 0.35, 0, 1) * np.clip((dur - tt) / 0.5, 0, 1)
    return y * env


def choir(n, beat, notes_beats, rng, vowel="u", voices=3, octave=0):
    """Coro: varias voces algo desafinadas cantando (nota, tiempos, empieza en tiempo)."""
    buf = np.zeros(n)
    for note, d, at in notes_beats:
        for v in range(voices):
            f = hz(note + 12 * octave) * 2 ** (rng.normal(0, 9) / 1200)
            add(buf, voice(f, d * beat + 0.3, rng, vowel), int((at * beat + rng.uniform(0, 0.05)) * SR))
    return buf


def heart(n, bpm, gain):
    """Latido doble (lub-dub), en bucle."""
    buf = np.zeros(n)
    m = int(SR * 0.25)
    tm = np.arange(m) / SR
    beat = np.zeros(int(SR * 0.6))
    for k0, g, f in ((0.0, 1.0, 48), (0.19, 0.65, 56)):
        k = int(k0 * SR)
        beat[k:k + m] += np.sin(2 * np.pi * f * tm * (1 - 0.3 * tm)) * np.exp(-tm / 0.06) * np.clip(tm / 0.003, 0, 1) * g
    beat = lp(beat, 200)
    step = SR * 60 / bpm
    k = 0
    while k * step < n:
        add(buf, beat * gain, int(k * step))
        k += 1
    return buf


def drone(n, f, gain, rng):
    """Zumbido grave que respira: el filtro se abre y se cierra despacio."""
    tt = np.arange(n) / SR
    x = saw(f, tt, rng) + saw(f * 1.003, tt, rng) * 0.8 + np.sin(2 * np.pi * f / 2 * tt) * 1.2
    breath = 0.5 + 0.5 * np.sin(np.arange(n) / n * 2 * np.pi * 4)
    lo = lp(x, 90)
    hi = lp(x, 400)
    y = lo + (hi - lo) * breath * 0.6
    y += lp(rng.normal(0, 1, n), 80) * 2
    return y * gain


def reverse_swell(dur, rng, f0=None):
    """Barrido al reves (ruido y una nota con reverb, dados la vuelta) que acaba en seco."""
    n = int(SR * dur)
    tt = np.arange(n) / SR
    x = bp(rng.normal(0, 1, n), 300, 5000, 1) * np.exp(-tt / 0.35)
    if f0:
        x += np.sin(2 * np.pi * f0 * tt) * np.exp(-tt / 0.6) * 0.6
    x = reverb(x, 2.0, 0.85, 99, loop=False)
    return x[::-1] * np.linspace(0, 1, n) ** 2


def tick(rng, hi=True):
    m = int(SR * 0.04)
    tm = np.arange(m) / SR
    return bp(rng.normal(0, 1, m), 2500 if hi else 1200, 7000 if hi else 3000, 2) * np.exp(-tm / 0.006)


def kick():
    m = int(SR * 0.5)
    tm = np.arange(m) / SR
    f = 45 + 110 * np.exp(-tm / 0.03)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tm / 0.18) * 1.2


def metal_hit(rng):
    """Golpe de chapa: ruido y parciales metalicos inarmonicos."""
    m = int(SR * 1.2)
    tm = np.arange(m) / SR
    x = sum(np.sin(2 * np.pi * f * tm) * g for f, g in ((182, 0.5), (437, 0.4), (771, 0.3), (1290, 0.2), (2113, 0.12)))
    x = x * np.exp(-tm / 0.35) + bp(rng.normal(0, 1, m), 900, 6000, 1) * np.exp(-tm / 0.05) * 1.5
    return x * 0.6


def tom(f):
    m = int(SR * 0.6)
    tm = np.arange(m) / SR
    ff = f * (1 + 0.6 * np.exp(-tm / 0.04))
    return np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-tm / 0.22)


def pizz(f, rng):
    m = int(SR * 0.5)
    tm = np.arange(m) / SR
    x = saw(f, tm, rng) * 0.6 + np.sin(2 * np.pi * f * tm)
    return lp(x, 1400) * np.exp(-tm / 0.09)


def reverb(x, secs, mix, seed, loop=True, predelay=0.025):
    rng = np.random.default_rng(seed)
    m = int(SR * secs)
    ir = rng.normal(0, 1, m) * np.exp(-np.arange(m) / SR / (secs / 5))
    ir = lp(ir, 4500)
    # reflexiones tempranas de una nave de hormigon
    for d, g in ((0.011, 0.6), (0.023, 0.45), (0.037, 0.35), (0.052, 0.3), (0.071, 0.22)):
        k = int(d * SR)
        if k < m:
            ir[k] += g * 8 * rng.choice((-1, 1))
    pd = int(predelay * SR)
    ir = np.concatenate([np.zeros(pd), ir])
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x)
    if loop:
        wet = fftconvolve(np.concatenate([x, x]), ir)[n:2 * n]      # cola en bucle
    else:
        wet = fftconvolve(x, ir)[:n]
    return x * (1 - mix) + wet * mix


def compress(x, thresh=0.5, ratio=3.0):
    """Compresor suave por envolvente (para que la mezcla respire junta)."""
    env = np.abs(x)
    env = lp(env, 8, 1)
    gain = np.where(env > thresh, (thresh + (env - thresh) / ratio) / (env + 1e-9), 1.0)
    return x * gain


# Composicion ------------------------------------------------------------------------------------------

def melody_notes(beat):
    t = 0.0
    out = []
    for note, d in MELODY:
        if note is not None:
            out.append((note, d, t))
        t += d
    return out


def render_box(n, beat, octave, rng, bright=1.0, swing=0.0, drop=0.0, chords=True, glitch=0.0):
    """La caja de musica: melodia y, en el primer tiempo de cada compas, la tercera de debajo."""
    buf = np.zeros(n)
    for note, d, t in melody_notes(beat):
        if rng.random() < drop:                          # el mecanismo se salta una nota
            continue
        late = rng.uniform(0, swing) * beat
        vel = 0.75 + 0.3 * rng.random()
        f = hz(note + 12 * octave)
        if rng.random() < glitch:
            f *= 2 ** (rng.choice((-1, 1)) * rng.uniform(0.3, 1.0) / 12)
        add(buf, box_note(f, d * beat, rng, bright, vel), int((t * beat + late) * SR))
        if chords and abs(t % 3) < 1e-6:
            bar = int(t // 3) % len(CHORDS)
            low = CHORDS[bar][1] + 12 * (octave + 1)
            add(buf, box_note(hz(low), d * beat, rng, bright * 0.7, vel * 0.45), int((t * beat + late + 0.012) * SR))
    # el mecanismo: cric-cric de los dientes del cilindro
    for k in range(int(n / SR * 9)):
        if rng.random() < 0.6:
            add(buf, tick(rng) * 0.03, int(rng.uniform(0, n)))
    return buf


def phase(k):
    rng = np.random.default_rng(900 + k)
    bpm = (60, 66, 88)[k - 1]
    beat = 60 / bpm
    bars = len(CHORDS)
    n = int(round(SR * beat * 3 * bars))
    tt = np.arange(n) / SR
    L = np.zeros(n)
    R = np.zeros(n)
    half = n // 2

    def both(x, pan=0.0, width=0):
        nonlocal L, R
        L = L + x * (1 - max(0, pan))
        if width:
            x = np.roll(x, width)
        R = R + x * (1 + min(0, pan))

    # La nana
    if k == 1:
        box = wow(render_box(n, beat, 0, rng, swing=0.04), 20, 3, 7, rng)
        both(box * 0.5, -0.12, 120)
        # coro lejano tarareando en la segunda mitad (una octava abajo)
        notes = [(nt, d, t) for nt, d, t in melody_notes(beat) if t >= bars * 3 / 2]
        ch = choir(n, beat, notes, rng, "u", voices=3, octave=-1)
        ch = reverb(lp(ch, 2500), 4.5, 0.75, 301)
        both(ch * 0.28, 0.25, 600)
    elif k == 2:
        a = wow(render_box(n, beat, 0, rng, swing=0.06, drop=0.12, glitch=0.06), 32, 4, 14, rng)
        b = wow(render_box(n, beat, 0, rng, swing=0.1, drop=0.3, chords=False), 30, 5, 14, rng)
        b = np.interp(np.arange(n) * 2 ** (-60 / 1200) % n, np.arange(n), b, period=n)   # medio tono por debajo
        both(a * 0.48, -0.4)
        both(b * 0.26, 0.5)
    else:
        a = wow(render_box(n, beat, -1, rng, bright=1.6, swing=0.03, glitch=0.12), 55, 6, 25, rng)
        a = np.tanh(a * 4.5) * 0.6
        a = np.round(a * 40) / 40                                                 # triturada
        a = lp(a, 3200)
        both(a * 0.5, 0.0, 200)

    # zumbido grave
    both(drone(n, 55.0 if k < 3 else 41.2, (0.05, 0.065, 0.08)[k - 1], rng), 0, 300)

    # barridos al reves hacia el compas 1 y el 9
    for bar0 in (0, bars // 2):
        sw = reverse_swell((2.0, 1.6, 1.2)[k - 1], rng, hz(CHORDS[bar0][0] + 24))
        add(L, sw * 0.18, int(bar0 * 3 * beat * SR) - len(sw))
        add(R, sw[::1] * 0.18, int(bar0 * 3 * beat * SR) - len(sw) + 400)

    # cuerdas
    for bar, ch in enumerate(CHORDS):
        t0 = bar * 3 * beat
        if k == 1:
            m = int(SR * 3 * beat * 1.05)
            tm = np.arange(m) / SR
            x = sum(saw(hz(p - 12) * 2 ** (rng.normal(0, 6) / 1200), tm, rng) for p in ch[:2])
            x = lp(x, 300) * np.sin(np.pi * np.clip(tm / (3 * beat), 0, 1)) * 0.06
            add(L, x, int(t0 * SR))
            add(R, x, int(t0 * SR) + 300)
        elif k == 2:
            for s in range(6):
                p = ch[s % 3] - 12 if s % 2 == 0 else ch[0] - 12
                x = pizz(hz(p), rng) * (0.22 if s % 3 == 0 else 0.15)
                pan = -0.3 if s % 2 else 0.3
                add(L, x * (1 - max(0, pan)), int((t0 + s * beat / 2) * SR))
                add(R, x * (1 + min(0, pan)), int((t0 + s * beat / 2) * SR) + 150)
        else:
            for s in range(12):
                m = int(SR * beat * 0.24)
                tm = np.arange(m) / SR
                p = ch[0] - 12 + (12 if s % 6 == 5 else 0) + (1 if s % 4 == 3 else 0)
                x = lp(saw(hz(p), tm, rng) + saw(hz(p) * 1.007, tm, rng), 900) * np.exp(-tm / 0.07)
                x *= 0.15 if s % 3 == 0 else 0.1
                add(L, x, int((t0 + s * beat / 4) * SR))
                add(R, x, int((t0 + s * beat / 4) * SR) + 220)

    # latidos
    both(heart(n, (50, 82, 136)[k - 1], (0.32, 0.4, 0.5)[k - 1]))

    if k == 2:
        # reloj: tic-tac en cada tiempo, uno mas agudo que otro
        for b in range(bars * 3):
            add(L if b % 2 else R, tick(rng, b % 2 == 0) * 0.08, int(b * beat * SR))
        # respiraciones y susurros que pasan de un lado a otro
        for j in range(6):
            m = int(SR * rng.uniform(0.6, 1.4))
            tm = np.linspace(0, 1, m)
            x = bp(rng.normal(0, 1, m), rng.uniform(700, 1500), 3000, 2) * np.sin(np.pi * tm) ** 2
            x *= 0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(4, 8) * tm * m / SR)
            at = int(rng.uniform(0, n))
            pan = rng.uniform(-0.9, 0.9)
            add(L, x * 0.07 * (1 - max(0, pan)), at)
            add(R, x * 0.07 * (1 + min(0, pan)), at + 200)
    if k >= 2:
        # rascadas metalicas (una lamina arrastrada)
        for j in range(4 if k == 2 else 7):
            at = rng.uniform(0, n / SR)
            m = int(SR * rng.uniform(0.8, 1.8))
            tm = np.arange(m) / SR
            f0 = rng.uniform(900, 1800)
            x = bp(rng.normal(0, 1, m), f0, f0 * 1.25, 2) * np.sin(np.pi * tm / tm[-1]) ** 2
            x *= 0.6 + 0.4 * np.sin(2 * np.pi * rng.uniform(9, 17) * tm)
            add(L if j % 2 else R, x * 0.1, int(at * SR))
    if k == 3:
        # tambores: bombo en el 1, chapa en el 3, toms que caen al final de cada 4 compases
        for b in range(bars * 3):
            at = int(b * beat * SR)
            if b % 3 == 0:
                add(L, kick() * 0.5, at)
                add(R, kick() * 0.5, at)
            if b % 3 == 2:
                mh = reverb(metal_hit(rng), 1.5, 0.4, 500 + b, loop=False)
                add(L, mh * 0.18, at)
                add(R, mh * 0.18, at + 300)
            if b % 12 == 11:
                for j, f in enumerate((140, 110, 85)):
                    t_ = tom(f) * 0.35
                    add(L, t_, at + int(j * beat / 3 * SR))
                    add(R, t_, at + int(j * beat / 3 * SR) + 100)
        # charles en semicorcheas
        for s in range(bars * 12):
            add(L if s % 2 else R, tick(rng) * (0.05 if s % 4 else 0.08), int(s * beat / 4 * SR))
        # violines que chillan: glissandos agudos cada 4 compases
        for ph4 in range(4):
            t0 = ph4 * 4 * 3 * beat + 2 * 3 * beat
            m = int(SR * 3 * beat * 2)
            tm = np.arange(m) / SR
            f = hz(93) * 2 ** ((np.sin(np.pi * tm / tm[-1]) * 3 - 1) / 12) * (1 + 0.01 * np.sin(2 * np.pi * 6.5 * tm))
            x = sum(2 * ((np.cumsum(f * d) / SR) % 1.0) - 1 for d in (1.0, 1.006, 0.994))
            x = bp(x, 1200, 6000, 2) * np.sin(np.pi * tm / tm[-1]) ** 1.5
            add(L, x * 0.035, int(t0 * SR))
            add(R, x * 0.035, int(t0 * SR) + 500)
        # el coro grita un acorde disonante en los compases 1 y 9
        for bar0 in (0, bars // 2):
            for nt in (69, 70, 76):
                add(L, voice(hz(nt), 3 * beat * 2, rng, "a", vib=7, breath=0.3) * 0.05, int(bar0 * 3 * beat * SR))
                add(R, voice(hz(nt) * 1.004, 3 * beat * 2, rng, "a", vib=6.5, breath=0.3) * 0.05,
                    int(bar0 * 3 * beat * SR) + 250)

    L = reverb(L, (3.8, 3.2, 2.4)[k - 1], (0.42, 0.38, 0.3)[k - 1], 11 + k)
    R = reverb(R, (3.8, 3.2, 2.4)[k - 1], (0.42, 0.38, 0.3)[k - 1], 21 + k)
    L, R = hp(L, 28), hp(R, 28)
    # algo mas de anchura (medio/lados)
    mid, side = (L + R) / 2, (L - R) / 2
    side *= 0.8
    L, R = mid + side, mid - side
    st = np.stack([L, R], 1)
    st /= np.max(np.abs(st)) + 1e-9
    st = np.stack([compress(st[:, 0], 0.45, 2.5), compress(st[:, 1], 0.45, 2.5)], 1)
    st = np.tanh(st / (np.max(np.abs(st)) + 1e-9) * 1.3)
    st = st / (np.max(np.abs(st)) + 1e-9) * (0.62, 0.78, 0.92)[k - 1]
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
        parts = [np.concatenate([st, st]) for st in phases]
        f = int(SR * 1.5)
        out = parts[0]
        for p in parts[1:]:
            x = np.linspace(0, 1, f)[:, None]
            out = np.concatenate([out[:-f], out[-f:] * (1 - x) + p[:f] * x, p[f:]])
        write(Path(sys.argv[1]), out, ogg=False)


if __name__ == "__main__":
    main()
