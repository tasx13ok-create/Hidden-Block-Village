"""Builds a PROPOSED bridge-moves.json from a list of real registry names.

Input: a JSON file with "items" and "entities" lists of registry IDs (for example the owner's
private runtime-registry-names.json). Output: a proposal in the bridge-moves.json format, plus a
review CSV. Every entry is a keyword-based PROPOSAL marked unverified; nothing is enabled until
someone confirms it in game (/narutobridge test <style>, then /narutobridge map <style>).
Inputs and outputs that contain the private registry list belong in local/ (gitignored).

Usage: python3 tools/bridge/propose_moves.py <registry-names.json> <out-dir>
"""
import csv, json, os, sys

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
# Things that are never moves even if a keyword matches (armour, eyes, blocks, food, spawn eggs, decorations).
SKIP = ("helmet", "chestplate", "leggings", "boots", "body", "_eye", "sharingan", "rinnegan", "byakugan", "spawn_egg",
        "ore", "block", "planks", "log", "leaves", "food", "ramen", "dango", "record", "banner", "scroll_empty")

def propose(rid):
    path = rid.split(":", 1)[-1].lower()
    if any(s in path for s in SKIP): return None, "skip"
    for key, style in RULES:
        if key in path: return style, key
    return None, ""

def main(src, out):
    data = json.load(open(src, encoding="utf-8"))
    os.makedirs(out, exist_ok=True)
    moves, rows = {}, []
    for kind in ("items", "entities"):
        for rid in data.get(kind, []):
            if rid.startswith("minecraft:"): continue
            style, why = propose(rid)
            rows.append((kind, rid, style or "", why, "false"))
            if style: moves[rid] = style
    doc = {"_help": "PROPOSED, UNVERIFIED keyword mapping from real registry names. Confirm each entry in game before enabling: /narutobridge test <style>, then /narutobridge map <style> while holding the item.",
           "verified": False, "moves": dict(sorted(moves.items()))}
    json.dump(doc, open(os.path.join(out, "bridge-moves.proposed.json"), "w", encoding="utf-8"), indent=2)
    with open(os.path.join(out, "bridge-moves.review.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(["kind", "registry_id", "proposed_style", "matched", "verified"]); w.writerows(rows)
    counts = {}
    for s in moves.values(): counts[s] = counts.get(s, 0) + 1
    print(f"{len(rows)} ids, {len(moves)} proposed, {sum(1 for r in rows if r[3]=='skip')} skipped as non-moves")
    for s, n in sorted(counts.items(), key=lambda x: -x[1]): print(f"  {s}: {n}")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
