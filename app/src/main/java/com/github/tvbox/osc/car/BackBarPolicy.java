package com.github.tvbox.osc.car;

/** 旧「返回杠」的点击规则：短按、静止、单指、未取消才算返回。纯 Java，便于宿主机测试。 */
public final class BackBarPolicy {

    public static final long MAX_TAP_DURATION_MS = 300L;

    private BackBarPolicy() {
    }

    public static boolean isTap(float distancePx, long durationMs, boolean cancelled,
                                boolean multiTouch, float slopPx) {
        float slop = Math.max(0f, slopPx);
        return !cancelled
                && !multiTouch
                && durationMs >= 0L
                && durationMs <= MAX_TAP_DURATION_MS
                && Math.abs(distancePx) < slop;
    }
}
