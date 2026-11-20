# Bijuu armor 4K reference edition

The owner supplied a nine-outfit reference sheet and requested a close visual match, 4K textures and shader response. The edition preserves the armor model shape and puts the detail into the texture.

The painter maps each helmet front separately from its visible cheek and centers the lower opening on the face UV. Isobu retains the continuous shell guard. The larger figures supply chest art and the sleeve, knee and boot details. Alternate faces use the relevant thumbnail panels. Hidden faces continue adjacent colors and trim. The reference has scene lighting and overlapping effects and does not provide clean UVs for every face. The original image and comparisons that contain parts of it stay local.

The vanilla export contains 18 RGBA color textures at **4096 × 2048**, 36 LabPBR maps at the same dimensions, and 36 distinct **32 × 32** item icons. It uses the standard 64 × 32 Minecraft 1.12.2 armor UV proportions and binary alpha. The comparison sheets show front, side, back and three-quarter offline views. Helmet-alignment.png pairs each reference head crop with fixed straight-on and angled helmet views.

The private JAR exporter projects the color faces directly into the original mod’s **4096 × 4096** atlases. Chest, sleeve and leg patterns wrap through matching front-to-side edges. The existing tail patterns and opacity are preserved and tinted with sampled reference colors. It validates the exact 0.16.2-rc1 input and checks all 273 entries after export. Only 101 texture PNGs change. Original model definitions, code, registry, tail physics and configurations remain byte-identical. The mod ID and version remain hvbijuu / 0.16.2-rc1.

Use [LabPBR](https://shaderlabs.org/wiki/LabPBR_Material_Standard) in BSL’s material format with Advanced Materials active and OptiFine’s Normal Map and Specular Map toggles on. The albedo carries the pictured shading. The maps vary surface gloss and add restrained highlights. The height field is flat. See [BSL’s official FAQ](https://capttatsu.com/bslshaders/faqs.html).

## Rebuild

Requires Python 3.12 and Pillow. Use a fresh output directory:

    py -3.12 tools/bijuu_reference/build.py <owner-reference.png> <output-directory>
    py -3.12 tools/bijuu_reference/native.py <output-directory> <original-0.16.2-rc1.jar> <new-jar>

The PNG sheet must be the original 828 × 1792 image. The build report records its SHA-256 and exact RGB sample positions. The generator checks binary alpha, UV bounds, stored dimensions, icon color retention at 16 pixels, archive CRC and extracted bytes.

The isolated Windows Forge check uses installed launcher libraries, Java 8 and a separate Java compiler:

    py -3.12 tools/bijuu_reference/qa/client.py --install <launcher-install> --java <java8.exe> --javac <javac.exe> --artifact <new-jar> --output <fresh-trial-directory>

Supply the optional **--optifine** and **--bsl** inputs to render the isolated gallery and capture all nine sets with fixed straight-on and 32-degree cameras. The probe is separate from the deliverable JAR and uses a synthetic singleplayer world.

Share original source, documentation and safe verification metadata in Git. Keep the input and derived JARs, owner artwork, rendered comparisons and local screenshots private.

## Owner v3 tail merge

The inked edition straightens the pictured chest markings before the side seams are laid out. Small traced contours follow the original markings. The overview pairs each original with a level front view.

Build the armor JAR first. Run the following with a JDK 21 bin directory and ASM 9.7 JAR. The merge reads the owner archive locally and writes a fresh private candidate.

    py -3.12 tools/bijuu_reference/tails/merge.py <armor-jar> <owner-v3-zip> <fresh-output-jar> <jdk-bin> <asm-jar>

The adapter attaches the supplied hierarchy to the chest and places each supplied texture in an empty area of its corresponding atlas. Existing tail rotation changes drive the replacement segments. The ordinary armor export and UV ZIP remain available independently. After running the shader gallery, create a direct screenshot comparison with the following command.

    py -3.12 tools/bijuu_reference/gallery_compare.py <source-picture> <gallery-screenshots> <comparison.png>

## 8K fitted edition

Each chest mark moves onto the biped centerline at its original width. The outer cloth absorbs the remaining change in perspective. The native exporter uses separate front samples for both arms and legs. Vanilla armor shares a limb UV between the two sides.

After the v3 merge, run `export_8k.py <merged-jar> <new-8K.jar>` and `export_8k.py <vanilla-zip> <new-textures.zip>`. Each source texel occupies a two-by-two block in the 8K maps. The builder decodes each saved map and verifies that downsampling restores its exact source pixels. The native atlas is 8192 square and the vanilla layers are 8192 by 4096. Include 8K in the test JAR filename to select the isolated 8K test settings.

Run `front_compare.py <owner-picture> <native-jar> <proof.png>` to inspect each set against the original without camera perspective. The in-game comparison uses the gallery capture tool above.

## Registered front master

The private orthographic guide redraws the owner's original nine outfits. Build with `build.py <original> <fresh-output> --front-master <registered-guide>`. The builder cuts the head, torso and both sets of limb fronts from the guide. The original sheet supplies unseen surface continuations and the palette. This version's visor is painted directly from the guide. Source tools require Pillow, OpenCV and NumPy.

After building the native JAR, apply the v3 merge and 8K exporter above. Run `check_master.py <registered-guide> <8K-jar>` for byte comparisons on all 72 visible base cells. Use `master_compare.py <original> <registered-guide> <gallery-screenshots> <proof.png>` for the three-way comparison. Use `player_skins.py <builder-output> <fresh-zip>` for nine Classic 64 by 64 player skins. The reference image and outputs stay private.

## Side and back turnaround

Pass `--turnaround <registered-side-back-sheet>` with `--front-master` to build all four horizontal faces. This private drawing supplies distinct helmet, sleeve, rear torso, trouser and boot art. The arm covers the torso in the profile drawing, so that narrow side continues the corresponding rear flank. Each torso and sleeve maps the same vertical range between neckline and hem. Side edges take the adjacent front and back samples at their corners. The native export handles each limb independently and pins corners again at the native resolution.

Run `check_turnaround.py <8k-jar>` and `check_master.py <front-master> <8k-jar>`. The Turnaround shader gallery rotates each equipped stand through front, side and rear. For side and unobstructed rear captures the QA fixture hides the replacement tail mount, then restores it for a second rear screenshot. This display control is confined to the disposable test client. Use `turnaround_compare.py <front-master> <side-back-master> <screenshots> <output-folder>` for the six-view comparison sheets.

## Equipped-player tail and waist check

Add `--player-motion` to the isolated shader client invocation to equip the local player with all four pieces of each set. The test uses normal forward and crouch inputs during a 90-degree turn. It records displacement, torso pitch, appendage rotation changes and the mount's torso parent and local coordinates during movement. The QA camera orbits the player while the renderer continues to use the actual player's pose. The test leaves all v3 tail mounts visible.

Run `qa/player_gallery.py <screenshots> <fresh-output-directory>` to assemble the three-angle sheets and animated motion sequences. Keep the raw frames and generated sheets private. This test covers flat-ground traversal in an isolated client.

## Full shoulder fit

Pass `--full-shoulders` to the native exporter before merging the v3 tails. Each pad is centered on its own upper-arm pivot, six model pixels wide and deep, with a three- or four-pixel height and the per-set bevel. The exporter reallocates all six UV faces against those dimensions. The merge tool adjusts base clearance for the matching geometry. Leggings and boots have different shell depths to keep the join from flickering. The fitted helmet and upper sleeves accommodate the raised outer skin layer.

The player gallery installs a conspicuous test skin locally, takes an unarmored control, then captures the equipped character from all sides and above the shoulders. The private fixture leaves all tail roots visible.

For this fitted merge, pass the Forge 1.12.2 universal JAR as the final argument after ASM. The client coverage hook temporarily omits the vanilla skin cubes under each covered part and restores the original geometry and display list after the player draw. It keeps bone transforms available to item layers. Complete leg coverage applies when both leggings and boots are worn. Run `qa/skin_check.py <motion-screenshots>` to count fluorescent test colors in the sampled poses and verify the unarmored positive control.

For any flagged pixels, repeat the player-motion run in a fresh folder with `--neutral-skin`. This keeps the unarmored control fluorescent and switches the equipped player's base and outer skin textures to gray. Pass `--neutral-control <second-screenshots>` to the color check to verify whether the same colors persist at the same pixel coordinates with the gray skin.
