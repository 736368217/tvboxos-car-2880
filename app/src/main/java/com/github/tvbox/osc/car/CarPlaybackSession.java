package com.github.tvbox.osc.car;

import com.orhanobut.hawk.Hawk;

/**
 * One-shot snapshot of the fullscreen playback page. It is written when the app leaves the
 * foreground during playback and consumed when Android has to recreate the task (process death or
 * task removal). Normal foreground/background switches never use it: the Activity and player stay
 * alive and keep their exact state.
 */
public final class CarPlaybackSession {
    private static final String ACTIVE = "car_playback_session_active";
    private static final String ID = "car_playback_session_id";
    private static final String SOURCE = "car_playback_session_source";
    private static final String TITLE = "car_playback_session_title";
    private static final String PICTURE = "car_playback_session_picture";
    private static final String FLAG = "car_playback_session_flag";
    private static final String INDEX = "car_playback_session_index";
    private static final String POSITION = "car_playback_session_position";
    private static final String UPDATED = "car_playback_session_updated";
    private static final long MAX_AGE_MS = 12L * 60L * 60L * 1000L;

    private CarPlaybackSession() { }

    public static final class Snapshot {
        public final String id;
        public final String sourceKey;
        public final String title;
        public final String picture;
        public final String playFlag;
        public final int playIndex;
        public final long position;

        Snapshot(String id, String sourceKey, String title, String picture,
                 String playFlag, int playIndex, long position) {
            this.id = id;
            this.sourceKey = sourceKey;
            this.title = title;
            this.picture = picture;
            this.playFlag = playFlag;
            this.playIndex = playIndex;
            this.position = position;
        }
    }

    /** Called from PlayFragment when the Activity is paused while fullscreen playback is active. */
    public static void save(String id, String sourceKey, String title, String picture,
                            String playFlag, int playIndex, long position) {
        if (id == null || id.isEmpty() || sourceKey == null || sourceKey.isEmpty()) return;
        Hawk.put(ACTIVE, true);
        Hawk.put(ID, id);
        Hawk.put(SOURCE, sourceKey);
        Hawk.put(TITLE, title == null ? "" : title);
        Hawk.put(PICTURE, picture == null ? "" : picture);
        Hawk.put(FLAG, playFlag == null ? "" : playFlag);
        Hawk.put(INDEX, playIndex);
        Hawk.put(POSITION, Math.max(0L, position));
        Hawk.put(UPDATED, System.currentTimeMillis());
    }

    /** Returns the pending snapshot without consuming it. */
    public static Snapshot peek() {
        if (!Boolean.TRUE.equals(Hawk.get(ACTIVE, false))) return null;
        long updated = Hawk.get(UPDATED, 0L);
        if (updated <= 0L || System.currentTimeMillis() - updated > MAX_AGE_MS) {
            clear();
            return null;
        }
        Snapshot snapshot = new Snapshot(
                Hawk.get(ID, ""),
                Hawk.get(SOURCE, ""),
                Hawk.get(TITLE, ""),
                Hawk.get(PICTURE, ""),
                Hawk.get(FLAG, ""),
                Hawk.get(INDEX, 0),
                Hawk.get(POSITION, 0L));
        return snapshot.id.isEmpty() || snapshot.sourceKey.isEmpty() ? null : snapshot;
    }

    /** Returns the pending snapshot once and marks it consumed, or null when there is none. */
    public static Snapshot consume() {
        Snapshot snapshot = peek();
        if (snapshot != null) clear();
        return snapshot;
    }

    /** Explicit navigation back to the home page ends the restore window. */
    public static void clear() {
        Hawk.put(ACTIVE, false);
        Hawk.put(UPDATED, 0L);
    }
}
