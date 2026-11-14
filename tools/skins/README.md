# Original shinobi skin painter

Paints original 256x256 biped skins in the standard 64-unit skin layout (4x density), so they work on any 64-UV `ModelBiped` renderer. Each skin is built in layers:

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
python3 tools/skins/hdcast.py tools/skins modules/vale-encounters/src/main/resources/assets/valeencounters/textures/entity
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
