import com.github.tvbox.osc.cache.PlaybackSessionPolicy;

/** r78：播放会话恢复策略的宿主机回归。 */
public final class PlaybackSessionPolicyHostTest {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long hour = 60L * 60L * 1000L;
        long max = 12L * hour;
        long now = 1_700_000_000_000L;

        check(PlaybackSessionPolicy.shouldRestore(true, false, now - 60_000L, now, max),
                "active + background + fresh session restores");
        check(!PlaybackSessionPolicy.shouldRestore(false, false, now - 60_000L, now, max),
                "inactive session never restores");
        check(!PlaybackSessionPolicy.shouldRestore(true, true, now - 60_000L, now, max),
                "already foreground never restores");
        check(!PlaybackSessionPolicy.shouldRestore(true, false, now - 13L * hour, now, max),
                "stale session expires");
        check(!PlaybackSessionPolicy.shouldRestore(true, false, 0L, now, max),
                "missing timestamp never restores");
        check(!PlaybackSessionPolicy.shouldRestore(true, false, now + hour, now, max),
                "future timestamp never restores");

        System.out.println("PlaybackSessionPolicyHostTest PASS checks=" + checks);
    }

    private PlaybackSessionPolicyHostTest() {
    }
}
