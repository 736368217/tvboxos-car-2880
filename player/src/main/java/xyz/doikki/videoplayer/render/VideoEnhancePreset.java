package xyz.doikki.videoplayer.render;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Stable, immutable SDR colour presets shared by settings and the GL renderer. */
public final class VideoEnhancePreset {
    public static final int ORIGINAL = 0;
    public static final int VIVID_COLOR = 1; // Keeps the car.2-car.4 臻彩 setting unchanged.
    public static final int ANIME_DARK = 8;
    public static final int ANIME_DAY = 9;
    public static final int ANIME_CLEAR = 10;

    public final int id;
    public final String name;
    public final String description;
    public final float saturation;
    public final float contrast;
    public final float brightness;
    public final float red;
    public final float green;
    public final float blue;
    public final float sgsrSharpness;

    private static final List<VideoEnhancePreset> PRESETS = Collections.unmodifiableList(Arrays.asList(
            new VideoEnhancePreset(0, "原画", "保留原始画面",       1.00f, 1.00f,  0.000f, 1.000f, 1.000f, 1.000f),
            new VideoEnhancePreset(1, "臻彩", "增强色彩与层次",     1.35f, 1.12f,  0.030f, 1.000f, 1.000f, 1.000f),
            new VideoEnhancePreset(2, "自然", "轻调色，接近原片",   1.08f, 1.04f,  0.010f, 1.000f, 1.000f, 1.000f),
            new VideoEnhancePreset(3, "影院", "温润色调，强化明暗", 1.08f, 1.16f, -0.015f, 1.030f, 1.000f, 0.970f),
            new VideoEnhancePreset(4, "鲜艳", "浓郁色彩，突出画面", 1.50f, 1.16f,  0.015f, 1.000f, 1.000f, 1.000f),
            new VideoEnhancePreset(5, "柔和", "降低反差，柔化色彩", 0.90f, 0.94f,  0.020f, 1.015f, 1.000f, 0.985f),
            new VideoEnhancePreset(6, "明亮", "提亮偏暗片源",       1.12f, 1.02f,  0.080f, 1.000f, 1.000f, 1.000f),
            new VideoEnhancePreset(7, "暖色", "暖色温，减少蓝色",   1.06f, 1.04f,  0.020f, 1.060f, 1.000f, 0.920f),
            new VideoEnhancePreset(8, "动漫·暗光", "夜间与常规番剧", 1.08f, 1.05f, -0.010f, 1.000f, 0.980f, 0.940f),
            new VideoEnhancePreset(9, "动漫·日间", "强光下提亮暗部", 1.14f, 1.03f,  0.015f, 1.000f, 0.990f, 0.960f),
            new VideoEnhancePreset(10, "动漫·清透", "风景与赛璐珞动画", 1.12f, 1.08f, -0.005f, 0.990f, 0.990f, 1.000f)
    ));

    private VideoEnhancePreset(int id, String name, String description, float saturation,
                               float contrast, float brightness, float red, float green, float blue) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.saturation = saturation;
        this.contrast = contrast;
        this.brightness = brightness;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.sgsrSharpness = id == VIVID_COLOR ? 1.60f : id == ANIME_DAY ? 1.30f
                : id == ANIME_CLEAR ? 1.45f : 1.25f;
    }

    public static List<VideoEnhancePreset> all() {
        return PRESETS;
    }

    public static VideoEnhancePreset forMode(int mode) {
        return PRESETS.get(mode >= 0 && mode < PRESETS.size() ? mode : ORIGINAL);
    }

    /** Migrates the old Boolean/0/1 switch and tolerates corrupt preferences. */
    public static int modeFromPrefValue(Object stored) {
        if (stored instanceof Boolean) return (Boolean) stored ? VIVID_COLOR : ORIGINAL;
        if (stored instanceof Number) return forMode(((Number) stored).intValue()).id;
        if (stored instanceof String) {
            String value = ((String) stored).trim();
            if ("true".equalsIgnoreCase(value)) return VIVID_COLOR;
            if ("false".equalsIgnoreCase(value)) return ORIGINAL;
            try { return forMode(Integer.parseInt(value)).id; } catch (NumberFormatException ignored) { }
        }
        return ORIGINAL;
    }
}
