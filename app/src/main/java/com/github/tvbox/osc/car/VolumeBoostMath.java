package com.github.tvbox.osc.car;

/** 音量增益上限与换算，纯 Java，便于宿主机测试。 */
public final class VolumeBoostMath {

    /**
     * 应用内增益上限（倍数）。r68 起 3.0 = 300%。
     *
     * <p>为什么提到 300%：200% 时 16bit 采样已经在削顶（>16383 被钳住），
     * 实际听感提升被压掉；用户明确要求把上限调到 300%。链路里所有 clamp 都引用这个常量。</p>
     */
    public static final float MAX_GAIN = 3f;

    /** 非 PCM 路径用 LoudnessEnhancer：300% 对应约 +9.5 dB，量化到 25mB。 */
    public static final int MAX_BOOST_MB = 950;
    /** 拖动时的量化步进，避免每个像素都重建效果链。 */
    private static final int QUANTUM_MB = 25;

    private VolumeBoostMath() {
    }

    /** @param gain 0..3 应用内增益；@param factor 0..1 静音/闪避系数 */
    public static int millibel(float gain, float factor) {
        float effective = clamp(gain, 0f, MAX_GAIN) * clamp(factor, 0f, 1f);
        if (effective <= 1f) return 0;
        int raw = Math.round((effective - 1f) * (MAX_BOOST_MB / (MAX_GAIN - 1f)));
        return Math.round(raw / (float) QUANTUM_MB) * QUANTUM_MB;
    }

    /** 播放器自身能接受的部分（0..1）。 */
    public static float playerVolume(float gain) {
        return clamp(gain, 0f, 1f);
    }

    /** PCM owns user gain, while AudioTrack is unity; non-PCM owns attenuation itself. */
    public static float outputVolume(float gain, float factor, boolean pcm) {
        return pcm ? 1f : playerVolume(gain) * clamp(factor, 0f, 1f);
    }

    public static float pcmGain(float gain, float factor) {
        return clamp(gain, 0f, MAX_GAIN) * clamp(factor, 0f, 1f);
    }

    /** Nearest 5-percent detent, computed from the entire gesture rather than move count. */
    public static float gestureVolume(float start, float deltaY, int height) {
        float value = clamp(start, 0f, MAX_GAIN) + deltaY / Math.max(1, height);
        return Math.round(clamp(value, 0f, MAX_GAIN) * 20f) / 20f;
    }

    /** 全链路唯一的增益夹取入口。 */
    public static float clampGain(float value) {
        return clamp(value, 0f, MAX_GAIN);
    }

    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
