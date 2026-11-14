"""Independent Anvil nibble layout and reservation protection."""
from pathlib import Path
import tempfile
import unittest

from tools.arenas.audit import AuditError
from tools.arenas.stage import _store_chunk, _reserve_empty, stage_world, restore_snapshot, _files
from tools.world_merge import nbt
from tools.world_merge.anvil import Region, new_chunk, read_block


class StagingTests(unittest.TestCase):
    def test_anvil_cell_orientation_meta_and_high_nibble(self):
        import numpy as np
        with tempfile.TemporaryDirectory() as temp:
            world=Path(temp);(world/'region').mkdir()
            region=Region(world/'region/r.0.0.mca')
            blocks=np.zeros((256,16,16),dtype=np.uint16);meta=np.zeros_like(blocks,dtype=np.uint8)
            blocks[19,7,5]=0xA31;meta[19,7,5]=11
            blocks[19,7,6]=0x234;meta[19,7,6]=3
            _store_chunk(region,0,0,blocks,meta);region.save_atomic()
            self.assertEqual(read_block(world,5,19,7),(0xA31,11))
            self.assertEqual(read_block(world,6,19,7),(0x234,3))
            self.assertEqual(read_block(world,7,19,5),(0,0))

    def test_reserved_saved_entities_fail_before_edit(self):
        with tempfile.TemporaryDirectory() as temp:
            world=Path(temp);(world/'region').mkdir()
            region=Region(world/'region/r.0.0.mca');root=new_chunk(0,0)
            root['Level']['Entities'].value.append(nbt.compound({'id':nbt.Tag(nbt.STRING,'minecraft:pig')}))
            region.store_chunk(0,'',root);region.save_atomic()
            before=(world/'region/r.0.0.mca').read_bytes()
            with self.assertRaisesRegex(AuditError,'saved entities'):
                _reserve_empty(world,[0,3,0],[500,250,500])
            self.assertEqual(before,(world/'region/r.0.0.mca').read_bytes())

    def test_new_world_requirement_refuses_existing_destination(self):
        import json
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);manifest=root/'manifest.json'
            manifest.write_text(json.dumps({'schema':1,'arenas':[{}]*5}))
            with self.assertRaises(FileExistsError): stage_world(manifest,None,root)

    def test_empty_reservation_is_observed_not_inferred(self):
        with tempfile.TemporaryDirectory() as temp:
            result=_reserve_empty(Path(temp),[64000,3,64000],[500,250,500])
            self.assertEqual(result['existing_chunks_inspected'],0)
            self.assertEqual(result['status'],'PASS')

    def test_source_drift_refuses_before_candidate_creation(self):
        import json
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);manifest=root/'manifest.json';candidate=root/'must-not-exist'
            base={'schema':1,'arenas':[{}]*5,'source_drift':True}
            manifest.write_text(json.dumps(base))
            with self.assertRaisesRegex(AuditError,'drifted build source'):
                stage_world(manifest,None,candidate)
            self.assertFalse(candidate.exists())
            base.update(source_drift=False,source={'sha256':'old'},source_after={'sha256':'old'})
            manifest.write_text(json.dumps(base))
            with self.assertRaisesRegex(AuditError,'Current arena source differs'):
                stage_world(manifest,None,candidate)
            self.assertFalse(candidate.exists())

    def test_whole_snapshot_restore_and_source_drift_rejection(self):
        import json
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);snapshot=root/'cold';snapshot.mkdir()
            (snapshot/'level.dat').write_bytes(b'whole-world')
            (snapshot/'unknown.dat').write_bytes(b'unknown-preserved')
            receipt=root/'receipt.json'
            receipt.write_text(json.dumps({'schema':1,'snapshot':str(snapshot),
                'whole_world_hash_verified':True,'files':_files(snapshot)}))
            result=restore_snapshot(receipt,root/'restored')
            self.assertTrue(result['all_bytes_hash_verified'])
            self.assertEqual(_files(snapshot),_files(root/'restored'))
            (snapshot/'unknown.dat').write_bytes(b'drift')
            with self.assertRaisesRegex(AuditError,'identity changed'):
                restore_snapshot(receipt,root/'rejected')


if __name__=='__main__': unittest.main()
