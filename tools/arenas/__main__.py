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
    else:
        from .native import run_checks
        result=run_checks(args.candidate,args.cases)
    print(json.dumps(result,indent=2))
    if isinstance(result,dict) and (result.get('status')=='FAIL' or result.get('passed') is False):
        raise SystemExit(1)


if __name__=='__main__':
    main()
