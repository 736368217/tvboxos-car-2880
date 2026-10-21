package com.github.tvbox.osc.ui.adapter;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
public class SeriesFlagAdapter extends BaseQuickAdapter<VodInfo.VodSeriesFlag, BaseViewHolder> {
    public SeriesFlagAdapter() {
        super(R.layout.item_series_flag, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeriesFlag item) {
        TextView tvSeries = helper.getView(R.id.tvSeriesFlag);
        View select = helper.getView(R.id.tvSeriesFlagSelect);
        if (item.selected) {
            select.setVisibility(View.VISIBLE);
        } else {
            select.setVisibility(View.GONE);
        }
        helper.setText(R.id.tvSeriesFlag, displaySeriesFlag(item.name));
        View mSeriesGroupTv = ((Activity) helper.itemView.getContext()).findViewById(R.id.mSeriesGroupTv);
        View mGridViewQuality = ((Activity) helper.itemView.getContext()).findViewById(R.id.mGridViewQuality);
        if (mSeriesGroupTv != null && mSeriesGroupTv.getVisibility() == View.VISIBLE) {
            helper.itemView.setNextFocusDownId(R.id.mSeriesSortTv);
        } else if (mGridViewQuality != null && mGridViewQuality.getVisibility() == View.VISIBLE) {
            helper.itemView.setNextFocusDownId(R.id.mGridViewQuality);
        }else {
            helper.itemView.setNextFocusDownId(R.id.mGridView);
        }
        if (helper.getLayoutPosition() == getData().size() - 1) {
            helper.itemView.setId(View.generateViewId());
            helper.itemView.setNextFocusRightId(helper.itemView.getId());
        }else {
            helper.itemView.setNextFocusRightId(View.NO_ID);
        }
    }
    private String displaySeriesFlag(String name) {
        if (name == null) return "";
        int marker = name.lastIndexOf("共(");
        if (marker <= 0 || !name.endsWith(")集")) return name;
        String count = name.substring(marker + 2, name.length() - 2);
        if (!count.matches("\\d+")) return name;
        String source = name.substring(0, marker).trim().replaceAll("([A-Za-z]+)$", " $1");
        return source + " · 共 " + count + " 集";
    }
}