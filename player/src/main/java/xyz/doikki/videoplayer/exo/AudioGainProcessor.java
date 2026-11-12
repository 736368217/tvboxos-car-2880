package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * App-level PCM gain for ExoPlayer.
 *
 * <p>r77：彻底删除 r73–r76 的「音量自动对齐 / 动态电平平衡」。该功能每约 1 秒按峰值
 * 自动改变增益，安静片段抬升、响片段回落，用户实测「声音忽大忽小」；且不在产品规划内。
 * 现在只保留用户手动设定的固定增益（5% 档位、最高 500%），并用软限幅避免削顶失真。
 * 一个 DefaultAudioSink 对应一个 processor。</p>
 */
public final class AudioGainProcessor extends BaseAudioProcessor {

    public interface GainProvider {
        float getGain();
    }

    private static final String TAG = "CarAudioGain";
    private static final float MAX_GAIN = 5f;
    private final GainProvider gainProvider;
    private volatile float gain = 1f;
    private int encoding = C.ENCODING_PCM_16BIT;

    private long configureCalls;
    private long queueCalls;
    private float lastAppliedGain = -1f;
    private float lastLoggedGain = 1f;

    /**
     * 静态片源电平补偿：每个片源开头测一次峰值、锁定补偿量，整片不变。
     * 与已删除的动态自动电平不同，不会逐秒起伏（详见 SourceLevelLock 注释）。
     */
    private final SourceLevelLock sourceLevel = new SourceLevelLock();
    private int configuredSampleRate = 48000;

    public AudioGainProcessor() {
        this(null);
    }

    public AudioGainProcessor(GainProvider gainProvider) {
        this.gainProvider = gainProvider;
    }

    public static AudioGainProcessor create() {
        return new AudioGainProcessor();
    }

    public static AudioGainProcessor create(GainProvider gainProvider) {
        return new AudioGainProcessor(gainProvider);
    }

    public void setGain(float gain) {
        if (Float.isNaN(gain)) return;
        float next = clampGain(gain);
        if (next != this.gain && Math.abs(next - lastLoggedGain) >= 0.05f) {
            lastLoggedGain = next;
            log("set_gain " + this.gain + " -> " + next + " (queue_calls=" + queueCalls + ")");
        }
        this.gain = next;
    }

    public float getGain() {
        GainProvider provider = gainProvider;
        return provider == null ? gain : clampGain(provider.getGain());
    }

    /**
     * 片源电平补偿开关。关闭后行为与 r77 完全一致（只应用固定用户增益）。
     * 可在播放中切换：关闭立即生效（下一块即不再补偿），开启会重新测量。
     */
    public void setSourceLevelMatchEnabled(boolean enabled) {
        if (sourceLevel.isEnabled() == enabled) return;
        sourceLevel.setEnabled(enabled);
        if (enabled) {
            sourceLevel.begin(configuredSampleRate);
        }
        log("source_level_match=" + enabled);
    }

    public boolean isSourceLevelMatchEnabled() {
        return sourceLevel.isEnabled();
    }

    /** 诊断：当前片源锁定后的补偿倍数。 */
    public float sourceLevelMakeup() {
        return sourceLevel.currentMakeup();
    }

    /** 诊断：测量窗口内的片源峰值（dBFS）。 */
    public float sourceLevelDbfs() {
        return sourceLevel.measuredDbfs();
    }

    private static float clampGain(float value) {
        if (Float.isNaN(value)) return 1f;
        return Math.max(0f, Math.min(MAX_GAIN, value));
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        configureCalls++;
        boolean kept = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
                || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT;
        if (configureCalls <= 20) {
            log("configure n=" + configureCalls
                    + " enc=" + encodingName(inputAudioFormat.encoding)
                    + " sampleRate=" + inputAudioFormat.sampleRate
                    + " channels=" + inputAudioFormat.channelCount
                    + " accepted=" + kept + " gain=" + getGain()
                    + " autoLevel=removed");
        }
        if (kept) {
            encoding = inputAudioFormat.encoding;
            // 新片源/新格式：重置静态电平测量。
            configuredSampleRate = inputAudioFormat.sampleRate > 0
                    ? inputAudioFormat.sampleRate : 48000;
            sourceLevel.begin(configuredSampleRate);
            return inputAudioFormat;
        }
        log("configure REJECTED enc=" + encodingName(inputAudioFormat.encoding));
        throw new UnhandledAudioFormatException(inputAudioFormat);
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining <= 0) return;
        float userGain = getGain();
        float combined = clampGain(userGain);
        boolean report = queueCalls < 3 || queueCalls % 500 == 0;
        boolean isFloat = encoding == C.ENCODING_PCM_FLOAT;
        int samples = remaining / (isFloat ? 4 : 2);

        ByteBuffer output = replaceOutputBuffer(remaining);
        output.order(ByteOrder.nativeOrder());
        inputBuffer.order(ByteOrder.nativeOrder());

        float peak = 0f;
        long levelIn = 0;
        long levelOut = 0;
        int pos = inputBuffer.position();

        if (isFloat) {
            for (int i = 0; i < samples; i++) {
                float v = Math.abs(inputBuffer.getFloat(pos + i * 4));
                if (v > peak) peak = v;
            }
        } else {
            int peakAbs = 0;
            for (int i = 0; i < samples; i++) {
                int v = Math.abs(inputBuffer.getShort(pos + i * 2));
                if (v > peakAbs) peakAbs = v;
            }
            peak = peakAbs / 32767f;
        }

        boolean muted = combined < 0.0001f;

        // 静态片源补偿：锁定前返回 1.0（不改声音），锁定后返回固定值。
        //
        // 语义：补偿先把片源拉回正常基准（−33 dBFS → 接近音乐电平），
        // 手动增益再在基准之上叠加。两者都表达"更响"，不该互相抵消，
        // 所以补偿不受 MAX_GAIN 限制——它改的是"基准"，不是"用户档位"。
        // 最终波形由软限幅兜底（0.72 以上指数压回 ±1），不会硬削顶。
        //
        // 为什么不会像 r73 那样"忽大忽小"：makeup 在整片内恒定，
        // 只有 userGain 会变（且只在用户主动操作时变），不存在逐秒起伏。
        float makeup = muted ? 1f : sourceLevel.apply(peak, samples);
        if (makeup > 1.0001f) {
            combined = combined * makeup;   // 不立即 clamp，交给下面的软限幅
        }
        boolean boosted = combined > 1.0001f;

        if (isFloat) {
            for (int i = 0; i < samples; i++) {
                float in = inputBuffer.getFloat();
                float amplified = in * combined;
                float value = muted ? 0f
                        : boosted && (amplified > 0.72f || amplified < -0.72f)
                        ? softClip(amplified) : amplified;
                if (report) {
                    levelIn += Math.round(Math.abs(in) * 1_000_000f);
                    levelOut += Math.round(Math.abs(value) * 1_000_000f);
                }
                output.putFloat(value);
            }
        } else {
            for (int i = 0; i < samples; i++) {
                int in = inputBuffer.getShort();
                int out;
                if (muted) {
                    out = 0;
                } else if (Math.abs(combined - 1f) < 0.0001f) {
                    out = in;   // exact unity passthrough, no float quantisation loss
                } else {
                    float amplified = (in / 32768f) * combined;
                    float clipped = boosted && (amplified > 0.72f || amplified < -0.72f)
                            ? softClip(amplified) : amplified;
                    out = Math.max(Short.MIN_VALUE,
                            Math.min(Short.MAX_VALUE, Math.round(clipped * 32767f)));
                }
                if (report) {
                    levelIn += Math.abs(in);
                    levelOut += Math.abs(out);
                }
                output.putShort((short) out);
            }
        }
        output.flip();

        queueCalls++;
        if (report) {
            lastAppliedGain = combined;
            float ratio = levelIn == 0 ? 0f : (float) levelOut / levelIn;
            log("queue n=" + queueCalls
                    + " user_gain=" + userGain
                    + " makeup=" + String.format(java.util.Locale.US, "%.3f", makeup)
                    + " makeup_locked=" + sourceLevel.isLocked()
                    + " locked_value=" + String.format(java.util.Locale.US, "%.3f",
                            sourceLevel.lockedValue())
                    + " source_dbfs=" + String.format(java.util.Locale.US, "%.1f",
                            sourceLevel.measuredDbfs())
                    + " combined=" + lastAppliedGain
                    + " peak=" + peak
                    + " peak_dbfs=" + String.format(java.util.Locale.US, "%.1f",
                            peak > 0 ? 20 * Math.log10(peak) : -90d)
                    + " expected_peak=" + (peak * lastAppliedGain)
                    + " enc=" + encodingName(encoding)
                    + " samples=" + samples
                    + " level_in=" + levelIn + " level_out=" + levelOut
                    + " measured_ratio=" + ratio);
        }
    }

    public float appliedGain() { return lastAppliedGain; }
    public long queueCount() { return queueCalls; }

    /** 软限幅：0.72 以下线性，以上平滑压回 ±1，避免 500% 时硬削顶失真。 */
    private static float softClip(float value) {
        if (Float.isNaN(value)) return 0f;
        float sign = value < 0f ? -1f : 1f;
        float x = Math.abs(value);
        if (x <= 0.72f) return value;
        float over = x - 0.72f;
        return sign * (0.72f + 0.28f * (1f - (float) Math.exp(-over / 0.42f)));
    }

    private static void log(String message) {
        try {
            android.util.Log.i(TAG, message);
        } catch (Throwable ignored) {
            // Android stub throws on a plain host JVM.
        }
    }

    private static String encodingName(int encoding) {
        switch (encoding) {
            case C.ENCODING_PCM_16BIT: return "PCM_16BIT";
            case C.ENCODING_PCM_FLOAT: return "PCM_FLOAT";
            case C.ENCODING_PCM_8BIT: return "PCM_8BIT";
            case C.ENCODING_PCM_24BIT: return "PCM_24BIT";
            case C.ENCODING_PCM_32BIT: return "PCM_32BIT";
            case C.ENCODING_AC3: return "AC3";
            case C.ENCODING_E_AC3: return "E_AC3";
            case C.ENCODING_DTS: return "DTS";
            case C.ENCODING_DTS_HD: return "DTS_HD";
            case C.ENCODING_INVALID: return "INVALID";
            default: return "enc#" + encoding;
        }
    }
}
