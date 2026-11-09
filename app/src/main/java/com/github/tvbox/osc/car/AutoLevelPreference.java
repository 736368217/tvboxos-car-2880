package com.github.tvbox.osc.car;

import com.orhanobut.hawk.Hawk;

/**
 * 自动电平对齐开关（应用内全局，默认开）。
 *
 * <p>开启时把偏轻的网络视频音轨在 PCM 层慢速抬到接近音乐的基准，避免用户靠
 * **系统音量**补偿（系统音量音乐/视频共用，导致来回调整）。关闭时恢复纯用户增益。</p>
 */
public final class AutoLevelPreference {

    private static final String KEY = "auto_level_enabled";

    private AutoLevelPreference() {
    }

    public static boolean enabled() {
        try {
            return Hawk.get(KEY, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            Hawk.put(KEY, enabled);
        } catch (Throwable ignored) {
        }
    }
}
