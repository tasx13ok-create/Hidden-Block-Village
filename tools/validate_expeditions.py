#!/usr/bin/env python3
"""Validate dungeon-gate mission/resident/dialogue content against the repository's real data.

Cross-checks: contract prerequisites against Missions.java, encounter profiles against
encounter-profiles.json, reward items against registered field items, dialogue against the
Residents DialogueBook limits, and stage graphs for reachability, termination and retries.
Exit 0 when valid, 1 on errors. ``--require-anchors`` also fails on unmeasured anchors.
"""
import argparse
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ID = re.compile(r'^[a-z][a-z0-9_]{0,47}$')
DUNGEONS = {'sakura_v17', 'cloud_v17', 'sand_v17', 'mist_v17', 'nether_v17'}
ANCHOR_KINDS = {'arrival', 'marker', 'encounter', 'checkpoint'}
PHASES = {'dawn', 'day', 'dusk', 'night'}
# Objective -> required fields. "existing" types map onto Missions.java today; the rest need
# the adapter described in content/expeditions/README.md.
OBJECTIVES = {
    'reach_marker': ('proposed', {'anchor', 'radius'}),
    'interact_marker': ('proposed', {'anchor'}),
    'interact_sequence': ('proposed', {'anchors', 'ordered'}),
    'choose_branch': ('proposed', set()),
    'defeat_group': ('proposed', {'anchor', 'profiles'}),
    'escort_actor': ('proposed', {'actor', 'to', 'max_seconds'}),
    'protect_actor': ('proposed', {'actor', 'seconds'}),
    'defend_point': ('proposed', {'anchor', 'seconds', 'profiles'}),
    'deliver_items': ('proposed', {'item', 'count'}),
}
MAX_XP, MAX_MERIT = 1000, 6  # Missions.java tops out at 900 XP / 6 merit
ALLOWED_DELIVERY = {'minecraft:bread', 'minecraft:wheat', 'minecraft:cooked_beef', 'minecraft:carrot'}
DIALOGUE_ACTIONS = {'PAGE', 'ACCEPT_CONTRACT', 'CLAIM_CONTRACT'}


def repo_facts(root):
    missions = (root / 'modules/vale-orders/src/main/java/village/vale/Missions.java').read_text(encoding='utf-8')
    contracts = set(re.findall(r'add\("([a-z0-9_]+)"', missions))
    profiles = json.loads((root / 'modules/vale-encounters/src/main/resources/encounter-profiles.json').read_text(encoding='utf-8'))
    items = (root / 'modules/vale-orders/src/main/java/village/vale/FieldItems.java').read_text(encoding='utf-8')
    foods = re.search(r'FOODS=\{([^}]*)\}', items)
    ledger_items = set(re.findall(r'"([a-z_]+)"', foods.group(1))) if foods else set()
    return {'contracts': contracts, 'profiles': {p['key']: p['kind'] for p in profiles}, 'ledger_items': ledger_items}


def _text(errors, where, value, limit):
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        errors.append('%s: text missing or longer than %d' % (where, limit))
    elif any(ord(c) < 32 or ord(c) == 127 for c in value):
        errors.append('%s: control character' % where)


def validate_dialogue(doc, mission_ids, errors, where):
    if doc.get('schema') != 1 or not ID.match(str(doc.get('start', ''))):
        errors.append('%s: needs schema 1 and a start page' % where)
        return
    pages = doc.get('pages')
    if not isinstance(pages, list) or not 1 <= len(pages) <= 16:
        errors.append('%s: 1-16 pages required' % where)
        return
    by_id = {}
    for page in pages:
        pid = page.get('id', '')
        if not ID.match(pid) or pid in by_id:
            errors.append('%s: invalid or duplicate page %r' % (where, pid))
        by_id[pid] = page
        lines = page.get('lines', [])
        if not isinstance(lines, list) or not 1 <= len(lines) <= 4:
            errors.append('%s/%s: 1-4 lines required' % (where, pid))
        for n, line in enumerate(lines):
            _text(errors, '%s/%s line %d' % (where, pid, n), line, 200)
        choices = page.get('choices', [])
        if not isinstance(choices, list) or not 1 <= len(choices) <= 6:
            errors.append('%s/%s: 1-6 choices required' % (where, pid))
            continue
        seen = set()
        for c in choices:
            cid = c.get('id', '')
            if not ID.match(cid) or cid in seen:
                errors.append('%s/%s: invalid or duplicate choice %r' % (where, pid, cid))
            seen.add(cid)
            _text(errors, '%s/%s/%s label' % (where, pid, cid), c.get('label'), 64)
            action = c.get('action')
            if action not in DIALOGUE_ACTIONS:
                errors.append('%s/%s/%s: unsupported action %r' % (where, pid, cid, action))
            needs_target = action in ('PAGE', 'ACCEPT_CONTRACT')
            if needs_target != ('target' in c):
                errors.append('%s/%s/%s: target presence does not match action' % (where, pid, cid))
            if action == 'ACCEPT_CONTRACT' and c.get('target') not in mission_ids:
                errors.append('%s/%s/%s: accepts unknown or foreign mission %r' % (where, pid, cid, c.get('target')))
    for page in pages:
        for c in page.get('choices', []):
            if c.get('action') == 'PAGE' and c.get('target') not in by_id:
                errors.append('%s: dangling page target %r' % (where, c.get('target')))
    starts = [doc['start']] + ([doc['after_clear_start']] if doc.get('after_clear_start') else [])
    for s in starts:
        if s not in by_id:
            errors.append('%s: start page %r missing' % (where, s))
    reach, stack = set(), [s for s in starts if s in by_id]
    while stack:
        pid = stack.pop()
        if pid in reach:
            continue
        reach.add(pid)
        stack.extend(c['target'] for c in by_id[pid].get('choices', [])
                     if c.get('action') == 'PAGE' and c.get('target') in by_id)
    for pid in set(by_id) - reach:
        errors.append('%s: page %r is unreachable' % (where, pid))


def validate_mission(m, ctx, errors):
    mid = m.get('id', '?')
    w = 'mission %s' % mid
    anchors, facts = ctx['anchors'], ctx['facts']
    if not ID.match(mid):
        errors.append('%s: invalid id' % w)
    if m.get('dungeon') not in DUNGEONS:
        errors.append('%s: unknown dungeon' % w)
    giver = ctx['residents'].get(m.get('giver'))
    if giver is None or giver.get('dungeon') != m.get('dungeon'):
        errors.append('%s: giver missing or in another dungeon' % w)
    if not isinstance(m.get('level'), int) or not 1 <= m['level'] <= 20:
        errors.append('%s: level must be 1-20' % w)
    for c in m.get('prerequisites', {}).get('contracts_completed', []):
        if c not in facts['contracts']:
            errors.append('%s: prerequisite contract %r does not exist in Missions.java' % (w, c))
    tm = m.get('target_minutes')
    if not (isinstance(tm, list) and len(tm) == 2 and 1 <= tm[0] <= tm[1] <= 25):
        errors.append('%s: target_minutes must be a short [min, max] within 25' % w)
    stages = {}
    for s in m.get('stages', []):
        if not ID.match(s.get('id', '')) or s['id'] in stages:
            errors.append('%s: invalid/duplicate stage %r' % (w, s.get('id')))
        stages[s.get('id')] = s
    if m.get('start_stage') not in stages:
        errors.append('%s: start stage missing' % w)
        return
    used = set()
    edges = {}
    for sid, s in stages.items():
        _text(errors, '%s/%s text' % (w, sid), s.get('text'), 200)
        obj = s.get('objective', {})
        otype = obj.get('type')
        if otype not in OBJECTIVES:
            errors.append('%s/%s: unknown objective %r' % (w, sid, otype))
            continue
        used.add(otype)
        missing = OBJECTIVES[otype][1] - set(obj)
        if missing:
            errors.append('%s/%s: %s missing %s' % (w, sid, otype, sorted(missing)))
        refs = [obj[k] for k in ('anchor', 'to') if k in obj] + list(obj.get('anchors', []))
        for a in refs:
            if a not in anchors:
                errors.append('%s/%s: unknown anchor %r' % (w, sid, a))
            elif anchors[a]['dungeon'] != m.get('dungeon'):
                errors.append('%s/%s: anchor %r belongs to another dungeon' % (w, sid, a))
        for p in obj.get('profiles', []):
            kind = facts['profiles'].get(p.get('key'))
            if kind != 'mob':
                errors.append('%s/%s: profile %r is not an existing mob profile' % (w, sid, p.get('key')))
            if not isinstance(p.get('count'), int) or not 1 <= p['count'] <= 6:
                errors.append('%s/%s: profile count must be 1-6' % (w, sid))
        for key, hi in (('time_limit_seconds', 300), ('seconds', 180), ('max_seconds', 300), ('radius', 8)):
            if key in obj and not (isinstance(obj[key], int) and 1 <= obj[key] <= hi):
                errors.append('%s/%s: %s must be 1-%d' % (w, sid, key, hi))
        if otype == 'deliver_items' and (obj.get('item') not in ALLOWED_DELIVERY or not 1 <= obj.get('count', 0) <= 64):
            errors.append('%s/%s: delivery must be a plain allowed item, 1-64' % (w, sid))
        nexts = []
        if otype == 'choose_branch':
            branches = s.get('branches', [])
            if len(branches) < 2:
                errors.append('%s/%s: a choice needs at least two branches' % (w, sid))
            for b in branches:
                _text(errors, '%s/%s branch label' % (w, sid), b.get('label'), 64)
                nexts.append(b.get('next'))
        else:
            nexts.append(s.get('next'))
        for n in nexts:
            if n is not None and n not in stages:
                errors.append('%s/%s: next stage %r missing' % (w, sid, n))
        fail = s.get('on_fail')
        if fail is not None and fail.get('retry_from') not in stages:
            errors.append('%s/%s: retry_from must name a stage' % (w, sid))
        if obj.get('time_limit_seconds') and fail is None:
            errors.append('%s/%s: a timed objective needs an on_fail retry' % (w, sid))
        edges[sid] = [n for n in nexts if n in stages]
    # Reachability, termination and acyclic forward graph (retries are separate edges).
    order, state = [], {}

    def visit(sid):
        if state.get(sid) == 1:
            errors.append('%s: forward stage cycle through %r' % (w, sid))
            return
        if state.get(sid) == 2:
            return
        state[sid] = 1
        for n in edges.get(sid, []):
            visit(n)
        state[sid] = 2
        order.append(sid)
    visit(m['start_stage'])
    for sid in set(stages) - set(state):
        errors.append('%s: stage %r unreachable from start' % (w, sid))
    if not any(stages[s].get('objective', {}).get('type') != 'choose_branch' and stages[s].get('next') is None
               for s in state):
        errors.append('%s: no completing stage' % w)
    for key in ('death', 'disconnect', 'abandon'):
        if not m.get('failure', {}).get(key):
            errors.append('%s: failure policy for %s missing' % (w, key))
    r = m.get('rewards', {})
    first, repeat = r.get('first_clear', {}), r.get('repeat', {})
    for label, block in (('first_clear', first), ('repeat', repeat)):
        if not (0 <= block.get('xp', -1) <= MAX_XP and 0 <= block.get('merit', -1) <= MAX_MERIT):
            errors.append('%s: %s xp/merit outside Missions scale' % (w, label))
        for it in block.get('ledger_items', []):
            if it.get('key') not in facts['ledger_items'] or not 1 <= it.get('count', 0) <= 16:
                errors.append('%s: %s ledger item %r is not a registered field item' % (w, label, it.get('key')))
    if repeat.get('xp', 0) >= first.get('xp', 0) or repeat.get('merit', 0) > first.get('merit', 0) or repeat.get('unlocks'):
        errors.append('%s: repeat rewards must be smaller than first clear and unlock nothing' % w)
    for u in first.get('unlocks', []):
        if not ID.match(u):
            errors.append('%s: invalid unlock id %r' % (w, u))
    receipt = r.get('receipt', '')
    if '{player}' not in receipt or '{run}' not in receipt:
        errors.append('%s: receipt must bind player and run' % w)
    if not isinstance(r.get('cooldown_ticks'), int) or r['cooldown_ticks'] < 1200:
        errors.append('%s: cooldown must be at least 1200 ticks (no farming loop)' % w)
    return used


def validate(content_dir, root=ROOT, require_anchors=False):
    content_dir = Path(content_dir)
    errors, warnings = [], []
    facts = repo_facts(root)
    data = json.loads((content_dir / 'gates.json').read_text(encoding='utf-8'))
    if data.get('schema') != 1:
        return ['gates.json: schema must be 1'], warnings, {}
    anchors = data.get('anchors', {})
    for aid, a in anchors.items():
        if not ID.match(aid) or a.get('dungeon') not in DUNGEONS or a.get('kind') not in ANCHOR_KINDS:
            errors.append('anchor %s: invalid id, dungeon or kind' % aid)
        pos = a.get('local_pos')
        if pos is None:
            (errors if require_anchors else warnings).append('anchor %s: not yet measured' % aid)
        elif not (isinstance(pos, list) and len(pos) == 3 and all(isinstance(v, int) and v >= 0 for v in pos)):
            errors.append('anchor %s: local_pos must be three non-negative integers' % aid)
    residents = {}
    for r in data.get('residents', []):
        rid = r.get('id', '')
        if not ID.match(rid) or rid in residents:
            errors.append('resident %r: invalid or duplicate id' % rid)
        residents[rid] = r
        phases = {row.get('phase') for row in r.get('routine', [])}
        if phases != PHASES:
            errors.append('resident %s: routine must cover dawn/day/dusk/night' % rid)
        for row in r.get('routine', []) + [{'anchor': r.get('fallback_anchor')}]:
            a = anchors.get(row.get('anchor'))
            if a is None or a['dungeon'] != r.get('dungeon'):
                errors.append('resident %s: routine/fallback anchor %r missing or foreign' % (rid, row.get('anchor')))
    mission_ids = {m.get('id') for m in data.get('missions', [])}
    ctx = {'anchors': anchors, 'residents': residents, 'facts': facts}
    proposed = set()
    for m in data.get('missions', []):
        proposed |= validate_mission(m, ctx, errors) or set()
    if len(mission_ids) != len(data.get('missions', [])):
        errors.append('duplicate mission ids')
    for rid, r in residents.items():
        path = content_dir / r.get('dialogue', '')
        if not path.is_file():
            errors.append('resident %s: dialogue file missing' % rid)
            continue
        doc = json.loads(path.read_text(encoding='utf-8'))
        if doc.get('resident') != rid:
            errors.append('%s: resident field does not match' % path.name)
        own = {m['id'] for m in data.get('missions', []) if m.get('giver') == rid}
        validate_dialogue(doc, own, errors, path.name)
    covered = {m.get('dungeon') for m in data.get('missions', [])}
    if covered != DUNGEONS:
        errors.append('missions must cover all five dungeons; missing %s' % sorted(DUNGEONS - covered))
    summary = {'missions': len(mission_ids), 'residents': len(residents), 'anchors': len(anchors),
               'unmeasured_anchors': sum(1 for a in anchors.values() if a.get('local_pos') is None),
               'adapter_objectives_needed': sorted(proposed)}
    return errors, warnings, summary


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--content', default=str(ROOT / 'content/expeditions'))
    p.add_argument('--require-anchors', action='store_true')
    args = p.parse_args(argv)
    errors, warnings, summary = validate(args.content, require_anchors=args.require_anchors)
    print(json.dumps({'errors': errors, 'warnings': len(warnings), 'summary': summary}, indent=2))
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
