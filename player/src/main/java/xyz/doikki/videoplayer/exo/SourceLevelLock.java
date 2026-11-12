package xyz.doikki.videoplayer.exo;

/**
 * 静态片源电平补偿（source level lock）。
 *
 * <p>背景：车机上同一系统音量档，音乐母带接近 0 dBFS，而视频音轨实测只有约 −33 dBFS
 * （r70 现场 <code>level_in/4096 ≈ 720/32767 = 2.2%</code>）。用户为了听清视频拉高系统音量，
 * 回头听歌就过响，只能来回调整。</p>
 *
 * <p>与 r73–r76 被删除的「自动电平对齐」的根本区别：
 * <b>本类只在每个片源开头测量一次，随即锁定，整片不再变化。</b>
 * r73 每约 1 秒按滑动窗口重算 makeup，安静段抬升、响段回落，用户实测「忽大忽小」。
 * 静态锁定从根本上消除这种起伏，代价是不同片源之间的补偿量不同（但同一片源内绝对稳定）。</p>
 *
 * <p>纯 Java，无 Android 依赖，可在宿主机单测。</p>
 */
public final class SourceLevelLock {

    /** 目标峰值：约 −6 dBFS，接近流行音乐母带电平。 */
    public static final float TARGET_PEAK = 0.5f;

    /** 补偿上限（倍数）。4.0 = +12 dB，覆盖实测 −33 dBFS 的大部分差距。 */
    public static final float MAX_MAKEUP = 4f;

    /** 测样窗口（秒）。太短易被静音开头误导，太长用户会感到"越放越响"。 */
    public static final float MEASURE_SECONDS = 4f;

    /** 低于此峰值视为静音，不抬底噪。 */
    private static final float SILENCE_PEAK = 0.002f;

    private float windowPeak;
    private long windowSamples;
    private long windowTargetSamples = 1;
    private float lockedMakeup = 1f;
    private boolean locked;
    /** 淡入进度 0..1，避免锁定瞬间的音量跳变被听出来。 */
    private float fade;

    private int sampleRate = 48000;

    /** 开关：关闭时不测量、不补偿，输出恒为原样（等价于该功能不存在）。 */
    private volatile boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 开始一个新片源。sampleRate 用于把秒换算成样本数。
     * 每次 prepare / 切源都必须调用，否则补偿量会沿用到下一个片源。
     */
    public void begin(int sampleRate) {
        this.sampleRate = sampleRate > 0 ? sampleRate : 48000;
        windowTargetSamples = (long) (this.sampleRate * MEASURE_SECONDS);
        windowPeak = 0f;
        windowSamples = 0;
        lockedMakeup = 1f;
        locked = false;
        fade = 0f;
    }

    /**
     * 喂入一个 PCM buffer 的峰值与样本数，返回当前应使用的补偿倍数。
     * 锁定前始终返回 1.0（不改动声音），锁定后返回固定值并淡入。
     */
    public float apply(float peak, int samples) {
        if (samples <= 0) return currentMakeup();
        if (!enabled) return 1f;
        if (!locked) {
            if (peak > windowPeak) windowPeak = peak;
            windowSamples += samples;
            if (windowSamples >= windowTargetSamples) {
                lockedMakeup = computeMakeup(windowPeak);
                locked = true;
                log("makeup_locked peak="
                        + String.format(java.util.Locale.US, "%.4f", windowPeak)
                        + " peak_dbfs=" + String.format(java.util.Locale.US, "%.1f",
                                windowPeak > 0 ? 20 * Math.log10(windowPeak) : -90d)
                        + " makeup=" + String.format(java.util.Locale.US, "%.3f", lockedMakeup)
                        + " samples=" + windowSamples);
            } else {
                return 1f;
            }
        }
        // 锁定后 0.5 秒淡入，避免突变。
        if (fade < 1f) {
            fade = Math.min(1f, fade + samples / (sampleRate * 0.5f));
        }
        return 1f + (lockedMakeup - 1f) * fade;
    }

    /** 当前生效的补偿（供日志/诊断）。 */
    public float currentMakeup() {
        if (!enabled || !locked) return 1f;
        return 1f + (lockedMakeup - 1f) * fade;
    }

    public boolean isLocked() { return locked; }
    public float lockedValue() { return lockedMakeup; }
    public float measuredPeak() { return windowPeak; }

    public float measuredDbfs() {
        return windowPeak > 0 ? (float) (20 * Math.log10(windowPeak)) : -90f;
    }

    private static float computeMakeup(float peak) {
        if (peak <= SILENCE_PEAK) return 1f;      // 静音不抬底噪
        if (peak >= TARGET_PEAK) return 1f;       // 本来够响，不压
        float want = TARGET_PEAK / peak;
        return Math.min(MAX_MAKEUP, want);
    }

    private static void log(String message) {
        try {
            android.util.Log.i("CarAudioGain", message);
        } catch (Throwable ignored) {
            // Android stub throws on a plain host JVM.
        }
    }
}
