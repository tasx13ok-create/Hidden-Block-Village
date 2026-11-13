# VFX package: review notes for Codex

Branch: `claude/upbeat-ride-nyxq23`. Every effect is presentation-only: no damage, no collision, never saved. Gameplay outcomes still come from `ScrollEvents` and `ArsenalLogic`.

## New files (Fable-owned)
| File | Purpose |
|---|---|
| `src/main/java/village/vale/JutsuMath.java` | Pure math for hand techniques |
| `src/main/java/village/vale/WeaponMath.java` | Pure math and per-role palettes for weapons |
| `src/main/java/village/vale/arsenal/HandTechnique.java` | Carrier entity for scroll hand modes (id 121) |
| `src/main/java/village/vale/arsenal/WeaponPulse.java` | Weapon effect carrier (id 122): kinds CAST / ARC / SWING / AURA |
| `src/main/java/village/vale/client/JutsuVfx.java` | Renderer for Spiral Palm, Storm Lance, edges, lava, light mantle |
| `src/main/java/village/vale/client/WeaponVfx.java` | Renderer for all 27 arsenal roles, shot trails and ground fields |
| `src/main/java/village/vale/client/VfxDraw.java` | Shared primitives: camera-facing ribbons, so width is driver-independent |
| `tests/village/vale/JutsuMathTest.java` | 1002 checks |
| `tests/village/vale/WeaponMathTest.java` | 2088 checks |

## Hooks in Codex-owned files (please review)
- `Scrolls`: registers entity and renderer. `ScrollEffects.mode` calls `HandTechnique.begin`. `ScrollEvents` calls `HandTechnique.impact`.
- `RunPoses.finish` calls `JutsuVfx.pose`. `ClientProxy.preInit` calls `JutsuVfx.configure` (client cfg `jutsuDetail` 0–2).
- `Arsenal.registerEntities`: `weapon_pulse` id 122.
- `ArsenalClient`: registers the `WeaponVfx` renderer. After drawing the item model, `Representation` calls `WeaponVfx.shot` or `WeaponVfx.field`.
- `ArsenalLogic.release`: after a successful `commit`, calls `WeaponPulse.cast(p,spec,power)`.
- `ArsenalLogic` chain loop: calls `WeaponPulse.arc` for each secondary arc.
- `ArsenalLogic.meleeHit`: calls `WeaponPulse.swing` on each confirmed hit (wider when sweep mode is on; three claws for Talons).

## Proposed (shared build file, not edited)
`verifyJutsu` task: compile and run `JutsuMathTest` and `WeaponMathTest` (plain Java 8, no Forge).

## Evidence
Real 1.12.2 client under Xvfb and Mesa, using a private probe that is not committed:
- `release/evidence/vfx-hand-techniques-20261113.png`
- `release/evidence/vfx-arsenal-weapons-20261113.png`: side camera, technique and melee slash for each of the 27 weapons.

`gradlew check` passes.

## Known limits
- In the screenshots, the caster is hidden by vanilla because the camera is a stand-in entity.
- Claw lock and lunge get a hand flash plus three-claw slashes, but no dedicated lunge trail yet.

## Screenshot probe (dev only)
`clientchecks/village/vale/arsenal/VfxShots.java` is built only into the separate `vale-clientchecks-dev.jar` and does nothing unless `-Dvaleorders.vfxshots=true` is set. It opens a flat creative world and saves screenshots of every hand technique. Add `-Dvaleorders.vfxshots.weapons=true` to cover all 27 weapons instead.

## Four-way dodge (key V, rebindable)
- New: `Dodge.java` (channel `valeorders_dodge`; the server checks cooldown and one air dodge per jump, then applies the burst and a short hurt grace), `DodgeMath.java`, `client/DodgeClient.java`, `tests/village/vale/DodgeMathTest.java` (547 checks).
- Anime-style flash step (no flips). Direction comes from movement input: W gives a low dash with arms swept back, S or no input gives a guarded backstep, and A/D give mirrored side steps. Each step leaves 5 fading blue afterimages and speed lines, with a burst of 1.55 blocks per tick kept low to the ground.
- Hooks in shared files: `ValeOrders.preInit` calls `Dodge.register()`. `CommonProxy` and `ClientProxy` get `dodgeAnim`. `ClientProxy.preInit` calls `DodgeClient.initialize()`. `RunPoses.finish` calls `DodgeClient.pose`. Lang keys added.
- Tuning to agree with Codex's combat: cooldown 16 ticks, speed 1.55, i-frames ~6 ticks. Should it also cost resolve or stamina?
- Evidence: `release/evidence/dodge-4way-20261113.png`. Probe flag: `-Dvaleorders.vfxshots.dodge=true`.

## Blocky / pixel style pass
- `VfxDraw.voxel` mode: every weapon line, orb, slash and glow is rebuilt from face-shaded cubes (Minecraft block shading: top 100%, sides 80%/60%, bottom 50%) snapped to a 1/16-block grid.
- New `client/VoxelBits.java`: capped (600) client-only pixel particles with gravity, ground bounce and whole-pixel shrink. Uses:
  - Dodge: take-off chips coloured from the block underfoot, smoke cubes, skid chips, landing puff, and afterimages crumbling into blue pixels. Speed lines are pixel dashes.
  - Weapons: Siege Maul ground debris, strike shards, frost, sparks, and Emberbrand embers.
- Jutsu hand techniques keep the smooth style for now.

## Animated Fable hotbars (field-hud, `hotbarSkin=1` default, `0` restores the classic scroll skin)
New field-hud files:
- `FableHudMath` (springs, easing, pulses, climate curves): 550 checks in `modules/field-hud/tests/village/fieldhud/FableHudMathTest.java`.
- `FableEnv`: environment sampler plus 2D pixel particles.
- `FableHotbar`: main 9-slot bar.
- `FableRail`: right-hand 6-slot jutsu bar.

They reuse the real inventory, the existing `JutsuRailModel` and input (scroll focus, slot keys, server activation), and `JutsuRail.icon`. Only the drawing is new.

Hooks: `ShinobiHud.hotbar()` and `JutsuRail.draw()` pick the skin, and `HudConfig` gains `hotbarSkin`. The rack-rail path is unchanged.

Reactions:
- Selection: spring selector with ghost trail, slot lift and item tilt.
- Items: pickup pop with sparkles, removal poof, count bump, durability blink.
- Movement: sway when strafing, bob on jumps and falls, squash on landing.
- Damage and health: damage shake with red rim, low-health heartbeat.
- Environment: frost crystals and heat shimmer or embers by biome temperature, rain drops that land and slide, bubbles underwater, glowing rivets in darkness, lightning flash.
- XP: eased fill with glint and level-up pop.
- Rail: staggered slide-in, selected slot slides out, clockwise pixel cooldown border, ready flash and sparkle, cast recoil, chained-out disabled slots, widened and lit rim in focus mode.

Evidence: `release/evidence/fable-hotbars-20261113.png` (the probe flag `-Dvaleorders.vfxshots.hud=true` with the field-hud dev jar in the client's `mods` folder). The rail needs server abilities, so it did not appear in the flat-world probe and still needs checking in a real world.
