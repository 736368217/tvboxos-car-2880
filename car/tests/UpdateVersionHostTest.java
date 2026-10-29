import com.github.tvbox.osc.update.UpdateVersion;

/**
 * 宿主机检查（不需要 Android 运行时、不需要 JUnit）：
 * {@code javac -encoding UTF-8 -cp <app classes> -d out car/tests/UpdateVersionHostTest.java}
 * {@code java -cp out UpdateVersionHostTest}
 *
 * <p>重点盯住本项目特有的坑：迭代号 {@code rNN} 与 versionCode **不同步**
 * （r44=17、r48=21），只按点分段比较会把 r44 → r48 判成「没有新版本」。</p>
 */
public final class UpdateVersionHostTest {

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

    private static void eqInt(int actual, int expected, String message) {
        checks++;
        if (actual != expected) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    public static void main(String[] args) {
        // ---------- 解析 ----------
        UpdateVersion local = UpdateVersion.parse("3.1.0-car.7-r48");
        eqInt(local.iteration, 48, "iteration parsed from -car.7-r48");
        eqInt(local.dotted.length, 3, "three dotted segments");
        eqInt(local.dotted[0], 3, "major");
        eqInt(local.dotted[1], 1, "minor");
        eqInt(local.dotted[2], 0, "patch");

        eqInt(UpdateVersion.parse("v3.1.0-car.7-r49").iteration, 49, "leading v tolerated");
        eqInt(UpdateVersion.parse("3.1.0-CAR.7-R49").iteration, 49, "case insensitive");
        eqInt(UpdateVersion.parse("3.1.0-car7-r7").iteration, 7, "separator tolerant");
        eqInt(UpdateVersion.parse("3.1.0-car.7-r100").iteration, 100, "three-digit iteration");
        eqInt(UpdateVersion.parse("1.2.3-r7").iteration, 7, "loose -rNN fallback");
        eqInt(UpdateVersion.parse("3.1.0").iteration, -1, "no iteration when absent");
        eqInt(UpdateVersion.parse("").dotted.length, 0, "empty has no dotted");
        eqInt(UpdateVersion.parse(null).iteration, -1, "null is safe");
        eqInt(UpdateVersion.parse("garbage").dotted.length, 0, "garbage has no dotted");

        // ---------- 迭代号优先 ----------
        check(UpdateVersion.hasUpdate("3.1.0-car.7-r48", "3.1.0-car.7-r49"),
                "r48 -> r49 is an update");
        check(!UpdateVersion.hasUpdate("3.1.0-car.7-r48", "3.1.0-car.7-r48"),
                "same tag is not an update");
        check(!UpdateVersion.hasUpdate("3.1.0-car.7-r48", "3.1.0-car.7-r47"),
                "older tag is not an update");
        // 真实场景：车上装的是 r44，仓库最新是 r48
        check(UpdateVersion.hasUpdate("3.1.0-car.7-r44", "3.1.0-car.7-r48"),
                "r44 -> r48 must be an update (the case plain dotted compare gets wrong)");
        // 迭代号优先于点分段：点分段更高但迭代号更低，应判为「无更新」
        check(!UpdateVersion.hasUpdate("3.1.0-car.7-r48", "3.2.0-car.7-r44"),
                "iteration outranks dotted segments");
        check(UpdateVersion.hasUpdate("3.1.0-car.7-r48", "3.2.0-car.7-r49"),
                "both newer still an update");

        // ---------- 点分段兜底 ----------
        check(UpdateVersion.hasUpdate("3.0.0", "3.1.0"), "dotted fallback detects update");
        check(!UpdateVersion.hasUpdate("3.1.0", "3.1.0"), "dotted equal is not an update");
        check(!UpdateVersion.hasUpdate("3.1.0", "3.0.9"), "dotted older is not an update");
        check(!UpdateVersion.hasUpdate("3.1", "3.1.0"), "trailing zero segment is equal");
        check(UpdateVersion.hasUpdate("3.1", "3.1.1"), "shorter dotted can still be older");
        check(!UpdateVersion.hasUpdate("3.1.0", "not-a-version"), "unparseable remote never claims an update");
        check(!UpdateVersion.hasUpdate("", "3.1.0"), "unparseable local never claims an update");
        check(!UpdateVersion.hasUpdate("3.1.0-car.7-r48", "3.1.0-car.7-r48-hotfix"),
                "same iteration with a suffix is the same release, not an update");

        // ---------- 资产选择 ----------
        int picked = UpdateVersion.pickApk(new String[]{
                "TVBox-Original-Feature-3.1.0-car.7-r49.apk",
                "TVBox-Original-Feature-3.1.0-car.7-r49.apk.sha256",
                "classes.dex"}, new long[]{52709480L, 110L, 100L});
        eqInt(picked, 0, "picks the release apk and ignores sha256/dex");
        eqInt(UpdateVersion.pickApk(new String[]{"notes.md"}, new long[]{10L}), -1, "no apk -> -1");
        eqInt(UpdateVersion.pickApk(new String[0], new long[0]), -1, "empty list -> -1");
        eqInt(UpdateVersion.pickApk(new String[]{"app.apk"}, null), 0, "missing sizes are tolerated");
        // TVBox 命名优先于其它 apk，即使体积更小
        eqInt(UpdateVersion.pickApk(new String[]{"other.apk", "TVBox-Original-Feature-r49.apk"},
                new long[]{99999999L, 100L}), 1, "TVBox-named apk outranks a bigger unrelated apk");
        // debug 包垫底
        eqInt(UpdateVersion.pickApk(new String[]{"TVBox-debug.apk", "TVBox-release.apk"},
                new long[]{99999999L, 100L}), 1, "debug apk ranks last");
        // 同一优先级取最大
        eqInt(UpdateVersion.pickApk(new String[]{"a.apk", "b.apk"}, new long[]{1L, 9L}), 1,
                "largest wins within the same rank");
        // 大小写不敏感
        eqInt(UpdateVersion.pickApk(new String[]{"TVBOX-R49.APK"}, new long[]{1L}), 0,
                "upper-case .APK is recognised");

        // ---------- 通道 ----------
        UpdateVersion.Channel[] direct = UpdateVersion.channels("736368217", "tvboxos-car-2880", "");
        check(direct.length >= 3, "direct + built-in mirrors, got " + direct.length);
        eq(direct[0].prefix, "", "no user mirror -> direct first");
        check(direct[0].apiUrl.endsWith("/repos/736368217/tvboxos-car-2880/releases/latest"),
                "api url shape");
        boolean hasGhfast = false, hasGhproxy = false;
        for (UpdateVersion.Channel channel : direct) {
            if (channel.prefix.contains("ghfast.top")) hasGhfast = true;
            if (channel.prefix.contains("ghproxy.net")) hasGhproxy = true;
        }
        check(hasGhfast, "ghfast fallback present");
        check(hasGhproxy, "ghproxy fallback present");

        UpdateVersion.Channel[] custom =
                UpdateVersion.channels("o", "r", "https://my-mirror.example/");
        eq(custom[0].label, "自定义", "user mirror goes first");
        check(custom[0].apiUrl.startsWith("https://my-mirror.example/https://api.github.com/"),
                "user mirror prefixes the api url, got " + custom[0].apiUrl);
        // 用户把镜像设成内置值时不能重复添加
        UpdateVersion.Channel[] dedup = UpdateVersion.channels("o", "r", "https://ghfast.top/");
        int ghfastCount = 0;
        for (UpdateVersion.Channel channel : dedup) {
            if (channel.apiUrl.contains("ghfast.top")) ghfastCount++;
        }
        eqInt(ghfastCount, 1, "duplicate mirror is not added twice");

        // ---------- 下载候选（查询通 ≠ 下载通，必须能换通道重试） ----------
        String rawAsset = "https://github.com/o/r/releases/download/v1/app.apk";
        String[] fromDirect = UpdateVersion.downloadCandidates(rawAsset, "", "");
        check(fromDirect.length >= 3, "direct + mirrors, got " + fromDirect.length);
        eq(fromDirect[0], rawAsset, "direct channel tries the raw url first");
        check(fromDirect[1].contains("ghfast.top"), "ghfast next, got " + fromDirect[1]);
        check(fromDirect[2].contains("ghproxy.net"), "ghproxy third, got " + fromDirect[2]);
        // 同一地址不重复出现
        boolean dup = false;
        for (int i = 0; i < fromDirect.length; i++) {
            for (int j = i + 1; j < fromDirect.length; j++) {
                if (fromDirect[i].equals(fromDirect[j])) dup = true;
            }
        }
        check(!dup, "candidates must be unique");
        // 查询走 ghfast 成功时，下载也先走 ghfast，再回直连，再另一个镜像
        String[] fromGhfast = UpdateVersion.downloadCandidates(rawAsset, "https://ghfast.top/", "");
        check(fromGhfast[0].startsWith("https://ghfast.top/https://github.com/"),
                "preferred mirror first, got " + fromGhfast[0]);
        eq(fromGhfast[1], rawAsset, "then the raw url");
        // 用户自定义镜像参与候选
        String[] withUser =
                UpdateVersion.downloadCandidates(rawAsset, "", "https://my-mirror.example/");
        check(withUser[1].startsWith("https://my-mirror.example/"),
                "user mirror is a candidate, got " + withUser[1]);
        // 空地址不产生候选
        eqInt(UpdateVersion.downloadCandidates(null, "", "").length, 0, "null url -> no candidates");
        eqInt(UpdateVersion.downloadCandidates("", "", "").length, 0, "empty url -> no candidates");
        // 镜像没有结尾斜杠也要能拼
        eq(custom[0].apply("https://github.com/x/y.apk"),
                "https://my-mirror.example/https://github.com/x/y.apk", "asset url follows the channel");
        eq(UpdateVersion.applyPrefix("", "https://github.com/x/y.apk"),
                "https://github.com/x/y.apk", "no prefix leaves the url alone");
        eq(UpdateVersion.applyPrefix("https://m/", null), null, "null url tolerated");

        // ---------- 说明文本 ----------
        String notes = UpdateVersion.cleanNotes(
                "## 标题\r\n<!-- 内部注释 -->\n![x](https://img.shields.io/badge/a)\n正文内容", 1000);
        check(notes.contains("正文内容"), "notes keep the body");
        check(!notes.contains("内部注释"), "html comments removed");
        check(!notes.contains("shields.io"), "badge noise removed");
        check(!notes.contains("\r"), "CRLF normalised");
        String longNotes = UpdateVersion.cleanNotes("abcdefghij", 4);
        check(longNotes.startsWith("abcd") && longNotes.endsWith("…"), "long notes truncated, got " + longNotes);
        eq(UpdateVersion.cleanNotes(null, 10), "", "null notes -> empty");

        // Markdown 标记必须清掉：弹窗是纯 TextView，原样显示会出现「### 怎么用」「**自动**」这种噪声
        String md = UpdateVersion.cleanNotes(
                "### 怎么用\n- **自动**：进入首页约 5 秒后检查\n1. 第一项\n> 引用的一段\n"
                        + "行内 `code` 与 [链接](https://example.com) 保留文字\n"
                        + "| 校验 | 目的 |\n| :--- | :--- |\n| 包名 | 防装错 |", 2000);
        check(!md.contains("#"), "heading markers removed, got:\n" + md);
        check(!md.contains("**"), "bold markers removed, got:\n" + md);
        check(!md.contains("`"), "inline code markers removed, got:\n" + md);
        check(!md.contains("]("), "link syntax removed, got:\n" + md);
        check(!md.contains("> 引用"), "blockquote marker removed, got:\n" + md);
        check(md.contains("▪ 怎么用"), "heading keeps a bullet prefix, got:\n" + md);
        check(md.contains("· 自动"), "list marker becomes a dot, got:\n" + md);
        check(md.contains("包名 · 防装错"), "table row becomes dot separated, got:\n" + md);
        check(!md.contains("---"), "table separator row dropped, got:\n" + md);
        check(md.contains("链接"), "link text kept, got:\n" + md);
        check(!md.contains("example.com"), "link target dropped, got:\n" + md);

        System.out.println("UpdateVersionHostTest PASS (" + checks + " checks)");
    }
}
