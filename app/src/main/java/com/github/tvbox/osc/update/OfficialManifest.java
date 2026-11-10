package com.github.tvbox.osc.update;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 官方发布通道的版本清单（{@code <软件>/latest.json}），纯 Java、可在宿主机测试。
 *
 * <p>清单由发布脚本生成，形如：</p>
 * <pre>
 * { "software": "tvbox-car", "version": "3.1.0-car.7-r65",
 *   "files": [ { "name": "TVBox.apk", "size": 123, "sha256": "...",
 *                "url": "/tvbox-car/3.1.0-car.7-r65/TVBox.apk" } ],
 *   "notes": "可选", "releasedAt": "2026-11-04T12:33:30+0800" }
 * </pre>
 *
 * <p>为什么要自己解析而不是用 org.json：解析逻辑要能在宿主机 JVM 上跑回归测试
 * （{@code car/tests/OfficialManifestHostTest.java}），而 android.jar 里的
 * org.json 在宿主机上是抛 Stub 异常的。这里只取固定几个字段，扫描比引入依赖更直接。</p>
 */
public final class OfficialManifest {

    public final String software;
    public final String version;
    public final String apkName;
    /** 绝对下载地址；清单里给相对路径时按传入的 base 展开。 */
    public final String apkUrl;
    /** 上传方给出的 SHA-256；没有则为 null。 */
    public final String sha256;
    public final long apkSize;
    /** 可选更新说明；没有则为空串。 */
    public final String notes;

    private OfficialManifest(String software, String version, String apkName, String apkUrl,
                             String sha256, long apkSize, String notes) {
        this.software = software;
        this.version = version;
        this.apkName = apkName;
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.apkSize = apkSize;
        this.notes = notes;
    }

    /**
     * 解析清单。{@code base} 形如 {@code https://dl.z736.vip/}，用于展开相对地址。
     *
     * @throws IllegalArgumentException 清单不是 JSON 对象、缺 version，或给不出可用下载地址
     */
    public static OfficialManifest parse(String json, String base) {
        if (json == null) throw new IllegalArgumentException("官方清单为空");
        String body = json.trim();
        if (body.length() < 2 || body.charAt(0) != '{') {
            throw new IllegalArgumentException("官方清单不是 JSON 对象");
        }
        int filesAt = indexOfKey(body, "files");
        String head = filesAt >= 0 ? body.substring(0, filesAt) : body;
        String version = stringField(head, "version");
        if (version == null || version.length() == 0) {
            throw new IllegalArgumentException("官方清单缺少 version");
        }
        String software = stringField(body, "software");
        // r75：发布脚本把 notes 放在 files 数组之后。旧实现只截 files 之前的 head 去找，
        // 官方优先通道永远返回空说明，更新弹窗显示「该版本没有填写更新说明」。
        // 顶层标量字段要在整个对象里找，files 的嵌套对象仍只从 firstObject 读。
        String notes = stringField(body, "notes");

        String apkName = null;
        String apkUrl = null;
        String sha256 = null;
        long apkSize = 0L;
        String firstObject = filesAt >= 0 ? firstArrayObject(body, filesAt) : null;
        if (firstObject != null) {
            apkName = stringField(firstObject, "name");
            apkUrl = stringField(firstObject, "url");
            sha256 = stringField(firstObject, "sha256");
            apkSize = longField(firstObject, "size");
        }
        // 兼容「files 里没有 url」的写法：退回顶层 apk/url 字段。
        if (apkUrl == null) apkUrl = stringField(head, "url");
        if (apkName == null) apkName = stringField(head, "name");
        apkUrl = resolve(base, apkUrl);
        if (sha256 != null && sha256.length() != 64) sha256 = null;
        return new OfficialManifest(software, version, apkName, apkUrl, sha256, apkSize,
                notes == null ? "" : notes);
    }

    /** 相对路径按 base 展开；已是绝对地址则原样返回。 */
    public static String resolve(String base, String url) {
        if (url == null) return null;
        String value = url.trim();
        if (value.length() == 0) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return value;
        String root = base == null ? "" : base.trim();
        if (root.length() == 0) return value;
        if (!root.endsWith("/")) root = root + "/";
        while (value.startsWith("/")) value = value.substring(1);
        return root + value;
    }

    /** {@code key} 在顶层出现的下标（找不到为 -1），用于把头部字段和 files 数组分开。 */
    private static int indexOfKey(String body, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:").matcher(body);
        return matcher.find() ? matcher.start() : -1;
    }

    /** files 数组里的第一个对象（含花括号）。 */
    private static String firstArrayObject(String body, int filesAt) {
        int open = body.indexOf('{', filesAt);
        if (open < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = open; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return body.substring(open, i + 1);
            }
        }
        return null;
    }

    private static String stringField(String body, String key) {
        if (body == null) return null;
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    private static long longField(String body, String key) {
        if (body == null) return 0L;
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d{1,18})").matcher(body);
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
