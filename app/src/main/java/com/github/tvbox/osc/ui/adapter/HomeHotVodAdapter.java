package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.util.TypedValue;
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
    /** 首页按可用空间算出来的卡片尺寸（px）；为 0 时回落到布局里的默认尺寸。 */
    private int cardWidthPx, cardHeightPx;

    public HomeHotVodAdapter(ImgUtil.Style style, String tvRate) {
        super(R.layout.item_user_hot_vod, new ArrayList<>());
        if (style != null) {
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
        this.tvRateValue = tvRate;
    }

    /** 卡片宽高比（宽/高），用于按可用高度反推宽度。 */
    public float getCardRatio() {
        if (style != null && style.ratio > 0) return style.ratio;
        return defaultWidth / (float) Math.max(1, ImgUtil.defaultHeight);
    }

    /** 用首页实际可用空间设置卡片尺寸，让封面铺满而不是留一大片空白。 */
    public void setCardSizePx(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) return;
        if (widthPx == cardWidthPx && heightPx == cardHeightPx) return;
        cardWidthPx = widthPx;
        cardHeightPx = heightPx;
        notifyDataSetChanged();
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
        if (item.note == null || item.note.isEmpty()) {
            tvNote.setVisibility(View.GONE);
        } else {
            tvNote.setText(item.note);
            tvNote.setVisibility(View.VISIBLE);
        }
        TextView tvName = helper.getView(R.id.tvName);
        tvName.setText(item.name);
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if (style != null) {
            newWidth = defaultWidth;
            newHeight = (int) (newWidth / style.ratio);
        }
        int targetW = cardWidthPx > 0 ? cardWidthPx : AutoSizeUtils.mm2px(mContext, newWidth);
        int targetH = cardHeightPx > 0 ? cardHeightPx : AutoSizeUtils.mm2px(mContext, newHeight);

        String pic = item.pic == null ? "" : item.pic.trim();
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
            } else {
                ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), targetW, targetH, item.name);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
        applyCardSize(ivThumb, tvName, tvNote, tvRate, targetW, targetH);
    }

    private void applyCardSize(ImageView ivThumb, TextView tvName, TextView tvNote, TextView tvRate,
                               int targetW, int targetH) {
        ViewGroup container = (ViewGroup) ivThumb.getParent();
        if (container != null) {
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            if (containerParams.width != targetW || containerParams.height != targetH) {
                containerParams.width = targetW;
                containerParams.height = targetH;
                container.setLayoutParams(containerParams);
            }
        }
        if (cardHeightPx > 0) {
            // 卡片变大后文字跟着放大，避免“大封面小字”。
            tvName.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(18f, targetH * .072f));
            tvNote.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(14f, targetH * .050f));
            tvRate.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(14f, targetH * .048f));
        }
    }
}
