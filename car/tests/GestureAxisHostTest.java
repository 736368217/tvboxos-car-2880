import com.github.tvbox.osc.car.GestureAxis;

/** Host-only checks: one playback gesture must lock exactly one axis. */
public final class GestureAxisHostTest {
    private static int checks;
    private static void eq(int actual, int expected, String msg) {
        checks++;
        if (actual != expected) throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
    }
    public static void main(String[] args) {
        final float slop = 24f, dom = 1.2f;
        eq(GestureAxis.decide(0, 0, slop, dom, true), GestureAxis.NONE, "no movement");
        eq(GestureAxis.decide(10, 0, slop, dom, true), GestureAxis.NONE, "under slop");
        eq(GestureAxis.decide(0, 10, slop, dom, true), GestureAxis.NONE, "under slop vertical");
        eq(GestureAxis.decide(200, 5, slop, dom, true), GestureAxis.X, "clear horizontal");
        eq(GestureAxis.decide(-200, 5, slop, dom, true), GestureAxis.X, "clear horizontal left");
        eq(GestureAxis.decide(5, 200, slop, dom, true), GestureAxis.Y, "clear vertical");
        eq(GestureAxis.decide(5, -200, slop, dom, true), GestureAxis.Y, "clear vertical up");
        // 用户场景：想上下拉音量但手指歪了，横向噪声远小于纵向
        eq(GestureAxis.decide(20, 180, slop, dom, true), GestureAxis.Y, "slightly tilted vertical stays volume");
        eq(GestureAxis.decide(-35, 240, slop, dom, true), GestureAxis.Y, "tilted vertical stays volume");
        eq(GestureAxis.decide(60, 300, slop, dom, true), GestureAxis.Y, "tilted vertical stays volume 2");
        // 含糊的 45° 宁可什么都不做，也不误调第二种功能
        eq(GestureAxis.decide(100, 100, slop, dom, true), GestureAxis.IGNORE, "ambiguous diagonal ignored");
        eq(GestureAxis.decide(100, 110, slop, dom, true), GestureAxis.IGNORE, "near diagonal ignored");
        eq(GestureAxis.decide(110, 100, slop, dom, true), GestureAxis.IGNORE, "near diagonal ignored 2");
        eq(GestureAxis.decide(200, 5, slop, dom, false), GestureAxis.IGNORE, "position disabled");
        eq(GestureAxis.decide(5, -200, slop, dom, false), GestureAxis.Y, "vertical still works");
        System.out.println("GestureAxisHostTest PASS (" + checks + " checks)");
    }
}
