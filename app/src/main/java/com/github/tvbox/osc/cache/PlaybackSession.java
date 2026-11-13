package com.github.tvbox.osc.cache;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.tvbox.osc.bean.VodInfo;

/** Playback recovery is armed by an explicit playback start, never by a lifecycle callback. */
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
    private static final String KEY_USER_EXIT = "user_exit";

    private PlaybackSession() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** Only an explicit start/new episode may re-arm recovery after the user has left. */
    public static void begin(Context context, VodInfo info, String sourceKey,
                             long position, long duration, boolean fullscreen) {
        if (context == null || info == null || TextUtils.isEmpty(sourceKey)
                || TextUtils.isEmpty(info.id)) return;
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_FOREGROUND, true)
                .putBoolean(KEY_RESTORING, false)
                .putBoolean(KEY_USER_EXIT, false)
                .apply();
        progress(context, info, sourceKey, position, duration, fullscreen);
    }

    /** Updating a snapshot must never resurrect a session cleared by Back/finish. */
    public static void progress(Context context, VodInfo info, String sourceKey,
                                long position, long duration, boolean fullscreen) {
        if (context == null || info == null || TextUtils.isEmpty(sourceKey)
                || !isActive(context)) return;
        prefs(context).edit()
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
        PlaybackSnapshot.save(context, info, sourceKey, position, duration);
    }

    /** Called only when the entire application becomes stopped, or on a new process. */
    public static void background(Context context) {
        if (context == null || !isActive(context)) return;
        prefs(context).edit().putBoolean(KEY_FOREGROUND, false)
                .putBoolean(KEY_RESTORING, false).apply();
    }

    public static boolean needsRestore(Context context) {
        return context != null && isActive(context)
                && !prefs(context).getBoolean(KEY_FOREGROUND, true)
                && !prefs(context).getBoolean(KEY_RESTORING, false);
    }

    public static boolean claimRestore(Context context) {
        if (!needsRestore(context)) return false;
        prefs(context).edit().putBoolean(KEY_RESTORING, true)
                .putBoolean(KEY_FOREGROUND, true).apply();
        return true;
    }

    public static void foreground(Context context) {
        if (context == null || !isActive(context)) return;
        prefs(context).edit().putBoolean(KEY_FOREGROUND, true)
                .putBoolean(KEY_RESTORING, false).apply();
    }

    /** Invalidate recovery before Activity teardown. Delayed pause/progress cannot undo this. */
    public static void clear(Context context) {
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_USER_EXIT, true)
                .putBoolean(KEY_ACTIVE, false)
                .putBoolean(KEY_FOREGROUND, false)
                .putBoolean(KEY_RESTORING, false)
                .putLong(KEY_POSITION, 0).apply();
        PlaybackSnapshot.clear(context);
    }

    public static boolean isActive(Context context) {
        return context != null && prefs(context).getBoolean(KEY_ACTIVE, false)
                && !prefs(context).getBoolean(KEY_USER_EXIT, false);
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
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
