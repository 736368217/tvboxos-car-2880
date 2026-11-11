import com.github.tvbox.osc.car.BackDisplayMetrics;

/** 方案 A：按分辨率占比与可信密度自适应，不能把视觉写死成 50/120/180px。 */
public final class BackDisplayMetricsHostTest {
    private static int checks;

    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }

    private static void near(float actual, float expected, float tolerance, String name) {
        check(Math.abs(actual - expected) <= tolerance, name + " got=" + actual + " want=" + expected);
    }

    private static void proportions(BackDisplayMetrics m, String name) {
        check(m.hitPx >= 36f && m.hitPx <= Math.max(36f, Math.min(
                        m.widthPx * BackDisplayMetrics.HIT_FRACTION, BackDisplayMetrics.HIT_CAP_PX)),
                name + " hit within fraction/cap");
        check(m.visualPx >= 72f && m.visualPx <= Math.max(72f, Math.min(
                        m.widthPx * BackDisplayMetrics.VISUAL_FRACTION, BackDisplayMetrics.VISUAL_CAP_PX)),
                name + " visual within fraction/cap");
        check(m.triggerPx <= m.visualPx, name + " trigger never exceeds visual");
        check(m.leafHalfPx >= 72f && m.leafHalfPx <= Math.max(72f, Math.min(
                        m.heightPx * BackDisplayMetrics.LEAF_FRACTION, BackDisplayMetrics.LEAF_CAP_PX)),
                name + " leaf within fraction/cap");
        check(m.barHitWidthPx >= m.barWidthPx, name + " bar hit covers visual bar");
        check(m.barHitHeightPx >= m.barHeightPx, name + " bar hit covers visual height");
        check(m.guidePx >= 2f && m.guidePx <= 7f, name + " guide remains thin");
        check(m.log().contains("screen=") && m.log().contains("dpiTrusted="),
                name + " log includes geometry and dpi trust");
    }

    public static void main(String[] args) {
        // 零跑 B01 真车：xdpi/ydpi 是 91.44 虚拟化桩值，densityDpi=160 是唯一可信尺度。
        BackDisplayMetrics car = BackDisplayMetrics.from(2880f, 1620f, 91.44f, 91.44f, 160f);
        check(!car.trustedPhysicalPpiX && !car.trustedPhysicalPpiY, "car stub ppi rejected");
        near(car.effectivePpiX, 160f, .001f, "car effective ppi falls back to density");
        near(car.hitPx, 50.39f, .1f, "car hit ~8mm / 50px");
        near(car.visualPx, 125.98f, .1f, "car visual ~20mm / 126px");
        near(car.triggerPx, 113.39f, .1f, "car trigger ~18mm / 113px");
        near(car.leafHalfPx, 178.2f, .1f, "car leaf capped by 11% height");
        check(car.visualPx < car.widthPx * .06f, "car visual is under 6% width");
        proportions(car, "car");

        // MuMu 在 wm density 160 下仍报 xdpi=440；必须拒绝该桩值并按车机 160 计算。
        BackDisplayMetrics mumu = BackDisplayMetrics.from(2880f, 1620f, 440f, 440f, 160f);
        check(!mumu.trustedPhysicalPpiX && !mumu.trustedPhysicalPpiY, "MuMu inconsistent ppi rejected");
        near(mumu.hitPx, car.hitPx, .001f, "MuMu hit matches car benchmark");
        near(mumu.visualPx, car.visualPx, .001f, "MuMu visual matches car benchmark");
        near(mumu.barWidthPx, car.barWidthPx, .001f, "MuMu bar matches car benchmark");
        proportions(mumu, "MuMu");

        // 1280x720 低分辨率：占比上限保证视觉仍只是边缘窄带。
        BackDisplayMetrics small = BackDisplayMetrics.from(1280f, 720f, 160f, 160f, 160f);
        near(small.hitPx, 38.4f, .1f, "small screen hit capped by 3% width");
        near(small.visualPx, 72f, .1f, "small screen visual min px");
        check(small.visualPx <= 1280f * .06f, "small screen visual under 6% width");
        near(small.leafHalfPx, 79.2f, .1f, "small screen leaf capped by 11% height");
        proportions(small, "small");

        // 3840x2160 / density 320：可信 ppi 随物理像素密度放大，但绝对上限封顶。
        BackDisplayMetrics fourK = BackDisplayMetrics.from(3840f, 2160f, 320f, 320f, 320f);
        check(fourK.trustedPhysicalPpiX && fourK.trustedPhysicalPpiY, "congruent 4K ppi trusted");
        near(fourK.hitPx, 96f, .1f, "4K hit absolute cap");
        near(fourK.visualPx, 160f, .1f, "4K visual absolute cap");
        check(fourK.visualPx < fourK.widthPx * BackDisplayMetrics.VISUAL_FRACTION + 1f,
                "4K visual under fraction");
        near(fourK.leafHalfPx, 210f, .1f, "4K leaf absolute cap");
        proportions(fourK, "4K");

        // 极高密度小窗口：占比上限低于最小可触摸像素时，保留最小可触摸像素。
        BackDisplayMetrics dense = BackDisplayMetrics.from(640f, 360f, 960f, 960f, 960f);
        near(dense.hitPx, 36f, .001f, "dense tiny window hit min px");
        near(dense.visualPx, 72f, .001f, "dense tiny window visual min px");
        near(dense.leafHalfPx, 72f, .001f, "dense tiny window leaf min px");
        proportions(dense, "dense");

        // 非法密度/尺寸不能产生 NaN 或除零。
        BackDisplayMetrics weird = BackDisplayMetrics.from(0f, 0f, Float.NaN, Float.POSITIVE_INFINITY, 0f);
        near(weird.widthPx, 2880f, .001f, "invalid width fallback");
        near(weird.heightPx, 1620f, .001f, "invalid height fallback");
        near(weird.densityDpi, 160f, .001f, "invalid density fallback");
        check(!Float.isNaN(weird.hitPx) && weird.hitPx > 0f, "invalid dpi hit finite");
        check(!Float.isNaN(weird.visualPx) && weird.visualPx > 0f, "invalid dpi visual finite");
        proportions(weird, "weird");

        // 返回杠左/右贴边与点击热区不越界。
        near(car.barLeft(false), 0f, .001f, "left bar flush");
        near(car.barLeft(true), car.widthPx - car.barWidthPx, .001f, "right bar flush");
        near(car.barHitLeft(false), 0f, .001f, "left bar hit flush");
        near(car.barHitLeft(true), car.widthPx - car.barHitWidthPx, .001f, "right bar hit flush");
        check(car.barHitLeft(true) >= 0f, "right bar hit inside window");

        System.out.println("BackDisplayMetricsHostTest PASS (" + checks + " checks)");
    }
}
