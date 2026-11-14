"""Negative readback boundaries and 12-bit Alpha independence."""
import gzip
from pathlib import Path
import struct
import tempfile
import unittest

from tools.world_merge import nbt
from tools.arenas.audit import AuditError, Reader, readback, _standing, audit_layer_checks, audit_stairs, audit_stadium, audit_route_light_fixtures


def fixture(path, blocks=None, data=None, add=None, size=(3,5,3),owners=None,names=None,tiles=None):
    count=size[0]*size[1]*size[2]
    values={key:nbt.Tag(nbt.SHORT,value) for key,value in zip(('Width','Height','Length'),size)}
    values.update({'Materials':nbt.Tag(nbt.STRING,'Alpha'),
        'Blocks':nbt.Tag(nbt.BYTE_ARRAY,blocks or bytes(count)),
        'Data':nbt.Tag(nbt.BYTE_ARRAY,data or bytes(count)),
        'Entities':nbt.Tag(nbt.LIST,[],nbt.COMPOUND),'TileEntities':nbt.Tag(nbt.LIST,tiles or [],nbt.COMPOUND)})
    if add is not None: values['AddBlocks']=nbt.Tag(nbt.BYTE_ARRAY,add)
    if owners is not None:
        import json
        values['ArenaLayers']=nbt.Tag(nbt.BYTE_ARRAY,owners)
        values['ArenaLayerNames']=nbt.Tag(nbt.STRING,json.dumps(names or {}))
    path.write_bytes(nbt.dumps(nbt.compound(values),'Schematic',compression='gzip'))


class IndependentReadbackTests(unittest.TestCase):
    def test_addblocks_even_low_odd_high(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic'
            fixture(path,blocks=bytes([1,2])+bytes(43),add=bytes([0xA3])+bytes(22))
            with readback(path) as v:
                self.assertEqual(v.block(0,0,0),0x301)
                self.assertEqual(v.block(1,0,0),0xA02)

    def test_truncated_and_wrong_array_length_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic'
            fixture(path,blocks=bytes(44))
            with self.assertRaisesRegex(AuditError,'array length'):
                with readback(path): pass
            fixture(path)
            path.write_bytes(path.read_bytes()[:-5])
            with self.assertRaises((AuditError,EOFError)):
                with readback(path): pass

    def test_duplicate_tags_and_negative_lengths_rejected(self):
        import io
        with tempfile.TemporaryDirectory() as temp:
            payload=b'\x02\x00\x01x\x00\x01\x02\x00\x01x\x00\x02\x00'
            with self.assertRaisesRegex(AuditError,'Duplicate'):
                Reader(io.BytesIO(payload),temp).payload(10)
            with self.assertRaisesRegex(AuditError,'array size'):
                Reader(io.BytesIO(struct.pack('>i',-1)),temp).payload(7)

    def test_headroom_catches_render_only_route(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic';blocks=bytearray(45)
            blocks[:9]=bytes([1])*9
            blocks[27+4]=1
            fixture(path,blocks=blocks)
            with readback(path) as v:
                self.assertIsNone(_standing(v,[0,1,0]))
                self.assertIn('headroom',_standing(v,[1,1,1]))

    def test_fluid_is_not_dry_standing_support(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic';blocks=bytearray(45);blocks[4]=9
            fixture(path,blocks=blocks)
            with readback(path) as v:
                self.assertEqual(_standing(v,[1,1,1]),'missing/unsafe support')

    def test_missing_and_corrupt_required_layers_fail_independent_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic';blocks=bytearray(45);blocks[4]=98
            fixture(path,blocks=blocks)
            row={'geometry':{'structural_layers':['supports','interiors']},'layer_checks':{
                'supports':{'required':True,'written_cells':1,'surviving_witnesses':[{'position':[1,0,1],'block':98,'meta':0}]}}}
            with readback(path) as v:
                result=audit_layer_checks(v,row)
                self.assertEqual(result['layers']['supports']['status'],'PASS')
                self.assertEqual(result['layers']['interiors']['status'],'FAIL')
                row['layer_checks']['supports']['surviving_witnesses'][0]['block']=1
                self.assertEqual(audit_layer_checks(v,row)['layers']['supports']['status'],'FAIL')

    def test_walking_stair_metadata_is_not_inferred_from_render(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic';blocks=bytearray(45);blocks[4]=53
            fixture(path,blocks=blocks)
            geometry={'walking_stairs':{'count':1,'samples':[{'position':[1,0,1],'block':53,'meta':0}]}}
            with readback(path) as v:
                self.assertEqual(audit_stairs(v,geometry)['status'],'PASS')
                geometry['walking_stairs']['samples'][0]['meta']=4
                self.assertEqual(audit_stairs(v,geometry)['status'],'FAIL')

    def test_plot_terrain_and_extreme_marker_do_not_pass_stadium_size(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic'
            blocks=bytes([98])*45
            fixture(path,blocks=blocks,owners=bytes([1])*45,names={'1':'terrain_and_foundation'})
            with readback(path) as v:
                report=audit_stadium(v)
                self.assertEqual(report['architecture_non_air'],0)
                self.assertEqual(report['status'],'FAIL')
            owners=bytearray([1])*45;owners[-1]=2
            fixture(path,blocks=blocks,owners=owners,names={'1':'terrain_and_foundation','2':'landmark_masses'})
            with readback(path) as v:
                report=audit_stadium(v)
                self.assertEqual(report['architecture_non_air'],1)
                self.assertEqual(report['status'],'FAIL')
            fixture(path,blocks=bytes([9])*45,owners=bytes([2])*45,names={'2':'stadium_structure'})
            with readback(path) as v:
                self.assertEqual(audit_stadium(v)['architecture_non_air'],0)

    def test_layer_array_must_match_full_cell_count(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic'
            fixture(path,owners=bytes(44),names={'1':'stadium_structure'})
            with self.assertRaisesRegex(AuditError,'ArenaLayers length'):
                with readback(path): pass

    def test_exposed_light_path_rejects_opaque_stairs_and_lost_bearing(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic';blocks=bytearray(45)
            blocks[13]=169;blocks[4]=98
            geometry={'routes':{'primary':[[0,1,1]]},
                'route_light_path_design':{'added_exposed_fixtures':1},
                'route_light_fixtures':[{'position':[1,1,1],'block':169,'meta':0,
                    'attached_to':[1,0,1],'route':'primary','served_at':[0,1,1],
                    'air_path':[[0,1,1],[1,1,1]],'distance':1}]}
            fixture(path,blocks=blocks)
            with readback(path) as v:
                self.assertEqual(audit_route_light_fixtures(v,geometry)['status'],'PASS')
            blocks[12]=53
            fixture(path,blocks=blocks)
            with readback(path) as v:
                report=audit_route_light_fixtures(v,geometry)
                self.assertEqual(report['status'],'FAIL')
                self.assertIn('opaque',report['failures'][0]['reason'])
            blocks[12]=0;blocks[4]=9
            fixture(path,blocks=blocks)
            with readback(path) as v:
                self.assertEqual(audit_route_light_fixtures(v,geometry)['status'],'FAIL')

    def test_tile_unknown_tags_and_numeric_types_preserved(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'t.schematic'
            tile=nbt.compound({'id':nbt.Tag(nbt.STRING,'minecraft:chest'),
                'x':nbt.Tag(nbt.INT,1),'y':nbt.Tag(nbt.INT,1),'z':nbt.Tag(nbt.INT,1),
                'Items':nbt.Tag(nbt.LIST,[],nbt.COMPOUND),'UnknownLong':nbt.Tag(nbt.LONG,1234567890123),
                'UnknownNested':nbt.compound({'byte':nbt.Tag(nbt.BYTE,7)})})
            fixture(path,tiles=[tile])
            with readback(path) as v:
                self.assertEqual(v.tile_tags,[tile])
                self.assertEqual(v.tile_tags[0]['UnknownLong'].type,nbt.LONG)


if __name__=='__main__': unittest.main()
