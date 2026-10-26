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

    SettingSheetDialog(@NonNull Context context, String titleText) {
        super(context);
        setContentView(R.layout.dialog_setting_sheet);
        setCanceledOnTouchOutside(true);
        title = findViewById(R.id.title);
        title.setText(titleText);
        rows = findViewById(R.id.rows);
        close = findViewById(R.id.close);
        close.setOnClickListener(v -> dismiss());
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
        valueView.setText(value);
        if (click != null) {
            row.setOnClickListener(click);
        } else {
            row.setFocusable(false);
            row.setClickable(false);
        }
        rows.addView(row);
        return valueView;
    }

    protected void setCloseText(String text) {
        if (close != null) close.setText(text);
    }
}
