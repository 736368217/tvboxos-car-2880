package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;

import java.util.ArrayList;
import java.util.Locale;

/** Native large-touch channel rows. EPG is supplied from the activity's real cache only. */
public class LiveChannelItemAdapter extends BaseQuickAdapter<LiveChannelItem, BaseViewHolder> {
    public interface ProgramTitleProvider {
        String getTitle(LiveChannelItem channel);
    }

    private int selectedChannelIndex = -1;
    private int focusedChannelIndex = -1;
    private LiveChannelItem playingChannel;
    private boolean playing;
    private ProgramTitleProvider programTitleProvider;

    public LiveChannelItemAdapter() {
        super(R.layout.item_live_channel, new ArrayList<>());
    }

    public void setProgramTitleProvider(ProgramTitleProvider provider) {
        programTitleProvider = provider;
    }

    public void setPlaybackState(LiveChannelItem channel, boolean isPlaying) {
        if (playingChannel == channel && playing == isPlaying) return;
        playingChannel = channel;
        playing = isPlaying;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveChannelItem item) {
        TextView number = holder.getView(R.id.tvChannelNum);
        TextView name = holder.getView(R.id.tvChannelName);
        TextView epg = holder.getView(R.id.ux_live_channel_epg);
        TextView state = holder.getView(R.id.ux_live_channel_playing);
        boolean current = item == playingChannel;
        holder.itemView.setSelected(current);
        number.setText(String.format(Locale.getDefault(), "%02d", item.getChannelNum()));
        number.setTextColor(ContextCompat.getColor(mContext, current ? R.color.car_accent : R.color.car_text_muted));
        name.setText(item.getChannelName());
        name.setTextColor(ContextCompat.getColor(mContext, R.color.car_text));
        String title = programTitleProvider == null ? null : programTitleProvider.getTitle(item);
        epg.setText(title == null || title.trim().isEmpty() ? "暂无节目单" : "正在播出：" + title);
        state.setVisibility(current ? View.VISIBLE : View.GONE);
        state.setText(playing ? "播放中" : "已选择");
    }

    public void setSelectedChannelIndex(int selectedChannelIndex) {
        if (selectedChannelIndex == this.selectedChannelIndex) return;
        int previous = this.selectedChannelIndex;
        this.selectedChannelIndex = selectedChannelIndex;
        notifyChannelChanged(previous);
        notifyChannelChanged(selectedChannelIndex);
    }

    public void setFocusedChannelIndex(int focusedChannelIndex) {
        int previous = this.focusedChannelIndex;
        this.focusedChannelIndex = focusedChannelIndex;
        notifyChannelChanged(previous);
        notifyChannelChanged(focusedChannelIndex);
    }

    private void notifyChannelChanged(int position) {
        if (position >= 0 && position < getItemCount()) notifyItemChanged(position);
    }
}
