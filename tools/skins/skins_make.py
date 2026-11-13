import sys; sys.path.insert(0, sys.argv[1])
from skingen import *
OUT=sys.argv[2]

def ember_mark(s,x,y):      # Ember Ward: three dots in a triangle
    s.px(x+3,y+1,C('#3a2a1a')); s.px(x+4,y+1,C('#3a2a1a')); s.px(x+3,y+2,C('#3a2a1a')) if False else None
def sym_ember(s,x,y):
    d=C('#4a2a14'); s.px(x+3,y+2,d); s.px(x+4,y+2,d); s.px(x+4,y+1,d) if s.get(x+4,y+1)[3] else None
def sym_wave(s,x,y):
    d=C('#1a3a4a'); s.px(x+2,y+2,d); s.px(x+3,y+2,shade(d,1.3)); s.px(x+5,y+2,d)
def sym_stone(s,x,y):
    d=C('#2a2620'); s.px(x+3,y+2,d); s.px(x+4,y+2,d)

def kaito():
    s=Skin(1); skin=C('#e8b48e'); hair=C('#8a3a22')
    top=C('#2e2f36'); sash=C('#e0662a'); pants=C('#3a3c44')
    box(s,BODY,top); box(s,RARM,skin); box(s,LARM,skin); box(s,RLEG,pants); box(s,LLEG,pants)
    vgrad(s,BODY,top,shade(top,0.8))
    bands(s,BODY,[7,8],sash)                                  # ember sash
    fx,fy,_,_=BODY['front']
    for r in range(0,7): s.px(fx+3+(r%2),fy+r,shade(top,1.25))  # zipper/crossover seam
    s.px(fx+1,fy+9,shade(sash,0.8)); s.px(fx+1,fy+10,shade(sash,0.7))  # sash tail
    wraps(s,RARM,C('#d8d0bc'),5,10); wraps(s,LARM,C('#d8d0bc'),5,10)
    wraps(s,RLEG,C('#cfc6b0'),6,10); wraps(s,LLEG,C('#cfc6b0'),6,10)
    hands(s,RARM,skin); hands(s,LARM,skin); shoes(s,RLEG,C('#2a221c')); shoes(s,LLEG,C('#2a221c'))
    face(s,skin,hair,C('#1a1a1a'),C('#d8702a'))
    x,y,_,_=HEAD['front']; s.px(x+6,y+5,C('#c88a6a')); s.px(x+6,y+6,C('#c88a6a'))   # small cheek scar
    spiky_hat(s,hair,length=4); headband(s,C('#5a2a1a'),C('#b8bcc4'),sym_ember)
    box(s,RSLV,(0,0,0,0)) if False else None
    s.save(OUT+'/kaito_ember_genin.png')

def sayo():
    s=Skin(2); skin=C('#f0ccb0'); hair=C('#2a8a8a')
    coat=C('#4a5a6e'); trim=C('#9fd8e8'); pants=C('#2a3240')
    box(s,BODY,coat); box(s,RARM,coat); box(s,LARM,coat); box(s,RLEG,pants); box(s,LLEG,pants)
    vgrad(s,BODY,shade(coat,1.05),shade(coat,0.75))
    bands(s,BODY,[0],trim); bands(s,BODY,[11],trim); bands(s,RARM,[9],trim); bands(s,LARM,[9],trim)
    fx,fy,_,_=BODY['front']
    for r in range(12): s.px(fx+4,fy+r,shade(trim,0.8))          # coat opening
    for r in (3,6,9): s.px(fx+5,fy+r,C('#e8e8f0'))                # toggles
    hands(s,RARM,skin); hands(s,LARM,skin); shoes(s,RLEG,C('#1e2430')); shoes(s,LLEG,C('#1e2430'))
    face(s,skin,hair,C('#1a2a3a'),C('#8ac8e8'),mouth=C('#c87a7a'))
    box(s,HAT,(0,0,0,0)) if False else None
    for k in ('top','back','right','left'):                     # sleek hair + long braid on back
        x,y,w,h=HAT[k]; s.rect(x,y,w,2 if k!='top' else h,shade(hair,SIDE[k]),0.08)
    bx,by,_,_=JACKET['back']
    for r in range(10): s.px(bx+3,by+r,shade(hair,0.85)); s.px(bx+4,by+r,shade(hair,0.95 if r%2 else 0.75))
    for i in (3,4): s.px(bx+i,by+10,trim)
    x,y,_,_=HAT['front']
    for i in range(8): s.px(x+i,y+1,shade(C('#3a4a5a'),1.0))     # cloth band
    sym_wave(s,x,y-1)
    jx,jy,_,_=JACKET['front']
    for i in range(8): s.px(jx+i,jy,shade(trim,1.0)); s.px(jx+i,jy+1,shade(coat,1.1))   # high collar
    s.save(OUT+'/sayo_current_kunoichi.png')

def garran():
    s=Skin(3); skin=C('#c89070'); hair=C('#2a241e')
    shirt=C('#3a3a42'); vest=C('#5a6a3a'); pants=C('#3a3a42')
    box(s,BODY,shirt); box(s,RARM,shirt); box(s,LARM,shirt); box(s,RLEG,pants); box(s,LLEG,pants)
    box(s,JACKET,vest,0.08)
    for k in ('right','left'):
        x,y,w,h=JACKET[k]; s.rect(x,y,w,h,(0,0,0,0),0) if False else None
    jx,jy,jw,jh=JACKET['front']
    for py in (3,6):                                            # scroll pouches
        for px_ in (0,5):
            s.rect(jx+px_+ (0 if px_==0 else 0),jy+py,3,2,shade(vest,0.75),0.04); s.px(jx+px_+1,jy+py,shade(vest,1.2))
    for r in range(12): s.px(jx+4,jy+r,shade(vest,0.6))
    for i in range(8): s.px(jx+i,jy,shade(vest,1.2)); s.px(jx+i,jy+1,shade(vest,1.05))  # collar roll
    for k in ('front','back','right','left'):
        x,y,w,h=RSLV[k]; s.rect(x,y,w,3,shade(C('#7a2a2a'),SIDE[k]),0.05)  # shoulder guard (red)
    hands(s,RARM,skin); hands(s,LARM,skin)
    wraps(s,RLEG,C('#d0c8b4'),7,10); wraps(s,LLEG,C('#d0c8b4'),7,10)
    shoes(s,RLEG,C('#262222')); shoes(s,LLEG,C('#262222'))
    face(s,skin,hair,C('#141414'),C('#6a4a2a'))
    x,y,_,_=HEAD['front']
    for i in range(1,6): s.px(x+i,y+3+i//3,C('#9a5a4a')) if i in (2,3,4,5) else None   # scar across nose
    for k in ('top','right','left','back'):                     # buzz cut
        hx,hy,w,h=HEAD[k]; s.rect(hx,hy,w,h if k=='top' else 2,shade(hair,SIDE[k]),0.15)
    headband(s,C('#2a2a2e'),C('#a8acb4'),sym_stone)
    s.save(OUT+'/garran_flint_jonin.png')

def lantern_sage():
    s=Skin(4); skin=C('#dcb090'); hair=C('#d8d8d0')
    robe=C('#efe8d8'); gold=C('#d8a83a'); under=C('#8a6a3a')
    box(s,BODY,robe); box(s,RARM,robe); box(s,LARM,robe); box(s,RLEG,robe); box(s,LLEG,robe)
    vgrad(s,BODY,robe,shade(robe,0.82)); vgrad(s,RLEG,shade(robe,0.9),shade(robe,0.7)); vgrad(s,LLEG,shade(robe,0.9),shade(robe,0.7))
    bands(s,BODY,[6],gold); bands(s,BODY,[7],under)
    fx,fy,_,_=BODY['front']
    for r in range(6): s.px(fx+2+r//2,fy+r,shade(gold,0.9)); s.px(fx+5-r//2,fy+r,shade(gold,0.9))  # V-collar
    bands(s,RARM,[10],gold); bands(s,LARM,[10],gold)
    hands(s,RARM,skin); hands(s,LARM,skin); shoes(s,RLEG,C('#5a4a3a')); shoes(s,LLEG,C('#5a4a3a'))
    face(s,skin,hair,C('#222222'),C('#d8b85a'),brow=C('#f0f0ea'))
    x,y,_,_=HEAD['front']
    for i in range(1,7): s.px(x+i,y+7,C('#e8e8e0'))             # beard
    for i in (2,3,4,5): s.px(x+i,y+6,C('#e0e0d8'))
    s.px(x+3,y+6,C('#a07a6a')); s.px(x+4,y+6,C('#a07a6a'))
    for k in ('top','right','left','back'):                     # wide straw hat on outer layer
        hx,hy,w,h=HAT[k]
        s.rect(hx,hy,w,h if k=='top' else 3,shade(C('#c8a868'),SIDE[k]),0.12)
    hx,hy,_,_=HAT['front']; s.rect(hx,hy,8,2,shade(C('#c8a868'),1.0),0.12); 
    for i in range(8): s.px(hx+i,hy+2,shade(C('#8a6a3a'),1.0))   # hat band
    s.save(OUT+'/lantern_sage_dawn.png')

def rook():
    s=Skin(5); skin=C('#d8b49a'); hair=C('#6a6a74')
    suit=C('#1e2026'); armor=C('#b8bcc4'); accent=C('#7a4ab8')
    box(s,BODY,suit); box(s,RARM,suit); box(s,LARM,suit); box(s,RLEG,suit); box(s,LLEG,suit)
    box(s,JACKET,armor,0.05)
    for k in ('front','back'):
        x,y,w,h=JACKET[k]
        for r in range(4,12): s.rect(x,y+r,w,1,(0,0,0,0),0) if r>7 else None
        for r in range(8,12):
            for i in range(w): s.px(x+i,y+r,(0,0,0,0))
        s.px(x+1,y+1,shade(armor,1.2)); s.rect(x,y+7,w,1,shade(accent,SIDE[k]),0.03)
    for P in (RSLV,LSLV):
        for k in ('front','back','right','left'):
            x,y,w,h=P[k]; s.rect(x,y,w,4,shade(armor,SIDE[k]),0.05); s.rect(x,y+6,w,4,shade(armor,SIDE[k]*0.95),0.05)
    hands(s,RARM,C('#2a2a30')); hands(s,LARM,C('#2a2a30')); shoes(s,RLEG,C('#141418')); shoes(s,LLEG,C('#141418'))
    face(s,skin,hair,C('#111111'),C('#6a6a74'))
    x,y,_,_=HAT['front']                                         # original crow mask
    s.rect(x+1,y+2,6,6,C('#f2f2ee'),0.03)
    for i in range(1,7): s.px(x+i,y+2,C('#e0e0dc'))
    s.px(x+2,y+4,C('#101010')); s.px(x+5,y+4,C('#101010'))       # eye slits
    s.px(x+3,y+5,C('#2a2a2a')); s.px(x+4,y+5,C('#2a2a2a')); s.px(x+3,y+6,C('#3a3a3a')); s.px(x+4,y+6,C('#3a3a3a')); s.px(x+4,y+7,C('#4a4a4a'))  # beak
    s.px(x+1,y+3,accent); s.px(x+6,y+3,accent); s.px(x+1,y+5,accent); s.px(x+6,y+5,accent)  # violet marks
    for k in ('top','right','left','back'):
        hx,hy,w,h=HAT[k]; s.rect(hx,hy,w,h if k=='top' else 3,shade(C('#2a2a32'),SIDE[k]),0.1)  # hood
    s.save(OUT+'/rook_dusk_operative.png')

def ash_regent():
    s=Skin(6); skin=C('#8a8a8e'); cloak=C('#3a3438'); ember=C('#ff7a2a')
    box(s,BODY,C('#2a2428')); box(s,RARM,cloak); box(s,LARM,cloak); box(s,RLEG,C('#2a2428')); box(s,LLEG,C('#2a2428'))
    box(s,JACKET,cloak,0.1); box(s,RSLV,cloak,0.1); box(s,LSLV,cloak,0.1); box(s,RPANT,cloak,0.1); box(s,LPANT,cloak,0.1)
    for P in (JACKET,RPANT,LPANT):
        for k in ('front','back','right','left'):
            x,y,w,h=P[k]
            for i in range(w):                                    # smouldering hem
                s.px(x+i,y+h-1,shade(ember,0.7 if s.r.random()<0.5 else 0.45)); 
                if s.r.random()<0.4: s.px(x+i,y+h-2,shade(ember,0.4))
    jx,jy,_,_=JACKET['front']
    for r in range(3,9): s.px(jx+3+(r%2),jy+r,shade(ember,0.5+0.06*r))   # glowing crack
    hands(s,RARM,C('#5a5a5e')); hands(s,LARM,C('#5a5a5e'))
    face(s,skin,C('#2a2626'),C('#1a0a06'),ember,brow=C('#3a3030'),mouth=C('#3a2a2a'))
    x,y,_,_=HEAD['front']
    for (a,b) in ((2,5),(5,6),(1,6)): s.px(x+a,y+b,shade(ember,0.45))     # ash veins
    for k in ('top','right','left','back','front'):               # horned helm
        hx,hy,w,h=HAT[k]; s.rect(hx,hy,w,2 if k!='top' else h,shade(C('#4a4248'),SIDE[k]),0.1)
    hx,hy,_,_=HAT['front']
    for d in range(3): s.px(hx+0,hy+2-d,C('#d8ccb8')) if d<3 else None
    s.px(hx+0,hy+2,C('#d8ccb8')); s.px(hx+7,hy+2,C('#d8ccb8')); s.px(hx+0,hy+3,C('#c8bca8')); s.px(hx+7,hy+3,C('#c8bca8'))
    s.px(hx+3,hy+1,ember); s.px(hx+4,hy+1,ember)                  # crest gem
    s.save(OUT+'/ash_regent_boss.png')

for f in (kaito,sayo,garran,lantern_sage,rook,ash_regent): f()
