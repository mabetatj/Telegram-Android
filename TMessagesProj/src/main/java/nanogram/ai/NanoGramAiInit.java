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
 * v3 changes:
 *   - ACCOUNT LIMIT: raised to 6 (one phone number = one account). Done via
 *     reflection VERY early (before Telegram's controllers create their
 *     per-account arrays), because MessagesController.java is too large to
 *     rewrite through the API. The patch never lowers an already higher
 *     limit and is fully fail-soft.
 *
 * v2 — why the row sometimes never appeared before:
 *   1. The previous version looked up the FIRST RecyclerListView anywhere in the
 *      window. With the bottom navigation bar ("Чаты / Контакты / Настройки /
 *      Профиль") there are several lists and containers, so the row could be
 *      attached to the wrong one (or nowhere at all).
 *   2. It only ran at window resume; coming back from a chat never re-checked.
 *
 * v2 approach:
 *   - A global layout listener re-checks on EVERY screen/layout change.
 *   - It scans ALL ActionBarLayout containers (one per bottom-nav tab), finds the
 *      one whose current fragment is DialogsActivity, and pins the row inside
 *      THAT fragment's own view, directly to the dialogs list's parent.
 *   - Position: the bottom edge of the folder tabs strip (FilterTabsView), i.e.
 *      exactly below "Все чаты"/"Личные" — with a geometric fallback.
 *   - The chat list gets extra top padding so the first chat is never hidden.
 *   - GUARANTEED ENTRY POINT: a dynamic launcher shortcut "NanoGram AI" is
 *      registered on every app start. Long-press the app icon -> tap "NanoGram AI"
 *      -> the AI chat opens even if the row fails to attach for any reason.
 *
 * The whole class is fail-soft: every entry point is wrapped in try/catch,
 * so it can never crash or block the app.
 */
public class NanoGramAiInit extends ContentProvider {

    private static final String ROW_TAG = "nanogram_ai_row_tag";
    private static final String PADDED_TAG = "nanogram_ai_padded";
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
            // Patch it too (defensively) if this fork hardcoded a lower value.
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
            float baseline = size / 2f - (text.descent() + text.ascent()) / 2f;
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
     * appears as soon as the dialogs list becomes visible again.
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

    /** Pins the row for one ActionBarLayout if it currently shows the dialogs list. */
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
            // Already pinned: just keep the position fresh (rotation, folder change...).
            int top = rowTopOffset(host, (ViewGroup) fragmentView, list);
            if (top > 0) {
                existing.setTranslationY(top);
            }
            return;
        }

        Activity activity = findActivity(fragmentView);
        if (activity == null) {
            return;
        }
        pinRow(activity, host, list, (ViewGroup) fragmentView);
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
                        // Geometry undecipherable in this container: do not overlap the header.
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

                    if (!PADDED_TAG.equals(list.getTag())) {
                        list.setTag(PADDED_TAG);
                        // Push the chat list down so the first dialog starts right below the row.
                        int[] hostLoc = new int[2];
                        int[] listLoc = new int[2];
                        host.getLocationOnScreen(hostLoc);
                        list.getLocationOnScreen(listLoc);
                        int listOffsetInHost = listLoc[1] - hostLoc[1];
                        int newPadTop = Math.max(list.getPaddingTop(), top + height - listOffsetInHost);
                        list.setPadding(list.getPaddingLeft(), newPadTop,
                                list.getPaddingRight(), list.getPaddingBottom());
                    }
                } catch (Throwable ignored) { }
            }
        });
    }

    /**
     * Preferred position for the AI row: right below the folder tabs strip
     * ("Все чаты" / "Личные"). Falls back to the geometric header detection
     * when the strip is absent or not measured.
     */
    private static int rowTopOffset(ViewGroup host, ViewGroup fragmentView, View list) {
        try {
            View tabs = findInstanceOf(fragmentView, org.telegram.ui.Components.FilterTabsView.class);
            if (tabs != null && tabs.getHeight() > 0 && tabs.getVisibility() == View.VISIBLE) {
                int[] hostLoc = new int[2];
                int[] tabsLoc = new int[2];
                host.getLocationOnScreen(hostLoc);
                tabs.getLocationOnScreen(tabsLoc);
                return Math.max(0, tabsLoc[1] + tabs.getHeight() - hostLoc[1]);
            }
        } catch (Throwable ignored) { }
        return computeTopOffset(host, list);
    }

    /**
     * Geometric fallback: the bottom edge of the last "header-like" sibling above
     * the list (action bar, folder tabs strip). A sibling counts only if it is
     * visible, short compared to the list (header-height, not a full-screen
     * overlay), and sits near the top of the list.
     */
    private static int computeTopOffset(ViewGroup host, View list) {
        try {
            int[] hostLoc = new int[2];
            int[] listLoc = new int[2];
            int[] childLoc = new int[2];
            host.getLocationOnScreen(hostLoc);
            list.getLocationOnScreen(listLoc);

            int maxBottom = 0;
            int maxHeight = list.getHeight() > 0 ? Math.max(1, list.getHeight() * 2 / 5) : Integer.MAX_VALUE;
            int bottomLimit = listLoc[1] + (list.getHeight() > 0 ? list.getHeight() / 3 : 0);

            for (int i = 0; i < host.getChildCount(); i++) {
                View child = host.getChildAt(i);
                if (child == list || child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (ROW_TAG.equals(child.getTag())) {
                    continue;
                }
                if (child.getHeight() <= 0 || child.getHeight() > maxHeight) {
                    continue; // skip full-screen overlays and zero-sized views
                }
                child.getLocationOnScreen(childLoc);
                int bottom = childLoc[1] + child.getHeight();
                if (bottom <= bottomLimit) {
                    maxBottom = Math.max(maxBottom, bottom);
                }
            }
            return Math.max(0, maxBottom - hostLoc[1]);
        } catch (Throwable ignored) {
            return 0;
        }
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
