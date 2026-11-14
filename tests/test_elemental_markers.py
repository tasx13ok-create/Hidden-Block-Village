import unittest

from tools.elemental_dungeons.markers import ENCOUNTERS, SITES, build_plan
from tools.world_merge.schematic import add_tile, new_schematic


class ElementalMarkersTests(unittest.TestCase):
    def test_floor_headroom_and_tile_checks(self):
        schematic = new_schematic(9, 5, 9)
        site = {'center': (4, 1, 4), 'allowed': {(1, 0)}, 'color': 4,
                'element': 'lightning', 'sha256': 'x', 'pattern': ['.#.', '###', '.#.']}
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            schematic.set_block(4 + dx, 1, 4 + dz, 1)
        self.assertEqual(len(build_plan(schematic, site)['edits']), 5)
        schematic.set_block(4, 2, 4, 1)
        with self.assertRaises(ValueError):
            build_plan(schematic, site)
        schematic.set_block(4, 2, 4, 0)
        add_tile(schematic, 'minecraft:chest', 4, 1, 4)
        with self.assertRaises(ValueError):
            build_plan(schematic, site)

    def test_five_mapping_and_full_block_palette(self):
        self.assertEqual({k: v['element'] for k, v in SITES.items()},
                         {'sakura': 'earth', 'cloud': 'lightning', 'sand': 'wind', 'mist': 'water', 'nether': 'fire'})
        self.assertEqual(len({v['color'] for v in SITES.values()}), 5)
        self.assertEqual(set(ENCOUNTERS), set(SITES))
        for site in ENCOUNTERS.values():
            pattern = site['pattern']
            self.assertLess(sum(line.count('#') for line in pattern), sum(map(len, pattern)) // 2)


if __name__ == '__main__':
    unittest.main()
