#!/usr/bin/env python3
"""Overlay the Steam Dash classes and assets onto a copy of the owner's Bijuu Armor jar.
Every original entry is copied byte-for-byte (textures untouched); only the lang file and mcmod.info gain entries."""
import json, sys, zipfile, os
src, classes, out = sys.argv[1], sys.argv[2], sys.argv[3]
here = os.path.dirname(os.path.abspath(__file__))
add = {}
with zipfile.ZipFile(classes) as c:
    for n in c.namelist():
        if n.startswith('village/bijuu/') and n.endswith('.class'): add[n] = c.read(n)
for root in ('assets',):
    for d, _, fs in os.walk(os.path.join(here, root)):
        for f in fs:
            p = os.path.join(d, f); add[os.path.relpath(p, here).replace(os.sep, '/')] = open(p, 'rb').read()
lang_add = open(os.path.join(here, 'lang-add.txt'), 'rb').read()
# Files the overlay owns. A base jar that already carries an earlier overlay (as Codex's integrated jar
# does) has these replaced; anything else in the base is the owner's or Codex's and is never overwritten.
OWNED_CLASSES = ('Dash', 'SteamDash', 'MoltenScars', 'TimeBubble', 'Lariat', 'TerrainRegen')
def owned(n):
    if n.startswith('village/bijuu/') and n.endswith('.class'):
        return n[len('village/bijuu/'):].startswith(OWNED_CLASSES)
    return n == 'assets/hvbijuu/sounds.json' or n.startswith('assets/hvbijuu/sounds/steamdash/') or n.startswith('assets/hvbijuu/shaders/') \
        or n.startswith('assets/minecraft/shaders/program/hvb_')
lang_keys = {l.split(b'=', 1)[0] for l in lang_add.splitlines() if b'=' in l}
with zipfile.ZipFile(src) as z, zipfile.ZipFile(out + '.tmp', 'w') as o:
    names = z.namelist()
    clash = [n for n in add if n in names and not owned(n)]
    assert not clash, 'would overwrite original entries: %s' % clash
    replaced = [n for n in names if owned(n)]
    for info in z.infolist():
        if owned(info.filename):
            continue   # an earlier overlay's file: the current overlay supplies it (or it is retired)
        data = z.read(info.filename)
        if info.filename == 'assets/hvbijuu/lang/en_us.lang':
            kept = [l for l in data.rstrip(b'\n').splitlines() if l.split(b'=', 1)[0] not in lang_keys]
            data = b'\n'.join(kept) + b'\n' + lang_add
        elif info.filename == 'mcmod.info':
            mods = [m for m in json.loads(data) if m.get('modid') != 'hvbijuu_steamdash']
            mods.append({"modid": "hvbijuu_steamdash", "name": "Bijuu Steam Dash", "version": "2.1.0", "mcversion": "1.12.2",
                         "description": "Steam Dash Punch for Kurama + Kokuo: overcharge tiers that pierce a line, afterimage barrage, impact frames, bullet time, boil launch through mountains, juggle relays, tail pin and spike, molten scars, wall springboard and a cut-scene camera. Gyuki: the Lariat, a four-beat blink clothesline with hit-stop, inverted impact frames, a launch through soft blocks, a trench and terrain that grows back.",
                         "authorList": ["Hidden Block Village"], "dependencies": ["hvbijuu"]})
            data = (json.dumps(mods, indent=2) + '\n').encode()
        o.writestr(info, data)
    for n in sorted(add):
        o.writestr(zipfile.ZipInfo(n, date_time=(2026, 11, 20, 0, 0, 0)), add[n], compress_type=zipfile.ZIP_DEFLATED)
os.replace(out + '.tmp', out)
with zipfile.ZipFile(out) as z:
    assert z.testzip() is None
    print('wrote', out, len(z.namelist()), 'entries,', len(add), 'overlay files,', len(replaced), 'earlier overlay files replaced')
