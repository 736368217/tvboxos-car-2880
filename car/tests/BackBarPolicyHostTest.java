import com.github.tvbox.osc.car.BackBarPolicy;

/** 旧返回杠备用方案的纯规则：短按、静止、单指、未取消。 */
public final class BackBarPolicyHostTest {
    private static int checks;

    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        check(BackBarPolicy.MAX_TAP_DURATION_MS == 300L, "tap ceiling is 300ms");
        check(BackBarPolicy.isTap(0f, 0L, false, false, 12f), "instant tap");
        check(BackBarPolicy.isTap(7.99f, 199L, false, false, 12f), "small movement quick tap");
        check(BackBarPolicy.isTap(11.99f, 299L, false, false, 12f), "boundary tap inside slop");
        check(!BackBarPolicy.isTap(12f, 100L, false, false, 12f), "slop boundary is a drag");
        check(BackBarPolicy.isTap(0f, 300L, false, false, 12f), "300ms boundary still taps");
        check(!BackBarPolicy.isTap(0f, 301L, false, false, 12f), "long press does not tap");
        check(!BackBarPolicy.isTap(0f, -1L, false, false, 12f), "negative duration rejected");
        check(!BackBarPolicy.isTap(0f, 100L, true, false, 12f), "cancelled does not tap");
        check(!BackBarPolicy.isTap(0f, 100L, false, true, 12f), "multi-touch does not tap");
        check(!BackBarPolicy.isTap(Float.NaN, 100L, false, false, 12f), "NaN distance rejected");
        check(BackBarPolicy.isTap(3f, 100L, false, false, -1f) == false, "negative slop becomes zero");
        check(BackBarPolicy.isTap(0f, 100L, false, false, 0f) == false, "zero slop rejects tap");

        System.out.println("BackBarPolicyHostTest PASS (" + checks + " checks)");
    }
}
