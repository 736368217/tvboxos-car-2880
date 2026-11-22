package com.github.tvbox.osc.ui.activity;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.util.PlayerHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Temporary on-device audio check (r93): plays the packaged 20 s diagnostic clip through the real
 * EXO PCM path so the enhanced/original switch can be compared on the actual head unit.
 *
 * <p>Remove this activity together with {@code assets/audio-diagnostic.mp4} once the volume
 * calibration is done.</p>
 */
public class AudioDiagnosticActivity extends BaseActivity {
    private static final String ASSET = "audio-diagnostic.mp4";

    private MyVideoView video;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (status == null) return;
            status.setText("诊断片 20s · " + CarEnhance.audioProcessingMode().label
                    + " · App " + Math.round(CarEnhance.appGain() * 100) + "%"
                    + "\n点下方按钮切增强/原声；返回键退出");
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_audio_diagnostic;
    }

    @Override
    protected void init() {
        video = findViewById(R.id.diagnostic_video);
        status = findViewById(R.id.diagnostic_status);
        TextView toggle = findViewById(R.id.diagnostic_toggle);
        toggle.setOnClickListener(v -> {
            CarEnhance.toggleAudioEnhancement(video);
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        });
        try {
            File file = new File(getCacheDir(), ASSET);
            copyAsset(file);
            // Use the engine the saved preference selects, so this clip exercises the real PCM
            // gain/limiter chain instead of the bare library default.
            PlayerHelper.updateCfg(video);
            video.setUrl(Uri.fromFile(file).toString());
            video.setLooping(true);
            video.start();
            handler.post(ticker);
        } catch (Throwable error) {
            status.setText("诊断片无法播放：" + error);
        }
    }

    private void copyAsset(File target) throws Exception {
        try (InputStream in = getAssets().open(ASSET);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (video != null) {
            CarEnhance.releaseAudio(video);
            video.release();
            video = null;
        }
        super.onDestroy();
    }
}
