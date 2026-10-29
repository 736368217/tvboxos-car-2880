package com.github.tvbox.osc.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 应用内更新：查 GitHub Release → 下载 APK → 校验 → 唤起安装器。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>版本判断走 {@link UpdateVersion}</b>：本项目迭代号 {@code rNN} 与 versionCode 不同步，
 *       必须按迭代号比，否则 r44→r48 会被判成「已是最新」。</li>
 *   <li><b>多通道</b>：自定义镜像 → 直连 → ghfast → ghproxy。壳里常见 GitHub 不可达，
 *       逐个试，第一个成功的通道同时决定下载地址的前缀（保证下载走同一条路）。</li>
 *   <li><b>安装前校验</b>：包名、versionCode、签名摘要三项都对得上才交给安装器。
 *       签名不一致会直接 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，提前拦掉比装到一半失败好。</li>
 *   <li><b>全部后台线程</b>，结果回主线程；进度做了节流，避免刷爆 UI。</li>
 * </ul>
 *
 * <p>自查：{@code adb logcat -s AppUpdate}</p>
 */
public final class AppUpdate {

    public static final String TAG = "AppUpdate";

    /** 更新来源仓库（本项目自身的 Release）。 */
    private static final String OWNER = "736368217";
    private static final String REPO = "tvboxos-car-2880";

    /** 自动检查的最小间隔；启动检查受它节流。 */
    private static final long AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final int TIMEOUT_MS = 15000;
    /** 进度回调节流。 */
    private static final long PROGRESS_INTERVAL_MS = 250L;
    /** 小于这个体积的「APK」基本是错误页，直接判失败。 */
    private static final long MIN_APK_BYTES = 1024 * 1024L;

    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppUpdate() {
    }

    public interface Callback {
        /** error 为 null 表示查询成功；{@code info.hasUpdate} 表示是否有更新。 */
        void onResult(Info info, String error);
    }

    public interface DownloadCallback {
        void onProgress(long bytes, long total);

        /** 下载并校验完成，可以安装。 */
        void onDone(File apk);

        void onError(String message);
    }

    /** 一次查询的结果快照。 */
    public static final class Info {
        public final String tag;
        public final boolean hasUpdate;
        public final String localVersion;
        public final String remoteVersion;
        public final String notes;
        public final String apkUrl;
        public final String apkName;
        public final long apkSize;
        public final String channelLabel;
        public final String releasePage;

        Info(String tag, boolean hasUpdate, String localVersion, String remoteVersion, String notes,
             String apkUrl, String apkName, long apkSize, String channelLabel, String releasePage) {
            this.tag = tag;
            this.hasUpdate = hasUpdate;
            this.localVersion = localVersion;
            this.remoteVersion = remoteVersion;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.apkName = apkName;
            this.apkSize = apkSize;
            this.channelLabel = channelLabel;
            this.releasePage = releasePage;
        }
    }

    /** 下载任务句柄。 */
    public static final class Task {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    // ---------------- 节流 ----------------

    /** 启动时的静默检查是否该跑（距上次检查不足间隔就跳过）。 */
    public static boolean shouldAutoCheck() {
        if (!Hawk.get(HawkConfig.UPDATE_AUTO_CHECK, true)) return false;
        long last = Hawk.get(HawkConfig.UPDATE_LAST_CHECK, 0L);
        long now = System.currentTimeMillis();
        return now - last >= AUTO_CHECK_INTERVAL_MS;
    }

    public static void markChecked() {
        Hawk.put(HawkConfig.UPDATE_LAST_CHECK, System.currentTimeMillis());
    }

    /** 记住最近一次成功的检查结果，供设置页在无网络时也能显示状态。 */
    private static void remember(Info info) {
        markChecked();
        if (info == null) return;
        Hawk.put(HawkConfig.UPDATE_LAST_TAG, info.tag == null ? "" : info.tag);
        Hawk.put(HawkConfig.UPDATE_HAS_UPDATE, info.hasUpdate);
    }

    /** 用户选择「跳过此版本」时记下 tag，之后不再自动提示该版本。 */
    public static void skip(String tag) {
        if (tag != null && tag.length() > 0) Hawk.put(HawkConfig.UPDATE_SKIP_TAG, tag);
    }

    public static boolean isSkipped(String tag) {
        String skipped = Hawk.get(HawkConfig.UPDATE_SKIP_TAG, "");
        return tag != null && tag.equals(skipped);
    }

    public static String localVersion(Context context) {
        String name = DefaultConfig.getAppVersionName(context);
        return name == null ? "" : name;
    }

    // ---------------- 查询 ----------------

    /**
     * 查询最新 Release。{@code force} 只影响日志，节流由调用方用 {@link #shouldAutoCheck()} 决定。
     */
    public static void check(final Context context, final boolean force, final Callback callback) {
        final Context appContext = context.getApplicationContext();
        POOL.execute(() -> {
            String local = localVersion(appContext);
            String mirror = Hawk.get(HawkConfig.UPDATE_MIRROR, "");
            UpdateVersion.Channel[] channels = UpdateVersion.channels(OWNER, REPO, mirror);
            List<String> failures = new ArrayList<>();
            for (UpdateVersion.Channel channel : channels) {
                try {
                    Info info = query(appContext, channel, local);
                    remember(info);
                    Log.i(TAG, "check ok channel=" + channel.label + " force=" + force
                            + " local=" + local + " remote=" + info.remoteVersion
                            + " hasUpdate=" + info.hasUpdate);
                    post(() -> callback.onResult(info, null));
                    return;
                } catch (Exception error) {
                    String reason = error.getClass().getSimpleName()
                            + (error.getMessage() == null ? "" : (": " + error.getMessage()));
                    failures.add(channel.label + "=" + reason);
                    Log.w(TAG, "check failed channel=" + channel.label + " : " + reason);
                }
            }
            Log.w(TAG, "check failed on every channel: " + failures);
            String message = failures.isEmpty()
                    ? "没有可用的更新通道"
                    : "更新检查失败（" + failures.size() + " 个通道都不可达）：" + failures.get(0);
            post(() -> callback.onResult(null, message));
        });
    }

    private static Info query(Context context, UpdateVersion.Channel channel, String local) throws Exception {
        OkHttpClient client = OkHttp.client(TIMEOUT_MS);
        Request request = new Request.Builder()
                .url(channel.apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TVBox-Car/" + local)
                .build();
        String body;
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            body = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
        }
        JSONObject json = new JSONObject(body);
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
            // 预发布/草稿不作为更新来源，避免把测试包推给用户
            return new Info(json.optString("tag_name"), false, local, local, "", null, null, 0,
                    channel.label, null);
        }
        // 只用 tag 判版本：Release 标题里可能顺带提到别的 rNN（例如「修 r48 的问题」），
        // 拿标题参与比较会解析出错误的迭代号。
        String tag = json.optString("tag_name", "");
        String remoteVersion = tag;
        String notes = UpdateVersion.cleanNotes(json.optString("body", ""), 2000);
        JSONArray assets = json.optJSONArray("assets");
        int count = assets == null ? 0 : assets.length();
        String[] names = new String[count];
        long[] sizes = new long[count];
        String[] urls = new String[count];
        for (int i = 0; i < count; i++) {
            JSONObject asset = assets.getJSONObject(i);
            names[i] = asset.optString("name", "");
            sizes[i] = asset.optLong("size", 0L);
            urls[i] = asset.optString("browser_download_url", "");
        }
        int picked = UpdateVersion.pickApk(names, sizes);
        String apkName = picked >= 0 ? names[picked] : null;
        String apkUrl = picked >= 0 ? channel.apply(urls[picked]) : null;
        long apkSize = picked >= 0 ? sizes[picked] : 0L;
        boolean hasUpdate = UpdateVersion.hasUpdate(local, remoteVersion);
        String releasePage = "https://github.com/" + OWNER + "/" + REPO + "/releases/tag/" + tag;
        if (hasUpdate && apkUrl == null) {
            throw new IOException("Release " + tag + " 没有可用的 APK 资产");
        }
        return new Info(tag, hasUpdate, local, remoteVersion, notes, apkUrl, apkName, apkSize,
                channel.label, releasePage);
    }

    // ---------------- 下载 ----------------

    public static Task download(final Context context, final Info info, final DownloadCallback callback) {
        final Task task = new Task();
        final Context appContext = context.getApplicationContext();
        if (info == null || info.apkUrl == null) {
            post(() -> callback.onError("没有可下载的安装包"));
            return task;
        }
        POOL.execute(() -> {
            File target = apkFile(appContext, info);
            File partial = new File(target.getAbsolutePath() + ".part");
            Log.i(TAG, "download start url=" + info.apkUrl + " size=" + info.apkSize + " -> " + target);
            try {
                delete(partial);
                delete(target);
                OkHttpClient client = OkHttp.client(TIMEOUT_MS);
                Request request = new Request.Builder()
                        .url(info.apkUrl)
                        .header("User-Agent", "TVBox-Car/" + info.localVersion)
                        .build();
                long lastPost = 0L;
                long total;
                try (Response response = client.newCall(request).execute()) {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        throw new IOException("HTTP " + response.code());
                    }
                    total = body.contentLength() > 0 ? body.contentLength() : info.apkSize;
                    try (InputStream in = body.byteStream();
                         FileOutputStream out = new FileOutputStream(partial)) {
                        byte[] buffer = new byte[64 * 1024];
                        long done = 0L;
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            if (task.isCancelled()) {
                                out.close();
                                delete(partial);
                                Log.i(TAG, "download cancelled at " + done + " bytes");
                                post(() -> callback.onError("已取消下载"));
                                return;
                            }
                            out.write(buffer, 0, read);
                            done += read;
                            long now = System.currentTimeMillis();
                            if (now - lastPost >= PROGRESS_INTERVAL_MS) {
                                lastPost = now;
                                final long posted = done;
                                final long postedTotal = total;
                                post(() -> callback.onProgress(posted, postedTotal));
                            }
                        }
                    }
                }
                if (partial.length() < MIN_APK_BYTES) {
                    delete(partial);
                    throw new IOException("下载内容过小（" + partial.length() + " 字节），可能是错误页");
                }
                if (!partial.renameTo(target)) {
                    throw new IOException("无法重命名到 " + target.getName());
                }
                post(() -> callback.onProgress(target.length(), target.length()));
                String problem = verify(appContext, target);
                if (problem != null) {
                    delete(target);
                    Log.w(TAG, "verify failed: " + problem);
                    post(() -> callback.onError(problem));
                    return;
                }
                Log.i(TAG, "download done file=" + target + " bytes=" + target.length()
                        + " package=" + appContext.getPackageName() + " channel=" + info.channelLabel);
                post(() -> callback.onDone(target));
            } catch (Exception error) {
                delete(partial);
                String message = error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : (": " + error.getMessage()));
                Log.w(TAG, "download failed: " + message);
                post(() -> callback.onError("下载失败：" + message));
            }
        });
        return task;
    }

    private static File apkFile(Context context, Info info) {
        File dir = context.getExternalFilesDir("apk");
        if (dir == null) dir = new File(context.getCacheDir(), "apk");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "无法创建下载目录 " + dir);
        String name = info.apkName != null ? info.apkName : ("TVBox-" + info.tag + ".apk");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) name = name + ".apk";
        return new File(dir, name);
    }

    // ---------------- 校验 ----------------

    /** 返回 null 表示可以安装；否则返回给用户看的原因。 */
    public static String verify(Context context, File apk) {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (archive == null) return "安装包无法解析，已删除下载文件";
        if (!context.getPackageName().equals(archive.packageName)) {
            return "安装包包名不匹配（" + archive.packageName + "），已删除";
        }
        PackageInfo installed;
        try {
            installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException error) {
            return "读取当前安装信息失败";
        }
        if (archive.versionCode <= installed.versionCode) {
            return "安装包版本（" + archive.versionCode + "）不高于当前版本（"
                    + installed.versionCode + "），已删除";
        }
        String archiveSign = signatureDigest(archive.signatures);
        String installedSign = signatureDigest(installed.signatures);
        if (archiveSign == null || !archiveSign.equals(installedSign)) {
            return "安装包签名与当前应用不一致，无法覆盖安装，已删除";
        }
        Log.i(TAG, "verify ok package=" + archive.packageName + " versionCode=" + archive.versionCode
                + " sig=" + shortDigest(archiveSign));
        return null;
    }

    private static String signatureDigest(Signature[] signatures) {
        if (signatures == null || signatures.length == 0) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signatures[0].toByteArray());
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) builder.append(String.format("%02X", value));
            return builder.toString();
        } catch (Exception error) {
            return null;
        }
    }

    private static String shortDigest(String digest) {
        return digest == null || digest.length() < 16 ? digest : digest.substring(0, 16) + "…";
    }

    // ---------------- 安装 ----------------

    /** 是否需要用户去系统里打开「安装未知应用」权限。 */
    public static boolean needsInstallPermission(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls();
    }

    /** 跳到「安装未知应用」授权页。 */
    public static void openInstallPermissionSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.i(TAG, "opened unknown-app-sources settings");
        } catch (Exception error) {
            Log.w(TAG, "无法打开安装权限设置: " + error);
        }
    }

    /**
     * 唤起系统安装器。返回 false 表示缺「安装未知应用」权限（调用方应提示用户）。
     */
    public static boolean install(Context context, File apk) {
        if (apk == null || !apk.exists()) return false;
        if (needsInstallPermission(context)) {
            openInstallPermissionSettings(context);
            return false;
        }
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            Log.i(TAG, "install launched file=" + apk.getName() + " uri=" + uri);
            return true;
        } catch (Exception error) {
            Log.w(TAG, "install failed: " + error);
            return false;
        }
    }

    /** 打开 Release 页面（浏览器手动下载，作为应用内下载失败时的兜底）。 */
    public static void openReleasePage(Context context, Info info) {
        if (info == null || info.releasePage == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.releasePage));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.i(TAG, "opened release page " + info.releasePage);
        } catch (Exception error) {
            Log.w(TAG, "无法打开浏览器: " + error);
        }
    }

    // ---------------- 小工具 ----------------

    private static void post(Runnable runnable) {
        MAIN.post(runnable);
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "无法删除 " + file);
        }
    }
}
