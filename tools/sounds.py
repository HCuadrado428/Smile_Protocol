#!/usr/bin/env python3
"""
Sonidos provisionales generados por sintesis (README 20.2, fase 11): pasos, respiracion,
chasquidos de escucha, olfateo, el grito del aviso y el golpe del Berserker, mas el quejido del
derribado y el agarre. Mono a 44,1 kHz (Minecraft solo posiciona los sonidos mono) en .ogg.

    python3 tools/sounds.py          # necesita ffmpeg con libvorbis
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/smileprotocol/sounds"


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, a=0.005, d=0.3, curve=3.0):
    """Envolvente de ataque rapido y caida exponencial."""
    x = np.arange(n) / SR
    att = np.clip(x / max(a, 1e-4), 0, 1)
    return att * np.exp(-x / max(d, 1e-4) * curve / 3)


def lowpass(x, fc):
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def highpass(x, fc):
    return x - lowpass(x, fc)


def bandpass(x, f0, q=4.0):
    """Biquad pasa banda (RBJ)."""
    w = 2 * np.pi * f0 / SR
    al = np.sin(w) / (2 * q)
    b0, b1, b2 = al, 0, -al
    a0, a1, a2 = 1 + al, -2 * np.cos(w), 1 - al
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        v = (b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, x[i], y1, v
        y[i] = v
    return y


def noise(n, rng):
    return rng.uniform(-1, 1, n)


def norm(x, peak=0.9):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def save(name, x):
    OUT.joinpath(name).parent.mkdir(parents=True, exist_ok=True)
    x = norm(x)
    # fundidos para que no chasquee
    f = min(len(x) // 10, int(SR * 0.01))
    x[:f] *= np.linspace(0, 1, f)
    x[-f:] *= np.linspace(1, 0, f)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "4",
                        str(OUT / f"{name}.ogg")], check=True)
        Path(tmp.name).unlink()


# Sonidos ---------------------------------------------------------------------------------------------

def step(seed):
    """Pisada pesada y descalza: golpe sordo, carne y un crujido de hueso."""
    r = np.random.default_rng(seed)
    n = int(SR * 0.45)
    tt = np.arange(n) / SR
    thud = np.sin(2 * np.pi * (48 + r.uniform(-6, 6)) * tt * (1 - 0.3 * tt)) * env(n, 0.003, 0.12)
    flesh = lowpass(noise(n, r), 900) * env(n, 0.001, 0.05) * 1.2
    crack = np.zeros(n)
    k = int(SR * r.uniform(0.02, 0.06))
    crack[k:k + 400] = highpass(noise(400, r), 2500) * np.linspace(1, 0, 400) * 0.6
    return thud * 1.4 + flesh + crack


def breath(seed):
    """Respiracion humeda y ronca: inspira, pausa, espira con gorgoteo."""
    r = np.random.default_rng(seed)
    out = []
    for dur, gain, f0 in ((1.1, 0.7, 700), (0.25, 0.0, 500), (1.4, 1.0, 420)):
        n = int(SR * dur)
        e = np.sin(np.linspace(0, np.pi, n)) ** 1.5 * gain
        air = bandpass(noise(n, r), f0, 1.2)
        tt = np.arange(n) / SR
        growl = np.sign(np.sin(2 * np.pi * (62 + 6 * np.sin(tt * 7)) * tt)) * 0.12 * gain
        wet = (np.sin(2 * np.pi * 13 * tt) > 0.6) * bandpass(noise(n, r), 300, 3) * 0.4
        out.append((air + lowpass(growl, 400) + wet) * e)
    return np.concatenate(out)


def listen():
    """Chasquidos de dientes, como si escuchara: click-click... click."""
    r = np.random.default_rng(31)
    x = np.zeros(int(SR * 1.6))
    times = [0.0, 0.09, 0.18, 0.55, 0.62, 1.1, 1.17, 1.24, 1.31]
    for tt in times:
        k = int(tt * SR)
        n = 900
        c = bandpass(noise(n, r), r.uniform(1800, 2600), 6) * env(n, 0.0005, 0.01)
        x[k:k + n] += c
    tt = np.arange(len(x)) / SR
    x += lowpass(noise(len(x), r), 120) * 0.15 * (0.5 + 0.5 * np.sin(tt * 4))
    return x


def sniff(seed):
    r = np.random.default_rng(seed)
    x = np.zeros(int(SR * 1.2))
    for i, tt in enumerate((0.0, 0.18, 0.36)):
        n = int(SR * 0.14)
        k = int(tt * SR)
        s = bandpass(noise(n, r), 1400 + i * 200, 1.5) * np.sin(np.linspace(0, np.pi, n)) ** 2
        x[k:k + n] += s
    return x


def roar():
    """El aviso del golpe: chillido que sube, ronco y distorsionado."""
    r = np.random.default_rng(41)
    n = int(SR * 0.9)
    tt = np.arange(n) / SR
    f = 110 + 260 * (tt / tt[-1]) ** 0.7 + 8 * np.sin(2 * np.pi * 9 * tt)
    ph = 2 * np.pi * np.cumsum(f) / SR
    saw = 2 * ((ph / (2 * np.pi)) % 1) - 1
    voice = sum(bandpass(saw, fm, 5) * g for fm, g in ((700, 1.0), (1150, 0.7), (2600, 0.35)))
    rasp = bandpass(noise(n, r), 1600, 0.8) * 0.5
    e = np.clip(tt / 0.08, 0, 1) * np.clip((tt[-1] - tt) / 0.15, 0, 1)
    x = (voice + rasp) * e
    return np.tanh(norm(x) * 3.0)


def impact():
    """Golpe contra el suelo: estampido grave, polvo y cascotes."""
    r = np.random.default_rng(51)
    n = int(SR * 1.4)
    tt = np.arange(n) / SR
    boom = np.sin(2 * np.pi * (60 * np.exp(-tt * 3) + 28) * tt) * env(n, 0.002, 0.45)
    hit = lowpass(noise(n, r), 2500) * env(n, 0.001, 0.08) * 1.5
    debris = np.zeros(n)
    for _ in range(26):
        k = int(r.uniform(0.05, 1.0) * SR)
        m = 600
        if k + m < n:
            debris[k:k + m] += highpass(noise(m, r), 3000) * env(m, 0.0005, 0.01) * r.uniform(0.1, 0.4)
    return np.tanh((boom * 1.6 + hit + debris) * 1.5)


def groan(seed):
    """Quejido del derribado: voz grave entrecortada."""
    r = np.random.default_rng(seed)
    n = int(SR * 1.3)
    tt = np.arange(n) / SR
    f = 105 - 20 * tt + 3 * np.sin(2 * np.pi * 5 * tt)
    ph = 2 * np.pi * np.cumsum(f) / SR
    pulse = (np.sin(ph) > 0.7).astype(float) - 0.2
    voice = bandpass(pulse, 500, 3) + bandpass(pulse, 900, 4) * 0.6 + bandpass(noise(n, r), 700, 1) * 0.15
    e = np.sin(np.linspace(0, np.pi, n)) ** 0.8 * (0.7 + 0.3 * np.sin(2 * np.pi * 3 * tt))
    return voice * e


def grab():
    """Agarre: golpe seco y un gruñido corto encima."""
    r = np.random.default_rng(61)
    n = int(SR * 0.7)
    tt = np.arange(n) / SR
    hit = lowpass(noise(n, r), 1800) * env(n, 0.001, 0.06) * 1.5
    g = np.sign(np.sin(2 * np.pi * (80 + 40 * tt) * tt)) * env(n, 0.02, 0.4) * 0.5
    return hit + lowpass(g, 700)


def heartbeat():
    """Latido fuerte (derribado y QTE)."""
    n = int(SR * 0.9)
    tt = np.arange(n) / SR
    x = np.zeros(n)
    for k0, g in ((0.0, 1.0), (0.22, 0.7)):
        k = int(k0 * SR)
        m = int(SR * 0.2)
        tm = np.arange(m) / SR
        x[k:k + m] += np.sin(2 * np.pi * 45 * tm) * env(m, 0.004, 0.08) * g
    return lowpass(x, 300)


def crunch(seed):
    """Remate: golpe carnoso y crujido de huesos que se astillan."""
    r = np.random.default_rng(seed)
    n = int(SR * 0.8)
    tt = np.arange(n) / SR
    thud = np.sin(2 * np.pi * (70 * np.exp(-tt * 8) + 38) * tt) * env(n, 0.001, 0.12) * 1.4
    flesh = lowpass(noise(n, r), 1200) * env(n, 0.001, 0.07) * 1.3
    bones = np.zeros(n)
    k = int(SR * 0.015)
    for _ in range(9):
        m = int(SR * r.uniform(0.004, 0.012))
        if k + m < n:
            bones[k:k + m] += highpass(noise(m, r), 1800) * np.linspace(1, 0, m) * r.uniform(0.5, 1.0)
        k += int(SR * r.uniform(0.008, 0.03))
    wet = bandpass(noise(n, r), 600, 2) * env(n, 0.02, 0.25) * 0.5
    return np.tanh((thud + flesh + bones + wet) * 1.4)


def bite():
    """Mordisco: chasquido de dientes, desgarro humedo y un gorgoteo."""
    r = np.random.default_rng(91)
    n = int(SR * 1.0)
    tt = np.arange(n) / SR
    snap = highpass(noise(n, r), 2500) * env(n, 0.0005, 0.015) * 1.5
    tear = bandpass(noise(n, r), 1400, 1.5) * (env(n, 0.03, 0.35) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * tt)))
    gurgle = bandpass(noise(n, r), 350, 6) * env(n, 0.15, 0.6) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * tt)) * 0.8
    return np.tanh((snap + tear + gurgle) * 1.3)


def tear(seed):
    """Desgarro: carne que se rasga, tendones que chascan y un chorro humedo."""
    r = np.random.default_rng(seed)
    n = int(SR * 1.2)
    tt = np.arange(n) / SR
    rip = bandpass(noise(n, r), 900, 1.2) * np.clip(np.sin(np.clip(tt / 0.7, 0, 1) * np.pi), 0, 1) ** 0.6
    rip *= 0.55 + 0.45 * np.abs(np.sin(2 * np.pi * (14 + 10 * tt) * tt))
    snaps = np.zeros(n)
    for _ in range(7):
        k = int(r.uniform(0.05, 0.75) * SR)
        m = int(SR * 0.006)
        snaps[k:k + m] += highpass(noise(m, r), 2000) * np.linspace(1, 0, m) * r.uniform(0.6, 1.2)
    gush = bandpass(noise(n, r), 380, 3) * np.clip((tt - 0.55) / 0.15, 0, 1) * np.exp(-np.clip(tt - 0.7, 0, None) * 3) * 0.9
    return np.tanh((rip + snaps + gush) * 1.5)


def squelch(seed):
    """Chorro de sangre: borboteo y salpicadura contra el suelo."""
    r = np.random.default_rng(seed)
    n = int(SR * 0.9)
    tt = np.arange(n) / SR
    burble = bandpass(noise(n, r), 300 + 120 * np.sin(2 * np.pi * 7 * tt).mean(), 5) * (0.5 + 0.5 * np.sin(2 * np.pi * 11 * tt))
    burble *= env(n, 0.01, 0.5)
    splash = np.zeros(n)
    for _ in range(18):
        k = int(r.uniform(0.0, 0.6) * SR)
        m = int(SR * 0.02)
        splash[k:k + m] += bandpass(noise(m, r), 1500, 2) * env(m, 0.001, 0.008) * r.uniform(0.3, 0.9)
    return np.tanh((burble * 1.6 + splash) * 1.4)


def surge():
    """Sobretension: zumbido electrico que sube, chisporroteo y un golpe seco al final."""
    r = np.random.default_rng(131)
    n = int(SR * 1.6)
    tt = np.arange(n) / SR
    f = 50 + 40 * tt
    hum = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.35 + np.sin(2 * np.pi * 2 * np.cumsum(f) / SR) * 0.3
    hum *= np.clip(tt / 1.2, 0, 1) ** 2 * (tt < 1.35)
    crackle = np.zeros(n)
    for _ in range(60):
        k = int(r.uniform(0.2, 1.35) * SR)
        m = int(SR * r.uniform(0.002, 0.008))
        crackle[k:k + m] += highpass(noise(m, r), 3000) * r.uniform(0.2, 0.9)
    pop = np.zeros(n)
    k = int(1.35 * SR)
    m = n - k
    pop[k:] = lowpass(noise(m, r), 1500) * env(m, 0.001, 0.12) * 1.4
    return np.tanh((hum + crackle + pop) * 1.4)


def alarm():
    """Alarma del laboratorio: dos tonos estridentes que se alternan, con eco."""
    n = int(SR * 2.4)
    tt = np.arange(n) / SR
    tone = np.where((tt * 2.5) % 1 < 0.5, 880, 660)
    ph = 2 * np.pi * np.cumsum(tone) / SR
    x = np.sign(np.sin(ph)) * 0.5 + np.sin(2 * ph) * 0.2
    x = bandpass(x, 1200, 1.5)
    d = int(SR * 0.18)
    x[d:] += x[:-d] * 0.35
    return x * np.clip(tt / 0.05, 0, 1) * np.clip((2.4 - tt) / 0.2, 0, 1)


def sting(seed):
    """Golpe de susto: cluster disonante de cuerdas con un impacto grave."""
    r = np.random.default_rng(seed)
    n = int(SR * 1.8)
    tt = np.arange(n) / SR
    x = np.zeros(n)
    for f in (233, 247, 262, 349, 370, 523, 554):
        f2 = f * (1 + 0.004 * r.standard_normal())
        saw = 2 * ((f2 * tt) % 1) - 1
        x += saw * (0.6 + 0.4 * np.sin(2 * np.pi * r.uniform(5, 8) * tt))
    x = bandpass(x, 1800, 0.7) * env(n, 0.004, 0.9) * 0.6
    boom = np.sin(2 * np.pi * (55 * np.exp(-tt * 4) + 30) * tt) * env(n, 0.002, 0.5) * 1.3
    scrape = bandpass(noise(n, r), 3000, 2) * env(n, 0.01, 0.4) * 0.4
    return np.tanh((x + boom + scrape) * 1.6)


def knock():
    """Tres golpes secos al otro lado de la pared, el ultimo mas fuerte."""
    r = np.random.default_rng(141)
    n = int(SR * 1.5)
    x = np.zeros(n)
    for t0, g in ((0.0, 0.7), (0.32, 0.8), (0.75, 1.1)):
        k = int(t0 * SR)
        m = int(SR * 0.25)
        tm = np.arange(m) / SR
        x[k:k + m] += (np.sin(2 * np.pi * 90 * tm) * env(m, 0.001, 0.07) + lowpass(noise(m, r), 900) * env(m, 0.0005, 0.02)) * g
    return lowpass(x, 1500)


def scream():
    """Grito lejano y filtrado por las paredes, que se quiebra."""
    r = np.random.default_rng(151)
    n = int(SR * 2.2)
    tt = np.arange(n) / SR
    f = 520 + 180 * np.sin(np.clip(tt / 1.6, 0, 1) * np.pi) + 25 * np.sin(2 * np.pi * 7 * tt) - 200 * np.clip((tt - 1.5) / 0.5, 0, 1)
    ph = 2 * np.pi * np.cumsum(f) / SR
    voice = np.sign(np.sin(ph)) * 0.4 + bandpass(noise(n, r), 1200, 1.5) * 0.3
    voice = bandpass(voice, 900, 2) + bandpass(voice, 2400, 3) * 0.5
    e = np.clip(tt / 0.2, 0, 1) * np.clip((2.2 - tt) / 0.6, 0, 1)
    x = lowpass(voice * e, 1600)
    d = int(SR * 0.23)
    x[d:] += x[:-d] * 0.4
    return x


def revive():
    """Reanimar: jadeo profundo al recuperar el aire."""
    r = np.random.default_rng(81)
    n = int(SR * 1.1)
    tt = np.arange(n) / SR
    air = bandpass(noise(n, r), 1100, 1.2) * np.sin(np.clip(tt / 0.55, 0, 1) * np.pi) ** 1.5
    out = bandpass(noise(n, r), 700, 1.0) * np.sin(np.clip((tt - 0.6) / 0.45, 0, 1) * np.pi) ** 2 * 0.6
    voice = np.sin(2 * np.pi * (140 - 30 * tt) * tt) * np.sin(np.clip(tt / 0.55, 0, 1) * np.pi) ** 3 * 0.15
    return air + out + voice


def walkie_click():
    """Clic del boton del walkie y un pitido corto de cortesia."""
    r = np.random.default_rng(171)
    n = int(SR * 0.18)
    tt = np.arange(n) / SR
    click = highpass(noise(n, r), 2000) * env(n, 0.0005, 0.008)
    beep = np.sin(2 * np.pi * 1850 * tt) * (tt > 0.03) * (tt < 0.11) * 0.35
    return click + bandpass(beep, 1850, 3) * 3


def walkie_crackle():
    """Crujido de radio al recibir: rafaga de estatica filtrada con chasquidos."""
    r = np.random.default_rng(181)
    n = int(SR * 0.55)
    tt = np.arange(n) / SR
    hiss = bandpass(noise(n, r), 2200, 0.9) * (0.6 + 0.4 * np.sin(2 * np.pi * 13 * tt))
    pops = np.zeros(n)
    for k in r.integers(0, n - 200, 14):
        pops[k:k + 120] += highpass(noise(120, r), 3000) * r.uniform(0.5, 1.3)
    e = np.clip(tt / 0.02, 0, 1) * np.clip((0.55 - tt) / 0.2, 0, 1)
    return (hiss * 0.7 + pops) * e


def marker_ping():
    """Marcador de equipo: dos notas suaves y apagadas, discretas."""
    n = int(SR * 0.4)
    tt = np.arange(n) / SR
    a = np.sin(2 * np.pi * 660 * tt) * env(n, 0.003, 0.12)
    k = int(SR * 0.09)
    b = np.zeros(n)
    b[k:] = np.sin(2 * np.pi * 880 * tt[:n - k]) * env(n - k, 0.003, 0.15)
    return lowpass(a + b * 0.8, 2500)


def drone():
    """Zumbido del laboratorio para el ambiente en bucle: red electrica, ventilacion lejana y un
    gemido metalico que va y viene. Empieza y acaba igual para que el bucle no se note."""
    r = np.random.default_rng(191)
    L = 16.0
    n = int(SR * L)
    tt = np.arange(n) / SR
    hum = sum(np.sin(2 * np.pi * f * tt) * a for f, a in ((50, 0.5), (100, 0.35), (150, 0.12), (200, 0.06)))
    hum *= 0.8 + 0.2 * np.sin(2 * np.pi * tt / L * 3)
    air = lowpass(noise(n, r), 400) * 1.2 * (0.7 + 0.3 * np.sin(2 * np.pi * tt / L * 2 + 1))
    moan_f = 63 + 6 * np.sin(2 * np.pi * tt / L * 4)
    moan = np.sin(2 * np.pi * np.cumsum(moan_f) / SR) * (0.5 + 0.5 * np.sin(2 * np.pi * tt / L)) ** 4 * 0.6
    x = hum * 0.5 + air + moan
    # bucle: se funde el final con el principio
    f = int(SR * 1.0)
    x[:f] = x[:f] * np.linspace(0, 1, f) + x[-f:] * np.linspace(1, 0, f)
    return x[:-f]


def creak(seed):
    """Crujido metalico lejano de la estructura."""
    r = np.random.default_rng(seed)
    n = int(SR * 1.8)
    tt = np.arange(n) / SR
    f = 180 + 60 * np.sin(np.clip(tt / 1.6, 0, 1) * np.pi) + r.uniform(-30, 30)
    ph = 2 * np.pi * np.cumsum(f) / SR
    grind = np.sign(np.sin(ph)) * (0.5 + 0.5 * np.sin(2 * np.pi * 23 * tt + r.uniform(0, 6))) ** 3
    x = bandpass(grind, 700, 3) + bandpass(grind, 1600, 5) * 0.4
    e = np.clip(tt / 0.3, 0, 1) * np.clip((1.8 - tt) / 0.7, 0, 1)
    x = lowpass(x * e, 1800)
    d = int(SR * 0.17)
    x[d:] += x[:-d] * 0.5
    return x


def drip():
    """Gota que cae en un charco, con eco."""
    n = int(SR * 0.9)
    tt = np.arange(n) / SR
    f = 1400 * np.exp(-tt * 30) + 700
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.001, 0.05)
    for k, g in ((0.11, 0.4), (0.23, 0.2), (0.37, 0.1)):
        d = int(SR * k)
        x[d:] += x[:-d] * g
    return x


def crack(seed):
    """El Paciente cambia de sitio: chasquido seco de huesos y un crujido corto."""
    r = np.random.default_rng(seed)
    n = int(SR * 0.45)
    x = np.zeros(n)
    for t0, g in ((0.0, 1.0), (0.05, 0.6), (0.11, 0.8), (0.2, 0.4)):
        k = int(t0 * SR)
        m = int(SR * 0.06)
        x[k:k + m] += highpass(noise(m, r), 1800) * env(m, 0.0003, 0.012) * g
    tt = np.arange(n) / SR
    x += np.sin(2 * np.pi * 140 * tt) * env(n, 0.001, 0.05) * 0.4
    return x


def screech():
    """El Rastreador te ve: chillido agudo y aspero que se quiebra."""
    r = np.random.default_rng(311)
    n = int(SR * 0.9)
    tt = np.arange(n) / SR
    f = 1500 + 600 * np.sin(np.clip(tt / 0.5, 0, 1) * np.pi) - 700 * np.clip((tt - 0.55) / 0.35, 0, 1)
    ph = 2 * np.pi * np.cumsum(f) / SR
    v = np.sign(np.sin(ph)) * 0.35 + np.sin(ph * 1.5) * 0.3 + bandpass(noise(n, r), 2600, 1.4) * 0.5
    e = np.clip(tt / 0.04, 0, 1) * np.clip((0.9 - tt) / 0.3, 0, 1)
    return bandpass(v * e, 1800, 0.8)


def pant():
    """Jadeo humedo y rapido del Rastreador cansado."""
    r = np.random.default_rng(321)
    n = int(SR * 1.6)
    tt = np.arange(n) / SR
    puff = np.maximum(0, np.sin(2 * np.pi * 3.2 * tt)) ** 3
    return bandpass(noise(n, r), 900, 1.0) * puff + lowpass(noise(n, r), 300) * puff * 0.4


def scratch():
    """Garras que arañan metal: abre un escondite."""
    r = np.random.default_rng(331)
    n = int(SR * 0.8)
    tt = np.arange(n) / SR
    x = np.zeros(n)
    for t0 in (0.0, 0.25, 0.45):
        k = int(t0 * SR)
        m = int(SR * 0.22)
        f = 2400 - 900 * np.arange(m) / m
        x[k:k + m] += np.sin(2 * np.pi * np.cumsum(f) / SR) * env(m, 0.005, 0.15) * 0.5 + highpass(noise(m, r), 3000) * env(m, 0.002, 0.1)
    return x


def main():
    for i in range(3):
        save(f"berserker/step{i + 1}", step(10 + i))
    for i in range(2):
        save(f"berserker/breath{i + 1}", breath(20 + i))
    save("berserker/listen", listen())
    for i in range(2):
        save(f"berserker/sniff{i + 1}", sniff(30 + i))
    save("berserker/roar", roar())
    save("berserker/impact", impact())
    for i in range(2):
        save(f"player/groan{i + 1}", groan(70 + i))
    save("player/heartbeat", heartbeat())
    save("mob/grab", grab())
    for i in range(2):
        save(f"mob/crunch{i + 1}", crunch(100 + i))
    save("mob/bite", bite())
    for i in range(2):
        save(f"mob/tear{i + 1}", tear(110 + i))
        save(f"mob/squelch{i + 1}", squelch(120 + i))
    save("player/revive", revive())
    save("event/surge", surge())
    save("event/alarm", alarm())
    for i in range(2):
        save(f"event/sting{i + 1}", sting(160 + i))
    save("event/knock", knock())
    save("event/scream", scream())
    save_new()


def save_new():
    save("walkie/click", walkie_click())
    save("walkie/crackle", walkie_crackle())
    save("ui/marker", marker_ping())
    save("ambient/drone", drone())
    for i in range(2):
        save(f"ambient/creak{i + 1}", creak(200 + i))
    save("ambient/drip", drip())
    save("rastreador/screech", screech())
    save("rastreador/pant", pant())
    save("rastreador/scratch", scratch())
    save("paciente/crack1", crack(301))
    save("paciente/crack2", crack(302))
    print("sonidos escritos en", OUT)


if __name__ == "__main__":
    main()
