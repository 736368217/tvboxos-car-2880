package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.adapter.CheckboxSearchAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class SearchCheckboxDialog extends BaseDialog{

    private TvRecyclerView mGridView;
    private CheckboxSearchAdapter checkboxSearchAdapter;
    public List<SourceBean> mSourceList;
    TextView checkAll;
    TextView clearAll;

    public HashMap<String, String> mCheckSourcees;

    public SearchCheckboxDialog(@NonNull @NotNull Context context, List<SourceBean> sourceList, HashMap<String, String> checkedSources) {
        super(context);
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setCanceledOnTouchOutside(true);
        setCancelable(true);
        mSourceList = sourceList;
        mCheckSourcees = checkedSources;
        setContentView(R.layout.dialog_checkbox_search);
        enableSelectionDismissal(R.id.root);
        initView(context);
    }

    @Override
    public void dismiss() {
        checkboxSearchAdapter.setMCheckedSources();
        super.dismiss();
    }

    protected void initView(Context context) {
        mGridView = findViewById(R.id.mGridView);
        checkAll = findViewById(R.id.checkAll);
        clearAll = findViewById(R.id.clearAll);
        checkboxSearchAdapter = new CheckboxSearchAdapter(new DiffUtil.ItemCallback<SourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getKey().equals(newItem.getKey());
            }

            @Override
            public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getName().equals(newItem.getName());
            }
        });
        mGridView.setHasFixedSize(true);

        int size = mSourceList.size();
        TextView title = findViewById(R.id.uxSearchSourceTitle);
        if (title != null) title.setText(size == 0 ? "选择搜索源" : "选择搜索源 · " + size + " 个站点");
        int spanCount = (int) Math.floor(size / 10);
        if (spanCount <= 0) spanCount = 1;
        if (spanCount > 3) spanCount = 3;
        mGridView.setLayoutManager(new V7GridLayoutManager(getContext(), spanCount));
        View root = findViewById(R.id.root);
        ViewGroup.LayoutParams clp = root.getLayoutParams();
        clp.width = AutoSizeUtils.mm2px(getContext(), 400 + 260 * (spanCount - 1));
        clp.height = ViewGroup.LayoutParams.WRAP_CONTENT;

        mGridView.setAdapter(checkboxSearchAdapter);
        checkboxSearchAdapter.setData(mSourceList, mCheckSourcees);
        int pos = 0;
        if (mCheckSourcees != null) {
            for(int i=0; i<mSourceList.size(); i++) {
                String key = mSourceList.get(i).getKey();
                if (mCheckSourcees.containsKey(key)) {
                    pos = i;
                    break;
                }
            }
        }
//        final int scrollPosition = pos;
//        mGridView.post(new Runnable() {
//            @Override
//            public void run() {
//                mGridView.smoothScrollToPosition(scrollPosition);
//            }
//        });
        checkAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                mCheckSourcees = new HashMap<>();
                for(SourceBean sourceBean : mSourceList) {
                    mCheckSourcees.put(sourceBean.getKey(), "1");
                }
                checkboxSearchAdapter.setData(mSourceList, mCheckSourcees);
            }
        });
        clearAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                mCheckSourcees = new HashMap<>();
                checkboxSearchAdapter.setData(mSourceList, mCheckSourcees);
            }
        });
    }
    @Override
    public void show() {
        super.show();
        if (!isShowing() || getWindow() == null) return;
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        View root = findViewById(R.id.root);
        if (root != null) root.post(this::fitGridHeight);
    }

    private void fitGridHeight() {
        if (mGridView == null || getWindow() == null) return;
        int windowHeight = getWindow().getDecorView().getHeight();
        if (windowHeight <= 0) windowHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        View header = checkAll == null ? null : (View) checkAll.getParent();
        int reserved = header == null ? 0 : header.getMeasuredHeight();
        reserved += getContext().getResources().getDimensionPixelSize(R.dimen.vs_50);
        reserved += getContext().getResources().getDimensionPixelSize(R.dimen.vs_30);
        int available = Math.max(getContext().getResources().getDimensionPixelSize(R.dimen.vs_160), windowHeight - reserved);
        int desired = mGridView.getMeasuredHeight();
        if (desired <= 0) desired = available;
        desired = Math.min(desired, available);
        ViewGroup.LayoutParams params = mGridView.getLayoutParams();
        if (params.height != desired) {
            params.height = desired;
            mGridView.setLayoutParams(params);
        }
    }


    public void setMSourceList(List<SourceBean> SourceBeanList) {
        mSourceList = SourceBeanList;
        checkboxSearchAdapter.setData(mSourceList, mCheckSourcees);
    }
}
