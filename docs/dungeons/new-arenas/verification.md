# Arena verification and world isolation

`tools/arenas/audit.py` independently decodes written gzip/NBT files. It imports
neither the generator nor its scratch arrays. Blocks, Data and optional AddBlocks
are streamed into temporary mmap files under the private export directory, then
removed on normal completion. Decompression, tag depth, cell and list budgets stay
bounded. Duplicate tags, truncated arrays and unsafe paths are rejected.

The output audit measures all 62.5 million cells per site: actual occupied bounds,
palette/metadata counts, source-fluid enclosure, gravity support, required tile
payloads, exact standing anchors and every authored route. Required route strips
are checked across their width and headroom; an independent walking network must
connect arrival, preparation, checkpoint, boss, reward and exit. Optional submerged
spaces are reported as wet spaces; they do not pass as dry walking routes.

Every required structural layer also needs recorded writes and surviving nonair
block witnesses. Independent output readback checks each witness position, ID and
metadata; missing modules or altered witness cells fail. This is sampled module
occupancy evidence, not an analysis proving support for every authored cell.

The owner's later clarification requires the stadium architecture itself to span
500×500×250. Export plot bounds and terrain no longer satisfy that requirement.
The written Alpha NBT includes `ArenaLayers` (one last-writer byte per cell) and
bounded JSON `ArenaLayerNames`. Independent output readback counts actual nonair
cells in a fixed architecture allowlist: bowl/stands, galleries, landmarks,
functional interiors, roof transitions and explicit stadium structure/gallery/
roof layers. It excludes terrain/foundation, supports-only, circulation, fluid,
anchor, lighting, detail and mechanic markers.

The offline anti-padding gate requires architectural bounds `[0,0,0]` through
`[499,249,499]`, at least 50,000 architectural cells in each outer 16-block band,
100,000 per XZ quadrant, 500 on each of the six extreme planes and 10,000 across
the upper 50 layers. Every count comes from the written output; unknown layer
labels fail. These explicit occupancy guards prevent a plot-sized foundation or
extreme marker from passing as a full-size stadium. Native support, usable routes,
physics, encounters and visual acceptance remain separate gates.

These checks do not prove native collision, underwater survival, block lighting,
mod AI, player kit behavior or encounter completion. Native-form envelopes are
design envelopes until measured against the actual runtime. A static registry
name, render or green Python report cannot satisfy a native acceptance row.

`tools/arenas/stage.py` implements the authorized fresh preview-world option. It
creates a new void Overworld, places the five independent outputs in region-aligned
plots at Y3..252, creates no player/NPC/account records and refuses an existing
destination. It first refuses drifted build/current source identities and reruns
the strict independent five-export audit against the exact manifest SHA256.
The absolute manifest path/hash are recorded in the world marker and cold-copy
receipt; runtime selection must use that identity and never glob a latest build.
Every reservation is checked against candidate Anvil bytes before
placement. It never pastes into the immutable v17 candidate or an owner's existing
world. Production-world staging remains under the reviewed `tools.world_merge`
copy/paste/rollback workflow and is deliberately refused by this fresh-world tool.

Each 500-block plot must be region-aligned and occupy a different 512-block
region. This avoids deleting another plot's cells in a shared boundary chunk.
World coordinates are checked before placement. New destinations nested inside
an existing world or server runtime are refused. The newly created empty world
gets a complete hash-verified snapshot before the first placement; the finished
five-site world gets a second complete cold snapshot before native boot.

Final staging also authors a two-chunk empty void buffer around each plot's
32×32 chunk AABB. The 1,360 additional chunks are independently reopened and
checked for zero blocks/metadata over all256 layers and no tiles/entities/ticks.
Each raw typed chunk NBT receives a SHA256 and the sorted inventory is hashed.
Already staged neighboring region slots are read before atomic region rewrites;
their bytes are preserved. The cold snapshot includes these buffers. They enable
bounded native prewarm of existing chunks without generating unseen terrain;
they do not force-load all6,480 plot/buffer chunks.

The fresh preview explicitly stores `keepInventory=true` and the original
NarutoMod `keepNinjaXp=true` rule. This preserves a legitimately earned once-only
awakening through death. It does not repeatedly grant battle XP, alter an
original world or change a global server config. Native acceptance must call
the actual `PlayerTracker.keepNinjaXp(world)` API after boot; the level.dat string
alone is not that acceptance.

Chunk conversion uses bounded NumPy arrays per 16×256×16 chunk. Alpha metadata is
repacked into Anvil low-even/high-odd nibbles. Empty sections are omitted. Existing
Anvil/NBT defensive writers serialize output atomically; no hostile-input limits
are increased. Initial lighting arrays are zero and LightPopulated=0: native
relighting is a required later check, not precomputed acceptance.

The exposed route-fixture audit reopens every declared new sea lantern and
checks its exact block/meta, adjacent solid bearing, served authored route point
and actual transparent cardinal path of at most seven edges. An opaque stair
interrupts that path even when the source is close in Manhattan distance. This
checks the declared added fixtures; it does not establish whole-route native
block light. The negative test corrupts an actual path with a stair and removes
its bearing.

After staging, a separate whole-world cold snapshot is copied and every relative
file, size and SHA256 matched. `restore_snapshot(receipt,new_destination)` verifies
that exact complete source inventory and restores all files into a new directory.
Failed worlds and source snapshots are preserved. Restoring one arena region is
not represented as a whole-world rollback.

`verify_world_full` independently reads all Anvil sections and compares every
exported block ID and metadata value: 312.5 million cell comparisons across the
five plots. It does not reuse the staging conversion helper. Lighting arrays and
world time are not included because native runtime legitimately changes them.

Storage chests are actual vanilla block54 with a bounded standard TileEntities
list (maximum128). Independent output readback checks tile identity, coordinates,
lid clearance and a safe standing position on the facing side. A tiny captured
tile payload uses the existing typed NBT decoder so unknown tags and numeric
types survive coordinate remapping. Staging installs each tile in its correct
world chunk; the full Anvil comparison checks typed payloads as well as every
block and metadata cell. Native container opening remains a separate gate.

Private `ServicesNativeChecks` uses real native villagers and an officially
registered player. It tests exact three-role UUID stability, rejected template
and missing-UUID cases without replacement, non-adoption of an unrelated same-
class actor, native typed entity NBT and real process-restart UUID retention.
Aid checks dispatch through Forge's actual interaction hook and measure healing,
cooldown, player.dat durability and preservation of stronger effects. Keeper/
stores checks establish actual interaction routing; they do not establish client
chat presentation or imported NPC compatibility.

Private asynchronous `PartyNativeChecks` creates official registered survival
bodies with legitimate physical two-node introductions. It exercises party sizes
one through four, rejection of an introduced fifth player, spectator/fake and
actual other-run membership rejection, final cancellation/proximity credit
negatives and old-contact rejection across logout/new-body login. It uses an
accepted physical objective to create a genuine secondary contributor. The
bounded live phase equips real Core-owned armor and retains four actual survival
participants near the authentic updating boss before leaving the contributor
offline for the driver's actual clear/archive case. Positions are QA guidance;
these are not human walking, client input, full cooperative-clear or sustained
load acceptance. Accepted healing/damage contribution still requires the shared
authority surface; no intent-only credit is fabricated.

The optional `ArenaStarterKits` component uses an independent permanent UUID
choice and Core's public durable ledger for four matching Ward pieces plus the
chosen order's basic saber. It requires genuine completed physical practice and
rejects current arena membership, preview, creative, spectator and fake bodies.
It never assigns native nature, battle XP or Core starter order and never
replaces inventory. Retries reuse the same five receipts, including after loss.

The immutable `preview-world-04-native-08` candidate passed the actual starter
two-process case:45 assertions saved a full-inventory five-item pending queue;
51 assertions on the second boot loaded that queue, delivered five genuine
owner serials and rejected replay/changed choice/replacement of lost gear.
Native XP/non-Core state and Core order preservation also passed. Both boots
stopped cleanly. These results prove that bounded starter scope, not boss-clear
entitlement or complete arena acceptance. The same native candidate retained
126 dark route samples out of9,261, so its lighting gate remained failed.

Exact starter production source SHA256:
`d735a48daebd678246841b03f328f9eedc66b30ceb7a700dcc0ae4ad66524e02`.
Private helper source:
`09355de617d1271a85275e5ee3535b6d053ad851fbee6413dd3f877d18d72eb5`.
That candidate's arena runtime JAR:
`dc86d0ad20f57214b8fee2f3a38a03f101f9810790a3040f2306db8428474d2f`;
private QA JAR:
`e457d467c082afeec80335ad34dad7a3bbaf000fea089d1dcdc26c24a5c7731d`.

Build05 independently passed the five-output strict audit, including 1,007 added
exposed-fixture paths. The complete04-to05 block/meta comparison found no changed
cell outside the old or new lighting layer. Replacing the previous lighting pass
also removes its trim cantilevers/uprights and restores the underlying authored
cells; an initial add-only comparison failed and remains retained. All anchors,
container metadata and 25 typed chest payloads are identical. The scoped lighting
replacement comparison and the raw failed assertion are separate evidence files.

The pristine `preview-world-05` passed 312.5 million block/meta comparisons and all
25 typed tile round trips, plus 89,128,960 zero-cell checks across its 1,360 buffer
chunks. A new whole-world restore into `preview-world-rollback-05` matched all 35
file hashes. Its manifest SHA256 is
`4113e1f45b505c9c96637383c1311a5a1c73158df6510494ed7d1ba2033e3a8e`;
the cold receipt SHA256 is
`6afdaaf8b4cc9ed3bf70fb5e5aa19862c32209d1f3481fd28549ac0784730782`.
These are offline results. Native lighting/gameplay and post-native structural
readback must bind a cleanly terminated copy of this exact candidate.

The [final06 independent receipt](../../../release/evidence/arenas-final06-independent.json)
consolidates safe aggregate results and exact hashes of the private evidence.
It accepts only its named scopes; it does not claim a complete native suite,
load acceptance, physical client input or shader acceptance.

Build06 passed the independent strict audit and fresh-world staging. Every one
of the 312.5 million block/meta pairs and all 25 typed chest payloads matched
the staged Anvil world. The 1,360 buffer chunks contained 89,128,960 zero
block/meta cells and no entities, tiles or ticks. A whole cold restore matched
all 35 file hashes. The exact05-to06 comparison found only the intended 399
Mist floor lights: 393 water cells and six stonebrick cells became sea lanterns.
The other four schematics, every anchor and all typed chest payloads were
unchanged. Independent wet-route readback checked all 133 authored points
across the three-wide, four-high source-water envelope and supported light strip.

Two stopped native snapshots retain raw exact-match failures rather than being
presented as byte-identical worlds. The earlier full-controller candidate had
13,383 Forest grass-to-dirt changes; the seven-boot restart candidate had 3,563.
Every changed cell belonged to terrain/foundation, was covered by an opaque
block and had native saved block/sky light zero. All other authored blocks/meta
matched. Chest differences were only native empty `Lock` strings, on 25 and six
chests respectively; inventories and all other typed fields matched. These
observations are consistent with covered-grass decay and establish the bounded
absence of architectural changes in those snapshots. They do not prove an
exclusive cause or universal native jutsu grief immunity.

The restart case completed seven successful logical boots, zero through six.
One separate JVM resource failure before world load remains retained; continuation
used a new whole-hash-verified copy, without claiming a rollback. At final save,
all five runs were AVAILABLE with no members or journal cells. Independent entity
readback covered every 6,480 plot/buffer chunk and 201 additional saved Overworld
chunks, including passengers. All five exact last-owned boss UUIDs and the owned
Ay ally UUID were absent, with no replacement actors, duplicates or saved players.
The actual saved actors were ten authentic registered service villagers and
three preserved baseline Residents. The restart-only fixture had provisioned
ten of fifteen possible service roles; the receipt does not infer fifteen actors.

Focused Wet checks passed on both clean boots: each official survival body
traversed every authored point, spent 681 real body/world ticks submerged,
reconnected underwater and reached an actual supported dry recovery. Genuine
camp aid supplied 2,400 ticks of breathing, preserved stronger existing and
unrelated effects, and respected its cooldown. Health remained 20 and air 300;
the case used no health/air setters, immunity or world writes. Movement was
explicit server-guided `MoverType.SELF`, not client swimming input. The retained
original potion equality failure was traced to one real FML player update inside
logout. The corrected check subtracts exactly measured updates from Duration and
requires every other typed and unknown potion/curative capability tag to match.

The initialized native world still needs its own independent readback after the
runtime owner announces its exact clean stop. That pending row is kept separate
from the completed offline, stopped-snapshot, restart and focused Wet evidence.

Private authoring dependencies: NumPy 2.2.6 for bounded full-output scans and chunk
conversion; Pillow 11.2.1 for the integrator's offline raster previews. They are
installed into the private D: authoring directory, not distributed with the game.
The unit suite exercises malformed readback, 12-bit nibble ordering, collision
headroom, water support, Anvil axis/metadata round trips, occupied reservations,
destination refusal and complete snapshot drift rejection.

Actual per-candidate results and exact artifact hashes belong in the integrator's
arena evidence and local manifest. Minecraft remains closed under the owner's
standing preference. Shader/client acceptance stays pending until that changes.
