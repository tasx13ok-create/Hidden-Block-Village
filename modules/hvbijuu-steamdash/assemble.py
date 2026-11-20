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
with zipfile.ZipFile(src) as z, zipfile.ZipFile(out + '.tmp', 'w') as o:
    names = z.namelist()
    clash = [n for n in add if n in names]
    assert not clash, 'would overwrite original entries: %s' % clash
    for info in z.infolist():
        data = z.read(info.filename)
        if info.filename == 'assets/hvbijuu/lang/en_us.lang':
            data = data.rstrip(b'\n') + b'\n' + lang_add
        elif info.filename == 'mcmod.info':
            mods = json.loads(data)
            mods.append({"modid": "hvbijuu_steamdash", "name": "Bijuu Steam Dash", "version": "1.0.0", "mcversion": "1.12.2",
                         "description": "Steam Dash Punch for Kurama + Kokuo: overheat telegraph, sonic blink, impact frames, boil launch through mountains, wall springboard and the spike finisher.",
                         "authorList": ["Hidden Block Village"], "dependencies": ["hvbijuu"]})
            data = (json.dumps(mods, indent=2) + '\n').encode()
        o.writestr(info, data)
    for n in sorted(add):
        o.writestr(zipfile.ZipInfo(n, date_time=(2026, 11, 20, 0, 0, 0)), add[n], compress_type=zipfile.ZIP_DEFLATED)
os.replace(out + '.tmp', out)
with zipfile.ZipFile(out) as z:
    assert z.testzip() is None
    print('wrote', out, len(z.namelist()), 'entries,', len(add), 'added')
