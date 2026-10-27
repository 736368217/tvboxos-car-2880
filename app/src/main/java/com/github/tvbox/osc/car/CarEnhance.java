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
import xyz.doikki.videoplayer.render.VideoEnhanceTiers;
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
    /** 应用内音量增益（0..2）；>1 的部分由 LoudnessEnhancer 放大。 */
    private static float appGain = 1f;
    /** 增益换算（含静音/闪避与量化）统一走 VolumeBoostMath，便于宿主机验证。 */
    /** 静音(0)/闪避(0..1) 时同步压缩增幅，避免静音了还在放大。 */
    private static float volumeFactor = 1f;

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
        int boost = boostMillibel();
        AudioEffectController.apply(mode, session, boost);
        Log.i("CarAudio", "runtime_apply mode=" + mode + " session=" + session + " gain=" + appGain
                + " boostMb=" + boost + " enabled=" + AudioEffectController.isActive()
                + " state=" + view.getCurrentPlayState()
                + " player=" + System.identityHashCode(view.getMediaPlayer()));
    }

    /**
     * 应用内音量增益（0..2）。播放器本身只吃 0..1，>1 的部分由 LoudnessEnhancer 真正放大，
     * 否则 100% 和 200% 听感一样（ExoPlayer/IJK 的 setVolume 都会被钳到 1.0）。
     */
    public static void setAppGain(MyVideoView view, float gain) {
        appGain = Math.max(0f, Math.min(2f, gain));
        applyAudio(view);
    }

    public static float appGain() {
        return appGain;
    }

    /** 1.0 → 0 dB；2.0 → +6 dB（电压 2 倍，对应「200%」）。静音/闪避按系数压缩。 */
    private static int boostMillibel() {
        return VolumeBoostMath.millibel(appGain, volumeFactor);
    }
    /** 静音(true)/恢复(false) 或音频焦点闪避时同步调整增幅。 */
    public static void setVolumeFactor(MyVideoView view, float factor) {
        volumeFactor = Math.max(0f, Math.min(1f, factor));
        applyAudio(view);
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

    /** 兼容旧调用：开=1 档基线，关=0 档。 */
    public static boolean videoSrEnabled() {
        return srTier() > VideoEnhanceTiers.SR_OFF;
    }

    /** 兼容旧调用：开=1 档基线，关=0 档。 */
    public static boolean videoInterpEnabled() {
        return memcTier() > VideoEnhanceTiers.MEMC_OFF;
    }

    public static int srTier() {
        return VideoEnhanceTiers.clampSr(readTier(HawkConfig.VIDEO_SR_TIER, HawkConfig.VIDEO_SR));
    }

    public static int memcTier() {
        return VideoEnhanceTiers.clampMemc(readTier(HawkConfig.VIDEO_MEMC_TIER, HawkConfig.VIDEO_INTERP));
    }

    /** 新键优先；旧布尔/0-1 值迁移成 1 档，缺省为 0 档。 */
    private static int readTier(String key, String legacyKey) {
        Object raw = Hawk.get(key, null);
        if (raw == null) raw = Hawk.get(legacyKey, 0);
        if (raw instanceof Number) {
            int value = ((Number) raw).intValue();
            return Math.max(value, 0);
        }
        if (raw instanceof Boolean) return ((Boolean) raw) ? 1 : 0;
        if ("true".equalsIgnoreCase(String.valueOf(raw))) return 1;
        try {
            return Math.max(Integer.parseInt(String.valueOf(raw).trim()), 0);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static void setVideoSrEnabled(boolean enabled) {
        setSrTier(enabled ? VideoEnhanceTiers.SR_SGSR : VideoEnhanceTiers.SR_OFF);
    }

    public static void setVideoInterpEnabled(boolean enabled) {
        setMemcTier(enabled ? VideoEnhanceTiers.MEMC_OVERSAMPLE : VideoEnhanceTiers.MEMC_OFF);
    }

    public static void setSrTier(int tier) {
        Hawk.put(HawkConfig.VIDEO_SR_TIER, VideoEnhanceTiers.clampSr(tier));
        Hawk.put(HawkConfig.VIDEO_SR, tier > VideoEnhanceTiers.SR_OFF ? 1 : 0);
        refreshActiveVideo();
    }

    public static void setMemcTier(int tier) {
        Hawk.put(HawkConfig.VIDEO_MEMC_TIER, VideoEnhanceTiers.clampMemc(tier));
        Hawk.put(HawkConfig.VIDEO_INTERP, tier > VideoEnhanceTiers.MEMC_OFF ? 1 : 0);
        refreshActiveVideo();
    }

    public static void applyVideo(MyVideoView view) {
        if (view == null) return;
        activeVideo = new java.lang.ref.WeakReference<>(view);
        view.setVideoEnhanceTiers(videoMode(), srTier(), memcTier());
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

    /** 设置页「播放增强」入口用的一行摘要；只描述当前保存值，不代表运行时已生效。 */
    public static String summary() {
        return VideoEnhancePreset.forMode(videoMode()).name
                + " · 超分 " + VideoEnhanceTiers.srName(srTier())
                + " · 补帧 " + VideoEnhanceTiers.memcName(memcTier())
                + " · 音效 " + AudioEffectController.name(audioMode());
    }

    public static void toast(Context context, String text) {
        if (context != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        }
    }
}
