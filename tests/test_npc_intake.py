"""Synthetic NPC graphs, type-preserving formats and candidate-only merge checks."""
import copy
import gzip
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.npc_intake.formats import FormatError, fields, read_nbt, read_snbt, scalar, semantic_hash, tag, write_nbt, write_snbt
from tools.npc_intake.intake import ADAPTER_CUSTOMNPCS_SHA256, make_plan, parse_file, run, runtime_mods, sha

MANIFEST = {'schema': 1, 'required_mods': {'customnpcs': ADAPTER_CUSTOMNPCS_SHA256}}
MODS = dict(MANIFEST['required_mods'])


def snbt(path, text):
    return parse_file(path, text.encode('utf-8'))


def dialog(identity, title='new', target=None):
    node = tag('compound', {'DialogId': tag('int', identity), 'DialogTitle': tag('string', title)})
    if target is not None:
        fields(node)['Options'] = tag('list', {'element': 'compound', 'items': [tag('compound', {'Option': tag('compound', {'Dialog': tag('int', target), 'OptionType': tag('int', 1)})})]})
    path = 'world/customnpcs/dialogs/test/%d.json' % identity
    return path, parse_file(path, write_snbt(node).encode())


def factions(values):
    node = tag('compound', {'lastID': tag('int', max(values) if values else -1),
                           'NPCFactions': tag('list', {'element': 'compound', 'items': [tag('compound', {'Slot': tag('int', identity), 'Name': tag('string', name)}) for identity, name in sorted(values.items())]})})
    path = 'world/customnpcs/factions.dat'
    return path, parse_file(path, write_nbt(node))


class NpcIntakeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def plan(self, baseline, incoming, mapping=None):
        return make_plan(baseline, incoming, MANIFEST, mapping or {'schema': 1}, MODS)

    def test_typed_snbt_binary_nbt_roundtrip_and_semantic_hash(self):
        text = b'{"b":1b,"s":2s,"i":3,"l":4L,"f":0.1f,"d":0.2d,"a":[I;1,2],"ba":[B;1b,-1b],"la":[L;5L],"list":[{"Name":"Unicode \\u6771"}]}'
        node = read_snbt(text)
        self.assertEqual(semantic_hash(node), semantic_hash(read_snbt(write_snbt(node).encode())))
        binary = write_nbt(node, 'root')
        other, name, compressed = read_nbt(binary)
        self.assertTrue(compressed)
        self.assertEqual(name, 'root')
        self.assertEqual(semantic_hash(node), semantic_hash(other))
        self.assertNotEqual(semantic_hash(read_snbt(b'{x:1b}')), semantic_hash(read_snbt(b'{x:1}')))

    def test_hostile_or_ambiguous_formats_are_explicitly_rejected(self):
        for data in (b'{x:1,x:2}', b'{x:[1,2b]}', b'{x:128b}', b'{x:1e999d}', b"{x:'unsupported'}", b'{' * 70):
            with self.subTest(data=data[:30]), self.assertRaises(ValueError):
                read_snbt(data)
        with self.assertRaises(ValueError):
            read_nbt(gzip.compress(b'x' * (16 * 1024**2 + 1)))
        with self.assertRaises(ValueError):
            read_nbt(b'\x0a\x00\x00\x03\x00')
        with self.assertRaises(ValueError):
            read_snbt(b'{many:[' + b'1,' * 100000 + b'1]}')

    def test_texture_dependency_resolves_from_assets_or_verified_resource_catalog(self):
        path = 'server/customnpcs/clones/1/new.json'
        incoming = {path: snbt(path, '{Texture:"customnpcs:textures/skin.png"}')}
        report, unused = self.plan({}, incoming)
        self.assertIn('UNRESOLVED_TEXTURE_RESOURCE', [i['code'] for i in report['issues']])
        asset = 'server/customnpcs/assets/customnpcs/textures/skin.png'
        incoming[asset] = parse_file(asset, b'fixture-image')
        report, unused = self.plan({}, incoming)
        self.assertFalse(report['issues'])

    def test_runtime_catalog_requires_actual_jar_hash_and_reads_resources_without_loading(self):
        jar = self.root / 'mods/fixture.jar'
        jar.parent.mkdir()
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('assets/fixture/textures/skin.png', b'fixture-image')
        identity = sha(jar.read_bytes())
        inventory = self.root / 'inventory.json'
        metadata = {'jars': [{'path': 'mods/fixture.jar', 'sha256': identity, 'mod_declarations': [{'modid': 'fixture'}]}]}
        inventory.write_text(json.dumps(metadata))
        catalog = runtime_mods(inventory, self.root)
        self.assertEqual(catalog['fixture'], identity)
        self.assertEqual(catalog['resource:fixture:textures/skin.png'], identity)
        metadata['jars'][0]['sha256'] = '0' * 64
        inventory.write_text(json.dumps(metadata))
        with self.assertRaises(ValueError):
            runtime_mods(inventory, self.root)
        other = self.root / 'mods/other.jar'
        with zipfile.ZipFile(other, 'w') as archive:
            archive.writestr('different', b'different payload')
        metadata['jars'][0]['sha256'] = identity
        metadata['jars'].append({'path': 'mods/other.jar', 'sha256': sha(other.read_bytes()), 'mod_declarations': [{'modid': 'fixture'}]})
        inventory.write_text(json.dumps(metadata))
        with self.assertRaises(ValueError):
            runtime_mods(inventory, self.root)

    def test_colliding_dialog_id_needs_explicit_mapping(self):
        baseline = dict([dialog(3, 'old')])
        incoming = dict([dialog(3, 'friend')])
        report, unused = self.plan(baseline, incoming)
        self.assertIn('ID_CONFLICT', [i['code'] for i in report['issues']])
        report, final = self.plan(baseline, incoming, {'schema': 1, 'definitions': {'dialog:3': 'incoming'}})
        self.assertFalse(report['issues'])
        self.assertEqual(scalar(fields(next(iter(final.values()))['node'])['DialogTitle']), 'friend')
        self.assertEqual(scalar(fields(next(iter(baseline.values()))['node'])['DialogTitle']), 'old')

    def test_id_remap_updates_filename_and_supported_graph_edges(self):
        baseline = dict([dialog(3, 'old')])
        incoming = dict([dialog(3, 'friend', 3)])
        incoming['server/customnpcs/clones/1/new.json'] = snbt('server/customnpcs/clones/1/new.json', '{NPCDialogOptions:[{Dialog:3}]}')
        report, final = self.plan(baseline, incoming, {'schema': 1, 'id_remap': {'dialog': {'3': 103}}})
        self.assertFalse(report['issues'])
        self.assertIn('world/customnpcs/dialogs/test/103.json', final)
        clone = fields(final['server/customnpcs/clones/1/new.json']['node'])['NPCDialogOptions']['value']['items'][0]
        self.assertEqual(scalar(fields(clone)['Dialog']), 103)
        self.assertIn('world/customnpcs/dialogs/test/3.json', final)

    def test_remap_target_conflicts_and_duplicate_ids_do_not_overwrite(self):
        baseline = dict([dialog(103, 'old')])
        incoming = dict([dialog(3, 'friend')])
        report, unused = self.plan(baseline, incoming, {'schema': 1, 'id_remap': {'dialog': {'3': 103}}})
        self.assertTrue(report['issues'])
        duplicate = dict([dialog(3)])
        duplicate['world/customnpcs/dialogs/other/3.json'] = copy.deepcopy(next(iter(duplicate.values())))
        with self.assertRaises(ValueError):
            self.plan({}, duplicate)

    def test_actual_quest_id_field_and_next_quest_remap(self):
        path = 'world/customnpcs/quests/test/7.json'
        incoming = {path: snbt(path, '{Id:7,NextQuestId:7,Title:"quest"}')}
        report, final = self.plan({}, incoming, {'schema': 1, 'id_remap': {'quest': {'7': 70}}})
        self.assertFalse(report['issues'])
        node = final['world/customnpcs/quests/test/70.json']['node']
        self.assertEqual(scalar(fields(node)['Id']), 70)
        self.assertEqual(scalar(fields(node)['NextQuestId']), 70)
        with self.assertRaises(ValueError):
            self.plan({}, {path: snbt(path, '{Id:7,QuestId:7}')})

    def test_type_specific_quest_and_unknown_reference_forms_require_review(self):
        path = 'world/customnpcs/quests/test/7.json'
        incoming = {path: snbt(path, '{Id:7,QuestDialogs:[{Slot:1,Integer:3}],CompleterNpc:"placed-npc"}')}
        report, unused = self.plan({}, incoming)
        self.assertIn('OPAQUE_REFERENCES_REQUIRE_HASH_ACKNOWLEDGEMENT', [i['code'] for i in report['issues']])
        clone = 'server/customnpcs/clones/1/new.json'
        report, unused = self.plan({}, {clone: snbt(clone, '{NPCDialogOptions:[{Dialog:"not-an-id"}],Scripts:"unknown-shape"}')})
        self.assertTrue(report['issues'])

    def test_disabled_availability_and_sentinel_ids_are_not_active_edges(self):
        path = 'server/customnpcs/clones/1/new.json'
        incoming = {path: snbt(path, '{AvailabilityDialog:0,AvailabilityDialogId:99,DialogQuest:-1}')}
        report, unused = self.plan({}, incoming)
        self.assertFalse(report['issues'])

    def test_case_aliases_and_file_parent_conflicts_fail_before_candidate_write(self):
        one = 'server/customnpcs/assets/a'
        two = 'server/customnpcs/assets/a/b'
        with self.assertRaises(ValueError):
            self.plan({one: parse_file(one, b'a')}, {two: parse_file(two, b'b')})
        one = 'server/customnpcs/clones/1/A.json'
        two = 'server/customnpcs/clones/1/a.json'
        with self.assertRaises(ValueError):
            self.plan({one: snbt(one, '{}')}, {two: snbt(two, '{}')})

    def test_faction_merge_preserves_unrelated_and_remaps_clone_reference(self):
        baseline = dict([factions({0: 'keep', 1: 'original'})])
        incoming = dict([factions({1: 'friend'})])
        path = 'server/customnpcs/clones/1/new.json'
        incoming[path] = snbt(path, '{FactionID:1}')
        report, final = self.plan(baseline, incoming, {'schema': 1, 'id_remap': {'faction': {'1': 50}}})
        self.assertFalse(report['issues'])
        data = fields(final['world/customnpcs/factions.dat']['node'])
        self.assertEqual([scalar(fields(f)['Slot']) for f in data['NPCFactions']['value']['items']], [0, 1, 50])
        self.assertEqual(scalar(data['lastID']), 50)
        self.assertEqual(scalar(fields(final[path]['node'])['FactionID']), 50)

    def test_new_dangling_edges_block_and_existing_edges_are_reported(self):
        baseline = dict([dialog(3, target=99)])
        incoming = {'server/customnpcs/clones/1/new.json': snbt('server/customnpcs/clones/1/new.json', '{Name:"new"}')}
        report, unused = self.plan(baseline, incoming)
        self.assertFalse(report['issues'])
        self.assertEqual(len(report['warnings']), 1)
        report, unused = self.plan({}, dict([dialog(3, target=99)]))
        self.assertIn('UNRESOLVED_REFERENCE', [i['code'] for i in report['issues']])

    def test_scripts_commands_and_clone_links_need_exact_hash_ack(self):
        path = 'server/customnpcs/clones/1/new.json'
        incoming = {path: snbt(path, '{LinkedNpcName:"someone",Scripts:[{Script:"throw unsafe()"}]}')}
        report, unused = self.plan({}, incoming)
        self.assertTrue(report['issues'])
        report, unused = self.plan({}, incoming, {'schema': 1, 'opaque_acknowledgements': {path: incoming[path]['sha256']}})
        self.assertFalse(report['issues'])
        self.assertFalse(report['scripts_executed'])

    def test_binary_asset_collision_requires_explicit_path_choice(self):
        path = 'server/customnpcs/assets/customnpcs/textures/skin.png'
        baseline, incoming = {path: parse_file(path, b'original')}, {path: parse_file(path, b'friend')}
        report, unused = self.plan(baseline, incoming)
        self.assertIn('PATH_CONFLICT', [i['code'] for i in report['issues']])
        report, final = self.plan(baseline, incoming, {'schema': 1, 'paths': {path: 'baseline'}})
        self.assertFalse(report['issues'])
        self.assertEqual(final[path]['data'], b'original')

    def test_dependencies_playerdata_and_unsupported_region_inputs(self):
        path = 'server/customnpcs/clones/1/new.json'
        report, unused = self.plan({}, {path: snbt(path, '{id:"missing:entity"}')})
        self.assertIn('MISSING_ITEM_OR_ENTITY_NAMESPACE', [i['code'] for i in report['issues']])
        for path in ('world/customnpcs/playerdata/private.json', 'world/region/r.0.0.mca', '../escape'):
            with self.assertRaises(ValueError):
                parse_file(path, b'{}')
        report, unused = make_plan({}, {}, MANIFEST, {'schema': 1}, {})
        self.assertIn('UNSUPPORTED_CUSTOMNPCS_RUNTIME', [i['code'] for i in report['issues']])

    def fixture_trees(self):
        baseline, bundle = self.root / 'baseline', self.root / 'bundle'
        path = 'server/customnpcs/clones/1/npc.json'
        original, incoming = b'{Name:"original",Health:20f}', b'{Name:"friend",Health:30f}'
        source = baseline / 'customnpcs/clones/1/npc.json'
        source.parent.mkdir(parents=True)
        source.write_bytes(original)
        dest = bundle / path
        dest.parent.mkdir(parents=True)
        dest.write_bytes(incoming)
        (bundle / 'npc-bundle.json').write_text(json.dumps(MANIFEST))
        mapping = self.root / 'mapping.json'
        mapping.write_text(json.dumps({'schema': 1, 'paths': {path: 'incoming'}}))
        return baseline, bundle, mapping, path, original, incoming

    def test_candidate_has_verified_original_backup_and_never_edits_sources(self):
        baseline, bundle, mapping, path, original, incoming = self.fixture_trees()
        output = self.root / 'output'
        with patch('tools.npc_intake.intake.runtime_mods', return_value=MODS):
            report = run(baseline, 'world', bundle, output, mapping=mapping, candidate=True)
        self.assertTrue(report['candidate_written'] and report['backup_verified'])
        self.assertEqual((output / 'backup' / path).read_bytes(), original)
        self.assertEqual((output / 'candidate' / path).read_bytes(), incoming)
        self.assertEqual((baseline / 'customnpcs/clones/1/npc.json').read_bytes(), original)
        self.assertEqual((bundle / path).read_bytes(), incoming)
        with self.assertRaises(ValueError):
            run(baseline, 'world', bundle, output)

    def test_dry_run_writes_no_overlay_and_zip_traversal_is_preserved_failure(self):
        baseline, bundle, mapping, path, original, incoming = self.fixture_trees()
        with patch('tools.npc_intake.intake.runtime_mods', return_value=MODS):
            report = run(baseline, 'world', bundle, self.root / 'dry', mapping=mapping)
        self.assertFalse(report['candidate_written'])
        self.assertFalse((self.root / 'dry/candidate').exists())
        source = self.root / 'hostile.zip'
        with zipfile.ZipFile(source, 'w') as archive:
            archive.writestr('../escape', b'bad')
        with self.assertRaises(ValueError):
            run(baseline, 'world', source, self.root / 'failed')
        self.assertTrue((self.root / 'failed/PRIVATE-NPC-FAILED.json').exists())
        self.assertFalse((self.root / 'escape').exists())


if __name__ == '__main__':
    unittest.main()
