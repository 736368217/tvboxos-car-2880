import com.github.tvbox.osc.car.EdgeGeometry;

/** r76：边缘返回视觉/命中必须以像素封顶，不能被 AutoSize tokenScale 放大到近半屏。 */
public final class EdgeGeometryHostTest {

    private static int checks;

    private static void eq(float actual, float expected, String msg) {
        checks++;
        if (Math.abs(actual - expected) > 0.001f) {
            throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void check(boolean condition, String msg) {
        checks++;
        if (!condition) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        float width = 2880f, height = 1620f;

        // 用户实测的 2880×1620 / tokenScale=3.375 场景
        eq(EdgeGeometry.maxVisualWidthPx(width), 230.4f, "visual max is 8% of 2880px");
        eq(EdgeGeometry.triggerPx(width, 84f * 3.375f), 172.8f, "trigger capped at 6% width");
        eq(EdgeGeometry.hitWidthPx(width, 40f * 3.375f), 86.4f, "hit band capped at 3% width");
        eq(EdgeGeometry.leafHalfHeightPx(height, 170f * 3.375f), 178.2f,
                "leaf half-height capped at 11% height");

        // 配置值再大也不能超过封顶；小屏仍保留可触摸最小值
        check(EdgeGeometry.maxVisualWidthPx(width) < width / 4f,
                "visual must be far below half screen");
        check(EdgeGeometry.hitWidthPx(width, 99999f) < width / 10f,
                "hit band must be far below half screen");
        eq(EdgeGeometry.maxVisualWidthPx(600f), 48f, "small screen keeps minimum visual");
        eq(EdgeGeometry.hitWidthPx(600f, 99999f), 24f, "small screen keeps minimum hit");

        // 配置值比上限小则原样保留，避免把窄边缘再缩到不可点
        eq(EdgeGeometry.triggerPx(width, 100f), 100f, "small configured trigger retained");
        eq(EdgeGeometry.hitWidthPx(width, 50f), 50f, "small configured hit retained");

        System.out.println("EdgeGeometryHostTest PASS checks=" + checks);
    }

    private EdgeGeometryHostTest() {
    }
}
