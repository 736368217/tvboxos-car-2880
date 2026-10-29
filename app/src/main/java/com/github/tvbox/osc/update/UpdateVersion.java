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
    public static final class Channel {
        public final String prefix;
        public final String apiUrl;
        public final String label;

        Channel(String prefix, String apiUrl, String label) {
            this.prefix = prefix;
            this.apiUrl = apiUrl;
            this.label = label;
        }

        /** 把同一通道的前缀套到资产下载地址上，保证下载与查询走同一条路。 */
        public String apply(String url) {
            return applyPrefix(prefix, url);
        }
    }

    /** 内置镜像前缀，按经验顺序：用户自定义 → 直连 → ghfast → ghproxy。 */
    public static final String[] BUILT_IN_MIRRORS = {"https://ghfast.top/", "https://ghproxy.net/"};

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
        return out.toArray(new Channel[0]);
    }

    public static String applyPrefix(String prefix, String url) {
        if (url == null) return null;
        if (prefix == null || prefix.length() == 0) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return prefix + url;
        return url;
    }

    /** 把 release 说明裁到弹窗能显示的长度，并去掉 Markdown 里最吵的噪声。 */
    public static String cleanNotes(String body, int maxChars) {
        String text = body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("<!--")) continue;
            if (trimmed.startsWith("<img") || trimmed.contains("shields.io")) continue;
            out.append(line).append('\n');
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
