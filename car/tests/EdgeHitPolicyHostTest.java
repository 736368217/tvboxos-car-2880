import com.github.tvbox.osc.car.EdgeHitPolicy;

/** Host-only checks: the whole edge strip must be live, and cooldown must never block a gesture. */
public final class EdgeHitPolicyHostTest {
    private static int checks;
    private static void check(boolean condition, String msg) {
        checks++;
        if (!condition) throw new AssertionError(msg);
    }
    private static void eq(int actual, int expected, String msg) {
        checks++;
        if (actual != expected) throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
    }
    public static void main(String[] args) {
        final float width = 2880f, top = 0f, bottom = 1620f, edge = 135f;
        eq(EdgeHitPolicy.hit(20f, 100f, width, top, bottom, edge, true, true), -1, "left near top");
        eq(EdgeHitPolicy.hit(20f, 810f, width, top, bottom, edge, true, true), -1, "left middle");
        eq(EdgeHitPolicy.hit(20f, 1550f, width, top, bottom, edge, true, true), -1, "left near bottom");
        eq(EdgeHitPolicy.hit(130f, 1400f, width, top, bottom, edge, true, true), -1, "left edge boundary");
        eq(EdgeHitPolicy.hit(2860f, 100f, width, top, bottom, edge, true, true), 1, "right near top");
        eq(EdgeHitPolicy.hit(2860f, 1500f, width, top, bottom, edge, true, true), 1, "right near bottom");
        // 旧版热区只认小棍中心 ±70dp，以下这些位置全部返回 0 -> 手势失效
        eq(EdgeHitPolicy.hit(60f, 60f, width, top, bottom, edge, true, true), -1, "far above old pill");
        eq(EdgeHitPolicy.hit(60f, 1580f, width, top, bottom, edge, true, true), -1, "far below old pill");
        // 页面中央必须完全穿透
        eq(EdgeHitPolicy.hit(1440f, 810f, width, top, bottom, edge, true, true), 0, "center passes through");
        eq(EdgeHitPolicy.hit(200f, 810f, width, top, bottom, edge, true, true), 0, "just outside edge passes through");
        eq(EdgeHitPolicy.hit(20f, -10f, width, top, bottom, edge, true, true), 0, "above screen passes through");
        eq(EdgeHitPolicy.hit(20f, 1700f, width, top, bottom, edge, true, true), 0, "below screen passes through");
        // 单侧配置
        eq(EdgeHitPolicy.hit(20f, 810f, width, top, bottom, edge, false, true), 0, "left disabled");
        eq(EdgeHitPolicy.hit(2860f, 810f, width, top, bottom, edge, true, false), 0, "right disabled");
        // 唤醒冷却：普通点亮被抑制，边缘手势无条件放行
        check(!EdgeHitPolicy.allowWake(1100L, 1000L, 1000L, false), "normal wake suppressed in cooldown");
        check(EdgeHitPolicy.allowWake(2100L, 1000L, 1000L, false), "normal wake allowed after cooldown");
        check(EdgeHitPolicy.allowWake(1001L, 1000L, 1000L, true), "edge wake never blocked by cooldown");
        check(EdgeHitPolicy.allowWake(1000L, 1000L, 1000L, true), "edge wake at zero delta allowed");
        System.out.println("EdgeHitPolicyHostTest PASS (" + checks + " checks)");
    }
}
