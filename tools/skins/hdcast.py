import sys; sys.path.insert(0, sys.argv[1])
from hdpaint import *
OUT = sys.argv[2]
ALL = [HEAD, HAT, BODY, JACKET, RARM, RSLV, LARM, LSLV, RLEG, RPANT, LLEG, LPANT]

def kaito():  # Ember Ward genin: rust hair swept up, charcoal crossover top, ember sash, wrapped limbs
    p = Painter(11); skin, hair = C('#e8b48e'), C('#9a3e22'); top = C('#30323a')
    p.fill(BODY, mul(top, 1.1), mul(top, 0.8)); p.folds(BODY, 3)
    x, y, w, h = BODY['front']
    p.poly([(x, y), (x + 5, y), (x + 3, y + 7), (x, y + 7)], mul(top, 1.12)); p.line([(x + 5, y), (x + 3, y + 7)], mul(top, 0.6), 2)
    p.band(BODY, 7, 9, C('#e8702a'), C('#b8501a')); p.stitch(BODY, 7.1, C('#ffc890')); p.stitch(BODY, 8.9, C('#7a3010'))
    p.poly([(x + 1, y + 8.5), (x + 2.4, y + 8.5), (x + 2.2, y + 11.5), (x + 1.2, y + 11.8)], C('#c85a1e'))
    p.fill(RLEG, C('#3c3e46'), C('#2a2c32')); p.fill(LLEG, C('#3c3e46'), C('#2a2c32')); p.folds(RLEG, 1); p.folds(LLEG, 1)
    for A in (RARM, LARM): p.fill(A, skin, mul(skin, 0.9), 'skin'); p.wraps(A, 5, 10, C('#ddd4be')); p.hands(A, skin)
    for L in (RLEG, LLEG): p.wraps(L, 6, 10, C('#d4caB4' if False else '#d4cab4')); p.shoes(L, C('#3a2c22'))
    p.pouch(RLEG['front'][0] + 0.3, RLEG['front'][1] + 1.5, 3.4, 2.4, C('#5a4a3a'))
    def scar(s, x, y): s.line([(x + 6.2, y + 4.9), (x + 6.9, y + 5.9)], C('#c48068'), 2)
    p.head(skin, hair, C('#e07a2a'), brows='fierce', mouth='grin', mark=scar)
    p.hair_cap(hair)
    p.locks('front', hair, [0.6, 0.9, 0.5, 0.8, 0.4, 0.7, 0.55, 0.9], 2.4)
    for k in ('right', 'left'): p.locks(k, hair, [0.9, 0.6, 1.0, 0.7, 0.8, 0.5, 0.9, 0.6], 4.0)
    p.locks('back', hair, [1.0, 0.8, 1.1, 0.9, 1.0, 0.8, 1.1, 0.9], 5.0)
    xh, yh, _, _ = HAT['top']
    for i in range(6): p.poly([(xh + i * 1.3, yh + 8), (xh + i * 1.3 + 1.3, yh + 8), (xh + i * 1.3 + 0.9, yh + 1.5 + (i % 2))], mul(hair, 1.15))
    p.headband(C('#6a2a1a'), C('#b8bec8'), g_ember)
    p.finish(ALL); p.im.save(OUT + '/kaito_ember_genin.png')

def sayo():  # Current Ward kunoichi: teal hair, long braid, slate high-collar coat with sky trim
    p = Painter(22); skin, hair = C('#f2d0b6'), C('#2a8c8e'); coat = C('#4a5c72'); trim = C('#a4dcec')
    for P in (BODY, RARM, LARM): p.fill(P, mul(coat, 1.1), mul(coat, 0.78)); p.folds(P, 2)
    p.fill(JACKET, mul(coat, 1.05), mul(coat, 0.72)); p.folds(JACKET, 3)
    for P in (BODY, JACKET): p.band(P, 0, 1, trim); p.band(P, 11, 12, trim)
    for A in (RARM, LARM): p.band(A, 9, 10, trim); p.hands(A, skin)
    x, y, w, h = JACKET['front']
    p.rect(x, y, w, 1.6, mul(coat, 1.25)); p.line([(x, y + 1.6), (x + w, y + 1.6)], trim, 1)
    p.line([(x + 4, y + 1.6), (x + 4, y + 12)], mul(trim, 0.85), 2)
    for ty in (3.5, 6, 8.5): p.rect(x + 4.3, ty + y, 1.0, 0.5, C('#eef0f6')); p.line([(x + 3.7, y + ty + 0.25), (x + 4.3, y + ty + 0.25)], C('#eef0f6'), 1)
    p.stitch(JACKET, 10.6, mul(trim, 1.1))
    for L in (RLEG, LLEG): p.fill(L, C('#2c3444'), C('#1e2430')); p.shoes(L, C('#1c2230'))
    p.head(skin, hair, C('#7ec4e6'), brows='soft', blush=0.35)
    p.hair_cap(hair)
    p.locks('front', hair, [1.0, 0.6, 0.3, 0.2, 0.2, 0.3, 0.6, 1.0], 2.8)
    for k in ('right', 'left'): p.locks(k, hair, [1.0, 1.0, 1.1, 1.0, 1.1, 1.0, 1.0, 0.9], 5.6)
    p.locks('back', hair, [0.9] * 8, 4.0)
    bx, by, _, _ = JACKET['back']
    for i in range(10):
        cy = by + 0.5 + i * 1.05; off = 0.35 if i % 2 else -0.35
        p.d.ellipse([(bx + 3.2 + off) * K, cy * K, (bx + 4.8 + off) * K, (cy + 1.2) * K], fill=mul(hair, 0.95 if i % 2 else 0.8))
        p.line([(bx + 3.6 + off, cy + 0.3), (bx + 4.2 + off, cy + 0.9)], mul(hair, 1.3), 1)
    p.rect(bx + 3.4, by + 10.8, 1.4, 0.8, trim)
    hx, hy, _, _ = HAT['front']
    p.plate(hx + 2, hy + 0.2, 4, 1.2, C('#c0c8d2'), g_wave)
    p.finish(ALL); p.im.save(OUT + '/sayo_current_kunoichi.png')

def garran():  # Flint Ward jonin: buzz cut, olive pouch vest, crimson shoulder guard, nose scar
    p = Painter(33); skin, hair = C('#c48c6c'), C('#2a241e'); shirt = C('#383a42'); vest = C('#5e6c3c')
    for P in (BODY, RARM, LARM): p.fill(P, mul(shirt, 1.1), mul(shirt, 0.8))
    p.fill(JACKET, mul(vest, 1.1), mul(vest, 0.78), faces=('front', 'back', 'right', 'left', 'top')); p.folds(JACKET, 2)
    x, y, w, h = JACKET['front']
    p.rect(x, y, w, 1.8, mul(vest, 1.25)); p.line([(x, y + 1.8), (x + w, y + 1.8)], mul(vest, 0.6), 1)
    p.line([(x + 4, y + 1.8), (x + 4, y + 12)], mul(vest, 0.55), 2)
    for py in (3, 6.2):
        for px in (0.4, 4.6): p.pouch(x + px, y + py, 3.0, 2.6, mul(vest, 0.92))
    p.stitch(JACKET, 11.4, mul(vest, 1.4))
    for k in WRAP:
        sx, sy, sw, sh = RSLV[k]; p.fill({k: (sx, sy, sw, 3.2)}, C('#8a2e2a'), C('#5a1c1a'), 'leather')
        p.line([(sx, sy + 3.2), (sx + sw, sy + 3.2)], C('#c8a060'), 1)
    for A in (RARM, LARM): p.hands(A, skin, 2.4); p.band(A, 8, 9.4, C('#2a2a2e'))
    for L in (RLEG, LLEG): p.fill(L, C('#3a3c44'), C('#2a2c32')); p.wraps(L, 7, 10, C('#d0c8b4')); p.shoes(L, C('#2a2422'))
    def scar(s, x, y): s.line([(x + 2.2, y + 3.9), (x + 5.6, y + 5.1)], C('#9a5a48'), 2)
    p.head(skin, hair, C('#6e4a2a'), brows='stern', mouth='stern', mark=scar)
    for k in ('top', 'right', 'left', 'back'):
        hx, hy, hw, hh = HEAD[k]; p.fill({k: (hx, hy, hw, hh if k == 'top' else 2.2)}, mul(hair, 1.2), hair, 'fur')
    p.headband(C('#26262a'), C('#a8aeb6'), g_stone)
    p.finish(ALL); p.im.save(OUT + '/garran_flint_jonin.png')

def lantern_sage():  # Dawn Ward sage: ivory robe with gold trim, wide woven hat, long white beard
    p = Painter(44); skin, hair = C('#d8ae8e'), C('#e8e8e0'); robe = C('#f0e8d6'); gold = C('#d8a83a')
    for P in (BODY, RARM, LARM, RLEG, LLEG): p.fill(P, mul(robe, 1.05), mul(robe, 0.8)); p.folds(P, 2, 0.86)
    p.fill(JACKET, mul(robe, 1.0), mul(robe, 0.75)); p.folds(JACKET, 3, 0.86)
    x, y, w, h = JACKET['front']
    p.poly([(x + 1.5, y), (x + 2.5, y), (x + 4, y + 5.5), (x + 3.2, y + 6)], gold); p.poly([(x + 6.5, y), (x + 5.5, y), (x + 4, y + 5.5), (x + 4.8, y + 6)], gold)
    p.band(JACKET, 6, 7, gold); p.band(JACKET, 7, 8, C('#8a6a3a')); p.stitch(JACKET, 6.9, C('#fff0b0'))
    p.d.ellipse([(x + 3.2) * K, (y + 8.6) * K, (x + 4.8) * K, (y + 10.2) * K], fill=mul(gold, 0.9)); g_dawn(p, x + 4, y + 9.3, C('#7a5a20'))
    for A in (RARM, LARM): p.band(A, 9.6, 10.4, gold); p.hands(A, skin, 1.6)
    for L in (RLEG, LLEG): p.shoes(L, C('#5a4632'))
    def beard(s, x, y):
        s.poly([(x + 1, y + 6.2), (x + 7, y + 6.2), (x + 5.8, y + 8), (x + 2.2, y + 8)], C('#ecece4'))
        for i in range(5): s.line([(x + 1.8 + i * 1.1, y + 6.4), (x + 2.0 + i * 1.0, y + 7.8)], C('#c8c8c0'), 1)
        s.line([(x + 2.8, y + 5.9), (x + 5.2, y + 5.9)], C('#f4f4ee'), 2)
    p.head(skin, hair, C('#d8b85a'), brows='soft', beard=beard)
    bx, by, _, _ = BODY['front']
    p.poly([(bx + 2.2, by), (bx + 5.8, by), (bx + 4.8, by + 3.5), (bx + 3.2, by + 3.5)], C('#e4e4dc'))   # beard over chest
    straw = C('#caa86a')
    for k in ('top', 'right', 'left', 'back', 'front'):
        hx, hy, hw, hh = HAT[k]
        rows = hh if k == 'top' else 1.6
        p.fill({k: (hx, hy, hw, rows)}, mul(straw, 1.08), mul(straw, 0.85), 'cloth')
        for i in range(int(hw * 2)): p.line([(hx + i * 0.5, hy), (hx + i * 0.5 + 0.4, hy + rows)], mul(straw, 0.8), 1)
    hx, hy, _, _ = HAT['front']
    for k in ('front', 'right', 'left', 'back'):
        hx, hy, hw, hh = HAT[k]; p.rect(hx, hy + 1.6, hw, 0.5, C('#7a5a32'))
    p.finish(ALL); p.im.save(OUT + '/lantern_sage_dawn.png')

def rook():  # Dusk operative: black weave suit, segmented pale plate, original crow mask, hood
    p = Painter(55); skin, hair = C('#d8b49a'), C('#5a5a64'); suit = C('#1e2026'); plate = C('#b4bac4'); acc = C('#7e4cc0')
    for P in (BODY, RARM, LARM, RLEG, LLEG): p.fill(P, mul(suit, 1.2), mul(suit, 0.85)); p.folds(P, 1, 0.8)
    for k in ('front', 'back', 'right', 'left'):
        x, y, w, h = JACKET[k]
        for seg in range(3):
            p.fill({k: (x, y + seg * 2.4, w, 2.2)}, mul(plate, 1.1 - seg * 0.06), mul(plate, 0.85 - seg * 0.06), 'metal')
            p.line([(x, y + seg * 2.4 + 2.2), (x + w, y + seg * 2.4 + 2.2)], mul(plate, 0.45), 1)
        p.rect(x, y + 7.4, w, 0.8, acc)
    for P in (RSLV, LSLV):
        for k in WRAP:
            x, y, w, h = P[k]; p.fill({k: (x, y, w, 3.2)}, plate, mul(plate, 0.8), 'metal'); p.fill({k: (x, y + 6, w, 3.4)}, mul(plate, 0.95), mul(plate, 0.75), 'metal')
    for A in (RARM, LARM): p.hands(A, C('#2a2a32'), 2.4)
    for L in (RLEG, LLEG): p.band(L, 6, 7, acc); p.shoes(L, C('#121216'))
    p.strap(BODY, C('#3a3a44'), 0.4, 0.4, 7.4, 11.2)
    p.head(skin, hair, C('#6a6a74'))
    hood = C('#26262e')
    for k in ('top', 'right', 'left', 'back'):
        hx, hy, hw, hh = HAT[k]; p.fill({k: (hx, hy, hw, hh if k in ('top', 'back') else 8)}, mul(hood, 1.2), mul(hood, 0.8), 'cloth')
    hx, hy, _, _ = HAT['front']
    p.rect(hx, hy, 8, 1.2, mul(hood, 1.1))
    p.poly([(hx + 0.8, hy + 1.2), (hx + 7.2, hy + 1.2), (hx + 7.0, hy + 5.4), (hx + 4.6, hy + 7.9), (hx + 3.4, hy + 7.9), (hx + 1.0, hy + 5.4)], C('#f0f0ea'))
    p.poly([(hx + 0.8, hy + 1.2), (hx + 7.2, hy + 1.2), (hx + 7.1, hy + 2.4), (hx + 0.9, hy + 2.4)], C('#ffffff'))
    for ex in (2.1, 5.9): p.poly([(ex - 1.0, hy + 3.6), (ex + 1.0, hy + 3.4 if ex < 4 else hy + 3.6), (ex + 0.6, hy + 4.3), (ex - 0.6, hy + 4.3)], C('#0e0e12'))
    p.poly([(hx + 3.3, hy + 4.4), (hx + 4.7, hy + 4.4), (hx + 4.0, hy + 7.6)], C('#3a3a40')); p.line([(hx + 4.0, hy + 4.5), (hx + 4.0, hy + 7.3)], C('#6a6a72'), 1)
    for (a, b) in ((1.4, 2.8), (6.6, 2.8), (1.6, 5.0), (6.4, 5.0)): p.line([(hx + a, hy + b), (hx + a + (0.6 if a < 4 else -0.6), hy + b + 0.9)], acc, 2)
    p.finish(ALL); p.im.save(OUT + '/rook_dusk_operative.png')

def ash_regent():  # boss: smouldering layered cloak, horned bone helm, ember eyes, cracked ash skin
    p = Painter(66); skin, hair = C('#86868c'), C('#2a2626'); cloak = C('#3a3438'); ember = C('#ff7a2a')
    for P in (BODY, RARM, LARM, RLEG, LLEG): p.fill(P, mul(C('#2a2428'), 1.1), mul(C('#2a2428'), 0.8))
    for P in (JACKET, RSLV, LSLV, RPANT, LPANT):
        p.fill(P, mul(cloak, 1.15), mul(cloak, 0.7)); p.folds(P, 3, 0.78)
        for k in WRAP:
            x, y, w, h = P[k]
            for X in range(x * K, (x + w) * K):
                ragged = int(2 + 3 * abs(math.sin(X * 0.9)))
                for t in range(ragged): p.pix(X, (y + h) * K - 1 - t, mix(ember, C('#5a1a08'), t / ragged))
    x, y, w, h = JACKET['front']
    pts = [(x + 4, y + 2), (x + 3.4, y + 4), (x + 4.4, y + 6), (x + 3.6, y + 8.5), (x + 4.2, y + 10)]
    p.line(pts, mul(ember, 0.5), 4); p.line(pts, ember, 2); p.line(pts, C('#ffe0a0'), 1)
    p.rect(x, y + 5.8, w, 0.9, C('#5a4a3a')); p.rect(x + 3.3, y + 5.6, 1.4, 1.3, C('#c8a060'))
    for A in (RARM, LARM): p.hands(A, C('#56565c'), 2.2)
    def veins(s, x, y):
        for (a, b, c2, d) in ((1.2, 5.4, 2.0, 6.6), (6.0, 5.2, 6.6, 6.4), (3.0, 0.4, 3.6, 1.6)): s.line([(x + a, y + b), (x + c2, y + d)], mul(ember, 0.7), 2)
    p.head(skin, hair, ember, brows='fierce', mouth='stern', mark=veins)
    bone = C('#dccfb8')
    for k in ('top', 'right', 'left', 'back', 'front'):
        hx, hy, hw, hh = HAT[k]; p.fill({k: (hx, hy, hw, hh if k == 'top' else 2.4)}, mul(C('#4a4248'), 1.15), mul(C('#4a4248'), 0.8), 'metal')
    hx, hy, _, _ = HAT['front']
    for side in (0, 1):
        bx = hx + (0.2 if side == 0 else 6.2)
        p.poly([(bx, hy + 2.6), (bx + 1.6, hy + 2.6), (bx + (0.3 if side == 0 else 1.3), hy - 0.0)], bone)
        p.line([(bx + 0.5, hy + 2.4), (bx + (0.35 if side == 0 else 1.25), hy + 0.4)], C('#f4ecdc'), 1)
    p.d.ellipse([(hx + 3.2) * K, (hy + 0.6) * K, (hx + 4.8) * K, (hy + 2.0) * K], fill=ember); p.dot(hx + 3.6, hy + 0.9, C('#ffe8c0'))
    for k in ('right', 'left'):
        sx, sy, sw, sh = HAT[k]; p.poly([(sx + 2, sy + 2.4), (sx + 6, sy + 2.4), (sx + 4, sy - 0.2)], bone)
    p.finish(ALL); p.im.save(OUT + '/ash_regent_boss.png')

for f in (kaito, sayo, garran, lantern_sage, rook, ash_regent): f()
