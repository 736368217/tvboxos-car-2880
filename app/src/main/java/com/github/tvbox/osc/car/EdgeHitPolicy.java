package com.github.tvbox.osc.car;

/**
 * 边缘返回命中判定：整条屏幕边缘带，而不是「小棍附近一小块」。
 *
 * <p>旧版 {@code BackNavPolicy.edgeHit} 要求手指落在导引小棍中心上下 ±70dp 的方框内，
 * 播放页/详情页/全屏页手指稍偏就返回 0，整条手势直接失效。对标作品用的是全高
 * Accessibility Overlay，这里同样按「x 在边缘宽度内 + y 在可用高度内」判定。</p>
 */
public final class EdgeHitPolicy {

    private EdgeHitPolicy() {
    }

    /** @return -1 左边缘，+1 右边缘，0 不命中（必须完全穿透给页面内容）。 */
    public static int hit(float x, float y, float width, float top, float bottom,
                          float edgeWidth, boolean left, boolean right) {
        if (y < top || y > bottom) return 0;
        float w = Math.max(0f, edgeWidth);
        if (left && x >= 0f && x <= w) return -1;
        if (right && x >= width - w && x <= width) return 1;
        return 0;
    }

    /** 唤醒冷却是否允许这次「点亮」；边缘手势已确认命中时必须无条件放行。 */
    public static boolean allowWake(long nowMs, long lastWakeMs, long cooldownMs,
                                    boolean edgeGesture) {
        return edgeGesture || nowMs - lastWakeMs >= cooldownMs;
    }
}
