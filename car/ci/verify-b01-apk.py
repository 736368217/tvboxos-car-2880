"""Fail if a generated APK drifts from the private Leapmotor B01 build contract."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--aapt2', type=Path, required=True)
    args = parser.parse_args()
    result = subprocess.run([str(args.aapt2), 'dump', 'badging', str(args.apk)], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=True, timeout=30)
    badging = result.stdout.decode('utf-8', errors='replace')
    assert "package: name='com.github.tvbox.osc.jun'" in badging, 'Wrong package'
    assert "sdkVersion:'32'" in badging, 'B01 minSdk must be API 32'
    assert not re.search(r'^uses-(?:implied-)?feature:', badging, re.M), 'Unexpected required hardware feature'
    with zipfile.ZipFile(args.apk) as apk:
        # Android ZIP names use UTF-8 bytes even when the UTF-8 flag is absent.
        names = []
        for info in apk.infolist():
            name = info.filename
            if not info.flag_bits & 0x800:
                try:
                    name = name.encode('cp437').decode('utf-8')
                except UnicodeError:
                    pass
            names.append(name)
        assert len(names) == len(set(names)), 'Duplicate ZIP paths'
        assert apk.testzip() is None, 'Corrupt ZIP entry'
        abi = sorted({name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')})
        assert abi == ['arm64-v8a'], 'B01 is ARM64-only: ' + str(abi)
        assert apk.getinfo('resources.arsc').compress_type == zipfile.ZIP_STORED, 'Compressed resource table'
        for name in ('assets/chaquopy/requirements-arm64-v8a.imy', 'assets/chaquopy/requirements-common.imy'):
            assert name in names, 'Missing Python runtime dependency archive: ' + name
        assert 'assets/chaquopy/requirements-armeabi-v7a.imy' not in names, 'Unexpected 32-bit Python payload'
        assert 'assets/js/lib/模板.js' in names, 'UTF-8 asset path corrupted'
    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    print(json.dumps({'result': 'B01_APK_PASS', 'minSdk': 32, 'abi': abi, 'bytes': args.apk.stat().st_size, 'sha256': digest}))
    return 0


if __name__ == '__main__':
    sys.exit(main())
