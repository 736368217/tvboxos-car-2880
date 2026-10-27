import com.github.tvbox.osc.car.VolumeBoostMath;

/** Host-only checks for the app-volume → LoudnessEnhancer mapping. No Android runtime needed. */
public final class VolumeBoostMathHostTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // 100% 及以下：完全交给播放器，效果链不参与
        check(VolumeBoostMath.millibel(0f, 1f) == 0, "mute stays 0 mB");
        check(VolumeBoostMath.millibel(0.35f, 1f) == 0, "35% stays 0 mB");
        check(VolumeBoostMath.millibel(1f, 1f) == 0, "100% is 0 mB");

        // 100%..200% 单调递增，200% = +6 dB
        int at125 = VolumeBoostMath.millibel(1.25f, 1f);
        int at150 = VolumeBoostMath.millibel(1.5f, 1f);
        int at175 = VolumeBoostMath.millibel(1.75f, 1f);
        int at200 = VolumeBoostMath.millibel(2f, 1f);
        check(at125 > 0, "125% must boost");
        check(at125 < at150 && at150 < at175 && at175 < at200, "boost must be monotonic");
        check(at200 == VolumeBoostMath.MAX_BOOST_MB, "200% is +6 dB (600 mB)");
        check(at150 >= 275 && at150 <= 325, "150% ≈ +3 dB, got " + at150);

        // 量化：相邻微小变化不会每像素都触发一次
        check(VolumeBoostMath.millibel(1.510f, 1f) == VolumeBoostMath.millibel(1.500f, 1f),
                "sub-quantum change is filtered");

        // 越界输入被夹紧
        check(VolumeBoostMath.millibel(5f, 1f) == VolumeBoostMath.MAX_BOOST_MB, "clamped at 200%");
        check(VolumeBoostMath.millibel(-2f, 1f) == 0, "negative clamps to 0 mB");

        // 静音 / 闪避：boost 一起收敛，不能静音了还放大
        check(VolumeBoostMath.millibel(2f, 0f) == 0, "muted kills boost");
        check(VolumeBoostMath.millibel(2f, 0.5f) == 0, "ducked to 100% kills boost");
        check(VolumeBoostMath.millibel(2f, 1f) == VolumeBoostMath.MAX_BOOST_MB, "restore returns boost");

        // 播放器只吃 0..1
        check(VolumeBoostMath.playerVolume(2f) == 1f, "player volume capped at 1.0");
        check(VolumeBoostMath.playerVolume(0.4f) == 0.4f, "player volume passes through below 1");
        check(VolumeBoostMath.playerVolume(-1f) == 0f, "player volume floor is 0");

        System.out.println("VolumeBoostMathHostTest PASS");
    }
}
