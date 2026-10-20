package xyz.doikki.videoplayer.render;

/** Pure timing/policy model shared by the renderer and host acceptance tests. */
public final class VideoPostProcessPolicy {
    public static final float MAX_BLEND_ALPHA = 0.28f;
    /** The renderer's interpolation presentation cadence is capped at the display target. */
    public static final float INTERPOLATION_OUTPUT_FPS = 60f;
    public static final float DIFF_LOW = 0.015f;
    public static final float DIFF_HIGH = 0.10f;
    public static final long MIN_INTERVAL_NS = 8_000_000L;
    public static final long MAX_INTERVAL_NS = 150_000_000L;
    private static final long DEFAULT_INTERVAL_NS = 41_708_333L;

    private long lastArrivalNs;
    private long lastTimestampNs;
    private long averageIntervalNs = DEFAULT_INTERVAL_NS;
    private boolean previousAvailable;

    public void reset() {
        lastArrivalNs = 0;
        lastTimestampNs = 0;
        averageIntervalNs = DEFAULT_INTERVAL_NS;
        previousAvailable = false;
    }

    /** Timestamp continuity catches seeks; wall time determines the display subframe phase. */
    public void onSourceFrame(long timestampNs, long arrivalNs) {
        long wallInterval = lastArrivalNs > 0 ? arrivalNs - lastArrivalNs : 0;
        long mediaInterval = lastTimestampNs > 0 ? timestampNs - lastTimestampNs : 0;
        previousAvailable = wallInterval >= MIN_INTERVAL_NS && wallInterval <= MAX_INTERVAL_NS
                && (timestampNs == 0 || (mediaInterval >= MIN_INTERVAL_NS && mediaInterval <= MAX_INTERVAL_NS));
        if (previousAvailable) {
            long interval = mediaInterval > 0 ? mediaInterval : wallInterval;
            averageIntervalNs = (averageIntervalNs * 3 + interval) / 4;
        } else {
            averageIntervalNs = DEFAULT_INTERVAL_NS;
        }
        lastTimestampNs = timestampNs;
        lastArrivalNs = arrivalNs;
    }

    public float blendAlpha(long nowNs, boolean enabled, boolean playing) {
        if (!enabled || !playing || !previousAvailable) return 0f;
        long elapsed = nowNs - lastArrivalNs;
        if (elapsed <= 0) return MAX_BLEND_ALPHA;
        long intervalNs = Math.max(MIN_INTERVAL_NS, averageIntervalNs);
        float phase = (float) elapsed / intervalNs;
        if (phase >= 1f) return 0f;
        // Keep a bounded previous-frame contribution over the whole source interval so
        // 24/25/30fps material produces a visible 60Hz temporal output path.
        float smooth = phase * phase * (3f - 2f * phase);
        return MAX_BLEND_ALPHA * (1f - smooth);
    }

    public long frameIntervalNs() { return averageIntervalNs; }

    public static float adaptiveAlpha(float alpha, float difference) {
        float t = Math.max(0f, Math.min(1f, (difference - DIFF_LOW) / (DIFF_HIGH - DIFF_LOW)));
        return Math.max(0f, Math.min(MAX_BLEND_ALPHA, alpha)) * (1f - t * t * (3f - 2f * t));
    }

    public static boolean superResolutionActive(boolean enabled, int sourceWidth, int sourceHeight,
                                                 int viewportWidth, int viewportHeight) {
        // SGSR is also useful as an edge reconstruction pass at 1:1 output. Do not
        // silently disable it merely because the source already covers the viewport.
        return enabled && sourceWidth > 0 && sourceHeight > 0 && viewportWidth > 0 && viewportHeight > 0;
    }

    public static long frameBufferBytes(int width, int height) {
        return width > 0 && height > 0 ? (long) width * height * 4L * 2L : 0L;
    }

    public static boolean enabledFromPreference(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        if ("1".equals(value) || "true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("0".equals(value) || "false".equalsIgnoreCase(String.valueOf(value))) return false;
        return defaultValue;
    }

}
