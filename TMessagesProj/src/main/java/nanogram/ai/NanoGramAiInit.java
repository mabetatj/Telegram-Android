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
import android.widget.FrameLayout;

/**
 * Startup hook for the "NanoGram AI" row inside the dialogs list.
 *
 * Registered as a ContentProvider in AndroidManifest.xml, so it initializes
 * before any activity starts and requires no edits inside Telegram's own classes.
 *
 * The whole class is fail-soft: every entry point is wrapped in try/catch,
 * so it can never crash or block the app.
 */
public class NanoGramAiInit extends ContentProvider {

    private static final String ROW_TAG = "nanogram_ai_row_tag";

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
                            decor.post(new Runnable() {
                                @Override
                                public void run() {
                                    attach(decor);
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
     * Pins the "NanoGram AI" row at the top of the dialogs list, below the
     * action bar AND below the chat folders strip ("Все чаты" / "Личные" tabs).
     */
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

            View list = findInstanceOf(root, org.telegram.ui.Components.RecyclerListView.class);
            if (list == null || !(list.getParent() instanceof ViewGroup)) {
                return;
            }
            Activity activity = findActivity(root);
            if (activity == null) {
                return;
            }
            final ViewGroup host = (ViewGroup) list.getParent();
            final View row = NanoGramAiRow.createWithDivider(activity);
            row.setTag(ROW_TAG);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            host.addView(row, params);

            // Compute the top offset so the row sits below BOTH the action bar
            // and the chat folders strip (folder tabs). This keeps it from
            // overlapping the "Все чаты" tab strip and keeps the first chat
            // row fully visible below it.
            row.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        int top = computeTopOffset(root);
                        row.setTranslationY(top);

                        int width = host.getWidth() > 0 ? host.getWidth() : root.getWidth();
                        row.measure(
                                View.MeasureSpec.makeMeasureSpec(Math.max(1, width), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        int height = row.getMeasuredHeight();
                        if (height > 0 && !"nanogram_ai_padded".equals(list.getTag())) {
                            list.setTag("nanogram_ai_padded");
                            list.setPadding(list.getPaddingLeft(), list.getPaddingTop() + height,
                                    list.getPaddingRight(), list.getPaddingBottom());
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    /**
     * Walks the dialogs screen view tree and finds the lowest bottom edge of:
     * - the ActionBar (title row), and
     * - the chat folders strip (the horizontal tabs view that contains
     *   "Все чаты" / "Личные" folder tabs).
     * The returned offset positions the NanoGram AI row directly below both.
     */
    private static int computeTopOffset(ViewGroup root) {
        int top = 0;
        try {
            View bar = findInstanceOf(root, org.telegram.ui.ActionBar.ActionBar.class);
            if (bar != null && bar.getVisibility() == View.VISIBLE) {
                top = Math.max(top, bar.getTranslationY() > 0
                        ? (int) (bar.getTranslationY() + bar.getHeight())
                        : bar.getHeight());
            }

            // The folders strip is a ViewPager (folder tabs live inside it) that sits
            // directly below the action bar on the dialogs screen.
            View folders = findInstanceOf(root, androidx.viewpager.widget.ViewPager.class);
            if (folders != null && folders.getVisibility() == View.VISIBLE) {
                int[] loc = new int[2];
                folders.getLocationOnScreen(loc);
                View list = findInstanceOf(root, org.telegram.ui.Components.RecyclerListView.class);
                if (list != null) {
                    int[] listLoc = new int[2];
                    list.getLocationOnScreen(listLoc);
                    // Only count the pager as the folders strip when it is above
                    // the dialogs list (i.e. it is the tab strip, not the pages container).
                    if (loc[1] <= listLoc[1]) {
                        top = Math.max(top, loc[1] + folders.getHeight() - listLoc[1]);
                    }
                }
            }
        } catch (Throwable ignored) { }
        return top;
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
