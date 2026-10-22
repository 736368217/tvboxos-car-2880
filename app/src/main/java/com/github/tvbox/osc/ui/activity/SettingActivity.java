package com.github.tvbox.osc.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.adapter.SettingMenuAdapter;
import com.github.tvbox.osc.ui.adapter.SettingPageAdapter;
import com.github.tvbox.osc.ui.fragment.ModelSettingFragment;
import com.github.tvbox.osc.ui.adapter.ux_setting_MenuAdapter;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SettingActivity extends BaseActivity {
    private TvRecyclerView mGridView;
    private ViewPager mViewPager;
    private SettingMenuAdapter sortAdapter;
    private SettingPageAdapter pageAdapter;
    private List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean sortChange = false;
    private int defaultSelected = 0;
    private int sortFocused = 0;
    private Handler mHandler = new Handler();
    private String homeSourceKey;
    private String currentApi;
    private int homeRec;
    private String currentLiveApi;

    private static final String[] UX_SETTING_TITLES = {
            "画面色彩与超分", "座舱声场与音效", "播放解码与后台",
            "触控手势与首页入口", "应用内返回控件", "订阅源与多仓管理", "关于与数据"
    };
    private static final String[] UX_SETTING_SUBTITLES = {
            "核心解码与音画调优", "真实 EQ 与声场", "播放器、字幕与弹幕",
            "车载触摸与导航入口", "悬浮球与边缘触发", "配置地址与站源", "版本、备份与缓存"
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_setting;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mViewPager = findViewById(R.id.mViewPager);
        findViewById(R.id.ux_setting_back).setOnClickListener(v -> onBackPressed());
        sortAdapter = new ux_setting_MenuAdapter();
        mGridView.setAdapter(sortAdapter);
        mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        sortAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                if (view.getId() == R.id.tvName) {
                    if (view.getParent() != null) {
                        ((ViewGroup) view.getParent()).requestFocus();
                        sortFocused = position;
                        if (sortFocused != defaultSelected) {
                            selectCategory(sortFocused);
                        }
                    }
                }
            }
        });
        sortAdapter.setOnItemClickListener((adapter, view, position) -> selectCategory(position));
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) {
                    TextView tvName = itemView.findViewById(R.id.tvName);
                    tvName.setTextColor(getResources().getColor(R.color.color_CCFFFFFF));
                }
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) {
                    sortChange = true;
                    sortFocused = position;
                    TextView tvName = itemView.findViewById(R.id.tvName);
                    tvName.setTextColor(Color.WHITE);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                selectCategory(position);
            }
        });
    }

    private void initData() {
        currentApi = Hawk.get(HawkConfig.API_URL, "");
        homeSourceKey = ApiConfig.get().getHomeSourceBean().getKey();
        homeRec = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC);
        currentLiveApi = Hawk.get(HawkConfig.LIVE_API_URL, "");
        List<String> sortList = new ArrayList<>();
        java.util.Collections.addAll(sortList, "01  画面色彩与超分引擎", "02  座舱声场与音效", "03  播放解码与后台行为",
                "04  触控手势与长按倍速", "05  双模应用内返回控件", "06  订阅源与多仓管理", "07  关于 TVBox");
        sortAdapter.setNewData(sortList);
        initViewPager();
        int requestedUxCategory = getIntent().getIntExtra("uxSettingCategory", -1);
        if (requestedUxCategory >= 0) {
            mHandler.postDelayed(() -> selectCategory(requestedUxCategory), 450);
        }
    }

    private void initViewPager() {
        // One original settings instance: preserve handlers without duplicate subscription state.
        fragments.add(ModelSettingFragment.newInstance());
        pageAdapter = new SettingPageAdapter(getSupportFragmentManager(), fragments);
        mViewPager.setAdapter(pageAdapter);
        mViewPager.setCurrentItem(0);
    }

    private Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != defaultSelected) {
                    selectCategory(sortFocused);
                }
            }
        }
    };

    private void selectCategory(int category) {
        defaultSelected = category;
        sortFocused = category;
        int index = Math.max(0, Math.min(category, UX_SETTING_TITLES.length - 1));
        TextView title = findViewById(R.id.ux_setting_title);
        if (title != null) {
            title.setText("设置中心  /  " + UX_SETTING_TITLES[index] + "  ·  " + UX_SETTING_SUBTITLES[index]);
        }
        ((ux_setting_MenuAdapter) sortAdapter).setSelectedCategory(category);
        if (!fragments.isEmpty()) ((ModelSettingFragment) fragments.get(0)).showUxCategory(category);
    }

    @Override protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        callback = null;
        super.onDestroy();
    }

    private Runnable mDevModeRun = new Runnable() {
        @Override
        public void run() {
            devMode = "";
        }
    };


    public interface DevModeCallback {
        void onChange();
    }

    public static DevModeCallback callback = null;

    String devMode = "";

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mHandler.removeCallbacks(mDataRunnable);
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_0:
                    mHandler.removeCallbacks(mDevModeRun);
                    devMode += "0";
                    mHandler.postDelayed(mDevModeRun, 200);
                    if (devMode.length() >= 4) {
                        if (callback != null) {
                            callback.onChange();
                        }
                    }
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            mHandler.postDelayed(mDataRunnable, 200);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (currentApi.equals(Hawk.get(HawkConfig.API_URL, ""))) {
            if (homeRec != Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)) {
                jumpActivity(HomeActivity.class, createBundle());
            }else if(!currentLiveApi.equals(Hawk.get(HawkConfig.LIVE_API_URL, ""))){
                jumpActivity(HomeActivity.class, createBundle());
            }
        } else {
            AppManager.getInstance().finishActivity(HomeActivity.class);
            jumpActivity(HomeActivity.class);
            finish();
            return;
        }
        super.onBackPressed();
    }

    private Bundle createBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        return bundle;
    }
}