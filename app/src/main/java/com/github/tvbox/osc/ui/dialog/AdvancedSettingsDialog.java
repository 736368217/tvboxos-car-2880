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
 * <p>「返回组件」直接平铺成四个选项（关闭 / 边缘拉出 / 悬浮球 / 边缘滑动），
 * 点哪项就是哪项 —— 不再先开一层弹窗再进去选模式。设置项本身就不多，
 * 多一层跳转只会让人觉得「点了个寂寞」。</p>
 *
 * <p>只放需要长期留在设置里的两项：返回组件与亮度手势。
 * 播放增强在播放页、首页入口在首页长按「设置」、音量增益不提供设置。</p>
 */
public class AdvancedSettingsDialog extends SettingSheetDialog {

    private final Runnable onChanged;
    private final OptionRow[] backOptions = new OptionRow[4];
    private TextView tvBrightness;

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
        refresh();
    }

    /** 选中某一项：关闭 = enabled false；其余 = enabled true 且切到对应模式。 */
    private void applyBack(boolean enabled, String mode) {
        android.app.Activity activity = ownerActivity();
        if (!enabled) {
            InAppBackButton.setEnabled(activity, false);
        } else {
            InAppBackButton.setEnabled(activity, true);
            if (mode != null) InAppBackButton.setMode(activity, mode);
        }
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
        if (onChanged != null) onChanged.run();
    }
}