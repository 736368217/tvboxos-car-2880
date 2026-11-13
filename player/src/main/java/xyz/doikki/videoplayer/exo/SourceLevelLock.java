package xyz.doikki.videoplayer.exo;

/** Per-source peak compensation. This does not measure perceived loudness or system routing. */
public final class SourceLevelLock {
    public static final float TARGET_PEAK = .5f;
    public static final float MAX_MAKEUP = 4f;
    public static final float MEASURE_SECONDS = 4f;
    private static final float SILENCE_PEAK = .002f;
    private int sampleRate = 48000, channels = 1;
    private long measuredFrames;
    private float peak, makeup = 1f, fade;
    private boolean enabled = true, locked;

    public synchronized void setEnabled(boolean value) {
        if (enabled != value) { enabled = value; reset(); }
    }
    public synchronized boolean isEnabled() { return enabled; }
    public synchronized void begin(int rate) { begin(rate, 1); }
    public synchronized void begin(int rate, int channelCount) {
        sampleRate = rate > 0 ? rate : 48000;
        channels = Math.max(1, channelCount);
        reset();
    }
    private void reset() {
        measuredFrames = 0; peak = 0; makeup = 1f; fade = 0; locked = false;
    }
    /** sampleCount includes every channel; only buffers containing useful sound count toward measurement. */
    public synchronized float apply(float bufferPeak, int sampleCount) {
        if (!enabled) return 1f;
        long frames = Math.max(0, sampleCount) / channels;
        if (frames == 0) return currentMakeup();
        if (!locked) {
            // A silent opening must not permanently lock the entire source to unity.
            if (!Float.isNaN(bufferPeak) && !Float.isInfinite(bufferPeak) && bufferPeak > SILENCE_PEAK) {
                peak = Math.max(peak, bufferPeak);
                measuredFrames += frames;
            }
            if (measuredFrames < (long)(sampleRate * MEASURE_SECONDS)) return 1f;
            makeup = Math.max(1f, Math.min(MAX_MAKEUP, TARGET_PEAK / peak));
            locked = true;
            log("makeup_locked peak=" + peak + " makeup=" + makeup + " frames=" + measuredFrames + " channels=" + channels);
            return 1f;
        }
        fade = Math.min(1f, fade + frames / (sampleRate * .5f));
        return currentMakeup();
    }
    public synchronized float currentMakeup() { return !enabled || !locked ? 1f : 1f + (makeup - 1f) * fade; }
    public synchronized boolean isLocked() { return locked; }
    public synchronized float lockedValue() { return makeup; }
    public synchronized float measuredPeak() { return peak; }
    public synchronized long measuredFrames() { return measuredFrames; }
    public synchronized float measuredDbfs() { return peak > 0 ? (float)(20 * Math.log10(peak)) : -90f; }
    private static void log(String text) {
        try { android.util.Log.i("CarAudioGain", text); } catch (Throwable ignored) { }
    }
}
