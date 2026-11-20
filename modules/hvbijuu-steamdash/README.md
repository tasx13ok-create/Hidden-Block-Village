# Bijuu Steam Dash Punch (`hvbijuu_steamdash` 1.0.0)

A cinematic dash attack for the owner's Hidden Village Bijuu Armor (`hvbijuu` 0.16.2-rc1, "Bijuu Shinobi Arts 8K v3"). It ships *inside* that jar as a second `@Mod`. Everything in this directory is original: code, the six synthesized sounds and the ink shader. No hvbijuu class, texture or decompiled body is here. The owner's jar stays private (see "Build").

## How it plays

To channel it, wear the full Kurama set and carry a crate-issued Kokuo piece anywhere in your inventory, or the reverse. Aim at an enemy within 35 blocks and press **B** ("Steam Dash Punch" under Hidden Village Bijuu Armor in Controls). One dash costs 50 resonance and has a 25 s cooldown.

| Stage | Ticks | What happens |
| --- | --- | --- |
| Overheat telegraph | 0–3 | You root and lean in. High-velocity steam and smoke jets vent backward from the shoulders and tail base, and gold dust spirals around your feet. The armor glows white-hot. Fire-extinguish hiss at pitch 1.8 plus a steam charge. FOV pulls in to ×0.92. Letterbox bars come in and the camera cuts to a wide third-person shot. |
| Blink | 4 | A golden afterimage stays where you stood. Sonic blink with a firework blast at pitch 0.6 and elytra wind. FOV snaps to ×1.25. A sonic ring forms. |
| Sonic tunnel | 4 → contact | You fly at 5 blocks/tick with no gravity. Four golden trail clones follow you. Steam and flame wind-tunnel rings form, with a rotating white-and-gold mach cone over your punching arm and pressure rings peeling off it. Soft blocks in your hitbox's path are cleared (no trench in the ground unless you dive). Your tails stream back. |
| Contact / hit-stop | 3 ticks | 16 damage. Both fighters freeze mid-air (a mob skips its updates, so it holds its pose). Tick 1 is a full white frame with an anvil "bell" at pitch 2.0. Ticks 2–3 are an ink-silhouette shader frame. Your Kurama tails whip toward the target. |
| Release | — | Explosion, anvil land at 0.5, a wooden door breaking and thunder. A huge explosion particle, a shockwave sphere, a cone of blast and steam out of their back, and screen shake. |
| Boil launch | 25 ticks | They fly along *your look* at 4.2 + 1.1 up. The steam keeps driving them, decaying 0.93 per tick (about 50 blocks), and smashes everything in their hitbox's path with a random 0.2–0.8 margin. About 15% of broken rock and dirt flies back at you as tumbling rubble. Targets carry the `hvbBoilLaunchedTicks` entity tag. |
| Wall splatter | on impact | If they hit something they can't smash while boiling: a blast that doesn't break terrain, 6 damage, and a permanent spiderweb of cobblestone and cracked stone bricks (radius 4) in the wall. |
| Spike (press B again within 1.5 s) | — | 20 resonance. A lightning flash, then you appear 2 above and 2 behind them and slam them down at 75° (−3.8). 10 damage. On landing: a radius-8 crater (8 damage), a radial ring of tilted debris, and nearby enemies thrown out. |
| Springboard | — | Press B while wall-running (Shinobi Immersion's wall state, or touching a wall in mid-air) with nothing in sight. You launch off the wall at 1.4× speed along your look, the stone behind your feet cracks, and a steam ring bursts off the surface. 10 resonance. With a target in sight, the springboard kicks straight into the dash instead. |

All terrain changes ask protection first: Forge `BreakEvent` with the attacker, and the `setBlockState` result, which Dungeon Guard gates. Tile entities, liquids and anything harder than 3.0 are never broken. One dash breaks at most 450 blocks. Nothing drops as an item, and the rubble is client-only, so it is never placed in the world. `config/hvbijuu_steamdash.cfg` can switch off breaking, cracking, the letterbox, camera effects and impact frames, and sets costs and damage.

## Build

```
./gradlew -p modules/hvbijuu-steamdash -PhvbJar=<owner jar> reobfJar
python3 modules/hvbijuu-steamdash/assemble.py <owner jar> modules/hvbijuu-steamdash/build/libs/hvb-steamdash-private-1.jar <output jar>
```

`assemble.py` copies every original entry byte for byte (the 8K textures are untouched). It refuses to overwrite any original entry. It adds the 20 classes, `sounds.json`, the six `.ogg` files and the shader, appends seven lang lines, and adds a second `mcmod.info` entry.

Pure rules: `javac -d out src/village/bijuu/DashMath.java tests/village/bijuu/DashChecks.java && java -cp out village.bijuu.DashChecks`. Sounds: `python3 tools/synth.py`, which needs numpy and ffmpeg.

## Client QA

`qa/` is a client-only acceptance mod. It needs `-Dhvb.dashqa=true` and drives the real key binding in a flat test world through three runs:
1. dash → mountain → ridge → cliff splatter
2. dash → spike → crater
3. wall springboard

It writes `DASH-QA-RESULT.json` and stage screenshots.

The dev client uses readable (MCP) names, but hvbijuu's `ReferenceTails` looks up fields by their obfuscated SRG names. So QA runs on a copy of the jar made by `qa/qa-devnames.py`. That copy is QA only; the release jar keeps the SRG names, which is correct in production.
