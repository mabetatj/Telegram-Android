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
 * РЎС‚СЂРѕРєР° "NanoGram AI" РІРІРµСЂС…Сѓ СЃРїРёСЃРєР° РґРёР°Р»РѕРіРѕРІ.
 *
 * РњРѕРґСѓР»СЊ 3: СЃР°РјРѕРґРѕСЃС‚Р°С‚РѕС‡РЅС‹Р№ РєР»Р°СЃСЃ РЅР° С‡РёСЃС‚РѕРј Android UI (Р±РµР· Р·Р°РІРёСЃРёРјРѕСЃС‚РµР№ РѕС‚ РєР»Р°СЃСЃРѕРІ Telegram-С„РѕСЂРєР°),
 * РїРѕСЌС‚РѕРјСѓ РµРіРѕ РґРѕР±Р°РІР»РµРЅРёРµ РЅРµ РјРѕР¶РµС‚ СЃР»РѕРјР°С‚СЊ СЃР±РѕСЂРєСѓ. РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РёР· DialogsActivity (РњРѕРґСѓР»СЊ 4).
 *
 * - РљСЂСѓРіР»Р°СЏ РёРєРѕРЅРєР° "AI" + РЅР°Р·РІР°РЅРёРµ "NanoGram AI" + РїРѕРґР·Р°РіРѕР»РѕРІРѕРє.
 * - РўР°Рї РїРѕ СЃС‚СЂРѕРєРµ РѕС‚РєСЂС‹РІР°РµС‚ NanoGramAiActivity.
 * - Р•РґРёРЅСЃС‚РІРµРЅРЅС‹Р№ РїСѓР±Р»РёС‡РЅС‹Р№ РјРµС‚РѕРґ: NanoGramAiRow.create(activity) вЂ” РІРѕР·РІСЂР°С‰Р°РµС‚ РіРѕС‚РѕРІСѓСЋ View.
 */
public class NanoGramAiRow {

    /** РЎРѕР·РґР°С‘С‚ СЃС‚СЂРѕРєСѓ "NanoGram AI"; РІРµС€Р°РµС‚ РЅР° РЅРµС‘ РѕР±СЂР°Р±РѕС‚С‡РёРє РѕС‚РєСЂС‹С‚РёСЏ СЌРєСЂР°РЅР° С‡Р°С‚Р°. */
    public static View create(final Activity activity) {
        Context context = activity;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        row.setLayoutParams(new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- РљСЂСѓРіР»Р°СЏ РёРєРѕРЅРєР° "AI" ---
        TextView avatar = new TextView(context);
        avatar.setText("AI");
        avatar.setTextColor(Color.WHITE);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextSize(16);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(0xFF2B5278); // СЃРёРЅРёР№ NanoGram
        avatar.setBackground(avatarBg);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(context, 46), dp(context, 46)));

        // --- РўРµРєСЃС‚: РЅР°Р·РІР°РЅРёРµ + РїРѕРґР·Р°РіРѕР»РѕРІРѕРє ---
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
        subtitle.setText("Р›РѕРєР°Р»СЊРЅС‹Р№ РР-Р°СЃСЃРёСЃС‚РµРЅС‚");
        subtitle.setTextColor(0xFF6D7A85);
        subtitle.setTextSize(14);
        texts.addView(subtitle);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // --- РўР°Рї: РѕС‚РєСЂС‹С‚СЊ СЌРєСЂР°РЅ РР-С‡Р°С‚Р° ---
        row.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, NanoGramAiActivity.class);
                activity.startActivity(intent);
            } catch (Exception ignored) { }
        });

        return row;
    }

    /** РљРѕРЅС‚РµР№РЅРµСЂ СЃ РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅРѕР№ РїРѕР»РѕСЃРєРѕР№-СЂР°Р·РґРµР»РёС‚РµР»РµРј вЂ” СѓРґРѕР±РЅРѕ РІСЃС‚Р°РІРёС‚СЊ РїРµСЂРІРѕР№ СЃС‚СЂРѕРєРѕР№ РІ СЃРїРёСЃРѕРє. */
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