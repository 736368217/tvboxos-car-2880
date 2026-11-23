#!/usr/bin/env python3
"""Compile/run the real CDN HTTP adapter offline, using only cached project dependencies."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
def main():
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
    java_home = Path(os.environ['JAVA_HOME'])
    sdk = Path(os.environ['ANDROID_HOME'])
    jars = [sdk / 'platforms/android-33/android.jar']
    for group, name, versions in [('com.squareup.okhttp3', 'okhttp', ['3.12.11']),
                                  ('com.squareup.okio', 'okio', ['2.10.0', '2.8.0', '1.15.0']),
                                  ('org.jetbrains.kotlin', 'kotlin-stdlib', ['1.6.21','1.5.31','1.4.32'])]:
        found = []
        for version in versions:
            found = list((cache / group / name / version).glob('*/*.jar'))
            if found: break
        if not found:
            found = list((cache / group / name).glob('*/*/*.jar'))
        if not found: raise RuntimeError('Missing cached dependency: ' + name)
        jars.append(found[0])
    sources = [ROOT / 'app/src/main/java/com/github/tvbox/osc/util' / name for name in
               ['GitHubCdn.java','GitHubCdnInterceptor.java']]
    sources.append(ROOT / 'car/tests/http/GitHubCdnTransportTest.java')
    suffix = '.exe' if os.name == 'nt' else ''
    flags = {'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt' else {}
    with tempfile.TemporaryDirectory(prefix='github-cdn-', dir=os.environ.get('PI_SCRATCH_DIR', os.environ.get('TEMP'))) as temp:
        cp = os.pathsep.join(map(str,jars))
        for command in [[java_home / 'bin' / ('javac'+suffix),'-encoding','UTF-8','-proc:none','-cp',cp,'-d',temp,*sources],
                        [java_home / 'bin' / ('java'+suffix),'-ea','-cp',temp+os.pathsep+cp,'car.http.GitHubCdnTransportTest']]:
            run = subprocess.run(list(map(str,command)),cwd=ROOT,capture_output=True,timeout=120,**flags)
            if run.returncode:
                sys.stdout.buffer.write(run.stdout + run.stderr)
                return run.returncode
            if 'car.http.GitHubCdnTransportTest' in command: sys.stdout.buffer.write(run.stdout)
    run = subprocess.run([sys.executable, '-B', str(ROOT / 'car/tests/http/test_github_cdn_python.py')],
                         cwd=ROOT, capture_output=True, timeout=60, **flags)
    sys.stdout.buffer.write(run.stdout + run.stderr)
    return run.returncode
if __name__ == '__main__': sys.exit(main())
