#!/usr/bin/env python3
"""Build with optional local credentials, without putting them in arguments or logs."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

DIALOGS_PATH = Path('TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java')

AI_ROW_JAVA = '''try {
    android.app.Activity nanogramAiActivity = getParentActivity();
    if (nanogramAiActivity != null) {
        android.view.View nanogramAiRowView = nanogram.ai.NanoGramAiRow.createWithDivider(nanogramAiActivity);
        android.view.ViewGroup nanogramAiListParent = null;
        try {
            java.lang.reflect.Field nanogramAiField = viewPage.getClass().getDeclaredField("listView");
            nanogramAiField.setAccessible(true);
            android.view.View nanogramAiList = (android.view.View) nanogramAiField.get(viewPage);
            if (nanogramAiList != null && nanogramAiList.getParent() instanceof android.view.ViewGroup) {
                nanogramAiListParent = (android.view.ViewGroup) nanogramAiList.getParent();
            }
        } catch (Throwable nanogramAiIgnore) {
            nanogramAiListParent = null;
        }
        if (nanogramAiListParent == null) {
            try {
                java.lang.reflect.Field nanogramAiField = viewPage.getClass().getDeclaredField("fragmentView");
                nanogramAiField.setAccessible(true);
                Object nanogramAiValue = nanogramAiField.get(viewPage);
                if (nanogramAiValue instanceof android.view.ViewGroup) {
                    nanogramAiListParent = (android.view.ViewGroup) nanogramAiValue;
                }
            } catch (Throwable nanogramAiIgnore) {
                nanogramAiListParent = null;
            }
        }
        if (nanogramAiListParent != null) {
            nanogramAiListParent.addView(nanogramAiRowView, 0, new android.widget.FrameLayout.LayoutParams(-1, -2, 49));
        }
    }
} catch (Throwable nanogramAiIgnore) { }'''


def patch_dialogs(root):
    """Insert the NanoGram AI row into the dialogs list (in-list chat entry).

    Idempotent and fail-soft: if the anchor is not found the build continues and
    the launcher shortcut (res/xml/shortcuts.xml) remains the entry point.
    """
    path = root / DIALOGS_PATH
    try:
        if not path.is_file():
            print('nanogram-ai: DialogsActivity.java missing, row patch skipped')
            return
        source = path.read_text(encoding='utf-8')
        if 'nanogram.ai.NanoGramAiRow' in source:
            print('nanogram-ai: row patch already applied')
            return
        match = re.search(r'^([ \t]*)viewPage\s*=\s*new\s+ViewPage\s*\(', source, re.M)
        if match is None:
            print('nanogram-ai: ViewPage anchor not found, row patch skipped')
            return
        semicolon = source.find(';', match.end())
        if semicolon == -1 or semicolon - match.end() > 300:
            print('nanogram-ai: ViewPage anchor unusable, row patch skipped')
            return
        indent = match.group(1)
        block = '\n'.join((indent + line).rstrip() if line.strip() else '' for line in AI_ROW_JAVA.split('\n'))
        patched = source[:semicolon + 1] + '\n' + block + '\n' + source[semicolon + 1:]
        if 'nanogram.ai.NanoGramAiRow' not in patched:
            print('nanogram-ai: verification failed, row patch skipped')
            return
        path.write_text(patched, encoding='utf-8')
        print('nanogram-ai: NanoGram AI row added to dialogs list')
    except Exception as error:
        print('nanogram-ai: row patch skipped (%s)' % error)


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
