package nanogram.ai;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
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
 * - Обои-градиент вместо плоского тёмного фона.
 * - Пузыри как ChatMessageCell: радиусы 17.5dp/6dp, «хвостик» у последнего в группе,
 *   цвет пользователя — фирменный синий NanoGram (0xFF2B5278), ИИ — тёмный (0xFF1F2A38).
 * - Время «14:52» мелким шрифтом внутри пузыря, справа внизу.
 * - Сообщения одной стороны подряд — группируются (меньше отступ, без хвоста у всех, кроме последнего).
 * - Плашка-разделитель дат («Сегодня», «Вчера», дата) по центру, как в Telegram.
 * - Панель ввода как ChatActivityEnterView: скруглённое поле + круглая кнопка отправки.
 */
public class NanoGramAiActivity extends Activity {

    // --- Палитра NanoGram (из тёмной темы Telegram/NanoGram) ---
    private static final int COLOR_BG_TOP = 0xFF0E1621;      // фон-градиент
    private static final int COLOR_BG_BOTTOM = 0xFF17212B;
    private static final int COLOR_IN_BUBBLE = 0xFF1F2A38;   // пузырь ИИ
    private static final int COLOR_OUT_BUBBLE = 0xFF2B5278; // пузырь пользователя — синий NanoGram
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TIME = 0xFF7D8B99;        // время в пузыре
    private static final int COLOR_DATE_CHIP = 0xCC1C2733;   // плашка даты
    private static final int COLOR_DIVIDER = 0xFF101921;     // линия шапки
    private static final int COLOR_INPUT_BG = 0xFF232E3C;    // поле ввода
    private static final int COLOR_SEND = 0xFF5288C1;        // кнопка отправки — синий NanoGram
    private static final int COLOR_SEND_OFF = 0x445288C1;
    private static final int COLOR_STOP = 0xFFE06060;

    private LinearLayout messagesList;
    private ScrollView scroll;
    private EditText inputField;
    private TextView sendButton;
    private TextView assistantBubble;   // пузырь текущего ответа (стриминг)
    private LinearLayout assistantRow;
    private TextView typingBubble;
    private StringBuilder streamingText = new StringBuilder();
    private volatile boolean generating = false;

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
        subtitle.setTextColor(0xFF7D8B99);
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
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8) + getNavBarHeight());
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_DIVIDER, COLOR_BG_BOTTOM}));

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
        sendButton.setTextSize(22);
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.4f);
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setShape(GradientDrawable.OVAL);
        sendBg.setColor(0xFF232E3C);
        sendButton.setBackground(sendBg);
        bottom.addView(sendButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        return bottom;
    }

    private int getNavBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? Math.round(getResources().getDimensionPixelSize(id) * 0.5f) : 0;
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

    /** Показывает сохранённую историю (после прошлого запуска), с плашками дат и временем. */
    private void renderHistory() {
        List<NanoGramAi.Message> history = NanoGramAi.getInstance(this).getHistory();
        long lastDay = -1;
        for (NanoGramAi.Message m : history) {
            long day = dayKey(m.time);
            if (day != lastDay) {
                addDateChip(dateLabel(m.time));
                lastDay = day;
            }
            addBubble(m.content, m.role.equals("user"), m.time);
        }
        if (history.isEmpty()) {
            addBubble("Привет! Я NanoGram AI — спроси меня о чём угодно.", false, 0);
        }
        scrollToBottom();
    }

    // ================= Отправка =================

    private void send() {
        String text = inputField.getText().toString().trim();
        if (text.isEmpty() || generating) return;
        inputField.setText("");
        addBubble(text, true, System.currentTimeMillis());
        setGenerating(true);

        assistantBubble = null;
        assistantRow = null;
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
                        TextView[] pair = addBubbleWithRow("", false, System.currentTimeMillis(), true);
                        assistantBubble = pair[0];
                        assistantRow = pair[1];
                    }
                    streamingText.append(delta);
                    assistantBubble.setText(withTime(streamingText.toString(), true));
                    scrollToBottom();
                });
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (assistantBubble == null) {
                        addBubble(streamingText.length() > 0 ? streamingText.toString() : "(пустой ответ)", false, System.currentTimeMillis());
                    }
                    setGenerating(false);
                });
            }

            @ лямбда onError была ниже — поправьте при сборке.
        });
    }
}
