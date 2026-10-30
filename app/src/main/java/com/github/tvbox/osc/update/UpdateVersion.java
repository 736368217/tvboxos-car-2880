package com.github.tvbox.osc.update;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本号解析、比较与资产选择。纯 Java，不碰 Android，便于在宿主机上跑测试。
 *
 * <p>本项目的 versionName 形如 {@code 3.1.0-car.7-r48}：点分段 {@code 3.1.0} 是上游白盒基准，
 * {@code car.7} 是车机分支标识，{@code r48} 是我方迭代号（每次发版 +1，且**不与 versionCode 同步**：
 * r44=17、r48=21、r49=22）。所以版本高低必须**优先比迭代号**，迭代号相同再比点分段；
 * 只按点分段比会把 r44 → r48 判成「没有新版本」。</p>
 */
public final class UpdateVersion implements Comparable<UpdateVersion> {

    /** 我方迭代号：{@code -car.7-rNN}（对大小写与分隔符容忍）。 */
    private static final Pattern ITERATION =
            Pattern.compile("(?:^|[-_.])car[._-]?\\d*[._-]?r(\\d+)(?![0-9])", Pattern.CASE_INSENSITIVE);
    /** 兜底迭代号：随便什么形式的 {@code -rNN} 结尾段。 */
    private static final Pattern ITERATION_LOOSE =
            Pattern.compile("(?:^|[-_.])r(\\d+)(?![0-9])", Pattern.CASE_INSENSITIVE);
    /** 前导点分段，例如 {@code 3.1.0}。 */
    private static final Pattern DOTTED =
            Pattern.compile("^[vV]?(\\d+(?:[._]\\d+)*)");

    public final String raw;
    /** 点分段；解析不到时为空数组。 */
    public final int[] dotted;
    /** 迭代号；解析不到时为 -1。 */
    public final int iteration;

    private UpdateVersion(String raw, int[] dotted, int iteration) {
        this.raw = raw == null ? "" : raw.trim();
        this.dotted = dotted;
        this.iteration = iteration;
    }

    public static UpdateVersion parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        Matcher dottedMatcher = DOTTED.matcher(text);
        int[] dotted = new int[0];
        if (dottedMatcher.find()) {
            String[] parts = dottedMatcher.group(1).split("[._]");
            dotted = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    dotted[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException error) {
                    dotted[i] = 0;
                }
            }
        }
        int iteration = -1;
        Matcher matcher = ITERATION.matcher(text);
        if (matcher.find()) {
            iteration = safeInt(matcher.group(1));
        } else {
            matcher = ITERATION_LOOSE.matcher(text);
            if (matcher.find()) iteration = safeInt(matcher.group(1));
        }
        return new UpdateVersion(text, dotted, iteration);
    }

    private static int safeInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    /** 本版本是否比 other 新。 */
    public boolean isNewerThan(UpdateVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(UpdateVersion other) {
        if (other == null) return 1;
        if (iteration >= 0 && other.iteration >= 0 && iteration != other.iteration) {
            return iteration - other.iteration;
        }
        return compareDotted(dotted, other.dotted);
    }

    private static int compareDotted(int[] a, int[] b) {
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) return left - right;
        }
        return 0;
    }

    /** 返回值 &gt; 0 表示 {@code a} 比 {@code b} 新。 */
    public static int compareVersions(String a, String b) {
        return parse(a).compareTo(parse(b));
    }

    /** 远端版本相对本地是否有更新。任一版本号解析不出有效数字时一律判定「无更新」。 */
    public static boolean hasUpdate(String localVersionName, String remoteVersionName) {
        UpdateVersion local = parse(localVersionName);
        UpdateVersion remote = parse(remoteVersionName);
        if (remote.dotted.length == 0 && remote.iteration < 0) return false;
        if (local.dotted.length == 0 && local.iteration < 0) return false;
        return remote.isNewerThan(local);
    }

    /**
     * 从 release 资产里挑安装包。
     *
     * <p>规则：只认 {@code .apk} 结尾；优先名字里带 {@code TVBox} 的（排除 {@code debug}）；
     * 都不带就退回任意 {@code .apk}；同优先级取体积最大的（避免选到空壳/占位文件）。</p>
     */
    public static int pickApk(String[] names, long[] sizes) {
        if (names == null || names.length == 0) return -1;
        int best = -1;
        int bestRank = -1;
        long bestSize = -1;
        for (int i = 0; i < names.length; i++) {
            String name = names[i] == null ? "" : names[i].trim();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".apk")) continue;
            boolean isDebug = lower.contains("debug");
            boolean isTvBox = lower.contains("tvbox");
            int rank;
            if (isTvBox && !isDebug) rank = 2;
            else if (!isDebug) rank = 1;
            else rank = 0;
            long size = sizes != null && i < sizes.length ? sizes[i] : 0L;
            if (rank > bestRank || (rank == bestRank && size > bestSize)) {
                best = i;
                bestRank = rank;
                bestSize = size;
            }
        }
        return best;
    }

    /** 一个可达的更新检查通道：{@code prefix} 为空表示直连。 */
    /** 一个可达的更新检查通道：{@code prefix} 为空表示直连。 */
    public static final class Channel {
        public final String prefix;
        public final String apiUrl;
        public final String label;
        /**
         * true = Web 通道：地址是 {@code github.com/{owner}/{repo}/releases/latest} 页面，
         * 靠 302 Location 解析 tag，不查 GitHub API。很多 gh-proxy 镜像只代理
         * github.com 域名、不支持 api.github.com 前缀 —— API 全挂时它是唯一活路。
         */
        public final boolean web;

        Channel(String prefix, String apiUrl, String label) {
            this(prefix, apiUrl, label, false);
        }

        Channel(String prefix, String apiUrl, String label, boolean web) {
            this.prefix = prefix;
            this.apiUrl = apiUrl;
            this.label = label;
            this.web = web;
        }

        /** 把同一通道的前缀套到资产下载地址上，保证下载与查询走同一条路。 */
        public String apply(String url) {
            return applyPrefix(prefix, url);
        }
    }
    /** 内置镜像前缀，按经验顺序：用户自定义 → 直连 → ghfast → gh-proxy → ghproxy。 */
    public static final String[] BUILT_IN_MIRRORS = {"https://ghfast.top/", "https://gh-proxy.com/", "https://ghproxy.net/"};

    // 下载候选地址：把同一个资产 URL 按通道前缀展开，preferredPrefix 排最前。
    //
    // 为什么需要：查询 API 能通不代表下载能通。实测 api.github.com 正常返回，
    // 但 github.com/.../releases/download/... 会超时 —— 两者是被单独处理的域名。
    // 所以下载失败要能自动换镜像重试，而不是直接报错。
    public static String[] downloadCandidates(String rawUrl, String preferredPrefix, String userMirror) {
        if (rawUrl == null || rawUrl.length() == 0) return new String[0];
        List<String> ordered = new ArrayList<>();
        String preferred = preferredPrefix == null ? "" : preferredPrefix;
        addCandidate(ordered, applyPrefix(preferred, rawUrl));
        if (preferred.length() > 0) addCandidate(ordered, rawUrl);
        String mirror = userMirror == null ? "" : userMirror.trim();
        if (mirror.length() > 0) {
            if (!mirror.endsWith("/")) mirror = mirror + "/";
            if (!mirror.equals(preferred)) addCandidate(ordered, applyPrefix(mirror, rawUrl));
        }
        for (String prefix : BUILT_IN_MIRRORS) {
            if (prefix.equals(preferred)) continue;
            addCandidate(ordered, applyPrefix(prefix, rawUrl));
        }
        return ordered.toArray(new String[0]);
    }

    private static void addCandidate(List<String> ordered, String url) {
        if (url == null || url.length() == 0) return;
        if (!ordered.contains(url)) ordered.add(url);
    }

    public static Channel[] channels(String owner, String repo, String userMirror) {
        String api = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
        List<Channel> out = new ArrayList<>();
        String mirror = userMirror == null ? "" : userMirror.trim();
        if (mirror.length() > 0) {
            if (!mirror.endsWith("/")) mirror = mirror + "/";
            out.add(new Channel(mirror, mirror + api, "自定义"));
        }
        out.add(new Channel("", api, "直连"));
        for (String prefix : BUILT_IN_MIRRORS) {
            String url = prefix + api;
            boolean duplicate = false;
            for (Channel existing : out) {
                if (existing.apiUrl.equals(url)) duplicate = true;
            }
            if (!duplicate) out.add(new Channel(prefix, url, prefix.replace("https://", "").replace("/", "")));
        }
        // Web 通道排在 API 通道之后：API 能通时优先用（信息全：notes、资产清单、size）；
        // API 全挂（镜像不支持 api.github.com 前缀、DNS 污染）时靠 302 兜底。
        // 通道顺序按前缀集合对齐：自定义 → 直连 → ghfast → gh-proxy → ghproxy。
        String web = webLatestUrl(owner, repo);
        if (mirror.length() > 0) out.add(new Channel(mirror, mirror + web, "自定义web", true));
        out.add(new Channel("", web, "直连web", true));
        for (String prefix : BUILT_IN_MIRRORS) {
            String url = prefix + web;
            boolean duplicate = false;
            for (Channel existing : out) {
                if (existing.apiUrl.equals(url)) duplicate = true;
            }
            if (!duplicate) out.add(new Channel(prefix, url, prefix.replace("https://", "").replace("/", "") + "web", true));
        }
        return out.toArray(new Channel[0]);
    }

    /** Web 通道地址：releases/latest 页面，服务端 302 到 /releases/tag/&lt;tag&gt;。 */
    public static String webLatestUrl(String owner, String repo) {
        return "https://github.com/" + owner + "/" + repo + "/releases/latest";
    }

    /**
     * 从 302 的 Location（或最终页面 URL）提取 release tag。
     *
     * <p>取 {@code /releases/tag/} 之后、下一个 {@code /} 或 {@code ?} 之前的段并做 URL 解码；
     * 解析不出返回 null。样例：
     * {@code /736368217/tvboxos-car-2880/releases/tag/v3.1.0-car.7-r59 → v3.1.0-car.7-r59}。</p>
     */
    public static String tagFromRedirect(String location) {
        if (location == null) return null;
        String marker = "/releases/tag/";
        int at = location.indexOf(marker);
        if (at < 0) return null;
        String tag = location.substring(at + marker.length());
        int end = tag.length();
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        tag = tag.substring(0, end);
        if (tag.length() == 0) return null;
        try {
            tag = java.net.URLDecoder.decode(tag, "UTF-8");
        } catch (Exception ignored) {
            // tag 里出现非法转义就按原样用
        }
        return tag;
    }

    /**
     * Web 通道拿不到资产清单，按本仓库的资产命名规范反推候选文件名。
     *
     * <p>命名规范：tag = {@code v&lt;版本&gt;}，资产 = {@code TVBox-Original-Feature-&lt;版本&gt;.apk}。
     * 候选按可能性排序，下载端有「内容过小/包名校验」兜底，拼错只会 404 换下一个。</p>
     */
    public static String[] assetNamesForTag(String tag) {
        if (tag == null || tag.length() == 0) return new String[0];
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        List<String> names = new ArrayList<>();
        names.add("TVBox-Original-Feature-" + version + ".apk");
        names.add("TVBox-Original-Feature-" + tag + ".apk");
        names.add("TVBox-" + version + ".apk");
        return names.toArray(new String[0]);
    }

    public static String applyPrefix(String prefix, String url) {
        if (url == null) return null;
        if (prefix == null || prefix.length() == 0) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return prefix + url;
        return url;
    }

    /**
     * 把 Release 正文裁成弹窗能直接显示的样子。
     *
     * <p>弹窗里是纯 TextView，不解析 Markdown —— 原样贴过去用户就会看到
     * {@code ## 标题}、{@code **粗体**}、{@code | 表格 |} 这种噪声。所以这里做轻量清洗：
     * 去掉标题井号、粗体星号、行内代码反引号、引用/列表符与链接语法，
     * 丢弃 HTML 注释与徽章图片；标题行加「▪」保留层次。</p>
     */
    public static String cleanNotes(String body, int maxChars) {
        String text = body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("<!--")) continue;
            if (trimmed.startsWith("<img") || trimmed.contains("shields.io")) continue;
            if (trimmed.startsWith("|")) {
                // Markdown 表格：分隔行（| :--- | :--- |）先按原始行判定并丢弃，
                // 数据行逐格 trim 后用中点连接 —— 否则会出现「:--- · :---」和多余空格。
                if (trimmed.matches("^\\|?[\\s:|-]+\\|?$")) continue;
                String[] cells = trimmed.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|");
                StringBuilder row = new StringBuilder();
                for (String cell : cells) {
                    String value = cell.trim();
                    if (value.length() == 0) continue;
                    if (row.length() > 0) row.append(" · ");
                    row.append(value);
                }
                if (row.length() > 0) out.append(row).append('\n');
                continue;
            }
            boolean heading = trimmed.startsWith("#");
            String cleaned = trimmed
                    .replaceAll("^#{1,6}\\s*", "")
                    .replaceAll("^>\\s*", "")
                    .replaceAll("^[-*+]\\s+", "· ")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
                    .trim();
            if (cleaned.length() == 0) continue;
            if (heading) out.append("▪ ").append(cleaned).append('\n');
            else out.append(cleaned).append('\n');
        }
        String cleaned = out.toString().trim();
        if (maxChars > 0 && cleaned.length() > maxChars) {
            cleaned = cleaned.substring(0, maxChars) + "…";
        }
        return cleaned;
    }

    /** 便于日志与测试展示。 */
    public static String describe(String raw) {
        UpdateVersion version = parse(raw);
        return raw + "{dotted=" + Arrays.toString(version.dotted) + ", iteration=" + version.iteration + "}";
    }
}
