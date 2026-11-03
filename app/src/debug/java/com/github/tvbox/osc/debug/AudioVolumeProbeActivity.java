package com.github.tvbox.osc.debug;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.FrameLayout;
import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.player.ExoPlayer;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import java.io.File;
import java.lang.reflect.Field;
import xyz.doikki.videoplayer.player.PlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;

/** Debug-only regression of the real controller, View and EXO using a local fixture. */
public class AudioVolumeProbeActivity extends Activity {
    private final Handler main = new Handler();
    private MyVideoView video;
    private VodController controller;
    private FrameLayout root;
    private int checks, failed, phase;
    private Object previousGain;
    private boolean changedPreference;
    private boolean resumedFromBackground;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        setContentView(root);
        previousGain = Hawk.get(HawkConfig.VOLUME_GAIN, null);
        Hawk.put(HawkConfig.VOLUME_GAIN, 150);
        changedPreference = true;
        setup();
        main.postDelayed(() -> safe(this::first), 2500);
    }

    private void setup() {
        video = new MyVideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        video.setPlayerFactory(new PlayerFactory<ExoPlayer>() {
            @Override public ExoPlayer createPlayer(android.content.Context context) { return new ExoPlayer(context); }
        });
        video.setEnableAudioFocus(true);
        controller = new VodController(this);
        try {
            org.json.JSONObject config = new org.json.JSONObject();
            config.put("pl", 2).put("sc", 0).put("ijk", "").put("sp", 1.0)
                    .put("st", 0).put("et", 0);
            controller.setPlayerConfig(config);
        } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        // Production PlayFragment supplies this listener; the probe has no source/episode UI.
        controller.setListener((VodController.VodControlListener) java.lang.reflect.Proxy.newProxyInstance(
                VodController.VodControlListener.class.getClassLoader(),
                new Class<?>[]{VodController.VodControlListener.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepared")) video.prepared();
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                }));
        video.setVideoController(controller);
        video.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override public void onPlayStateChanged(int state) {
                if (state == VideoView.STATE_ERROR) fail("playback error");
                if (state == VideoView.STATE_PLAYBACK_COMPLETED && phase == 1) {
                    phase = 2;
                    checkVolume("natural episode completion", 1.5f);
                    video.replay(true);
                    main.postDelayed(() -> safe(AudioVolumeProbeActivity.this::afterEpisode), 1500);
                }
            }
        });
        String file = getIntent().getStringExtra("file");
        if (file == null) file = new File(getExternalFilesDir(null), "volume-probe.mp4").toURI().toString();
        video.setUrl(file);
        video.start();
    }

    private void first() throws Exception {
        checkVolume("saved 150% construction and bind", 1.5f);
        VideoView fallback = new VideoView(this);
        controller.setMediaPlayer(fallback);
        java.lang.reflect.Method limit = com.github.tvbox.osc.player.controller.BaseController.class
                .getDeclaredMethod("applyVolumeLimit");
        limit.setAccessible(true);
        limit.invoke(controller);
        check(Hawk.<Integer>get(HawkConfig.VOLUME_GAIN, 0) == 150,
                "non-PCM capability limit preserves saved 150%");
        controller.setMediaPlayer(video);
        limit.invoke(controller);
        checkVolume("return to PCM retains 150%", 1.5f);
        java.lang.reflect.Method slide = com.github.tvbox.osc.player.controller.BaseController.class
                .getDeclaredMethod("slideToChangeVolume", float.class);
        slide.setAccessible(true);
        slide.invoke(controller, controller.getMeasuredHeight() * .05f);
        checkVolume("gesture up one 5% detent", 1.55f);
        slide.invoke(controller, 0f);
        checkVolume("gesture cumulative return to 150%", 1.5f);
        check(video.getPcmGainQueueCount() > 0, "PCM processor consumed samples");
        check(video.getVideoSize()[0] > 0 && video.getCurrentPosition() > 0, "actual video frames and position");
        controller.setAppVolume(.5f);
        checkVolume("50% exactly once", .5f);
        video.duckVolume(.1f);
        checkVolume("duck 50%", .05f);
        video.setMute(true);
        checkVolume("mute", 0f);
        video.setMute(false);
        checkVolume("unmute retains duck", .05f);
        video.restoreVolume();
        checkVolume("restore 50%", .5f);
        controller.setAppVolume(1.5f);
        video.startFullScreen();
        checkVolume("full screen", 1.5f);
        video.stopFullScreen();
        video.setTinyScreenSize(new int[]{900, 500});
        video.startTinyScreen();
        checkVolume("tiny screen", 1.5f);
        video.stopTinyScreen();
        checkVolume("restored window", 1.5f);
        video.duckVolume(.1f);
        video.pause();
        video.resume();
        checkVolume("duck pause resume", 1.5f);
        phase = 1;
        // Seek near the end, then allow the real completion callback to start the next item.
        video.seekTo(Math.max(0, video.getDuration() - 700));
        main.postDelayed(() -> { if (phase == 1) { fail("completion timeout"); done(); } }, 7000);
    }

    private void afterEpisode() throws Exception {
        checkVolume("automatic next episode", 1.5f);
        video.release();
        root.removeView(video);
        setup();
        main.postDelayed(() -> safe(() -> {
            checkVolume("new View and controller with saved 150%", 1.5f);
            // Leave the Activity for a real foreground/background cycle; the host brings it back.
            phase = 3;
            Log.i("AudioVolumeProbe", "READY_BACKGROUND checks=" + checks);
        }), 2000);
    }

    @Override protected void onPause() {
        super.onPause();
        if (video != null) video.pause();
        if (phase == 3) resumedFromBackground = true;
    }
    @Override protected void onResume() {
        super.onResume();
        if (video != null) video.resume();
        if (resumedFromBackground) {
            resumedFromBackground = false;
            main.postDelayed(() -> safe(() -> { checkVolume("Activity background resume", 1.5f); done(); }), 1000);
        }
    }
    private void checkVolume(String label, float gain) {
        try {
            Field field = xyz.doikki.videoplayer.exo.ExoMediaPlayer.class.getDeclaredField("mInternalPlayer");
            field.setAccessible(true);
            com.google.android.exoplayer2.SimpleExoPlayer engine =
                    (com.google.android.exoplayer2.SimpleExoPlayer) field.get(video.getMediaPlayer());
            check(engine != null && Math.abs(engine.getVolume() - 1f) < .0001f
                    && Math.abs(video.getPcmGain() - gain) < .0001f,
                    label + " track=" + (engine == null ? -1 : engine.getVolume()) + " pcm=" + video.getPcmGain());
        } catch (Exception e) { fail(label + " reflection=" + e); }
    }
    private void check(boolean ok, String label) {
        checks++;
        if (ok) Log.i("AudioVolumeProbe", "PASS " + label);
        else fail(label);
    }
    private void fail(String label) { failed++; Log.e("AudioVolumeProbe", "FAIL " + label); }
    private interface Step { void run() throws Exception; }
    private void safe(Step step) { try { step.run(); } catch (Throwable e) { fail(e.toString()); done(); } }
    private void done() {
        phase = 4;
        Log.i("AudioVolumeProbe", "DONE checks=" + checks + " failed=" + failed);
        finish();
    }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (video != null) video.release();
        if (changedPreference) {
            if (previousGain == null) Hawk.delete(HawkConfig.VOLUME_GAIN);
            else Hawk.put(HawkConfig.VOLUME_GAIN, previousGain);
        }
        super.onDestroy();
    }
}
