import tempfile
import unittest
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

    def test_rejects_outside_volume_bad_metadata_and_existing_dest(self):
        with tempfile.TemporaryDirectory() as tmp:
            with Volume(Path(tmp)/'scratch',(5,4,3)) as v:
                for args in ((-1,0,0,1),(5,0,0,1),(0,4,0,1),(0,0,3,1),(0,0,0,4096)):
                    with self.assertRaises(ValueError): v.set(*args)
                with self.assertRaises(ValueError): v.set(0,0,0,1,16)
            with self.assertRaises(ValueError): safe_new_directory(tmp)


if __name__=='__main__': unittest.main()
