# Private reference flat-icon pack

**Historical/disabled by current owner direction14November2026.** Keep original
mod icons and armor; our own additions sit on top. This pack is retained as a
private rollback/source artifact and must not be enabled by default. Read
`docs/original-mods-first.md` and use the latest stopped-client restore receipt.

The frozen Everything 0.3.14 production JAR cohort remains unchanged. `tools/reference_item_art.py` creates a separate private Minecraft 1.12.2 client resource pack from the owner's v3 PNG bundle and the locally supplied 49 hash-pinned original JARs. It imports 724 supported generated/handheld model states and retains 382 of the 1,106 reference rows. All 42 synthetic tests pass. This is static model/ZIP acceptance; the copied client has not yet graphically accepted this resource pack.

The private ZIP is `private/reference-art-draft/final-20261113/Vale-Reference-Flat-Icons-v3.zip`, 828,165 bytes, SHA256 `ed45b2f8aab0840b236aef97cfcec42c452a43a84a7c6119fbb622ce617ee39e`. Its 1,449 entries are 724 private model overrides, 724 original owner PNGs and pack.mcmeta. Coverage is in the adjacent `coverage.json`; public safe pins/counts are in `release/evidence/reference-flat-icons-v3-verification.json`. Do not publish the private ZIP: it contains observed third-party model JSON transforms. The original mapper, synthetic tests and hash/count metadata are public-safe.

## Render contracts retained

Only a complete parent chain resolving to native generated/handheld sprites with one layer is eligible. The private override deep-copies the original JSON and changes only `textures.layer0`. Existing parents, first/third-person transforms, aliases, charged/use/blocking predicates and their target models remain intact. Each replacement sprite has a unique `vale_reference_art:items/icon_<hash>` path. Original shared PNGs, block models, blockstates, worn armor UVs, entities and custom 3D models are never overwritten.

A model that is itself another model's parent or a world-model dependency is retained, preventing an inventory patch from changing another draw. Malformed lenient dependency JSON is conservatively scanned for references; unresolved/case-colliding/multiple-owner resources fail closed. Exact current project model paths are retained, including the four world-compat aliases and five Standards models. Entire original VariedCommodities/Waystones namespaces are not discarded.

| Retained reason | Rows |
| --- | ---: |
| Explicit/inherited 3D geometry |255|
| Block parent |46|
| Builtin custom renderer |28|
| Multiple/missing sprite layers |20|
| Source model exceeds bounded per-model read |11|
| Current project model already supplied |9|
| Shared parent/world dependency |7|
| Custom model keys |2|
| Missing exact source resource |2|
| Animated original texture |1|
| Invalid original model JSON |1|

724 mapped states contain 284 distinct RGBA images in the submitted artwork. Repeated submitted pixels are recorded honestly, not claimed to be distinct art. 538 of the 685 raw equipment state rows map safely. The addendum's 673 count excludes 12 infrastructure entries; its 601 base-form equipment classification is a different scope from raw visual-state rows. Alternate/thrown/blocking forms are not extra unique weapons.

The 1,772 named art slots comprise 592 current item model slots, 74 proposed HUD slots and 1,106 reference model slots. The earlier 561 runtime inventory PNG imports are separate from this 724-state private pack. Neither sum proves every named slot is a unique item or visible in runtime. 74 proposed HUD slots, 26 retained charged strips, 5 preserved project 3D models, unsupported reference models and actual worn-UV work remain explicitly separate.

## Reproduce locally

Use Python 3.9+ and supply the owner's existing original inputs. The mapper never downloads, launches Minecraft, modifies JARs or writes into the original mods directory. Output must be a new directory.

```powershell
python tools/reference_item_art.py `
  --inventory collaboration/sonnet-art/asset-inventory.json `
  --equipment collaboration/sonnet-art/equipment-inventory.json `
  --art-archive artwork/claude-v3/naruto_icons_v3.zip `
  --art-provenance artwork/claude-v3/provenance-manifest.json `
  --mods-dir 'C:/Users/Steal/curseforge/minecraft/Instances/Hidden Block Village/mods' `
  --minecraft-jar 'C:/Users/Steal/curseforge/minecraft/Install/versions/1.12.2/1.12.2.jar' `
  --output private/reference-art-draft/new-reviewed-pack
python -m unittest discover -s tests -p test_reference_item_art.py
```

The 42 tests use synthetic original JSON/PNGs only. They cover deterministic output, source hash/member tampering, exact-case collisions, duplicate resources/JSON keys, unsafe paths, shared original PNGs, charged-state/display preservation, parent cycles, inherited 3D/multilayer rejection, animations, aliases, missing assets, direct custom renderers, exact project aliases, shared parent/world dependencies and malformed/escaped lenient dependency references. Source hashes are checked again after real pack generation.

Install only into a copied client's `resourcepacks` directory, retain its existing pack list, and have the runtime owner activate this pack with an ordinary resource reload or restart. The mapper does not do this automatically. Actual unsupported render contracts keep their original assets; no flattening of 3D weapons or armor is performed.
