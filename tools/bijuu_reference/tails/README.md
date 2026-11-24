# Bijū armor movement

This overlay targets the private Steam Dash 2.0.0 assembly with the v3 appendages. The reference mount, pivots, boxes, UVs and all 111 textures come from that assembly. The new code supplies local joint rotations and one server-owned running attribute. It does not ship the private assembly.

## Art direction

The official roster identifies the nine beasts, and the anime shows Bee and Gyūki and then Naruto and Kurama acting together in battle. These establish the identities and the coordinated combat reference. The supplied v3 rigs define the appearance of the wearables. A Minecraft crouch, shield block, ladder climb and armored sprint curve require original motion choices. The numbers below are gameplay tuning. [Official roster](https://naruto-official.com/en/news/01_2376) · [Anime retrospective](https://naruto-official.com/en/news/01_1333)

| Full set | Motion direction | Added speed at cap | Continuous ground sprint to cap |
| --- | --- | ---: | ---: |
| Shukaku | One heavy counterweight rises on braking and settles behind the run | 18% | 1.8 s |
| Matatabi | Paired supple tails curl outward and balance turns | 36% | 2.4 s |
| Isobu | Three stiff plated tails spread in a brace | 54% | 3.0 s |
| Son Gokū | Four muscular tails counter the attacking arm | 72% | 3.6 s |
| Kokuō | Five plumes gather behind the runner and lift with the stride | 90% | 4.2 s |
| Saiken | Six soft tails carry a slower wave into the tips | 108% | 4.8 s |
| Chōmei | Three mirrored wing pairs beat from the hinges in air. The seventh appendage steers separately | 126% | 5.4 s |
| Gyūki | Eight tentacles curl more strongly toward their ends during a strike or brace | 144% | 6.0 s |
| Kurama | Nine fox tails draw in at speed and fan out for impact and defense | 162% | 6.6 s |

## Inputs and limits

The client samples each visible wearer once per game tick and interpolates for drawing. A short history carries turns, acceleration, braking, jumps, falls, landing and recoil from the waist to the tips. Strides, sneaking, water, climbing, riding, gliding, head turns, handed swings, item use, blocking and drawing a bow affect the pose. Steam Dash supplies charge, blink and pin cues. Nearby blocks steer the tail fan through five short collision probes. These provide approximate clearance. There are no segment hitboxes, grapples, damage or flight grants attached to these cosmetic poses.

The server checks all four issued pieces and measures actual forward sprint displacement. The smooth acceleration curve tops out at 18% per tail at 20 ticks per second. Jumps carry earned momentum, sharp reversals bleed charge, and releasing sprint drops the applied bonus. Hitting a wall, being hurt, changing full sets, teleport-sized displacements, mounting, climbing, swimming or flying clear the run. Vanilla and other mods retain their own speed contributions. A fixed nonpersistent modifier prevents permanent or stacked speed after a restart.

## Rebuild and verify

Use Java 21 with Java 8 output, the original private Steam Dash assembly, the SRG Minecraft 1.12.2 JAR, its matching Forge development classes, and ASM 9.

```
python tools/bijuu_reference/tails/build.py ORIGINAL.jar OUTPUT.jar SRG_MINECRAFT.jar FORGE_DEV.jar ASM.jar
```

`qa/SprintChecks.java` exercises the wind-up, cap, sharp reversal, jump carry and release at each tail count. The private singleplayer fixture compiles `qa/RunProbe.java` and `qa/PoseChecks.java` against the candidate, SRG Minecraft, Forge and authlib. Put only the compiled fixture classes in a separate test mod in an isolated game directory. It issues valid gear, drives the real client, compares movement with and without the armor running modifier, checks the appendage count and motion, captures a third-person frame for each beast and exits. The pose checks exercise delayed and repeated render samples, independent wearers, teleport, turns, airborne motion, landing, hit, attack, swim and climb.
