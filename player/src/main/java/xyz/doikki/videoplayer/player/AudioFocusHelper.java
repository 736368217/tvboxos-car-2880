package xyz.doikki.videoplayer.player;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

/**
 * 音频焦点改变监听
 */
final class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {

    /** 焦点日志统一 tag，实机自查：{@code adb logcat -s CarFocus} */
    private static final String FOCUS_TAG = "CarFocus";

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private WeakReference<VideoView> mWeakVideoView;

    private AudioManager mAudioManager;

    private boolean mStartRequested = false;
    private boolean mPausedForLoss = false;
    private int mCurrentFocus = 0;

    AudioFocusHelper(@NonNull VideoView videoView) {
        mWeakVideoView = new WeakReference<>(videoView);
        mAudioManager = (AudioManager) videoView.getContext().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onAudioFocusChange(final int focusChange) {
        if (mCurrentFocus == focusChange) {
            return;
        }

        //由于onAudioFocusChange有可能在子线程调用，
        //故通过此方式切换到主线程去执行
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                handleAudioFocusChange(focusChange);
            }
        });

        mCurrentFocus = focusChange;
    }

    private void handleAudioFocusChange(int focusChange) {
        final VideoView videoView = mWeakVideoView.get();
        if (videoView == null) {
            return;
        }
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN://获得焦点
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT://暂时获得焦点
                if (mStartRequested || mPausedForLoss) {
                    videoView.start();
                    mStartRequested = false;
                    mPausedForLoss = false;
                }
                if (!videoView.isMute()) {//恢复音量
                    videoView.restoreVolume();
                    Log.i(FOCUS_TAG, "focus regained -> volume restored");
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS://焦点丢失
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT://焦点暂时丢失
                if (videoView.isPlaying()) {
                    mPausedForLoss = true;
                    videoView.pause();
                    Log.i(FOCUS_TAG, "focus lost -> paused");
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK://此时需降低音量
                if (videoView.isPlaying() && !videoView.isMute()) {
                    videoView.duckVolume(0.1f);
                    Log.i(FOCUS_TAG, "focus duck -> volume 10%");
                }
                break;
        }
    }

    /**
     * Requests to obtain the audio focus
     */
    void requestFocus() {
        if (mCurrentFocus == AudioManager.AUDIOFOCUS_GAIN) {
            return;
        }

        if (mAudioManager == null) {
            return;
        }

        int status = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status) {
            mCurrentFocus = AudioManager.AUDIOFOCUS_GAIN;
            // 修复「切到别的应用再切回来，声音特别小」：别的应用用 MAY_DUCK 抢焦点时
            // 我们把音量压到了 duck 档（10%）；切回 App 后 resume 会走到这里重新请求焦点，
            // 而这次请求是同步成功的 —— 系统不会再回调 onAudioFocusChange(GAIN)，
            // 音量恢复逻辑永远不会执行，声音就一直停在 10%，直到退出播放重播。
            // 所以同步拿到焦点时必须在这里手动恢复音量。
            VideoView videoView = mWeakVideoView.get();
            if (videoView != null && !videoView.isMute()) {
                videoView.restoreVolume();
                Log.i(FOCUS_TAG, "focus granted synchronously -> volume restored (duck residue cleared)");
            }
            return;
        }

        Log.i(FOCUS_TAG, "focus request deferred status=" + status);
        mStartRequested = true;
    }

    /**
     * Requests the system to drop the audio focus
     */
    void abandonFocus() {

        if (mAudioManager == null) {
            return;
        }

        mStartRequested = false;
        mAudioManager.abandonAudioFocus(this);
        // abandon 之后系统不会再回调本监听器，mCurrentFocus 会停在上一次的值。
        // 若停在 GAIN（pause 前没经历过焦点变化），resume 里的 requestFocus 会被
        // 开头的去重判断直接吞掉 —— 我们就永远拿不回焦点：导航播报不会触发 duck、
        // 别的应用也拿不到焦点让路。所以 abandon 时必须把状态复位。
        mCurrentFocus = 0;
        Log.i(FOCUS_TAG, "focus abandoned -> state reset");
    }
}