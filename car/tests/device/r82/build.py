import argparse
from pathlib import Path
import subprocess
import zipfile
from generate_names import generate

root = Path(__file__).resolve().parents[4]
p = argparse.ArgumentParser()
p.add_argument('--sdk', required=True)
p.add_argument('--java-home', required=True)
p.add_argument('--out', required=True)
p.add_argument('--mapping', required=True)
a = p.parse_args()
out = Path(a.out).resolve()
out.mkdir(parents=True, exist_ok=True)
sdk = Path(a.sdk)
bt = sdk / 'build-tools' / '30.0.3'
android = sdk / 'platforms' / 'android-33' / 'android.jar'
java = Path(a.java_home) / 'bin'
classes = out / 'classes'
dex = out / 'dex'
classes.mkdir(exist_ok=True)
dex.mkdir(exist_ok=True)
names = out / 'Names.java'
generate(Path(a.mapping), names)
def run(cmd):
    subprocess.run([str(x) for x in cmd], check=True)
run([java/'javac.exe', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options', '-cp', android, '-d', classes, Path(__file__).with_name('Acceptance.java'), names])
jar = out / 'test.jar'
run([java/'jar.exe', 'cf', jar, '-C', classes, '.'])
run([java/'java.exe', '-cp', bt/'lib'/'d8.jar', 'com.android.tools.r8.D8', '--lib', android, '--min-api', '26', '--output', dex, jar])
manifest = out / 'AndroidManifest.xml'
manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.tvbox.car.r82test"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="32"/><application android:label="TVBox r82 acceptance"/><instrumentation android:name="com.tvbox.car.r82test.Acceptance" android:targetPackage="com.github.tvbox.osc.jun" android:label="Native acceptance"/></manifest>', encoding='utf-8')
base = out / 'base.apk'
run([bt/'aapt2.exe', 'link', '-I', android, '--manifest', manifest, '-o', base])
with zipfile.ZipFile(base, 'a') as apk:
    apk.write(dex/'classes.dex', 'classes.dex')
aligned = out / 'aligned.apk'
run([bt/'zipalign.exe', '-f', '4', base, aligned])
run([java/'java.exe', '-jar', bt/'lib'/'apksigner.jar', 'sign', '--ks', root/'car'/'ci'/'debug.keystore', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--ks-key-alias', 'androiddebugkey', '--out', out/'acceptance.apk', aligned])
run([java/'java.exe', '-jar', bt/'lib'/'apksigner.jar', 'verify', '--print-certs', out/'acceptance.apk'])
print('NATIVE_TEST_APK', out/'acceptance.apk')
