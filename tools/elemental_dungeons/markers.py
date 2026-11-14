"""Generate small flush floor motifs in new copies of the pinned v17 schematics."""
import argparse
import json
from pathlib import Path

from tools.world_merge.edits import apply_plan
from tools.world_merge.schematic import Schematic, sha256_file


# Coordinates are local schematic cells, on independently measured platform floors.
# All expected inputs are exact original recovered schematics, not C1 fixes.
SITES = {
    'sakura': {'element': 'earth', 'sha256': '5f0eb66c2998b156a8fcc2af503e16bcedbe8ee446d503e1d04a76b2f2e46a24', 'center': (144, 98, 73), 'allowed': {(1, 6), (98, 0)}, 'color': 12, 'pattern': ['.###.', '##.##', '#.#.#', '##.##', '.###.']},
    'cloud': {'element': 'lightning', 'sha256': 'a59a1b4e0a56c35f832cbac0404de76a6bae73f2bc0825476438989b145a70fa', 'center': (144, 122, 73), 'allowed': {(98, 2), (1, 6), (98, 0)}, 'color': 4, 'pattern': ['..##.', '.##..', '.###.', '..##.', '.##..']},
    'sand': {'element': 'wind', 'sha256': '7ea2cb4fd47c8d73305ab9713984b280f776cb791f5f04f492bc25d2de9015ec', 'center': (144, 106, 73), 'allowed': {(24, 0), (24, 2)}, 'color': 0, 'pattern': ['.###.', '#...#', '..##.', '.#...', '..##.']},
    'mist': {'element': 'water', 'sha256': '40d6fbdbaf2d2c4b858e2bd2ea840ff5b2c32e373deaad255c928fe0edcdbe51', 'center': (96, 20, 10), 'allowed': {(5, 1)}, 'color': 11, 'pattern': ['..#..', '.#.#.', '#...#', '.#.#.', '..#..']},
    'nether': {'element': 'fire', 'sha256': '55867e4fb7774ef40bcf0f658be30eb9daf2786f3b3c49af1be0d177a9177f72', 'center': (126, 50, 53), 'allowed': {(159, 15)}, 'color': 14, 'pattern': ['.#.', '##.', '###']},
}

# Separate encounter-area accents. Pattern dots retain the existing floor.
ENCOUNTERS = {
    'sakura': {'center': (141, 96, 155), 'allowed': {(2, 0)}, 'pattern': ['...#...', '..#.#..', '.#...#.', '#..#..#', '.#...#.', '..#.#..', '...#...']},
    'cloud': {'center': (199, 122, 99), 'allowed': {(155, 0)}, 'pattern': ['....##.', '...##..', '..##...', '.#####.', '...##..', '..##...', '.##....']},
    'sand': {'center': (102, 106, 148), 'allowed': {(24, 2), (24, 1), (159, 9)}, 'pattern': ['.#####.', '#......', '#..###.', '#.#...#', '..#...#', '...###.', '.......']},
    'mist': {'center': (166, 20, 110), 'allowed': {(5, 1)}, 'pattern': ['...#...', '..#.#..', '.#...#.', '#.....#', '.#...#.', '..###..', '...#...']},
    'nether': {'center': (141, 50, 87), 'allowed': {(159, 7), (112, 0)}, 'pattern': ['..#..', '.##..', '#.##.', '.###.', '..#..']},
}


def build_plan(schem, site):
    """Check each chosen solid floor cell, headroom and tiles before producing edits."""
    x, y, z = site['center']
    tiles = schem.tile_at()
    pattern = site['pattern']
    edits = []
    for row, line in enumerate(pattern):
        for col, mark in enumerate(line):
            if mark != '#':
                continue
            pos = (x + col - len(line) // 2, y, z + row - len(pattern) // 2)
            before = schem.get(*pos)
            if before not in site['allowed']:
                raise ValueError('Unsafe or unexpected floor at %r: %r' % (pos, before))
            for dy in range(3):
                check = (pos[0], pos[1] + dy, pos[2])
                if check in tiles or (dy and schem.get(*check) != (0, 0)):
                    raise ValueError('Occupied marker or headroom at %r' % (check,))
            edits.append({'id': '%s-%02d' % (site['element'], len(edits)), 'pos': list(pos),
                          'expect': {'block': before[0], 'data': before[1]},
                          'set': {'block': 159, 'data': site['color']},
                          'reason': 'Flush full-block elemental floor motif with clear headroom'})
    return {'schema': 1, 'input_sha256': site['sha256'], 'edits': edits}


def build_combined_plan(schem, name):
    main = SITES[name]
    plan = build_plan(schem, main)
    encounter = dict(main, **ENCOUNTERS[name])
    extra = build_plan(schem, encounter)['edits']
    for edit in extra:
        edit['id'] = 'encounter-' + edit['id']
    plan['edits'].extend(extra)
    return plan


def generate(input_dir, output_dir):
    input_dir, output_dir = Path(input_dir), Path(output_dir)
    # Validate every source and marker before writing anything.
    plans = {}
    for name, site in SITES.items():
        path = input_dir / (name + '_v17.schematic')
        if sha256_file(path) != site['sha256']:
            raise ValueError('Input hash mismatch for ' + name)
        plans[name] = build_combined_plan(Schematic.load(path), name)
    output_dir.mkdir(parents=False, exist_ok=False)
    logs = {}
    for name, plan in plans.items():
        plan_path = output_dir / (name + '-plan.json')
        plan_path.write_text(json.dumps(plan, indent=2) + '\n', encoding='utf-8')
        logs[name] = apply_plan(input_dir / (name + '_v17.schematic'), plan_path,
                                output_dir / (name + '-elemental-marker.schematic'),
                                output_dir / (name + '-verification.json'))
    return logs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True, help='new private candidate directory')
    args = parser.parse_args()
    logs = generate(args.input_dir, args.output_dir)
    for name, log in logs.items():
        print('%s: %s, %s changed cells' % (name, log['output']['sha256'], log['verification']['changed_cells']))
    print('Small floor markers only; no world installation or gameplay acceptance.')


if __name__ == '__main__':
    main()
