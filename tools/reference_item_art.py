#!/usr/bin/env python3
"""Make a PRIVATE 1.12.2 resource pack from pinned source models and owner PNGs.

Only generated/handheld sprite contracts are eligible. Source JARs are never
modified or extracted. Original model JSON is copied only into private output.
"""
import argparse
from collections import Counter, defaultdict
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import struct
import zipfile

NAMESPACE = "vale_reference_art"
GENERATED = {"builtin/generated", "minecraft:builtin/generated"}
MODEL_KEYS = {"parent", "textures", "display", "overrides", "ambientocclusion", "gui_light", "credit", "__comment", "name"}
CUSTOM_MARKERS = (b"getTileEntityItemStackRenderer", b"setTileEntityItemStackRenderer", b"ItemStackTileEntityRenderer", b"IPerspectiveAwareModel", b"ICustomModelLoader")

class PackError(RuntimeError):
    pass

class Skip(PackError):
    def __init__(self, reason, detail=""):
        self.reason, self.detail = reason, detail
        super().__init__(reason + (": " + detail if detail else ""))

def digest(data):
    return hashlib.sha256(data).hexdigest()

def file_digest(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise PackError("Duplicate JSON key: " + key)
        result[key] = value
    return result

def finite(value):
    if isinstance(value, float) and not math.isfinite(value):
        raise PackError("Nonfinite JSON number")
    if isinstance(value, dict):
        for item in value.values(): finite(item)
    elif isinstance(value, list):
        for item in value: finite(item)
    return value

def document(data):
    if len(data) > 8 * 1024 * 1024:
        raise PackError("Oversized JSON")
    return finite(json.loads(data.decode("utf-8-sig"), object_pairs_hook=unique_pairs))

def path_ok(name):
    if not name or len(name) > 512 or "\\" in name or "\x00" in name or name.startswith("/") or ":" in name:
        raise PackError("Unsafe ZIP path: " + repr(name))
    if any(part in ("", ".", "..") for part in name.split("/")):
        raise PackError("Noncanonical ZIP path: " + name)
    return name

def resource(value):
    if not isinstance(value, str) or len(value) > 240:
        raise Skip("invalid_resource_reference")
    namespace, separator, name = value.partition(":")
    if not separator: namespace, name = "minecraft", namespace
    if not re.fullmatch(r"[a-z0-9_.-]+", namespace) or not re.fullmatch(r"[A-Za-z0-9_./-]+", name):
        raise Skip("invalid_resource_reference", value)
    if any(part in ("", ".", "..") for part in name.split("/")):
        raise Skip("invalid_resource_reference", value)
    return namespace + ":" + name

def model_path(identifier):
    namespace, name = resource(identifier).split(":", 1)
    return "assets/" + namespace + "/models/" + name + ".json"

def texture_path(identifier):
    namespace, name = resource(identifier).split(":", 1)
    return "assets/" + namespace + "/textures/" + name + ".png"

class Sources:
    def __init__(self):
        self.archives = []
        self.entries = defaultdict(list)
        self.folded = defaultdict(set)
        self.models = {}
        self.input_pins = []

    def add(self, path, expected=None, label=None):
        path = Path(path)
        if not path.is_file() or path.is_symlink():
            raise PackError("Missing/nonregular source JAR: " + str(path))
        actual = file_digest(path)
        if expected is not None and actual != expected:
            raise PackError("Source JAR hash mismatch: " + path.name)
        archive = zipfile.ZipFile(path)
        self.archives.append(archive)
        seen = set()
        for info in archive.infolist():
            if info.is_dir() or not info.filename.startswith("assets/"):
                continue
            name = path_ok(info.filename)
            if name in seen:
                raise PackError("Duplicate source resource ZIP member: " + name)
            seen.add(name)
            if (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise PackError("Source resource symlink: " + name)
            self.entries[name].append((label or path.name, archive, info))
            self.folded[name.casefold()].add(name)
        self.input_pins.append({"file": label or path.name, "sha256": actual, "bytes": path.stat().st_size})

    def read(self, name, maximum=512 * 1024):
        path_ok(name)
        matches = self.entries.get(name, [])
        folded = self.folded.get(name.casefold(), set())
        if len(folded) > 1:
            raise Skip("source_case_collision", name)
        if not matches:
            raise Skip("source_case_mismatch" if folded else "missing_source_resource", name)
        if len(matches) != 1:
            raise Skip("ambiguous_resource_owner", name)
        label, archive, info = matches[0]
        if info.file_size > maximum:
            raise Skip("oversized_source_resource", name)
        data = archive.read(info)  # zipfile verifies this member's CRC.
        return data, label

    def model(self, name):
        if name not in self.models:
            data, owner = self.read(name)
            try: value = document(data)
            except (PackError, ValueError, RecursionError) as error:
                raise Skip("invalid_model_json", name + ": " + str(error))
            if not isinstance(value, dict): raise Skip("invalid_model_json", name)
            self.models[name] = value, owner, digest(data)
        return self.models[name]

    def close(self):
        for archive in self.archives: archive.close()



def external_model_uses(sources):
    """Keep shared parents/world draws, including references in lenient JSON.

    Valid documents distinguish item override targets (safe to patch separately)
    from parent/world dependencies. For malformed or huge documents, scan every
    quoted/unquoted resource-like token conservatively. No native lenient parser
    behavior or malformed third-party content is repaired or copied by this scan.
    """
    used, uncertain_namespaces = set(), set()
    for path in sorted(sources.entries):
        if not path.endswith(".json") or not ("/models/" in path or "/blockstates/" in path):
            continue
        namespace = path.split("/")[1]
        def consider(value):
            try:
                candidate = resource(value)
                if model_path(candidate) in sources.entries:
                    used.add(candidate)
            except PackError:
                pass
        try:
            raw, _ = sources.read(path, 64 * 1024 * 1024)
            try:
                value = document(raw)
            except (PackError, ValueError, RecursionError):
                text = raw.decode("utf-8-sig")
                for match in re.finditer(r'"(?:[^"\\]|\\.)*"|[A-Za-z0-9_./:-]+', text):
                    token = match.group(0)
                    if token.startswith('"'):
                        try: token = json.loads(token)
                        except ValueError:
                            uncertain_namespaces.add(namespace)
                            continue
                    consider(token)
                continue
        except (PackError, UnicodeError):
            uncertain_namespaces.add(namespace)
            continue
        def visit(node):
            if isinstance(node, dict):
                for key, value in node.items():
                    if (key == "parent" or (key == "model" and ("/blockstates/" in path or "/models/block/" in path))) and isinstance(value, str):
                        consider(value)
                    visit(value)
            elif isinstance(node, list):
                for value in node: visit(value)
        visit(value)
    return used, uncertain_namespaces


def sprite_contract(sources, identifier):
    chain, models, seen = [], [], set()
    current = resource(identifier)
    while current not in GENERATED:
        if current in seen or len(chain) >= 24:
            raise Skip("parent_cycle_or_depth", current)
        seen.add(current)
        namespace, name = current.split(":", 1)
        if name.startswith("block/"):
            raise Skip("block_parent", current)
        if name.startswith("builtin/"):
            raise Skip("custom_builtin_renderer", current)
        value, owner, sha = sources.model(model_path(current))
        if "elements" in value:
            raise Skip("explicit_or_inherited_3d_geometry", current)
        unknown = set(value) - MODEL_KEYS
        if unknown:
            raise Skip("custom_model_contract", ",".join(sorted(unknown)))
        for key in ("textures", "display"):
            if key in value and not isinstance(value[key], dict): raise Skip("invalid_model_" + key, current)
        if "overrides" in value and not isinstance(value["overrides"], list): raise Skip("invalid_model_overrides", current)
        chain.append({"id": current, "owner": owner, "source_sha256": sha})
        models.append(value)
        parent = value.get("parent")
        if not isinstance(parent, str): raise Skip("missing_generated_parent", current)
        current = resource(parent)
    textures = {}
    for value in reversed(models):
        textures.update(value.get("textures", {}))
    layers = [key for key in textures if re.fullmatch(r"layer\d+", key)]
    if layers != ["layer0"] and set(layers) != {"layer0"}:
        raise Skip("multiple_or_missing_sprite_layers", ",".join(sorted(layers)))
    value, aliases = textures["layer0"], set()
    while isinstance(value, str) and value.startswith("#"):
        key = value[1:]
        if key in aliases or len(aliases) >= 24: raise Skip("texture_alias_cycle")
        aliases.add(key)
        if key not in textures: raise Skip("missing_texture_alias", key)
        value = textures[key]
    path = texture_path(value)
    source_png, owner = sources.read(path, 16 * 1024 * 1024)
    png_size(source_png)
    metadata = None
    if path + ".mcmeta" in sources.entries or sources.folded.get((path + ".mcmeta").casefold()):
        raw, _ = sources.read(path + ".mcmeta")
        try: metadata = document(raw)
        except (ValueError, PackError): raise Skip("invalid_texture_metadata", path)
        if not isinstance(metadata, dict): raise Skip("invalid_texture_metadata", path)
        if "animation" in metadata: raise Skip("animated_texture_retained", path)
        if set(metadata) - {"texture"}: raise Skip("custom_texture_metadata_retained", path)
    # Keep all predicate/override structure. Refuse unresolved state targets.
    for model in models:
        for override in model.get("overrides", []):
            if not isinstance(override, dict) or not isinstance(override.get("predicate"), dict) or not isinstance(override.get("model"), str):
                raise Skip("invalid_model_override")
            sources.read(model_path(override["model"]))
    return chain, path, metadata


def png_size(data):
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise PackError("Invalid PNG header")
    width, height = struct.unpack(">II", data[16:24])
    if not (1 <= width <= 16384 and 1 <= height <= 16384): raise PackError("PNG dimensions out of bounds")
    return width, height


def patch_model(original, texture):
    result = deepcopy(original)
    result.setdefault("textures", {})["layer0"] = texture
    for key, value in original.items():
        if key != "textures" and result[key] != value:
            raise PackError("Model state changed outside layer0")
    for key, value in original.get("textures", {}).items():
        if key != "layer0" and result["textures"][key] != value:
            raise PackError("Model texture state changed outside layer0")
    return result


def archive_icons(path, provenance):
    entries = [row for row in provenance["archives"] if row["name"] == Path(path).name]
    if len(entries) != 1 or file_digest(path) != entries[0]["sha256"]:
        raise PackError("Owner art archive identity mismatch")
    members = {row["path"]: row for row in entries[0]["members"]}
    if len(members) != len(entries[0]["members"]): raise PackError("Duplicate art manifest member")
    archive = zipfile.ZipFile(path)
    names = [row.filename for row in archive.infolist() if not row.is_dir()]
    if len(names) != len(set(names)) or len(names) != len({name.casefold() for name in names}):
        raise PackError("Duplicate/case-colliding art ZIP member")
    for name in names: path_ok(name)
    if set(names) != set(members): raise PackError("Art archive/member manifest mismatch")
    return archive, members


def build(inventory, equipment, art_archive, art_provenance, mods_dir, minecraft_jar, output):
    output = Path(output)
    if output.resolve().is_relative_to(Path(mods_dir).resolve()): raise PackError("Output cannot be inside original mods directory")
    if output.exists(): raise PackError("Output must be a new directory")
    rows = [(reference, row) for reference in inventory["references"] for row in reference["items"]]
    expected = inventory.get("counts", {}).get("reference_item_model_entries", len(rows))
    if len(rows) != expected: raise PackError("Reference inventory count mismatch")
    model_ids = [row["model_id"] for reference, row in rows]
    if len(model_ids) != len(set(model_ids)): raise PackError("Duplicate reference model ID")
    protected = {"minecraft", "valeorders", "fieldhud", "hvdungeonpictures"}
    protected_models = set()
    for row in inventory.get("current_items", []):
        identifier = row.get("id", "")
        if ":" in identifier:
            namespace, name = identifier.split(":", 1)
            protected_models.add(resource(namespace + ":item/" + name))
    sources, icons = Sources(), None
    results, payload, applied_rgba = [], {}, []
    equipment_rows = {(row["jar"], row["model_id"]): row for row in equipment.get("equipment", [])}
    try:
        for reference in inventory["references"]:
            sources.add(Path(mods_dir) / reference["jar"], reference["sha256"], reference["jar"])
        sources.add(minecraft_jar, label="minecraft-1.12.2-client")
        if any(path.startswith("assets/" + NAMESPACE + "/") for path in sources.entries):
            raise PackError("Owned output namespace already exists in source stack")
        used_models, uncertain_namespaces = external_model_uses(sources)
        icons, members = archive_icons(art_archive, art_provenance)
        named = [row["model_id"].replace(":", "_").replace("/", "_") + ".png" for reference, row in rows]
        if len(named) != len(set(named)): raise PackError("Model-to-art filename collision")
        for (reference, row), icon_name in zip(rows, named):
            result = {"jar": reference["jar"], "model_id": row["model_id"], "model_path": row["path"], "art_member": icon_name}
            try:
                if row["path"] != model_path(row["model_id"]): raise Skip("inventory_model_path_mismatch")
                if row["model_id"].split(":", 1)[0] in protected or row["model_id"] in protected_models: raise Skip("current_project_model_preserved")
                if icon_name not in members: raise Skip("missing_exact_owner_icon", icon_name)
                icon = icons.read(icon_name)
                if digest(icon) != members[icon_name]["sha256"]: raise PackError("Owner PNG member hash mismatch: " + icon_name)
                if png_size(icon) != (32, 32): raise Skip("owner_icon_not_32px")
                original, owner, sha = sources.model(row["path"])
                if owner != reference["jar"]: raise Skip("inventory_jar_owner_mismatch", owner)
                gear = equipment_rows.get((reference["jar"], row["model_id"]), {})
                for name in gear.get("class_names", []):
                    # Direct custom item renderers need a separate runtime contract.
                    archive = next(a for a in sources.archives if Path(a.filename).name == reference["jar"])
                    if name + ".class" in archive.namelist():
                        raw = archive.read(name + ".class")
                        if any(marker in raw for marker in CUSTOM_MARKERS): raise Skip("direct_custom_item_renderer_retained", name)
                chain, old_texture, metadata = sprite_contract(sources, row["model_id"])
                if row["model_id"] in used_models: raise Skip("shared_parent_or_world_model_retained")
                if row["model_id"].split(":", 1)[0] in uncertain_namespaces: raise Skip("unreadable_model_dependency_namespace_retained")
                token = hashlib.sha256((row["model_id"] + "|" + members[icon_name]["sha256"]).encode()).hexdigest()[:24]
                texture = NAMESPACE + ":items/icon_" + token
                path = texture_path(texture)
                patched = patch_model(original, texture)
                encoded = json.dumps(patched, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
                if row["path"] in payload or path in payload: raise PackError("Duplicate output resource path")
                payload[row["path"]], payload[path] = encoded, icon
                if metadata: payload[path + ".mcmeta"] = json.dumps(metadata, separators=(",", ":")).encode()
                applied_rgba.append(members[icon_name].get("rgba_sha256"))
                result.update(status="applied", source_model_sha256=sha, source_texture_retained=old_texture,
                              output_texture=path, output_model_sha256=digest(encoded), art_sha256=digest(icon),
                              rgba_sha256=members[icon_name].get("rgba_sha256"), parent_chain=chain,
                              original_parent_display_predicates_preserved=True)
            except Skip as skip:
                result.update(status="skipped", reason=skip.reason, detail=skip.detail)
            results.append(result)
        if not payload: raise PackError("No supported flat model overrides")
        payload["pack.mcmeta"] = json.dumps({"pack": {"pack_format": 3, "description": "Vale - owner v3 reference flat icons (private)"}}, separators=(",", ":")).encode()
        if len(payload) != len({name.casefold() for name in payload}): raise PackError("Output case collision")
        output.mkdir(parents=True, exist_ok=False)
        pack = output / "Vale-Reference-Flat-Icons-v3.zip"
        with zipfile.ZipFile(pack, "x", zipfile.ZIP_DEFLATED) as archive:
            for name in sorted(payload):
                info = zipfile.ZipInfo(path_ok(name), (2026, 11, 13, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, payload[name])
        with zipfile.ZipFile(pack) as archive:
            if archive.testzip() is not None or set(archive.namelist()) != set(payload): raise PackError("Output ZIP verification failed")
            for name, data in payload.items():
                if archive.read(name) != data: raise PackError("Output ZIP member changed")
        # Recheck source JARs after assembly; this command never writes them.
        for pin, path in zip(sources.input_pins, [Path(mods_dir) / r["jar"] for r in inventory["references"]] + [Path(minecraft_jar)]):
            if file_digest(path) != pin["sha256"]: raise PackError("Source JAR changed during mapping: " + pin["file"])
        counts = Counter(row.get("reason", "applied") for row in results)
        noninfra = [row for row in equipment.get("equipment", []) if row.get("category") != "Equipment infrastructure / examples"]
        report = {"schema": 1, "scope": "PRIVATE_PINNED_REFERENCE_FLAT_ITEM_ART_PACK_ONLY", "game_launched": False,
                  "reference_jars_verified": len(inventory["references"]), "reference_model_rows": len(rows),
                  "applied_models": counts["applied"], "skipped_models": len(rows) - counts["applied"],
                  "dispositions": dict(sorted(counts.items())), "applied_unique_rgba": len(set(applied_rgba)),
                  "equipment_scope": {"raw_model_states": len(equipment.get("equipment", [])), "excluding_infrastructure": len(noninfra),
                                      "claimed_base_equipment_601": "classification-dependent claim; not a runtime registry count"},
                  "protected_project_namespaces": sorted(protected), "protected_exact_project_models": sorted(protected_models), "new_texture_namespace": NAMESPACE,
                  "original_jars_and_textures_unchanged": True, "custom_geometry_worn_entity_block_assets_replaced": False,
                  "model_state_changed_only_at_layer0": True, "source_pins": sources.input_pins,
                  "dependency_scan_unreadable_namespaces": sorted(uncertain_namespaces),
                  "inventory_canonical_json_sha256": digest(json.dumps(inventory, sort_keys=True, separators=(",", ":")).encode()),
                  "equipment_canonical_json_sha256": digest(json.dumps(equipment, sort_keys=True, separators=(",", ":")).encode()),
                  "art_provenance_canonical_json_sha256": digest(json.dumps(art_provenance, sort_keys=True, separators=(",", ":")).encode()),
                  "mapper_sha256": file_digest(__file__),
                  "art_archive_sha256": file_digest(art_archive), "pack": {"file": pack.name, "bytes": pack.stat().st_size, "sha256": file_digest(pack), "entries": len(payload)},
                  "rows": results}
        (output / "coverage.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        lines = ["# Private reference flat-icon pack", "", str(counts["applied"]) + " supported model states overridden; " + str(len(rows) - counts["applied"]) + " retained/skipped across " + str(len(rows)) + " reference model rows.", "", "| Disposition | Model rows |", "| --- | ---: |"]
        lines += ["| " + reason + " | " + str(count) + " |" for reason, count in sorted(counts.items())]
        lines += ["", "Only owner v3 PNGs and private copies of supported original item-model transforms are in the ZIP. Original parent/display/override predicates remain unchanged; every new sprite has an owned unique path. No source JAR, block/worn/entity texture or custom3D geometry was replaced. This is static mapping/ZIP validation, not a graphical acceptance or all1772 runtime-appearance claim.", "", "SHA256: `" + report["pack"]["sha256"] + "`. Do not publish this private pack: it includes observed third-party model JSON."]
        (output / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
        return report
    finally:
        sources.close()
        if icons is not None: icons.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("inventory", "equipment", "art-archive", "art-provenance", "mods-dir", "minecraft-jar", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    report = build(document(args.inventory.read_bytes()), document(args.equipment.read_bytes()), args.art_archive,
                   document(args.art_provenance.read_bytes()), args.mods_dir, args.minecraft_jar, args.output)
    print(json.dumps({k: report[k] for k in ("reference_model_rows", "applied_models", "skipped_models", "dispositions", "pack")}, indent=2))

if __name__ == "__main__":
    main()
