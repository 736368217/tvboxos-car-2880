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
 * Runs the production PCM processor on deterministic quiet/loud/pause/silence/rumble/cross-source
 * transitions.
 *
 * <p>r100 (v5) expectations: quiet and hot sources converge to the same target loudness without
 * touching the car volume knob, short dialogue pauses hold gain for 1.5 s instead of snapping down,
 * sustained noise floor smoothly releases back to unity, sudden loud peaks are softly controlled
 * without leaving the -1.5 dBFS ceiling, and 250 Hz sidechain HPF ignores low-frequency rumble.</p>
 */
public final class AudioDynamicsHostTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static short[] tone(int rate, int channels, int ms, double amplitude) {
        return toneFreq(rate, channels, ms, amplitude, 500.0);
    }
    private static short[] toneFreq(int rate, int channels, int ms, double amplitude, double hz) {
        short[] pcm = new short[rate * ms / 1000 * channels];
        for (int f = 0; f < pcm.length / channels; f++) {
            short v = (short) Math.round(32767 * amplitude * Math.sin(2 * Math.PI * hz * f / rate));
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
    private static short[] settle(AudioGainProcessor p, short[] pcm, int packets) {
        short[] out = run(p, pcm);
        for (int i = 0; i < packets; i++) out = run(p, pcm);
        return out;
    }
    private static void exercise(Mode mode, int rate, int channels,
            float minQuietOutDb, float maxQuietOutDb, float minLoudCut, float maxLoudCut) throws Exception {
        AudioGainProcessor p = AudioGainProcessor.create();
        p.setMode(mode);
        p.configure(new AudioFormat(rate, channels, C.ENCODING_PCM_16BIT));
        p.flush();
        short[] quiet = tone(rate, channels, 100, .04472136); // -30 dBFS RMS (quiet anime)
        short[] loud = tone(rate, channels, 100, .5630f);     // -8 dBFS RMS (scream / rock OP)
        short[] noise = tone(rate, channels, 100, .0005);     // -69 dBFS RMS
        short[] silence = new short[quiet.length];
        float ceiling = CarAudioDynamicsEngine.SAMPLE_PEAK_CEILING * 32768;

        // 1. Cold noise floor stays strictly at unity.
        check(Arrays.equals(noise, run(p, noise)), "noise below gate stays at unity from cold");

        // 2. Quiet source automatically levels up to the mode's target loudness.
        short[] settledQuiet = settle(p, quiet, 50);
        double qi = db(quiet, 0), qo = db(settledQuiet, 0), li = db(loud, 0);
        check(qo >= minQuietOutDb && qo <= maxQuietOutDb,
                "quiet source levels to target window: " + qi + " -> " + qo);

        // 3. Short dialogue pause (500 ms silence) holds gain so the next line does not drop out.
        for (int i = 0; i < 5; i++) {
            check(Arrays.equals(silence, run(p, silence)), "digital silence remains silent during hold");
        }
        short[] afterShortPause = run(p, quiet);
        check(db(afterShortPause, 0) >= minQuietOutDb - 0.6,
                "short pause holds leveled gain for next line: " + db(afterShortPause, 0));

        // 4. Sustained noise floor well past the 1.5 s hold smoothly returns to unity.
        short[] afterLongNoise = settle(p, noise, 65);
        check(Arrays.equals(noise, afterLongNoise), "sustained noise floor returns to unity");

        // 5. Loud peak/OP from a fresh hot source receives downward control without exceeding -1.5 dBFS.
        AudioGainProcessor pHot = AudioGainProcessor.create();
        pHot.setMode(mode);
        pHot.configure(new AudioFormat(rate, channels, C.ENCODING_PCM_16BIT));
        pHot.flush();
        short[] onset = run(pHot, loud);
        check(peak(onset) <= ceiling + 1, "loud onset never leaves the -1.5 dBFS ceiling: " + peak(onset));
        short[] settledLoud = settle(pHot, loud, 50);
        double lo = db(settledLoud, 0);
        double loudCut = li - lo;
        check(loudCut >= minLoudCut && loudCut <= maxLoudCut,
                "hot source receives downward leveling: cut=" + loudCut + " dB");

        // 6. Channel linking preserves polarity across all channels.
        if (channels > 1) {
            for (int i = 0; i < quiet.length; i += channels) quiet[i] *= -1;
            short[] linked = run(p, quiet);
            for (int i = 0; i < linked.length; i += channels) {
                if (Math.abs(linked[i] + linked[i + 1]) > 1) throw new AssertionError("channel gain mismatch " + i);
            }
            check(true, "one gain per frame preserves polarity across channels");
        }

        // 7. Large input plus 500% must never wrap or exceed the -1.5 dBFS ceiling plus 1 PCM16 LSB.
        p.setGain(5f);
        short[] transientInput = tone(rate, channels, 100, .97);
        transientInput[0] = Short.MIN_VALUE;
        short[] limited = run(p, transientInput);
        check(limited[0] < 0 && peak(limited) <= ceiling + 1, "linked limiter ceiling: " + peak(limited));
        check(peak(limited) >= ceiling * 0.85, "limiter reaches its ceiling instead of a hidden gain cut: " + peak(limited));

        // 8. Limiter releases smoothly after transient.
        p.setGain(1f);
        short[] recovered = settle(p, quiet, 50);
        check(db(recovered, 0) >= minQuietOutDb - 0.6, "limiter releases after the transient: " + db(recovered, 0));

        // 9. Explicit mute stays silent.
        p.setGain(0f);
        check(peak(run(p, loud)) == 0, "explicit PCM mute");

        System.out.printf(Locale.ROOT,
                "%s %dHz %dch quiet %.2f -> %.2f dBFS (+%.2f) hot %.2f -> %.2f dBFS (-%.2f) onset peak %.6f limiter peak %.6f%n",
                mode, rate, channels, qi, qo, qo - qi, li, lo, loudCut,
                peak(onset) / 32768f, peak(limited) / 32768f);
    }

    /** Quiet anime (-32 dBFS) and hot music (-13 dBFS) must converge within 1.5 dB. */
    private static void verifyCrossSourceConsistency(Mode mode) throws Exception {
        short[] quietAnime = tone(48000, 2, 100, .0355);  // -32 dBFS RMS (like Ranma)
        short[] hotMusic = tone(48000, 2, 100, .3162);    // -13 dBFS RMS (like streaming music)

        AudioGainProcessor pQuiet = AudioGainProcessor.create();
        pQuiet.setMode(mode);
        pQuiet.configure(new AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        pQuiet.flush();
        double outQuiet = db(settle(pQuiet, quietAnime, 50), 0);

        AudioGainProcessor pHot = AudioGainProcessor.create();
        pHot.setMode(mode);
        pHot.configure(new AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        pHot.flush();
        double outHot = db(settle(pHot, hotMusic, 50), 0);

        double spread = Math.abs(outHot - outQuiet);
        check(spread <= 1.5,
                mode + " cross-source consistency failed: quietOut=" + outQuiet + " hotOut=" + outHot + " spread=" + spread);
        System.out.printf(Locale.ROOT,
                "%s cross-source: -32dB -> %.2f dBFS, -13dB -> %.2f dBFS (spread=%.2f dB)%n",
                mode, outQuiet, outHot, spread);
    }

    /** 250 Hz sidechain HPF prevents 60 Hz rumble from suppressing upward lift. */
    private static void verifySidechainHpf() throws Exception {
        AudioGainProcessor pLow = AudioGainProcessor.create();
        pLow.setMode(Mode.CAR_BALANCED);
        pLow.configure(new AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        pLow.flush();
        short[] rumble = toneFreq(48000, 2, 100, .1414, 60.0); // -20 dBFS 60 Hz rumble
        short[] outRumble = settle(pLow, rumble, 40);

        AudioGainProcessor pMid = AudioGainProcessor.create();
        pMid.setMode(Mode.CAR_BALANCED);
        pMid.configure(new AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        pMid.flush();
        short[] vocal = toneFreq(48000, 2, 100, .1414, 1000.0); // -20 dBFS 1 kHz vocal
        short[] outVocal = settle(pMid, vocal, 40);

        double rumbleLift = db(outRumble, 0) - db(rumble, 0);
        double vocalLift = db(outVocal, 0) - db(vocal, 0);
        check(rumbleLift > vocalLift + 8.0,
                "sidechain HPF attenuates 60 Hz detection relative to 1 kHz: rumbleLift=" + rumbleLift + " vocalLift=" + vocalLift);
    }

    public static void main(String[] args) throws Exception {
        exercise(Mode.CAR_BALANCED, 48000, 2, -18.5f, -16.8f, 5.2f, 8.2f);
        exercise(Mode.CAR_BALANCED, 44100, 1, -18.5f, -16.8f, 5.2f, 8.2f);
        exercise(Mode.CAR_BALANCED, 96000, 8, -18.5f, -16.8f, 5.2f, 8.2f);
        exercise(Mode.CAR_HIGHWAY, 48000, 2, -16.5f, -14.8f, 5.2f, 8.2f);
        verifyCrossSourceConsistency(Mode.CAR_BALANCED);
        verifyCrossSourceConsistency(Mode.CAR_HIGHWAY);
        verifySidechainHpf();
        check(Mode.fromPreference(null, false) == Mode.OFF && Mode.fromPreference(null, true) == Mode.CAR_BALANCED,
                "legacy off/on migration");
        check(Mode.fromPreference(2, false) == Mode.CAR_HIGHWAY && Mode.fromPreference(0, true) == Mode.OFF,
                "explicit preset wins over legacy");
        check(Mode.fromPreference(-1, null) == Mode.CAR_BALANCED, "invalid DRC preset cannot toggle master");
        System.out.println("AudioDynamicsHostTest PASS checks=" + checks);
    }
}
