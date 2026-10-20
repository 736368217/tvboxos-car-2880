package com.github.tvbox.osc.car;

import com.orhanobut.hawk.Hawk;

/** Optional home shortcuts. Categories and settings remain fixed by the home information architecture. */
public final class CarHomeNavigationPreferences {
    public static final String HOME = "car_home_nav_home";
    public static final String LIVE = "car_home_nav_live";
    public static final String HISTORY = "car_home_nav_history";
    public static final String COLLECT = "car_home_nav_collect";
    public static final String PUSH = "car_home_nav_push";

    private CarHomeNavigationPreferences() { }

    public static boolean isVisible(String key) {
        return Hawk.get(key, true);
    }

    public static void setVisible(String key, boolean visible) {
        Hawk.put(key, visible);
    }
}