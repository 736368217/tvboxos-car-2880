package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

/**
 * 设置类弹窗的统一外壳：与 App 原有弹窗（「指定搜索源」等）共用同一套底板、
 * 标题字号、行背景与底部按钮，避免自绘弹窗风格跑偏。
 */
class SettingSheetDialog extends BaseDialog {

    private final TextView title;
    private TextView close;
    protected final LinearLayout rows;
    /** 车机/遥控常把一次按下派发成两次点击，这里做统一防抖，避免一次点击跳两档或连开两个弹窗。 */
    private long lastClickMs;
    /** 本弹窗 show() 的时刻；同一次按下的第二次派发会落在刚打开的弹窗上，必须忽略。 */
    private long shownAtMs;

    SettingSheetDialog(@NonNull Context context, String titleText) {
        super(context);
        setContentView(R.layout.dialog_setting_sheet);
        setCanceledOnTouchOutside(true);
        title = findViewById(R.id.title);
        title.setText(titleText);
        rows = findViewById(R.id.rows);
        close = findViewById(R.id.close);
        close.setOnClickListener(v -> { if (acceptClick()) dismiss(); });
    }

    protected void setTitleText(String text) {
        if (title != null) title.setText(text);
    }

    protected void clearRows() {
        if (rows != null) rows.removeAllViews();
    }

    /** 可点击的设置行：左名称、右当前值。 */
    protected TextView addRow(String name, String value, View.OnClickListener click) {
        LinearLayout row = (LinearLayout) LayoutInflater.from(getContext())
                .inflate(R.layout.item_dialog_setting_row, rows, false);
        ((TextView) row.findViewById(R.id.name)).setText(name);
        TextView valueView = row.findViewById(R.id.value);
        if (click != null) {
            row.setOnClickListener(v -> {
                long dt = android.os.SystemClock.uptimeMillis() - shownAtMs;
                boolean accepted = acceptClick();
                // 车机现场的诊断入口：同一行若出现 dt 极小的两次点击，即证明「一次按下被派发两次」。
                android.util.Log.i("CarSheet", "row " + name + " dt=" + dt
                        + " accepted=" + accepted + " class=" + getClass().getSimpleName());
                if (!accepted) return;
                click.onClick(v);
            });
        } else {
            row.setFocusable(false);
            row.setClickable(false);
        }
        rows.addView(row);
        return valueView;
    }

    /**
     * 可勾选的行：右侧显示当前选中项的「✓」。
     *
     * <p>用于把二级弹窗压平进一层 —— 例如「返回组件」直接列出
     * 关闭/边缘拉出/悬浮球/边缘滑动 四项，点哪项就是哪项，不再先开弹窗再选。</p>
     */
    protected com.github.tvbox.osc.ui.dialog.SettingSheetDialog.OptionRow addOption(
            String name, boolean selected, View.OnClickListener click) {
        LinearLayout row = (LinearLayout) LayoutInflater.from(getContext())
                .inflate(R.layout.item_dialog_setting_row, rows, false);
        TextView nameView = row.findViewById(R.id.name);
        nameView.setText(selected ? "✓ " + name : name);
        TextView valueView = row.findViewById(R.id.value);
        valueView.setVisibility(View.GONE);
        if (click != null) {
            row.setOnClickListener(v -> {
                long dt = android.os.SystemClock.uptimeMillis() - shownAtMs;
                boolean accepted = acceptClick();
                android.util.Log.i("CarSheet", "option " + name + " dt=" + dt
                        + " accepted=" + accepted + " class=" + getClass().getSimpleName());
                if (!accepted) return;
                click.onClick(v);
            });
        }
        rows.addView(row);
        return new OptionRow(row, nameView, name);
    }

    /** {@link #addOption} 返回的句柄：用于原地刷新选中态。 */
    static final class OptionRow {
        final LinearLayout row;
        final TextView name;
        final String label;

        OptionRow(LinearLayout row, TextView name, String label) {
            this.row = row;
            this.name = name;
            this.label = label;
        }

        void setSelected(boolean selected) {
            name.setText(selected ? "✓ " + label : label);
        }
    }

    /**
     * 取宿主 Activity。BaseDialog 用的是 {@code Dialog(context, styleRes)} 构造，
     * Android 会把传入的 context 包成 ContextThemeWrapper，所以 {@code getContext() instanceof Activity}
     * 恒为 false —— 必须逐层解包，否则拿到 null、设置写进 Hawk 却从不刷新正在运行的返回组件。
     */
    protected android.app.Activity ownerActivity() {
        android.content.Context context = getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof android.app.Activity) return (android.app.Activity) context;
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return context instanceof android.app.Activity ? (android.app.Activity) context : null;
    }

    protected void setCloseText(String text) {
        if (close != null) close.setText(text);
    }

    private static java.lang.ref.WeakReference<SettingSheetDialog> CURRENT;

    /**
     * 同一个设置类弹窗只允许存在一个同类实例，避免车机重复派发点击时叠出多层。
     * 只替换同类：父弹窗（高级设置）打开子弹窗（返回组件）时不能被顶掉。
     */
    @Override
    public void show() {
        SettingSheetDialog previous = CURRENT == null ? null : CURRENT.get();
        if (previous != null && previous != this
                && previous.getClass() == getClass() && previous.isShowing()) {
            previous.dismiss();
        }
        super.show();
        shownAtMs = android.os.SystemClock.uptimeMillis();
        CURRENT = new java.lang.ref.WeakReference<>(this);
    }

    /**
     * 丢弃两类重复点击：
     * 1) 弹窗刚打开就收到的点击 —— 这是触发本弹窗的那一次按下的第二次派发，落在首行会直接把开关改回原值，
     *    表现就是「点什么都没反应 / 开关永远是关闭」；
     * 2) 同一弹窗内 400ms 内的第二次点击。
     */
    private boolean acceptClick() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - shownAtMs < 300L) return false;
        if (now - lastClickMs < 400L) return false;
        lastClickMs = now;
        return true;
    }
}
