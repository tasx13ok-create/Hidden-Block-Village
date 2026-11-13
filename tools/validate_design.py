#!/usr/bin/env python3
"""Validate reference coverage, work dependencies and the maintained design links."""
import argparse
import json
from pathlib import Path
import re
from urllib.parse import unquote

STATUSES = {'planned', 'in_progress', 'blocked_inputs', 'verified_metadata',
            'verified_component', 'verified_native', 'verified_graphical', 'verified_hosted'}
STRATEGIES = {'dependency_transition', 'original_utility_rebuild', 'original_gameplay_rebuild',
              'identity_investigation', 'optional_external', 'compatibility_import_and_rebuild',
              'authoring_tool_and_runtime_rebuild'}


def validate_data(inventory, mapping, backlog):
    errors = []
    jars = inventory.get('jars', [])
    references = {row['file']: row for row in jars}
    if len(references) != len(jars) or inventory.get('jar_count') != len(jars):
        errors.append('Reference JAR identities/count are ambiguous')
    rows = mapping.get('rows', [])
    mapped = {row['reference_file']: row for row in rows}
    if len(mapped) != len(rows) or set(mapped) != set(references):
        errors.append('Rebuild map must cover every supplied JAR exactly once')
    if mapping.get('archive_sha256') != inventory.get('archive_sha256'):
        errors.append('Rebuild map refers to a different archive')
    tasks = backlog.get('tasks', [])
    by_id = {task['id']: task for task in tasks}
    if len(by_id) != len(tasks):
        errors.append('Duplicate work package ID')
    if backlog.get('completion_percentage') is not None:
        errors.append('No supported overall gameplay completion percentage exists')
    for row in rows:
        source = references.get(row['reference_file'])
        if source and row.get('reference_sha256') != source.get('sha256'):
            errors.append('Reference hash mismatch: ' + row['reference_file'])
        if row.get('strategy') not in STRATEGIES or not row.get('owner') or not row.get('scope'):
            errors.append('Incomplete reference disposition: ' + row['reference_file'])
        if row.get('work_package') not in by_id or not row.get('acceptance'):
            errors.append('Missing reference implementation/acceptance package: ' + row['reference_file'])
    for task in tasks:
        if task.get('status') not in STATUSES or task.get('priority') not in {'P0', 'P1', 'P2'}:
            errors.append('Invalid scoped status/priority: ' + task['id'])
        if not task.get('owner') or not task.get('deliverables') or not task.get('acceptance'):
            errors.append('Unreviewable work package: ' + task['id'])
        for dep in task.get('depends_on', []):
            if dep not in by_id:
                errors.append('Unknown prerequisite ' + dep + ' for ' + task['id'])
        if task.get('status', '').startswith('verified_') and task['id'] != 'OBS-INVENTORY' and not task.get('evidence'):
            errors.append('Verified work requires evidence: ' + task['id'])
    visited, active = set(), set()

    def visit(task_id):
        if task_id in active:
            errors.append('Dependency cycle at ' + task_id)
            return
        if task_id in visited or task_id not in by_id:
            return
        active.add(task_id)
        for dep in by_id[task_id].get('depends_on', []):
            visit(dep)
        active.remove(task_id)
        visited.add(task_id)

    for task in tasks:
        visit(task['id'])
    return errors


def validate_files(root):
    inventory = json.loads((root / 'design/reference-mod-inventory.json').read_text(encoding='utf-8'))
    mapping = json.loads((root / 'design/mod-rebuild-map.json').read_text(encoding='utf-8'))
    backlog = json.loads((root / 'design/backlog.json').read_text(encoding='utf-8'))
    errors = validate_data(inventory, mapping, backlog)
    for document in sorted((root / 'design').glob('*.md')):
        for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', document.read_text(encoding='utf-8')):
            if '://' in target or target.startswith('#'):
                continue
            local = unquote(target.strip('<>').split('#', 1)[0])
            if local and not (document.parent / local).is_file():
                errors.append('Missing link from ' + document.name + ': ' + target)
    if inventory.get('third_party_payloads_published') is not False:
        errors.append('Inventory publication boundary is missing')
    for row in inventory['jars']:
        if row.get('crc_verified') is not True:
            errors.append('Missing JAR integrity check: ' + row['file'])
    return errors, {'reference_jars': len(inventory['jars']), 'mapped_jars': len(mapping['rows']),
                    'work_packages': len(backlog['tasks']),
                    'documents': len(list((root / 'design').glob('*.md')))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors, counts = validate_files(args.root)
    print(json.dumps({'passed': not errors, **counts, 'errors': errors}, indent=2))
    return int(bool(errors))


if __name__ == '__main__':
    raise SystemExit(main())
