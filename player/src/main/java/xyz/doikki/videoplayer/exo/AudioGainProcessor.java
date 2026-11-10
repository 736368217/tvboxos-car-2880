package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * App-level PCM gain + automatic level alignment for ExoPlayer.
 *
 * <p>Network video tracks are often ~-33 dBFS while music is near 0 dBFS. Even 300 % sounds
 * quiet, so users compensate with the *system* volume (shared with music) and keep adjusting
 * back and forth. {@link AutoLevel} raises quiet tracks toward a music-like target while the
 * app gain stays at 100 %; soft-clipping replaces hard clipping. Once the user changes the
 * app gain, auto alignment steps aside. One processor per DefaultAudioSink.</p>
 */
public final class AudioGainProcessor extends BaseAudioProcessor {

    public interface GainProvider {
        float getGain();
    }

    private static final String TAG = "CarAudioGain";
    private static final float MAX_GAIN = 5f;
    private final GainProvider gainProvider;
    private volatile float gain = 1f;
    private final AutoLevel autoLevel = new AutoLevel();
    private volatile boolean autoLevelEnabled = true;
    private int encoding = C.ENCODING_PCM_16BIT;

    private long configureCalls;
    private long queueCalls;
    private float lastAppliedGain = -1f;
    private float lastLoggedGain = 1f;
    private float lastMakeup = 1f;

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

    public void setAutoLevelEnabled(boolean enabled) {
        autoLevelEnabled = enabled;
        autoLevel.reset();
    }

    public boolean isAutoLevelEnabled() {
        return autoLevelEnabled;
    }

    public void setGain(float gain) {
        if (Float.isNaN(gain)) return;
        float next = clampGain(gain);
        if (next != this.gain && Math.abs(next - lastLoggedGain) >= 0.05f) {
            lastLoggedGain = next;
            log("set_gain " + this.gain + " -> " + next + " (queue_calls=" + queueCalls + ")");
        }
        if (Math.abs(next - this.gain) > 0.001f) autoLevel.reset();
        this.gain = next;
    }

    public float getGain() {
        GainProvider provider = gainProvider;
        return provider == null ? gain : clampGain(provider.getGain());
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
                    + " autoLevel=" + autoLevelEnabled);
        }
        if (kept) {
            encoding = inputAudioFormat.encoding;
            autoLevel.reset();
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

        // r76：自动对齐始终叠加，不再因用户手动调增益而让位；否则用户常驻 300%
        // 时 makeup 永远为 1.0，轻音轨仍比音乐低十几 dB。
        float makeup = autoLevelEnabled ? autoLevel.update(peak, samples) : 1f;
        float combined = AutoLevel.combined(makeup, userGain, 1f);
        boolean muted = combined < 0.0001f;
        boolean boosted = combined > 1.0001f;
        lastMakeup = makeup;

        if (isFloat) {
            for (int i = 0; i < samples; i++) {
                float in = inputBuffer.getFloat();
                float amplified = in * combined;
                float value = muted ? 0f
                        : boosted && (amplified > 0.72f || amplified < -0.72f)
                        ? AutoLevel.softClip(amplified) : amplified;
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
                            ? AutoLevel.softClip(amplified) : amplified;
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
                    + " auto_level=" + autoLevelEnabled
                    + " makeup=" + lastMakeup
                    + " combined=" + lastAppliedGain
                    + " peak=" + peak
                    + " target_peak=" + AutoLevel.TARGET_PEAK
                    + " peak_dbfs=" + String.format(java.util.Locale.US, "%.1f",
                            peak > 0 ? 20 * Math.log10(peak) : -90d)
                    + " expected_peak=" + (peak * lastAppliedGain)
                    + " enc=" + encodingName(encoding)
                    + " samples=" + samples
                    + " level_in=" + levelIn + " level_out=" + levelOut
                    + " measured_ratio=" + ratio
                    + " auto_samples=" + autoLevel.totalSamples());
        }
    }

    public float appliedGain() { return lastAppliedGain; }
    public float makeup() { return lastMakeup; }
    public long queueCount() { return queueCalls; }

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
