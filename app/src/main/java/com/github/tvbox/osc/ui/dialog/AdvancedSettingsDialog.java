package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.car.EdgeGeometry;
import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.diag.DiagLog;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 设置页唯一的「高级设置」入口。
 *
 * <p>「返回组件」直接平铺成四个选项；亮度手势、自动隐藏、诊断日志都是一行开关，
 * 点一下就切换，不进二级弹窗。r77 起删除「音量自动对齐」：动态增益会造成忽大忽小，
 * 音量完全由用户手动 5% 档位控制。</p>
 */
public class AdvancedSettingsDialog extends SettingSheetDialog {

    private final Runnable onChanged;
    private final OptionRow[] backOptions = new OptionRow[4];
    private TextView tvBrightness;
    private TextView tvDiagLog;
    private TextView tvBackHide;

    public AdvancedSettingsDialog(@NonNull Context context, Runnable onChanged) {
        super(context, "高级设置");
        this.onChanged = onChanged;
        backOptions[0] = addOption("关闭", false, v -> applyBack(false, null));
        backOptions[1] = addOption("边缘拉出", false, v -> applyBack(true, HawkConfig.BACK_MODE_EDGE));
        backOptions[2] = addOption("悬浮球", false, v -> applyBack(true, HawkConfig.BACK_MODE_FLOATING));
        backOptions[3] = addOption("边缘滑动", false, v -> applyBack(true, HawkConfig.BACK_MODE_SWIPE));
        tvBrightness = addRow("亮度手势", "", v -> {
            Hawk.put(HawkConfig.BRIGHTNESS_GESTURE, !Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false));
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
                + " hit_max=" + Math.round(EdgeGeometry.hitWidthPx(
                        getContext().getResources().getDisplayMetrics().widthPixels,
                        99999f)) + "px");
        refresh();
    }

    private void refresh() {
        boolean enabled = InAppBackButton.isEnabled();
        String mode = InAppBackButton.getMode();
        boolean edge = enabled && !HawkConfig.BACK_MODE_FLOATING.equals(mode)
                && !HawkConfig.BACK_MODE_SWIPE.equals(mode) && !HawkConfig.BACK_MODE_BOTH.equals(mode);
        boolean ball = enabled && (HawkConfig.BACK_MODE_FLOATING.equals(mode)
                || HawkConfig.BACK_MODE_BOTH.equals(mode));
        boolean swipe = enabled && HawkConfig.BACK_MODE_SWIPE.equals(mode);
        if (backOptions[0] != null) backOptions[0].setSelected(!enabled);
        if (backOptions[1] != null) backOptions[1].setSelected(edge);
        if (backOptions[2] != null) backOptions[2].setSelected(ball);
        if (backOptions[3] != null) backOptions[3].setSelected(swipe);
        if (tvBrightness != null) {
            tvBrightness.setText(Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false) ? "开启" : "关闭");
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
