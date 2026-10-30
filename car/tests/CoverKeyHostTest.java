import com.github.tvbox.osc.util.CoverKey;

/**
 * 宿主机检查：封面缓存的纯逻辑（键计算 / 归一化 / 候选顺序 / 文件名安全）。
 *
 * <pre>
 * javac -encoding UTF-8 -d out app/src/main/java/com/github/tvbox/osc/util/CoverKey.java car/tests/CoverKeyHostTest.java
 * java -cp out CoverKeyHostTest
 * </pre>
 */
public final class CoverKeyHostTest {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void eq(Object actual, Object expected, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    public static void main(String[] args) {
        String pic = "https://p.lztutu.com/upload/vod/20261022-1/412f6b55014314336d1a50bdcbd3a49d.webp";

        // ---------- 归一化：内联头信息不能影响缓存键 ----------
        eq(CoverKey.normalize(pic), pic, "plain url unchanged");
        eq(CoverKey.normalize("  " + pic + "  "), pic, "surrounding spaces trimmed");
        eq(CoverKey.normalize(pic + "@Referer=https://x/"), pic, "referer suffix dropped");
        eq(CoverKey.normalize(pic + "@User-Agent=abc@Cookie=z"), pic, "ua/cookie suffix dropped");
        eq(CoverKey.normalize(null), "", "null -> empty");
        eq(CoverKey.normalize(""), "", "empty -> empty");

        // ---------- 归一化：URL 自带 @ 的图床不能被误伤 ----------
        // 图床地址本身带 @（尺寸后缀/签名）时，旧实现见 @ 就砍会让不同图片算出同一个键、
        // 互相覆盖成同一张封面。新行为：只截 @Headers= / @Cookie= / @User-Agent= / @Referer= 这些已知内联键。
        String atUrl = "https://img.example.com/pic@200w.jpg";
        eq(CoverKey.normalize(atUrl), atUrl, "decorated url with @ is kept intact");
        eq(CoverKey.normalize(atUrl + "@Referer=https://x/"), atUrl, "@-url still strips known inline keys");
        check(!CoverKey.key("https://h/a@1.jpg").equals(CoverKey.key("https://h/a@2.jpg")),
                "different @-decorated urls must get different keys");
        eq(CoverKey.key("https://h/a@200w.jpg@Headers={\"ua\":\"x\"}"), CoverKey.key("https://h/a@200w.jpg"),
                "inline headers after an @-url do not change the key");

        // ---------- 缓存键：稳定、可复现、不含路径分隔符 ----------
        String key = CoverKey.key(pic);
        check(key != null, "key computed");
        check(key.length() == 32, "key is 32 hex chars, got " + key);
        check(key.matches("[0-9a-f]{32}"), "key is lowercase hex, got " + key);
        eq(CoverKey.key(pic), key, "key is deterministic");
        eq(CoverKey.key("  " + pic + "  "), key, "same key after trimming");
        // 带 Headers/Referer 的同一张图必须命中同一个键（站点会频繁刷新这些后缀）
        eq(CoverKey.key(pic + "@Headers={\"a\":\"b\"}"), key, "same key despite inline headers");
        check(!key.equals(CoverKey.key(pic + "-other")), "different url -> different key");
        eq(CoverKey.key(null), null, "null url -> null key");
        eq(CoverKey.key("   "), null, "blank url -> null key");

        // ---------- 文件名 ----------
        eq(CoverKey.fileName(pic), key + ".img", "file name = key + .img");
        eq(CoverKey.fileName(null), null, "null url -> null file name");
        check(CoverKey.isValidFileName(key + ".img"), "own file name accepted");
        check(!CoverKey.isValidFileName("../../etc/passwd"), "path traversal rejected");
        check(!CoverKey.isValidFileName(key + ".img.exe"), "wrong suffix rejected");
        check(!CoverKey.isValidFileName(key.toUpperCase() + ".img"), "uppercase rejected");
        check(!CoverKey.isValidFileName(key), "missing suffix rejected");
        check(!CoverKey.isValidFileName(null), "null rejected");
        check(!CoverKey.isValidFileName(""), "empty rejected");

        // ---------- 候选顺序：本地优先，避免站点挂掉就变色块 ----------
        String local = "/data/user/0/app/files/cover/abc.img";
        String remote = pic;
        String[] both = CoverKey.candidates(local, remote);
        eqInt(both.length, 2, "local + remote");
        eq(both[0], local, "local file comes first");
        eq(both[1], remote, "network is the fallback");

        String[] onlyLocal = CoverKey.candidates(local, "");
        eqInt(onlyLocal.length, 1, "local only");
        eq(onlyLocal[0], local, "local only keeps local");

        String[] onlyRemote = CoverKey.candidates(null, remote);
        eqInt(onlyRemote.length, 1, "remote only");
        eq(onlyRemote[0], remote, "remote only keeps remote");

        eqInt(CoverKey.candidates(null, null).length, 0, "nothing -> no candidates");
        eqInt(CoverKey.candidates("", "  ").length, 0, "blank inputs -> no candidates");

        // ---------- 内容键：站点换图床后仍能命中 ----------
        String ck = CoverKey.contentKey("后会友期", "量子");
        check(ck != null && ck.matches("[0-9a-f]{32}"), "content key is 32 hex, got " + ck);
        eq(CoverKey.contentKey("后会友期", "量子"), ck, "content key deterministic");
        eq(CoverKey.contentKey("  后会友期  ", " 量子 "), ck, "content key trims");
        check(!ck.equals(CoverKey.contentKey("后会友期", "爱动漫")), "different source -> different key");
        check(!ck.equals(CoverKey.contentKey("别的片", "量子")), "different title -> different key");
        check(!ck.equals(CoverKey.key(pic)), "content key differs from url key");
        // 同一个 URL 换成另一个图床地址：内容键不变（这正是要修的场景）
        String moved = "https://other-cdn.example/x/y/412f6b55014314336d1a50bdcbd3a49d.webp";
        check(!CoverKey.key(pic).equals(CoverKey.key(moved)), "url key changes when the cdn changes");
        eq(CoverKey.contentKey("后会友期", "量子"), CoverKey.contentKey("后会友期", "量子"),
                "content key survives a cdn change");
        eq(CoverKey.contentKey("", "量子"), null, "empty title -> null content key");
        eq(CoverKey.contentKey(null, null), null, "null title -> null content key");
        eq(CoverKey.contentFileName("后会友期", "量子"), ck + ".img", "content file name");
        eq(CoverKey.contentFileName("", "量子"), null, "empty title -> null file name");

        System.out.println("CoverKeyHostTest PASS (" + checks + " checks)");
    }

    private static void eqInt(int actual, int expected, String message) {
        checks++;
        if (actual != expected) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}