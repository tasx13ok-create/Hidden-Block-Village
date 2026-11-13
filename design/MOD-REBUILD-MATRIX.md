# Complete reference-mod rebuild matrix

All 49 supplied JARs are accounted for. This is a feature replacement/compatibility plan; none of these rows establishes complete reference parity. Whole-file identities and embedded declarations are in [reference-mod-inventory.json](reference-mod-inventory.json).

Core gameplay and utility behavior are to be authored as owned systems. Dependency-only libraries can disappear after their consumers are replaced. Optional external integrations remain explicit; their presence locally does not establish redistribution permission. We are not proposing a gratuitous rewrite of Java, Forge, or every general-purpose library.

For each row, first collect actual behavior obligations. A filename or item label is not a complete mechanic specification. Rows whose identity is unresolved remain investigations rather than invented feature lists.

## 01. !mixinbooter-11.18.jar
- **Observed metadata:** mixinbooter 11.18
- **Disposition / owner:** `dependency_transition` — compatibility helpers.
- **Functional scope:** Mixin bootstrap requirements of retained consumers; remove dependency only after those consumers are gone.
- **Current boundary:** Not an owned replacement. Current optional integrations may bootstrap their own mixins.
- **Acceptance:** Start the approved roster without duplicate injection or unsupported bytecode; identify every remaining consumer.
- **Work package:** `MOD-001`.

## 02. [1.12.2]DamageIndicatorsMod-3.5.1.jar
- **Observed metadata:** DamageIndicatorsMod 3.5.1
- **Disposition / owner:** `original_utility_rebuild` — field-hud.
- **Functional scope:** Target health, status, damage feedback, bounded range and visibility rules.
- **Current boundary:** HUD exists; full reference indicator behavior has not been audited.
- **Acceptance:** Real two-player combat shows correct health/status without hidden-entity disclosure or stale target bars.
- **Work package:** `MOD-002`.

## 03. ahznb weapons plus 1.2.1.jar
- **Observed metadata:** ahznbswordsplus 1.2.1
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders arsenal.
- **Functional scope:** Every reference weapon: acquisition, reach, charged/secondary action, effects, model and persistence.
- **Current boundary:** Original arsenal and rack exist; weapon-by-weapon parity remains open.
- **Acceptance:** Each observed weapon has a distinct tested action, readable presentation and normal-player acquisition.
- **Work package:** `MOD-003`.

## 04. ahznbstweaks-2.0.jar
- **Observed metadata:** ahznbstweaks 1.7
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders and world-compat.
- **Functional scope:** Observe actual balance/progression/behavior patches and specify intended replacements individually.
- **Current boundary:** Filename 2.0 and embedded 1.7 disagree; behavior scope unresolved.
- **Acceptance:** Record each observed tweak and test its interaction with the selected owned systems; do not infer behavior from filename.
- **Work package:** `MOD-004`.

## 05. almirioncore-1.1.jar
- **Observed metadata:** examplemod ${version}
- **Disposition / owner:** `identity_investigation` — compatibility helpers.
- **Functional scope:** Resolve loader identity and actual dependency/behavior obligations before choosing an implementation.
- **Current boundary:** Embedded Example Mod and version placeholder are not a trustworthy identity.
- **Acceptance:** Observe runtime identity and dependent consumers; document a concrete replacement or justified dependency retirement.
- **Work package:** `MOD-005`.

## 06. AnbuBingoBook - 1.6.1.jar
- **Observed metadata:** anbubingobook 1.5.3
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders missions.
- **Functional scope:** Bounty dossiers, mission eligibility, target identity, contribution, payouts and public/private intel.
- **Current boundary:** Mission/journal foundations exist; complete bounty behavior is unverified.
- **Acceptance:** Correct eligible target and contributor receive exactly one payout; invalid targets and duplicate claims are refused.
- **Work package:** `MOD-006`.

## 07. AppleSkin-mc1.12-1.0.14.jar
- **Observed metadata:** appleskin 1.0.14
- **Disposition / owner:** `original_utility_rebuild` — field-hud and culinary.
- **Functional scope:** Readable hunger/saturation and food-preview information integrated into provisions.
- **Current boundary:** Culinary runtime exists; owned saturation/preview presentation is planned.
- **Acceptance:** Compare actual eating result to preview, respect GUI scale and avoid obscuring combat information.
- **Work package:** `MOD-007`.

## 08. Armourers-Workshop-1.12.2-0.51.0.jar
- **Observed metadata:** armourers_workshop 1.12.2-0.51.0
- **Disposition / owner:** `original_gameplay_rebuild` — owned appearance tools.
- **Functional scope:** Cosmetic equipment/wardrobe, original authoring and a documented import path for permitted user-created skins.
- **Current boundary:** Original ward renderer exists; AW editor/import parity is not implemented.
- **Acceptance:** Equip/save/reload cosmetics without altering combat equipment; import only explicitly supported formats and retain source provenance.
- **Work package:** `MOD-008`.

## 09. Baryon Expansion_beta2.jar
- **Observed metadata:** baryonexpansion 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders modes.
- **Functional scope:** Observed high-risk transformation mechanics, strain, activation, duration, counters and visuals.
- **Current boundary:** Original modes are a foundation, not verified Baryon parity.
- **Acceptance:** Costs and consequences persist through relog/death; opponents see readable tells; no free toggle exploit.
- **Work package:** `MOD-009`.

## 10. biju_unleashing-1.7.3.jar
- **Observed metadata:** biju_unleashing 1.7.3
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders wards and encounters.
- **Functional scope:** Nine beast-related progression paths, encounters, contracts, forms and distinct combat expression.
- **Current boundary:** Nine original ward profiles and shared encounters exist; complete beast loop/unique mechanics remain open.
- **Acceptance:** All nine have acquisition, distinct kit, counterplay, ownership recovery and real client animation evidence.
- **Work package:** `MOD-010`.

## 11. BorutoMod Addon byAnimeSupremeTeam v1.0.jar
- **Observed metadata:** borutomodaddononero 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders advanced content.
- **Functional scope:** Observe its specific advanced techniques/entities/items and map each obligation into owned engines.
- **Current boundary:** No complete artifact-specific parity claim.
- **Acceptance:** Each observed feature has an evidence row; no assumed modern-franchise roster is marked implemented.
- **Work package:** `MOD-011`.

## 12. Clumps-3.1.2.jar
- **Observed metadata:** clumps 3.1.2
- **Disposition / owner:** `original_utility_rebuild` — world runtime optimization.
- **Functional scope:** Bounded XP aggregation preserving the actual XP sum, pickup rules and progression effects.
- **Current boundary:** Not yet an owned verified equivalent.
- **Acceptance:** Stress test many XP awards with unchanged totals, party semantics and no duplicate pickup.
- **Work package:** `MOD-012`.

## 13. colorfulhealthbar-0.0.4a.jar
- **Observed metadata:** colorfulhealthbar 0.0.4a
- **Disposition / owner:** `original_utility_rebuild` — field-hud.
- **Functional scope:** Consistent player/target health bars with configurable layout, status shapes and accessibility.
- **Current boundary:** Original HUD exists; reference-specific visual behavior remains unverified.
- **Acceptance:** Readability at multiple aspect ratios and health scales; no duplicate conflicting bars.
- **Work package:** `MOD-013`.

## 14. Controlling-3.0.12.4.jar
- **Observed metadata:** controlling 3.0.12.4
- **Disposition / owner:** `original_utility_rebuild` — field-controls.
- **Functional scope:** Binding search, conflict detection, remap persistence, focus and input safety.
- **Current boundary:** Controls 1.4 simple exists; full binding-search parity remains planned.
- **Acceptance:** Resolve a conflict, save/restart and verify chat/search do not fire abilities.
- **Work package:** `MOD-014`.

## 15. CosmeticArmorReworked-1.12.2-v5a.jar
- **Observed metadata:** cosmeticarmorreworked 1.12.2-v5a
- **Disposition / owner:** `original_gameplay_rebuild` — owned appearance and rack.
- **Functional scope:** Separate cosmetic presentation from real armor stats with exact stack storage and synchronized visibility.
- **Current boundary:** Ward renderer exists; independent cosmetic-slot parity is not established.
- **Acceptance:** Equip/remove/death/reconnect preserves real combat gear, cosmetics and observer rendering without duplication.
- **Work package:** `MOD-015`.

## 16. CustomNPCs_1.12.2-(05Jul20).jar
- **Observed metadata:** customnpcs 1.12
- **Disposition / owner:** `compatibility_import_and_rebuild` — vale-residents.
- **Functional scope:** Original NPC behavior/dialogue/services/schedules plus careful import or optional retention of friend-authored definitions.
- **Current boundary:** Residents has three service roles; it is not a complete CNPC runtime.
- **Acceptance:** ID/reference remap, scripts, quests, trade, faction, appearance, pathing and copied-world restart all pass for imported content.
- **Work package:** `MOD-016`.

## 17. Essential_1-5-1_forge_1-12-2.jar
- **Observed metadata:** No usable mcmod.info declaration; confirm through loader/runtime evidence.
- **Disposition / owner:** `optional_external` — client integration.
- **Functional scope:** Keep optional local compatibility; implement owned server/party join UX without requiring a proprietary social/account service.
- **Current boundary:** Pinned local JAR works in inspection; no redistribution permission inferred.
- **Acceptance:** Owned gameplay works without Essential; optional enabled profile still starts and connects.
- **Work package:** `MOD-017`.

## 18. Fluidlogged-API-v3.3.3-mc1.12.2.jar
- **Observed metadata:** fluidlogged_api 3.3.3
- **Disposition / owner:** `compatibility_import_and_rebuild` — world-compat and water geometry.
- **Functional scope:** Legacy fluid/block data compatibility and required water-bearing architecture behavior.
- **Current boundary:** Mist uses real water; arbitrary legacy fluidlogged block migration is unverified.
- **Acceptance:** Preserve source water/block identity in a test copy; no flooding, silent deletion or invented universal conversion.
- **Work package:** `MOD-018`.

## 19. FTBLib-5.4.7.2.jar
- **Observed metadata:** ftblib 5.4.7.2
- **Disposition / owner:** `dependency_transition` — vale-orders party services.
- **Functional scope:** Map actual team/library consumers to owned party/state APIs; remove only after their obligations are covered.
- **Current boundary:** Current party/reward foundations exist, not complete FTBLib parity.
- **Acceptance:** Team membership and consumers survive restart/rejoin; no abandoned library dependency remains.
- **Work package:** `MOD-019`.

## 20. FTBQuests-1202.9.0.15.jar
- **Observed metadata:** ftbquests 1202.9.0.15
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders missions and residents.
- **Functional scope:** Versioned objective graphs, chapters, party credit, rewards, repeat rules, content authoring and safe import.
- **Current boundary:** Current missions are partial foundations; full quest editor/import parity is planned.
- **Acceptance:** Normal-player chain and co-op replay work through restart; each payout is idempotent; unsupported imported objectives are explicit.
- **Work package:** `MOD-020`.

## 21. geckolib-forge-1.12.2-3.0.31.jar
- **Observed metadata:** geckolib3 ${version}
- **Disposition / owner:** `dependency_transition` — owned animation renderer.
- **Functional scope:** Document retained animation consumers and replace necessary behavior with original rigs/controllers or reviewed dependencies.
- **Current boundary:** Original cuboid ward engine exists; it is not a general GeckoLib replacement.
- **Acceptance:** Every owned actor has a tested animation contract; no transitive runtime dependency is removed prematurely.
- **Work package:** `MOD-021`.

## 22. HiddenVillage-BijuuArmor-0.16.2-rc1.jar
- **Observed metadata:** hvbijuu 0.16.2-rc1
- **Disposition / owner:** `original_gameplay_rebuild` — owned ward renderer and legacy repair.
- **Functional scope:** Audit all nine armor silhouettes, tail/body transforms, native attachments, motion, clipping and mode hooks.
- **Current boundary:** Original Vale wards and separate recovered hvbijuu candidate are distinct; later private legacy candidate is not installed.
- **Acceptance:** Test all forms/movement states at several frame rates and perspectives; never substitute one renderer while claiming to fix the other.
- **Work package:** `MOD-022`.

## 23. ItemFilters-1.0.4.2.jar
- **Observed metadata:** itemfilters 1.0.4.2
- **Disposition / owner:** `original_utility_rebuild` — crafting and mission matching.
- **Functional scope:** Bounded item/tag/metadata matching for recipes, objectives and service filters.
- **Current boundary:** Existing owned crafting/state checks cover some use cases; complete filter language is not established.
- **Acceptance:** Positive/negative metadata/NBT cases, unknown items and changed tags behave deterministically without arbitrary expressions.
- **Work package:** `MOD-023`.

## 24. jei_1.12.2-4.22.0.1034.jar
- **Observed metadata:** jei 4.22.0.1034
- **Disposition / owner:** `original_utility_rebuild` — owned codex and optional integration.
- **Functional scope:** Search, recipe/use discovery and item visibility, with retained optional JEI support during development.
- **Current boundary:** JEI is in inspection; empty-list hide bridge warning is an observed P0 fix.
- **Acceptance:** Empty/nonempty hide bridge works; owned recipe/codex search gives accurate acquisition and input handling.
- **Work package:** `MOD-024`.

## 25. JustEnoughResources-1.12.2-0.9.2.60.jar
- **Observed metadata:** jeresources 0.9.2
- **Disposition / owner:** `original_utility_rebuild` — owned codex.
- **Functional scope:** Explain observed resource locations, mob drops and authored loot ranges without exposing hidden content.
- **Current boundary:** Planned; do not invent observed drop tables.
- **Acceptance:** Displayed sources match actual configured loot/world generation and discovery eligibility.
- **Work package:** `MOD-025`.

## 26. JutsuAddon v1.0.jar
- **Observed metadata:** jutsuaddon ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders techniques.
- **Functional scope:** Observe each additional technique, its acquisition, timing, geometry, costs and visual identity.
- **Current boundary:** Current scroll engines cover several families; exact addon parity remains open.
- **Acceptance:** Each mapped technique completes the universal behavior record and native/two-client scenarios.
- **Work package:** `MOD-026`.

## 27. kabutoaddon 5.5.8-beta.jar
- **Observed metadata:** kabutoaddon 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders and encounters.
- **Functional scope:** Audit its actual techniques, actors, upgrades and progression rather than inferring them from the title.
- **Current boundary:** Filename 5.5.8 and generic embedded 1.0.0 cannot establish mechanics coverage.
- **Acceptance:** Every observed actor/technique gets acquisition, unique mechanics, persistence and presentation proof.
- **Work package:** `MOD-027`.

## 28. Naruto-Dungeon-Pictures-1.12.2-1.0.0.jar
- **Observed metadata:** hvdungeonpictures 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — village-standards.
- **Functional scope:** Five village picture items, placement/support, dimensions, variants, saved entity identity and original art.
- **Current boundary:** Standards 0.3.1 has verified historical binary contract; actual production-world migration remains open.
- **Acceptance:** Existing pictures load in a copied world with correct village, support and recovery; replacement art is provenance-reviewed.
- **Work package:** `MOD-028`.

## 29. NarutoMod-1.12.2-0.3.1.beta.jar
- **Observed metadata:** narutomod 0.3.1-beta
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders, encounters and systems.
- **Functional scope:** Full shinobi base: natures, clans, eyes, combat, movement, modes, actors, items, blocks and progression.
- **Current boundary:** 556 current items and partial mechanics do not establish base-mod parity.
- **Acceptance:** Complete per-feature ledger and normal-player campaign with unique mechanics, multiplayer and visual evidence.
- **Work package:** `MOD-029`.

## 30. NarutoMod-Addon_byAnimeSupremeTeam-v1.0.jar
- **Observed metadata:** narutomodaddononero 1.0.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders content.
- **Functional scope:** Audit actual addon-specific equipment/techniques/entities and integrate distinct obligations.
- **Current boundary:** Some counterpart/encounter foundations exist; source-artifact mapping needs observation.
- **Acceptance:** No merged generic placeholder can close multiple unique obligations; verify actual configured behaviors.
- **Work package:** `MOD-030`.

## 31. NarutoModAddon-0.3.1- V1.3.jar
- **Observed metadata:** narutomodaddon 1.1
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders progression.
- **Functional scope:** Observe chakra/stat/progression additions and their interactions with the base mod.
- **Current boundary:** Embedded 1.1 differs from filename V1.3.
- **Acceptance:** Resource scaling, purchase/unlock costs, reset behavior and save/relog are specified and tested.
- **Work package:** `MOD-031`.

## 32. nmee-0.2.2-beta.jar
- **Observed metadata:** nmee 0.2.1-beta
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders and compatibility.
- **Functional scope:** Audit enhanced-extension fixes/features and map each concrete behavior.
- **Current boundary:** Embedded 0.2.1-beta differs from filename 0.2.2-beta; no exhaustive obligation list yet.
- **Acceptance:** Reference observations distinguish a bug fix from a new ability; each accepted replacement has a regression case.
- **Work package:** `MOD-032`.

## 33. OptiFine_1.12.2_HD_U_G5.jar
- **Observed metadata:** No usable mcmod.info declaration; confirm through loader/runtime evidence.
- **Disposition / owner:** `optional_external` — client rendering integration.
- **Functional scope:** Retain optional local shader/rendering compatibility; owned game readability, effects and settings work without it.
- **Current boundary:** Exact local pinned JAR initialized and connected in inspection.
- **Acceptance:** Actual client tests vanilla and OptiFine separately; no private JAR is silently bundled into public distribution.
- **Work package:** `MOD-033`.

## 34. Patchouli-1.0-23.6.jar
- **Observed metadata:** patchouli 1.0-23.6
- **Disposition / owner:** `original_utility_rebuild` — owned codex.
- **Functional scope:** In-world readable journal/codex, discovery gates, localization, navigation and content authoring.
- **Current boundary:** Current technique codex/mission journal are foundations, not a full book framework.
- **Acceptance:** A novice can find acquisition, recipe and mission help without OP/debug commands; localization and navigation persist.
- **Work package:** `MOD-034`.

## 35. RinneganAddon v1.3.jar
- **Observed metadata:** rinneganaddon ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders advanced eyes.
- **Functional scope:** Observe path-specific abilities, sustained summons/forces, unlocks, drains, geometry and counters.
- **Current boundary:** Advanced eye-path parity is not established by current generic effects.
- **Acceptance:** Each path has distinct tested mechanics, sustained cost, loaded-chunk/protection limits and observer visuals.
- **Work package:** `MOD-035`.

## 36. SealsAddon v1.0.jar
- **Observed metadata:** sealsaddon ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders sealing.
- **Functional scope:** Marks, placed seals, bindings, travel/storage/ritual semantics actually observed in reference.
- **Current boundary:** Waymark and some scroll effects are partial foundations.
- **Acceptance:** Ownership, placement, multiple targets, removal, expiry, protection and restart behave deterministically.
- **Work package:** `MOD-036`.

## 37. SharinganAddon v1.0.jar
- **Observed metadata:** sharinganaddon ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders eye progression.
- **Functional scope:** Observed eye stages and abilities, copied techniques, counters, strain and evolution paths.
- **Current boundary:** No full eye-stage/mechanic parity claim.
- **Acceptance:** Unlocks, eligible copy whitelist, information limits, form conflicts and two-client readability pass.
- **Work package:** `MOD-037`.

## 38. shinobiculinary-1.2.0.jar
- **Observed metadata:** shinobiculinary 1.1.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders culinary.
- **Functional scope:** Regional ingredients, farming, processing, cooking, stations, recipes, food benefits and trade.
- **Current boundary:** Owned culinary runtime/items exist; full recipe and machine-behavior audit remains open.
- **Acceptance:** Each recipe/station has real inputs/outputs, occupied/full cases, unload/restart and valid normal-player acquisition.
- **Work package:** `MOD-038`.

## 39. SpringAddon7.0.jar
- **Observed metadata:** ahznbcursemarkaddon 5.0
- **Disposition / owner:** `original_gameplay_rebuild` — vale-orders curse and advanced content.
- **Functional scope:** Audit observed curse-mark and other registered content with activation/risk/progression/visual contracts.
- **Current boundary:** Embedded ahznbcursemarkaddon 5.0 differs from SpringAddon7.0 filename.
- **Acceptance:** Distinct stages/costs/counters persist and render correctly; no inferred feature is marked complete.
- **Work package:** `MOD-039`.

## 40. stg-1.12.2-1.2.3.jar
- **Observed metadata:** stg 1.12.2-1.2.3
- **Disposition / owner:** `original_utility_rebuild` — vale-orders combat trace.
- **Functional scope:** Allow intended melee through replaceable grass while preserving real obstacle/target order.
- **Current boundary:** Existing owned attack geometry needs a dedicated foliage parity scenario.
- **Acceptance:** Grass does not block a permitted target; walls, allies and nearer collision still prevent an invalid hit.
- **Work package:** `MOD-040`.

## 41. supermartijn642configlib-1.1.8-forge-mc1.12.jar
- **Observed metadata:** supermartijn642configlib 1.1.8
- **Disposition / owner:** `dependency_transition` — field-controls and configuration.
- **Functional scope:** Identify actual consumers and replace their required settings workflows with owned validated schemas.
- **Current boundary:** Not an owned library replacement; removal depends on consumer audit.
- **Acceptance:** Invalid values are bounded; saved settings reload; no active consumer references a removed library.
- **Work package:** `MOD-041`.

## 42. TravelersBackpack-1.12.2-1.0.35.jar
- **Observed metadata:** travelersbackpack 1.0.35
- **Disposition / owner:** `original_gameplay_rebuild` — owned inventory equipment.
- **Functional scope:** Field pack storage, upgrades, carried/displayed state and any observed fluid/tool behavior.
- **Current boundary:** Six-slot rack is not backpack parity.
- **Acceptance:** No nested-container duplication; full inventory/death/drop/reconnect preserves every exact stack and upgrade.
- **Work package:** `MOD-042`.

## 43. VariedCommodities_1.12.2-(31Mar23).jar
- **Observed metadata:** variedcommodities ${version}
- **Disposition / owner:** `original_gameplay_rebuild` — owned world decoration and compatibility.
- **Functional scope:** Original furnishings/props and required gameplay blocks, plus conservative legacy saved-data support.
- **Current boundary:** Canonical combat-prop quarantine exists; complete decoration/block migration does not.
- **Acceptance:** Usable original furnishings and accurate collision; unknown legacy blocks never silently become air in a preserved world.
- **Work package:** `MOD-043`.

## 44. warpmod-1.12.2-1.0.1.jar
- **Observed metadata:** warp_mod 1.0.0
- **Disposition / owner:** `original_utility_rebuild` — owned travel service.
- **Functional scope:** Permissioned destinations, named travel, cost, cooldown, combat lock and failure recovery.
- **Current boundary:** Waymark is a separate personal technique, not complete named-warp parity.
- **Acceptance:** Private/unknown/unsafe destinations fail without charging; successful travel charges once and respects protection.
- **Work package:** `MOD-044`.

## 45. Waystones_1.12.2-4.1.0.jar
- **Observed metadata:** waystones 4.1.0
- **Disposition / owner:** `original_gameplay_rebuild` — owned travel and discoveries.
- **Functional scope:** Discoverable physical travel network, bound destinations, ownership and regional progression.
- **Current boundary:** Planned; current inspection teleport book is not a finished travel network.
- **Acceptance:** Discovery, destination destruction, rename, transfer, costs, cooldown and restart all preserve correct state.
- **Work package:** `MOD-045`.

## 46. worldedit-forge-mc1.12.2-6.1.10-dist.jar
- **Observed metadata:** worldedit 6.1.10-SNAPSHOT;e6b38e8
- **Disposition / owner:** `authoring_tool_and_runtime_rebuild` — owned template/expedition tools.
- **Functional scope:** Template import/export, anchors, safe placement, validation, bounded restoration and required authoring workflow.
- **Current boundary:** Offline schematic generators and checks exist outside this source checkout; reusable runtime template service is planned.
- **Acceptance:** Round-trip native metadata/tile entities, refuse unsafe targets, preserve rollback and reset only owned encounter cells.
- **Work package:** `MOD-046`.

## 47. xaerolib-forge-1.12.2-1.7.3.jar
- **Observed metadata:** xaerolib 1.7.3
- **Disposition / owner:** `dependency_transition` — owned map and configuration.
- **Functional scope:** Map dependency obligations follow the owned map/discovery UI; retain locally only for explicit compatibility tests.
- **Current boundary:** No owned replacement or redistribution inference.
- **Acceptance:** Owned discovery/map features function without the library; any retained map consumer has matching required dependencies.
- **Work package:** `MOD-047`.

## 48. xaerominimap-forge-1.12.2-26.6.0.jar
- **Observed metadata:** xaerominimap 26.6.0
- **Disposition / owner:** `original_utility_rebuild` — owned exploration HUD.
- **Functional scope:** Readable local navigation, discovered landmarks and consensual party waypoints.
- **Current boundary:** Planned; avoid inheriting radar/hidden-entity disclosures by default.
- **Acceptance:** Map orientation/scale/visibility and saved waypoints work; inaccessible rooms/enemies remain undisclosed.
- **Work package:** `MOD-048`.

## 49. xaeroworldmap-forge-1.12.2-1.47.0.jar
- **Observed metadata:** xaeroworldmap 1.47.0
- **Disposition / owner:** `original_utility_rebuild` — owned atlas.
- **Functional scope:** Persistent discovered-world atlas, dimension-aware routes, labels and party sharing.
- **Current boundary:** Planned; no existing full atlas parity.
- **Acceptance:** Discover/save/reload/dimension switch and bounded map storage work without scanning unloaded world terrain.
- **Work package:** `MOD-049`.
