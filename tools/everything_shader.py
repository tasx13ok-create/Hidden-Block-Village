"""Private shader audit/config draft. No extraction, game launch or driver changes."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import re
import stat
import zipfile

BSL_SHA256 = '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
BSL_NAME = 'BSL_v10.1.8.zip'
OPTIONS = {'SHADOW': 'true', 'AO': 'true', 'SHADOW_FILTER': 'true', 'SHADOW_COLOR': 'true',
           'LIGHT_SHAFT': 'true', 'TAA': 'true', 'FXAA': 'true', 'shadowMapResolution': '2048',
           'shadowDistance': '128.0', 'DOF': 'false', 'MOTION_BLUR': 'false', 'BLOOM_STRENGTH': '0.75',
           'LIGHT_SHAFT_STRENGTH': '0.75', 'OUTLINE': '1', 'TOON_LIGHTMAP': 'true',
           'SATURATION': '1.05', 'VIBRANCE': '1.05', 'MULTICOLORED_BLOCKLIGHT': 'false',
           'ADVANCED_MATERIALS': 'false', 'PARALLAX': 'false', 'REFLECTION': '2'}


def need(ok, message):
    if not ok:
        raise ValueError(message)


def safe_zip(path, expected_sha256=None):
    raw = path.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    need(len(raw) <= 16 * 1024**2 and (expected_sha256 is None or digest == expected_sha256), 'Shader archive size/hash differs')
    rows, names, kinds, spelling = [], set(), {}, {}
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        need(len(entries) <= 4096 and sum(row.file_size for row in entries) <= 64 * 1024**2, 'Shader archive expansion limit')
        for row in entries:
            # Windows zipfile normalizes backslashes in filename; inspect the raw original too.
            name = row.orig_filename.rstrip('/')
            parts = PurePosixPath(name).parts
            need(parts and not name.startswith('/') and chr(92) not in name and ':' not in name and all(p not in ('', '.', '..') for p in name.split('/')), 'Unsafe shader archive path')
            need(name.casefold() not in names, 'Duplicate/case-colliding shader archive path')
            names.add(name.casefold())
            kinds[name.casefold()] = row.is_dir()
            for index in range(1, len(parts) + 1):
                prefix = '/'.join(parts[:index])
                need(prefix.casefold() not in spelling or spelling[prefix.casefold()] == prefix, 'Case-colliding shader parent path')
                spelling[prefix.casefold()] = prefix
            need(stat.S_IFMT(row.external_attr >> 16) != stat.S_IFLNK, 'Shader archive link forbidden')
            need(not row.flag_bits & 1 and row.file_size <= 8 * 1024**2 and row.file_size <= max(1, row.compress_size) * 200, 'Unsupported encrypted/oversize shader member')
            if not row.is_dir():
                data = archive.read(row)  # zipfile verifies the member CRC here.
                rows.append({'path': row.filename, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
        for name in names:
            need(all(kinds.get('/'.join(name.split('/')[:i]), True) for i in range(1, len(name.split('/')))), 'Shader file/directory parent collision')
        need(archive.testzip() is None, 'Shader archive CRC failed')
    return {'zip_bytes': len(raw), 'sha256': digest, 'entries': len(entries), 'files': rows,
            'expanded_bytes': sum(row['bytes'] for row in rows), 'crc_verified': True}


def validate_options(settings, choices=OPTIONS):
    settings = settings.replace('\r', '')
    for name, value in choices.items():
        definitions = []
        for line in settings.splitlines():
            macro = re.fullmatch(r'\s*(?://\s*)?#define\s+' + re.escape(name) + r'(?:\s+([^/]*?))?\s*(?://\s*\[([^\]]+)\].*)?', line)
            constant = re.fullmatch(r'\s*const\s+(?:int|float)\s+' + re.escape(name) + r'\s*=\s*([^;]+);\s*(?://\s*\[([^\]]+)\].*)?', line)
            match = macro or constant
            if match:
                definitions.append(match.groups())
        need(len(definitions) == 1, 'Unknown/ambiguous supported shader option: ' + name)
        default, allowed = definitions[0]
        if default is None or not default.strip():
            need(value in ('true', 'false'), 'Nonboolean shader toggle: ' + name)
        else:
            need(allowed is not None and value in allowed.split(), 'Unsupported shader option value: ' + name)
    return dict(choices)


def config_files(settings):
    validated = validate_options(settings)
    return {'options.txt': 'renderDistance:8\nguiScale:2\nmaxFps:120\nenableVsync:true\nfboEnable:true\nrenderClouds:false\nao:2\nparticles:0\nfullscreen:false\ngamma:0.6\nautoJump:false\n',
            'optionsof.txt': 'ofFastRender:false\nofAaLevel:0\nofAfLevel:1\nofRenderRegions:false\nofSmoothFps:false\nofClouds:3\nofDynamicLights:3\n',
            'optionsshaders.txt': 'shaderPack=' + BSL_NAME + '\nantialiasingLevel=0\nrenderResMul=1.0\nshadowResMul=1.0\nnormalMapEnabled=true\nspecularMapEnabled=true\n',
            'shaderpacks/' + BSL_NAME + '.txt': ''.join(key + '=' + value + '\n' for key, value in validated.items())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--shader-zip', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    path = args.shader_zip
    need(not path.is_symlink(), 'Shader input link forbidden')
    report = safe_zip(path, BSL_SHA256)
    with zipfile.ZipFile(path) as archive:
        settings = archive.read('shaders/lib/settings.glsl').decode('utf8')
        properties = archive.read('shaders/shaders.properties').decode('utf8').replace('\r', '')
        need('profile.HIGH=' in properties and 'program.world0/shadowcomp.enabled=MULTICOLORED_BLOCKLIGHT' in properties, 'Unexpected BSL profile/compute switch ABI')
        versions = {}
        for name in archive.namelist():
            if name.endswith(('.fsh', '.vsh', '.gsh', '.csh')):
                for version in re.findall(r'^\s*#version\s+([^\r\n]+)', archive.read(name).decode('utf8', errors='strict'), re.M):
                    version = version.strip()
                    versions[version] = versions.get(version, 0) + 1
    root = args.output
    root.mkdir(exist_ok=False)
    generated = []
    for name, content in config_files(settings).items():
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding='utf8', newline='\n')
        generated.append({'path': name, 'bytes': target.stat().st_size, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
    report.update(status='PRIVATE_SHADER_AUDIT_AND_CONFIG_DRAFT_NOT_RUNTIME_ACCEPTANCE', glsl_headers=versions,
                  selected_options=OPTIONS, generated_config=generated, shader_zip_changed=False,
                  shader_compiled_in_game=False, fps_measured=False, gui_and_controls_verified=False,
                  preset='HIGH-derived with128-block shadows/2048map, mild toon/outline, DOF/motion blur off; performance not measured')
    output = root / 'PRIVATE-BSL-AUDIT.json'
    need(not output.exists(), 'Preserve previous audit')
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: report[key] for key in ('status', 'sha256', 'entries', 'expanded_bytes', 'crc_verified', 'glsl_headers')}))


if __name__ == '__main__':
    main()
