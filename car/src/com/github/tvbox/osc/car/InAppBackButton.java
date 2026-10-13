package com.github.tvbox.osc.car;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Car head-unit in-app floating Back button.
 *
 * <p>Head units cannot authorize an accessibility service, so there is no global Back key.
 * This button is a plain View inside the current Activity window: no system overlay, no
 * accessibility, no permission and no ADB. It is attached by {@link BaseActivity} on every
 * page, wakes on any touch, hides after a few idle seconds so it never blocks content, and
 * one tap goes up one level inside the app. The home root only shows a hint and never exits
 * the app by itself.</p>
 */
public final class InAppBackButton {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Entry> ENTRIES = new ArrayList<Entry>();

    private InAppBackButton() {
    }

    /** 0 means 常显 (always visible); otherwise the configured idle seconds. */
    private static int idleMillis() {
        int seconds = Hawk.get(HawkConfig.BACK_HIDE_SECONDS, 4);
        if (seconds == 0) return 0;
        if (seconds < 2 || seconds > 15) seconds = 4;
        return seconds * 1000;
    }

    private static boolean enabled() {
        return Hawk.get(HawkConfig.IN_APP_BACK, true);
    }

    /** Attach on every resumed page; safe to call repeatedly. */
    public static void attach(final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!enabled()) return;
        final View decor;
        try {
            decor = activity.getWindow().getDecorView();
        } catch (Throwable error) {
            return;
        }
        Entry existing = find(decor);
        if (existing != null) {
            existing.show();
            return;
        }
        Entry entry = create(activity, decor);
        if (entry == null) return;
        synchronized (ENTRIES) {
            ENTRIES.add(entry);
        }
        entry.show();
    }

    /** Show and restart the idle timer; called on any touch of the current page. */
    public static void wake(Activity activity) {
        if (activity == null) return;
        Entry entry;
        try {
            entry = find(activity.getWindow().getDecorView());
        } catch (Throwable error) {
            return;
        }
        if (entry != null) entry.show();
    }

    /** Detach when the page is destroyed. */
    public static void release(Activity activity) {
        if (activity == null) return;
        Entry entry;
        try {
            entry = find(activity.getWindow().getDecorView());
        } catch (Throwable error) {
            return;
        }
        if (entry == null) return;
        synchronized (ENTRIES) {
            ENTRIES.remove(entry);
        }
        MAIN.removeCallbacks(entry);
        View parent = (View) entry.view.getParent();
        if (parent instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) parent).removeView(entry.view);
        }
    }

    private static final class Entry implements Runnable, View.OnClickListener {
        final WeakReference<Activity> activity;
        final View decor;
        final TextView view;

        Entry(Activity owner, View decor, TextView view) {
            this.activity = new WeakReference<Activity>(owner);
            this.decor = decor;
            this.view = view;
        }

        @Override
        public void onClick(View ignored) {
            hide();
            Activity owner = activity.get();
            if (owner == null || owner.isFinishing() || owner.isDestroyed()) return;
            if (owner instanceof BaseActivity) {
                BaseActivity base = (BaseActivity) owner;
                try {
                    if (base.onBackButtonStepUp()) return;
                    if (base.onBackButtonAtRoot()) {
                        Toast.makeText(owner, "已在首页", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (Throwable error) {
                    // fall through to the normal back handling
                }
            }
            try {
                owner.onBackPressed();
            } catch (Throwable error) {
                try {
                    owner.finish();
                } catch (Throwable ignored2) {
                }
            }
        }

        @Override
        public void run() {
            Activity owner = activity.get();
            if (owner == null || owner.isFinishing() || owner.isDestroyed()) return;
            view.setVisibility(View.GONE);
        }

        void hide() {
            MAIN.removeCallbacks(this);
            view.setVisibility(View.GONE);
        }

        void show() {
            Activity owner = activity.get();
            if (owner == null || owner.isFinishing() || owner.isDestroyed()) return;
            view.setVisibility(View.VISIBLE);
            MAIN.removeCallbacks(this);
            int idle = idleMillis();
            if (idle > 0) MAIN.postDelayed(this, idle);
        }
    }

    private static Entry find(View decor) {
        synchronized (ENTRIES) {
            for (int i = 0; i < ENTRIES.size(); i++) {
                if (ENTRIES.get(i).decor == decor) return ENTRIES.get(i);
            }
        }
        return null;
    }

    /**
     * Car-spec geometry (CAR-UI-SPEC-TVBOX-2880x1620 §2/§4/§5.2):
     * left safe margin 35mm, button 50mm x 50mm (>= the 40mm minimum touch target), kept clear of
     * the top 40mm and bottom 55mm exclusion strips, focusable with a bright focus state.
     */
    private static Entry create(Activity activity, View decor) {
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        if (decor.getHeight() > 100) screenHeight = decor.getHeight();
        int size = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 50f, metrics));
        int margin = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 35f, metrics));
        int stroke = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 2f, metrics));
        if (size < 90) size = Math.max(90, Math.min(240, Math.round(screenWidth * 0.05f)));
        if (margin < 40) margin = Math.max(40, Math.round(screenWidth * 0.012f));
        if (stroke < 2) stroke = 2;
        int top = Math.round(screenHeight * 0.30f);

        TextView view = new TextView(activity);
        view.setText("\u2190");
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.42f);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription("应用内返回");
        view.setAlpha(0.9f);
        view.setClickable(true);
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setElevation(Math.round(size * 0.06f));
        view.setBackground(focusAwareBackground(stroke));
        FrameLayout.LayoutParams parameters = new FrameLayout.LayoutParams(
                size, size, Gravity.LEFT | Gravity.TOP);
        parameters.leftMargin = margin;
        parameters.topMargin = top;
        try {
            ((android.view.ViewGroup) decor).addView(view, parameters);
        } catch (Throwable error) {
            return null;
        }
        Entry entry = new Entry(activity, decor, view);
        view.setOnClickListener(entry);
        return entry;
    }

    /** Normal: translucent black circle. Focused: lighter fill with the car accent ring (2mm). */
    private static android.graphics.drawable.Drawable focusAwareBackground(int stroke) {
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.OVAL);
        focused.setColor(0xE6242A36);
        focused.setStroke(stroke, 0xFF2979FF);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.OVAL);
        normal.setColor(0xA6000000);
        states.addState(new int[]{}, normal);
        return states;
    }
}
