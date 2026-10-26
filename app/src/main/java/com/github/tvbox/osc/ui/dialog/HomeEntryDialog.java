package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 首页中间入口自定义（历史 / 直播 / 搜索 / 推送 / 收藏）。
 * 入口在首页长按「设置」按钮弹出；「设置」本身常驻，避免把自己锁在门外。
 */
public class HomeEntryDialog extends SettingSheetDialog {

    private final Runnable onChanged;
    private final String[] keys = {
            HawkConfig.HOME_ENTRY_HISTORY, HawkConfig.HOME_ENTRY_LIVE, HawkConfig.HOME_ENTRY_SEARCH,
            HawkConfig.HOME_ENTRY_PUSH, HawkConfig.HOME_ENTRY_FAVORITE
    };
    private final String[] labels = {"历史", "直播", "搜索", "推送", "收藏"};
    private final TextView[] values = new TextView[5];

    public HomeEntryDialog(@NonNull Context context, Runnable onChanged) {
        super(context, "首页入口");
        this.onChanged = onChanged;
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            values[i] = addRow(labels[i], "", v -> {
                Hawk.put(key, !enabled(key));
                refresh();
            });
        }
        addRow("设置", "常驻", null);
        refresh();
    }

    private static boolean enabled(String key) {
        Object value = Hawk.get(key, true);
        if (value instanceof Boolean) return (Boolean) value;
        String text = String.valueOf(value);
        return !"false".equalsIgnoreCase(text) && !"0".equals(text);
    }

    private void refresh() {
        for (int i = 0; i < keys.length; i++) {
            if (values[i] != null) values[i].setText(enabled(keys[i]) ? "显示" : "隐藏");
        }
        if (onChanged != null) onChanged.run();
    }
}
