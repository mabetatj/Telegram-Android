package nanogram.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
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
 * - Круглая иконка "AI" + название "NanoGram AI" + подзаголовок.
 * - Тап по строке открывает NanoGramAiActivity.
 * - Единственный публичный метод: NanoGramAiRow.create(activity) — возвращает готовую View.
 */
public class NanoGramAiRow {

    /** Создаёт строку "NanoGram AI"; вешает на неё обработчик открытия экрана ИИ-чата. */
    public static View create(final Activity activity) {
        Context context = activity;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));

        // Нормальная подсветка при нажатии — как у остальных строк списка.
        try {
            TypedArray ta = context.obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
            row.setBackground(ta.getDrawable(0));
            ta.recycle();
        } catch (Throwable ignored) { }

        // --- Круглая иконка "AI" ---
        TextView avatar = new TextView(context);
        avatar.setText("AI");
        avatar.setTextColor(Color.WHITE);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextSize(16);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(0xFF2B5278); // синий NanoGram
        avatar.setBackground(avatarBg);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(context, 46), dp(context, 46)));

        // --- Текст: название + подзаголовок ---
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(context, 14), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText("NanoGram AI");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(17);
        texts.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("Локальный ИИ-ассистент");
        subtitle.setTextColor(0xFF6D7A85);
        subtitle.setTextSize(14);
        texts.addView(subtitle);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // --- Тап: открыть экран ИИ-чата ---
        row.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, NanoGramAiActivity.class);
                activity.startActivity(intent);
            } catch (Exception ignored) { }
        });

        return row;
    }

    /** Контейнер с горизонтальной полоской-разделителем — удобно вставить первой строкой в список. */
    public static View createWithDivider(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        container.addView(create(activity), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(activity);
        divider.setBackgroundColor(0xFF101921);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(activity, 1));
        dlp.gravity = Gravity.BOTTOM;
        divider.setLayoutParams(dlp);
        container.addView(divider);

        return container;
    }

    private static int dp(Context context, float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
