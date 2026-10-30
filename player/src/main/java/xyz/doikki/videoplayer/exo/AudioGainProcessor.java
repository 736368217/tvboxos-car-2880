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
 * so anything above 100 % has to be applied to the decoded samples. This processor multiplies
 * every PCM sample by the current gain, saturating to the sample range.</p>
 *
 * <p>A processor instance belongs to exactly one {@code DefaultAudioSink}. Extension renderer
 * factories can construct more than one sink, so production callers use a per-sink instance and
 * a shared gain provider owned by the ExoPlayer. This prevents BaseAudioProcessor buffers/state
 * from being concurrently shared across audio renderers.</p>
 *
 * <p>Diagnostics use tag {@code CarAudioGain}: {@code configure} proves the processor is in the
 * sink chain; {@code queue} includes input/output sample magnitudes. Logs are throttled and safe
 * on a plain JVM because {@code AudioGainProcessorHostTest} drives this class without Android.</p>
 */
public final class AudioGainProcessor extends BaseAudioProcessor {

    public interface GainProvider {
        float getGain();
    }

    private static final String TAG = "CarAudioGain";

    private final GainProvider gainProvider;
    private volatile float gain = 1f;
    private int encoding = C.ENCODING_PCM_16BIT;

    // ---------------------------------------------------------------- diagnostics
    private long configureCalls;
    private long queueCalls;
    private float lastAppliedGain = -1f;
    private float lastLoggedGain = 1f;

    public AudioGainProcessor() {
        this(null);
    }

    public AudioGainProcessor(GainProvider gainProvider) {
        this.gainProvider = gainProvider;
    }

    /** A standalone processor for tests or a sink that owns its gain state. */
    public static AudioGainProcessor create() {
        return new AudioGainProcessor();
    }

    /** A sink-owned processor whose gain is read from its owning player. */
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

    private static float clampGain(float value) {
        if (Float.isNaN(value)) return 1f;
        return Math.max(0f, Math.min(2f, value));
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
                    + " accepted=" + kept + " gain=" + getGain());
        }
        if (kept) {
            encoding = inputAudioFormat.encoding;
            return inputAudioFormat;
        }
        log("configure REJECTED enc=" + encodingName(inputAudioFormat.encoding)
                + " -> gain cannot be applied");
        throw new UnhandledAudioFormatException(inputAudioFormat);
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining <= 0) return;
        float currentGain = getGain();
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
                float value = in * currentGain;
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
                int value = Math.round(in * currentGain);
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
            lastAppliedGain = currentGain;
            log("queue n=" + queueCalls + " gain=" + currentGain
                    + " enc=" + encodingName(encoding)
                    + " samples=" + (remaining / (isFloat ? 4 : 2))
                    + " level_in=" + levelIn + " level_out=" + levelOut);
        }
    }

    public float appliedGain() {
        return lastAppliedGain;
    }

    public long queueCount() {
        return queueCalls;
    }

    private static void log(String message) {
        try {
            android.util.Log.i(TAG, message);
        } catch (Throwable ignored) {
            // The Android stub throws on a plain host JVM; diagnostics must not affect processing.
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
