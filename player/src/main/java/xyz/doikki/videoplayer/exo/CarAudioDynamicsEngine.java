package xyz.doikki.videoplayer.exo;

/**
 * Per-sink, channel-linked AGC, gentle downward compression and smooth per-sample peak limiting.
 *
 * <p>v2 (r93) changes for the Leapmotor B01 hardware DSP: the output ceiling drops to -6 dBFS so
 * the head unit's pre-fader positive channel/EQ gain cannot push the digital signal into its own
 * clipper, the block-boundary onset override is gone, and DRC limiting is applied per sample with
 * an 80 ms release instead of scaling whole 10 ms blocks.</p>
 */
public final class CarAudioDynamicsEngine {
    public enum Mode {
        ORIGINAL(-1, "原始声音", 0f, 0f, 0f, -18f, 1f),
        OFF(0, "关闭", 0f, 0f, 0f, -18f, 1f),
        CAR_BALANCED(1, "车载均衡", -22f, 9f, -6f, -18f, 2f),
        CAR_HIGHWAY(2, "高速压缩", -19.5f, 12f, -8f, -17f, 3.2f);

        public final int id;
        public final String label;
        final float targetDb, maxBoostDb, minGainDb, compThreshDb, ratio;

        Mode(int id, String label, float targetDb, float maxBoostDb, float minGainDb,
             float compThreshDb, float ratio) {
            this.id = id;
            this.label = label;
            this.targetDb = targetDb;
            this.maxBoostDb = maxBoostDb;
            this.minGainDb = minGainDb;
            this.compThreshDb = compThreshDb;
            this.ratio = ratio;
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

    /**
     * -6.0 dBFS sample-peak ceiling (10^(-6/20) = 0.501187). This is a sample peak, not an
     * oversampled true peak: it leaves roughly +6 dB of digital headroom for the B01 pre-fader
     * hardware DSP instead of driving that stage into its own limiter.
     */
    public static final float SAMPLE_PEAK_CEILING = 0.501187f;
    private static final float GATE_DB = -52f;
    private static final float AGC_ATTACK_MS = 150f, AGC_RELEASE_MS = 1200f;
    private static final float DRC_ATTACK_MS = 15f, DRC_RELEASE_MS = 220f;
    /** 80 ms per-sample release: no 10 ms block modulation on dense material. */
    private static final float LIMITER_RELEASE_SEC = 0.080f;

    private float agcEnvDb, drcGainDb, lastGain, limiterGain;

    CarAudioDynamicsEngine() { reset(); }

    void reset() {
        agcEnvDb = -24f;
        drcGainDb = 0f;
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
            // Manual gain is a direct, state-free gain: it must apply per buffer and stay stable
            // between windows. Above 100% one block-wide scale keeps the ceiling and the balance.
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
            drcGainDb = 0f;
            lastGain = applied;
            limiterGain = 1f;
            return applied;
        }

        int safeRate = Math.max(8000, rate);
        float start = lastGain, end = userGain;
        double energy = 0;
        for (int i = 0; i < count; i++) energy += (double) pcm[i] * pcm[i];
        float rmsDb = (float) (10d * Math.log10(Math.max(1e-12, energy / count)));
        float ms = frames * 1000f / safeRate;
        if (rmsDb > GATE_DB) {
            agcEnvDb = follow(agcEnvDb, rmsDb, ms,
                    rmsDb > agcEnvDb ? AGC_ATTACK_MS : AGC_RELEASE_MS);
            float agcDb = Math.max(mode.minGainDb,
                    Math.min(mode.maxBoostDb, mode.targetDb - agcEnvDb));
            float overDb = Math.max(0f, rmsDb + agcDb - mode.compThreshDb);
            float wantedDrcDb = -overDb * (1f - 1f / mode.ratio);
            drcGainDb = follow(drcGainDb, wantedDrcDb, ms,
                    wantedDrcDb < drcGainDb ? DRC_ATTACK_MS : DRC_RELEASE_MS);
            end = userGain * dbToLinear(agcDb + drcGainDb);
        } else {
            // Below the gate: freeze the AGC and drop its makeup so noise and silence stay
            // untouched; a boost never carries into the floor.
            drcGainDb = follow(drcGainDb, 0f, ms, DRC_RELEASE_MS);
            start = Math.min(lastGain, userGain);
            end = userGain;
        }

        // Always ramp from the previous block's gain: no per-block override may step it. The
        // limiter attacks inside the offending sample and releases over 80 ms, per sample.
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
