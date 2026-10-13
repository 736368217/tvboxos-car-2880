package com.github.tvbox.osc.car;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * Car-edition audio enhancement chain: Equalizer + BassBoost + Virtualizer + LoudnessEnhancer
 * attached to the audio session of the playing video.
 *
 * <p>Six presets (关闭/影院/重低音/3D环绕/HiFi/人声). Each preset is a 13-point EQ curve keyed
 * by band centre frequency (30 Hz..32 kHz) plus bass, virtualizer and loudness strengths; a
 * device equalizer band takes the value of the nearest curve position. apply() ramps all values
 * in a 500 ms fade-in and swallows every failure. Only engines that expose a real audio session
 * id (Exo/IJK) can be enhanced; software / pass-through outputs have no session id and apply()
 * then just releases the chain, matching the reference fork.</p>
 */
public final class AudioEffectController {
    public static final int MODE_OFF = 0, MODE_CINEMA = 1, MODE_BASS = 2,
            MODE_SURROUND = 3, MODE_HIFI = 4, MODE_VOCAL = 5;
    /** 关闭 / 影院 / 重低音 / 3D环绕 / HiFi / 人声; any other value reads as 关闭. */
    private static final String[] NAMES = {"关闭", "影院", "重低音", "3D环绕", "HiFi", "人声"};

    /** Band centre frequencies (Hz) the preset curves are keyed by. */
    private static final int[] CURVE_FREQS = {30, 60, 100, 150, 200, 250, 500, 1000, 2000, 5000, 8000, 16000, 32000};
    private static final float[] CURVE_CINEMA = {7f, 8f, 6f, 3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 3f, 2f};
    private static final float[] CURVE_BASS = {6f, 7f, 5f, 4f, 3f, 2f, 0f, -1f, -1f, 0f, 0f, 0f, 0f};
    private static final float[] CURVE_SURROUND = {-2f, -1f, 0f, 1f, 2f, 3f, 2f, 1f, 2f, 3f, 4f, 4f, 3f};
    private static final float[] CURVE_HIFI = {1f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f};
    private static final float[] CURVE_VOCAL = {-4f, -3f, -1.5f, 0f, 1f, 2f, 3f, 4f, 3f, 2f, 1f, 0f, -1f};
    /** Preset curves indexed by mode; index 0 (关闭) is unused and mirrors the HiFi shape. */
    private static final float[][] CURVES = {CURVE_HIFI, CURVE_CINEMA, CURVE_BASS, CURVE_SURROUND, CURVE_HIFI, CURVE_VOCAL};
    private static final int[] BASS_STRENGTH = {0, 900, 700, 450, 250, 0};
    private static final int[] VIRT_STRENGTH = {0, 850, 150, 1000, 300, 50};
    private static final int[] LOUD_STRENGTH = {0, 900, 300, 450, 400, 750};

    private static final int FADE_STEPS = 16, MSG_FADE_STEP = 1;
    private static final long FADE_STEP_MILLIS = 32L;

    /** 500 ms fade-in (16 steps x 32 ms) driven on the main looper. */
    private static final Handler FADE = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what != MSG_FADE_STEP) return true;
            fadeIndex++;
            writeProgress(fadeIndex >= FADE_STEPS ? 1f : fadeIndex / (float) FADE_STEPS);
            if (fadeIndex >= FADE_STEPS) {
                fadeIndex = 0;
            } else {
                FADE.sendEmptyMessageDelayed(MSG_FADE_STEP, FADE_STEP_MILLIS);
            }
            return true;
        }
    });

    private static Equalizer equalizer;
    private static BassBoost bassBoost;
    private static Virtualizer virtualizer;
    private static LoudnessEnhancer loudness;
    private static int sessionId, currentMode = MODE_OFF;
    private static boolean active, bassSupported, virtualSupported;
    private static int[] eqStart, eqTarget;
    private static int eqMin = Short.MIN_VALUE, eqMax = Short.MAX_VALUE;
    private static int bassStart, bassTarget, virtualStart, virtualTarget, loudnessStart, loudnessTarget, fadeIndex;

    private AudioEffectController() {
    }

    public static String name(int mode) {
        return mode >= MODE_OFF && mode < NAMES.length ? NAMES[mode] : NAMES[MODE_OFF];
    }

    public static String label(int mode) {
        return "音效:" + name(mode);
    }

    /** Attach or retarget the chain; mode 关闭 or an invalid session id releases it. */
    public static void apply(int mode, int audioSessionId) {
        if (mode < MODE_OFF || mode > MODE_VOCAL) mode = MODE_OFF;
        if (mode == MODE_OFF || audioSessionId <= 0) {
            release();
            return;
        }
        boolean reuse = equalizer != null && audioSessionId == sessionId;
        if (reuse && mode == currentMode) return;
        if (!reuse) {
            release();
            sessionId = audioSessionId;
            buildChain(audioSessionId);
        }
        currentMode = mode;
        computeTargets(mode);
        captureStart(reuse);
        active = true;
        enableChain();
        FADE.removeMessages(MSG_FADE_STEP); fadeIndex = 0;
        FADE.sendEmptyMessage(MSG_FADE_STEP);
    }

    /** Disable and release the four effects, stop a running fade and reset all state. */
    public static void release() {
        FADE.removeMessages(MSG_FADE_STEP); fadeIndex = 0;
        releaseOne(equalizer); equalizer = null;
        releaseOne(bassBoost); bassBoost = null;
        releaseOne(virtualizer); virtualizer = null;
        releaseOne(loudness); loudness = null;
        eqStart = null; eqTarget = null;
        bassStart = bassTarget = virtualStart = virtualTarget = loudnessStart = loudnessTarget = 0;
        bassSupported = virtualSupported = false;
        sessionId = 0;
        currentMode = MODE_OFF;
        active = false;
    }

    /** Diagnostics: true while the chain is attached on a valid session and enabled. */
    public static boolean isActive() {
        if (!active || equalizer == null) return false;
        try { return equalizer.getEnabled(); } catch (Throwable error) { return false; }
    }

    /** Tolerant bridge from the old boolean pref: true -> 影院, false/null/other -> 关闭. */
    public static int modeFromPrefValue(Object stored) {
        if (stored instanceof Integer) return ((Integer) stored).intValue();
        if (stored instanceof Boolean) return ((Boolean) stored).booleanValue() ? MODE_CINEMA : MODE_OFF;
        return MODE_OFF;
    }

    private static void buildChain(int session) {
        try { equalizer = new Equalizer(0, session); } catch (Throwable ignored) { equalizer = null; }
        try { bassBoost = new BassBoost(0, session); } catch (Throwable ignored) { bassBoost = null; }
        try { virtualizer = new Virtualizer(0, session); } catch (Throwable ignored) { virtualizer = null; }
        try { loudness = new LoudnessEnhancer(session); } catch (Throwable ignored) { loudness = null; }
    }

    private static void computeTargets(int mode) {
        bassTarget = BASS_STRENGTH[mode];
        virtualTarget = VIRT_STRENGTH[mode];
        loudnessTarget = LOUD_STRENGTH[mode];
        try { bassSupported = bassBoost != null && bassTarget > 0 && bassBoost.getStrengthSupported(); } catch (Throwable ignored) { bassSupported = false; }
        try { virtualSupported = virtualizer != null && virtualTarget > 0 && virtualizer.getStrengthSupported(); } catch (Throwable ignored) { virtualSupported = false; }
        eqStart = null; eqTarget = null;
        eqMin = Short.MIN_VALUE; eqMax = Short.MAX_VALUE;
        if (equalizer == null) return;
        try {
            short[] range = equalizer.getBandLevelRange();
            if (range != null && range.length >= 2) {
                eqMin = range[0];
                eqMax = range[1];
            }
            float[] curve = CURVES[mode];
            short bands = equalizer.getNumberOfBands();
            int[] targets = new int[bands];
            for (int band = 0; band < bands; band++) {
                int hz = equalizer.getCenterFreq((short) band) / 1000;
                targets[band] = clamp(Math.round(curve[nearestIndex(hz)] * 100f), eqMin, eqMax);
            }
            eqTarget = targets;
        } catch (Throwable ignored) {
            eqTarget = null;
        }
    }

    private static void captureStart(boolean reuse) {
        eqStart = eqTarget == null || equalizer == null ? null : new int[eqTarget.length];
        if (eqStart != null && reuse) {
            for (int band = 0; band < eqStart.length; band++) {
                try { eqStart[band] = clamp(equalizer.getBandLevel((short) band), eqMin, eqMax); } catch (Throwable ignored) { eqStart[band] = 0; }
            }
        }
        bassStart = 0;
        if (reuse && bassTarget > 0 && bassSupported && bassBoost != null) {
            try { bassStart = bassBoost.getRoundedStrength(); } catch (Throwable ignored) { }
        }
        virtualStart = 0;
        if (reuse && virtualTarget > 0 && virtualSupported && virtualizer != null) {
            try { virtualStart = virtualizer.getRoundedStrength(); } catch (Throwable ignored) { }
        }
        loudnessStart = 0;
        if (reuse && loudnessTarget > 0 && loudness != null) {
            try { loudnessStart = Math.round(loudness.getTargetGain()); } catch (Throwable ignored) { }
        }
    }

    /** Enable the chain once per apply; 人声 has no bass, so BassBoost is zeroed and disabled. */
    private static void enableChain() {
        if (equalizer != null) {
            try { equalizer.setEnabled(true); } catch (Throwable ignored) { }
        }
        if (bassBoost != null && bassTarget > 0 && bassSupported) {
            try { bassBoost.setEnabled(true); } catch (Throwable ignored) { }
        } else if (bassBoost != null) {
            try { bassBoost.setStrength((short) 0); bassBoost.setEnabled(false); } catch (Throwable ignored) { }
        }
        if (virtualizer != null && virtualTarget > 0 && virtualSupported) {
            try { virtualizer.setEnabled(true); } catch (Throwable ignored) { }
        } else if (virtualizer != null) {
            try { virtualizer.setStrength((short) 0); virtualizer.setEnabled(false); } catch (Throwable ignored) { }
        }
        if (loudness != null && loudnessTarget > 0) {
            try { loudness.setEnabled(true); } catch (Throwable ignored) { }
        } else if (loudness != null) {
            try { loudness.setTargetGain(0); loudness.setEnabled(false); } catch (Throwable ignored) { }
        }
    }

    /** Interpolate every parameter from the captured start values towards the targets. */
    private static void writeProgress(float fraction) {
        if (fraction > 1f) fraction = 1f;
        if (equalizer != null && eqStart != null && eqTarget != null) {
            try {
                for (int band = 0; band < eqTarget.length; band++) {
                    equalizer.setBandLevel((short) band, (short) interpolate(eqStart[band], eqTarget[band], fraction));
                }
            } catch (Throwable ignored) { }
        }
        if (bassBoost != null && bassTarget > 0 && bassSupported) {
            try { bassBoost.setStrength((short) interpolate(bassStart, bassTarget, fraction)); } catch (Throwable ignored) { }
        }
        if (virtualizer != null && virtualTarget > 0 && virtualSupported) {
            try { virtualizer.setStrength((short) interpolate(virtualStart, virtualTarget, fraction)); } catch (Throwable ignored) { }
        }
        if (loudness != null && loudnessTarget > 0) {
            try { loudness.setTargetGain(interpolate(loudnessStart, loudnessTarget, fraction)); } catch (Throwable ignored) { }
        }
    }

    private static int interpolate(int start, int target, float fraction) {
        return start + Math.round((target - start) * fraction);
    }

    private static void releaseOne(AudioEffect effect) {
        if (effect == null) return;
        try { effect.setEnabled(false); } catch (Throwable ignored) { }
        try { effect.release(); } catch (Throwable ignored) { }
    }

    /** Index of the curve position nearest to a band centre frequency in Hz. */
    private static int nearestIndex(int hz) {
        int best = 0;
        for (int i = 1; i < CURVE_FREQS.length; i++) {
            if (Math.abs(hz - CURVE_FREQS[i]) < Math.abs(hz - CURVE_FREQS[best])) best = i;
        }
        return best;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        return value > max ? max : value;
    }
}
