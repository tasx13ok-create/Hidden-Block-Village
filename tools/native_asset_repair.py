#!/usr/bin/env python3
"""Prepare PRIVATE, parent-only native model repairs from pinned owner inputs.

No image, sound or class member is read or copied. Original model JSON appears
only inside the explicitly private output pack. Nothing is installed or enabled.
The output remains held until real ModelBlock and runtime registry gates pass.
"""
from __future__ import annotations

import argparse
from collections import OrderedDict, defaultdict
import hashlib
import json
import math
from pathlib import Path
import re
import zipfile

MAX_MANIFEST = 2 * 1024 * 1024
MAX_JSON = 8 * 1024 * 1024
MAX_LOG = 32 * 1024 * 1024
MAX_ENTRIES = 250000
MAX_CANDIDATES = 512
MAX_DEPTH = 64
SPECIAL_PARENTS = {
    "minecraft:builtin/generated", "minecraft:builtin/entity",
    "minecraft:builtin/missing", "minecraft:item/generated",
    "minecraft:item/handheld", "minecraft:item/handheld_rod",
}


class Refuse(RuntimeError):
    pass


class Skip(Refuse):
    def __init__(self, reason, detail=""):
        self.reason, self.detail = reason, detail
        super().__init__(reason + (": " + detail if detail else ""))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def pairs(values):
    result = {}
    for key, value in values:
        if key in result:
            raise Refuse("Duplicate JSON field: " + key)
        result[key] = value
    return result


def document(raw, limit=MAX_JSON):
    if len(raw) > limit:
        raise Refuse("JSON exceeds explicit byte bound")
    def constant(_):
        raise Refuse("Nonfinite JSON number")
    result = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=pairs,
                        parse_constant=constant)
    def finite(node, depth=0):
        if depth > 128:
            raise Refuse("JSON nesting exceeds bound")
        if isinstance(node, float) and not math.isfinite(node):
            raise Refuse("Nonfinite JSON number")
        if isinstance(node, dict):
            for value in node.values(): finite(value, depth + 1)
        elif isinstance(node, list):
            for value in node: finite(value, depth + 1)
    finite(result)
    return result


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":"), allow_nan=False).encode("utf-8")


def json_bytes(value):
    return (json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False,
                       allow_nan=False) + "\n").encode("utf-8")


def asset_path(value):
    if not isinstance(value, str) or not value or len(value) > 512:
        raise Refuse("Invalid resource member path")
    if value.startswith("/") or "\\" in value or ":" in value or "\x00" in value:
        raise Refuse("Unsafe resource member path")
    if any(part in ("", ".", "..") for part in value.split("/")):
        raise Refuse("Noncanonical resource member path")
    if any(ord(c) < 32 for c in value):
        raise Refuse("Control character in resource path")
    return value


def resource(value):
    """Mirror the examined 1.12 ResourceLocation case/default rules, not 1.13 IDs.

    Old exported parent names can contain spaces/apostrophes. They may be proven
    absent, but never become output member names. Native proof verifies the exact
    parsed parent too. No whitespace is silently stripped.
    """
    if not isinstance(value, str) or not value or len(value) > 240:
        raise Skip("invalid_resource_reference")
    value = value.lower()
    if value.count(":") > 1:
        raise Skip("invalid_resource_reference")
    namespace, colon, name = value.partition(":")
    if not colon: namespace, name = "minecraft", namespace
    if not re.fullmatch(r"[a-z0-9_.-]+", namespace):
        raise Skip("invalid_resource_namespace")
    try: asset_path(name)
    except Refuse as error: raise Skip("invalid_resource_path", str(error))
    return namespace + ":" + name


def model_path(identifier):
    namespace, name = resource(identifier).split(":", 1)
    return "assets/" + namespace + "/models/" + name + ".json"


def model_id(member):
    match = re.fullmatch(r"assets/([^/]+)/models/(.+)\.json", member)
    if not match: raise Refuse("Not a model member")
    return resource(match.group(1) + ":" + match.group(2))


def texture_path(identifier):
    namespace, name = resource(identifier).split(":", 1)
    return "assets/" + namespace + "/textures/" + name + ".png"


def special_parent(identifier):
    namespace, name = resource(identifier).split(":", 1)
    # Do not mistake generated/entity models or unexamined virtual loaders for
    # an ordinary absent JSON parent merely because no physical member exists.
    return identifier in SPECIAL_PARENTS or name.startswith("builtin/") or namespace == "forge"


def remove_parent_bytes(raw):
    """Remove one root parent member; every other original byte survives exactly."""
    obj = document(raw)
    if not isinstance(obj, dict) or not isinstance(obj.get("parent"), str):
        raise Skip("missing_or_nonstr_parent")
    # Byte positions differ with UTF-8; retain a character-to-byte boundary table.
    text = raw.decode("utf-8-sig")
    bom = b"\xef\xbb\xbf" if raw.startswith(b"\xef\xbb\xbf") else b""
    decoder = json.JSONDecoder(object_pairs_hook=pairs)
    def ws(index):
        while index < len(text) and text[index] in " \r\n\t": index += 1
        return index
    start = ws(0)
    if text[start:start + 1] != "{": raise Skip("model_not_object")
    i, members = ws(start + 1), []
    while text[i:i + 1] != "}":
        begin = i
        key, i = decoder.raw_decode(text, i)
        if not isinstance(key, str): raise Refuse("Object key is not a string")
        i = ws(i)
        if text[i:i + 1] != ":": raise Refuse("Missing JSON colon")
        _, end = decoder.raw_decode(text, ws(i + 1))
        members.append((key, begin, end))
        i = ws(end)
        if text[i:i + 1] == "}": break
        if text[i:i + 1] != ",": raise Refuse("Missing JSON comma")
        i = ws(i + 1)
    selected = [n for n, row in enumerate(members) if row[0] == "parent"]
    if len(selected) != 1: raise Refuse("Exactly one root parent required")
    n = selected[0]
    if n + 1 < len(members): left, right = members[n][1], members[n + 1][1]
    elif n: left, right = members[n - 1][2], members[n][2]
    else: left, right = members[n][1], members[n][2]
    patched = bom + (text[:left] + text[right:]).encode("utf-8")
    expected = dict(obj); expected.pop("parent")
    if document(patched) != expected:
        raise Refuse("Parent-only semantic preservation failed")
    left_bytes = len(bom) + len(text[:left].encode("utf-8"))
    right_bytes = len(bom) + len(text[:right].encode("utf-8"))
    if raw[:left_bytes] + raw[right_bytes:] != patched:
        raise Refuse("Original retained byte ranges were changed")
    return patched, [left_bytes, right_bytes]


class Sources:
    def __init__(self, rows):
        self.rows, self.archives = rows, []
        self.entries, self.folded = defaultdict(list), defaultdict(set)
        self.cache = OrderedDict()
        self.entry_count = 0
        labels = set()
        try:
            for row in rows:
                if set(row) != {"label", "path", "sha256", "kind"}:
                    raise Refuse("Exact archive fields required")
                if row["label"] in labels: raise Refuse("Duplicate archive label")
                labels.add(row["label"])
                path = Path(row["path"])
                if not path.is_file() or path.is_symlink() or file_sha(path) != row["sha256"]:
                    raise Refuse("Missing, symbolic or changed pinned archive: " + row["label"])
                archive = zipfile.ZipFile(path); self.archives.append(archive)
                own = set()
                for info in archive.infolist():
                    if info.is_dir() or not info.filename.startswith("assets/"): continue
                    name = asset_path(info.filename)
                    if name in own: raise Refuse("Duplicate ZIP resource member")
                    own.add(name)
                    if (info.external_attr >> 16) & 0o170000 == 0o120000:
                        raise Refuse("ZIP resource symlink")
                    self.entries[name].append((row, archive, info))
                    self.folded[name.casefold()].add(name)
                    self.entry_count += 1
                    if self.entry_count > MAX_ENTRIES:
                        raise Refuse("Resource entry count exceeds bound")
        except BaseException:
            self.close(); raise

    def close(self):
        for archive in self.archives: archive.close()

    def unique(self, member):
        asset_path(member)
        folded = self.folded.get(member.casefold(), set())
        if len(folded) > 1: raise Skip("resource_case_collision", member)
        rows = self.entries.get(member, [])
        if not rows: raise Skip("resource_case_mismatch" if folded else "missing_resource", member)
        if len(rows) != 1: raise Skip("ambiguous_resource_owner", member)
        return rows[0]

    def read(self, member):
        row, archive, info = self.unique(member)
        if info.file_size > MAX_JSON: raise Skip("oversize_json", member)
        return archive.read(info), row

    def model(self, identifier, overlay=None):
        if overlay and identifier in overlay: return document(overlay[identifier])
        member = model_path(identifier)
        if member in self.cache:
            self.cache.move_to_end(member); return self.cache[member]
        raw, _ = self.read(member); obj = document(raw)
        if not isinstance(obj, dict): raise Skip("model_not_object", identifier)
        self.cache[member] = obj
        while len(self.cache) > 4: self.cache.popitem(last=False)
        return obj

    def unchanged(self):
        return all(file_sha(Path(row["path"])) == row["sha256"] for row in self.rows)


def available_texture(sources, textures, key):
    visited = set()
    while key.startswith("#"):
        key = key[1:]
        if key in visited or len(visited) >= MAX_DEPTH: raise Skip("texture_alias_cycle")
        visited.add(key)
        if key not in textures: raise Skip("inherited_or_undefined_texture_key", key)
        key = textures[key]
        if not isinstance(key, str): raise Skip("nonstr_texture")
    if resource(key) in {"minecraft:missingno", "minecraft:missing"}:
        raise Skip("missing_texture_placeholder")
    row, _, info = sources.unique(texture_path(key))
    if info.file_size == 0: raise Skip("empty_original_texture")
    result = {"id": resource(key), "archive": row["label"], "archive_sha256": row["sha256"],
            "entry": texture_path(key), "zip_crc32": "%08x" % info.CRC,
            "bytes": info.file_size, "pixel_payload_read_or_copied": False}
    metadata = texture_path(key) + ".mcmeta"
    if metadata in sources.entries or sources.folded.get(metadata.casefold()):
        raw, _ = sources.read(metadata)
        obj = document(raw)
        if not isinstance(obj, dict): raise Skip("invalid_texture_metadata")
        result["metadata_sha256"] = sha(raw)
        result["animation_declared"] = "animation" in obj
    return result


def own_texture_closure(sources, model):
    elements, textures = model.get("elements"), model.get("textures")
    if not isinstance(elements, list) or not elements: raise Skip("no_own_nonempty_geometry")
    if not isinstance(textures, dict) or not textures: raise Skip("no_own_nonempty_textures")
    if len(elements) > 10000 or len(textures) > 512: raise Skip("explicit_geometry_or_texture_count_bound")
    if any(not isinstance(k, str) or not isinstance(v, str) for k, v in textures.items()):
        raise Skip("invalid_texture_map")
    references = {"#" + key for key in textures}
    for element in elements:
        if not isinstance(element, dict) or not isinstance(element.get("faces"), dict):
            raise Skip("unexamined_element_contract")
        for face in element["faces"].values():
            if not isinstance(face, dict) or not isinstance(face.get("texture"), str):
                raise Skip("invalid_face_texture")
            if not face["texture"].startswith("#"):
                raise Skip("unexamined_direct_face_texture_contract")
            references.add(face["texture"])
    result = {}
    for reference in sorted(references):
        found = available_texture(sources, textures, reference)
        result[found["id"]] = found
    return [result[key] for key in sorted(result)]


def parent_closure(sources, identifier, overlay=None, trail=()):
    if identifier in trail or len(trail) >= MAX_DEPTH: raise Skip("model_parent_cycle_or_depth", identifier)
    if special_parent(identifier): return {"terminal": identifier, "special": True}
    model = sources.model(identifier, overlay)
    if "loader" in model: raise Skip("custom_loader_model", identifier)
    if "parent" not in model: return {"terminal": identifier, "special": False}
    return parent_closure(sources, resource(model["parent"]), overlay, trail + (identifier,))


class ContextGraph:
    """All incoming static JSON contexts, including shared parents and overrides.

    The graph comes from the real Gson/ResourceLocation/variant parser. This is
    stronger than filename-only closure, but remains separate from callback ABI
    bindings and a real model bake. Unknown incoming contexts are rejected.
    """
    def __init__(self, sources, pin, archive_rows):
        self.sources = sources
        self.pin = pin
        self.data = document(pin_read(pin, 32 * 1024 * 1024), 32 * 1024 * 1024)
        if self.data.get("scope") != "ACTUAL_GSON_RESOURCELOCATION_AND_NATIVE_BLOCKSTATE_METADATA_GRAPH":
            raise Refuse("Actual native metadata graph required")
        expected = {x["label"]: x["sha256"] for x in archive_rows}
        actual = {x["label"]: x["sha256"] for x in self.data["archives"]}
        if actual != expected or not self.data["source_archives_unchanged"]:
            raise Refuse("Native graph source archive identities differ")
        self.models, self.reverse, self.block_roots = defaultdict(list), defaultdict(set), set()
        for row in self.data["definitions"]:
            if row["kind"] == "model":
                self.models[row["id"]].append(row)
                if row.get("parent"): self.reverse[row["parent"]].add(row["id"])
                for target in row["overrides"]: self.reverse[target].add(row["id"])
            elif row["kind"] == "blockstate": self.block_roots.update(row["models"])

    def node(self, identifier):
        rows = self.models.get(identifier, [])
        if len(rows) != 1: raise Skip("unknown_or_ambiguous_incoming_model", identifier)
        row = rows[0]
        if row["custom_loader"]: raise Skip("custom_loader_incoming_context", identifier)
        raw, owner = self.sources.read(row["entry"])
        if owner["label"] != row["provider"] or sha(raw) != row["original_json_sha256"]:
            raise Refuse("Native graph model bytes drifted")
        return row

    def chain(self, root, repaired):
        result, seen, current = [], set(), root
        while True:
            if current in seen or len(seen) >= MAX_DEPTH: raise Skip("incoming_parent_cycle_or_depth", root)
            if special_parent(current): raise Skip("unexamined_builtin_incoming_context", current)
            seen.add(current); row = self.node(current); result.append(row)
            if current == repaired or not row.get("parent"): return result
            current = row["parent"]

    def opaque_references(self, affected):
        """An unreadable source may not silently hide a cross-namespace edge.

        Invalid vanilla JSON cannot provide a valid inheritance edge. Nevertheless
        scan its original bounded bytes for every affected ID; escaped references
        are held because this fallback cannot decode them with authority.
        """
        literals = [x.encode("utf-8") for x in affected]
        checked = []
        for error in self.data["errors"]:
            member = error["entry"]; row, archive, info = self.sources.unique(member)
            if info.file_size > 64 * 1024 * 1024: raise Skip("unexamined_large_incoming_source", member)
            tail = b""; found = False; escaped = False
            overlap = max([len(x) for x in literals] + [8])
            with archive.open(info) as stream:
                while True:
                    block = stream.read(1024 * 1024)
                    if not block: break
                    data = (tail + block).lower()
                    if any(value in data for value in literals): found = True
                    if b"\\u" in data or b"\\/" in data: escaped = True
                    tail = data[-overlap:]
            if found or escaped: raise Skip("uncertain_opaque_incoming_reference", member)
            checked.append({"entry": member, "provider": row["label"], "bytes": info.file_size,
                            "no_affected_resource_id_literals_or_escaped_paths": True,
                            "original_native_parse_error_retained": error["error_type"]})
        return checked

    def contexts(self, identifier):
        affected, pending = {identifier}, [identifier]
        while pending:
            for child in self.reverse.get(pending.pop(), set()):
                if child not in affected: affected.add(child); pending.append(child)
        opaque = self.opaque_references(affected)
        roots = {x for x in affected if x.split(":", 1)[1].startswith("item/")} | (affected & self.block_roots)
        if not roots: raise Skip("no_proven_static_wrapper_context", identifier)
        # Every incoming leaf must be explained by an item or native blockstate
        # selector, even if a model author placed it outside the usual folders.
        for candidate in affected:
            self.node(candidate)
            if not self.reverse.get(candidate) and candidate not in roots:
                raise Skip("unexplained_incoming_leaf_context", candidate)
        contexts, all_textures, seen_roots = [], {}, set()
        queue = sorted(roots)
        while queue:
            root = queue.pop(0)
            if root in seen_roots: continue
            if len(seen_roots) >= 512: raise Skip("incoming_context_count_bound")
            seen_roots.add(root); chain = self.chain(root, identifier)
            for row in chain:
                for override in row["overrides"]:
                    if override not in seen_roots: queue.append(override)
            textures = {}
            for row in reversed(chain): textures.update(row["textures"])
            geometry = next((row for row in chain if row["elements"]), None)
            if geometry is None: raise Skip("no_effective_incoming_geometry", root)
            body = self.sources.model(geometry["id"])
            # This map is used only to predict closure. No model texture map is
            # written, merged or invented in the private repair pack.
            effective = dict(body); effective["textures"] = textures
            closure = own_texture_closure(self.sources, effective)
            for texture in closure: all_textures[texture["id"]] = texture
            contexts.append({"root": root, "chain": [{key: row[key] for key in ("id", "entry", "provider", "provider_sha256", "original_json_sha256")} for row in chain],
                             "texture_closure": closure, "includes_repaired_model": any(row["id"] == identifier for row in chain),
                             "texture_fields_written_or_changed": False})
        if not any(x["includes_repaired_model"] for x in contexts): raise Skip("no_context_reaches_candidate")
        return contexts, [all_textures[key] for key in sorted(all_textures)], opaque


def log_missing_models(raw):
    if len(raw) > MAX_LOG: raise Refuse("Log exceeds explicit byte bound")
    text = raw.decode("utf-8", errors="replace")
    result = set()
    for value in re.findall(r"FileNotFoundException: ([^\r\n]+:models/[^\r\n]+\.json)", text):
        namespace, path = value.split(":models/", 1)
        result.add(resource(namespace + ":" + path[:-5]))
    return result


def pin_read(row, maximum):
    if set(row) != {"path", "sha256"}: raise Refuse("Exact pinned file fields required")
    path = Path(row["path"])
    if not path.is_file() or path.is_symlink() or path.stat().st_size > maximum:
        raise Refuse("Invalid pinned file")
    raw = path.read_bytes()
    if sha(raw) != row["sha256"]: raise Refuse("Pinned file changed")
    return raw


def model_references(node):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "model" and isinstance(value, str): yield resource(value)
            yield from model_references(value)
    elif isinstance(node, list):
        for value in node: yield from model_references(value)


def registry_proof(sources, registry_pin, overlays, graph=None):
    if registry_pin is None:
        return {"verified": False, "reason": "No pinned actual-runtime registry export supplied",
                "affected_items": [], "affected_blocks": []}
    registry = document(pin_read(registry_pin, MAX_MANIFEST), MAX_MANIFEST)
    if not isinstance(registry, dict) or set(registry) != {"schema", "kind", "provider_sha256", "items", "blocks"}:
        raise Refuse("Exact actual-runtime registry schema required")
    if registry["schema"] != 1 or registry["kind"] != "actual_runtime_export":
        raise Refuse("Registry source is not declared actual runtime export")
    for kind in ("items", "blocks"):
        if not isinstance(registry[kind], list) or len(registry[kind]) > 32768:
            raise Refuse("Invalid/oversized registry ID list")
        if any(not isinstance(x, str) for x in registry[kind]): raise Refuse("Registry ID type is not text")
        if len(set(registry[kind])) != len(registry[kind]): raise Refuse("Duplicate registry ID")
        if any(not isinstance(x, str) or resource(x) != x for x in registry[kind]):
            raise Refuse("Registry ID is not exact")
    if not isinstance(registry["provider_sha256"], dict): raise Refuse("Provider hash map required")
    # Evidence must bind every patched provider, not an unrelated historical mod set.
    for identifier in overlays:
        row, _, _ = sources.unique(model_path(identifier))
        if registry["provider_sha256"].get(row["label"]) != row["sha256"]:
            raise Refuse("Runtime registry export provider identity mismatch")
    unresolved, reached_models = [], set()
    def reaches(identifier):
        seen = set()
        while identifier not in seen and len(seen) < MAX_DEPTH:
            if identifier in overlays:
                reached_models.add(identifier)
                return True
            seen.add(identifier)
            if special_parent(identifier): return False
            try: obj = sources.model(identifier)
            except (Refuse, ValueError) as error:
                # Existing unrelated broken models are not repaired, hidden or
                # allowed to masquerade as reachability to this candidate.
                unresolved.append({"model": identifier, "reason": getattr(error, "reason", "invalid_original_model_json")})
                return False
            if "parent" not in obj: return False
            identifier = resource(obj["parent"])
        raise Skip("registry_model_parent_cycle")
    items, blocks = [], []
    for identifier in sorted(registry["items"]):
        namespace, path = identifier.split(":", 1)
        entry = "assets/" + namespace + "/models/item/" + path + ".json"
        if entry not in sources.entries: continue
        if reaches(namespace + ":item/" + path): items.append(identifier)
    for identifier in sorted(registry["blocks"]):
        namespace, path = identifier.split(":", 1)
        entry = "assets/" + namespace + "/blockstates/" + path + ".json"
        if entry not in sources.entries: continue
        raw, _ = sources.read(entry)
        if graph is not None:
            definitions = [row for row in graph.data["definitions"] if row["kind"] == "blockstate" and row["id"] == identifier]
            if len(definitions) != 1 or definitions[0]["original_json_sha256"] != sha(raw):
                unresolved.append({"model": identifier, "reason": "native_blockstate_definition_unproven"})
                continue
            references = definitions[0]["models"]
        else:
            try: references = list(model_references(document(raw)))
            except (Refuse, ValueError):
                unresolved.append({"model": identifier, "reason": "original_blockstate_requires_native_parser"})
                continue
        if any(reaches(candidate) for candidate in references): blocks.append(identifier)
    return {"verified": True, "export_sha256": registry_pin["sha256"],
            "affected_items": items, "affected_blocks": blocks,
            "all_patched_models_reached": set(overlays) == reached_models,
            "unreached_models": sorted(set(overlays) - reached_models),
            "unresolved_other_model_branches": unresolved,
            "limit": "Exact registered IDs plus static JSON selectors, not a model bake or runtime gameplay test"}


def evaluate(sources, candidate, missing, graph=None):
    if set(candidate) != {"archive", "entry", "sha256"}: raise Refuse("Exact candidate fields required")
    identifier = model_id(candidate["entry"])
    if model_path(identifier) != candidate["entry"]: raise Skip("noncanonical_model_entry_case")
    raw, owner = sources.read(candidate["entry"])
    if owner["label"] != candidate["archive"] or sha(raw) != candidate["sha256"]:
        raise Refuse("Candidate source identity mismatch")
    model = document(raw)
    if not isinstance(model, dict) or not isinstance(model.get("parent"), str): raise Skip("no_string_parent")
    if "loader" in model: raise Skip("custom_loader_model")
    parent = resource(model["parent"])
    if special_parent(parent): raise Skip("builtin_or_virtual_parent_retained", parent)
    if parent not in missing: raise Skip("parent_not_log_proven_missing", parent)
    if model_path(parent) in sources.entries or sources.folded.get(model_path(parent).casefold()):
        raise Skip("parent_available_or_case_ambiguous", parent)
    contexts, opaque = [], []
    if graph is None:
        textures = own_texture_closure(sources, model)
    else:
        if not isinstance(model.get("elements"), list) or not model["elements"]: raise Skip("no_own_nonempty_geometry")
        contexts, textures, opaque = graph.contexts(identifier)
    if "display" in model and not isinstance(model["display"], dict): raise Skip("invalid_display_contract")
    if graph is None and "overrides" in model and model["overrides"]: raise Skip("override_model_contract_retained")
    patched, removed = remove_parent_bytes(raw)
    if parent_closure(sources, identifier, {identifier: patched})["special"]:
        raise Refuse("Unexpected synthetic parent closure")
    semantic = dict(model); semantic.pop("parent")
    geometry = {key: model[key] for key in ("elements", "textures", "display") if key in model}
    return patched, {
        "id": identifier, "entry": candidate["entry"], "provider": owner["label"],
        "provider_sha256": owner["sha256"], "original_json_sha256": sha(raw),
        "patched_json_sha256": sha(patched), "removed_parent": parent,
        "removed_byte_range": removed, "retained_byte_ranges_unchanged": True,
        "all_fields_except_parent_semantic_sha256": sha(canonical(semantic)),
        "original_and_patched_geometry_semantic_sha256": sha(canonical(geometry)),
        "elements_count": len(model["elements"]), "texture_closure": textures,
        "display_keys_preserved": sorted(model.get("display", {})),
        "no_existing_inheritance_available_in_complete_pinned_stack": True,
        "parent_cycle_after_patch": False,
        "incoming_contexts": contexts,
        "opaque_other_sources_checked": opaque,
        "actual_native_callback_selector_binding_proven": False,
    }


def complete_inputs(manifest):
    required = {"schema", "workspace", "mods_directory", "archives", "options", "log", "candidates", "registry"}
    optional = {"context_mode", "context_graph"}
    if not isinstance(manifest, dict) or not required <= set(manifest) or set(manifest) - required - optional or manifest["schema"] != 1:
        raise Refuse("Exact schema1 repair manifest required")
    if manifest.get("context_mode") not in (None, "all_incoming_wrappers"): raise Refuse("Unknown context mode")
    if (manifest.get("context_mode") == "all_incoming_wrappers") != bool(manifest.get("context_graph")):
        raise Refuse("Explicit context mode and pinned actual graph must be supplied together")
    if not isinstance(manifest["archives"], list) or not 1 <= len(manifest["archives"]) <= 256:
        raise Refuse("Archive list bound")
    for row in manifest["archives"]:
        if not isinstance(row, dict) or set(row) != {"label", "path", "sha256", "kind"}:
            raise Refuse("Exact archive fields required")
        if not all(isinstance(row[key], str) for key in row) or not re.fullmatch(r"[0-9a-f]{64}", row["sha256"]):
            raise Refuse("Archive field types or hash invalid")
    if not isinstance(manifest["candidates"], list) or len(manifest["candidates"]) > MAX_CANDIDATES:
        raise Refuse("Candidate count bound")
    for row in manifest["candidates"]:
        if not isinstance(row, dict) or set(row) != {"archive", "entry", "sha256"} or not all(isinstance(value, str) for value in row.values()):
            raise Refuse("Exact candidate field types required")
        if not re.fullmatch(r"[0-9a-f]{64}", row["sha256"]): raise Refuse("Candidate hash invalid")
    if len({x.get("entry") for x in manifest["candidates"]}) != len(manifest["candidates"]):
        raise Refuse("Duplicate candidate model")
    options = pin_read(manifest["options"], 1024 * 1024).decode("utf-8-sig")
    selected = [line.split(":", 1)[1] for line in options.splitlines() if line.startswith("resourcePacks:")]
    if len(selected) != 1 or document(selected[0].encode(), 1024 * 1024) != []:
        raise Refuse("Active resource packs are not empty; exact ordered pack stack is not implemented")
    mods = Path(manifest["mods_directory"]).resolve()
    actual = {p.resolve() for p in mods.glob("*.jar")}
    declared = {Path(x["path"]).resolve() for x in manifest["archives"] if x["kind"] == "mod"}
    if actual != declared: raise Refuse("Complete installed mod archive set must be pinned")
    vanilla = [x for x in manifest["archives"] if x["kind"] == "vanilla"]
    if len(vanilla) != 1 or any(x["kind"] not in {"vanilla", "mod"} for x in manifest["archives"]):
        raise Refuse("Exactly one vanilla resource archive is required")
    return log_missing_models(pin_read(manifest["log"], MAX_LOG))


def deterministic_pack(path, members):
    # Stored entries avoid zlib-version-dependent output differences across PCs.
    with zipfile.ZipFile(path, "x", compression=zipfile.ZIP_STORED) as archive:
        for member, data in sorted(members.items()):
            asset_path(member)
            if member != "pack.mcmeta" and not re.fullmatch(r"assets/[^/]+/models/.+\.json", member):
                raise Refuse("Non-model native payload forbidden in generated pack")
            info = zipfile.ZipInfo(member, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED; info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data, compress_type=zipfile.ZIP_STORED)
    with zipfile.ZipFile(path) as archive:
        if archive.namelist() != sorted(members) or archive.testzip() is not None:
            raise Refuse("Private pack readback/CRC failed")
        for member, expected in members.items():
            if sha(archive.read(member)) != sha(expected):
                raise Refuse("Private pack member identity changed during write")


def build(manifest_path, output):
    manifest = document(Path(manifest_path).read_bytes(), MAX_MANIFEST)
    missing = complete_inputs(manifest)
    workspace, output = Path(manifest["workspace"]).resolve(), Path(output).resolve()
    if output.exists() or workspace not in output.parents:
        raise Refuse("Use a NEW output directory strictly inside the disposable workspace")
    if Path(manifest["mods_directory"]).resolve() in output.parents:
        raise Refuse("Output cannot be inside an input profile")
    sources = Sources(manifest["archives"])
    try:
        vanilla = next(x for x in manifest["archives"] if x["kind"] == "vanilla")
        for name in ("assets/minecraft/models/item/generated.json", "assets/minecraft/models/block/cube_all.json"):
            rows = sources.entries.get(name, [])
            if not any(row[0]["label"] == vanilla["label"] for row in rows):
                raise Refuse("Declared vanilla archive lacks required standard model resources")
        members, patched, skipped = {}, [], []
        graph = ContextGraph(sources, manifest["context_graph"], manifest["archives"]) if manifest.get("context_mode") else None
        for candidate in sorted(manifest["candidates"], key=lambda row: row["entry"]):
            try:
                data, proof = evaluate(sources, candidate, missing, graph)
                members[candidate["entry"]] = data; patched.append(proof)
            except Skip as error:
                skipped.append({"entry": candidate["entry"], "reason": error.reason, "detail": error.detail})
        overlays = {model_id(member): data for member, data in members.items()}
        registry = registry_proof(sources, manifest["registry"], overlays, graph)
        if not sources.unchanged(): raise Refuse("Pinned source archive changed during planning")
        # A second complete-input check detects changes to the options/mod set/log.
        if complete_inputs(manifest) != missing: raise Refuse("Pinned input view changed")
        output.mkdir(parents=True)
        members["pack.mcmeta"] = json_bytes({"pack": {"pack_format": 3, "description": "PRIVATE original model parent-only repair; hold for native proof"}})
        pack = output / "PRIVATE-ORIGINAL-MODEL-PARENT-REPAIR.zip"
        if patched: deterministic_pack(pack, members)
        safe = {
            "schema": 1, "scope": "PRIVATE_ORIGINAL_MODEL_PARENT_ONLY_CANDIDATE", "activation_allowed": False,
            "native_modelblock_proof_executed": False, "registry_proof": registry,
            "incoming_context_mode": manifest.get("context_mode", "self_contained_only"),
            "native_context_graph_sha256": (manifest.get("context_graph") or {}).get("sha256"),
            "patched": patched, "skipped": skipped, "patched_count": len(patched),
            "skipped_count": len(skipped), "source_archives_unchanged": True,
            "source_pins": [{"label": x["label"], "sha256": x["sha256"], "kind": x["kind"]} for x in manifest["archives"]],
            "log_sha256": manifest["log"]["sha256"], "options_sha256": manifest["options"]["sha256"],
            "pack_sha256": file_sha(pack) if patched else None,
            "original_pixels_audio_code_copied": False, "original_geometry_copied_only_to_private_pack": bool(patched),
            "runtime_or_profile_modified": False, "held_reasons": ["Actual native ModelBlock proof not yet executed", "New disposable real-client bake not yet executed", "Actual native callback selector ABI binding not yet proven"] + ([] if registry["verified"] else ["Actual runtime registry export missing"]) + (["Some patched models lack a verified registered static selector"] if registry["verified"] and not registry["all_patched_models_reached"] else []),
            "publication_rule": "Publish only original tool/tests and this safe hash metadata, never this private pack or original JSON bodies",
        }
        (output / "SAFE-REPAIR-REPORT.json").write_bytes(json_bytes(safe))
        archive_paths = {x["label"]: x["path"] for x in manifest["archives"]}
        private_models = json.loads(json.dumps(patched))
        for row in private_models:
            row["original_archive"] = archive_paths[row["provider"]]
            for context in row["incoming_contexts"]:
                for node in context["chain"]: node["original_archive"] = archive_paths[node["provider"]]
        proof_input = {"schema": 1, "pack": str(pack), "pack_sha256": file_sha(pack) if patched else None, "models": private_models}
        (output / "PRIVATE-NATIVE-PROOF-INPUT.json").write_bytes(json_bytes(proof_input))
        return safe
    finally:
        sources.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        report = build(args.manifest, args.output)
        print(json.dumps({"patched": report["patched_count"], "skipped": report["skipped_count"],
                          "activation_allowed": False, "native_proof_executed": False}))
    except (Refuse, ValueError, OSError, zipfile.BadZipFile) as error:
        parser.exit(2, "Repair refused: " + str(error) + "\n")


if __name__ == "__main__": main()
