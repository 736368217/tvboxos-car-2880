import com.github.tvbox.osc.backbar.BackBarInteractionPolicy;

/** Host-only rules for tap safety, multi-touch rejection and the four-second default. */
public final class BackBarInteractionPolicyHostTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        float slop = 24f;
        check(BackBarInteractionPolicy.isTap(0f, 80L, false, false, slop), "stationary short tap");
        check(BackBarInteractionPolicy.isTap(23.9f, 300L, false, false, slop), "tap at duration limit");
        check(!BackBarInteractionPolicy.isTap(24f, 80L, false, false, slop), "movement at slop rejected");
        check(!BackBarInteractionPolicy.isTap(0f, 301L, false, false, slop), "long press rejected");
        check(!BackBarInteractionPolicy.isTap(0f, 80L, true, false, slop), "cancelled gesture rejected");
        check(!BackBarInteractionPolicy.isTap(0f, 80L, false, true, slop), "multi-touch rejected");
        check(!BackBarInteractionPolicy.isTap(0f, -1L, false, false, slop), "invalid duration rejected");
        check(!BackBarInteractionPolicy.isTap(Float.NaN, 80L, false, false, slop), "NaN distance rejected");
        check(!BackBarInteractionPolicy.isTap(Float.POSITIVE_INFINITY, 80L, false, false, slop),
                "infinite distance rejected");
        check(BackBarInteractionPolicy.isTap(0f, 80L, false, false, 0f) == false,
                "zero slop cannot produce a tap");
        check(BackBarInteractionPolicy.isTap(0f, 80L, false, false, -5f) == false,
                "negative slop cannot produce a tap");

        check(BackBarInteractionPolicy.normalizeAutoHideMillis(0L) == 4_000L,
                "legacy zero normalizes to 4 s, never permanent");
        check(BackBarInteractionPolicy.normalizeAutoHideMillis(-1L) == 4_000L,
                "negative idle value normalizes to 4 s");
        check(BackBarInteractionPolicy.normalizeAutoHideMillis(6_000L) == 6_000L,
                "optional six-second interval accepted");
        check(BackBarInteractionPolicy.normalizeAutoHideMillis(8_000L) == 8_000L,
                "optional eight-second interval accepted");
        check(BackBarInteractionPolicy.normalizeAutoHideMillis(1L) == 1_000L,
                "sub-second value clamps to a usable minimum");

        System.out.println("PASS: " + checks + " Back-bar gesture/timer checks");
    }
}
