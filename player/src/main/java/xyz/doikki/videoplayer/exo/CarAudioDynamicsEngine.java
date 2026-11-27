package xyz.doikki.videoplayer.exo;

import java.util.Arrays;

/**
 * Per-sink, channel-linked gentle two-sided car dynamics processor and smooth per-sample peak
 * limiter.
 *
 * <p>v4 on-car driving calibration (r98, based on real B01 driving recordings + r97 R2 telemetry
 * with head-unit EQ {@code 音效aaaa}):
 * <ol>
 *   <li>250 Hz first-order sidechain high-pass filter (detection only, audio path untouched) keeps
 *       low-frequency wind/BGM rumble from under-estimating vocal quietness.</li>
 *   <li>1500 ms noise-gate hold prevents short dialogue pauses from snapping gain back to unity
 *       (eliminating the v3 "quiet start of next sentence" dropout), while sustained silence or
 *       noise floor smoothly releases back to unity.</li>
 *   <li>Faster musical envelope (250/800 ms balanced, 200/600 ms highway) tracks dialogue entries
 *       without pumping.</li>
 *   <li>Gentle two-sided curve: stronger upward lift for whisper dialogue above highway road noise
 *       (+10.5 / +13.5 dB cap) paired with soft downward control (-3.5 / -5.0 dB cap) to tame
 *       sudden screams and rock OPs without flattening natural crest factor.</li>
 *   <li>Ceiling stays at the measured lossless -1.5 dBFS, guarded per sample with the 80 ms
 *       limiter.</li>
 * </ol>
 */
public final class CarAudioDynamicsEngine {
    public enum Mode {
        ORIGINAL(-1, "原始声音", -16.0f, 0.0f, 0.0f, 0.0f, 0.0f, 250f, 800f),
        OFF(0, "关闭", -16.0f, 0.0f, 0.0f, 0.0f, 0.0f, 250f, 800f),
        CAR_BALANCED(1, "车载均衡", -16.0f, 0.60f, 10.5f, 0.45f, 3.5f, 250f, 800f),
        CAR_HIGHWAY(2, "高速压缩", -14.0f, 0.68f, 13.5f, 0.55f, 5.0f, 200f, 600f);

        public final int id;
        public final String label;
        final float thresholdDb, upSlope, maxBoostDb, downSlope, maxCutDb, attackMs, releaseMs;

        Mode(int id, String label, float thresholdDb, float upSlope, float maxBoostDb,
                float downSlope, float maxCutDb, float attackMs, float releaseMs) {
            this.id = id;
            this.label = label;
            this.thresholdDb = thresholdDb;
            this.upSlope = upSlope;
            this.maxBoostDb = maxBoostDb;
            this.downSlope = downSlope;
            this.maxCutDb = maxCutDb;
            this.attackMs = attackMs;
            this.releaseMs = releaseMs;
        }

        /** The saved DRC preset is independent of the master Original/Enhanced toggle. */
        public static Mode fromPreference(Object saved, Object legacyMatch) {
            if (saved != null) {
                switch (String.valueOf(saved)) {
                    case "0": return OFF;
                    case "1": return CAR_BALANCED;
                    case "2": return CAR_HIGHWAY;
                    default: break;
                }
            }
            return "false".equalsIgnoreCase(String.valueOf(legacyMatch)) ? OFF : CAR_BALANCED;
        }

        public Mode nextPreset() {
            return this == OFF ? CAR_BALANCED : this == CAR_BALANCED ? CAR_HIGHWAY : OFF;
        }
    }

    /** Measured on the B01: -1.5 dBFS with 0.2-0.4% THD, i.e. the lossless ceiling. */
    public static final float SAMPLE_PEAK_CEILING = 0.841395f;
    private static final float GATE_DB = -52f;
    private static final float GATE_HOLD_MS = 1500f;
    private static final float GATE_RELEASE_MS = 600f;
    private static final float SIDECHAIN_HPF_HZ = 250f;
    private static final float LIMITER_RELEASE_SEC = 0.080f;

    private float envDb, lastGain, limiterGain, lastGainDb, gateHoldRemainingMs;
    private boolean gateOpen;
    private float[] scPrevX = new float[0];
    private float[] scPrevY = new float[0];

    CarAudioDynamicsEngine() { reset(); }

    void reset() {
        envDb = -16f;
        lastGain = 1f;
        limiterGain = 1f;
        lastGainDb = 0f;
        gateHoldRemainingMs = 0f;
        gateOpen = false;
        Arrays.fill(scPrevX, 0f);
        Arrays.fill(scPrevY, 0f);
    }

    /** Renderer-thread only. Process up to 10 ms, using one gain for every channel of a frame. */
    float process(float[] pcm, int frames, int channels, int rate, Mode mode, float userGain) {
        if (frames <= 0 || channels <= 0) return lastGain;
        int count = frames * channels;
        if (userGain <= 0.0001f) {
            Arrays.fill(pcm, 0, count, 0f);
            lastGain = 0f;
            limiterGain = 1f;
            return 0f;
        }

        boolean dynamics = mode != Mode.OFF && mode != Mode.ORIGINAL;
        if (!dynamics) {
            // Manual gain is a direct, state-free gain. Above 100% one block-wide scale keeps the
            // ceiling and the channel balance without ducking later quiet passages.
            float peak = 0f;
            for (int i = 0; i < count; i++) {
                float value = pcm[i] * userGain;
                pcm[i] = value;
                float magnitude = Math.abs(value);
                if (magnitude > peak) peak = magnitude;
            }
            float applied = userGain;
            if (userGain > 1.0001f && peak > SAMPLE_PEAK_CEILING) {
                applied = userGain * (SAMPLE_PEAK_CEILING / peak);
                float scale = applied / userGain;
                for (int i = 0; i < count; i++) pcm[i] *= scale;
            }
            lastGain = applied;
            limiterGain = 1f;
            return applied;
        }

        int safeRate = Math.max(8000, rate);
        if (scPrevX.length != channels) {
            scPrevX = new float[channels];
            scPrevY = new float[channels];
        }
        // First-order 250 Hz high-pass on the sidechain only (audio path remains full-range).
        float hpfAlpha = 1f / (1f + 2f * (float) Math.PI * SIDECHAIN_HPF_HZ / safeRate);
        double scEnergy = 0;
        for (int frame = 0; frame < frames; frame++) {
            int base = frame * channels;
            for (int ch = 0; ch < channels; ch++) {
                float x = pcm[base + ch];
                float y = hpfAlpha * (scPrevY[ch] + x - scPrevX[ch]);
                scPrevX[ch] = x;
                scPrevY[ch] = y;
                scEnergy += (double) y * y;
            }
        }
        float rmsDb = (float) (10d * Math.log10(Math.max(1e-12, scEnergy / count)));
        float ms = frames * 1000f / safeRate;
        float start = lastGain;
        float gainDb;
        if (rmsDb > GATE_DB) {
            if (!gateOpen) {
                envDb = mode.thresholdDb;
                gateOpen = true;
            }
            gateHoldRemainingMs = GATE_HOLD_MS;
            envDb = follow(envDb, rmsDb, ms, rmsDb > envDb ? mode.attackMs : mode.releaseMs);
            float deltaDb = mode.thresholdDb - envDb;
            if (deltaDb >= 0f) {
                gainDb = Math.min(mode.maxBoostDb, deltaDb * mode.upSlope);
            } else {
                gainDb = Math.max(-mode.maxCutDb, deltaDb * mode.downSlope);
            }
            lastGainDb = gainDb;
        } else if (gateOpen && gateHoldRemainingMs > 0f) {
            // Short dialogue pause: hold the current gain so the next line does not drop out.
            gateHoldRemainingMs = Math.max(0f, gateHoldRemainingMs - ms);
            gainDb = lastGainDb;
        } else if (gateOpen) {
            // Sustained silence / noise floor beyond hold: smoothly release back to unity.
            lastGainDb = follow(lastGainDb, 0f, ms, GATE_RELEASE_MS);
            if (Math.abs(lastGainDb) < 0.05f) {
                lastGainDb = 0f;
                gateOpen = false;
                envDb = mode.thresholdDb;
            }
            gainDb = lastGainDb;
        } else {
            // Cold below gate: leave noise floor untouched at unity.
            gainDb = 0f;
            lastGainDb = 0f;
        }
        float end = userGain * dbToLinear(gainDb);

        // Per-sample limiter: attack inside the offending sample, 80 ms smooth release.
        float release = (float) Math.exp(-1d / (safeRate * LIMITER_RELEASE_SEC));
        float invFrames = 1f / frames;
        float limiter = limiterGain;
        for (int frame = 0; frame < frames; frame++) {
            float gain = start + (end - start) * ((frame + 1) * invFrames);
            int base = frame * channels;
            float framePeak = 0f;
            for (int channel = 0; channel < channels; channel++) {
                float value = pcm[base + channel] * gain;
                pcm[base + channel] = value;
                float magnitude = Math.abs(value);
                if (magnitude > framePeak) framePeak = magnitude;
            }
            if (framePeak > SAMPLE_PEAK_CEILING) {
                float needed = SAMPLE_PEAK_CEILING / framePeak;
                if (needed < limiter) limiter = needed;
            } else {
                limiter = 1f + (limiter - 1f) * release;
            }
            if (limiter < 0.9999f) {
                for (int channel = 0; channel < channels; channel++) pcm[base + channel] *= limiter;
            }
        }
        lastGain = end;
        limiterGain = limiter;
        return end * limiter;
    }

    private static float follow(float from, float to, float ms, float timeConstant) {
        return to + (from - to) * (float) Math.exp(-ms / timeConstant);
    }

    private static float dbToLinear(float db) {
        return (float) Math.pow(10, db / 20f);
    }
}
