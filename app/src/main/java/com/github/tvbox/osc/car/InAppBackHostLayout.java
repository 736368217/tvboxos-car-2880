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
    private float displayDensity = 1f, tokenScale = 1f;
    private float insetLeft, insetTop, insetRight, insetBottom;
    private float safeLeft, safeTop, safeRight, safeBottom;

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
        if (disposed || !resumed) return;
        ball.wake();
        edge.wake();
        swipe.wake();
        // Defer visibility changes until after the initiating DOWN has reached its original target.
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

    void performBack() { InAppBackButton.performBack(owner.get()); }

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

        ball.setVisibility(showBall ? VISIBLE : INVISIBLE);
        edge.setVisibility(showEdge ? VISIBLE : INVISIBLE);
        swipe.setVisibility(showSwipe ? VISIBLE : INVISIBLE);
        ball.setActive(showBall);
        edge.setActive(showEdge);
        swipe.setActive(showSwipe);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) wake();
        return super.dispatchTouchEvent(event);
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
