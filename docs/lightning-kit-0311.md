# Core 0.3.11: Lightning Armor and distinct starter jutsu

This is an original Minecraft adaptation of Lightning Chakra Armor. It adds an optional early power trial, a six-action kit, posed electric ribbons, local witnessed reactions, five distinct starter techniques and six native melee chains. Check `docs/current-handoff.md` and version-bound evidence for the checks actually completed. Source implementation does not establish a finished game or hosted release.

## Get the kit in normal play

In a placed academy, enroll with the Dispatch Keeper and choose a foundation nature at Aki. You can then choose **Try Lightning Chakra Armor and its combat trial** without finishing the remaining academy exercises. An existing active contract must be finished or abandoned first.

Aki teaches the mode permanently and issues four Storm armor pieces, a Storm saber and a technique codex through the existing reward outbox. Your original nature and equipment remain yours. This is a permanent learned kit, not a temporary loaner. Reopening the trial does not issue another copy. A full inventory queues the equipment; make space and let delivery settle. Equip the four matching issued Storm pieces yourself.

Carry the codex to use the action bar, or hold it to cast the selected jutsu. `/vale arts` lists available techniques; click a technique to select it. The configurable action controls use the same server-validated roster. Chat/search text entry continues to suppress action inputs.

| Action | Chakra / recovery | Behavior |
| --- | --- | --- |
| Lightning Chakra Armor | 20 / 40 seconds | Up to 30 seconds; four chakra per second. Adds speed, attack and armor and reduces native incoming damage by 20%. It ends when its issued armor is removed, chakra cannot pay upkeep, or its duration ends. |
| Lariat | 12 / 4 seconds | Close frontal strike against up to three permitted visible targets, followed by push and a brief slow only after accepted native damage. |
| Lightning Step | 6 / 3 seconds | A directional velocity burst. Solid collision, loaded geometry and world border checks apply. It is physical movement rather than teleportation through walls. |
| Thunder Counter | 10 / 5 seconds | Arms a one-second window. The next observed accepted nearby visible melee contact can trigger one native counterattack. Canceled or fully restored contact cannot trigger it; an absorbed hit can. |
| Guillotine Drop | 25 / 12 seconds | Earned by completing the trial. Launch from solid ground, actually become airborne, then land to strike nearby enemies. It has a bounded flight window/range, one paid launch and one impact. It does not destroy terrain. |
| Release Lightning Armor | Free | End the mode deliberately. Generic forms and Lightning Armor are mutually exclusive. |

Mode actions share a brief global recovery. Upkeep is separate from each action's price. Cooldowns survive reconnect; active mode/counter/landing state is cleaned up. The mode does not authorize friendly fire: existing server PvP, ownership and team checks still apply.

Go to the far courtyard at campus-relative `(16, 1, 34)`. With the default campus floor `(0, 4, 0)`, that is around `(16, 5, 34)`. The trial gives a destination hint while approaching. Three owner/run-bound challengers spawn nearby. Defeat them and claim the contract at Aki or with `/vale mission claim` to earn Guillotine Drop, 200 contract XP and three merit, plus ordinary accepted enemy-kill XP. A resume creates only remaining opponents. Abandoning/reaccepting cannot bypass the new-run cooldown.

## Starter natures now change your decisions

Stable saved technique IDs remain intact. Fire uses a narrow multi-target flame burst. Water uses a wider short wave with push, extinguishing, slow and a brief wet mark. Lightning's precise short strike benefits from wet targets and commits the caster's footing. Wind pushes a directional group and can disperse supported hostile arrows through native protection. Earth raises a solid three-column, two-row wall on clear supported terrain.

Earth cover lasts five seconds, has no item/drop and cannot be moved by pistons. Its per-dimension lease journal restores only still-owned cover, preserves later player edits and tile entities, and does not load chunks to clean them. Protected or obstructed placement fails. During the academy Earth lesson, actual accepted cover near the owned construct counts; the other four natures require accepted contact. This avoids asking an Earth student to damage a target with a defensive wall.

The first five techniques are distinct; higher school fans/seals retain their existing mechanics. This checkpoint does not establish unique parity for every technique in the reference pack.

## Weapons and local feedback

With an issued matching weapon and armor, land three properly cooled left-click contacts on the same target to use the weapon's finisher. The saber, axe, dagger, glaive, spear and staff have distinct damage/control/movement outcomes. Tooltips explain the current role. Spam, canceled contact, target/stack changes and equipment loss interrupt the chain; recovery persists rather than resetting through unequip/relog. See `docs/melee-current.md` for exact current tuning and evidence scope.

Nearby vanilla villagers that actually see a settled Lightning action can look toward it and respond. Outside training, eligible mobile villagers can move away; academy guides encourage controlled practice. Memories are local, bounded and rate-limited. This does not yet supply full faction reputation, guards, schedules or friend CustomNPCs behavior. Third-person electric ribbons follow posed limbs and respect the existing form-visual option; first-person feedback currently uses particles, sound, action feedback and the authoritative HUD.

## Remaining acceptance

Read the version-specific receipts before distribution. Distinguish pure checks, disposable Forge events, a real OptiFine/JEI network join, visible gameplay, copied production-world restart, the exact Magma/plugin roster and Bisect deployment. Private QA helpers, recovered worlds, original third-party JARs and NPC exports stay outside the public repository.
