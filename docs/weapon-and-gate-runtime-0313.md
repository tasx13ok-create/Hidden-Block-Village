# Core 0.3.13 weapon presentation and expedition runtime

Core 0.3.13 adds bounded presentation carriers to the existing 27 arsenal weapons and an original server-owned adapter for the five authored dungeon gate routes. It also fixes boat boarding authority and blocks vanilla trading on mission couriers. The adapter passed an actual first/repeat route in a disposable Forge world; the real v17 placements and resident bindings are not applied in this checkpoint.

The exact cohort is Core 0.3.13, Residents 0.2.1, FieldHUD 0.14.1 and Rack 0.1.2. Residents now requires Core >=0.3.13 because its validated interaction calls the gate API. The concurrently edited FieldHUD 0.15, Rack 0.1.3 combat switching and other unaccepted Core/UI/art changes stay in the original checkout for later integration.

## Weapon presentation

Existing issued arsenal weapons keep their normal Minecraft attack/use controls, ownership, chakra costs, cooldowns and accepted gameplay outcomes. Their cast/contact hooks now emit weapon-colored flashes, slash arcs, chain connections or timed auras; the shot renderer supplies projectile presentation. These 27 presentation styles are not 27 newly proven unique combat mechanics.

WeaponPulse is cosmetic, non-colliding and non-persistent. Carriers have short lifetimes, refuse non-finite positions and are bounded to 32 live carriers per owner and 256 per world. Cosmetic spawn refusal or an exception cannot refund an already accepted guard cast or erase its gameplay/cooldown. Renderer/math implementation is present; local camera readability and visual polish are still unverified for this exact cohort.

## Expedition play and authority

Normal entry is right-clicking an explicitly bound Residents giver after completing and claiming the required contracts. The generic contract hook records the actual claim receipt; level or a total mission count does not invent prerequisite history. Older characters need a reviewed history migration or a fresh legitimate prerequisite claim. Field contracts and active gate runs exclude one another, and the existing journal shows the active gate objective.

The adapter supports physical outside-to-inside walking, protected native block interaction, ordered or unordered marker sequences, in-place nonce branch choices, owner/run-bound encounter groups, observed defense time, a following native villager courier, a protected native boat and plain-item hand-in. Named/NBT delivery items are refused. Accepted, finally uncanceled native deaths advance only the matching owner/run/stage group and do not also produce generic mission kill XP. First and repeat rewards use durable receipts/outbox delivery with cooldowns and replay refusal.

Friendly stages wait until the player approaches within 8 blocks of their measured start before creating an actor or starting the timer. Existing friendly actors require the player to remain within 12 blocks; escort navigation uses the nearer 8-block range. Leaving a defended checkpoint, losing a courier/skiff, logout and reconnect preserve bounded checkpoint progress while clearing actors and observed timers. Offline time jumps do not complete timed stages. GateSkiff permits only its matching live owner through native passenger admission; foreign boarding is rejected, while normal owner boarding and dismount remain available. Tagged friendly courier villagers reject ordinary native EntityInteract so they cannot open random vanilla trade menus.

Each player's gate state has an explicit schema. A canonical semantic hash binds authored content, sites and referenced dialogue to active runs, actors and pending rewards. Changed geometry/configuration cannot silently adopt an old active run. Dormant compatible accounts can rebind while retaining clears/cooldowns; unknown future schemas remain untouched. Core uses a fixed optional Encounters factory bridge to avoid a circular module dependency. Gate actions use the reviewed allowlist.

## Real-site integration remains open

On startup, missing authored JSON is copied into `config/vale-expeditions`; existing operator files are preserved. Reviewed `gates.json`, its dialogue files and local `sites.json` must supply actual anchors, placements, clickable blocks, actor starts and existing giver UUIDs. Null or missing sites fail disabled with a reason. A resident name or a proposed position is not a UUID binding.

All 28 v17 anchors now have a separate private measured draft, with route/geometry evidence. Those coordinates, the seven pending physical controls and proposed new resident bindings were not applied by this release. No fake NPC UUID or guessed world placement was shipped. Escort and boat protection use the defined observed presence/pathfinding objectives; additional hostile waves, boat steering routes and unavailable authored encounters are not invented or claimed complete.

## Observed checks and evidence limits

All 11 source areas and 36 artifact outputs passed with a stable source snapshot. The Core build log records 2,088 weapon presentation math checks, 50 gate checks (30 authority/cadence plus 20 content/disabled-site checks), and 1,002 existing hand-technique presentation math checks. Separate tooling ran 16 native-orchestration tests (one skip) and 12 build-tool tests successfully.

The dedicated Forge 2859 fixture passed 2,305 assertions. It exercised actual Residents interaction/final cancellation, real prerequisite settlement, walking/markers/nonces, foreign damage/target refusal, canceled and accepted owned deaths, defend/escort/skiff loss and retry, native boat boarding authority, courier trade refusal, actual logout/NBT reload, full-inventory rewards, reduced repeat rewards, replay/cooldown refusal and exact-sites identity mismatch. The route used a deliberately isolated synthetic measured platform, not a real v17 dungeon.

The harness uses native world entity updates with the required loaded 32-block neighborhood. It detaches already-dead owned fixture actors through native cleanup and continues real death updates for credited orphan corpses until they become dead; it does not fabricate their health, death or reward success. These correct test-world stepping and cleanup assumptions. They are separate from the production boarding/courier fixes. The pinned Rack 0.1.2 helper keeps its 18-byte protocol expectations; the unaccepted 27-byte combat-hotbar tests remain outside this cohort.

The final native runtime was removed. No graphical client, real network join, saved production-world migration, Magma/plugin run or hosted/Bisect deployment is claimed for this exact 0.3.13 source. Prior 0.3.12 previews and separately measured/copied worlds retain their own evidence. The full 49-mod/game/dungeon objective remains in progress.

Exact immutable report hashes, all 36 artifact pins, native labels and private fixture input hashes are in [the verification receipt](../release/evidence/core0313-verification.json). Frozen source SHA-256 is `aacf8985dc408704cba269daa5a4ef488635030a5e25d938462fbed7f5c0d096`; Core runtime SHA-256 is `c2db8c4d472aec9f38a3c1d7b44b4ac31c93dfd0bbfb6373908de1af8c049352`.
