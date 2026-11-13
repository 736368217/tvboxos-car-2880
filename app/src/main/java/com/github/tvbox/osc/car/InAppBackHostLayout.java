package com.github.tvbox.osc.car;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.github.tvbox.osc.util.HawkConfig;

import java.lang.ref.WeakReference;

/**
 * 方案 A：Activity DecorView 内的唯一输入拥有者，无 SYSTEM_ALERT_WINDOW / Accessibility。
 *
 * <p>{@code dispatchTouchEvent()} 只询问纯 Java 状态机；命中才消费完整手势并驱动被动绘制层，
 * 未命中一定交给 {@code super.dispatchTouchEvent()}。绘制层不接收 onTouchEvent，
 * 不再有「宿主转发 + 子 View 自己处理 + super 又派发」的双入口。</p>
 */
final class InAppBackHostLayout extends FrameLayout {

    private static final String TAG = "InAppBack";
    private static final long WAKE_COOLDOWN_MS = 1000L;

    private final WeakReference<Activity> owner;
    private final BackAffordanceView affordance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hideTask = () -> setPresentation(false, false);
    private final Runnable rootCheck = this::applyVisibility;

    private EdgeBackPolicy policy;
    private BackDisplayMetrics metrics;
    private EdgeBackPolicy.Mode mode = EdgeBackPolicy.Mode.EDGE_GESTURE;
    private EdgeBackPolicy.Side side = EdgeBackPolicy.Side.LEFT;
    private boolean resumed, disposed, playbackHidden, haptic;
    private boolean shown = true;
    private long lastWakeMs;
    private int hideSeconds = 4;
    private boolean capturing;

    InAppBackHostLayout(Activity activity) {
        super(activity);
        owner = new WeakReference<>(activity);
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= 21) setTranslationZ(1000f);
        affordance = new BackAffordanceView(activity);
        addView(affordance, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        refresh();
    }

    void resume() {
        if (disposed) return;
        resumed = true;
        refresh();
        wakeAt(-1f, -1f, true);
    }

    void refresh() {
        if (disposed) return;
        releaseGesture();
        ModeSide parsed = ModeSide.read();
        mode = parsed.mode;
        side = parsed.side;
        hideSeconds = InAppBackButton.getHideSeconds();
        haptic = InAppBackButton.isHapticEnabled();
        DisplayMetrics dm = displayMetrics();
        float width = getWidth() > 0 ? getWidth() : dm.widthPixels;
        float height = getHeight() > 0 ? getHeight() : dm.heightPixels;
        metrics = BackDisplayMetrics.from(width, height, dm.xdpi, dm.ydpi, dm.densityDpi);
        policy = new EdgeBackPolicy(metrics, mode, side, value -> {
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(value);
        });
        affordance.configure(metrics, mode, side);
        affordance.setGesture(0f, metrics.heightPx / 2f, true);
        android.util.Log.i(TAG, "geom " + metrics.log() + " mode=" + mode + " side=" + side);
        applyVisibility();
        scheduleHide();
    }

    void wake() { wakeAt(-1f, -1f, false); }

    void wakeAt(float x, float y) { wakeAt(x, y, false); }

    private void wakeAt(float x, float y, boolean force) {
        if (disposed || !resumed || playbackHidden) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!force && now - lastWakeMs < WAKE_COOLDOWN_MS) return;
        lastWakeMs = now;
        // 隐藏态只是不画，手势命中判定永远有效；任意 DOWN 都能重新点亮。
        setPresentation(true, false);
        scheduleHide();
    }

    void setPlaybackHidden(boolean hidden) {
        if (playbackHidden == hidden) return;
        playbackHidden = hidden;
        if (hidden) releaseGesture();
        applyVisibility();
    }

    void pause() {
        resumed = false;
        main.removeCallbacks(hideTask);
        main.removeCallbacks(rootCheck);
        releaseGesture();
        setPresentation(false, false);
    }

    void dispose() {
        pause();
        disposed = true;
    }

    void recheckRootAfterNavigation() {
        main.removeCallbacks(rootCheck);
        if (resumed && !disposed) main.post(rootCheck);
    }

    void performBack() {
        android.util.Log.i(TAG, "back_perform page=" + pageName()
                + " mode=" + mode + " state=" + policy.state());
        if (haptic) {
            int type = Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY;
            performHapticFeedback(type);
        }
        InAppBackButton.performBack(owner.get());
    }

    private void applyVisibility() {
        boolean show = resumed && !playbackHidden && InAppBackButton.alive(owner.get());
        affordance.setVisibility(VISIBLE); // 命中判定不依赖 View visibility；绘制由 presentation 控制
        setPresentation(show, false);
        if (!show) main.removeCallbacks(hideTask);
    }

    private void scheduleHide() {
        main.removeCallbacks(hideTask);
        if (!resumed || disposed || playbackHidden || hideSeconds <= 0) return;
        main.postDelayed(hideTask, hideSeconds * 1000L);
    }

    private void setPresentation(boolean visible, boolean pressed) {
        shown = visible;
        affordance.setPresentation(visible, pressed);
    }

    private void releaseGesture() {
        if (policy != null) policy.reset();
        capturing = false;
        affordance.setGesture(0f, metrics == null ? 0f : metrics.heightPx / 2f, true);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (disposed || !resumed || playbackHidden || policy == null) {
            return super.dispatchTouchEvent(event);
        }
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);
        float x = event.getX(actionIndex);
        float y = event.getY(actionIndex);
        long t = event.getEventTime();

        EdgeBackPolicy.Action result;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                result = policy.down(pointerId, x, y, t);
                capturing = result != EdgeBackPolicy.Action.NONE;
                log("down", x, y, result, action);
                wakeAt(x, y, result != EdgeBackPolicy.Action.NONE);
                if (capturing) main.removeCallbacks(hideTask);
                if (!capturing) return super.dispatchTouchEvent(event);
                affordance.setPresentation(true, mode == EdgeBackPolicy.Mode.BACK_BAR);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!capturing) return super.dispatchTouchEvent(event);
                policy.multiTouch();
                policy.cancel(event.getPointerId(0));
                log("pointer_down", x, y, EdgeBackPolicy.Action.CONSUME, action);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!capturing) return super.dispatchTouchEvent(event);
                result = policy.move(event.getPointerId(0), event.getX(0), event.getY(0), t);
                affordance.setGesture(policy.visualWidth(), policy.pullY(), policy.pullingLeft());
                log("move", event.getX(0), event.getY(0), result, action);
                return true; // 捕获后完整手势流必须消费，不能把半段 MOVE 漏给内容 View
            case MotionEvent.ACTION_UP:
                if (!capturing) return super.dispatchTouchEvent(event);
                result = policy.up(event.getPointerId(0), event.getX(0), event.getY(0), t);
                log("up", event.getX(0), event.getY(0), result, action);
                affordance.setGesture(0f, policy.pullY(), policy.pullingLeft());
                affordance.setPresentation(true, false);
                scheduleHide();
                policy.reset();
                capturing = false;
                if (result == EdgeBackPolicy.Action.BACK) performBack();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (!capturing) return super.dispatchTouchEvent(event);
                result = policy.cancel(event.getPointerId(0));
                log("cancel", event.getX(0), event.getY(0), result, action);
                affordance.setGesture(0f, metrics.heightPx / 2f, true);
                affordance.setPresentation(shown, false);
                policy.reset();
                capturing = false;
                scheduleHide();
                return true;
            default:
                if (capturing) return true;
                return super.dispatchTouchEvent(event);
        }
    }

    private void log(String action, float x, float y, EdgeBackPolicy.Action result, int rawAction) {
        android.util.Log.i(TAG, "edge_back page=" + pageName()
                + " mode=" + mode + " action=" + action + " raw=" + rawAction
                + " x=" + (int) x + " y=" + (int) y
                + " hitPx=" + (int) metrics.hitPx
                + " state=" + policy.state()
                + " inward=" + (int) policy.maxInward()
                + " visual=" + (int) policy.visualWidth()
                + " velocity=" + (int) policy.velocity()
                + " result=" + result);
    }

    private String pageName() {
        Activity a = owner.get();
        return a == null ? "null" : a.getClass().getSimpleName();
    }

    private DisplayMetrics displayMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        Activity a = owner.get();
        if (a != null) {
            WindowManager wm = (WindowManager) a.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) wm.getDefaultDisplay().getMetrics(dm);
        }
        if (dm.widthPixels <= 0) dm = getResources().getDisplayMetrics();
        return dm;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refresh();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) refresh();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) { return false; }
    @Override
    public boolean onTouchEvent(MotionEvent event) { return false; }

    private static final class ModeSide {
        final EdgeBackPolicy.Mode mode;
        final EdgeBackPolicy.Side side;

        ModeSide(EdgeBackPolicy.Mode mode, EdgeBackPolicy.Side side) {
            this.mode = mode;
            this.side = side;
        }

        static ModeSide read() {
            String raw = InAppBackButton.getMode();
            EdgeBackPolicy.Mode mode = HawkConfig.BACK_MODE_BAR.equals(raw)
                    ? EdgeBackPolicy.Mode.BACK_BAR : EdgeBackPolicy.Mode.EDGE_GESTURE;
            String s = InAppBackButton.getSide();
            EdgeBackPolicy.Side side = HawkConfig.BACK_SIDE_RIGHT.equals(s)
                    ? EdgeBackPolicy.Side.RIGHT
                    : HawkConfig.BACK_SIDE_BOTH.equals(s)
                    ? EdgeBackPolicy.Side.BOTH : EdgeBackPolicy.Side.LEFT;
            return new ModeSide(mode, side);
        }
    }
}
