import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('validate_design', Path(__file__).resolve().parents[1] / 'tools/validate_design.py')
design = importlib.util.module_from_spec(spec)
spec.loader.exec_module(design)


class DesignLedgerTests(unittest.TestCase):
    def setUp(self):
        self.inventory = {'archive_sha256': 'a' * 64, 'jar_count': 1,
                          'jars': [{'file': 'example.jar', 'sha256': 'b' * 64}]}
        self.mapping = {'archive_sha256': 'a' * 64, 'rows': [{
            'reference_file': 'example.jar', 'reference_sha256': 'b' * 64,
            'strategy': 'original_gameplay_rebuild', 'owner': 'combat', 'scope': 'real attack',
            'work_package': 'A', 'acceptance': ['Observed protected target is not hit.']}]}
        self.backlog = {'completion_percentage': None, 'tasks': [{
            'id': 'A', 'status': 'planned', 'priority': 'P1', 'owner': 'combat',
            'deliverables': ['implementation'], 'acceptance': ['real behavior'], 'depends_on': []}]}

    def validate(self):
        return design.validate_data(self.inventory, self.mapping, self.backlog)

    def test_traceable_plan_is_valid_without_claiming_it_is_implemented(self):
        self.assertEqual([], self.validate())

    def test_missing_reference_row_or_changed_bytes_are_rejected(self):
        self.mapping['rows'][0]['reference_sha256'] = 'c' * 64
        self.assertTrue(any('hash mismatch' in error for error in self.validate()))
        self.mapping['rows'] = []
        self.assertTrue(any('every supplied JAR' in error for error in self.validate()))

    def test_dependency_cycle_and_unknown_prerequisite_are_rejected(self):
        second = copy.deepcopy(self.backlog['tasks'][0])
        second.update(id='B', depends_on=['A'])
        self.backlog['tasks'].append(second)
        self.backlog['tasks'][0]['depends_on'] = ['B']
        self.assertTrue(any('cycle' in error for error in self.validate()))
        self.backlog['tasks'][0]['depends_on'] = ['MISSING']
        self.assertTrue(any('Unknown prerequisite' in error for error in self.validate()))

    def test_unsubstantiated_completion_cannot_be_hidden_in_a_status(self):
        self.backlog['tasks'][0]['status'] = 'verified_graphical'
        self.assertTrue(any('requires evidence' in error for error in self.validate()))
        self.backlog['completion_percentage'] = 100
        self.assertTrue(any('completion percentage' in error for error in self.validate()))


if __name__ == '__main__':
    unittest.main()
