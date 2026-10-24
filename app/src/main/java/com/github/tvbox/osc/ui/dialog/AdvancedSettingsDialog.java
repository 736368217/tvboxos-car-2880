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
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 设置页唯一的「高级设置」入口：本项目新增的开关全部收在这里，主列表不再逐条膨胀。
 *
 * <p>包含：播放增强（滤镜/超分/平滑/音效）、返回组件、亮度手势、音量增益、长按 3 倍速。</p>
 */
public class AdvancedSettingsDialog extends BaseDialog {

    private static final int[] VOLUME_STEPS = {80, 100, 120, 150, 180, 200};

    private final Runnable onChanged;

    private TextView tvPlayback;
    private TextView tvBackBar;
    private TextView tvBrightness;
    private TextView tvVolume;
    private TextView tvLongPress;

    public AdvancedSettingsDialog(@NonNull Context context, Runnable onChanged) {
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
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.44f);
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
        title.setText("高级设置");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, titleSize);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, (int) context.getResources().getDimension(R.dimen.vs_20));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvPlayback = addRow(root, "播放增强", v ->
                new PlaybackAdvancedDialog(getContext(), null, this::refresh).show());
        tvBackBar = addRow(root, "返回组件", v ->
                new BackBarSettingsDialog(getContext(), this::refresh).show());
        tvBrightness = addRow(root, "亮度手势", v -> {
            Hawk.put(HawkConfig.BRIGHTNESS_GESTURE, !Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false));
            refresh();
        });
        tvVolume = addRow(root, "音量增益", v -> {
            int current = asInt(Hawk.get(HawkConfig.VOLUME_GAIN, 100));
            int next = VOLUME_STEPS[0];
            for (int i = 0; i < VOLUME_STEPS.length; i++) {
                if (VOLUME_STEPS[i] == current) {
                    next = VOLUME_STEPS[(i + 1) % VOLUME_STEPS.length];
                    break;
                }
            }
            Hawk.put(HawkConfig.VOLUME_GAIN, next);
            refresh();
        });
        tvLongPress = addRow(root, "长按 3 倍速", v -> {
            Hawk.put(HawkConfig.LONG_PRESS_SPEED, !Hawk.get(HawkConfig.LONG_PRESS_SPEED, false));
            refresh();
        });

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

    private int asInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        return 100;
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
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setMaxWidth((int) (context.getResources().getDisplayMetrics().widthPixels * 0.30f));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = (int) context.getResources().getDimension(R.dimen.vs_10);
        row.addView(value, valueParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        params.topMargin = (int) context.getResources().getDimension(R.dimen.vs_10);
        parent.addView(row, params);
        return value;
    }

    private void refresh() {
        if (tvPlayback != null) {
            tvPlayback.setText(CarEnhance.summary());
        }
        if (tvBackBar != null) {
            if (!InAppBackButton.isEnabled()) {
                tvBackBar.setText("关闭");
            } else {
                String mode = InAppBackButton.getMode();
                tvBackBar.setText(HawkConfig.BACK_MODE_FLOATING.equals(mode) ? "悬浮球"
                        : HawkConfig.BACK_MODE_BOTH.equals(mode) ? "双模共存" : "边缘拉出");
            }
        }
        if (tvBrightness != null) {
            tvBrightness.setText(Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false) ? "开启" : "关闭");
        }
        if (tvVolume != null) {
            tvVolume.setText(Hawk.get(HawkConfig.VOLUME_GAIN, 100) + "%");
        }
        if (tvLongPress != null) {
            tvLongPress.setText(Hawk.get(HawkConfig.LONG_PRESS_SPEED, false) ? "开启" : "关闭");
        }
        if (onChanged != null) onChanged.run();
    }
}
