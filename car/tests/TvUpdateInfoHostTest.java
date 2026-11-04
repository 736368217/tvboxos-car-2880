import com.github.tvbox.osc.update.TvUpdateInfo;

/** 纯逻辑 host 测试：版本比较、清单解析、notes 清洗。 */
public final class TvUpdateInfoHostTest {

    private static int checks;

    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError(msg);
    }

    private static void eq(Object actual, Object expected, String msg) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        // ---------- 清单解析 ----------
        String manifest = "{\"version\":\"1.0.2-oldtv-enhance\",\"versionCode\":4,"
                + "\"notes\":\"修复订阅\",\"sha256\":\"ABCDEF\"}";
        TvUpdateInfo a = TvUpdateInfo.parseManifest(manifest,
                "https://x/app.apk", "app.apk", 12345L, "tv-v1.0.2-vc4");
        check(a != null, "manifest parsed");
        eq(a.versionName, "1.0.2-oldtv-enhance", "version");
        eq(a.versionCode, 4, "versionCode");
        eq(a.apkSha256, "ABCDEF", "sha256");
        eq(a.apkUrl, "https://x/app.apk", "apk url");
        check(TvUpdateInfo.parseManifest("not json", "u", "n", 1, "t") == null, "bad manifest -> null");

        // ---------- 版本比较：versionCode 优先 ----------
        check(a.isNewerThan(3, "1.0.1-oldtv-enhance"), "vc4 > local3");
        check(!a.isNewerThan(4, "1.0.2-oldtv-enhance"), "vc4 == local4 -> no update");
        check(!a.isNewerThan(5, "1.0.3"), "vc4 < local5 -> no update");

        // versionCode 缺省时按 rNN
        TvUpdateInfo b = new TvUpdateInfo("tv-v1.0.2-r5", "1.0.2-r5", 0, "", "a.apk", "u", 0, "");
        check(b.isNewerThan(0, "1.0.2-r4"), "r5 > r4");
        check(!b.isNewerThan(0, "1.0.2-r5"), "r5 == r5");
        check(!b.isNewerThan(0, "1.0.2-r9"), "r5 < r9");

        // 都没有迭代号时按点分段
        TvUpdateInfo c = new TvUpdateInfo("tv-v1.1.0", "1.1.0", 0, "", "a.apk", "u", 0, "");
        check(c.isNewerThan(0, "1.0.9"), "1.1.0 > 1.0.9");
        check(!c.isNewerThan(0, "1.1.0"), "1.1.0 == 1.1.0");
        check(!c.isNewerThan(0, "1.2.0"), "1.1.0 < 1.2.0");

        // ---------- Release JSON：只认 TV tag 的资产 ----------
        String release = "{\"tag_name\":\"tv-v1.0.2-vc4\",\"draft\":false,\"prerelease\":false,"
                + "\"body\":\"" + manifest.replace("\"", "\\\"") + "\","
                + "\"assets\":[{\"name\":\"TVBox-TV-oldtv-enhance-r4.apk\","
                + "\"browser_download_url\":\"https://x/r4.apk\",\"size\":16956378}]}";
        TvUpdateInfo d = TvUpdateInfo.fromReleaseJson(release);
        check(d != null, "release parsed");
        eq(d.tag, "tv-v1.0.2-vc4", "release tag");
        eq(d.apkName, "TVBox-TV-oldtv-enhance-r4.apk", "apk picked");
        eq(d.apkSize, 16956378L, "apk size");

        // 草稿/预发布 / 没有 TV APK → null
        check(TvUpdateInfo.fromReleaseJson("{\"tag_name\":\"x\",\"draft\":true,\"assets\":[]}") == null,
                "draft -> null");
        check(TvUpdateInfo.fromReleaseJson("{\"tag_name\":\"x\",\"assets\":[]}") == null,
                "no apk -> null");

        // ---------- notes 清洗 ----------
        String notes = TvUpdateInfo.cleanNotes(
                "## 修复内容\n| 项 | 值 |\n- **订阅** 修复\n[链接](https://x)\n<!-- 内部 -->", 500);
        check(notes.contains("修复内容"), "heading kept");
        check(notes.contains("订阅"), "bold stripped");
        check(!notes.contains("##"), "heading marker removed");
        check(!notes.contains("**"), "bold marker removed");
        check(!notes.contains("|"), "table removed");
        check(!notes.contains("内部"), "html comment removed");
        check(!notes.contains("https://x"), "link target removed");
        check(notes.contains("链接"), "link text kept");

        System.out.println("TvUpdateInfoHostTest PASS (" + checks + " checks)");
    }
}
