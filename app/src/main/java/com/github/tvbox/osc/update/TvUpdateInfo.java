package com.github.tvbox.osc.update;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * TV 版应用内更新的纯逻辑：Release 解析、版本比较、资产选择。不碰 Android，便于测试。
 *
 * <p>为什么单独一份：车机版的 update 包依赖 PlainHttp/UpdateVersion 等新模块，
 * 而 oldTv 是 2021 年的老工程（OkHttp 3.12 + AGP 4.2.2），直接搬会编译不过。</p>
 */
public final class TvUpdateInfo {

    public final String tag;
    public final String versionName;
    public final int versionCode;
    public final String notes;
    public final String apkName;
    public final String apkUrl;
    public final long apkSize;
    public final String apkSha256;
    public TvUpdateInfo(String tag, String versionName, int versionCode, String notes,
                        String apkName, String apkUrl, long apkSize, String apkSha256) {
        this.tag = tag;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.notes = notes;
        this.apkName = apkName;
        this.apkUrl = apkUrl;
        this.apkSize = apkSize;
        this.apkSha256 = apkSha256;
    }

    /** 从 GitHub Release JSON 解析，只认带 TV 专用资产的 Release。 */
    public static TvUpdateInfo fromReleaseJson(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return null;
        String tag = json.optString("tag_name", "");
        if (tag.length() == 0) return null;

        // 版本号与说明放在一个 JSON 资产 tv-update.json 里（避免解析 Markdown/标题）
        JSONArray assets = json.optJSONArray("assets");
        if (assets == null) return null;
        String manifestUrl = null;
        String apkUrl = null;
        String apkName = null;
        long apkSize = 0L;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name", "");
            String url = a.optString("browser_download_url", "");
            if (name.equals("tv-update.json")) manifestUrl = url;
            if (name.startsWith("TVBox-TV-oldtv-enhance-") && name.endsWith(".apk")) {
                if (apkUrl == null || name.compareTo(apkName) > 0) {
                    apkName = name;
                    apkUrl = url;
                    apkSize = a.optLong("size", 0L);
                }
            }
        }
        if (apkUrl == null) return null;

        TvUpdateInfo fromBody = parseManifest(json.optString("body", ""), apkUrl, apkName, apkSize, tag);
        if (fromBody != null) return fromBody;
        // 清单单独作为资产时由调用方再取；这里返回占位，manifestUrl 信息由 TvUpdate 用
        if (manifestUrl != null) {
            return new TvUpdateInfo(tag, tag, 0, "", apkName, apkUrl, apkSize, "");
        }
        return null;
    }

    /** 清单格式：{"version":"1.0.2-oldtv-enhance","versionCode":3,"notes":"...","sha256":"..."} */
    public static TvUpdateInfo parseManifest(String manifest, String apkUrl, String apkName,
                                             long apkSize, String tag) {
        try {
            JSONObject json = new JSONObject(manifest);
            String version = json.optString("version", tag);
            int code = json.optInt("versionCode", 0);
            String notes = json.optString("notes", "");
            String sha = json.optString("sha256", "");
            return new TvUpdateInfo(tag, version, code, notes, apkName, apkUrl, apkSize, sha);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 版本比较：优先 versionCode，其次 tag 里的 rNN 迭代号，最后点分段。 */
    public boolean isNewerThan(int localCode, String localTag) {
        if (versionCode > 0 && localCode > 0 && versionCode != localCode) {
            return versionCode > localCode;
        }
        int remoteIter = iteration(tag);
        int localIter = iteration(localTag);
        if (remoteIter >= 0 && localIter >= 0 && remoteIter != localIter) {
            return remoteIter > localIter;
        }
        return compareDotted(versionName, localTag) > 0;
    }

    private static int iteration(String text) {
        if (text == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:^|[-_.])r(\\d+)(?![0-9])").matcher(text);
        return m.find() ? safeInt(m.group(1)) : -1;
    }

    private static int safeInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return -1; }
    }

    static int compareDotted(String a, String b) {
        String[] aa = (a == null ? "" : a).replaceAll("[^0-9.]", " ").trim().split("[. ]+");
        String[] bb = (b == null ? "" : b).replaceAll("[^0-9.]", " ").trim().split("[. ]+");
        int n = Math.max(aa.length, bb.length);
        for (int i = 0; i < n; i++) {
            int x = i < aa.length ? safeInt(aa[i]) : 0;
            int y = i < bb.length ? safeInt(bb[i]) : 0;
            if (x != y) return x - y;
        }
        return 0;
    }

    /** 把 Release 正文里的 markdown 噪声清掉，供 TV 弹窗 TextView 显示。 */
    public static String cleanNotes(String body, int maxChars) {
        if (body == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : body.replace("\r\n", "\n").split("\n")) {
            String t = line.trim();
            if (t.startsWith("<!--") || t.startsWith("|") || t.startsWith("<img")) continue;
            if (t.length() == 0) continue;
            t = t.replaceAll("^#{1,6}\\s*", "▪ ")
                 .replaceAll("^>\\s*", "")
                 .replaceAll("^[-*+]\\s+", "· ")
                 .replace("**", "").replace("`", "")
                 .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1").trim();
            if (t.length() > 0) out.append(t).append('\n');
        }
        String result = out.toString().trim();
        if (maxChars > 0 && result.length() > maxChars) result = result.substring(0, maxChars) + "…";
        return result;
    }
}
