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

        // ---------- Catalogue-backed update routes ----------
        UpdateVersion.Channel[] direct = UpdateVersion.channels("736368217", "tvboxos-car-2880", "");
        eq(direct[0].prefix, "", "source list retains the direct API");
        eq(direct[0].apiUrl, "https://api.github.com/repos/736368217/tvboxos-car-2880/releases/latest", "direct API shape");
        boolean hasCdn = false;
        for (UpdateVersion.Channel channel : direct) {
            if (channel.prefix.equals("https://cdn.gh-proxy.org/")) {
                hasCdn = true;
                eq(channel.apiUrl, "https://cdn.gh-proxy.org/api.github.com/repos/736368217/tvboxos-car-2880/releases/latest", "metadata route strips nested scheme");
            }
            check(!channel.prefix.contains("ghfast.top") && !channel.prefix.contains("ghproxy.net"), "retired API routes are not built-ins");
        }
        check(hasCdn, "catalogue CDN fallback present");
        UpdateVersion.Channel[] custom = UpdateVersion.channels("o", "r", "https://my-mirror.example/");
        eq(custom[0].label, "自定义", "custom route remains supported");
        check(custom[0].apiUrl.startsWith("https://my-mirror.example/https://api.github.com/"), "custom prefixes retain their semantics");
        UpdateVersion.Channel[] dedup = UpdateVersion.channels("o", "r", "https://gh-proxy.org");
        int orgCount = 0;
        for (UpdateVersion.Channel channel : dedup) if (channel.prefix.equals("https://gh-proxy.org/")) orgCount++;
        eqInt(orgCount, 1, "catalogue mirror deduplicated and unsupported Web route omitted");
        eq(UpdateVersion.applyPrefix(" https://gh-proxy.org ", "https://github.com/o/r.apk"), "https://gh-proxy.org/github.com/o/r.apk", "trim prefix and normalize slash");
        eq(UpdateVersion.applyPrefix("https://gh-proxy.org/", "https://gh-proxy.org/github.com/o/r.apk"), "https://gh-proxy.org/github.com/o/r.apk", "do not double wrap");
        eq(UpdateVersion.applyPrefix("https://gh-proxy.org.evil.example/", "https://github.com/o/r.apk"), "https://gh-proxy.org.evil.example/https://github.com/o/r.apk", "host-path rule requires an exact host");
        eq(UpdateVersion.applyPrefix("https://gh-proxy.org/", "file:///local.apk"), "file:///local.apk", "non-HTTP URL is unchanged");
        String r2 = "https://dl.736.de5.net/tvboxos-car-2880/app.apk";
        String[] r2Routes = UpdateVersion.downloadCandidates(r2, "https://gh-proxy.org/", "");
        eqInt(r2Routes.length, 1, "R2 never inherits GitHub acceleration");
        eq(r2Routes[0], r2, "R2 URL unchanged");
        eqInt(UpdateVersion.downloadCandidates("https://bad host/app.apk", "", "").length, 0, "malformed origin rejected");

        // ---------- 下载候选（查询通 ≠ 下载通，必须能换通道重试） ----------
        String rawAsset = "https://github.com/o/r/releases/download/v1/app.apk";
        String[] fromDirect = UpdateVersion.downloadCandidates(rawAsset, "", "");
        check(fromDirect.length >= 3, "direct + mirrors, got " + fromDirect.length);
        eq(fromDirect[0], rawAsset, "direct channel tries the raw url first");
        eq(fromDirect[1], "https://gh-proxy.org/github.com/o/r/releases/download/v1/app.apk", "catalogue host-path format");
        eq(fromDirect[2], "https://cdn.gh-proxy.org/github.com/o/r/releases/download/v1/app.apk", "CDN host-path format");
        eq(fromDirect[3], "https://v4.gh-proxy.org/github.com/o/r/releases/download/v1/app.apk", "IPv4 host-path format");
        eq(fromDirect[4], "https://gh-proxy.com/https://github.com/o/r/releases/download/v1/app.apk", "legacy service keeps URL format");
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

        // ---------- Web 通道：302 Location 解析 tag + 资产名反推 ----------
        // 车机上 api.github.com 与镜像 API 前缀全挂时，靠 github.com/.../releases/latest
        // 的 302 兜底。解析错了 tag 就一切全错，所以这里逐样例盯死。
        eq(UpdateVersion.tagFromRedirect(
                "https://github.com/736368217/tvboxos-car-2880/releases/tag/v3.1.0-car.7-r59"),
                "v3.1.0-car.7-r59", "absolute location parsed");
        eq(UpdateVersion.tagFromRedirect("/736368217/tvboxos-car-2880/releases/tag/v3.1.0-car.7-r60"),
                "v3.1.0-car.7-r60", "relative location parsed");
        eq(UpdateVersion.tagFromRedirect(
                "https://github.com/o/r/releases/tag/v1.2-r9?utm=x"),
                "v1.2-r9", "query string stripped");
        eq(UpdateVersion.tagFromRedirect("https://github.com/o/r"), null, "no marker -> null");
        eq(UpdateVersion.tagFromRedirect(null), null, "null location -> null");
        eq(UpdateVersion.tagFromRedirect("https://x/releases/tag/"), null, "empty tag -> null");

        String[] assetNames = UpdateVersion.assetNamesForTag("v3.1.0-car.7-r60");
        eqInt(assetNames.length, 3, "three asset candidates");
        eq(assetNames[0], "TVBox-Original-Feature-3.1.0-car.7-r60.apk", "leading v stripped for asset name");
        eq(UpdateVersion.assetNamesForTag("3.1.0-car.7-r60")[0],
                "TVBox-Original-Feature-3.1.0-car.7-r60.apk", "tag without v also works");
        eqInt(UpdateVersion.assetNamesForTag(null).length, 0, "null tag -> no candidates");
        eqInt(UpdateVersion.assetNamesForTag("").length, 0, "empty tag -> no candidates");

        // Built-in CDN API routes precede the final direct GitHub HTML fallback.
        UpdateVersion.Channel[] chans = UpdateVersion.channels("o", "r", "");
        eqInt(chans.length, 6, "four verified CDN APIs + direct API + direct Web");
        boolean apiFirst = true;
        boolean sawWeb = false;
        for (UpdateVersion.Channel channel : chans) {
            if (channel.web) {
                sawWeb = true;
                eq(channel.prefix, "", "built-ins never probe unsupported releases/latest HTML");
                check(channel.apiUrl.contains("github.com/") && !channel.apiUrl.contains("api.github.com"),
                        "Web channel targets GitHub HTML");
            } else if (sawWeb) {
                apiFirst = false;
            }
        }
        check(sawWeb, "direct Web channel retained");
        // 车机实测直连资产 21 秒失败、三个 gh 镜像 1–2 秒返回 206：
        // 镜像必须排在直连之前，否则用户会长时间停在 0%。
        UpdateVersion.Channel[] preferred = UpdateVersion.preferMirrors(chans);
        eqInt(preferred.length, chans.length, "preferMirrors keeps every channel");
        check(preferred[0].prefix.length() > 0, "mirror channel first, got " + preferred[0].prefix);
        int firstDirect = -1;
        int lastMirror = -1;
        for (int i = 0; i < preferred.length; i++) {
            if (preferred[i].prefix.length() > 0) lastMirror = i;
            else if (firstDirect < 0) firstDirect = i;
        }
        check(firstDirect > lastMirror, "direct channel only after every mirror");
        check(UpdateVersion.preferMirrors(null) == null, "null channel list tolerated");
        check(apiFirst, "web channels come after all api channels");
        eq(UpdateVersion.webLatestUrl("o", "r"),
                "https://github.com/o/r/releases/latest", "web url shape");

        System.out.println("UpdateVersionHostTest PASS (" + checks + " checks)");
    }
}
