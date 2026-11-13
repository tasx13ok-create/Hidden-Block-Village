"""Fixture tests for tools/world_merge: hostile input, format bounds, offsets, audits and fixes."""
import gzip
import io
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from contextlib import redirect_stdout, redirect_stderr

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.world_merge import anvil, audit, compare, edits, nbt, placement  # noqa: E402
from tools.world_merge import cli  # noqa: E402
from tools.world_merge.schematic import (Schematic, SchematicError, add_tile, new_schematic,  # noqa: E402
                                         sha256_file)

STONE, SAND, GRAVEL, WATER, FLOW_WATER, CHEST, LADDER, OAK_DOOR, IRON_DOOR = 1, 12, 13, 9, 8, 54, 65, 64, 71
OAK_STAIRS = 53


def fill(s, a, b, block, data=0):
    for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
        for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
            for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                s.set_block(x, y, z, block, data)


def save(s, folder, name):
    path = Path(folder) / name
    path.write_bytes(s.to_bytes())
    return path


def reload(s):
    name, root = nbt.loads(s.to_bytes())
    return Schematic(root, name)


def house():
    """16x8x16 slab of ground with a walled room, an oak door on the south wall and a chest."""
    s = new_schematic(16, 8, 16, origin=(100, 64, 200), offset=(-8, -1, -8))
    fill(s, (0, 0, 0), (15, 0, 15), STONE)
    fill(s, (4, 1, 4), (10, 4, 10), STONE)       # solid block, then hollow it
    fill(s, (5, 1, 5), (9, 3, 9), 0)
    s.set_block(7, 1, 10, OAK_DOOR, 3)            # lower half, facing north (passage along z)
    s.set_block(7, 2, 10, OAK_DOOR, 8)            # upper half
    s.set_block(6, 1, 6, CHEST, 3)
    add_tile(s, 'minecraft:chest', 6, 1, 6, {'Items': nbt.Tag(nbt.LIST, [nbt.compound({
        'Slot': nbt.Tag(nbt.BYTE, 0), 'id': nbt.Tag(nbt.STRING, 'minecraft:apple'),
        'Count': nbt.Tag(nbt.BYTE, 3), 'Damage': nbt.Tag(nbt.SHORT, 0)})], nbt.COMPOUND)})
    return s


class NBTHostileInputTests(unittest.TestCase):
    def test_round_trip_preserves_types_and_order(self):
        root = nbt.compound({'b': nbt.Tag(nbt.SHORT, 7), 'a': nbt.Tag(nbt.LONG_ARRAY, [1, -2]),
                             'l': nbt.Tag(nbt.LIST, [], nbt.STRING), 's': nbt.Tag(nbt.STRING, 'kunai —')})
        name, back = nbt.loads(nbt.dumps(root, 'R'))
        self.assertEqual('R', name)
        self.assertEqual(['b', 'a', 'l', 's'], list(back.value))
        self.assertEqual(nbt.SHORT, back['b'].type)
        self.assertEqual(root, back)

    def test_truncated_and_oversized_lengths_are_rejected_without_allocation(self):
        raw = nbt.dumps(nbt.compound({'x': nbt.Tag(nbt.BYTE_ARRAY, bytearray(64))}), compression=None)
        with self.assertRaises(nbt.NBTError):
            nbt.loads(raw[:-10])
        # Byte array claiming 2 GiB inside a 20-byte payload.
        hostile = b'\x0a\x00\x00\x07\x00\x01x' + struct.pack('>i', 0x7FFFFFF0) + b'\x00' * 4
        with self.assertRaisesRegex(nbt.NBTError, 'exceeds remaining'):
            nbt.loads(hostile)
        negative = b'\x0a\x00\x00\x07\x00\x01x' + struct.pack('>i', -1) + b'\x00'
        with self.assertRaisesRegex(nbt.NBTError, 'Negative'):
            nbt.loads(negative)

    def test_nesting_bomb_and_decompression_bomb_are_bounded(self):
        deep = b'\x0a\x00\x00' + (b'\x0a\x00\x01a' * 600) + b'\x00' * 601
        with self.assertRaisesRegex(nbt.NBTError, 'nesting'):
            nbt.loads(deep)
        buf = io.BytesIO()
        with gzip.GzipFile(fileobj=buf, mode='wb') as gz:
            gz.write(b'\x0a\x00\x00\x07\x00\x01x' + struct.pack('>i', 4 << 20) + bytes(4 << 20) + b'\x00')
        with self.assertRaisesRegex(nbt.NBTError, 'Decompressed'):
            nbt.loads(buf.getvalue(), max_bytes=1 << 20)

    def test_root_must_be_compound_and_trailing_bytes_are_rejected(self):
        with self.assertRaises(nbt.NBTError):
            nbt.loads(b'\x08\x00\x00\x00\x00')
        good = nbt.dumps(nbt.compound(), compression=None)
        with self.assertRaisesRegex(nbt.NBTError, 'Trailing'):
            nbt.loads(good + b'\x00')
        with self.assertRaisesRegex(nbt.NBTError, 'Corrupt gzip'):
            nbt.loads(b'\x1f\x8b' + b'\x00' * 30)


class SchematicFormatTests(unittest.TestCase):
    def test_wrong_array_lengths_and_bad_dimensions_are_rejected(self):
        s = new_schematic(2, 2, 2)
        s.root.value['Blocks'] = nbt.Tag(nbt.BYTE_ARRAY, bytearray(7))
        with self.assertRaisesRegex(SchematicError, 'Blocks length 7'):
            Schematic(s.root)
        s = new_schematic(2, 2, 2)
        s.root.value['Width'] = nbt.Tag(nbt.SHORT, -2)
        with self.assertRaisesRegex(SchematicError, 'Non-positive'):
            reload(s)
        s = new_schematic(2, 2, 2)
        s.root.value['Width'] = nbt.Tag(nbt.SHORT, 32000)
        s.root.value['Length'] = nbt.Tag(nbt.SHORT, 32000)
        with self.assertRaisesRegex(SchematicError, 'limit'):
            reload(s)

    def test_sponge_and_non_alpha_formats_are_refused(self):
        s = new_schematic(1, 1, 1)
        s.root.value['Materials'] = nbt.Tag(nbt.STRING, 'Pocket')
        with self.assertRaisesRegex(SchematicError, 'Materials'):
            reload(s)
        sponge = nbt.compound({'Palette': nbt.compound(), 'BlockData': nbt.Tag(nbt.BYTE_ARRAY, bytearray())})
        with self.assertRaisesRegex(SchematicError, 'Sponge'):
            Schematic(sponge)

    def test_add_blocks_use_worldedit_nibble_order_and_round_trip(self):
        s = new_schematic(3, 1, 1)  # odd cell count exercises the padding byte
        s.set_block(0, 0, 0, 300, 2)  # even index -> low nibble
        s.set_block(1, 0, 0, 0x5FF, 1)  # odd index -> high nibble
        packed = Schematic(s.root).root  # force pack via to_bytes
        back = reload(s)
        self.assertEqual((300, 2), back.get(0, 0, 0))
        self.assertEqual((0x5FF, 1), back.get(1, 0, 0))
        add = back.root['AddBlocks'].value
        self.assertEqual(0x01, add[0] & 0x0F)
        self.assertEqual(0x05, add[0] >> 4)
        self.assertEqual(2, len(add))  # (3 >> 1) + 1, as WorldEdit writes it
        report = audit.audit_format(back)
        self.assertEqual([300, 0x5FF], report['non_vanilla_ids'])
        self.assertEqual(2, report['extended_id_cells'])
        del packed

    def test_short_add_blocks_and_data_over_15_are_reported(self):
        s = new_schematic(4, 1, 1)
        s.root.value['AddBlocks'] = nbt.Tag(nbt.BYTE_ARRAY, bytearray(1))
        with self.assertRaisesRegex(SchematicError, 'AddBlocks'):
            reload(s)
        s = new_schematic(2, 1, 1)
        s.data[0] = 200
        self.assertEqual(1, audit.audit_format(s)['data_values_over_15'])

    def test_occupied_layers_are_reported_for_height_planning(self):
        s = new_schematic(2, 10, 2)
        s.set_block(0, 3, 0, STONE)
        s.set_block(1, 7, 1, STONE)
        self.assertEqual([3, 7], audit.audit_format(s)['occupied_y_range'])
        self.assertIsNone(audit.audit_format(new_schematic(1, 2, 1))['occupied_y_range'])


class PlacementTests(unittest.TestCase):
    def test_v17_cloud_metadata_cannot_be_pasted_at_either_stored_position(self):
        # Values from release/evidence/v17-intake-20261113.json (cloud_v17.schematic).
        s = new_schematic(1, 209, 1, origin=(96, 93, 1), offset=(-144, -123, -49))
        s.width = s.length = 288
        r = placement.placement_report(s)
        authored = r['scenarios']['authored_location (//paste -o)']
        blind = r['scenarios']['blind_paste_standing_at_stored_origin']
        self.assertFalse(authored['fits_build_height'])
        self.assertEqual(46, authored['above_world_layers'])     # 93 + 208 = 301
        self.assertFalse(blind['fits_build_height'])
        self.assertEqual(30, blind['below_world_layers'])        # 93 - 123 = -30
        self.assertEqual([0, 47], r['valid_min_y_range'])
        self.assertEqual([123, 170], r['valid_paste_y_range'])
        self.assertEqual([240, 216, 50], r['authored_copy_position'])

    def test_explicit_anchor_reports_equivalent_paste_position_and_regions(self):
        s = new_schematic(200, 148, 200, origin=(96, 21, 1), offset=(-96, -21, -1))
        r = placement.placement_report(s, anchor_local=(100, 16, 100), anchor_world=(0, 63, -10))
        e = r['scenarios']['explicit_anchor']
        self.assertEqual([-100, 47, -110], e['min'])
        self.assertTrue(e['fits_build_height'])
        self.assertEqual([-4, 68, -109], e['equivalent_paste_position'])  # matches the v9 Mist note
        self.assertEqual({'x': [-1, 0], 'z': [-1, 0]}, e['regions'])


class TileAndGravityTests(unittest.TestCase):
    def test_tile_identity_and_coordinate_defects(self):
        s = house()
        add_tile(s, 'minecraft:chest', 6, 1, 6)            # duplicate position
        add_tile(s, 'Chest', 50, 1, 1)                     # out of bounds, legacy id
        add_tile(s, 'minecraft:furnace', 0, 0, 0)          # furnace tile on stone
        s.set_block(1, 1, 1, 137)
        add_tile(s, 'minecraft:command_block', 1, 1, 1, {'Command': nbt.Tag(nbt.STRING, '/op everyone')})
        s.set_block(2, 1, 1, CHEST)                        # chest block without tile
        r = audit.audit_tiles(s)
        kinds = sorted(i['kind'] for i in r['issues'])
        self.assertEqual(['command_block_present', 'tile_block_mismatch', 'tile_duplicate_position',
                          'tile_out_of_bounds'], kinds)
        cmd = [i for i in r['issues'] if i['kind'] == 'command_block_present'][0]
        self.assertEqual(12, cmd['command_chars'])
        self.assertNotIn('/op', json.dumps(r))             # embedded command text is never echoed
        self.assertEqual([(2, 1, 1)], [m['pos'] for m in r['blocks_missing_tile']['sample']])
        self.assertEqual(1, r['containers'][0]['item_stacks'])

    def test_gravity_blocks_over_air_and_liquid_but_not_over_solid_or_floor(self):
        s = new_schematic(4, 4, 1)
        s.set_block(0, 2, 0, SAND)                        # over air
        s.set_block(1, 1, 0, WATER)
        s.set_block(1, 2, 0, GRAVEL)                      # over water
        s.set_block(2, 1, 0, STONE)
        s.set_block(2, 2, 0, SAND)                        # supported
        s.set_block(3, 0, 0, SAND)                        # bottom layer: depends on paste terrain
        r = audit.audit_gravity(s)
        self.assertEqual([(0, 2, 0), (1, 2, 0)], [u['pos'] for u in r['unsupported']['sample']])
        self.assertEqual([(3, 0, 0)], r['on_schematic_floor']['sample'])


class FluidTests(unittest.TestCase):
    def basin(self):
        s = new_schematic(9, 4, 9)
        fill(s, (1, 0, 1), (7, 2, 7), STONE)
        fill(s, (2, 1, 2), (6, 2, 6), WATER)
        return s

    def test_contained_pool_is_not_a_defect(self):
        r = audit.audit_fluids(self.basin())
        self.assertEqual({'contained': 1}, r['by_classification'])

    def test_breach_in_basin_wall_is_a_leak_with_coordinates(self):
        s = self.basin()
        s.set_block(7, 2, 4, 0)       # wall hole next to source at (6,2,4)
        r = audit.audit_fluids(s)
        self.assertEqual({'leak_candidate': 1}, r['by_classification'])
        outlet = r['details'][0]['source_outlets']['sample'][0]
        self.assertEqual(((6, 2, 4), (7, 2, 4)), (outlet['from'], outlet['into']))

    def test_sea_reaching_side_edge_is_open_boundary_not_a_leak(self):
        s = new_schematic(6, 3, 6)
        fill(s, (0, 0, 0), (5, 0, 5), STONE)
        fill(s, (0, 1, 0), (5, 1, 5), WATER)
        self.assertEqual({'open_boundary': 1}, audit.audit_fluids(s)['by_classification'])

    def test_flowing_waterfall_is_classified_as_authored_flow(self):
        s = new_schematic(5, 6, 3)
        fill(s, (0, 0, 0), (4, 5, 2), STONE)
        fill(s, (1, 1, 1), (3, 5, 1), 0)              # a shaft open at the top
        s.set_block(2, 5, 1, FLOW_WATER, 8)           # falling water column
        s.set_block(2, 4, 1, FLOW_WATER, 8)
        r = audit.audit_fluids(s)
        self.assertEqual({'authored_flow': 1}, r['by_classification'])


class RouteAndAccessTests(unittest.TestCase):
    def test_chest_behind_door_is_reachable_but_sealed_room_is_not(self):
        s = house()
        r, _, _ = audit.audit_routes(s, entrances=[(1, 1, 1)])
        self.assertEqual([], r['invalid_entrances'])
        self.assertEqual(0, r['unreachable_containers']['count'])
        s.set_block(7, 1, 10, STONE)    # brick up the doorway
        s.set_block(7, 2, 10, STONE)
        r, _, _ = audit.audit_routes(s, entrances=[(1, 1, 1)])
        self.assertEqual([{'pos': (6, 1, 6), 'id': 'chest'}], r['unreachable_containers']['sample'])
        regions = r['unreached_regions_16plus']['largest']
        self.assertTrue(any(reg['bbox'][0][1] == 1 and reg['bbox'][0][0] == 5 for reg in regions), regions)

    def test_iron_door_without_redstone_blocks_the_route(self):
        s = house()
        s.set_block(7, 1, 10, IRON_DOOR, 3)
        s.set_block(7, 2, 10, IRON_DOOR, 8)
        r, rg, _ = audit.audit_routes(s, entrances=[(1, 1, 1)])
        self.assertEqual(1, r['unreachable_containers']['count'])
        self.assertEqual(1, audit.audit_access(s, rg)['iron_doors_need_redstone']['count'])

    def test_ladder_climb_reaches_upper_target_and_missing_ladder_does_not(self):
        s = new_schematic(5, 10, 5)
        fill(s, (0, 0, 0), (4, 0, 4), STONE)
        fill(s, (2, 1, 3), (2, 6, 3), STONE)          # pillar
        fill(s, (1, 6, 3), (3, 6, 4), STONE)          # upper ledge
        for y in range(1, 7):
            s.set_block(2, y, 2, LADDER, 2)           # facing north: supported by pillar at z+1
        r, _, _ = audit.audit_routes(s, entrances=[(0, 1, 0)], targets=[(1, 7, 4)])
        self.assertTrue(r['targets'][0]['reachable'])
        fill(s, (2, 1, 2), (2, 6, 2), 0)
        r, _, _ = audit.audit_routes(s, entrances=[(0, 1, 0)], targets=[(1, 7, 4)])
        self.assertFalse(r['targets'][0]['reachable'])

    def test_step_up_needs_headroom_unless_onto_a_stair_and_drop_is_bounded(self):
        s = new_schematic(4, 8, 1)
        fill(s, (0, 0, 0), (3, 0, 0), STONE)
        s.set_block(1, 1, 0, STONE)                   # one-block step
        s.set_block(0, 3, 0, STONE)                   # ceiling at y=3 above the start
        r, _, _ = audit.audit_routes(s, entrances=[(0, 1, 0)], targets=[(1, 2, 0)])
        self.assertFalse(r['targets'][0]['reachable'])
        s.set_block(1, 1, 0, OAK_STAIRS, 0)           # stair ascending east: no jump needed
        r, _, _ = audit.audit_routes(s, entrances=[(0, 1, 0)], targets=[(1, 2, 0)])
        self.assertTrue(r['targets'][0]['reachable'])
        s2 = new_schematic(2, 8, 1)
        s2.set_block(0, 5, 0, STONE)
        s2.set_block(1, 0, 0, STONE)                  # 5-block drop is not a safe route
        r, _, _ = audit.audit_routes(s2, entrances=[(0, 6, 0)], targets=[(1, 1, 0)])
        self.assertFalse(r['targets'][0]['reachable'])

    def test_invalid_entrance_is_reported_not_silently_ignored(self):
        r, _, _ = audit.audit_routes(house(), entrances=[(5, 0, 5), (99, 1, 1)])
        self.assertEqual([(5, 0, 5), (99, 1, 1)], r['invalid_entrances'])

    def test_door_halves_ladders_and_blocked_doors(self):
        s = house()
        s.set_block(2, 1, 2, OAK_DOOR, 0)             # lower half without upper
        s.set_block(12, 3, 12, OAK_DOOR, 8)           # floating upper half
        s.set_block(7, 1, 11, STONE)                  # block the outside of the real door
        s.set_block(13, 1, 13, LADDER, 3)             # facing south, needs support at z-1 (air)
        r = audit.audit_access(s)
        self.assertEqual({((2, 1, 2), 'lower'), ((12, 3, 12), 'upper')},
                         {(o['pos'], o['half']) for o in r['door_orphan_halves']['sample']})
        self.assertEqual([(7, 1, 10)], [d['pos'] for d in r['doors_blocked']['sample']])
        self.assertEqual('no_solid_support', r['ladder_problems']['sample'][0]['problem'])


class EditPlanTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        s = house()
        s.set_block(12, 3, 12, SAND)                  # unsupported
        s.set_block(2, 1, 2, OAK_DOOR, 0)             # orphan lower half
        s.root.value['AuthorNote'] = nbt.Tag(nbt.STRING, 'keep me')
        self.input = save(s, self.dir, 'in.schematic')
        self.input_hash = sha256_file(self.input)

    def tearDown(self):
        self.tmp.cleanup()

    def plan(self, edits_list, **extra):
        path = self.dir / ('plan%d.json' % len(list(self.dir.glob('plan*.json'))))
        path.write_text(json.dumps(dict({'schema': 1, 'edits': edits_list}, **extra)))
        return path

    def test_proposed_fixes_apply_to_new_candidate_and_clear_findings(self):
        schem = Schematic.load(self.input)
        report = audit.full_audit(schem)
        plan = edits.propose_fixes(schem, report, self.input_hash)
        self.assertEqual('PROPOSED_FOR_REVIEW', plan['status'])
        self.assertEqual({'gravity-12-2-12', 'door-upper-2-2-2'}, {e['id'] for e in plan['edits']})
        out, log = self.dir / 'cand.schematic', self.dir / 'cand-edits.json'
        result = edits.apply_plan(self.input, self.plan(plan['edits'], input_sha256=self.input_hash), out, log)
        self.assertTrue(result['verification']['ok'])
        self.assertEqual(2, result['verification']['changed_cells'])
        self.assertEqual(self.input_hash, sha256_file(self.input))     # input untouched
        self.assertEqual(result['output']['sha256'], sha256_file(out))
        cand = Schematic.load(out)
        after = audit.full_audit(cand)
        self.assertEqual(0, after['gravity']['unsupported']['count'])
        self.assertEqual(0, after['access']['door_orphan_halves']['count'])
        self.assertEqual('keep me', cand.root['AuthorNote'].value)
        self.assertEqual(1, len(cand.tile_tags[0]['Items'].value))       # inventory preserved
        self.assertTrue(json.loads(log.read_text())['verification']['ok'])

    def test_expectation_mismatch_aborts_whole_plan_and_writes_nothing(self):
        out = self.dir / 'never.schematic'
        bad = [{'id': 'ok', 'pos': [12, 2, 12], 'expect': {'block': 0}, 'set': {'block': 24}, 'reason': 'r'},
               {'id': 'wrong', 'pos': [0, 0, 0], 'expect': {'block': 2}, 'set': {'block': 3}, 'reason': 'r'}]
        with self.assertRaisesRegex(edits.EditError, 'expected 2:0'):
            edits.apply_plan(self.input, self.plan(bad), out)
        self.assertFalse(out.exists())

    def test_refuses_overwrite_wrong_input_hash_tile_cells_and_unreasoned_edits(self):
        edit = {'id': 'e', 'pos': [12, 2, 12], 'expect': {'block': 0}, 'set': {'block': 24}, 'reason': 'r'}
        existing = self.dir / 'exists.schematic'
        existing.write_bytes(b'')
        with self.assertRaises(FileExistsError):
            edits.apply_plan(self.input, self.plan([edit]), existing)
        with self.assertRaises(FileExistsError):
            edits.apply_plan(self.input, self.plan([edit]), self.input)
        with self.assertRaisesRegex(edits.EditError, 'written for input'):
            edits.apply_plan(self.input, self.plan([edit], input_sha256='0' * 64), self.dir / 'a.schematic')
        chest = {'id': 'c', 'pos': [6, 1, 6], 'expect': {'block': CHEST, 'data': 3}, 'set': {'block': 1}, 'reason': 'r'}
        with self.assertRaisesRegex(edits.EditError, 'tile entity'):
            edits.apply_plan(self.input, self.plan([chest]), self.dir / 'b.schematic')
        chest['tile'] = 'keep'
        with self.assertRaisesRegex(edits.EditError, 'cannot keep'):
            edits.apply_plan(self.input, self.plan([chest]), self.dir / 'c.schematic')
        chest['tile'] = 'remove'
        log = edits.apply_plan(self.input, self.plan([chest]), self.dir / 'd.schematic')
        self.assertEqual([], Schematic.load(self.dir / 'd.schematic').tile_tags)
        self.assertTrue(log['verification']['ok'])
        with self.assertRaisesRegex(edits.EditError, 'reason'):
            edits.apply_plan(self.input, self.plan([dict(edit, reason='')]), self.dir / 'e.schematic')
        with self.assertRaisesRegex(edits.EditError, 'twice'):
            edits.apply_plan(self.input, self.plan([edit, dict(edit, id='f')]), self.dir / 'f.schematic')


class CompareTests(unittest.TestCase):
    def test_explicit_alignment_and_tile_vote_suggestion(self):
        old = house()
        new = new_schematic(24, 10, 24)
        dx, dy, dz = 5, 1, 3
        for x in range(16):
            for y in range(8):
                for z in range(16):
                    b, d = old.get(x, y, z)
                    new.set_block(x + dx, y + dy, z + dz, b, d)
        add_tile(new, 'minecraft:chest', 6 + dx, 1 + dy, 6 + dz)
        new.set_block(20, 5, 20, STONE)  # a v17 addition outside the old footprint
        right = compare.compare(old, new, (dx, dy, dz))
        self.assertEqual(1.0, right['retained_fraction_of_old_non_air'])
        self.assertEqual({'matched': 1}, right['tiles'])
        wrong = compare.compare(old, new, (0, 0, 0))
        self.assertLess(wrong['retained_fraction_of_old_non_air'], 0.5)
        self.assertEqual([dx, dy, dz], compare.suggest_alignments(old, new)[0]['delta'])
        self.assertEqual((dx, dy, dz), compare.delta_from_anchor((7, 1, 10), (12, 2, 13)))


def make_world(folder):
    world = Path(folder) / 'source-world'
    (world / 'region').mkdir(parents=True)
    (world / 'level.dat').write_bytes(nbt.dumps(nbt.compound({'Data': nbt.compound()})))
    # One pre-existing chunk at (0,0) with stone at y=0..3, a Forge tag and a tile far from the paste.
    root = anvil.new_chunk(0, 0)
    level = root['Level']
    sec = anvil._section(level, 0, create=True)
    for i in range(4 * 256):
        sec['Blocks'].value[i] = STONE
    level.value['ForgeCaps'] = nbt.compound({'mod:cap': nbt.Tag(nbt.INT, 9)})
    level['TileEntities'].value.append(nbt.compound({'id': nbt.Tag(nbt.STRING, 'minecraft:sign'),
                                                     'x': nbt.Tag(nbt.INT, 15), 'y': nbt.Tag(nbt.INT, 3),
                                                     'z': nbt.Tag(nbt.INT, 15)}))
    region = anvil.Region(world / 'region' / 'r.0.0.mca')
    region.store_chunk(0, '', root)
    root2 = anvil.new_chunk(5, 5)
    region.store_chunk(5 + 5 * 32, '', root2)          # untouched neighbour chunk
    region.save_atomic()
    return world


class WorldPasteTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.source = make_world(self.dir)
        self.source_hashes = {p.name: sha256_file(p) for p in self.source.rglob('*') if p.is_file()}

    def tearDown(self):
        self.tmp.cleanup()

    def test_paste_requires_candidate_copy_and_never_touches_source(self):
        with self.assertRaisesRegex(anvil.WorldError, 'not a candidate'):
            anvil.paste_into_world_copy(house(), self.source, (0, 4, 0))
        with self.assertRaises(FileExistsError):
            anvil.copy_world(self.source, self.source)
        with self.assertRaises(anvil.WorldError):
            anvil.copy_world(self.source, self.source / 'nested')
        manifest = anvil.copy_world(self.source, self.dir / 'cand')
        self.assertEqual(2, manifest['files'])
        self.assertEqual(self.source_hashes, {p.name: sha256_file(p) for p in self.source.rglob('*') if p.is_file()})

    def test_paste_across_negative_region_boundary_places_blocks_tiles_and_rolls_back(self):
        cand = self.dir / 'cand'
        anvil.copy_world(self.source, cand)
        before = sha256_file(cand / 'region' / 'r.0.0.mca')
        untouched = anvil.Region(cand / 'region' / 'r.0.0.mca').chunks[5 + 5 * 32]
        s = house()
        receipt = anvil.paste_into_world_copy(s, cand, (-8, 4, -8), source_path=None)
        self.assertEqual(4, len(receipt['regions']))     # r.-1.-1, r.-1.0, r.0.-1, r.0.0
        self.assertEqual(3, receipt['totals']['chunks_created'])
        # Schematic local (6,1,6) chest -> world (-2,5,-2); door (7,1,10) -> (-1,5,2).
        self.assertEqual((CHEST, 3), anvil.read_block(cand, -2, 5, -2))
        self.assertEqual((OAK_DOOR, 3), anvil.read_block(cand, -1, 5, 2))
        self.assertEqual((STONE, 0), anvil.read_block(cand, 7, 4, 7))   # schematic floor layer
        self.assertEqual((STONE, 0), anvil.read_block(cand, 7, 3, 7))   # world below the paste
        tiles = anvil.chunk_tiles(cand, -1, -1)
        self.assertEqual([(-2, 5, -2)], [tuple(t.scalar(a) for a in 'xyz') for t in tiles])
        self.assertEqual(1, len(tiles[0]['Items'].value))
        chunk00 = anvil.Region(cand / 'region' / 'r.0.0.mca')
        lvl = chunk00.load_chunk(0)[1]['Level']
        self.assertEqual(9, lvl['ForgeCaps']['mod:cap'].value)          # unknown tags kept
        self.assertEqual(0, lvl['LightPopulated'].value)
        self.assertEqual([(15, 3, 15)], [tuple(t.scalar(a) for a in 'xyz') for t in lvl['TileEntities'].value])
        self.assertEqual(untouched, chunk00.chunks[5 + 5 * 32])         # raw bytes unchanged
        self.assertEqual(self.source_hashes, {p.name: sha256_file(p) for p in self.source.rglob('*') if p.is_file()})
        result = anvil.rollback(cand, 1)
        self.assertEqual(0, result['remaining_pastes'])
        self.assertEqual(before, sha256_file(cand / 'region' / 'r.0.0.mca'))
        self.assertFalse((cand / 'region' / 'r.-1.-1.mca').exists())

    def test_out_of_height_paste_is_refused_before_any_write(self):
        cand = self.dir / 'cand'
        anvil.copy_world(self.source, cand)
        before = sha256_file(cand / 'region' / 'r.0.0.mca')
        with self.assertRaisesRegex(anvil.WorldError, 'outside 0..255'):
            anvil.paste_into_world_copy(house(), cand, (0, 250, 0))
        self.assertEqual(before, sha256_file(cand / 'region' / 'r.0.0.mca'))
        self.assertFalse((self.dir / 'cand.rollback').exists())

    def test_corrupt_region_header_is_rejected(self):
        bad = self.dir / 'r.0.0.mca'
        bad.write_bytes(struct.pack('>I', (9999 << 8) | 1) + bytes(8188))
        with self.assertRaisesRegex(anvil.WorldError, 'outside the file'):
            anvil.Region(bad)


class AnchorTests(unittest.TestCase):
    def gates(self):
        return {'schema': 1, 'anchors': {
            'gate': {'dungeon': 'sakura_v17', 'kind': 'arrival', 'local_pos': None},
            'chest_room': {'dungeon': 'sakura_v17', 'kind': 'checkpoint', 'local_pos': None},
            'in_wall': {'dungeon': 'sakura_v17', 'kind': 'marker', 'local_pos': None},
            'other': {'dungeon': 'mist_v17', 'kind': 'arrival', 'local_pos': [1, 1, 1]}}}

    def test_suggest_finds_edge_arrival_open_area_and_container_standing_cell(self):
        from tools.world_merge import anchors
        r = anchors.suggest(house(), min_area=20)
        self.assertTrue(r['arrival_candidates'] and r['arrival_candidates'][0]['y'] == 1)
        self.assertEqual(1, r['open_areas'][0]['y'])
        chest = r['containers'][0]
        self.assertTrue(chest['reachable'] and chest['pos'] == [6, 1, 6])
        s = house()
        s.set_block(7, 1, 10, STONE)
        s.set_block(7, 2, 10, STONE)
        self.assertFalse(anchors.suggest(s)['containers'][0]['reachable'])

    def test_verify_classifies_each_anchor_and_fill_writes_only_verified_to_new_file(self):
        from tools.world_merge import anchors
        measured = {'gate': [1, 1, 1], 'chest_room': [6, 1, 7], 'in_wall': [4, 2, 4]}
        rows = {r['anchor']: r['status'] for r in anchors.verify(house(), self.gates(), 'sakura_v17', [(1, 1, 1)], measured)}
        self.assertEqual({'gate': 'ok', 'chest_room': 'ok', 'in_wall': 'not_a_route_node'}, rows)
        s = house()
        s.set_block(7, 1, 10, STONE)
        s.set_block(7, 2, 10, STONE)
        rows2 = {r['anchor']: r['status'] for r in anchors.verify(s, self.gates(), 'sakura_v17', [(1, 1, 1)], measured)}
        self.assertEqual('unreachable_from_entrance', rows2['chest_room'])
        self.assertEqual('unmeasured', {r['anchor']: r['status'] for r in anchors.verify(house(), self.gates(), 'sakura_v17')}['gate'])
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / 'gates.json'
            src.write_text(json.dumps(self.gates()))
            rows = anchors.verify(house(), self.gates(), 'sakura_v17', [(1, 1, 1)], measured)
            with self.assertRaises(FileExistsError):
                anchors.fill(src, src, rows)
            out = Path(tmp) / 'gates.measured.json'
            applied = anchors.fill(src, out, rows)
            self.assertEqual(['chest_room', 'gate'], sorted(applied))
            data = json.loads(out.read_text())
            self.assertEqual([6, 1, 7], data['anchors']['chest_room']['local_pos'])
            self.assertIsNone(data['anchors']['in_wall']['local_pos'])
            self.assertIsNone(json.loads(src.read_text())['anchors']['gate']['local_pos'])


class DoorwayTests(unittest.TestCase):
    def test_bricked_doorway_is_proposed_minimally_and_fix_restores_route(self):
        from tools.world_merge import doorways
        s = house()
        s.set_block(7, 1, 10, 95)      # the v17 defect: stained glass sealing the doorway
        s.set_block(7, 2, 10, 95)
        r = doorways.propose(s)
        self.assertEqual(1, len(r['proposals']))
        p = r['proposals'][0]
        self.assertEqual([[6, 1, 6]], [list(c) for c in p['containers']])
        self.assertEqual(2, p['cost'])
        self.assertEqual({(7, 1, 10), (7, 2, 10)}, {tuple(e['pos']) for e in p['edits']})
        sets = {tuple(e['pos']): (e['set']['block'], e['set']['data']) for e in p['edits']}
        self.assertEqual((OAK_DOOR, 1), sets[(7, 1, 10)])    # walking south (+z) out of the room
        self.assertEqual((OAK_DOOR, 8), sets[(7, 2, 10)])
        for e in p['edits']:
            self.assertEqual({'block': 95, 'data': 0}, e['expect'])
            s.set_block(*e['pos'], block=e['set']['block'], data=e['set']['data'])
        self.assertEqual(0, audit.audit_access(s)['door_orphan_halves']['count'])
        routes, _, _ = audit.audit_routes(s, entrances=[(1, 1, 1)])
        self.assertEqual(0, routes['unreachable_containers']['count'])

    def test_tile_and_fluid_cells_are_never_cleared(self):
        from tools.world_merge import doorways
        s = house()
        s.set_block(7, 1, 10, CHEST)
        add_tile(s, 'minecraft:chest', 7, 1, 10)
        s.set_block(7, 2, 10, STONE)
        fill(s, (4, 1, 4), (4, 2, 10), STONE)  # keep other walls intact
        r = doorways.propose(s, max_cost=2)
        for prop in r['proposals']:
            self.assertNotIn([7, 1, 10], [e['pos'] for e in prop['edits']])


class NpcInventoryTests(unittest.TestCase):
    def test_nbt_json_dialect_and_metadata_only_report(self):
        from tools.world_merge import npcs
        self.assertEqual({'a': 1, 'b': [1, 2], 'c': 1.5, 's': 'keep 1b'},
                         npcs.parse(b'{"a": 1b, "b": [I; 1, 2], "c": 1.5f, "s": "keep 1b"}'))
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'world' / 'customnpcs'
            (root / 'dialogs' / 'village').mkdir(parents=True)
            (root / 'quests' / 'main').mkdir(parents=True)
            (root / 'clones' / '1').mkdir(parents=True)
            (root / 'scripts').mkdir()
            (root / 'dialogs' / 'village' / '1.json').write_text(
                '{"DialogId": 1, "DialogTitle": "Secret greeting", "DialogText": "PRIVATE TEXT", "DialogQuest": 7,'
                ' "Options": [{"OptionSlot": 0b, "Option": {"Title": "Go", "Dialog": 2, "DialogCommand": "/give @p diamond 64"}},'
                ' {"OptionSlot": 1b, "Option": {"Title": "Bye", "Dialog": 9}}]}')
            (root / 'dialogs' / 'village' / '2.json').write_text('{"DialogId": 2, "DialogTitle": "x", "Options": []}')
            (root / 'quests' / 'main' / '7.json').write_text('{"Title": "q", "NextQuestId": -1}')
            (root / 'clones' / '1' / 'Sensei.json').write_text(
                '{"id": "customnpcs:customnpc", "Name": "PRIVATE NAME", "Weapons": [{"id": "narutomod:kunai"}], "ScriptEnabled": 1b}')
            (root / 'scripts' / 'boss.js').write_text('function interact(e){ e.API.executeCommand("op x"); }')
            (root / 'broken.json').write_text('{not json')
            report = npcs.inventory(Path(tmp) / 'world')
            dumped = json.dumps(report)
            for private in ('PRIVATE TEXT', 'Secret greeting', 'PRIVATE NAME', 'Sensei', '/give', 'executeCommand'):
                self.assertNotIn(private, dumped)
            self.assertEqual(2, len(report['dialogs']))
            self.assertEqual(2, report['dialogs']['1']['options'])
            self.assertEqual({'dialog_options_with_commands': 1, 'total_command_chars': 19}, report['commands'])
            self.assertEqual([{'from_dialog': 1, 'to_dialog': 9}], report['broken_references'])
            self.assertEqual(['customnpcs', 'narutomod'], report['dependency_namespaces'])
            self.assertTrue(report['clones'][0]['has_script_data'])
            self.assertEqual(1, report['scripts']['files'])
            self.assertEqual([{'path': 'broken.json', 'reason': 'not_json_or_nbt_json'}], report['unparsed'])
            self.assertFalse(npcs.inventory(Path(tmp))['customnpcs_present'])


class CliTests(unittest.TestCase):
    def run_cli(self, *argv):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = cli.main(list(argv))
        return code, out.getvalue(), err.getvalue()

    def test_audit_strict_exit_codes_and_report_overwrite_refusal(self):
        with tempfile.TemporaryDirectory() as tmp:
            good = save(house(), tmp, 'good.schematic')
            report = Path(tmp) / 'r.json'
            code, _, _ = self.run_cli('audit', '--input', str(good), '--output', str(report),
                                      '--entrance', '1,1,1', '--strict')
            self.assertEqual(0, code, report.read_text())
            data = json.loads(report.read_text())
            self.assertEqual(sha256_file(good), data['input']['sha256'])
            code, _, err = self.run_cli('audit', '--input', str(good), '--output', str(report))
            self.assertEqual(2, code)
            self.assertIn('Refusing to overwrite', err)
            s = house()
            s.set_block(12, 3, 12, SAND)
            bad = save(s, tmp, 'bad.schematic')
            code, out, _ = self.run_cli('audit', '--input', str(bad), '--entrance', '1,1,1', '--strict')
            self.assertEqual(1, code)
            self.assertTrue(json.loads(out)['needs_review'])
            garbage = Path(tmp) / 'garbage.schematic'
            garbage.write_bytes(b'\x1f\x8bnot really gzip')
            code, _, err = self.run_cli('audit', '--input', str(garbage))
            self.assertEqual(2, code)
            self.assertIn('error:', err)

    def test_batch_writes_per_file_reports_and_aggregate_summary_without_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / 'in'
            src.mkdir()
            save(house(), src, 'a_v17.schematic')
            s = house()
            s.set_block(12, 3, 12, SAND)
            save(s, src, 'b_v17.schematic')
            out = Path(tmp) / 'out'
            code, stdout, _ = self.run_cli('batch', '--input-dir', str(src), '--output-dir', str(out))
            self.assertEqual(0, code)
            summary = json.loads((out / 'summary.json').read_text())
            rows = {r['file']: r for r in summary['files']}
            self.assertEqual(0, rows['a_v17.schematic']['unsupported_gravity'])
            self.assertEqual(1, rows['b_v17.schematic']['unsupported_gravity'])
            self.assertTrue(rows['b_v17.schematic']['needs_review'])
            self.assertTrue((out / 'a_v17.audit.json').is_file())
            code, _, err = self.run_cli('batch', '--input-dir', str(src), '--output-dir', str(out))
            self.assertEqual(2, code)
            self.assertIn('Refusing to overwrite', err)

    def test_compare_requires_exactly_one_alignment(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = save(house(), tmp, 'a.schematic')
            with self.assertRaises(SystemExit):
                self.run_cli('compare', '--old', str(a), '--new', str(a))
            code, out, _ = self.run_cli('compare', '--old', str(a), '--new', str(a), '--delta', '0,0,0')
            self.assertEqual(0, code)
            self.assertEqual(1.0, json.loads(out)['retained_fraction_of_old_non_air'])


if __name__ == '__main__':
    unittest.main()
