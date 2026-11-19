#!/usr/bin/env python3
"""Build the local signed Xuyinfang APK; secrets are read from macOS Keychain."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path.home() / 'Documents/project/TUIKit_Android-xuyingfang'
ORIGIN = 'https://github.com/luoshen6698/TUIKit_Android.git'
OUTPUT = ROOT / 'chat/demo/app/build/outputs/apk/release'
MATERIALS = {'logo.png', 'signing', '备案+软著资料 (叙音坊).md', '安卓截图'}
PACKAGE = 'com.xuyinfang.app'
JDK = Path('/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
SDK = Path.home() / 'Library/Android/sdk'
KEYSTORE = Path.home() / '.config/xuyinfang/android-signing/xuyinfang-release.p12'
SERVICE = 'com.xuyinfang.app.android.release-keystore'
ALIAS = 'xuyinfang_release'

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode('utf-8')


def verify_source(commit):
    if ROOT != SOURCE.resolve() or Path(git('rev-parse', '--show-toplevel').strip()).resolve() != ROOT:
        raise SystemExit(f'Android release source must be {SOURCE}.')
    if not re.fullmatch('[0-9a-f]{40}', commit):
        raise SystemExit('Pass the approved full 40-character main commit with --commit.')
    if git('remote', 'get-url', 'origin').strip() != ORIGIN:
        raise SystemExit('Android release origin is not the approved repository.')
    if git('branch', '--show-current').strip() != 'main' or git('rev-parse', 'HEAD').strip() != commit:
        raise SystemExit('Android release checkout must be on main at the approved commit.')
    if git('ls-remote', '--exit-code', 'origin', 'refs/heads/main').split()[0] != commit:
        raise SystemExit('Approved release commit does not match live origin/main.')
    if any(entry and (entry[0].islower() or entry[0] == 'S') for entry in git('ls-files', '-v', '-z').split('\0')):
        raise SystemExit('Remove hidden Git index flags before the release build.')
    for entry in git('status', '--porcelain=v1', '-z', '--untracked-files=all').split('\0'):
        if not entry or entry == ' M .DS_Store':
            continue
        if entry.startswith('?? ') and entry[3:].split('/', 1)[0] in MATERIALS:
            continue
        raise SystemExit('Android release source contains changes or unexpected local files.')
    print(f'Release source verified: {ROOT} main {commit}', flush=True)


def record_artifact(commit, channel):
    apk = OUTPUT / 'app-release.apk'
    metadata = json.loads((OUTPUT / 'output-metadata.json').read_text())
    elements = metadata.get('elements', [])
    if metadata.get('applicationId') != PACKAGE or len(elements) != 1 or elements[0].get('outputFile') != apk.name:
        raise SystemExit('Unexpected Android release output identity.')
    tool_dirs = sorted((SDK / 'build-tools').iterdir(), reverse=True)
    tool_dir = next((directory for directory in tool_dirs if (directory / 'aapt').is_file()
                     and (directory / 'apksigner').is_file()), None)
    if tool_dir is None:
        raise SystemExit('Android SDK build tools are unavailable.')
    manifest = subprocess.check_output([str(tool_dir / 'aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
    field = re.search(r'="com[.]xuyinfang[.]distribution[.]channel"[^\n]*\n[^\n]*="([^"]+)"', manifest)
    if not field or field.group(1) != channel:
        raise SystemExit('APK channel does not match the requested release channel.')
    cert = subprocess.check_output([str(tool_dir / 'apksigner'), 'verify', '--print-certs', str(apk)],
                                   env={**os.environ, 'JAVA_HOME': str(JDK)}, text=True)
    if 'certificate SHA-256 digest: a91fe88accabec6d404da6f93e8f1494f52b12c617c142d91d6cee6f25dc82ff' not in cert:
        raise SystemExit('APK was not signed with the filed Xuyinfang certificate.')
    checksum = hashlib.sha256()
    with apk.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            checksum.update(chunk)
    record = dict(commit=commit, channel=channel, package=PACKAGE, versionName=elements[0]['versionName'],
                  versionCode=elements[0]['versionCode'], sha256=checksum.hexdigest())
    (OUTPUT / 'xuyinfang-release-source.json').write_text(json.dumps(record, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--source-only', action='store_true')
    args, tasks = parser.parse_known_args()
    verify_source(args.commit)
    if args.source_only:
        return 0
    if not KEYSTORE.is_file() or not (JDK / 'bin/java').is_file():
        raise SystemExit('Missing local signing identity or JDK 17; see delivery materials.')
    result = subprocess.run(['security', 'find-generic-password', '-s', SERVICE,
                             '-a', ALIAS, '-w'], capture_output=True, text=True)
    if result.returncode != 0 or not result.stdout.strip():
        raise SystemExit('Release password is unavailable in macOS Keychain.')
    password = result.stdout.strip()
    env = os.environ.copy()
    push_enabled = env.get('XINGDUN_OFFLINE_PUSH_ENABLED', 'true')
    if push_enabled not in ('true', 'false'):
        raise SystemExit('XINGDUN_OFFLINE_PUSH_ENABLED must be true or false.')
    env.update(JAVA_HOME=str(JDK), ANDROID_HOME=str(SDK), ANDROID_SDK_ROOT=str(SDK),
               XINGDUN_RELEASE_STORE_FILE=str(KEYSTORE),
               XINGDUN_RELEASE_STORE_PASSWORD=password,
               XINGDUN_RELEASE_KEY_ALIAS=ALIAS,
               XINGDUN_RELEASE_KEY_PASSWORD=password)
    tasks = tasks or [':app:testReleaseUnitTest', ':app:assembleRelease']
    channel = env.get('XINGDUN_RELEASE_CHANNEL', 'universal')
    builds_release = any(task in ('assembleRelease', ':app:assembleRelease') for task in tasks)
    if builds_release:
        (OUTPUT / 'xuyinfang-release-source.json').unlink(missing_ok=True)
    command = ['./gradlew', '--no-daemon', '--max-workers=2', '--console=plain',
               '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8',
               '-Pkotlin.compiler.execution.strategy=in-process',
               '-PXINGDUN_HOME_ENABLED=true', '-PXINGDUN_OFFLINE_PUSH_ENABLED=' + push_enabled, *tasks]
    result = subprocess.call(command, cwd=ROOT / 'chat/demo', env=env)
    if result != 0:
        return result
    verify_source(args.commit)
    if builds_release:
        record_artifact(args.commit, channel)
    return 0

if __name__ == '__main__':
    sys.exit(main())
