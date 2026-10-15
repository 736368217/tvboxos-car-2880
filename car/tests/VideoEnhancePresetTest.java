import java.util.HashSet;
import java.util.Set;
import xyz.doikki.videoplayer.render.VideoEnhancePreset;

/** Host checks for persisted IDs, migration and distinct shader inputs (no Android runtime). */
public final class VideoEnhancePresetTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(VideoEnhancePreset.all().size() == 8, "Eight selectable presets");
        Set<String> curves = new HashSet<>();
        for (int i = 0; i < VideoEnhancePreset.all().size(); i++) {
            VideoEnhancePreset p = VideoEnhancePreset.all().get(i);
            check(p.id == i && VideoEnhancePreset.forMode(i) == p, "Stable ID " + i);
            check(VideoEnhancePreset.modeFromPrefValue(i) == i, "Integer round trip " + i);
            check(VideoEnhancePreset.modeFromPrefValue(Integer.toString(i)) == i, "String migration " + i);
            float[] values = {p.saturation, p.contrast, p.brightness, p.red, p.green, p.blue};
            for (float value : values) check(!Float.isNaN(value) && !Float.isInfinite(value), "Finite shader input");
            check(p.saturation >= 0 && p.contrast > 0 && p.red > 0 && p.green > 0 && p.blue > 0, "Valid transform");
            check(!p.name.isEmpty() && !p.description.isEmpty(), "Label and guidance");
            curves.add(p.saturation + "/" + p.contrast + "/" + p.brightness + "/" + p.red + "/" + p.green + "/" + p.blue);
        }
        check(curves.size() == 8, "Every preset has distinct parameters");
        VideoEnhancePreset off = VideoEnhancePreset.forMode(0), vivid = VideoEnhancePreset.forMode(1);
        check(off.saturation == 1 && off.contrast == 1 && off.brightness == 0 && off.red == 1 && off.green == 1 && off.blue == 1, "Original is identity");
        check(vivid.saturation == 1.35f && vivid.contrast == 1.12f && vivid.brightness == .03f, "Legacy 臻彩 retained");
        check(VideoEnhancePreset.modeFromPrefValue(Boolean.TRUE) == 1, "Old true -> 臻彩");
        check(VideoEnhancePreset.modeFromPrefValue(Boolean.FALSE) == 0, "Old false -> original");
        for (Object invalid : new Object[]{null, -1, 8, Integer.MAX_VALUE, "broken", "99999999999999999"}) {
            check(VideoEnhancePreset.modeFromPrefValue(invalid) == 0, "Corrupt preference -> original: " + invalid);
        }
        try { VideoEnhancePreset.all().clear(); throw new AssertionError("Mutable list"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("PASS: 8 presets, stable IDs, legacy migration, corrupt preference fallback, distinct immutable curves");
    }
}
