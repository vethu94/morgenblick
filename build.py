"""Portable local APK builder. Requires Python 3.9+, JDK 17 and Android SDK 34."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / '.build'


def dependencies():
    folder = BUILD / 'libs'
    folder.mkdir(parents=True, exist_ok=True)
    result = []
    for item in json.loads((ROOT / 'dependencies.json').read_text(encoding='utf-8')):
        target = folder / item['file']
        if not target.exists():
            with urllib.request.urlopen(item['url'], timeout=60) as response:
                target.write_bytes(response.read())
        if hashlib.sha256(target.read_bytes()).hexdigest() != item['sha256']:
            raise RuntimeError('Dependency checksum mismatch: ' + item['file'])
        result.append(target)
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jdk', type=Path, default=os.environ.get('JAVA_HOME'))
    p.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    p.add_argument('--android-jar', type=Path)
    p.add_argument('--build-tools', type=Path)
    p.add_argument('--signing-dir', type=Path, default=ROOT / '.local-signing')
    p.add_argument('--test', action='store_true', help='Run offline JVM tests; no Android SDK required')
    args = p.parse_args()
    java_home = args.jdk
    if not java_home:
        javac = shutil.which('javac')
        if not javac:
            p.error('Set JAVA_HOME or pass --jdk with a JDK 17 directory')
        java_home = Path(javac).resolve().parent.parent
    java_home = java_home.resolve()
    exe = '.exe' if os.name == 'nt' else ''
    env = dict(os.environ, JAVA_HOME=str(java_home))
    env['PATH'] = str(java_home / 'bin') + os.pathsep + env['PATH']

    def run(*command):
        subprocess.run([str(x) for x in command], env=env, check=True, cwd=ROOT)

    libs = dependencies()
    cp = os.pathsep.join(map(str, libs))
    if args.test:
        classes = BUILD / 'test-classes'
        classes.mkdir(exist_ok=True)
        names = ['ScheduleCore', 'CalendarCore', 'DayData', 'RainAdvice', 'NewsCore', 'CommuteCore', 'SrfCore']
        sources = [ROOT / 'src/ch/morgenblick/tv' / (name + '.java') for name in names]
        run(java_home / ('bin/javac' + exe), '-encoding', 'UTF-8', '-cp', cp, '-d', classes, *sources, *sorted((ROOT / 'tests').glob('*.java')))
        tests = {
            'CoreTests': [], 'RainTests': [], 'CommuteTests': [],
            'DayTests': ['weather.json', 'trains.json'], 'NewsTests': ['news.xml'], 'SrfTests': ['srf.xml']}
        for name, fixtures in tests.items():
            run(java_home / ('bin/java' + exe), '-cp', cp + os.pathsep + str(classes), 'ch.morgenblick.tv.' + name,
                *[ROOT / 'tests/fixtures' / fixture for fixture in fixtures])
        return

    android_jar = args.android_jar or (args.sdk / 'platforms/android-34/android.jar' if args.sdk else None)
    tools = args.build_tools or (args.sdk / 'build-tools/34.0.0' if args.sdk else None)
    if not android_jar or not tools or not android_jar.is_file() or not tools.is_dir():
        p.error('Set ANDROID_HOME with platforms;android-34 and build-tools;34.0.0, or pass --android-jar and --build-tools')
    android_jar, tools = android_jar.resolve(), tools.resolve()
    classes, dex = BUILD / 'classes', BUILD / 'dex'
    for directory in [classes, dex]:
        # Restrict cleanup to these generated directories, including when symlinks exist.
        if directory.exists():
            if directory.resolve().parent != BUILD.resolve():
                raise RuntimeError('Generated directory resolves outside build directory')
            shutil.rmtree(directory)
        directory.mkdir()
    run(tools / ('aapt2' + exe), 'compile', '--dir', ROOT / 'res', '-o', BUILD / 'resources.zip')
    run(tools / ('aapt2' + exe), 'link', '-o', BUILD / 'base.apk', '-I', android_jar,
        '--manifest', ROOT / 'AndroidManifest.xml', '--min-sdk-version', '26', '--target-sdk-version', '34', BUILD / 'resources.zip')
    run(java_home / ('bin/javac' + exe), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-bootclasspath', str(android_jar) + os.pathsep + str(tools / 'core-lambda-stubs.jar'),
        '-classpath', cp, '-d', classes, *sorted((ROOT / 'src').rglob('*.java')))
    run(java_home / ('bin/java' + exe), '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8',
        '--min-api', '26', '--release', '--lib', android_jar, '--output', dex, *sorted(classes.rglob('*.class')), *libs)
    unsigned = BUILD / 'unsigned.apk'
    shutil.copyfile(BUILD / 'base.apk', unsigned)
    with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as apk:
        for part in sorted(dex.glob('*.dex')):
            apk.write(part, part.name)
        included = set(apk.namelist())
        for library in libs:
            with zipfile.ZipFile(library) as jar:
                for name in jar.namelist():
                    upper = name.upper()
                    if name.endswith(('/', '.class')) or name in included or upper == 'META-INF/MANIFEST.MF' or upper.endswith(('.SF', '.RSA', '.DSA')):
                        continue
                    apk.writestr(name, jar.read(name))
                    included.add(name)
        # Regression guard: the initial Android calendar crash came from missing Java resources.
        if 'biweekly/biweekly.properties' not in included:
            raise RuntimeError('Missing biweekly runtime resources')
    run(tools / ('zipalign' + exe), '-f', '4', unsigned, BUILD / 'aligned.apk')
    signing = args.signing_dir.resolve()
    signing.mkdir(parents=True, exist_ok=True)
    credentials = signing / 'credentials.json'
    if not credentials.exists():
        credentials.write_text(json.dumps({'password': secrets.token_urlsafe(32)}), encoding='utf-8')
        try:
            credentials.chmod(0o600)
        except OSError:
            pass
    env['MORGENBLICK_STORE_PASS'] = json.loads(credentials.read_text(encoding='utf-8'))['password']
    key = signing / 'morgenblick.jks'
    if not key.exists():
        run(java_home / ('bin/keytool' + exe), '-genkeypair', '-keystore', key,
            '-storepass:env', 'MORGENBLICK_STORE_PASS', '-keypass:env', 'MORGENBLICK_STORE_PASS',
            '-alias', 'morgenblick', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Morgenblick Local Build')
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    result = dist / 'Morgenblick.apk'
    run(java_home / ('bin/java' + exe), '-jar', tools / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-pass', 'env:MORGENBLICK_STORE_PASS', '--out', result, BUILD / 'aligned.apk')
    run(java_home / ('bin/java' + exe), '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose', result)
    print('Built:', result)


if __name__ == '__main__':
    main()
