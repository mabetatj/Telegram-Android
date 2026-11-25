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
 * The "NanoGram AI" row at the top of the dialogs list.
 *
 * Self-contained class on plain Android UI (no dependencies on Telegram fork
 * classes), so adding it cannot break the build.
 *
 * - Round "AI" icon + "NanoGram AI" title + subtitle.
 * - Tapping the row opens NanoGramAiActivity.
 * - Only public entry point: NanoGramAiRow.create(activity) - returns a ready View.
 *
 * All user-visible strings are plain English ASCII so no encoding issue can garble them.
 */
public class NanoGramAiRow {

    /** Creates the "NanoGram AI" row and attaches the chat screen open handler. */
    public static View create(final Activity activity) {
        Context context = activity;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        row.setLayoutParams(new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- Round "AI" icon ---
        TextView avatar = new TextView(context);
        avatar.setText("AI");
        avatar.setTextColor(Color.WHITE);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextSize(16);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(0xFF2B5278); // NanoGram blue
        avatar.setBackground(avatarBg);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(context, 46), dp(context, 46)));

        // --- Text: title + subtitle ---
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
        subtitle.setText("Your local AI assistant");
        subtitle.setTextColor(0xFF6D7A85);
        subtitle.setTextSize(14);
        texts.addView(subtitle);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // --- Tap: open the AI chat screen ---
        row.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, NanoGramAiActivity.class);
                activity.startActivity(intent);
            } catch (Exception ignored) { }
        });

        return row;
    }

    /** Container with a bottom divider - easy to insert as the first row of a list. */
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
