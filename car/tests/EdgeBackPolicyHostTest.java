import com.github.tvbox.osc.car.BackDisplayMetrics;
import com.github.tvbox.osc.car.EdgeBackPolicy;

/** 方案 A：唯一输入状态机，覆盖命中/空白穿透、方向锁定、取消、fling、多指与两种模式。 */
public final class EdgeBackPolicyHostTest {
    private static int checks;

    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }

    private static void near(float actual, float expected, float tolerance, String name) {
        check(Math.abs(actual - expected) <= tolerance, name + " got=" + actual + " want=" + expected);
    }

    private static final class Parent implements EdgeBackPolicy.ParentBridge {
        int disallow;
        @Override public void requestDisallowInterceptTouchEvent(boolean value) { disallow += value ? 1 : -1; }
    }

    private static BackDisplayMetrics metrics() {
        return BackDisplayMetrics.from(2880f, 1620f, 91.44f, 91.44f, 160f);
    }

    private static EdgeBackPolicy edge(EdgeBackPolicy.Side side, Parent parent) {
        return new EdgeBackPolicy(metrics(), EdgeBackPolicy.Mode.EDGE_GESTURE, side, parent);
    }

    private static EdgeBackPolicy bar(EdgeBackPolicy.Side side, Parent parent) {
        return new EdgeBackPolicy(metrics(), EdgeBackPolicy.Mode.BACK_BAR, side, parent);
    }

    private static EdgeBackPolicy.Action drag(EdgeBackPolicy p, float startX, float endX, long stepMs) {
        p.down(7, startX, 810f, 1000L);
        p.move(7, endX, 812f, 1000L + stepMs);
        return p.up(7, endX, 812f, 1000L + stepMs + 10L);
    }

    public static void main(String[] args) {
        BackDisplayMetrics m = metrics();
        Parent parent = new Parent();
        EdgeBackPolicy p = edge(EdgeBackPolicy.Side.LEFT, parent);

        // 命中边界与空白区：空白区 DOWN 立即 NONE，绝不拦截内容点击。
        check(p.hitSide(0f) == -1, "left x=0 hits");
        check(p.hitSide(m.hitPx - .01f) == -1, "left inclusive hit boundary");
        check(p.hitSide(m.hitPx + .01f) == 0, "one pixel outside hit passes through");
        check(p.hitSide(1440f) == 0, "center passes through");
        check(p.down(7, 1440f, 810f, 1000L) == EdgeBackPolicy.Action.NONE, "blank DOWN NONE");
        check(!p.consumed(), "blank DOWN does not capture");
        check(p.move(7, 1500f, 810f, 1020L) == EdgeBackPolicy.Action.NONE, "blank MOVE NONE");
        check(p.up(7, 1500f, 810f, 1030L) == EdgeBackPolicy.Action.NONE, "blank UP NONE");

        // 左边缘距离达阈值才提交；视觉宽度封顶。
        check(p.down(7, 10f, 810f, 2000L) == EdgeBackPolicy.Action.CONSUME, "edge DOWN consumes");
        check(p.state() == EdgeBackPolicy.State.ARMED, "edge DOWN armed");
        check(p.consumed(), "edge DOWN captures");
        check(p.move(7, 10f + m.slopPx * .5f, 811f, 2010L) == EdgeBackPolicy.Action.CONSUME,
                "sub-slop MOVE consumed");
        check(p.state() == EdgeBackPolicy.State.ARMED, "sub-slop stays armed");
        check(p.move(7, 10f + m.triggerPx + 5f, 812f, 2100L) == EdgeBackPolicy.Action.CONSUME,
                "drag MOVE consumed");
        check(p.state() == EdgeBackPolicy.State.DRAGGING, "horizontal MOVE locks drag");
        check(parent.disallow == 1, "horizontal drag disallows parent intercept");
        near(p.visualWidth(), m.triggerPx + 5f, .001f, "visual width follows inward distance");
        p.move(7, 10f + m.visualPx * 2f, 812f, 2105L);
        near(p.visualWidth(), m.visualPx, .001f, "visual width caps at adaptive max");
        check(p.pullingLeft(), "left drag mirrors left");
        check(p.up(7, 10f + m.triggerPx + 5f, 812f, 2110L) == EdgeBackPolicy.Action.BACK,
                "distance commits BACK");
        check(p.committed(), "state COMMIT");
        p.reset();
        check(!p.consumed(), "reset releases capture");
        check(p.state() == EdgeBackPolicy.State.IDLE, "reset returns IDLE");

        // 竖向占优：取消手势但完整消费已捕获流，不触发返回。
        p.down(8, 10f, 810f, 3000L);
        check(p.move(8, 12f, 900f, 3020L) == EdgeBackPolicy.Action.CONSUME, "vertical MOVE consumed");
        check(p.state() == EdgeBackPolicy.State.CANCEL, "vertical cancels");
        check(p.up(8, 12f, 900f, 3030L) == EdgeBackPolicy.Action.NONE, "vertical UP no BACK");
        check(parent.disallow == 0, "vertical cancel restores parent intercept");
        p.reset();

        // 斜向近似 45°：仍然取消，避免列表滚动误触。
        p.down(9, 10f, 810f, 4000L);
        check(p.move(9, 110f, 910f, 4020L) == EdgeBackPolicy.Action.CONSUME, "diagonal MOVE consumed");
        check(p.state() == EdgeBackPolicy.State.CANCEL, "diagonal cancels");
        check(p.up(9, 110f, 910f, 4030L) == EdgeBackPolicy.Action.NONE, "diagonal UP no BACK");
        p.reset();

        // 距离不足且速度不足：不返回。
        check(drag(p, 10f, 10f + m.triggerPx * .5f, 200L) == EdgeBackPolicy.Action.NONE,
                "short slow drag no BACK");
        check(p.state() == EdgeBackPolicy.State.CANCEL, "short slow drag CANCEL");
        p.reset();

        // 快速 fling：方向必须朝屏幕内，右边缘要求负速度。
        p.down(10, 10f, 810f, 5000L);
        p.move(10, 30f, 811f, 5010L);
        check(p.up(10, 90f, 811f, 5020L) == EdgeBackPolicy.Action.BACK, "left fast inward fling BACK");
        p.reset();
        EdgeBackPolicy right = edge(EdgeBackPolicy.Side.RIGHT, parent);
        check(right.hitSide(m.widthPx) == 1, "right boundary hits");
        check(right.down(11, m.widthPx - 10f, 810f, 6000L) == EdgeBackPolicy.Action.CONSUME,
                "right DOWN consumes");
        right.move(11, m.widthPx - 30f, 811f, 6010L);
        check(!right.pullingLeft(), "right drag mirrors right");
        check(right.up(11, m.widthPx - 90f, 811f, 6020L) == EdgeBackPolicy.Action.BACK,
                "right inward fling BACK");
        right.reset();

        // 右边缘向外甩不能靠 Math.abs 误判为返回。
        EdgeBackPolicy outward = edge(EdgeBackPolicy.Side.RIGHT, parent);
        outward.down(12, m.widthPx - 10f, 810f, 7000L);
        outward.move(12, m.widthPx - 30f, 811f, 7010L);
        outward.move(12, 100f, 811f, 7020L); // 大负向内位移，但最后一帧速度向外为正
        outward.move(12, m.widthPx, 811f, 7030L);
        check(outward.up(12, m.widthPx, 811f, 7040L) == EdgeBackPolicy.Action.BACK,
                "distance still commits regardless of outward last frame");
        outward.reset();

        // BOTH：两侧命中，中间空白穿透。
        EdgeBackPolicy both = edge(EdgeBackPolicy.Side.BOTH, parent);
        check(both.hitSide(0f) == -1 && both.hitSide(m.widthPx) == 1, "BOTH edges hit");
        check(both.hitSide(1440f) == 0, "BOTH center passes through");
        both.reset();

        // CANCEL / 多指 / 错误 pointerId：状态机必须拒绝返回并释放拦截。
        p.down(13, 10f, 810f, 8000L);
        p.move(13, 100f, 811f, 8010L);
        check(p.cancel(13) == EdgeBackPolicy.Action.CONSUME, "ACTION_CANCEL consumed");
        check(p.up(13, 100f, 811f, 8020L) == EdgeBackPolicy.Action.NONE, "cancelled UP no BACK");
        p.reset();
        p.down(14, 10f, 810f, 9000L);
        p.multiTouch();
        check(p.up(14, 200f, 811f, 9010L) == EdgeBackPolicy.Action.NONE, "multi-touch no BACK");
        p.reset();
        p.down(15, 10f, 810f, 10000L);
        check(p.move(99, 200f, 811f, 10010L) == EdgeBackPolicy.Action.NONE, "wrong pointer MOVE ignored");
        check(p.up(99, 200f, 811f, 10020L) == EdgeBackPolicy.Action.NONE, "wrong pointer UP ignored");
        p.reset();

        // 备用返回杠：短按返回，长按/拖动/多指/空白区不返回。
        EdgeBackPolicy b = bar(EdgeBackPolicy.Side.LEFT, parent);
        float bx = m.barHitWidthPx * .5f;
        float by = m.heightPx * .5f;
        check(b.hitBar(bx, by), "bar center hit");
        check(!b.hitBar(m.widthPx * .5f, by), "bar center blank passes through");
        check(!b.hitBar(bx, 0f), "bar above hit box passes through");
        check(b.down(16, bx, by, 11000L) == EdgeBackPolicy.Action.CONSUME, "bar DOWN consumes");
        check(b.state() == EdgeBackPolicy.State.BAR_ARMED, "bar DOWN armed");
        check(b.up(16, bx, by, 11100L) == EdgeBackPolicy.Action.BACK, "bar short tap BACK");
        b.reset();
        b.down(17, bx, by, 12000L);
        check(b.up(17, bx, by, 12400L) == EdgeBackPolicy.Action.NONE, "bar long press no BACK");
        b.reset();
        b.down(18, bx, by, 13000L);
        b.move(18, bx + m.slopPx + 1f, by, 13020L);
        check(b.up(18, bx + m.slopPx + 1f, by, 13030L) == EdgeBackPolicy.Action.NONE, "bar drag no BACK");
        b.reset();
        b.down(19, bx, by, 14000L);
        b.multiTouch();
        check(b.up(19, bx, by, 14020L) == EdgeBackPolicy.Action.NONE, "bar multi-touch no BACK");
        b.reset();
        EdgeBackPolicy rb = bar(EdgeBackPolicy.Side.RIGHT, parent);
        float rbx = m.widthPx - m.barHitWidthPx * .5f;
        check(rb.hitBar(rbx, by), "right bar center hit");
        check(rb.down(20, rbx, by, 15000L) == EdgeBackPolicy.Action.CONSUME, "right bar DOWN");
        check(rb.up(20, rbx, by, 15100L) == EdgeBackPolicy.Action.BACK, "right bar short tap BACK");

        System.out.println("EdgeBackPolicyHostTest PASS (" + checks + " checks)");
    }
}
