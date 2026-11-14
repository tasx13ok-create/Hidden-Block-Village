"""Emit safe, narrowly scoped evidence without publishing private addon bodies."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


INPUT_SHA256 = 'c4a5e4c96b597efe4b76a4d562307c4ae837fe299abe91a127d0169b8d3e8fe3'
FIXTURE_SCOPES = {'PRIVATE_NATIVE_ADMITTED_PLAYER_BANK_GUI_RELATION_DATA_PAYMENT',
                  'PRIVATE_NATIVE_ADMITTED_PLAYER_BANK_XP_PVP'}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def jar_classes(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError('JAR CRC failure')
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate JAR entry')
        return {name: archive.read(name) for name in names if name.endswith('.class')}


def bind_adapter_build(report: dict, runtime: Path, metadata_path: Path, log_path: Path) -> None:
    metadata = json.loads(metadata_path.read_text(encoding='utf-8'))
    log = log_path.read_text(encoding='utf-8', errors='replace')
    checks = [line[5:] for line in log.splitlines() if line.startswith('PASS ')]
    if ('BUILD SUCCESSFUL' not in log or '> Task :compileNativeChecksJava' not in log
            or len(checks) != 5 or 'real provider gets two additive hooks without replacing methods' not in checks
            or metadata.get('acceptance') != 'COMPONENT_BUILD_ONLY'
            or not any(row == {'task': 'verifyTrainingHook', 'state': 'EXECUTED'}
                       for row in metadata.get('component_checks', []))):
        raise ValueError('Current component build and provider checks were not observed')
    sdk = Path(next(row['path'] for row in metadata['artifacts'] if row['role'] == 'runtime_mod'))
    originals, production = jar_classes(sdk), jar_classes(runtime)
    different = sorted(name for name, data in originals.items() if production.get(name) != data)
    if not originals or different:
        raise ValueError('Public adapter bytes differ from the native-tested port: ' + repr(different))
    root = Path(__file__).resolve().parent
    report['original_adapters'] = {
        'source_files_sha256': {str(path.relative_to(root)).replace('\\', '/'): sha(path)
                                for folder in ('compat-src', 'tests-src', 'abi-tests')
                                for path in sorted((root / folder).rglob('*.java'))},
        'version': metadata['version'], 'component_build_passed': True,
        'native_fixture_source_compile_passed': True, 'provider_structure_checks_passed': checks,
        'build_metadata_sha256': sha(metadata_path), 'build_log_sha256': sha(log_path),
        'adapter_jar_sha256': sha(sdk), 'reobfuscated_classes_compared': len(originals),
        'classes_different_from_native_tested_port': different, 'standalone_installable_mod': False,
    }


def audit(original: Path, runtime: Path, native_result: Path, run_receipt: Path,
          stage_receipt: Path, cold_result: Path | None = None,
          cold_run: Path | None = None) -> dict:
    if sha(original) != INPUT_SHA256:
        raise ValueError('Supplied addon identity differs')
    before, after = jar_classes(original), jar_classes(runtime)
    result = json.loads(native_result.read_text(encoding='utf-8'))
    run = json.loads(run_receipt.read_text(encoding='utf-8'))
    stage = json.loads(stage_receipt.read_text(encoding='utf-8'))
    if result.get('scope') not in FIXTURE_SCOPES or result.get('original_world_or_real_player_used') is not False:
        raise ValueError('Unexpected native fixture scope')
    if run.get('scope') != 'PRIVATE_NATIVE_FEATURE_FIXTURE' or run.get('original_world_or_player_used') is not False:
        raise ValueError('Unexpected native run scope')
    if run.get('exit_code') != 0 or run.get('forced_termination') is not False or run.get('all_staged_mods_unchanged') is not True:
        raise ValueError('Native run did not close cleanly with unchanged staged mods')
    matches = [row for row in stage['mods'] if row['sha256'] == sha(runtime)]
    if len(matches) != 1 or not stage.get('full_current_server_cohort'):
        raise ValueError('Native stage does not bind the supplied runtime to the full server cohort')
    if stage.get('original_world_or_player_data_used') is not False:
        raise ValueError('Unexpected original world/player input')
    old_links = sorted(name for name, data in after.items()
                       if b'cpw/mods/fml/' in data or b'narutodestination/Mathioks/' in data)
    majors = sorted({struct.unpack('>H', data[6:8])[0] for data in after.values()})
    if any(major > 52 for major in majors) or old_links:
        raise ValueError('Runtime retains old linkage or requires newer-than-Java8 bytecode')
    required = ('org/sqlite/JDBC.class', 'META-INF/maven/org.xerial/sqlite-jdbc/LICENSE',
                'META-INF/maven/org.xerial/sqlite-jdbc/LICENSE.zentus')
    with zipfile.ZipFile(runtime) as archive:
        for name in required:
            if not archive.read(name):
                raise ValueError('Missing driver or its retained license')
        metadata = json.loads(archive.read('mcmod.info'))[0]
        if metadata['modid'] != 'narutodestinationvillages' or metadata['mcversion'] != '1.12.2':
            raise ValueError('Wrong port metadata')
        native_libraries = [name for name in archive.namelist()
                            if name.startswith('org/sqlite/native/') and name.endswith(('.dll', '.so', '.dylib', '.jnilib'))]
        if not any(name.endswith('.dll') for name in native_libraries) or not any(name.endswith('.so') for name in native_libraries):
            raise ValueError('Bundled SQLite lacks Windows or Linux natives')
    missing = sorted(set(before) - set(after))
    if missing:
        raise ValueError('Original addon class names missing')
    report = {
        'schema': 1,
        'scope': 'NARUTO_VILLAGES_112_PORT_PREVIEW_SERVER_SUBSET',
        'input': {'file': original.name, 'bytes': original.stat().st_size, 'sha256': sha(original), 'classes': len(before)},
        'runtime': {'file': runtime.name, 'bytes': runtime.stat().st_size, 'sha256': sha(runtime),
                    'version': metadata['version'], 'minecraft': '1.12.2', 'class_major_versions': majors,
                    'original_class_names_missing': missing, 'old_base_or_fml_linkage_classes': old_links,
                    'sqlite_driver_and_licenses_present': True, 'sqlite_native_library_count': len(native_libraries)},
        'native': {'fixture_scope': result['scope'], 'passed': result.get('passed') is True,
                   'assertions_attempted': result.get('assertions'), 'checks_passed': result.get('checks', []),
                   'failure': result.get('error'), 'result_sha256': sha(native_result),
                   'run_receipt_sha256': sha(run_receipt), 'qa_jar_sha256': run.get('qa_sha256'),
                   'exit_code': run['exit_code'], 'forced_termination': False, 'all_staged_mod_hashes_unchanged': True,
                   'staged_server_mods_including_port_excluding_fixture': len(stage['mods']),
                   'explicit_test_config_max_bank_pages': stage.get('test_config_max_bank_pages'),
                   'carried_item_inventory_cursor_ground_counts': result.get('carried_item_after_hub_open'),
                   'default_loot_mapping_complete': result.get('default_loot_mapping_complete'),
                   'unresolved_original_loot_ids': result.get('unresolved_original_loot_ids'),
                   'native_xp_redemption_verified': result.get('native_xp_redemption_verified', False),
                   'native_training_point_spend_and_learning_verified': result.get('native_training_point_spend_and_learning_verified', False),
                   'native_training_canonical_capacity_and_reset_verified': result.get('native_training_canonical_capacity_and_reset_verified', False),
                   'training_native_damage': result.get('training_native_damage'),
                   'canonical_training_chakra_maximum': result.get('canonical_training_chakra_maximum'),
                   'canonical_training_chakra_points': result.get('canonical_training_chakra_points'),
                   'native_pvp_damage_death_respawn_antifarm_verified': result.get('native_pvp_damage_death_respawn_antifarm_verified', False),
                   'same_village_native_damage': result.get('same_village_native_damage'),
                   'war_native_damage': result.get('war_native_damage'),
                   'earned_xp_token_redemption': result.get('earned_xp_token_redemption'),
                   'partial_pvp_reward_inventory_count': result.get('partial_pvp_reward_inventory_count'),
                   'partial_pvp_reward_ground_delta': result.get('partial_pvp_reward_ground_delta')},
        'private_reference_bodies_or_runtime_distributed_in_git': False,
        'original_world_or_real_player_data_used': False,
        'real_network_client_or_graphical_acceptance': False,
        'native_xp_callback_failure_refund_exercised': False,
        'full_restart_migration_acceptance': False,
        'full_feature_parity_or_production_readiness_claimed': False,
        'unfinished': ['remaining legacy PvP reward item semantics', 'legacy-only character statistics and clan/senjutsu bindings',
                       'rank caps and promotion authority acceptance', 'full transport execution',
                       'PvP cooldown restart persistence', 'territory capture acceptance',
                       'real network client and optional mirror', 'copied deployment-world restart/migration'],
    }
    if (cold_result is None) != (cold_run is None):
        raise ValueError('Cold proof requires both the result and process receipt')
    if cold_result is not None:
        cold = json.loads(cold_result.read_text(encoding='utf-8'))
        process = json.loads(cold_run.read_text(encoding='utf-8'))
        scope = 'PRIVATE_TRAINING_ACTUAL_PROCESS_RESTART'
        if (cold.get('scope') != scope or process.get('scope') != scope
                or cold.get('passed') is not True
                or cold.get('original_world_or_real_player_used') is not False
                or process.get('original_world_or_real_player_used') is not False
                or process.get('exit_code') != 0
                or process.get('forced_termination') is not False
                or process.get('all_staged_mod_hashes_unchanged') is not True
                or cold.get('reward_reissued_by_fixture') is not False):
            raise ValueError('Cold training proof failed or has an unexpected scope')
        report['training_process_restart'] = {
            'scope': scope, 'passed': True, 'assertions': cold['assertions'],
            'checks_passed': cold['checks'], 'loaded_balances_sp_jp_reset': cold['loaded_balances'],
            'native_battle_xp': cold['native_battle_xp'], 'reward_reissued_by_fixture': False,
            'result_sha256': sha(cold_result), 'run_receipt_sha256': sha(cold_run),
            'qa_jar_sha256': process['qa_sha256'], 'expected_state_sha256': process['expected_state_sha256'],
            'exit_code': 0, 'forced_termination': False, 'all_staged_mod_hashes_unchanged': True,
            'pvp_cooldown_restart_persistence_proven': False,
            'copied_production_world_migration_proven': False,
        }
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('input-jar', 'runtime-jar', 'native-result', 'run-receipt', 'stage-receipt', 'output'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--cold-result', type=Path)
    parser.add_argument('--cold-run', type=Path)
    parser.add_argument('--adapter-build-metadata', type=Path)
    parser.add_argument('--component-build-log', type=Path)
    args = parser.parse_args()
    report = audit(args.input_jar, args.runtime_jar, args.native_result, args.run_receipt,
                   args.stage_receipt, args.cold_result, args.cold_run)
    if (args.adapter_build_metadata is None) != (args.component_build_log is None):
        parser.error('Adapter binding requires both metadata and the build log')
    if args.adapter_build_metadata is not None:
        bind_adapter_build(report, args.runtime_jar, args.adapter_build_metadata, args.component_build_log)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes((json.dumps(report, indent=2) + '\n').encode('utf-8'))
    print(json.dumps({'output': str(args.output), 'native_subset_passed': report['native']['passed'],
                      'full_feature_parity_or_production_readiness_claimed': False}))


if __name__ == '__main__':
    main()
