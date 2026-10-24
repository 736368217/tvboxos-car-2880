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
 *
 * <p>三种返回方式：边缘拉出（可调杠） / 悬浮球 / 贴边滑动（复刻 SNG 的拉伸手柄）。</p>
 */
public class BackBarSettingsDialog extends BaseDialog {

    private static final int[] BAR_COLORS = {
            0xFF38BDF8, 0xFFFFFFFF, 0xFFFBBF24, 0xFF34D399, 0xFFF472B6
    };
    private static final String[] BAR_COLOR_NAMES = {"青", "白", "琥珀", "绿", "粉"};
    private static final int[] SWIPE_STEPS = {60, 84, 110};
    private static final String[] SWIPE_NAMES = {"近", "中", "远"};

    private final Runnable onChanged;
    private TextView tvEnabled, tvMode, tvSize, tvColor, tvAlpha, tvHide, tvSwipe;
    private View rowSize, rowColor, rowAlpha, rowHide, rowSwipe;

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
        title.setText("返回组件");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, titleSize);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, (int) context.getResources().getDimension(R.dimen.vs_20));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Row enabled = addRow(root, "开关", v -> {
            InAppBackButton.setEnabled(ownerActivity(), !InAppBackButton.isEnabled());
            refresh();
        });
        tvEnabled = enabled.value;

        Row mode = addRow(root, "返回模式", v -> {
            String current = InAppBackButton.getMode();
            String next;
            if (HawkConfig.BACK_MODE_EDGE.equals(current)) next = HawkConfig.BACK_MODE_FLOATING;
            else if (HawkConfig.BACK_MODE_FLOATING.equals(current)) next = HawkConfig.BACK_MODE_SWIPE;
            else next = HawkConfig.BACK_MODE_EDGE;
            InAppBackButton.setMode(ownerActivity(), next);
            refresh();
        });
        tvMode = mode.value;

        Row size = addRow(root, "返回杠大小", v -> {
            int width = InAppBackButton.getBarWidthDp();
            int nextWidth = width <= 8 ? 12 : width <= 14 ? 18 : 6;
            int nextHeight = nextWidth <= 8 ? 64 : nextWidth <= 14 ? 96 : 140;
            InAppBackButton.setBarWidthDp(ownerActivity(), nextWidth);
            InAppBackButton.setBarHeightDp(ownerActivity(), nextHeight);
            refresh();
        });
        tvSize = size.value;
        rowSize = size.container;

        Row color = addRow(root, "返回杠颜色", v -> {
            int current = InAppBackButton.getBarColor();
            int index = 0;
            for (int i = 0; i < BAR_COLORS.length; i++) {
                if (BAR_COLORS[i] == current) {
                    index = i;
                    break;
                }
            }
            InAppBackButton.setBarColor(ownerActivity(), BAR_COLORS[(index + 1) % BAR_COLORS.length]);
            refresh();
        });
        tvColor = color.value;
        rowColor = color.container;

        Row alpha = addRow(root, "返回杠透明度", v -> {
            int value = InAppBackButton.getBarAlpha();
            int next = value < 50 ? 60 : value < 70 ? 80 : value < 90 ? 100 : 40;
            InAppBackButton.setBarAlpha(ownerActivity(), next);
            refresh();
        });
        tvAlpha = alpha.value;
        rowAlpha = alpha.container;

        Row hide = addRow(root, "自动隐藏", v -> {
            int seconds = InAppBackButton.getHideSeconds();
            int next = seconds == 4 ? 6 : seconds == 6 ? 8 : seconds == 8 ? 0 : 4;
            InAppBackButton.setHideSeconds(ownerActivity(), next);
            refresh();
        });
        tvHide = hide.value;
        rowHide = hide.container;

        Row swipe = addRow(root, "滑动距离", v -> {
            int current = InAppBackButton.getSwipeTriggerDp();
            int next = SWIPE_STEPS[1];
            for (int i = 0; i < SWIPE_STEPS.length; i++) {
                if (SWIPE_STEPS[i] == current) {
                    next = SWIPE_STEPS[(i + 1) % SWIPE_STEPS.length];
                    break;
                }
            }
            InAppBackButton.setSwipeTriggerDp(ownerActivity(), next);
            refresh();
        });
        tvSwipe = swipe.value;
        rowSwipe = swipe.container;

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

    private static final class Row {
        final LinearLayout container;
        final TextView value;

        Row(LinearLayout container, TextView value) {
            this.container = container;
            this.value = value;
        }
    }

    private Row addRow(LinearLayout parent, String label, View.OnClickListener click) {
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
        return new Row(row, value);
    }

    private android.app.Activity ownerActivity() {
        Context context = getContext();
        return context instanceof android.app.Activity ? (android.app.Activity) context : null;
    }

    private void refresh() {
        tvEnabled.setText(InAppBackButton.isEnabled() ? "开启" : "关闭");
        String mode = InAppBackButton.getMode();
        boolean swipeMode = HawkConfig.BACK_MODE_SWIPE.equals(mode);
        tvMode.setText(HawkConfig.BACK_MODE_SWIPE.equals(mode) ? "边缘滑动"
                : HawkConfig.BACK_MODE_FLOATING.equals(mode) ? "悬浮球"
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

        int trigger = InAppBackButton.getSwipeTriggerDp();
        String triggerName = "中";
        for (int i = 0; i < SWIPE_STEPS.length; i++) {
            if (SWIPE_STEPS[i] == trigger) triggerName = SWIPE_NAMES[i];
        }
        tvSwipe.setText(triggerName);

        // 只显示当前模式真正会用到的行，避免设置项越堆越乱。
        int barVisibility = swipeMode ? View.GONE : View.VISIBLE;
        rowSize.setVisibility(barVisibility);
        rowColor.setVisibility(barVisibility);
        rowAlpha.setVisibility(barVisibility);
        rowHide.setVisibility(barVisibility);
        rowSwipe.setVisibility(swipeMode ? View.VISIBLE : View.GONE);

        if (onChanged != null) onChanged.run();
        // 下一帧再刷一次：弹窗刚打开时宿主可能还没完成测量，第一次 configure 会用错尺寸。
        final android.app.Activity activity = ownerActivity();
        if (activity != null && activity.getWindow() != null) {
            activity.getWindow().getDecorView().post(() -> InAppBackButton.refresh(activity));
        }
    }
}
