import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;
import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine;
import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine.Mode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

/** Runs the production PCM processor on deterministic quiet/loud/silence transitions. */
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
    private static void exercise(Mode mode, int rate, int channels) throws Exception {
        AudioGainProcessor p = AudioGainProcessor.create();
        p.setMode(mode);
        p.configure(new AudioFormat(rate, channels, C.ENCODING_PCM_16BIT));
        p.flush();
        // Large packets exercise the internal 10 ms subdivision, not just 10 ms queueInput calls.
        short[] quiet = tone(rate, channels, 100, .04472136);
        short[] loud = tone(rate, channels, 100, .2821727);
        short[] noise = tone(rate, channels, 100, .0005);
        short[] silence = new short[quiet.length];
        check(Arrays.equals(noise, run(p, noise)), "noise below gate stays at unity from cold");
        short[] initial = run(p, quiet), settledQuiet = initial;
        for (int i = 0; i < 29; i++) settledQuiet = run(p, quiet);
        double qi = db(quiet, 0), qo = db(settledQuiet, 0), li = db(loud, 0);
        check(qo - qi >= 8.0, "quiet passages lifted: " + qi + " -> " + qo);
        check(Arrays.equals(noise, run(p, noise)), "quiet boost must not raise following noise floor");
        check(Arrays.equals(silence, run(p, silence)), "digital silence remains silent");
        short[] settledLoud = run(p, loud);
        double onset = db(Arrays.copyOf(settledLoud, rate / 100 * channels), 0);
        check(onset <= li + 0.05, "loud onset must not exceed raw RMS at 100%: " + onset);
        for (int i = 0; i < 19; i++) settledLoud = run(p, loud);
        double lo = db(settledLoud, 0);
        check(lo < li - 1.5, "steady loud passages attenuated: " + li + " -> " + lo);
        check(Math.abs(lo - qo) < 4, "settled gap < 4 dB: " + (lo - qo));
        // Large input plus 500% must never wrap or exceed -1.5 dBFS plus 1 PCM16 LSB.
        p.setGain(5f);
        short[] transientInput = tone(rate, channels, 100, .97);
        transientInput[0] = Short.MIN_VALUE;
        short[] limited = run(p, transientInput);
        check(limited[0] < 0 && peak(limited) <= CarAudioDynamicsEngine.SAMPLE_PEAK_CEILING * 32768 + 1,
                "linked limiter ceiling: " + peak(limited));
        if (channels > 1) {
            for (int i = 0; i < quiet.length; i += channels) quiet[i] *= -1;
            short[] linked = run(p, quiet);
            for (int i = 0; i < linked.length; i += channels) {
                if (Math.abs(linked[i] + linked[i + 1]) > 1) throw new AssertionError("channel gain mismatch " + i);
            }
            check(true, "one gain per frame preserves polarity across channels");
        }
        p.setGain(0f);
        check(peak(run(p, loud)) == 0, "explicit PCM mute");
        System.out.printf(Locale.ROOT,
                "%s %dHz %dch quiet %.2f -> %.2f dBFS loud %.2f -> %.2f dBFS gap %.2f -> %.2f dB onset %.2f dBFS peak %.6f%n",
                mode, rate, channels, qi, qo, li, lo, li - qi, lo - qo, onset, peak(limited) / 32768f);
    }

    public static void main(String[] args) throws Exception {
        exercise(Mode.CAR_BALANCED, 48000, 2);
        exercise(Mode.CAR_BALANCED, 44100, 1);
        exercise(Mode.CAR_BALANCED, 96000, 8);
        exercise(Mode.CAR_HIGHWAY, 48000, 2);
        check(Mode.fromPreference(null, false) == Mode.OFF && Mode.fromPreference(null, true) == Mode.CAR_BALANCED,
                "legacy off/on migration");
        check(Mode.fromPreference(2, false) == Mode.CAR_HIGHWAY && Mode.fromPreference(0, true) == Mode.OFF,
                "explicit preset wins over legacy");
        check(Mode.fromPreference(-1, null) == Mode.CAR_BALANCED, "invalid DRC preset cannot toggle master");
        System.out.println("AudioDynamicsHostTest PASS checks=" + checks);
    }
}
