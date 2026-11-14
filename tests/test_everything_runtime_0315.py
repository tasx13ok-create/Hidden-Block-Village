import unittest
from tools import everything_runtime_0315 as runtime

class SyntheticCheckpointChecks(unittest.TestCase):
    def row(self, path, digest='before'):
        return {'path': path, 'bytes': 4, 'sha256': digest}
    def test_only_synthetic_and_own_reward_ledger_changes_are_supported(self):
        player='playerdata/'+runtime.SYNTHETIC_UUID+'.dat'
        old=[self.row(player), self.row('playerdata/original.dat')]
        new=[self.row(player,'after'),self.row('playerdata/original.dat'),self.row('data/vale_orders_rewards.dat')]
        self.assertEqual(runtime.synthetic_changes(old,new),['data/vale_orders_rewards.dat',player])
    def test_other_player_region_scripts_or_deletions_fail(self):
        for path in ('playerdata/original.dat','region/r.1.1.mca','customnpcs/scripts/test.js'):
            with self.assertRaises(ValueError):runtime.synthetic_changes([self.row(path)],[self.row(path,'after')])
        with self.assertRaises(ValueError):runtime.synthetic_changes([self.row('original')],[])
    def test_noop_cannot_be_falsely_labelled_test_profile(self):
        with self.assertRaises(ValueError):runtime.synthetic_changes([],[])

if __name__=='__main__':unittest.main()
