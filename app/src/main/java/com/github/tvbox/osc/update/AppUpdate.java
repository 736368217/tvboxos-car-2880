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
import com.github.tvbox.osc.util.PlainHttp;
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
        /** 首选下载地址（已套上查询成功那个通道的前缀）。 */
        public final String apkUrl;
        /** 原始资产地址（不带前缀），下载失败换通道时以它为基准。 */
        public final String apkUrlRaw;
        public final String apkName;
        public final long apkSize;
        public final String channelLabel;
        public final String releasePage;

        Info(String tag, boolean hasUpdate, String localVersion, String remoteVersion, String notes,
             String apkUrl, String apkUrlRaw, String apkName, long apkSize, String channelLabel,
             String releasePage) {
            this.tag = tag;
            this.hasUpdate = hasUpdate;
            this.localVersion = localVersion;
            this.remoteVersion = remoteVersion;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.apkUrlRaw = apkUrlRaw;
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
            // 两轮：先用系统 DNS 直连（最快、最常见），全挂了再用 App 自己的网络栈
            // （带用户的 DoH / 代理规则）兜底 —— 有些网络只有后者能出去。
            for (int round = 0; round < 2; round++) {
                boolean plain = round == 0;
                for (UpdateVersion.Channel channel : channels) {
                    try {
                        Info info = query(channel, local, plain);
                        remember(info);
                        Log.i(TAG, "check ok channel=" + channel.label
                                + (plain ? "(系统DNS)" : "(应用网络)") + " force=" + force
                                + " local=" + local + " remote=" + info.remoteVersion
                                + " hasUpdate=" + info.hasUpdate);
                        post(() -> callback.onResult(info, null));
                        return;
                    } catch (Exception error) {
                        String reason = error.getClass().getSimpleName()
                                + (error.getMessage() == null ? "" : (": " + error.getMessage()));
                        failures.add(channel.label + (plain ? "" : "/app") + "=" + reason);
                        Log.w(TAG, "check failed channel=" + channel.label
                                + (plain ? "(系统DNS)" : "(应用网络)") + " : " + reason);
                    }
                }
            }
            Log.w(TAG, "check failed on every channel: " + failures);
            String message = failures.isEmpty()
                    ? "没有可用的更新通道"
                    : "更新检查失败（" + failures.size() + " 次尝试都不可达）：" + failures.get(0);
            post(() -> callback.onResult(null, message));
        });
    }

    private static Info query(UpdateVersion.Channel channel, String local, boolean plain) throws Exception {
        if (channel.web) {
            // Web 通道：不查 JSON API，靠 releases/latest 的 302 解析 tag。
            return queryWeb(channel, local);
        }
        OkHttpClient client = client(plain);
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
            return new Info(json.optString("tag_name"), false, local, local, "", null, null, null, 0,
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
        String apkUrlRaw = picked >= 0 ? urls[picked] : null;
        String apkUrl = apkUrlRaw == null ? null : channel.apply(apkUrlRaw);
        long apkSize = picked >= 0 ? sizes[picked] : 0L;
        boolean hasUpdate = UpdateVersion.hasUpdate(local, remoteVersion);
        String releasePage = "https://github.com/" + OWNER + "/" + REPO + "/releases/tag/" + tag;
        if (hasUpdate && apkUrl == null) {
            throw new IOException("Release " + tag + " 没有可用的 APK 资产");
        }
        return new Info(tag, hasUpdate, local, remoteVersion, notes, apkUrl, apkUrlRaw, apkName, apkSize,
                channel.label, releasePage);
    }

    /**
     * Web 通道：GET {@code releases/latest}（不跟随重定向），从 302 Location 解析最新 tag。
     *
     * <p>为什么绕这一道：车机网络下 api.github.com 和多数镜像的 API 前缀都不通
     * （gh-proxy 系镜像只代理 github.com 域名），但 {@code github.com/.../releases/latest}
     * 会 302 到 {@code /releases/tag/<tag>} —— 代理了 github.com 的镜像几乎都活着。
     * 拿到 tag 后按本仓库资产命名规范反推 APK 文件名；下载端有「内容过小/包名校验」兜底，
     * 推测的文件名不对只会 404 换通道，不会装错包。</p>
     */
    private static Info queryWeb(UpdateVersion.Channel channel, String local) throws Exception {
        OkHttpClient client = PlainHttp.systemDnsNoRedirect();
        Request request = new Request.Builder()
                .url(channel.apiUrl)
                .header("User-Agent", "TVBox-Car/" + local)
                .build();
        String location = null;
        try (Response response = client.newCall(request).execute()) {
            location = response.header("Location");
            Log.i(TAG, "web probe " + channel.label + " code=" + response.code() + " location=" + location);
            if (location == null || location.length() == 0) {
                // 有的镜像直接跟随重定向返回 200 页面：从 HTML 里挖 tag
                ResponseBody body = response.body();
                if (response.code() == 200 && body != null) {
                    location = extractTagFromHtml(body.string());
                }
            }
        }
        String tag = UpdateVersion.tagFromRedirect(location);
        if (tag == null || tag.length() == 0) {
            throw new IOException("releases/latest 未返回 tag（location=" + location + "）");
        }
        boolean hasUpdate = UpdateVersion.hasUpdate(local, tag);
        String[] names = UpdateVersion.assetNamesForTag(tag);
        String apkName = names.length > 0 ? names[0] : null;
        String apkUrlRaw = apkName == null ? null
                : "https://github.com/" + OWNER + "/" + REPO + "/releases/download/" + tag + "/" + apkName;
        String apkUrl = apkUrlRaw == null ? null : channel.apply(apkUrlRaw);
        String releasePage = "https://github.com/" + OWNER + "/" + REPO + "/releases/tag/" + tag;
        return new Info(tag, hasUpdate, local, tag, "", apkUrl, apkUrlRaw, apkName, 0L,
                channel.label, releasePage);
    }

    /** 从 releases/latest 的 HTML 里挖第一个 {@code /releases/tag/<tag>}（镜像跟随重定向时的兜底）。 */
    private static String extractTagFromHtml(String html) {
        if (html == null || html.length() == 0) return null;
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("/releases/tag/([A-Za-z0-9._\\-]+)").matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ---------------- 下载 ----------------

    public static Task download(final Context context, final Info info, final DownloadCallback callback) {
        final Task task = new Task();
        final Context appContext = context.getApplicationContext();
        if (info == null || (info.apkUrl == null && info.apkUrlRaw == null)) {
            post(() -> callback.onError("没有可下载的安装包"));
            return task;
        }
        POOL.execute(() -> {
            File target = apkFile(appContext, info);
            File partial = new File(target.getAbsolutePath() + ".part");
            String raw = info.apkUrlRaw != null ? info.apkUrlRaw : info.apkUrl;
            String preferred = prefixOf(info.apkUrl, info.apkUrlRaw);
            String mirror = Hawk.get(HawkConfig.UPDATE_MIRROR, "");
            String[] candidates = UpdateVersion.downloadCandidates(raw, preferred, mirror);
            Log.i(TAG, "download start size=" + info.apkSize + " -> " + target
                    + " candidates=" + candidates.length + " first=" + (candidates.length > 0 ? candidates[0] : "-"));
            delete(target);
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < candidates.length; i++) {
                String url = candidates[i];
                if (task.isCancelled()) {
                    delete(partial);
                    post(() -> callback.onError("已取消下载"));
                    return;
                }
                try {
                    Log.i(TAG, "download try " + (i + 1) + "/" + candidates.length + " url=" + url);
                    transfer(task, url, partial, info, target, callback, i < candidates.length - 1);
                    if (partial.length() < MIN_APK_BYTES) {
                        delete(partial);
                        throw new IOException("内容过小（" + (partial.exists() ? partial.length() : 0) + " 字节），可能是错误页");
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
                            + " package=" + appContext.getPackageName() + " url=" + url);
                    post(() -> callback.onDone(target));
                    return;
                } catch (Exception error) {
                    delete(partial);
                    String message = error.getClass().getSimpleName()
                            + (error.getMessage() == null ? "" : (": " + error.getMessage()));
                    failures.add(message);
                    Log.w(TAG, "download failed " + (i + 1) + "/" + candidates.length + " : " + message);
                }
            }
            String summary = failures.isEmpty() ? "没有可用地址" : failures.get(0);
            Log.w(TAG, "download failed on every candidate (" + failures.size() + "): " + failures);
            post(() -> callback.onError("下载失败（" + failures.size() + " 个地址都不可用）：" + summary));
        });
        return task;
    }

    /** 把资产地址前缀拆出来，供下载候选排序用。 */
    private static String prefixOf(String urlWithPrefix, String rawUrl) {
        if (urlWithPrefix == null || rawUrl == null) return "";
        if (!urlWithPrefix.endsWith(rawUrl)) return "";
        String prefix = urlWithPrefix.substring(0, urlWithPrefix.length() - rawUrl.length());
        return prefix;
    }

    // 慢速切换：GitHub 资产域名常被限速到几十 KB/s，而它「不报错」，用户只会看着进度条
    // 十几分钟不动。所以前几个候选若在观察窗内太慢就主动放弃，把机会给镜像。
    private static final long SLOW_WINDOW_MS = 20000L;
    // 观察窗内至少要拿到总大小的这个比例（约 125 KB/s @50MB），否则判定为慢速。
    private static final double SLOW_MIN_FRACTION = 0.05;
    // 最后一个候选是兜底：允许它慢速跑完，不再放弃。
    private static final long SLOW_MIN_BYTES = 2L * 1024 * 1024;

    /** 单地址下载到 partial；取消/超时/太慢会抛异常，由调用方换下一个地址。 */
    private static void transfer(Task task, String url, File partial, Info info, File target,
                                 DownloadCallback callback, boolean allowSlowAbort) throws IOException {
        OkHttpClient client = client(true);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "TVBox-Car/" + info.localVersion)
                .build();
        long lastPost = 0L;
        long startedAt = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            long total = body.contentLength() > 0 ? body.contentLength() : info.apkSize;
            long slowTarget = Math.max(SLOW_MIN_BYTES, (long) (total * SLOW_MIN_FRACTION));
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0L;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    if (task.isCancelled()) throw new IOException("已取消");
                    out.write(buffer, 0, read);
                    done += read;
                    long now = System.currentTimeMillis();
                    if (allowSlowAbort && now - startedAt >= SLOW_WINDOW_MS && done < slowTarget) {
                        out.flush();
                        long kbps = done * 1000L / Math.max(1L, now - startedAt) / 1024L;
                        throw new IOException("速度过慢（" + kbps + " KB/s，"
                                + Math.max(1, (now - startedAt) / 1000L) + " 秒仅 " + done + " 字节）");
                    }
                    if (now - lastPost >= PROGRESS_INTERVAL_MS) {
                        lastPost = now;
                        final long posted = done;
                        final long postedTotal = total;
                        post(() -> callback.onProgress(posted, postedTotal));
                    }
                }
            }
        }
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
    /**
     * 两类客户端：
     * <ul>
     *   <li>{@code plain=true}：<b>系统 DNS</b> + 短超时。GitHub 域名用这一条最稳 ——
     *       App 自己的 {@link OkHttp#client} 带用户配置的 DoH/代理规则，实测会出现
     *       「系统 ping 能解析、OkHttp 却 UnknownHostException」，而且每个域名要卡 20 秒。</li>
     *   <li>{@code plain=false}：沿用 App 网络栈，给必须走 DoH/代理才能出网的网络兜底。</li>
     * </ul>
     */
    private static OkHttpClient client(boolean plain) {
        if (!plain) {
            // App 网络栈（OkDns/DoH + 用户代理规则）。同样给 DNS 加超时，
            // 某些网络下 netd 会卡满 20 秒才报 UnknownHostException。
            return AppStackClient.HOLDER;
        }
        // 系统 DNS 直连：绕开 App 栈在部分车机上 DNS 全挂的问题，实现统一收在 PlainHttp。
        return PlainHttp.systemDns();
    }

    private static final class AppStackClient {
        static final OkHttpClient HOLDER = PlainHttp.withDnsTimeout(OkHttp.client(TIMEOUT_MS));
    }


    private static void post(Runnable runnable) {
        MAIN.post(runnable);
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "无法删除 " + file);
        }
    }
}
