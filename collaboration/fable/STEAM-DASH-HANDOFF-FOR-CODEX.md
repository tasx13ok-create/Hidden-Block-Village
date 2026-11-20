# Codex: Steam Dash Punch is ready, please build it and send the jar back to the owner

From Fable (Claude Code cloud session), 2026-11-20. The owner asked for this to be delivered to you and for you to **send the finished jar back to them**.

## What it is

The owner's "Bijuu Shinobi Arts 8K v3" jar (`hvbijuu` 0.16.2-rc1, SHA-256 `4c850bac…ce9a`, 9,093,274 bytes; private, not in git) now carries a second mod, `hvbijuu_steamdash` 1.0.0. It is a cut-scene-style **Steam Dash Punch** for Kurama + Kokuo, in five stages:
1. Overheat telegraph.
2. Sonic blink with golden afterimages and a mach cone.
3. 3-tick hit-stop with a **white flash frame and an ink-silhouette shader frame**.
4. A boil launch that tunnels through mountains and ends in a cracked-stone spiderweb splatter.
5. The optional anime **spike** on a second press within 1.5 s, which leaves a radius-8 crater.

There is also a **wall springboard** that hooks into Shinobi Immersion's wall-run state. The full stage table is in [`modules/hvbijuu-steamdash/README.md`](../../modules/hvbijuu-steamdash/README.md).

Source, sounds and shader are original and public in `modules/hvbijuu-steamdash/`. No hvbijuu class, texture or decompiled body is committed.

## Build the jar for the owner (on the PC that has their jar)

```
./gradlew -p modules/hvbijuu-steamdash -PhvbJar=<path to Bijuu-Shinobi-Arts-8K-v3.jar> reobfJar
python modules/hvbijuu-steamdash/assemble.py <that jar> modules/hvbijuu-steamdash/build/libs/hvb-steamdash-private-1.jar Bijuu-Shinobi-Arts-8K-v3-SteamDash.jar
```

Expected output: **9,281,101 bytes, SHA-256 `c9f97c0115e6bed0a1c6ab645d6709629c1b761eb1c3acd885d8e349a64a1617`**. That is the jar Fable built and tested here. It may differ only if your build produces different class bytes, so compare the reobf jar hash `c40afa0c…73ef` first.
- 288 of 290 original entries are byte-identical, and all 111 PNG textures are untouched.
- `en_us.lang` gets 7 appended lines and `mcmod.info` gets a second entry.
- 30 entries are added.

Install: replace `Bijuu-Shinobi-Arts-8K-v3.jar` with this jar on the server and every client. The key is **B** (rebindable). The config is `config/hvbijuu_steamdash.cfg`.

## Evidence

- Receipt: [`release/evidence/hvbijuu-steamdash-1.0.0.json`](../../release/evidence/hvbijuu-steamdash-1.0.0.json).
- Pure rules: 395/395 pass.
- Real 1.12.2 client: 23/23 pass. This was a scripted run through the real key binding: dash → mountain (153 blocks) → second ridge (81) → splatter (19 cobblestone + 16 cracked), then a spike crater (311 blocks), then the springboard. During the hit-stop the target held perfectly still (spread 0.0). The client drew 2 white frames and 1 true ink-shader frame.
- Media, all from the final build, in [`media/steam-dash/`](media/steam-dash/):
  - `steam-dash-1-punch-through-mountain.mp4`: the full sequence, real time.
  - `steam-dash-1-impact-slowmo-4x.mp4`: the impact in 4x slow motion (white flash, ink frame, shockwave).
  - `steam-dash-1-impact-frame-by-frame.png`: 25 consecutive frames across the hit.
  - `frame-2-contact-white-flash.png` and `frame-3-ink-silhouette.png`: the two impact frames.
  - `steam-dash-2-spike-crater.mp4` and `steam-dash-3-wall-springboard.mp4`.
  - `run1-*.png` and `run2-9-spike-crater.png`: stage stills.

The test client used software OpenGL at about 27–30 fps with sound muted, so the video is choppier than a real GPU client.

## Please check on the real setup (not run here)

1. The dedicated/Magma server with real players and latency. All terrain edits go through Forge `BreakEvent` plus the `setBlockState` result (Dungeon Guard honors that). Bukkit protection plugins on Magma are untested.
2. Sound playback in a client with audio: the six `.ogg` files are mono 44.1 kHz, so they are positional.
3. GPU frame pacing for the ink frame and the four afterimage clones (each re-renders the 8K armor once per frame).
4. With OptiFine shaders on, the ink frame steps aside (it never replaces another shader) and a dark hold frame is drawn instead.
5. Polish idea: in the ink frame, busy ground textures (grass) turn into red noise. The edge threshold in `assets/minecraft/shaders/program/hvb_inkframe.fsh` could be raised.

## Notes for integration

- Classes live in package `village.bijuu` to reach hvbijuu's package-private `BijuuArmor.state` and `ChakraBridge.pay`. They only reference hvbijuu members, which have readable names, so `compileOnly` on the release jar works.
- hvbijuu's `ReferenceTails` reflects on SRG field names. That is correct in production, but it crashes a readable-name dev client. QA used `qa/qa-devnames.py` to make a QA-only copy.
- The springboard reads Shinobi Immersion's `Parkour.SERVER` wall state by reflection. If Immersion is absent, it falls back to a geometric wall probe.
