package nanogram.ai;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * Startup hook for the "NanoGram AI" row inside the dialogs list.
 *
 * Registered as a ContentProvider in AndroidManifest.xml (merged at build time
 * by scripts/build-android.py), so it initializes before any activity starts
 * and requires no edits inside Telegram's own classes.
 *
 * Important Telegram detail: the chat list is NOT a separate Activity.
 * The whole app runs inside one window (LaunchActivity), and the dialogs
 * screen is a BaseFragment inside ActionBarLayout. So we check the fragment
 * stack to detect when the dialogs screen is visible, then pin the
 * "NanoGram AI" row at the top of the chat list, strictly BELOW the action
 * bar (which contains the folder tabs such as "All chats"), so the row never
 * overlaps the folders area and never hides the first chat row.
 *
 * Positioning fix: the previous version looked up the first ActionBar found
 * anywhere in the window, which could be a different fragment's bar with a
 * wrong height - that made the row land on top of the folder tabs. Now the
 * bar is taken directly from the visible DialogsActivity fragment, and the
 * row is placed below that exact bar.
 *
 * The whole class is fail-soft: every entry point is wrapped in try/catch,
 * so it can never crash or block the app.
 */
public class NanoGramAiInit extends ContentProvider {

    private static final String ROW_TAG = "nanogram_ai_row_tag";
    private static final String PADDED_TAG = "nanogram_ai_padded";
    private static final String HOOK_TAG = "nanogram_ai_layout_hook";

    @Override
    public boolean onCreate() {
        try {
            Context context = getContext();
            if (context instanceof Application) {
                ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

                    @Override
                    public void onActivityStarted(Activity activity) { }

                    @Override
                    public void onActivityResumed(final Activity activity) {
                        try {
                            final View decor = activity.getWindow().getDecorView();
                            decor.post(() -> attach(decor));
                            // Re-check on every layout pass: fragment switches inside the
                            // single window do not fire onActivityResumed, but they do
                            // change the layout, and the listener lets us re-attach.
                            if (decor.findViewWithTag(HOOK_TAG) == null) {
                                decor.setTag(HOOK_TAG);
                                decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> attach(decor));
                            }
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

    /** Pins the "NanoGram AI" row at the top of the dialogs list, below the folder tabs. */
    private static void attach(View decor) {
        try {
            View content = decor.findViewById(android.R.id.content);
            if (!(content instanceof ViewGroup)) {
                return;
            }
            final ViewGroup root = (ViewGroup) content;
            if (root.findViewWithTag(ROW_TAG) != null) {
                return; // already attached on this screen
            }

            // Telegram: the dialogs list is a fragment inside ActionBarLayout, not an Activity.
            View layoutView = findInstanceOf(root, org.telegram.ui.ActionBar.ActionBarLayout.class);
            if (!(layoutView instanceof org.telegram.ui.ActionBar.ActionBarLayout)) {
                return;
            }
            org.telegram.ui.ActionBar.BaseFragment lastFragment =
                    ((org.telegram.ui.ActionBar.ActionBarLayout) layoutView).getLastFragmentIncludeMainTabs();
            if (!(lastFragment instanceof org.telegram.ui.DialogsActivity)) {
                return; // the chat list is not the visible screen
            }
            final org.telegram.ui.DialogsActivity dialogs = (org.telegram.ui.DialogsActivity) lastFragment;

            View fragmentView = dialogs.getFragmentView();
            if (!(fragmentView instanceof ViewGroup)) {
                return;
            }
            final ViewGroup host = (ViewGroup) fragmentView;

            // The chat list itself: searched INSIDE this fragment's view, so we never
            // pick up a list that belongs to another screen (the old bug).
            final View list = findInstanceOf(host, org.telegram.ui.Components.RecyclerListView.class);
            if (list == null) {
                return;
            }
            Activity activity = findActivity(host);
            if (activity == null) {
                return;
            }

            final View row = NanoGramAiRow.createWithDivider(activity);
            row.setTag(ROW_TAG);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            host.addView(row, params);

            // Position strictly below the action bar OF THIS FRAGMENT. The action bar
            // holds the folder tabs ("All chats" and others), so the row always lands
            // below the folders, never on top of them.
            final org.telegram.ui.ActionBar.ActionBar bar = dialogs.getActionBar();
            float top = 0f;
            if (bar != null && bar.getVisibility() == View.VISIBLE) {
                top = Math.max(0f, bar.getTranslationY() + bar.getHeight());
            }
            row.setTranslationY(top);

            // Push the chat list down so the first dialog is not hidden behind our row.
            // Applied once per list instance (tagged), so layout passes never stack padding.
            row.post(() -> {
                try {
                    int width = host.getWidth() > 0 ? host.getWidth() : root.getWidth();
                    row.measure(
                            View.MeasureSpec.makeMeasureSpec(Math.max(1, width), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    int height = row.getMeasuredHeight();
                    if (height > 0 && !PADDED_TAG.equals(list.getTag())) {
                        list.setTag(PADDED_TAG);
                        list.setPadding(list.getPaddingLeft(), list.getPaddingTop() + height,
                                list.getPaddingRight(), list.getPaddingBottom());
                    }
                } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
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
