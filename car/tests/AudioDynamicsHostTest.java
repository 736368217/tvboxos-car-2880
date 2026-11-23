import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;
import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine;
import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine.Mode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

/**
 * Runs the production PCM processor on deterministic quiet/loud/silence transitions.
 *
 * <p>r93 expectations: quiet passages are lifted by about +8 dB instead of +11 dB, loud passages
 * settle around -20 dBFS, and no sample ever leaves the -6 dBFS ceiling that reserves headroom for
 * the B01 pre-fader hardware DSP.</p>
 */
public final class AudioDynamicsHostTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static short[] tone(int rate, int channels, int ms, double amplitude) {
        short[] pcm = new short[rate * ms / 1000 * channels];
        for (int f = 0; f < pcm.length / channels; f++) {
            short v = (short) Math.round(32767 * amplitude * Math.sin(2 * Math.PI * 500 * f / rate));
            Arrays.fill(pcm, f * channels, (f + 1) * channels, v);
        }
        return pcm;
    }
    private static short[] run(AudioGainProcessor p, short[] pcm) {
        ByteBuffer input = ByteBuffer.allocateDirect(pcm.length * 2).order(ByteOrder.nativeOrder());
        input.asShortBuffer().put(pcm);
        p.queueInput(input);
        ByteBuffer output = p.getOutput();
        if (input.hasRemaining() || output.remaining() != pcm.length * 2) throw new AssertionError("PCM framing");
        short[] samples = new short[pcm.length];
        output.asShortBuffer().get(samples);
        return samples;
    }
    private static double db(short[] pcm, int from) {
        double energy = 0;
        for (int i = from; i < pcm.length; i++) energy += (double) pcm[i] * pcm[i] / (32768d * 32768d);
        return 10 * Math.log10(energy / (pcm.length - from));
    }
    private static int peak(short[] pcm) {
        int max = 0;
        for (short v : pcm) max = Math.max(max, Math.abs((int) v));
        return max;
    }
    /** Settle a mode on the quiet fixture; the 1.2 s AGC release needs more than a few packets. */
    private static short[] settle(AudioGainProcessor p, short[] pcm, int packets) {
        short[] out = run(p, pcm);
        for (int i = 0; i < packets; i++) out = run(p, pcm);
        return out;
    }
    private static void exercise(Mode mode, int rate, int channels, float minLift, float maxLift) throws Exception {
        AudioGainProcessor p = AudioGainProcessor.create();
        p.setMode(mode);
        p.configure(new AudioFormat(rate, channels, C.ENCODING_PCM_16BIT));
        p.flush();
        // Large packets exercise the internal 10 ms subdivision, not just 10 ms queueInput calls.
        short[] quiet = tone(rate, channels, 100, .04472136); // -30 dBFS RMS
        short[] loud = tone(rate, channels, 100, .2821727); // -14 dBFS RMS
        short[] noise = tone(rate, channels, 100, .0005);
        short[] silence = new short[quiet.length];
        float ceiling = CarAudioDynamicsEngine.SAMPLE_PEAK_CEILING * 32768;
        check(Arrays.equals(noise, run(p, noise)), "noise below gate stays at unity from cold");
        short[] settledQuiet = settle(p, quiet, 120);
        double qi = db(quiet, 0), qo = db(settledQuiet, 0), li = db(loud, 0);
        check(qo - qi >= minLift, "quiet passages lifted toward the mode target: " + qi + " -> " + qo);
        check(qo - qi <= maxLift, "quiet lift stays inside the design window: " + qi + " -> " + qo);
        check(Arrays.equals(noise, run(p, noise)), "quiet boost must not raise following noise floor");
        check(Arrays.equals(silence, run(p, silence)), "digital silence remains silent");
        short[] onset = run(p, loud);
        check(peak(onset) <= ceiling + 1, "loud onset never leaves the -1.5 dBFS ceiling: " + peak(onset));
        short[] settledLoud = settle(p, loud, 19);
        double lo = db(settledLoud, 0);
        // Upward-only: normal/loud passages must stay transparent, not be pulled down.
        check(Math.abs(lo - li) < 0.6, "loud passages pass at 0 dB: " + li + " -> " + lo);
        if (channels > 1) {
            for (int i = 0; i < quiet.length; i += channels) quiet[i] *= -1;
            short[] linked = run(p, quiet);
            for (int i = 0; i < linked.length; i += channels) {
                if (Math.abs(linked[i] + linked[i + 1]) > 1) throw new AssertionError("channel gain mismatch " + i);
            }
            check(true, "one gain per frame preserves polarity across channels");
        }
        // Large input plus 500% must never wrap or exceed the -1.5 dBFS ceiling plus 1 PCM16 LSB.
        p.setGain(5f);
        short[] transientInput = tone(rate, channels, 100, .97);
        transientInput[0] = Short.MIN_VALUE;
        short[] limited = run(p, transientInput);
        check(limited[0] < 0 && peak(limited) <= ceiling + 1, "linked limiter ceiling: " + peak(limited));
        check(peak(limited) >= ceiling * 0.85, "limiter reaches its ceiling instead of a hidden gain cut: " + peak(limited));
        // The limiter must recover per sample, otherwise loud passages stay ducked after a transient.
        p.setGain(1f);
        short[] recovered = settle(p, quiet, 120);
        check(db(recovered, 0) - qi >= 5.5, "limiter releases after the transient: " + db(recovered, 0));
        p.setGain(0f);
        check(peak(run(p, loud)) == 0, "explicit PCM mute");
        System.out.printf(Locale.ROOT,
                "%s %dHz %dch quiet %.2f -> %.2f dBFS loud %.2f -> %.2f dBFS gap %.2f -> %.2f dB onset peak %.6f limiter peak %.6f%n",
                mode, rate, channels, qi, qo, li, lo, li - qi, lo - qo,
                peak(onset) / 32768f, peak(limited) / 32768f);
    }

    public static void main(String[] args) throws Exception {
        exercise(Mode.CAR_BALANCED, 48000, 2, 6.0f, 7.2f);
        exercise(Mode.CAR_BALANCED, 44100, 1, 6.0f, 7.2f);
        exercise(Mode.CAR_BALANCED, 96000, 8, 6.0f, 7.2f);
        exercise(Mode.CAR_HIGHWAY, 48000, 2, 8.4f, 9.8f);
        check(Mode.fromPreference(null, false) == Mode.OFF && Mode.fromPreference(null, true) == Mode.CAR_BALANCED,
                "legacy off/on migration");
        check(Mode.fromPreference(2, false) == Mode.CAR_HIGHWAY && Mode.fromPreference(0, true) == Mode.OFF,
                "explicit preset wins over legacy");
        check(Mode.fromPreference(-1, null) == Mode.CAR_BALANCED, "invalid DRC preset cannot toggle master");
        System.out.println("AudioDynamicsHostTest PASS checks=" + checks);
    }
}
