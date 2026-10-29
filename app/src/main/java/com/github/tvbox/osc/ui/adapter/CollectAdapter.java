package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class CollectAdapter extends BaseQuickAdapter<VodCollect, BaseViewHolder> {
    public CollectAdapter() {
        super(R.layout.item_grid, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodCollect item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        tvDel.setVisibility(HawkConfig.hotVodDelete ? View.VISIBLE : View.GONE);

        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
        helper.setVisible(R.id.tvNote, false);
        helper.setText(R.id.tvName, item.name);
        TextView tvYear = helper.getView(R.id.tvYear);
        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        tvYear.setText(source != null ? source.getName() : "");

        ImageView ivThumb = helper.getView(R.id.ivThumb);
        if (!TextUtils.isEmpty(item.pic)) {
            // 与历史页同策略：先读本地缓存，命中就零网络；否则加载并顺手落盘。
            String cover = com.github.tvbox.osc.util.DefaultConfig.checkReplaceProxy(item.pic);
            int width = AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth);
            int height = AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight);
            String local = com.github.tvbox.osc.cache.CoverStore.findLocalPath(mContext, cover, item.name, item.sourceKey);
            if (!TextUtils.isEmpty(local)) {
                ImgUtil.load(new java.io.File(local).getAbsolutePath(), ivThumb,
                        AutoSizeUtils.mm2px(mContext, 10), width, height, item.name);
            } else {
                ImgUtil.load(cover, ivThumb, AutoSizeUtils.mm2px(mContext, 10), width, height, item.name);
                com.github.tvbox.osc.cache.CoverStore.remember(mContext, cover, item.name, item.sourceKey);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
    }
}