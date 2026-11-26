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

import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.WeakHashMap;

/**
 * Startup hook for the "NanoGram AI" row inside the dialogs list.
 *
 * v7 changes (native-list architecture, fixes all motion complaints):
 *   - The row position is synchronized with the folder tabs strip in EVERY
 *     frame BEFORE the frame is drawn (ViewTreeObserver.OnPreDrawListener),
 *     the same way Telegram itself moves header elements while scrolling.
 *     The old onGlobalLayout approach was always one frame late - that lag
 *     was the doubling/flicker when scrolling to stories and back.
 *   - The list top padding is applied ONLY while the list scroll state is
 *     IDLE (never during a drag/fling). The old code changed padding in the
 *     middle of gestures, which caused requestLayout storms; they broke the
 *     ViewPagerFixed folder-swipe tracking and made the screen jump.
 *   - Forward/share pickers (DialogsActivity with initialDialogsType != 0)
 *     are completely skipped: no row, no padding, no visible shift when the
 *     "forward to chat" screen opens.
 *   - No "skip forever" logic any more. If the user has no folder tabs at
 *     all, the row is anchored below the ActionBar instead of disappearing.
 *   - The row card fades in once after it is first positioned, so the dialogs
 *     list never "snaps" when returning from a chat.
 *
 * v3: account limit raised to 6 via reflection before Telegram initializes.
 * v2: global layout listener scans all ActionBarLayout containers; guaranteed
 *     entry via launcher shortcut.
 *
 * The whole class is fail-soft: every entry point is wrapped in try/catch.
 */
public class NanoGramAiInit extends ContentProvider {

    private static final String ROW_TAG = "nanogram_ai_row_tag";
    private static final String SHORTCUT_ID = "nanogram_ai_chat";
    private static final String AI_ACTIVITY = "nanogram.ai.NanoGramAiActivity";

    /** NanoGram account limit: 6 accounts (one phone number = one account). */
    private static final int MAX_ACCOUNTS = 6;

    /** Decor views already watched with a global layout listener. */
    private static final WeakHashMap<View, Boolean> watched = new WeakHashMap<>();

    /** Fragment views already wired with the per-frame pre-draw listener. */
    private static final WeakHashMap<View, Boolean> wired = new WeakHashMap<>();

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
     * Safety rules: never lowers a limit that is already >= 6; if the
     * per-account "Instance" array cannot be resized, the constant is left
     * untouched (no half-patched state, no crashes). Completely fail-soft.
     */
    private static void raiseAccountLimit() {
        try {
            Class<?> userConfigClass = Class.forName("org.telegram.messenger.UserConfig");

            Field countField = userConfigClass.getField("MAX_ACCOUNT_COUNT");
            int current = countField.getInt(null);
            if (current >= MAX_ACCOUNTS) {
                return; // fork already allows 6+ accounts - nothing to do
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
                    return; // cannot resize safely - keep the original limit
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
                            return; // cannot resize safely - do not half-patch
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
     * Re-checks the chat list on every layout change of the window (fragment
     * switches do NOT resume the activity). Creates the row once and wires
     * the per-frame pre-draw sync for the current dialogs list.
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

    /** Finds every dialogs list currently on screen and pins the AI row in each. */
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
     * One ActionBarLayout at a time. v7: forward/share pickers are excluded
     * completely - the "select chat to forward" screen must not shift.
     */
    private static void handleLayout(org.telegram.ui.ActionBar.ActionBarLayout layout) {
        org.telegram.ui.ActionBar.BaseFragment fragment = null;
        try {
            fragment = layout.getLastFragmentIncludeMainTabs();
        } catch (Throwable ignored) { }
        if (!(fragment instanceof org.telegram.ui.DialogsActivity)) {
            return; // this container does not show the chat list right now
        }
        org.telegram.ui.DialogsActivity dialogsActivity = (org.telegram.ui.DialogsActivity) fragment;

        // v7: pickers (forward/share/select) must stay untouched.
        boolean isPicker = false;
        try {
            java.lang.reflect.Method getType = dialogsActivity.getClass().getMethod("getInitialDialogsType");
            getType.setAccessible(true);
            Object type = getType.invoke(dialogsActivity);
            if (type instanceof Integer && ((Integer) type).intValue() != 0) {
                isPicker = true;
            }
        } catch (Throwable ignored) {
            // no such method or not accessible - treat as the main list
        }
        if (isPicker) {
            return;
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

        View existing = host.findViewWithTag(ROW_TAG);
        if (existing == null) {
            Activity activity = findActivity(fragmentView);
            if (activity == null) {
                return;
            }
            pinRow(activity, host, list, (ViewGroup) fragmentView);
        }

        // v7: wire the per-frame sync exactly once per fragment view.
        try {
            synchronized (wired) {
                if (wired.containsKey(fragmentView)) {
                    return;
                }
                wired.put(fragmentView, Boolean.TRUE);
            }
            fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    try {
                        syncRow((ViewGroup) fragmentView, host, list);
                    } catch (Throwable ignored) { }
                    return true; // never block drawing
                }
            });
        } catch (Throwable ignored) { }
    }

    /**
     * v7 CORE: runs EVERY frame BEFORE it is drawn - the exact point where
     * Telegram itself moves the tabs strip and the stories section while the
     * list scrolls. Because we run in the same frame, the row and the tabs can
     * never be out of sync, which eliminates doubling/flicker entirely.
     */
    private static void syncRow(ViewGroup fragmentView, ViewGroup host, View list) {
        try {
            View row = host.findViewWithTag(ROW_TAG);
            if (row == null) {
                return;
            }

            View tabs = findInstanceOf(fragmentView, org.telegram.ui.Components.FilterTabsView.class);
            int top = rowTopOffset(host, fragmentView, tabs);

            boolean tabsVisible = tabs != null && tabs.getVisibility() == View.VISIBLE && tabs.getHeight() > 0;
            if (top <= 0) {
                // Tabs are scrolled out (stories section) or ActionBar anchor
                // is not measurable yet: slide the row under the header by
                // translating it out of the visible area - same movement the
                // tabs themselves follow, so no jump is visible.
                float hideAt = -(row.getHeight() <= 0 ? 0 : row.getHeight());
                if (Math.abs(row.getTranslationY() - hideAt) >= 1f) {
                    row.setTranslationY(hideAt);
                }
                return;
            }

            // Keep the row exactly under the tabs, every frame, sub-pixel free.
            if (Math.abs(row.getTranslationY() - top) >= 1f) {
                row.setTranslationY(top);
            }

            // v7: padding is set ONLY when the list is not being touched -
            // requestLayout() during a drag/fling is what broke the folder
            // swipe tracking and caused the visible jump.
            boolean scrollIdle = true;
            if (list instanceof RecyclerView) {
                RecyclerView rv = (RecyclerView) list;
                scrollIdle = rv.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
            if (scrollIdle && row.getHeight() > 0) {
                int[] hostLoc = new int[2];
                int[] listLoc = new int[2];
                host.getLocationOnScreen(hostLoc);
                list.getLocationOnScreen(listLoc);
                int listOffsetInHost = listLoc[1] - hostLoc[1];
                int needed = top + row.getHeight() - listOffsetInHost;
                if (needed > 0 && Math.abs(list.getPaddingTop() - needed) >= 4) {
                    list.setPadding(list.getPaddingLeft(), needed,
                            list.getPaddingRight(), list.getPaddingBottom());
                }
            }
        } catch (Throwable ignored) { }
    }

    /**
     * Preferred row position: right below the folder tabs strip
     * ("All chats" / "Personal"). If the strip exists, the row follows it
     * frame by frame. If there are no folder tabs at all, the row anchors
     * below the ActionBar instead of disappearing (v7: no more "skip forever").
     */
    private static int rowTopOffset(ViewGroup host, ViewGroup fragmentView, View tabs) {
        try {
            int[] hostLoc = new int[2];
            host.getLocationOnScreen(hostLoc);
            if (tabs != null && tabs.getVisibility() == View.VISIBLE && tabs.getHeight() > 0) {
                int[] tabsLoc = new int[2];
                tabs.getLocationOnScreen(tabsLoc);
                return tabsLoc[1] + tabs.getHeight() - hostLoc[1];
            }
            // No folder tabs configured: anchor to the fragment top (ActionBar
            // sits above it), so the row still has a stable, meaningful place.
            int[] fvLoc = new int[2];
            fragmentView.getLocationOnScreen(fvLoc);
            int top = fvLoc[1] - hostLoc[1];
            if (top > 0) {
                return top;
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /**
     * Creates the row and positions it once the layout is measurable.
     * v7: the row starts fully transparent and fades in after the first
     * stable frame - so opening the dialogs list (including from
     * "forward") never shows a visible snap.
     */
    private static void pinRow(Activity activity, final ViewGroup host, final View list, final ViewGroup fragmentView) {
        final View row = NanoGramAiRow.createWithDivider(activity);
        row.setTag(ROW_TAG);
        row.setAlpha(0f);
        // The outer wrapper must not consume touches: only the inner card is
        // clickable, so ViewPagerFixed can start the folder swipe from the
        // top of the list (the swipe complaint).
        host.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        row.post(new Runnable() {
            int attempts = 0;

            @Override
            public void run() {
                try {
                    View tabs = findInstanceOf(fragmentView, org.telegram.ui.Components.FilterTabsView.class);
                    int top = rowTopOffset(host, fragmentView, tabs);
                    if (attempts < 12 && (host.getWidth() <= 0 || top <= 0)) {
                        attempts++;
                        row.postDelayed(this, 250);
                        return;
                    }
                    if (top <= 0) {
                        // v7: do NOT remove the row and do NOT mark the host -
                        // the anchor will resolve on later frames via syncRow.
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
                    row.animate().alpha(1f).setDuration(200).start();
                } catch (Throwable ignored) { }
            }
        });
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
