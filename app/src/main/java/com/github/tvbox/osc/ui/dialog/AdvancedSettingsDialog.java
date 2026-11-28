package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.car.InAppBackButton;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.AudioDiagnosticActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine.Mode;

/** Advanced settings using the same sheet and option rows as the original app dialogs. */
public class AdvancedSettingsDialog extends SettingSheetDialog {
    private final Runnable onChanged;

    public AdvancedSettingsDialog(@NonNull Context context, Runnable onChanged) {
        super(context, "高级设置");
        this.onChanged = onChanged;
        render();
    }

    private void render() {
        clearRows();
        final Activity owner = ownerActivity();

        addRow("增强音量", "原始声音、车载均衡或高速压缩", CarEnhance.audioProcessingMode().label, v ->
                new AudioModeDialog(getContext(), CarEnhance.audioProcessingMode(), mode -> {
                    CarEnhance.setAudioProcessingMode(mode);
                    notifyChanged();
                    render();
                }).show());

        addRow("应用内音量", "播放页左右区域上下滑动调整，不修改系统音量",
                Math.round(CarEnhance.appGain() * 100) + "%", null);

        addRow("音量测试片", "使用当前 EXO 音频处理档位播放内置诊断片", "播放", v -> {
            if (owner != null) owner.startActivity(new Intent(owner, AudioDiagnosticActivity.class));
        });

        String backState = InAppBackButton.isEnabled() ? "已开启" : "已关闭";
        addRow("本应用返回", "边缘手势返回组件", backState, v -> {
            InAppBackButton.setEnabled(owner, !InAppBackButton.isEnabled());
            notifyChanged();
            render();
        });

        addRow("carclash 返回状态", "只有 carclash 提供只读状态接口后才能精确判断",
                InAppBackButton.carClashBackSummary(getContext()), null);

        String side = InAppBackButton.getSide();
        addRow("触发位置", "从屏幕边缘向内滑动", HawkConfig.BACK_SIDE_RIGHT.equals(side) ? "右侧" : "左侧", v -> {
            InAppBackButton.setSide(owner, HawkConfig.BACK_SIDE_RIGHT.equals(side)
                    ? HawkConfig.BACK_SIDE_LEFT : HawkConfig.BACK_SIDE_RIGHT);
            notifyChanged();
            render();
        });

        int seconds = InAppBackButton.getHideSeconds();
        addRow("提示自动隐藏", "隐藏后仍可从边缘发起手势", seconds == 0 ? "常驻" : seconds + " 秒", v -> {
            InAppBackButton.setHideSeconds(owner, seconds == 4 ? 6 : seconds == 6 ? 8 : seconds == 8 ? 0 : 4);
            notifyChanged();
            render();
        });

        boolean brightness = Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false);
        addRow("亮度手势", "播放页左侧上下滑动调整窗口亮度", brightness ? "已开启" : "跟随系统", v -> {
            Hawk.put(HawkConfig.BRIGHTNESS_GESTURE, !brightness);
            notifyChanged();
            render();
        });

        addRow("订阅缓存", "首页优先使用缓存；这里立即拉取并刷新首页", "立即更新", v -> {
            if (owner == null) return;
            Toast.makeText(getContext(), "正在更新订阅…", Toast.LENGTH_SHORT).show();
            ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
                @Override public void notice(String msg) { }

                @Override public void success() {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HOME_SOURCE_CHANGE, 0));
                    Toast.makeText(getContext(), "订阅已更新", Toast.LENGTH_SHORT).show();
                }

                @Override public void error(String msg) {
                    Toast.makeText(getContext(), "更新失败：" + msg, Toast.LENGTH_SHORT).show();
                }
            }, owner);
        });

        addRow("诊断日志上传", "上传当前诊断批次到发布通道", "立即上传", v -> {
            com.github.tvbox.osc.diag.DiagLog.event("diag_upload_manual");
            com.github.tvbox.osc.diag.DiagLog.uploadNow();
            Toast.makeText(getContext(), com.github.tvbox.osc.diag.DiagLog.status(), Toast.LENGTH_LONG).show();
        });

        addRow("诊断信息", "复制版本与当前功能状态", "复制", v -> {
            ClipboardManager clipboard = (ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("TVBox 诊断",
                        "TVBox " + BuildConfig.VERSION_NAME
                                + "\nvolume=" + Math.round(CarEnhance.appGain() * 100) + "%"
                                + "\naudio=" + CarEnhance.audioProcessingMode().label
                                + "\nback=" + InAppBackButton.getMode()
                                + "\ncarclash=" + InAppBackButton.carClashBackSummary(getContext())));
            }
            Toast.makeText(getContext(), "诊断信息已复制", Toast.LENGTH_SHORT).show();
        });
    }

    private void notifyChanged() {
        if (onChanged != null) onChanged.run();
    }
}
