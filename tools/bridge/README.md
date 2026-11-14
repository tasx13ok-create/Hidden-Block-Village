# Naruto bridge proposal tool

`propose_moves.py` reads private registry IDs and creates two new candidates:
`bridge-moves.proposed.json` and `bridge-moves.review.csv`. It preserves Claude's
ordered keyword rules from commit `3a257fe16c302d2a0b0ee9fd5c07072f789ddf41`.
These rules suggest visual styles; they do not establish a mod's behavior,
ownership API, permission to redistribute it, or functional parity.

Use a fresh private output directory for every run:

```powershell
python -B tools/bridge/propose_moves.py <private>/runtime-registry-names.json local/bridge-review-01
```

By default the tool reads this repository's original
`modules/vale-orders/src/main/java/village/vale/arsenal/ArsenalSpec.java`.
For a separate draft, pass `--arsenal-source <matching-source>/modules/vale-orders/src/main/java/village/vale/arsenal/ArsenalSpec.java`.
All `weapon:<key>` rules must exist in that source's actual original enum keys.
There is no guessed third-party weapon API or replacement key catalog.

## Private input and validation

The UTF-8 JSON input is an object whose only allowed keys are `items` and
`entities`. Each optional key contains a list of registry ID strings; an omitted
list is empty. This matches the runtime's ID-only `bridge-registry.json` export.
Its separate observation/metadata discovery file is not proposal input.
Synthetic example:

```json
{"items":["sample:kunai"],"entities":["sample:rasengan"]}
```

The tool rejects duplicate JSON keys, NaN/Infinity, unknown keys, incorrect types,
duplicate IDs within either kind, and invalid IDs. The same ID can appear once
as an item and once as an entity; kind is retained. All IDs are validated before
classification, including Minecraft IDs and non-move hints. Input is bounded to
2 MiB and 16,384 IDs total. IDs are at most 128 ASCII characters, with exactly
one colon, a 1–64 character namespace `[a-z0-9_.-]`, and a 1–96 character path
`[a-z0-9_./-]`. Uppercase, whitespace and controls are rejected.

Claude's first matching keyword still wins. Its original non-move skip terms
now match complete delimiter-separated words or word sequences: `ore` excludes
`fire_ore`, while `forest_fire` remains eligible; `log` excludes `fire_log`, while
`fire_dialog` remains eligible. These remain name heuristics. Minecraft IDs
appear in the review CSV with reason `minecraft` and are never proposed. The CSV
also includes skipped and unmatched names. A leading spreadsheet formula
character is prefixed with an apostrophe in CSV cells; JSON IDs remain exact.

## Inactive schema and review boundary

The generated JSON has exactly three top-level keys:

```json
{
  "schema": 1,
  "verified": false,
  "moves": [
    {"kind":"entity","id":"sample:rasengan","when":"damage","owner":"damage_source","style":"spiral"},
    {"kind":"item","id":"sample:kunai","when":"melee","owner":"direct_player","style":"weapon:waymark_knife"}
  ]
}
```

Rows have exactly `kind`, `id`, `when`, `owner`, and `style`. Output is sorted by
kind and registry ID, uses UTF-8 with LF newlines, and always has
`verified: false`. The runtime's accepted file requires `schema: 1` and
`verified: true`; renaming this candidate cannot activate it. This tool never
writes `bridge-moves.json`, edits an accepted mapping, or approves its output.
It refuses an existing JSON or CSV candidate, including an empty file or
dangling symlink. Choose a new directory instead of overwriting review evidence.
Validation, classification, and rendered size checks finish before output files
or directories are created. Creation uses exclusive writes.

The runtime limits also apply to proposals: at most 512 rows and 128 KiB of
rendered UTF-8 JSON, with style length at most 64. Oversized proposals fail with
an explicit error; nothing is silently truncated. Review smaller input groups
instead. An accepted combined file must still satisfy the same total limits.

Registry names cannot prove a spawn owner accessor. Entity proposals therefore
use only `when: damage` / `owner: damage_source`; item proposals use only
`when: melee` / `owner: direct_player`. The CSV repeats these limits per row.
An item name does not establish item-use/cast events, and a damage event does
not establish jutsu casting. Verify the exact event, real player attribution,
visual style and intended item/entity identity against the installed version
before separately approving a runtime mapping. This tool makes no gameplay
verification or deployment claim.

The paired runtime contract also permits manually reviewed entity/spawn rows
with owner `thrower`, `arrow`, `ownable`, `naruto_projectile`, or `naruto_flow`;
the generator emits none of those. Entity/damage uses only `damage_source`;
item/melee uses only `direct_player`. Available style names are `spiral`,
`charge`, `fireedge`, `windedge`, `lava`, `lightmode`, `none`, `dodge`, and
`weapon:<actual ArsenalSpec.key>`. Actual runtime acceptance is a separate check.

Keep registry dumps, generated CSV/JSON, runtime logs and production inputs in
ignored `local/` or another private directory. Do not commit them merely because
the generator validates their syntax. Preserve original inputs and candidates.
Public tests use synthetic IDs only.

## Checks

```powershell
python -B -m unittest discover -s tools/bridge/tests -v
```

Tests cover schema identity, conservative ownership, deterministic bytes, current
weapon-rule validation, skip boundaries, formula safety, parser/ID/type/duplicate
rejection, row/byte limits, pre-write validation, overwrite refusal and the CLI.
For a detached draft test run, set `HBV_ARSENAL_SOURCE` to the matching original
`ArsenalSpec.java`; it is read only. Standard-library tests do not start
Minecraft, access a server, or establish runtime acceptance.
