# Native issued-weapon combos

Implemented in `village.vale.melee.MeleeCombos` and `MeleeComboRules`. This is production gameplay source, automatically registered with Forge's event bus. The private native fixture remains separate from the shipped Core JAR.

## Player flow

Equip four matching issued armor pieces and the matching issued saber, spear, dagger, glaive, staff or axe. Use ordinary left-click attacks. Three fully cooled, successful contacts on the same enemy produce the weapon's finisher. Normal building, vanilla attack cooldowns, shield priority, order right-click, and sneak-use techniques remain available.

The hand must contain the exact issued stack throughout each contact. Replacing a stack drops its in-progress chain; items without the owner's valid receipt cannot gain combo authority. The server samples cooldown before vanilla resets it; readiness must be at least 90%. A step expires after 64 server ticks, and contacts less than five ticks apart cannot advance. The native weapon attack speed remains the practical cadence. A finisher starts 80 ticks of recovery; swapping gear, dying, changing dimension, or reconnecting cannot erase the saved recovery deadline. Only live chains and pending contact observations are ephemeral.

Successful contacts produce an actionbar `1/3` or `2/3`, a native attack sound and a small visible sweep. Finishers show their name, native critical particles and a heavier sound to the player and nearby observers. Weapon tooltips explain the input and recovery. These are native Minecraft cues, not imported animation or sound assets.

| Weapon | Third accepted contact | Decision / role |
| --- | --- | --- |
| Saber | Native hit amount ×1.40; Resistance I for 24 ticks | Stay in contact and briefly trade through the next exchange. |
| Spear | Native hit amount ×1.10; native knockback strength 0.85 | Break the enemy's line and reclaim spacing. Knockback resistance remains effective. |
| Dagger | Native hit amount ×1.80 from behind, ×1.20 otherwise; Speed II for 30 ticks | Earn a flank, then reposition using ordinary movement. Rear bonus requires target-facing dot product below −0.35. |
| Glaive | Native hit amount ×1.20; up to two other visible frontal hostiles within three blocks take four base damage through existing protection authority | Choose a clustered target and face the group. No remote reach is added to the initial contact. |
| Staff | Native hit amount ×1.10; Weakness I for 40 ticks; recover four resolve | Suppress a dangerous enemy and sustain the next technique. No heal loop. |
| Axe | Native hit amount ×1.65; Slowness I for 20 ticks | Commit to the slow native swing and cash out heavy pressure. No stun or invulnerability bypass. |

Damage amounts enter the existing native pipeline. Armor, enchantments, order bonuses, potions, shields, absorption, native invulnerability and other protection listeners still determine the result. The table is not a promise of fixed post-armor damage. Potion listeners may refuse effects. Native knockback may be resisted. Killing the target can finish the chain and grant a self effect, but never applies control effects to a dead entity.

## Authority and transaction boundaries

1. `AttackEntityEvent` records a server-observed native intent, exact issued stack, target, cooldown and prospective step. A protected, friendly, nonhostile NPC, creative/spectator or out-of-reach target cannot qualify. Reach is tested from the player's eyes to the target bounding box, bounded to 3.1 blocks, with native visibility. No remote target search creates the initial hit.
2. The matching direct native player `LivingAttackEvent` consumes that intent once, after confirming vanilla reset the native swing clock between intent and damage. Projectile damage, ordinary ability-generated player damage and vanilla sweep victims cannot consume it. Intent lifetime is at most one tick.
3. The exact same `DamageSource` transaction receives a third-contact modifier in `LivingHurtEvent`. This changes the existing attack rather than making a synthetic second hit against invulnerability frames. Nonfinite scaling is refused. Other listeners retain the ability to change or cancel native damage.
4. At server tick END, the module examines the final retained event objects and actual combined health plus absorption. Canceled AttackEntity/Attack/Hurt/Damage events, nonpositive pre-absorption damage, no net loss, stale observations, changed stack/receipt, changed world or invalid equipment cannot commit a step or secondary effect. A fully absorbed native contact can qualify through real absorption loss; a canceled damage event cannot. A native attack can be visually attempted without earning combo progress.
5. Only after acceptance are the step, persisted recovery, feedback and secondary effects committed. Cleave enters `ArsenalAccess.hit`, preserving the project's existing PvP, team, NPC, cancellation and observed-damage authority. A reentry guard excludes finisher-generated damage from combo credit.

There are at most 256 active player sessions. Each stores one bounded cadence, one pending native intent and one observed native hit. Unused sessions expire after 200 ticks. Logout, owner death and dimension changes drop ephemeral observations. Equipment changes break the chain; the independent persisted recovery survives. No accumulating target list, scheduled world actor or client-supplied combat packet is introduced.

## Verification

`MeleeComboChecks` is a pure Java component test. Its standalone JDK 8 run passed **16 assertions**, including 10,000 bounded transitions. It checks pacing, exact timing edges, target/weapon interruption, preview purity, saved recovery restoration, empty identities and a rewound clock.

`MeleeNativeChecks.run(EntityPlayerMP)` is the private Forge integration entry point. It creates disposable issued equipment and native zombies, invokes `attackTargetEntityWithCurrentItem`, and checks all six finisher outcomes. It additionally checks four late cancellation stages, uncooled attacks, target swaps, stack replacement, damage with no final health loss, equipment changes before deferred resolution, retained recovery after session clearing, synthetic player damage and unissued blades. It must run only in the existing disposable native acceptance world, not the user's live world.

The pure check is wired into the component build and the native helper into the private acceptance harness. Core 0.3.11 passed all eleven component builds and the full 1,704-assertion Forge run, including these six weapon roles and cancellation/lifecycle cases. Exact source/artifact identities are in `release/evidence/core0311-verification.json`. The tooltip listener is a separate client-only subscriber so dedicated startup does not load Minecraft's client tooltip class. This is native combat acceptance; a complete human playthrough of every weapon animation is not claimed.

## Scope

These combos cover the six existing WardBlade roles across their matching orders. Specialized arsenal weapons keep their own implemented controls and state machines. The feature adds functional contact chains and native feedback; it does not claim custom third-person skeletal animation, a new parry input, armor penetration, a stun system or equivalence to a reference-mod weapon solely from a move name.
