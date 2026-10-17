package com.github.tvbox.osc.ui.activity;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.fragment.ux_setting_Pages;

/** Same native enhancement/back controls as the category settings center. */
public class CarAdvancedActivity extends BaseActivity {
    private ux_setting_Pages pages;
    private int category;

    @Override protected int getLayoutResID() { return R.layout.activity_car_advanced; }
    @Override protected boolean shouldRefreshAutoSize() { return true; }

    @Override protected void init() {
        pages = new ux_setting_Pages(this, findViewById(R.id.ux_setting_content));
        pages.buildAdvanced();
        findViewById(R.id.ux_setting_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.ux_setting_video_tab).setOnClickListener(v -> select(0));
        findViewById(R.id.ux_setting_audio_tab).setOnClickListener(v -> select(1));
        findViewById(R.id.ux_setting_return_tab).setOnClickListener(v -> select(2));
        select(0);
    }

    private void select(int selected) {
        category = selected;
        int[] tabs = {R.id.ux_setting_video_tab, R.id.ux_setting_audio_tab, R.id.ux_setting_return_tab};
        for (int i = 0; i < tabs.length; i++) findViewById(tabs[i]).setSelected(i == category);
        pages.show(category);
        findViewById(R.id.ux_setting_scroll).scrollTo(0, 0);
    }

    @Override protected void onResume() {
        super.onResume();
        if (pages != null) pages.refresh();
    }
}
