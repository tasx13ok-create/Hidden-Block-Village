"""Extract a focused equipment art supplement from pinned reference metadata.

Reads class names/inheritance and literal resource/name strings, never executes
reference bytecode. Exports names, asset paths and sizes; no artwork or code.
"""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "collaboration/sonnet-art"
WEAPON = re.compile(r"sword|blade|kunai|shuriken|senbon|scythe|staff|spear|sickle|axe|hammer|shield|bow|gun|glaive|dagger|mace|trident|flail|halberd|katana|katar|kukri|kniv|knife|musket|claw|cleaver|tonfa|kusarigama|fan|rapier|saber|sai$|ishiken|kurosawa|nodati|aikitechu|shakujo|samehada|hiramekarei|kubikiribocho|nuibari|kabutowari|kiba|excalibur|macuahuitl|slingshot|grenade|batton|baseball_bat|hockey_stick|golf_club|crowbar|lead_pipe", re.I)
GARMENT = re.compile(r"armor|armour|helmet|boots|chest|leggings|pants|trousers|skirt|cloak|cloack|robe|jacket|vest|mask|hood|headband|bandana|protector|hat$|_head$|_legs$|_bottom$|crown|necklace|wings|belt|gourd", re.I)
EXCLUDE = re.compile(r"^scroll_|^item_scroll_|^icon_|^advancement_|^spell_|^placeholder_|^element_|^ingot_|^weapon_rack$|^violin_bow$|^broken_arrow$|^crossbow_bolt$|^bullet$", re.I)
REVIEWED_WEAPONS = {"ahznbswordsplus": {"infiniteshurikan"},
    "narutomod": {"adamantine_nyoi", "ashbones", "asuracanon", "black_receiver", "bone_drill", "explosive_tag", "gaunlet", "gauntlet_thrown", "poisonbomb", "smoke_bomb", "sound_gaunlet"},
    "narutomodaddononero": {"nunoboko", "umbrella"}, "variedcommodities": {"magic_wand", "broken_bottle", "pipe_wrench", "wrench"}}
ROOT_TYPES = {"ItemArmor": "Armor / wearables", "ItemSword": "Weapons", "ItemBow": "Weapons", "ItemShield": "Weapons", "ItemAxe": "Weapons"}


def class_metadata(raw):
    stream = io.BytesIO(raw)
    def take(n):
        value = stream.read(n)
        if len(value) != n: raise ValueError("Truncated class metadata")
        return value
    def u(n): return int.from_bytes(take(n), "big")
    if u(4) != 0xCAFEBABE: raise ValueError("Invalid class metadata")
    take(4); count = u(2); pool = [None]; tags = [0]
    while len(pool) < count:
        tag = u(1)
        if tag == 1: value = take(u(2)).decode("utf-8", errors="replace")
        elif tag in (3, 4): value = take(4)
        elif tag in (5, 6): take(8); pool.extend([None, None]); tags.extend([tag, 0]); continue
        elif tag in (7, 8, 16, 19, 20): value = u(2)
        elif tag in (9, 10, 11, 12, 17, 18): value = take(4)
        elif tag == 15: value = take(3)
        else: raise ValueError("Unknown constant-pool tag")
        pool.append(value); tags.append(tag)
    u(2); name = pool[pool[u(2)]]; parent = u(2)
    return {"name": name, "parent": pool[pool[parent]] if parent else "",
            "strings": {pool[v] for t, v in zip(tags, pool) if t == 8}}


def normalize(value): return re.sub(r"[^a-z0-9]", "", re.sub(r"\u00a7.", "", value).casefold())


def build(directory):
    original = json.loads((OUT / "asset-inventory.json").read_text(encoding="utf-8"))
    classes, jars = {}, []
    for ref in original["references"]:
        path = directory / ref["jar"]
        if hashlib.sha256(path.read_bytes()).hexdigest() != ref["sha256"]: raise ValueError("Reference hash changed: " + ref["jar"])
        local, labels = {}, {}
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if info.filename.endswith(".class"):
                    if info.file_size > 8 * 1024**2: raise ValueError("Class metadata exceeds bound")
                    row = class_metadata(archive.read(info)); local[row["name"]] = row; classes[row["name"]] = row
                elif info.filename.lower().endswith("/en_us.lang"):
                    for line in archive.read(info).decode("utf-8-sig", errors="replace").splitlines():
                        if "=" in line and not line.startswith("#"):
                            key, value = line.split("=", 1); labels[key] = re.sub(r"\u00a7.", "", value).strip()
        jars.append((ref, local, labels))
    def category(name, seen=None):
        seen = set() if seen is None else seen
        if name in seen: return None
        seen.add(name)
        if name.startswith("net/minecraft/item/") and name.rsplit("/", 1)[-1] in ROOT_TYPES: return ROOT_TYPES[name.rsplit("/", 1)[-1]]
        return category(classes[name]["parent"], seen) if name in classes else None
    current = {normalize(r["name"]) for r in original["current_items"]}
    equipment, corrections, worn, scans = [], [], [], []
    for ref, local, labels in jars:
        kinds, groups = {}, {}
        for name, row in local.items():
            outer = name.split("$")[0]; groups.setdefault(outer, set()).update(row["strings"])
            kind = category(name)
            if kind: kinds[outer] = kind
        all_strings = set().union(*(c["strings"] for c in local.values())) if local else set()
        for model in ref["items"]:
            ns, key = model["model_id"].split(":item/")
            if EXCLUDE.search(key) or ns == "travelersbackpack": continue
            matches = [name for name in kinds if key in groups[name] or ns + ":" + key in groups[name]]
            kind = next((kinds[name] for name in matches if kinds[name] == "Armor / wearables"), kinds[matches[0]] if matches else None)
            if ns == "hvbijuu": kind = "Armor / wearables"
            registrar = "noppes/vc/VCWeapons" if ns == "variedcommodities" else ""
            if key in groups.get(registrar,set()) or key in REVIEWED_WEAPONS.get(ns,set()): kind = "Weapons"
            if ns == "variedcommodities" and key in groups.get("noppes/vc/VCArmors",set()): kind = "Armor / wearables"
            if not kind and WEAPON.search(key): kind = "Weapons"
            if not kind and GARMENT.search(key): kind = "Armor / wearables"
            if not kind: continue
            if ns in {"armourers_workshop", "geckolib3"}: kind = "Equipment infrastructure / examples"
            row = {"jar": ref["jar"], "model_id": model["model_id"], "name": model["label"].strip(), "category": kind,
                   "classification": "item inheritance and literal resource match" if matches else "reviewed resource-name family",
                   "class_names": matches, "already_in_reference_appendix": True, "already_named_in_current_list": normalize(model["label"]) in current,
                   "model_path": model["path"], "texture_references": model["texture_references"], "override_models": model["override_models"], "label_source": model["label_source"]}
            equipment.append(row)
        namespaces = {i["model_id"].split(":")[0] for i in ref["items"]}
        model_keys = {i["model_id"].split(":item/")[1] for i in ref["items"]}
        for translation, name in labels.items():
            if not (translation.startswith("item.") and translation.endswith(".name") and name): continue
            key = translation[5:-5]
            if key in model_keys or not (WEAPON.search(key) or GARMENT.search(key)): continue
            if key not in all_strings and not any(ns + ":" + key in all_strings for ns in namespaces): continue
            owners = [c for c, strings in groups.items() if key in strings or any(ns + ":" + key in strings for ns in namespaces)]
            linked = [i["model_id"] for i in ref["items"] if any(i["model_id"].split(":item/")[1] in groups[c] or i["model_id"].replace(":item/", ":") in groups[c] for c in owners)]
            exact = [i for i in linked if normalize(i.split(":item/")[1]) == normalize(key)]
            if exact: linked = exact
            corrections.append({"jar": ref["jar"], "name": name, "translation_key": translation, "related_model_ids": sorted(set(linked)),
                                "scope": "localized name/base item missing as a standalone model entry; not an additional confirmed runtime item"})
        armor_strings = set().union(*(groups[c] for c in kinds if kinds[c] == "Armor / wearables")) if any(k == "Armor / wearables" for k in kinds.values()) else set()
        for texture in ref["textures"]:
            path = texture["path"]; ns, rest = path[7:].split("/", 1); resource = ns + ":" + rest
            if resource in armor_strings or re.search(r"/(?:armor|armour)/|_layer_[12](?:_overlay)?\.png$", path):
                worn.append({"jar": ref["jar"], **texture, "scope": "worn texture, separate from square inventory icons"})
        scans.append({"jar": ref["jar"], "sha256": ref["sha256"], "classes_checked": len(local), "language_entries": len(labels)})
    for correction in corrections:
        for row in equipment:
            if row['jar'] == correction['jar'] and row['model_id'] in correction['related_model_ids']:
                row['previous_label'] = row['name']; row['name'] = correction['name']
                if len(correction['related_model_ids']) > 1: row['name'] += " (form " + row['model_id'].rsplit('_',1)[1] + ")"
                row['label_source'] = 'localized base name matched through item class resource metadata'
                row['already_named_in_current_list'] = normalize(row['name']) in current
    equipment.sort(key=lambda r:(r["category"],r["jar"].casefold(),r["model_id"]))
    missing = [r for r in equipment if not r["already_named_in_current_list"] and r["category"] != "Equipment infrastructure / examples"]
    counts = {"jars_verified":len(scans),"equipment_model_states":len(equipment),"not_named_in_current_vale_list":len(missing),
              "new_localized_names_or_base_aliases":len(corrections),"new_model_paths_beyond_first_bundle":0,"worn_texture_paths":len(worn),"categories":dict(Counter(r["category"] for r in equipment))}
    document = {"schema":"vale.equipment-art-supplement/v1","counts":counts,"scope":"Static equipment coverage. All model rows already existed in the full reference appendix; this is a focused supplement to the current Vale list, not a claim of newly discovered hundreds of runtime items.",
                "limitations":["Class/resource metadata does not execute registration or enumerate NBT-generated variants.","Unused localization-only armor slots are not promoted to real items.","Private Armourers Workshop skins and CustomNPC equipment can add custom appearances; not covered by JAR metadata.","Generic Vale names do not establish character-art parity or one-to-one mappings."],"equipment":equipment,"name_corrections":corrections,"worn_textures":worn,"scans":scans}
    def write(name, lines): (OUT/name).write_text("\n".join(lines)+"\n",encoding="utf-8",newline="\n")
    write("06-ARMOR-AND-WEAPONS.txt",["SONNET: REFERENCE ARMOR AND WEAPONS NOT NAMED IN THE CURRENT VALE CHECKLIST","",
        f"{len(missing)} model/visual-state rows. Alternate, thrown, blocking and extended forms are NOT extra unique weapons.",
        "These appeared in the full original-pack reference appendix, but were not named in the current Vale art checklist. No automatic generic-Vale-to-character mapping is claimed.",
        "Each needs a coverage decision for its original inventory icon AND recognizable held/worn appearance. Preserve the reference ID for tracking; do not invent a production registry ID.","",*sum(([f"{category} — {jar}",*[f"[ ] {r['name']} | {r['model_id']}" for r in missing if r['category']==category and r['jar']==jar],""] for category,jar in sorted({(r['category'],r['jar']) for r in missing})),[])])
    write("07-EQUIPMENT-NAME-CORRECTIONS.txt",["EXACT EXTRA HUMAN-READABLE NAMES / BASE-ITEM ALIASES","",
        "The first index enumerated model JSON files. These localized names/base items have different filenames or multiple state models. They are corrections to existing art jobs, not nine extra confirmed items.","",
        *sum(([f"[ ] {r['name']} | {r['jar']}","    Name key: "+r['translation_key'],"    Existing related models: "+", ".join(r['related_model_ids']),""] for r in corrections),[])])
    write("08-WORN-ARMOR-AND-WEAPON-ART.txt",["SEPARATE WORK: WORN ARMOR, HELD WEAPONS AND ALTERNATE FORMS","",
        "A new square icon does not complete a character's outfit or a weapon's held appearance.",
        "For each weapon in 06: original icon; original first/third-person held texture/model; each listed blocking/thrown/extended form; effect layers where the design calls for them.",
        "For each wearable in 06: original item icon per actual piece; worn skin/armor texture using the actual model's UVs; slim/normal compatibility when supported; mode/cloak/wing attachments separately.",
        "Existing worn texture paths below were previously present only in the broad PNG appendix. Sizes are canvas dimensions, not new icon export sizes. Review UVs; do not resize a worn texture to 32x32.","",
        *[f"[ ] {r['jar']} | {r['path']} | {'x'.join(map(str,r['size'])) if r['size'] else 'inspect dimensions'}" for r in worn]])
    write("SONNET-EQUIPMENT-ADDENDUM.txt",["ADD THIS TO THE ORIGINAL SONNET ART HANDOFF","",
        "The owner wants every character outfit/armor and every weapon across the supplied pack, including actual worn armor and held weapons.",
        "Read 06-ARMOR-AND-WEAPONS.txt for the focused reference gear not named in the current Vale checklist; 07 for corrected real display names/base items; 08 for additional worn/held-art work; equipment-inventory.json for exact provenance and duplicate flags.",
        "Do not assume Bracken Coat, generic focus lenses or generic arsenal names already reproduce Naruto character equipment. Make explicit coverage/mapping decisions. Do not invent a one-to-one mapping or extra armor slots.",
        "Most character equipment was already in files 04/05 of the earlier full bundle. This supplement makes it actionable and prevents missed characters; deduplicate by source JAR + model ID, not display name. Distinct mods can have separate swords with the same display name.",
        "Unused generated language strings such as eye-boots are not proof those items exist. Private custom skins and runtime NBT variants remain a separate inspection task, not fabricated checklist entries.",
        "Create original art, readable icons, individual PNGs, UV-correct worn textures, held-model assets where needed, labeled previews and a per-ID completion manifest. Do not copy supplied third-party artwork or change gameplay/registry IDs.","",json.dumps(counts,indent=2)])
    (OUT/"equipment-inventory.json").write_text(json.dumps(document,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    assert len({(r['jar'],r['model_id']) for r in equipment})==len(equipment)
    assert all(not r['already_named_in_current_list'] for r in missing)
    print(json.dumps(counts,indent=2))


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--reference-dir",type=Path,required=True)
    build(parser.parse_args().reference_dir)
