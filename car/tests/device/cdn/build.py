"""Build the separate CDN acceptance APK from the exact product R8 mapping."""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--sdk', type=Path, required=True)
parser.add_argument('--java-home', type=Path, required=True)
parser.add_argument('--mapping', type=Path, required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--target-package', default='com.github.tvbox.osc.jun', choices=('com.github.tvbox.osc.jun', 'com.github.tvbox.osc.jun.cdnqa'))
a = parser.parse_args()
out = a.out.resolve()
out.mkdir(parents=True, exist_ok=True)
classes, methods, fields = {}, {}, {}
owner = ''
for line in a.mapping.read_text(encoding='utf-8').splitlines():
    if not line or line.lstrip().startswith('#'):
        continue
    if not line.startswith(' ') and ' -> ' in line and line.endswith(':'):
        owner, mapped = line[:-1].split(' -> ')
        classes[owner] = mapped
    elif owner and ' -> ' in line:
        original, mapped = line.strip().split(' -> ')
        original = re.sub(r'^\d+:\d+:', '', original)
        name = original.split('(')[0].rsplit(' ', 1)[-1]
        target = methods if '(' in original else fields
        target.setdefault(owner + '#' + name, set()).add(mapped)


def member(table, owner_name, name):
    values = table.get(owner_name + '#' + name, set())
    if len(values) != 1:
        raise RuntimeError('No unambiguous runtime member: ' + owner_name + '#' + name + ': ' + str(values))
    return next(iter(values))


update = 'com.github.tvbox.osc.update.AppUpdate'
channel = 'com.github.tvbox.osc.update.UpdateVersion$Channel'
info = update + '$Info'
names = {'UPDATE': classes[update], 'CHANNEL': classes[channel], 'QUERY': member(methods, update, 'query'),
         'VERSION': member(fields, info, 'remoteVersion'), 'ASSET': member(fields, info, 'apkUrl'),
         'HAS_UPDATE': member(fields, info, 'hasUpdate')}
java = 'package com.tvbox.car.cdntest;\nfinal class Names {\n'
java += ''.join('static final String {}="{}";\n'.format(k, v) for k, v in names.items()) + '}\n'
(out / 'Names.java').write_text(java, encoding='utf-8')
exe = '.exe' if __import__('os').name == 'nt' else ''
bin_dir = a.java_home / 'bin'
bt = a.sdk / 'build-tools/30.0.3'
android = a.sdk / 'platforms/android-33/android.jar'
for directory in ('classes', 'dex'):
    (out / directory).mkdir(exist_ok=True)


def run(cmd):
    subprocess.run([str(v) for v in cmd], check=True)


run([bin_dir / ('javac' + exe), '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
     '-cp', android, '-d', out / 'classes', Path(__file__).with_name('CdnProbe.java'), out / 'Names.java'])
run([bin_dir / ('jar' + exe), 'cf', out / 'test.jar', '-C', out / 'classes', '.'])
run([bin_dir / ('java' + exe), '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', android,
     '--min-api', '32', '--output', out / 'dex', out / 'test.jar'])
(out / 'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.tvbox.car.cdntest"><uses-sdk android:minSdkVersion="32" android:targetSdkVersion="32"/><application android:label="B01 CDN acceptance"/><instrumentation android:name="com.tvbox.car.cdntest.CdnProbe" android:targetPackage="' + a.target_package + '"/></manifest>', encoding='utf-8')
run([bt / ('aapt2' + exe), 'link', '-I', android, '--manifest', out / 'AndroidManifest.xml', '-o', out / 'base.apk'])
with zipfile.ZipFile(out / 'base.apk', 'a') as apk:
    apk.write(out / 'dex/classes.dex', 'classes.dex')
run([bt / ('zipalign' + exe), '-f', '4', out / 'base.apk', out / 'aligned.apk'])
run([bin_dir / ('java' + exe), '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', ROOT / 'car/ci/debug.keystore',
     '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--ks-key-alias', 'androiddebugkey',
     '--out', out / 'cdn-test.apk', out / 'aligned.apk'])
print('CDN_TEST_APK', out / 'cdn-test.apk')
