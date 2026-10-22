import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import xyz.doikki.videoplayer.render.VideoEnhanceConfig;
import xyz.doikki.videoplayer.render.VideoEnhancePreset;

/** Host-only runtime settings matrix. No Android runtime or Gradle is required. */
public final class Car7RuntimeConfigTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        List<VideoEnhanceConfig> configurations = new ArrayList<>();
        for (int mode = 0; mode < 11; mode++) {
            for (boolean sr : new boolean[]{false, true}) {
                for (boolean interp : new boolean[]{false, true}) {
                    VideoEnhanceConfig original = new VideoEnhanceConfig(mode, sr, interp);
                    configurations.add(original);
                    check(original.preset == VideoEnhancePreset.forMode(mode), "Exact preset object/parameters");
                    check(original.usesGl() == (mode != 0 || sr || interp), "All-off uses Texture only");
                    check(original.sameAs(new VideoEnhanceConfig(mode, sr, interp)), "Equivalent snapshot");
                    check(!original.sameAs(null), "Null is not a configuration");
                    check(original.forHdr(false) == original, "SDR keeps the snapshot");
                    VideoEnhanceConfig hdr = original.forHdr(true);
                    check(hdr.preset.id == 0, "Every HDR preset bypasses grading");
                    check(hdr.superResolution == sr && hdr.interpolation == interp, "HDR preserves independent switches");
                    check(hdr.usesGl() == (sr || interp), "HDR grade alone cannot force GL");
                    check(original.preset.id == mode, "HDR bypass does not rewrite the requested preset");
                    check(original.withMode(10).sameAs(new VideoEnhanceConfig(10, sr, interp)), "Mode keeps both switches");
                    check(original.withSuperResolution(!sr).sameAs(new VideoEnhanceConfig(mode, !sr, interp)), "SR keeps mode/interpolation");
                    check(original.withInterpolation(!interp).sameAs(new VideoEnhanceConfig(mode, sr, !interp)), "Interpolation keeps mode/SR");
                }
            }
        }
        for (VideoEnhanceConfig before : configurations) {
            for (VideoEnhanceConfig after : configurations) {
                boolean equal = before.preset.id == after.preset.id
                        && before.superResolution == after.superResolution && before.interpolation == after.interpolation;
                check(before.sameAs(after) == equal, "Frame-boundary change detection includes every setting");
            }
        }
        for (int invalid : new int[]{-1, 11, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            check(!new VideoEnhanceConfig(invalid, false, false).usesGl(), "Invalid mode normalizes to original");
            check(new VideoEnhanceConfig(invalid, true, false).usesGl(), "Invalid grade cannot disable SR");
        }
        for (Field field : VideoEnhanceConfig.class.getDeclaredFields()) {
            check(Modifier.isFinal(field.getModifiers()), "Published configuration fields are final");
        }
        System.out.println("PASS: 44 runtime configurations, 88 SDR/HDR cases, 1936 transition comparisons, independent switches and immutable publication");
    }
}
