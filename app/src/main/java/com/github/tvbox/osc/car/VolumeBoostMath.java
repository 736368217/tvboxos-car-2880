package com.github.tvbox.osc.car;

/**
 * 音量增益上限与换算，纯 Java，便于宿主机测试。
 *
 * <p>r76：用户实测「天天顶着 300% 上限」仍觉得视频比音乐轻，明确要求把上限改到
 * 500%，自己按 5% 档位调。所有 clamp / 手势 / PCM / 非 PCM 换算都必须引用这里的常量，
 * 不允许在链路里再写死 3f。自动电平对齐也不会因为用户手动调音量而让位。</p>
 */
public final class VolumeBoostMath {

    /** 应用内增益上限（倍数）：5.0 = 500%。 */
    public static final float MAX_GAIN = 5f;

    /** 非 PCM 路径用 LoudnessEnhancer：500% 对应约 +14 dB，量化到 25mB。 */
    public static final int MAX_BOOST_MB = 1400;
    /** 拖动时的量化步进，避免每个像素都重建效果链。 */
    private static final int QUANTUM_MB = 25;
    /** 5% 一档：0/5/10…500%。 */
    private static final float GESTURE_DETENT = 0.05f;

    private VolumeBoostMath() {
    }

    /** @param gain 0..5 应用内增益；@param factor 0..1 静音/闪避系数 */
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
        return Math.round(clamp(value, 0f, MAX_GAIN) / GESTURE_DETENT) * GESTURE_DETENT;
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
