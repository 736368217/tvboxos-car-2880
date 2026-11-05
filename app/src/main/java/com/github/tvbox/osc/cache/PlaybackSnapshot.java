package com.github.tvbox.osc.cache;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.tvbox.osc.bean.VodInfo;

/**
 * 播放状态快照：切到别的窗口、Activity 被系统回收、桌面来回时保住播放进度。
 *
 * <p>只存「恢复到哪一集/哪一秒」所需的最小信息，不复制播放器对象。
 * {@code VodInfo} 本身在 {@code App} 单例里，重建 Activity 时仍可用；这里额外把
 * name/id/pic/sourceKey 存一份，防止进程被整个杀掉后单例为空也能回到详情页。</p>
 */
public final class PlaybackSnapshot {

    private static final String PREF = "car_playback_snapshot";
    private static final String KEY_VALID = "valid";
    private static final String KEY_POSITION = "position";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_SOURCE = "sourceKey";
    private static final String KEY_ID = "vodId";
    private static final String KEY_NAME = "name";
    private static final String KEY_PIC = "pic";
    private static final String KEY_FLAG = "playFlag";
    private static final String KEY_INDEX = "playIndex";

    private PlaybackSnapshot() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 进入后台/失去窗口时调用；position<=0 也保存，用于标记「确实在播放过」。 */
    public static void save(Context context, VodInfo info, String sourceKey,
                             long position, long duration) {
        if (context == null) return;
        SharedPreferences.Editor e = prefs(context).edit();
        if (info == null || TextUtils.isEmpty(sourceKey)) {
            e.putBoolean(KEY_VALID, false);
        } else {
            e.putBoolean(KEY_VALID, true)
                    .putLong(KEY_POSITION, Math.max(0, position))
                    .putLong(KEY_DURATION, Math.max(0, duration))
                    .putString(KEY_SOURCE, sourceKey)
                    .putString(KEY_ID, nullToEmpty(info.id))
                    .putString(KEY_NAME, nullToEmpty(info.name))
                    .putString(KEY_PIC, nullToEmpty(info.pic))
                    .putString(KEY_FLAG, nullToEmpty(info.playFlag))
                    .putInt(KEY_INDEX, Math.max(0, info.playIndex));
        }
        e.apply();
    }

    public static boolean has(Context context) {
        return context != null && prefs(context).getBoolean(KEY_VALID, false);
    }

    public static long position(Context context) {
        return context == null ? 0 : prefs(context).getLong(KEY_POSITION, 0);
    }

    public static String sourceKey(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_SOURCE, "");
    }

    public static String vodId(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_ID, "");
    }

    public static String name(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_NAME, "");
    }

    /** 恢复后清掉，避免下次冷启动误回到旧片子。 */
    public static void clear(Context context) {
        if (context != null) prefs(context).edit().putBoolean(KEY_VALID, false).apply();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
