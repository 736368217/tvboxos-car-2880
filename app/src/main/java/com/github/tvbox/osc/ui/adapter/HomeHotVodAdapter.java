package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeHotVodAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private int defaultWidth;
    private final ImgUtil.Style style;
    private String tvRateValue;

    public HomeHotVodAdapter(ImgUtil.Style style, String tvRate) {
        super(R.layout.item_user_hot_vod, new ArrayList<>());
        if (style != null) {
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
        this.tvRateValue = tvRate;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        tvDel.setVisibility(HawkConfig.hotVodDelete ? View.VISIBLE : View.GONE);

        TextView tvRate = helper.getView(R.id.tvRate);
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            SourceBean bean = ApiConfig.get().getSource(item.sourceKey);
            tvRateValue = bean != null ? bean.getName() : "";
        }
        tvRate.setText(tvRateValue);

        TextView tvNote = helper.getView(R.id.tvNote);
        if (!hasMeaningfulNote(item.note)) {
            tvNote.setVisibility(View.GONE);
        } else {
            tvNote.setText(item.note.trim());
            tvNote.setVisibility(View.VISIBLE);
        }
        helper.setText(R.id.tvName, item.name);
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        // This card is hosted in the native 2880px scene; do not apply AutoSize twice.
        int newWidth = 435;
        int newHeight = 520;
        ViewGroup frame = helper.getView(R.id.mItemFrame);
        ViewGroup.LayoutParams frameParams = frame.getLayoutParams();
        frameParams.width = newWidth;
        frameParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        frame.setLayoutParams(frameParams);
        View poster = helper.getView(R.id.posterFrame);
        ViewGroup.LayoutParams posterParams = poster.getLayoutParams();
        posterParams.height = newHeight;
        poster.setLayoutParams(posterParams);
        tvRate.setMaxWidth(newWidth - 20);
        tvNote.setMaxWidth(newWidth - 20);

        String pic = item.pic == null ? "" : item.pic.trim();
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
            } else {
                ImgUtil.load(pic, ivThumb, 18, newWidth, newHeight, item.name);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
        helper.itemView.setContentDescription(item.name);
    }
    public static boolean hasMeaningfulNote(String note) {
        if (TextUtils.isEmpty(note)) return false;
        String value = note.trim().replaceFirst("(?i)^评分\\s*[:：]?\\s*", "").replaceFirst("\\s*(分|/10)$", "").trim();
        if (!value.matches("[-+]?\\d+(\\.\\d+)?")) return true;
        try {
            return Double.parseDouble(value) > 0d;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    public static String readableFeatureNote(String note) {
        return hasMeaningfulNote(note) ? note.trim() : "站点推荐 · 点击查看详情";
    }

}