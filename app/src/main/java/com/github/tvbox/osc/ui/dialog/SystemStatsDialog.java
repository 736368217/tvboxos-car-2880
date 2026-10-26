package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 右上角性能徽标的详情面板：每秒刷新一次，列出所有能取到的运行数据。
 * 取不到的项目由调用方直接不放进列表（与徽标同样策略）。
 * 外观与其它弹窗一致（同一套底板 / 标题 / 行样式）。
 */
public class SystemStatsDialog extends SettingSheetDialog {

    public interface Provider {
        List<String[]> snapshot();
    }

    private final Provider provider;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    public SystemStatsDialog(@NonNull Context context, Provider provider) {
        super(context, "运行状态");
        this.provider = provider;
        refresh();
        handler.post(tick);
    }

    @Override
    public void dismiss() {
        handler.removeCallbacks(tick);
        super.dismiss();
    }

    private void refresh() {
        if (rows == null || provider == null) return;
        List<String[]> data = provider.snapshot();
        clearRows();
        if (data == null) return;
        for (String[] row : data) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            addRow(row[0], row[1] == null ? "" : row[1], null);
        }
    }
}
