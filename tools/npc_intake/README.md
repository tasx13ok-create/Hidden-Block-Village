# CustomNPCs private intake

Run from the repository root with Python 3.8 or newer. Standard library only; no scripts, classes, Minecraft or Java execute.

```powershell
python -m tools.npc_intake --baseline D:/private-server --world Naruto.test --bundle D:/friend-npcs.zip --runtime-inventory D:/PRIVATE-STATIC-INVENTORY.json --output D:/new-npc-review
```

The default is a dry run. Exit code `2` means unresolved merge issues; the private report explains them. To generate a new verified backup plus data overlay, resolve the private mapping and rerun with **another new output directory** and `--mapping D:/npc-mapping.json --candidate`.

Bundle roots must be `server/customnpcs/` and/or `world/customnpcs/`, with `npc-bundle.json` at the ZIP/folder root. The manifest contains `{"schema":1,"required_mods":{"customnpcs":"<exact JAR SHA-256>"}}`; include every required addon/mod identity. `jar:mods/<filename>.jar` keys can pin coremod/bootstrap JARs without a top-level mod ID. The actual baseline JARs are hash-checked against the supplied runtime inventory before their resource names are catalogued.

Example mapping, with illustrative IDs and filenames:

```json
{
  "schema": 1,
  "id_remap": {"dialog": {"3": 1003}, "quest": {}, "faction": {}},
  "definitions": {"dialog:8": "incoming", "faction:2": "baseline"},
  "paths": {"server/customnpcs/clones/1/example.json": "incoming"},
  "opaque_acknowledgements": {}
}
```

ID remaps apply to incoming definitions and supported incoming references. Existing baseline references retain their IDs. A distinct definition at the resulting ID still needs an explicit `incoming` or `baseline` decision. Scripts, commands, linked NPC names and unsupported objective/global formats are preserved without interpretation and require a reviewed exact file-hash acknowledgement. Acknowledgement means the human has accepted a manual dependency review; it is not proof that an opaque reference was remapped or tested.

`PRIVATE-NPC-PLAN.json` contains private paths, ID graphs, conflicts and complete byte-hash manifests. Never commit it. A successful candidate has `backup/` with exact original supported NPC-data bytes and `candidate/` with the merged overlay; every written file is read back and hash-checked. This is not a complete world backup or a full server. Baseline playerdata and rollback files are deliberately outside the overlay. Missing incoming files never delete baseline definitions.

The adapter is bound to the actual pack's CustomNPCs/SpringAddon compatibility JAR hash. Typed SNBT keeps byte/short/int/long/float/double distinctions. Binary NBT preserves types, root name and compression choice when rewritten. Original byte hashes and canonical typed semantic hashes are distinct. Modified UTF-8 NUL/surrogate forms, unsupported JSON/SNBT forms, duplicate keys, excessive nesting/nodes/size, unknown paths, colliding paths and unresolved new graph edges fail explicitly.

See `docs/npc-merge-current.md` for the exact upload checklist, known supported reference fields and real private preview evidence. Placed entity/region merging and live-game acceptance remain separate work.
