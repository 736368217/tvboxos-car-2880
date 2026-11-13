package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/** A bounded native settings panel: one category, a few controls, no nested sheets. */
public class AdvancedSettingsDialog extends BaseDialog {
    private static final int WHITE = 0xFFF1F5F9;
    private static final int MUTED = 0xFFA8B5C5;
    private static final int ACCENT = 0xFF8BCBE2;
    private final Runnable onChanged;
    private final Activity owner;
    private final float scale;
    private final int panelWidth, panelHeight;
    private final LinearLayout content;
    private final TextView[] tabs = new TextView[3];
    private int category;
    private long lastClick;

    public AdvancedSettingsDialog(@NonNull Context context, Runnable onChanged) {
        super(context);
        this.onChanged = onChanged;
        Context candidate = context;
        while (candidate instanceof ContextWrapper && !(candidate instanceof Activity)) {
            candidate = ((ContextWrapper) candidate).getBaseContext();
        }
        owner = candidate instanceof Activity ? (Activity) candidate : null;
        DisplayMetrics dm = new DisplayMetrics();
        if (owner != null) owner.getWindowManager().getDefaultDisplay().getMetrics(dm);
        else dm.setTo(context.getResources().getDisplayMetrics());
        scale = Math.min(dm.widthPixels / 2880f, dm.heightPixels / 1620f);
        panelWidth = px(2160);
        panelHeight = px(1180);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(52), px(36), px(52), px(36));
        panel.setBackgroundResource(R.drawable.shape_danmu_setting_bg);
        setContentView(panel, new ViewGroup.LayoutParams(panelWidth, panelHeight));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("高级设置", 54, WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, px(90), 1));
        TextView close = button("完成");
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(px(210), px(82)));
        panel.addView(header);
        View rule = new View(context);
        rule.setBackgroundColor(0x33475569);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, px(2));
        ruleParams.setMargins(0, px(22), 0, px(32));
        panel.addView(rule, ruleParams);

        LinearLayout body = new LinearLayout(context);
        LinearLayout nav = new LinearLayout(context);
        nav.setOrientation(LinearLayout.VERTICAL);
        String[] labels = {"播放与声音", "返回操作", "其他"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            tabs[i] = button(labels[i]);
            tabs[i].setGravity(Gravity.CENTER_VERTICAL);
            tabs[i].setPadding(px(24), 0, px(24), 0);
            tabs[i].setOnClickListener(v -> { category = index; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(112));
            lp.bottomMargin = px(16);
            nav.addView(tabs[i], lp);
        }
        body.addView(nav, new LinearLayout.LayoutParams(px(370), -1));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(48), 0, 0, 0);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        panel.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setCanceledOnTouchOutside(true);
        render();
    }

    @Override public void show() {
        super.show();
        if (getWindow() != null) getWindow().setLayout(panelWidth, panelHeight);
    }

    private int px(float value) { return Math.max(1, Math.round(value * scale)); }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable selectedBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF243D50);
        bg.setCornerRadius(px(16));
        bg.setStroke(px(2), ACCENT);
        return bg;
    }

    private TextView button(String name) {
        TextView view = text(name, 36, WHITE, false);
        view.setGravity(Gravity.CENTER);
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackgroundResource(R.drawable.button_danmu_setting);
        return view;
    }

    private boolean acceptClick() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastClick < 350) return false;
        lastClick = now;
        return true;
    }

    private void changed() {
        render();
        if (onChanged != null) onChanged.run();
    }

    private void render() {
        for (int i = 0; i < tabs.length; i++) {
            if (i == category) tabs[i].setBackground(selectedBackground());
            else tabs[i].setBackgroundResource(R.drawable.button_danmu_setting);
            tabs[i].setTextColor(i == category ? ACCENT : WHITE);
        }
        content.removeAllViews();
        if (category == 0) {
            heading("声音", "仅调整本应用，系统音量保持不变");
            row("视频音量补偿", "有效声音测量后锁定；" + CarEnhance.audioCompensationStatus(), CarEnhance.sourceLevelMatchEnabled() ? "已开启" : "已关闭", () -> {
                CarEnhance.setSourceLevelMatchEnabled(!CarEnhance.sourceLevelMatchEnabled());
                changed();
            });
            row("应用内音量", "播放时在右侧上下滑动，每档 5%", Math.round(CarEnhance.appGain() * 100) + "%", null);
        } else if (category == 1) {
            heading("返回方式", "边缘手势与返回杠二选一");
            LinearLayout modes = new LinearLayout(getContext());
            String[] names = {"边缘手势", "返回杠", "关闭"};
            String mode = InAppBackButton.getMode();
            int selected = !InAppBackButton.isEnabled() ? 2 : HawkConfig.BACK_MODE_BAR.equals(mode) ? 1 : 0;
            for (int i = 0; i < names.length; i++) {
                final int index = i;
                TextView choice = button(names[i]);
                if (i == selected) choice.setBackground(selectedBackground());
                choice.setTextColor(i == selected ? ACCENT : WHITE);
                choice.setOnClickListener(v -> {
                    if (!acceptClick()) return;
                    if (index != 2) InAppBackButton.setMode(owner, index == 1 ? HawkConfig.BACK_MODE_BAR : HawkConfig.BACK_MODE_EDGE);
                    InAppBackButton.setEnabled(owner, index != 2);
                    changed();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(112), 1);
                if (i > 0) lp.leftMargin = px(16);
                modes.addView(choice, lp);
            }
            LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-1, -2);
            modeParams.bottomMargin = px(32);
            content.addView(modes, modeParams);
            String side = InAppBackButton.getSide();
            row("触发位置", "从屏幕边缘向内滑动；返回杠轻点返回", HawkConfig.BACK_SIDE_RIGHT.equals(side) ? "右侧" : "左侧", () -> {
                InAppBackButton.setSide(owner, HawkConfig.BACK_SIDE_RIGHT.equals(side) ? HawkConfig.BACK_SIDE_LEFT : HawkConfig.BACK_SIDE_RIGHT);
                changed();
            });
            int seconds = InAppBackButton.getHideSeconds();
            row("提示自动隐藏", "隐藏后仍可从边缘发起手势", seconds == 0 ? "常驻" : seconds + " 秒", () -> {
                InAppBackButton.setHideSeconds(owner, seconds == 4 ? 6 : seconds == 6 ? 8 : seconds == 8 ? 0 : 4);
                changed();
            });
        } else {
            heading("其他", "手势偏好与本地诊断");
            boolean brightness = Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false);
            row("亮度手势", "在播放页左侧上下滑动调整窗口亮度", brightness ? "已开启" : "跟随系统", () -> {
                Hawk.put(HawkConfig.BRIGHTNESS_GESTURE, !brightness);
                changed();
            });
            row("诊断信息", "复制版本与功能开关，便于反馈", "复制", () -> {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("TVBox 诊断",
                        "TVBox " + com.github.tvbox.osc.BuildConfig.VERSION_NAME
                        + "\nvolume=" + Math.round(CarEnhance.appGain() * 100)
                        + "% compensation=" + CarEnhance.sourceLevelMatchEnabled()
                        + " back=" + InAppBackButton.getMode()));
                Toast.makeText(getContext(), "诊断信息已复制", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void heading(String title, String subtitle) {
        content.addView(text(title, 44, WHITE, true), new LinearLayout.LayoutParams(-1, px(66)));
        TextView note = text(subtitle, 30, MUTED, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(62));
        lp.bottomMargin = px(26);
        content.addView(note, lp);
    }

    private void row(String title, String subtitle, String value, Runnable action) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(28), px(18), px(28), px(18));
        row.setBackgroundResource(R.drawable.shape_setting_model_focus);
        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 38, WHITE, true), new LinearLayout.LayoutParams(-1, px(62)));
        labels.addView(text(subtitle, 28, MUTED, false), new LinearLayout.LayoutParams(-1, px(52)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = text(value, 34, action == null ? MUTED : ACCENT, false);
        state.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(state, new LinearLayout.LayoutParams(px(210), px(100)));
        row.setFocusable(action != null);
        row.setClickable(action != null);
        if (action != null) row.setOnClickListener(v -> { if (acceptClick()) action.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(164));
        lp.bottomMargin = px(20);
        content.addView(row, lp);
    }
}
