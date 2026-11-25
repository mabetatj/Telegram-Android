#!/usr/bin/env python3
"""Build with optional local credentials, without putting them in arguments or logs.

Also applies the NanoGram AI patch before the Gradle build: pins a "NanoGram AI"
row at the very top of the dialogs list (visible above the chat list on every
folder tab), which opens the AI chat on tap. The patch is idempotent; if the
anchor disappears upstream the build continues and the launcher shortcut
remains the entry point.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

DIALOGS_PATH = Path('TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java')

AI_ROW_JAVA = '''try {
    final org.telegram.ui.ActionBar.ActionBar nanogramAiActionBar = getActionBar();
    final android.view.View nanogramAiRow = nanogram.ai.NanoGramAiRow.create(getParentActivity());
    if (nanogramAiRow != null && nanogramAiActionBar != null && nanogramAiActionBar.getParent() instanceof android.view.ViewGroup) {
        android.view.ViewGroup nanogramAiHost = (android.view.ViewGroup) nanogramAiActionBar.getParent();
        nanogramAiHost.removeView(nanogramAiRow);
        nanogramAiHost.addView(nanogramAiRow, new android.widget.FrameLayout.LayoutParams(-1, -2, 49));
        android.view.ViewTreeObserver.OnPreDrawListener nanogramAiLayout = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                nanogramAiRow.getViewTreeObserver().removeOnPreDrawListener(this);
                float nanogramAiHeader = 0f;
                if (nanogramAiActionBar.getVisibility() == android.view.View.VISIBLE) {
                    nanogramAiHeader = nanogramAiActionBar.getTranslationY() + nanogramAiActionBar.getHeight();
                }
                nanogramAiRow.setTranslationY(nanogramAiHeader);
                return true;
            }
        };
        nanogramAiRow.getViewTreeObserver().addOnPreDrawListener(nanogramAiLayout);
        nanogramAiRow.post(new Runnable() {
            @Override
            public void run() {
                float nanogramAiHeader = 0f;
                if (nanogramAiActionBar.getVisibility() == android.view.View.VISIBLE) под android.view.View.VISIBLE) {
                    nanogramAiHeader = nanogramAiActionBar.getTranslationY() + nanogramAiActionBar.getHeight();
                }
                nanogramAiRow.setTranslationY(nanogramAiRow.getTranslationY() - nanogramAiHeader);
            }
        });
    }
} catch (Throwable nanogramAiIgnore) { }'''
