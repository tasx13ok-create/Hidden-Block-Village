# Five dungeon campaigns: world and encounter atlas

> **Owner direction, 14 November 2026:** The existing five v17 sites are elemental NPC-and-loot dungeons. The five new 500×500×250 coliseums are separate boss-fight sites. The boss concepts in the older sections below are historical design ideas, not instructions to place those bosses in the elemental sites. See [elemental sites](../docs/dungeons/elemental-sites-current.md) for the recovered builds, confirmed nature assignment and implementation status. The owner's friend will handle NPC drop choices.

## Production baseline and shared contract

This is the next design specification, not a new exported dungeon release. Available v9 source has Earth/Leaf, Cloud and Sand at 192Ã—192; Mist at 200Ã—200; Fire/Nether at 160Ã—160. All five newer v17 schematics have now been recovered and metadata-audited: Sakura/Cloud/Sand 288×288, Mist 200×200 and Nether 256×256. See `release/evidence/v17-intake-20261113.json` and the current handoff. Their geometry/routes/native runtime still require comparison and validation before replacement. Preserve the original arrays, world, anchors and exports, and author each revision in a separate output/save.

Keep Mist exactly 200Ã—200 at the designed settlement footprint. Earth, Cloud and Sand retain fractured torn-ground undersides. Mist sits in real surrounding water and Fire/Nether retains its own grounded/base treatment. Proposed Fire expansion is a 320Ã—320 compound with a central citadel roughly 192Ã—192; stage it by district so scale is earned through usable content and measured rendering cost.

Every dungeon needs: readable arrival; context from a resident or environmental clue; a safe preparation area; a primary route; at least one alternative route; a mid-route landmark; an optional challenge; an understandable puzzle; an encounter sequence that uses the space; a distinct boss; a reward scene; a short readable exit; and a clean reset/recovery plan. Proposed normal runs are 25â€“40 minutes, with 10â€“15 minute shortcuts after mastery. Times are playtest targets. No fifteen-minute walk back to a failed boss.

Campaign difficulty is progression-led. Expedition tiers are optional variants, not the replacement for authored normal difficulty. Support 1â€“4 players first, with scale data separated from geometry. Consider 5â€“6 only after navigation, target visibility, healing and effects have been stress-tested. Scaling adjusts patterns/add counts and selected defenses within bounds; it must not multiply health indefinitely. Solo players need puzzle alternatives and support options.

Each site has an immutable authored baseline plus a runtime state graph. An expedition records run ID, template version, seed, allowed participants, checkpoints, objective state, placed temporary blocks, actor ownership, boss state, reward receipts, disconnect deadline and reset status. Private runs occupy validated reserved cells with buffers or a proven isolated-world design; do not assume the shared v9 inspection world already provides instancing. Never paste/reset over player homes or unrelated expeditions.

## Earth / Leaf: The Rootbound Archive

**Identity:** a sacred sakura complex built around an ancient archive and exposed roots, torn from layered earth. The player feels the tension between cultivated serenity above and geological violence below. Use the existing broader sakura as an authored tree, with clear trunk roots and a branching hierarchy rather than a pink sphere.

**Arrival composition:** the first approach frames the tree behind a modest gate, then reveals the deeper fractured base from a side overlook. Keep the main trunk out of the exact central sightline until the second court. A river-stone path, moss edge and supported veranda connect inhabited rooms; the route is readable without luminous arrows.

**Room and route graph:** outer sanctuary â†’ keeper's veranda/preparation â†’ split garden routes â†’ archive threshold â†’ root chamber/midboss â†’ three reading courts â†’ fractured descent â†’ heartwood arena â†’ bloom balcony/reward â†’ return lift or stair. The alternate garden route trades a combat encounter for an observation puzzle and rejoins before the root chamber. A lower side gallery rewards controlled traversal without becoming mandatory for movement-impaired players.

**Spatial brief:** arrival court about 24Ã—28; readable secondary streets/verandas at least three clear blocks wide; ordinary doorways with actual player collision clearance; archive hall about 30Ã—22 with two usable floors; root chamber about 34Ã—30 with four broad lanes; boss arena about 44Ã—44, leaving the largest allowed player form and camera a tested envelope. Dimensions are design starting points, not unreviewed array edits.

**Architecture/material:** dark timber framing, warm plaster, gray-green tile and light stone foundation. Distinct main/secondary roof pitch, curved eaves made from native 1.12.2 stairs/slabs and deliberate corners. Brackets carry visible loads. Roofs connect to walls, and walls meet foundations. Archive rooms contain categorized shelves, scroll workstations, paper storage, ventilation and inhabited reading nooks. Do not cover every surface in trapdoors.

**Terrain:** three unequal major rock plates with different depths, torn joints, exposed soil/root bands and smaller subordinate fragments. Design load-bearing relationships around the tree. Root tips follow cracks and terminate deliberately. No uniform cones, identical stalactites or unconnected floating rubble. Maintain explicit walking routes around fractures.

**Puzzle:** restore the archive's seasonal records by interpreting garden/room clues and directing three seed marks. The order is communicated visually and in accessible text; every clue can be revisited. Wrong choices activate a short resettable defense, not permanent lockout. Co-op can divide observations; solo can perform the same sequence.

**Encounter language:** root-bound guards use braced frontal defense; archivist echoes interrupt sustained casts; seed carriers punish standing in the same lane. Fire is useful against a marked temporary root barrier but must not burn the authored tree. Earth cover protects against a sweep but reduces escape space; wind can move a seed objective only when explicitly eligible.

**Boss â€” Heartwood Warden:** phase one teaches a slow root fan with three safe lanes; phase two changes selected cover and creates a visible channeling target; phase three combines a bark break window with a moving safe sector. Every danger has a clear tell, short anticipation, actual active volume and recovery. Damaging the correct temporary root interrupts an attack; no forced reliance on one rare element. Rewards improve a chosen control/support specialty and unlock an archive crafting/knowledge service.

**Mood:** warm ambient daylight above, cool green pockets below, layered leaf/wood/water sound, sparse paper and bell cues. Night has supported lantern rhythm and room-local pools of light; the sakura silhouette stays legible. Falling petals are locally capped particles, not a permanent world-wide emitter.

## Cloud: The Thunderstep Monastery

**Identity:** high-altitude monastery and signal station across unequal fractured terraces. Emphasize light stone, deep blue roofs, open sky, distant thunder and vertical rhythm. Its distinctive experience is controlled exposure and line-of-sight management, not endless parkour over unavoidable death.

**Route graph:** sheltered arrival terrace â†’ bell court/training â†’ two bridge choices â†’ wind archive â†’ signal platform/midboss â†’ three tower interiors â†’ storm observatory â†’ thunder arena â†’ leeward rest court/exit. One bridge is direct and exposed; the other uses a covered interior and a puzzle. Each tower has a reason to enter and an exit that is not the same dead-end ladder.

**Geometry:** use a clear dominant tower with two subordinate masses. Stagger floor elevations with supported stairs and visible landings. Primary bridges provide at least three clear blocks and intermittent shelter; challenge ledges are optional. Place catch terraces/rescue volumes under intended jumps so a single latency spike does not erase a run. Sky bridges connect at believable structural bearings.

**Materials and detailing:** pale stone piers, blue-gray tiles, indigo accents, restrained brass/wood signal apparatus and cloud-weathered joints. Put water runoff, repair patches and hanging fabric where wind/support would make sense. Reuse shapes at different scales, not the same repeated tower module. Interiors include map tables, bell ropes, signal storage and occupied rest quarters.

**Puzzle:** align three grounded signal stations according to intervals heard/seen in the bell court. Sound clues have visual/subtitle equivalents. Players can inspect alignment from safe positions; combat does not reset a solved puzzle arbitrarily. A short alternate maintenance route can be unlocked by a prior mission.

**Encounters:** ranged sentinels lock a visible firing lane before release; dueling monks punish predictable rushing; a weather keeper alternates safe/crosswind zones. Knockback is bounded near exposed edges. Party healers have protected short windows and clear sight to allies.

**Boss â€” Storm Bell Abbot:** first teaches two sweeping arcs; second introduces grounded safe pads and a breakable channel; final rotates the arena's danger lane while preserving a stable refuge. Wind does not secretly remove all player control. Successful dodges and interruption create real punish windows. Reward a movement/precision technique, signal-network discovery and a meaningful traversal mastery route.

**Lighting and atmosphere:** use broad sky brightness plus warm sheltered rooms, not global fullbright. Fog is an optional presentation layer; paths, edges and attack tells remain visible without shaders and under reduced view distance. Thunder/screen effects are opt-out individually. Fracture strata under terraces follow the same torn-earth language as Earth/Sand without copying their shapes.

## Sand: The Windcarved Observatory

**Identity:** a sun-weathered scholarly/trading compound with turquoise/lapis wall bays, supported lamps, protected courts, carved stone reveals, wind channels and a hidden water system. The visual language is deliberate shinobi-fantasy architecture; the plan does not claim to reconstruct a particular historical culture.

**Route graph:** shaded caravan gate â†’ market/preparation court â†’ wind hall â†’ two courtyard wings â†’ buried cistern/midboss â†’ observatory ascent â†’ open-sky instrument court â†’ dune arena â†’ sheltered reward garden/exit. The underground section contrasts heat, texture and sound with the bright upper court.

**Material/structure:** a primary sandstone family with deliberate strata, recessed dark joints and occasional repaired masonry. Tile bands frame thresholds, not every block edge. Turquoise/lapis focus surfaces identify scholarly/service spaces. Domes/pointed bays need supported transitions to square rooms; roofs have drainage and access. Make stalls, sleeping quarters, storage, kitchens and cistern maintenance genuinely enterable.

**Terrain:** unequal fractured desert rock plates with exposed compacted sand, stone bands and scarps. Sand at edges has an explicit supporting layer in 1.12.2; it must not collapse on first update. Add small sheltered oases and wind-shaped vegetation where water/access justify them. Avoid a smooth sandstone bowl beneath the whole island.

**Puzzle:** route water through a bounded, resettable cistern network while using wind indicators to identify the observatory's intended calibration. The puzzle does not require destructive block mining or uncontrolled fluid updates. Co-op players can communicate indicator positions, but solo has a designed loop. Mark completed stages with physical changes and journal text.

**Encounters:** shielded caravaneers, bounded puppet lanes and sand marks that punish stationary casting. A support actor can be interrupted or isolated. Heat is a readable pressure mechanic with recovery shelters, not a hidden timer that punishes exploration. Magnetic/projectile interactions use the explicit eligible-material table.

**Boss â€” Glasswind Custodian:** phase one throws visible patterned volleys; phase two uses pillars as temporary lanes/shelter; phase three exposes a weak core after a commitment attack. Provide a weapon-based counter as well as nature interactions. Rewards improve sealing/projectile specialization, unlock a regional craft and reveal a caravan route.

**Atmosphere:** long shadows, sunlit amber stone, small blue/cool shaded interiors, restrained dust and cloth/rattle sound. Dust cannot hide ground warnings. Illuminate nighttime stairs and landings with lamps that actually cast vanilla block light. Show a second visual identity at dusk rather than applying the same orange bloom everywhere.

## Mist: The Tidal Covenant

**Identity:** a complete 200Ã—200 waterfront settlement in an ocean, supported by submerged piles, seabed foundations and a connected tidal sanctuary. It is not a floating rock island. Use the v9 1536-block ocean context as a local inspection baseline; the larger production coastline must be separately recovered/designed.

**Route graph:** moored arrival/fishing pier â†’ covered market walk â†’ boat workshop/preparation â†’ branching veranda network â†’ tide archive â†’ lower water gate/midboss â†’ three-tier observatory â†’ rear garden/quiet clue â†’ ritual hall â†’ tide-basin arena â†’ lantern quay/reward/exit. The settlement has ordinary homes and maintenance spaces as well as combat zones.

**Vertical layers:** submerged seabed/piles; working boat and dock level; dry raised circulation; furnished primary rooms; usable upper balconies/galleries; roof/observatory silhouettes. Every upper floor has a real internal route and purpose. A broad gallery cannot be supported by arbitrary posts placed in its boat channel. Main joists connect to perimeter bearings, and water approaches stay navigable.

**Architecture:** connected curved tile roofs, deep eaves, timber posts with visible brackets, shutters, asymmetric balconies, boat storage and drainage. Closed walls meet floor/roof; roof intersections have a designed valley/ridge; corner gardens connect to accessible veranda edges. Include a complete moored cedar hull with coherent waterline, gangway, rope/fence attachment and clearance from pilings. Furnish the upper rooms as rooms, not decorative empty shells.

**Water contract:** no fake water plane in place of real source blocks. Verify fluid containment, shore continuation, dry interiors, seabed contact, boat passage and collision. The v9 schematic has local top water Y=16 and origin anchor Y=21; placing into top source blocks at world Y=63 uses origin Y=68. Preserve those values only for the existing v9 template; every new export gets measured metadata and its own paste instructions. Include air deliberately when clearing occupied room volumes, but do not erase surrounding production terrain blindly.

**Puzzle:** read tidal markers, open a controlled sequence of floodgates and recover a covenant seal through a split dry/water route. It can be solved without a water-nature character; water specialists gain a useful alternative, not exclusive access. Gate changes are modeled by bounded authored states rather than simulating an unbounded ocean flood. A failed attempt returns the puzzle to an understandable checkpoint.

**Encounters:** mist silhouettes telegraph from sound plus visible lantern/weapon cues; boat-house defenders use short, readable flanks; a seal keeper creates temporary safe/unsafe water sectors. No essential fight takes place in a one-block doorway. Include dry regrouping platforms and clear drowning recovery routes. Fog never removes all target information.

**Boss â€” Covenant Tidekeeper:** first teaches a circular wake attack and safe islands; second adds paired seal nodes that can be handled sequentially solo or simultaneously in co-op; final alternates water and raised dry lanes with a large recoverable commitment. Clarify status and dispel rules; do not turn the screen into opaque blue particles. Reward a water/control mastery, harbor service, original mantle cosmetic and an optional rescue-story continuation.

**Atmosphere:** cool exterior air, warm inhabited rooms, pale dawn, restrained rain, reflected lantern lines and local water/wood/bell sound. Shader reflections are optional. Establish readability with actual block light and material contrast before BSL/Solas tuning. Capture day, wet dusk, night, underwater pile detail, room interiors and roofline views.

## Fire / Nether: The Ember Regent's Citadel

**Identity:** the largest campaign site: a 320Ã—320 proposed fortress district with an imposing central citadel, mask-like guardian gate, monumental approach, occupied lower service courts and a vertical sanctum. Its scale comes from nested spaces and useful districts, not a huge empty cube. Preserve the requested exclusion from torn-floating-ground redesign.

**District and route graph:** ash approach/preview overlook â†’ guardian gate â†’ forge court/preparation â†’ split barracks and archive districts â†’ furnace machinery/midboss â†’ great stair â†’ high galleries â†’ seal sanctum â†’ regent arena â†’ cooled vault/reward â†’ sheltered lift/return. The central gate has an open, intentional approach and a walkable entrance at a believable scale. Side towers connect through usable interiors.

**Materials:** charcoal/basalt-like stone families available in 1.12.2, dark Nether brick, restrained red accents, controlled warm metal suggestions and ash-colored repairs. Use native blocks or owned registered materials; do not specify modern Minecraft blocks unavailable to the target version. Lava is contained behind traversable safety margins and actually checked for spread/updates. Provide ventilation and believable support for furnaces and chimneys.

**Vertical plan:** lower maintenance and cistern-like cooling routes; military/service floor; formal halls; upper galleries; a high sanctum with a controlled reveal. Large halls have secondary doorways, supports, galleries and inhabited edge spaces. Ceilings use structure and ordered light groups, not random glowstone dots. Scaled guardian statuary has original geometry and a strong silhouette; avoid copying another server's sculpture.

**Puzzle:** balance three forge/seal stations to expose a safe route through the sanctum. Each station has a tactical challenge and a physical, readable state. A late puzzle failure should reopen a short retry route. Required items cannot be permanently lost in lava; their objective state can be recovered through the run record.

**Encounters:** armored guards with frontal tells; a furnace specialist who commits to an area blast; seal breakers who can be interrupted; upper-gallery archers with clear counter-paths. Use actual routes and shields instead of projectile saturation. A dramatic gate defense can occur in a bounded approach cell without editing the wider Nether.

**Boss â€” Ember Regent:** phase one teaches weapon sweeps and furnace lanes; phase two raises the importance of seal stations and safe galleries; final changes commitment patterns while leaving two distinct counter approaches. Arena heat and effects have a reduced setting. An advanced mode can alter boss movement/decision timing while respecting a solo route. Rewards include a transformation trial prerequisite, an original equipment variant and an authored epilogue, not just a crate roll.

**Dimension acceptance:** prior inspection arenas were in the Overworld. Actual Nether fog, lighting, sky, sound, travel rules, bed restrictions, route entry/exit and death recovery require explicit live Nether tests. Do not mark this campaign's atmosphere verified from an offline Blender render or Overworld screenshot.

## Cross-dungeon encounters, rewards and replay

Every arena has a reusable encounter sheet: participants and power band; safe entry; spawn anchors with validated collision; tells and attack volumes; cover; alternate player strategies; ally support opportunities; phase triggers; timeout/soft-enrage; boss leashing; disconnect policy; failure reset; rewards and exit. Bind all actors to the run ID and clean up only those actors. Friendly NPCs and unrelated players must not become boss targets.

Normal, veteran and mastery tiers use explicit configuration. A mastery route should test understanding, coordination or optional constraints rather than simply demand better loot. Rewards offer choice: technique lesson/mastery, craft material, village progress and original cosmetics. Deliver unique rewards once per eligible receipt; repeats provide useful bounded materials/currency. A full inventory uses the current paid-delivery queue. A player relogging during a reward scene cannot reroll or duplicate it.

Record contributions for healing, protection, objectives and control as well as damage. Someone who disconnected before the run must not claim a later first-clear; someone who contributed and briefly reconnects follows the documented grace rule. Restore a safe checkpoint and objective state after a restart, or explicitly refund/close an unrecoverable run. Keep the original authored template immutable.

## Block placement, lighting and visual acceptance checklist

1. **Baseline identity:** hash inputs; identify whether v9, recovered v17 or a new branch is being edited; preserve anchor/dimensions/palette metadata.
2. **Massing:** approve top-down route and three silhouette views before detailed furnishing. Every major mass has a purpose, entry and support.
3. **Connectivity:** check stairs/slabs orientation, roof valleys, bracket joins, rail corners, shutters, doors, bed orientation, chest access, support and actual collision. Use native 1.12.2 metadata/tile entities; no renderer-only repairs.
4. **Terrain:** inspect underside, strata, roots/piles/foundations and water continuity. Remove disconnected accidental fragments and structurally meaningless supports.
5. **Traversal:** run human-size and maximum supported-form collision envelopes from entry to exit and alternate routes. Test jumping separately from ordinary walking; provide a usable accessibility route. Validate all teleport/checkpoint landings.
6. **Lighting:** measure actual vanilla block light on safe paths (design target at least 8), identify deliberately dark encounter zones, suppress incidental spawns through explicit instance rules where needed, and anchor every visible fixture. Check stair edges and water reflections without shaders.
7. **Native persistence:** schematic/save round-trip; all doors/containers/signs/books/other tile entities; fluid updates; gravity blocks; save/stop/restart; edited-node reset and a second encounter run.
8. **Gameplay:** solo and cooperative run, resource shortage, wrong puzzle answer, boss reset, ally death, disconnect/rejoin, restart, full inventory reward and already-completed member.
9. **Visual review:** real client entry, room sequence, interior, roofline, underside, arena, night and intended dimension. Record shaders/hardware/settings. Offline renders support composition and are labeled as such.
10. **Polish:** no unintentional visual clutter, exposed room backs, empty inaccessible shells, repeated roof mistakes, floating lamps, unusable furniture, unreadable telegraphs, unexplained dead ends or missing reward exit. Record remaining issues explicitly.

Progress in passes: graybox â†’ support/roof/water correctness â†’ playable encounter graph â†’ material/art pass â†’ inhabited detail â†’ lighting/audio â†’ performance/accessibility â†’ final actual-client review. Detail belongs where it strengthens material, story, navigation or atmosphere. A thousand extra blocks cannot repair a broken route or an unfair boss.
