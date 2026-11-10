package xyz.doikki.videoplayer.exo;

/**
 * 自动电平对齐：把「网络视频音轨太轻」提升到与音乐相当的目标电平。
 *
 * <p>r76 用户反馈：手动顶到 300% 仍比音乐轻、且「没同步」。旧实现只在用户保持 100% 时
 * 启用自动对齐，并把总增益封顶到 4×；现场日志 {@code gain=3.0 makeup=1.0
 * combined=3.0 autoLevel=false} 正是「自动对齐让位」的硬证据。现在自动对齐**始终叠加**：
 * 用户增益是相对音量的微调，自动补增益独立把轻音轨拉向音乐基准；组合增益上限放宽到
 * 用户可接受的最大手动增益（500%）之上，由软限幅守住满量程、避免削顶失真。</p>
 *
 * <p>增益变化用一阶平滑（attack 慢、release 更慢），避免忽大忽小和抽气声；纯 Java，
 * 便于宿主机测试。</p>
 */
public final class AutoLevel {

    /** 目标峰值占满量程的比例：约 -6 dBFS，留余量给瞬态与用户增益。 */
    public static final float TARGET_PEAK = 0.5f;
    /** 自动对齐允许的最大补增益（约 +12 dB），防止把噪声抬成爆音。 */
    public static final float MAX_MAKEUP = 4f;
    /** 低于这个峰值视为静音，不抬增益。 */
    public static final float SILENCE_PEAK = 0.002f;
    /** 自动对齐 × 用户增益的总增益上限；留到 20×，实际由 softClip 守住输出电平。 */
    public static final float MAX_TOTAL_GAIN = 20f;

    private static final float ATTACK = 0.02f;
    private static final float RELEASE = 0.005f;
    private static final int WINDOW_SAMPLES = 48000 * 2;

    private float measuredPeak;
    private float appliedMakeup = 1f;
    private int windowCount;
    private long totalSamples;

    /** 每个 PCM buffer 调用一次；返回本 buffer 应采用的自动补增益。 */
    public float update(float bufferPeakNormalized, int sampleCount) {
        totalSamples += Math.max(0, sampleCount);
        if (bufferPeakNormalized > measuredPeak) measuredPeak = bufferPeakNormalized;
        windowCount += Math.max(0, sampleCount);
        if (windowCount < WINDOW_SAMPLES) return appliedMakeup;

        float peak = measuredPeak;
        measuredPeak = 0f;
        windowCount = 0;

        float target;
        if (peak <= SILENCE_PEAK) {
            target = 1f;
        } else {
            target = Math.min(MAX_MAKEUP, TARGET_PEAK / peak);
            if (target < 1f) target = 1f;   // 只补轻音轨，不压本来就很响的
        }
        float coeff = target > appliedMakeup ? ATTACK : RELEASE;
        appliedMakeup += (target - appliedMakeup) * coeff;
        if (appliedMakeup < 1f) appliedMakeup = 1f;
        if (appliedMakeup > MAX_MAKEUP) appliedMakeup = MAX_MAKEUP;
        return appliedMakeup;
    }

    /** 组合：自动对齐 × 用户增益 × duck 因子，总增益封顶。 */
    public static float combined(float makeup, float userGain, float factor) {
        if (Float.isNaN(makeup) || Float.isNaN(userGain) || Float.isNaN(factor)) return 1f;
        float total = Math.max(0f, makeup) * Math.max(0f, userGain)
                * Math.max(0f, Math.min(1f, factor));
        return Math.min(MAX_TOTAL_GAIN, total);
    }

    /** 软限幅：0.72 以下线性，以上平滑压回 ±1，避免硬削顶失真。 */
    public static float softClip(float value) {
        if (Float.isNaN(value)) return 0f;
        float sign = value < 0f ? -1f : 1f;
        float x = Math.abs(value);
        if (x <= 0.72f) return value;
        float over = x - 0.72f;
        return sign * (0.72f + 0.28f * (1f - (float) Math.exp(-over / 0.42f)));
    }

    public float makeup() { return appliedMakeup; }
    public long totalSamples() { return totalSamples; }

    /** 切换片源/重建 sink 时调用。 */
    public void reset() {
        measuredPeak = 0f;
        appliedMakeup = 1f;
        windowCount = 0;
    }
}
