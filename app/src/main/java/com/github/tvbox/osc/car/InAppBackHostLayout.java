package com.github.tvbox.osc.car;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.lang.ref.WeakReference;

/** Blank space never intercepts or consumes a touch; only the two controls can be targets. */
final class InAppBackHostLayout extends FrameLayout {
    private final WeakReference<Activity> owner;
    private final FloatingBackBallView ball;
    private final EdgePullBackView edge;
    private final EdgeSwipeBackView swipe;
    private final Runnable rootCheck = this::applyVisibility;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutObserver = this::onPageLayout;
    private ViewTreeObserver observedTree;
    private String mode, side;
    private boolean resumed, disposed, hideOnRoot, playbackHidden, haptic, geometryDirty = true;
    private boolean lastShowBall, lastShowEdge, lastShowSwipe, visibilityApplied;
    private boolean mEdgeCapture;
    private long lastWakeMs;
    private float displayDensity = 1f, tokenScale = 1f;
    private float insetLeft, insetTop, insetRight, insetBottom;
    private float safeLeft, safeTop, safeRight, safeBottom;
    private View mEdgeCaptureView;

    InAppBackHostLayout(Activity activity) {
        super(activity);
        owner = new WeakReference<>(activity);
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= 21) setTranslationZ(1000f);
        edge = new EdgePullBackView(this);
        ball = new FloatingBackBallView(this);
        swipe = new EdgeSwipeBackView(this);
        addView(edge, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(ball, new LayoutParams(1, 1));
        addView(swipe, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 20) {
            setOnApplyWindowInsetsListener((view, insets) -> {
                readInsets(insets);
                return insets; // Do not consume or alter insets used by the original Activity.
            });
        }
    }

    private void onPageLayout() {
        if (!resumed || disposed) return;
        Resources resources = getContext().getResources();
        float nextDensity = Math.max(.1f, resources.getDisplayMetrics().density);
        float nextScale = BackNavPolicy.carTokenScale(getWidth(), getHeight(), nextDensity);
        if (Math.abs(nextDensity - displayDensity) > .001f || Math.abs(nextScale - tokenScale) > .001f
                || Math.abs(Math.max(12 * nextDensity, resources.getDimension(R.dimen.car_side_padding)) - safeLeft) > .5f
                || Math.abs(Math.max(36 * nextDensity, resources.getDimension(R.dimen.car_top_padding)) - safeTop) > .5f
                || Math.abs(Math.max(56 * nextDensity, resources.getDimension(R.dimen.car_bottom_padding)) - safeBottom) > .5f) {
            // AutoSize can update its mm canvas after the Activity's delayed layout refresh.
            refresh(false);
        } else if (hideOnRoot) recheckRootAfterNavigation();
    }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        refresh(false);
    }

    void resume() {
        if (disposed) return;
        resumed = true;
        refresh(false);
        wake();
    }

    void refresh() { refresh(true); }

    private void refresh(boolean applySideChange) {
        if (disposed) return;
        String nextSide = InAppBackButton.getSide();
        if (applySideChange && side != null && !side.equals(nextSide)
                && !HawkConfig.BACK_SIDE_BOTH.equals(nextSide)) {
            // Only an explicit settings refresh chooses a dock; resume restores newer page state.
            Hawk.put(HawkConfig.BACK_POSITION_X, HawkConfig.BACK_SIDE_RIGHT.equals(nextSide) ? 10000 : 0);
        }
        side = nextSide;
        mode = InAppBackButton.getMode();
        hideOnRoot = InAppBackButton.isHideOnRoot();
        haptic = InAppBackButton.isHapticEnabled();
        Resources resources = getContext().getResources();
        displayDensity = Math.max(.1f, resources.getDisplayMetrics().density);
        tokenScale = BackNavPolicy.carTokenScale(getWidth(), getHeight(), displayDensity);
        // Safe insets remain native dp / AutoSize mm; only the reference controls use car tokens.
        safeLeft = Math.max(dp(12), resources.getDimension(R.dimen.car_side_padding));
        safeRight = safeLeft;
        safeTop = Math.max(dp(36), resources.getDimension(R.dimen.car_top_padding));
        safeBottom = Math.max(dp(56), resources.getDimension(R.dimen.car_bottom_padding));
        ball.configure(tokenScale, InAppBackButton.isLarge(), InAppBackButton.getHideSeconds() * 1000,
                InAppBackButton.getBarColor(), InAppBackButton.getBarAlpha(),
                InAppBackButton.getBarWidthDp());
        edge.configure(tokenScale, side, InAppBackButton.getEdgeSkin(),
                InAppBackButton.getBarWidthDp(), InAppBackButton.getBarHeightDp(),
                InAppBackButton.getBarColor(), InAppBackButton.getBarAlpha(),
                InAppBackButton.getHideSeconds());
        swipe.configure(tokenScale, side, InAppBackButton.getBarColor(),
                InAppBackButton.getSwipeTriggerDp());
        geometryDirty = true;
        requestLayout();
        syncGeometry();
        applyVisibility();
        ball.wake();
    }

    void wake() {
        wakeAt(-1f, -1f, false);
    }

    /** 页面 DOWN：普通触摸用于「点亮」，但边缘手势必须永远可用。 */
    void wakeAt(float x, float y) {
        wakeAt(x, y, false);
    }

    /**
     * @param forceEdge true 表示这次 DOWN 已由边缘视图确认命中，必须无条件放行。
     *                  冷却只用于抑制车机重复派发造成的「一直点亮」，绝不拦截手势本身。
     */
    private void wakeAt(float x, float y, boolean forceEdge) {
        if (disposed || !resumed) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!EdgeHitPolicy.allowWake(now, lastWakeMs, 1000L, forceEdge)) {
            recheckRootAfterNavigation();
            return;
        }
        lastWakeMs = now;
        ball.wake();
        edge.wake();
        swipe.wake();
        recheckRootAfterNavigation();
    }

    void setPlaybackHidden(boolean hidden) {
        if (playbackHidden == hidden) return;
        playbackHidden = hidden;
        applyVisibility();
    }


    void recheckRootAfterNavigation() {
        removeCallbacks(rootCheck);
        if (resumed && !disposed) post(rootCheck);
    }

    void performBack() {
        android.util.Log.i("InAppBack", "back_perform page=" + pageName()
                + " mode=" + mode + " capture=" + mEdgeCapture);
        InAppBackButton.performBack(owner.get());
    }

    void feedback(View source, boolean confirm) {
        if (!haptic) return;
        int type = confirm && Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM : confirm
                ? HapticFeedbackConstants.VIRTUAL_KEY : HapticFeedbackConstants.LONG_PRESS;
        if (!source.performHapticFeedback(type) && confirm && Build.VERSION.SDK_INT >= 30) {
            source.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    float dp(float value) { return value * displayDensity; }

    private void applyVisibility() {
        if (disposed) return;
        boolean rootHidden = hideOnRoot && InAppBackButton.atRoot(owner.get());
        boolean show = resumed && !playbackHidden && InAppBackButton.alive(owner.get()) && !rootHidden;
        boolean swipeMode = HawkConfig.BACK_MODE_SWIPE.equals(mode);
        boolean showBall = show && !swipeMode && !HawkConfig.BACK_MODE_EDGE.equals(mode);
        boolean showEdge = show && !swipeMode && !HawkConfig.BACK_MODE_FLOATING.equals(mode);
        boolean showSwipe = show && swipeMode;
        // 这个方法会被 attach / layout / focus / rootCheck 反复调用（现场日志 147 次）。
        // 以前每次都 setActive(true) 会把已淡出的边缘条/球重新点亮并重启隐藏计时，
        // 结果看起来就是「一直显示关不掉」。只在显示状态真正变化时才碰它们。
        if (visibilityApplied && showBall == lastShowBall && showEdge == lastShowEdge
                && showSwipe == lastShowSwipe) {
            // 即使显示状态没变，也要保证边缘视图的 active 与 visible 没被别的路径关掉。
            // 部分界面被系统回收/重建后曾出现「逻辑上 showEdge=true、视图却 inactive」
            // 从而整条边缘拉不出来的情况。
            if (showEdge && edge.getVisibility() != VISIBLE) edge.setVisibility(VISIBLE);
            if (showBall && ball.getVisibility() != VISIBLE) ball.setVisibility(VISIBLE);
            return;
        }
        visibilityApplied = true;
        lastShowBall = showBall;
        lastShowEdge = showEdge;
        lastShowSwipe = showSwipe;
        android.util.Log.i("InAppBack", "vis_changed mode=" + mode + " show=" + show
                + " ball=" + showBall + " edge=" + showEdge + " swipe=" + showSwipe
                + " resumed=" + resumed + " playbackHidden=" + playbackHidden + " rootHidden=" + rootHidden);
        ball.setVisibility(showBall ? VISIBLE : INVISIBLE);
        ball.setVisibility(showBall ? VISIBLE : INVISIBLE);
        edge.setVisibility(showEdge ? VISIBLE : INVISIBLE);
        swipe.setVisibility(showSwipe ? VISIBLE : INVISIBLE);
        ball.setActive(showBall);
        edge.setActive(showEdge);
        swipe.setActive(showSwipe);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (disposed || !resumed) return super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        float x = event.getX(), y = event.getY();
        View activeView = HawkConfig.BACK_MODE_SWIPE.equals(mode) ? swipe : edge;
        if (action == MotionEvent.ACTION_DOWN) {
            // r76：历史记录页等页面实测手势失效。旧逻辑只在 lastShowEdge=true 时把 DOWN
            // 直接转给 edge，一旦 AutoSize/可见性刷新时序让这个缓存值滞后，整条手势就丢失。
            // 现在无条件把完整 DOWN 交给当前激活视图自己判定；命中返回 true 才建立 capture，
            // 空白区仍原样穿透给页面内容。
            MotionEvent copy = MotionEvent.obtain(event);
            boolean handled;
            try {
                handled = activeView.onTouchEvent(copy);
            } finally {
                copy.recycle();
            }
            android.util.Log.i("InAppBack", "host_down page=" + pageName()
                    + " mode=" + mode + " active=" + activeView.getClass().getSimpleName()
                    + " x=" + (int) x + " y=" + (int) y
                    + " w=" + getWidth() + " h=" + getHeight()
                    + " hit=" + handled + " edgeVis=" + edge.getVisibility()
                    + " swipeVis=" + swipe.getVisibility());
            wakeAt(x, y, handled);
            if (handled) {
                mEdgeCapture = true;
                mEdgeCaptureView = activeView;
                return true;
            }
        }
        if (mEdgeCapture && mEdgeCaptureView != null) {
            MotionEvent copy = MotionEvent.obtain(event);
            boolean handled;
            try {
                handled = mEdgeCaptureView.onTouchEvent(copy);
            } finally {
                copy.recycle();
            }
            if (action == MotionEvent.ACTION_MOVE) {
                android.util.Log.i("InAppBack", "host_move page=" + pageName()
                        + " x=" + (int) x + " y=" + (int) y + " handled=" + handled);
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                android.util.Log.i("InAppBack", "host_up page=" + pageName()
                        + " action=" + action + " x=" + (int) x + " y=" + (int) y
                        + " handled=" + handled);
                mEdgeCapture = false;
                mEdgeCaptureView = null;
            }
            if (handled) return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private String pageName() {
        Activity a = owner.get();
        return a == null ? "null" : a.getClass().getSimpleName();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return false; }
    @Override public boolean onTouchEvent(MotionEvent event) { return false; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        Resources resources = getContext().getResources();
        float density = Math.max(.1f, resources.getDisplayMetrics().density);
        float nextScale = BackNavPolicy.carTokenScale(MeasureSpec.getSize(widthSpec),
                MeasureSpec.getSize(heightSpec), density);
        if (Math.abs(nextScale - tokenScale) > .001f || Math.abs(density - displayDensity) > .001f) {
            // Configure before FrameLayout measures its children. A size change in onSizeChanged
            // is too late for the first pass and can leave the cold-start hit box at 72 px.
            displayDensity = density;
            tokenScale = nextScale;
            ball.configure(tokenScale, InAppBackButton.isLarge(), InAppBackButton.getHideSeconds() * 1000,
                    InAppBackButton.getBarColor(), InAppBackButton.getBarAlpha(),
                    InAppBackButton.getBarWidthDp());
            edge.configure(tokenScale, side, InAppBackButton.getEdgeSkin(),
                    InAppBackButton.getBarWidthDp(), InAppBackButton.getBarHeightDp(),
                    InAppBackButton.getBarColor(), InAppBackButton.getBarAlpha(),
                    InAppBackButton.getHideSeconds());
            swipe.configure(tokenScale, side, InAppBackButton.getBarColor(),
                    InAppBackButton.getSwipeTriggerDp());
            geometryDirty = true;
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        geometryDirty = true;
        if (w > 0 && h > 0) refresh(false);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        syncGeometry();
        recheckRootAfterNavigation();
    }

    private void syncGeometry() {
        if (!geometryDirty || getWidth() <= 0 || getHeight() <= 0) return;
        geometryDirty = false;
        float left = Math.max(safeLeft, insetLeft), right = Math.max(safeRight, insetRight);
        if (HawkConfig.BACK_MODE_BOTH.equals(mode)) {
            // Keep even the half-hidden ball's hit box clear of the local edge-pull zone.
            float reserve = 28 * tokenScale + dp(12) + (InAppBackButton.isLarge() ? 64 : 56) * tokenScale * .35f;
            if (!HawkConfig.BACK_SIDE_RIGHT.equals(side)) left = Math.max(left, reserve);
            if (!HawkConfig.BACK_SIDE_LEFT.equals(side)) right = Math.max(right, reserve);
        }
        float top = Math.max(safeTop, insetTop), bottom = Math.max(safeBottom, insetBottom);
        int box = ball.getLayoutParams().width;
        BackNavPolicy.Bounds bounds = new BackNavPolicy.Bounds(getWidth(), getHeight(), box, box,
                left, top, right, bottom);
        ball.syncBounds(bounds, getWidth(), HawkConfig.BACK_SIDE_RIGHT.equals(side));
        // 导引小棍的可停范围 = 安全区内，且保证小棍本体不越过上下安全线（含系统手势/导航条区域）。
        float halfBar = InAppBackButton.getBarHeightDp() * tokenScale / 2f;
        float edgeMin = Math.max(top, halfBar);
        float edgeMax = Math.max(edgeMin, getHeight() - bottom - halfBar);
        edge.syncBounds(edgeMin, edgeMax);
        float hitPx = EdgeGeometry.hitWidthPx(getWidth(),
                Math.max(dp(40), dp(InAppBackButton.getBarWidthDp()) * 3f));
        android.util.Log.i("InAppBack", "geom mode=" + mode
                + " host=" + getWidth() + "x" + getHeight()
                + " scale=" + tokenScale + " dens=" + displayDensity
                + " hitPx=" + (int) hitPx
                + " visualMaxPx=" + (int) EdgeGeometry.maxVisualWidthPx(getWidth())
                + " leafHalfMaxPx=" + (int) EdgeGeometry.leafHalfHeightPx(getHeight(), 99999f)
                + " safe=" + (int) safeLeft + "," + (int) safeTop + "," + (int) safeRight + "," + (int) safeBottom
                + " ballX=" + (int) ball.getX() + " ballY=" + (int) ball.getY()
                + " ballSize=" + ball.getWidth() + "x" + ball.getHeight()
                + " ballVis=" + ball.getVisibility() + " ballAlpha=" + ball.getAlpha()
                + " edgeVis=" + edge.getVisibility());
    }

    private void readInsets(WindowInsets insets) {
        float left = insets.getSystemWindowInsetLeft(), right = insets.getSystemWindowInsetRight();
        float top = insets.getSystemWindowInsetTop(), bottom = insets.getSystemWindowInsetBottom();
        if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
            left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
            right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
            top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
            bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
        }
        if (left == insetLeft && top == insetTop && right == insetRight && bottom == insetBottom) return;
        insetLeft = left;
        insetRight = right;
        insetTop = top;
        insetBottom = bottom;
        geometryDirty = true;
        requestLayout();
    }

    void pause() {
        resumed = false;
        removeCallbacks(rootCheck);
        ball.setActive(false);
        edge.setActive(false);
        swipe.setActive(false);
    }

    void dispose() {
        pause();
        disposed = true;
        unobserve();
        if (Build.VERSION.SDK_INT >= 20) setOnApplyWindowInsetsListener(null);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (disposed) return;
        observedTree = getViewTreeObserver();
        observedTree.addOnGlobalLayoutListener(layoutObserver);
        if (Build.VERSION.SDK_INT >= 20) requestApplyInsets();
    }

    @Override protected void onDetachedFromWindow() {
        pause();
        unobserve();
        super.onDetachedFromWindow();
    }

    private void unobserve() {
        if (observedTree != null && observedTree.isAlive()) observedTree.removeOnGlobalLayoutListener(layoutObserver);
        observedTree = null;
    }
}
