"""Build a metadata-only art worklist from current source and 49 pinned local JARs.

No third-party image, model body, class, world, or private player payload is exported.
Run: python tools/build_icon_handoff.py --reference-dir <original mods directory>
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "collaboration/sonnet-art"


def read(path):
    return path.read_text(encoding="utf-8-sig")


def strings(value):
    return re.findall(r'"([^"\n]*)"', value)


def dimensions(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return list(struct.unpack(">II", data[16:24]))


def title(key):
    return key.replace("_", " ").title()


def category(key, ns):
    if ns != "valeorders": return "Village standards and world utilities"
    if key.startswith("technique_") and key != "technique_codex": return "Jutsu technique icons"
    if key.startswith("scroll_") and key != "scroll_actor_glyph": return "Jutsu lesson scrolls"
    if key.endswith("_charged"): return "Charged weapon animation strips"
    if key.startswith("focus_") and key != "focus_talisman": return "Focus garments"
    if key.startswith("outfit_"): return "Outfits"
    if key.startswith("chronicle_"): return "Chronicles and achievements"
    if key.startswith("kitchen_"): return "Kitchen ingredients, meals, medicines and stations"
    if key.endswith("_codex") or key == "resonance_vow": return "Codices and resonance"
    if key.endswith(("_helmet", "_chestplate", "_leggings", "_boots")): return "Armor inventory icons"
    if key.endswith("_eye"): return "Eyes"
    if key.startswith("form_"): return "Transformation tokens"
    if key.startswith("summon_"): return "Summoning tokens"
    if key in {"trail_biscuit", "herb_broth", "sunrice_bowl", "river_skewer", "root_stew", "cloud_tea", "dried_berries", "smoked_mushrooms", "orchard_flatbread", "seared_fillet", "spice_curry", "travel_ration"}: return "Field food and drinks"
    if key in WEAPONS or re.match(r'^(ember|current|gale|flint|bloom|rime|storm|dusk|dawn)_(saber|spear|dagger|glaive|staff|axe)$', key): return "Weapons"
    return "Tools, training, puppets and consumables"


WEAPONS = set(re.findall(r'\("([a-z_]+)",\s*"', read(ROOT / "modules/vale-orders/src/main/java/village/vale/arsenal/ArsenalSpec.java")))


def source_inventory():
    files = [ROOT / p for p in subprocess.check_output(["rg", "--files", "modules"], cwd=ROOT, text=True).splitlines()]
    models, textures, labels = {}, {}, {}
    for p in files:
        rel = p.relative_to(ROOT).as_posix()
        if p.name == "en_us.lang":
            for line in read(p).splitlines():
                if "=" in line:
                    k, v = line.split("=", 1); labels[k] = v
        if "/assets/" not in rel: continue
        asset = rel.split("/assets/", 1)[1]
        if "/models/" in asset and p.suffix == ".json":
            ns, rest = asset.split("/models/", 1)
            models[ns + ":" + rest[:-5]] = (rel, json.loads(read(p)))
        if "/textures/" in asset and p.suffix == ".png":
            ns, rest = asset.split("/textures/", 1)
            metadata = Path(str(p) + ".mcmeta")
            textures[ns + ":" + rest[:-4]] = {"path": rel, "asset_path": "assets/" + asset, "size": dimensions(p.read_bytes()), "animation": json.loads(read(metadata)).get("animation") if metadata.exists() else None}
    for p in files:
        if p.name in {"OriginalOutfits.java", "OriginalFocusGear.java", "CarrierProfiles036.java"}:
            for k, v in re.findall(r'new (?:Spec|AbilityCarriers036.Profile)\("([a-z_]+)",\s*"([^"]+)"', read(p)):
                labels["item.valeorders." + k + ".name"] = v
    def resolve(key, seen=()):
        if key in seen: raise ValueError("Model cycle: " + key)
        if key not in models: return {}
        d = models[key][1]
        result = resolve(d.get("parent", ""), seen + (key,))
        result.update(d.get("textures", {}))
        return result
    rows = []
    for full, (path, model) in sorted(models.items()):
        if ":item/" not in full: continue
        ns, key = full.split(":item/")
        name = labels.get(f"item.{ns}.{key}.name", title(key))
        if key.startswith("chronicle_"): name = title(key[10:]) + " Chronicle"
        if key.endswith("_charged"): name = labels.get(f"item.{ns}.{key[:-8]}.name", title(key[:-8])) + " (charged)"
        refs = resolve(full)
        layer = refs.get("layer0")
        status = "existing dedicated texture"
        if not layer: status = "3D inventory model; separate flat icon optional"
        elif layer.startswith("minecraft:"): status = "vanilla placeholder"
        elif layer != f"{ns}:items/{key}": status = "shared placeholder or shared family art"
        if layer and not layer.startswith("minecraft:") and layer not in textures:
            raise ValueError("Missing source PNG: " + layer)
        dims = textures.get(layer, {}).get("size", [32, 32])
        proposed = f"assets/{ns}/textures/items/{key}.png"
        notes = []
        if status.startswith("shared") or status.startswith("vanilla"): notes.append("Point model to the new per-item texture; adding a PNG alone is insufficient.")
        if key.endswith("_codex") and key != "technique_codex" or key == "resonance_vow": notes.append("Check AbilityCarriers036.registerRenderers, which explicitly binds minecraft:book, as well as ClientModels.")
        if not layer: notes.append("Preserve the current 3D held model; GUI-only flat art needs a separate model/perspective binding.")
        if key.endswith("_charged"): notes.append("Keep existing .png.mcmeta: two 32x32 frames stacked vertically, frametime 5, interpolate false.")
        rows.append({"id": ns + ":" + key, "name": name, "category": category(key, ns), "model": path, "current_textures": refs, "status": status, "current_size": dims if layer else None, "deliver_png": proposed, "deliver_size": dims if layer and status == "existing dedicated texture" else [32, 32], "notes": notes})
    other = [dict(v, resource_id=k) for k, v in sorted(textures.items()) if ":items/" not in k]
    block_models = [{"id": k, "source": p, "current_textures": resolve(k)} for k, (p, _) in sorted(models.items()) if ":block/" in k]
    profiles = json.loads(read(ROOT / "modules/vale-encounters/src/main/resources/encounter-profiles.json"))
    return rows, other, block_models, profiles, textures


def hud_inventory():
    base = ROOT / "modules/vale-orders/src/main/java/village/vale"
    arts = read(base / "Arts.java")
    orders = re.findall(r'\("([a-z]+)",\s*"([^"]+)",\s*0x[0-9a-f]+,\s*"([^"]+)"', read(base / "Order.java"))
    matrix = re.search(r'String\[\]\[\] titles=\{(.*?)\};', arts).group(1)
    rows = []
    for (key, ward, _), names in zip(orders, re.findall(r'\{([^}]+)\}', matrix)):
        for suffix, name in zip(("lance", "fan", "seal"), strings(names)):
            rows.append({"id": "art/" + key + "_" + suffix, "name": name, "category": "Codex jutsu actions"})
    for enum, name in re.findall(r'starter\(Order\.([A-Z]+),"([^"]+)"', arts):
        next(r for r in rows if r["id"] == "art/" + enum.lower() + "_lance")["name"] = name
    for filename, keyvar, titlevar in [("LightningKit.java", "KEYS", "TITLES"), ("Arts.java", "keys", "names")]:
        src = read(base / filename)
        keys = strings(re.search(r'\b' + keyvar + r'=\{(.*?)\}', src).group(1))
        names = strings(re.search(r'\b' + titlevar + r'=\{(.*?)\}', src).group(1))
        rows.extend({"id": "art/" + k, "name": n, "category": "Codex jutsu actions"} for k, n in zip(keys, names))
    rows.extend({"id": "order/" + k, "name": skill, "category": "Ward skills"} for k, _, skill in orders)
    roles = [("saber", "Patient Guard"), ("spear", "Long Thrust"), ("dagger", "Sidestep"), ("glaive", "Fan Cut"), ("staff", "Centering Breath"), ("axe", "Crushing Strike")]
    rows.extend({"id": "weapon_role/" + k, "name": n, "category": "Weapon role symbols (shared across nine wards)"} for k, n in roles)
    rows.extend({"id": "status/" + k, "name": n, "category": "Status effects"} for k, n in [("bastion", "Bastion Form"), ("zephyr", "Zephyr Form"), ("oracle", "Oracle Form"), ("eclipse", "Eclipse Form"), ("lightning_chakra_armor", "Lightning Chakra Armor")])
    seal = read(ROOT / "modules/field-hud/src/main/java/village/fieldhud/SealArt.java")
    rows.extend({"id": "seal/" + k, "name": n or title(k), "category": "Existing procedural HUD seals"} for k, n in re.findall(r'\("([a-z]+)","([^"]*)",0x', seal))
    for row in rows:
        row["deliver_png"] = "assets/fieldhud/textures/icons/" + row["id"] + ".png"
        row["size"] = [32, 32]
        row["status"] = "new proposed PNG path; renderer wiring required"
    return rows


def reference_inventory(directory):
    expected = json.loads(read(ROOT / "design/reference-mod-inventory.json"))
    result = []
    for record in expected["jars"]:
        p = directory / record["file"]
        if hashlib.sha256(p.read_bytes()).hexdigest() != record["sha256"]: raise ValueError("Reference hash mismatch: " + p.name)
        items, textures, labels = [], [], {}
        with zipfile.ZipFile(p) as z:
            names = z.namelist()
            for n in names:
                if n.lower().endswith("/en_us.lang"):
                    for line in z.read(n).decode("utf-8-sig", errors="replace").splitlines():
                        if "=" in line:
                            k, v = line.split("=", 1); labels[k] = v
            for n in names:
                if not n.startswith("assets/"): continue
                if n.endswith(".png"):
                    with z.open(n) as f: size = dimensions(f.read(24))
                    textures.append({"path": n, "size": size, "animated": n + ".mcmeta" in names})
                if "/models/item/" in n and n.endswith(".json"):
                    ns, key = n[7:].split("/models/item/", 1); key = key[:-5]
                    matches = [v for k, v in labels.items() if k in (f"item.{ns}.{key}.name", f"item.{ns}:{key}.name", f"item.{key}.name", f"tile.{key}.name", f"tile.{ns}.{key}.name")]
                    try: d = json.loads(z.read(n))
                    except (ValueError, UnicodeError): d = {}
                    items.append({"model_id": ns + ":item/" + key, "label": matches[0] if matches else title(key), "label_source": "localization" if matches else "filename-derived; verify in game", "path": n, "parent": d.get("parent"), "texture_references": d.get("textures", {}), "override_models": [o.get("model") for o in d.get("overrides", [])]})
        result.append({"jar": p.name, "sha256": record["sha256"], "declared_mods": record["declared_mods"], "items": sorted(items, key=lambda r:r["model_id"]), "textures": sorted(textures, key=lambda r:r["path"])})
    return result


def write(name, lines):
    (OUT / name).write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8", newline="\n")


def build(directory):
    OUT.mkdir(parents=True, exist_ok=True)
    items, other, blocks, actors, textures = source_inventory()
    hud = hud_inventory()
    references = reference_inventory(directory)
    counts = {"current_item_model_slots": len(items), "current_base_item_models": sum(not r["id"].endswith("_charged") for r in items), "charged_weapon_strips": sum(r["id"].endswith("_charged") for r in items), "current_item_statuses": dict(Counter(r["status"] for r in items)), "current_item_categories": dict(Counter(r["category"] for r in items)), "hud_art_slots_requiring_wiring": len(hud), "existing_non_item_pngs": len(other), "source_block_models": len(blocks), "encounter_profiles": len(actors), "reference_jars_verified": len(references), "reference_item_model_entries": sum(len(r["items"]) for r in references), "reference_png_entries": sum(len(r["textures"]) for r in references)}
    manifest = {"schema": "vale.sonnet-art-inventory/v1", "source_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(), "scope": "Static current working-source resources plus exact pinned 49-JAR resource metadata. Model entries include alternatives and are not runtime registry counts. Proposed HUD and portrait paths are not implemented.", "counts": counts, "current_items": items, "hud": hud, "current_non_item_textures": other, "current_block_models": blocks, "encounter_profiles": [{k:r[k] for k in ("key", "title", "kind")} for r in actors], "references": references}
    (OUT / "asset-inventory.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    lines = ["CURRENT VALE: COMPLETE ITEM / INVENTORY ART CHECKLIST", "", "Each checkbox is one model/visual state. The 26 charged strips are alternate states, not extra weapons.", "PNG sizes are pixels. Use transparent RGBA. Existing dedicated art is included for the requested full redesign.", "For shared icons, the output path is proposed and the model needs rewiring. Preserve registry IDs.", ""]
    for cat in sorted(counts["current_item_categories"]):
        group = [r for r in items if r["category"] == cat]
        lines += [f"{cat.upper()} ({len(group)})", ""]
        for r in group:
            lines += [f"[ ] {r['name']} | {r['id']}", f"    PNG: {r['deliver_png']} | {r['deliver_size'][0]}x{r['deliver_size'][1]} | {r['status']}"]
            lines += ["    " + note for note in r["notes"]]
        lines += [""]
    write("01-CURRENT-ITEM-ICONS.txt", lines)
    lines = ["CHAKRA, JUTSU ACTIONS AND HUD SYMBOLS", "", "These action/status/seal symbols are separate from the lesson-scroll and technique item icons in file 01.", "All paths below are PROPOSED; current HUD often draws shared seals in Java rather than loading individual PNGs.", "Supply 32x32 transparent RGBA; verify legibility at 16x16 display. A 128x128 editable master is optional.", "The 49 lesson/technique motifs in file 01 can be reused for HUD views; do not invent another 49 designs.", ""]
    for cat in sorted(set(r["category"] for r in hud)):
        lines += [cat.upper(), ""]
        for r in hud:
            if r["category"] == cat: lines += [f"[ ] {r['name']} | {r['id']} | {r['deliver_png']}"]
        lines += [""]
    lines += ["ADDITIONAL CHAKRA / INTERFACE ART DIRECTION (proposed, not an existing asset contract)", "[ ] Chakra/resolve resource symbol (the current internal name is resolve).", "[ ] Health, stamina, hunger, experience, mission merit, level/rank and currency symbols where the owning screen exposes them.", "[ ] Fire, Water, Wind, Earth and Lightning chakra nature symbols; reuse the corresponding Ember/Current/Gale/Flint/Storm seal art where appropriate.", "[ ] Ready, active, cooldown, locked, unlearned, mastered, insufficient chakra, no valid target and unavailable states; prefer renderer tint/overlay variants to duplicate art.", "[ ] Mission available/active/complete/failed; quest giver; shop; party; ally/enemy; destination; waypoint; dungeon entrance; boss; summon; empty/selected hotbar slots.", "These extra UI concepts have no counted fixed file roster yet. Confirm actual screen wiring before making per-state files.", "", "INTEGRATION NOTES", "JutsuRail.icon uses actual item art for weapons/projectiles/forms/summons, and SealArt/SealDraw for other actions.", "Forms currently select vanilla potion icon indices. LightningKit has no custom status sprite binding.", "AbilityCarriers036 explicitly assigns minecraft:book in its renderer registration; reconcile that with ClientModels before installing codex art.", "Armor-specific actions may reuse their armor/profile motif; see OriginalArmorLoadoutProvider and ArmorSnapshot before adding new standalone slots."]
    write("02-CHAKRA-JUTSU-HUD.txt", lines)
    lines = ["FULL ART PASS: NON-ICON TEXTURES, WORLD MODELS AND ACTORS", "", "These are not flat inventory icons. Preserve UV layouts, animation metadata and existing dimensions.", "A flat PNG does not replace a worn armor model, entity model, animation or procedural effect.", "", "EXISTING NON-ITEM PNGS", ""]
    for r in other: lines += [f"[ ] {r['asset_path']} | {r['size'][0]}x{r['size'][1]}"]
    lines += ["", "WORLD/BLOCK MODEL ART CONTRACTS (current texture references)", ""]
    for r in blocks: lines += [f"[ ] {r['id']} | " + ", ".join(f"{k}={v}" for k,v in r['current_textures'].items())]
    lines += ["", "ENCOUNTER CHARACTER ROSTER: 136 profiles, not 136 existing portrait files", "All currently share the vanilla Steve/biped render path. Unique skins/models/portraits require renderer routing.", "For optional portraits use assets/valeencounters/textures/portraits/<profile_key>.png (proposed), 64x64 RGBA.", "Do not reuse a portrait PNG as a Minecraft skin; author skin/UV assets against the selected model.", ""]
    for r in actors: lines += [f"[ ] {r['title']} | {r['key']} | {r['kind']}"]
    lines += ["", "OTHER ART WORK", "[ ] Residents: guide, clerk and merchant roles currently use vanilla villager textures; bespoke skins and portrait mappings require code.", f"[ ] The {counts['current_item_categories']['Focus garments']} focus garments and {counts['current_item_categories']['Outfits']} outfit pieces need worn-material/UV designs in addition to their item icons; current code uses vanilla armor textures.", "[ ] Nine original armor profiles: preserve original_roster.json attachment geometry/UVs; use each profile's existing atlas, layer and shader-map dimensions above.", "[ ] Hand-technique and weapon VFX: inspect current renderers and JutsuMath/WeaponMath before proposing sprite sheets. Many effects are procedural geometry and particles.", "[ ] Full entity motions, weapon poses, armor attachments, chakra cloaks and dungeon environmental art are separate deliverables; the icon checklist does not complete them."]
    write("03-TEXTURES-MODELS-ACTORS.txt", lines)
    lines = ["ORIGINAL 49-MOD PACK: COMPLETE STATIC ITEM-MODEL NAME INDEX", "", "All 49 JAR hashes match design/reference-mod-inventory.json. Names only: no third-party artwork or implementation exported.", "These are reference observations, NOT proof of registration, unique item count, current Vale equivalence or missing art.", "Alternate/pulling/stage models can be repeated states of one item; do not add these counts to the Vale count as distinct designs.", "A readable name derived from a filename is marked [filename]; confirm uncertain labels in game. Preserve spelling in technical paths.", "Use this index to retain all requested Naruto content; create original designs and keep explicit mapping decisions.", ""]
    for r in references:
        lines += [f"{r['jar']} ({len(r['items'])} item model entries; {len(r['textures'])} PNG assets)"]
        if not r["items"]: lines += ["  No standalone JSON item models found; inspect the texture index for UI/atlas art. This does not establish that the mod has no items."]
        for a in r["items"]: lines += [f"[ ] {a['label']} | {a['model_id']}" + (" [filename]" if a["label_source"].startswith("filename") else "")]
        lines += [""]
    write("04-REFERENCE-49-MOD-ITEMS.txt", lines)
    lines = ["ORIGINAL 49-MOD PACK: COMPLETE TOP-LEVEL assets/ PNG INDEX", "", "Filenames and canvas dimensions only. Includes item sprites, armor/skins, GUI atlases, particles, blocks, logos and library examples.", "An atlas may contain many symbols; its regions are not enumerated here. Nested library archives/dynamically generated resources are outside this static scan.", "Not every dependency logo or existing working texture needs replacement. Prioritize game content, then deliberately choose UI reskins.", "Original 3D models and UV contracts must be reviewed before replacing skin/armor texture files. Do not copy source artwork.", ""]
    for r in references:
        lines += [f"{r['jar']} ({len(r['textures'])} PNGs)"]
        for a in r["textures"]:
            size = "x".join(map(str,a["size"])) if a["size"] else "non-PNG header; review"
            lines += [f"[ ] {a['path']} | {size}" + (" | animated .mcmeta exists" if a["animated"] else "")]
        lines += [""]
    write("05-REFERENCE-49-MOD-TEXTURES.txt", lines)
    prompt = ["SONNET HANDOFF: COMPLETE HIDDEN BLOCK VILLAGE / VALE ART PASS", "", "OWNER REQUEST", "Create a cohesive original Naruto-inspired art set for EVERYTHING in the supplied mod project: weapons, lesson scrolls, technique icons, chakra and nature symbols, eyes, transformations, summoning, armor, outfits, food, medicines, tools, achievements, world utilities and HUD. Include existing assets in the redesign, not only absent PNGs.", "", "READ THESE ATTACHMENTS", "01-CURRENT-ITEM-ICONS.txt: exact current item names, IDs, output filenames, sizes and wiring notes.", "02-CHAKRA-JUTSU-HUD.txt: action/status/seal icons and proposed chakra/UI art.", "03-TEXTURES-MODELS-ACTORS.txt: existing non-icon PNGs, block model contracts and full encounter roster.", "04-REFERENCE-49-MOD-ITEMS.txt: every JSON item-model name found across the 49 pinned original mods.", "05-REFERENCE-49-MOD-TEXTURES.txt: all top-level assets/ PNG names and sizes across those mods.", "asset-inventory.json: exact machine-readable lists, provenance, model references and counts.", "", "VERIFIED STATIC COUNTS", *[f"{k}: {v}" for k,v in counts.items() if not isinstance(v,dict)], "", "HOW TO EXECUTE", "1. Read the current list and original reference index. Build a coverage manifest before drawing. Current and reference entries overlap; do not silently double-count them or invent one-to-one mappings.", "2. Preserve exact registry/save IDs and filenames. Public display names can be Naruto-facing when behavior supports them. Rasengan uses spiral_palm and Chidori uses storm_lance; deliver both their scroll_ and technique_ files using those stable keys.", "3. Produce ORIGINAL art: strong silhouettes, readable at a 16-pixel on-screen icon, consistent outline weight/palette/lighting, transparent RGBA PNG. No baked background, border, label text, watermark or contact-sheet grid inside individual icons.", "4. Default inventory export is 32x32. Editable 128x128 masters are welcome. Keep each existing charged weapon at 32x64: two 32x32 frames stacked vertically, existing animation frametime 5/interpolate false. A sheet preview is supplementary; deliver individual files too.", "5. Make every weapon visually identifiable. Lesson scroll and cast-technique pairs share a motif but have different silhouettes (physical scroll versus technique symbol). Nature families should be distinguishable by shape as well as color. Eyes, modes, summons and chakra effects should communicate their specific identity.", "6. Preserve existing armor/entity UVs and texture sizes. Inventory armor icons, worn armor textures, attachment atlases, normal/specular/emissive maps and 3D models are different jobs. Never replace a skin with a square icon. Avoid painting over third-party art; create fresh assets.", "7. Work in manageable named batches and mark every row pending/completed/needs-design/needs-wiring. Deliver real PNGs when image/filesystem tooling is available. If only text is available, provide executable generation code or per-asset art prompts and honestly mark unrendered files; do not claim files exist.", "8. For shared placeholders, create unique PNGs and supply a separate model-binding patch proposal. HUD and status paths in file 02 are proposed and require renderer changes. Preserve 3D held weapon geometry when adding GUI icons. Do not silently change gameplay, saves, live mod JARs or the owner's running client.", "9. Do not treat all 49 dependency mods as needing fresh logos. Their metadata is included for complete coverage; prioritize Naruto/gameplay assets and flag infrastructure/UI choices separately. Do not claim complete runtime coverage from this static index: metadata variants, GUI atlas regions, generated resources and private NPC skins can require another pass.", "10. Return an assets/ folder, editable masters if available, labeled previews, a per-file manifest with dimensions/status/source-ID and a ZIP. List all unresolved design mappings and wiring work. No third-party artwork/JARs/world/player data in the output bundle.", "", "START WITH THESE BATCHES", "A. All 49 technique motifs and their 49 lesson scrolls, including Rasengan and Chidori.", "B. 81 weapon base entries (54 ward weapons + 27 arsenal weapons), then 26 charged-state strips; Crescent Talons currently uses 3D geometry.", "C. Chakra/nature/action/status icons, seven eyes, four form tokens, three summon tokens, all codices.", "D. Armor, focus garments, outfits, tools, food, medicines, chronicles and world utilities.", "E. Worn textures/models, actors, world art, UI and the remaining unmapped original-pack content.", "", "SCOPE", "This package is an art specification and static inventory, not a new gameplay build or proof every reference mechanic has been implemented. Existing source work and the user's live sessions remain untouched."]
    write("START-HERE-SONNET.txt", prompt)
    names = ["EVERY INDEXED ICON / ITEM NAME: CURRENT PROJECT + ORIGINAL 49 MODS", "", "Read START-HERE-SONNET.txt for instructions. Current and reference lists overlap; do not add them as unique designs.", "Current model slots include charged alternatives. Original model entries may include GUI, pulling and other alternatives.", "", "CURRENT VALE ITEM MODELS"]
    for cat in sorted(counts["current_item_categories"]):
        names += ["", cat.upper()]
        names += [f"[ ] {r['name']} | {r['id']} | {r['deliver_png'].split('/')[-1]}" for r in items if r["category"] == cat]
    names += ["", "CURRENT ACTION / STATUS / HUD ART (new PNG wiring required)"]
    names += [f"[ ] {r['name']} | {r['id']}" for r in hud]
    names += ["", "ORIGINAL 49-MOD REFERENCE ITEM MODELS (not a current Vale mapping)"]
    for r in references:
        names += ["", r["jar"]]
        names += [f"[ ] {a['label']} | {a['model_id']}" for a in r["items"]]
        if not r["items"]: names += ["No top-level JSON item-model entries; see texture index for GUI/atlas resources."]
    write("ALL-ICON-NAMES.txt", names)
    print(json.dumps(counts, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference-dir", required=True, type=Path)
    build(parser.parse_args().reference_dir)
