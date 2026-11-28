package xyz.doikki.videoplayer.exo;

import java.util.Arrays;

/**
 * Per-sink, channel-linked two-stage car audio dynamics processor and smooth per-sample peak
 * limiter.
 *
 * <p>v5 source-adaptive loudness leveling + gentle car DRC (r100, calibrated across three full
 * real-car anime episodes with an 18.7 dB spread against streaming music: {@code 乱马} -32.2 dBFS,
 * {@code 穹顶} -28.4 dBFS, {@code 暗部少女的共栖} -24.4 dBFS vs. pop music -13.5 dBFS):
 * <ol>
 *   <li><b>250 Hz first-order sidechain high-pass</b> (detection only, audio path untouched) keeps
 *       low-frequency rumble/BGM from misleading vocal loudness estimation.</li>
 *   <li><b>Stage 1 — Slow source-adaptive program leveler</b>: tracks long-term active loudness
 *       above -46 dBFS (800 ms bootstrap for the first 3 s of a new video, then 6000 ms slow
 *       tracking) and applies automatic makeup (+16.0 / +17.5 dB boost cap, -6.0 dB cut cap) so
 *       quiet anime masters, cinematic dramas, and loud music all converge within ~1.6 dB at
 *       {@code userGain=1.0} without touching the car volume knob.</li>
 *   <li><b>Stage 2 — Relative gentle two-sided DRC</b>: operates around the normalized target
 *       baseline with musical envelopes (200/750 ms balanced, 160/600 ms highway) to lift whispers
 *       (+6.0 / +7.0 dB) and softly tame sudden screams/OPs (-5.0 / -6.0 dB) while preserving
 *       12-14 dB natural crest factor.</li>
 *   <li><b>1500 ms noise-gate hold</b> prevents short dialogue pauses from dropping out, while
 *       sustained silence or noise floor smoothly releases back to unity (600 ms).</li>
 *   <li><b>Lossless -1.5 dBFS ceiling</b> guarded per sample with the 80 ms peak limiter.</li>
 * </ol>
 */
public final class CarAudioDynamicsEngine {
    public enum Mode {
        ORIGINAL(-1, "原始声音", -18.5f, 0f, 0f, 0f, 0f, 0f, 0f, 200f, 750f, 0f),
        OFF(0, "关闭", -18.5f, 0f, 0f, 0f, 0f, 0f, 0f, 200f, 750f, 0f),
        CAR_BALANCED(1, "车载均衡", -18.5f, 16.0f, 6.0f, 0.50f, 6.0f, 0.50f, 5.0f, 200f, 750f, 19.0f),
        CAR_HIGHWAY(2, "高速压缩", -16.5f, 17.5f, 6.0f, 0.58f, 7.0f, 0.58f, 6.0f, 160f, 600f, 21.0f);

        public final int id;
        public final String label;
        final float targetProgramDb, maxProgramBoostDb, maxProgramCutDb;
        final float upSlope, maxLocalBoostDb, downSlope, maxLocalCutDb;
        final float attackMs, releaseMs, maxTotalBoostDb;

        Mode(int id, String label,
                float targetProgramDb, float maxProgramBoostDb, float maxProgramCutDb,
                float upSlope, float maxLocalBoostDb, float downSlope, float maxLocalCutDb,
                float attackMs, float releaseMs, float maxTotalBoostDb) {
            this.id = id;
            this.label = label;
            this.targetProgramDb = targetProgramDb;
            this.maxProgramBoostDb = maxProgramBoostDb;
            this.maxProgramCutDb = maxProgramCutDb;
            this.upSlope = upSlope;
            this.maxLocalBoostDb = maxLocalBoostDb;
            this.downSlope = downSlope;
            this.maxLocalCutDb = maxLocalCutDb;
            this.attackMs = attackMs;
            this.releaseMs = releaseMs;
            this.maxTotalBoostDb = maxTotalBoostDb;
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
    private static final float PROGRAM_GATE_DB = -46f;
    private static final float PROGRAM_BOOTSTRAP_MS = 3000f;
    private static final float PROGRAM_FAST_MS = 800f;
    private static final float PROGRAM_SLOW_MS = 6000f;
    private static final float GATE_HOLD_MS = 1500f;
    private static final float GATE_RELEASE_MS = 600f;
    private static final float SIDECHAIN_HPF_HZ = 250f;
    private static final float LIMITER_RELEASE_SEC = 0.080f;

    private float envDb, programDb, programActiveMs, lastGain, limiterGain, lastGainDb, gateHoldRemainingMs;
    private boolean gateOpen;
    private float[] scPrevX = new float[0];
    private float[] scPrevY = new float[0];

    CarAudioDynamicsEngine() { reset(); }

    void reset() {
        envDb = -18.5f;
        programDb = -18.5f;
        programActiveMs = 0f;
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
                envDb = programActiveMs == 0f ? rmsDb : programDb;
                gateOpen = true;
            }
            gateHoldRemainingMs = GATE_HOLD_MS;

            // Stage 1: Slow source-adaptive program loudness tracker (active speech/music only).
            if (rmsDb > PROGRAM_GATE_DB) {
                if (programActiveMs == 0f) {
                    programDb = rmsDb;
                } else {
                    float progTc = programActiveMs < PROGRAM_BOOTSTRAP_MS ? PROGRAM_FAST_MS : PROGRAM_SLOW_MS;
                    programDb = follow(programDb, rmsDb, ms, progTc);
                }
                programActiveMs += ms;
            }
            float programMakeupDb = Math.max(-mode.maxProgramCutDb,
                    Math.min(mode.maxProgramBoostDb, mode.targetProgramDb - programDb));

            // Stage 2: Musical local two-sided dynamics relative to the normalized baseline.
            envDb = follow(envDb, rmsDb, ms, rmsDb > envDb ? mode.attackMs : mode.releaseMs);
            float normEnvDb = envDb + programMakeupDb;
            float deltaDb = mode.targetProgramDb - normEnvDb;
            float localDb = deltaDb >= 0f
                    ? Math.min(mode.maxLocalBoostDb, deltaDb * mode.upSlope)
                    : Math.max(-mode.maxLocalCutDb, deltaDb * mode.downSlope);

            float minTotalDb = -(mode.maxProgramCutDb + mode.maxLocalCutDb);
            gainDb = Math.max(minTotalDb, Math.min(mode.maxTotalBoostDb, programMakeupDb + localDb));
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
                envDb = programDb;
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
