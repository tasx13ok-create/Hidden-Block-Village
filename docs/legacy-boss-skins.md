# Legacy boss and character skin PNG gallery

The owner requested the full old boss list and original skin PNGs. `tools/legacy_gallery/skin_pngs.py` reads the curated 67-entry roster and joins each entity/nested class's literal PNG resources to the supplied reference assets. It executes no reference code. All five source JARs are SHA256-matched to the roster. It found PNG references for all 67 entries: 37 named characters, 20 additional combat NPC types and ten tailed beasts.

The result contains 78 unique original PNG files and 79 entity/PNG bindings. Itachi Uchiha and Edotensei Itachi share one PNG. Additional resources include Jiraiya Sage Mode, four Shinobi skins, Kurama chakra mode, Ten Tails forms/minion, Matatabi's flame overlay and explicitly labeled related core/symbol/projectile/beam textures. PNG counts are not unique boss counts. Not every named character is a boss; merchants/supporting characters are included for completeness. Direct class-resource coverage does not enumerate private CustomNPC skins or every possible runtime-composed variant.

The ignored local bundle is `exports/Legacy-Boss-Skins-20261113.zip`, SHA256 `4e82e1a159c6c8584b1e4c8cff73c33d5aa9d0306df8c9086c9de79ee2c74bbb`. Its extracted folder has `index.html`, a complete text roster, original PNGs by namespace, exact source/hash metadata and five labeled preview sheets. The HTML embeds all images and supports name/mod/variant filtering. These previews are flat texture maps, not new 3D renders.

Observed validation: all 67 entries mapped; all 78 PNGs decoded and matched their original entry bytes/hashes; all 78 images are embedded in the HTML; each preview sheet was visually inspected; ZIP CRC, entry set and byte equality passed. The HTML script/structure was inspected; no new in-game or browser acceptance is claimed. Pillow 13.0.0 was installed only into an ignored local dependency directory for artifact generation. Original artwork and ZIP payloads remain under ignored exports and are not part of this public source checkpoint.

Rebuild with Pillow available, a new output folder and the supplied private JAR directory:

```text
python tools/legacy_gallery/skin_pngs.py --mods-dir <private-mods-directory> --output exports/Legacy-Boss-Skins-New
```

Safe public provenance is `collaboration/legacy-boss-gallery/skin-texture-index.json`; the raw assets are read from the owner's local input files. This PNG extraction does not repair or verify the unfinished stationary model renderer described in the previous handoff.
