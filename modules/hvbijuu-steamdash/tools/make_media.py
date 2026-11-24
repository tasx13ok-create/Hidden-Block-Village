#!/usr/bin/env python3
"""Assemble smooth 60 fps clips with the game's own sound from a slow-motion QA capture.

The QA client runs each scene at 1/scale speed and marks its start with a one-frame magenta flash; the
screen recording is real time. Each scene is cut at its flash, sped up by `scale` (so ~25 rendered fps
become ~125 fps of game time, resampled to a clean 60), and given a stereo soundtrack mixed from the
sound events the client logged: each sound file is pitched, attenuated like Minecraft (linear over
16 blocks, or 16 x volume when louder than 1) and panned relative to the camera.

usage: make_media.py <recording.mkv> <DASH-QA-RESULT.json> <DASH-QA-SOUNDS.json> <assets index json> <assets objects dir> <mod assets dir> <out dir>
"""
import json, os, subprocess, sys
import numpy as np

REC, RESULT, SOUNDS, INDEX, OBJECTS, MODASSETS, OUT = sys.argv[1:8]
SR = 48000
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
TITLES = {
    "A-tap-dash": ("STEAM DASH PUNCH", "tap - afterimage barrage - bullet time - through the mountain"),
    "B-overcharge-pierce": ("OVERCHARGE  TIER 3", "hold to charge - pierces the whole line"),
    "C-juggle-pin-spike": ("JUGGLE  >  TAIL PIN  >  SPIKE", "two relays, nine tails, one crater"),
    "D-springboard": ("WALL SPRINGBOARD", "off the wall at 1.4x"),
    "E-lariat": ("GYUKI  LARIAT", "crouch - blink - hit-stop - release"),
}
# Scenes that get a 1/2-speed replay of the hit: the sound that marks contact.
REPLAYS = {
    "A-tap-dash": lambda e: "anvil" in e["file"] and e["pitch"] > 1.5,
    "E-lariat": lambda e: "lariat_impact" in e["file"],
}
REEL = os.environ.get("REEL", "steam-dash-2-showreel.mp4")
os.makedirs(OUT, exist_ok=True)
result = json.load(open(RESULT))
sounds = json.load(open(SOUNDS))
index = json.load(open(INDEX))["objects"]


def run(cmd):
    subprocess.run(cmd, check=True)


def probe_frames(start, dur):
    """Tiny RGB frames of the recording from `start` for `dur` seconds, with their timestamps."""
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-ss", f"{max(0, start):.3f}", "-t", f"{dur:.3f}", "-i", REC,
           "-vf", "scale=32:18,format=rgb24", "-f", "rawvideo", "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    frames = np.frombuffer(raw, np.uint8).reshape(-1, 18, 32, 3)
    fps = 60.0
    return [(max(0, start) + i / fps, f) for i, f in enumerate(frames)]


def find_flash(guess):
    """Recording time (s) of the first magenta frame near `guess`."""
    for t, f in probe_frames(guess - 6, 12):
        m = f.reshape(-1, 3).mean(0)
        if m[0] > 200 and m[1] < 70 and m[2] > 200:
            return t
    raise SystemExit(f"no sync flash near {guess:.1f}s")


_cache = {}


def load(path):
    """A sound file decoded to mono float32 at SR."""
    if path in _cache:
        return _cache[path]
    ns, rest = path.split(":", 1)
    if ns == "minecraft":
        h = index["minecraft/" + rest]["hash"]
        f = os.path.join(OBJECTS, h[:2], h)
    else:
        f = os.path.join(MODASSETS, ns, rest)
    raw = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", f, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    a = np.frombuffer(raw, np.float32).copy()
    _cache[path] = a
    return a


def mix(events, v0, length_s, stretch=1.0):
    """Stereo mix of events placed at (v - v0) * stretch ms; `stretch` > 1 slows time (and pitch) further."""
    n = int(SR * length_s) + SR
    out = np.zeros((n, 2), np.float32)
    for e in events:
        at = (e["v"] - v0) * stretch / 1000.0
        if at < -1 or at > length_s:
            continue
        try:
            a = load(e["file"])
        except Exception as x:
            print("skip", e["file"], x)
            continue
        pitch = min(2.0, max(0.5, e["pitch"])) / stretch
        idx = np.arange(0, len(a) - 1, pitch)
        s = np.interp(idx, np.arange(len(a)), a)
        vol = e["vol"]
        gain = min(1.0, max(0.0, vol))
        dx, dy, dz = e["x"] - e.get("lx", e["x"]), e["y"] - e.get("ly", e["y"]), e["z"] - e.get("lz", e["z"])
        dist = (dx * dx + dy * dy + dz * dz) ** 0.5
        pan = 0.0
        if e["att"] == 1:
            gain *= max(0.0, 1 - dist / (16.0 * max(1.0, vol)))
            yaw = np.radians(e.get("lyaw", 0.0))
            rx, rz = -np.cos(yaw), -np.sin(yaw)
            h = (dx * dx + dz * dz) ** 0.5
            if h > 0.3:
                pan = float(np.clip((dx * rx + dz * rz) / h, -1, 1)) * min(1.0, h / 4)
        if gain <= 0.002:
            continue
        lg, rg = np.cos((pan + 1) * np.pi / 4) * 1.414, np.sin((pan + 1) * np.pi / 4) * 1.414
        i0 = int(at * SR)
        j0 = max(0, -i0)
        i0 = max(0, i0)
        seg = s[j0:j0 + n - i0] * gain
        out[i0:i0 + len(seg), 0] += seg * lg
        out[i0:i0 + len(seg), 1] += seg * rg
    out = out[: int(SR * length_s)]
    peak = np.max(np.abs(out)) + 1e-9
    out = np.tanh(out / peak * 1.6) / np.tanh(1.6) * 0.89   # gentle limiter to -1 dBFS
    return out


def write_wav(path, stereo):
    pcm = (np.clip(stereo, -1, 1) * 32767).astype("<i2")
    import wave
    with wave.open(path, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())


def title_filter(title, sub):
    esc = lambda s: s.replace(":", r"\:").replace("'", r"\'")
    a = "if(lt(t,0.3),t/0.3,if(lt(t,2.4),1,if(lt(t,2.9),(2.9-t)/0.5,0)))"
    return (f"drawtext=fontfile={FONT}:text='{esc(title)}':fontcolor=white:fontsize=34:x=48:y=h-118:alpha='{a}':shadowcolor=black@0.7:shadowx=2:shadowy=2,"
            f"drawtext=fontfile={FONT}:text='{esc(sub)}':fontcolor=0xFFD37A:fontsize=20:x=50:y=h-76:alpha='{a}':shadowcolor=black@0.7:shadowx=2:shadowy=2")


start_real = int(open(os.path.join(os.path.dirname(REC), "dash-run.start")).read().strip())
clips = []
for w in result["windows"]:
    name, scale = w["name"], w["scale"]
    guess = (w["sync_real_ms"] - start_real) / 1000.0
    t0 = find_flash(guess)
    lead = 0.15                                       # skip the flash itself (it lasts 120 ms of real time)
    real_len = (w["end_real_ms"] - w["sync_real_ms"]) / 1000.0 - lead
    out_len = real_len / scale
    v0 = w["sync_virtual_ms"] + lead * 1000 / scale   # game time at the first frame of the clip
    events = [e for e in sounds if v0 - 500 <= e["v"] <= w["end_virtual_ms"]]
    wav = os.path.join(OUT, name + ".wav")
    write_wav(wav, mix(events, v0, out_len))
    title, sub = TITLES.get(name, (name, ""))
    mp4 = os.path.join(OUT, f"steam-dash-{name}.mp4")
    vf = f"setpts=(PTS-STARTPTS)/{scale},fps=60,{title_filter(title, sub)},fade=t=in:st=0:d=0.25,fade=t=out:st={out_len - 0.3:.3f}:d=0.3"
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{t0 + lead:.3f}", "-t", f"{real_len:.3f}", "-i", REC, "-i", wav,
         "-filter_complex", f"[0:v]{vf}[v];[1:a]afade=t=out:st={out_len - 0.3:.3f}:d=0.3[a]", "-map", "[v]", "-map", "[a]",
         "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-r", "60",
         "-c:a", "aac", "-b:a", "192k", "-t", f"{out_len:.3f}", "-movflags", "+faststart", mp4])
    os.remove(wav)
    clips.append(mp4)
    print(name, f"flash at {t0:.2f}s, {real_len:.1f}s recorded -> {out_len:.2f}s at 60 fps, {len(events)} sounds")

    if name in REPLAYS:   # a 2x slow-motion replay of the hit with deeper, stretched sound
        imp = [e for e in events if REPLAYS[name](e)]
        if imp:
            hit_v = imp[0]["v"]
            rs = (hit_v - v0) / 1000.0 - 0.6          # replay from 0.6 s of game time before contact
            rl = 2.6
            wav = os.path.join(OUT, "replay.wav")
            write_wav(wav, mix(events, v0 + rs * 1000, rl * 2, stretch=2.0))
            rmp4 = os.path.join(OUT, f"steam-dash-{name[0]}-impact-replay-slowmo.mp4")
            vf = (f"setpts=(PTS-STARTPTS)*2/{scale},fps=60,drawtext=fontfile={FONT}:text='REPLAY  1/2 speed':fontcolor=white:fontsize=22:x=w-tw-40:y=40:shadowcolor=black@0.7:shadowx=2:shadowy=2,"
                  f"fade=t=in:st=0:d=0.2,fade=t=out:st={rl * 2 - 0.3:.3f}:d=0.3")
            run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{t0 + lead + rs * scale:.3f}", "-t", f"{rl * scale:.3f}", "-i", REC, "-i", wav,
                 "-filter_complex", f"[0:v]{vf}[v]", "-map", "[v]", "-map", "1:a",
                 "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-r", "60",
                 "-c:a", "aac", "-b:a", "192k", "-t", f"{rl * 2:.3f}", "-movflags", "+faststart", rmp4])
            os.remove(wav)
            clips.insert(clips.index(mp4) + 1, rmp4)
            print("replay", f"{rl * 2:.1f}s")

# The showreel: every scene back to back.
lst = os.path.join(OUT, "concat.txt")
with open(lst, "w") as f:
    for c in clips:
        f.write(f"file '{os.path.abspath(c)}'\n")
reel = os.path.join(OUT, REEL)
run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst,
     "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-r", "60", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", reel])
os.remove(lst)
print("showreel", reel)
