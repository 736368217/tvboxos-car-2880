"""Run this repository's standalone JVM regressions; generate only in --out."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--gradle-home', type=Path, default=os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    args = parser.parse_args()
    if not args.java_home or not args.sdk:
        parser.error('Set JAVA_HOME and ANDROID_HOME, or pass --java-home and --sdk.')
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    suffix = '.exe' if os.name == 'nt' else ''
    java, javac = [args.java_home / 'bin' / (name + suffix) for name in ('java', 'javac')]
    cache = args.gradle_home / 'caches/modules-2/files-2.1'
    cp = [args.sdk / 'platforms/android-33/android.jar']
    for artifact in ('common', 'core', 'container', 'decoder', 'extractor', 'datasource'):
        name = 'exoplayer-' + artifact
        matches = list(cache.glob('com.google.android.exoplayer/{}/2.19.1/*/{}-2.19.1.aar'.format(name, name)))
        if len(matches) != 1:
            raise RuntimeError('Build :app:assemblePythonRelease first; missing cached ' + name)
        jar = out / (name + '.jar')
        with zipfile.ZipFile(matches[0]) as archive:
            jar.write_bytes(archive.read('classes.jar'))
        cp.append(jar)
    dependencies = [('com.google.guava', 'guava', ('31.1-android', '30.1-jre')),
                    ('androidx.annotation', 'annotation', ('1.5.0', '1.3.0'))]
    for group, name, versions in dependencies:
        for version in versions:
            matches = list(cache.glob('{}/{}/{}/*/{}-{}.jar'.format(group, name, version, name, version)))
            if len(matches) == 1:
                cp.append(matches[0])
                break
        else:
            raise RuntimeError('Missing cached dependency: ' + name)
    if not all(p.is_file() for p in [java, javac] + cp):
        raise RuntimeError('JDK/SDK files are missing.')
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join(str(p) for p in cp)
    env = dict(os.environ, TMP=str(out), TEMP=str(out), PYTHONIOENCODING='utf-8', JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8')
    flags = {'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt' else {}
    results = []

    def run(label, command):
        start = time.monotonic()
        process = subprocess.run([str(v) for v in command], cwd=str(ROOT), env=env, stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT, timeout=120, **flags)
        log = process.stdout.decode('utf-8', errors='replace')
        (out / (label + '.log')).write_text(log, encoding='utf-8')
        results.append({'name': label, 'exit': process.returncode, 'seconds': round(time.monotonic() - start, 2),
                        'summary': log.strip().splitlines()[-1] if log.strip() else ''})
        print('{}: {}'.format(label, 'PASS' if process.returncode == 0 else 'FAIL'), flush=True)
        return process.returncode

    tests = sorted((ROOT / 'car/tests').glob('*.java'))
    sourcepath = os.pathsep.join(str(ROOT / p) for p in ('app/src/main/java', 'player/src/main/java'))
    compiled = run('javac', [javac, '-encoding', 'UTF-8', '-proc:none', '-cp', classpath,
                             '-sourcepath', sourcepath, '-d', classes] + tests)
    if not compiled:
        for test in tests:
            source = test.read_text(encoding='utf-8')
            package = re.search(r'^package\s+([\w.]+);', source, flags=re.M)
            name = (package.group(1) + '.' if package else '') + test.stem
            extra = [ROOT] if 'args.length > 0' in source else []
            run(test.stem, [java, '-ea', '-cp', str(classes) + os.pathsep + classpath, name] + extra)
    for test in sorted((ROOT / 'car/tests').glob('run_*_host_test.py')):
        run(test.stem, [sys.executable, '-B', test, '--java-home', args.java_home])
    (out / 'result.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    failed = sum(row['exit'] != 0 for row in results)
    print('ALL_HOST_TESTS_PASS' if not failed else 'HOST_FAILURES={}'.format(failed))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
