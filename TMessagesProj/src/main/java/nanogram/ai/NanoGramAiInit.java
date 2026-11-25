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
 * The row is positioned geometrically: it is placed directly above the first
 * visible chat row (i.e. below the action bar AND below the chat folders strip
 * "Все чаты" / "Личные"), and the list's top padding is increased so the first
 * chat is never hidden behind the row. No Telegram-internal classes are used
 * for the offset math, so the fix survives upstream layout changes.
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

    /** Pins the "NanoGram AI" row below the action bar and the folder tabs, above the first chat row. */
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

            row.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        int width = host.getWidth() > 0 ? host.getWidth() : root.getWidth();
                        row.measure(
                                View.MeasureSpec.makeMeasureSpec(Math.max(1, width), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        int height = row.getMeasuredHeight();
                        if (height <= 0) {
                            return;
                        }

                        // Where the first chat row currently starts on screen.
                        int top = computeTopOffset(host, list);
                        row.setTranslationY(top);

                        if (!"nanogram_ai_padded".equals(list.getTag())) {
                            list.setTag("nanogram_ai_padded");
                            // Push the chat list down so the first dialog starts right below the row.
                            int[] hostLoc = new int[2];
                            int[] listLoc = new int[2];
                            host.getLocationOnScreen(hostLoc);
                            list.getLocationOnScreen(listLoc);
                            int listOffsetInHost = listLoc[1] - hostLoc[1];
                            int newPadTop = Math.max(list.getPaddingTop(),
                                    top + height - listOffsetInHost);
                            list.setPadding(list.getPaddingLeft(), newPadTop,
                                    list.getPaddingRight(), list.getPaddingBottom());
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    /**
     * Geometric top offset inside `host` for the AI row: the bottom edge of the
     * last "header-like" sibling above the list (action bar, folder tabs strip).
     * A sibling counts only if it is visible, short compared to the list
     * (header-height, not a full-screen overlay), and sits near the top of the
     * list. This reliably finds the folder tabs strip without depending on
     * Telegram-internal view classes.
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
