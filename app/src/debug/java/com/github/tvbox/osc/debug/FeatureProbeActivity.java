package com.github.tvbox.osc.debug;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.tvbox.osc.car.AudioEffectController;
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;
import java.util.Properties;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * Debug-only, adb-driven playback probe. It is not part of a release APK. The activity applies one
 * independent feature combination from probe.properties, reports renderer/session diagnostics and
 * samples real output pixels, so playback can be verified without a remote subscription.
 */
public class FeatureProbeActivity extends Activity {
    private static final String TAG = "FeatureProbe";

    private final Handler main = new Handler(Looper.getMainLooper());
    private MyVideoView video;
    private TextView status;
    private String source;
    private String playerType;
    private int filter, audio, durationSeconds;
    private boolean sr, interp, sampled;
    private boolean audioSweep;
    private boolean failed;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) return;
            logState();
            main.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        File dir = getExternalFilesDir(null);
        if (dir == null) dir = new File(getFilesDir(), "probe");
        if (!dir.exists()) dir.mkdirs();
        Properties config = new Properties();
        File configFile = new File(dir, "probe.properties");
        if (configFile.isFile()) {
            try (FileInputStream input = new FileInputStream(configFile)) {
                config.load(input);
            } catch (Throwable error) {
                Log.w(TAG, "PROBE_CONFIG_FILE ignored", error);
            }
        }
        File localVideo = new File(dir, "probe.mp4");
        source = config.getProperty("file", Uri.fromFile(localVideo).toString());
        playerType = config.getProperty("player", "exo").toLowerCase(Locale.US);
        filter = integer(config, "filter", 0);
        audio = integer(config, "audio", 0);
        audioSweep = integer(config, "audioSweep", 0) != 0;
        sr = integer(config, "sr", 1) != 0;
        interp = integer(config, "interp", 1) != 0;
        durationSeconds = Math.max(4, Math.min(30, integer(config, "duration", 12)));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video = new MyVideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(18);
        status.setGravity(Gravity.START | Gravity.TOP);
        status.setBackgroundColor(0x99000000);
        status.setPadding(18, 12, 18, 12);
        root.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP));
        setContentView(root);

        Hawk.put(HawkConfig.PLAY_TYPE, "ijk".equals(playerType) ? 1 : 2);
        Hawk.put(HawkConfig.PLAY_RENDER, 0);
        CarEnhance.setVideoMode(filter);
        CarEnhance.setVideoSrEnabled(sr);
        CarEnhance.setVideoInterpEnabled(interp);
        CarEnhance.setAudioMode(audio);
        PlayerHelper.updateCfg(video);
        video.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                Log.i(TAG, "PROBE_STATE_CHANGE state=" + playState);
                if (playState == VideoView.STATE_ERROR) failed = true;
            }
        });
        Log.i(TAG, "PROBE_CONFIG file=" + source + " player=" + playerType + " filter=" + filter
                + " audio=" + audio + " sr=" + sr + " interp=" + interp
                + " duration=" + durationSeconds);
        video.setUrl(source);
        video.start();
        main.post(ticker);
        main.postDelayed(this::samplePixels, 3_000L);
        if (audioSweep) main.postDelayed(() -> runAudioSweep(1), 2_000L);
        else if (audio > 0) main.postDelayed(this::releaseAudio, 7_000L);
        main.postDelayed(this::finishProbe, durationSeconds * 1_000L);
    }

    private static int integer(Properties config, String key, int fallback) {
        try {
            return Integer.parseInt(config.getProperty(key, Integer.toString(fallback)).trim());
        } catch (Throwable error) {
            return fallback;
        }
    }

    private void logState() {
        String renderer = video == null ? "none" : video.getRendererClassName();
        int state = video == null ? -99 : video.getCurrentPlayState();
        boolean srActive = video != null && video.isSuperResolutionRuntimeActive();
        boolean interpActive = video != null && video.isInterpolationRuntimeActive();
        boolean audioActive = AudioEffectController.isActive();
        float sourceFps = video == null ? 0f : video.getRendererSourceFrameRate();
        float outputFps = video == null ? 0f : video.getRendererOutputFrameRate();
        int session = video == null ? 0 : video.getAudioSessionId();
        int[] size = video == null ? new int[]{0, 0} : video.getVideoSize();
        String line = String.format(Locale.US,
                "state=%d renderer=%s size=%dx%d session=%d audioActive=%s srActive=%s interpActive=%s srcFps=%.1f outFps=%.1f pos=%d",
                state, renderer, size[0], size[1], session, audioActive, srActive, interpActive,
                sourceFps, outputFps, video == null ? 0 : video.getCurrentPosition());
        status.setText(line);
        Log.i(TAG, "PROBE_STATUS " + line);
    }

    private void samplePixels() {
        if (sampled || video == null) return;
        View renderView = video.getRenderViewForDiagnostics();
        if (Build.VERSION.SDK_INT < 24 || !(renderView instanceof SurfaceView)) {
            Log.i(TAG, "PROBE_PIXELS unavailable renderer="
                    + (renderView == null ? "null" : renderView.getClass().getName()));
            return;
        }
        sampled = true;
        final Bitmap bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
        try {
            android.view.PixelCopy.request((SurfaceView) renderView, bitmap, result -> {
                if (result != android.view.PixelCopy.SUCCESS) {

                    Log.i(TAG, "PROBE_PIXELS error=" + result);
                    return;
                }
                int[][] sums = new int[4][3];
                int[] counts = new int[4];
                for (int y = 0; y < bitmap.getHeight(); y++) {
                    for (int x = 0; x < bitmap.getWidth(); x++) {
                        int color = bitmap.getPixel(x, y);
                        int quadrant = (y >= bitmap.getHeight() / 2 ? 2 : 0)
                                + (x >= bitmap.getWidth() / 2 ? 1 : 0);
                        sums[quadrant][0] += Color.red(color);
                        sums[quadrant][1] += Color.green(color);
                        sums[quadrant][2] += Color.blue(color);
                        counts[quadrant]++;
                    }
                }
                StringBuilder out = new StringBuilder("PROBE_PIXELS");
                String[] names = {"tl", "tr", "bl", "br"};
                for (int i = 0; i < 4; i++) {
                    int count = Math.max(1, counts[i]);
                    out.append(' ').append(names[i]).append('=')
                            .append(sums[i][0] / count).append(',')
                            .append(sums[i][1] / count).append(',')
                            .append(sums[i][2] / count);
                }
                Log.i(TAG, out.toString());
            }, main);
        } catch (Throwable error) {
            Log.i(TAG, "PROBE_PIXELS exception=" + error.getClass().getSimpleName());
        }
    }

    private void runAudioSweep(int mode) {
        if (isFinishing()) return;
        CarEnhance.setAudioMode(mode);
        CarEnhance.applyAudio(video);
        main.postDelayed(() -> {
            Log.i(TAG, "PROBE_AUDIO_SWEEP mode=" + mode
                    + " active=" + AudioEffectController.isActive());
            if (mode < 8) runAudioSweep(mode + 1);
            else {
                CarEnhance.setAudioMode(0);
                CarEnhance.applyAudio(video);
                main.postDelayed(() -> Log.i(TAG,
                        "PROBE_AUDIO_SWEEP mode=0 active=" + AudioEffectController.isActive()), 500L);
            }
        }, 600L);
    }

    private void releaseAudio() {
        CarEnhance.setAudioMode(0);
        CarEnhance.applyAudio(video);
        main.postDelayed(() -> Log.i(TAG, "PROBE_AUDIO_RELEASE active="
                + AudioEffectController.isActive()), 500L);
    }

    private void finishProbe() {
        logState();
        Log.i(TAG, "PROBE_DONE failed=" + failed + " state="
                + (video == null ? -99 : video.getCurrentPlayState()));
        if (video != null) {
            video.release();
            video = null;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (video != null) {
            video.release();
            video = null;
        }
        super.onDestroy();
    }
}
