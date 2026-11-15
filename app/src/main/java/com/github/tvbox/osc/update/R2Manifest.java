package com.github.tvbox.osc.update;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cloudflare R2 发布清单（{@code <project>/latest.json}）。
 *
 * <p>清单由 {@code tools/publish_r2.ps1} 生成，形如：</p>
 * <pre>
 * { "version": "3.1.0-car.7-r88", "versionCode": 60,
 *   "apk": "tvboxos-car-2880/TVBox-...-r88.apk",
 *   "size": 52767435, "sha256": "220f...", "notes": "2–5 行",
 *   "publishedAt": "2026-11-15T03:00:00Z" }
 * </pre>
 *
 * <p>为什么要自己解析而不是用 org.json：解析与白名单校验要能在宿主机 JVM 上做回归
 * （{@code car/tests/R2ManifestHostTest.java}），而 android.jar 的 org.json 在宿主机
 * 上一律抛 Stub 异常。这里只取固定字段。</p>
 *
 * <p>安全约束：{@code apk} 是对象 key（不是绝对地址）。展开后必须是
 * <b>https</b> 且主机名等于 {@link #ALLOWED_HOST}，否则整份清单按不可用处理 ——
 * 避免更新通道被改成任意下载源。</p>
 */
public final class R2Manifest {

    /** 唯一允许的下载主机（Cloudflare R2 分发域名）。 */
    public static final String ALLOWED_HOST = "dl.736.de5.net";

    public final String version;
    public final int versionCode;
    /** 已展开并校验过的绝对 https 地址。 */
    public final String apkUrl;
    public final long size;
    /** 大写十六进制 SHA-256；缺失或长度不对时为 null。 */
    public final String sha256;
    public final String notes;

    private R2Manifest(String version, int versionCode, String apkUrl, long size,
                       String sha256, String notes) {
        this.version = version;
        this.versionCode = versionCode;
        this.apkUrl = apkUrl;
        this.size = size;
        this.sha256 = sha256;
        this.notes = notes;
    }

    /**
     * 解析清单。
     *
     * @param base 分发根地址，形如 {@code https://dl.736.de5.net/}
     * @throws IllegalArgumentException 不是 JSON 对象、缺 version，或 apk 地址不合规
     */
    public static R2Manifest parse(String json, String base) {
        if (json == null || json.trim().length() < 2) {
            throw new IllegalArgumentException("R2 清单为空");
        }
        String body = json.trim();
        if (body.charAt(0) != '{') {
            throw new IllegalArgumentException("R2 清单不是 JSON 对象");
        }
        String version = stringField(body, "version");
        if (version == null || version.length() == 0) {
            throw new IllegalArgumentException("R2 清单缺少 version");
        }
        String apkKey = stringField(body, "apk");
        String url = resolveApk(base, apkKey);
        if (url == null) {
            throw new IllegalArgumentException("R2 清单的 apk 地址不合规：" + apkKey);
        }
        String sha = stringField(body, "sha256");
        if (sha != null) {
            sha = sha.trim().toUpperCase(Locale.ROOT);
            if (!sha.matches("[0-9A-F]{64}")) sha = null;
        }
        String notes = stringField(body, "notes");
        long size = longField(body, "size");
        int versionCode = (int) Math.min(Integer.MAX_VALUE, longField(body, "versionCode"));
        return new R2Manifest(version, versionCode, url, size, sha, notes == null ? "" : notes);
    }

    /**
     * 展开并校验 APK 地址：只接受 https + {@link #ALLOWED_HOST}。
     *
     * @return 合规的绝对地址；不合规返回 null
     */
    public static String resolveApk(String base, String apkKey) {
        if (apkKey == null) return null;
        String key = apkKey.trim();
        if (key.length() == 0) return null;
        String url;
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            // 清单里直接给了绝对地址（历史写法）：仍然必须过主机与协议校验。
            url = key;
        } else {
            String root = base == null ? "" : base.trim();
            if (root.length() == 0) return null;
            if (!root.endsWith("/")) root = root + "/";
            while (key.startsWith("/")) key = key.substring(1);
            url = root + key;
        }
        return isAllowedDownloadUrl(url) ? url : null;
    }

    /** https 且主机名等于 {@link #ALLOWED_HOST}（端口不参与判定，但必须是 https）。 */
    public static boolean isAllowedDownloadUrl(String url) {
        if (url == null) return false;
        String value = url.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://")) return false;
        String rest = value.substring("https://".length());
        int slice = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                slice = i;
                break;
            }
        }
        String authority = rest.substring(0, slice);
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            String port = authority.substring(colon + 1);
            if (!port.matches("[0-9]{1,5}")) return false;
            authority = authority.substring(0, colon);
        }
        return ALLOWED_HOST.equalsIgnoreCase(authority);
    }

    private static String stringField(String body, String key) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    private static long longField(String body, String key) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d{1,18})").matcher(body);
        if (!matcher.find()) return 0L;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                default: out.append(next); break;
            }
        }
        return out.toString();
    }
}
