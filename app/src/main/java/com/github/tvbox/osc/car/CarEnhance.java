package com.github.tvbox.osc.car;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.VideoEnhancePreset;
import xyz.doikki.videoplayer.render.VideoPostProcessPolicy;

/**
 * Car edition glue for the two audio/video enhancement features ported from the reference car build:
 *
 * <ul>
 *     <li><b>音效 (audio effects)</b>: the 9-preset Equalizer/BassBoost/Virtualizer/LoudnessEnhancer
 *     chain of {@link AudioEffectController}, attached to the running playback engine's audio
 *     session id. Only the EXO and IJK engines expose one; the system player is skipped.</li>
 *     <li><b>臻彩 (GPU colour grading)</b>: the GL render view {@code GlEnhanceRenderView} that
 *     grades saturation/contrast/brightness on the GPU. It can switch on the current playback;
 *     HDR/Dolby Vision tracks always bypass colour grading.</li>
 * </ul>
 *
 * Preference keys: {@link HawkConfig#HIFI_AUDIO} (int preset, default 0 = off) and
 * {@link HawkConfig#VIDEO_ENHANCE} (stable preset ID; old Boolean/0/1 values are retained).
 */
public final class CarEnhance {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static java.lang.ref.WeakReference<MyVideoView> activeVideo = new java.lang.ref.WeakReference<>(null);

    private CarEnhance() {
    }

    // ------------------------------------------------------------------ audio

    public static int audioMode() {
        return AudioEffectController.modeFromPrefValue(
                Hawk.get(HawkConfig.HIFI_AUDIO, AudioEffectController.MODE_OFF));
    }

    /** Persist the preset; call applyAudio(view) to update an active session. */
    public static void setAudioMode(int mode) {
        Hawk.put(HawkConfig.HIFI_AUDIO, mode);
        MyVideoView view = activeVideo.get();
        if (view != null) applyAudio(view);

    }
    /**
     * Attach (or refresh) the effect chain for the engine's current audio session. Safe to call on
     * every playback state change: it is a no-op when the mode and session did not change.
     */
    public static void applyAudio(MyVideoView view) {
        if (view == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> applyAudio(view));
            return;
        }
        int mode = audioMode();
        int session = view.getAudioSessionId();
        AudioEffectController.apply(mode, session);
        Log.i("CarAudio", "runtime_apply mode=" + mode + " session=" + session
                + " active=" + AudioEffectController.isActive() + " state=" + view.getCurrentPlayState()
                + " player=" + System.identityHashCode(view.getMediaPlayer()));
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
        refreshActiveVideo();

    }
    public static void setVideoEnhanceEnabled(boolean enabled) {
        setVideoMode(enabled ? VideoEnhancePreset.VIVID_COLOR : VideoEnhancePreset.ORIGINAL);
    }

    public static boolean videoSrEnabled() {
        return VideoPostProcessPolicy.enabledFromPreference(Hawk.get(HawkConfig.VIDEO_SR), true);
    }

    public static boolean videoInterpEnabled() {
        return VideoPostProcessPolicy.enabledFromPreference(Hawk.get(HawkConfig.VIDEO_INTERP), true);
    }

    public static void setVideoSrEnabled(boolean enabled) {
        Hawk.put(HawkConfig.VIDEO_SR, enabled ? 1 : 0);
        refreshActiveVideo();
    }

    public static void setVideoInterpEnabled(boolean enabled) {
        Hawk.put(HawkConfig.VIDEO_INTERP, enabled ? 1 : 0);
        refreshActiveVideo();
    }

    public static void applyVideo(MyVideoView view) {
        if (view == null) return;
        activeVideo = new java.lang.ref.WeakReference<>(view);
        view.setVideoEnhancements(videoMode(), videoSrEnabled(), videoInterpEnabled());
    }

    private static void refreshActiveVideo() {
        MyVideoView view = activeVideo.get();
        if (view != null) applyVideo(view);
    }

    public static String videoAppliedStatus() {
        MyVideoView view = activeVideo.get();
        return view == null
                ? "当前无播放画面；设置将在下次播放时应用，HDR 调色会自动旁路"
                : view.getVideoEnhancementRuntimeStatus();
    }

    public static void releaseVideo(MyVideoView view) {
        if (activeVideo.get() == view) activeVideo = new java.lang.ref.WeakReference<>(null);
    }

    /** HDR colour grading is disabled in prepared(); spatial/temporal switches remain independent. */
    public static boolean useEnhanceRenderView(VideoView view) {
        return videoEnhanceEnabled() || videoSrEnabled() || videoInterpEnabled();
    }

    public static void toast(Context context, String text) {
        if (context != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        }
    }
}
