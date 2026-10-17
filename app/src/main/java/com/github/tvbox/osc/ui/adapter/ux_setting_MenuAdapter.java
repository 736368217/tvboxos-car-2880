package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseViewHolder;

/** Persistent category highlight is independent of remote focus. */
public final class ux_setting_MenuAdapter extends SettingMenuAdapter {
    private int selectedCategory;

    public void setSelectedCategory(int position) {
        int previous = selectedCategory;
        selectedCategory = position;
        notifyItemChanged(previous);
        notifyItemChanged(position);
    }

    @Override protected void convert(BaseViewHolder holder, String item) {
        super.convert(holder, item);
        holder.itemView.setSelected(holder.getAdapterPosition() == selectedCategory);
    }
}
