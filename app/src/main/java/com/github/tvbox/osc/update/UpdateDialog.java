package com.github.tvbox.osc.update;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.dialog.BaseDialog;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * 软件更新弹窗：显示版本对比与更新说明，点「立即更新」就地下载并唤起安装器。
 *
 * <p>两个入口：设置页手动检查（会一直显示结果，含「已是最新」），以及启动时静默检查
 * （只在真有新版本且未被跳过时才弹）。</p>
 */
public class UpdateDialog extends BaseDialog {

    private static WeakReference<UpdateDialog> CURRENT = new WeakReference<>(null);
    private static final String TAG = "AppUpdate";
    private static AppUpdate.Info pendingAutoInfo;

    private final WeakReference<Activity> hostActivity;
    private final TextView tvVersions, tvNotes, tvHint, tvPercent;
    private final TextView btnUpdate, btnSkip, btnManual, btnClose;
    private final LinearLayout progressBox;
    private final ProgressBar progress;

    private boolean fromUser;
    private AppUpdate.Info info;
    private AppUpdate.Task task;
    private File downloaded;
    private boolean dismissed;
    private boolean downloading;
    private long lastClickMs;
    private long shownAtMs;

    private UpdateDialog(@NonNull Context context, boolean fromUser) {
        super(context);
        this.hostActivity = new WeakReference<>(context instanceof Activity ? (Activity) context : null);
        this.fromUser = fromUser;
        setContentView(R.layout.dialog_app_update);
        tvVersions = findViewById(R.id.tvVersions);
        tvNotes = findViewById(R.id.tvNotes);
        tvHint = findViewById(R.id.tvHint);
        tvPercent = findViewById(R.id.tvPercent);
        progressBox = findViewById(R.id.progressBox);
        progress = findViewById(R.id.progress);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnSkip = findViewById(R.id.btnSkip);
        btnManual = findViewById(R.id.btnManual);
        btnClose = findViewById(R.id.btnClose);
        btnUpdate.setOnClickListener(v -> onPrimaryClick());
        btnClose.setOnClickListener(v -> dismissSafely());
        btnSkip.setOnClickListener(v -> onSkipClick());
        btnManual.setOnClickListener(v -> {
            if (!acceptClick()) return;
            AppUpdate.openReleasePage(getContext(), info);
        });
        setCanceledOnTouchOutside(true);
        setOnDismissListener(dialog -> {
            dismissed = true;
            // 关掉弹窗就停掉下载，避免后台继续吃流量却没人看得见进度
            if (task != null && downloading) {
                task.cancel();
                Log.i(TAG, "dialog dismissed, download cancelled");
            }
        });
    }

    /** 手动检查（设置页）：立刻显示弹窗，结果出来后原地更新。 */
    public static void checkManually(Activity activity) {
        check(activity, true);
    }

    /** 启动静默检查：受节流约束，只有真有新版本时才弹窗。 */
    public static void checkSilently(Activity activity) {
        check(activity, false);
    }

    private static void check(final Activity activity, final boolean fromUser) {
        if (activity == null || activity.isFinishing()) return;
        if (!fromUser) {
            if (!AppUpdate.shouldAutoCheck()) {
                Log.i(TAG, "auto check skipped (throttled or disabled)");
                return;
            }
            // Silent checks never allocate a Dialog against the HomeActivity window.
            // The result may arrive after playback has covered HomeActivity.
            AppUpdate.check(activity, false, (result, error) -> {
                if (error != null) {
                    Log.w(TAG, "silent check error: " + error);
                    return;
                }
                if (result == null || !result.hasUpdate || AppUpdate.isSkipped(result.tag)) return;
                pendingAutoInfo = result;
                Activity current = null;
                try {
                    current = com.github.tvbox.osc.base.App.getInstance().getCurrentActivity();
                } catch (Throwable ignored) {
                }
                if (current == null || current.isFinishing() || current.isDestroyed()) current = activity;
                showPendingIfReady(current);
            });
            return;
        }
        UpdateDialog existing = CURRENT.get();
        if (existing != null && existing.isShowing()) {
            if (existing.hostActivity.get() == activity) {
                existing.startCheck(true);
                return;
            }
            existing.dismiss();
        }
        pendingAutoInfo = null;
        final UpdateDialog dialog = new UpdateDialog(activity, true);
        dialog.startCheck(true);
    }

    /** Called by BaseActivity on resume/window-focus; delayed auto results bind to the foreground window. */
    public static void showPendingIfReady(Activity activity) {
        if (!(activity instanceof com.github.tvbox.osc.ui.activity.HomeActivity)) return;
        if (activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()) return;
        AppUpdate.Info info = pendingAutoInfo;
        if (info == null) return;
        if (AppUpdate.isSkipped(info.tag)) {
            pendingAutoInfo = null;
            return;
        }
        UpdateDialog existing = CURRENT.get();
        if (existing != null && existing.isShowing()) {
            if (existing.hostActivity.get() == activity) {
                pendingAutoInfo = null;
                return;
            }
            existing.dismiss();
        }
        pendingAutoInfo = null;
        UpdateDialog dialog = new UpdateDialog(activity, false);
        dialog.info = info;
        dialog.applyStateAvailable();
        dialog.ensureShown();
        Log.i(TAG, "auto update dialog shown on " + activity.getClass().getSimpleName());
    }

    /** 将未下载的更新提示从即将被覆盖的 Activity 窗口收回，返回 Home 后再显示。 */
    public static void onActivityPaused(Activity activity) {
        UpdateDialog dialog = CURRENT.get();
        // 只有「还没下载、也没下好」的提示才收回；下载中或已下好要留在原窗口，避免打断或丢状态。
        if (activity == null || dialog == null || !dialog.isShowing()
                || dialog.hostActivity.get() != activity
                || dialog.downloading || dialog.downloaded != null
                || dialog.info == null || !dialog.info.hasUpdate) return;
        pendingAutoInfo = dialog.info;
        Log.i(TAG, "update dialog deferred until HomeActivity resumes");
        dialog.dismiss();
    }

    private void startCheck(boolean userInitiated) {
        // 复用已有弹窗实例时，本次可能是「手动检查」，而实例是自动检查建的（fromUser=false）。
        fromUser = userInitiated;
        if (userInitiated) {
            applyStateChecking();
            ensureShown();
        }
        AppUpdate.check(getContext(), fromUser, (result, error) -> {
            if (dismissed) return;
            if (error != null) {
                Log.w(TAG, "check error: " + error);
                if (fromUser) {
                    applyStateError(error);
                    ensureShown();
                }
                return;
            }
            info = result;
            if (result.hasUpdate) {
                if (!fromUser && AppUpdate.isSkipped(result.tag)) {
                    Log.i(TAG, "auto check: " + result.tag + " skipped by user");
                    return;
                }
                applyStateAvailable();
                ensureShown();
            } else if (fromUser) {
                applyStateUpToDate();
                ensureShown();
            } else {
                Log.i(TAG, "auto check: already latest (" + result.localVersion + ")");
            }
        });
    }

    private void onPrimaryClick() {
        if (!acceptClick()) return;
        if (downloading) {
            if (task != null) task.cancel();
            applyStateAvailable();
            tvHint.setText("已取消下载");
            return;
        }
        if (downloaded != null && downloaded.exists()) {
            installDownloaded();
            return;
        }
        if (info != null && info.hasUpdate) startDownload();
        else startCheck(true);
    }

    private void onSkipClick() {
        if (!acceptClick()) return;
        if (info != null && info.tag != null) {
            AppUpdate.skip(info.tag);
            Toast.makeText(getContext(), "已跳过 " + info.tag + "，可在设置里手动检查更新", Toast.LENGTH_LONG).show();
        }
        dismissSafely();
    }

    private void startDownload() {
        downloaded = null;
        downloading = true;
        applyStateDownloading();
        ensureShown();
        task = AppUpdate.download(getContext(), info, new AppUpdate.DownloadCallback() {
            @Override
            public void onProgress(long bytes, long total) {
                if (dismissed) return;
                int permille = total > 0 ? (int) Math.min(1000L, bytes * 1000L / total) : 0;
                progress.setProgress(permille);
                tvPercent.setText(permille / 10 + "%");
                tvHint.setText(sizeText(bytes) + " / " + sizeText(total));
            }

            @Override
            public void onDone(File apk) {
                if (dismissed) return;
                downloaded = apk;
                downloading = false;
                progress.setProgress(1000);
                tvPercent.setText("100%");
                installDownloaded();
            }

            @Override
            public void onError(String message) {
                if (dismissed) return;
                downloading = false;
                applyStateError(message);
            }
        });
    }

    private void installDownloaded() {
        if (downloaded == null || !downloaded.exists()) {
            applyStateError("安装包不存在，请重新下载");
            return;
        }
        if (AppUpdate.needsInstallPermission(getContext())) {
            AppUpdate.openInstallPermissionSettings(getContext());
            tvHint.setText("请先在本应用上允许「安装未知应用」，然后回来点「立即安装」。");
            btnUpdate.setText("立即安装");
            return;
        }
        if (AppUpdate.install(getContext(), downloaded)) {
            progressBox.setVisibility(View.GONE);
            tvHint.setText("已唤起系统安装器，按提示完成安装即可。");
            btnUpdate.setText("再次安装");
        } else {
            applyStateError("无法唤起安装器，可点「浏览器下载」手动安装");
        }
    }

    // ---------------- 状态渲染 ----------------

    private void applyStateChecking() {
        progressBox.setVisibility(View.GONE);
        tvVersions.setText("当前版本 " + AppUpdate.localVersion(getContext()));
        tvNotes.setText("正在检查更新…");
        tvHint.setText("更新源：" + mirrorText());
        btnUpdate.setVisibility(View.GONE);
        btnSkip.setVisibility(View.GONE);
        btnManual.setVisibility(View.GONE);
        btnClose.setText("取消");
    }

    private void applyStateUpToDate() {
        progressBox.setVisibility(View.GONE);
        tvVersions.setText("当前版本 " + info.localVersion + " · 已是最新版本");
        tvNotes.setText("");
        tvHint.setText("检查通道：" + info.channelLabel);
        btnUpdate.setVisibility(View.GONE);
        btnSkip.setVisibility(View.GONE);
        btnManual.setVisibility(View.GONE);
        btnClose.setText("关闭");
    }

    private void applyStateAvailable() {
        downloading = false;
        progressBox.setVisibility(View.GONE);
        tvVersions.setText("当前 " + info.localVersion + "  →  最新 " + info.remoteVersion);
        String notes = info.notes == null ? "" : info.notes.trim();
        tvNotes.setText(notes.length() == 0 ? "（该版本没有填写更新说明）" : notes);
        tvHint.setText(sizeText(info.apkSize) + " · 通道 " + info.channelLabel);
        btnUpdate.setText("立即更新");
        btnUpdate.setVisibility(View.VISIBLE);
        btnSkip.setVisibility(View.VISIBLE);
        btnManual.setVisibility(View.VISIBLE);
        btnClose.setText("关闭");
    }

    private void applyStateDownloading() {
        progressBox.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        tvPercent.setText("0%");
        tvHint.setText(sizeText(0L) + " / " + sizeText(info.apkSize));
        btnUpdate.setText("取消");
        btnUpdate.setVisibility(View.VISIBLE);
        btnSkip.setVisibility(View.GONE);
        btnManual.setVisibility(View.GONE);
        btnClose.setText("后台");
    }

    private void applyStateError(String message) {
        downloading = false;
        progressBox.setVisibility(View.GONE);
        tvHint.setText(message);
        btnUpdate.setText("重试");
        btnUpdate.setVisibility(View.VISIBLE);
        btnSkip.setVisibility(info != null && info.hasUpdate ? View.VISIBLE : View.GONE);
        btnManual.setVisibility(View.VISIBLE);
        btnClose.setText("关闭");
    }

    private String mirrorText() {
        String mirror = com.orhanobut.hawk.Hawk.get(com.github.tvbox.osc.util.HawkConfig.UPDATE_MIRROR, "");
        return mirror == null || mirror.trim().length() == 0 ? "自动（直连 → 镜像）" : mirror.trim();
    }

    private static String sizeText(long bytes) {
        if (bytes <= 0L) return "0 MB";
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private void ensureShown() {
        if (!dismissed && !isShowing()) show();
    }

    @Override
    public void show() {
        UpdateDialog previous = CURRENT.get();
        if (previous != null && previous != this && previous.isShowing()) previous.dismiss();
        super.show();
        shownAtMs = SystemClock.uptimeMillis();
        CURRENT = new WeakReference<>(this);
    }

    private void dismissSafely() {
        if (!acceptClick()) return;
        dismiss();
    }

    /** 车机/遥控可能把一次按下派发两次；弹窗刚打开的点击也要丢掉，避免一次点击跳两档。 */
    private boolean acceptClick() {
        long now = SystemClock.uptimeMillis();
        if (now - shownAtMs < 300L) return false;
        if (now - lastClickMs < 400L) return false;
        lastClickMs = now;
        return true;
    }
}
