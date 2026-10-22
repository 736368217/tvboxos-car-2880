package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import java.util.List;

/**
 * 右上角性能徽标的详情页：每秒刷新一次，列出所有能取到的运行数据。
 * 取不到的项目由调用方直接不放进列表（与徽标同样策略）。
 */
public class SystemStatsDialog extends BaseDialog {

    public interface Provider {
        List<String[]> snapshot();
    }

    private final Provider provider;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    public SystemStatsDialog(@NonNull Context context, Provider provider) {
        super(context);
        this.provider = provider;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        handler.post(tick);
    }

    @Override
    public void dismiss() {
        handler.removeCallbacks(tick);
        super.dismiss();
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

    private android.view.View buildContent() {
        Context context = getContext();
        int padding = (int) context.getResources().getDimension(R.dimen.vs_30);
        float titleSize = context.getResources().getDimension(R.dimen.ts_30);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF21E293B);
        background.setCornerRadius(context.getResources().getDimension(R.dimen.vs_20));
        root.setBackground(background);

        TextView title = new TextView(context);
        title.setText("运行状态");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, titleSize);
        title.setPadding(0, 0, 0, (int) context.getResources().getDimension(R.dimen.vs_20));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        TextView close = new TextView(context);
        close.setText("关闭");
        close.setTextColor(0xFF0F172A);
        close.setGravity(Gravity.CENTER);
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

        return root;
    }

    private void refresh() {
        if (list == null || provider == null) return;
        List<String[]> rows = provider.snapshot();
        list.removeAllViews();
        if (rows == null || rows.isEmpty()) return;
        Context context = getContext();
        float textSize = context.getResources().getDimension(R.dimen.ts_24);
        int rowPadding = (int) context.getResources().getDimension(R.dimen.vs_10);
        for (String[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(0, rowPadding, 0, rowPadding);

            TextView name = new TextView(context);
            name.setText(row[0]);
            name.setTextColor(0xFF9CA3AF);
            name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
            item.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView value = new TextView(context);
            value.setText(row[1]);
            value.setTextColor(0xFFFFFFFF);
            value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
            value.setGravity(Gravity.END);
            item.addView(value, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            list.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
