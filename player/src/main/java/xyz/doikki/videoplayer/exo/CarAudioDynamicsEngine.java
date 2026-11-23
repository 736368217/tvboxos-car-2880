package xyz.doikki.videoplayer.exo;

/**
 * Per-sink, channel-linked gentle upward-only compression and smooth per-sample peak limiting.
 *
 * <p>v3 on-car calibration (B01 hardware DSP measured linear: 0.4% THD at -1.5 dBFS):
 * <ol>
 *   <li>No infinity-ratio AGC and no negative makeup: RMS at or above the threshold passes at
 *       0 dB, so dialogue, OP and loud passages keep the original dynamics.</li>
 *   <li>Quiet passages are lifted with a gentle slope (0.55 / 0.65) and music-grade envelope
 *       (350 ms attack, 2000 ms release) instead of a 150 ms brick wall.</li>
 *   <li>Ceiling back to the measured lossless -1.5 dBFS, guarded per sample with the 80 ms
 *       limiter, so no block-boundary steps appear.</li>
 * </ol>
 */
public final class CarAudioDynamicsEngine {
    public enum Mode {
        ORIGINAL(-1, "原始声音", -18.0f, 0.0f, 0.0f),
        OFF(0, "关闭", -18.0f, 0.0f, 0.0f),
        CAR_BALANCED(1, "车载均衡", -18.0f, 0.55f, 8.5f),
        CAR_HIGHWAY(2, "高速压缩", -16.0f, 0.65f, 11.5f);

        public final int id;
        public final String label;
        final float upThresholdDb, upSlope, maxBoostDb;

        Mode(int id, String label, float upThresholdDb, float upSlope, float maxBoostDb) {
            this.id = id;
            this.label = label;
            this.upThresholdDb = upThresholdDb;
            this.upSlope = upSlope;
            this.maxBoostDb = maxBoostDb;
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

    /** Measured on the B01: -1.5 dBFS with 0.4% THD, i.e. the lossless ceiling. */
    public static final float SAMPLE_PEAK_CEILING = 0.841395f;
    private static final float GATE_DB = -52f;
    private static final float ENV_ATTACK_MS = 350f;
    private static final float ENV_RELEASE_MS = 2000f;
    private static final float LIMITER_RELEASE_SEC = 0.080f;

    private float envDb, lastGain, limiterGain;

    CarAudioDynamicsEngine() { reset(); }

    void reset() {
        envDb = -18f;
        lastGain = 1f;
        limiterGain = 1f;
    }

    /** Renderer-thread only. Process up to 10 ms, using one gain for every channel of a frame. */
    float process(float[] pcm, int frames, int channels, int rate, Mode mode, float userGain) {
        if (frames <= 0 || channels <= 0) return lastGain;
        int count = frames * channels;
        if (userGain <= 0.0001f) {
            java.util.Arrays.fill(pcm, 0, count, 0f);
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
        double energy = 0;
        for (int i = 0; i < count; i++) energy += (double) pcm[i] * pcm[i];
        float rmsDb = (float) (10d * Math.log10(Math.max(1e-12, energy / count)));
        float ms = frames * 1000f / safeRate;
        float start = lastGain, end = userGain;
        float boostDb = 0f;
        if (rmsDb > GATE_DB) {
            envDb = follow(envDb, rmsDb, ms, rmsDb > envDb ? ENV_ATTACK_MS : ENV_RELEASE_MS);
            // Upward only: anything at or above the threshold stays at exactly 0 dB.
            boostDb = Math.max(0f, Math.min(mode.maxBoostDb, (mode.upThresholdDb - envDb) * mode.upSlope));
        } else {
            // Don't carry a quiet boost into the noise floor or into digital silence.
            start = Math.min(lastGain, userGain);
        }
        // Below the noise gate there is nothing to lift: leaving it untouched keeps hiss silent.
        end = userGain * dbToLinear(boostDb);

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
