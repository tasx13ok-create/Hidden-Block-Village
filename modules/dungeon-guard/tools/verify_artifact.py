#!/usr/bin/env python3
"""Validate the actual distributable; never packages dependency/test classes."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


VERSION = '1.4.3'
BANNER = 'assets/dungeonguard/textures/gui/area_banner.png'
BANNER_SHA256 = '47f97697d6687bc888b19659c563a6b2a3dcb5ef4b1a43aa3e4a9c1297fb60ac'


def verify(path):
    payload = path.read_bytes()
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), 'Duplicate archive names'
        assert archive.testzip() is None, 'Archive CRC failure'
        manifest = archive.read('META-INF/MANIFEST.MF').decode('utf-8').replace('\r\n ', '')
        assert 'FMLCorePlugin: village.dungeonguard.core.GuardLoadingPlugin' in manifest
        assert 'FMLCorePluginContainsFMLMod: true' in manifest
        headers = dict(line.split(': ', 1) for line in manifest.splitlines() if ': ' in line)
        assert headers.get('Implementation-Version') == VERSION, 'Wrong manifest version'
        assert 'mcmod.info' in names, 'Missing Forge mod metadata'
        metadata = json.loads(archive.read('mcmod.info').decode('utf-8'))
        assert isinstance(metadata, list) and len(metadata) == 1, 'Unexpected mod metadata'
        assert metadata[0].get('modid') == 'dungeonguard', 'Wrong mod identity'
        assert metadata[0].get('version') == VERSION, 'Wrong mod metadata version'
        assert metadata[0].get('mcversion') == '1.12.2', 'Wrong Minecraft target'
        assert BANNER in names, 'Missing area banner texture'
        banner = archive.read(BANNER)
        assert banner[:8] == b'\x89PNG\r\n\x1a\n', 'Banner is not a PNG'
        assert banner[8:16] == b'\x00\x00\x00\rIHDR', 'Missing PNG IHDR'
        width, height, depth, color, compression, filtering, interlace = struct.unpack('>IIBBBBB', banner[16:29])
        assert (width, height, depth, color) == (2172, 724, 8, 6), 'Unexpected banner dimensions or RGBA format'
        assert compression == 0 and filtering == 0 and interlace in (0, 1), 'Unsupported PNG format'
        banner_digest = hashlib.sha256(banner).hexdigest()
        assert banner_digest == BANNER_SHA256, 'Banner differs from the approved transparent source image'
        classes = []
        for name in names:
            parts = name.rstrip('/').split('/')
            assert not name.startswith('/') and '\\' not in name and '..' not in parts, name
            if name.endswith('.class'):
                assert name.startswith('village/dungeonguard/'), 'Foreign class: ' + name
                assert not name.startswith('village/dungeonguard/test/'), 'Private test class: ' + name
                assert not any(token in name.lower() for token in ('nativetest', 'nativecheck', 'fixture')), name
                raw = archive.read(name)
                assert raw[:4] == b'\xca\xfe\xba\xbe', 'Invalid class: ' + name
                assert struct.unpack('>H', raw[6:8])[0] == 52, 'Not Java 8: ' + name
                classes.append(name)
        assert 'village/dungeonguard/DungeonGuard.class' in classes
        assert 'village/dungeonguard/core/GuardLoadingPlugin.class' in classes
        for required in ('AreaMessage', 'AreaNetwork', 'AreaResolver', 'OperatorAccess',
                         'GuardCommand', 'GuardMenus', 'GuardSelections', 'GuardWand',
                         'client/AreaBannerHud', 'client/AreaBannerLayout',
                         'client/FieldHudReservations', 'client/ClientProxy'):
            assert 'village/dungeonguard/' + required + '.class' in classes, 'Missing implementation: ' + required
    return {'file': path.name, 'bytes': len(payload), 'sha256': hashlib.sha256(payload).hexdigest(),
            'version': VERSION,
            'class_count': len(classes), 'class_major_version': 52,
            'archive_crc': 'PASS', 'foreign_classes': False, 'test_classes': False,
            'manifest': 'PASS', 'mod_metadata': 'PASS',
            'banner': {'path': BANNER, 'bytes': len(banner), 'sha256': banner_digest,
                       'width': width, 'height': height, 'format': 'PNG RGBA 8-bit',
                       'matches_original_image': True}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.jar), indent=2))
