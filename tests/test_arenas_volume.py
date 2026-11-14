import tempfile
import unittest
import json
from pathlib import Path

from tools.arenas.volume import Volume, safe_new_directory
from tools.world_merge.schematic import Schematic


class StreamingVolumeTests(unittest.TestCase):
    def test_independent_alpha_readback_and_high_nibble_order(self):
        with tempfile.TemporaryDirectory() as tmp:
            with Volume(Path(tmp)/'scratch',(5,4,3)) as v:
                v.set(0,0,0,257,3)
                v.set(1,0,0,4095,15)
                v.box(2,1,1,4,2,2,98,2)
                first=v.export(Path(tmp)/'first.schematic',(64000,3,64000))
                second=v.export(Path(tmp)/'second.schematic',(64000,3,64000))
            self.assertEqual(first,second)
            s=Schematic.load(Path(tmp)/'first.schematic')
            self.assertEqual((s.width,s.height,s.length),(5,4,3))
            self.assertEqual(s.we_origin,(64000,3,64000))
            self.assertEqual(s.add[0],1)
            self.assertEqual(s.add[1],15)
            self.assertEqual(s.blocks[1],255)
            self.assertEqual(s.data[1],15)
            self.assertEqual(sum(b==98 for b in s.blocks),12)
            owner=s.root['ArenaLayers'].value
            names=json.loads(s.root['ArenaLayerNames'].value)
            self.assertEqual(len(owner),s.cells)
            self.assertEqual(names[str(owner[0])],'geometry')
            self.assertEqual(owner[5],0)

    def test_rejects_outside_volume_bad_metadata_and_existing_dest(self):
        with tempfile.TemporaryDirectory() as tmp:
            with Volume(Path(tmp)/'scratch',(5,4,3)) as v:
                for args in ((-1,0,0,1),(5,0,0,1),(0,4,0,1),(0,0,3,1),(0,0,0,4096)):
                    with self.assertRaises(ValueError): v.set(*args)
                with self.assertRaises(ValueError): v.set(0,0,0,1,16)
            with self.assertRaises(ValueError): safe_new_directory(tmp)

    def test_real_chest_tile_payload_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            with Volume(Path(tmp)/'scratch',(8,8,8)) as v:
                v.chest(3,2,4,2,'Workshop stores')
                with self.assertRaises(ValueError):v.chest(3,2,4)
                v.export(Path(tmp)/'chest.schematic',(64000,3,64000))
            s=Schematic.load(Path(tmp)/'chest.schematic')
            self.assertEqual(len(s.tile_tags),1)
            tile=s.tile_tags[0]
            self.assertEqual(tile.scalar('id'),'minecraft:chest')
            self.assertEqual(tile.scalar('x'),3)
            self.assertEqual(tile.scalar('y'),2)
            self.assertEqual(tile.scalar('z'),4)
            self.assertEqual(tile.scalar('CustomName'),'Workshop stores')
            self.assertEqual(tile['Items'].value,[])


if __name__=='__main__': unittest.main()
