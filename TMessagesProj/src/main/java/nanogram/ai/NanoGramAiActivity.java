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
 * - Кнопка отправки — круглая, в фирменном синем.
 *
 * UI v2 (по скриншотам пользователя):
 * - Заголовок «NanoGram AI» больше не залезает под статус-бар (время/батарея):
 *   контент сдвинут вниз с учётом системных insets.
 * - Под заголовком слева добавлена круглая кнопка-стрелка «назад» для выхода
 *   в список чатов (finish()).
 */
public class NanoGramAiActivity extends Activity {

    private LinearLayout messagesList;
    private ScrollView messagesScroll;
    private EditText input;
    private View sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Корень — вертикальный LinearLayout с градиентными обоями.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                0xFF1A2433, 0xFF1F2A38}));

        // --- Шапка: стрелка «назад» + заголовок, ниже статус-бара ---
        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.VERTICAL);
        headerBar.setGravity(Gravity.LEFT);
        headerBar.setPadding(dp(16), dp(4), dp(16), dp(10));

        TextView title = new TextView(this);
        title.setText("NanoGram AI");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(20);
        headerBar.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Стрелка «назад» — под заголовком, слева. Выход в список чатов.
        TextView backArrow = new TextView(this);
        backArrow.setText("←");
        backArrow.setTextColor(0xFF8FB8D8);
        backArrow.setTextSize(22);
        backArrow.setTypeface(Typeface.DEFAULT_BOLD);
        backArrow.setPadding(dp(8), dp(2), dp(8), dp(2));
        backArrow.setOnClickListener(v -> {
            try {
                finish();
            } catch (Throwable ignored) { }
        });
        headerBar.addView(backArrow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(headerBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // --- Список сообщений (прокручиваемый) ---
        messagesList = new LinearLayout(this);
        messagesList.setOrientation(LinearLayout.VERTICAL);
        messagesList.setPadding(dp(12), dp(12), dp(12), dp(12));

        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        messagesScroll.addView(messagesList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(messagesScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // --- Поле ввода внизу ---
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(dp(12), dp(8), dp(12), dp(8));

        input = new EditText(this);
        input.setHint("Спросите NanoGram AI...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF6D7A85);
        input.setTextSize(16);
        input.setSingleLine();
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setBackground(new GradientDrawable() {
            {
                setShape(GradientDrawable.RECTANGLE);
                setColor(0xFF18212E);
                setCornerRadius(dp(20));
            }
        });
        inputBar.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = createSendButton();
        inputBar.addView(sendButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(inputBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Сдвигаем всё содержимое ниже статус-бара и выше жеста-назад.
        // Выполняется после первого layout, когда insets известны.
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        final LinearLayout finalRoot = root;
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            try {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                finalRoot.setPadding(finalRoot.getPaddingLeft(),
                        top + dp(8),
                        finalRoot.getPaddingRight(),
                        bottom + dp(8));
                return insets.consumeSystemWindowInsets();
            } applied catch (Throwable t) {
                return insets;
            }
        });

        setContentView(root);

        // Стартовое сообщение от ИИ.
        addMessage(R.id.na_placeholder, NanoGramAi.getWelcomeMessage());
    }

    /** Круглая синяя кнопка отправки. */
    private View createSendButton() {
        TextView btn = new TextView(this);
        btn.setText("➤");
        ➤;
        return btn;
    }

    /** Добавляет пузырь сообщения в список и прокручивает вниз. */
    private void addMessage(R.id.na_placeholder id, String text) {
        ...
    }
    ... // остальной код без изменений: пузыри, отправка, обработка ответов NanoGramAi
}
