# Original mods with project integration on top

The owner explicitly requests the original supplied mod experience, including
old Varied Commodities icons and worn armor, with our Core/HUD/missions and
other additions connecting it. This supersedes the earlier global reference-art
reskin. Project-owned icons and worn armor remain available.

`tools/restore_original_mod_art.py` changed only the selected resource-pack list
in the stopped Creative0316test client. It disabled724reference model overrides,
including353Varied Commodities overrides. All58runtime JARs and the archived
overlay pack remain byte-identical. Forty-five installed JARs exactly match the
original49-mod inventory. The exact original Varied Commodities JAR remains
`bb1afc496b25e2afda523ffc02e3883b4f5a8e6c852c6d5818bd2d82afae6016`.
That pack contained no worn-armor UV replacement; its removal restores original
inventory models/icons while native worn textures remain in the original JAR.
The prior options file and a private restore receipt are retained on D:.

The audit also found our World Furnishings compatibility JAR supplied three
Varied Commodities blockstates/item models. Its1.0.1source now defers block,
item and tile ownership to installed original Varied Commodities/Waystones.
Its fallback art moved into `assets/valeworldcompat`; explicit client model/state
mapping serves only fallback blocks actually owned by this compatibility mod.
Saved technical IDs remain stable when the provider is absent. This prevents
the compatibility module from replacing original furniture presentation or
behavior when the original mod is installed.

The matching source09build now passes all11areas/36artifacts,3,665Forge native
assertions and55actual full54Magma provider checks. Original block/item/tile
classes and native ID/factory/NBT roundtrips pass. A read-only hash-checked census
of all1,249,484frozen-world chunks found65VC/Waystones tiles, all using the exact
modern native keys verified by the probe. No additional aliases are needed for
that frozen input. Read `release/evidence/original-provider-native09.json` and
`release/evidence/frozen-v17-provider-tile-census.json`.

The stopped test client has the resource-pack selection change; the new matching
component installation is separate. No Minecraft window was opened. Visual
inspection, full opaque-payload migration and copied-world restart remain
pending; the census does not claim those broader results.

Future client/friend exports must use the stock-art selection and the latest
matching approved component receipt. Historical flat-icon/friend archives remain
rollback inputs, not the current art policy. Do not upload original third-party
JARs or derived reference-art packs to the public repository.
