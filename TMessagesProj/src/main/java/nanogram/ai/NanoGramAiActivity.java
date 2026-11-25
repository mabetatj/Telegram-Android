package nanogram.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Р­РєСЂР°РЅ РР-С‡Р°С‚Р° NanoGram.
 *
 * РњРѕРґСѓР»СЊ 2: СЃР°РјРѕРґРѕСЃС‚Р°С‚РѕС‡РЅС‹Р№ СЌРєСЂР°РЅ РЅР° С‡РёСЃС‚РѕРј Android UI (Р±РµР· Р·Р°РІРёСЃРёРјРѕСЃС‚РµР№ РѕС‚ РєР»Р°СЃСЃРѕРІ Telegram-С„РѕСЂРєР°),
 * РїРѕСЌС‚РѕРјСѓ РµРіРѕ РґРѕР±Р°РІР»РµРЅРёРµ РЅРµ РјРѕР¶РµС‚ СЃР»РѕРјР°С‚СЊ СЃР±РѕСЂРєСѓ.
 *
 * - РўС‘РјРЅР°СЏ С‚РµРјР° РІ С‚РѕРЅ NanoGram.
 * - РЎРѕРѕР±С‰РµРЅРёСЏ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ СЃРїСЂР°РІР°, РѕС‚РІРµС‚С‹ РР СЃР»РµРІР°, СЃС‚СЂРёРјРёРЅРі РїРѕ Р±СѓРєРІР°Рј.
 * - РљРЅРѕРїРєР° РѕС‚РїСЂР°РІРєРё РґРµР°РєС‚РёРІРЅР°, РїРѕРєР° РѕС‚РІРµС‚ РіРµРЅРµСЂРёСЂСѓРµС‚СЃСЏ; РїРѕРІС‚РѕСЂРЅС‹Р№ С‚Р°Рї РїРѕ РЅРµР№ = СЃС‚РѕРї.
 */
public class NanoGramAiActivity extends Activity {

    private LinearLayout messagesList;
    private ScrollView scroll;
    private EditText inputField;
    private TextView sendButton;
    private TextView assistantBubble;   // РїСѓР·С‹СЂСЊ С‚РµРєСѓС‰РµРіРѕ РѕС‚РІРµС‚Р° (СЃС‚СЂРёРјРёРЅРі)
    private StringBuilder streamingText = new StringBuilder();
    private volatile boolean generating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF17212B); // С‚С‘РјРЅС‹Р№ С„РѕРЅ NanoGram
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // --- РЁР°РїРєР° ---
        TextView header = new TextView(this);
        header.setText("NanoGram AI");
        header.setTextColor(Color.WHITE);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextSize(20);
        header.setPadding(dp(20), dp(18), dp(20), dp(14));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(0xFF101921);
        root.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // --- Р›РµРЅС‚Р° СЃРѕРѕР±С‰РµРЅРёР№ ---
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messagesList = new LinearLayout(this);
        messagesList.setOrientation(LinearLayout.VERTICAL);
        messagesList.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(messagesList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        // --- РџРѕР»Рµ РІРІРѕРґР° ---
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottom.setBackgroundColor(0xFF17212B);

        inputField = new EditText(this);
        inputField.setHint("РЎРїСЂРѕСЃРёС‚Рµ NanoGram AI...");
        inputField.setHintTextColor(0xFF6D7A85);
        inputField.setTextColor(Color.WHITE);
        inputField.setBackgroundColor(0xFF232E3C);
        inputField.setPadding(dp(14), dp(10), dp(14), dp(10));
        inputField.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputField.setMaxLines(6);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0xFF232E3C);
        inputBg.setCornerRadius(dp(12));
        inputField.setBackground(inputBg);
        bottom.addView(inputField, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = new TextView(this);
        sendButton.setText("вћ¤");
        sendButton.setTextColor(0xFF5288C1);
        sendButton.setTextSize(24);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setPadding(dp(6), dp(8), dp(6), dp(8));
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.4f);
        bottom.addView(sendButton, new LinearLayout.LayoutParams(dp(56),
                LinearLayout.LayoutParams.MATCH_PARENT));

        root.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        // --- РЎР»СѓС€Р°С‚РµР»Рё ---
        inputField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                if (!generating) {
                    sendButton.setEnabled(s.length() > 0);
                    sendButton.setAlpha(s.length() > 0 ? 1f : 0.4f);
                }
            }
        });
        inputField.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { send(); return true; }
            return false;
        });
        sendButton.setOnClickListener(v -> {
            if (generating) stopGenerating(); else send();
        });

        renderHistory();
    }

    /** РџРѕРєР°Р·С‹РІР°РµС‚ СЃРѕС…СЂР°РЅС‘РЅРЅСѓСЋ РёСЃС‚РѕСЂРёСЋ (РїРѕСЃР»Рµ РїСЂРѕС€Р»РѕРіРѕ Р·Р°РїСѓСЃРєР°). */
    private void renderHistory() {
        List<NanoGramAi.Message> history = NanoGramAi.getInstance(this).getHistory();
        for (NanoGramAi.Message m : history) {
            addBubble(m.content, m.role.equals("user"), false);
        }
        if (history.isEmpty()) {
            addBubble("РџСЂРёРІРµС‚! РЇ NanoGram AI вЂ” СЃРїСЂРѕСЃРё РјРµРЅСЏ Рѕ С‡С‘Рј СѓРіРѕРґРЅРѕ.", false, false);
        }
        scrollToBottom();
    }

    private void send() {
        String text = inputField.getText().toString().trim();
        if (text.isEmpty() || generating) return;
        inputField.setText("");
        addBubble(text, true, false);
        setGenerating(true);

        assistantBubble = null;
        streamingText.setLength(0);

        NanoGramAi.getInstance(this).send(text, new NanoGramAi.Listener() {
            @Override
            public void onToken(String delta) {
                runOnUiThread(() -> {
                    if (assistantBubble == null) {
                        assistantBubble = addBubble("", false, true);
                    }
                    streamingText.append(delta);
                    assistantBubble.setText(streamingText.toString());
                    scrollToBottom();
                });
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (assistantBubble == null) {
                        addBubble(streamingText.length() > 0 ? streamingText.toString() : "(РїСѓСЃС‚РѕР№ РѕС‚РІРµС‚)", false, false);
                    }
                    setGenerating(false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    addBubble("вљ  " + message, false, false);
                    setGenerating(false);
                });
            }
        });
    }

    private void stopGenerating() {
        NanoGramAi.getInstance(this).cancel();
        setGenerating(false);
        addBubble("вЏ№ РћСЃС‚Р°РЅРѕРІР»РµРЅРѕ", false, false);
    }

    private void setGenerating(boolean on) {
        generating = on;
        sendButton.setText(on ? "вЏ№" : "вћ¤");
        sendButton.setEnabled(on || inputField.length() > 0);
        sendButton.setAlpha(sendButton.isEnabled() ? 1f : 0.4f);
        sendButton.setTextColor(on ? 0xFFE06060 : 0xFF5288C1);
    }

    /** Р”РѕР±Р°РІР»СЏРµС‚ РїСѓР·С‹СЂСЊ СЃРѕРѕР±С‰РµРЅРёСЏ; true = РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ (СЃРїСЂР°РІР°), false = РР (СЃР»РµРІР°). */
    private TextView addBubble(String text, boolean mine, boolean streaming) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(16);
        bubble.setTextColor(Color.WHITE);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setLineSpacing(dp(2), 1f);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mine ? 0xFF2B5278 : 0xFF232E3C); // СЃРёРЅРёР№NanoGram / С‚С‘РјРЅС‹Р№
        bg.setCornerRadius(dp(14));
        bubble.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (mine) {
            lp.gravity = Gravity.END;
            lp.rightMargin = dp(4);
        } else {
            lp.gravity = Gravity.START;
            lp.leftMargin = dp(4);
        }
        bubble.setLayoutParams(lp);
        row.addView(bubble);
        messagesList.addView(row);
        scrollToBottom();
        return bubble;
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onBackPressed() {
        NanoGramAi.getInstance(this).cancel();
        super.onBackPressed();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }