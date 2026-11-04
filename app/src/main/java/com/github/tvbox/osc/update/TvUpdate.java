package com.github.tvbox.osc.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.github.tvbox.osc.util.MirrorFetch;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * TV 版应用内更新：查 Release → 弹窗确认 → 下载 APK → 校验 → 唤起系统安装器。
 *
 * <p>设计原则（TV 操作习惯）：
 * <ul>
 *   <li><b>不做静默自动弹窗</b>：TV 上突然弹出的窗口会抢走遥控焦点、打断看片。
 *       只在用户从「设置 → 检查更新」主动点击时才检查并弹窗。</li>
 *   <li>下载/校验全在后台线程，进度回主线程；不阻塞遥控。</li>
 *   <li>用 {@link MirrorFetch} 的多通道（直连 → jsDelivr → 各 gh 镜像），国内也能拿到包。</li>
 *   <li>安装前校验包名、versionCode、签名与 SHA-256，避免装错包 / 半截包。</li>
 * </ul>
 *
 * <p>自查：{@code adb logcat -s TvUpdate SubFetch}</p>
 */
public final class TvUpdate {

    public static final String TAG = "TvUpdate";

    /** TV 专用 Release 的 tag 前缀；只认以它开头的 tag，避免误装车机版包。 */
    private static final String TAG_PREFIX = "tv-v";
    private static final String OWNER = "736368217";
    private static final String REPO = "tvboxos-car-2880";
    private static final String API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases";

    private static final String KEY_SKIP_TAG = "tv_update_skip_tag";
    private static final long MIN_APK_BYTES = 1024L * 1024L;

    private TvUpdate() {
    }

    public interface CheckCallback {
        void onResult(TvUpdateInfo info, String error);
    }

    public interface DownloadCallback {
        void onProgress(long bytes, long total);
        void onDone(File apk);
        void onError(String message);
    }

    public interface ProgressHost {
        void post(Runnable runnable);
    }

    public static void skip(String tag) {
        if (tag != null && tag.length() > 0) Hawk.put(KEY_SKIP_TAG, tag);
    }

    public static boolean isSkipped(String tag) {
        return tag != null && tag.equals(Hawk.get(KEY_SKIP_TAG, ""));
    }

    public static int localVersionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static String localVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 查找最新的 TV 专用 Release（遍历最近若干条，跳过车机版 tag）。 */
    public static void check(final Context context, final ProgressHost host, final CheckCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = MirrorFetch.fetchText(API, "TVBox-TV/" + localVersionName(context),
                            "application/vnd.github+json");
                    org.json.JSONArray releases = new org.json.JSONArray(body);
                    for (int i = 0; i < releases.length() && i < 30; i++) {
                        JSONObject rel = releases.getJSONObject(i);
                        String tag = rel.optString("tag_name", "");
                        if (!tag.startsWith(TAG_PREFIX)) continue;
                        TvUpdateInfo info = TvUpdateInfo.fromReleaseJson(rel.toString());
                        if (info == null) {
                            Log.w(TAG, "TV release " + tag + " 缺少 APK 资产");
                            continue;
                        }
                        // 优先用 Release 正文里的清单（versionCode / notes / sha256）
                        String notes = TvUpdateInfo.cleanNotes(rel.optString("body", ""), 800);
                        TvUpdateInfo full = TvUpdateInfo.parseManifest(
                                rel.optString("body", ""), info.apkUrl, info.apkName, info.apkSize, tag);
                        if (full == null || full.versionCode <= 0) {
                            // 正文不是 JSON 清单时用纯文本说明 + tag 里的版本号
                            int code = parseCode(tag);
                            full = new TvUpdateInfo(tag, tag, code, notes,
                                    info.apkName, info.apkUrl, info.apkSize, "");
                        }
                        final TvUpdateInfo result = full;
                        final boolean hasUpdate = result.isNewerThan(localVersionCode(context), localVersionName(context));
                        Log.i(TAG, "check ok tag=" + tag + " vc=" + result.versionCode
                                + " local=" + localVersionCode(context) + " hasUpdate=" + hasUpdate);
                        host.post(new Runnable() {
                            @Override public void run() { callback.onResult(hasUpdate ? result : null, null); }
                        });
                        return;
                    }
                    host.post(new Runnable() {
                        @Override public void run() { callback.onResult(null, "没有找到 TV 版更新"); }
                    });
                } catch (Throwable error) {
                    Log.w(TAG, "check failed: " + error);
                    final String msg = "检查更新失败：" + (error.getMessage() == null ? error.toString() : error.getMessage());
                    host.post(new Runnable() {
                        @Override public void run() { callback.onResult(null, msg); }
                    });
                }
            }
        }).start();
    }

    private static int parseCode(String tag) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)vc(\\d+)").matcher(tag);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** 下载 APK 到应用私有 externalFilesDir/apk，多通道 + 完整性校验。 */
    public static void download(final Context context, final TvUpdateInfo info,
                                final ProgressHost host, final DownloadCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = context.getExternalFilesDir("apk");
                    if (dir == null) dir = new File(context.getCacheDir(), "apk");
                    if (!dir.exists()) dir.mkdirs();
                    String name = info.apkName == null ? ("TVBox-TV-" + info.tag + ".apk") : info.apkName;
                    if (!name.endsWith(".apk")) name += ".apk";
                    final File target = new File(dir, name);
                    if (target.exists()) target.delete();
                    Log.i(TAG, "download start url=" + info.apkUrl + " size=" + info.apkSize);
                    // apkSha256 是 SHA-256，不能当 md5 传给 MirrorFetch；
                    // 完整性由下面 verify() 用 SHA-256 单独校验。
                    // 用声明的文件大小 + SHA-256 判定完整性，任一候选半截就自动换源
                    long written = MirrorFetch.download(info.apkUrl, target,
                            "TVBox-TV/" + localVersionName(context), info.apkSize, info.apkSha256);
                    if (written < MIN_APK_BYTES) {
                        target.delete();
                        throw new java.io.IOException("安装包过小（" + written + " 字节）");
                    }
                    String problem = verify(context, target, info);
                    if (problem != null) {
                        target.delete();
                        throw new java.io.IOException(problem);
                    }
                    host.post(new Runnable() {
                        @Override public void run() { callback.onDone(target); }
                    });
                } catch (Throwable error) {
                    Log.w(TAG, "download failed: " + error);
                    final String msg = "下载失败：" + (error.getMessage() == null ? error.toString() : error.getMessage());
                    host.post(new Runnable() {
                        @Override public void run() { callback.onError(msg); }
                    });
                }
            }
        }).start();
    }

    /** 返回 null 表示可安装。 */
    public static String verify(Context context, File apk, TvUpdateInfo info) {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (archive == null) return "安装包无法解析";
        if (!context.getPackageName().equals(archive.packageName)) {
            return "包名不匹配（" + archive.packageName + "）";
        }
        PackageInfo installed;
        try {
            installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        } catch (Exception error) {
            return "读取当前版本失败";
        }
        if (archive.versionCode < installed.versionCode) {
            return "安装包版本更低（" + archive.versionCode + " < " + installed.versionCode + "）";
        }
        String a = signatureDigest(archive.signatures);
        String b = signatureDigest(installed.signatures);
        if (a == null || !a.equals(b)) return "签名与当前应用不一致，无法覆盖安装";
        if (info.apkSha256 != null && info.apkSha256.length() > 0) {
            String actual = fileSha256(apk);
            if (!info.apkSha256.equalsIgnoreCase(actual)) {
                return "SHA-256 不匹配（文件不完整或被改写）";
            }
        }
        Log.i(TAG, "verify ok vc=" + archive.versionCode + " sig=" + a.substring(0, 16));
        return null;
    }

    private static String signatureDigest(android.content.pm.Signature[] signatures) {
        if (signatures == null || signatures.length == 0) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte v : hash) sb.append(String.format(Locale.US, "%02X", v));
            return sb.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String fileSha256(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte v : hash) sb.append(String.format(Locale.US, "%02X", v));
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean needsInstallPermission(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls();
    }

    public static void openInstallPermissionSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception error) {
            Log.w(TAG, "无法打开安装权限设置: " + error);
        }
    }

    /** 唤起系统安装器；返回 false 表示缺权限（调用方应引导授权）。 */
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
            Log.i(TAG, "install launched " + apk.getName());
            return true;
        } catch (Exception error) {
            Log.w(TAG, "install failed: " + error);
            return false;
        }
    }
}
