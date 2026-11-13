# Original worn armor pass 1 — Core 0.3.15 source cohort

This is original editable vector artwork for Minecraft's **worn** armor surfaces. It is separate from Claude's inventory-icon work. Nothing here treats a flat inventory plate as a 3D armor model. The eight new Ward families use headbands, cloth wraps, utility vest/coat cuts, stitched element marks, and open-toe footwear. The active Lightning Chakra set adds a Cloud-inspired original three-curl plate engraving, ivory flak-vest collar/pouches, navy cloth and layered bandages. No reference PNG pixels or third-party models were copied.

The pass installs 173 PNG resources: 16 layers for eight four-piece Ward sets, 76 focus garments, 79 outfit garments, and two dedicated active-Lightning layers. There are 158 distinct PNG byte sequences; family reuse is intentional and recorded, especially footwear. This gives 191 existing equipment pieces new worn art in their applicable state. The four ordinary Storm pieces retain their classic inactive texture and select the Lightning texture only while the existing server-synchronized `LightningKit.EFFECT` is active. Ward matching, powers, caps, armor values and progression are unchanged.

All masters use a 64×32 logical UV viewbox, freshly rasterized at 256×128. Native `TexturedQuad` normalizes against the model's 64×32 textureWidth/Height, so the larger canvas adds quarter-unit ink/thread detail without changing UV islands. It is not a resize of an inventory PNG. Native leggings use ModelBiped inflation0.5; outer helmet/chest/boots use1.0. Native HEAD renders both head and the headwear cube (additional0.5 inflation); every authored headwear face remains transparent. Left arm/leg mirroring follows native ModelBox, so this pass does not claim independent left/right insignia.

The 36 pieces from nine independent ArmorMesh profiles are preserved. Their actual 895 project-owned cuboids /5,370 faces and eight-tile material UVs are catalogued in `mesh-face-uv.json`; their existing albedo/emission atlases and geometry are unchanged. A material tile atlas is not interchangeable with a humanoid skin unwrap. External shader interpretation of existing `_n`/`_s` resources remains unverified.

File08's complete256 reference texture rows across13 JARs remain in `coverage.json`. Their dimensions include non-native layouts and high-resolution/custom models. These are intake/rebuild obligations, not artwork copied or256 newly implemented textures. `coverage.json` retains the pre015 baseline hashes; approved changes are precisely the runtime assets in `runtime-assets.json`. All other83 preexisting owned armor PNGs remain byte-identical, including classic Storm and all custom-mesh resources. `wearable-variants.json` records all227 current project equipment bindings without claiming reference parity.

`previews/` shows six aggregate front/back sheets and a four-view Lightning hero. All163 roster entries plus the hero were rendered in656 offline native-contract views (front, three-quarter, back, walking), with positive equipped-slot visibility and zero out-of-canvas UV samples. Full individual sheets are reproducible locally. **These are software UV/geometry checks, not Minecraft screenshots, shaders acceptance or proof of native lighting.** Native getter, real equipped model, animation, multiplayer active/inactive state, shaders and copied-world acceptance remain separate.

Reproduce from the repository root using Python3.12 with Pillow and NumPy:

```
python tools/worn_armor/verify.py --rebuild
python tools/worn_armor/build_roster.py local/worn-qa/new-roster
python tools/worn_armor/render_roster.py local/worn-qa/new-roster
python tools/worn_armor/build_hero.py local/worn-qa/new-hero
python tools/worn_armor/render_hero.py local/worn-qa/new-hero
```

Destinations must be new; incomplete candidates are preserved. `verify.py --rebuild` compares all173 PNGs and173 editable SVG masters against fresh outputs, checks file08/source pins and unaffected baselines. Java `verifyWornArmor` checks packaged hashes, normalized dimensions, native headwear alpha and every actual roster binding/conditional route. `WornArmorNativeChecks.run(EntityPlayerMP)` is an excluded disposable-fixture hook that exercises registered getters, packaged resource presence and the actual effect state; it restores the prior effect in `finally`. It does not claim native model rendering. Actual native acceptance is recorded by the parent release receipt only after execution.
