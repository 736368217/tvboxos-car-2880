package com.github.tvbox.osc.ui.fragment;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.car.AudioEffectController;
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.car.CarPlaybackPreferences;
import com.github.tvbox.osc.car.CarHomeNavigationPreferences;
import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.HawkConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import xyz.doikki.videoplayer.render.VideoEnhancePreset;

/** Native scene-pixel pages shared by Settings and Advanced. Existing Model handlers own legacy rows. */
public final class ux_setting_Pages {
    private final Activity context;
    private final LinearLayout host;
    private final List<View> pages = new ArrayList<>();
    private final List<TextView> videoButtons = new ArrayList<>();
    private final List<TextView> audioButtons = new ArrayList<>();
    private TextView videoValue, videoAppliedState, audioValue, backMode, backSide, backSkin, backHide;
    private SwitchCompat sr, interp, enabled, large, haptic, rootHide;
    private LinearLayout backPage;

    public ux_setting_Pages(Activity context, LinearLayout host) {
        this.context = context;
        this.host = host;
    }

    public void buildAll() {
        host.removeAllViews();
        LinearLayout picture = page();
        video(picture);
        heading(picture, "画面与界面", "保留原有缩放、窗口预览与壁纸入口");
        legacy(picture, R.id.llScale, "画面缩放", R.id.tvScaleType);
        legacy(picture, R.id.showPreview, "窗口预览", R.id.showPreviewText);
        legacy(picture, R.id.llWp, "换张壁纸", 0);
        legacy(picture, R.id.llWpRecovery, "重置壁纸", 0);

        LinearLayout sound = page();
        audio(sound);

        LinearLayout decoder = page();
        heading(decoder, "播放解码与后台行为", "播放器、网络、字幕与弹幕沿用原有配置链路");
        toggle(decoder, R.id.uxBackgroundRow, R.id.uxBackgroundSwitch, "切出导航时保持后台音频", "复用前台音频服务；导航临时焦点 duck 到 20%，退出影片后释放", CarPlaybackPreferences.backgroundAudio(), CarPlaybackPreferences::setBackgroundAudio);
        legacy(decoder, R.id.llPlay, "播放器", R.id.tvPlay);
        legacy(decoder, R.id.llMediaCodec, "IJK 解码方式", R.id.tvMediaCodec);
        legacy(decoder, R.id.llRender, "渲染方式", R.id.tvRenderType);
        legacy(decoder, R.id.llIjkCachePlay, "IJK 缓存", R.id.tvIjkCachePlay);
        legacy(decoder, R.id.m3u8Ad, "M3U8 去广告", R.id.m3u8AdText);
        legacy(decoder, R.id.autoSwitchLine, "自动换线", R.id.autoSwitchLineText);
        legacy(decoder, R.id.llDns, "安全 DNS", R.id.tvDns);
        legacy(decoder, R.id.llParseWebVew, "嗅探 WebView", R.id.tvParseWebView);
        legacy(decoder, R.id.danmuOpen, "弹幕", R.id.danmuOpenText);
        legacy(decoder, R.id.danmuApi, "弹幕地址", R.id.danmuApiText);
        note(decoder, "字幕", "继续使用播放器内的字幕选择、加载与同步入口；不改变字幕解析或渲染。");

        LinearLayout touch = page();
        heading(touch, "触控手势与长按倍速", "车载原生触摸 · 保留遥控器与输入法能力");
        toggle(touch, R.id.uxLongPressRow, R.id.uxLongPressSwitch, "全屏长按 2.0x", "长按加速，松手或取消恢复原速", CarPlaybackPreferences.longPress(), CarPlaybackPreferences::setLongPress);
        toggle(touch, R.id.uxGestureRow, R.id.uxGestureSwitch, "左右分屏滑动手势", "左侧亮度、右侧媒体音量；不改变系统全局亮度", CarPlaybackPreferences.gestures(), CarPlaybackPreferences::setGestures);
        heading(touch, "首页入口显示", "分类专题与系统设置固定保留；其他入口可按使用场景隐藏");
        toggle(touch, R.id.uxNavHomeRow, R.id.uxNavHomeSwitch, "精选推荐入口", "显示或隐藏首页推荐入口", CarHomeNavigationPreferences.isVisible(CarHomeNavigationPreferences.HOME), value -> CarHomeNavigationPreferences.setVisible(CarHomeNavigationPreferences.HOME, value));
        toggle(touch, R.id.uxNavLiveRow, R.id.uxNavLiveSwitch, "电视直播入口", "显示或隐藏直播入口", CarHomeNavigationPreferences.isVisible(CarHomeNavigationPreferences.LIVE), value -> CarHomeNavigationPreferences.setVisible(CarHomeNavigationPreferences.LIVE, value));
        toggle(touch, R.id.uxNavHistoryRow, R.id.uxNavHistorySwitch, "播放历史入口", "显示或隐藏历史入口", CarHomeNavigationPreferences.isVisible(CarHomeNavigationPreferences.HISTORY), value -> CarHomeNavigationPreferences.setVisible(CarHomeNavigationPreferences.HISTORY, value));
        toggle(touch, R.id.uxNavCollectRow, R.id.uxNavCollectSwitch, "我的收藏入口", "显示或隐藏收藏入口", CarHomeNavigationPreferences.isVisible(CarHomeNavigationPreferences.COLLECT), value -> CarHomeNavigationPreferences.setVisible(CarHomeNavigationPreferences.COLLECT, value));
        toggle(touch, R.id.uxNavPushRow, R.id.uxNavPushSwitch, "多端推送入口", "显示或隐藏推送入口", CarHomeNavigationPreferences.isVisible(CarHomeNavigationPreferences.PUSH), value -> CarHomeNavigationPreferences.setVisible(CarHomeNavigationPreferences.PUSH, value));
        heading(touch, "搜索、首页与历史", "以下选项均使用现有配置与处理函数");
        legacy(touch, R.id.llSearchView, "搜索展示", R.id.tvSearchView);
        legacy(touch, R.id.showFastSearch, "聚合搜索", R.id.showFastSearchText);
        legacy(touch, R.id.llHomeRec, "首页推荐", R.id.tvHomeRec);
        legacy(touch, R.id.llHomeRecStyle, "首页多行", R.id.showRecStyleText);
        legacy(touch, R.id.tvHomeLive, "下次进入", R.id.tvHomeText);
        legacy(touch, R.id.llHistoryMerge, "历史合并", R.id.tvHistoryMerge);
        legacy(touch, R.id.llHistoryNum, "历史记录数量", R.id.tvHistoryNum);

        LinearLayout back = page();
        back(back);
        heading(back, "兼容设置入口", "保留原版返回设置与高级功能入口");
        legacy(back, R.id.llCarBack, "应用内返回按钮", R.id.tvCarBack);
        legacy(back, R.id.llCarBackHide, "返回按钮隐藏时间", R.id.tvCarBackHide);
        legacy(back, R.id.llCarAdvanced, "画面与音效高级功能", 0);

        LinearLayout sources = page();
        heading(sources, "订阅源与多仓管理", "配置地址、历史、线路和首页站源沿用现有逻辑");
        legacy(sources, R.id.llApi, "配置地址", R.id.tvApi);
        legacy(sources, R.id.llApiHistory, "配置历史", R.id.tvMediaCodec1);
        legacy(sources, R.id.llApiLine, "线路选择", R.id.tvApiLine);
        legacy(sources, R.id.llHomeApi, "首页站源", R.id.tvHomeApi);
        note(sources, "本地与远程配置", "点击配置地址可使用原有订阅、本地文件与远程输入能力；不修改包名、签名或站源身份。");

        LinearLayout about = page();
        heading(about, "关于 TVBox", "17.3 英寸横屏 · 2880 × 1620 深空黑曜界面");
        legacy(about, R.id.llAbout, "关于 / 版本信息", 0);
        legacy(about, R.id.llBackup, "数据备份与恢复", 0);
        legacy(about, R.id.llClearCache, "清空缓存", 0);
        View debug = legacy(about, R.id.llDebug, "调试模式", R.id.tvDebugOpen);
        debug.setVisibility(View.GONE);
        note(about, "能力说明", "SGSR1 是空间边缘增强；时域平滑不等同硬件光流 MEMC。音效依赖实际音频会话与设备能力，不宣称获得杜比认证。");
        refresh();
    }

    /** Standalone advanced activity exposes the same real picture/audio/back controls. */
    public void buildAdvanced() {
        host.removeAllViews();
        video(page());
        audio(page());
        back(page());
        refresh();
    }

    public void show(int category) {
        for (int i = 0; i < pages.size(); i++) pages.get(i).setVisibility(i == category ? View.VISIBLE : View.GONE);
        refresh();
    }

    private LinearLayout page() {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        host.addView(page, new LinearLayout.LayoutParams(-1, -2));
        pages.add(page);
        return page;
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        text.setTextColor(context.getResources().getColor(color));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        if (bold) text.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return text;
    }

    private FrameLayout card(LinearLayout parent, int height) {
        FrameLayout card = new FrameLayout(context);
        card.setBackgroundResource(R.drawable.ux_setting_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height);
        lp.bottomMargin = 30;
        parent.addView(card, lp);
        return card;
    }

    private void place(FrameLayout parent, View child, int x, int y, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        lp.leftMargin = x;
        lp.topMargin = y;
        parent.addView(child, lp);
    }

    private void heading(LinearLayout page, String title, String description) {
        FrameLayout header = card(page, 140);
        place(header, text(title, 34, true, R.color.car_text), 40, 20, 2010, 52);
        place(header, text(description, 26, false, R.color.car_text_muted), 40, 82, 2010, 40);
    }

    private void note(LinearLayout page, String title, String description) {
        FrameLayout note = card(page, 210);
        place(note, text(title, 30, true, R.color.car_accent_cyan), 40, 25, 2010, 50);
        TextView body = text(description, 28, false, R.color.car_text_muted);
        body.setMaxLines(3);
        place(note, body, 40, 90, 2010, 100);
    }

    private View legacy(LinearLayout page, int id, String label, int valueId) {
        FrameLayout row = card(page, 130);
        row.setId(id);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.ux_setting_choice);
        place(row, text(label, 30, true, R.color.car_text), 40, 25, 640, 80);
        if (valueId != 0) {
            TextView value = text("", 28, false, R.color.car_accent_cyan);
            value.setSingleLine(true);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            value.setId(valueId);
            place(row, value, 720, 25, 1230, 80);
        }
        place(row, text("›", 40, false, R.color.car_text_muted), 2000, 25, 60, 80);
        return row;
    }

    private TextView choice(FrameLayout parent, String title, int x, int y, int width, int height, Runnable action) {
        TextView button = text(title, 28, true, R.color.car_text);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setBackgroundResource(R.drawable.ux_setting_choice);
        button.setOnClickListener(v -> { action.run(); refresh(); });
        place(parent, button, x, y, width, height);
        return button;
    }

    private void video(LinearLayout page) {
        FrameLayout preview = card(page, 330);
        place(preview, text("处理链示意（非实时截图）", 34, true, R.color.aether_text_primary), 40, 20, 1500, 52);
        TextView source = text("原始输入帧 · 播放时", 30, true, R.color.aether_text_primary);
        source.setGravity(Gravity.CENTER);
        source.setBackgroundResource(R.drawable.car_surface_card);
        place(preview, source, 40, 92, 930, 150);
        TextView output = text("GL 处理输出 · 播放时", 30, true, R.color.aether_cyan_focus);
        output.setGravity(Gravity.CENTER);
        output.setBackgroundResource(R.drawable.car_surface_card_raised);
        place(preview, output, 980, 92, 930, 150);
        videoAppliedState = text("", 24, false, R.color.aether_emerald_ready);
        videoAppliedState.setId(R.id.tvAdvancedVideoApplied);
        videoAppliedState.setMaxLines(2);
        place(preview, videoAppliedState, 40, 258, 2010, 48);

        FrameLayout presets = card(page, 460);
        presets.setId(R.id.llAdvancedVideo);
        place(presets, text("SDR / HDR 画面调优预设", 34, true, R.color.car_text), 40, 20, 1500, 50);
        videoValue = text("", 28, true, R.color.car_accent_cyan);
        videoValue.setId(R.id.tvAdvancedVideo);
        place(presets, videoValue, 1620, 20, 440, 50);
        place(presets, text("11 组 GLSL 预设 · HDR 会旁路调色 · 不重启解码器", 26, false, R.color.car_text_muted), 40, 78, 2010, 40);
        for (VideoEnhancePreset preset : VideoEnhancePreset.all()) {
            final int mode = preset.id;
            int index = videoButtons.size();
            videoButtons.add(choice(presets, preset.name, 40 + (index % 4) * 500,
                    130 + (index / 4) * 100, 480, 90, () -> CarEnhance.setVideoMode(mode)));
        }
        sr = toggle(page, R.id.llAdvancedSr, R.id.switchAdvancedSr, "SGSR1 边缘超分辨率",
                "空间边缘检测与锐化；720P / 1080P 放大时增强清晰度", CarEnhance.videoSrEnabled(), CarEnhance::setVideoSrEnabled);
        interp = toggle(page, R.id.llAdvancedInterp, R.id.switchAdvancedInterp, "时域平滑 · Temporal Smoothing",
                "相邻帧时域混合，不等同硬件 MEMC；实际效果依赖片源与设备", CarEnhance.videoInterpEnabled(), CarEnhance::setVideoInterpEnabled);
    }

    private void audio(LinearLayout page) {
        heading(page, "座舱声场与音效", "9 组真实 EQ / 低频 / 虚拟声场预设，播放时绑定 EXO / IJK 音频会话");
        FrameLayout presets = card(page, 580);
        presets.setId(R.id.llAdvancedAudio);
        audioValue = text("", 32, true, R.color.car_accent_cyan);
        audioValue.setId(R.id.tvAdvancedAudio);
        place(presets, audioValue, 40, 20, 2000, 60);
        for (int i = 0; i < AudioEffectController.MODE_COUNT; i++) {
            final int mode = i;
            audioButtons.add(choice(presets, AudioEffectController.name(i), 40 + (i % 3) * 674,
                    110 + (i / 3) * 148, 650, 122, () -> CarEnhance.setAudioMode(mode)));
        }
        note(page, "播放时应用", "选择保存到 CarEnhance 原有音效配置，播放时由现有音频会话链路应用。效果器不受设备支持时保持原有回退行为。");
    }

    private interface Changed { void apply(boolean value); }
    private SwitchCompat toggle(LinearLayout page, int rowId, int switchId, String title, String description,
                                boolean initial, Changed changed) {
        FrameLayout row = card(page, 220);
        row.setId(rowId);
        row.setFocusable(true);
        place(row, text(title, 32, true, R.color.car_text), 40, 35, 1690, 60);
        place(row, text(description, 26, false, R.color.car_text_muted), 40, 112, 1690, 75);
        SwitchCompat toggle = new SwitchCompat(context);
        toggle.setId(switchId);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26);
        toggle.setContentDescription(title);
        toggle.setTextColor(context.getResources().getColor(R.color.car_text));
        toggle.setChecked(initial);
        int active = context.getResources().getColor(R.color.car_accent_green);
        int idle = context.getResources().getColor(R.color.car_text_muted);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{active, idle}));
        toggle.setText(initial ? "已开启  " : "已关闭  ");
        toggle.setOnCheckedChangeListener((button, value) -> {
            changed.apply(value);
            toggle.setText(value ? "已开启  " : "已关闭  ");
        });
        place(row, toggle, 1760, 60, 290, 100);
        row.setOnClickListener(v -> toggle.toggle());
        return toggle;
    }

    private void back(LinearLayout page) {
        backPage = page;
        heading(page, "双模应用内返回控件", "仅在应用窗口内生效，不申请系统悬浮窗、无障碍或全局按键权限");
        enabled = toggle(page, R.id.llAdvancedBackEnabled, R.id.switchAdvancedBackEnabled, "应用内返回按钮", "显示或隐藏窗口内的返回控件", InAppBackButton.isEnabled(), v -> {
            InAppBackButton.setEnabled(context, v);
            setBackOptionRowsEnabled(page, v);
        });
        backMode = option(page, R.id.llAdvancedBackMode, R.id.tvAdvancedBackMode, "返回模式", () -> {
            String[] keys = {HawkConfig.BACK_MODE_FLOATING, HawkConfig.BACK_MODE_EDGE, HawkConfig.BACK_MODE_BOTH};
            options("返回模式", Arrays.asList("悬浮球", "边缘拉出", "双模共存"), Arrays.asList(keys).indexOf(InAppBackButton.getMode()), p -> InAppBackButton.setMode(context, keys[p]));
        });
        backSide = option(page, R.id.llAdvancedBackSide, R.id.tvAdvancedBackSide, "边缘驻留侧", () -> {
            String[] keys = {HawkConfig.BACK_SIDE_LEFT, HawkConfig.BACK_SIDE_RIGHT, HawkConfig.BACK_SIDE_BOTH};
            options("边缘驻留侧", Arrays.asList("左侧", "右侧", "左右两侧"), Arrays.asList(keys).indexOf(InAppBackButton.getSide()), p -> InAppBackButton.setSide(context, keys[p]));
        });
        backSkin = option(page, R.id.llAdvancedBackSkin, R.id.tvAdvancedBackSkin, "边缘抽屉外观", () -> options("边缘抽屉外观", Arrays.asList("流体水滴", "胶囊抽屉"), HawkConfig.BACK_SKIN_CAPSULE.equals(InAppBackButton.getEdgeSkin()) ? 1 : 0, p -> InAppBackButton.setEdgeSkin(context, p == 1 ? HawkConfig.BACK_SKIN_CAPSULE : HawkConfig.BACK_SKIN_TEARDROP)));
        large = toggle(page, R.id.llAdvancedBackSize, R.id.switchAdvancedBackSize, "大号悬浮球", "增大应用内返回热区", InAppBackButton.isLarge(), v -> InAppBackButton.setLarge(context, v));
        backHide = option(page, R.id.llAdvancedBackHide, R.id.tvAdvancedBackHide, "闲置半隐藏", () -> {
            int[] seconds = {0, 4, 6, 8};
            int selected = 0;
            for (int i = 0; i < seconds.length; i++) if (seconds[i] == InAppBackButton.getHideSeconds()) selected = i;
            options("闲置半隐藏", Arrays.asList("常驻", "4 秒", "6 秒", "8 秒"), selected, p -> InAppBackButton.setHideSeconds(context, seconds[p]));
        });
        haptic = toggle(page, R.id.llAdvancedHaptic, R.id.switchAdvancedHaptic, "触感反馈", "沿用系统可用的触觉反馈能力", InAppBackButton.isHapticEnabled(), v -> InAppBackButton.setHapticEnabled(context, v));
        rootHide = toggle(page, R.id.llAdvancedRootHide, R.id.switchAdvancedRootHide, "首页根状态自动隐藏", "仅影响应用内返回控件", InAppBackButton.isHideOnRoot(), v -> InAppBackButton.setHideOnRoot(context, v));
        setBackOptionRowsEnabled(page, enabled.isChecked());
    }

    private void setBackOptionRowsEnabled(LinearLayout page, boolean value) {
        int[] rowIds = {R.id.llAdvancedBackMode, R.id.llAdvancedBackSide, R.id.llAdvancedBackSkin,
                R.id.llAdvancedBackSize, R.id.llAdvancedBackHide, R.id.llAdvancedHaptic, R.id.llAdvancedRootHide};
        int[] switchIds = {R.id.switchAdvancedBackSize, R.id.switchAdvancedHaptic, R.id.switchAdvancedRootHide};
        for (int id : rowIds) {
            View row = page.findViewById(id);
            if (row == null) continue;
            row.setEnabled(value);
            row.setFocusable(value);
            row.setAlpha(value ? 1f : 0.38f);
        }
        for (int id : switchIds) {
            View child = page.findViewById(id);
            if (child != null) child.setEnabled(value);
        }
    }

    private TextView option(LinearLayout page, int rowId, int valueId, String title, Runnable action) {
        View row = legacy(page, rowId, title, valueId);
        row.setOnClickListener(v -> action.run());
        return row.findViewById(valueId);
    }

    private interface Selected { void apply(int position); }
    private void options(String title, List<String> values, int selected, Selected action) {
        SelectDialog<String> dialog = new SelectDialog<>(context);
        dialog.setTip(title);
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override public void click(String value, int position) {
                action.apply(position);
                refresh();
                dialog.dismiss();
            }
            @Override public String getDisplay(String value) { return value; }
        }, SelectDialogAdapter.stringDiff, values, Math.max(0, selected));
        dialog.show();
    }

    public void refresh() {
        for (int i = 0; i < videoButtons.size(); i++) videoButtons.get(i).setSelected(VideoEnhancePreset.all().get(i).id == CarEnhance.videoMode());
        for (int i = 0; i < audioButtons.size(); i++) audioButtons.get(i).setSelected(i == CarEnhance.audioMode());
        if (videoValue != null) videoValue.setText("当前：" + VideoEnhancePreset.forMode(CarEnhance.videoMode()).name);
        if (videoAppliedState != null) videoAppliedState.setText(CarEnhance.videoAppliedStatus());
        if (audioValue != null) audioValue.setText("当前：" + AudioEffectController.name(CarEnhance.audioMode()));
        sync(sr, CarEnhance.videoSrEnabled());
        sync(interp, CarEnhance.videoInterpEnabled());
        sync(enabled, InAppBackButton.isEnabled());
        sync(large, InAppBackButton.isLarge());
        sync(haptic, InAppBackButton.isHapticEnabled());
        sync(rootHide, InAppBackButton.isHideOnRoot());
        if (backPage != null && enabled != null) setBackOptionRowsEnabled(backPage, enabled.isChecked());
        if (backMode != null) backMode.setText(HawkConfig.BACK_MODE_BOTH.equals(InAppBackButton.getMode()) ? "双模共存" : HawkConfig.BACK_MODE_EDGE.equals(InAppBackButton.getMode()) ? "边缘拉出" : "悬浮球");
        if (backSide != null) backSide.setText(HawkConfig.BACK_SIDE_BOTH.equals(InAppBackButton.getSide()) ? "左右两侧" : HawkConfig.BACK_SIDE_RIGHT.equals(InAppBackButton.getSide()) ? "右侧" : "左侧");
        if (backSkin != null) backSkin.setText(HawkConfig.BACK_SKIN_CAPSULE.equals(InAppBackButton.getEdgeSkin()) ? "胶囊抽屉" : "流体水滴");
        if (backHide != null) backHide.setText(InAppBackButton.getHideSeconds() == 0 ? "常驻" : InAppBackButton.getHideSeconds() + " 秒");
    }

    private void sync(SwitchCompat view, boolean value) {
        if (view != null && view.isChecked() != value) view.setChecked(value);
    }
}
