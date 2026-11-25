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
 * Экран ИИ-чата NanoGram.
 *
 * Самодостаточный экран на чистом Android UI (без зависимостей от классов Telegram-форка),
 * поэтому его добавление не может сломать сборку.
 *
 * - Тёмная тема в тон NanoGram.
 * - Сообщения пользователя справа, ответы ИИ слева, стриминг по буквам.
 * - Кнопка отправки превращается в «стоп» на время генерации; повторный тап = стоп.
 * - При ошибке сети показывается понятное сообщение, отмена не считается ошибкой.
 */
public class NanoGramAiActivity extends Activity {

    private LinearLayout messagesList;
    private ScrollView scroll;
    private EditText inputField;
    private TextView sendButton;
    private TextView assistantBubble;   // пузырь текущего ответа (стриминг)
    private TextView typingBubble;      // «Думаю…» до первого токена
    private StringBuilder streamingText = new StringBuilder();
    private volatile boolean generating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF17212B); // тёмный фон NanoGram

        // --- Шапка ---
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

        // --- Лента сообщений ---
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

        // --- Поле ввода ---
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottom.setBackgroundColor(0xFF17212B);

        inputField = new EditText(this);
        inputField.setHint("Спросить NanoGram AI...");
        inputField.setHintTextColor(0xFF6D7A85);
        inputField.setTextColor(Color.WHITE);
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
        sendButton.setText("➤");
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

        // --- Слушатели ---
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

    /** Показывает сохранённую историю (после прошлого запуска). */
    private void renderHistory() {
        List<NanoGramAi.Message> history = NanoGramAi.getInstance(this).getHistory();
        for (NanoGramAi.Message m : history) {
            addBubble(m.content, m.role.equals("user"), false);
        }
        if (history.isEmpty()) {
            addBubble("Привет! Я NanoGram AI — спроси меня о чём угодно.", false, false);
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
        typingBubble = null;
        streamingText.setLength(0);

        NanoGramAi.getInstance(this).send(text, new NanoGramAi.Listener() {
            @Override
            public void onToken(String delta) {
                runOnUiThread(() -> {
                    if (typingBubble != null) {
                        // Убираем «Думаю…» перед первым токеном ответа.
                        View parent = (View) typingBubble.getParent();
                        if (parent instanceof LinearLayout) {
                            ((LinearLayout) parent).removeView(typingBubble);
                        }
                        typingBubble = null;
                    }
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
                        addBubble(streamingText.length() > 0 ? streamingText.toString() : "(пустой ответ)", false, false);
                    }
                    setGenerating(false);
                });
            }

            @Override
            public void onError(String message, boolean userCancelled) {
                runOnUiThread(() -> {
                    if (userCancelled) {
                        addBubble("⏹ Остановлено", false, false);
                    } else {
                        addBubble("⚠ " + message, false, false);
                    }
                    setGenerating(false);
                });
            }
        });

        // «Думаю…» пока не пришёл первый кусочек ответа.
        showTyping();
    }

    private void showTyping() {
        if (generating) {
            typingBubble = addBubble("Думаю…", false, false);
        }
    }

    private void stopGenerating() {
        NanoGramAi.getInstance(this).cancel();
        setGenerating(false);
    }

    private void setGenerating(boolean on) {
        generating = on;
        sendButton.setText(on ? "⏹" : "➤");
        sendButton.setEnabled(on || inputField.length() > 0);
        sendButton.setAlpha(sendButton.isEnabled() ? 1f : 0.4f);
        sendButton.setTextColor(on ? 0xFFE06060 : 0xFF5288C1);
    }

    /** Добавляет пузырь сообщения; true = пользователь (справа), false = ИИ (слева). */
    private TextView addBubble(String text, boolean mine, boolean streaming) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(16);
        bubble.setTextColor(Color.WHITE);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setLineSpacing(dp(2), 1f);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mine ? 0xFF2B5278 : 0xFF232E3C); // синий NanoGram / тёмный
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
}
