#!/usr/bin/env python3
"""Package the built Steam Dash Arena save for the owner."""
import os, shutil, sys, zipfile
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..'))
from tools.world_merge import nbt

SAVE, OUT = sys.argv[1], sys.argv[2]
QA_ONLY = {'hvb_dash_qa', 'hvb_arena_builder', 'vale_clientchecks', 'valevfxshots'}
stage = OUT + '.stage'
shutil.rmtree(stage, ignore_errors=True)
shutil.copytree(SAVE, os.path.join(stage, 'SteamDashArena'))
w = os.path.join(stage, 'SteamDashArena')
for d in ('playerdata', 'stats', 'advancements'):
    shutil.rmtree(os.path.join(w, d), ignore_errors=True)
for f in ('level.dat_old', 'session.lock'):
    if os.path.exists(os.path.join(w, f)): os.remove(os.path.join(w, f))
name, root = nbt.load(os.path.join(w, 'level.dat'))
fml = root.get('FML')
removed = []
if fml is not None and 'ModList' in fml:
    ml = fml['ModList']
    keep = [m for m in ml.value if m.scalar('ModId') not in QA_ONLY]
    removed = [m.scalar('ModId') for m in ml.value if m.scalar('ModId') in QA_ONLY]
    ml.value[:] = keep
    print('mods kept:', [(m.scalar('ModId'), m.scalar('ModVersion')) for m in keep])
open(os.path.join(w, 'level.dat'), 'wb').write(nbt.dumps(root, name))
print('removed from mod list:', removed)
readme = """STEAM DASH ARENA - test world for the Steam Dash Punch 2.0

1. Unzip. Put the SteamDashArena folder in your .minecraft/saves folder
   (or your instance's saves folder) and open it from Singleplayer.
2. You need Bijuu-Shinobi-Arts-8K-v3-SteamDash.jar in mods.
3. You spawn at the hub already wearing an issued Kurama set with a Kokuo
   helmet in your hotbar. The hub chest has a spare Kokuo set and more Kurama
   pieces (try the reverse combo: Kokuo set + a Kurama piece).

Stations (signs at each gold pad):
  SOUTH  Mountain run - tap B at the dummy: barrage, hit-stop, bullet time
         (the pigs in the pen slow down), launch through mountain, ridge,
         cliff splatter, molten scars.
  EAST   Overcharge - hold B about 1 second at the line of three dummies.
  WEST   Juggle - tap B, press B again while it flies (twice), then once
         more (or sneak + B) for the tail pin and spike crater.
  NORTH  Springboard - jump into the wall and press B while touching it.

Buttons: RESET ARENA (hub) rebuilds all terrain and respawns dummies;
RESPAWN DUMMIES; and a RESET button at each station.
Cheats are on. Resonance refills +2 a second while you wear the set.

Optional: test-config/hvbijuu_steamdash.cfg has a 3 second cooldown and
cheap costs for rapid testing - copy it into your config folder (remove it
again before normal play).
"""
open(os.path.join(stage, 'README-STEAM-DASH-ARENA.txt'), 'w').write(readme)
os.makedirs(os.path.join(stage, 'test-config'), exist_ok=True)
open(os.path.join(stage, 'test-config', 'hvbijuu_steamdash.cfg'), 'w').write("""# Steam Dash rapid-testing settings (copy into config/, remove for normal play)
dash {
    I:cooldownSeconds=3
    I:resonanceCost=10
    I:spikeResonanceCost=5
    I:springboardResonanceCost=2
}
combo {
    I:relayResonanceCost=5
}
""")
if os.path.exists(OUT): os.remove(OUT)
with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
    for dp, _, fs in os.walk(stage):
        for f in fs:
            p = os.path.join(dp, f); z.write(p, os.path.relpath(p, stage))
shutil.rmtree(stage)
print('wrote', OUT, os.path.getsize(OUT), 'bytes')
