package com.github.tvbox.osc.car;

import com.orhanobut.hawk.Hawk;

import java.lang.ref.WeakReference;

/** Independent car interaction preferences; no change to decoder or source identity. */
public final class CarPlaybackPreferences {
    public interface BackgroundAudioListener {
        void onBackgroundAudioChanged(boolean enabled);
    }

    private static WeakReference<BackgroundAudioListener> backgroundAudioListener = new WeakReference<>(null);

    private CarPlaybackPreferences() { }

    public static boolean backgroundAudio() {
        return Hawk.get("car_ux_background_audio", false);
    }

    public static void setBackgroundAudio(boolean value) {
        Hawk.put("car_ux_background_audio", value);
        BackgroundAudioListener listener = backgroundAudioListener.get();
        if (listener != null) listener.onBackgroundAudioChanged(value);
    }

    public static void registerBackgroundAudioListener(BackgroundAudioListener listener) {
        backgroundAudioListener = new WeakReference<>(listener);
    }

    public static void unregisterBackgroundAudioListener(BackgroundAudioListener listener) {
        if (backgroundAudioListener.get() == listener) backgroundAudioListener = new WeakReference<>(null);
    }

    public static boolean gestures() {
        return Hawk.get("car_ux_gestures", true);
    }

    public static void setGestures(boolean value) {
        Hawk.put("car_ux_gestures", value);
    }

    public static boolean longPress() {
        return Hawk.get("car_ux_long_press", false);
    }

    public static void setLongPress(boolean value) {
        Hawk.put("car_ux_long_press", value);
    }
}
