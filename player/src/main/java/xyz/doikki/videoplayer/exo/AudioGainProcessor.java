package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine.Mode;

/** One PCM processor per Exo sink. Original sound is a byte copy, not a DSP preset. */
public final class AudioGainProcessor extends BaseAudioProcessor {
    public interface GainProvider { float getGain(); }

    private static final long REPORT_INTERVAL_NS = 1_000_000_000L;
    private static final float PCM16_SCALE = 32768f;
    private final GainProvider gainProvider;
    private final CarAudioDynamicsEngine dynamics = new CarAudioDynamicsEngine();
    private volatile float gain = 1f;
    private volatile long queueCalls;
    private volatile float lastAppliedGain = 1f;
    private long lastReportNs;
    private float[] scratch = new float[0];

    // An immutable marker handles both setting changes and same-format source changes. DSP state
    // itself belongs to the renderer: never lock the render thread against UI or preference I/O.
    private static final class Signal {
        final Mode mode;
        Signal(Mode mode) { this.mode = mode; }
    }
    private volatile Signal requested = new Signal(Mode.OFF);
    private Signal applied;

    public AudioGainProcessor() { this(null); }
    public AudioGainProcessor(GainProvider provider) { gainProvider = provider; }
    public static AudioGainProcessor create() { return new AudioGainProcessor(); }
    public static AudioGainProcessor create(GainProvider provider) { return new AudioGainProcessor(provider); }
    public void setGain(float value) { if (!Float.isNaN(value)) gain = clampGain(value); }
    public float getGain() { return gainProvider == null ? gain : clampGain(gainProvider.getGain()); }

    public synchronized void setMode(Mode mode) {
        if (requested.mode != mode) requested = new Signal(mode);
    }

    public synchronized void beginSource() { requested = new Signal(requested.mode); }
    public long queueCount() { return queueCalls; }
    public float appliedGain() { return lastAppliedGain; }

    @Override protected AudioFormat onConfigure(AudioFormat input) throws UnhandledAudioFormatException {
        if ((input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT)
                || input.sampleRate <= 0 || input.channelCount <= 0) {
            throw new UnhandledAudioFormatException(input);
        }
        // Keep the processor attached in Original mode too, so changing mode never rebuilds the sink.
        return input;
    }

    @Override protected void onFlush() {
        applied = null;
        dynamics.reset();
        lastAppliedGain = 1f;
        if (inputAudioFormat != AudioFormat.NOT_SET) {
            int blockSamples = Math.max(1, inputAudioFormat.sampleRate / 100) * inputAudioFormat.channelCount;
            if (scratch.length < blockSamples) scratch = new float[blockSamples];
        }
    }

    @Override protected void onReset() {
        queueCalls = 0;
        lastReportNs = 0;
    }

    @Override public void queueInput(ByteBuffer input) {
        int bytes = input.remaining();
        if (bytes == 0) return;
        Signal signal = requested;
        boolean changed = signal != applied;
        if (changed) {
            dynamics.reset();
            applied = signal;
        }
        Mode mode = signal.mode;
        long now = System.nanoTime();
        boolean report = changed || queueCalls == 0 || now - lastReportNs >= REPORT_INTERVAL_NS;
        if (report) lastReportNs = now;
        ByteBuffer output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder());
        queueCalls++;
        if (mode == Mode.ORIGINAL) {
            output.put(input);
            output.flip();
            lastAppliedGain = 1f;
            if (report) log("queue n=" + queueCalls + " mode=" + mode + " bypass=true bytes=" + bytes);
            return;
        }

        float userGain = getGain();
        if (mode == Mode.OFF && userGain == 1f) {
            output.put(input);
            output.flip();
            lastAppliedGain = 1f;
            if (report) log("queue n=" + queueCalls + " mode=" + mode + " user=1.0 bytes=" + bytes);
            return;
        }
        input.order(ByteOrder.nativeOrder());
        int channels = inputAudioFormat.channelCount, rate = inputAudioFormat.sampleRate;
        boolean floats = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT;
        int frames = bytes / inputAudioFormat.bytesPerFrame;
        int remaining = frames;
        float inputPeak = 0f, outputPeak = 0f;
        double inputPower = 0d, outputPower = 0d;
        float appliedGain = userGain;
        while (remaining > 0) {
            int count = Math.min(remaining, Math.max(1, rate / 100));
            int samples = count * channels;
            for (int i = 0; i < samples; i++) {
                float value = floats ? input.getFloat() : input.getShort() / PCM16_SCALE;
                scratch[i] = Float.isNaN(value) || Float.isInfinite(value) ? 0f : Math.max(-1f, Math.min(1f, value));
                if (report) {
                    inputPeak = Math.max(inputPeak, Math.abs(scratch[i]));
                    inputPower += (double) scratch[i] * scratch[i];
                }
            }
            appliedGain = dynamics.process(scratch, count, channels, rate, mode, userGain);
            for (int i = 0; i < samples; i++) {
                float value = scratch[i];
                if (floats) {
                    output.putFloat(value);
                } else {
                    int quantized = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value * PCM16_SCALE)));
                    output.putShort((short) quantized);
                    value = quantized / PCM16_SCALE;
                }
                if (report) {
                    outputPeak = Math.max(outputPeak, Math.abs(value));
                    outputPower += (double) value * value;
                }
            }
            remaining -= count;
        }
        output.put(input); // Exo supplies whole frames; do not drop any trailing bytes.
        output.flip();
        lastAppliedGain = appliedGain;
        if (report) log("queue n=" + queueCalls + " mode=" + mode + " user=" + userGain
                + " gain=" + appliedGain + " frames=" + frames + " channels=" + channels
                + " inPeak=" + inputPeak + " outPeak=" + outputPeak
                + " rmsRatio=" + (inputPower > 0d ? Math.sqrt(outputPower / inputPower) : 0d));
    }

    private static float clampGain(float value) {
        return Float.isNaN(value) ? 1f : Math.max(0f, Math.min(5f, value));
    }

    private static void log(String message) {
        try { android.util.Log.i("CarAudioGain", message); } catch (RuntimeException ignored) { /* host JVM */ }
    }
}
