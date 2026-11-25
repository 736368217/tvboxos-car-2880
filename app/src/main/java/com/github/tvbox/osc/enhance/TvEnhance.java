package com.github.tvbox.osc.enhance;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.VideoEnhanceHelper;
import com.orhanobut.hawk.Hawk;

import xyz.doikki.videoplayer.render.VideoEnhanceTiers;

/**
 * TV 版播放增强的唯一读写门面：画面滤镜、超分、补帧、音效、原声对比。
 *
 * <p>和车机版的差别：TV 版没有 PCM 增益与车机 DRC，音效只挂
 * {@link AudioEffectController} 的四件套（均衡器 / 低音 / 环绕 / 响度），
 * 音量仍然交给电视自己的音量键。</p>
 *
 * <p>生效时机：滤镜 / 超分 / 补帧要重建 GL 渲染器，改完在<b>下一次起播</b>生效；
 * 音效挂在当前音频会话上，可以立即生效。</p>
 *
 * <p>自查：{@code adb logcat -s TvAudio GlEnhance VideoEnhance}</p>
 */
public final class TvEnhance {

    public static final String TAG = "TvEnhance";

    private TvEnhance() {
    }

    // ------------------------------------------------------------------ 画面

    public static int filterMode() {
        return VideoEnhanceHelper.filterMode();
    }

    public static void setFilterMode(int mode) {
        VideoEnhanceHelper.setFilterMode(mode);
    }

    public static int filterCount() {
        return VideoEnhanceHelper.filterCount();
    }

    public static String filterName() {
        return VideoEnhanceHelper.filterName();
    }

    public static String filterNameOf(int mode) {
        return VideoEnhanceHelper.filterNameOf(mode);
    }

    public static int srTier() {
        return VideoEnhanceHelper.srTier();
    }

    public static void setSrTier(int tier) {
        VideoEnhanceHelper.setSrTier(tier);
    }

    public static String srName() {
        return VideoEnhanceHelper.srName();
    }

    public static String srNameOf(int tier) {
        return VideoEnhanceHelper.srNameOf(tier);
    }

    public static int srMaxTier() {
        return VideoEnhanceTiers.SR_ANIME;
    }

    public static int memcTier() {
        return VideoEnhanceHelper.memcTier();
    }

    public static void setMemcTier(int tier) {
        VideoEnhanceHelper.setMemcTier(tier);
    }

    public static String memcName() {
        return VideoEnhanceHelper.memcName();
    }

    public static String memcNameOf(int tier) {
        return VideoEnhanceHelper.memcNameOf(tier);
    }

    public static int memcMaxTier() {
        return VideoEnhanceTiers.MEMC_SAFE_FLOW;
    }

    /** 画面三项是否全默认（全默认走系统渲染器，不改原版表现）。 */
    public static boolean videoEnhanceEnabled() {
        return VideoEnhanceHelper.enabled();
    }

    // ------------------------------------------------------------------ 音效

    public static int audioMode() {
        return AudioEffectController.modeFromPrefValue(
                Hawk.get(HawkConfig.HIFI_AUDIO, AudioEffectController.MODE_OFF));
    }

    public static void setAudioMode(int mode) {
        Hawk.put(HawkConfig.HIFI_AUDIO, mode);
    }

    public static int audioModeCount() {
        return AudioEffectController.MODE_COUNT;
    }

    public static String audioNameOf(int mode) {
        return AudioEffectController.name(mode);
    }

    public static String audioName() {
        return AudioEffectController.name(audioMode());
    }

    /**
     * 原声 / 增强对比开关：关掉时把四件套整链释放，听感回到未处理状态，
     * 但已保存的音效档位不会丢。
     */
    public static boolean audioEnhancementEnabled() {
        Object saved = Hawk.get(HawkConfig.AUDIO_ENHANCEMENT, true);
        return !"false".equalsIgnoreCase(String.valueOf(saved));
    }

    public static void setAudioEnhancementEnabled(boolean enabled) {
        Hawk.put(HawkConfig.AUDIO_ENHANCEMENT, enabled);
    }

    public static boolean toggleAudioEnhancement() {
        boolean next = !audioEnhancementEnabled();
        setAudioEnhancementEnabled(next);
        return next;
    }

    public static String audioCompareLabel() {
        return audioEnhancementEnabled() ? "增强音效" : "原始声音";
    }

    /**
     * 把音效挂到当前播放会话上；{@code sessionId <= 0} 或 A/B 关掉时释放整链。
     */
    public static void applyAudio(int sessionId) {
        if (!audioEnhancementEnabled()) {
            AudioEffectController.release();
            return;
        }
        int mode = audioMode();
        if (sessionId <= 0 || mode == AudioEffectController.MODE_OFF) {
            AudioEffectController.release();
            return;
        }
        AudioEffectController.apply(mode, sessionId, 0);
    }

    public static void releaseAudio() {
        AudioEffectController.release();
    }

    // ------------------------------------------------------------------ 摘要

    /** 设置页 / 弹窗标题用的一行摘要。 */
    public static String summary() {
        return filterName()
                + " · 超分" + (srTier() > VideoEnhanceTiers.SR_OFF ? String.valueOf(srTier()) : "关")
                + " · 补帧" + (memcTier() > VideoEnhanceTiers.MEMC_OFF ? String.valueOf(memcTier()) : "关")
                + " · " + audioName();
    }
}
