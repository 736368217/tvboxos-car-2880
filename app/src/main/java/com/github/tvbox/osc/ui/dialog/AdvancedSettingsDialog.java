package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.diag.DiagLog;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 设置页唯一的「高级设置」入口。
 *
 * <p>方案 A：返回组件只保留两个互斥方案 —— 新边缘返回（默认）与旧返回杠（备用）；
 * 悬浮球与旧贴边横滑从 UI 和运行时一起移除。其余开关沿用 App 原有弹窗风格，
 * 点一行即切换，不进二级弹窗。</p>
 */
public class AdvancedSettingsDialog extends SettingSheetDialog {

    private final Runnable onChanged;
    private final OptionRow[] backOptions = new OptionRow[3];
    private TextView tvBrightness;
    private TextView tvDiagLog;
    private TextView tvBackHide;
    private TextView tvSourceLevel;
    private TextView tvAudioUsage;

    public AdvancedSettingsDialog(@NonNull Context context, Runnable onChanged) {
        super(context, "高级设置");
        this.onChanged = onChanged;
        backOptions[0] = addOption("关闭", false, v -> applyBack(false, null));
        backOptions[1] = addOption("新边缘返回（默认）", false,
                v -> applyBack(true, HawkConfig.BACK_MODE_EDGE));
        backOptions[2] = addOption("旧返回杠（备用）", false,
                v -> applyBack(true, HawkConfig.BACK_MODE_BAR));
        tvBrightness = addRow("亮度手势", "", v -> {
            Hawk.put(HawkConfig.BRIGHTNESS_GESTURE, !Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false));
            refresh();
        });
        // 音量开关 1：片源电平补偿（视频音轨比音乐低约 33 dB，开启后每片只测一次并锁定）
        tvSourceLevel = addRow("视频音量自动对齐", "", v -> {
            boolean next = !Hawk.get(HawkConfig.SOURCE_LEVEL_MATCH, true);
            com.github.tvbox.osc.car.CarEnhance.setSourceLevelMatchEnabled(next);
            DiagLog.event("source_level_match=" + next);
            refresh();
        });
        // 音量开关 2：音频属性对齐（显式 USAGE_MEDIA/CONTENT_TYPE_MOVIE，与音乐 App 同通道）
        tvAudioUsage = addRow("音频通道对齐音乐", "", v -> {
            boolean next = !Hawk.get(HawkConfig.AUDIO_USAGE_MEDIA, true);
            com.github.tvbox.osc.car.CarEnhance.setAudioUsageMediaEnabled(next);
            DiagLog.event("audio_usage_media=" + next);
            refresh();
        });
        tvBackHide = addRow("几秒后自动隐藏", "", v -> {
            int seconds = InAppBackButton.getHideSeconds();
            int next = seconds == 4 ? 6 : seconds == 6 ? 8 : seconds == 8 ? 0 : 4;
            InAppBackButton.setHideSeconds(ownerActivity(), next);
            refresh();
        });
        tvDiagLog = addRow("诊断日志", DiagLog.status(), v -> {
            DiagLog.uploadNow();
            android.widget.Toast.makeText(getContext(), "诊断日志已提交上传", android.widget.Toast.LENGTH_SHORT).show();
            tvDiagLog.setText(DiagLog.status());
        });
        refresh();
    }

    private void applyBack(boolean enabled, String mode) {
        android.app.Activity activity = ownerActivity();
        if (!enabled) {
            InAppBackButton.setEnabled(activity, false);
        } else {
            InAppBackButton.setEnabled(activity, true);
            if (mode != null) InAppBackButton.setMode(activity, mode);
        }
        DiagLog.event("back_mode=" + (enabled ? mode : "off")
                + " screen=" + getContext().getResources().getDisplayMetrics().widthPixels
                + "x" + getContext().getResources().getDisplayMetrics().heightPixels
                + " density=" + getContext().getResources().getDisplayMetrics().densityDpi);
        refresh();
    }

    private void refresh() {
        boolean enabled = InAppBackButton.isEnabled();
        String mode = InAppBackButton.getMode();
        boolean edge = enabled && !HawkConfig.BACK_MODE_BAR.equals(mode);
        boolean bar = enabled && HawkConfig.BACK_MODE_BAR.equals(mode);
        if (backOptions[0] != null) backOptions[0].setSelected(!enabled);
        if (backOptions[1] != null) backOptions[1].setSelected(edge);
        if (backOptions[2] != null) backOptions[2].setSelected(bar);
        if (tvBrightness != null) {
            tvBrightness.setText(Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false) ? "开启" : "关闭");
        }
        if (tvSourceLevel != null) {
            tvSourceLevel.setText(Hawk.get(HawkConfig.SOURCE_LEVEL_MATCH, true)
                    ? "开启（推荐）" : "关闭");
        }
        if (tvAudioUsage != null) {
            tvAudioUsage.setText(Hawk.get(HawkConfig.AUDIO_USAGE_MEDIA, true)
                    ? "开启（推荐）" : "关闭");
        }
        if (tvBackHide != null) {
            int seconds = InAppBackButton.getHideSeconds();
            tvBackHide.setText(seconds == 0 ? "关闭（常驻）" : seconds + " 秒");
        }
        if (tvDiagLog != null) {
            tvDiagLog.setText("点此立即上传");
        }
        if (onChanged != null) onChanged.run();
    }
}
