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
 * Остаётся самодостаточным (чистый Android UI, без классов Telegram-форка), поэтому
 * не может сломать сборку, но копирует внешний вид настоящего чата:
 *
 * - Обои-градиент вместо плоского фона.
 * - Пузыри как ChatMessageCell: крупные радиусы у трёх углов и маленький
 *   у «хвостового» угла со стороны отправителя, цвет пользователя — фирменный
 *   синий NanoGram (0xFF2B5278), ИИ — тёмный (0xFF1F2A38).
 * - Время «14:52» мелким шрифтом внутри пузыря.
 * - Сообщения одной стороны подряд группируются (плотнее, с общим контуром группы).
 * - Плашка-разделитель дат («Сегодня», «Вчера», дата) по центру, как в Telegram.
 * - Панель ввода как ChatActivityEnterView: скруглённое поле + круглая кнопка.
 */
public class NanoGramAiActivity extends Activity {

    // --- Палитра NanoGram (тёмная тема) ---
    private static final int COLOR_BG_TOP = 0xFF0E1621;
    private static final int COLOR_BG_BOTTOM = 0xFF17212B;
    private static final int COLOR_IN_BUBBLE = 0xFF1F2A38;
    private static final int COLOR_OUT_BUBBLE = 0xFF2B5278;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TIME_IN = 0xFF7D8B99;
    private static final int COLOR_TIME_OUT = 0xFFA2C4E0;
    private static final int COLOR_DATE_CHIP = 0xCC1C2733;
    private static final int COLOR_DATE_TEXT = 0xFFB8C7D2;
    private static final int COLOR_DIVIDER = 0xFF101921;
    private static final int COLOR_INPUT_BG = 0xFF232E3C;
    private static final int COLOR_SEND = 0xFF5288C1;
    private static final int COLOR_STOP = 0xFFE06060;
    private static final int COLOR_SUBTITLE = 0xFF7D8B99;

    private LinearLayout messagesList;
    private ScrollView scroll;
    private EditText inputField;
    private TextView sendButton;
    private TextView assistantBubble;   // пузырь текущего ответа (стриминг)
    private TextView typingBubble;      // «Думаю…» до первого токена
    private StringBuilder streamingText = new StringBuilder();
    private volatile boolean generating = false;
    private boolean lastWasMine = false; // для группировки подряд идущих сообщений

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM}));

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
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

        root.addView(buildInputBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        wireInput();
        renderHistory();
    }

    // ================= Шапка =================

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("NanoGram AI");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(20);
        title.setPadding(dp(20), dp(14), dp(20), dp(4));

        TextView subtitle = new TextView(this);
        subtitle.setText("локальный ИИ-ассистент");
        subtitle.setTextColor(COLOR_SUBTITLE);
        subtitle.setTextSize(13);
        subtitle.setPadding(dp(20), 0, dp(20), dp(12));

        header.addView(title);
        header.addView(subtitle);

        View line = new View(this);
        line.setBackgroundColor(COLOR_DIVIDER);
        header.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return header;
    }

    // ================= Панель ввода (как ChatActivityEnterView) =================

    private View buildInputBar() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8));

        inputField = new EditText(this);
        inputField.setHint("Сообщение");
        inputField.setHintTextColor(0xFF6D7A85);
        inputField.setTextColor(Color.WHITE);
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

    // ================= История =================

    /** Показывает сохранённую историю с плашками дат и временем в пузырях. */
    private void renderHistory() {
        List<NanoGramAi.Message> history = NanoGramAi.getInstance(this).getHistory();
        long lastDay = Long.MIN_VALUE;
        boolean prevMine = false;
        for (NanoGramAi.Message m : history) {
            long day = dayKey(m.time);
            if (day != lastDay) {
                addDateChip(dateLabel(m.time));
                lastDay = day;
                prevMine = false;
            }
            boolean mine = m.role.equals("user");
            addBubble(m.content, mine, m.time, prevMine);
            prevMine = mine;
        }
        if (history.isEmpty()) {
            addBubble("Привет! Я NanoGram AI — спроси меня о чём угодно.", false, 0, false);
        }
        scrollToBottom();
    }

    // ================= Отправка =================

    private void send() {
        String text = inputField.getText().toString().trim();
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
                    if (typingBubble != null) {
                        removeBubble(typingBubble);
                        typingBubble = null;
                    }
                    if (assistantBubble == null) {
                        assistantBubble = addBubble("", false, System.currentTimeMillis(), false);
                    }
                    streamingText.append(delta);
                    assistantBubble.setText(withTime(streamingText.toString(), false));
                    scrollToBottom();
                });
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (assistantBubble == null) {
                        String result = streamingText.length() > 0 ? streamingText.toString() : "(пустой ответ)";
                        addBubble(result, false, System.currentTimeMillis(), false);
                    }
                    setGenerating(false);
                });
            }

            @Override
            public void onError(String message, boolean userCancelled) {
                runOnUiThread(() -> {
                    if (userCancelled) {
                        addBubble("⏹ Остановлено", false, System.currentTimeMillis(), false);
                    } else {
                        addBubble("⚠ " + message, false, System.currentTimeMillis(), false);
                    }
                    setGenerating(false);
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

    private void stopGenerating() {
        NanoGramAi.getInstance(this).cancel();
        setGenerating(false);
    }

    private void setGenerating(boolean on) {
        generating = on;
        sendButton.setText(on ? "⏹" : "➤");
        sendButton.setEnabled(on || inputField.length() > 0);
        sendButton.setAlpha(sendButton.isEnabled() ? 1f : 0.4f);
        sendButton.setTextColor(on ? COLOR_STOP : COLOR_SEND);
    }

    // ================= Пузыри =================

    /**
     * Добавляет пузырь в стиле ChatMessageCell:
     * крупные радиусы у трёх углов и маленький (4dp) у «хвостового» угла
     * со стороны отправителя. Время — мелким текстом внутри пузыря.
     */
    private TextView addBubble(String text, boolean mine, long time, boolean sameAuthorAsPrevious) {
        TextView bubble = new TextView(this);
        bubble.setTextSize(16);
        bubble.setTextColor(COLOR_TEXT);
        bubble.setLineSpacing(dp(2), 1f);
        bubble.setPadding(dp(12), dp(7), dp(12), dp(7));
        bubble.setText(withTime(text, mine, time));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mine ? COLOR_OUT_BUBBLE : COLOR_IN_BUBBLE);
        float r = dp(17.5f);
        float tail = dp(4);
        // Порядок углов: левый-верх, правый-верх, правый-низ, левый-низ.
        // Хвостик: у пользователя — правый-низ, у ИИ — левый-низ.
        if (mine) {
            bg.setCornerRadii(new float[]{r, r, r, r, tail, tail, r, r});
        } else {
            bg.setCornerRadii(new float[]{r, r, r, r, r, r, tail, tail});
        }
        bubble.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        // Внутри группы сообщения стоят плотнее; после чужого — отступ побольше.
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

    /** Убирает пузырь из ленты (используется для «Думаю…»). */
    private void removeBubble(TextView bubble) {
        View parent = (View) bubble.getParent();
        if (parent instanceof LinearLayout) {
            messagesList.removeView(parent);
        }
    }

    /** Текст пузыря + время мелким шрифтом в конце, как в Telegram. */
    private CharSequence withTime(String text, boolean mine, long time) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(text != null ? text : "");
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

    // ================= Даты =================

    /** Плашка-разделитель по центру ленты («Сегодня», «Вчера», «12 ноября»). */
    private void addDateChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setTextColor(COLOR_DATE_TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_DATE_CHIP);
        bg.setCornerRadius(dp(13));
        chip.setBackground(bg);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(6), 0, dp(6));
        row.addView(chip);
        messagesList.addView(row);
        lastWasMine = false;
    }

    /** Добавляет плашку даты, если сообщение принадлежит новому дню. */
    private void addDateChipIfNewDay(long time) {
        List<NanoGramAi.Message> history = NanoGramAi.getInstance(this).getHistory();
        long prev = history.size() >= 2 ? history.get(history.size() - 2).time : Long.MIN_VALUE;
        if (dayKey(prev) != dayKey(time)) {
            addDateChip(dateLabel(time));
        }
    }

    private long dayKey(long time) {
        if (time <= 0) return Long.MIN_VALUE;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String dateLabel(long time) {
        if (time <= 0) return "";
        Calendar today = Calendar.getInstance();
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(time);
        if (dayKey(time) == dayKey(today.getTimeInMillis())) return "Сегодня";
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (dayKey(time) == dayKey(today.getTimeInMillis())) return "Вчера";
        return new SimpleDateFormat("d MMMM", Locale.getDefault()).format(new Date(time));
    }

    // ================= Прочее =================

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
