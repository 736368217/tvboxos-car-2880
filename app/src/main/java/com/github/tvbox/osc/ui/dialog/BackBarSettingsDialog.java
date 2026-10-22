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
import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.util.HawkConfig;

/**
 * 返回组件的二级设置：设置页只留一个入口，具体开关/模式/外观都在这里调。
 * 每一行点击即循环取值，改完立即生效（InAppBackButton 内部会 refresh 当前页面）。
 */
public class BackBarSettingsDialog extends BaseDialog {

    private static final int[] BAR_COLORS = {
            0xFF38BDF8, 0xFFFFFFFF, 0xFFFBBF24, 0xFF34D399, 0xFFF472B6
    };
    private static final String[] BAR_COLOR_NAMES = {"青", "白", "琥珀", "绿", "粉"};

    private final Runnable onChanged;
    private TextView tvEnabled, tvMode, tvSize, tvColor, tvAlpha, tvHide;

    public BackBarSettingsDialog(@NonNull Context context, Runnable onChanged) {
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
        float titleSize = context.getResources().getDimension(R.dimen.ts_30);
        int padding = (int) context.getResources().getDimension(R.dimen.vs_30);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF21E293B);
        background.setCornerRadius(context.getResources().getDimension(R.dimen.vs_20));
        root.setBackground(background);

        TextView title = new TextView(context);
        title.setText("返回组件");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, titleSize);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, (int) context.getResources().getDimension(R.dimen.vs_20));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvEnabled = addRow(root, "开关", v -> {
            InAppBackButton.setEnabled(ownerActivity(), !InAppBackButton.isEnabled());
            refresh();
        });
        tvMode = addRow(root, "返回模式", v -> {
            String mode = InAppBackButton.getMode();
            String next;
            if (HawkConfig.BACK_MODE_EDGE.equals(mode)) next = HawkConfig.BACK_MODE_FLOATING;
            else if (HawkConfig.BACK_MODE_FLOATING.equals(mode)) next = HawkConfig.BACK_MODE_BOTH;
            else next = HawkConfig.BACK_MODE_EDGE;
            InAppBackButton.setMode(ownerActivity(), next);
            refresh();
        });
        tvSize = addRow(root, "返回杠大小", v -> {
            int width = InAppBackButton.getBarWidthDp();
            int nextWidth = width <= 8 ? 12 : width <= 14 ? 18 : 6;
            int nextHeight = nextWidth <= 8 ? 64 : nextWidth <= 14 ? 96 : 140;
            InAppBackButton.setBarWidthDp(ownerActivity(), nextWidth);
            InAppBackButton.setBarHeightDp(ownerActivity(), nextHeight);
            refresh();
        });
        tvColor = addRow(root, "返回杠颜色", v -> {
            int color = InAppBackButton.getBarColor();
            int index = 0;
            for (int i = 0; i < BAR_COLORS.length; i++) {
                if (BAR_COLORS[i] == color) {
                    index = i;
                    break;
                }
            }
            InAppBackButton.setBarColor(ownerActivity(), BAR_COLORS[(index + 1) % BAR_COLORS.length]);
            refresh();
        });
        tvAlpha = addRow(root, "返回杠透明度", v -> {
            int alpha = InAppBackButton.getBarAlpha();
            int next = alpha < 50 ? 60 : alpha < 70 ? 80 : alpha < 90 ? 100 : 40;
            InAppBackButton.setBarAlpha(ownerActivity(), next);
            refresh();
        });
        tvHide = addRow(root, "自动隐藏", v -> {
            int seconds = InAppBackButton.getHideSeconds();
            int next = seconds == 4 ? 6 : seconds == 6 ? 8 : seconds == 8 ? 0 : 4;
            InAppBackButton.setHideSeconds(ownerActivity(), next);
            refresh();
        });

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

        refresh();
        return root;
    }

    private TextView addRow(LinearLayout parent, String label, View.OnClickListener click) {
        Context context = getContext();
        int rowHeight = (int) context.getResources().getDimension(R.dimen.vs_60);
        int side = (int) context.getResources().getDimension(R.dimen.vs_20);
        float textSize = context.getResources().getDimension(R.dimen.ts_24);

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
        value.setTextColor(0xFFFFFFFF);
        value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
        row.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        params.topMargin = (int) context.getResources().getDimension(R.dimen.vs_10);
        parent.addView(row, params);
        return value;
    }

    private android.app.Activity ownerActivity() {
        Context context = getContext();
        return context instanceof android.app.Activity ? (android.app.Activity) context : null;
    }

    private void refresh() {
        tvEnabled.setText(InAppBackButton.isEnabled() ? "开启" : "关闭");
        String mode = InAppBackButton.getMode();
        tvMode.setText(HawkConfig.BACK_MODE_FLOATING.equals(mode) ? "悬浮球"
                : HawkConfig.BACK_MODE_BOTH.equals(mode) ? "双模共存" : "边缘拉出");
        int width = InAppBackButton.getBarWidthDp();
        tvSize.setText(width <= 8 ? "细" : width <= 14 ? "中" : "粗");
        int color = InAppBackButton.getBarColor();
        String name = "自定义";
        for (int i = 0; i < BAR_COLORS.length; i++) {
            if (BAR_COLORS[i] == color) {
                name = BAR_COLOR_NAMES[i];
                break;
            }
        }
        tvColor.setText(name);
        tvAlpha.setText(InAppBackButton.getBarAlpha() + "%");
        int seconds = InAppBackButton.getHideSeconds();
        tvHide.setText(seconds == 0 ? "常驻" : seconds + " 秒");
        if (onChanged != null) onChanged.run();
    }
}
