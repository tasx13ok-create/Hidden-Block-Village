"""Validator tests: each case breaks one real contract on a copy of the authored content."""
import copy
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('validate_expeditions', ROOT / 'tools/validate_expeditions.py')
vx = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vx)
CONTENT = ROOT / 'content/expeditions'


class ExpeditionValidatorTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name) / 'expeditions'
        shutil.copytree(CONTENT, self.dir)
        self.gates = json.loads((self.dir / 'gates.json').read_text())

    def tearDown(self):
        self.tmp.cleanup()

    def reset(self):
        self.tearDown()
        self.setUp()

    def run_with(self, gates=None, **kw):
        (self.dir / 'gates.json').write_text(json.dumps(gates or self.gates))
        errors, warnings, summary = vx.validate(self.dir, **kw)
        return errors, warnings, summary

    def mission(self, mid):
        return next(m for m in self.gates['missions'] if m['id'] == mid)

    def stage(self, mid, sid):
        return next(s for s in self.mission(mid)['stages'] if s['id'] == sid)

    def assertError(self, fragment, **kw):
        errors, _, _ = self.run_with(**kw)
        self.assertTrue(any(fragment in e for e in errors), errors)

    def test_authored_content_is_valid_and_reports_unmeasured_anchors(self):
        errors, warnings, summary = self.run_with()
        self.assertEqual([], errors)
        self.assertEqual(5, summary['missions'])
        self.assertEqual(summary['anchors'], summary['unmeasured_anchors'])
        self.assertIn('defeat_group', summary['adapter_objectives_needed'])
        errors, _, _ = self.run_with(require_anchors=True)
        self.assertTrue(any('not yet measured' in e for e in errors))

    def test_repository_facts_are_read_from_real_sources(self):
        facts = vx.repo_facts(ROOT)
        self.assertIn('academy_ambush', facts['contracts'])
        self.assertIn('travel_ration', facts['ledger_items'])
        self.assertEqual('mob', facts['profiles']['mob_ember_binder'])
        self.assertEqual('boss', facts['profiles']['boss_ember_regent'])

    def test_unknown_encounter_profile_and_boss_profile_are_rejected(self):
        self.stage('gate_nether_ash_seal', 'assault')['objective']['profiles'][0]['key'] = 'mob_lava_dragon'
        self.assertError("profile 'mob_lava_dragon' is not an existing mob profile")
        self.reset()
        self.stage('gate_nether_ash_seal', 'assault')['objective']['profiles'][0]['key'] = 'boss_ember_regent'
        self.assertError('is not an existing mob profile')

    def test_unknown_prerequisite_contract_is_rejected(self):
        self.mission('gate_cloud_first_interval')['prerequisites']['contracts_completed'] = ['chunin_exam']
        self.assertError("'chunin_exam' does not exist in Missions.java")

    def test_stage_graph_dangling_unreachable_cycle_and_no_completion(self):
        self.stage('gate_mist_low_tide', 'lane')['next'] = 'missing'
        self.assertError("next stage 'missing' missing")
        self.reset()
        m = self.mission('gate_mist_low_tide')
        m['stages'].append({'id': 'orphan', 'text': 'Never reached.', 'objective': {'type': 'interact_marker', 'anchor': 'mist_moored_pier'}, 'next': None})
        self.assertError("stage 'orphan' unreachable")
        self.reset()
        self.stage('gate_mist_low_tide', 'quay')['next'] = 'arrive'
        self.assertError('forward stage cycle')

    def test_timed_objective_without_retry_is_rejected(self):
        del self.stage('gate_cloud_first_interval', 'levers')['on_fail']
        self.assertError('timed objective needs an on_fail retry')

    def test_anchor_from_another_dungeon_is_rejected(self):
        self.stage('gate_sakura_seedfall', 'arrive')['objective']['anchor'] = 'mist_moored_pier'
        self.assertError('belongs to another dungeon')

    def test_reward_farming_and_off_scale_rewards_are_rejected(self):
        m = self.mission('gate_sand_dry_wind')
        m['rewards']['repeat']['xp'] = m['rewards']['first_clear']['xp']
        self.assertError('repeat rewards must be smaller')
        self.reset()
        self.mission('gate_sand_dry_wind')['rewards']['repeat']['unlocks'] = ['expedition_sand_entry']
        self.assertError('repeat rewards must be smaller')
        self.reset()
        self.mission('gate_sand_dry_wind')['rewards']['first_clear']['merit'] = 50
        self.assertError('outside Missions scale')
        self.reset()
        self.mission('gate_sand_dry_wind')['rewards']['first_clear']['ledger_items'] = [{'key': 'legendary_blade', 'count': 1}]
        self.assertError("'legendary_blade' is not a registered field item")
        self.reset()
        self.mission('gate_sand_dry_wind')['rewards']['receipt'] = 'gate/{mission}'
        self.assertError('receipt must bind player and run')
        self.reset()
        self.mission('gate_sand_dry_wind')['rewards']['cooldown_ticks'] = 20
        self.assertError('cooldown must be at least')

    def test_missing_failure_policy_and_dungeon_coverage(self):
        del self.mission('gate_mist_low_tide')['failure']['disconnect']
        self.assertError('failure policy for disconnect missing')
        self.reset()
        self.gates['missions'] = [m for m in self.gates['missions'] if m['dungeon'] != 'mist_v17']
        self.assertError("missing ['mist_v17']")

    def test_resident_routine_must_cover_the_day_and_use_own_anchors(self):
        r = self.gates['residents'][0]
        r['routine'] = r['routine'][:3]
        self.assertError('routine must cover dawn/day/dusk/night')
        self.reset()
        self.gates['residents'][0]['fallback_anchor'] = 'cloud_arrival_terrace'
        self.assertError('missing or foreign')

    def edit_dialogue(self, name, fn):
        path = self.dir / 'dialogue' / name
        doc = json.loads(path.read_text())
        fn(doc)
        path.write_text(json.dumps(doc))

    def test_dialogue_cannot_accept_another_residents_mission(self):
        def foreign(doc):
            doc['pages'][1]['choices'][0]['target'] = 'gate_nether_ash_seal'
        self.edit_dialogue('keeper_hanae.json', foreign)
        self.assertError("accepts unknown or foreign mission 'gate_nether_ash_seal'")

    def test_dialogue_obeys_dialoguebook_limits(self):
        self.edit_dialogue('pilot_shiori.json', lambda d: d['pages'][0]['lines'].append('x' * 201))
        self.assertError('longer than 200')
        self.reset()
        self.edit_dialogue('pilot_shiori.json', lambda d: d['pages'][0]['choices'].append({'id': 'op', 'label': 'Run', 'action': 'COMMAND', 'target': 'op'}))
        self.assertError("unsupported action 'COMMAND'")
        self.reset()
        self.edit_dialogue('pilot_shiori.json', lambda d: d['pages'][0]['choices'][2].update(target='welcome'))
        self.assertError('target presence does not match action')
        self.reset()
        self.edit_dialogue('pilot_shiori.json', lambda d: d['pages'].append({'id': 'lost', 'lines': ['Nobody links here.'], 'choices': [{'id': 'b', 'label': 'Back', 'action': 'PAGE', 'target': 'welcome'}]}))
        self.assertError("page 'lost' is unreachable")
        self.reset()
        self.edit_dialogue('pilot_shiori.json', lambda d: d['pages'][0]['lines'].__setitem__(0, 'bad\nline'))
        self.assertError('control character')


if __name__ == '__main__':
    unittest.main()
