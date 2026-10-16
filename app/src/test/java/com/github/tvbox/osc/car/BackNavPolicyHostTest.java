package com.github.tvbox.osc.car;

/** Standalone host test; run with javac/java, with no Android runtime or JUnit dependency. */
public final class BackNavPolicyHostTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void near(float actual, float expected, float tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        carMetrics();
        hitBoxes();
        edgeGestures();
        geometry();
        floating();
        System.out.println("PASS: " + checks + " Back navigation policy checks");
    }
    private static void carMetrics() {
        for (float density : new float[]{1, 1.5f, 2, 3}) {
            float scale = BackNavPolicy.carTokenScale(2880, 1620, density);
            near(scale, 3.375f, .001f, "Car scale independent of density bucket");
            near(56 * scale, 189, .01f, "Standard ball is189px");
            near(72 * scale, 243, .01f, "Standard blind-touch target is243px");
            near(64 * scale, 216, .01f, "Large ball is216px");
            near(80 * scale, 270, .01f, "Large target is270px");
            near(6 * scale, 20.25f, .01f, "Visible edge rail width");
            near(76 * scale, 256.5f, .01f, "Visible edge rail height");
            near(28 * scale, 94.5f, .01f, "Scoped edge touch width");
            near(120 * scale, 405, .01f, "Scoped edge touch height");
            near(48 * scale, 162, .01f, "Physical pull threshold scales coherently");
            near(56 * scale / 1620, 7f / 60f, .001f, "Ball occupies11.67percent of screen height");
            float reserve = 28 * scale + 12 * density + 56 * scale * .35f;
            check(reserve + BackNavPolicy.idleOffset(56 * scale, true, 1) >= 28 * scale + 12 * density - .01f,
                    "Idle ball does not overlap edge pull hot zone in dual mode");
        }
        near(BackNavPolicy.carTokenScale(1920, 1080, 1), 2.25f, .001f, "1080p car proportions");
        near(BackNavPolicy.carTokenScale(3840, 2160, 1), 4.5f, .001f, "4K car proportions");
        near(BackNavPolicy.carTokenScale(0, 0, 1.5f), 1.5f, .001f, "Prelayout density fallback");
        for (int height : new int[]{100, 180, 360, 720, 1620}) {
            float scale = BackNavPolicy.carTokenScale(height * 16f / 9f, height, 1.5f);
            check(80 * scale <= height, "Large target fits short window");
            check(72 * scale >= Math.min(72, height * .75f), "Small window retains a usable minimum");
        }
        near(BackNavPolicy.capsuleWidth(0, 0), 0, .001f, "Capsule retracts to zero");
        near(BackNavPolicy.capsuleWidth(BackNavPolicy.pullWidth(48), 1), 96, .001f,
                "Armed capsule has room for separate text and round arrow");
        float previous = 0;
        for (int step = 0; step <= 100; step++) {
            float progress = step / 100f;
            float width = BackNavPolicy.capsuleWidth(BackNavPolicy.pullWidth(48 * progress), progress);
            check(width >= previous && width <= 104.401f, "Capsule width grows continuously within limit");
            previous = width;
        }
    }

    private static void hitBoxes() {
        check(BackNavPolicy.edgeHit(3, 500, 1280, 500, true, false) == -1, "Left pill local box");
        check(BackNavPolicy.edgeHit(27.99f, 559.99f, 1280, 500, true, true) == -1, "28x120 inclusive interior");
        check(BackNavPolicy.edgeHit(28, 500, 1280, 500, true, true) == 0, "Outside narrow box passes through");
        check(BackNavPolicy.edgeHit(0, 560, 1280, 500, true, true) == 0, "Outside local height passes through");
        check(BackNavPolicy.edgeHit(10, 439, 1280, 500, true, true) == 0, "Other edge height passes through");
        check(BackNavPolicy.edgeHit(640, 500, 1280, 500, true, true) == 0, "Blank content passes through");
        check(BackNavPolicy.edgeHit(1277, 500, 1280, 500, false, true) == 1, "Right local box");
        check(BackNavPolicy.edgeHit(1277, 500, 1280, 500, true, false) == 0, "Disabled right side passes through");
        check(BackNavPolicy.edgeHit(-1, 500, 1280, 500, true, true) == 0, "Negative coordinates rejected");
        check(BackNavPolicy.edgeHit(1280, 500, 1280, 500, true, true) == 0, "Outside window rejected");
    }

    private static void edgeGestures() {
        BackNavPolicy.EdgeGesture g = new BackNavPolicy.EdgeGesture();
        g.begin(4, 500, true);
        check(!g.end(false), "Edge tap never goes Back");
        g.begin(4, 500, true);
        check(!g.move(51.99f, 501), "Below raw 48dp does not arm");
        check(!g.isArmed() && !g.end(false), "Subthreshold release cancels");
        g.begin(4, 500, true);
        check(g.move(52, 501), "Exactly 48 raw dp crosses threshold");
        check(g.isArmed(), "48 raw dp arms despite damped width below 48");
        check(!g.move(100, 502), "Further pull does not repeat feedback");
        check(g.end(false), "Armed release dispatches");
        check(!g.end(false), "One gesture cannot dispatch twice");
        g.begin(4, 500, true);
        check(g.move(80, 500), "New gesture has new feedback");
        check(!g.end(true), "Armed CANCEL never dispatches");
        g.begin(4, 500, true);
        check(g.move(80, 500), "Arm before changing mind");
        check(!g.move(20, 500) && !g.isArmed(), "Pulling back below threshold disarms");
        check(!g.end(false), "Disarmed release cancels");
        g.begin(4, 500, true);
        check(g.move(80, 500), "First crossing feedback");
        g.move(20, 500);
        check(!g.move(80, 500), "Rearming does not repeat haptic in same gesture");
        check(g.end(false), "Rearmed release still dispatches once");
        g.begin(4, 500, true);
        g.move(64, 540);
        check(g.state() == BackNavPolicy.EdgeGesture.State.REJECTED, "1.5x equality is not horizontal dominance");
        check(!g.longPress() && !g.end(false), "Diagonal gesture cannot become long press or Back");
        g.begin(4, 500, true);
        g.move(7, 520);
        g.move(100, 520);
        check(!g.end(false), "Vertical-first swipe stays rejected");
        g.begin(4, 500, true);
        g.move(14, 500);
        g.move(80, 700);
        check(!g.isArmed() && !g.end(false), "Horizontal start cannot arm a vertical-dominant release");
        g.begin(4, 500, true);
        g.move(-60, 500);
        check(!g.end(false), "Outward pull rejected");
        g.begin(1276, 500, false);
        check(g.move(1228, 501) && g.end(false), "Right edge uses inward-negative dx");
        g.begin(4, 500, true);
        check(BackNavPolicy.LONG_PRESS_MS == 400 && g.longPress(), "400ms scheduled long press enables reposition");
        g.move(100, 700);
        near(g.deltaY(), 200, .001f, "Reposition preserves finger delta");
        check(!g.isArmed() && !g.end(false), "Reposition never arms or dispatches Back");
        check(!g.longPress(), "Ended gesture cannot receive delayed long press");
        near(BackNavPolicy.pullWidth(0), 0, .001f, "No pull at rest");
        near(BackNavPolicy.pullWidth(-100), 0, .001f, "No outward visual pull");
        near(BackNavPolicy.pullWidth(48), 72f * (float) Math.tanh(.4), .001f, "Exact tanh damping");
        check(BackNavPolicy.pullWidth(10000) <= 72, "Visual pull capped at 72dp");
    }

    private static void geometry() {
        BackNavPolicy.Bounds b = new BackNavPolicy.Bounds(1280, 720, 72, 72, 35, 40, 35, 56);
        near(b.minX, 35, .001f, "App safe side padding retained");
        near(b.maxX, 1173, .001f, "Right safe touch-box bound");
        near(b.minY, 40, .001f, "App safe top padding retained");
        near(b.maxY, 592, .001f, "Spec bottom minimum retained");
        for (int position : new int[]{0, 1000, 5200, 9999, 10000}) {
            float restored = BackNavPolicy.restore(position, b.minY, b.maxY);
            check(BackNavPolicy.normalize(restored, b.minY, b.maxY) == position, "Normalized round trip " + position);
            BackNavPolicy.Bounds rotated = new BackNavPolicy.Bounds(720, 1280, 80, 80, 35, 40, 35, 56);
            float afterRotation = BackNavPolicy.restore(position, rotated.minY, rotated.maxY);
            check(afterRotation >= rotated.minY && afterRotation <= rotated.maxY, "Rotation/large size clamp");
        }
        near(BackNavPolicy.restore(-200, b.minY, b.maxY), b.minY, .001f, "Corrupt lower coordinate clamped");
        near(BackNavPolicy.restore(20000, b.minY, b.maxY), b.maxY, .001f, "Corrupt upper coordinate clamped");
        check(BackNavPolicy.normalize(5, 10, 10) == 0, "Zero-sized safe interval");
        BackNavPolicy.Bounds small = new BackNavPolicy.Bounds(100, 100, 80, 80, 35, 40, 35, 56);
        check(small.minX >= 0 && small.maxX + 80 <= 100.001f, "Narrow split-screen horizontal bounds");
        check(small.minY >= 0 && small.maxY + 80 <= 100.001f, "Short split-screen vertical bounds");
        near(BackNavPolicy.clamp(Float.NaN, 1, 5), 1, .001f, "Nonfinite input rejected");
    }

    private static void floating() {
        check(BackNavPolicy.ballTap(7.99f, 199, false, false), "Quick tap inside 8dp slop");
        check(!BackNavPolicy.ballTap(8, 100, false, false), "8dp is a drag, not a tap");
        check(!BackNavPolicy.ballTap(0, 200, false, false), "Long hold is not a quick tap");
        check(!BackNavPolicy.ballTap(0, 100, true, false), "Returning drag to origin does not click");
        check(!BackNavPolicy.ballTap(0, 100, false, true), "Floating CANCEL does not click");
        check(BackNavPolicy.dockLeft(200, 1280, 0), "Nearest left dock");
        check(!BackNavPolicy.dockLeft(900, 1280, 0), "Nearest right dock");
        check(!BackNavPolicy.dockLeft(200, 1280, 700), "Right fling overrides midpoint");
        check(BackNavPolicy.dockLeft(900, 1280, -700), "Left fling overrides midpoint");
        near(BackNavPolicy.idleOffset(56, true, 1), -19.6f, .001f, "35% of VISUAL width, not hit box");
        near(BackNavPolicy.idleOffset(64, false, 1), 22.4f, .001f, "Large idle offset mirrors right");
        float dock = 35;
        for (int i = 0; i < 500; i++) {
            float idle = dock + BackNavPolicy.idleOffset(56, true, 1);
            float awake = dock + BackNavPolicy.idleOffset(56, true, 0);
            near(awake - idle, 19.6f, .001f, "Idle/wake never accumulates drift");
        }
        near(BackNavPolicy.springPosition(500, 35, 700, 0), 500, .001f, "Spring starts without teleport");
        float epsilon = .0001f;
        float velocity = (BackNavPolicy.springPosition(500, 35, 700, epsilon) - 500) / epsilon;
        near(velocity, 700, 15, "Spring retains release velocity");
        near(BackNavPolicy.springPosition(500, 35, 700, .8f), 35, .02f, "0.75/400 spring settles");
    }
}
