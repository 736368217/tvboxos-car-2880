package com.github.tvbox.osc.util;

import com.orhanobut.hawk.Hawk;

import java.util.List;

import xyz.doikki.videoplayer.render.GlEnhanceRenderViewFactory;
import xyz.doikki.videoplayer.render.VideoEnhancePreset;
import xyz.doikki.videoplayer.render.VideoEnhanceTiers;

/**
 * 画面增强（超分 / 补帧 / 滤镜）的唯一读写入口。
 *
 * <p>三项互相独立：{@code 滤镜} 决定色彩与锐度预设，{@code 超分} 决定放大算法档位，
 * {@code 补帧} 决定运动插值档位。任一非默认值都会启用 GL 增强渲染器
 * （{@link GlEnhanceRenderViewFactory}），全默认时沿用系统原本的
 * Texture / Surface 渲染方式 —— 不改动老版本原有表现。</p>
 *
 * <p>生效时机：渲染器由播放器在每次起播时重建（VideoView.addDisplay），所以改动设置后
 * 下一次起播即生效，无需重启应用。</p>
 *
 * <p>自查：{@code adb logcat -s GlEnhance VideoEnhance}</p>
 */
public final class VideoEnhanceHelper {

    public static final String TAG = "VideoEnhance";

    private VideoEnhanceHelper() {
    }

    // ---------------- 滤镜（色彩预设） ----------------

    public static int filterMode() {
        return VideoEnhancePreset.modeFromPrefValue(Hawk.get(HawkConfig.VIDEO_ENHANCE, 0));
    }

    public static void setFilterMode(int mode) {
        Hawk.put(HawkConfig.VIDEO_ENHANCE, VideoEnhancePreset.forMode(mode).id);
    }

    /** 全部滤镜预设，供设置页列举。 */
    public static List<VideoEnhancePreset> filters() {
        return VideoEnhancePreset.all();
    }

    public static String filterName() {
        return VideoEnhancePreset.forMode(filterMode()).name;
    }

    // ---------------- 超分 ----------------

    public static int srTier() {
        return VideoEnhanceTiers.clampSr(Hawk.get(HawkConfig.VIDEO_SR_TIER, 0));
    }

    public static void setSrTier(int tier) {
        Hawk.put(HawkConfig.VIDEO_SR_TIER, VideoEnhanceTiers.clampSr(tier));
    }


    /** 指定档位的显示名（0 档为「关闭」），供设置页列举。 */
    public static String srNameOf(int tier) {
        return VideoEnhanceTiers.srName(tier);
    }

    /** 指定补帧档位的显示名（0 档为「关闭」）。 */
    public static String memcNameOf(int tier) {
        return VideoEnhanceTiers.memcName(tier);
    }

    /** 滤镜预设总数与显示名，供设置页列举。 */
    public static int filterCount() {
        return VideoEnhancePreset.all().size();
    }

    public static String filterNameOf(int mode) {
        return VideoEnhancePreset.forMode(mode).name;
    }

    public static String srName() {
        return VideoEnhanceTiers.srName(srTier());
    }

    // ---------------- 补帧 ----------------

    public static int memcTier() {
        return VideoEnhanceTiers.clampMemc(Hawk.get(HawkConfig.VIDEO_MEMC_TIER, 0));
    }

    public static void setMemcTier(int tier) {
        Hawk.put(HawkConfig.VIDEO_MEMC_TIER, VideoEnhanceTiers.clampMemc(tier));
    }

    public static String memcName() {
        return VideoEnhanceTiers.memcName(memcTier());
    }

    // ---------------- 汇总 / 渲染器 ----------------

    /** 任一增强项非默认值即为开启。 */
    public static boolean enabled() {
        return filterMode() != VideoEnhancePreset.ORIGINAL || srTier() > VideoEnhanceTiers.SR_OFF
                || memcTier() > VideoEnhanceTiers.MEMC_OFF;
    }

    /** 增强已开启时的渲染器工厂；调用前请先用 {@link #enabled()} 判断。 */
    public static GlEnhanceRenderViewFactory renderViewFactory() {
        return GlEnhanceRenderViewFactory.createTiers(filterMode(), srTier(), memcTier());
    }

    /** 设置页一行摘要，例如「臻彩 · 超分1 · 补帧关」。 */
    public static String summary() {
        return filterName()
                + " · 超分" + (srTier() > VideoEnhanceTiers.SR_OFF ? String.valueOf(srTier()) : "关")
                + " · 补帧" + (memcTier() > VideoEnhanceTiers.MEMC_OFF ? String.valueOf(memcTier()) : "关");
    }
}
