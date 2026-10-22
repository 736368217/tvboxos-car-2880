package com.github.tvbox.osc.backbar;

/**
 * Pure gesture rules for the in-app Back bar. Kept free of Android imports so the host test can
 * exercise threshold, duration and multi-touch behaviour without an emulator.
 */
public final class BackBarInteractionPolicy {
    /** A deliberate tap is short; a long press must not turn into an accidental Back. */
    public static final long MAX_TAP_DURATION_MS = 300L;
    public static final long DEFAULT_AUTO_HIDE_MS = 4_000L;

    private BackBarInteractionPolicy() {
    }

    /** Legacy zero/invalid idle values become four seconds; permanent visibility is not offered. */
    public static long normalizeAutoHideMillis(long millis) {
        return millis <= 0L ? DEFAULT_AUTO_HIDE_MS : Math.max(1_000L, millis);
    }

    /** True only for a short, stationary, single-pointer gesture that was not cancelled. */
    public static boolean isTap(float distancePx, long durationMs, boolean cancelled,
                                boolean multiTouch, float touchSlopPx) {
        float slop = Math.max(0f, touchSlopPx);
        return !cancelled
                && !multiTouch
                && durationMs >= 0L
                && durationMs <= MAX_TAP_DURATION_MS
                && Math.abs(distancePx) < slop;
    }
}
