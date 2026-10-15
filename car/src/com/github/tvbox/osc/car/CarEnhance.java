package com.github.tvbox.osc.car;

import android.content.Context;
import android.widget.Toast;

import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.VideoEnhancePreset;

/**
 * Car edition glue for the two audio/video enhancement features ported from the reference car build:
 *
 * <ul>
 *     <li><b>音效 (audio effects)</b>: the 6-preset Equalizer/BassBoost/Virtualizer/LoudnessEnhancer
 *     chain of {@link AudioEffectController}, attached to the running playback engine's audio
 *     session id. Only the EXO and IJK engines expose one; the system player is skipped.</li>
 *     <li><b>臻彩 (GPU colour grading)</b>: the GL render view {@code GlEnhanceRenderView} that
 *     grades saturation/contrast/brightness on the GPU. It is selected per playback (before the
 *     player starts) and is forced off for HDR/Dolby Vision tracks, which must not be graded.</li>
 * </ul>
 *
 * Preference keys: {@link HawkConfig#HIFI_AUDIO} (int preset, default 0 = off) and
 * {@link HawkConfig#VIDEO_ENHANCE} (stable preset ID; old Boolean/0/1 values are retained).
 */
public final class CarEnhance {

    private CarEnhance() {
    }

    // ------------------------------------------------------------------ audio

    public static int audioMode() {
        return AudioEffectController.modeFromPrefValue(
                Hawk.get(HawkConfig.HIFI_AUDIO, AudioEffectController.MODE_OFF));
    }

    /** Persist the preset; takes effect on the current playback (see {@link #applyAudio}). */
    public static void setAudioMode(int mode) {
        Hawk.put(HawkConfig.HIFI_AUDIO, mode);
    }

    /**
     * Attach (or refresh) the effect chain for the engine's current audio session. Safe to call on
     * every playback state change: it is a no-op when the mode and session did not change.
     */
    public static void applyAudio(MyVideoView view) {
        if (view == null) return;
        int mode = audioMode();
        if (mode == AudioEffectController.MODE_OFF) {
            AudioEffectController.release();
            return;
        }
        int session = view.getAudioSessionId();
        if (session > 0) {
            AudioEffectController.apply(mode, session);
        }
    }

    public static void releaseAudio() {
        AudioEffectController.release();
    }

    // ------------------------------------------------------------------ video

    public static int videoMode() {
        return VideoEnhancePreset.modeFromPrefValue(Hawk.get(HawkConfig.VIDEO_ENHANCE, 0));
    }

    public static boolean videoEnhanceEnabled() {
        return videoMode() != VideoEnhancePreset.ORIGINAL;
    }

    public static void setVideoMode(int mode) {
        Hawk.put(HawkConfig.VIDEO_ENHANCE, VideoEnhancePreset.forMode(mode).id);
    }

    public static void setVideoEnhanceEnabled(boolean enabled) {
        setVideoMode(enabled ? VideoEnhancePreset.VIVID_COLOR : VideoEnhancePreset.ORIGINAL);
    }

    /**
     * Whether the GL colour-grading render view may be used for the next playback of this view:
     * the switch must be on and the current video track must not be HDR/Dolby Vision (the reference
     * build force-disables 臻彩 for HDR to avoid grading an already HDR-mapped picture).
     */
    public static boolean useEnhanceRenderView(VideoView view) {
        if (!videoEnhanceEnabled()) return false;
        if (view instanceof MyVideoView && ((MyVideoView) view).isHdrVideo()) {
            return false;
        }
        return true;
    }

    public static void toast(Context context, String text) {
        if (context != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        }
    }
}
