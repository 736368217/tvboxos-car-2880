package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.car.AudioEffectController;
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;

import java.util.ArrayList;
import java.util.List;

import xyz.doikki.videoplayer.render.VideoEnhancePreset;
import xyz.doikki.videoplayer.render.VideoEnhanceTiers;

/**
 * 播放增强面板：把「滤镜 / 超分 / 时域平滑 / 音效」四项收进一个入口。
 *
 * <p>同一个面板同时服务两个位置：播放页底部的「高级设置」按钮（带当前 MyVideoView，改动立即生效）
 * 和设置页的「播放增强」二级入口（videoView 为 null，只改全局默认值，下次播放生效）。</p>
 */
public class PlaybackAdvancedDialog extends BaseDialog {

    private final MyVideoView videoView;
    private final Runnable onChanged;

    private TextView tvVideo;
    private TextView tvSr;
    private TextView tvInterp;
    private TextView tvAudio;
    private long lastRowClickMs;

    public PlaybackAdvancedDialog(@NonNull Context context, MyVideoView videoView, Runnable onChanged) {
        super(context);
        this.videoView = videoView;
        this.onChanged = onChanged;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    public void show() {
        super.show();
        Window window = getWindow();
        if (window != null) {
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.44f);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private View buildContent() {
        Context context = getContext();
        float titleSize = context.getResources().getDisplayMetrics().heightPixels * 0.034f;
        int padding = (int) context.getResources().getDimension(R.dimen.vs_30);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF21E293B);
        background.setCornerRadius(context.getResources().getDimension(R.dimen.vs_20));
        root.setBackground(background);

        TextView title = new TextView(context);
        title.setText("高级设置 · 播放增强");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, titleSize);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, (int) context.getResources().getDimension(R.dimen.vs_20));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvVideo = addRow(root, "画面滤镜", v -> showVideoPresetDialog());
        // 档位用列表直接选，避免「循环点击」在部分输入设备上一次点击跳两档
        tvSr = addRow(root, "超分档位", v -> showTierDialog(true));
        tvInterp = addRow(root, "补帧档位", v -> showTierDialog(false));
        tvAudio = addRow(root, "音效", v -> showAudioDialog());

        TextView close = new TextView(context);
        close.setText("关闭");
        close.setTextColor(0xFF0F172A);
        close.setGravity(Gravity.CENTER);
        close.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDisplayMetrics().heightPixels * 0.026f);
        close.setFocusable(true);
        close.setPadding(0, padding / 2, 0, padding / 2);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0xFF38BDF8);
        closeBg.setCornerRadius(context.getResources().getDimension(R.dimen.vs_10));
        close.setBackground(closeBg);
        close.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = padding;
        root.addView(close, closeParams);

        refresh();
        return root;
    }

    /** 档位选择：0..3 直接点选，比循环点击可靠。 */
    private void showTierDialog(final boolean superResolution) {
        final SelectDialog<String> dialog = new SelectDialog<>(getContext());
        dialog.setTip(superResolution ? "超分档位" : "补帧档位");
        List<String> options = new ArrayList<>();
        for (int tier = 0; tier <= 3; tier++) {
            options.add("Lv" + tier + " " + (superResolution
                    ? VideoEnhanceTiers.srName(tier) : VideoEnhanceTiers.memcName(tier)));
        }
        int current = superResolution ? CarEnhance.srTier() : CarEnhance.memcTier();
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                if (superResolution) {
                    CarEnhance.setSrTier(pos);
                } else {
                    CarEnhance.setMemcTier(pos);
                }
                CarEnhance.applyVideo(videoView);
                boolean active = pos > 0 && videoView != null && (superResolution
                        ? videoView.isSuperResolutionRuntimeActive()
                        : videoView.isInterpolationRuntimeActive());
                String name = superResolution ? VideoEnhanceTiers.srName(pos) : VideoEnhanceTiers.memcName(pos);
                CarEnhance.toast(getContext(), pos == 0
                        ? (superResolution ? "超分已关闭" : "补帧已关闭")
                        : (active ? "已生效 · " + name : "已请求 · " + name));
                dialog.dismiss();
                refresh();
            }

            @Override
            public String getDisplay(String value) {
                return value;
            }
        }, SelectDialogAdapter.stringDiff, options, current);
        dialog.show();
    }

    private void showVideoPresetDialog() {
        final SelectDialog<String> dialog = new SelectDialog<>(getContext());
        dialog.setTip("画面滤镜");
        List<VideoEnhancePreset> presets = VideoEnhancePreset.all();
        List<String> options = new ArrayList<>();
        for (VideoEnhancePreset preset : presets) {
            options.add(preset.name + " · " + preset.description);
        }
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                CarEnhance.setVideoMode(pos);
                CarEnhance.applyVideo(videoView);
                String state = videoView == null ? "已保存 · 下次播放生效"
                        : videoView.isHdrVideo() && pos != 0 ? "HDR 视频 · 调色旁路"
                        : "画面预设已应用";
                CarEnhance.toast(getContext(), state);
                dialog.dismiss();
                refresh();
            }

            @Override
            public String getDisplay(String value) {
                return value;
            }
        }, SelectDialogAdapter.stringDiff, options, CarEnhance.videoMode());
        dialog.show();
    }

    private void showAudioDialog() {
        final SelectDialog<String> dialog = new SelectDialog<>(getContext());
        dialog.setTip("音效");
        List<String> options = new ArrayList<>();
        for (int mode = 0; mode < AudioEffectController.MODE_COUNT; mode++) {
            options.add(AudioEffectController.name(mode));
        }
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                CarEnhance.setAudioMode(pos);
                CarEnhance.applyAudio(videoView);
                String state = pos == 0 ? "音效已关闭"
                        : videoView == null ? "已保存 · 下次播放生效"
                        : AudioEffectController.isActive() ? "音效已应用" : "设备未接受该音效 · 已跳过";
                CarEnhance.toast(getContext(), state);
                dialog.dismiss();
                refresh();
            }

            @Override
            public String getDisplay(String value) {
                return value;
            }
        }, SelectDialogAdapter.stringDiff, options, CarEnhance.audioMode());
        dialog.show();
    }

    private TextView addRow(LinearLayout parent, String label, View.OnClickListener click) {
        Context context = getContext();
        int rowHeight = (int) context.getResources().getDimension(R.dimen.vs_60);
        int side = (int) context.getResources().getDimension(R.dimen.vs_20);
        float textSize = context.getResources().getDisplayMetrics().heightPixels * 0.026f;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setPadding(side, 0, side, 0);
        row.setBackgroundResource(R.drawable.shape_setting_model_focus);
        // 防抖：部分设备/远程点击会把同一次按下派发两次，导致一次点击跳过一档（3→1）。
        row.setOnClickListener(v -> {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastRowClickMs < 350L) return;
            lastRowClickMs = now;
            click.onClick(v);
        });

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        value.setTextColor(0xFFBAE6FD);
        value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
        // 值串限宽 + 省略号：避免「播放增强」的摘要把整行挤满、和标签贴在一起
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setMaxWidth((int) (context.getResources().getDisplayMetrics().widthPixels * 0.30f));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = (int) context.getResources().getDimension(R.dimen.vs_10);
        row.addView(value, valueParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        params.topMargin = (int) context.getResources().getDimension(R.dimen.vs_10);
        parent.addView(row, params);
        return value;
    }

    private void refresh() {
        if (tvVideo != null) tvVideo.setText(VideoEnhancePreset.forMode(CarEnhance.videoMode()).name);
        if (tvSr != null) tvSr.setText("Lv" + CarEnhance.srTier() + " " + VideoEnhanceTiers.srName(CarEnhance.srTier()));
        if (tvInterp != null) tvInterp.setText("Lv" + CarEnhance.memcTier() + " " + VideoEnhanceTiers.memcName(CarEnhance.memcTier()));
        if (tvAudio != null) tvAudio.setText(AudioEffectController.name(CarEnhance.audioMode()));
        if (onChanged != null) onChanged.run();
    }
}
