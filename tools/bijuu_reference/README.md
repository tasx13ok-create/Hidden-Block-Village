# Bijuu armor 4K reference edition

The owner supplied a nine-outfit reference sheet and requested a close visual match, 4K textures and shader response. The edition preserves the armor model shape and puts the detail into the texture.

The painter rectifies visible helmet and chest surfaces from the larger reference figures. It samples the outfit thumbnails for sleeve, leg and boot patterns. Hidden faces continue adjacent colors and trim. The reference has scene lighting and overlapping effects and does not provide clean UVs for every face. The original image and comparisons that contain parts of it stay local.

The vanilla export contains 18 RGBA color textures at **4096 × 2048**, 36 LabPBR maps at the same dimensions, and 36 distinct **32 × 32** item icons. It uses the standard 64 × 32 Minecraft 1.12.2 armor UV proportions and binary alpha. Comparison sheets show the reference alongside front, side, back and three-quarter offline views.

The private JAR exporter adapts the colors to the original mod’s **4096 × 4096** atlases. It validates the exact 0.16.2-rc1 input and checks all 273 entries after export. Only 101 texture PNGs change. Original model definitions, code, registry, tail physics and configurations remain byte-identical. The mod ID and version remain hvbijuu / 0.16.2-rc1.

Use [LabPBR](https://shaderlabs.org/wiki/LabPBR_Material_Standard) in BSL’s material format with Advanced Materials active and OptiFine’s Normal Map and Specular Map toggles on. The albedo carries the pictured shading. The maps vary surface gloss and add restrained highlights. The height field is flat. See [BSL’s official FAQ](https://capttatsu.com/bslshaders/faqs.html).

## Rebuild

Requires Python 3.12 and Pillow. Use a fresh output directory:

    py -3.12 tools/bijuu_reference/build.py <owner-reference.png> <output-directory>
    py -3.12 tools/bijuu_reference/native.py <output-directory> <original-0.16.2-rc1.jar> <new-jar>

The PNG sheet must be the original 828 × 1792 image. The build report records its SHA-256 and exact RGB sample positions. The generator checks binary alpha, UV bounds, stored dimensions, icon color retention at 16 pixels, archive CRC and extracted bytes.

The isolated Windows Forge check uses installed launcher libraries, Java 8 and a separate Java compiler:

    py -3.12 tools/bijuu_reference/qa/client.py --install <launcher-install> --java <java8.exe> --javac <javac.exe> --artifact <new-jar> --output <fresh-trial-directory>

Supply the optional **--optifine** and **--bsl** inputs to render the isolated gallery. The probe is separate from the deliverable JAR and uses a synthetic singleplayer world.

Share original source, documentation and safe verification metadata in Git. Keep the input and derived JARs, owner artwork, rendered comparisons and local screenshots private.
