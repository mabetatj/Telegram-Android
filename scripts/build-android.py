#!/usr/bin/env python3
"""Build with optional local credentials, without putting them in arguments or logs.

Also applies the NanoGram AI patch before the Gradle build:
the manifest gets a startup provider (nanogram.ai.NanoGramAiInit) that pins a
"NanoGram AI" row at the top of the dialogs list (inside the chat list screen,
below the action bar). Tapping the row opens the AI chat. All anchors are
verified at patch time; the build fails loudly if they disappear so a broken
APK is never shipped silently.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

MANIFEST_PATH = Path('TMessagesProj/src/main/AndroidManifest.xml')
PROVIDER_LINE = ('        <provider\n'
                 '            android:name="nanogram.ai.NanoGramAiInit"\n'
                 '            android:authorities="${applicationId}.nanogram_ai_init"\n'
                 '            android:exported="false"\n'
                 '            android:initOrder="100" />\n')


def patch_manifest(root):
    """Register the NanoGram AI startup provider in the manifest."""
    path = root / MANIFEST_PATH
    if not path.is_file():
        raise SystemExit('nanogram-ai: AndroidManifest.xml not found; aborting build.')
    source = path.read_text(encoding='utf-8')
    if 'nanogram.ai.NanoGramAiInit' in source:
        print('nanogram-ai: manifest provider already registered')
        return
    anchor = '</application>'
    count = source.count(anchor)
    if count != 1:
        raise SystemExit('nanogram-ai: expected exactly one </application> tag, found %d; aborting build.' % count)
    source = source.replace(anchor, PROVIDER_LINE + anchor)
    path.write_text(source, encoding='utf-8')
    print('nanogram-ai: NanoGramAiInit provider registered in manifest')


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--credentials-file', type=Path)
    parser.add_argument('tasks', nargs='*', default=[':TMessagesProj_AppStandalone:assembleAfatDebug'])
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    patch_manifest(root)
    env = dict(os.environ)
    if args.credentials_file:
        try:
            if os.name == 'posix' and args.credentials_file.stat().st_mode & 0o077:
                parser.error('The credential file must be private: chmod 600 <file>')
            credentials = json.loads(args.credentials_file.read_text())
        except (OSError, ValueError):
            parser.error('Cannot read credentials: supply a readable private JSON file')
        if not isinstance(credentials, dict):
            parser.error('The credential file must contain a JSON object')
        for key in ('TELEGRAM_API_ID', 'TELEGRAM_API_HASH'):
            value = str(credentials.get(key, ''))
            pattern = r'[1-9][0-9]{0,9}' if key.endswith('_ID') else r'[a-fA-F0-9]{32}'
            if not re.fullmatch(pattern, value) or (key.endswith('_ID') and int(value) > 2147483647):
                parser.error('Invalid or missing ' + key)
            env[key] = value
    command = [str(root / 'gradlew'), '--no-daemon', '--console=plain', *args.tasks]
    return subprocess.call(command, cwd=root, env=env)


if __name__ == '__main__':
    raise SystemExit(main())
