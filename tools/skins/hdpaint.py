"""Native 256x256 painter for original shinobi skins (standard 64-unit skin UV, x4 resolution).
Each character is painted in layers: gradient base -> material grain -> folds -> seams and
stitching -> gear (straps, pouches, sashes, plates) -> portrait face -> hair locks -> edge
occlusion and rim light. All designs are original to Hidden Block Village."""
import math, random
from PIL import Image, ImageDraw

K = 4
def C(h, a=255): h = h.lstrip('#'); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)
def mul(c, f): return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)
def mix(a, b, t): t = max(0, min(1, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (a[3],)

def part(ox, oy, w, h, d):
    return {'top': (ox + d, oy, w, d), 'bottom': (ox + d + w, oy, w, d), 'right': (ox, oy + d, d, h), 'front': (ox + d, oy + d, w, h), 'left': (ox + d + w, oy + d, d, h), 'back': (ox + 2 * d + w, oy + d, w, h)}
HEAD = part(0, 0, 8, 8, 8); HAT = part(32, 0, 8, 8, 8)
BODY = part(16, 16, 8, 12, 4); JACKET = part(16, 32, 8, 12, 4)
RARM = part(40, 16, 4, 12, 4); RSLV = part(40, 32, 4, 12, 4)
LARM = part(32, 48, 4, 12, 4); LSLV = part(48, 48, 4, 12, 4)
RLEG = part(0, 16, 4, 12, 4); RPANT = part(0, 32, 4, 12, 4)
LLEG = part(16, 48, 4, 12, 4); LPANT = part(0, 48, 4, 12, 4)
SIDE = {'top': 1.10, 'front': 1.0, 'right': 0.88, 'left': 0.88, 'back': 0.80, 'bottom': 0.62}
WRAP = ('front', 'right', 'back', 'left')

class Painter:
    def __init__(s, seed):
        s.im = Image.new("RGBA", (256, 256), (0, 0, 0, 0)); s.d = ImageDraw.Draw(s.im); s.r = random.Random(seed)
    # ---- low level (coordinates in 64-units, floats allowed) ----
    def rect(s, x, y, w, h, c):
        x0, y0 = x * K, y * K; x1, y1 = max(x0, (x + w) * K - 1), max(y0, (y + h) * K - 1)
        s.d.rectangle([x0, y0, x1, y1], fill=c)
    def poly(s, pts, c): s.d.polygon([(px * K, py * K) for px, py in pts], fill=c)
    def line(s, pts, c, w=1): s.d.line([(px * K, py * K) for px, py in pts], fill=c, width=w)
    def dot(s, x, y, c): s.im.putpixel((int(x * K), int(y * K)), c)
    def pix(s, X, Y, c):
        if 0 <= X < 256 and 0 <= Y < 256: s.im.putpixel((X, Y), c)
    # ---- surfaces ----
    def fill(s, P, top, bottom=None, grain='cloth', faces=None):
        bottom = bottom or mul(top, 0.8)
        for k, (x, y, w, h) in P.items():
            if faces and k not in faces: continue
            X0, Y0, W, H = int(round(x * K)), int(round(y * K)), int(round(w * K)), int(round(h * K))
            for Y in range(Y0, Y0 + H):
                t = (Y - Y0) / max(1, H - 1)
                base = mul(mix(top, bottom, t), SIDE[k])
                for X in range(X0, X0 + W):
                    f = 1.0
                    if grain == 'cloth': f = 1.04 if (X + Y) % 2 == 0 else 0.96
                    elif grain == 'leather': f = 1 + (s.r.random() - 0.5) * 0.10
                    elif grain == 'metal': f = 1 + 0.06 * math.sin(Y * 1.7) + (0.15 if Y - Y0 < 2 else 0)
                    elif grain == 'skin': f = 1 + 0.02 * math.sin((X + Y) * 0.5)
                    elif grain == 'fur': f = 1 + (0.12 if (X * 3 + Y) % 5 == 0 else -0.04)
                    s.im.putpixel((X, Y), mul(base, f))
    def band(s, P, y0, y1, top, bottom=None, grain='cloth', faces=WRAP):
        sub = {k: (x, y + y0, w, y1 - y0) for k, (x, y, w, h) in P.items() if k in faces}
        s.fill(sub, top, bottom, grain)
    def folds(s, P, n, strength=0.82, faces=('front', 'back')):
        for k in faces:
            x, y, w, h = P[k]
            for i in range(n):
                sx = x + w * (0.2 + 0.6 * s.r.random()); sy = y + h * (0.15 + 0.4 * s.r.random()); ln = h * (0.25 + 0.3 * s.r.random()); bend = (s.r.random() - 0.5) * 1.6
                prev = None
                for t in range(int(ln * K)):
                    u = t / (ln * K)
                    X = int((sx + bend * math.sin(u * math.pi)) * K); Y = int(sy * K) + t
                    for dx, f in ((0, strength), (1, 1 + (1 - strength) * 0.8)):
                        if x * K <= X + dx < (x + w) * K and Y < (y + h) * K:
                            c = s.im.getpixel((X + dx, Y))
                            if c[3]: s.im.putpixel((X + dx, Y), mul(c, f))
    def stitch(s, P, yrow, c, faces=WRAP):
        for k in faces:
            x, y, w, h = P[k]; Y = int((y + yrow) * K)
            for X in range(x * K + 1, (x + w) * K - 1, 3): s.pix(X, Y, c)
    def wraps(s, P, y0, y1, c):
        for k in WRAP:
            x, y, w, h = P[k]
            for Y in range(int((y + y0) * K), int((y + y1) * K)):
                for X in range(x * K, (x + w) * K):
                    phase = (X - Y * 2) % 10
                    f = 1.08 if phase < 4 else 0.86 if phase == 9 else 0.98
                    s.im.putpixel((X, Y), mul(c, f * SIDE[k]))
    def strap(s, P, c, x0, y0, x1, y1, width=1.2):
        x, y, w, h = P['front']
        s.line([(x + x0, y + y0), (x + x1, y + y1)], mul(c, 0.6), int(width * K + 2))
        s.line([(x + x0, y + y0), (x + x1, y + y1)], c, int(width * K))
        s.line([(x + x0, y + y0 - 0.15), (x + x1, y + y1 - 0.15)], mul(c, 1.3), 1)
    def pouch(s, x, y, w, h, c):
        s.rect(x, y, w, h, mul(c, 0.55)); s.rect(x + 0.2, y + 0.2, w - 0.4, h - 0.3, c)
        s.rect(x + 0.2, y + 0.2, w - 0.4, h * 0.38, mul(c, 1.18)); s.line([(x + 0.2, y + 0.2 + h * 0.38), (x + w - 0.2, y + 0.2 + h * 0.38)], mul(c, 0.5), 1)
        s.rect(x + w / 2 - 0.3, y + h * 0.3, 0.6, 0.5, C('#c8b080'))
    def plate(s, x, y, w, h, metal, glyph):
        s.rect(x - 0.2, y - 0.2, w + 0.4, h + 0.4, mul(metal, 0.45)); s.rect(x, y, w, h, metal)
        s.rect(x, y, w, h * 0.3, mul(metal, 1.25)); s.rect(x, y + h * 0.8, w, h * 0.2, mul(metal, 0.75))
        for cx in (x + 0.35, x + w - 0.35):
            for cy in (y + 0.35, y + h - 0.35): s.dot(cx, cy, mul(metal, 0.5))
        glyph(s, x + w / 2, y + h / 2, mul(metal, 0.4))
    # ---- finishing ----
    def finish(s, parts):
        for P in parts:
            for k, (x, y, w, h) in P.items():
                X0, Y0, W, H = int(round(x * K)), int(round(y * K)), int(round(w * K)), int(round(h * K))
                for Y in range(Y0, Y0 + H):
                    for X in range(X0, X0 + W):
                        c = s.im.getpixel((X, Y))
                        if not c[3]: continue
                        e = min(X - X0, X0 + W - 1 - X, Y - Y0, Y0 + H - 1 - Y)
                        f = 0.78 if e == 0 else 0.9 if e == 1 else 1.0
                        if Y - Y0 == 1 and k in WRAP: f *= 1.1
                        s.im.putpixel((X, Y), mul(c, f))
    # ---- head ----
    def head(s, skin, hair, iris, brows='calm', mouth='neutral', blush=0.0, mark=None, beard=None):
        s.fill(HEAD, mul(skin, 1.04), mul(skin, 0.9), 'skin')
        x, y, w, h = HEAD['front']
        s.rect(x, y + 6.6, 8, 1.4, mul(skin, 0.86))                                 # jaw shade
        if blush: 
            for cx in (x + 1.6, x + 6.4): s.d.ellipse([(cx - 0.8) * K, (y + 5.1) * K, (cx + 0.8) * K, (y + 5.8) * K], fill=mix(skin, C('#e88a8a'), blush))
        s.poly([(x + 3.7, y + 4.2), (x + 4.3, y + 4.2), (x + 4.5, y + 5.4), (x + 3.6, y + 5.4)], mul(skin, 0.86))  # nose
        s.dot(x + 4.2, y + 4.6, mul(skin, 1.12))
        if mouth == 'grin':
            s.poly([(x + 2.8, y + 6.0), (x + 5.2, y + 6.0), (x + 4.6, y + 6.6), (x + 3.4, y + 6.6)], C('#5a2a2a')); s.rect(x + 3.0, y + 6.0, 2.0, 0.25, C('#f4f0ea'))
        elif mouth == 'stern': s.line([(x + 3, y + 6.25), (x + 5.2, y + 6.15)], mul(skin, 0.55), 2)
        else: s.line([(x + 3.2, y + 6.2), (x + 4.8, y + 6.2)], mul(skin, 0.6), 2); s.line([(x + 3.4, y + 6.55), (x + 4.6, y + 6.55)], mul(skin, 0.92), 1)
        for ex, flip in ((x + 1.0, 1), (x + 5.0, -1)):                               # eyes
            ey = y + 3.4
            s.d.ellipse([ex * K, ey * K, (ex + 2) * K - 1, (ey + 1.3) * K], fill=C('#f6f6fa'))
            ix = ex + (1.25 if flip == 1 else 0.75)
            s.d.ellipse([(ix - 0.62) * K, (ey - 0.05) * K, (ix + 0.62) * K, (ey + 1.3) * K], fill=mul(iris, 0.6))
            s.d.ellipse([(ix - 0.45) * K, (ey + 0.15) * K, (ix + 0.45) * K, (ey + 1.2) * K], fill=iris)
            s.d.ellipse([(ix - 0.2) * K, (ey + 0.45) * K, (ix + 0.2) * K, (ey + 0.95) * K], fill=C('#141018'))
            s.dot(ix - 0.3, ey + 0.3, C('#ffffff')); s.dot(ix + 0.25, ey + 0.95, C('#dfe8ff'))
            s.line([(ex - 0.1, ey + 0.05), (ex + 2.1, ey - 0.05)], C('#1a1416'), 2)   # lash line
            s.dot(ex + (2.05 if flip == 1 else -0.1), ey + 0.1, C('#1a1416'))
            by = {'calm': (0, 0), 'fierce': (-0.35, 0.35), 'stern': (0.0, 0.25), 'soft': (0.25, -0.1)}[brows]
            b0, b1 = (ey - 0.75 + by[0], ey - 0.75 + by[1]) if flip == 1 else (ey - 0.75 + by[1], ey - 0.75 + by[0])
            s.line([(ex, b0), (ex + 2, b1)], mul(hair, 0.65), 3)
        if beard: beard(s, x, y)
        if mark: mark(s, x, y)
        for k in ('right', 'left', 'back', 'top'):                                   # scalp
            hx, hy, hw, hh = HEAD[k]
            s.fill({k: (hx, hy, hw, hh if k in ('back', 'top') else 3)}, mul(hair, 1.05), mul(hair, 0.8), 'fur')
        s.rect(HEAD['right'][0] + 5, HEAD['right'][1] + 3, 3, 3, mul(hair, 0.85)); s.rect(HEAD['left'][0], HEAD['left'][1] + 3, 3, 3, mul(hair, 0.85))
        s.rect(HEAD['right'][0] + 2, HEAD['right'][1] + 4, 1.2, 1.4, mul(skin, 0.8))  # ears
        s.rect(HEAD['left'][0] + 4.8, HEAD['left'][1] + 4, 1.2, 1.4, mul(skin, 0.8))
    def locks(s, face_key, hair, tips, length, spread=1.0, fringe=True):
        x, y, w, h = HAT[face_key]
        hi, lo = mul(hair, 1.25), mul(hair, 0.62)
        n = len(tips)
        for i, t in enumerate(tips):
            cx = x + (i + 0.5) * w / n
            L = length * t
            pts = [(cx - w / n * 0.62 * spread, y), (cx + w / n * 0.62 * spread, y), (cx + (0.4 if i % 2 else -0.4), y + L)]
            s.poly(pts, lo); s.poly([(p[0] * 0.98 + cx * 0.02, p[1] * 0.96 + y * 0.04) for p in pts], hair)
            s.line([(cx - 0.15, y + 0.2), (cx + (0.25 if i % 2 else -0.25), y + L * 0.75)], hi, 1)
        if fringe: s.rect(x, y, w, 1.0, hair); s.line([(x, y + 0.2), (x + w, y + 0.2)], hi, 1)
    def hair_cap(s, hair, back_len=6):
        hx, hy, hw, hh = HAT['top']; s.fill({'top': HAT['top']}, mul(hair, 1.1), mul(hair, 0.9), 'fur')
        for k in ('right', 'left', 'back'):
            x, y, w, h = HAT[k]
            s.fill({k: (x, y, w, 2)}, mul(hair, 1.05), hair, 'fur')
    def headband(s, cloth, metal, glyph):
        for k in ('front', 'right', 'left', 'back'):
            x, y, w, h = HAT[k]; s.rect(x, y + 1.6, w, 1.0, mul(cloth, SIDE[k])); s.line([(x, y + 1.65), (x + w, y + 1.65)], mul(cloth, 1.25 * SIDE[k]), 1)
        x, y, w, h = HAT['front']; s.plate(x + 1.6, y + 1.3, 4.8, 1.6, metal, glyph)
        x, y, w, h = HAT['back']
        s.poly([(x + 3.6, y + 2.4), (x + 4.4, y + 2.4), (x + 3.2, y + 5.2), (x + 2.6, y + 5.0)], cloth)
        s.poly([(x + 3.8, y + 2.4), (x + 4.6, y + 2.4), (x + 5.6, y + 5.4), (x + 5.0, y + 5.6)], mul(cloth, 0.85))
    def hands(s, P, skin, length=2):
        for k in WRAP:
            x, y, w, h = P[k]; s.fill({k: (x, y + h - length, w, length)}, skin, mul(skin, 0.88), 'skin')
            s.line([(x, y + h - length), (x + w, y + h - length)], mul(skin, 0.7), 1)
        s.fill({'bottom': P['bottom']}, mul(skin, 0.8), mul(skin, 0.7), 'skin')
    def shoes(s, P, c, sole=C('#1a1614')):
        for k in WRAP:
            x, y, w, h = P[k]; s.fill({k: (x, y + h - 2.2, w, 2.2)}, c, mul(c, 0.7), 'leather')
            s.rect(x, y + h - 0.5, w, 0.5, sole)
        s.fill({'bottom': P['bottom']}, sole, sole, 'leather')

# ---- original village glyphs ----
def g_ember(s, cx, cy, c):  # three-flame crown
    for dx in (-0.9, 0, 0.9):
        s.poly([(cx + dx - 0.3, cy + 0.45), (cx + dx + 0.3, cy + 0.45), (cx + dx, cy - 0.55 - (0.15 if dx == 0 else 0))], c)
def g_wave(s, cx, cy, c):   # twin crescent waves
    for dx in (-0.7, 0.7): s.d.arc([(cx + dx - 0.6) * K, (cy - 0.5) * K, (cx + dx + 0.6) * K, (cy + 0.6) * K], 200, 340, fill=c, width=2)
def g_stone(s, cx, cy, c):  # stacked cairn
    for i, (w, dy) in enumerate(((1.6, 0.35), (1.1, -0.1), (0.6, -0.5))): s.rect(cx - w / 2, cy + dy - 0.15, w, 0.35, c)
def g_dawn(s, cx, cy, c):   # rising half-sun with rays
    s.d.pieslice([(cx - 0.6) * K, (cy - 0.4) * K, (cx + 0.6) * K, (cy + 0.8) * K], 180, 360, fill=c)
    for a in (200, 240, 270, 300, 340):
        r = math.radians(a); s.line([(cx + math.cos(r) * 0.8, cy + 0.2 + math.sin(r) * 0.8), (cx + math.cos(r) * 1.1, cy + 0.2 + math.sin(r) * 1.1)], c, 1)
def g_dusk(s, cx, cy, c):   # crescent moon and star
    s.d.ellipse([(cx - 0.6) * K, (cy - 0.6) * K, (cx + 0.6) * K, (cy + 0.6) * K], fill=c)
    s.d.ellipse([(cx - 0.3) * K, (cy - 0.7) * K, (cx + 0.8) * K, (cy + 0.5) * K], fill=(0, 0, 0, 0))
    s.dot(cx + 0.9, cy - 0.3, c)
