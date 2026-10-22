package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SettingMenuAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public SettingMenuAdapter() {
        super(R.layout.item_setting_menu, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvName, item);
        setMenuIcon(helper, helper.getLayoutPosition(), false);
        helper.addOnClickListener(R.id.tvName);
    }

    protected void setMenuIcon(BaseViewHolder helper, int position, boolean active) {
        int icon;
        switch (position) {
            case 0: icon = active ? R.drawable.ic_tvbox_settings_display_active : R.drawable.ic_tvbox_settings_display; break;
            case 1: icon = active ? R.drawable.ic_tvbox_player_audio_active : R.drawable.ic_tvbox_player_audio; break;
            case 2: icon = active ? R.drawable.ic_tvbox_player_decoder_active : R.drawable.ic_tvbox_player_decoder; break;
            case 3: icon = active ? R.drawable.ic_tvbox_settings_gesture_active : R.drawable.ic_tvbox_settings_gesture; break;
            case 4: icon = active ? R.drawable.ic_tvbox_action_back_active : R.drawable.ic_tvbox_action_back; break;
            case 5: icon = active ? R.drawable.ic_tvbox_settings_subscribe_active : R.drawable.ic_tvbox_settings_subscribe; break;
            case 6: icon = active ? R.drawable.ic_tvbox_content_info_active : R.drawable.ic_tvbox_content_info; break;
            default: icon = R.drawable.ic_tvbox_nav_settings; break;
        }
        android.widget.TextView title = helper.getView(R.id.tvName);
        title.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        title.setCompoundDrawablePadding(16);
    }
}