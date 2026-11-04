package com.github.tvbox.osc.diag;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 车机诊断日志：环形滚动文件 + 自动上传到官方服务器。
 *
 * <p>为什么需要：车机不在手边时，用户说「封面还是不显示」但 logcat 拿不到；这套东西把
 * App 运行期 logcat 固定写进私有目录，按大小切 4 片（共 1 MiB，永不无限增长），
 * 每两分钟把「上次成功上传之后的新内容」POST 到官方通道，服务器端同样按设备滚动保留。</p>
 *
 * <p>上传的是**全部 logcat**，不只我们自己的 tag：图片栈 OkHttp/Glide、系统安装器、
 * 音频焦点等第三方日志才是定位问题的关键。含 URL，因此只传用户自己的服务器并带 token。</p>
 */
public final class DiagLog {

    public static final String TAG = "DiagLog";
    private static final String BASE = "https://dl.z736.vip/tvbox-logs/";
    /** 上传凭据：写死在客户端，仅用于「限速 + 挡扫描」，不是保密凭据。 */
    private static final String TOKEN = "yi2yX6BYTr7byTbJcHG0i3a0uqmFhN_U";

    private static final long PART_BYTES = 256L * 1024L;
    private static final int MAX_PARTS = 4;
    private static final long MAX_UPLOAD_BYTES = 480L * 1024L;
    private static final long UPLOAD_INTERVAL_MS = 120_000L;
    private static final long START_DELAY_MS = 20_000L;
    private static final long RETRY_INTERVAL_MS = 30_000L;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "diag-log");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static Context app;
    private static String deviceId;
    private static String appVersion;
    private static volatile long startMs;
    private static volatile long lastUploadMs;
    private static volatile long lastAttemptMs;
    private static volatile long lastSentOffset = -1L;
    private static volatile long lastSentPart = -1L;

    private DiagLog() {
    }

    /** App 启动即调用；重复调用无副作用。 */
    public static void start(Context context) {
        if (context == null || !STARTED.compareAndSet(false, true)) return;
        app = context.getApplicationContext();
        deviceId = resolveDeviceId(app);
        appVersion = resolveVersion(app);
        startMs = System.currentTimeMillis();
        POOL.execute(() -> {
            writeMarker("=== diag start device=" + deviceId + " app=" + appVersion
                    + " build=" + Build.MODEL + "/" + Build.VERSION.RELEASE + " ===");
            pumpLogcat();
        });
    }

    /** 设置页手动「立即上传」：强制补传一次。 */
    public static void uploadNow() {
        if (app == null) return;
        POOL.execute(() -> {
            writeMarker("=== manual upload requested ===");
            uploadIfDue(true);
        });
    }

    /** 供设置页展示的状态文案。 */
    public static String status() {
        if (app == null) return "未启动";
        long bytes = totalBytes();
        String last = lastUploadMs <= 0 ? "尚未上传" : new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
                .format(new Date(lastUploadMs));
        return String.format(Locale.US, "设备 %s · 本地 %.1f MB · 上次上传 %s",
                deviceId, bytes / 1024.0 / 1024.0, last);
    }

    /** 立即写一行标记（崩溃前后、关键操作用）。 */
    public static void event(String message) {
        if (app == null) return;
        writeMarker("EVENT " + message);
    }

    @SuppressLint("HardwareIds")
    private static String resolveDeviceId(Context context) {
        String raw = null;
        try {
            raw = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.length() < 4) raw = Build.SERIAL;
        if (raw == null || raw.length() < 4) raw = "unknown";
        String clean = raw.replaceAll("[^A-Za-z0-9_-]", "");
        if (clean.length() < 4) clean = "device" + Math.abs(raw.hashCode());
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    private static String resolveVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "unknown";
        }
    }

    private static File dir() {
        File dir = new File(app.getFilesDir(), "diaglog");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "无法创建日志目录");
        return dir;
    }

    private static long totalBytes() {
        File[] files = dir().listFiles((d, n) -> n.startsWith("part-") && n.endsWith(".log"));
        long total = 0;
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    /** 追加一行并按需滚动；只保留 MAX_PARTS 片。 */
    private static synchronized void append(byte[] line) {
        if (app == null || line == null || line.length == 0) return;
        try {
            File dir = dir();
            File[] parts = parts(dir);
            File current;
            int index;
            if (parts.length == 0) {
                index = 1;
                current = new File(dir, partName(index));
            } else {
                index = partIndex(parts[parts.length - 1]);
                current = parts[parts.length - 1];
                if (current.length() + line.length > PART_BYTES) {
                    index += 1;
                    current = new File(dir, partName(index));
                }
            }
            try (FileOutputStream out = new FileOutputStream(current, true)) {
                out.write(line);
                if (line.length == 0 || line[line.length - 1] != '\n') out.write('\n');
            }
            // 单设备 4 片循环覆盖：删掉序号过小的旧片
            File[] after = parts(dir);
            for (File old : after) {
                if (partIndex(old) <= index - MAX_PARTS) {
                    if (old.delete() && lastSentPart == partIndex(old)) {
                        lastSentPart = -1;
                        lastSentOffset = -1;
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "append failed: " + error);
        }
    }

    private static File[] parts(File dir) {
        File[] files = dir.listFiles((d, n) -> n.startsWith("part-") && n.endsWith(".log"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingInt(DiagLog::partIndex));
        return files;
    }

    private static String partName(int index) {
        return String.format(Locale.US, "part-%06d.log", index);
    }

    private static int partIndex(File file) {
        String name = file.getName();
        try {
            return Integer.parseInt(name.substring(5, name.length() - 4));
        } catch (Exception error) {
            return 0;
        }
    }

    private static void writeMarker(String text) {
        append((new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + " I/" + TAG + ": " + text).getBytes(UTF8));
    }

    /**
     * 常驻读 logcat：进程存活期间不断把新行写进环形文件。
     * 用 {@code logcat -v time -T <行数>} 会漏，直接全量读并靠 reader 的流式语义即可；
     * 进程重启时先 clear 一次，避免把上一次启动的旧日志重复搬进来。
     */
    private static void pumpLogcat() {
        Process process = null;
        try {
            Runtime.getRuntime().exec(new String[]{"logcat", "-c"}).waitFor();
        } catch (Throwable ignored) {
        }
        // 进程重启后把「上次写到的位置」接上；环形文件不会无限长。
        File[] existing = parts(dir());
        if (existing.length > 0) {
            File last = existing[existing.length - 1];
            lastSentPart = partIndex(last);
            lastSentOffset = last.length();
        }
        while (!Thread.currentThread().isInterrupted()) {
            BufferedReader reader = null;
            try {
                process = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "time"});
                reader = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF8), 8192);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().length() == 0) continue;
                    if (line.length() > 4096) line = line.substring(0, 4093) + "...";
                    append(line.getBytes(UTF8));
                    // 上传失败时设置退避，避免每一行日志都触发一次失败请求。
                    long now = System.currentTimeMillis();
                    if (now - startMs >= START_DELAY_MS
                            && now - lastUploadMs >= UPLOAD_INTERVAL_MS
                            && now - lastAttemptMs >= RETRY_INTERVAL_MS) {
                        lastAttemptMs = now;
                        uploadIfDue(false);
                    }
                }
            } catch (Throwable error) {
                Log.w(TAG, "logcat pump error: " + error);
            } finally {
                if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
                if (process != null) process.destroy();
            }
            sleep(3000L);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    /**
     * 把「上次成功位置之后」的新日志读出来上传。跨分片按序号从小到大读，
     * 成功后记录 (partIndex, offset)，不成功不推进 —— 下次重试不丢日志。
     */
    private static synchronized void uploadIfDue(boolean force) {
        if (app == null) return;
        if (!force && System.currentTimeMillis() - lastUploadMs < UPLOAD_INTERVAL_MS) return;
        File[] parts = parts(dir());
        if (parts.length == 0) return;
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        long cursorPart = lastSentPart;
        long cursorOffset = lastSentOffset;
        if (cursorPart < 0) {
            // 首次：只发最近一片，避免一次把 1 MiB 全推上去
            cursorPart = partIndex(parts[parts.length - 1]) - 1;
            cursorOffset = Long.MAX_VALUE;
        }
        boolean advanced = false;
        for (File part : parts) {
            int idx = partIndex(part);
            if (idx < cursorPart) continue;
            long skip = idx == cursorPart ? (cursorOffset == Long.MAX_VALUE ? part.length() : cursorOffset) : 0L;
            if (skip > part.length()) skip = part.length();
            try (FileInputStream in = new FileInputStream(part)) {
                if (skip > 0) {
                    long left = skip;
                    while (left > 0) {
                        long n = in.skip(left);
                        if (n <= 0) break;
                        left -= n;
                    }
                }
                byte[] chunk = new byte[8192];
                int read;
                boolean truncated = false;
                while ((read = in.read(chunk)) > 0) {
                    if (buffer.size() + read > MAX_UPLOAD_BYTES) {
                        int room = (int) (MAX_UPLOAD_BYTES - buffer.size());
                        if (room > 0) buffer.write(chunk, 0, room);
                        truncated = true;
                        break;
                    }
                    buffer.write(chunk, 0, read);
                }
                if (truncated) {
                    // 只推进到已发出去的字节，避免 480KB 截断时丢中间一段。
                    cursorPart = idx;
                    cursorOffset = skip + buffer.size();
                    break;
                }
                if (part != parts[parts.length - 1]) {
                    // 中间片全部读完 → 下一轮从下一片 0 开始
                    cursorPart = idx + 1;
                    cursorOffset = 0L;
                    advanced = true;
                } else {
                    cursorPart = idx;
                    cursorOffset = part.length();
                }
            } catch (Throwable error) {
                Log.w(TAG, "read part failed: " + error);
                return;
            }
        }
        byte[] payload = buffer.toByteArray();
        if (payload.length == 0 && !force) return;
        if (payload.length == 0) writeMarker("=== manual upload: no new lines ===");
        if (payload.length == 0) return;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE + "upload?device=" + deviceId + "&token=" + TOKEN
                    + "&app=" + java.net.URLEncoder.encode(appVersion, "UTF-8"));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(payload.length);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                lastSentPart = cursorPart;
                lastSentOffset = cursorOffset;
                lastUploadMs = System.currentTimeMillis();
                writeMarker("upload ok bytes=" + payload.length + " code=" + code
                        + (advanced ? " rolled" : ""));
            } else {
                Log.w(TAG, "upload rejected code=" + code);
            }
        } catch (Throwable error) {
            Log.w(TAG, "upload failed: " + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : (": " + error.getMessage())));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
