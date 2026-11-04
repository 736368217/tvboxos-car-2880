package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HistoryAdapter extends BaseQuickAdapter<VodInfo, BaseViewHolder> {
    public HistoryAdapter() {
        super(R.layout.item_grid, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        tvDel.setVisibility(HawkConfig.hotVodDelete ? View.VISIBLE : View.GONE);

        TextView tvYear = helper.getView(R.id.tvYear);
        SourceBean bean = ApiConfig.get().getSource(item.sourceKey);
        tvYear.setText(bean != null ? bean.getName() : "搜");
        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
        if (item.note == null || item.note.isEmpty()) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        loadCover(ivThumb, item);
    }

    /**
     * 历史封面：**先本地缓存，再网络**。
     *
     * <p>历史记录只存了封面 URL，能否显示原先完全取决于 Glide 的按需缓存。站点换图床、
     * 删图或者车机没网时，已经看过的片子会退回「随机纯色块 + 片名首字」，看起来像封面坏了。
     * 现在看过一次就会落盘，这里优先读本地文件，命中时零网络。</p>
     */
    private void loadCover(ImageView ivThumb, VodInfo item) {
        int widthPx = AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth);
        int heightPx = AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight);
        com.github.tvbox.osc.cache.CoverBinder.bind(ivThumb, item.pic, item.name,
                item.sourceKey, widthPx, heightPx);
    }
}