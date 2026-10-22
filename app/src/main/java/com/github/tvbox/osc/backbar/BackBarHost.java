package com.github.tvbox.osc.backbar;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/**
 * 全屏透明宿主。宿主本身不消费任何触摸；只有 BackBarView 的
 * 32 x 120 dp 热区会接收事件。这样既能在任意位置 ACTION_DOWN 时唤醒
 * 返回杠，又不会挡住页面原生的点击、滑动和播放器手势。
 */
final class BackBarHost extends FrameLayout {

    private final WeakReference<Activity> owner;
    private final BackBarView bar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideTask = this::hideNow;

    private boolean active;
    private boolean pressed;
    private boolean disposed;
    private long autoHideMs = 4_000L;
    private int side = Gravity.START;
    private int insetLeft;
    private int insetRight;
    private float barWidthDp = 10f;
    private float barHeightDp = 84f;
    private float hitWidthDp = 32f;
    private float hitHeightDp = 120f;
    private float edgeMarginDp = 2f;

    BackBarHost(Activity activity) {
        super(activity);
        owner = new WeakReference<>(activity);
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 21) setElevation(1000f);

        bar = new BackBarView(this);
        addView(bar, new LayoutParams(dp(hitWidthDp), dp(hitHeightDp)));
        bar.setVisibility(INVISIBLE);
        bar.setAlpha(0f);

        if (Build.VERSION.SDK_INT >= 20) {
            setOnApplyWindowInsetsListener((view, insets) -> {
                int left = insets.getSystemWindowInsetLeft();
                int right = insets.getSystemWindowInsetRight();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                    right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                }
                if (left != insetLeft || right != insetRight) {
                    insetLeft = left;
                    insetRight = right;
                    updateBarLayout();
                }
                return insets; // 不消费系统 inset，原页面照常布局。
            });
        }
    }

    void applyConfig(int side, long autoHideMs, float barWidthDp, float barHeightDp,
                     float hitWidthDp, float hitHeightDp, float edgeMarginDp) {
        this.side = side == Gravity.END ? Gravity.END : Gravity.START;
        this.autoHideMs = Math.max(0L, autoHideMs);
        this.barWidthDp = Math.max(2f, barWidthDp);
        this.barHeightDp = Math.max(24f, barHeightDp);
        this.hitWidthDp = Math.max(24f, hitWidthDp);
        this.hitHeightDp = Math.max(64f, hitHeightDp);
        this.edgeMarginDp = Math.max(0f, edgeMarginDp);
        updateBarLayout();
    }

    void refresh() {
        if (disposed) return;
        updateBarLayout();
    }

    void resume() {
        if (disposed) return;
        active = true;
        bar.setVisibility(INVISIBLE);
        bar.setAlpha(0f);
        keepOnTop();
        showTemporarily();
    }

    void pause() {
        active = false;
        pressed = false;
        handler.removeCallbacks(hideTask);
        bar.animate().cancel();
        bar.setVisibility(INVISIBLE);
        bar.setAlpha(0f);
    }

    void dispose() {
        pause();
        disposed = true;
        handler.removeCallbacksAndMessages(null);
        bar.setOnTouchListener(null);
    }

    void wake() {
        if (!active || disposed) return;
        if (bar.getVisibility() != VISIBLE) {
            // This runs from Activity.dispatchTouchEvent before the DOWN is delivered. Showing
            // synchronously would put a new bar under that same finger and could fire a false
            // Back. Defer only the hidden -> visible transition to the next main-loop turn.
            post(this::showTemporarily);
        } else {
            showTemporarily();
        }
    }

    void onBarPressed(boolean value) {
        pressed = value;
        if (value) {
            handler.removeCallbacks(hideTask);
            bar.animate().cancel();
            bar.setVisibility(VISIBLE);
            bar.setAlpha(1f);
        } else {
            scheduleAutoHide();
        }
    }

    void performBack() {
        Activity activity = owner.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            BackBarController.performBack(activity);
        } finally {
            scheduleAutoHide();
        }
    }

    private void showTemporarily() {
        if (!active || disposed) return;
        handler.removeCallbacks(hideTask);
        bar.animate().cancel();
        keepOnTop();
        bar.setVisibility(VISIBLE);
        bar.animate().alpha(1f).setDuration(140L).start();
        scheduleAutoHide();
    }

    private void keepOnTop() {
        if (disposed || !(getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) getParent();
        if (parent.getChildAt(parent.getChildCount() - 1) != this) bringToFront();
        bar.bringToFront();
    }

    private void scheduleAutoHide() {
        handler.removeCallbacks(hideTask);
        if (active && !pressed && autoHideMs > 0L) {
            handler.postDelayed(hideTask, autoHideMs);
        }
    }

    void hideNow() {
        if (disposed) return;
        handler.removeCallbacks(hideTask);
        if (pressed) return;
        bar.animate().cancel();
        bar.animate().alpha(0f).setDuration(180L).withEndAction(() -> {
            if (!pressed) bar.setVisibility(INVISIBLE);
        }).start();
    }

    private void updateBarLayout() {
        if (bar == null) return;
        int width = dp(hitWidthDp);
        int height = dp(hitHeightDp);
        LayoutParams params = (LayoutParams) bar.getLayoutParams();
        if (params == null) params = new LayoutParams(width, height);
        params.width = width;
        params.height = height;
        params.gravity = Gravity.CENTER_VERTICAL | side;
        boolean left = isLeftSide();
        params.leftMargin = left ? dp(edgeMarginDp) + insetLeft : 0;
        params.rightMargin = left ? 0 : dp(edgeMarginDp) + insetRight;
        bar.setLayoutParams(params);
        bar.configure(left, barWidthDp, barHeightDp);
    }

    private boolean isLeftSide() {
        if (side == Gravity.END) {
            return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        }
        return getLayoutDirection() != View.LAYOUT_DIRECTION_RTL;
    }

    float viewportScale() {
        Resources resources = getResources();
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            width = resources.getDisplayMetrics().widthPixels;
            height = resources.getDisplayMetrics().heightPixels;
        }
        float proportional = Math.min(width / 1280f, height / 720f);
        return Math.max(1f, Math.min(3.4f, proportional));
    }

    private int dp(float value) {
        Resources resources = getResources();
        float density = resources == null ? 1f : resources.getDisplayMetrics().density;
        return Math.max(1, Math.round(value * Math.max(0.1f, density) * viewportScale()));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) wake();
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) updateBarLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!disposed && getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent.getChildAt(parent.getChildCount() - 1) != this) post(this::keepOnTop);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= 20) requestApplyInsets();
        post(this::keepOnTop);
    }

    @Override
    protected void onDetachedFromWindow() {
        pause();
        super.onDetachedFromWindow();
    }
}
