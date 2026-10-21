package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

public final class ConfirmClearCacheDialog extends BaseDialog {
    public ConfirmClearCacheDialog(@NonNull Context context, Runnable onConfirm) {
        super(context);
        setContentView(R.layout.dialog_confirm);
        setCanceledOnTouchOutside(true);
        ((TextView) findViewById(R.id.confirmation)).setText("确定清空临时缓存吗？");
        TextView confirm = findViewById(R.id.btnConfirm);
        TextView cancel = findViewById(R.id.btnCancel);
        confirm.setText("清理");
        cancel.setText("取消");
        confirm.setOnClickListener(v -> {
            dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        cancel.setOnClickListener(v -> dismiss());
    }
}
