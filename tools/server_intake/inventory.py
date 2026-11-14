"""Static, bounded identity inspection of recovered server inputs. Never executes code.

The full inventory is PRIVATE. Public summaries must be separately reviewed and
must not include config contents, player data or arbitrary manifest attributes.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import zipfile

from .intake import pack, save, require

MOD_DESCRIPTOR = "Lnet/minecraftforge/fml/common/Mod;"
MAX_METADATA = 2 * 1024**2
MAX_CLASS = 16 * 1024**2


class ClassReader:
    def __init__(self, data):
        self.data, self.pos, self.cp = data, 0, [None]

    def take(self, count):
        require(count >= 0 and self.pos + count <= len(self.data), "Truncated class metadata")
        result = self.data[self.pos:self.pos + count]
        self.pos += count
        return result

    def number(self, count):
        return int.from_bytes(self.take(count), "big")

    def constant(self):
        index = self.number(2)
        require(0 < index < len(self.cp), "Invalid class constant reference")
        return self.cp[index]

    def element(self, depth=0):
        require(depth < 32, "Annotation nesting limit exceeded")
        tag = chr(self.number(1))
        if tag in "BCDFIJSZs":
            value = self.constant()
            return bool(value) if tag == "Z" else value
        if tag == "e":
            return {"enum_type": self.constant(), "enum_value": self.constant()}
        if tag == "c":
            return {"class": self.constant()}
        if tag == "@":
            return self.annotation(depth + 1)
        if tag == "[":
            return [self.element(depth + 1) for _ in range(self.number(2))]
        raise ValueError("Unsupported annotation element tag")

    def annotation(self, depth=0):
        kind = self.constant()
        result = {"annotation": kind, "values": {}}
        for _ in range(self.number(2)):
            name = self.constant()
            result["values"][name] = self.element(depth + 1)
        return result

    def attributes(self, annotations=False):
        result = []
        for _ in range(self.number(2)):
            name, size = self.constant(), self.number(4)
            end = self.pos + size
            require(end <= len(self.data), "Truncated attribute")
            if annotations and name in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
                result.extend(self.annotation() for _ in range(self.number(2)))
                require(self.pos == end, "Invalid annotation attribute size")
            self.pos = end
        return result

    def inspect(self):
        require(self.take(4) == b"\xca\xfe\xba\xbe", "Invalid class magic")
        self.number(2)
        major = self.number(2)
        count = self.number(2)
        index = 1
        while index < count:
            tag = self.number(1)
            value = None
            if tag == 1:
                value = self.take(self.number(2)).decode("utf-8", errors="replace")
            elif tag == 3:
                value = int.from_bytes(self.take(4), "big", signed=True)
            elif tag in (4, 9, 10, 11, 12, 17, 18):
                self.take(4)
            elif tag in (5, 6):
                self.take(8)
                self.cp.extend([None, None])
                index += 2
                continue
            elif tag in (7, 8, 16, 19, 20):
                self.take(2)
            elif tag == 15:
                self.take(3)
            else:
                raise ValueError("Unsupported class constant-pool tag")
            self.cp.append(value)
            index += 1
        self.take(6)
        self.take(2 * self.number(2))
        for _ in range(2):
            for _ in range(self.number(2)):
                self.take(6)
                self.attributes()
        annotations = self.attributes(annotations=True)
        return {"major": major, "mod_declarations": [a["values"] for a in annotations if a["annotation"] == MOD_DESCRIPTOR]}


def manifest_fields(data):
    result, previous = {}, None
    for line in data.decode("utf-8", errors="replace").splitlines():
        if line.startswith(" ") and previous:
            result[previous] += line[1:]
        elif ": " in line:
            previous, value = line.split(": ", 1)
            result[previous] = value
        elif not line:
            break  # Only main section, not per-file signatures.
    allowed = ("Main-Class", "Implementation-Title", "Implementation-Version", "Specification-Version",
               "FMLCorePlugin", "FMLCorePluginContainsFMLMod", "ForceLoadAsMod", "TweakClass", "Build-Jdk", "Class-Path")
    return {key: result[key] for key in allowed if key in result}


def jar_identity(path, relative):
    result = dict(pack.hash_file(path), path=relative, manifest={}, mod_declarations=[], class_majors={}, newer_class_entries=[], warnings=[])
    with pack.stable_reader(path) as stream, zipfile.ZipFile(stream) as archive:
        infos = archive.infolist()
        require(len(infos) <= 200000, "JAR entry limit exceeded")
        require(sum(info.file_size for info in infos) <= 2 * 1024**3, "JAR expanded-size limit exceeded")
        majors = Counter()
        for info in infos:
            name = info.filename
            lower = name.lower()
            if lower == "meta-inf/manifest.mf" and info.file_size <= MAX_METADATA:
                result["manifest"] = manifest_fields(archive.read(info))
            elif name in ("mcmod.info", "plugin.yml") and info.file_size <= MAX_METADATA:
                data = archive.read(info)
                if name == "mcmod.info":
                    try:
                        metadata = json.loads(data.decode("utf-8-sig"))
                        metadata = metadata.get("modList", [metadata]) if isinstance(metadata, dict) else metadata
                        result["mcmod"] = [{key: mod[key] for key in ("modid", "name", "version", "mcversion", "dependencies", "requiredMods") if key in mod}
                                           for mod in metadata if isinstance(mod, dict)]
                    except (ValueError, TypeError) as error:
                        result["warnings"].append("mcmod.info parse: " + type(error).__name__)
                else:
                    # Identity only: no YAML constructors or arbitrary code.
                    allowed = ("name", "version", "main", "api-version", "depend", "softdepend", "loadbefore")
                    result["plugin"] = {}
                    for line in data.decode("utf-8-sig", errors="replace").splitlines():
                        if not line[:1].isspace() and ":" in line:
                            key, value = line.split(":", 1)
                            if key in allowed:
                                result["plugin"][key] = value.strip()
            elif name.endswith(".class") and info.file_size <= MAX_CLASS:
                with archive.open(info) as member:
                    header = member.read(8)
                    if len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe":
                        major = int.from_bytes(header[6:8], "big")
                        majors[major] += 1
                        if major > 52:
                            result["newer_class_entries"].append({"path": name, "major": major,
                                                                  "versioned_module_descriptor": name.startswith("META-INF/versions/") and name.endswith("/module-info.class")})
                        data = header + member.read()
                        if MOD_DESCRIPTOR.encode("ascii") in data:
                            try:
                                declarations = ClassReader(data).inspect()["mod_declarations"]
                                for declaration in declarations:
                                    allowed = ("modid", "name", "version", "dependencies", "clientSideOnly", "serverSideOnly", "acceptableRemoteVersions", "acceptedMinecraftVersions")
                                    result["mod_declarations"].append(dict({key: declaration[key] for key in allowed if key in declaration}, declaring_class=name))
                            except ValueError as error:
                                result["warnings"].append("Class annotation parse failed: " + name + ": " + type(error).__name__)
        result["class_majors"] = dict(sorted(majors.items()))
        result["declared_client_only"] = any(mod.get("clientSideOnly") is True for mod in result["mod_declarations"])
        result["declared_server_only"] = any(mod.get("serverSideOnly") is True for mod in result["mod_declarations"])
    return result


def inventory(root):
    root = pack.no_links(root)
    receipt = json.loads((root / "PRIVATE-SERVER-INTAKE.json").read_text(encoding="utf-8"))
    require(receipt["status"].startswith("RECOVERED_"), "Successful recovery receipt required")
    result = {"schema": 1, "scope": "PRIVATE_STATIC_SERVER_INPUT_INVENTORY", "server_executed": False,
              "archive_sha256": receipt["archive_sha256"], "jars": [], "runtime_inputs": [], "java_releases": []}
    for row in receipt["rows"]:
        if row["kind"] != "file":
            continue
        relative = row["path"]
        parts = relative.split("/")
        path = pack.no_links(root / row["destination"])
        if path.suffix.lower() == ".jar" and (len(parts) == 1 or parts[0] in ("mods", "plugins")):
            item = jar_identity(path, relative)
            item["category"] = "runtime" if len(parts) == 1 else parts[0]
            require(item["sha256"] == row["sha256"], "Inventory differs from extraction receipt")
            result["jars"].append(item)
        if len(parts) == 1 and (path.suffix.lower() in (".bat", ".sh", ".cmd", ".ps1") or relative in ("server.properties", "magma.yml", "magma_libs.json", ".minecraft-startup-history.json", "eula.txt")):
            result["runtime_inputs"].append({key: row[key] for key in ("path", "bytes", "sha256")})
        if relative.endswith("/release") and "java" in relative.casefold() and row["bytes"] <= MAX_METADATA:
            allowed = ("JAVA_VERSION", "JAVA_RUNTIME_VERSION", "IMPLEMENTOR", "OS_NAME", "OS_ARCH")
            fields = {}
            for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
                if "=" in line:
                    key, value = line.split("=", 1)
                    if key in allowed:
                        fields[key] = value.strip('"')
            result["java_releases"].append(dict(path=relative, sha256=row["sha256"], fields=fields))
    result["counts"] = dict(Counter(jar["category"] for jar in result["jars"]))
    result["client_only_declarations"] = [jar["path"] for jar in result["jars"] if jar["declared_client_only"]]
    file_map = {pack.collision_key(row["canonical_path"]): row for row in receipt["rows"] if row["kind"] == "file" and row["classification"] == "primary"}
    result["runtime_classpaths"] = []
    for jar in result["jars"]:
        if jar["category"] != "runtime" or "Class-Path" not in jar["manifest"]:
            continue
        targets = []
        for target in jar["manifest"]["Class-Path"].split():
            try:
                pack.safe_relative(target)
                source = file_map.get(pack.collision_key(target))
                targets.append({"path": target, "present": source is not None,
                                "sha256": source["sha256"] if source else None})
            except pack.PackError:
                targets.append({"path": target, "present": False, "unsafe_relative_target": True})
        result["runtime_classpaths"].append({"jar": jar["path"], "targets": targets,
                                             "missing": [target["path"] for target in targets if not target["present"]]})
    result["java_native_binaries"] = []
    for row in receipt["rows"]:
        relative = row["path"]
        if relative.lower().endswith(("/bin/java", "/bin/java.exe")):
            with (root / row["destination"]).open("rb") as stream:
                magic = stream.read(4)
            result["java_native_binaries"].append({"path": relative, "sha256": row["sha256"],
                                                    "format": "ELF" if magic == b"\x7fELF" else "PE" if magic[:2] == b"MZ" else "UNKNOWN"})
    save(root / "PRIVATE-STATIC-INVENTORY.json", result)
    return result


def public_summary(root):
    """Export allowlisted metadata; never export config/player/NPC/world contents."""
    root = Path(root)
    recovery = json.loads((root / "PRIVATE-SERVER-INTAKE.json").read_text(encoding="utf-8"))
    static = json.loads((root / "PRIVATE-STATIC-INVENTORY.json").read_text(encoding="utf-8"))
    require(recovery["archive_sha256"] == static["archive_sha256"], "Receipt source identities differ")
    return {
        "schema": 1, "scope": "STATIC_SERVER_INTAKE_ONLY", "status": recovery["status"],
        "finished_utc": recovery["finished_utc"], "archive_bytes": recovery["archive_bytes"],
        "archive_sha256": recovery["archive_sha256"], "manifest_sha256": recovery["manifest_sha256"],
        "entries": recovery["entries"], "file_entries": recovery["file_entries"],
        "uncompressed_bytes": recovery["uncompressed_bytes"], "crc_verified": recovery["crc_verified"],
        "output_readback_verified": recovery["output_readback_verified"],
        "distinct_payloads_preserved": recovery["distinct_payloads_preserved"],
        "source_unmodified": recovery["source_unmodified"], "server_executed": False,
        "world_started": False, "production_merged": False,
        "unresolved_collision_groups": recovery["unresolved_collision_groups"],
        "collision_payloads": [{"content_equivalent": group["content_equivalent"],
                                "review_required": group["review_required"],
                                "entries": [{key: entry[key] for key in ("archive_index", "bytes", "sha256")} for entry in group["entries"]]}
                               for group in recovery["collisions"]],
        "jar_counts": static["counts"],
        "jars": [{key: jar[key] for key in ("path", "category", "bytes", "sha256", "manifest", "mod_declarations", "class_majors", "newer_class_entries", "declared_client_only", "declared_server_only", "warnings", "plugin") if key in jar}
                 for jar in static["jars"]],
        "java_releases": static["java_releases"], "java_native_binaries": static["java_native_binaries"],
        "runtime_classpaths": static["runtime_classpaths"],
        "limitations": ["Static metadata and class headers only; no Java, plugin, server or script execution.",
                        "Case-conflicting configs require canonical choices in a separate copied candidate.",
                        "Absent manifest classpath targets require runtime dependency resolution; presence of embedded or exploded replacement classes is not a proven substitute.",
                        "A clientSideOnly annotation records a declaration, not a complete dedicated-server compatibility test.",
                        "No production-world start, migration, networking, gameplay, restart or Bisect acceptance occurred."]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recovery", type=Path)
    parser.add_argument("--public-report", type=Path)
    args = parser.parse_args(argv)
    result = inventory(args.recovery)
    if args.public_report:
        save(args.public_report, public_summary(args.recovery))
    print(json.dumps({"counts": result["counts"], "client_only_declarations": result["client_only_declarations"], "java_releases": result["java_releases"]}, indent=2))


if __name__ == "__main__":
    main()
