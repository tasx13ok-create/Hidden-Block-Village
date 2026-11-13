# Shinobi systems and behavior contracts

All new values below are proposed tuning defaults, not observations of legacy mods. Existing technical IDs and saved states remain stable until an explicit migration is implemented. Narrative Naruto-facing names are presentation/content keys; internal namespaces remain owned and save-compatible.

## 1. Character identity and persistent state

Persist schema version, player UUID, village allegiance, independent village reputation, rank, nature affinities, lineage/specialist choices, learned techniques, mastery, equipment ownership, modes, companion contracts, mission graph state, exploration discoveries, currency balances, receipt/outbox records and cooldown deadlines. Use stable keys rather than translated text or collection indexes. Unknown fields survive migrations where practical; unknown referenced content is quarantined with an actionable diagnostic rather than silently replaced.

Character creation offers village and a coherent starting specialization. A server can opt into randomized lineage for roleplay, but the default design provides a comprehensible choice and a deterministic route to desired gameplay. Never make paid rerolls a prerequisite. Explain rarity as content rarity or narrative status, not an exemption from balance. Separate character power from administrator role, social rank and cosmetic identity.

Rank path: academy student → genin → chunin → jonin → specialist/leadership responsibilities. Promotion combines practical skill checks, mission contributions and a narrative/training assessment. Teamwork and support count. Kage is a limited governance role with a configured term and succession rules, not an unlimited combat multiplier. Missing-nin status changes services and missions while retaining a viable progression route and a way to recover reputation.

## 2. Chakra, stamina and strain

Preserve the current resolve ledger until a versioned chakra presentation/migration exists. The desired model separates chakra expenditure from exertion and transformation strain. Chakra powers techniques; exertion limits repeated physical bursts; strain limits unsafe form stacking. Food and rest support recovery without making eating a combat exploit. Do not add three simultaneous bars before the HUD and encounter design justify them.

Proposed starting tuning: 100 chakra; basic techniques cost roughly 6–12, tactical techniques 16–30, high-impact techniques 35–60; exact costs are per-technique data. Regeneration slows during active casting and direct combat but never leaves a novice permanently unable to act. Basic weapon defense and locomotion remain useful when chakra is exhausted. Concentration recovery creates a visible vulnerable window; it cannot be held while attacking, changing modes, riding a protected vehicle or spamming reconnects.

Costs reserve before native callbacks. Denied effects refund only the reserved attempt to the actual debited account. Successful effects remain charged even if callbacks replace the held stack or lesson state. Model each action as a state machine: eligible → reserved → windup → active effect → settlement → recovery. Record interruptions explicitly. Reconnect, death, dimension change, target despawn and packet duplication must not reset an earned cost or create two effects.

## 3. Universal technique record

Each technique definition must specify:

| Field group | Required detail |
| --- | --- |
| Identity | Stable ID, localization keys, version, nature/lineage/style tags, source observation and original author |
| Acquisition | Mentor/trial/recipe path, rank/mastery prerequisites, respec and migration behavior |
| Inputs | Tap/hold/release behavior, hand-sign sequence if any, focus-slot rules, aim mode and cancel binding |
| Timing | Windup, active interval, recovery, charges, cooldown scope, saved deadlines and interruption states |
| Resource | Initial reservation, sustained drain, hit/miss/cancel settlement, maximum duration and strain |
| Geometry | Target filters, reach, sweep/volume, line of sight, collision masks, loaded-chunk and world-border behavior |
| Effect | Damage/status/utility, scaling formula, stacking/refresh policy, secondary-effect conditions and cleanses |
| Counterplay | Tell, dodge/guard/dispel route, terrain interaction, immunity windows and ally response |
| Presentation | First/third-person animation, original model/material, readable VFX, audio, reduced-effects version and camera behavior |
| Multiplayer | Server authority, request payload, rate limit, owner/team checks, observer range and stale-message rejection |
| Recovery | Death, relog, respawn, transfer, unload, expiration, owner replacement, exception and disabled-content handling |
| Evidence | Component cases, native scenarios, two-client behavior, recorded visual review and remaining limitations |

No label-only techniques. A projectile, area, summon and stance cannot share one generic damage action and be counted as four finished powers. Common engines are desirable; distinct behavior and authored content still need proof.

## 4. Combat foundations

Default melee uses normal Minecraft aim and physical weapon reach. Give light attacks, held/charged attacks, guard, deflect and a short evasive action clear recovery. Separate collision from visual trail. Server chooses valid hits; packets do not carry trusted damage or arbitrary target coordinates. Current Crescent Talons lock, dodgeable lunge, rear damage and bounded bleed are a foundation to extend, not proof that all reference weapons are complete.

Soft lock helps orientation but must respect occlusion, range, targets behind the player, owner/team protection and disengagement. It must not silently rotate a player's camera across large angles or hit through walls. Spectators, allies, peaceful service residents and dead actors are ineligible unless an explicit opt-in scenario says otherwise. Combat through tall grass should trace past appropriate replaceable foliage while still respecting solid terrain and entity ordering.

Guard resolves facing and impact direction; stronger attacks can break guard with a clear tell. Control effects have bounded duration, diminishing repeat value and an escape/counter opportunity. Avoid continuous stun chains and unlimited airborne juggles. Apply damaging secondary effects only after accepted primary contact when appropriate. Vanilla/Forge cancellation, team protection, region protection and target invulnerability participate in the same outcome.

Movement includes short dash, wall/tree training, water-surface control, leap and substitution. Validate finite positions, loaded geometry, collision, support, world border and destination dimension. Distinguish a rejected move from a move that happened before a callback threw. Preserve vanilla jumping, swimming and building. Training routes teach movement in forgiving spaces before demanding it over a lethal drop.

## 5. Five base natures and advanced families

| Nature | Introductory kit | Tactical identity | Counter and world interaction |
| --- | --- | --- | --- |
| Fire | Small aimed burst, sustained cone, delayed zone | Pressure and space denial | Visible ignition tells; extinguish/evade; no persistent terrain burning by default |
| Water | Water bolt, moving serpent, temporary binding | Flow, redirection and control | Break line of sight, release/rescue conditions, terrain wetness as bounded context |
| Earth | Raised cover, grounded thrust, armor stance | Position and durability | Flanks, climb routes, finite cover lifetime; restore changed blocks deterministically |
| Wind | Cutting arc, push/gust, weapon infusion | Spacing and projectile interaction | Telegraph direction; respect knockback immunity and borders; no ally griefing |
| Lightning | Aimed lance, short acceleration, committed melee charge | Precision and brief burst | Interruptible commitment, clear endpoint, predictable escape angle |

Add combined families through parent-mastery plus an authored trial: Ice, Wood, Lava, Boil, Storm, Magnet, Scorch, Dust and Crystal, followed by other verified reference obligations. Each has an engine-specific role, not simply two damage colors. Ice uses readable routes and breakable constraints; Wood emphasizes seeded control and support; Magnet requires a bounded affected-material set; Dust needs strong exposure/counterplay and caps against bosses; Crystal cannot leave permanent world debris. Space-time, sensory, medical, seals, sound, puppetry, insects, shadows and mind techniques are independent specialist tracks.

Nature interactions use a small documented rule table. For example, water can reduce a temporary fire zone; earth can interrupt a ground-traveling attack; wind can redirect an eligible light projectile. The table controls eligibility, magnitude, ownership and VFX; it must not become an uncontrolled pairwise physics simulation. Decide conflict order deterministically on the server. In player-owned building areas, temporary battle terrain must not overwrite containers or protected blocks.

## 6. Lineages, eyes and specialist paths

The target roster includes requested Naruto archetypes, subject to observed-reference expansion: Uchiha, Hyuga, Uzumaki, Senju, Nara, Yamanaka, Akimichi, Aburame, Inuzuka, Kaguya, Hozuki, puppet specialists, taijutsu/gates specialists, medical specialists, sealing specialists, sensory specialists and other identified pack-specific branches. This is a planned roster, not a claim that every named path already exists or is implemented in `mods.zip`.

| Path | Signature mechanic | Growth | Essential counter/limit |
| --- | --- | --- | --- |
| Eye prediction/copy archetype | Read a bounded attack tell; copy only eligible learned-form techniques | Training, sparring and authored trials | Information is partial; no invisible-player ESP; expensive focus and copy whitelist |
| Gentle-fist/sensory-eye archetype | Precise close-range pressure and bounded chakra sight | Accuracy drills, defensive stance, team reconnaissance | Range/occlusion limits; concentration tells; no unrestricted x-ray mining |
| Sealing/resilience archetype | Placed marks, bindings and rescue/support seals | Ritual components, mentor puzzles, squad utility | Placement/protection validation, destructible or dispellable nodes, active-node cap |
| Wood/life archetype | Temporary terrain control and ally shelter | Ecology tasks and layered mastery | Finite structures, area exclusion, no overwrite of saved player buildings |
| Shadow archetype | Facing/light-aware tether and position manipulation | Setup, pursuit and multi-target restraint | Line break, maximum reach, diminishing control, visible anchor |
| Mind archetype | Information, brief control window and coordinated support | Read intent, bind, assist | Real-player camera/agency preserved; strict PvP opt-in and breakout |
| Expansion archetype | Size, mass and committed impact | Food/preparation and form discipline | Model/collision consistency, door clearance, recovery and stamina drain |
| Insect archetype | Small owned swarms with sensing/drain/support roles | Colony care and finite brood capacity | Aggregate bounded actors/effects, owner cleanup, area counterplay |
| Hound archetype | Bonded companion and coordinated pincer attacks | Trust and team timing | Owner/companion separation limits, protected targets, fair replacement on loss |
| Bone archetype | Persistent short-lived weapon forms and movement pressure | Controlled-form trials | Health/strain costs, bounded projectiles, distinct weak windows |
| Fluid-body archetype | Conditional evasion and fluid movement | Source-context training | Finite duration, cooldown lock, defined electrical/containment interaction |
| Puppet archetype | Remote weapon actor with a vulnerable controller | Modules and positioning | Leash range, line/terrain restrictions, lost-control state and cleanup |
| Gates/taijutsu | Escalating physical commitment | Skill trials and controlled exertion | Increasing strain, meaningful disengagement, no free toggling past costs |
| Medical | Triage, diagnosis, cleanse and protected recovery | Rescue objectives and field practice | Cast exposure, resource cost, anti-stacking, support contribution credit |

Eye progression separates acquisition, awakening, mastery and sustained use. Advanced stages require an authored story/trial rather than repeated self-harm, a payment or unexplained kill count. Provide multiplayer-consensual alternatives to player-sacrifice requirements. Dojutsu, sage, curse and beast transformations have explicit compatibility slots and conflicts; never assume all can stack. Save cooldown/strain through relogging. Respec retains earned story flags, clearly identifies refundable points and cannot duplicate one-time grants.

## 7. Nine ward paths, sage forms and large silhouettes

Treat each of the nine beast-inspired paths as a unique movement/combat/support identity with an original visual design: sand/defense, blue-flame agility, shell/tide, molten strength, steam mobility, corrosive mist, aerial versatility, ink/tentacle control, and powerful multi-tail coordination. These thematic targets require authored designs and behavior review; they do not assert reuse rights to another mod's named models.

Progression stages: discovery/contract → unstable cloak → trained partial manifestation → mastered mantle → conditional full manifestation. Each step has a mentor/inner trial, a distinct tactical action, cost curve, counterplay and visual state. Full manifestation is restricted by terrain fit, encounter rules and performance budget. A transformation cannot clip the camera, leave an invisible hitbox or turn an entire dungeon unreadable.

Tail and wing geometry follows a documented rig: body-space anchor, local parent chain, animation tick/frame separation, collision-aware displacement, crouch compensation and interpolation reset. Test all nine at idle, sprint, backward strafe, crouch, swim, flight if supported, hit reaction, death, mount/dismount, teleport and dimension transfer. Test 30/60/144 FPS and a low-tick-rate observer. Do not replace a collision-corrected pose with an uncorrected reconstruction later in the frame. Current original Vale wards and the separate recovered `hvbijuu` Matatabi/tail renderer are distinct implementations; neither silently replaces the other.

Sage and curse paths add preparation and risk, not free stat inflation. Balance them against ordinary specialists and support builds. Gated final-stage moves show cost and consequence before activation. Co-op design must not require every player to bring a legendary mode.

## 8. Equipment, crafting and provisions

Keep six physical weapon rack slots with exact stack metadata, ownership serials and server validation. Each weapon family has a role and at least one characteristic action: precision blade, reach weapon, sweeping polearm, defensive staff, committed heavy weapon, claws, thrown tools, bow/launcher, chains and specialist artifacts. Legendary weapons need actual mechanics—resource exchange, sustained pressure, trap setup, defense break or controlled traversal—not only damage multipliers.

Maintain a complete item parity ledger: acquire, equip/use, craft, repair/upgrade, trade/drop, store, die/recover, reconnect, migrate and render. The ledger distinguishes a registered counterpart from a verified behavioral counterpart. Recipe book and mission guidance must expose legitimate acquisition. Preserve or compensate obsolete content through explicit migration receipts rather than deleting registries from a saved world.

Cooking should make village life useful: gather rice/vegetables/protein, process at authored workstations, cook regional dishes, share a team meal and prepare field rations. Machine or workstation state survives unload/restart with exact ingredients and results; full outputs do not void paid inputs. Food benefits have categories and anti-stacking rules. Backpack upgrades preserve actual stacks, reject nested-container duplication and reconcile death/drop behavior with rack storage.

Currency sources are missions, production services, selected drops and cooperative milestones. Sinks are provisions, repair, travel, cosmetic crafting, village projects and bounded respec. Keep power progression available through play. Rewards use deterministic transaction IDs, committed costs and a paid-delivery outbox; crashes or a full inventory produce exactly one payable outcome. Display costs and reward rules before commitment.

## 9. Missions, NPCs and friend-created content

Mission schema: stable ID/version; prerequisites; offered-by role; participants; objective graph; optional branches; timers; fail/retry policy; protected areas; contribution rules; rewards; dialogue/localization; repeatability; idempotency and migration. Objectives emit server-observed events such as escort arrival, evidence secured, guarded ally surviving or an encounter completion receipt. A client notification never grants mission credit by itself.

Party state handles leader departure, member disconnect, late join, role changes, shared objectives, individual rewards and already-completed members. Replay assists receive appropriate credit without duplicating first-clear rewards. Failed escort pathing or an NPC death must yield a recoverable objective rather than a permanently stalled journal. Make objectives readable without opening a debug console.

Residents have authored identities, appearance, homes/workplaces, schedules, service roles, relationships, alert/flee/recover states, dialogue conditions and bounded navigation. At minimum the village needs academy mentor, mission officer, quartermaster, medic, cook, blacksmith, archivist, local civilian contacts and travel official. A schedule that moves an essential mentor must provide a findable destination and fallback interaction.

Friend NPC intake requires world `customnpcs/`, profile `customnpcs/`, relevant config, custom assets, dependency versions, changed NPC list and pre-edit backup when available. Preserve source files privately, build an ID/reference graph, compare new versus changed definitions, remap collisions consistently and keep an explicit mapping report. Clone JSON is not the whole world; placed NPCs and scripted blocks can require region/entity data. Never overwrite an entire live world's NPC folder to merge a few actors.

Two supported outcomes are distinct: retain a compatible optional CustomNPCs profile with original authored definitions, or translate approved behavior/content into the owned Residents runtime. Track unsupported scripted APIs and dependencies; do not claim the translation preserves them until tested. Test dialogue branches, quest transitions, trades, factions, skins, sounds, equipment, script errors, navigation and save/restart. Only reviewed original definitions/scripts and permitted assets enter public Git; private player progress and full saves do not.

## 10. Villages, travel, faction play and exploration

Five villages have distinct arrival silhouettes, street structure, service placement, region materials, ambient sound, food, craft specialties and mission identities. Civilian spaces contain homes, storage, repairs and work traces rather than repetitive empty facades. Give villages at least two public destinations and one quiet route between major services. The player's house/building remains meaningful.

Travel begins by walking and discovering routes. A bound travel network unlocks through exploration or services; it validates destination support, dimension, protection and combat lock before charging. Failure restores the correct reservation and preserves the discovered destination. Rescue travel has a separate bounded policy. Maps reveal discovered places and party waypoints; do not expose enemy players, hidden rooms or undiscovered objectives by default.

Opt-in village conflict uses declared windows, objectives, nonparticipating safe zones, defender recovery and a recorded end condition. No offline village erasure or player-build destruction by default. Exams use scheduled or on-demand brackets, role diversity, spectator bounds, disconnect recovery and rematch rules. Rewards emphasize achievement and identity without invalidating ordinary missions.

## 11. HUD, controls, codex and accessibility

Keep E as Minecraft inventory and preserve standard hotbar/building behavior. Retain current configurable focus/rack actions and F8 Controls until a versioned redesign is tested. New bindings must be conflict-checked and remappable; onboarding demonstrates only a few at a time. Never trigger an ability while entering chat, searching JEI or using a text field. Respect press/release, focus loss and charge cancellation.

HUD shows only contextually useful health/chakra, active technique, recovery, party distress and current mission. Use consistent shapes plus text for status rather than color alone. First/third-person VFX should not cover crosshair, target tells or building placement. Reduce camera shake, flashes, particles and motion separately; offer readable text scale, subtitles, effect contrast and a stable high-contrast HUD.

The original codex replaces fragmented helper systems with technique prerequisites, crafting/resource information, enemy observations, regional notes, training feedback and mission history. Mark unknown information as unknown. Search supports keyboard input and real controller focus if implemented. JEI remains an optional test/integration aid, with the observed empty-list hide bug queued for a tested fix.

## 12. Networking, persistence and performance contracts

Requests are versioned and bounded. Validate message size, finite values, sender ownership, actual selected item, known ability, dimension, world tick/deadline, state version, line of sight and rate limit. Use small server snapshots and sequence numbers for confirmed actions. Never accept client-provided rewards, raw NBT edits, arbitrary script paths or unbounded target lists.

Persistent capabilities and world-saved data need explicit migrations and atomic/replaceable writes where supported. Exercise partial write, stale snapshot, relog during action, crash between debit and delivery, missing optional mod, unavailable texture, removed content and a server restart inside a dungeon. Log useful IDs and state transitions without logging authentication secrets or personal data.

World-editing techniques and dungeon resets keep a bounded journal or use reserved encounter spaces. Never regenerate arbitrary production chunks to reset a fight. Entity/particle/sound budgets are configurable per player, encounter and region. Distant actors degrade animation/effects but retain authoritative logic. Use bounded task queues rather than scanning all worlds every tick. Match a production client's and server's shared JAR bytes; optional client graphics mods must not become dedicated-server hard dependencies.

## 13. Recognizable Naruto repertoire, with actual distinct mechanics

The requested experience should visibly communicate Naruto techniques, clans, village roles and progression. Neutral legacy/internal codenames must not become an excuse to deliver an unrelated fantasy game. Keep stable technical IDs and an explicit content/localization mapping to the requested presentation; independently author implementation and visuals, and keep unresolved publication/provenance questions visible. This table is a proposed gameplay interpretation, not a claim that all entries exist in the supplied pack or currently work.

| Requested technique/family | Concrete original implementation target | Acceptance obligation |
| --- | --- | --- |
| Shadow Clone | Owned, time-bounded decoys/support actors with a deliberate command/attention role and finite resource cost | Correct attribution and expiry; no item/XP duplication, infinite actor count or stale owner reuse |
| Substitution | Reactive, limited reposition with an understandable opening and retained consequences | Forge protection/collision and actual movement determine settlement; no free damage cancellation on a failed move |
| Transformation | Bounded visual disguise with declared interaction/hitbox/information limits | Correct first/third-person rendering and observer identity; no unauthorized player impersonation or changed damage authority |
| Rasengan family | Committed close-range chakra shaping with charge/approach/impact and a meaningful recovery window | Cannot hit through cover; interrupted or invalid approach has a documented cost; visual impact matches actual contact |
| Chidori family | A precise committed lightning approach with readable direction and counterplay | Server-valid path/endpoint; enemy can evade; no homing through walls or unintended ally damage |
| Fireball/Phoenix-style patterns | Different projectile formation and timing rather than a shared recolored bolt | Distinct spacing/commitment; correct protection and no permanent accidental village fires |
| Water Dragon/Shark patterns | Distinct travel, turn/commitment and water-context behavior | Dry/wet cost rules, collision, target eligibility and actor cleanup are explicit |
| Earth Wall/earth armor | Temporary geometry/cover versus personal defensive stance, implemented as different engines | Restore only owned temporary blocks; preserve containers, protection and actual collision |
| Gentle Fist / rotation | Precision close-range pressure and a separate committed defensive action | Angle/range and pressure/defense conditions are server-observed; no permanent ability denial |
| Shadow Possession | Visible bounded tether with a sustainable resource/position contest | Escape/line-break and repeated-control limits; no indefinite freeze or control through unloaded geometry |
| Mind Transfer | An opt-in PvP-compatible information/control encounter with protected player agency | Target restrictions, breakout, owner vulnerability and disconnect recovery; no unconstrained client input takeover |
| Puppet techniques | A controlled actor with distinct modules/attacks and a vulnerable controller | Leash/line constraints, module state, owner cleanup and exact equipment preservation |
| Insect/hound techniques | Distinct owned support ecosystems and coordinated attacks | No unbounded swarm entities; actual companion contribution, owner limits and cleanup |
| Sealing chains/barriers | Placement, anchors, binding and cooperative utility rather than generic potion icons | Ownership, geometry, dispel/counter, expiry and reload behavior are demonstrated |
| Flying-mark/space-time techniques | Authored discovered/placed marks with strict relocation and combat policies | Safe loaded destination, protection, costs, stale marks and movement callback exceptions are settled correctly |
| Genjutsu | Readable illusion/deception with counter skills and bounded sensory presentation | Reduced-effects option, escape conditions, no permanent blindness or fake client-authoritative damage |
| Susanoo-style manifestation | Original large protective/weapon silhouette with staged commitment and camera/terrain fit | Model and hitbox coherence, cost/strain, crouch/teleport/relog and observer interpolation pass |
| Sage/curse/gates forms | Different preparation, risk, support and physical pressure paths | Each changes choices; explicit strain/conflict rules; meaningful normal-player mastery route |
| Beast cloaks/full forms | Nine distinct rigs and kits with partial-to-full progression | Every path has a signature action, weakness, acquisition and tested tail/body/camera behavior |
| Advanced combined natures/eyes | A traceable expansion of the complete reference obligations and requested roster | Each unique effect closes its own behavior/evidence row; a generic label or shared placeholder cannot close it |

The same discipline applies to recognizable weapons, village dress, mission types, ranks and environmental motifs. The owner should be able to point to a feature in-game and understand its intended shinobi role from behavior and presentation, while the repository retains original authored source and an honest rights/provenance record.
