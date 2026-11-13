"""Precise, reviewable edits that produce a *new* candidate schematic.

An edit plan is JSON::

    {"schema": 1, "input_sha256": "<optional, enforced when present>",
     "edits": [{"id": "sakura-gravity-001", "pos": [x, y, z],
                "expect": {"block": 12, "data": 0},
                "set": {"block": 24, "data": 0},
                "reason": "sand over air would fall when the paste triggers physics"}]}

Every edit must state what it expects to replace; a mismatch aborts the whole plan so a plan
written against another version cannot silently damage geometry. Cells holding a tile entity
are refused unless the edit sets ``"tile": "keep"`` (block keeps a compatible type) or
``"tile": "remove"``. After writing, the output is re-read and diffed against the input: only
the planned cells may differ and every untouched tile entity must survive byte-for-byte.
"""
import hashlib
import json
from pathlib import Path

from . import blocks as B
from . import nbt
from .audit import audit_gravity
from .schematic import Schematic, sha256_file


class EditError(ValueError):
    pass


def _cell(spec, what):
    if not isinstance(spec, dict) or 'block' not in spec:
        raise EditError('%s needs {"block": id, "data": n}' % what)
    block, data = spec['block'], spec.get('data', 0)
    if not isinstance(block, int) or not isinstance(data, int) or not 0 <= block <= 0xFFF or not 0 <= data <= 15:
        raise EditError('%s has out-of-range block/data %r' % (what, spec))
    return block, data


def validate_plan(plan, schem):
    if not isinstance(plan, dict) or plan.get('schema') != 1 or not isinstance(plan.get('edits'), list):
        raise EditError('Edit plan must be {"schema": 1, "edits": [...]}')
    seen_ids, seen_pos = set(), set()
    tiles = schem.tile_at()
    checked = []
    for n, edit in enumerate(plan['edits']):
        eid = edit.get('id') or 'edit-%d' % n
        if eid in seen_ids:
            raise EditError('Duplicate edit id %s' % eid)
        seen_ids.add(eid)
        pos = edit.get('pos')
        if not (isinstance(pos, list) and len(pos) == 3 and all(isinstance(v, int) for v in pos)):
            raise EditError('%s: pos must be [x, y, z] integers' % eid)
        pos = tuple(pos)
        if not schem.in_bounds(*pos):
            raise EditError('%s: %r is outside the schematic' % (eid, pos))
        if pos in seen_pos:
            raise EditError('%s: position %r edited twice' % (eid, pos))
        seen_pos.add(pos)
        if not edit.get('reason'):
            raise EditError('%s: every edit needs a reason' % eid)
        expect = _cell(edit.get('expect'), eid + ' expect')
        target = _cell(edit.get('set'), eid + ' set')
        actual = schem.get(*pos)
        if actual != expect:
            raise EditError('%s: expected %d:%d at %r but found %d:%d' % ((eid,) + expect + (pos,) + actual))
        tile_mode = edit.get('tile')
        if pos in tiles:
            if tile_mode not in ('keep', 'remove'):
                raise EditError('%s: %r holds a tile entity; set "tile" to "keep" or "remove"' % (eid, pos))
            if tile_mode == 'keep':
                tid = B.normalize_tile_id(tiles[pos][0].scalar('id', ''))
                if target[0] not in B.TILE_BLOCKS.get(tid, ()):
                    raise EditError('%s: block %d cannot keep a %s tile entity' % (eid, target[0], tid))
        elif tile_mode == 'remove':
            raise EditError('%s: no tile entity to remove at %r' % (eid, pos))
        checked.append({'id': eid, 'pos': pos, 'expect': expect, 'set': target,
                        'tile': tile_mode, 'reason': edit['reason']})
    return checked


def apply_plan(input_path, plan_path, output_path, log_path=None):
    input_path, output_path = Path(input_path), Path(output_path)
    if output_path.exists():
        raise FileExistsError('Refusing to overwrite %s' % output_path)
    if output_path.resolve() == input_path.resolve():
        raise FileExistsError('Output must be a new file, not the input')
    before_hash = sha256_file(input_path)
    plan = json.loads(Path(plan_path).read_text(encoding='utf-8'))
    if plan.get('input_sha256') and plan['input_sha256'] != before_hash:
        raise EditError('Plan was written for input %s but this file is %s' % (plan['input_sha256'], before_hash))
    schem = Schematic.load(input_path)
    original = Schematic.load(input_path)
    checked = validate_plan(plan, schem)
    removed = set()
    for edit in checked:
        schem.set_block(*edit['pos'], block=edit['set'][0], data=edit['set'][1])
        if edit['tile'] == 'remove':
            removed.add(edit['pos'])
    if removed:
        kept = [t for t in schem.tile_tags if tuple(t.scalar(a) for a in 'xyz') not in removed]
        schem.tile_tags[:] = kept
        schem.root.value['TileEntities'] = nbt.Tag(nbt.LIST, kept, nbt.COMPOUND)
    after_hash = schem.save_new(output_path)
    verification = verify_candidate(original, Schematic.load(output_path), checked)
    log = {
        'schema': 1, 'tool': 'tools/world_merge edits',
        'input': {'file': input_path.name, 'sha256': before_hash},
        'output': {'file': output_path.name, 'sha256': after_hash},
        'plan': {'file': Path(plan_path).name, 'sha256': hashlib.sha256(Path(plan_path).read_bytes()).hexdigest()},
        'edits': [{'id': e['id'], 'pos': list(e['pos']), 'before': {'block': e['expect'][0], 'data': e['expect'][1]},
                   'after': {'block': e['set'][0], 'data': e['set'][1]}, 'tile': e['tile'], 'reason': e['reason']}
                  for e in checked],
        'verification': verification,
    }
    if log_path:
        log_path = Path(log_path)
        if log_path.exists():
            raise FileExistsError('Refusing to overwrite log %s' % log_path)
        log_path.write_text(json.dumps(log, indent=2) + '\n', encoding='utf-8')
    if not verification['ok']:
        raise EditError('Candidate verification failed: %s' % verification)
    return log


def verify_candidate(before, after, checked):
    problems = []
    if (before.width, before.height, before.length) != (after.width, after.height, after.length):
        problems.append('dimensions changed')
        return {'ok': False, 'problems': problems}
    planned = {before.index(*e['pos']) for e in checked}
    changed = set()
    for name in ('blocks', 'data'):
        a, b = getattr(before, name), getattr(after, name)
        if a != b:
            changed.update(_diff_indices(a, b))
    ha = before.add or bytearray(before.cells)
    hb = after.add or bytearray(after.cells)
    if ha != hb:
        changed.update(_diff_indices(ha, hb))
    unexpected = sorted(changed - planned)
    if unexpected:
        problems.append('unplanned cells changed: %r' % [before.coords(i) for i in unexpected[:10]])
    for e in checked:
        if after.get(*e['pos']) != e['set']:
            problems.append('%s not applied' % e['id'])
    removed = {e['pos'] for e in checked if e['tile'] == 'remove'}
    before_tiles = {k: v for k, v in before.tile_at().items() if k not in removed}
    after_tiles = after.tile_at()
    if set(before_tiles) != set(after_tiles):
        problems.append('tile entity positions changed')
    else:
        for pos, tags in before_tiles.items():
            if [nbt.dumps(t, compression=None) for t in tags] != [nbt.dumps(t, compression=None) for t in after_tiles[pos]]:
                problems.append('tile entity at %r changed' % (pos,))
    for key in before.root.value:
        if key in ('Blocks', 'Data', 'AddBlocks', 'TileEntities'):
            continue
        if key not in after.root.value or before.root.value[key] != after.root.value[key]:
            problems.append('tag %s changed' % key)
    return {'ok': not problems, 'problems': problems, 'changed_cells': len(changed), 'planned_cells': len(planned)}


def _diff_indices(a, b, chunk=1 << 16):
    out = []
    for start in range(0, len(a), chunk):
        if a[start:start + chunk] != b[start:start + chunk]:
            out.extend(start + i for i in range(min(chunk, len(a) - start)) if a[start + i] != b[start + i])
    return out


# Proposal generation -------------------------------------------------------------------------
_SUPPORT_FOR = {12: lambda d: (179, 0) if d == 1 else (24, 0), 13: lambda d: (4, 0),
                252: lambda d: (251, d), 145: lambda d: (1, 0), 122: lambda d: (49, 0)}


def propose_fixes(schem, audit_result, input_sha256=None):
    """Turn concrete audit defects into a *proposed* plan for review (never applied automatically).

    - unsupported gravity block: add a matching non-falling support directly below when that cell
      is plain air (sand->sandstone, red sand->red sandstone, gravel->cobblestone, concrete powder
      ->concrete of the same colour). Fluid/fire cells below are left for manual review.
    - orphan lower door half: add the missing upper half when the cell above is air.
    """
    edits, skipped = [], []
    for item in audit_gravity(schem, limit=None)['unsupported']['sample']:
        x, y, z = item['pos']
        below = (x, y - 1, z)
        if schem.get(*below) != (0, 0):
            skipped.append({'pos': list(item['pos']), 'why': 'support cell is not plain air'})
            continue
        block, data = _SUPPORT_FOR[item['block']](item['data'])
        edits.append({'id': 'gravity-%d-%d-%d' % below, 'pos': list(below), 'expect': {'block': 0, 'data': 0},
                      'set': {'block': block, 'data': data},
                      'reason': 'support for unsupported %d:%d at %r' % (item['block'], item['data'], tuple(item['pos']))})
    for item in audit_result['access']['door_orphan_halves']['sample']:
        x, y, z = item['pos']
        if item['half'] != 'lower' or not schem.in_bounds(x, y + 1, z) or schem.get(x, y + 1, z) != (0, 0):
            skipped.append({'pos': list(item['pos']), 'why': 'orphan door half needs manual review'})
            continue
        edits.append({'id': 'door-upper-%d-%d-%d' % (x, y + 1, z), 'pos': [x, y + 1, z],
                      'expect': {'block': 0, 'data': 0}, 'set': {'block': item['block'], 'data': 8},
                      'reason': 'missing upper half of door at %r' % (tuple(item['pos']),)})
    plan = {'schema': 1, 'status': 'PROPOSED_FOR_REVIEW', 'edits': edits, 'skipped': skipped}
    if input_sha256:
        plan['input_sha256'] = input_sha256
    return plan
