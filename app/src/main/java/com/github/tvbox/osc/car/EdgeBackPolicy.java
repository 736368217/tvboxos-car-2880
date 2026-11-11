package com.github.tvbox.osc.car;

/**
 * 方案 A：返回组件唯一输入状态机（纯 Java，无 Android 依赖）。
 *
 * <p>两种互斥方案共用一个入口：默认 EDGE_GESTURE（窄触发带 + 小幅橡皮筋，松手返回）；
 * 备用 BACK_BAR（旧返回杠，短按返回）。宿主只负责把 MotionEvent 的坐标/时间喂进来，
 * 并按返回的 Action 决定消费、穿透或执行 App 内导航。</p>
 */
public final class EdgeBackPolicy {

    public enum Mode { EDGE_GESTURE, BACK_BAR }
    public enum State { IDLE, ARMED, DRAGGING, COMMIT, CANCEL, BAR_ARMED, BAR_COMMIT }
    public enum Action { NONE, CONSUME, BACK }
    public enum Side { LEFT, RIGHT, BOTH }

    private final BackDisplayMetrics metrics;
    private final Mode mode;
    private final Side side;
    private final ParentBridge parent;

    private State state = State.IDLE;
    private int activePointer = -1;
    private float downX, downY, lastX, lastY;
    private long downTime, lastMoveTime;
    private float maxInward, velocityPxPerSec;
    private int hitDirection;
    private boolean multiTouch, cancelled, capture, parentDisallowed;

    public interface ParentBridge { void requestDisallowInterceptTouchEvent(boolean value); }
    private static final ParentBridge NOOP_PARENT = value -> { };

    public EdgeBackPolicy(BackDisplayMetrics metrics, Mode mode, Side side, ParentBridge parent) {
        this.metrics = metrics;
        this.mode = mode;
        this.side = side;
        this.parent = parent == null ? NOOP_PARENT : parent;
    }

    public BackDisplayMetrics metrics() { return metrics; }
    public Mode mode() { return mode; }
    public Side side() { return side; }
    public State state() { return state; }
    public float maxInward() { return maxInward; }
    public float velocity() { return velocityPxPerSec; }
    public boolean consumed() { return capture; }
    public boolean multiTouchDetected() { return multiTouch; }
    public boolean committed() { return state == State.COMMIT || state == State.BAR_COMMIT; }

    private void setParentDisallow(boolean value) {
        if (parentDisallowed == value) return;
        parentDisallowed = value;
        parent.requestDisallowInterceptTouchEvent(value);
    }

    /** @return -1 左边缘，+1 右边缘，0 未命中。 */
    public int hitSide(float x) {
        float w = metrics.widthPx;
        float hit = metrics.hitPx;
        boolean left = side != Side.RIGHT;
        boolean right = side != Side.LEFT;
        if (left && x >= 0f && x <= hit) return -1;
        if (right && x >= w - hit && x <= w) return 1;
        return 0;
    }

    /** 备用返回杠：点击热区固定在垂直居中的小杠周围；空白区永不拦截。 */
    public boolean hitBar(float x, float y) {
        boolean right = side == Side.RIGHT;
        float x0 = metrics.barHitLeft(right);
        float y0 = (metrics.heightPx - metrics.barHitHeightPx) / 2f;
        return x >= x0 && x <= x0 + metrics.barHitWidthPx
                && y >= y0 && y <= y0 + metrics.barHitHeightPx;
    }

    public float barLeft() { return metrics.barLeft(side == Side.RIGHT); }
    public float barHitLeft() { return metrics.barHitLeft(side == Side.RIGHT); }

    public Action down(int pointerId, float x, float y, long timeMs) {
        reset();
        activePointer = pointerId;
        downX = lastX = x;
        downY = lastY = y;
        downTime = lastMoveTime = timeMs;
        if (mode == Mode.BACK_BAR) {
            if (!hitBar(x, y)) return Action.NONE;
            state = State.BAR_ARMED;
            capture = true;
            return Action.CONSUME;
        }
        hitDirection = hitSide(x);
        if (hitDirection == 0) return Action.NONE;
        state = State.ARMED;
        capture = true;
        return Action.CONSUME;
    }

    public Action move(int pointerId, float x, float y, long timeMs) {
        if (!capture || pointerId != activePointer
                || state == State.IDLE || state == State.COMMIT || state == State.CANCEL
                || state == State.BAR_COMMIT) return Action.NONE;
        float dx = x - downX;
        float dy = y - downY;
        long dt = Math.max(1L, timeMs - lastMoveTime);
        velocityPxPerSec = (x - lastX) / (dt / 1000f);
        lastX = x;
        lastY = y;
        lastMoveTime = timeMs;

        if (state == State.BAR_ARMED) {
            if (Math.hypot(dx, dy) > metrics.slopPx) {
                state = State.CANCEL;
                cancelled = true;
            }
            return Action.CONSUME;
        }

        float inward = hitDirection < 0 ? x - downX : downX - x;
        maxInward = Math.max(maxInward, Math.max(0f, inward));
        if (state == State.ARMED) {
            float total = (float) Math.hypot(dx, dy);
            if (total < metrics.slopPx) return Action.CONSUME;
            // 与播放手势共用「累计位移 + 1.3 倍水平占优」，斜向手势不误触。
            if (Math.abs(dx) > Math.abs(dy) * 1.3f) {
                state = State.DRAGGING;
                setParentDisallow(true);
            } else {
                state = State.CANCEL;
                cancelled = true;
            }
        }
        return Action.CONSUME;
    }

    public Action up(int pointerId, float x, float y, long timeMs) {
        if (!capture || pointerId != activePointer) return Action.NONE;
        Action result = Action.NONE;
        if (mode == Mode.BACK_BAR) {
            boolean tap = BackBarPolicy.isTap((float) Math.hypot(x - downX, y - downY),
                    timeMs - downTime, cancelled, multiTouch, metrics.slopPx);
            if (tap) {
                state = State.BAR_COMMIT;
                result = Action.BACK;
            } else {
                state = State.CANCEL;
            }
        } else if (state == State.DRAGGING) {
            float flingThreshold = BackDisplayMetrics.FLING_MM_PER_SEC * metrics.pxPerMmX;
            boolean fastInward = hitDirection < 0 ? velocityPxPerSec >= flingThreshold
                    : velocityPxPerSec <= -flingThreshold;
            if (maxInward >= metrics.triggerPx || fastInward) {
                state = State.COMMIT;
                result = Action.BACK;
            } else {
                state = State.CANCEL;
            }
        } else {
            state = State.CANCEL;
        }
        setParentDisallow(false);
        return result;
    }

    public Action cancel(int pointerId) {
        if (!capture || pointerId != activePointer) return Action.NONE;
        state = State.CANCEL;
        cancelled = true;
        setParentDisallow(false);
        return Action.CONSUME;
    }

    public void multiTouch() {
        multiTouch = true;
        cancelled = true;
    }

    /** 明确释放手势，避免隐藏态/页面切换后下一次 DOWN 仍处于旧 capture。 */
    public void reset() {
        setParentDisallow(false);
        state = State.IDLE;
        activePointer = -1;
        maxInward = 0f;
        velocityPxPerSec = 0f;
        hitDirection = 0;
        multiTouch = false;
        cancelled = false;
        capture = false;
    }

    /** 视觉拉伸宽度：永不越过自适应 visualPx。 */
    public float visualWidth() {
        if (state != State.DRAGGING && state != State.COMMIT) return 0f;
        return Math.min(metrics.visualPx, maxInward);
    }

    public float pullY() { return lastY; }
    /** 当前拖动从哪条边缘拉出；未命中时默认左，用于 BOTH/右边缘镜像。 */
    public boolean pullingLeft() { return hitDirection != 1; }
}
