"""Owner-started, loopback-only Vale companion. Python 3.8+, standard library.

This program does not install a service, grant itself folders, or accept shell
commands. Start it locally, select folders, then connect the File Link page.
"""
import argparse
import base64
import collections
import hashlib
import hmac
import json
import os
import pathlib
import re
import secrets
import stat
import subprocess
import sys
import threading
import time
import uuid
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


APP_ORIGIN = "https://vale-file-link.neontick37.chatgpt.site"
MAX_REQUEST_BYTES = 1024 * 1024
MAX_TEXT_BYTES = 128 * 1024
MAX_READ_BYTES = 65536
MAX_HASH_BYTES = 512 * 1024 * 1024
MAX_DIRECTORY_ENTRIES = 10000
MAX_ROOT_GRANTS = 8
MAX_RPC_TIMEOUT_MS = 12000
REPLAY_TTL_SECONDS = 600
MAX_REPLAY_ENTRIES = 4096
MAX_REPLAY_BYTES = 16 * 1024 * 1024
REQUEST_ID = re.compile(r"^[A-Za-z0-9_.:-]{1,128}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
WINDOWS_RESERVED = re.compile(r"^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?$", re.I)
TEXT_EXTENSIONS = frozenset("java json jsonl mcmeta txt md cfg conf properties toml yaml yml xml gradle csv tsv js mjs ts tsx css html py ps1 bat cmd sh lang log".split())
BLOCKED_SEGMENTS = frozenset(".git .ssh .aws .azure .gnupg vale-file-link-backups id_rsa id_ed25519 id_ecdsa id_dsa authorized_keys".split())
SCRIPT_EXTENSIONS = frozenset((".jar", ".py", ".ps1", ".cmd", ".bat", ".sh", ".js", ".mjs", ".cjs"))


class CompanionError(Exception):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code
        self.message = message

    def to_dict(self):
        return {"code": self.code, "message": self.message}


def integer(value, name, minimum=0, maximum=MAX_READ_BYTES):
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise CompanionError("BAD_ARGUMENT", "%s must be an integer between %s and %s" % (name, minimum, maximum))
    return value


def identity(st):
    # Windows Python stat/fstat may expose different legacy ctime semantics.
    # Prefer the explicit birth time when Python supplies it, as the assembler
    # does; retain POSIX ctime for detecting a changed file identity/metadata.
    created = getattr(st, "st_birthtime_ns", st.st_ctime_ns) if os.name == "nt" else st.st_ctime_ns
    return (st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, created)


def reparse(st):
    return stat.S_ISLNK(st.st_mode) or bool(getattr(st, "st_file_attributes", 0) & 0x400)


def relative_parts(value, allow_root=False):
    if not isinstance(value, str) or len(value) > 1024:
        raise CompanionError("BAD_PATH", "A relative path of at most 1024 characters is required")
    if value == "" and allow_root:
        return ()
    if "\\" in value:
        raise CompanionError("BAD_PATH", "Use forward slashes; backslashes are not normalized")
    parts = value.split("/")
    if len(parts) > 32:
        raise CompanionError("BAD_PATH", "A relative path may have at most 32 components")
    for part in parts:
        if (not part or part in (".", "..") or len(part) > 255
                or part[-1:] in (" ", ".") or WINDOWS_RESERVED.match(part)
                or any(ord(c) < 32 or ord(c) == 127 or c in ':*?"<>|' for c in part)):
            raise CompanionError("BAD_PATH", "Absolute paths, traversal, device names, and special path components are not allowed")
        if part.casefold() in BLOCKED_SEGMENTS or part.casefold().startswith(".env") or re.search(r"\.(pem|pfx|p12|key)$", part, re.I):
            raise CompanionError("EXCLUDED_PATH", "This path is excluded from remote file access")
    return tuple(parts)


def stable_hash(path):
    path_before = os.lstat(str(path))
    if reparse(path_before) or not stat.S_ISREG(path_before.st_mode):
        raise CompanionError("FILE_TYPE", "Only ordinary files can be hashed")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
    fd = os.open(str(path), flags)
    with os.fdopen(fd, "rb") as stream:
        before = os.fstat(stream.fileno())
        if identity(path_before) != identity(before):
            raise CompanionError("SOURCE_CHANGED", "File identity changed between inspection and opening")
        if not stat.S_ISREG(before.st_mode) or before.st_size > MAX_HASH_BYTES:
            raise CompanionError("FILE_LIMIT", "Only regular files of at most 512 MiB can be hashed or edited")
        digest = hashlib.sha256()
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
        after = os.fstat(stream.fileno())
    if identity(before) != identity(after) or identity(after) != identity(os.lstat(str(path))):
        raise CompanionError("SOURCE_CHANGED", "File changed while opening or reading; retry with a new request after the writer has stopped")
    return digest.hexdigest(), after


class RootGrant:
    def __init__(self, path, writable=False, root_id=None):
        original = pathlib.Path(path).absolute()
        if not original.is_dir():
            raise CompanionError("BAD_ROOT", "Selected root is not a directory")
        # A picked junction/symlink is not an implicit grant to its destination.
        for parent in reversed((original,) + tuple(original.parents)):
            if reparse(os.lstat(str(parent))):
                raise CompanionError("REPARSE_POINT", "Select an ordinary directory, not a symbolic link or junction")
        self.path = original.resolve(strict=True)
        self.root_identity = (os.stat(str(self.path)).st_dev, os.stat(str(self.path)).st_ino)
        self.writable = bool(writable)
        self.id = root_id or str(uuid.uuid4())
        self.write_prefix = "Vale-Export"

    def describe(self):
        return {"id": self.id, "label": self.path.name or str(self.path), "path": str(self.path),
                "writable": self.writable, "write_prefix": self.write_prefix if self.writable else None}

    def resolve(self, relative, write=False, allow_root=False, create_parents=False):
        parts = relative_parts(relative, allow_root)
        for parent in reversed(tuple(self.path.parents)):
            if reparse(os.lstat(str(parent))):
                raise CompanionError("REPARSE_POINT", "A parent of the selected root changed to a link or junction")
        current_stat = os.lstat(str(self.path))
        if reparse(current_stat) or (current_stat.st_dev, current_stat.st_ino) != self.root_identity:
            raise CompanionError("ROOT_CHANGED", "Selected root changed; restart and select it again locally")
        if write:
            if any(part.casefold() == ".vale-backups" or part.casefold().startswith(".vale-pending-") for part in parts):
                raise CompanionError("RESERVED_PATH", "Companion backup and pending files may be inspected but never edited or resumed through RPC")
            if not self.writable:
                raise CompanionError("READ_ONLY", "This folder was granted read-only access")
            if len(parts) < 2 or parts[0] != self.write_prefix:
                raise CompanionError("WRITE_SCOPE", "Writes are restricted to Vale-Export inside the selected root")
        current = self.path
        for index, part in enumerate(parts):
            current = current / part
            try:
                entry = os.lstat(str(current))
            except FileNotFoundError:
                if index < len(parts) - 1 and create_parents:
                    try:
                        current.mkdir()
                    except FileExistsError:
                        pass
                    entry = os.lstat(str(current))
                elif index == len(parts) - 1:
                    return current
                else:
                    raise CompanionError("NOT_FOUND", "Parent directory does not exist")
            if reparse(entry):
                raise CompanionError("REPARSE_POINT", "Symbolic links and Windows junctions are not followed")
            if index < len(parts) - 1 and not stat.S_ISDIR(entry.st_mode):
                raise CompanionError("BAD_PATH", "A parent path is not a directory")
        return current


def validated_grants(grants):
    grants = list(grants)
    if len(grants) > MAX_ROOT_GRANTS:
        raise CompanionError("ROOT_LIMIT", "A session may grant at most eight distinct local folders")
    paths = set()
    root_ids = set()
    for grant in grants:
        path_key = os.path.normcase(str(grant.path))
        if path_key in paths or grant.id in root_ids:
            raise CompanionError("DUPLICATE_ROOT", "Each local folder may be granted only once per session")
        paths.add(path_key)
        root_ids.add(grant.id)
    return grants


class FileAccess:
    def __init__(self, grants):
        self.grants = {grant.id: grant for grant in validated_grants(grants)}

    def locate(self, args, **kwargs):
        root_id = args.get("root_id")
        if not isinstance(root_id, str) or root_id not in self.grants:
            raise CompanionError("UNKNOWN_ROOT", "Choose a locally granted root from status")
        grant = self.grants[root_id]
        return grant, grant.resolve(args.get("path", ""), **kwargs)

    def info(self, args):
        grant, path = self.locate(args, allow_root=True)
        entry = os.lstat(str(path))
        if not (stat.S_ISREG(entry.st_mode) or stat.S_ISDIR(entry.st_mode)):
            raise CompanionError("FILE_TYPE", "Only ordinary files and directories are available")
        result = {"root_id": grant.id, "path": args.get("path", ""), "kind": "directory" if path.is_dir() else "file",
                  "size": entry.st_size, "mtime_ns": str(entry.st_mtime_ns)}
        if stat.S_ISREG(entry.st_mode) and args.get("hash", True):
            result["sha256"], _ = stable_hash(path)
        grant.resolve(args.get("path", ""), allow_root=True)
        return result

    def list(self, args):
        grant, path = self.locate(args, allow_root=True)
        offset = integer(args.get("offset", 0), "offset", maximum=1000000)
        limit = integer(args.get("limit", 200), "limit", 1, 500)
        if not path.is_dir():
            raise CompanionError("NOT_DIRECTORY", "The requested path is not a directory")
        entries = []
        truncated = False
        with os.scandir(str(path)) as iterator:
            for item in iterator:
                if len(entries) >= MAX_DIRECTORY_ENTRIES:
                    truncated = True
                    break
                item_stat = item.stat(follow_symlinks=False)
                kind = ("blocked_reparse_point" if reparse(item_stat) else "directory" if stat.S_ISDIR(item_stat.st_mode)
                        else "file" if stat.S_ISREG(item_stat.st_mode) else "blocked_special_file")
                entries.append({"name": item.name, "kind": kind, "size": item_stat.st_size, "mtime_ns": str(item_stat.st_mtime_ns)})
        entries.sort(key=lambda item: (item["name"].casefold(), item["name"]))
        grant.resolve(args.get("path", ""), allow_root=True)
        return {"root_id": grant.id, "path": args.get("path", ""), "entries": entries[offset:offset + limit],
                "next_offset": offset + limit if offset + limit < len(entries) else None,
                "total": None if truncated else len(entries), "scanned": len(entries), "truncated": truncated}

    def read_range(self, args):
        grant, path = self.locate(args)
        offset = integer(args.get("offset", 0), "offset", maximum=2**53 - 1)
        length = integer(args.get("length", 65536), "length", 1, MAX_READ_BYTES)
        path_before = os.lstat(str(path))
        if reparse(path_before) or not stat.S_ISREG(path_before.st_mode):
            raise CompanionError("FILE_TYPE", "Only ordinary files may be read")
        fd = os.open(str(path), os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
        with os.fdopen(fd, "rb") as stream:
            before = os.fstat(stream.fileno())
            if identity(path_before) != identity(before):
                raise CompanionError("SOURCE_CHANGED", "File identity changed between inspection and opening")
            if not stat.S_ISREG(before.st_mode):
                raise CompanionError("FILE_TYPE", "Only ordinary files may be read")
            stream.seek(offset)
            data = stream.read(length)
            after = os.fstat(stream.fileno())
        if identity(before) != identity(after) or identity(after) != identity(os.lstat(str(path))):
            raise CompanionError("SOURCE_CHANGED", "File changed while reading this range")
        grant.resolve(args["path"])
        return {"root_id": grant.id, "path": args["path"], "offset": offset, "length": len(data),
                "size": after.st_size, "mtime_ns": str(after.st_mtime_ns), "eof": offset + len(data) >= after.st_size,
                "data_base64": base64.b64encode(data).decode("ascii"), "range_sha256": hashlib.sha256(data).hexdigest()}

    @staticmethod
    def text_bytes(args):
        if not isinstance(args.get("text"), str) or "\0" in args["text"]:
            raise CompanionError("BAD_ARGUMENT", "text must be a UTF-8 string without NUL characters")
        parts = relative_parts(args.get("path"))
        if any(part.casefold() == ".vale-backups" or part.casefold().startswith(".vale-pending-") for part in parts):
            raise CompanionError("RESERVED_PATH", "Companion backup and pending files may be inspected but never edited or resumed through RPC")
        extension = parts[-1].rsplit(".", 1)[-1].casefold() if "." in parts[-1] else ""
        if extension not in TEXT_EXTENSIONS:
            raise CompanionError("NOT_TEXT", "This extension is not approved for text edits")
        try:
            data = args["text"].encode("utf-8")
        except UnicodeError:
            raise CompanionError("BAD_ARGUMENT", "text must contain valid Unicode")
        if len(data) > MAX_TEXT_BYTES:
            raise CompanionError("FILE_LIMIT", "Text writes are limited to 128 KiB")
        return data

    @staticmethod
    def exclusive_write(path, data):
        fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0), 0o600)
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())

    def create_text(self, args):
        data = self.text_bytes(args)
        grant, path = self.locate(args, write=True, create_parents=True)
        self.exclusive_write(path, data)
        grant.resolve(args["path"], write=True)
        digest, _ = stable_hash(path)
        if digest != hashlib.sha256(data).hexdigest():
            raise CompanionError("OUTPUT_CHANGED", "New output changed during verification; retain it for inspection")
        grant.resolve(args["path"], write=True)
        return {"root_id": grant.id, "path": args["path"], "sha256": digest, "size": len(data), "created": True}

    def replace_text(self, args):
        data = self.text_bytes(args)
        expected = args.get("expected_sha256")
        if not isinstance(expected, str) or not SHA256.fullmatch(expected):
            raise CompanionError("HASH_REQUIRED", "Supply the exact lower-case SHA-256 from a prior info request")
        grant, path = self.locate(args, write=True)
        current_hash, before = stable_hash(path)
        if current_hash != expected:
            raise CompanionError("SOURCE_CHANGED", "Current file hash differs from the reviewed hash; no edit was made")
        if before.st_size > MAX_TEXT_BYTES:
            raise CompanionError("FILE_LIMIT", "Text replacement is limited to existing files of at most 128 KiB")
        original = path.read_bytes()
        grant.resolve(args["path"], write=True)
        try:
            original.decode("utf-8-sig")
        except UnicodeError:
            raise CompanionError("NOT_TEXT", "Existing file is not UTF-8 text")
        if hashlib.sha256(original).hexdigest() != expected:
            raise CompanionError("SOURCE_CHANGED", "File changed before backup; no edit was made")
        backup_dir = path.parent / ".vale-backups"
        if backup_dir.exists() and reparse(os.lstat(str(backup_dir))):
            raise CompanionError("REPARSE_POINT", "Backup directory is a link or junction")
        backup_dir.mkdir(exist_ok=True)
        if reparse(os.lstat(str(backup_dir))):
            raise CompanionError("REPARSE_POINT", "Backup directory changed")
        stamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()) + "-" + uuid.uuid4().hex
        backup = backup_dir / (stamp + ".bak")
        pending = path.parent / (".vale-pending-" + stamp)
        self.exclusive_write(backup, original)
        self.exclusive_write(pending, data)
        # Keep backup and pending outputs on any error. Never resume them.
        grant.resolve(args["path"], write=True)
        final_hash, final_stat = stable_hash(path)
        if final_hash != expected or identity(final_stat) != identity(before):
            raise CompanionError("SOURCE_CHANGED", "File changed before commit; backup and pending output retained for inspection")
        grant.resolve(args["path"], write=True)
        os.replace(str(pending), str(path))
        grant.resolve(args["path"], write=True)
        after_hash, _ = stable_hash(path)
        if after_hash != hashlib.sha256(data).hexdigest():
            raise CompanionError("OUTPUT_CHANGED", "Edited output changed during verification; original backup retained")
        grant.resolve(args["path"], write=True)
        return {"root_id": grant.id, "path": args["path"], "sha256": after_hash, "previous_sha256": expected,
                "size": len(data), "backup_path": str(backup.relative_to(grant.path)).replace("\\", "/")}


class NamedActions:
    """Fixed, local-owner registrations only. RPC never supplies argv or paths."""
    def __init__(self, registrations=None):
        self.items = {}
        self.processes = {}
        for item in registrations or []:
            name = item.get("id")
            if not isinstance(name, str) or not REQUEST_ID.fullmatch(name) or name in self.items:
                raise CompanionError("BAD_ACTION", "Each named action needs a unique simple id")
            executable = pathlib.Path(item["executable"]).resolve(strict=True)
            cwd = pathlib.Path(item["cwd"]).resolve(strict=True)
            argv = item.get("argv", [])
            digest = item.get("sha256")
            if (not cwd.is_dir() or not executable.is_file() or executable.suffix.lower() not in (".exe",)
                    or not isinstance(argv, list) or not all(isinstance(v, str) and "\0" not in v for v in argv)
                    or not isinstance(digest, str) or not SHA256.fullmatch(digest)):
                raise CompanionError("BAD_ACTION", "Registered actions require an .exe, fixed string argv, cwd, and reviewed executable SHA-256")
            required = item.get("required_files", [])
            if not isinstance(required, list):
                raise CompanionError("BAD_ACTION", "required_files must be a locally supplied array of path/hash records")
            checked_files = {}
            for dependency in required:
                if not isinstance(dependency, dict) or set(dependency) != {"path", "sha256"}:
                    raise CompanionError("BAD_ACTION", "Each required file needs exactly path and sha256")
                dependency_path = pathlib.Path(dependency["path"])
                if not dependency_path.is_absolute():
                    dependency_path = cwd / dependency_path
                dependency_path = dependency_path.resolve(strict=True)
                required_hash = dependency["sha256"]
                if not dependency_path.is_file() or not isinstance(required_hash, str) or not SHA256.fullmatch(required_hash):
                    raise CompanionError("BAD_ACTION", "Each required file must exist and have a reviewed lower-case SHA-256")
                checked_files[os.path.normcase(str(dependency_path))] = {"path": str(dependency_path), "sha256": required_hash}
            # Fixed command arguments do not imply that a script/JAR remained
            # the same. Require explicit hashes for those direct inputs.
            previous_arg = ""
            for arg in argv:
                classpath = previous_arg in ("-cp", "-classpath", "--class-path")
                candidates = arg.split(";") if classpath else [arg]
                for candidate in candidates:
                    if classpath and ("*" in candidate or pathlib.Path(candidate).suffix.lower() != ".jar"):
                        raise CompanionError("BAD_ACTION", "Use explicit hashed JAR files instead of wildcard or directory class paths")
                    if pathlib.Path(candidate).suffix.lower() not in SCRIPT_EXTENSIONS:
                        if re.search(r"\.(jar|py|ps1|cmd|bat|sh|js|mjs|cjs)(?:[=\s]|$)", candidate, re.I):
                            raise CompanionError("BAD_ACTION", "Script and JAR inputs must be separate explicit arguments with required_files hashes")
                        continue
                    candidate_path = pathlib.Path(candidate)
                    if not candidate_path.is_absolute():
                        candidate_path = cwd / candidate_path
                    candidate_path = candidate_path.resolve(strict=True)
                    if os.path.normcase(str(candidate_path)) not in checked_files:
                        raise CompanionError("BAD_ACTION", "Every fixed script/JAR argument must be listed in required_files with its reviewed hash")
                previous_arg = arg
            self.items[name] = dict(item, executable=str(executable), cwd=str(cwd), argv=argv,
                                    required_files=list(checked_files.values()))

    def describe(self):
        results = []
        for name, item in self.items.items():
            process = self.processes.get(name)
            results.append({"id": name, "label": item.get("label", name), "running": process is not None and process.poll() is None,
                            "pid": process.pid if process is not None else None})
        return results

    def launch(self, args):
        if set(args) != {"action_id"} or args["action_id"] not in self.items:
            raise CompanionError("UNKNOWN_ACTION", "Only a fixed action registered by the local owner can be launched")
        name = args["action_id"]
        previous = self.processes.get(name)
        if previous is not None and previous.poll() is None:
            return {"action_id": name, "pid": previous.pid, "already_running": True}
        item = self.items[name]
        digest, _ = stable_hash(pathlib.Path(item["executable"]))
        if digest != item["sha256"]:
            raise CompanionError("SOURCE_CHANGED", "Registered executable changed; review and register it again locally")
        for dependency in item["required_files"]:
            digest, _ = stable_hash(pathlib.Path(dependency["path"]))
            if digest != dependency["sha256"]:
                raise CompanionError("SOURCE_CHANGED", "A fixed script/JAR or required launch input changed; review and register it again locally")
        process = subprocess.Popen([item["executable"]] + item["argv"], cwd=item["cwd"], shell=False,
                                   close_fds=True, creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0))
        self.processes[name] = process
        return {"action_id": name, "pid": process.pid, "already_running": False}


class Companion:
    def __init__(self, backend, grants=(), actions=None, token=None, clock=time.monotonic):
        self.backend = backend
        self.files = FileAccess(grants)
        self.actions = NamedActions(actions)
        self.token = token or secrets.token_urlsafe(32)
        self.instance_id = str(uuid.uuid4())
        self.clock = clock
        self.lock = threading.RLock()
        self.replays = collections.OrderedDict()
        self.replay_bytes = 0

    def status(self):
        return {"protocol": 1, "instance_id": self.instance_id, "source_version": "0.1.0",
                "capabilities": ["status", "windows", "processes", "select", "focus", "screenshot", "action", "release", "info", "list", "read_range", "create_text", "replace_text", "launch"],
                "roots": [g.describe() for g in self.files.grants.values()],
                "native": self.backend.status(), "actions": self.actions.describe(),
                "limits": {"text_bytes": MAX_TEXT_BYTES, "read_bytes": MAX_READ_BYTES,
                           "root_grants": MAX_ROOT_GRANTS, "rpc_timeout_ms": MAX_RPC_TIMEOUT_MS,
                           "replay_ttl_seconds": REPLAY_TTL_SECONDS}}

    def dispatch(self, op, args):
        if op in ("status", "windows", "processes", "focus", "release") and args:
            raise CompanionError("BAD_ARGUMENT", "This operation does not accept arguments")
        methods = {"status": lambda a: self.status(), "windows": lambda a: self.backend.windows(),
                   "processes": lambda a: self.backend.processes(),
                   "select": self.backend.select, "screenshot": self.backend.screenshot, "action": self.backend.action,
                   "focus": lambda a: self.backend.focus(),
                   "release": lambda a: self.backend.release(), "launch": self.actions.launch,
                   "info": self.files.info, "list": self.files.list, "read_range": self.files.read_range,
                   "create_text": self.files.create_text, "replace_text": self.files.replace_text}
        if op not in methods:
            raise CompanionError("UNKNOWN_OPERATION", "Operation is not supported")
        return methods[op](args)

    def rpc(self, payload):
        entered_at = self.clock()
        if (not isinstance(payload, dict) or not {"id", "op", "args"}.issubset(payload)
                or set(payload) - {"id", "op", "args", "timeout_ms"}):
            raise CompanionError("BAD_REQUEST", "Request must contain id, op, args, and optionally timeout_ms")
        request_id, op, args = payload["id"], payload["op"], payload["args"]
        timeout_ms = integer(payload.get("timeout_ms", MAX_RPC_TIMEOUT_MS), "timeout_ms", 1, MAX_RPC_TIMEOUT_MS)
        deadline = entered_at + timeout_ms / 1000.0
        if not isinstance(request_id, str) or not REQUEST_ID.fullmatch(request_id):
            raise CompanionError("BAD_REQUEST", "id must be a simple unique string of at most 128 characters")
        if not isinstance(op, str) or not isinstance(args, dict):
            raise CompanionError("BAD_REQUEST", "op must be a string and args must be an object")
        fingerprint_payload = dict(payload, timeout_ms=timeout_ms)
        fingerprint = hashlib.sha256(json.dumps(fingerprint_payload, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")).hexdigest()
        with self.lock:
            now = self.clock()
            for key in list(self.replays):
                if now - self.replays[key][0] > REPLAY_TTL_SECONDS:
                    self.replay_bytes -= self.replays.pop(key)[3]
            if request_id in self.replays:
                _, prior_fingerprint, response, _ = self.replays[request_id]
                if not hmac.compare_digest(fingerprint, prior_fingerprint):
                    raise CompanionError("REPLAY_CONFLICT", "This id already belongs to a different request")
                if response is None:
                    raise CompanionError("REPLAY_RESULT_EXPIRED", "Large cached result expired; this request was not executed again")
                return response
            if len(self.replays) >= MAX_REPLAY_ENTRIES:
                raise CompanionError("REPLAY_CAPACITY", "Session request cache is full; wait for prior entries to expire")
            try:
                if self.clock() >= deadline:
                    raise CompanionError("REQUEST_EXPIRED", "Request expired while queued; no operation was executed")
                response = {"id": request_id, "ok": True, "result": self.dispatch(op, args)}
            except Exception as exc:
                if hasattr(exc, "to_dict") and hasattr(exc, "code"):
                    error = exc.to_dict()
                elif isinstance(exc, FileExistsError):
                    error = {"code": "OUTPUT_EXISTS", "message": "Output already exists; create a new candidate and retain the existing file"}
                elif isinstance(exc, FileNotFoundError):
                    error = {"code": "NOT_FOUND", "message": "Requested file or directory does not exist"}
                elif isinstance(exc, PermissionError):
                    error = {"code": "ACCESS_DENIED", "message": "Windows or file permissions denied this operation"}
                else:
                    error = {"code": "OPERATION_FAILED", "message": "Operation failed; inspect the local companion console"}
                    print("Operation failed:", op, type(exc).__name__, file=sys.stderr)
                response = {"id": request_id, "ok": False, "error": error}
            size = len(json.dumps(response, separators=(",", ":"), allow_nan=False).encode("utf-8"))
            self.replays[request_id] = (now, fingerprint, response, size)
            self.replay_bytes += size
            # Keep small receipts and tombstones; discard large screenshot/read
            # bodies before they can make the long-lived process consume GBs.
            if self.replay_bytes > MAX_REPLAY_BYTES:
                for key, (created, digest, body, count) in list(self.replays.items()):
                    if count > 16384:
                        self.replays[key] = (created, digest, None, 0)
                        self.replay_bytes -= count
                    if self.replay_bytes <= MAX_REPLAY_BYTES:
                        break
            return response


def json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON keys are not allowed")
        result[key] = value
    return result


class CompanionServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = False

    def __init__(self, companion, port=0):
        self.companion = companion
        super().__init__(("127.0.0.1", port), CompanionHandler)


class CompanionHandler(BaseHTTPRequestHandler):
    server_version = "ValeCompanion/1"
    sys_version = ""

    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def log_message(self, format_string, *args):
        # Do not log URLs, bearer tokens, paths, or file contents.
        return

    def respond(self, status, payload=None, cors=False, preflight=False):
        data = b"" if payload is None else json.dumps(payload, separators=(",", ":"), allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Connection", "close")
        self.send_header("Vary", "Origin")
        if cors:
            self.send_header("Access-Control-Allow-Origin", APP_ORIGIN)
        if preflight:
            self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "authorization, content-type")
            self.send_header("Access-Control-Max-Age", "300")
            if self.headers.get("Access-Control-Request-Private-Network", "").lower() == "true":
                self.send_header("Access-Control-Allow-Private-Network", "true")
        self.end_headers()
        if data:
            self.wfile.write(data)
        self.close_connection = True

    def reject(self, status, code, message, cors=False):
        self.respond(status, {"ok": False, "error": {"code": code, "message": message}}, cors=cors)

    def source_allowed(self):
        port = self.server.server_port
        if self.client_address[0] != "127.0.0.1":
            self.reject(403, "LOOPBACK_REQUIRED", "Only local loopback connections are accepted")
            return False
        if len(self.headers.get_all("Host", [])) != 1 or self.headers["Host"] not in ("127.0.0.1:%s" % port, "localhost:%s" % port):
            self.reject(403, "BAD_HOST", "Host must identify this loopback listener")
            return False
        if len(self.headers.get_all("Origin", [])) != 1 or self.headers["Origin"] != APP_ORIGIN:
            self.reject(403, "BAD_ORIGIN", "Only the Vale File Link origin is accepted")
            return False
        if self.path != "/rpc":
            self.reject(404, "NOT_FOUND", "Only /rpc exists", cors=True)
            return False
        return True

    def do_OPTIONS(self):
        if not self.source_allowed():
            return
        if self.headers.get("Access-Control-Request-Method") != "POST":
            self.reject(403, "BAD_PREFLIGHT", "Only POST may be requested", cors=True)
            return
        requested = {v.strip().lower() for v in self.headers.get("Access-Control-Request-Headers", "").split(",") if v.strip()}
        if not requested.issubset({"authorization", "content-type"}):
            self.reject(403, "BAD_PREFLIGHT", "Requested headers are not allowed", cors=True)
            return
        self.respond(204, cors=True, preflight=True)

    def do_POST(self):
        if not self.source_allowed():
            return
        authorization = self.headers.get_all("Authorization", [])
        if len(authorization) != 1 or not hmac.compare_digest(authorization[0].encode("utf-8"), ("Bearer " + self.server.companion.token).encode("ascii")):
            self.reject(401, "AUTH_REQUIRED", "A current native session bearer token is required", cors=True)
            return
        if self.headers.get("Transfer-Encoding") or len(self.headers.get_all("Content-Length", [])) != 1:
            self.reject(400, "BAD_LENGTH", "A single Content-Length is required", cors=True)
            return
        try:
            length = int(self.headers["Content-Length"])
        except (ValueError, TypeError):
            length = -1
        if not 1 <= length <= MAX_REQUEST_BYTES:
            self.reject(413, "REQUEST_LIMIT", "JSON request must be between 1 byte and 1 MiB", cors=True)
            return
        if self.headers.get("Content-Type", "").split(";", 1)[0].strip().lower() != "application/json":
            self.reject(415, "JSON_REQUIRED", "Content-Type must be application/json", cors=True)
            return
        try:
            raw = self.rfile.read(length)
            if len(raw) != length:
                raise ValueError("Incomplete body")
            payload = json.loads(raw.decode("utf-8"), object_pairs_hook=json_object,
                                 parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Non-finite JSON number")))
            result = self.server.companion.rpc(payload)
        except CompanionError as exc:
            self.reject(400, exc.code, exc.message, cors=True)
            return
        except (ValueError, UnicodeError, RecursionError):
            self.reject(400, "BAD_JSON", "Request must be finite, valid UTF-8 JSON without duplicate keys", cors=True)
            return
        except (TimeoutError, OSError):
            self.reject(408, "READ_TIMEOUT", "Request body was not received completely", cors=True)
            return
        self.respond(200, result, cors=True)

    def do_GET(self):
        self.reject(405, "METHOD_NOT_ALLOWED", "This service exposes authenticated POST RPC only")


def pick_roots():
    """Local owner interaction; no remote operation changes these grants."""
    try:
        import tkinter as tk
        from tkinter import filedialog, messagebox
        window = tk.Tk()
    except Exception as exc:
        # Some Python installations omit Tk; the visible console is a fallback.
        print("Folder picker unavailable (%s). Enter grants in this local console." % type(exc).__name__)
        grants = []
        while True:
            folder = input("Folder to share (blank finishes): ").strip().strip('"')
            if not folder:
                break
            writable = input("Allow text edits only under Vale-Export? Type YES, otherwise read-only: ").strip() == "YES"
            try:
                grant = RootGrant(folder, writable)
                validated_grants(grants + [grant])
                grants.append(grant)
            except CompanionError as error:
                print("Folder was not added:", error.message)
        return grants
    window.title("Vale Native Companion — choose local folders")
    window.geometry("760x420")
    grants = []
    started = [False]
    tk.Label(window, text="Choose up to eight distinct folders this session may read. Writes stay inside Vale-Export.\nMinecraft control is limited to a selected Java/LWJGL window.", justify="left").pack(padx=16, pady=12, anchor="w")
    listing = tk.Listbox(window, width=100, height=12)
    listing.pack(padx=16, pady=6, fill="both", expand=True)

    def add_folder():
        folder = filedialog.askdirectory(parent=window, title="Select a folder to grant")
        if not folder:
            return
        writable = messagebox.askyesno("Optional output edits", "Allow text creation and backed-up edits only under Vale-Export in this folder?\n\nChoose No for read-only access.", default="no", parent=window)
        try:
            grant = RootGrant(folder, writable)
            validated_grants(grants + [grant])
            grants.append(grant)
            listing.insert("end", ("[Vale-Export writes] " if writable else "[Read-only] ") + str(grant.path))
        except CompanionError as exc:
            messagebox.showerror("Folder unavailable", exc.message, parent=window)

    def start():
        started[0] = True
        window.destroy()

    controls = tk.Frame(window)
    controls.pack(pady=12)
    tk.Button(controls, text="Add folder", command=add_folder).pack(side="left", padx=8)
    tk.Button(controls, text="Start this session", command=start).pack(side="left", padx=8)
    tk.Button(controls, text="Cancel", command=window.destroy).pack(side="left", padx=8)
    window.mainloop()
    if not started[0]:
        raise CompanionError("CANCELED", "Local setup was canceled")
    return grants


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--setup", action="store_true", help="Show the local folder picker (default)")
    parser.add_argument("--root", action="append", default=[], help="Locally grant a read-only folder")
    parser.add_argument("--writable-root", action="append", default=[], help="Locally grant a folder with writes only under Vale-Export")
    parser.add_argument("--actions-file", help="Optional local JSON array of fixed, reviewed executable registrations")
    parser.add_argument("--no-browser", action="store_true", help="Print the local session link instead of opening the browser")
    options = parser.parse_args()
    if sys.version_info < (3, 8):
        raise CompanionError("PYTHON_VERSION", "Python 3.8 or newer is required")
    from vale_windows import create_backend
    backend = create_backend()
    grants = ([RootGrant(p) for p in options.root] + [RootGrant(p, True) for p in options.writable_root]
              if options.root or options.writable_root else pick_roots())
    actions = None
    if options.actions_file:
        with open(options.actions_file, "r", encoding="utf-8") as source:
            actions = json.load(source)
        if not isinstance(actions, list):
            raise CompanionError("BAD_ACTION", "Action registrations must be a local JSON array")
    companion = Companion(backend, grants, actions)
    server = CompanionServer(companion)
    url = APP_ORIGIN + "/#native=" + companion.token + "&port=" + str(server.server_port)
    print("Vale Native Companion is listening only on 127.0.0.1:%s." % server.server_port)
    print("Keep this window open. Press Ctrl+C to stop and release held inputs.")
    print("This companion uses no OpenAI API calls or API key.")
    if options.no_browser or not webbrowser.open(url, new=2):
        print("Open this private, current-session link locally; do not share it:\n" + url)
    try:
        server.serve_forever(poll_interval=0.25)
    except KeyboardInterrupt:
        pass
    finally:
        backend.stop()
        server.server_close()
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print("Companion stopped:", getattr(error, "message", str(error)), file=sys.stderr)
        sys.exit(1)
