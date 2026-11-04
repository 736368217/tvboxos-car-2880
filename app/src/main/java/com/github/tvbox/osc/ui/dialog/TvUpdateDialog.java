package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.update.TvUpdate;
import com.github.tvbox.osc.update.TvUpdateInfo;

import java.io.File;

/**
 * TV 版更新弹窗：专为遥控器设计。
 *
 * <p>TV 操作习惯要点：</p>
 * <ul>
 *   <li>不会自动弹出 —— 只在用户从「设置 → 检查更新」主动点开时出现，不抢焦点、不打断看片。</li>
 *   <li>只有两个大按钮，横排；<b>弹窗自己接管方向键与确认键</b>，
 *       不依赖 Android 的 View 焦点（实测部分盒子上 Dialog 里 focused 一直为 false，
 *       只靠 requestFocus 会出现「按钮看得见却按不动」）。</li>
 *   <li>默认选中「立即更新」，按一次 OK 即下载；左右/上下切到「暂不更新」；返回键关闭。</li>
 *   <li>下载进度与失败原因写在同一个弹窗里，不额外弹层、不只靠一闪而过的 Toast。</li>
 * </ul>
 */
public class TvUpdateDialog extends BaseDialog {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final TvUpdate.ProgressHost host = main::post;

    private TextView tvVersion, tvNotes, tvStatus, btnUpdate, btnSkip;
    private TvUpdateInfo info;
    private boolean downloading;
    /** 0=立即更新，1=暂不更新。 */
    private int selectedIndex;

    /** 设置页调用：先检查，有更新才弹窗。结果/错误同时写回设置行文字。 */
    public static void checkAndShow(final Context context, final TextView statusSink) {
        TvUpdate.check(context, mainHost(), new TvUpdate.CheckCallback() {
            @Override
            public void onResult(TvUpdateInfo info, String error) {
                if (statusSink != null) {
                    statusSink.setText(error != null ? error
                            : (info == null ? "已是最新版本" : "发现新版本 " + info.versionName));
                }
                if (info == null) {
                    Toast.makeText(context, error == null ? "已是最新版本" : error, Toast.LENGTH_LONG).show();
                    return;
                }
                if (TvUpdate.isSkipped(info.tag)) {
                    Toast.makeText(context, "该版本已选择跳过", Toast.LENGTH_SHORT).show();
                    return;
                }
                TvUpdateDialog dialog = new TvUpdateDialog(context);
                dialog.setInfo(info);
                dialog.show();
            }
        });
    }

    private static TvUpdate.ProgressHost mainHost() {
        return new TvUpdate.ProgressHost() {
            private final Handler h = new Handler(Looper.getMainLooper());
            @Override public void post(Runnable runnable) { h.post(runnable); }
        };
    }

    public TvUpdateDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_tv_update);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tvVersion = findViewById(R.id.tvVersion);
        tvNotes = findViewById(R.id.tvNotes);
        tvStatus = findViewById(R.id.tvStatus);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnSkip = findViewById(R.id.btnSkip);

        // 触摸/鼠标点击依然有效，遥控器走下面的 onKeyDown
        btnUpdate.setOnClickListener(v -> onUpdate());
        btnSkip.setOnClickListener(v -> onSkip());
        btnUpdate.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) select(0); });
        btnSkip.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) select(1); });

        select(0);
        setOnShowListener(dialog -> main.post(() -> select(0)));
    }

    public void setInfo(TvUpdateInfo info) {
        this.info = info;
        if (tvVersion != null) {
            tvVersion.setText("当前 " + TvUpdate.localVersionName(getContext())
                    + "  →  新版 " + info.versionName);
        }
        if (tvNotes != null) {
            String notes = info.notes == null || info.notes.length() == 0 ? "修复问题并优化体验" : info.notes;
            tvNotes.setText(notes);
        }
        if (tvStatus != null) {
            long mb = info.apkSize > 0 ? info.apkSize / 1024 / 1024 : 0;
            tvStatus.setText(mb > 0 ? "安装包约 " + mb + " MB，按「立即更新」开始下载" : "按「立即更新」开始下载");
        }
    }

    private void onUpdate() {
        if (downloading || info == null) return;
        downloading = true;
        btnUpdate.setEnabled(false);
        btnSkip.setEnabled(false);
        tvStatus.setText("正在下载…");
        TvUpdate.download(getContext(), info, host, new TvUpdate.DownloadCallback() {
            @Override
            public void onProgress(long bytes, long total) { }

            @Override
            public void onDone(File apk) {
                tvStatus.setText("下载完成，正在唤起安装…");
                if (!TvUpdate.install(getContext(), apk)) {
                    tvStatus.setText("需要开启「安装未知应用」权限，已打开设置页；授权后请再次点「立即更新」");
                } else {
                    tvStatus.setText("已打开安装器，按遥控器确认即可完成升级");
                }
                downloading = false;
                btnUpdate.setEnabled(true);
                btnSkip.setEnabled(true);
            }

            @Override
            public void onError(String message) {
                tvStatus.setText(message);
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                downloading = false;
                btnUpdate.setEnabled(true);
                btnSkip.setEnabled(true);
            }
        });
    }

    private void onSkip() {
        if (info != null) TvUpdate.skip(info.tag);
        dismiss();
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        // 显式接管：不依赖 Dialog 是否拿到 View 焦点，遥控器一定能操作。
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                select(0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                select(1);
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (selectedIndex == 0) onUpdate();
                else onSkip();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!downloading) dismiss();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void select(int index) {
        selectedIndex = index;
        if (btnUpdate != null) btnUpdate.setSelected(index == 0);
        if (btnSkip != null) btnSkip.setSelected(index == 1);
    }
}
