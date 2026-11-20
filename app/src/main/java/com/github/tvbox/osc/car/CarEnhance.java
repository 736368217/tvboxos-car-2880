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
import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine.Mode;
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

    /** 上一次真正下发到引擎的参数；音量手势只更新变化的值。 */
    private static java.lang.ref.WeakReference<MyVideoView> audioOwner = new java.lang.ref.WeakReference<>(null);
    private static float lastAppliedGain = Float.NaN;
    private static float lastAppliedFactor = Float.NaN;
    private static int lastAppliedMode = Integer.MIN_VALUE;
    private static int lastAppliedSession = Integer.MIN_VALUE;
    private static Mode lastAppliedProcessing;
    private static boolean lastAppliedPcm;
    private static float lastLoggedGain = Float.NaN;
    private static Integer audioPreset;
    private static Boolean audioEnhancement;
    private static Mode drcPreset;

    private CarEnhance() {
    }

    // ------------------------------------------------------------------ audio

    public static int audioMode() {
        if (audioPreset == null) audioPreset = AudioEffectController.modeFromPrefValue(
                Hawk.get(HawkConfig.HIFI_AUDIO, AudioEffectController.MODE_OFF));
        return audioPreset;
    }

    public static void setAudioMode(int mode) {
        Hawk.put(HawkConfig.HIFI_AUDIO, mode);
        audioPreset = mode;
        applyAudio(activeVideo.get());
    }

    public static boolean audioEnhancementEnabled() {
        if (audioEnhancement == null) {
            Object saved = Hawk.get(HawkConfig.AUDIO_ENHANCEMENT, true);
            audioEnhancement = !"false".equalsIgnoreCase(String.valueOf(saved));
        }
        return audioEnhancement;
    }

    /** A/B changes the processing path, never the saved gain or the two presets. */
    public static void toggleAudioEnhancement(MyVideoView view) {
        audioEnhancement = !audioEnhancementEnabled();
        applyAudio(view, true);
        Hawk.put(HawkConfig.AUDIO_ENHANCEMENT, audioEnhancement);
    }

    public static Mode audioDrcMode() {
        if (drcPreset == null) drcPreset = Mode.fromPreference(Hawk.get(HawkConfig.AUDIO_DRC_MODE, null),
                Hawk.get(HawkConfig.SOURCE_LEVEL_MATCH, true));
        return drcPreset;
    }

    public static void cycleAudioDrcMode() {
        drcPreset = audioDrcMode().nextPreset();
        Hawk.put(HawkConfig.AUDIO_DRC_MODE, drcPreset.id);
        applyAudio(activeVideo.get());
    }

    public static Mode audioProcessingMode() {
        return audioEnhancementEnabled() ? audioDrcMode() : Mode.ORIGINAL;
    }

    public static void applyAudio(MyVideoView view) {
        applyAudio(view, false);
    }

    /** New players and AudioTracks need a forced update even with the same saved settings. */
    public static void applyAudio(MyVideoView view, boolean force) {
        if (view == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> applyAudio(view, force));
            return;
        }
        if (view.getMediaPlayer() == null) return;
        float gain = view.getAppAudioGain();
        float factor = view.getAudioVolumeFactor();
        Mode processing = audioProcessingMode();
        boolean enhanced = processing != Mode.ORIGINAL;
        int mode = enhanced ? audioMode() : AudioEffectController.MODE_OFF;
        int session = view.getAudioSessionId();
        boolean pcm = view.usesPcmGain();
        int boost = !enhanced || pcm ? 0 : VolumeBoostMath.millibel(gain, factor);
        boolean sameView = audioOwner.get() == view;
        boolean policyChanged = processing != lastAppliedProcessing || mode != lastAppliedMode;
        if (!force && Math.abs(gain - lastAppliedGain) < 1e-4f
                && Math.abs(factor - lastAppliedFactor) < 1e-4f
                && !policyChanged && session == lastAppliedSession && pcm == lastAppliedPcm && sameView) {
            return;
        }
        lastAppliedGain = gain;
        lastAppliedFactor = factor;
        lastAppliedMode = mode;
        lastAppliedProcessing = processing;
        lastAppliedSession = session;
        lastAppliedPcm = pcm;
        if (!sameView) audioOwner = new java.lang.ref.WeakReference<>(view);
        view.syncAudioVolume(processing);
        // Mode OFF + boost 0 releases all four effects and also cancels their pending fade.
        AudioEffectController.apply(mode, session, boost);
        if (!policyChanged && Math.abs(gain - lastLoggedGain) < 0.20f) return;
        lastLoggedGain = gain;
        Log.i("CarAudio", "runtime_apply processing=" + processing + " mode=" + mode
                + " session=" + session + " gain=" + gain + " factor=" + factor
                + " path=" + (pcm ? "pcm" : "native") + " boostMb=" + boost
                + " enabled=" + AudioEffectController.isActive()
                + " state=" + view.getCurrentPlayState()
                + " player=" + System.identityHashCode(view.getMediaPlayer())
                + " pcmStored=" + view.getPcmGain()
                + " pcmQueued=" + view.getPcmGainQueueCount());
    }

    /** 仅供设置页查询当前视频 View 的增益值。 */
    public static float appGain() {
        MyVideoView view = activeVideo.get();
        return view == null ? Hawk.get(HawkConfig.VOLUME_GAIN, 100) / 100f : view.getAppAudioGain();
    }

    public static void releaseAudio(MyVideoView view) {
        if (audioOwner.get() != view) return;
        audioOwner.clear();
        AudioEffectController.release();
    }

    /** 读取「音频通道对齐音乐」偏好；默认开启。 */
    public static boolean audioUsageMediaEnabled() {
        Object raw = Hawk.get(HawkConfig.AUDIO_USAGE_MEDIA, true);
        return !(raw instanceof Boolean) || (Boolean) raw;
    }

    public static void setAudioUsageMediaEnabled(boolean enabled) {
        Hawk.put(HawkConfig.AUDIO_USAGE_MEDIA, enabled);
        MyVideoView view = activeVideo.get();
        if (view != null) view.setAudioUsageMedia(enabled);
        Log.i("CarAudio", "pref audio_usage_media=" + enabled);
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
