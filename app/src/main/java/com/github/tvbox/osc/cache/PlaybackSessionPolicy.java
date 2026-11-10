package com.github.tvbox.osc.cache;

/** r78：播放保活会话恢复策略，纯 Java，便于宿主机回归。 */
public final class PlaybackSessionPolicy {

    private PlaybackSessionPolicy() {
    }

    /**
     * 是否应在回前台时自动重建详情页/播放页。
     *
     * <p>条件：会话仍活跃、上次离开时处于后台、状态足够新。用户主动退出会清 active；
     * 正常回到仍存活的播放页时 foreground 会被重新置 true，不会重复拉起详情页。</p>
     */
    public static boolean shouldRestore(boolean active, boolean foreground,
                                         long updatedMs, long nowMs, long maxAgeMs) {
        if (!active || foreground) return false;
        if (updatedMs <= 0 || nowMs <= 0) return false;
        long age = nowMs - updatedMs;
        return age >= 0 && age <= Math.max(0, maxAgeMs);
    }
}
