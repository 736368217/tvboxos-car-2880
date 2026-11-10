package com.github.tvbox.osc.cache;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.tvbox.osc.bean.VodInfo;

/**
 * r78：轻量播放保活会话。
 *
 * <p>用户实测「随便切个其他应用切回来，马上放一半的片就没了，自动回到主页」。
 * 旧的 {@link PlaybackSnapshot} 只在 onPause 保存进度，既不记录「正在哪个页面播放」，
 * 也没有任何回到前台时重新打开详情页的逻辑；HomeActivity 的 singleTask 被 Launcher
 * 再拉起时还会把详情页清到后台/销毁，于是回到主页。</p>
 *
 * <p>这里只持久化最小状态：sourceKey/id/片名/封面/集数/播放进度/全屏标记/活跃标记，
 * 不做前台 Service、不做画中画、不申请额外权限。进程被杀后冷启动也能按这份状态回详情页。</p>
 */
public final class PlaybackSession {

    private static final String PREF = "car_playback_session";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_FOREGROUND = "foreground";
    private static final String KEY_POSITION = "position";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_SOURCE = "sourceKey";
    private static final String KEY_ID = "vodId";
    private static final String KEY_NAME = "name";
    private static final String KEY_PIC = "pic";
    private static final String KEY_FLAG = "playFlag";
    private static final String KEY_INDEX = "playIndex";
    private static final String KEY_FULLSCREEN = "fullscreen";
    private static final String KEY_UPDATED = "updated";
    private static final String KEY_RESTORING = "restoring";

    private PlaybackSession() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 开始/继续播放时标记会话活跃，并保存可重建详情页的最小信息。 */
    public static void begin(Context context, VodInfo info, String sourceKey,
                             long position, long duration, boolean fullscreen) {
        if (context == null || info == null || TextUtils.isEmpty(sourceKey)
                || TextUtils.isEmpty(info.id)) {
            clear(context);
            return;
        }
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_FOREGROUND, true)
                .putBoolean(KEY_RESTORING, false)
                .putLong(KEY_POSITION, Math.max(0, position))
                .putLong(KEY_DURATION, Math.max(0, duration))
                .putString(KEY_SOURCE, sourceKey)
                .putString(KEY_ID, nullToEmpty(info.id))
                .putString(KEY_NAME, nullToEmpty(info.name))
                .putString(KEY_PIC, nullToEmpty(info.pic))
                .putString(KEY_FLAG, nullToEmpty(info.playFlag))
                .putInt(KEY_INDEX, Math.max(0, info.playIndex))
                .putBoolean(KEY_FULLSCREEN, fullscreen)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply();
        // 兼容 r71 的进度恢复逻辑，避免两条状态互相打架。
        PlaybackSnapshot.save(context, info, sourceKey, position, duration);
    }

    /** 播放进度变化；只在已经 active 的会话上更新，避免普通浏览误标记。 */
    public static void progress(Context context, VodInfo info, String sourceKey,
                                long position, long duration, boolean fullscreen) {
        if (context == null || !isActive(context)) return;
        prefs(context).edit()
                .putLong(KEY_POSITION, Math.max(0, position))
                .putLong(KEY_DURATION, Math.max(0, duration))
                .putString(KEY_SOURCE, nullToEmpty(sourceKey))
                .putString(KEY_ID, info == null ? "" : nullToEmpty(info.id))
                .putString(KEY_NAME, info == null ? "" : nullToEmpty(info.name))
                .putString(KEY_PIC, info == null ? "" : nullToEmpty(info.pic))
                .putString(KEY_FLAG, info == null ? "" : nullToEmpty(info.playFlag))
                .putInt(KEY_INDEX, info == null ? 0 : Math.max(0, info.playIndex))
                .putBoolean(KEY_FULLSCREEN, fullscreen)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply();
        if (info != null && !TextUtils.isEmpty(sourceKey)) {
            PlaybackSnapshot.save(context, info, sourceKey, position, duration);
        }
    }

    /** App 切到后台：保留 active，但把 foreground 标成 false。 */
    public static void background(Context context) {
        if (context == null || !isActive(context)) return;
        prefs(context).edit().putBoolean(KEY_FOREGROUND, false).apply();
    }


    /** 回前台时是否需要自动重新打开详情页：会话活跃，且上次不是正常停在前台播放页。 */
    public static boolean needsRestore(Context context) {
        return context != null && isActive(context)
                && !prefs(context).getBoolean(KEY_FOREGROUND, true)
                && !prefs(context).getBoolean(KEY_RESTORING, false);
    }

    /** 原子领取一次恢复机会，避免 Home 多次 onResume 重复打开详情页。 */
    public static boolean claimRestore(Context context) {
        if (!needsRestore(context)) return false;
        prefs(context).edit()
                .putBoolean(KEY_RESTORING, true)
                .putBoolean(KEY_FOREGROUND, true)
                .apply();
        return true;
    }

    /** 真正回到播放页后关闭 restoring 标记，允许下次切后台再恢复。 */
    public static void foreground(Context context) {
        if (context == null || !isActive(context)) return;
        prefs(context).edit()
                .putBoolean(KEY_FOREGROUND, true)
                .putBoolean(KEY_RESTORING, false)
                .apply();
    }

    /** 用户主动退出时同时清理恢复令牌。 */
    public static void clear(Context context) {
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_ACTIVE, false)
                .putBoolean(KEY_FOREGROUND, false)
                .putBoolean(KEY_RESTORING, false)
                .putLong(KEY_POSITION, 0)
                .apply();
        PlaybackSnapshot.clear(context);
    }

    public static boolean isActive(Context context) {
        return context != null && prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static long position(Context context) {
        return context == null ? 0 : prefs(context).getLong(KEY_POSITION, 0);
    }

    public static long duration(Context context) {
        return context == null ? 0 : prefs(context).getLong(KEY_DURATION, 0);
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

    public static String pic(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_PIC, "");
    }

    public static String playFlag(Context context) {
        return context == null ? "" : prefs(context).getString(KEY_FLAG, "");
    }

    public static int playIndex(Context context) {
        return context == null ? 0 : Math.max(0, prefs(context).getInt(KEY_INDEX, 0));
    }

    public static boolean fullscreen(Context context) {
        return context != null && prefs(context).getBoolean(KEY_FULLSCREEN, false);
    }

    public static long updated(Context context) {
        return context == null ? 0 : prefs(context).getLong(KEY_UPDATED, 0);
    }


    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
