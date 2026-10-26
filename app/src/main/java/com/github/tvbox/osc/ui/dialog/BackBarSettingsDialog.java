package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.util.HawkConfig;

/**
 * 返回组件：只保留「开关」和「形式三选一」，不再提供杠大小/颜色/透明度/自动隐藏等自定义。
 */
public class BackBarSettingsDialog extends SettingSheetDialog {

    private final Runnable onChanged;
    private TextView tvEnabled, tvMode;

    public BackBarSettingsDialog(@NonNull Context context, Runnable onChanged) {
        super(context, "返回组件");
        this.onChanged = onChanged;
        tvEnabled = addRow("开关", "", v -> {
            InAppBackButton.setEnabled(ownerActivity(), !InAppBackButton.isEnabled());
            refresh();
        });
        tvMode = addRow("返回形式", "", v -> {
            String current = InAppBackButton.getMode();
            String next;
            if (HawkConfig.BACK_MODE_EDGE.equals(current)) next = HawkConfig.BACK_MODE_FLOATING;
            else if (HawkConfig.BACK_MODE_FLOATING.equals(current)) next = HawkConfig.BACK_MODE_SWIPE;
            else next = HawkConfig.BACK_MODE_EDGE;
            InAppBackButton.setMode(ownerActivity(), next);
            refresh();
        });
        refresh();
    }

    private android.app.Activity ownerActivity() {
        Context context = getContext();
        return context instanceof android.app.Activity ? (android.app.Activity) context : null;
    }

    private void refresh() {
        if (tvEnabled != null) tvEnabled.setText(InAppBackButton.isEnabled() ? "开启" : "关闭");
        if (tvMode != null) {
            String mode = InAppBackButton.getMode();
            tvMode.setText(HawkConfig.BACK_MODE_SWIPE.equals(mode) ? "边缘滑动"
                    : HawkConfig.BACK_MODE_FLOATING.equals(mode) ? "悬浮球"
                    : HawkConfig.BACK_MODE_BOTH.equals(mode) ? "双模共存" : "边缘拉出");
        }
        if (onChanged != null) onChanged.run();
    }
}
