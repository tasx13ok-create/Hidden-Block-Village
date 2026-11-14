# Original shinobi skin painter

Paints original 256x256 skins in the standard **64x64-unit** player layout (4x density). The supplied textures include separate left limbs and outerwear, so our Encounter renderer uses `ModelPlayer(0,false)`. The default64x32 `ModelBiped` samples these atlases incorrectly. Each skin is built in layers:

1. A gradient base for each face.
2. Material grain: cloth weave, leather, metal, skin and hair.
3. Folds and stitched seams.
4. Gear: straps, pouches, sashes and engraved plates with original village glyphs.
5. A portrait face with expression.
6. Hair locks on the outer layer.
7. Edge occlusion and rim light.

All six characters and their glyphs are original designs for Hidden Block Village; none reproduce characters or textures from any show or other mod.

To regenerate them, run this from the repository root:

```
python tools/skins/hdcast.py --out local/actor-art-review/new-skins
python tools/skins/verify_actor_art.py --project-root . --regenerate-to local/actor-art-review/verification-render --report local/actor-art-review/verification.json
```

| Skin | Used for (`vale-encounters` ClientProxy `Skins.of`) |
|---|---|
| `ash_regent_boss` | Ash, Ember and Hearth bosses and mobs |
| `lantern_sage_dawn` | Lantern and Dawn profiles; a pool option |
| `rook_dusk_operative` | Rain, Fen and Reed bosses; mob pool |
| `garran_flint_jonin` | Slate, Flint, Quarry and Cairn profiles |
| `kaito_ember_genin` | Ember-family companions |
| `sayo_current_kunoichi` | Rain, Fen and Reed mobs and companions |

Any profile not matched by name gets a stable pick from its kind's pool, so each actor always keeps the same look.

The generator refuses to overwrite any existing output PNG. Keep review renders and reports separate from preserved sources. The verifier checks all six supplied PNGs, exact generated pixel identity and216 opaque base-body UV faces. A real headless prepared-API check constructs the64x64 native player model and exercises its visibility contract; actual shader rendering remains separate.

Only our three Encounter actor classes use these textures. This does not replace imported CustomNPCs, Residents, player skins or any original mod's NPC/armor art. Six appearances do not establish unique AI or gameplay parity for every actor profile.
