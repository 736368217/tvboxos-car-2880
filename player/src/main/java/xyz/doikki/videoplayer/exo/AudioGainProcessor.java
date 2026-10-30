package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * App-level PCM gain for ExoPlayer.
 *
 * <p>ExoPlayer's {@code setVolume()} only accepts 0..1 (it ends up in AudioTrack volume),
 * so anything above 100 % has to be applied to the decoded samples.  This processor sits
 * in the audio sink's processor chain and simply multiplies every PCM sample by the current
 * gain, saturating to the sample range.</p>
 *
 * <p>The gain is read per buffer, so changing it while playing takes effect immediately
 * (one buffer of latency at most).</p>
 *
 * <p><b>Diagnostics (car edition)</b>: tag {@code CarAudioGain}.  A playback session must emit
 * {@code configure enc=…} and {@code queue n=…}.  If {@code configure} never appears, this
 * processor is not in the sink chain at all and no gain can ever be heard; if {@code level_in}
 * and {@code level_out} differ by the expected factor, the samples really are being scaled.
 * Logging is throttled (first few calls plus every 500th buffer) so it is safe to leave on,
 * and every log call is exception-proof because this class is also driven on a plain JVM by
 * {@code car/tests/AudioGainProcessorHostTest}.</p>
 */
public final class AudioGainProcessor extends BaseAudioProcessor {

    private static final String TAG = "CarAudioGain";

    public static final AudioGainProcessor INSTANCE = new AudioGainProcessor();

    private volatile float gain = 1f;
    /** 当前输出编码，由 onConfigure 记录（BaseAudioProcessor 没有公开的输出格式访问器）。 */
    private int encoding = C.ENCODING_PCM_16BIT;

    // ---------------------------------------------------------------- diagnostics
    private long configureCalls;
    private long queueCalls;
    /** 上一次 queueInput 里采样值真的被放大过没有（用于区分"在链上但不放大"）。 */
    private float lastAppliedGain = -1f;
    /** set_gain 日志节流：只在增益变化超过 0.05 时打一行，避免拖动音量时刷屏。 */
    private float lastLoggedGain = 1f;

    /** 0..2, where 1 = untouched. */
    public void setGain(float gain) {
        if (Float.isNaN(gain)) return;
        float next = Math.max(0f, Math.min(2f, gain));
        if (next != this.gain && Math.abs(next - lastLoggedGain) >= 0.05f) {
            lastLoggedGain = next;
            log("set_gain " + this.gain + " -> " + next + " (queue_calls=" + queueCalls + ")");
        }
        this.gain = next;
    }

    public float getGain() {
        return gain;
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
                    + " accepted=" + kept + " gain=" + gain);
        }
        if (kept) {
            encoding = inputAudioFormat.encoding;
            return inputAudioFormat;
        }
        // 不可处理的编码（例如直通 AC3/EAC3）：处理器会被整条链路跳过，增益静默失效。
        log("configure REJECTED enc=" + encodingName(inputAudioFormat.encoding)
                + " -> gain cannot be applied");
        throw new UnhandledAudioFormatException(inputAudioFormat);
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining <= 0) return;
        float g = gain;
        boolean report = queueCalls < 3 || queueCalls % 500 == 0;
        boolean isFloat = encoding == C.ENCODING_PCM_FLOAT;
        long levelIn = 0;
        long levelOut = 0;

        ByteBuffer output = replaceOutputBuffer(remaining);
        output.order(ByteOrder.nativeOrder());
        inputBuffer.order(ByteOrder.nativeOrder());

        if (isFloat) {
            while (inputBuffer.remaining() >= 4) {
                float in = inputBuffer.getFloat();
                float value = in * g;
                if (value > 1f) value = 1f;
                else if (value < -1f) value = -1f;
                if (report) {
                    levelIn += Math.round(Math.abs(in) * 1_000_000f);
                    levelOut += Math.round(Math.abs(value) * 1_000_000f);
                }
                output.putFloat(value);
            }
        } else {
            while (inputBuffer.remaining() >= 2) {
                int in = inputBuffer.getShort();
                int value = Math.round(in * g);
                if (value > Short.MAX_VALUE) value = Short.MAX_VALUE;
                else if (value < Short.MIN_VALUE) value = Short.MIN_VALUE;
                if (report) {
                    levelIn += Math.abs(in);
                    levelOut += Math.abs(value);
                }
                output.putShort((short) value);
            }
        }
        output.flip();

        queueCalls++;
        if (report) {
            lastAppliedGain = g;
            log("queue n=" + queueCalls + " gain=" + g
                    + " enc=" + encodingName(encoding)
                    + " samples=" + (remaining / (isFloat ? 4 : 2))
                    + " level_in=" + levelIn + " level_out=" + levelOut);
        }
    }

    /** 供车机侧取证：处理器最后一次真正作用到的增益（-1 = 一个 buffer 都没处理过）。 */
    public float appliedGain() {
        return lastAppliedGain;
    }

    public long queueCount() {
        return queueCalls;
    }

    /**
     * 诊断日志出口。android.util.Log 在宿主 JVM（{@code AudioGainProcessorHostTest}）上不可用，
     * 这里吞掉一切异常，保证音频路径永远不因为日志失败。
     */
    private static void log(String message) {
        try {
            android.util.Log.i(TAG, message);
        } catch (Throwable ignored) {
            // host JVM / 日志系统异常：忽略
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
