package com.github.tvbox.osc.diag;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

/**
 * 车机诊断日志客户端 —— canonical v2（app-diagnostics，product=tvbox）。
 *
 * <p>协议：{@code POST https://dl.z736.vip/app-diagnostics/v2/upload}，
 * {@code Content-Type: application/json} + {@code Content-Encoding: gzip}，
 * {@code Authorization: Bearer <upload-only token>}。请求体是 JSON 信封，正文放
 * {@code rawLog}。旧 {@code /tvbox-logs/upload} 由服务端兼容路由转发，本客户端不再使用。</p>
 *
 * <p>平台侧约定的可靠性要求，逐条落实：</p>
 * <ul>
 *   <li><b>设备 ID 持久随机</b>：一次性 UUID，落在 SharedPreferences，重启/升级不变，
 *       只在清除数据时重建（不再用 ANDROID_ID）。</li>
 *   <li><b>batch 与请求体先持久化、失败复用</b>：发请求前把 gzip 后的完整字节写到
 *       {@code pending.bin}，把 batch/cursor/nextCursor/sha256 写到 {@code pending.json}；
 *       失败不删、不换 batch，下次重试发的是<b>同一份字节</b>。</li>
 *   <li><b>只使用 upload-only token</b>：APK 内只放 tvbox 作用域的上传 token；
 *       read-only token 只存在于服务器和分析端，绝不进源码/APK。</li>
 *   <li><b>严格成功判据</b>：仅当 HTTP 200 且 {@code accepted=true}、{@code id} 非空、
 *       {@code product}/{@code device} 匹配、{@code cursor} 与发出值一致、
 *       {@code bytes>0} 时才推进游标。</li>
 *   <li><b>duplicate</b>：{@code duplicate=true} 且 id 与已记录的一致（没有记录时按同一
 *       batch 的服务端去重结果）视为成功，同样推进游标。</li>
 * </ul>
 *
 * <p>本地仍是硬上限滚动存储：4 片 × 256 KiB = 1 MiB，超限删最旧；游标持久化，
 * 进程重启接着上次位置继续，不重发也不丢。</p>
 *
 * <p>线程：{@link #PUMP} 常驻读 logcat；{@link #SCHED} 每 30 秒做一次上传判定，
 * 手动「立即上传」也投到这里。r68 用单线程池时泵把线程占满，手动上传永远排不上 —— 已拆开。</p>
 */
public final class DiagLog {

    public static final String TAG = "DiagLog";

    private static final String UPLOAD_URL = "https://dl.z736.vip/app-diagnostics/v2/upload";
    private static final String PRODUCT = "tvbox";
    private static final String CLIENT_VERSION = "diag-v2-r70";
    /** tvbox 作用域的 upload-only token（只能上传，不能读）。read token 不在这里。 */
    private static final String UPLOAD_TOKEN = "yi2yX6BYTr7byTbJcHG0i3a0uqmFhN_U";

    private static final long PART_BYTES = 256L * 1024L;
    private static final int MAX_PARTS = 4;
    /** 单批原始日志上限；gzip 后远小于服务端 512 KiB 硬限。 */
    private static final int MAX_RAW_LOG_BYTES = 400 * 1024;
    private static final long UPLOAD_INTERVAL_MS = 120_000L;
    private static final long START_DELAY_MS = 20_000L;
    private static final long RETRY_INTERVAL_MS = 30_000L;
    private static final long TICK_INTERVAL_MS = 30_000L;

    private static final java.util.concurrent.ExecutorService PUMP =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "diag-log-pump");
                thread.setDaemon(true);
                return thread;
            });
    private static final ScheduledExecutorService SCHED =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "diag-log-upload");
                thread.setDaemon(true);
                return thread;
            });
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    /** 待写盘的标记（事件）队列；只在 DiagLog 内部使用。 */
    private static final java.util.concurrent.ConcurrentLinkedQueue<String> pendingMarkers =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final AtomicBoolean writerScheduled = new AtomicBoolean(false);
    /** 队列上限：宁可丢诊断标记，也不能让主线程等待磁盘。 */
    private static final int MAX_PENDING_MARKERS = 512;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static Context app;
    private static String deviceId;
    private static String appVersion;
    private static volatile long startMs;
    private static volatile long lastUploadMs;
    private static volatile long lastAttemptMs;
    private static volatile boolean drainNow;
    /** 已成功上传到的位置，格式 {@code <partIndex>:<byteOffset>}；真值以 cursor.txt 为准。 */
    private static volatile String cursor = "";

    private DiagLog() {
    }

    /** App 启动即调用；重复调用无副作用。 */
    public static void start(Context context) {
        if (context == null || !STARTED.compareAndSet(false, true)) return;
        app = context.getApplicationContext();
        deviceId = resolveDeviceId(app);
        appVersion = resolveVersion(app);
        startMs = System.currentTimeMillis();
        cursor = loadCursor();
        PUMP.execute(() -> {
            writeMarker("=== diag start product=" + PRODUCT + " device=" + deviceId
                    + " app=" + appVersion + " client=" + CLIENT_VERSION
                    + " build=" + Build.MODEL + "/" + Build.VERSION.RELEASE + " ===");
            pumpLogcat();
        });
        SCHED.scheduleWithFixedDelay(DiagLog::tick, 5_000L, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** 设置页手动「立即上传」：强制补传一次。 */
    public static void uploadNow() {
        if (app == null) return;
        SCHED.execute(() -> {
            writeMarker("=== manual upload requested ===");
            lastAttemptMs = System.currentTimeMillis();
            uploadDue(true, "manual");
        });
    }

    /** 供设置页展示的状态文案（不含凭据）。 */
    public static String status() {
        if (app == null) return "未启动";
        String last = lastUploadMs <= 0 ? "尚未上传" : new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
                .format(new Date(lastUploadMs));
        return String.format(Locale.US, "v2 · 设备 %s · 本地 %.1f MB · 上次 %s",
                deviceId, totalBytes() / 1024.0 / 1024.0, last);
    }

    /**
     * 立即记一条标记：logcat 同步输出，本地环形日志交给后台线程写。
     *
     * <p>为什么写盘必须异步：音量手势每 5% 一档，`append()` 是打开文件 → 写入 → 关闭
     * 再加两次目录扫描的同步 IO。放在主线程上（手势回调）就等于每一档都做一次磁盘操作，
     * 车机 eMMC 慢的时候直接把绘帧线程拖住，表现为「调音量时视频卡顿」。这里只入队，
     * 由单线程写盘；队列满时丢弃而不是阻塞调用方。</p>
     */
    public static void event(String message) {
        if (app == null) return;
        Log.i("CarFeedback", message);
        if (pendingMarkers.size() >= MAX_PENDING_MARKERS) return;
        pendingMarkers.offer(message);
        scheduleMarkerWrite();
    }

    /** 后台线程排空一批标记；失败不影响调用方。 */
    private static void scheduleMarkerWrite() {
        if (!writerScheduled.compareAndSet(false, true)) return;
        try {
            PUMP.execute(() -> {
                try {
                    for (int i = 0; i < 64; i++) {
                        String message = pendingMarkers.poll();
                        if (message == null) break;
                        writeMarker("EVENT " + message);
                    }
                } catch (Throwable ignored) {
                } finally {
                    writerScheduled.set(false);
                    if (!pendingMarkers.isEmpty()) scheduleMarkerWrite();
                }
            });
        } catch (Throwable error) {
            writerScheduled.set(false);
        }
    }

    /** 持久随机设备 ID：应用数据被清除前不变，重启/版本升级都不变。 */
    @SuppressLint("ApplySharedPref")
    private static String resolveDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("diag_identity", Context.MODE_PRIVATE);
        String saved = prefs.getString("device_id", "");
        if (saved != null && saved.length() >= 4 && saved.length() <= 64
                && saved.matches("[A-Za-z0-9_-]+")) return saved;
        String fresh = "tvbox-" + UUID.randomUUID().toString();
        prefs.edit().putString("device_id", fresh).commit();
        return fresh;
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
                if (line[line.length - 1] != '\n') out.write('\n');
            }
            for (File old : parts(dir)) {
                if (partIndex(old) <= index - MAX_PARTS) {
                    //noinspection ResultOfMethodCallIgnored
                    old.delete();
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "append failed: " + error.getClass().getSimpleName());
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

    // ---------------------------------------------------------------- 游标 / 批次持久化

    private static File cursorFile() { return new File(dir(), "cursor.txt"); }
    private static File pendingBody() { return new File(dir(), "pending.bin"); }
    private static File pendingMeta() { return new File(dir(), "pending.json"); }

    private static String loadCursor() {
        try {
            String saved = readAll(new FileInputStream(cursorFile())).trim();
            if (saved.matches("[0-9]+:[0-9]+")) {
                int part = Integer.parseInt(saved.substring(0, saved.indexOf(':')));
                File[] existing = parts(dir());
                if (existing.length > 0 && part < partIndex(existing[0])) {
                    return partIndex(existing[0]) + ":0";
                }
                return saved;
            }
        } catch (Throwable ignored) {
        }
        File[] existing = parts(dir());
        return existing.length > 0 ? partIndex(existing[0]) + ":0" : "1:0";
    }

    private static void saveCursor(String value) {
        try {
            persist(cursorFile(), value.getBytes(UTF8));
        } catch (Throwable error) {
            Log.w(TAG, "save cursor failed: " + error.getClass().getSimpleName());
        }
    }

    /** 直接落盘并 fsync；pending 与 cursor 都先写成功才允许发网络请求。 */
    private static void persist(File target, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(target, false)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = source.read(chunk)) > 0) out.write(chunk, 0, read);
            return new String(out.toByteArray(), UTF8);
        }
    }

    private static byte[] readBytes(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) out.write(chunk, 0, read);
            return out.toByteArray();
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(data);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                builder.append(String.format(Locale.US, "%02x", bytes[i] & 0xff));
            }
            return builder.toString();
        } catch (Throwable error) {
            return "";
        }
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    /** 从 {@code from} 位置往后数 {@code count} 字节，算出新的 {@code part:offset} 游标。 */
    private static String advanceCursor(String from, int count) {
        try {
            int basePart = Integer.parseInt(from.substring(0, from.indexOf(':')));
            long baseOffset = Long.parseLong(from.substring(from.indexOf(':') + 1));
            long left = count;
            File[] parts = parts(dir());
            if (parts.length == 0) return from;
            int lastPart = basePart;
            long lastOffset = baseOffset;
            for (File part : parts) {
                int idx = partIndex(part);
                if (idx < basePart) continue;
                long start = idx == basePart ? baseOffset : 0L;
                long available = part.length() - start;
                lastPart = idx;
                lastOffset = part.length();
                if (available <= 0) continue;
                if (left <= available) return idx + ":" + (start + left);
                left -= available;
            }
            return lastPart + ":" + lastOffset;
        } catch (Throwable error) {
            return from;
        }
    }

    /**
     * 组一批日志，落盘（body + meta）后再返回 true。已有 pending 时直接复用，
     * 不重新组批 —— 这正是「失败复用同一 batch / 同一请求体」。
     */
    private static synchronized boolean prepareBatch(String reason) {
        if (pendingBody().isFile() && pendingMeta().isFile() && pendingBody().length() > 0) return true;
        File[] parts = parts(dir());
        if (parts.length == 0) return false;
        String from = cursor;
        int fromPart;
        long fromOffset;
        try {
            fromPart = Integer.parseInt(from.substring(0, from.indexOf(':')));
            fromOffset = Long.parseLong(from.substring(from.indexOf(':') + 1));
        } catch (Exception error) {
            fromPart = partIndex(parts[0]);
            fromOffset = 0;
            from = fromPart + ":0";
        }
        if (fromPart < partIndex(parts[0])) {
            fromPart = partIndex(parts[0]);
            fromOffset = 0;
            from = fromPart + ":0";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        outer:
        for (File part : parts) {
            int idx = partIndex(part);
            if (idx < fromPart) continue;
            long skip = idx == fromPart ? Math.min(fromOffset, part.length()) : 0L;
            try (FileInputStream in = new FileInputStream(part)) {
                long left = skip;
                while (left > 0) {
                    long n = in.skip(left);
                    if (n <= 0) break;
                    left -= n;
                }
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    int room = MAX_RAW_LOG_BYTES - buffer.size();
                    if (room <= 0) break outer;
                    int take = Math.min(room, read);
                    buffer.write(chunk, 0, take);
                    if (take < read) break outer;
                }
            } catch (Throwable error) {
                Log.w(TAG, "read part failed: " + error.getClass().getSimpleName());
                return false;
            }
        }
        byte[] raw = buffer.toByteArray();
        int end = raw.length;
        while (end > 0 && raw[end - 1] != '\n') end--;
        if (end == 0) return false;
        byte[] logBytes = Arrays.copyOf(raw, end);
        String nextCursor = advanceCursor(from, end);
        if (nextCursor.equals(from)) return false;  // 没有推进就不组批，避免死循环
        String batch = "b-" + from.replace(':', '.') + "-" + nextCursor.replace(':', '.')
                + "-" + sha256Hex(logBytes);
        try {
            JSONObject envelope = new JSONObject();
            envelope.put("product", PRODUCT);
            envelope.put("device", deviceId);
            envelope.put("batch", batch);
            envelope.put("cursor", from);
            envelope.put("appVersion", appVersion);
            envelope.put("clientVersion", CLIENT_VERSION);
            envelope.put("reason", reason);
            envelope.put("rawLog", new String(logBytes, UTF8));
            byte[] body = gzip(envelope.toString().getBytes(UTF8));

            JSONObject meta = new JSONObject();
            meta.put("batch", batch);
            meta.put("cursor", from);
            meta.put("nextCursor", nextCursor);
            meta.put("reason", reason);
            meta.put("bytes", body.length);
            meta.put("sha256", sha256Hex(body));
            meta.put("createdAt", System.currentTimeMillis());
            // 请求体与元数据先落盘（fsync），再发网络；进程被杀也不丢。
            persist(pendingBody(), body);
            persist(pendingMeta(), meta.toString().getBytes(UTF8));
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "prepare batch failed: " + error.getClass().getSimpleName());
            clearPending();
            return false;
        }
    }

    private static void clearPending() {
        //noinspection ResultOfMethodCallIgnored
        pendingBody().delete();
        //noinspection ResultOfMethodCallIgnored
        pendingMeta().delete();
    }

    // ---------------------------------------------------------------- 上传调度

    private static void tick() {
        try {
            long now = System.currentTimeMillis();
            if (now - startMs < START_DELAY_MS) return;
            boolean due = drainNow
                    || (now - lastUploadMs >= UPLOAD_INTERVAL_MS
                        && now - lastAttemptMs >= RETRY_INTERVAL_MS);
            if (!due) return;
            lastAttemptMs = now;
            drainNow = false;
            uploadDue(false, "periodic");
        } catch (Throwable error) {
            Log.w(TAG, "tick failed: " + error.getClass().getSimpleName());
        }
    }

    private static void pumpLogcat() {
        Process process = null;
        try {
            Runtime.getRuntime().exec(new String[]{"logcat", "-c"}).waitFor();
        } catch (Throwable ignored) {
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
                }
            } catch (Throwable error) {
                Log.w(TAG, "logcat pump error: " + error.getClass().getSimpleName());
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

    /** 发送已持久化的 pending 批；只有严格判据全过才推进游标。 */
    private static synchronized void uploadDue(boolean force, String reason) {
        if (app == null) return;
        if (!prepareBatch(reason)) return;
        byte[] body;
        JSONObject meta;
        try {
            body = readBytes(pendingBody());
            meta = new JSONObject(readAll(new FileInputStream(pendingMeta())));
            if (!sha256Hex(body).equals(meta.optString("sha256", ""))) {
                Log.w(TAG, "pending checksum mismatch, drop and regroup");
                clearPending();
                return;
            }
        } catch (Throwable error) {
            Log.w(TAG, "pending unreadable: " + error.getClass().getSimpleName());
            clearPending();
            return;
        }
        final String batch = meta.optString("batch", "");
        final String sentCursor = meta.optString("cursor", "");
        final String nextCursor = meta.optString("nextCursor", "");
        if (batch.isEmpty() || nextCursor.isEmpty()) {
            clearPending();
            return;
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(UPLOAD_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Encoding", "gzip");
            connection.setRequestProperty("Authorization", "Bearer " + UPLOAD_TOKEN);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            String responseText;
            try {
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                responseText = stream == null ? "" : readAll(stream);
            } catch (Throwable error) {
                responseText = "";
            }
            if (code != 200) {
                Log.w(TAG, "upload rejected code=" + code + " batch kept=" + batch);
                return;  // pending 保留，下次原样重发
            }
            JSONObject result = new JSONObject(responseText);
            boolean accepted = result.optBoolean("accepted", false);
            boolean duplicate = result.optBoolean("duplicate", false);
            String id = result.optString("id", "");
            String rProduct = result.optString("product", "");
            String rDevice = result.optString("device", "");
            String rCursor = result.optString("cursor", "");
            String rBatch = result.optString("batch", "");
            long bytes = result.optLong("bytes", 0L);
            String recordedId = meta.optString("lastId", "");
            if (id.length() > 0 && !recordedId.equals(id)) {
                // 记下见过的 id，供 duplicate 场景做「同 id」比对；失败重试仍复用同一批。
                try {
                    meta.put("lastId", id);
                    persist(pendingMeta(), meta.toString().getBytes(UTF8));
                } catch (Throwable ignored) {
                }
            }
            boolean ok = accepted
                    && id.length() > 0
                    && PRODUCT.equals(rProduct)
                    && deviceId.equals(rDevice)
                    && sentCursor.equals(rCursor)
                    && bytes > 0
                    && (rBatch.isEmpty() || batch.equals(rBatch));
            if (ok && duplicate && !recordedId.isEmpty() && !recordedId.equals(id)) {
                ok = false;  // duplicate 但 id 不一致：不推进，继续原样重试
            }
            if (!ok) {
                Log.w(TAG, "upload response rejected accepted=" + accepted
                        + " id=" + (id.length() > 0) + " product=" + PRODUCT.equals(rProduct)
                        + " device=" + deviceId.equals(rDevice) + " cursor=" + sentCursor.equals(rCursor)
                        + " bytes=" + bytes + " batch kept=" + batch);
                return;
            }
            // 严格判据全过（含 duplicate=true）：推进游标、清 pending。
            cursor = nextCursor;
            saveCursor(nextCursor);
            clearPending();
            lastUploadMs = System.currentTimeMillis();
            writeMarker("upload ok v2 batch=" + batch + " duplicate=" + duplicate
                    + " bytes=" + bytes + " cursor=" + nextCursor);
            // 这一批读满说明还有积压：下一个 tick 立即续传，不等 2 分钟。
            if (bytes >= MAX_RAW_LOG_BYTES / 2) drainNow = true;
        } catch (Throwable error) {
            Log.w(TAG, "upload failed: " + error.getClass().getSimpleName()
                    + " batch kept=" + batch);
        } finally {
            if (connection != null) connection.disconnect();
        }
        if (!force) lastAttemptMs = System.currentTimeMillis();
    }
}
