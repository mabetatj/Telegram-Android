#!/usr/bin/env python3
"""Build with optional local credentials, without putting them in arguments or logs.

Also applies patches before the Gradle build:

1. NanoGram AI patch: the manifest gets a startup provider (nanogram.ai.NanoGramAiInit)
   that pins a "NanoGram AI" row at the top of the dialogs list (inside the chat list
   screen, below the action bar). Tapping the row opens the AI chat. All anchors are
   verified at patch time; the build fails loudly if they disappear so a broken APK
   is never shipped silently.

2. NanoGram rename patch: the launcher label and in-app name are renamed from
   BananaGram to NanoGram in every localized strings.xml, so the fork carries its
   own name everywhere the user can see it.
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

RESOURCES_DIR = Path('TMessagesProj/src/main/res')
OLD_NAME = 'BananaGram'
NEW_NAME = 'NanoGram'


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


def patch_resources(root):
    """Rename BananaGram to NanoGram in every localized strings.xml."""
    res = root / RESOURCES_DIR
    if not res.is_dir():
        raise SystemExit('nanogram: resources directory not found; aborting build.')
    patched = 0
    for path in sorted(res.glob('values*/strings.xml')):
        text = path.read_text(encoding='utf-8')
        if OLD_NAME not in text:
            continue
        text = text.replace(OLD_NAME + ' Beta', NEW_NAME + ' Beta')
        text = text.replace(OLD_NAME, NEW_NAME)
        path.write_text(text, encoding='utf-8')
        patched += 1
        print('nanogram: renamed %s to %s in %s' % (OLD_NAME, NEW_NAME, path.relative_to(root)))
    remaining = sum(1 for path in res.glob('values*/strings.xml') if OLD_NAME in path.read_text(encoding='utf-8'))
    if remaining:
        raise SystemExit('nanogram: %d strings.xml files still contain %s; aborting build.' % (remaining, OLD_NAME))
    if patched == 0:
        print('nanogram: no BananaGram strings found in resources (already renamed)')
    else:
        print('nanogram: renamed in %d strings.xml file(s)' % patched)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--credentials-file', type=Path)
    parser.add_argument('tasks', nargs='*', default=[':TMessagesProj_AppStandalone:assembleAfatDebug'])
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    patch_manifest(root)
    patch_resources(root)
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
