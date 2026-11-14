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

Private authoring dependencies: NumPy 2.2.6 for bounded full-output scans and chunk
conversion; Pillow 11.2.1 for the integrator's offline raster previews. They are
installed into the private D: authoring directory, not distributed with the game.
The unit suite exercises malformed readback, 12-bit nibble ordering, collision
headroom, water support, Anvil axis/metadata round trips, occupied reservations,
destination refusal and complete snapshot drift rejection.

Actual per-candidate results and exact artifact hashes belong in the integrator's
arena evidence and local manifest. Minecraft remains closed under the owner's
standing preference. Shader/client acceptance stays pending until that changes.
