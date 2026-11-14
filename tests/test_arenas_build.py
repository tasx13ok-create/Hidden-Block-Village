import copy
import json
from pathlib import Path
import tempfile
import unittest

from tools.arenas.build import ROOT, build_all, validate_brief


class BuildBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.brief=json.loads((ROOT/'collaboration/arena-build-orchestration/arena-brief.json').read_text(encoding='utf-8'))

    def test_real_five_arena_brief_is_accepted(self):
        validate_brief(self.brief)

    def test_unsafe_arena_path_never_creates_any_output(self):
        for name in ('../arena_forest_escape','C:/arena_forest_escape','arena_forest_root_coliseum_v1/../../escape'):
            brief=copy.deepcopy(self.brief);brief['arenas'][0]['id']=name
            with tempfile.TemporaryDirectory() as tmp:
                source=Path(tmp)/'bad.json';source.write_text(json.dumps(brief),encoding='utf-8');out=Path(tmp)/'candidate'
                with self.assertRaises(ValueError):build_all(source,out)
                self.assertFalse(out.exists())

    def test_duplicate_region_illegal_height_and_outside_anchor_refused(self):
        for kind in ('region','height','anchor','boolean'):
            brief=copy.deepcopy(self.brief)
            if kind=='region':brief['arenas'][1]['proposed_world_min']=list(brief['arenas'][0]['proposed_world_min'])
            elif kind=='height':brief['arenas'][0]['proposed_world_min'][1]=64
            elif kind=='anchor':brief['arenas'][0]['entry_local'][0]=0
            else:brief['arenas'][0]['entry_local'][1]=True
            with self.assertRaises(ValueError):validate_brief(brief)


if __name__=='__main__':unittest.main()
