import com.github.tvbox.osc.cache.CoverFormat;

/** r74：封面文件头分类 / BitmapFactory 风险判断的宿主机回归测试。 */
public final class CoverFormatHostTest {

    private static int checks;

    public static void main(String[] args) {
        jpg();
        png();
        gif();
        webpSimple();
        webpLossless();
        webpExtended();
        avif();
        unknown();
        shortInput();
        System.out.println("CoverFormatHostTest PASS checks=" + checks);
    }

    private static void jpg() {
        check(CoverFormat.Kind.JPEG, bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1));
        checkRisky(false, bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1));
    }

    private static void png() {
        check(CoverFormat.Kind.PNG, bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13));
        checkRisky(false, bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13));
    }

    private static void gif() {
        check(CoverFormat.Kind.GIF, ascii("GIF89a......"));
        check(CoverFormat.Kind.GIF, ascii("GIF87a......"));
        checkRisky(false, ascii("GIF89a......"));
    }

    private static void webpSimple() {
        byte[] head = webp("VP8 ");
        check(CoverFormat.Kind.WEBP_SIMPLE, head);
        checkRisky(false, head);
    }

    private static void webpLossless() {
        byte[] head = webp("VP8L");
        check(CoverFormat.Kind.WEBP_LOSSLESS, head);
        checkRisky(false, head);
    }

    private static void webpExtended() {
        byte[] head = webp("VP8X");
        check(CoverFormat.Kind.WEBP_EXTENDED, head);
        checkRisky(true, head);
        check(CoverFormat.Kind.WEBP_EXTENDED, webp("ANIM"));
        checkRisky(true, webp("ANIM"));
    }

    private static void avif() {
        byte[] head = ascii("....ftypavif0000avif............");
        check(CoverFormat.Kind.AVIF, head);
        checkRisky(true, head);
        byte[] still = ascii("....ftypmif10000mif1............");
        check(CoverFormat.Kind.UNKNOWN, still);
        checkRisky(true, still);
    }

    private static void unknown() {
        byte[] html = ascii("<!DOCTYPE html><html><body>403</body></html>");
        check(CoverFormat.Kind.UNKNOWN, html);
        checkRisky(true, html);
        byte[] json = ascii("{\"error\":\"forbidden\",\"code\":403}");
        check(CoverFormat.Kind.UNKNOWN, json);
        checkRisky(true, json);
    }

    private static void shortInput() {
        check(CoverFormat.Kind.TOO_SHORT, null);
        checkRisky(true, null);
        check(CoverFormat.Kind.TOO_SHORT, bytes(0xFF, 0xD8, 0xFF));
        checkRisky(true, bytes(0xFF, 0xD8, 0xFF));
    }

    private static byte[] webp(String fourcc) {
        byte[] out = new byte[32];
        byte[] prefix = ascii("RIFF....WEBP");
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        byte[] chunk = ascii(fourcc);
        System.arraycopy(chunk, 0, out, 12, chunk.length);
        return out;
    }

    private static void check(CoverFormat.Kind expected, byte[] head) {
        CoverFormat.Kind actual = CoverFormat.of(head);
        checks++;
        if (actual != expected) {
            throw new AssertionError("expected " + expected + " got " + actual);
        }
    }

    private static void checkRisky(boolean expected, byte[] head) {
        boolean actual = CoverFormat.bitmapFactoryRisky(head);
        checks++;
        if (actual != expected) {
            throw new AssertionError("expected risky=" + expected + " got " + actual);
        }
    }

    private static byte[] ascii(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private CoverFormatHostTest() {
    }
}
