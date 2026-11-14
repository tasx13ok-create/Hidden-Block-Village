import argparse
import json
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description='Bounded five-coliseum authoring; native gates are separate.')
    sub=p.add_subparsers(dest='command',required=True)
    b=sub.add_parser('build'); b.add_argument('--brief',required=True); b.add_argument('--all',action='store_true',required=True)
    b.add_argument('--output',required=True); b.add_argument('--seed',type=int,default=20261114); b.add_argument('--dry-run',action='store_true')
    a=sub.add_parser('audit'); a.add_argument('--manifest',required=True); a.add_argument('--strict',action='store_true')
    s=sub.add_parser('stage-world'); s.add_argument('--manifest',required=True); s.add_argument('--source'); s.add_argument('--dest',required=True)
    n=sub.add_parser('native-checks'); n.add_argument('--candidate',required=True); n.add_argument('--cases',default='all')
    i=sub.add_parser('intake'); i.add_argument('--archive',required=True); i.add_argument('--inventory',required=True)
    i.add_argument('--server-root'); i.add_argument('--component-report')
    for parser in (a,s,n,i): parser.add_argument('--report')
    args=p.parse_args()
    if args.command=='build':
        from .build import build_all
        result=build_all(args.brief,args.output,args.seed,args.dry_run)
    elif args.command=='audit':
        from .audit import audit
        result=audit(args.manifest,args.strict)
    elif args.command=='stage-world':
        from .stage import stage_world
        result=stage_world(args.manifest,args.source,args.dest)
    elif args.command=='native-checks':
        from .native import run_checks
        result=run_checks(args.candidate,args.cases)
    else:
        from .intake import inspect_inputs
        result=inspect_inputs(args.archive,args.inventory,args.server_root,args.component_report)
    # Dense route metadata belongs in the manifest, not megabytes of terminal text.
    if args.command=='build' and not args.dry_run:
        print(json.dumps({'status':result['status'],'output':str(Path(args.output).absolute()),
                          'source_sha256':result['source']['sha256'],
                          'arenas':[{k:a[k] for k in ('id','sha256','bytes')} for a in result['arenas']],
                          'peak_process_working_set_bytes':result['peak_process_working_set_bytes']},indent=2))
    elif getattr(args,'report',None):
        report=Path(args.report)
        with report.open('x',encoding='utf-8') as out: json.dump(result,out,indent=2)
        print(json.dumps({'status':result.get('status',result.get('passed')),'report':str(report.absolute())}))
    else:
        print(json.dumps(result,indent=2))
    if isinstance(result,dict) and (result.get('status')=='FAIL' or result.get('passed') is False):
        raise SystemExit(1)


if __name__=='__main__':
    main()
