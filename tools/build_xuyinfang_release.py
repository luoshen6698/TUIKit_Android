#!/usr/bin/env python3
"""Build the local signed Xuyinfang APK; secrets are read from macOS Keychain."""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
JDK = Path('/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
SDK = Path.home() / 'Library/Android/sdk'
KEYSTORE = Path.home() / '.config/xuyinfang/android-signing/xuyinfang-release.p12'
SERVICE = 'com.xuyinfang.app.android.release-keystore'
ALIAS = 'xuyinfang_release'

def main():
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
    tasks = sys.argv[1:] or [':app:testReleaseUnitTest', ':app:assembleRelease']
    command = ['./gradlew', '--no-daemon', '--max-workers=2', '--console=plain',
               '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8',
               '-Pkotlin.compiler.execution.strategy=in-process',
               '-PXINGDUN_HOME_ENABLED=true', '-PXINGDUN_OFFLINE_PUSH_ENABLED=' + push_enabled, *tasks]
    return subprocess.call(command, cwd=ROOT / 'chat/demo', env=env)

if __name__ == '__main__':
    sys.exit(main())
