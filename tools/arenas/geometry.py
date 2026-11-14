"""Original block architecture for the five additional Naruto coliseums.

Coordinates are local, inclusive and deliberately independent of mod numeric IDs.
This module constructs geometry; it never asserts native gameplay acceptance.
"""
from __future__ import annotations

import math
import heapq
from collections import defaultdict
import hashlib
import json
from pathlib import Path


AIR, STONE, GRASS, DIRT, PLANK, LOG = 0, 1, 2, 3, 5, 17
GLASS, SANDSTONE, WOOL, BOOKS, BRICK = 20, 24, 35, 47, 98
CLAY, GLOW, NETHER, QUARTZ, SEA, CONCRETE = 159, 89, 112, 155, 169, 251


def _dense(points):
    result = []
    for a, b in zip(points, points[1:]):
        distance = max(abs(b[0] - a[0]), abs(b[2] - a[2]), abs(b[1] - a[1]))
        if distance == 0:
            continue
        for i in range(distance):
            p = [round(a[j] + (b[j] - a[j]) * i / distance) for j in range(3)]
            if not result or p != result[-1]:
                result.append(p)
    if points:
        result.append(list(points[-1]))
    return result


class Architecture:
    def __init__(self, spec, volume):
        self.spec, self.v = spec, volume
        self.f = spec["boss_local"][1]
        self.entry_y = spec["entry_local"][1]
        self.routes = {}
        self.rooms = []
        self.landmarks = []
        self.fluids = []
        self.lights = []
        self.solid, self.trim, self.roof = BRICK, QUARTZ, (CLAY, 13)
        self.path_specs = []
        self.anchor_positions = {
            name: list(spec[name + "_local"])
            for name in ("entry", "prep", "checkpoint", "boss", "reward", "exit")
        }
        config_path=Path(__file__).resolve().parents[2]/"content"/"arenas"/"architecture-v1.json"
        config_bytes=config_path.read_bytes()
        config=json.loads(config_bytes)
        if config.get("schema")!=1 or config.get("dimensions")!=[500,250,500]:
            raise ValueError("Unsupported arena architecture configuration")
        self.config=config
        self.config_hash=hashlib.sha256(config_bytes).hexdigest()

    def profile(self, kind):
        return self.config["profiles"][kind]

    def profile_tower(self, kind):
        t=self.profile(kind)["tower"]
        return self.tower(t["name"],*t["center"],t["radius"],self.f,t["top"],t["style"])

    def box(self, *args, **kwargs):
        self.v.box(*args, **kwargs)

    def pillar(self, x, z, top, block=None, width=3, bottom=1, layer="supports"):
        d = width // 2
        self.box(x-d, bottom, z-d, x+d, top, z+d, block or self.solid, layer=layer)

    def path(self, name, points, width=9, clearance=7, material=None):
        cells = _dense(points)
        self.routes[name] = cells
        self.path_specs.append((name, cells, width, clearance, material or self.trim))
        return cells

    def light(self, x, y, z):
        self.v.set(x, y, z, SEA, layer="lighting")
        self.lights.append([x, y, z])

    def room(self, name, bounds, floor, material=None, roof=None, service="archive"):
        x0, z0, x1, z1 = bounds
        wall = material or self.solid
        height = 12
        # Thick real foundation and piers carry the occupied room, with open bays below.
        self.box(x0-1, floor-3, z0-1, x1+1, floor-1, z1+1, wall, layer="functional_interiors")
        for x in (x0+2, x1-2):
            for z in (z0+2, z1-2):
                self.pillar(x, z, floor-4, width=5)
        self.box(x0, floor, z0, x1, floor+height, z1, wall, layer="functional_interiors")
        self.box(x0+2, floor, z0+2, x1-2, floor+height-2, z1-2, AIR, layer="functional_interiors")
        # Enterable south portal and two actual open windows, not sealed display boxes.
        mid = (x0+x1)//2
        self.box(mid-4, floor, z0-1, mid+4, floor+6, z0+2, AIR, layer="functional_interiors")
        self.box(x0, floor+4, z0+7, x0+1, floor+7, z1-7, GLASS, layer="details")
        self.box(x1-1, floor+4, z0+7, x1, floor+7, z1-7, GLASS, layer="details")
        roofblock, roofmeta = roof or self.roof
        # Eaves sit on walls and timber cross beams. Stepped roof transitions are solid.
        self.box(x0-3, floor+height+1, z0-3, x1+3, floor+height+2, z1+3, roofblock, roofmeta, layer="roof_transitions")
        for inset in range(1, 6):
            self.box(x0-3+inset, floor+height+2+inset, z0-3+inset,
                     x1+3-inset, floor+height+2+inset, z1+3-inset,
                     roofblock, roofmeta, layer="roof_transitions")
        for z in (z0+4, z1-4):
            self.box(x0+1, floor+height-2, z, x1-1, floor+height-1, z, LOG, layer="supports")
        # Furnishings use vanilla blocks without inventing block-entity payloads.
        if service in ("archive", "observatory"):
            self.box(x0+3, floor, z1-4, x1-3, floor+3, z1-3, BOOKS, layer="details")
            self.box(x0+3, floor, z0+6, x0+4, floor+2, z1-7, BOOKS, layer="details")
            self.box(mid-2, floor, z1-9, mid+2, floor, z1-7, PLANK, layer="details")
        elif service == "aid":
            for x in (x0+4, x1-6):
                self.box(x, floor, z0+6, x+2, floor, z1-7, QUARTZ, layer="details")
                self.box(x, floor+1, z1-8, x+2, floor+1, z1-7, WOOL, 0, layer="details")
            self.v.set(x1-4, floor, z1-4, 118, 3, layer="details")
        elif service == "workshop":
            self.box(x0+3, floor, z1-4, x1-3, floor, z1-3, PLANK, layer="details")
            for x in range(x0+4, x1-2, 7):
                self.v.set(x, floor+1, z1-4, 58, layer="details")
            self.box(x0+3, floor, z0+6, x0+4, floor+2, z1-7, 170, layer="details")
        else:
            for x in range(x0+3, x1-2, 8):
                self.box(x, floor, z1-6, x+3, floor+1, z1-3, PLANK, layer="details")
                self.box(x, floor+2, z1-6, x+3, floor+2, z1-3, WOOL, 14, layer="details")
        for x in range(x0+4, x1, 8):
            for z in range(z0+4, z1, 8):
                self.light(x, floor-1, z)
        anchor = [mid, floor, z0+6]
        self.rooms.append({"name": name, "service": service, "standing": anchor,
                           "bounds": [x0, floor-3, z0, x1, floor+height+7, z1],
                           "door": [mid, floor, z0], "functional_native_actors": "not yet accepted"})
        return anchor, [mid, floor, z0-5]

    def terrace(self, x0, z0, x1, z1, top, block, fractures=True):
        # Five unequal geological strata taper into their own real footings.
        height = top
        for i in range(5):
            inset = (4-i)*7
            lo, hi = 1 + i*height//5, (i+1)*height//5
            self.box(x0+inset, lo, z0+inset, x1-inset, hi, z1-inset, block,
                     layer="terrain_and_foundation")
        if fractures:
            for x in range(x0+52, x1-25, 73):
                self.box(x, max(1, top-24), z0, x+3, top-7, min(z1, z0+64), AIR,
                         layer="terrain_and_foundation")
        self.box(x0, top-2, z0, x1, top, z1, block, layer="terrain_and_foundation")

    def court(self, kind):
        y, v = self.f, self.v
        if kind != "mist":
            v.cylinder(250, 250, 71, y-5, y-1, self.solid, layer="boss_court")
            v.cylinder(250, 250, 68, y-1, y-1, self.trim, layer="boss_court")
        else:
            # Real source-water basin, fully backed by a non-fluid floor and rim.
            v.cylinder(250, 250, 74, 41, 44, STONE, layer="boss_court")
            v.cylinder(250, 250, 74, 45, 61, STONE, inner=71, layer="boss_court")
            v.cylinder(250, 250, 70, 45, 60, 9, layer="fluids")
            self.fluids.append({"kind": "water", "shape": "cylinder", "center": [250,250],
                                "radius":70, "y":[45,60], "contained_baseline": True})
            for x,z in ((215,215),(285,215),(215,285),(285,285),(250,250)):
                v.cylinder(x,z,13,1,y-1,STONE,layer="supports")
                v.cylinder(x,z,14,y-2,y-1,PLANK,layer="boss_court")
            self.path("dry_melee_cross",[[184,y,250],[316,y,250]],width=9,material=PLANK)
        # The marked court has three lanes and physical seal plinths outside those lanes.
        for dx, meta in ((-34,4),(0,0),(34,11)):
            if kind != "mist":
                self.box(250+dx-2,y-1,193,250+dx+2,y-1,307,CLAY,meta,layer="arena_mechanics")
            for z in (198,302):
                self.box(250+dx-4,y-1,z-4,250+dx+4,y-1,z+4,CONCRETE,meta,layer="arena_mechanics")
        for x,z in ((194,210),(306,210),(194,290),(306,290)):
            self.box(x-4,y-2,z-4,x+4,y-1,z+4,self.solid,layer="arena_mechanics")
            self.light(x,y-1,z)
            self.box(x-1,y,z-1,x+1,y+1,z+1,self.trim,layer="arena_mechanics")
        for x in range(190,311,8):
            for z in range(190,311,8):
                if (x-250)**2+(z-250)**2 < 66**2 and kind != "mist":
                    self.light(x,y-1,z)

    def gallery(self, radius=144, kind="forest"):
        v,y=self.v,self.f
        self.inner_radius=radius
        # Genuine concourse floors, roof-bearing radial piers and usable seating tiers.
        for r in range(87,radius,6):
            sy=y+min(34,round(max(0,r-95)*34/(radius-7-95)))
            v.cylinder(250,250,r+5,sy-3,sy-1,self.solid,inner=r,layer="bowl_and_stands")
            v.cylinder(250,250,r+3,sy,sy,self.trim,inner=r+2,layer="bowl_and_stands")
        for angle in range(0,360,15):
            if kind=="mist" and 55<angle<125:
                continue
            a=math.radians(angle)
            x,z=round(250+math.cos(a)*(radius-3)),round(250+math.sin(a)*(radius-3))
            self.pillar(x,z,y+36,width=5)
            v.line(round(250+math.cos(a)*86),y+1,round(250+math.sin(a)*86),
                   x,y+32,z,3,self.solid,layer="supports")
            self.light(x,y+37,z)
        v.cylinder(250,250,radius+3,y+31,y+33,self.trim,inner=radius-8,layer="galleries")
        v.cylinder(250,250,radius+4,y+42,y+44,self.roof[0],self.roof[1],inner=radius-9,layer="roof_transitions")
        # Concourse ring walk is returned and carved after all decoration.
        pts=[]
        for angle in range(0,361,3):
            a=math.radians(angle)
            pts.append([round(250+math.cos(a)*(radius-3)),y+34,round(250+math.sin(a)*(radius-3))])
        self.path("upper_concourse",pts,width=7,clearance=7)
        self.path("concourse_stair",[[250,y,150],[145,y,150],[100,y+34,150],[90,y+34,150],
                                     [90,y+34,250],[250-(radius-3),y+34,250]],width=7)
        # Real stair aisles meet every circular seating row at its actual elevation.
        # West/east approaches keep the short south combat entrance unobstructed.
        for sign,name in ((-1,"west"),(1,"east")):
            points=[[250+sign*60,y,250],[250+sign*74,y,250]]
            for r in range(75,radius-2):
                rise=min(34,round(max(0,r-95)*34/(radius-7-95)))
                points.append([250+sign*r,y+rise,250])
            self.path("inner_spectator_"+name,points,width=7,clearance=7)

    @staticmethod
    def stadium_span(kind,inset,z):
        """500-wide real enclosing stadium footprints, with distinct plan shapes."""
        if not inset<=z<=499-inset:return None
        if kind=="sand":
            radius=250-inset
            dz=z-249.5
            if abs(dz)>radius:return None
            dx=math.sqrt(max(0,radius*radius-dz*dz))
            lo=max(0,math.ceil(249.5-dx));hi=min(499,math.floor(249.5+dx))
            return lo,hi
        if kind in ("mist","fire"):
            cut=36 if kind=="mist" else 70
            bevel=max(0,cut+inset-z,z-(499-cut-inset))
            return inset+bevel,499-inset-bevel
        if kind=="cloud":
            # Cloud's northeast fracture produces an asymmetric fortress plan.
            bevel=max(0,z-(434-inset))
            return inset,499-inset-bevel
        return inset,499-inset

    def stadium_ring(self,kind,inset,thickness,y0,y1,block,meta=0,layer="stadium_structure"):
        for z in range(500):
            outer=self.stadium_span(kind,inset,z)
            if outer is None:continue
            inner=self.stadium_span(kind,inset+thickness,z)
            if inner is None:
                self.box(outer[0],y0,z,outer[1],y1,z,block,meta,layer=layer)
            else:
                if outer[0]<inner[0]:self.box(outer[0],y0,z,inner[0]-1,y1,z,block,meta,layer=layer)
                if inner[1]<outer[1]:self.box(inner[1]+1,y0,z,outer[1],y1,z,block,meta,layer=layer)

    def enclosing_stadium(self,kind):
        """The stadium proper, rather than merely its plot, spans500x500x250.

        Spectator construction occupies the outer80 cells, with bearing walls,
        real service rooms, two occupied concourses and radial stair aisles. The
        active136-block court remains intentionally practical for native jutsu.
        """
        y=self.f
        fy=y+34
        # These are structural stadium walls/bleacher substructure, not terrain.
        self.stadium_ring(kind,0,9,0,fy+34,self.solid,layer="stadium_structure")
        self.stadium_ring(kind,9,9,0,fy+15,self.solid,layer="stadium_structure")
        for inset in range(18,81,4):
            sy=fy+round(max(0,min(34,54-inset))*33/34)
            self.stadium_ring(kind,inset,4,sy-4,sy-1,self.solid,layer="bowl_and_stands")
            self.stadium_ring(kind,inset,1,0,sy-5,self.solid,layer="stadium_structure")
            self.stadium_ring(kind,inset+1,1,sy,sy,self.trim,layer="bowl_and_stands")
        # Actual occupied lower service concourse is underneath the spectator banks.
        for inset in (60,12):
            level=fy if inset==60 else fy+33
            self.stadium_ring(kind,inset,9,level-3,level-1,self.trim,layer="stadium_galleries")
        # Distinct rooflines: stepped Leaf hips; interrupted Cloud indigo banks;
        # sandstone sun terraces; marine fin canopies; heavy Uchiha citadel eaves.
        roof_material,roof_meta=self.roof
        roof_levels={"forest":(fy+46,6),"cloud":(fy+42,9),"sand":(fy+40,4),
                     "mist":(fy+40,3),"fire":(fy+47,7)}
        roof_y,eaves=roof_levels[kind]
        for tier in range(eaves):
            self.stadium_ring(kind,tier*2,11,roof_y+tier,roof_y+tier,roof_material,roof_meta,layer="stadium_roof")
        # Genuine supported exterior bays vary in shape and height by region.
        for index,c in enumerate(range(36,475,40)):
            if kind=="sand":
                # Arcaded sandstone columns on the actual circular perimeter.
                span=self.stadium_span(kind,5,c)
                positions=((span[0]+3,c),(span[1]-3,c)) if span else ()
            elif kind=="cloud":
                span=self.stadium_span(kind,5,c)
                positions=((9,c),(span[1]-4,c)) if span else ()
            elif kind in ("mist","fire"):
                span=self.stadium_span(kind,5,c)
                positions=((span[0]+4,c),(span[1]-4,c)) if span else ()
            else:positions=((9,c),(490,c))
            for x,z in positions:
                # Timber/stone piers bear each elevated roof and the inhabited bays.
                mat=LOG if kind in ("forest","mist") else self.solid
                self.pillar(x,z,roof_y+eaves-1,mat,width=5,bottom=0,layer="stadium_structure")
                if kind=="forest":
                    self.box(x-2,fy+4,z-12,x+2,fy+20,z+12,CONCRETE,0,layer="stadium_galleries")
                    self.box(x-2,fy+8,z-7,x+2,fy+14,z+7,GLASS,layer="stadium_galleries")
                elif kind=="cloud":
                    height=roof_y+8+(index%3)*4
                    self.box(x-3,fy+12,z-3,x+3,height,z+3,QUARTZ,layer="stadium_structure")
                    self.box(x-5,height,z-5,x+5,height+3,z+5,CLAY,11,layer="stadium_roof")
                elif kind=="sand":
                    self.box(x-2,fy+8,z-8,x+2,fy+18,z+8,CLAY,9,layer="stadium_galleries")
                elif kind=="mist":
                    # Tall fin-shaped gables taper along Z, with a real timber back.
                    for dz in range(-9,10):
                        high=roof_y+2+max(0,12-abs(dz))
                        self.box(x-2,roof_y-2,z+dz,x+2,high,z+dz,CLAY,9,layer="stadium_roof")
                else:
                    self.box(x-4,fy+5,z-4,x+4,roof_y+10,z+4,NETHER,layer="stadium_structure")
        # Full-size stadium has a continuous connected service ring; the geometry of
        # the independent route is square inside all five enclosing footprints.
        inner=[[64,fy,64],[435,fy,64],[435,fy,435],[64,fy,435],[64,fy,64]]
        self.path("stadium_service_concourse",inner,width=9,clearance=9)
        # Upper access aisles are intentionally straight through the seating rows.
        # Circular/octagonal bank corners are reached along inner concourse galleries.
        radius=self.profile(kind)["bowl_radius"]-3
        for name,points in {
            "west":[[250-radius,y+34,250],[64,fy,250],[54,fy,250],[20,fy+33,250],[12,fy+33,250]],
            "east":[[250+radius,y+34,250],[435,fy,250],[445,fy,250],[479,fy+33,250],[487,fy+33,250]],
            "south":[[250,fy,250-radius],[210,fy,250-radius],[210,fy,64],[250,fy,64],[250,fy,54],
                     [250,fy+33,20],[250,fy+33,12]],
            "north":[[250,fy,250+radius],[210,fy,250+radius],[210,fy,435],[250,fy,435],[250,fy,445],
                     [250,fy+33,479],[250,fy+33,487]],
        }.items():
            self.path("stadium_"+name+"_stair",points,width=9,clearance=9)
        # Each of four useful outer services has a lit approach from the ring.
        for i,(bounds,service,connection) in enumerate((
            ((18,210,62,260),"archive",[64,fy,205]),
            ((437,210,481,260),"aid",[435,fy,205]),
            ((210,18,260,62),"workshop",[235,fy,64]),
            ((210,437,260,481),"market",[235,fy,435]))):
            anchor,door=self.room("Stadium "+service,bounds,fy,service=service)
            self.path("stadium_service_"+str(i),[connection,[door[0],fy,connection[2]],door,anchor],width=7)
        # An actual monumental south entry, with a19-wide,24-high usable opening.
        ey=self.entry_y
        self.box(232,ey,0,268,ey+25,22,AIR,layer="circulation")
        self.box(231,0,0,234,ey+37,24,self.solid,layer="stadium_structure")
        self.box(266,0,0,269,ey+37,24,self.solid,layer="stadium_structure")
        self.box(231,ey+26,0,269,ey+37,24,roof_material,roof_meta,layer="stadium_roof")
        self.path("stadium_arrival",[[250,ey,20],self.anchor_positions["entry"]],width=19,clearance=24)
        if kind=="forest":
            # Original sweeping wood roots and two unequal Leaf gate horns.
            for x,h in ((208,ey+56),(292,ey+45)):
                self.v.line(x,0,16,x+(-15 if x<250 else 15),h,20,9,LOG,layer="landmark_masses")
                self.v.line(x,h-5,20,250,h+8,16,7,LOG,layer="landmark_masses")
        elif kind=="cloud":
            for x,h in ((218,ey+54),(282,ey+68)):
                self.pillar(x,14,h,QUARTZ,width=7,bottom=0,layer="stadium_structure")
                self.box(x-9,h,5,x+9,h+4,23,CLAY,11,layer="stadium_roof")
                self.light(x,h+5,14)
        elif kind=="sand":
            # A gourd-shaped scholar seal silhouette, built from two distinct lobes.
            for yy,r in ((ey+43,15),(ey+49,18),(ey+57,14),(ey+64,9),(ey+72,12),(ey+79,8)):
                self.v.cylinder(250,24,r,yy,yy+5,SANDSTONE,layer="landmark_masses")
            self.box(241,ey+35,10,259,ey+43,22,SANDSTONE,layer="landmark_masses")
        elif kind=="mist":
            # Shark mouth: supported upper jaw and alternating original masonry teeth.
            for i,x in enumerate(range(234,268,4)):
                self.box(x,ey+24-(i%2)*2,2,x+1,ey+27,11,QUARTZ,layer="landmark_masses")
            for x in (219,281):
                for dz in range(15):
                    self.box(x-2,ey+5,8+dz,x+2,ey+43-dz,8+dz,CLAY,9,layer="landmark_masses")
        else:
            # Broad block-built Uchiha fan relief sits on two load-bearing gate piers.
            self.box(245,ey+37,12,255,ey+52,20,NETHER,layer="landmark_masses")
            for yy in range(ey+52,ey+76):
                half=23-abs(yy-(ey+64))//2
                self.box(250-half,yy,12,250+half,yy,20,CLAY,14 if yy>ey+63 else 0,layer="landmark_masses")
        self.landmarks.append({"name":"500-block enclosing "+kind+" stadium",
                               "bounds":[0,0,0,499,249,499],"spectator_outer_footprint":[500,500],
                               "functional_services":4,"native_acceptance":"NOT_RUN"})

    def tower(self, name, cx, cz, radius, base, top=249, style="signal"):
        v=self.v
        v.cylinder(cx,cz,radius+5,1,base-2,self.solid,layer="supports")
        v.cylinder(cx,cz,radius,base-1,top-11,self.solid,inner=radius-3,layer="landmark_masses")
        v.cylinder(cx,cz,radius-3,base-1,base-1,self.trim,layer="landmark_masses")
        for level in range(base+18,top-18,28):
            v.cylinder(cx,cz,radius+3,level,level+2,self.trim,inner=radius-3,layer="landmark_masses")
            for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
                if dx:
                    self.box(cx+dx*(radius-2)-2,level+5,cz-3,cx+dx*(radius-2)+2,level+13,cz+3,GLASS,layer="details")
                else:
                    self.box(cx-3,level+5,cz+dz*(radius-2)-2,cx+3,level+13,cz+dz*(radius-2)+2,GLASS,layer="details")
        # Square spiral staircase fits within the cylinder, with >25 blocks between laps.
        half=max(8,int((radius-5)/math.sqrt(2)))
        perimeter=[]
        for x in range(cx-half,cx+half): perimeter.append([x,cz-half])
        for z in range(cz-half,cz+half): perimeter.append([cx+half,z])
        for x in range(cx+half,cx-half,-1): perimeter.append([x,cz+half])
        for z in range(cz+half,cz-half,-1): perimeter.append([cx-half,z])
        path=[]
        step=0
        height=base
        side=2*half
        final_flat=0
        while height<top-12 or final_flat<12:
            x,z=perimeter[step%len(perimeter)]
            path.append([x,height,z])
            if height==top-12:final_flat+=1
            step+=1
            corner_distance=min(step%side,side-step%side)
            if height<top-12 and step>12 and step%3==0 and corner_distance>=5:
                height+=1
        deck=top-12
        ex,ey,ez=path[-1]
        path.extend(_dense([[ex,ey,ez],[ex,deck,ez],[cx,deck,cz]]))
        self.path(name+"_stair",path,width=5,clearance=6,material=self.trim)
        v.cylinder(cx,cz,radius-2,deck-2,deck-1,self.trim,layer="functional_interiors")
        v.cylinder(cx,cz,radius+1,deck,top-3,self.solid,inner=radius-2,layer="landmark_masses")
        if style=="lighthouse":
            v.cylinder(cx,cz,radius,deck+3,top-4,GLASS,inner=radius-3,layer="landmark_masses")
            v.cylinder(cx,cz,5,deck+2,top-3,SEA,layer="lighting")
        else:
            for x in (cx-radius+2,cx+radius-2):
                self.box(x-1,deck+2,cz-4,x+1,top-4,cz+4,GLASS,layer="details")
        v.cylinder(cx,cz,radius+4,top-2,top-1,self.roof[0],self.roof[1],layer="roof_transitions")
        v.cylinder(cx,cz,16,top,top,self.trim,layer="landmark_masses")
        self.box(cx-2,base,cz-radius-2,cx+2,base+7,cz-radius+3,AIR,layer="circulation")
        start=path[0]
        self.path(name+"_access",[[cx,base,cz-radius-7],[cx,base,cz-radius+5],start],width=7)
        self.rooms.append({"name":name+" overlook","service":"overlook","standing":[cx,deck,cz],
                           "bounds":[cx-radius,deck-2,cz-radius,cx+radius,top,cz+radius]})
        self.landmarks.append({"name":name,"bounds":[cx-radius-5,1,cz-radius-5,cx+radius+5,top,cz+radius+5],
                               "accessible_overlook":[cx,deck,cz],"style":style})
        return [cx,base,cz-radius-7]

    def facilities(self, kind):
        y=self.entry_y
        names={"forest":("Keeper archive","Medicinal preparation","Root workshop","Seed store"),
               "cloud":("Signal dispatch","Storm aid station","Grounding workshop","Bridge stores"),
               "sand":("Seal library","Caravan treatment","Cistern workshop","Shaded bazaar"),
               "mist":("Harbor logbook","Breathing aid room","Entered boathouse","Net stores"),
               "fire":("Citadel records","Forge treatment","Cooling workshop","Garrison stores")}[kind]
        for i,(bounds,service) in enumerate((((145,75,205,125),"archive"),((295,75,355,125),"aid"),
                                             ((115,340,175,386),"workshop"),((325,75,385,125),"market"))):
            # The fourth bay is offset to avoid overlap with the treatment room.
            if i==3: bounds=(365,125,425,175)
            floor=y if i!=2 else self.f
            anchor,door=self.room(names[i],bounds,floor,service=service)
            connection=[250,y,92] if i<2 else ([185,self.f,300] if i==2 else [330,self.f,150])
            # Floor deltas use ample horizontal run; no vertical ladders masquerade as paths.
            if i==3 and floor!=self.f:
                room_route=[connection,[345,self.f,150],[385,floor,115],door,anchor]
            else:
                room_route=[connection,[door[0],floor,connection[2]],door,anchor]
            self.path("room_"+str(i),room_route,width=7)
        # Lower maintenance route has two independent exits, plus actual rooms in the base.
        low=max(14,min(44,self.f-26))
        a,door=self.room("Lower maintenance and stores",(80,170,125,215),low,service="workshop")
        self.path("lower_service",[[185,self.f,175],[145,self.f,130],[45,low,130],[35,low,130],
                                   [35,low,150],door,a,[145,low,220],[35,low,220],[35,low,235],
                                   [35,self.f,360],[35,self.f,370],[185,self.f,370],[185,self.f,270]],width=7)
        ry=self.spec["reward_local"][1]
        self.path("alternative",[[250,self.f,150],[165,self.f,150],[165,self.f,320],
                                 [250,self.f,320],[250,self.f,332],[250,ry,363],[250,ry,370]],width=9)

    def finish(self):
        f=self.f
        anchors=self.anchor_positions
        ry=anchors["reward"][1]
        entry_route=[anchors["entry"],anchors["prep"],[250,self.entry_y,105],
                     [250,f,140],anchors["checkpoint"],anchors["boss"]]
        self.path("primary",entry_route+[[250,f,320],[250,f,332],[250,ry,363],anchors["reward"]],width=11,clearance=10)
        self.path("short_return",[anchors["reward"],[275,ry,370],[285,ry,370],[320,f,370],[330,f,370],
                                   [330,f,140],[330,self.entry_y,120],[330,self.entry_y,80],
                                   anchors["exit"],anchors["entry"]],width=9)
        self.path("giant_gateway",entry_route,width=19,clearance=24)
        self._routed_surfaces()
        # Exact arrival/reward standing pads remain broad, lit and free of fixtures.
        for name,(x,y,z) in anchors.items():
            self.box(x-5,y-3,z-5,x+5,y-1,z+5,self.trim,layer="anchors")
            self.box(x-5,y,z-5,x+5,min(249,y+9),z+5,AIR,layer="anchors")
            self.light(x,y-1,z)
        nodes=[]
        for x,z in ((194,210),(306,210),(194,290),(306,290)):
            self.v.set(x,f+1,z,41,layer="arena_mechanics")
            nodes.append([x,f+1,z])
        self._seal_authored_fluids()
        stair_report=self._walking_stairs()
        containers=self._storage_containers()
        # Retain genuine seating cross sections as occupancy witnesses after paths
        # remove the north concourse edge. No geometry is added for these witnesses.
        if hasattr(self.v,"layer_samples"):
            samples=self.v.layer_samples["bowl_and_stands"]
            for radius in (93,105,117,129):
                sy=f+min(34,round(max(0,radius-95)*34/(self.inner_radius-7-95)))
                for dx,dz in ((radius+1,0),(-radius-1,0),(0,radius+1),(0,-radius-1)):
                    x,z=250+dx,250+dz
                    i=((sy-1)*500+z)*500+x
                    if self.v.blocks[i]==self.solid and self.v.data[i]==0:
                        samples.append((x,sy-1,z,self.solid,0))
        return {"schema":1,"architecture_version":self.config["version"],"anchors":anchors,
                "architecture_config_sha256":self.config_hash,
                "routes":self.routes,"rooms":self.rooms,"landmarks":self.landmarks,
                "mechanic_nodes":nodes,
                "walking_stairs":stair_report,
                "containers":containers,
                "route_specs":{name:{"width":width,"clearance":head,"kind":"dry"}
                               for name,_,width,head,_ in self.path_specs},
                "fluid_volumes":self.fluids,"light_sources":self.lights,
                "court_diameter":136,"giant_route_envelope_design":[19,24],
                "stadium_bounds":[0,0,0,499,249,499],"stadium_dimensions":[500,250,500],
                "stadium_layers":["stadium_structure","stadium_galleries","stadium_roof","bowl_and_stands","landmark_masses","functional_interiors","roof_transitions","galleries"],
                "native_form_support":"NOT_RUN: native AABBs must be measured",
                "structural_layers":["terrain_and_foundation","supports","circulation","bowl_and_stands",
                                     "landmark_masses","functional_interiors","roof_transitions","arena_mechanics","details","lighting","anchors",
                                     "stadium_structure","stadium_galleries","stadium_roof"],
                "provenance":"Original procedural block architecture; vanilla 1.12.2 palette; no imported artwork or world cells."}

    def _storage_containers(self):
        """Real native empty chest inventories with matching tile payloads."""
        from tools.world_merge.blocks import FLOOR_TABLE
        result=[]
        for room in self.rooms:
            if room["service"] not in ("workshop","market"):continue
            x0,_,z0,x1,_,z1=room["bounds"]
            y=room["standing"][1]
            chosen=None
            for x,z in ((x0+8,z0+8),(x1-8,z0+8),(x0+8,z1-10),(x1-8,z1-10)):
                def block(px,py,pz):return self.v.blocks[(py*500+pz)*500+px]
                if all(block(x,y+dy,z)==AIR for dy in (0,1)) and all(block(x,y+dy,z-1)==AIR for dy in (0,1,2)):
                    below=block(x,y-1,z-1)
                    if below<256 and FLOOR_TABLE[below]:
                        chosen=(x,z);break
            if chosen is None:raise ValueError("No accessible native storage position in "+room["name"])
            x,z=chosen
            self.v.chest(x,y,z,facing=2,custom_name=room["name"]+" stores",layer="details")
            result.append({"position":[x,y,z],"front":[x,y,z-1],"room":room["name"],"facing":2,
                           "tile_id":"minecraft:chest","contents":"empty functional storage; no reward entitlement"})
        return result

    def _walking_stairs(self):
        """Replace each rising tread with an actual normal-half vanilla stair.

        The stair occupies the higher tread's supporting block, facing uphill:
        metadata0 east,1 west,2 south,3 north. Its low half gives a 0.5-block
        first rise instead of requiring a jump onto a full block. Real native
        neighbor-derived stair collision/shape remains separately tested.
        """
        kind=next(k for k in ("forest","cloud","sand","mist","fire") if "_"+k+"_" in self.spec["id"])
        block={"forest":53,"cloud":156,"sand":128,"mist":53,"fire":114}[kind]
        edits={}
        for name,cells,width,_,_ in self.path_specs:
            for first,second in zip(cells,cells[1:]):
                if abs(first[1]-second[1])!=1:continue
                low,high=(first,second) if first[1]<second[1] else (second,first)
                dx,dz=high[0]-low[0],high[2]-low[2]
                if not dx and not dz:continue
                along_x=abs(dx)>=abs(dz)
                meta=(0 if dx>0 else 1) if along_x else (2 if dz>0 else 3)
                for offset in range(-(width//2),width//2+1):
                    x=high[0]+(0 if along_x else offset)
                    z=high[2]+(offset if along_x else 0)
                    y=high[1]-1
                    i=(y*500+z)*500+x
                    if self.v.blocks[i] not in (0,9,11,41):
                        edits[(x,y,z)]=(block,meta,name)
        for (x,y,z),(block,meta,_) in edits.items():
            i=(y*500+z)*500+x
            if self.v.blocks[i] in (89,169):
                self.light(x,y-1,z)
            self.v.set(x,y,z,block,meta,layer="walking_stairs")
        self.lights=[p for p in self.lights if self.v.blocks[(p[1]*500+p[2])*500+p[0]]==SEA]
        return {"count":len(edits),"block_id":block,"half":"normal",
                "orientation":"0=east/up +X;1=west/up -X;2=south/up +Z;3=north/up -Z",
                "samples":[{"position":list(p),"block":v[0],"meta":v[1],"route":v[2]}
                           for p,v in list(edits.items())[:128]],
                "native_collision_acceptance":"NOT_RUN"}

    def _seal_authored_fluids(self):
        """Retain source liquids behind a glass liner at every authored dry cut.

        This changes fluid cells themselves, never dry standing/headroom cells.
        It is an actual geometry repair after route construction, not a waiver
        of the independent native-fluid-update acceptance gate.
        """
        import numpy as np
        from tools.world_merge.blocks import FLUIDS, NO_COLLISION
        blocks=np.frombuffer(self.v.blocks,dtype=np.uint8).reshape(250,500,500)
        repaired=0
        for y in range(1,250):
            band=blocks[y]
            liquid=np.isin(band,list(FLUIDS))
            if not liquid.any():continue
            open_cells=np.isin(band,list(NO_COLLISION))
            bad=np.isin(blocks[y-1],list(NO_COLLISION))
            bad[:,1:] |= open_cells[:,:-1]
            bad[:,:-1] |= open_cells[:,1:]
            bad[1:,:] |= open_cells[:-1,:]
            bad[:-1,:] |= open_cells[1:,:]
            bad[0,:]=True;bad[-1,:]=True;bad[:,0]=True;bad[:,-1]=True
            mask=liquid & bad
            repaired+=int(mask.sum())
            band[mask]=GLASS
        self.v.layers["fluid_safety_lining"]+=repaired
        self.v.used.add((GLASS,0))
        del blocks,band

    def _routed_surfaces(self):
        """Union walkable surfaces before carving, including sloped junctions.

        A naive square clear per step erases the next ascending step. Here exact
        route centers form a graph, nearby intersecting levels share a junction,
        and a lower envelope eases joins with at most one block rise per cell.
        Separate spiral laps stay separate. Each column is written once per level.
        """
        nodes=[]
        by_xz=defaultdict(list)
        graph=[]
        for _,cells,_,_,_ in self.path_specs:
            previous=None
            for p in cells:
                idx=len(nodes)
                nodes.append(p)
                graph.append([])
                by_xz[(p[0],p[2])].append(idx)
                if previous is not None:
                    graph[idx].append((previous,1))
                    graph[previous].append((idx,1))
                previous=idx
        for group in by_xz.values():
            group.sort(key=lambda i:nodes[i][1])
            for ia,ib in zip(group,group[1:]):
                if abs(nodes[ia][1]-nodes[ib][1])<=10:
                    graph[ia].append((ib,0))
                    graph[ib].append((ia,0))
        heights=[p[1] for p in nodes]
        queue=[(height,i) for i,height in enumerate(heights)]
        heapq.heapify(queue)
        while queue:
            height,i=heapq.heappop(queue)
            if height!=heights[i]:continue
            for j,cost in graph[i]:
                value=height+cost
                if heights[j]>value:
                    heights[j]=value
                    heapq.heappush(queue,(value,j))
        for point,height in zip(nodes,heights):point[1]=height
        columns=defaultdict(list)
        for _,cells,width,head,material in self.path_specs:
            d=width//2
            for i,(x,y,z) in enumerate(cells):
                if i%12==0:self.pillar(x,z,y-3,width=3)
                for dx in range(-d,d+1):
                    for dz in range(-d,d+1):
                        columns[(x+dx,z+dz)].append((y,dx*dx+dz*dz,head,material))
        surfaces=[]
        def choose(cluster):
            y,dist,_,material=min(cluster,key=lambda c:(c[1],c[0]))
            return y,dist,max(c[2] for c in cluster),material
        for (x,z),candidates in columns.items():
            candidates.sort()
            cluster=[]
            for candidate in candidates:
                if cluster and candidate[0]-cluster[0][0]>10:
                    surfaces.append((x,z,choose(cluster)))
                    cluster=[]
                cluster.append(candidate)
            if cluster:surfaces.append((x,z,choose(cluster)))
        for x,z,(y,_,_,material) in surfaces:
            self.box(x,y-3,z,x,y-1,z,material,layer="circulation")
        for x,z,(y,_,head,_) in surfaces:
            self.box(x,y,z,x,min(249,y+head-1),z,AIR,layer="circulation")
        for _,cells,_,_,_ in self.path_specs:
            for i,(x,y,z) in enumerate(cells):
                if i%6==0:self.light(x,y-1,z)


def _forest(a):
    a.solid,a.trim,a.roof=BRICK,PLANK,(CLAY,13)
    y,v=a.f,a.v
    a.terrace(24,24,245,470,y-5,STONE)
    a.terrace(258,50,475,445,y-11,STONE)
    a.box(24,y-5,24,245,y-4,470,GRASS,layer="terrain_and_foundation")
    a.box(258,y-11,50,475,y-10,445,GRASS,layer="terrain_and_foundation")
    # Broad roots carry the fractured seam, with tapering buttresses into the earth.
    for z in (80,160,260,350,430):
        v.line(190,24,z,310,y-5,z+13,13,LOG,layer="supports")
        v.line(310,y-5,z+13,345,15,z+32,9,LOG,layer="supports")
    a.gallery(a.profile("forest")["bowl_radius"],"forest")
    # A sculpted trunk, branching timber and a deliberately inhabited crown silhouette.
    v.cylinder(310,412,18,1,188,LOG,layer="landmark_masses")
    for angle in range(0,360,45):
        rad=math.radians(angle)
        ex,ez=round(310+math.cos(rad)*63),round(412+math.sin(rad)*49)
        v.line(310,143,412,ex,218,ez,11,LOG,layer="landmark_masses")
        for dy,r in ((0,28),(10,24),(19,17)):
            v.cylinder(ex,ez,r,211+dy,218+dy,18,4,layer="landmark_masses")
    v.cylinder(310,412,43,233,240,18,4,layer="landmark_masses")
    v.cylinder(310,412,25,241,249,18,4,layer="landmark_masses")
    a.landmarks.append({"name":"Living root crown","bounds":[219,1,335,401,249,489],"original_block_sculpture":True})
    access=a.profile_tower("forest")
    a.path("archive_crown_approach",[[316,y,250],[370,y,290],access],width=7)
    # Two visibly different block-built guardian reliefs, supported at the court sides.
    for x,sz in ((90,19),(410,25)):
        a.box(x-11,1,250-sz,x+11,y+12,250+sz,STONE,layer="landmark_masses")
        a.box(x-15,y+13,236,x+15,y+42,264,BRICK,layer="landmark_masses")
        a.box(x-10,y+43,240,x+10,y+63,260,STONE,layer="landmark_masses")
        a.box(x-18,y+22,244,x+18,y+27,256,LOG,layer="landmark_masses")


def _cloud(a):
    a.solid,a.trim,a.roof=QUARTZ,BRICK,(CLAY,11)
    y,v=a.f,a.v
    for bounds,top in (((35,35,235,455),y-8),((248,30,469,232),y-14),((248,248,460,470),y-5)):
        a.terrace(*bounds,top,QUARTZ)
    # Grounded buttresses and catch terraces under the fractured bridge connections.
    for x,z in ((230,170),(260,245),(230,350)):
        a.box(x-24,55,z-28,x+35,59,z+28,BRICK,layer="supports")
        for px in (x-18,x+26): a.pillar(px,z,56,width=7)
    a.gallery(a.profile("cloud")["bowl_radius"],"cloud")
    # Indigo fortress boxes occupy unequal corners, with wide, sheltered bridge galleries.
    for x,z,h in ((113,160,48),(382,175,71),(140,365,40)):
        a.box(x-18,1,z-18,x+18,y+h,z+18,QUARTZ,layer="landmark_masses")
        a.box(x-13,y+4,z-13,x+13,y+h-5,z+13,AIR,layer="functional_interiors")
        for inset in range(6):
            a.box(x-24+inset,y+h+inset,z-24+inset,x+24-inset,y+h+inset,z+24-inset,CLAY,11,layer="roof_transitions")
        a.path("fortress_"+str(x),[[x,y,z-27],[x,y,z],[x,y,z+27]],width=7)
    access=a.profile_tower("cloud")
    a.path("signal_approach",[[316,y,250],[350,y,285],access],width=9)
    # Two grounded conductors and physical safe pads communicate the pylon mechanic.
    for x,z,h in ((196,228,34),(304,272,46)):
        a.box(x-5,1,z-5,x+5,y+h,z+5,QUARTZ,layer="arena_mechanics")
        a.box(x-10,y+h,z-10,x+10,y+h+3,z+10,CLAY,11,layer="arena_mechanics")
        a.light(x,y+h+4,z)
        a.box(x-10,y-1,z+12,x+10,y-1,z+23,CLAY,0,layer="arena_mechanics")


def _sand(a):
    a.solid,a.trim,a.roof=SANDSTONE,CLAY,(CLAY,9)
    y,v=a.f,a.v
    a.terrace(18,22,244,479,y-6,SANDSTONE)
    a.terrace(259,44,481,445,y-12,CLAY)
    # Sand is only a single surface layer on sandstone, never unsupported falling fill.
    a.box(20,y-5,24,242,y-5,477,SANDSTONE,layer="terrain_and_foundation")
    a.gallery(a.profile("sand")["bowl_radius"],"sand")
    # Shaded market arcades are squared, unlike the round domes and observatory.
    for x in range(90,411,40):
        a.pillar(x,105,y+21,width=5)
        a.box(x-20,y+22,95,x+20,y+24,115,SANDSTONE,layer="galleries")
    # Cistern is a contained source reservoir, with a dry maintained gallery beside it.
    a.box(90,28,280,150,49,330,SANDSTONE,layer="functional_interiors")
    a.box(94,32,284,146,46,326,9,layer="fluids")
    a.box(94,47,284,146,48,326,GLASS,layer="functional_interiors")
    a.fluids.append({"kind":"water","shape":"box","min":[94,32,284],"max":[146,46,326],"contained_baseline":True})
    a.path("dry_cistern",[[165,y,220],[140,y,220],[140,y,235],[140,49,290],[140,49,310],
                          [140,y,360],[140,y,390],[175,y,390],[175,y,365]],width=7)
    for cx,cz,r,h in ((150,390,27,31),(350,380,37,43)):
        v.cylinder(cx,cz,r,1,y+h,SANDSTONE,inner=r-3,layer="landmark_masses")
        for dy in range(0,r):
            rr=round(math.sqrt(max(0,r*r-dy*dy)))
            if rr>0: v.cylinder(cx,cz,rr,y+h+dy,y+h+dy,CLAY,0,layer="roof_transitions")
        a.path("dome_"+str(cx),[[cx,y,cz-r-6],[cx,y,cz],[cx,y,cz+r+6]],width=7)
    access=a.profile_tower("sand")
    a.path("observatory_approach",[[316,y,250],[357,y,285],access],width=9)
    # Fixed non-falling dune-shaped solid cover and physical interruption nodes.
    for x,z in ((212,220),(285,280)):
        for h in range(4):
            a.box(x-9+h,y+h,z-6+h,x+9-h,y+h,z+6-h,SANDSTONE,layer="arena_mechanics")


def _mist(a):
    a.solid,a.trim,a.roof=BRICK,PLANK,(CLAY,9)
    y,v=a.f,a.v
    # Seabed extends exactly to the whole plot boundary, with real shore walls.
    a.box(0,0,0,499,13,499,STONE,layer="terrain_and_foundation")
    a.box(0,14,0,499,60,499,9,layer="fluids")
    a.box(0,14,0,3,61,499,STONE,layer="terrain_and_foundation")
    a.box(496,14,0,499,61,499,STONE,layer="terrain_and_foundation")
    a.box(0,14,0,499,61,3,STONE,layer="terrain_and_foundation")
    a.box(0,14,496,499,61,499,STONE,layer="terrain_and_foundation")
    a.fluids.append({"kind":"water","shape":"box","min":[4,14,4],"max":[495,60,495],"contained_baseline":True})
    # Piled harbor decks: every pier visibly reaches the seabed.
    for x0,z0,x1,z1 in ((90,50,410,130),(70,330,190,430),(325,330,435,435)):
        a.box(x0,y-3,z0,x1,y-1,z1,PLANK,layer="terrain_and_foundation")
        for x in range(x0+4,x1,20):
            for z in range(z0+4,z1,20): a.pillar(x,z,y-4,LOG,width=5,bottom=13)
    a.gallery(a.profile("mist")["bowl_radius"],"mist")
    access=a.profile_tower("mist")
    a.path("lighthouse_approach",[[316,y,250],[350,y,310],access],width=9,material=PLANK)
    # A fully contained actual underwater branch with two stair exits. Native breathing
    # and player movement are a separate runtime gate, never inferred from this volume.
    a.box(145,42,175,165,56,315,BRICK,layer="functional_interiors")
    a.box(149,46,179,161,53,311,9,layer="fluids")
    a.box(148,54,178,162,55,312,GLASS,layer="functional_interiors")
    a.path("underwater_dry_recovery",[[165,y,175],[140,y,175],[130,55,205],[130,55,285],
                                     [140,y,315],[165,y,315]],width=7)
    for z0,z1 in ((181,188),(301,308)):
        a.box(151,54,z0,159,58,z1,AIR,layer="functional_interiors")
    a.fluids.append({"kind":"water","shape":"box","min":[149,46,179],"max":[161,53,311],"contained_baseline":True})
    a.rooms.append({"name":"Contained underwater passage","service":"optional wet route",
                    "standing":[155,47,245],"bounds":[145,42,175,165,56,315],
                    "native_breathing_acceptance":"NOT_RUN","water_route":True})
    # Rigging masts are original connected timber structures, not floating decorations.
    for x,z,h in ((100,350,165),(360,390,192)):
        a.pillar(x,z,h,LOG,width=5,bottom=13)
        v.line(x,h-20,z,x+40,h-43,z,3,LOG,layer="supports")
        v.line(x,h-20,z,x-30,h-58,z,3,LOG,layer="supports")


def _fire(a):
    a.solid,a.trim,a.roof=NETHER,BRICK,(CLAY,15)
    y,v=a.f,a.v
    # Grounded podium is intentionally continuous and heavily buttressed, never floating.
    a.box(12,0,12,487,28,487,STONE,layer="terrain_and_foundation")
    for inset,top in ((24,48),(45,69),(65,y-4)):
        a.box(inset,29,inset,499-inset,top,499-inset,BRICK,layer="terrain_and_foundation")
    a.gallery(a.profile("fire")["bowl_radius"],"fire")
    # Nested processional walls have wide human/giant openings carved at the end.
    for inset,top in ((45,y+28),(85,y+45)):
        a.box(inset,29,inset,499-inset,top,inset+6,NETHER,layer="landmark_masses")
        a.box(inset,29,499-inset-6,499-inset,top,499-inset,NETHER,layer="landmark_masses")
        a.box(inset,29,inset,inset+6,top,499-inset,NETHER,layer="landmark_masses")
        a.box(499-inset-6,29,inset,499-inset,top,499-inset,NETHER,layer="landmark_masses")
    # Furnaces are bounded and sealed from objectives by double masonry and glass.
    for x0,z0,x1,z1 in ((95,140,112,185),(385,140,402,185)):
        a.box(x0,35,z0,x1,62,z1,BRICK,layer="functional_interiors")
        a.box(x0+3,39,z0+3,x1-3,57,z1-3,11,layer="fluids")
        a.box(x0+2,58,z0+2,x1-2,60,z1-2,GLASS,layer="functional_interiors")
        a.fluids.append({"kind":"lava","shape":"box","min":[x0+3,39,z0+3],"max":[x1-3,57,z1-3],"contained_baseline":True})
    # Moon gate has two real piers and a spanned stepped lintel at monumental height.
    for x in (190,310):
        a.box(x-17,29,389,x+17,243,423,NETHER,layer="landmark_masses")
        for yy in (135,175,215):
            a.box(x-22,yy,384,x+22,yy+4,428,BRICK,layer="details")
    for yy in range(228,250):
        a.box(174,yy,390,326,yy,422,NETHER,layer="landmark_masses")
    a.box(212,228,390,288,236,422,AIR,layer="landmark_masses")
    a.landmarks.append({"name":"Ashen moon gate","bounds":[168,29,384,332,249,428],"supported_piers":2})
    for x,arm in ((112,18),(386,25)):
        a.box(x-17,29,317,x+17,y+38,351,BRICK,layer="landmark_masses")
        a.box(x-12,y+39,322,x+12,y+65,346,NETHER,layer="landmark_masses")
        a.box(x-arm,y+12,325,x+arm,y+19,343,BRICK,layer="landmark_masses")
    access=a.profile_tower("fire")
    a.path("sanctum_approach",[[316,y,250],[360,y,292],access],width=9)


def build_geometry(spec, volume, seed):
    """Build one 500 x 250 x 500 original site in a bounded external volume."""
    if tuple(volume.dimensions) != (500,250,500):
        raise ValueError("Arena geometry requires exactly 500 x 250 x 500")
    a=Architecture(spec,volume)
    kind=next(k for k in ("forest","cloud","sand","mist","fire") if "_"+k+"_" in spec["id"])
    # Whole-plot footing frames are real retaining walls/shore boundaries, not tiny
    # extreme-coordinate markers used to manufacture an occupied bounding box.
    a.box(0,0,0,499,2,499,STONE,layer="terrain_and_foundation")
    for x in (0,494): a.box(x,3,0,x+5,8,499,STONE,layer="terrain_and_foundation")
    for z in (0,494): a.box(0,3,z,499,8,z+5,STONE,layer="terrain_and_foundation")
    {"forest":_forest,"cloud":_cloud,"sand":_sand,"mist":_mist,"fire":_fire}[kind](a)
    a.enclosing_stadium(kind)
    a.court(kind)
    a.facilities(kind)
    metadata=a.finish()
    metadata["seed"]=int(seed)
    metadata["kind"]=kind
    metadata["construction_status"]="GENERATED_CANDIDATE_REQUIRES_INDEPENDENT_AND_NATIVE_AUDIT"
    return metadata
