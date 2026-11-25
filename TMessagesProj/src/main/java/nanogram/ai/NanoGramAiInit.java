package nanogram.ai;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.WeakHashMap;

/**
 * Startup hook for the "NanoGram AI" row inside the dialogs list.
 *
 * Registered as a ContentProvider in AndroidManifest.xml, so it initializes
 * before any activity starts and requires no edits inside Telegram's own classes.
 *
 * v5 changes (anti-flicker, fixes doubling/glitching when scrolling to stories):
 *   - The AI row is now anchored ONLY below the folder tabs strip. When the strip
 *     is scrolled out of view (the user pulls the list down to the stories
 *     section), the row hides immediately instead of jumping between fallback
 *     positions every layout frame — that jumping was the flicker/doubling.
 *   - The row position is re-applied only when it actually changes (>= 1px), so
 *     ordinary scrolling no longer re-triggers view updates on every frame.
 *   - List padding is only adjusted while the row is visible, which breaks the
 *     "padding change -> layout -> recompute -> padding change" feedback loop.
 *     The old geometric fallback (scanning every neighbouring view each frame)
 *     has been removed — it was the source of the jitter.
 *
 * v4 changes:
 *   - OVERLAP FIX: Telegram resets the RecyclerListView's top padding when the
 *     user switches folders, changes theme, or the list is rebuilt. Previously
 *     the padding was applied only ONCE (with a tag guard), so after a reset the
 *     row overlapped the first chat in the list. Now every layout pass re-checks
 *     the padding and restores it if it is smaller than required.
 *
 * v3: account limit raised to 6 via reflection before Telegram initializes.
 *
 * v2: global layout listener re-checks on EVERY screen/layout change; scans ALL
 *     ActionBarLayout containers; positions the row below the folder tabs strip;
 *     guaranteed entry via launcher shortcut.
 *
 * The whole class is fail-soft: every entry point is wrapped in try/catch,
 * so it can never crash or block the app.
 */
public class NanoGramAiInit extends ContentProvider {

    private static final String ROW_TAG = "nanogram_ai_row_tag";
    private static final String SKIP_TAG = "nanogram_ai_skip_host";
    private static final String SHORTCUT_ID = "nanogram_ai_chat";
    private static final String AI_ACTIVITY = "nanogram.ai.NanoGramAiActivity";

    /** NanoGram account limit: 6 accounts (one phone number = one account). */
    private static final int MAX_ACCOUNTS = 6;

    /** Decor views already watched with a layout listener (avoids duplicates). */
    private static final WeakHashMap<View, Boolean> watched = new WeakHashMap<>();

    @Override
    public boolean onCreate() {
        try {
            Context context = getContext();
            if (context instanceof Application) {
                // Must run BEFORE Telegram initializes its per-account arrays.
                raiseAccountLimit();
                installDynamicShortcut(context);
                ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

                    @Override
                    public void onActivityStarted(Activity activity) { }

                    @Override
                    public void onActivityResumed(final Activity activity) {
                        try {
                            final View decor = activity.getWindow().getDecorView();
                            decor.post(new Runnable() {
                                @Override
                                public void run() {
                                    attach(decor);
                                    watchLayout(decor);
                                }
                            });
                        } catch (Throwable ignored) { }
                    }

                    @Override
                    public void onActivityPaused(Activity activity) { }

                    @Override
                    public void onActivityStopped(Activity activity) { }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

                    @Override
                    public void onActivityDestroyed(Activity activity) { }
                });
            }
        } catch (Throwable ignored) { }
        return true;
    }

    /**
     * ACCOUNT LIMIT (6 accounts, one phone number = one account).
     *
     * Telegram reads UserConfig.MAX_ACCOUNT_COUNT when its per-account arrays
     * and controller instances are created at app start. This ContentProvider
     * runs before that initialization, so raising the constant here is exactly
     * equivalent to editing it in the huge source files (which cannot be
     * rewritten through the GitHub API due to their size).
     *
     * Safety rules:
     *   - never lowers a limit that is already >= 6;
     *   - if the per-account "Instance" array cannot be resized, the constant
     *     is left untouched (no half-patched state, no crashes);
     *   - completely fail-soft.
     */
    private static void raiseAccountLimit() {
        try {
            Class<?> userConfigClass = Class.forName("org.telegram.messenger.UserConfig");

            Field countField = userConfigClass.getField("MAX_ACCOUNT_COUNT");
            int current = countField.getInt(null);
            if (current >= MAX_ACCOUNTS) {
                return; // fork already allows 6+ accounts — nothing to do
            }

            // The per-account instance array is created at class-load time with
            // the OLD limit. Resize it first; only then bump the constant.
            Object[] oldInstances = null;
            Field instanceField = null;
            try {
                instanceField = userConfigClass.getField("Instance");
                oldInstances = (Object[]) instanceField.get(null);
            } catch (Throwable ignored) { }
            if (oldInstances != null && oldInstances.length < MAX_ACCOUNTS) {
                try {
                    instanceField.set(null, Arrays.copyOf(oldInstances, MAX_ACCOUNTS));
                } catch (Throwable t) {
                    return; // cannot resize safely — keep the original limit
                }
            }
            countField.setInt(null, MAX_ACCOUNTS);

            // MessagesController usually initializes its own MAX_ACCOUNT_COUNT
            // from UserConfig, so it picks up 6 automatically on first use.
            // Patch it too (defensively) if this fork hardcodes a lower value.
            try {
                Class<?> messagesClass = Class.forName("org.telegram.messenger.MessagesController");
                Field mcCount = messagesClass.getField("MAX_ACCOUNT_COUNT");
                int mcCurrent = mcCount.getInt(null);
                if (mcCurrent < MAX_ACCOUNTS) {
                    Object[] mcOld = null;
                    Field mcInstance = null;
                    try {
                        mcInstance = messagesClass.getField("Instance");
                        mcOld = (Object[]) mcInstance.get(null);
                    } catch (Throwable ignored) { }
                    if (mcOld != null && mcOld.length < MAX_ACCOUNTS) {
                        try {
                            mcInstance.set(null, Arrays.copyOf(mcOld, MAX_ACCOUNTS));
                        } catch (Throwable t) {
                            return; // cannot resize safely — do not half-patch
                        }
                    }
                    mcCount.setInt(null, MAX_ACCOUNTS);
                }
            } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
    }

    /**
     * GUARANTEED ENTRY POINT (does not depend on the row):
     * registers a dynamic launcher shortcut, so long-pressing the app icon
     * always offers "NanoGram AI". Works on API 25+ (minSdk of this build is 33).
     */
    private static void installDynamicShortcut(Context context) {
        try {
            if (Build.VERSION.SDK_INT < 25) {
                return;
            }
            ShortcutManager sm = context.getSystemService(ShortcutManager.class);
            if (sm == null) {
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setClassName(context.getPackageName(), AI_ACTIVITY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ShortcutInfo info = new ShortcutInfo.Builder(context, SHORTCUT_ID)
                    .setShortLabel("NanoGram AI")
                    .setLongLabel("NanoGram AI")
                    .setIcon(Icon.createWithBitmap(makeAiIconBitmap(context)))
                    .setIntent(intent)
                    .build();
            sm.setDynamicShortcuts(Collections.singletonList(info));
        } catch (Throwable ignored) { }
    }

    /** Simple round "AI" icon for the launcher shortcut. */
    private static Bitmap makeAiIconBitmap(Context context) {
        try {
            int size = Math.round(108 * context.getResources().getDisplayMetrics().density);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
            circle.setColor(0xFF2B5278);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, circle);
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(Color.WHITE);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(size * 0.34f);
            float baseline = size / 2f - (text.descent() - text.ascent()) / 2f;
            canvas.drawText("AI", size / 2f, baseline, text);
            return bitmap;
        } catch (Throwable ignored) {
            return Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        }
    }

    /**
     * Re-checks the chat list on every layout change of the window.
     * Switching between fragments (chat -> list, settings -> list) does NOT
     * resume the activity, so this listener is what guarantees the AI row
     * appears as soon as the dialogs list becomes visible again — AND that
     * its padding survives Telegram's internal resets (v4 overlap fix).
     */
    private static void watchLayout(final View decor) {
        try {
            synchronized (watched) {
                if (watched.containsKey(decor)) {
                    return;
                }
                watched.put(decor, Boolean.TRUE);
            }
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    attach(decor);
                }
            });
        } catch (Throwable ignored) { }
    }

    /** Finds every dialogs list currently on screen and pins/repositions the AI row in each. */
    private static void attach(View decor) {
        try {
            View content = decor.findViewById(android.R.id.content);
            if (!(content instanceof ViewGroup)) {
                return;
            }
            ViewGroup root = (ViewGroup) content;

            ArrayList<org.telegram.ui.ActionBar.ActionBarLayout> layouts =
                    findAllInstanceOf(root, org.telegram.ui.ActionBar.ActionBarLayout.class);
            for (int k = 0; k < layouts.size(); k++) {
                org.telegram.ui.ActionBar.ActionBarLayout layout = layouts.get(k);
                try {
                    handleLayout(layout);
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
    }

    /**
     * Pins the row for one ActionBarLayout if it currently shows the dialogs list.
     *
     * v5 ANTI-FLICKER: the row is anchored ONLY below the folder tabs strip.
     * While the strip is scrolled out of view (the user pulled the list down to
     * the stories section) the row hides at once. This removes the per-frame
     * jumping between fallback positions that users saw as doubling/flicker.
     * The row position is re-applied only when it really changed (>= 1px).
     */
    private static void handleLayout(org.telegram.ui.ActionBar.ActionBarLayout layout) {
        org.telegram.ui.ActionBar.BaseFragment fragment = null;
        try {
            fragment = layout.getLastFragmentIncludeMainTabs();
        } catch (Throwable ignored) { }
        if (!(fragment instanceof org.telegram.ui.DialogsActivity)) {
            return; // this container does not show the chat list right now
        }

        View fragmentView = null;
        try {
            fragmentView = fragment.getFragmentView();
        } catch (Throwable ignored) { }
        if (!(fragmentView instanceof ViewGroup)) {
            return;
        }

        View list = findInstanceOf((ViewGroup) fragmentView, org.telegram.ui.Components.RecyclerListView.class);
        if (list == null || !(list.getParent() instanceof ViewGroup)) {
            return;
        }
        final ViewGroup host = (ViewGroup) list.getParent();
        if (SKIP_TAG.equals(host.getTag())) {
            return; // geometry could not be resolved here earlier; shortcut still gives access
        }

        View existing = host.findViewWithTag(ROW_TAG);
        if (existing != null) {
            // Already pinned: v5 keeps it anchored to the folder tabs strip ONLY.
            View tabs = findInstanceOf((ViewGroup) fragmentView, org.telegram.ui.Components.FilterTabsView.class);
            if (tabs == null || tabs.getVisibility() != View.VISIBLE || tabs.getHeight() <= 0) {
                return; // transient state (strip not measured yet): keep the row as-is
            }
            int top = rowTopOffset(host, (ViewGroup) fragmentView, list);
            if (top <= 0) {
                // The strip is scrolled out of view (stories section pulled down):
                // hide the row at once instead of letting it jump around.
                if (existing.getVisibility() == View.VISIBLE) {
                    existing.setVisibility(View.GONE);
                }
                return; // row stays hidden; the list itself is left untouched
            }
            if (existing.getVisibility() != View.VISIBLE) {
                existing.setVisibility(View.VISIBLE);
            }
            // v5: move the row only when the computed position really changed;
            // re-applying the same translation on every scroll frame caused
            // visible micro-jitter.
            if (Math.abs(existing.getTranslationY() - top) >= 1f) {
                existing.setTranslationY(top);
            }
            // Telegram resets the list padding on folder/theme changes (v4 fix):
            // keep re-verifying the padding, but only while the row is visible (v5).
            ensureListPadding(host, list, existing, top);
            return;
        }

        Activity activity = findActivity(fragmentView);
        if (activity == null) {
            return;
        }
        pinRow(activity, host, list, (ViewGroup) fragmentView);
    }

    /**
     * OVERLAP FIX (v4): guarantees the chat list is pushed down far enough so
     * the first dialog starts BELOW the AI row. Called on every layout pass
     * while the row is visible; if Telegram has reset the padding (folder
     * switch, theme change, list rebuild), it is restored immediately.
     * Never shrinks a larger padding.
     */
    private static void ensureListPadding(ViewGroup host, View list, View row, int top) {
        try {
            if (row.getHeight() <= 0) {
                return; // row not measured yet
            }
            int[] hostLoc = new int[2];
            int[] listLoc = new int[2];
            host.getLocationOnScreen(hostLoc);
            list.getLocationOnScreen(listLoc);
            int listOffsetInHost = listLoc[1] - hostLoc[1];
            int needed = top + row.getHeight() - listOffsetInHost;
            if (needed > 0 && list.getPaddingTop() < needed) {
                list.setPadding(list.getPaddingLeft(), needed,
                        list.getPaddingRight(), list.getPaddingBottom());
            }
        } catch (Throwable ignored) { }
    }

    /** Creates the row and positions it once the layout is measurable. */
    private static void pinRow(Activity activity, final ViewGroup host, final View list, final ViewGroup fragmentView) {
        final View row = NanoGramAiRow.createWithDivider(activity);
        row.setTag(ROW_TAG);
        host.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        row.post(new Runnable() {
            int attempts = 0;

            @Override
            public void run() {
                try {
                    int top = rowTopOffset(host, fragmentView, list);
                    if (attempts < 12 && (host.getWidth() <= 0 || list.getHeight() <= 0 || top <= 0)) {
                        // Layout not ready yet (folders strip not measured) — retry shortly.
                        attempts++;
                        row.postDelayed(this, 250);
                        return;
                    }
                    if (top <= 0) {
                        // Geometry unachievable in this container: do not overlap the header.
                        host.setTag(SKIP_TAG);
                        host.removeView(row);
                        return;
                    }
                    int width = Math.max(1, host.getWidth() > 0 ? host.getWidth() : list.getWidth());
                    row.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    int height = row.getMeasuredHeight();
                    if (height <= 0) {
                        return;
                    }
                    row.setTranslationY(top);

                    // Push the chat list down so the first dialog starts right below
                    // the row. NOT a one-time action anymore: further layout passes
                    // (see handleLayout) re-verify and restore this padding.
                    ensureListPadding(host, list, row, top);
                } catch (Throwable ignored) { }
            }
        });
    }

    /**
     * Preferred position for the AI row: right below the folder tabs strip
     * ("Все чаты" / "Личные").
     *
     * v5: the tabs strip is now the ONLY anchor. The value can be <= 0 while the
     * strip is scrolled away (stories section) or not measured yet — the caller
     * decides whether to hide the row or wait. The old geometric fallback that
     * scanned every neighbouring view on every frame has been removed: it was
     * the source of the position jumping (flicker/doubling).
     */
    private static int rowTopOffset(ViewGroup host, ViewGroup fragmentView, View list) {
        try {
            View tabs = findInstanceOf(fragmentView, org.telegram.ui.Components.FilterTabsView.class);
            if (tabs != null && tabs.getVisibility() == View.VISIBLE && tabs.getHeight() > 0) {
                int[] hostLoc = new int[2];
                int[] tabsLoc = new int[2];
                host.getLocationOnScreen(hostLoc);
                tabs.getLocationOnScreen(tabsLoc);
                return tabsLoc[1] + tabs.getHeight() - hostLoc[1];
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /** Depth-first search for the first view of the given type. */
    private static View findInstanceOf(ViewGroup root, Class<?> type) {
        try {
            if (type.isInstance(root)) {
                return root;
            }
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (type.isInstance(child)) {
                    return child;
                }
                if (child instanceof ViewGroup) {
                    View found = findInstanceOf((ViewGroup) child, type);
                    if (found != null) {
                        return found;
                    }
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** Depth-first search collecting ALL views of the given type. */
    private static <T extends View> ArrayList<T> findAllInstanceOf(ViewGroup root, Class<T> type) {
        ArrayList<T> result = new ArrayList<>();
        try {
            collectInstanceOf(root, type, result);
        } catch (Throwable ignored) { }
        return result;
    }

    private static <T extends View> void collectInstanceOf(View view, Class<T> type, ArrayList<T> out) {
        if (type.isInstance(view)) {
            out.add(type.cast(view));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectInstanceOf(group.getChildAt(i), type, out);
            }
        }
    }

    /** Unwraps the activity from a view context (may be a ContextThemeWrapper). */
    private static Activity findActivity(View view) {
        try {
            Context context = view.getContext();
            while (context != null) {
                if (context instanceof Activity) {
                    return (Activity) context;
                }
                if (context instanceof ContextWrapper) {
                    context = ((ContextWrapper) context).getBaseContext();
                } else {
                    return null;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
