package com.github.tvbox.osc.car;

/**
 * 应用内音量 → LoudnessEnhancer 目标增益（mB）的换算，纯 Java，便于宿主机测试。
 *
 * <p>播放器自身的 setVolume 只接受 0..1（ExoPlayer / IJK 都会钳位），
 * 所以 &gt;100% 的部分必须靠音频效果放大：1.0 → 0 dB，2.0 → +6 dB（电压 2 倍）。
 * 静音/音频焦点闪避用 factor 压缩，避免"静音了还在放大"。</p>
 */
public final class VolumeBoostMath {

    /** 200% 对应 +6 dB。 */
    public static final int MAX_BOOST_MB = 600;
    /** 拖动时的量化步进，避免每个像素都重建效果链。 */
    private static final int QUANTUM_MB = 25;

    private VolumeBoostMath() {
    }

    /** @param gain 0..2 应用内增益；@param factor 0..1 静音/闪避系数 */
    public static int millibel(float gain, float factor) {
        float effective = clamp(gain, 0f, 2f) * clamp(factor, 0f, 1f);
        if (effective <= 1f) return 0;
        int raw = Math.round((effective - 1f) * MAX_BOOST_MB);
        return Math.round(raw / (float) QUANTUM_MB) * QUANTUM_MB;
    }

    /** 播放器自身能接受的部分（0..1）。 */
    public static float playerVolume(float gain) {
        return clamp(gain, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
