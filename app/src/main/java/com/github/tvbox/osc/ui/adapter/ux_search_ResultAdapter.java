package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.ImgUtil;

/** Scene-local result rows; never changes the shared fast-search adapter or its preference. */
public final class ux_search_ResultAdapter extends SearchAdapter {
    public ux_search_ResultAdapter() {
        super();
        mLayoutResId = R.layout.ux_search_result;
    }

    @Override protected void convert(BaseViewHolder holder, Movie.Video item) {
        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        holder.setText(R.id.tvName, item.name);
        holder.setText(R.id.tvSite, source == null ? "" : "来源 · " + source.getName());
        String info = TextUtils.isEmpty(item.type) ? "" : item.type;
        if (item.year > 0) info += "  " + item.year;
        if (!TextUtils.isEmpty(item.note)) info += "  " + item.note;
        if (!TextUtils.isEmpty(item.actor)) info += "  " + item.actor;
        holder.setText(R.id.tvNote, info.trim());
        ImageView poster = holder.getView(R.id.ivThumb);
        if (TextUtils.isEmpty(item.pic)) poster.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        else ImgUtil.load(item.pic, poster, 14, 130, 165, item.name);
        holder.itemView.setContentDescription(item.name + "，" + (source == null ? "" : source.getName()) + "，查看播放");
    }
}
