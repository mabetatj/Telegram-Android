#!/usr/bin/env python3
"""Build with optional local credentials, without putting them in arguments or logs.

Also applies the NanoGram AI patch before the Gradle build: pins a "NanoGram AI"
row at the top of the dialogs list (right below the action bar, above the chat
rows, on every dialogs page). Tapping it opens the AI chat. The patch is
idempotent; it fails loudly if the anchor disappears upstream so that a broken
APK is never shipped silently.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

DIALOGS_PATH = Path('TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java')

AI_ROW_JAVA_TEMPLATE = '''try {{
    final android.view.View nanogramAiList = {var}.listView;
    nanogramAiList.post(new Runnable() {{
        @Override
        public void run() {{
            try {{
                if (!(nanogramAiList.getParent() instanceof android.view.ViewGroup)) {{
                    return;
                }}
                android.view.ViewGroup nanogramAiParent = (android.view.ViewGroup) nanogramAiList.getParent();
                if (nanogramAiParent.findViewWithTag("nanogram_ai_row_tag") != null) {{
                    return;
                }}
                android.app.Activity nanogramAiActivity = getParentActivity();
                if (nanogramAiActivity == null) {{
                    return;
                }}
                android.view.View nanogramAiRow = nanogram.ai.NanoGramAiRow.createWithDivider(nanogramAiActivity);
                nanogramAiRow.setTag("nanogram_ai_row_tag");
                nanogramAiParent.addView(nanogramAiRow, new android.widget.FrameLayout.LayoutParams(-1, -2, 49));
            }} catch (Throwable nanogramAiIgnore) {{ }}
        }}
    }});
}} catch (Throwable nanogramAiIgnore) {{ }}'''

AI_ROW_ANCHOR = re.compile(r'^([ \t]*)([A-Za-z_][A-Za-z0-9_]*)\.listView\.setInstantClick\(true\);[ \t]*$', re.M)


def patch_dialogs(root):
    """Insert the NanoGram AI row into the dialogs list (in-list chat entry)."""
    path = root / DIALOGS_PATH
    if not path.is_file():
        raise SystemExit('nanogram-ai: DialogsActivity.java not found; aborting build.')
    source = path.read_text(encoding='utf-8')
    if 'nanogram.ai.NanoGramAiRow' in source:
        print('nanogram-ai: row patch already applied')
        return
    matches = list(AI_ROW_ANCHOR.finditer(source))
    if not matches:
        raise SystemExit('nanogram-ai: anchor not found in DialogsActivity.java; aborting build.')
    patched = source
    for match in reversed(matches):
        indent, var = match.group(1), match.group(2)
        block_lines = []
        for line in AI_ROW_JAVA_TEMPLATE.format(var=var).split('\n'):
            block_lines.append((indent + line).rstrip() if line.strip() else '')
        block = '\n'.join(block_lines)
        end = match.end()
        patched = patched[:end] + '\n' + block + patched[end:]
    expected = len(matches)
    actual = patched.count('nanogram_ai_row_tag')
    if actual != expected:
        raise SystemExit('nanogram-ai: verification failed (%d anchors, %d inserts); aborting build.' % (expected, actual))
    path.write_text(patched, encoding='utf-8')
    print('nanogram-ai: NanoGram AI row pinned on %d dialogs page(s)' % expected)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('--credentials-file', type=Path)
    parser.add_argument('tasks', nargs='*', default=[':TMessagesProj_AppStandalone:assembleAfatDebug'])
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    patch_dialogs(root)
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
