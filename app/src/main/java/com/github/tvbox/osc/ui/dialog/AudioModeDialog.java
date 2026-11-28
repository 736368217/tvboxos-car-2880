package com.github.tvbox.osc.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine.Mode;

/** The three car audio processing modes exposed from the playback controls. */
public class AudioModeDialog extends SettingSheetDialog {
    public interface Listener {
        void onModeSelected(Mode mode);
    }

    private static final Mode[] MODES = {
            Mode.ORIGINAL,
            Mode.CAR_BALANCED,
            Mode.CAR_HIGHWAY
    };

    public AudioModeDialog(@NonNull Context context, Mode selected, Listener listener) {
        super(context, "增强音量");
        for (Mode mode : MODES) {
            final Mode option = mode;
            addOption(mode.label, mode == selected, v -> {
                if (listener != null) listener.onModeSelected(option);
                dismiss();
            });
        }
        setCloseText("关闭");
    }
}
