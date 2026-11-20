package xyz.doikki.videoplayer.exo;

/** Per-sink, channel-linked AGC, downward compression and sample-peak limiting. */
public final class CarAudioDynamicsEngine {
    public enum Mode {
        ORIGINAL(-1, "原始声音", 0f, 0f, 0f, 1f),
        OFF(0, "关闭", 0f, 0f, 0f, 1f),
        CAR_BALANCED(1, "车载均衡", -18f, 11f, -6f, 2.5f),
        CAR_HIGHWAY(2, "高速压缩", -16f, 14f, -8f, 4f);

        public final int id;
        public final String label;
        final float targetDb, maxBoostDb, minGainDb, ratio;

        Mode(int id, String label, float targetDb, float maxBoostDb, float minGainDb, float ratio) {
            this.id = id;
            this.label = label;
            this.targetDb = targetDb;
            this.maxBoostDb = maxBoostDb;
            this.minGainDb = minGainDb;
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

    public static final float SAMPLE_PEAK_CEILING = 0.841395f; // -1.5 dBFS, not an oversampled true peak
    private static final float GATE_DB = -52f;
    private static final float COMPRESSION_THRESHOLD_DB = -15f;
    private static final float AGC_ATTACK_MS = 150f, AGC_RELEASE_MS = 1200f;
    private static final float DRC_ATTACK_MS = 12f, DRC_RELEASE_MS = 220f;
    private float agcEnvDb, drcGainDb, lastGain;

    CarAudioDynamicsEngine() { reset(); }

    void reset() {
        agcEnvDb = -22f;
        drcGainDb = 0f;
        lastGain = 1f;
    }

    /** Renderer-thread only. Process up to 10 ms, using one gain for every channel of a frame. */
    float process(float[] pcm, int frames, int channels, int rate, Mode mode, float userGain) {
        int count = frames * channels;
        if (userGain == 0f) {
            java.util.Arrays.fill(pcm, 0, count, 0f);
            lastGain = 0f;
            return 0f;
        }
        float start = userGain, end = userGain;
        if (mode != Mode.OFF) {
            double energy = 0;
            for (int i = 0; i < count; i++) energy += (double) pcm[i] * pcm[i];
            float rmsDb = (float) (10d * Math.log10(Math.max(1e-12, energy / count)));
            float ms = frames * 1000f / rate;
            if (rmsDb > GATE_DB) {
                agcEnvDb = follow(agcEnvDb, rmsDb, ms,
                        rmsDb > agcEnvDb ? AGC_ATTACK_MS : AGC_RELEASE_MS);
                float agcDb = Math.max(mode.minGainDb, Math.min(mode.maxBoostDb, mode.targetDb - agcEnvDb));
                float overDb = Math.max(0f, rmsDb + agcDb - COMPRESSION_THRESHOLD_DB);
                float wantedDrcDb = -overDb * (1f - 1f / mode.ratio);
                drcGainDb = follow(drcGainDb, wantedDrcDb, ms,
                        wantedDrcDb < drcGainDb ? DRC_ATTACK_MS : DRC_RELEASE_MS);
                end = userGain * dbToLinear(agcDb + drcGainDb);
                start = lastGain;
                if (overDb > 0f) {
                    // Above the RMS threshold, don't carry any AGC boost into a loud onset.
                    float onsetDb = Math.min(agcDb + wantedDrcDb + 3f,
                            Math.max(0f, COMPRESSION_THRESHOLD_DB - rmsDb));
                    float onsetBound = userGain * dbToLinear(onsetDb);
                    start = Math.min(start, onsetBound);
                    end = Math.min(end, onsetBound);
                }
            } else {
                // Freeze the AGC measurement and remove its makeup below the noise gate.
                drcGainDb = follow(drcGainDb, 0f, ms, DRC_RELEASE_MS);
                start = Math.min(lastGain, userGain);
            }
        }
        float step = (end - start) / frames;
        float peak = 0f;
        for (int frame = 0, i = 0; frame < frames; frame++) {
            float gain = start + (frame + 1) * step;
            for (int channel = 0; channel < channels; channel++, i++) {
                pcm[i] *= gain;
                peak = Math.max(peak, Math.abs(pcm[i]));
            }
        }
        // Link the peak limiter too; don't hard clip a single sample or alter the channel balance.
        if ((mode != Mode.OFF || userGain > 1f) && peak > SAMPLE_PEAK_CEILING) {
            float scale = SAMPLE_PEAK_CEILING / peak;
            for (int i = 0; i < count; i++) pcm[i] *= scale;
            end *= scale;
        }
        lastGain = end;
        return end;
    }

    private static float follow(float from, float to, float ms, float timeConstant) {
        return to + (from - to) * (float) Math.exp(-ms / timeConstant);
    }

    private static float dbToLinear(float db) {
        return (float) Math.pow(10, db / 20f);
    }
}
