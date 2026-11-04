package com.github.tvbox.osc.util;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 订阅配置与 spider.jar 的多通道下载。
 *
 * <p>为什么需要：老 TV 版原来只对原地址发一次请求、用的是 OkGo 默认超时。实测在
 * 同一网络下直连 {@code raw.githubusercontent.com} 拉 1.9MB 的 spider.jar 要 2 分半，
 * 慢网/被墙时干脆超时 —— 表现就是「订阅获取失败」：配置里 112 个站点全部落在
 * {@code SpiderNull}，首页一片空白。而 mirror 前缀（ghproxy.net / gh-proxy.com）
 * 实测能秒回完整文件。</p>
 *
 * <p>策略：<b>直连优先，失败换镜像</b>；每次尝试都有连接/读取超时，避免长时间无响应。
 * 只对 http(s) 地址加前缀，`clan://` 等自有协议原样交给调用方处理。</p>
 *
 * <p>自查：{@code adb logcat -s SubFetch}</p>
 */
public final class MirrorFetch {

    public static final String TAG = "SubFetch";

    /** 镜像前缀，按实测可用性排序；直连始终排第一。 */
    /** 镜像前缀，按实测「能拿全文件」排序；直连始终排第一。 */
    private static final String[] MIRRORS = {
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            "https://ghproxy.net/",   // 实测会 200 但只给半截文件，靠 md5 校验兜底
    };

    private static final int CONNECT_TIMEOUT_SEC = 12;
    private static final int READ_TIMEOUT_SEC = 30;
    /** 单次请求总时长硬上限：readTimeout 只管两次 read 的间隔，服务器一直慢慢吐仍会拖很久
     *  （实测直连资产 118 秒才失败）。callTimeout 从调用开始算，超时必断。 */
    private static final int CALL_TIMEOUT_SEC = 45;

    /** 慢速放弃阈值：前 25 秒拿不到 256KB 就换下一个候选，避免死等慢速直连。 */
    private static final long SLOW_WINDOW_MS = 25000L;
    private static final long SLOW_MIN_BYTES = 256L * 1024L;

    private MirrorFetch() {
    }

    /** 单次请求用短超时；调用方在一个后台线程里顺序尝试候选地址。 */
    private static OkHttpClient client() {
        return Holder.CLIENT;
    }

    private static final class Holder {
        static final OkHttpClient CLIENT = new OkHttpClient.Builder()
                .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * 把 raw.githubusercontent.com/{owner}/{repo}/{branch}/{path} 规范化为 jsDelivr CDN。
     * jsDelivr 国内直连可用（实测 ~1.7s），但只放行文本类文件；二进制（.jar 等）会 403，
     * 所以只用于配置/JS，jar 仍走 gh-proxy 系镜像。
     */
    public static String jsDelivr(String rawUrl) {
        if (rawUrl == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)")
                .matcher(rawUrl);
        if (!m.matches()) return null;
        return "https://cdn.jsdelivr.net/gh/" + m.group(1) + "/" + m.group(2)
                + "@" + m.group(3) + "/" + m.group(4);
    }

    /**
     * jsDelivr 的 gh 基址 → raw.githubusercontent.com 基址（fixContentPath 用）。
     * 例：https://cdn.jsdelivr.net/gh/o/r@branch/  →  https://raw.githubusercontent.com/o/r/branch/
     */
    public static String rawFromJsDelivr(String base) {
        if (base == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "https?://(?:cdn|fastly)\\.jsdelivr\\.net/gh/([^/]+)/([^/@]+)@([^/]+)/(.*)")
                .matcher(base);
        if (!m.matches()) return null;
        String tail = m.group(4);
        // base 末尾是配置文件所在目录；映射到 raw 时 branch 后接目录
        return "https://raw.githubusercontent.com/" + m.group(1) + "/" + m.group(2)
                + "/" + m.group(3) + "/" + tail;
    }

    /** 判断地址看起来是二进制资源（jsDelivr 会 403，不能作为候选）。 */
    private static boolean isBinary(String url) {
        String low = url.toLowerCase();
        int q = low.indexOf('?');
        if (q > 0) low = low.substring(0, q);
        return low.endsWith(".jar") || low.endsWith(".apk") || low.endsWith(".zip")
                || low.endsWith(".so") || low.endsWith(".dex");
    }

    /** 候选地址：直连 + CDN + 各镜像前缀（去重、保持顺序）。 */
    public static String[] candidates(String url) {
        if (url == null || url.length() == 0) return new String[0];
        List<String> out = new ArrayList<>();
        out.add(url);
        if (url.startsWith("http://") || url.startsWith("https://")) {
            // jsDelivr 只对文本类资源放行；二进制走代理镜像
            if (!isBinary(url)) {
                String cdn = jsDelivr(url);
                if (cdn != null && !out.contains(cdn)) out.add(cdn);
                String fastly = cdn == null ? null : cdn.replace("cdn.jsdelivr.net", "fastly.jsdelivr.net");
                if (fastly != null && !out.contains(fastly)) out.add(fastly);
            }
            for (String mirror : MIRRORS) {
                String candidate = mirror + url;
                if (!out.contains(candidate)) out.add(candidate);
            }
        }
        return out.toArray(new String[0]);
    }

    /**
     * 取文本；任一候选成功即返回。全部失败抛最后一个异常。
     *
     * @param url        原地址
     * @param userAgent  请求 UA（可为空）
     * @param accept     请求 Accept（可为空）
     */
    public static String fetchText(String url, String userAgent, String accept) throws Exception {
        String[] urls = candidates(url);
        Exception last = null;
        for (int i = 0; i < urls.length; i++) {
            long start = System.currentTimeMillis();
            try {
                String body = fetchOnce(urls[i], userAgent, accept);
                Log.i(TAG, "text ok try=" + (i + 1) + "/" + urls.length
                        + " cost=" + (System.currentTimeMillis() - start) + "ms len=" + body.length()
                        + " url=" + shorten(urls[i]));
                return body;
            } catch (Exception error) {
                last = error;
                Log.w(TAG, "text fail try=" + (i + 1) + "/" + urls.length
                        + " cost=" + (System.currentTimeMillis() - start) + "ms "
                        + error.getClass().getSimpleName() + ": " + error.getMessage()
                        + " url=" + shorten(urls[i]));
            }
        }
        throw last == null ? new java.io.IOException("没有可用地址") : last;
    }

    private static String fetchOnce(String url, String userAgent, String accept) throws Exception {
        Request.Builder builder = new Request.Builder().url(url);
        if (userAgent != null && userAgent.length() > 0) builder.header("User-Agent", userAgent);
        if (accept != null && accept.length() > 0) builder.header("Accept", accept);
        try (Response response = client().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new java.io.IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new java.io.IOException("空响应");
            return body.string();
        }
    }

    /**
     * 下载文件到 target；先写 .part 再改名，避免半截文件被当成有效 jar。
     *
     * @return 实际写入字节数
     */
    public static long download(String url, File target, String userAgent) throws Exception {
        return download(url, target, userAgent, "", 0L, "");
    }

    /**
     * TV 更新用：除 md5 外还可用「声明的文件大小」和 SHA-256 判定完整性。
     * 任一候选不满足就删掉换下一个（ghproxy.net 常返回 200 但只给半截文件）。
     */
    public static long download(String url, File target, String userAgent,
                                long expectedSize, String expectedSha256) throws Exception {
        return download(url, target, userAgent, "", expectedSize, expectedSha256);
    }

    /** 兼容 ApiConfig：只按 md5 校验（订阅 spider.jar 用）。 */
    public static long download(String url, File target, String userAgent, String expectedMd5) throws Exception {
        return download(url, target, userAgent, expectedMd5, 0L, "");
    }

    /**
     * 下载文件到 target；先写 .part 再改名，避免半截文件被当成有效 jar。
     *
     * @param expectedMd5 配置里声明的 md5（可为空）；不为空时下载完立即校验，
     *                    不匹配就删掉并重试用下一个候选（实测 ghproxy.net 会返回
     *                    HTTP 200 但只给半截文件，不校验会留下坏 jar）。
     * @return 实际写入字节数
     */
    public static long download(String url, File target, String userAgent, String expectedMd5,
                                long expectedSize, String expectedSha256) throws Exception {
        String[] urls = candidates(url);
        Exception last = null;
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        for (int i = 0; i < urls.length; i++) {
            long start = System.currentTimeMillis();
            try {
                long written = downloadOnce(urls[i], target, userAgent);
                if (expectedMd5 != null && expectedMd5.length() > 0) {
                    String actual = com.github.tvbox.osc.util.MD5.getFileMd5(target);
                    if (!expectedMd5.equalsIgnoreCase(actual)) {
                        target.delete();
                        throw new java.io.IOException("MD5 不匹配 expected=" + expectedMd5
                                + " actual=" + actual + "（内容不完整或被改写）");
                    }
                }
                if (expectedSize > 0 && written != expectedSize) {
                    target.delete();
                    throw new java.io.IOException("文件大小不符 expected=" + expectedSize
                            + " actual=" + written + "（内容被截断）");
                }
                if (expectedSha256 != null && expectedSha256.length() > 0) {
                    String actual = sha256(target);
                    if (!expectedSha256.equalsIgnoreCase(actual)) {
                        target.delete();
                        throw new java.io.IOException("SHA-256 不匹配（内容不完整或被改写）");
                    }
                }
                Log.i(TAG, "file ok try=" + (i + 1) + "/" + urls.length
                        + " cost=" + (System.currentTimeMillis() - start) + "ms bytes=" + written
                        + " url=" + shorten(urls[i]));
                return written;
            } catch (Exception error) {
                last = error;
                Log.w(TAG, "file fail try=" + (i + 1) + "/" + urls.length
                        + " cost=" + (System.currentTimeMillis() - start) + "ms "
                        + error.getClass().getSimpleName() + ": " + error.getMessage()
                        + " url=" + shorten(urls[i]));
            }
        }
        throw last == null ? new java.io.IOException("没有可用地址") : last;
    }

    private static long downloadOnce(String url, File target, String userAgent) throws Exception {
        Request.Builder builder = new Request.Builder().url(url);
        if (userAgent != null && userAgent.length() > 0) builder.header("User-Agent", userAgent);
        File part = new File(target.getAbsolutePath() + ".part");
        long startedAt = System.currentTimeMillis();
        long written = 0L;
        try (Response response = client().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new java.io.IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new java.io.IOException("空响应");
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    written += read;
                    // 慢速放弃：死等慢速直连会让用户以为「没反应」，及时把机会给镜像。
                    if (written < SLOW_MIN_BYTES
                            && System.currentTimeMillis() - startedAt > SLOW_WINDOW_MS) {
                        throw new java.io.IOException("速度过慢（" + SLOW_WINDOW_MS / 1000
                                + "s 仅 " + written + " 字节）");
                    }
                }
            }
        } catch (Exception error) {
            if (part.exists()) part.delete();
            throw error;
        }
        if (written <= 0) {
            part.delete();
            throw new java.io.IOException("空文件");
        }
        if (target.exists()) target.delete();
        if (!part.renameTo(target)) {
            part.delete();
            throw new java.io.IOException("无法重命名下载文件");
        }
        return written;
    }

    private static String sha256(File file) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte v : hash) sb.append(String.format("%02X", v));
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String shorten(String url) {
        return url.length() <= 96 ? url : url.substring(0, 96) + "…";
    }
}
