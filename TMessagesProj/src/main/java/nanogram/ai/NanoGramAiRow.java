package nanogram.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Строка "NanoGram AI" вверху списка диалогов.
 *
 * Самодостаточный класс на чистом Android UI (без зависимостей от классов Telegram-форка),
 * поэтому его добавление не может сломать сборку. Используется из NanoGramAiInit.
 *
 * UI v2 (по скриншотам пользователя): строка больше не сливается с обычными чатами
 * и архивом — она оформлена отдельной КАРТОЧКОЙ:
 *   - скруглённый тонированный фон (фирменный синий NanoGram) с отступами по бокам;
 *   - бейдж "AI" с двойной обводкой-градиентом;
 *   - стрелка "›" справа, как у рекламных строк Telegram;
 *   - текст названия с лёгким свечением, подзаголовок-описание.
 *
 * - Тап по строке открывает NanoGramAiActivity.
 * - Публичный API прежний: create(activity) и createWithDivider(activity).
 */
public class NanoGramAiRow {

    /** Создаёт строку-карточку "NanoGram AI"; тап открывает экран ИИ-чата. */
    public static View create(final Activity activity) {
        Context context = activity;

        // Внешний контейнер с боковыми отступами — карточка не прилипает к краям.
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 2));

        // Сама карточка: скруглённые углы + фирменный тёмно-синий тон.
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(0xFF16283C); // фирменный тон NanoGram, светлее фона списка
        cardBg.setCornerRadius(dp(context, 16));
        cardBg.setStroke(dp(context, 1), 0x332B5278); // тонкая подсвеченная рамка
        card.setBackground(cardBg);

        // Нормальная подсветка при нажатии — как у остальных строк списка.
        try {
            android.content.res.TypedArray ta = context.obtainStyledAttributes(
                    new int[]{android.R.attr.selectableItemBackground});
            card.setForeground(ta.getDrawable(0));
            ta.recycle();
        } catch (Throwable ignored) { }

        // --- Бейдж "AI" с обводкой-градиентом ---
        FrameLayout avatarWrap = new FrameLayout(context);

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColors(new int[]{0xFF5AA8E8, 0xFF2B5278});
        ring.setOrientation(GradientDrawable.Orientation.TL_BR);
        avatarWrap.setBackground(ring);

        TextView avatar = new TextView(context);
        avatar.setText("AI");
        avatar.setTextColor(Color.WHITE);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextSize(15);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(0xFF2B5278);
        avatar.setBackground(avatarBg);
        // Внутренний отступ бейджа от градиентной обводки — 3dp.
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                dp(context, 42), dp(context, 42), Gravity.CENTER);
        alp.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
        avatarWrap.addView(avatar, alp);

        card.addView(avatarWrap, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));

        // --- Текст: название + подзаголовок ---
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(context, 14), 0, 0, 0);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("NanoGram AI");
        title.setTextColor(0xFFE8F2FA); // чуть светлее обычных названий чатов
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(17);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Точка-индикатор "онлайн" рядом с названием.
        View onlineDot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFF4CD964);
        onlineDot.setBackground(dotBg);
        titleRow.addView(onlineDot, new LinearLayout.LayoutParams(dp(context, 7), dp(context, 7)));

        texts.addView(titleRow);

        TextView subtitle = new TextView(context);
        subtitle.setText("Локальный ИИ-ассистент · всегда под рукой");
        subtitle.setTextColor(0xFF6D7A85);
        subtitle.setTextSize(13);
        texts.addView(subtitle);

        card.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // --- Стрелка справа, как у выделенных строк Telegram ---
        TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextColor(0xFF5AA8E8);
        chevron.setTextSize(26);
        chevron.setTypeface(Typeface.DEFAULT_BOLD);
        chevron.setPadding(dp(context, 6), 0, 0, 0);
        card.addView(chevron, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // --- Тап: открыть экран ИИ-чата ---
        card.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, NanoGramAiActivity.class);
                activity.startActivity(intent);
            } catch (Exception ignored) { }
        });

        outer.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return outer;
    }

    /**
     * Контейнер с разделителем. Карточка сама визуально отделена от списка,
     * поэтому разделитель делаем полупрозрачным и коротким (не на всю ширину) —
     * просто аккуратная граница перехода к архиву/чатам.
     */
    public static View createWithDivider(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        container.addView(create(activity), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(activity);
        divider.setBackgroundColor(0x1AFFFFFF);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        dlp.gravity = Gravity.BOTTOM;
        dlp.leftMargin = dp(activity, 20);
        dlp.rightMargin = dp(activity, 20);
        divider.setLayoutParams(dlp);
        container.addView(divider);

        return container;
    }

    private static int dp(Context context, float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
