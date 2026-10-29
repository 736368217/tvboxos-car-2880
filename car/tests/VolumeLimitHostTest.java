/**
 * 宿主机检查：应用内音量上限的判定。
 *
 * <p>规则：只有支持 &gt;100% 增益的播放器（PCM 采样增益，即本项目的 ExoPlayer）才能到 200%；
 * 其余播放器上限 100% —— 因为它们只能依赖系统 LoudnessEnhancer，很多车机上不生效，
 * 让用户拖到 200% 却听不出变化属于误导。</p>
 *
 * <pre>
 * javac -encoding UTF-8 -d out car/tests/VolumeLimitHostTest.java
 * java -cp out VolumeLimitHostTest
 * </pre>
 */
public final class VolumeLimitHostTest {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void eq(float actual, float expected, String message) {
        checks++;
        if (Math.abs(actual - expected) > 0.0001f) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    /** 与 BaseController/clampVolume 同构的最小实现，用来锁住边界行为。 */
    static float clamp(float v, boolean supportsBoost) {
        float max = supportsBoost ? 2f : 1f;
        return Math.max(0f, Math.min(max, v));
    }

    public static void main(String[] args) {
        // EXO（支持增益）：0..200%
        eq(clamp(0f, true), 0f, "exo: 0 stays 0");
        eq(clamp(0.5f, true), 0.5f, "exo: 50% passes through");
        eq(clamp(1f, true), 1f, "exo: 100% stays 100%");
        eq(clamp(1.8f, true), 1.8f, "exo: 180% allowed");
        eq(clamp(2f, true), 2f, "exo: 200% allowed");
        eq(clamp(3f, true), 2f, "exo: clamps at 200%");

        // 非 EXO（IJK / 系统播放器）：最高 100%
        eq(clamp(0f, false), 0f, "ijk: 0 stays 0");
        eq(clamp(0.5f, false), 0.5f, "ijk: 50% passes through");
        eq(clamp(1f, false), 1f, "ijk: 100% stays 100%");
        eq(clamp(1.2f, false), 1f, "ijk: 120% clamped to 100%");
        eq(clamp(1.8f, false), 1f, "ijk: 180% clamped to 100%");
        eq(clamp(2f, false), 1f, "ijk: 200% clamped to 100%");

        // 从 EXO 切到 IJK 时，存下来的 180% 必须立刻压回 100%（切换后重算）
        float stored = 1.8f;
        eq(clamp(stored, false), 1f, "switch exo->ijk drops stored 180% to 100%");
        eq(clamp(stored, true), 1.8f, "switch ijk->exo keeps the stored value");

        // 负值/异常值
        eq(clamp(-1f, true), 0f, "negative clamped to 0 (exo)");
        eq(clamp(-1f, false), 0f, "negative clamped to 0 (ijk)");

        System.out.println("VolumeLimitHostTest PASS (" + checks + " checks)");
    }
}