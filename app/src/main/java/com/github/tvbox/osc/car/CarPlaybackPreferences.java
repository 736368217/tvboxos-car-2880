package com.github.tvbox.osc.car;

import com.orhanobut.hawk.Hawk;

/** Independent car interaction preferences; no change to decoder or source identity. */
public final class CarPlaybackPreferences {
    private CarPlaybackPreferences() { }
    public static boolean backgroundAudio() { return Hawk.get("car_ux_background_audio", false); }
    public static void setBackgroundAudio(boolean value) { Hawk.put("car_ux_background_audio", value); }
    public static boolean gestures() { return Hawk.get("car_ux_gestures", true); }
    public static void setGestures(boolean value) { Hawk.put("car_ux_gestures", value); }
    public static boolean longPress() { return Hawk.get("car_ux_long_press", true); }
    public static void setLongPress(boolean value) { Hawk.put("car_ux_long_press", value); }
}
