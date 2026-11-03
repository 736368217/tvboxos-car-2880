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
        int width = AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth);
        int height = AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight);
        String cover = TextUtils.isEmpty(item.pic)
                ? "" : com.github.tvbox.osc.util.DefaultConfig.checkReplaceProxy(item.pic);
        if (TextUtils.isEmpty(cover)) {
            String byContent = com.github.tvbox.osc.cache.CoverStore.findLocalPathByContent(
                    mContext, item.name, item.sourceKey);
            if (!TextUtils.isEmpty(byContent)) {
                ImgUtil.load(new java.io.File(byContent).getAbsolutePath(), ivThumb,
                        AutoSizeUtils.mm2px(mContext, 10), width, height, item.name);
            } else {
                ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            }
            return;
        }
        // 与历史页同策略：先读本地缓存，命中就零网络；否则加载并顺手落盘。
        String local = com.github.tvbox.osc.cache.CoverStore.findLocalPath(mContext, cover, item.name, item.sourceKey);
        if (!TextUtils.isEmpty(local)) {
            ImgUtil.load(new java.io.File(local).getAbsolutePath(), ivThumb,
                    AutoSizeUtils.mm2px(mContext, 10), width, height, item.name);
            return;
        }
        ImgUtil.load(cover, ivThumb, AutoSizeUtils.mm2px(mContext, 10), width, height, item.name);
        ivThumb.setTag(R.id.ivThumb, cover);
        // 落盘成功立刻换真图（车机网络慢，不能只等这一手网络加载）。
        com.github.tvbox.osc.cache.CoverStore.remember(mContext, cover, item.name, item.sourceKey, () -> {
            String ready = com.github.tvbox.osc.cache.CoverStore.findLocalPath(
                    mContext, cover, item.name, item.sourceKey);
            if (TextUtils.isEmpty(ready)) return;
            Object bound = ivThumb.getTag(R.id.ivThumb);
            if (!(bound instanceof String) || !cover.equals(bound)) return;
            ImgUtil.load(new java.io.File(ready).getAbsolutePath(), ivThumb,
                    AutoSizeUtils.mm2px(mContext, 10), width, height, item.name);
        });
    }
}