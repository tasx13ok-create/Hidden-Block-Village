"""Command line for the world-merge audit/import package.

Run from the repository root::

    python -m tools.world_merge audit --input sakura_v17.schematic --output report.json \
        --entrance 144,40,2 --target 144,90,144
    python -m tools.world_merge placement --input mist_v17.schematic --anchor 100,16,100:400,63,-200
    python -m tools.world_merge compare --old mist_v9.schematic --new mist_v17.schematic --anchor-pair 10,20,0:10,20,0
    python -m tools.world_merge propose --input x.schematic --output x-plan.json
    python -m tools.world_merge apply --input x.schematic --plan x-plan.json --output x-candidate.schematic --log x-edits.json
    python -m tools.world_merge paste-world --input x.schematic --world-copy D:/cand/world --min 0,40,0 --receipt r.json

Inputs are never modified. Every output must be a new path unless ``--replace-report`` is given
for a JSON report. Exit status: 0 ok, 1 findings that need review (``--strict``), 2 usage/format error.
"""
import argparse
import json
from pathlib import Path
import sys

from . import audit, compare, edits, placement
from .schematic import Schematic, SchematicError, sha256_file


def _vec(text):
    parts = [p.strip() for p in str(text).split(',')]
    if len(parts) != 3:
        raise argparse.ArgumentTypeError('expected x,y,z but got %r' % text)
    try:
        return tuple(int(p) for p in parts)
    except ValueError:
        raise argparse.ArgumentTypeError('coordinates must be integers: %r' % text)


def _pair(text):
    if ':' not in text:
        raise argparse.ArgumentTypeError('expected a,b,c:x,y,z')
    a, b = text.split(':', 1)
    return _vec(a), _vec(b)


def _write_json(path, payload, replace=False):
    path = Path(path)
    if path.exists() and not replace:
        raise FileExistsError('Refusing to overwrite %s (use --replace-report for reports)' % path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, default=list) + '\n', encoding='utf-8')


def _emit(args, payload):
    if getattr(args, 'output', None):
        _write_json(args.output, payload, getattr(args, 'replace_report', False))
    else:
        json.dump(payload, sys.stdout, indent=2, default=list)
        sys.stdout.write('\n')


def needs_review(report):
    """True when the audit found concrete defects (not merely informational facts)."""
    t, gr, fl, ro, ac = (report[k] for k in ('tiles', 'gravity', 'fluids', 'routes', 'access'))
    bad_tiles = [i for i in t['issues'] if i['kind'] not in ('spawner_present',)]
    return bool(report['format']['non_vanilla_ids'] or report['format']['data_values_over_15'] or bad_tiles
                or gr['unsupported']['count'] or fl['by_classification'].get('leak_candidate')
                or ro['unreachable_containers']['count'] or ro['invalid_entrances']
                or any(not x['reachable'] for x in ro['targets'])
                or ac['door_orphan_halves']['count'] or ac['ladder_problems']['count'])


def cmd_audit(args):
    schem = Schematic.load(args.input)
    report = audit.full_audit(schem, args.entrance, args.target)
    report['input'] = {'file': Path(args.input).name, 'sha256': sha256_file(args.input)}
    report['placement'] = placement.placement_report(schem)
    report['needs_review'] = needs_review(report)
    report['scope'] = 'offline static geometry audit; not a native walk or visual review'
    _emit(args, report)
    return 1 if args.strict and report['needs_review'] else 0


def cmd_placement(args):
    schem = Schematic.load(args.input)
    local, world = args.anchor if args.anchor else (None, None)
    _emit(args, placement.placement_report(schem, local, world, args.paste_at))
    return 0


def cmd_compare(args):
    old, new = Schematic.load(args.old), Schematic.load(args.new)
    modes = [m for m in (args.delta, args.anchor_pair, args.we_origin or None) if m]
    if len(modes) != 1:
        raise SystemExit('compare needs exactly one of --delta, --anchor-pair or --we-origin')
    if args.delta:
        delta, how = args.delta, 'explicit delta'
    elif args.anchor_pair:
        delta, how = compare.delta_from_anchor(*args.anchor_pair), 'anchor pair %r -> %r' % args.anchor_pair
    else:
        delta, how = compare.delta_from_we_origin(old, new), 'WorldEdit origins (same source world assumed)'
    result = compare.compare(old, new, delta)
    result['alignment'] = how
    result['suggested_alignments_from_tiles'] = compare.suggest_alignments(old, new)
    result['inputs'] = {'old': {'file': Path(args.old).name, 'sha256': sha256_file(args.old)},
                        'new': {'file': Path(args.new).name, 'sha256': sha256_file(args.new)}}
    _emit(args, result)
    return 0


def cmd_propose(args):
    schem = Schematic.load(args.input)
    routes, rg, _ = audit.audit_routes(schem)
    report = {'gravity': audit.audit_gravity(schem), 'access': audit.audit_access(schem, rg)}
    plan = edits.propose_fixes(schem, report, sha256_file(args.input))
    _emit(args, plan)
    return 0


def cmd_apply(args):
    log = edits.apply_plan(args.input, args.plan, args.output, args.log)
    if not args.log:
        json.dump(log, sys.stdout, indent=2)
        sys.stdout.write('\n')
    return 0


def cmd_paste_world(args):
    from . import anvil
    schem = Schematic.load(args.input)
    receipt = anvil.paste_into_world_copy(schem, args.world_copy, args.min, source_path=args.input,
                                          clear_air=not args.keep_existing_on_air)
    _write_json(args.receipt, receipt)
    return 0


def build_parser():
    p = argparse.ArgumentParser(prog='python -m tools.world_merge', description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='command', required=True)
    a = sub.add_parser('audit', help='static audit report')
    a.add_argument('--input', required=True)
    a.add_argument('--output')
    a.add_argument('--replace-report', action='store_true')
    a.add_argument('--entrance', type=_vec, action='append', help='local x,y,z feet cell of an arrival point')
    a.add_argument('--target', type=_vec, action='append', help='local x,y,z that must be reachable')
    a.add_argument('--strict', action='store_true', help='exit 1 when findings need review')
    a.set_defaults(func=cmd_audit)
    pl = sub.add_parser('placement', help='paste bounds and build-height check')
    pl.add_argument('--input', required=True)
    pl.add_argument('--output')
    pl.add_argument('--replace-report', action='store_true')
    pl.add_argument('--anchor', type=_pair, help='local x,y,z:world x,y,z')
    pl.add_argument('--paste-at', type=_vec, help='world position a //paste would be run from')
    pl.set_defaults(func=cmd_placement)
    c = sub.add_parser('compare', help='compare versions under an explicit alignment')
    c.add_argument('--old', required=True)
    c.add_argument('--new', required=True)
    c.add_argument('--output')
    c.add_argument('--replace-report', action='store_true')
    c.add_argument('--delta', type=_vec, help='new_local = old_local + delta')
    c.add_argument('--anchor-pair', type=_pair, help='old x,y,z:new x,y,z of the same authored point')
    c.add_argument('--we-origin', action='store_true', help='align by both WEOrigin values')
    c.set_defaults(func=cmd_compare)
    pr = sub.add_parser('propose', help='write a reviewable fix plan (does not edit)')
    pr.add_argument('--input', required=True)
    pr.add_argument('--output')
    pr.add_argument('--replace-report', action='store_true')
    pr.set_defaults(func=cmd_propose)
    ap = sub.add_parser('apply', help='apply a reviewed plan to a NEW candidate file')
    ap.add_argument('--input', required=True)
    ap.add_argument('--plan', required=True)
    ap.add_argument('--output', required=True)
    ap.add_argument('--log')
    ap.set_defaults(func=cmd_apply)
    pw = sub.add_parser('paste-world', help='paste into a copied 1.12 Anvil world (never the source)')
    pw.add_argument('--input', required=True)
    pw.add_argument('--world-copy', required=True, help='a candidate world directory made with copy-world')
    pw.add_argument('--min', type=_vec, required=True, help='world minimum corner for the paste')
    pw.add_argument('--receipt', required=True)
    pw.add_argument('--keep-existing-on-air', action='store_true',
                    help='do not clear world blocks where the schematic has air')
    pw.set_defaults(func=cmd_paste_world)
    cw = sub.add_parser('copy-world', help='hash-verified copy of a world into a new candidate directory')
    cw.add_argument('--source', required=True)
    cw.add_argument('--dest', required=True)
    cw.add_argument('--manifest', required=True)
    cw.set_defaults(func=lambda args: _copy_world(args))
    sa = sub.add_parser('anchors-suggest', help='candidate arrival/court/arena/container cells')
    sa.add_argument('--input', required=True)
    sa.add_argument('--entrance', type=_vec, action='append')
    sa.add_argument('--output')
    sa.add_argument('--replace-report', action='store_true')
    sa.set_defaults(func=cmd_anchors_suggest)
    va = sub.add_parser('anchors-verify', help='verify gates.json anchors for one dungeon')
    va.add_argument('--input', required=True)
    va.add_argument('--gates', required=True)
    va.add_argument('--dungeon', required=True)
    va.add_argument('--measured', help='JSON {anchor_id: [x,y,z]} overriding local_pos')
    va.add_argument('--entrance', type=_vec, action='append')
    va.add_argument('--fill-output', help='write a NEW gates file with verified anchors applied')
    va.add_argument('--output')
    va.add_argument('--replace-report', action='store_true')
    va.set_defaults(func=cmd_anchors_verify)
    bt = sub.add_parser('batch', help='audit every .schematic in a folder; per-file reports + aggregate summary')
    bt.add_argument('--input-dir', required=True)
    bt.add_argument('--output-dir', required=True)
    bt.add_argument('--replace-report', action='store_true')
    bt.set_defaults(func=cmd_batch)
    dw = sub.add_parser('doorways', help='propose minimal door/opening edits for closed chest rooms (plan only)')
    dw.add_argument('--input', required=True)
    dw.add_argument('--output', help='write the PROPOSED edit plan here (use apply after review)')
    dw.add_argument('--replace-report', action='store_true')
    dw.add_argument('--no-doors', action='store_true', help='propose air openings instead of oak doors')
    dw.set_defaults(func=cmd_doorways)
    ni = sub.add_parser('npc-inventory', help='metadata-only CustomNPCs intake report (nothing executed)')
    ni.add_argument('--world', required=True)
    ni.add_argument('--output')
    ni.add_argument('--replace-report', action='store_true')
    ni.set_defaults(func=cmd_npc_inventory)
    rb = sub.add_parser('rollback', help='restore a candidate world to before paste N')
    rb.add_argument('--world-copy', required=True)
    rb.add_argument('--paste', type=int, required=True)
    rb.set_defaults(func=_rollback)
    return p


def cmd_batch(args):
    from . import anchors
    src, out = Path(args.input_dir), Path(args.output_dir)
    files = sorted(src.glob('*.schematic'))
    if not files:
        raise ValueError('No .schematic files in %s' % src)
    out.mkdir(parents=True, exist_ok=True)
    summary = {'schema': 1, 'scope': 'offline static audit; aggregate counts only', 'files': []}
    for f in files:
        report_path = out / (f.stem + '.audit.json')
        if report_path.exists() and not args.replace_report:
            raise FileExistsError('Refusing to overwrite %s' % report_path)
        schem = Schematic.load(f)
        report = audit.full_audit(schem)
        report['input'] = {'file': f.name, 'sha256': sha256_file(f)}
        report['placement'] = placement.placement_report(schem)
        report['anchor_candidates'] = anchors.suggest(schem)
        report['needs_review'] = needs_review(report)
        _write_json(report_path, report, args.replace_report)
        fm, ro, fl = report['format'], report['routes'], report['fluids']
        summary['files'].append({
            'file': f.name, 'sha256': report['input']['sha256'],
            'size': [fm['dimensions'][k] for k in ('width', 'height', 'length')],
            'occupied_y_range': fm['occupied_y_range'], 'non_vanilla_ids': fm['non_vanilla_ids'],
            'tile_issue_kinds': sorted({i['kind'] for i in report['tiles']['issues']}),
            'blocks_missing_tile': report['tiles']['blocks_missing_tile']['count'],
            'unsupported_gravity': report['gravity']['unsupported']['count'],
            'fluids': fl['by_classification'],
            'standable_cells': ro['standable_cells'], 'reached_standable_from_edges': ro['reached_standable'],
            'unreachable_containers': ro['unreachable_containers']['count'],
            'unreached_regions_16plus': ro['unreached_regions_16plus']['count'],
            'door_orphans': report['access']['door_orphan_halves']['count'],
            'ladder_problems': report['access']['ladder_problems']['count'],
            'valid_paste_y_range': report['placement'].get('valid_paste_y_range'),
            'largest_open_area_span': (report['anchor_candidates']['open_areas'] or [{}])[0].get('span'),
            'needs_review': report['needs_review']})
    _write_json(out / 'summary.json', summary, args.replace_report)
    _emit(argparse.Namespace(output=None), summary)
    return 0


def cmd_doorways(args):
    from . import doorways
    schem = Schematic.load(args.input)
    result = doorways.propose(schem, door=not args.no_doors)
    plan = doorways.plan(schem, result, sha256_file(args.input))
    plan['proposals'] = [{k: v for k, v in p.items() if k != 'edits'} for p in result['proposals']]
    plan['skipped'] = result['skipped']
    _emit(args, plan)
    return 0


def cmd_npc_inventory(args):
    from . import npcs
    _emit(args, npcs.inventory(args.world))
    return 0


def cmd_anchors_suggest(args):
    from . import anchors
    _emit(args, anchors.suggest(Schematic.load(args.input), args.entrance))
    return 0


def cmd_anchors_verify(args):
    from . import anchors
    gates = json.loads(Path(args.gates).read_text(encoding='utf-8'))
    measured = json.loads(Path(args.measured).read_text(encoding='utf-8')) if args.measured else None
    rows = anchors.verify(Schematic.load(args.input), gates, args.dungeon, args.entrance, measured)
    report = {'dungeon': args.dungeon, 'anchors': rows,
              'ok': sum(r['status'] == 'ok' for r in rows), 'total': len(rows)}
    if args.fill_output:
        report['applied_to_new_file'] = anchors.fill(args.gates, args.fill_output, rows)
    _emit(args, report)
    bad = [r for r in rows if r['status'] not in ('ok', 'unmeasured', 'route_node_unchecked_reachability')]
    return 1 if bad else 0


def _rollback(args):
    from . import anvil
    _emit(args, anvil.rollback(args.world_copy, args.paste))
    return 0


def _copy_world(args):
    from . import anvil
    _write_json(args.manifest, anvil.copy_world(args.source, args.dest))
    return 0


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except (SchematicError, edits.EditError, FileExistsError, OSError, ValueError) as exc:
        print('error: %s' % exc, file=sys.stderr)
        return 2
