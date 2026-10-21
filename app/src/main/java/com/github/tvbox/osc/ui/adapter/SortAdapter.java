package com.github.tvbox.osc.ui.adapter;

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
    private boolean categoryMode;
    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    public void setCategoryMode(boolean categoryMode) {
        if (this.categoryMode == categoryMode) return;
        this.categoryMode = categoryMode;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        String title = categoryMode && "主页".equals(item.name) ? "全部" : item.name;
        helper.setText(R.id.tvTitle, title);
    }
}