import com.github.tvbox.osc.car.VolumeBoostMath;

/** Host-only checks for the app-volume → gain mapping (r68: 300% ceiling). */
public final class VolumeBoostMathHostTest {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void eq(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 1e-6f,
                message + " expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) {
        check(VolumeBoostMath.MAX_GAIN == 3f, "r68 ceiling is 300%");
        check(VolumeBoostMath.millibel(0f, 1f) == 0, "mute stays 0 mB");
        check(VolumeBoostMath.millibel(0.35f, 1f) == 0, "35% stays 0 mB");
        check(VolumeBoostMath.millibel(1f, 1f) == 0, "100% is 0 mB");

        int at125 = VolumeBoostMath.millibel(1.25f, 1f);
        int at150 = VolumeBoostMath.millibel(1.5f, 1f);
        int at200 = VolumeBoostMath.millibel(2f, 1f);
        int at250 = VolumeBoostMath.millibel(2.5f, 1f);
        int at300 = VolumeBoostMath.millibel(3f, 1f);
        check(at125 > 0, "125% must boost");
        check(at125 < at150 && at150 < at200 && at200 < at250 && at250 < at300,
                "boost must be monotonic up to 300%");
        check(at300 == VolumeBoostMath.MAX_BOOST_MB, "300% is the top boost");
        check(at150 >= 225 && at150 <= 275, "150% ≈ +2.4 dB, got " + at150);

        check(VolumeBoostMath.millibel(1.510f, 1f) == VolumeBoostMath.millibel(1.500f, 1f),
                "sub-quantum change is filtered");
        check(VolumeBoostMath.millibel(5f, 1f) == VolumeBoostMath.MAX_BOOST_MB, "clamped at 300%");
        check(VolumeBoostMath.millibel(-2f, 1f) == 0, "negative clamps to 0 mB");

        check(VolumeBoostMath.millibel(3f, 0f) == 0, "muted kills boost");
        check(VolumeBoostMath.millibel(3f, 0.5f) > 0, "300% ducked to 150% still boosts");
        check(VolumeBoostMath.millibel(3f, 1f) == VolumeBoostMath.MAX_BOOST_MB, "restore returns boost");

        eq(VolumeBoostMath.playerVolume(3f), 1f, "player volume capped at 1.0");
        eq(VolumeBoostMath.playerVolume(0.4f), 0.4f, "player volume passes through below 1");
        eq(VolumeBoostMath.playerVolume(-1f), 0f, "player volume floor is 0");

        eq(VolumeBoostMath.pcmGain(3f, 1f), 3f, "PCM gain reaches 300%");
        eq(VolumeBoostMath.pcmGain(9f, 1f), 3f, "PCM gain clamps at 300%");
        eq(VolumeBoostMath.pcmGain(3f, 0.1f), 0.3f, "duck is a temporary factor");
        eq(VolumeBoostMath.outputVolume(3f, 1f, true), 1f, "PCM AudioTrack stays unity");
        eq(VolumeBoostMath.gestureVolume(2.95f, 1000f, 1000), 3f, "gesture clamps at 300%");
        eq(VolumeBoostMath.gestureVolume(1f, 1000f, 1000), 2f, "one full-screen gesture = +100%");
        eq(VolumeBoostMath.gestureVolume(1f, 30f, 1000), 1.05f, "5% detent");
        eq(VolumeBoostMath.clampGain(Float.NaN), 0f, "NaN clamps to floor");

        System.out.println("VolumeBoostMathHostTest PASS (" + checks + " checks)");
    }
}
