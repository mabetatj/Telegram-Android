package nanogram.ai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Экран ИИ-чата NanoGram — визуально как обычный чат NanoGram.
 *
 * Самодостаточный (чистый Android UI, без классов Telegram-форка), поэтому
 * не может сломать сборку, но копирует внешний вид настоящего чата:
 *
 * - Обои-градиент вместо плоского фона.
 * - Пузыри как ChatMessageCell: крупные радиусы у трёх углов и маленький
 *   у «хвостового» угла со стороны отправителя. Цвет пользователя — фирменный
 *   синий NanoGram, ИИ — тёмный.
 * - Кнопка отправки — круглая, в фирменном синем.
 * - Стриминг ответа по токенам с пузырём «Думаю…».
 *
 * UI v3 (по скриншотам пользователя):
 * - Заголовок «NanoGram AI» опущен ниже статус-бара (не перекрывается
 *   временем/батареей) — контент сдвигается с учётом системных insets.
 * - Под заголовком слева добавлена стрелка «← назад» для выхода
 *   в список чатов.
 */
public class NanoGramAiActivity extends Activity {

    // --- Палитра NanoGram (тёмная тема) ---
    private static final int COLOR_BG_TOP = 0xFF1A2433;
    private static final int COLOR_BG_BOTTOM = 0xFF1F2A38;
    private static final int COLOR_IN_BUBBLE = 0xFF1F2A38;   // ИИ (входящее)
    private static final int COLOR_OUT_BUBBLE = 0xFF2B5278;  // Пользователь (исходящее)
    private static final int COLOR_TEXT = 0xFFFFFFFF;        // Текст сообщений
    private static final int COLOR_TIME_IN = 0xFF7D8B99;     // Время на входящем
    private static final int COLOR_TIME_OUT = 0xFFA2C4E0;    // Время на исходящем
    private static final int COLOR_INPUT_BG = 0xFF18212E;    // Фон поля ввода
    private static final int COLOR_SEND = 0xFF5AA8E8;         // Иконка отправки
    private static final int COLOR_BACK = 0xFF8FB8D8;         // Стрелка «назад»
    private static final int COLOR_DATE_CHIP = 0xFF22303F;   // Плашка даты

    private LinearLayout messagesList;
    private ScrollView scroll;
    private EditText inputField;
    private TextView sendButton;

    private TextView assistantBubble;   // пузырь стримящегося ответа ИИ
    private TextView typingBubble;     // пузырь «Думаю…»
    private final StringBuilder streamingText = new StringBuilder();
    private long streamingTime;

    private boolean generating;
    private boolean lastWasMine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM}));

        final LinearLayout header = buildHeader();
        final LinearLayout inputBar = buildInputBar();

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messagesList = new LinearLayout(this);
        messagesList.setOrientation(LinearLayout.VERTICAL);
        messagesList.setPadding(dp(8), dp(10), dp(8), dp(10));
        scroll.addView(messagesList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(inputBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Рисуем под системными барами, но сдвигаем содержимое с учётом insets,
        // чтобы заголовок не перекрывался статус-баром (время, батарея), а поле
        // ввода не перекрывалось жестом «назад».
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                try {
                    int top = insets.getSystemWindowInsetTop();
                    int bottom = insets.getSystemWindowInsetBottom();
                    header.setPadding(header.getPaddingLeft(),
                            top + dp(6),
                            header.getPaddingRight(),
                            header.getPaddingBottom());
                    inputBar.setPadding(inputBar.getPaddingLeft(),
                            inputBar.getPaddingTop(),
                            inputBar.getPaddingRight(),
                            dp(8) + bottom);
                } catch (Throwable ignored) { }
                return insets;
            }
        });

        setContentView(root);
        wireInput();
        renderHistory();
    }

    /** Шапка: заголовок «NanoGram AI», ниже слева — стрелка выхода в список чатов. */
    private LinearLayout buildHeader() {
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.VERTICAL);
        headerBar.setGravity(Gravity.LEFT);
        headerBar.setPadding(dp(16), dp(6), dp(16), dp(10));

        TextView title = new TextView(this);
        title.setText("NanoGram AI");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(20);
        headerBar.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Стрелка «назад» — под заголовком, слева: выход в список чатов.
        TextView backArrow = new TextView(this);
        backArrow.setText("←");
        backArrow.setTextColor(COLOR_BACK);
        backArrow.setTextSize(22);
        backArrow.setTypeface(Typeface.DEFAULT_BOLD);
        backArrow.setPadding(dp(10), dp(2), dp(10), dp(2));
        backArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    finish();
                } catch (Throwable ignored) { }
            }
        });
        headerBar.addView(backArrow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return headerBar;
    }

    /** Отрисовывает сохранённую историю чата при открытии экрана. */
    private void renderHistory() {
        List<NanoGramAi.Message> history = null;
        try {
            history = NanoGramAi.getInstance(this).getHistory();
        } catch (Throwable ignored) { }
        if (history != null && !history.isEmpty()) {
            long lastDay = 0;
            boolean lastMine = false;
            for (NanoGramAi.Message m : history) {
                long day = dayStart(m.time > 0 ? m.time : System.currentTimeMillis());
                if (m.time > 0 && day != lastDay) {
                    addDateChip(m.time);
                    lastDay = day;
                }
                addBubble(m.content, "user".equals(m.role), m.time, lastMine == "user".equals(m.role));
                lastMine = "user".equals(m.role);
            }
            lastWasMine = lastMine;
            scrollToBottom();
        } else if (history != null) {
            addBubble("Привет! Я NanoGram AI — локальный ассистент. Задайте вопрос :)", false, 0, false);
        }
    }

    // ------------------------------------------------------------------
    // Пузыри сообщений
    // ------------------------------------------------------------------

    /** Пузырь в стиле ChatMessageCell: время мелким текстом внутри. */
    private TextView addBubble(String text, boolean mine, long time, boolean sameAuthorAsPrevious) {
        TextView bubble = new TextView(this);
        bubble.setTextSize(16);
        bubble.setTextColor(COLOR_TEXT);
        bubble.setLineSpacing(dp(2), 1f);
        bubble.setPadding(dp(12), dp(7), dp(12), dp(7));
        bubble.setText(withTime(text != null ? text : "", mine, time));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mine ? COLOR_OUT_BUBBLE : COLOR_IN_BUBBLE);
        float r = dp(17.5f);
        float tail = dp(4);
        // Углы: левый-верх, правый-верх, правый-низ, левый-низ.
        // Хвостик: у пользователя — правый-низ, у ИИ — левый-низ.
        if (mine) {
            bg.setCornerRadii(new float[]{r, r, r, r, tail, tail, r, r});
        } else {
            bg.setCornerRadii(new float[]{r, r, r, r, r, r, tail, tail});
        }
        bubble.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, sameAuthorAsPrevious ? dp(2) : dp(6), 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.leftMargin = mine ? dp(48) : dp(4);
        lp.rightMargin = mine ? dp(4) : dp(48);
        bubble.setLayoutParams(lp);
        row.addView(bubble);
        messagesList.addView(row);
        lastWasMine = mine;
        scrollToBottom();
        return bubble;
    }

    /** Плашка-разделитель даты по центру, как в Telegram. */
    private void addDateChip(long time) {
        TextView chip = new TextView(this);
        chip.setTextSize(12);
        chip.setTextColor(0xFF9FB0C0);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        chip.setText(new SimpleDateFormat("d MMMM", Locale.getDefault()).format(cal.getTime()));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(COLOR_DATE_CHIP);
        chipBg.setCornerRadius(dp(12));
        chip.setBackground(chipBg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(chip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        messagesList.addView(row);
    }

    /** Новая плашка даты — только если наступил новый день. */
    private void addDateChipIfNewDay(long time) {
        if (dayStart(time) != dayStart(System.currentTimeMillis() - 1) || messagesList.getChildCount() == 0) {
            addDateChip(time);
        }
    }

    /** Текст пузыря + время мелким шрифтом в конце, как в Telegram. time <= 0 — без времени. */
    private CharSequence withTime(String text, boolean mine, long time) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        if (time > 0) {
            String stamp = "  " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(time));
            ssb.append(stamp);
            ssb.setSpan(new RelativeSizeSpan(0.72f), text.length(), ssb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(mine ? COLOR_TIME_OUT : COLOR_TIME_IN),
                    text.length(), ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    private void removeBubble(TextView bubble) {
        try {
            LinearLayout row = (LinearLayout) bubble.getParent();
            messagesList.removeView(row);
        } catch (Throwable ignored) { }
    }

    private void scrollToBottom() {
        try {
            scroll.post(new Runnable() {
                @Override
                public void run() {
                    scroll.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------
    // Поле ввода и отправка
    // ------------------------------------------------------------------

    private View buildInputBar() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8));

        inputField = new EditText(this);
        inputField.setHint("Сообщение");
        inputField.setHintTextColor(0xFF6D7A85);
        inputField.setTextColor(Color.WHITE);
        inputField.setTextSize(16);
        inputField.setPadding(dp(14), dp(12), dp(14), dp(12));
        inputField.setImeOptions(EditorInfo.IME_ACTION_SEND);
        inputField.setMaxLines(6);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(COLOR_INPUT_BG);
        inputBg.setCornerRadius(dp(22));
        inputField.setBackground(inputBg);
        bottom.addView(inputField, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = new TextView(this);
        sendButton.setText("➤");
        sendButton.setTextColor(COLOR_SEND);
        sendButton.setTextSize(20);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.4f);
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setShape(GradientDrawable.OVAL);
        sendBg.setColor(COLOR_INPUT_BG);
        sendButton.setBackground(sendBg);
        bottom.addView(sendButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        return bottom;
    }

    private void wireInput() {
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
    }

    private void send() {
        final String text = inputField.getText().toString().trim();
        if (text.isEmpty() || generating) return;
        inputField.setText("");
        long now = System.currentTimeMillis();
        if (messagesList.getChildCount() > 0) {
            addDateChipIfNewDay(now);
        }
        addBubble(text, true, now, lastWasMine);
        setGenerating(true);

        assistantBubble = null;
        typingBubble = null;
        streamingText.setLength(0);

        NanoGramAi.getInstance(this).send(text, new NanoGramAi.Listener() {
            @Override
            public void onToken(String delta) {
                runOnUiThread(() -> {
                    try {
                        if (typingBubble != null) {
                            removeBubble(typingBubble);
                            typingBubble = null;
                        }
                        if (assistantBubble == null) {
                            streamingTime = System.currentTimeMillis();
                            assistantBubble = addBubble("", false, streamingTime, false);
                        }
                        streamingText.append(delta != null ? delta : "");
                        assistantBubble.setText(withTime(streamingText.toString(), false, streamingTime));
                        scrollToBottom();
                    } catch (Throwable ignored) { }
                });
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    try {
                        if (assistantBubble == null) {
                            String result = streamingText.length() > 0 ? streamingText.toString() : "(пустой ответ)";
                            addBubble(result, false, System.currentTimeMillis(), false);
                        }
                        setGenerating(false);
                    } catch (Throwable ignored) { }
                });
            }

            @Override
            public void onError(String message, boolean userCancelled) {
                runOnUiThread(() -> {
                    try {
                        if (userCancelled) {
                            addBubble("⏹ Остановлено", false, System.currentTimeMillis(), false);
                        } else {
                            addBubble("⚠ " + message, false, System.currentTimeMillis(), false);
                        }
                        setGenerating(false);
                    } catch (Throwable ignored) { }
                });
            }
        });

        showTyping();
    }

    private void showTyping() {
        if (generating) {
            typingBubble = addBubble("Думаю…", false, 0, false);
        }
    }

    private void setGenerating(boolean value) {
        generating = value;
        try {
            sendButton.setText(generating ? "⏹" : "➤");
            sendButton.setEnabled(true);
            sendButton.setAlpha(1f);
        } catch (Throwable ignored) { }
    }

    private void stopGenerating() {
        try {
            NanoGramAi.getInstance(this).cancel();
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    private long dayStart(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
