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

## Enhanced original hotbars (field-hud)
The owner asked for the existing `ScrollHotbar` and `JutsuRail` to be enhanced rather than replaced. Their art and layout are kept; there is no new skin or config switch.

New files:
- `HotbarFx`: the reaction layer.
- `FableEnv`: environment sampler plus 2D pixel particles.
- `FableHudMath`: springs and curves, with 550 checks in `modules/field-hud/tests/village/fieldhud/FableHudMathTest.java`.

`ScrollHotbar` now has:
- Body sway, bob and landing squash.
- Damage shake.
- Per-slot lift following the eased selector, and a selector ghost trail.
- Items that tilt on scroll, pop on pickup with sparkles, poof on removal, bump on count changes and blink when low on durability.
- Climate, damage, heartbeat, lightning, wet, underwater and dark tints, plus pixel weather.

`JutsuRail` now has:
- Staggered slide-in, and the selected slot slides out (the selector follows).
- Cast recoil and flash, and a ready flash with sparkle.
- A clockwise border fill alongside the existing arc.
- A focus glow pulse, counter-sway and shake.

The rack-rail path is unchanged. Motion off (or Quiet mode) snaps everything to static.

Evidence: `release/evidence/fable-hotbars-20261113.png`. The rail needs server abilities, so it still needs checking in a real world.

### Anime detail pass
New `AnimeDraw` helper (per-vertex-colour gradients, radial glows, halos, flame tongues, spirals, ribbons, ink blobs, kunai, with additive blending and full state restore). `HotbarFx.under/over/top/railUnder/railBurst` add these layers:
- A breathing multi-layer chakra aura and drop shadow, with chakra wisps rising off the scroll.
- Washi paper fibres, a sumi-e ink wash, per-slot gloss and chakra underlight.
- On the selected slot: a rotating swirl seal, a halo, chakra flames and twin kunai brackets.
- An ink splash on selection change.
- Flowing chakra streams with travelling beads along both edges, spinning seals on the end caps, rising chakra motes and a light sweep.
- The chakra colour shifts red with danger, orange when burning and icy in the cold.
- Rail slots get element-coloured gradients and glow (by school), a rotating seal, selected flames and kunai, and a radial-ray ready burst.

## Original 256x256 encounter skins
`vale-encounters` `ClientProxy` now picks one of six original HD skins per profile (`Skins.of`) instead of `steve.png`. Textures are in `src/main/resources/assets/valeencounters/textures/entity/`, and the painter is in `tools/skins/`. The module compiles against the current core dev jar. The skins have not yet been checked in the client on encounter actors.

## Licensed-mod bridge (vale-orders `village.vale.bridge`)
The bridge works by reflection and registry name only. No licensed code or assets are referenced or copied. Config lives in `config/valeorders/`.
- `ChakraLink`: reads NarutoMod chakra through the API already used by the HUD. A spend method is discovered at runtime and trusted only if chakra actually goes down. It reports its status in the log.
- `MoveCost.pay`: spends chakra when linked (`preferChakra`, `chakraPerResolve`) and Vale resolve otherwise. The dodge now goes through it (`dodgeCost`, default 0).
- Discovery: records item uses, entity spawns and damage sources from the watched namespaces into `bridge-discovered.json`.
- `bridge-moves.json`: maps a licensed item or entity id to a Vale style (spiral, charge, fireedge, windedge, lava, lightmode, `weapon:<arsenal key>`). It plays Vale visuals only, via `HandTechnique.visual` (a visual-only carrier) and `WeaponPulse.cast`.
- Hooks in shared files: one line each in `ValeOrders.preInit` (`Bridge.init`) and `Dodge.perform` (`MoveCost.pay`).
- Still needed: the licensed jars, to fill the move map, and a check of the chakra spend method against them.

## Menu and screen immersion (field-hud `MenuFx`, config `menuEffects`)
All vanilla and modded screens keep their layout; this only adds presentation.
- **Pause, options and other in-world menus:** layered ink-and-chakra backdrop with a slow rotating seal watermark, brush-stroke frame bands, chakra wisps, falling leaves and rising motes. Colour follows the time of day (dusk orange, night indigo), rain and low health.
- **Opening a screen:** brush ink reveal.
- **Buttons:** on hover, a brush underline sweeps in with a kunai marker and a soft tick. On click, an ink splash and a wooden clack with varied pitch.
- **Title screen:** petals and chakra motes.
- **Death screen:** red ink bleeds in from the edges and drips, with a slowing heartbeat halo and drum.
- **Loading terrain:** a spinning seal and a "Gathering chakra" line.
- **Inventories:** edge vignette and margin-only motes that never cover slots.
- **Chat:** ink band under the input.
- **Tooltips:** brush-ink colours by rarity, gold for Vale items, kunai corner marks.

Motion off or Quiet mode keeps only the static colouring. Evidence: `release/evidence/menu-immersion-20261113.png`. The probe flag is `-Dvaleorders.vfxshots.menus=true`.

### Bridge pass 2
- **Chakra mirror (`Bridge.mirror`):** with NarutoMod linked, resolve becomes a live 0–100% mirror of chakra each player tick. Any drop caused by an add-on move is spent from real chakra, scaled by `costScale`. Reserve-and-refund inside one action nets to zero. No add-on cost or refund code was changed.
- **Dodge evasion (`Dodge.evading` + `Bridge.evade`):** during the dodge window, attacks from any source are cancelled, licensed jutsu and projectiles included. Void and creative-bypass damage still apply.
- **Auto-mapping (`BridgeMath.infer`, `autoMap`):** watched-mod items and entities without an explicit `bridge-moves.json` entry get a style from registry-name keywords. Examples: rasen→spiral, chidori/raiton→charge, katon/fire→fire edge, futon/wind→wind edge, yoton/lava→lava, suiton/mist→Mist Censer, doton/earth→Siege Maul, kunai/shuriken/senbon/swords/tags→matching weapon effects. An entry of `"none"` disables one. 45 checks in `tests/village/vale/BridgeMathTest.java`.
- **Still to verify with the real jars:** the chakra spend method, and whether the keyword inferences pick sensible styles for the actual IDs.

### Bridge pass 3: chakra adapter rebuilt to the pinned API facts (Claude-Bridge-Facts-and-Diagnostics, 2026-11-13)
- **`ChakraLink`**
  - Binds the exact public signatures: `Chakra.pathway(EntityPlayer)`, `PlayerTracker.isNinja`, `getAmount()D`, `getMax()D`, `consume(DZ)Z` and `clear()V`.
  - Never uses the float (fraction-of-max) overloads.
  - Never uses client-only `isInitialized` on the server.
  - Server readiness is `isNinja`, and a pathway is never created for a non-ninja.
  - Base NarutoMod is the only provider; the Spring duplicate pool is not touched.
  - Status is ABSENT (resolve fallback allowed), UNSUPPORTED (installed but mismatched: fail closed) or READY.
- **`ChakraLedger`** (pure, 23 adversarial checks in `tests/village/vale/ChakraLedgerTest.java`):
  - Reserve verifies the exact debit postcondition.
  - An exact balance goes through `clear()` with a verified zero.
  - Zero cost is a no-op.
  - NaN, infinite and negative costs are rejected.
  - Insufficient balance spends nothing.
  - A throw after a possible debit is UNKNOWN_STATE: recorded, never retried or fallen back.
  - Commit and refund settle once.
  - Refunds are additive, so independent spends and regeneration are preserved.
  - Refunds are capped at the pre-balance and below the 4×max overfill zone.
  - Over-cap refunds use `true` only to restore the recorded debit.
  - A changed pathway identity (body replaced) forfeits the refund.
- **`MoveCost`**
  - The agreed facade is `reserve(EntityPlayerMP,int) -> Ticket|null`; `Ticket.commit()`, `refund()` and `close()` settle once.
  - There is a per-player reentry guard.
  - `chakraFor(cost) = cost × chakraPerUnit` (config `chakraPerUnit`, absolute units).
  - `pay()` is the irreversible-action shortcut; the dodge uses it.
  - `availableUnits()` returns the raw double view divided by the scale.
- **`Bridge`**
  - The resolve↔chakra mirror is removed. It paid after the fact and squeezed chakra into the 0..100 byte.
  - `autoMap` now defaults to off (keyword guesses are proposals only).
  - Discovery reloads the previous `bridge-discovered.json`, so data persists across runs.
- **Not done here (Codex-owned per the facts note):**
  - Moving the existing scroll, weapon and upkeep callers onto `reserve`/`commit`.
  - CustomNPC-bus reward de-duplication.
  - Ninjutsu versus physical classification for dodge and guard.
  - Native full-pack acceptance.

## Bijuu chest power core (vale-orders `client/ArmorCoreFx`)
A render layer that detects Hidden Village Bijuu Armor chestplates by reflection (`village.bijuu.BijuuArmor$Armor.beast`), with no compile dependency.

Drawn on the body bone, in layers:
- a recessed socket and bevelled voxel frame
- rune studs lighting in sequence
- energy veins with travelling pulses
- a counter-rotating shell around a tumbling emissive core
- two tilted orbit rings
- additive bloom and flaring shafts
- a pulsing ground light pool
- shed voxel sparks

"Power" (sprint, swing, recent damage, low health; smoothed) drives brightness, pulse rate, ring speed and shafts. The colour follows the beast slot.

Verified in the client: all nine sets, calm and powered. The test jar was built from this branch (0.3.10 label). Integrate into the accepted 0.3.15+ Core rather than shipping it standalone.
