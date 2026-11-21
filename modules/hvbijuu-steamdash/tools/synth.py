"""Synthesize the Steam Dash Punch sound effects (mono 44.1 kHz) and encode them to Ogg Vorbis."""
import numpy as np, subprocess, sys, os, wave
SR = 44100
rng = np.random.default_rng(1337)
OUT = sys.argv[1]

def t(sec): return np.arange(int(SR * sec)) / SR
def noise(sec): return rng.standard_normal(int(SR * sec))

def svf(x, f, q):
    """State-variable filter with per-sample cutoff f (Hz) and resonance q; returns (low, band, high)."""
    n = len(x); f = np.broadcast_to(np.asarray(f, float), (n,)); lo = np.zeros(n); bp = np.zeros(n); hp = np.zeros(n)
    l = b = 0.0; damp = 1.0 / q
    for i in range(n):
        g = 2 * np.sin(np.pi * min(f[i], SR * 0.22) / SR)
        h = x[i] - l - damp * b
        b += g * h; l += g * b
        lo[i] = l; bp[i] = b; hp[i] = h
    return lo, bp, hp

def onepole_lp(x, f):
    a = np.exp(-2 * np.pi * f / SR); y = np.zeros_like(x); s = 0.0
    for i in range(len(x)): s = (1 - a) * x[i] + a * s; y[i] = s
    return y
def onepole_hp(x, f): return x - onepole_lp(x, f)

def env(n, attack, release, shape=2.0):
    e = np.ones(n); a = int(SR * attack); r = int(SR * release)
    if a: e[:a] = np.linspace(0, 1, a) ** 1.5
    if r: e[-r:] *= np.linspace(1, 0, r) ** shape
    return e
def expdecay(n, tau): return np.exp(-np.arange(n) / (SR * tau))
def sat(x, k): return np.tanh(k * x) / np.tanh(k)
def pad(x, n): return np.pad(x, (0, max(0, n - len(x))))[:n]
def at(base, x, start):
    i = int(SR * start); base[i:i + len(x)] += x[:len(base) - i]; return base

def finish(name, x, peak=0.89):
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    fade = int(SR * 0.02); x[-fade:] *= np.linspace(1, 0, fade)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    wav = os.path.join(OUT, name + ".wav")
    with wave.open(wav, "wb") as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "6", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(wav)
    print(name, round(len(x) / SR, 2), "s")

# 1. charge: pressure building — rising band of steam, a kettle-like whine, a low furnace rumble
d = 1.4; n = int(SR * d); tt = t(d)
cut = 700 * (6 ** (tt / d))
_, band, _ = svf(noise(d), cut, 1.8)
hiss = onepole_hp(noise(d), 3500) * 0.35
whine = np.sin(2 * np.pi * (2600 * tt + 900 * tt ** 2 / d) + 0.4 * np.sin(2 * np.pi * 6 * tt)) * 0.05 * (tt / d) ** 2
rumble = onepole_lp(noise(d), 90) * 6 * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * tt))
chug = (np.sin(2 * np.pi * 7 * tt * (1 + tt)) > 0.6) * onepole_lp(noise(d), 400) * 0.6
x = (band * 0.9 + hiss + whine + rumble + chug) * env(n, 0.05, 0.08) * (0.25 + 0.75 * (tt / d) ** 1.6)
finish("charge", x)

# 2. blink: a tearing air-rip, high to low, with a sharp crack at the front
d = 0.55; n = int(SR * d); tt = t(d)
cut = 9000 * (0.08 ** (tt / d))
_, band, _ = svf(noise(d), cut, 2.5)
crack = pad(onepole_hp(noise(0.012), 2000) * expdecay(int(SR * 0.012), 0.003) * 4, n)
body = sat(band * env(n, 0.008, 0.35, 1.5) * 1.6, 1.8)
x = body + crack + onepole_lp(noise(d), 300) * 3 * expdecay(n, 0.12)
finish("blink", x)

# 3. impact: crack + punch body with pitch drop + sub boom + debris rumble + steam burst
d = 2.2; n = int(SR * d); x = np.zeros(n)
tr = onepole_hp(noise(0.02), 1500) * expdecay(int(SR * 0.02), 0.004) * 5
at(x, tr, 0.0)
tb = t(0.45); f_body = 55 + 110 * np.exp(-tb / 0.05)
body = sat(np.sin(2 * np.pi * np.cumsum(f_body) / SR) * expdecay(len(tb), 0.12) * 1.8, 2.5)
at(x, body * 1.2, 0.0)
ts = t(1.6); f_sub = 30 + 22 * np.exp(-ts / 0.25)
sub = np.sin(2 * np.pi * np.cumsum(f_sub) / SR) * expdecay(len(ts), 0.55)
at(x, sub * 1.1, 0.01)
rum = onepole_lp(noise(2.0), 220) * expdecay(int(SR * 2.0), 0.6) * 7
at(x, rum, 0.02)
_, sb, _ = svf(noise(0.9), 4200, 1.2)
at(x, sb * expdecay(len(sb), 0.25) * 0.8, 0.03)
finish("impact", sat(x * 0.9, 1.4))

# 4. launch: roaring rush of a body tearing through air — swells and passes
d = 1.7; n = int(SR * d); tt = t(d)
sweep = 260 + 900 * np.sin(np.pi * tt / d) ** 2
lo, bd, _ = svf(noise(d), sweep, 3.0)
growl = sat(np.sin(2 * np.pi * (70 + 25 * np.sin(np.pi * tt / d)) * tt) * 0.4, 2)
x = (lo * 0.6 + bd * 1.2 + growl) * np.sin(np.pi * np.clip(tt / d, 0, 1)) ** 0.8
finish("launch", x)

# 5. crash: rock breaking — dense crunch grains thinning out over a heavy thud
d = 1.5; n = int(SR * d); x = np.zeros(n)
th = t(0.6); thud = np.sin(2 * np.pi * np.cumsum(38 + 40 * np.exp(-th / 0.06)) / SR) * expdecay(len(th), 0.18)
at(x, sat(thud * 1.5, 2), 0.0)
for k in range(70):
    st = rng.exponential(0.25)
    if st > d - 0.1: continue
    g = rng.uniform(0.01, 0.05); gn = noise(g) * expdecay(int(SR * g), g / 3)
    _, gb, _ = svf(gn, rng.uniform(600, 3500), 1.5)
    at(x, gb * rng.uniform(0.3, 1.0) * np.exp(-st / 0.5) * 1.5, st)
at(x, onepole_lp(noise(1.4), 160) * expdecay(int(SR * 1.4), 0.4) * 5, 0.01)
finish("crash", x)

# 6. hiss: steam vent release with flutter
d = 1.0; n = int(SR * d); tt = t(d)
_, b6, h6 = svf(noise(d), 5200 - 1800 * tt / d, 1.4)
x = (b6 * 0.7 + h6 * 0.25) * expdecay(n, 0.35) * env(n, 0.01, 0.2) * (1 + 0.25 * np.sin(2 * np.pi * 23 * tt))
finish("hiss", x)

# 7. jab: one afterimage clone's punch — whoosh into a tight, snappy knuckle crack
d = 0.32; n = int(SR * d); x = np.zeros(n)
tw = t(0.12); _, wb, _ = svf(noise(0.12), 1800 + 5000 * (tw / 0.12), 2.0)
at(x, wb * env(len(wb), 0.06, 0.02) * 0.6, 0.0)
tk = onepole_hp(noise(0.01), 2500) * expdecay(int(SR * 0.01), 0.002) * 4
at(x, tk, 0.11)
tb = t(0.18); jb = np.sin(2 * np.pi * np.cumsum(90 + 160 * np.exp(-tb / 0.02)) / SR) * expdecay(len(tb), 0.05)
at(x, sat(jb * 1.6, 2), 0.11)
finish("jab", x)

# 8. timewarp: the world slowing down — a falling tape-stop whoosh into a deep, wavering hum
d = 2.4; n = int(SR * d); tt = t(d)
cut = 6000 * (0.03 ** np.clip(tt / 0.7, 0, 1))
_, tb8, _ = svf(noise(d), cut, 2.2)
f_hum = 55 + 120 * np.exp(-tt / 0.35)
hum = np.sin(2 * np.pi * np.cumsum(f_hum) / SR) * (0.5 + 0.2 * np.sin(2 * np.pi * 3.5 * tt))
hum2 = np.sin(2 * np.pi * np.cumsum(f_hum * 1.5) / SR) * 0.25
x = (tb8 * expdecay(n, 0.5) * 1.2 + (hum + hum2) * env(n, 0.15, 0.6)) * env(n, 0.005, 0.5)
finish("timewarp", sat(x, 1.3))

# 9. sizzle: molten rock meeting air — crackling pops over a steady fry
d = 1.6; n = int(SR * d); x = onepole_hp(noise(d), 2500) * 0.25 * (0.7 + 0.3 * np.sin(2 * np.pi * 1.3 * t(d)))
for k in range(90):
    st = rng.uniform(0, d - 0.05); g = rng.uniform(0.002, 0.008)
    at(x, onepole_hp(noise(g), 3000) * expdecay(int(SR * g), g / 3) * rng.uniform(0.5, 2.0), st)
_, sb9, _ = svf(noise(d), 900, 1.0)
x = (x + sb9 * 0.15) * env(n, 0.05, 0.4)
finish("sizzle", x)

# 10. stab: a tail spearing into the ground — air rush, heavy thump, gritty earth crunch
d = 0.7; n = int(SR * d); x = np.zeros(n)
tw = t(0.1); _, ws, _ = svf(noise(0.1), 900 + 3000 * (tw / 0.1), 1.6)
at(x, ws * env(len(ws), 0.08, 0.01) * 0.5, 0.0)
th = t(0.4); thump = np.sin(2 * np.pi * np.cumsum(48 + 70 * np.exp(-th / 0.03)) / SR) * expdecay(len(th), 0.1)
at(x, sat(thump * 2, 2.5), 0.09)
at(x, onepole_lp(noise(0.5), 700) * expdecay(int(SR * 0.5), 0.12) * 3, 0.095)
finish("stab", x)

# 11. relay: blinking ahead of a flying body — sharp rip, then a whip-crack and short boom
d = 0.9; n = int(SR * d); x = np.zeros(n)
tr = t(0.16); _, rb, _ = svf(noise(0.16), 9000 * (0.1 ** (tr / 0.16)), 2.5)
at(x, rb * env(len(rb), 0.004, 0.05) * 1.2, 0.0)
at(x, onepole_hp(noise(0.006), 3000) * expdecay(int(SR * 0.006), 0.0015) * 6, 0.15)
tb = t(0.6); bm = np.sin(2 * np.pi * np.cumsum(40 + 60 * np.exp(-tb / 0.08)) / SR) * expdecay(len(tb), 0.2)
at(x, sat(bm * 1.4, 2), 0.15)
finish("relay", x)
