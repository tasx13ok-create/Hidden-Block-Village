# Five additional Naruto coliseums

These are new sites alongside the preserved v17 campaigns. The generator and
server module are original project source. No reference artwork, copied world
cells, decompiled implementation, or third-party JAR payload is published here.
The major geometry uses vanilla 1.12.2 blocks. The installed original Naruto mods
supply native boss behavior and assets locally. Original-provider art and
registry ownership remain under `docs/original-mods-first.md`.

| Site | Native candidate | Preview minimum | Export maximum |
| --- | --- | --- | --- |
| Valley of Roots / Hashirama | `ahznbcursemarkaddon:edotensei_hashirama` | 64000,3,64000 | 64499,252,64499 |
| Storm Crown / Ay | `kabutoaddon:ay` | 65024,3,64000 | 65523,252,64499 |
| Kazekage Sun-Sand / Gaara | `narutomod:gaara` | 66048,3,64000 | 66547,252,64499 |
| Drowned Shark / Kisame | `narutomod:kisame_hoshigaki` | 67072,3,64000 | 67571,252,64499 |
| Ashen Moon / Madara | `ahznbcursemarkaddon:edotensei_madara` | 68096,3,64000 | 68595,252,64499 |

The owner's14November clarification requires the stadium architecture itself to
span500×500×250, with usable full-size stands and concourses. A smaller stadium
inside a500×500plot fails. Architectural-layer occupancy is measured separately
from terrain and plot padding; the older220..340outer-bowl proposal is superseded.

All exports are 500×250×500 in width,height,length order. Anchors in each
`arena.json` are local standing positions. Add the preview minimum componentwise
to get the actual world standing position; the floor is one block below. The
schematic stores WEOrigin equal to its minimum and zero WEOffset. Use explicit
local-to-world arithmetic instead of assuming a stored origin is safe elsewhere.

Generate into a new private destination using Python3.12, NumPy2.2.6 and
Pillow11.2.1. Dependencies can be installed into a separate private directory and
selected with `PYTHONPATH`. Every invocation refuses existing destinations.

```powershell
python -m tools.arenas build --brief collaboration/arena-build-orchestration/arena-brief.json --all --output D:/NEW-PRIVATE-BUILD --dry-run
python -m tools.arenas build --brief collaboration/arena-build-orchestration/arena-brief.json --all --output D:/NEW-PRIVATE-BUILD
python -m tools.arenas audit --manifest D:/NEW-PRIVATE-BUILD/manifest.json --strict --report D:/NEW-PRIVATE-BUILD/audit.json
python -m tools.arenas stage-world --manifest D:/NEW-PRIVATE-BUILD/manifest.json --dest D:/NEW-PRIVATE-PREVIEW-WORLD --report D:/NEW-PRIVATE-BUILD/stage.json
python -m tools.arenas native-checks --candidate D:/NEW-PRIVATE-PREVIEW-WORLD --cases all --report D:/NEW-PRIVATE-BUILD/native.json
```

The last command stages a new isolated loopback Magma runtime from explicitly
pinned private inputs and boots it headlessly. Read `runtime.md` for the exact
implemented commands, lifecycle boundaries and native result scope. It must never
be pointed at an owner's live runtime. The owner's Minecraft client stays closed;
the offline block projections are labeled and cannot establish client/shader,
native lighting, collision or encounter acceptance.

The generator writes one arena at a time through memory-mapped byte arrays and
streams gzip/NBT directly. Scratch arrays remain private for failure investigation.
The independent reader uses its own bounded parser and fresh scratch files. A
failed candidate/report is retained; repairs produce a new build directory.

Stage creates a fresh void Overworld, checks every reserved plot against its
existing candidate regions, writes all five sites and makes a complete hash-checked
cold snapshot. It does not import player profiles or original CustomNPCs. It
refuses production source-world integration; use the existing reviewed
`tools.world_merge` workflow for that separate task. The old Mist200×200 settlement
and every original v17 file remain outside this writer's destinations.

Stop only the isolated process whose current runtime identity has been verified
before recovery. `tools.arenas.stage.restore_snapshot(receipt,new_destination)`
restores the entire verified cold snapshot into a new directory; it preserves the
failed candidate. Never reset a live arena by pasting its whole volume over players.

See `handoff.md` and the arena-specific evidence for exact delivered files,
source/output hashes, actual tests, repairs and unresolved acceptance. An offline
PASS accepts only its stated byte/geometry scope. It is not a hosted release.
