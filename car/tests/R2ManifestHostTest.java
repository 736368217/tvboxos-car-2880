import java.util.ArrayList;
import java.util.List;

import com.github.tvbox.osc.update.R2Manifest;

/**
 * R2 发布清单解析与下载白名单的宿主机回归。
 *
 * <p>重点不是「能解析」，而是「不该被接受的一定被拒绝」：清单里的 apk 是对象 key，
 * 展开后必须是 https 且主机名为 dl.736.de5.net，否则更新通道就能被改成任意下载源。</p>
 */
public final class R2ManifestHostTest {

    private static final String BASE = "https://dl.736.de5.net/";
    private static int checks = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        // 1) 正常清单（publish_r2.ps1 的真实字段）
        String json = "{\n"
                + "  \"version\": \"3.1.0-car.7-r88\",\n"
                + "  \"versionCode\": 60,\n"
                + "  \"apk\": \"tvboxos-car-2880/TVBox-Original-Feature-3.1.0-car.7-r88.apk\",\n"
                + "  \"size\": 52767435,\n"
                + "  \"sha256\": \"220f812a9244c471f1e22a2323f4aa6d0236e1345de87feeb0cb47d86cac4757\",\n"
                + "  \"publishedAt\": \"2026-11-15T03:00:00Z\",\n"
                + "  \"notes\": \"第一行\\n第二行\"\n"
                + "}";
        R2Manifest manifest = R2Manifest.parse(json, BASE);
        eq("3.1.0-car.7-r88", manifest.version, "version");
        eqInt(60, manifest.versionCode, "versionCode");
        eq("https://dl.736.de5.net/tvboxos-car-2880/TVBox-Original-Feature-3.1.0-car.7-r88.apk",
                manifest.apkUrl, "apk url composed from key");
        eqInt(52767435L, manifest.size, "size");
        eq("220F812A9244C471F1E22A2323F4AA6D0236E1345DE87FEEB0CB47D86CAC4757",
                manifest.sha256, "sha256 upper-cased");
        eq("第一行\n第二行", manifest.notes, "notes unescaped");

        // 2) sha 长度不对 → 视为没有（不允许拿错 sha 去校验/展示）
        R2Manifest noSha = R2Manifest.parse(
                "{\"version\":\"1.0\",\"apk\":\"p/a.apk\",\"sha256\":\"abc\"}", BASE);
        check(noSha.sha256 == null, "short sha256 is dropped");

        // 3) 缺 version → 拒绝
        checkThrows("{\"apk\":\"p/a.apk\"}", "missing version rejected");

        // 4) 不是对象 → 拒绝
        checkThrows("[1,2,3]", "non-object rejected");

        // 5) apk 缺省 → 拒绝
        checkThrows("{\"version\":\"1.0\"}", "missing apk rejected");

        // 6) 协议白名单：http 一律拒绝
        checkThrows("{\"version\":\"1.0\",\"apk\":\"http://dl.736.de5.net/p/a.apk\"}",
                "http apk rejected");

        // 7) 主机白名单：其它 host 拒绝（含子串伪装）
        check(R2Manifest.resolveApk(BASE, "https://evil.com/p/a.apk") == null,
                "other host rejected");
        check(R2Manifest.resolveApk(BASE, "https://dl.736.de5.net.evil.com/p/a.apk") == null,
                "suffix-lookalike host rejected");
        check(R2Manifest.resolveApk(BASE, "https://dl.736.de5.net@evil.com/p/a.apk") == null,
                "userinfo trick rejected");

        // 8) 允许：正规 https + 白名单主机（含显式端口）
        check(R2Manifest.isAllowedDownloadUrl("https://dl.736.de5.net/p/a.apk"),
                "https allowed host accepted");
        check(R2Manifest.isAllowedDownloadUrl("https://DL.736.DE5.NET/p/a.apk"),
                "host match is case-insensitive");
        check(R2Manifest.isAllowedDownloadUrl("https://dl.736.de5.net:443/p/a.apk"),
                "explicit port accepted");
        check(!R2Manifest.isAllowedDownloadUrl("http://dl.736.de5.net/p/a.apk"),
                "plain http rejected");
        check(!R2Manifest.isAllowedDownloadUrl(null), "null rejected");

        // 9) key 前导斜杠会被规整，不会拼成双斜杠
        eq("https://dl.736.de5.net/p/a.apk",
                R2Manifest.resolveApk(BASE, "/p/a.apk"), "leading slash normalized");
        eq("https://dl.736.de5.net/p/a.apk",
                R2Manifest.resolveApk("https://dl.736.de5.net", "p/a.apk"), "base without slash");

        if (failures.isEmpty()) {
            System.out.println("R2ManifestHostTest PASS (" + checks + " checks)");
        } else {
            for (String failure : failures) System.out.println("FAIL: " + failure);
            throw new AssertionError(failures.size() + " of " + checks + " checks failed");
        }
    }

    private static void checkThrows(String json, String message) {
        checks++;
        try {
            R2Manifest.parse(json, BASE);
            failures.add(message + " (expected exception)");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(message);
    }

    private static void eq(String expected, String actual, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failures.add(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void eqInt(long expected, long actual, String message) {
        checks++;
        if (expected != actual) {
            failures.add(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
