package com.github.tvbox.osc.cache;

import android.util.Log;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.util.CoverKey;
import com.github.tvbox.osc.util.PlainHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 封面落盘缓存：把「看过的片子」的封面存到应用私有目录，之后不再依赖网络和图床。
 *
 * <p>r74 追加可解码性守门：下载后先验证/转码，启动时自愈旧版本留下的坏文件，
 * 读取时拒收风险格式，避免「本地永远失败 → 回退网络 → 认为已缓存」的死循环。</p>
 *
 * <p>自查：{@code adb logcat -s CoverCache}</p>
 */
public final class CoverStore {

    public static final String TAG = "CoverCache";

    /** 缓存总量上限。 */
    private static final long MAX_BYTES = 120L * 1024 * 1024;
    /** 单张封面大小上限。 */
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "cover-cache");
        thread.setDaemon(true);
        return thread;
    });

    /** 同一个地址只跑一次，避免列表滚动时重复下载同一张封面。 */
    private static final java.util.Set<String> IN_FLIGHT =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /** 下载失败退避：key=缓存文件名，value=最早可重试时间。 */
    private static final java.util.Map<String, Long> RETRY_BACKOFF =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());
    private static final long RETRY_BACKOFF_MS = 60_000L;
    private static final AtomicBoolean TRIMMING = new AtomicBoolean(false);
    /** r74：自愈与下载/读取互斥，避免同一文件被并发改写。 */
    private static final Object FILE_LOCK = new Object();
    /** r74：同一个坏文件只排一次自愈任务，避免列表滚动时反复排队。 */
    private static final java.util.Set<String> HEAL_IN_FLIGHT =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private CoverStore() {
    }

    /** 从内联头信息里解析出的请求头（{@code @Referer=} / {@code @Cookie=} / {@code @User-Agent=} / {@code @Headers=}）。 */
    private static java.util.Map<String, String> inlineHeaders(String pic) {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (pic == null) return headers;
        headers.put("User-Agent", "TVBox-Car/CoverCache");
        int at = pic.indexOf("@Headers=");
        if (at > 0) {
            String raw = pic.substring(at + "@Headers=".length());
            int next = raw.indexOf('@');
            if (next > 0) raw = raw.substring(0, next);
            try {
                String decoded = java.net.URLDecoder.decode(raw, "UTF-8");
                org.json.JSONObject json = new org.json.JSONObject(decoded);
                for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String key = it.next();
                    String value = json.optString(key, "");
                    if (!key.isEmpty() && !value.isEmpty()) headers.put(key, value);
                }
            } catch (Exception ignored) {
            }
        }
        headers.putAll(inlineHeader(pic, "@Referer=", "Referer"));
        headers.putAll(inlineHeader(pic, "@Cookie=", "Cookie"));
        headers.putAll(inlineHeader(pic, "@User-Agent=", "User-Agent"));
        return headers;
    }

    private static java.util.Map<String, String> inlineHeader(String pic, String marker, String name) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        int at = pic.indexOf(marker);
        if (at <= 0) return out;
        String value = pic.substring(at + marker.length());
        int next = value.indexOf('@');
        if (next > 0) value = value.substring(0, next);
        if (!value.isEmpty()) out.put(name, value);
        return out;
    }

    private static File dir(android.content.Context context) {
        File dir = new File(context.getFilesDir(), "cover");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "无法创建封面目录 " + dir);
        }
        return dir;
    }

    /**
     * r74：封面文件是否可以直接交给 {@code BitmapFactory.decodeFile}。
     * 只做 32 字节头快筛 + 显式校验标记；未验证的风险文件交给后台自愈。
     */
    public static boolean isReadableImage(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return false;
        if (!CoverImageIo.hasRiskyHeader(file)) return true;
        if (CoverImageIo.isValidated(file)) return true;
        healFile(file);
        return false;
    }

    /** r74：App 启动时后台扫描一次，把旧版本落盘的不可解码封面转码或删掉。 */
    public static void healLegacyCovers(final android.content.Context context) {
        if (context == null) return;
        final android.content.Context app = context.getApplicationContext();
        POOL.execute(() -> {
            File[] files = dir(app).listFiles((d, n) -> n.endsWith(".img"));
            if (files == null || files.length == 0) return;
            int normalized = 0, removed = 0;
            for (File file : files) {
                // 普通 .webp 头也可能因 skia 缺解码器而失败；这里做一次真实解码验证。
                int result = healNow(file);
                if (result == CoverImageIo.HEAL_NORMALIZED) normalized++;
                if (result == CoverImageIo.HEAL_REMOVED) removed++;
            }
            Log.i(TAG, "cover heal: scanned=" + files.length
                    + " normalized=" + normalized + " removed=" + removed);
        });
    }

    /** r74：单个坏文件的后台自愈；由 CoverBinder 解码失败或读取快筛触发。 */
    public static void healFile(final File file) {
        if (file == null || !HEAL_IN_FLIGHT.add(file.getAbsolutePath())) return;
        POOL.execute(() -> {
            try {
                healNow(file);
            } finally {
                HEAL_IN_FLIGHT.remove(file.getAbsolutePath());
            }
        });
    }

    private static int healNow(File file) {
        synchronized (FILE_LOCK) {
            return CoverImageIo.heal(file);
        }
    }

    /** 已缓存的本地文件；没有则返回 null。命中会 touch 一下用于 LRU 淘汰。 */
    public static File localFile(android.content.Context context, String pic) {
        String name = CoverKey.fileName(pic);
        if (name == null || !CoverKey.isValidFileName(name)) return null;
        File file = new File(dir(context), name);
        if (!isReadableImage(file)) return null;
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(System.currentTimeMillis());
        return file;
    }

    /** 按片名+来源查找本地缓存文件的绝对路径。 */
    public static String findLocalPath(android.content.Context context, String pic, String name, String sourceKey) {
        File file = findLocal(context, pic, name, sourceKey);
        return file == null ? "" : file.getAbsolutePath();
    }

    /** 只按内容键（片名 + 来源）查找；封面 URL 为空的老记录靠它兜底。 */
    public static String findLocalPathByContent(android.content.Context context, String name, String sourceKey) {
        String contentName = CoverKey.contentFileName(name, sourceKey);
        if (contentName == null || !CoverKey.isValidFileName(contentName)) return "";
        File file = new File(dir(context), contentName);
        if (!isReadableImage(file)) return "";
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(System.currentTimeMillis());
        return file.getAbsolutePath();
    }

    /** 本地绝对路径；没有缓存返回空串。 */
    public static String localPath(android.content.Context context, String pic) {
        File file = localFile(context, pic);
        return file == null ? "" : file.getAbsolutePath();
    }

    /** 用已有内容键文件补一个 URL 键副本（后台执行，不阻塞列表渲染）。 */
    public static void reindex(final android.content.Context context, final String pic, final String sourcePath) {
        if (context == null || pic == null || sourcePath == null) return;
        final android.content.Context app = context.getApplicationContext();
        POOL.execute(() -> {
            synchronized (FILE_LOCK) {
                try {
                    String name = CoverKey.fileName(pic);
                    if (name == null || !CoverKey.isValidFileName(name)) return;
                    File target = new File(dir(app), name);
                    if (target.exists() && target.length() > 0) return;
                    File source = new File(sourcePath);
                    if (!isReadableImage(source)) return;
                    copyWithMarker(target, source);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 按「URL → 片名+来源」两级查找本地缓存。 */
    public static File findLocal(android.content.Context context, String pic, String name, String sourceKey) {
        File byUrl = localFile(context, pic);
        if (byUrl != null) return byUrl;
        String contentName = CoverKey.contentFileName(name, sourceKey);
        if (contentName == null || !CoverKey.isValidFileName(contentName)) return null;
        File byContent = new File(dir(context), contentName);
        if (!isReadableImage(byContent)) return null;
        String urlName = CoverKey.fileName(pic);
        if (urlName != null && CoverKey.isValidFileName(urlName)) {
            File copy = new File(dir(context), urlName);
            if (!copy.exists()) {
                try {
                    synchronized (FILE_LOCK) {
                        copyWithMarker(copy, byContent);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        byContent.setLastModified(System.currentTimeMillis());
        return byContent;
    }

    private static void copy(File target, File source) throws java.io.IOException {
        try (InputStream in = new java.io.FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
    }

    /** 复制封面时同步 .ok 标记，别让已验证的风险格式副本又被当成坏文件。 */
    private static void copyWithMarker(File target, File source) throws java.io.IOException {
        copy(target, source);
        if (CoverImageIo.isValidated(source)) {
            CoverImageIo.markValidated(target);
        } else {
            CoverImageIo.deleteValidationMarker(target);
        }
    }

    /** 记下这张封面：本地已有就直接返回，否则后台下载。 */
    public static void remember(final android.content.Context context, final String pic) {
        remember(context, pic, null, null);
    }

    /** 记下这张封面，并写一份内容键兜底。 */
    public static void remember(final android.content.Context context, final String pic,
                                final String name, final String sourceKey) {
        remember(context, pic, name, sourceKey, null);
    }

    /** 同上，但落盘成功后主线程回调 {@code onReady}。 */
    public static void remember(final android.content.Context context, final String pic,
                                final String name, final String sourceKey, final Runnable onReady) {
        if (context == null) return;
        final android.content.Context app = context.getApplicationContext();
        String name2 = CoverKey.fileName(pic);
        if (name2 == null || !CoverKey.isValidFileName(name2)) return;
        final File target = new File(dir(app), name2);
        String contentName = CoverKey.contentFileName(name, sourceKey);
        final File contentTarget = contentName != null && CoverKey.isValidFileName(contentName)
                ? new File(dir(app), contentName) : null;
        if (isReadableImage(target)) {
            //noinspection ResultOfMethodCallIgnored
            target.setLastModified(System.currentTimeMillis());
            if (contentTarget != null && !contentTarget.exists()) {
                try {
                    synchronized (FILE_LOCK) {
                        copyWithMarker(contentTarget, target);
                    }
                } catch (Exception ignored) {
                }
            }
            if (onReady != null) MAIN.post(onReady);
            return;
        }
        if (contentTarget != null && isReadableImage(contentTarget)) {
            try {
                synchronized (FILE_LOCK) {
                    copyWithMarker(target, contentTarget);
                }
                Log.i(TAG, "restored " + target.getName() + " from content key");
                if (onReady != null) MAIN.post(onReady);
                return;
            } catch (Exception ignored) {
            }
        }
        Long blockedUntil = RETRY_BACKOFF.get(name2);
        if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) return;
        if (!IN_FLIGHT.add(name2)) return;
        POOL.execute(() -> {
            boolean cached = false;
            try {
                download(app, pic, target);
                cached = true;
                if (contentTarget != null && !contentTarget.exists() && target.exists()) {
                    try {
                        synchronized (FILE_LOCK) {
                            copyWithMarker(contentTarget, target);
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception error) {
                Log.w(TAG, "cache failed " + shortUrl(pic) + " : " + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : (": " + error.getMessage())));
                RETRY_BACKOFF.put(name2, System.currentTimeMillis() + RETRY_BACKOFF_MS);
            } finally {
                IN_FLIGHT.remove(name2);
                if (cached) RETRY_BACKOFF.remove(name2);
            }
            if (cached && onReady != null) MAIN.post(onReady);
        });
    }

    private static void download(android.content.Context context, String pic, File target) throws Exception {
        // 两轮：先系统 DNS 直连，再用 App 栈兜底。
        try {
            downloadVia(PlainHttp.systemDns(), context, pic, target);
            return;
        } catch (Exception error) {
            Log.w(TAG, "cache via system-dns failed " + shortUrl(pic) + " : "
                    + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : (": " + error.getMessage())));
        }
        OkHttpClient appStack = OkHttp.client(CONNECT_TIMEOUT_MS).newBuilder()
                .readTimeout(READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        downloadVia(appStack, context, pic, target);
    }

    private static void downloadVia(OkHttpClient client, android.content.Context context,
                                    String pic, File target) throws Exception {
        String cleanUrl = CoverKey.normalize(pic);
        if (cleanUrl.length() == 0) throw new java.io.IOException("封面地址为空");
        Request.Builder builder = new Request.Builder().url(cleanUrl);
        for (java.util.Map.Entry<String, String> entry : inlineHeaders(pic).entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        Request request = builder.build();
        File temp = new File(target.getAbsolutePath() + ".part");
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            long length = body.contentLength();
            if (length > MAX_FILE_BYTES) {
                throw new java.io.IOException("图片过大 " + length + " 字节，跳过缓存");
            }
            long written = 0L;
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    written += read;
                    if (written > MAX_FILE_BYTES) {
                        throw new java.io.IOException("图片超过 " + MAX_FILE_BYTES + " 字节，跳过缓存");
                    }
                    out.write(buffer, 0, read);
                }
            }
            if (written <= 0) throw new java.io.IOException("空响应");
            synchronized (FILE_LOCK) {
                // r74：先验证/转码，避免 HTML 错误页或本机解不开的扩展 WebP
                // 被当成「已缓存」，形成永久 decode failed 循环。
                if (!CoverImageIo.ensureDecodable(temp)) {
                    throw new java.io.IOException("封面不可解码，拒收");
                }
                if (!temp.renameTo(target)) throw new java.io.IOException("无法重命名缓存文件");
                if (CoverImageIo.hasRiskyHeader(target)) {
                    CoverImageIo.markValidated(target);
                } else {
                    CoverImageIo.deleteValidationMarker(target);
                }
            }
            Log.i(TAG, "cached " + target.getName() + " bytes=" + written + " url=" + shortUrl(pic));
            trim(context);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            if (temp.exists()) temp.delete();
        }
    }

    /** 超出总量就按最后访问时间删最旧的。 */
    public static void trim(android.content.Context context) {
        if (!TRIMMING.compareAndSet(false, true)) return;
        try {
            File dir = dir(context);
            File[] files = dir.listFiles((d, n) -> n.endsWith(".img"));
            if (files == null || files.length == 0) return;
            long total = 0L;
            for (File file : files) total += file.length();
            if (total <= MAX_BYTES) return;
            List<File> ordered = new ArrayList<>(Arrays.asList(files));
            ordered.sort(Comparator.comparingLong(File::lastModified));
            long freed = 0L;
            for (File file : ordered) {
                if (total - freed <= MAX_BYTES) break;
                long size = file.length();
                if (file.delete()) {
                    freed += size;
                    CoverImageIo.deleteValidationMarker(file);
                }
            }
            Log.i(TAG, "trim freed=" + freed + " keep=" + (total - freed) + " files=" + files.length);
        } finally {
            TRIMMING.set(false);
        }
    }

    /** 统计信息，用于设置页/诊断。 */
    public static String stats(android.content.Context context) {
        File dir = dir(context);
        File[] files = dir.listFiles((d, n) -> n.endsWith(".img"));
        if (files == null) return "0 张 / 0 MB";
        long total = 0L;
        for (File file : files) total += file.length();
        return files.length + " 张 / " + String.format(java.util.Locale.US, "%.1f MB", total / 1024.0 / 1024.0);
    }

    /** 清空封面缓存。 */
    public static void clear(android.content.Context context) {
        File dir = dir(context);
        File[] files = dir.listFiles();
        int removed = 0;
        if (files != null) {
            for (File file : files) {
                if (file.delete()) removed++;
            }
        }
        Log.i(TAG, "cleared " + removed + " files");
    }

    private static String shortUrl(String url) {
        if (url == null) return "null";
        String value = CoverKey.normalize(url);
        return value.length() <= 64 ? value : value.substring(0, 64) + "…";
    }
}
