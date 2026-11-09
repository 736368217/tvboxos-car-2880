import xyz.doikki.videoplayer.exo.AutoLevel;

/** Host-only checks for automatic level alignment. */
public final class AutoLevelHostTest {
    private static int checks;
    private static void check(boolean condition, String msg) {
        checks++;
        if (!condition) throw new AssertionError(msg);
    }
    private static void near(float actual, float expected, float tol, String msg) {
        checks++;
        if (Math.abs(actual - expected) > tol) {
            throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        // 现场日志：-33 dBFS 音轨（峰值约 0.022）
        AutoLevel quiet = new AutoLevel();
        float m = quiet.update(0.022f, 96000);
        check(m > 1f, "quiet track must be boosted immediately after first window");
        for (int i = 0; i < 400; i++) m = quiet.update(0.022f, 96000);
        check(m > 3.5f, "makeup should converge toward MAX_MAKEUP for -33dBFS track, got " + m);
        check(m <= AutoLevel.MAX_MAKEUP, "makeup capped");
        near(0.022f * m, 0.088f, 0.01f, "output peak rises well above input");

        // 本来就很响的音轨：不压、不抬
        AutoLevel loud = new AutoLevel();
        float lm = 1f;
        for (int i = 0; i < 400; i++) lm = loud.update(0.95f, 96000);
        near(lm, 1f, 0.001f, "loud track must stay at unity gain");

        // 纯静音：不抬（否则底噪被放大）
        AutoLevel silent = new AutoLevel();
        float sm = 1f;
        for (int i = 0; i < 400; i++) sm = silent.update(0f, 96000);
        near(sm, 1f, 0.001f, "silence stays unity");

        // 组合增益：用户 100% 时就是自动补增益；300% 时总增益封顶
        near(AutoLevel.combined(4f, 1f, 1f), 4f, 0.0001f, "100% + makeup");
        near(AutoLevel.combined(4f, 3f, 1f), AutoLevel.MAX_TOTAL_GAIN, 0.0001f, "300% capped");
        near(AutoLevel.combined(4f, 3f, 0f), 0f, 0.0001f, "mute factor kills gain");
        near(AutoLevel.combined(4f, 3f, 0.5f), 4f, 0.0001f, "duck factor capped");
        near(AutoLevel.combined(Float.NaN, 3f, 1f), 1f, 0.0001f, "NaN safe");

        // 软限幅：小信号线性、大信号单调且不超 1
        near(AutoLevel.softClip(0.2f), 0.2f, 0.0001f, "linear below knee");
        near(AutoLevel.softClip(-0.2f), -0.2f, 0.0001f, "linear negative");
        check(AutoLevel.softClip(0.8f) < 0.8f && AutoLevel.softClip(0.8f) > 0.72f,
                "above knee compressed");
        check(AutoLevel.softClip(3f) <= 1f, "never exceeds full scale");
        check(AutoLevel.softClip(3f) > AutoLevel.softClip(1f), "soft clip is monotonic");
        near(AutoLevel.softClip(Float.NaN), 0f, 0.0001f, "NaN safe clip");

        // reset
        quiet.reset();
        near(quiet.makeup(), 1f, 0.0001f, "reset clears makeup");
        quiet.update(0.022f, 96000);

        System.out.println("AutoLevelHostTest PASS (" + checks + " checks)");
    }
}
