package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 设置页唯一的「高级设置」入口。
 *
 * <p>只放需要长期留在设置里的两项：返回组件（只选形式）与亮度手势。
 * 播放增强在播放页、首页入口在首页长按「设置」、音量增益不再提供设置。</p>
 */
public class AdvancedSettingsDialog extends SettingSheetDialog {

    private final Runnable onChanged;
    private TextView tvBackBar, tvBrightness;

    public AdvancedSettingsDialog(@NonNull Context context, Runnable onChanged) {
        super(context, "高级设置");
        this.onChanged = onChanged;
        tvBackBar = addRow("返回组件", "", v ->
                new BackBarSettingsDialog(getContext(), this::refresh).show());
        tvBrightness = addRow("亮度手势", "", v -> {
            Hawk.put(HawkConfig.BRIGHTNESS_GESTURE, !Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false));
            refresh();
        });
        refresh();
    }

    private void refresh() {
        if (tvBackBar != null) {
            if (!InAppBackButton.isEnabled()) {
                tvBackBar.setText("关闭");
            } else {
                String mode = InAppBackButton.getMode();
                tvBackBar.setText(HawkConfig.BACK_MODE_SWIPE.equals(mode) ? "边缘滑动"
                        : HawkConfig.BACK_MODE_FLOATING.equals(mode) ? "悬浮球"
                        : HawkConfig.BACK_MODE_BOTH.equals(mode) ? "双模共存" : "边缘拉出");
            }
        }
        if (tvBrightness != null) {
            tvBrightness.setText(Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false) ? "开启" : "关闭");
        }
        if (onChanged != null) onChanged.run();
    }
}
