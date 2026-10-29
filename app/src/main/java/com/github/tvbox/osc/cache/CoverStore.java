package com.github.tvbox.osc.cache;

import android.util.Log;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.util.CoverKey;

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
 * <p>为什么需要它：历史/收藏只保存封面 URL，图片能不能显示完全取决于 Glide 的
 * 按需磁盘缓存 —— 站点换图床、删图、限速、车机没网，都会让已经看过的片子退回
 * 「随机纯色块 + 片名首字」。那看起来就像封面坏了，其实只是没缓存。</p>
 *
 * <p>策略：</p>
 * <ul>
 *   <li>写入时机：详情页拿到播放地址（也就是真正"看过一次"）时调用 {@link #remember}；</li>
 *   <li>读取时机：历史卡片优先用 {@link #localFile}，命中就直接读本地，零网络；</li>
 *   <li>总量上限 {@link #MAX_BYTES}，超出按最后访问时间淘汰最旧的；</li>
 *   <li>单张上限 2MB，过大的图不缓存（避免把流量和空间都吃掉）。</li>
 * </ul>
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

    private static final AtomicBoolean TRIMMING = new AtomicBoolean(false);

    private CoverStore() {
    }

    private static File dir(android.content.Context context) {
        File dir = new File(context.getFilesDir(), "cover");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "无法创建封面目录 " + dir);
        }
        return dir;
    }

    /** 已缓存的本地文件；没有则返回 null。命中会 touch 一下用于 LRU 淘汰。 */
    public static File localFile(android.content.Context context, String pic) {
        String name = CoverKey.fileName(pic);
        if (name == null || !CoverKey.isValidFileName(name)) return null;
        File file = new File(dir(context), name);
        if (!file.exists() || file.length() <= 0) return null;
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(System.currentTimeMillis());
        return file;
    }

    /** 本地绝对路径；没有缓存返回空串。 */
    public static String localPath(android.content.Context context, String pic) {
        File file = localFile(context, pic);
        return file == null ? "" : file.getAbsolutePath();
    }

    /**
     * 按「URL → 片名+来源」两级查找本地缓存。
     *
     * <p>站点换图床后 URL 会变，纯 URL 键命不中；片名+来源是稳定的，
     * 所以第二级用内容键兜底，避免「明明看过却变回色块」。</p>
     *
     * @return 命中的本地文件；都没有返回 null
     */
    public static File findLocal(android.content.Context context, String pic, String name, String sourceKey) {
        File byUrl = localFile(context, pic);
        if (byUrl != null) return byUrl;
        String contentName = CoverKey.contentFileName(name, sourceKey);
        if (contentName == null || !CoverKey.isValidFileName(contentName)) return null;
        File byContent = new File(dir(context), contentName);
        if (!byContent.exists() || byContent.length() <= 0) return null;
        // 命中内容键：顺便用当前 URL 再存一份，之后走 URL 键就是 O(1)。
        String urlName = CoverKey.fileName(pic);
        if (urlName != null && CoverKey.isValidFileName(urlName)) {
            File copy = new File(dir(context), urlName);
            if (!copy.exists()) {
                try {
                    copy(copy, byContent);
                } catch (Exception ignored) {
                    // 复制失败不影响本次显示
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        byContent.setLastModified(System.currentTimeMillis());
        return byContent;
    }

    /** 按片名+来源查找本地缓存文件的绝对路径。 */
    public static String findLocalPath(android.content.Context context, String pic, String name, String sourceKey) {
        File file = findLocal(context, pic, name, sourceKey);
        return file == null ? "" : file.getAbsolutePath();
    }

    private static void copy(File target, File source) throws java.io.IOException {
        try (InputStream in = new java.io.FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
    }

    /**
     * 记下这张封面：本地已有就直接返回，否则后台下载。
     *
     * <p>调用点放在「确实看过」之后，不阻塞界面，失败只打日志。</p>
     */
    public static void remember(final android.content.Context context, final String pic) {
        remember(context, pic, null, null);
    }

    /**
     * 记下这张封面：本地已有就直接返回，否则后台下载。
     *
     * @param name      片名（可空）；给了就和 {@code sourceKey} 一起写一份内容键，
     *                  之后站点换图床、URL 变了也能命中
     * @param sourceKey 站点 key（可空）
     */
    public static void remember(final android.content.Context context, final String pic,
                                final String name, final String sourceKey) {
        if (context == null) return;
        final android.content.Context app = context.getApplicationContext();
        String name2 = CoverKey.fileName(pic);
        if (name2 == null || !CoverKey.isValidFileName(name2)) return;
        final File target = new File(dir(app), name2);
        String contentName = CoverKey.contentFileName(name, sourceKey);
        final File contentTarget = contentName != null && CoverKey.isValidFileName(contentName)
                ? new File(dir(app), contentName) : null;
        if (target.exists() && target.length() > 0) {
            //noinspection ResultOfMethodCallIgnored
            target.setLastModified(System.currentTimeMillis());
            if (contentTarget != null && !contentTarget.exists()) {
                try {
                    copy(contentTarget, target);
                } catch (Exception ignored) {
                }
            }
            return;
        }
        if (contentTarget != null && contentTarget.exists() && contentTarget.length() > 0) {
            // URL 变了但内容键还命中：补一份 URL 键，省一次下载。
            try {
                copy(target, contentTarget);
                Log.i(TAG, "restored " + target.getName() + " from content key");
                return;
            } catch (Exception ignored) {
            }
        }
        if (!IN_FLIGHT.add(name2)) return;
        POOL.execute(() -> {
            try {
                download(app, pic, target);
                if (contentTarget != null && !contentTarget.exists() && target.exists()) {
                    try {
                        copy(contentTarget, target);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception error) {
                Log.w(TAG, "cache failed " + shortUrl(pic) + " : " + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : (": " + error.getMessage())));
            } finally {
                IN_FLIGHT.remove(name2);
            }
        });
    }

    private static void download(android.content.Context context, String pic, File target) throws Exception {
        OkHttpClient client = OkHttp.client(CONNECT_TIMEOUT_MS).newBuilder()
                .readTimeout(READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        Request request = new Request.Builder()
                .url(pic)
                .header("User-Agent", "TVBox-Car/CoverCache")
                .build();
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
            if (!temp.renameTo(target)) throw new java.io.IOException("无法重命名缓存文件");
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
                if (file.delete()) freed += size;
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