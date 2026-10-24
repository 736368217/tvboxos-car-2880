package com.github.tvbox.osc.car;

/** Geometry and gesture rules in dp, independent of Android for host-side tests. */
public final class BackNavPolicy {
    public static final int POSITION_SCALE = 10000;
    public static final float TOUCH_SLOP_DP = 8f;
    public static final float TRIGGER_DP = 48f;
    /** 贴边横滑返回：贴边判定宽度与默认触发距离（dp）。 */
    public static final float SWIPE_EDGE_DP = 24f;
    public static final float SWIPE_TRIGGER_DP = 84f;
    public static final float SWIPE_MAX_DP = 320f;
    public static final float DAMPING_RATIO = .75f;
    public static final float STIFFNESS = 400f;
    public static final long LONG_PRESS_MS = 400;

    /** Scale the reference tokens with the car viewport, not Android's phone-density bucket. */
    public static float carTokenScale(float width, float height, float displayDensity) {
        float density = Float.isNaN(displayDensity) || displayDensity <= 0 ? 1 : displayDensity;
        if (width <= 0 || height <= 0 || Float.isNaN(width) || Float.isNaN(height)) return density;
        float proportional = Math.min(width / 1280f, height / 720f) * 1.5f;
        float minimumTouch = density * 48f / 72f;
        return Math.max(.01f, Math.min(Math.max(proportional, minimumTouch), Math.min(width, height) / 96f));
    }

    public static float capsuleWidth(float pull, float progress) {
        return Math.max(Math.max(0, pull) * 1.45f, 96f * clamp(progress, 0, 1));
    }
    private BackNavPolicy() { }

    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    public static int normalize(float value, float min, float max) {
        return max <= min ? 0 : Math.round(clamp((value - min) / (max - min), 0, 1) * POSITION_SCALE);
    }

    public static float restore(int normalized, float min, float max) {
        return min + (Math.max(min, max) - min) * clamp(normalized, 0, POSITION_SCALE) / POSITION_SCALE;
    }

    public static boolean contains(float x, float y, float left, float top, float width, float height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    /** -1 = left local box, +1 = right local box, 0 = pass through. */
    public static int edgeHit(float x, float y, float width, float centerY, boolean left, boolean right) {
        return edgeHit(x, y, width, centerY, left, right, 32f, 140f);
    }

    /** 可配置热区：hitWidthDp 从屏幕边缘向内，hitHeightDp 以导引小棍中心上下对称。 */
    public static int edgeHit(float x, float y, float width, float centerY, boolean left, boolean right,
                              float hitWidthDp, float hitHeightDp) {
        float w = Math.max(24f, hitWidthDp);
        float half = Math.max(48f, hitHeightDp) / 2f;
        if (left && contains(x, y, 0, centerY - half, w, half * 2f)) return -1;
        if (right && contains(x, y, width - w, centerY - half, w, half * 2f)) return 1;
        return 0;
    }

    public static boolean ballTap(float distanceDp, long durationMs, boolean dragged, boolean cancelled) {
        return !cancelled && !dragged && distanceDp < TOUCH_SLOP_DP && durationMs >= 0 && durationMs < 200;
    }

    public static float pullWidth(float rawInwardDp) {
        return 72f * (float) Math.tanh(Math.max(0, rawInwardDp) / 120f);
    }

    public static boolean dockLeft(float centerX, float windowWidth, float velocityDpPerSecond) {
        return Math.abs(velocityDpPerSecond) > 650f
                ? velocityDpPerSecond < 0 : centerX < windowWidth / 2f;
    }

    public static float idleOffset(float visualSize, boolean dockLeft, float idleFraction) {
        return visualSize * .35f * (dockLeft ? -1 : 1) * clamp(idleFraction, 0, 1);
    }

    /** Unit-mass underdamped spring: x'' + 2*zeta*sqrt(k)*x' + k*(x-target) = 0. */
    public static float springPosition(float start, float target, float velocity, float seconds) {
        double omega = Math.sqrt(STIFFNESS);
        double decay = DAMPING_RATIO * omega;
        double damped = omega * Math.sqrt(1 - DAMPING_RATIO * DAMPING_RATIO);
        double t = Math.max(0, seconds);
        double displacement = start - target;
        return target + (float) (Math.exp(-decay * t) * (displacement * Math.cos(damped * t)
                + (velocity + decay * displacement) / damped * Math.sin(damped * t)));
    }

    /** A top-left position range. Insets shrink only when the window cannot fit the touch box. */
    public static final class Bounds {
        public final float minX, maxX, minY, maxY;

        public Bounds(float width, float height, float boxWidth, float boxHeight,
                      float left, float top, float right, float bottom) {
            float availableX = Math.max(0, width - boxWidth);
            float availableY = Math.max(0, height - boxHeight);
            float sx = left + right > availableX && left + right > 0 ? availableX / (left + right) : 1;
            float sy = top + bottom > availableY && top + bottom > 0 ? availableY / (top + bottom) : 1;
            minX = Math.max(0, left * sx);
            maxX = Math.max(minX, availableX - right * sx);
            minY = Math.max(0, top * sy);
            maxY = Math.max(minY, availableY - bottom * sy);
        }
    }

    public static final class EdgeGesture {
        public enum State { IDLE, WAITING, PULLING, REPOSITIONING, REJECTED }

        private State state = State.IDLE;
        private float downX, downY, rawPull, deltaY;
        private boolean left, armed, feedbackSent;

        public void begin(float xDp, float yDp, boolean fromLeft) {
            downX = xDp;
            downY = yDp;
            left = fromLeft;
            rawPull = deltaY = 0;
            armed = feedbackSent = false;
            state = State.WAITING;
        }

        /** Returns true once per gesture, on the first unarmed -> armed threshold crossing. */
        public boolean move(float xDp, float yDp) {
            float dx = xDp - downX;
            deltaY = yDp - downY;
            float inward = left ? dx : -dx;
            if (state == State.WAITING && Math.hypot(dx, deltaY) >= TOUCH_SLOP_DP) {
                state = inward >= TOUCH_SLOP_DP && Math.abs(dx) > 1.5f * Math.abs(deltaY)
                        ? State.PULLING : State.REJECTED;
            }
            if (state != State.PULLING) return false;
            rawPull = Math.max(0, inward);
            armed = rawPull >= TRIGGER_DP && Math.abs(dx) > 1.5f * Math.abs(deltaY);
            if (armed && !feedbackSent) {
                feedbackSent = true;
                return true;
            }
            return false;
        }

        public boolean longPress() {
            if (state != State.WAITING) return false;
            state = State.REPOSITIONING;
            return true;
        }

        /**
         * 车机交互：在导引小棍上「点一下」直接返回（无需拖到 48dp），拖到阈值也返回；
         * 被判定为纵向调位、斜向/向外滑动或 CANCEL 的手势永不返回。
         */
        public boolean end(boolean cancelled) {
            boolean back = !cancelled && (state == State.WAITING || (state == State.PULLING && armed));
            state = State.IDLE;
            armed = false;
            return back;
        }

        public State state() { return state; }
        public float rawPull() { return rawPull; }
        public float deltaY() { return deltaY; }
        public boolean isArmed() { return armed; }
    }
}
