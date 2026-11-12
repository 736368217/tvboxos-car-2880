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
 *       逐个试，第一个成功的通道同时决定下载地址的前缀（保证下载走同一条路）。
 *       r81 起这是唯一主通道；官方 dl.z736.vip 已下线，见 {@code OFFICIAL_CHANNEL_ENABLED}。</li>
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

    /**
     * 官方发布通道：阿里云发布根（SMB 上传，公网由 dl.z736.vip 提供）。
     *
     * <p>r81 起停用：服务器 121.40.69.97 的 22 / 443 / 4450 全部不可达，
     * {@code <软件名>/latest.json} 已无人维护。更新检查改为直接走 GitHub Release
     * 多通道，不再先等一台死服务器超时。解析与下载代码保留，若以后换回自建发布根，
     * 把 {@link #OFFICIAL_CHANNEL_ENABLED} 打开即可恢复「官方 → GitHub」旧顺序。</p>
     */
    private static final String OFFICIAL_BASE = "https://dl.z736.vip/";
    private static final String OFFICIAL_HOST = "dl.z736.vip";
    private static final String OFFICIAL_SOFTWARE = "tvbox-car";
    /** r81: 官方通道已下线；置 true 可恢复旧顺序。 */
    private static final boolean OFFICIAL_CHANNEL_ENABLED = false;

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
        /** 官方清单给出的 SHA-256（大写十六进制）；GitHub 通道为 null。 */
        public final String sha256;

        Info(String tag, boolean hasUpdate, String localVersion, String remoteVersion, String notes,
             String apkUrl, String apkUrlRaw, String apkName, long apkSize, String channelLabel,
             String releasePage, String sha256) {
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
            this.sha256 = sha256;
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
            List<String> failures = new ArrayList<>();

            // ① 官方通道（r81 起默认关闭，见 OFFICIAL_CHANNEL_ENABLED）。
            //    服务器已下线，检查更新直接走 GitHub Release 多通道。
            Info official = null;
            if (OFFICIAL_CHANNEL_ENABLED) {
                try {
                    final Info officialResult = queryOfficial(local);
                    if (officialResult.hasUpdate) {
                        remember(officialResult);
                        Log.i(TAG, "check ok channel=" + officialResult.channelLabel + " force=" + force
                                + " local=" + local + " remote=" + officialResult.remoteVersion
                                + " hasUpdate=true sha256=" + (officialResult.sha256 != null));
                        post(() -> callback.onResult(officialResult, null));
                        return;
                    }
                    official = officialResult;
                    Log.i(TAG, "official up to date local=" + local
                            + " remote=" + officialResult.remoteVersion);
                } catch (Exception error) {
                    String reason = describe(error);
                    failures.add("官方=" + reason);
                    Log.w(TAG, "official check failed: " + reason);
                }
            }
            UpdateVersion.Channel[] channels = UpdateVersion.channels(OWNER, REPO, mirror);
            // 两轮：先用系统 DNS 直连（最快、最常见），全挂了再用 App 自己的网络栈
            // （带用户的 DoH / 代理规则）兜底 —— 有些网络只有后者能出去。
            boolean githubLatest = false;
            for (int round = 0; round < 2 && !githubLatest; round++) {
                boolean plain = round == 0;
                for (UpdateVersion.Channel channel : channels) {
                    try {
                        Info info = query(channel, local, plain);
                        if (!info.hasUpdate && official != null) {
                            // 官方清单可达且说自己是最新：它带 sha256、与发布源一致，优先采信。
                            githubLatest = true;
                            break;
                        }
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
            if (OFFICIAL_CHANNEL_ENABLED) {
                // ③ 官方通道再试一次：冷启动/刚联网时系统 DNS 往往要好几秒才通（实测 MuMu 首次
                //    所有域名全超时、十几秒后同一域名秒回），一次失败不代表官方不可达。此时
                //    DNS 已热，再给官方一次机会，别把「有新版」误报成「已是最新」。
                if (official == null) {
                    try {
                        final Info retry = queryOfficial(local);
                        if (retry.hasUpdate) {
                            remember(retry);
                            Log.i(TAG, "check ok channel=" + retry.channelLabel + "(retry) force=" + force
                                    + " local=" + local + " remote=" + retry.remoteVersion
                                    + " hasUpdate=true sha256=" + (retry.sha256 != null));
                            post(() -> callback.onResult(retry, null));
                            return;
                        }
                        official = retry;
                        Log.i(TAG, "official retry up to date local=" + local
                                + " remote=" + retry.remoteVersion);
                    } catch (Exception error) {
                        Log.w(TAG, "official retry failed: " + describe(error));
                    }
                }
                final Info officialFinal = official;
                if (officialFinal != null) {
                    remember(officialFinal);
                    Log.i(TAG, "check ok channel=" + officialFinal.channelLabel + " local=" + local
                            + " remote=" + officialFinal.remoteVersion + " hasUpdate=false");
                    post(() -> callback.onResult(officialFinal, null));
                    return;
                }
            }
            Log.w(TAG, "check failed on every channel: " + failures);
            String message = failures.isEmpty()
                    ? "没有可用的更新通道"
                    : "更新检查失败（" + failures.size() + " 次尝试都不可达）：" + failures.get(0);
            post(() -> callback.onResult(null, message));
        });
    }

    /** 官方清单地址（发布脚本生成，更新器轮询它）。 */
    public static String officialManifestUrl() {
        return OFFICIAL_BASE + OFFICIAL_SOFTWARE + "/latest.json";
    }

    /**
     * 查官方清单：先系统 DNS，再 App 网络栈（有些网络只有后者能出去）。
     *
     * <p>不能复用 GitHub 那套 gh 镜像前缀：那些镜像只代理 github.com / api.github.com，
     * 套到 dl.z736.vip 上必然 404。</p>
     */
    private static Info queryOfficial(String local) throws Exception {
        Exception last = null;
        for (int round = 0; round < 2; round++) {
            boolean plain = round == 0;
            OkHttpClient client = plain ? PlainHttp.systemDns()
                    : PlainHttp.withDnsTimeout(OkHttp.client(TIMEOUT_MS));
            try {
                return queryOfficialOnce(client, local, plain);
            } catch (Exception error) {
                last = error;
                Log.w(TAG, "official " + (plain ? "system-dns" : "app-stack")
                        + " failed: " + describe(error));
            }
        }
        throw last == null ? new IOException("官方清单不可用") : last;
    }

    private static Info queryOfficialOnce(OkHttpClient client, String local, boolean plain) throws Exception {
        Request request = new Request.Builder()
                .url(officialManifestUrl())
                .header("Accept", "application/json")
                .header("User-Agent", "TVBox-Car/" + local)
                .header("Cache-Control", "no-cache")
                .build();
        String body;
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            body = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
        }
        OfficialManifest manifest = OfficialManifest.parse(body, OFFICIAL_BASE);
        boolean hasUpdate = UpdateVersion.hasUpdate(local, manifest.version);
        String tag = manifest.version.startsWith("v") ? manifest.version : "v" + manifest.version;
        if (hasUpdate && (manifest.apkUrl == null || manifest.apkUrl.length() == 0)) {
            throw new IOException("官方清单 " + manifest.version + " 没有下载地址");
        }
        String label = plain ? "官方(阿里云)" : "官方(应用网络)";
        return new Info(tag, hasUpdate, local, manifest.version,
                manifest.notes == null ? "" : manifest.notes.trim(),
                manifest.apkUrl, manifest.apkUrl, manifest.apkName, manifest.apkSize, label,
                OFFICIAL_BASE + OFFICIAL_SOFTWARE + "/", manifest.sha256);
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
                    channel.label, null, null);
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
                channel.label, releasePage, null);
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
                channel.label, releasePage, null);
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
            String[] candidates = isOfficialUrl(raw)
                    ? new String[]{info.apkUrl != null ? info.apkUrl : raw}
                    : UpdateVersion.downloadCandidates(raw, preferred, mirror);
            Log.i(TAG, "download start size=" + info.apkSize + " -> " + target
                    + " candidates=" + candidates.length + " first=" + (candidates.length > 0 ? candidates[0] : "-"));
            delete(target);
            List<String> failures = new ArrayList<>();
            // 多轮：弱网下单个地址断流很常见，第二轮会把没下完的部分用 Range 续上，
            // 而不是像以前那样一失败就删掉重下 50 MB。
            for (int pass = 0; pass < DOWNLOAD_PASSES; pass++) {
                for (int i = 0; i < candidates.length; i++) {
                    String url = candidates[i];
                    if (task.isCancelled()) {
                        post(() -> callback.onError("已取消下载（已下载部分保留，可继续）"));
                        return;
                    }
                    long already = partial.exists() ? partial.length() : 0L;
                    // 已经拿到完整体积就不用再请求了
                    if (info.apkSize > 0 && already >= info.apkSize) break;
                    try {
                        Log.i(TAG, "download try pass=" + (pass + 1) + " " + (i + 1) + "/" + candidates.length
                                + " resume_from=" + already + " url=" + url);
                        transfer(task, url, partial, info, target, callback,
                                i < candidates.length - 1 || pass < DOWNLOAD_PASSES - 1);
                        if (partial.length() < MIN_APK_BYTES) {
                            delete(partial);
                            throw new IOException("内容过小（" + (partial.exists() ? partial.length() : 0) + " 字节），可能是错误页");
                        }
                        if (!partial.renameTo(target)) {
                            throw new IOException("无法重命名到 " + target.getName());
                        }
                        post(() -> callback.onProgress(target.length(), target.length()));
                        if (info.sha256 != null && info.sha256.length() == 64) {
                            String actual = sha256(target);
                            if (!actual.equalsIgnoreCase(info.sha256)) {
                                delete(target);
                                delete(partial);
                                Log.w(TAG, "sha256 mismatch expected=" + info.sha256
                                        + " actual=" + actual);
                                post(() -> callback.onError("安装包校验失败（SHA-256 不一致），已删除，请重试"));
                                return;
                            }
                            Log.i(TAG, "sha256 ok " + actual);
                        }
                        String problem = verify(appContext, target);
                        if (problem != null) {
                            delete(target);
                            // 连半成品一起删：包名/签名/版本对不上时，续传也补不回来。
                            delete(partial);
                            Log.w(TAG, "verify failed: " + problem);
                            post(() -> callback.onError(problem));
                            return;
                        }
                        post(() -> callback.onDone(target));
                        return;
                    } catch (Exception error) {
                        String message = error.getClass().getSimpleName()
                                + (error.getMessage() == null ? "" : (": " + error.getMessage()));
                        failures.add(message);
                        Log.w(TAG, "download failed pass=" + (pass + 1) + " " + (i + 1) + "/" + candidates.length
                                + " kept=" + (partial.exists() ? partial.length() : 0) + " : " + message);
                    }
                }
            }
            String summary = failures.isEmpty() ? "没有可用地址" : failures.get(0);
            Log.w(TAG, "download failed on every candidate (" + failures.size() + "): " + failures
                    + " kept=" + (partial.exists() ? partial.length() : 0));
            final long kept = partial.exists() ? partial.length() : 0L;
            post(() -> callback.onError("下载失败（" + failures.size() + " 次尝试都不可用）：" + summary
                    + (kept > 0 ? "。已保留 " + (kept / 1024 / 1024) + " MB，重试会接着下。" : "")));
        });
        return task;
    }

    /** 官方地址（dl.z736.vip）不能套 gh 镜像前缀：镜像只代理 github，套上去必然 404。 */
    private static boolean isOfficialUrl(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains("://" + OFFICIAL_HOST + "/");
    }

    /** 流式计算文件 SHA-256（大写十六进制），用于比对官方清单。 */
    private static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("SHA-256 不可用");
        }
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(64);
        for (byte value : hash) builder.append(String.format("%02X", value));
        return builder.toString();
    }

    /** 把资产地址前缀拆出来，供下载候选排序用。 */
    private static String prefixOf(String urlWithPrefix, String rawUrl) {
        if (urlWithPrefix == null || rawUrl == null) return "";
        if (!urlWithPrefix.endsWith(rawUrl)) return "";
        String prefix = urlWithPrefix.substring(0, urlWithPrefix.length() - rawUrl.length());
        return prefix;
    }

    /** 整份清单最多跑几轮（第二轮靠 Range 续传补齐断流的部分）。 */
    private static final int DOWNLOAD_PASSES = 2;

    // 慢速切换：GitHub 资产域名常被限速到几十 KB/s，而它「不报错」，用户只会看着进度条
    // 十几分钟不动。所以前几个候选若在观察窗内太慢就主动放弃，把机会给镜像。
    private static final long SLOW_WINDOW_MS = 20000L;
    // 观察窗内至少要拿到总大小的这个比例（约 50 KB/s @50MB），否则判定为慢速。
    // 车机走 4G 常见 60~150 KB/s，阈值定高会把本来能下完的直连误杀。
    private static final double SLOW_MIN_FRACTION = 0.02;
    // 最后一个候选是兜底：允许它慢速跑完，不再放弃。
    private static final long SLOW_MIN_BYTES = 1024 * 1024L;

    /** 下载专用客户端：系统 DNS + 放宽读超时（断流常见的弱网下 20 秒太短），并允许断点续传。 */
    private static OkHttpClient downloadClient() {
        return DownloadClient.HOLDER;
    }

    private static final class DownloadClient {
        static final OkHttpClient HOLDER = PlainHttp.systemDns().newBuilder()
                .readTimeout(75, java.util.concurrent.TimeUnit.SECONDS)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * 单地址下载到 partial；取消/超时/太慢会抛异常，由调用方换下一个地址或下一轮。
     *
     * <p>已经存在的 partial 会用 {@code Range} 请求续传：服务端支持就只补缺的那段（206），
     * 不支持就整体重下（200）。这样弱网断流不用每次都丢掉已下载的几十 MB。</p>
     */
    private static void transfer(Task task, String url, File partial, Info info, File target,
                                 DownloadCallback callback, boolean allowSlowAbort) throws IOException {
        OkHttpClient client = downloadClient();
        long resumeFrom = partial.exists() ? partial.length() : 0L;
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", "TVBox-Car/" + info.localVersion);
        if (resumeFrom > 0) builder.header("Range", "bytes=" + resumeFrom + "-");
        Request request = builder.build();
        long lastPost = 0L;
        long startedAt = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            // 206 = 服务端按 Range 补了后半段，追加写；否则是整份，从头覆盖。
            boolean append = response.code() == 206 && resumeFrom > 0;
            long resumeDone = append ? resumeFrom : 0L;
            long total = body.contentLength() > 0 ? body.contentLength() + resumeDone : info.apkSize;
            long slowTarget = Math.max(SLOW_MIN_BYTES, (long) (total * SLOW_MIN_FRACTION));
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(partial, append)) {
                byte[] buffer = new byte[64 * 1024];
                long done = resumeDone;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    if (task.isCancelled()) throw new IOException("已取消");
                    out.write(buffer, 0, read);
                    done += read;
                    long now = System.currentTimeMillis();
                    // 续传时「慢速」按本轮新增量算，否则早已存在的字节会让慢速判定失效。
                    if (allowSlowAbort && now - startedAt >= SLOW_WINDOW_MS
                            && done - resumeDone < slowTarget) {
                        out.flush();
                        long kbps = (done - resumeDone) * 1000L / Math.max(1L, now - startedAt) / 1024L;
                        throw new IOException("速度过慢（" + kbps + " KB/s，"
                                + Math.max(1, (now - startedAt) / 1000L) + " 秒仅 " + (done - resumeDone) + " 字节）");
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


    /** 统一异常描述，失败原因要能直接进日志和弹窗。 */
    static String describe(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : (": " + message));
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
