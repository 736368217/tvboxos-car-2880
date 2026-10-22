package com.github.tvbox.osc.callback;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.kingja.loadsir.callback.Callback;

/** Empty state that exposes an explicit retry action for fallback/detail failures. */
public class RetryEmptyCallback extends Callback {
    @Override
    protected int onCreateView() {
        return R.layout.loadsir_empty_retry;
    }

    @Override
    protected void onViewCreate(Context context, View root) {
        super.onViewCreate(context, root);
        TextView retry = root.findViewById(R.id.uxEmptyRetry);
        if (retry != null) retry.setOnClickListener(v -> root.performClick());
        TextView back = root.findViewById(R.id.uxEmptyBack);
        if (back != null) back.setOnClickListener(v -> {
            if (context instanceof Activity) ((Activity) context).onBackPressed();
        });
    }
}
