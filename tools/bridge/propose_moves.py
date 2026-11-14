"""Generate private, inactive visual proposals; registry IDs do not prove behavior.

Claude's ordered RULES are retained from 3a257fe16c302d2a0b0ee9fd5c07072f789ddf41.
The strict schema, bounds, ownership defaults and output protections are original
integration work. See README.md for review limits and the activation boundary.
"""

import argparse
import csv
import io
import json
import os
from pathlib import Path
import re
import sys

MAX_INPUT_BYTES = 2 * 1024 * 1024
MAX_INPUT_IDS = 16384
MAX_MOVES = 512
MAX_PROPOSAL_BYTES = 128 * 1024
MAX_ARSENAL_BYTES = 1024 * 1024
PROPOSAL_NAME = "bridge-moves.proposed.json"
REVIEW_NAME = "bridge-moves.review.csv"
ID_PATTERN = re.compile(r"[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,96}", re.ASCII)
BASE_STYLES = frozenset(("spiral", "charge", "fireedge", "windedge", "lava",
                         "lightmode", "none", "dodge"))
DEFAULT_ARSENAL = (Path(__file__).resolve().parents[2] / "modules" / "vale-orders" /
                   "src/main/java/village/vale/arsenal/ArsenalSpec.java")


class ProposalError(ValueError):
    """A rejected input or output; no reviewed runtime mapping is written."""

# Ordered rules: first match wins. Specific jutsu names before broad element words.
RULES = [
    ("rasenshuriken", "spiral"), ("rasengan", "spiral"), ("rasen", "spiral"), ("bijudama", "spiral"), ("tailed_beast_bomb", "spiral"),
    ("chidori", "charge"), ("raikiri", "charge"), ("kirin", "charge"), ("lightning", "charge"), ("raiton", "charge"), ("thunder", "charge"),
    ("amaterasu", "fireedge"), ("katon", "fireedge"), ("fireball", "fireedge"), ("fire", "fireedge"), ("flame", "fireedge"), ("blaze", "fireedge"), ("ember", "fireedge"),
    ("yoton", "lava"), ("lava", "lava"), ("magma", "lava"), ("scorch", "lava"),
    ("futon", "windedge"), ("wind", "windedge"), ("vacuum", "windedge"), ("gale", "windedge"),
    ("suiton", "weapon:mist_censer"), ("water", "weapon:mist_censer"), ("mist", "weapon:mist_censer"), ("bubble", "weapon:mist_censer"), ("shark", "weapon:mist_censer"),
    ("doton", "weapon:siege_maul"), ("earth", "weapon:siege_maul"), ("rock", "weapon:siege_maul"), ("stone", "weapon:siege_maul"),
    ("hyoton", "weapon:rime_pin"), ("ice", "weapon:rime_pin"), ("snow", "weapon:rime_pin"),
    ("mokuton", "weapon:thorn_staff"), ("wood", "weapon:thorn_staff"), ("root", "weapon:thorn_staff"),
    ("sand", "weapon:ward_fan"), ("gunbai", "weapon:ward_fan"), ("fan", "weapon:ward_fan"),
    ("kunai", "weapon:waymark_knife"), ("shuriken", "weapon:returning_sickle"), ("senbon", "weapon:venom_needle"), ("needle", "weapon:venom_needle"),
    ("explosive", "weapon:seal_charge"), ("paper_bomb", "weapon:seal_charge"), ("tag", "weapon:seal_charge"),
    ("scythe", "weapon:sweep_glaive"), ("sickle", "weapon:returning_sickle"), ("chain", "weapon:binder_spear"), ("spear", "weapon:binder_spear"),
    ("hammer", "weapon:siege_maul"), ("axe", "weapon:breakaxe"), ("claw", "weapon:crescent_talons"), ("staff", "weapon:thorn_staff"),
    ("puppet", "weapon:needle_spool"), ("bow", "weapon:lens_bow"),
    ("sword", "weapon:thunder_edge"), ("katana", "weapon:thunder_edge"), ("blade", "weapon:thunder_edge"), ("samehada", "weapon:thunder_edge"), ("kubikiribocho", "weapon:thunder_edge"),
    ("sage", "lightmode"), ("chakra_mode", "lightmode"), ("cloak", "lightmode"),
]
# Non-move hints, not proof of behavior. Delimiter boundaries prevent ore/forest,
# log/dialog and food/foodless substring exclusions. Original skip terms retained.
SKIP = ("helmet", "chestplate", "leggings", "boots", "body", "_eye", "sharingan", "rinnegan", "byakugan", "spawn_egg",
        "ore", "block", "planks", "log", "leaves", "food", "ramen", "dango", "record", "banner", "scroll_empty")

def validate_id(rid):
    if not isinstance(rid, str) or len(rid) > 128 or not ID_PATTERN.fullmatch(rid):
        raise ProposalError("registry ID must be <=128 ASCII characters with one lowercase namespace:path")
    return rid


def skip_hint(path):
    tokens = re.split(r"[_./-]+", path)
    for hint in SKIP:
        parts = hint.strip("_").split("_")
        if any(tokens[i:i + len(parts)] == parts
               for i in range(len(tokens) - len(parts) + 1)):
            return True
    return False


def propose(rid):
    """Return the original first matching style/keyword after bounded skip hints."""
    validate_id(rid)
    path = rid.split(":", 1)[1]
    if skip_hint(path):
        return None, "skip"
    for key, style in RULES:
        if key in path:
            return style, key
    return None, ""


def read_bounded(path, limit, label):
    try:
        with Path(path).open("rb") as stream:
            raw = stream.read(limit + 1)
    except OSError as exc:
        raise ProposalError(f"cannot read {label}: {exc}") from exc
    if len(raw) > limit:
        raise ProposalError(f"{label} exceeds {limit} bytes")
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ProposalError(f"{label} must be UTF-8") from exc


def unique_object(pairs):
    obj = {}
    for key, value in pairs:
        if key in obj:
            raise ProposalError("duplicate JSON object key")
        obj[key] = value
    return obj


def reject_constant(value):
    raise ProposalError(f"non-finite JSON number is forbidden: {value}")


def load_registry(src):
    try:
        data = json.loads(read_bounded(src, MAX_INPUT_BYTES, "registry input"),
                          object_pairs_hook=unique_object, parse_constant=reject_constant)
    except ProposalError:
        raise
    except (ValueError, RecursionError) as exc:
        raise ProposalError("registry input must be a bounded JSON object") from exc
    return validate_registry(data)


def validate_registry(data):
    if not isinstance(data, dict) or set(data) - {"items", "entities"}:
        raise ProposalError("registry input allows only items and entities keys")
    result = []
    for plural, kind in (("entities", "entity"), ("items", "item")):
        ids = data.get(plural, [])
        if not isinstance(ids, list):
            raise ProposalError(f"{plural} must be a list")
        if len(ids) > MAX_INPUT_IDS or len(result) + len(ids) > MAX_INPUT_IDS:
            raise ProposalError(f"registry input exceeds {MAX_INPUT_IDS} total IDs")
        seen = set()
        for rid in ids:
            validate_id(rid)
            if rid in seen:
                raise ProposalError(f"duplicate {kind} registry ID")
            seen.add(rid)
            result.append((kind, rid))
    return sorted(result)


def load_arsenal_keys(source):
    text = read_bounded(source, MAX_ARSENAL_BYTES, "ArsenalSpec source")
    enum = re.search(r"public enum ArsenalSpec\s*\{(.*?)\bpublic enum Role\b", text, re.S)
    if enum is None:
        raise ProposalError("ArsenalSpec source has no supported enum declaration")
    keys = re.findall(r'^\s*[A-Z][A-Z0-9_]*\("([a-z0-9_]+)"', enum.group(1), re.M)
    if not keys or len(set(keys)) != len(keys):
        raise ProposalError("ArsenalSpec source must declare unique original weapon keys")
    return frozenset(keys)


def validate_rules(arsenal_keys):
    styles = BASE_STYLES | {"weapon:" + key for key in arsenal_keys}
    for keyword, style in RULES:
        if not keyword or len(style) > 64 or style not in styles:
            raise ProposalError(f"rule style absent from current original ArsenalSpec: {style}")
    return styles


def csv_cell(value):
    # CSV quoting alone does not stop spreadsheet formulas (notably -namespace).
    return "'" + value if value and value[0] in "=+-@\t\r\n" else value


def build_candidates(registry, arsenal_keys):
    styles = validate_rules(arsenal_keys)
    moves, review = [], []
    for kind, rid in registry:
        when, owner = (("damage", "damage_source") if kind == "entity"
                       else ("melee", "direct_player"))
        if rid.startswith("minecraft:"):
            style, why = None, "minecraft"
        else:
            style, why = propose(rid)
        if style:
            if style not in styles or len(style) > 64:
                raise ProposalError("proposal contains an unsupported style")
            moves.append(dict(kind=kind, id=rid, when=when, owner=owner, style=style))
        limitation = ("Registry name only; damage-source player attribution needs review; "
                      "spawn owner API and jutsu cast are not established."
                      if kind == "entity" else
                      "Registry name only; direct-player melee needs review; "
                      "item use and jutsu cast are not established.")
        review.append((kind, rid, style or "", why, when, owner, "false", limitation))
    if len(moves) > MAX_MOVES:
        raise ProposalError(f"{len(moves)} proposals exceed {MAX_MOVES}; split the reviewed input, no truncation")
    doc = dict(schema=1, verified=False, moves=moves)
    proposal_bytes = (json.dumps(doc, ensure_ascii=True, indent=2) + "\n").encode("utf-8")
    if len(proposal_bytes) > MAX_PROPOSAL_BYTES:
        raise ProposalError(f"proposal exceeds {MAX_PROPOSAL_BYTES} UTF-8 bytes; split the reviewed input")
    csv_buffer = io.StringIO(newline="")
    writer = csv.writer(csv_buffer, lineterminator="\n")
    writer.writerow(("kind", "registry_id", "proposed_style", "matched", "when",
                     "owner", "verified", "review_limit"))
    writer.writerows(tuple(csv_cell(cell) for cell in row) for row in review)
    return proposal_bytes, csv_buffer.getvalue().encode("utf-8"), len(moves), len(review)


def write_candidates(out, proposal, review):
    target = Path(out)
    paths = (target / PROPOSAL_NAME, target / REVIEW_NAME)
    if any(os.path.lexists(path) for path in paths):
        raise ProposalError("candidate already exists; choose a fresh output directory")
    created = []
    try:
        target.mkdir(parents=True, exist_ok=True)
        # Exclusive creation also refuses candidates created after the preflight.
        for path, content in zip(paths, (proposal, review)):
            with path.open("xb") as stream:
                created.append(path)
                stream.write(content)
    except OSError as exc:
        for path in created:
            path.unlink()
        raise ProposalError(f"cannot create new candidates: {exc}") from exc


def generate(src, out, arsenal_source=DEFAULT_ARSENAL):
    registry = load_registry(src)
    arsenal_keys = load_arsenal_keys(arsenal_source)
    proposal, review, proposed_count, reviewed_count = build_candidates(registry, arsenal_keys)
    # Every input, rule, ID, size and rendered CSV has passed before mkdir/open.
    write_candidates(out, proposal, review)
    return dict(ids=reviewed_count, proposed=proposed_count, proposal_bytes=len(proposal),
                arsenal_keys=len(arsenal_keys), verified=False)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("src", type=Path, help="private registry JSON with items/entities lists")
    parser.add_argument("out", type=Path, help="fresh private output directory")
    parser.add_argument("--arsenal-source", type=Path, default=DEFAULT_ARSENAL,
                        help="current original ArsenalSpec.java (defaults to this repository)")
    args = parser.parse_args(argv)
    try:
        result = generate(args.src, args.out, args.arsenal_source)
    except ProposalError as exc:
        print(f"proposal rejected: {exc}", file=sys.stderr)
        return 2
    print(f"{result['ids']} IDs reviewed, {result['proposed']} inactive proposals, "
          f"{result['proposal_bytes']} UTF-8 bytes; verified=false")
    return 0

if __name__ == "__main__":
    sys.exit(main())
