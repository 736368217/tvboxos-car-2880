package com.github.tvbox.osc.ui.activity;

import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.car.AudioEffectController;
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.HawkConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import xyz.doikki.videoplayer.render.VideoEnhancePreset;

public class CarAdvancedActivity extends BaseActivity {
    private TextView tvAudio;
    private TextView tvVideo;
    private TextView tvBackMode;
    private TextView tvBackSide;
    private TextView tvBackSkin;
    private TextView tvHide;

    @Override protected int getLayoutResID() { return R.layout.activity_car_advanced; }
    @Override protected boolean shouldRefreshAutoSize() { return true; }

    @Override
    protected void init() {
        tvAudio = findViewById(R.id.tvAdvancedAudio);
        tvVideo = findViewById(R.id.tvAdvancedVideo);
        tvBackMode = findViewById(R.id.tvAdvancedBackMode);
        tvBackSide = findViewById(R.id.tvAdvancedBackSide);
        tvBackSkin = findViewById(R.id.tvAdvancedBackSkin);
        tvHide = findViewById(R.id.tvAdvancedBackHide);
        refreshValues();
        findViewById(R.id.llAdvancedAudio).setOnClickListener(v -> showAudioPresets());
        findViewById(R.id.llAdvancedVideo).setOnClickListener(v -> showVideoPresets());
        findViewById(R.id.llAdvancedBackMode).setOnClickListener(v -> showBackModes());
        findViewById(R.id.llAdvancedBackSide).setOnClickListener(v -> showSides());
        findViewById(R.id.llAdvancedBackSkin).setOnClickListener(v -> showSkins());
        findViewById(R.id.llAdvancedBackHide).setOnClickListener(v -> showHideTimes());
        bindSwitch(R.id.llAdvancedSr, R.id.switchAdvancedSr, CarEnhance.videoSrEnabled(),
                CarEnhance::setVideoSrEnabled);
        bindSwitch(R.id.llAdvancedInterp, R.id.switchAdvancedInterp, CarEnhance.videoInterpEnabled(),
                CarEnhance::setVideoInterpEnabled);
        bindSwitch(R.id.llAdvancedBackEnabled, R.id.switchAdvancedBackEnabled, InAppBackButton.isEnabled(),
                enabled -> InAppBackButton.setEnabled(this, enabled));
        bindSwitch(R.id.llAdvancedBackSize, R.id.switchAdvancedBackSize, InAppBackButton.isLarge(),
                enabled -> InAppBackButton.setLarge(this, enabled));
        bindSwitch(R.id.llAdvancedHaptic, R.id.switchAdvancedHaptic, InAppBackButton.isHapticEnabled(),
                enabled -> InAppBackButton.setHapticEnabled(this, enabled));
        bindSwitch(R.id.llAdvancedRootHide, R.id.switchAdvancedRootHide, InAppBackButton.isHideOnRoot(),
                enabled -> InAppBackButton.setHideOnRoot(this, enabled));
    }

    private interface Changed { void apply(boolean enabled); }
    private interface Selected { void apply(int position); }

    private void bindSwitch(int row, int control, boolean initial, Changed changed) {
        SwitchCompat toggle = findViewById(control);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, enabled) -> changed.apply(enabled));
        findViewById(row).setOnClickListener(v -> toggle.toggle());
    }

    private void refreshValues() {
        tvAudio.setText(AudioEffectController.name(CarEnhance.audioMode()));
        tvVideo.setText(VideoEnhancePreset.forMode(CarEnhance.videoMode()).name);
        String mode = InAppBackButton.getMode();
        tvBackMode.setText(HawkConfig.BACK_MODE_EDGE.equals(mode) ? "边缘拉出"
                : HawkConfig.BACK_MODE_BOTH.equals(mode) ? "双模共存" : "悬浮球");
        String side = InAppBackButton.getSide();
        tvBackSide.setText(HawkConfig.BACK_SIDE_RIGHT.equals(side) ? "右侧"
                : HawkConfig.BACK_SIDE_BOTH.equals(side) ? "左右两侧" : "左侧");
        tvBackSkin.setText(HawkConfig.BACK_SKIN_CAPSULE.equals(InAppBackButton.getEdgeSkin())
                ? "胶囊抽屉" : "流体水滴");
        int seconds = InAppBackButton.getHideSeconds();
        tvHide.setText(seconds == 0 ? "常驻" : seconds + " 秒");
    }

    private void showOptions(String title, List<String> values, int selected, Selected action) {
        SelectDialog<String> dialog = new SelectDialog<>(this);
        dialog.setTip(title);
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override public void click(String value, int position) {
                action.apply(position);
                refreshValues();
                dialog.dismiss();
            }
            @Override public String getDisplay(String value) { return value; }
        }, SelectDialogAdapter.stringDiff, values, selected);
        dialog.show();
    }

    private void showAudioPresets() {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < AudioEffectController.MODE_COUNT; i++) values.add(AudioEffectController.name(i));
        showOptions("音效预设", values, CarEnhance.audioMode(), CarEnhance::setAudioMode);
    }

    private void showVideoPresets() {
        SelectDialog<String> dialog = new SelectDialog<>(this, R.layout.dialog_video_presets);
        dialog.setTip("画面预设");
        dialog.findViewById(R.id.btnVideoPresetCancel).setOnClickListener(v -> dialog.cancel());
        List<String> values = new ArrayList<>();
        for (VideoEnhancePreset preset : VideoEnhancePreset.all()) {
            values.add(preset.name + " · " + preset.description);
        }
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override public void click(String value, int position) {
                CarEnhance.setVideoMode(position);
                refreshValues();
                dialog.dismiss();
            }
            @Override public String getDisplay(String value) { return value; }
        }, SelectDialogAdapter.stringDiff, values, CarEnhance.videoMode());
        dialog.show();
    }

    private void showBackModes() {
        String[] keys = {HawkConfig.BACK_MODE_FLOATING, HawkConfig.BACK_MODE_EDGE, HawkConfig.BACK_MODE_BOTH};
        showOptions("返回模式", Arrays.asList("悬浮球", "边缘拉出", "双模共存"),
                Arrays.asList(keys).indexOf(InAppBackButton.getMode()),
                position -> InAppBackButton.setMode(this, keys[position]));
    }

    private void showSides() {
        String[] keys = {HawkConfig.BACK_SIDE_LEFT, HawkConfig.BACK_SIDE_RIGHT, HawkConfig.BACK_SIDE_BOTH};
        showOptions("边缘驻留侧", Arrays.asList("左侧", "右侧", "左右两侧"),
                Arrays.asList(keys).indexOf(InAppBackButton.getSide()),
                position -> InAppBackButton.setSide(this, keys[position]));
    }

    private void showSkins() {
        showOptions("边缘抽屉外观", Arrays.asList("流体水滴", "胶囊抽屉"),
                HawkConfig.BACK_SKIN_CAPSULE.equals(InAppBackButton.getEdgeSkin()) ? 1 : 0,
                position -> InAppBackButton.setEdgeSkin(this, position == 1
                        ? HawkConfig.BACK_SKIN_CAPSULE : HawkConfig.BACK_SKIN_TEARDROP));
    }

    private void showHideTimes() {
        int[] seconds = {0, 4, 6, 8};
        int selected = 0;
        for (int i = 0; i < seconds.length; i++) if (seconds[i] == InAppBackButton.getHideSeconds()) selected = i;
        showOptions("闲置半隐藏", Arrays.asList("常驻", "4 秒", "6 秒", "8 秒"), selected,
                position -> InAppBackButton.setHideSeconds(this, seconds[position]));
    }
}
