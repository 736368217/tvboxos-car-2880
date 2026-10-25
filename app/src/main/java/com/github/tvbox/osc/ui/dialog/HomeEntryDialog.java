package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 首页中间入口自定义：历史 / 直播 / 搜索 / 推送 / 收藏 可逐个开关。
 * 「设置」始终显示，避免把唯一的设置入口关掉之后再进不来。
 */
public class HomeEntryDialog extends BaseDialog {

    private final Runnable onChanged;
    private final String[] keys = {
            HawkConfig.HOME_ENTRY_HISTORY, HawkConfig.HOME_ENTRY_LIVE, HawkConfig.HOME_ENTRY_SEARCH,
            HawkConfig.HOME_ENTRY_PUSH, HawkConfig.HOME_ENTRY_FAVORITE
    };
    private final String[] labels = {"历史", "直播", "搜索", "推送", "收藏"};
    private final TextView[] values = new TextView[5];

    public HomeEntryDialog(@NonNull Context context, Runnable onChanged) {
        super(context);
        this.onChanged = onChanged;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    public void show() {
        super.show();
        Window window = getWindow();
        if (window != null) {
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.42f);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private View buildContent() {
        Context context = getContext();
        float titleSize = context.getResources().getDisplayMetrics().heightPixels * 0.034f;
        int padding = (int) context.getResources().getDimension(R.dimen.vs_30);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF21E293B);
        background.setCornerRadius(context.getResources().getDimension(R.dimen.vs_20));
        root.setBackground(background);

        TextView title = new TextView(context);
        title.setText("首页入口");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, titleSize);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, (int) context.getResources().getDimension(R.dimen.vs_20));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            final int index = i;
            values[i] = addRow(root, labels[i], v -> {
                Hawk.put(key, !enabled(key));
                refresh();
            });
        }
        TextView settingValue = addRow(root, "设置", v -> {
            // 常驻，不提供关闭
        });
        settingValue.setText("常驻");

        TextView close = new TextView(context);
        close.setText("关闭");
        close.setTextColor(0xFF0F172A);
        close.setGravity(Gravity.CENTER);
        close.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDisplayMetrics().heightPixels * 0.026f);
        close.setFocusable(true);
        close.setPadding(0, padding / 2, 0, padding / 2);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0xFF38BDF8);
        closeBg.setCornerRadius(context.getResources().getDimension(R.dimen.vs_10));
        close.setBackground(closeBg);
        close.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = padding;
        root.addView(close, closeParams);

        refresh();
        return root;
    }

    private static boolean enabled(String key) {
        Object value = Hawk.get(key, true);
        if (value instanceof Boolean) return (Boolean) value;
        String text = String.valueOf(value);
        return !"false".equalsIgnoreCase(text) && !"0".equals(text);
    }

    private TextView addRow(LinearLayout parent, String label, View.OnClickListener click) {
        Context context = getContext();
        int rowHeight = (int) context.getResources().getDimension(R.dimen.vs_60);
        int side = (int) context.getResources().getDimension(R.dimen.vs_20);
        float textSize = context.getResources().getDisplayMetrics().heightPixels * 0.026f;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setPadding(side, 0, side, 0);
        row.setBackgroundResource(R.drawable.shape_setting_model_focus);
        row.setOnClickListener(click);

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        value.setTextColor(0xFFBAE6FD);
        value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
        row.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        params.topMargin = (int) context.getResources().getDimension(R.dimen.vs_10);
        parent.addView(row, params);
        return value;
    }

    private void refresh() {
        for (int i = 0; i < keys.length; i++) {
            if (values[i] != null) values[i].setText(enabled(keys[i]) ? "显示" : "隐藏");
        }
        if (onChanged != null) onChanged.run();
    }
}
