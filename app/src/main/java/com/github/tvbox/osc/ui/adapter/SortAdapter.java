package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    public interface OnItemActivatedListener {
        void onItemActivated(int position, View itemView);
    }

    private boolean categoryMode;
    private OnItemActivatedListener itemActivatedListener;

    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    public void setCategoryMode(boolean categoryMode) {
        if (this.categoryMode == categoryMode) return;
        this.categoryMode = categoryMode;
        notifyDataSetChanged();
    }

    public void setOnItemActivatedListener(OnItemActivatedListener listener) {
        this.itemActivatedListener = listener;
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        String title = categoryMode && "主页".equals(item.name) ? "全部" : item.name;
        helper.setText(R.id.tvTitle, title);
        // TvRecyclerView only installs its own click proxy on items without a listener. Setting one
        // here keeps touch/DPAD activation working even when the pager is rebuilt after a source
        // switch, which is exactly when the old position-only path used to go silent.
        helper.itemView.setOnClickListener(v -> {
            if (itemActivatedListener != null) {
                int position = helper.getLayoutPosition();
                if (position != RecyclerView.NO_POSITION) {
                    itemActivatedListener.onItemActivated(position, v);
                }
            }
        });
    }
}
