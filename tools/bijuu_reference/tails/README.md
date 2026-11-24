# Bijū armor movement

This overlay targets the private Steam Dash 2.0.0 assembly with the v3 appendages. The reference mount, pivots, boxes, UVs and all 111 textures come from that assembly. The adapter transfers native joint motion to the v3 mesh. A separate server-owned attribute controls the sprint wind-up. It does not ship the private assembly.

## Art direction

The official roster identifies the nine beasts, and the anime shows Bee and Gyūki and then Naruto and Kurama acting together in battle. These establish the identities and the coordinated combat reference. The supplied v3 rigs define the appearance of the wearables. A Minecraft crouch, shield block, ladder climb and armored sprint curve require original motion choices. The numbers below are gameplay tuning. [Official roster](https://naruto-official.com/en/news/01_2376) · [Anime retrospective](https://naruto-official.com/en/news/01_1333)

| Full set | Native roles | Added speed at cap | Continuous ground sprint to cap |
| --- | --- | ---: | ---: |
| Shukaku | Single sand anchor | 18% | 1.8 s |
| Matatabi | Alert and balance pair | 36% | 2.4 s |
| Isobu | Two bulwarks and central keystone | 54% | 3.0 s |
| Son Gokū | Paired bracers and sweepers | 72% | 3.6 s |
| Kokuō | Outriders, drifters and steam leader | 90% | 4.2 s |
| Saiken | Feelers, low lobes and mantle | 108% | 4.8 s |
| Chōmei | Six wing blades and the abdomen keel | 126% | 5.4 s |
| Gyūki | Left and right anchor, reach, coil and probe | 144% | 6.0 s |
| Kurama | Left and right fan around the crown leader | 162% | 6.6 s |

## Inputs and limits

The v3 renderer reads the original armor engine after its per-wearer animator and terrain solver finish posing the stock bones. Each visible root inherits its native root's rotation relative to rest. Further down the chain, bends and dynamic spacing are distributed by overlapping intervals along each rig's length. The waist socket stays fixed. The six Chomei wings bind to the upper, middle and lower blade on each side. Its seventh appendage binds to the abdomen keel. Gyuki's alternating reference tentacles bind to the corresponding left and right roles.

The installed Naruto Core 0.3.16 supplies the armor effects and running pose. The private armor assembly supplies the tail tick driver, personalities, strikes and terrain solver. Earlier experimental physics passes have a separate [provenance record](../../../collaboration/fable/TAIL-PHYSICS-PROVENANCE-DISPOSITION.json). Surface contacts can differ between the stock and visible meshes because they have different lengths and rest curves.

The server checks all four issued pieces and measures actual forward sprint displacement. The smooth acceleration curve tops out at 18% per tail at 20 ticks per second. Jumps carry earned momentum, sharp reversals bleed charge, and releasing sprint drops the applied bonus. Hitting a wall, being hurt, changing full sets, teleport-sized displacements, mounting, climbing, swimming or flying clear the run. Vanilla and other mods retain their own speed contributions. A fixed nonpersistent modifier prevents permanent or stacked speed after a restart.

## Rebuild and verify

Use Java 21 with Java 8 output, the original private Steam Dash assembly, the SRG Minecraft 1.12.2 JAR, its matching Forge development classes.

```
python tools/bijuu_reference/tails/build.py ORIGINAL.jar OUTPUT.jar SRG_MINECRAFT.jar FORGE_DEV.jar
```

`qa/SprintChecks.java` exercises the wind-up, cap, sharp reversal, jump carry and release at each tail count. The private singleplayer fixture compiles `qa/RunProbe.java` and `qa/PoseChecks.java` against the candidate, SRG Minecraft, Forge and authlib. Put only the compiled fixture classes in a separate test mod in an isolated game directory. It issues valid gear, drives the real client, compares movement with and without the armor running modifier, checks the appendage count and motion, captures a third-person frame for each beast and exits. The binding checks perturb every original joint in the actual client model, measure how much bend and dynamic spacing reach the visible joints, restore the source and check for drift and a moving waist socket.
