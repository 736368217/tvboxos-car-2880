package xyz.doikki.videoplayer.player;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

/** Audio focus and temporary attenuation, confined to the main thread. */
final class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "CarFocus";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final WeakReference<VideoView> mWeakVideoView;
    private final AudioManager mAudioManager;
    private boolean mStartRequested;
    private boolean mPausedForLoss;
    private boolean mFocusRequested;
    private int mCurrentFocus;
    // Invalidates callbacks queued before a user pause, release or a fresh focus request.
    private volatile int mGeneration;

    AudioFocusHelper(@NonNull VideoView videoView) {
        mWeakVideoView = new WeakReference<>(videoView);
        mAudioManager = (AudioManager) videoView.getContext().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onAudioFocusChange(final int focusChange) {
        final int generation = mGeneration;
        mHandler.post(() -> {
            if (generation != mGeneration || !mFocusRequested) return;
            handleAudioFocusChange(focusChange);
        });
    }

    private void handleAudioFocusChange(int focusChange) {
        VideoView videoView = mWeakVideoView.get();
        if (videoView == null) return;
        mCurrentFocus = focusChange;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                boolean resume = mStartRequested || mPausedForLoss;
                mStartRequested = false;
                mPausedForLoss = false;
                videoView.restoreVolume(); // Respects mute; clears only temporary duck.
                if (resume) videoView.start();
                Log.i(TAG, "focus regained -> volume restored");
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                mPausedForLoss = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        && (videoView.isPlaying() || mPausedForLoss);
                if (videoView.isPlaying()) videoView.pauseForAudioFocus();
                // Do not abandon transient focus: otherwise Android will never send GAIN.
                videoView.restoreVolume();
                Log.i(TAG, "focus lost -> paused transient=" + mPausedForLoss);
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) abandonFocus();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                videoView.duckVolume(0.1f); // Keep duck across buffering/prepare as well.
                Log.i(TAG, "focus duck -> volume 10%");
                break;
            default:
                break;
        }
    }

    void requestFocus() {
        VideoView videoView = mWeakVideoView.get();
        if (mFocusRequested && mCurrentFocus == AudioManager.AUDIOFOCUS_GAIN) {
            if (videoView != null) videoView.restoreVolume();
            return;
        }
        if (mAudioManager == null) return;
        ++mGeneration;
        mFocusRequested = true;
        int status = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mCurrentFocus = AudioManager.AUDIOFOCUS_GAIN;
            mStartRequested = false;
            mPausedForLoss = false;
            if (videoView != null) videoView.restoreVolume();
            Log.i(TAG, "focus granted synchronously -> volume restored");
        } else {
            mCurrentFocus = 0;
            mStartRequested = true;
            Log.i(TAG, "focus request deferred status=" + status);
        }
    }

    void abandonFocus() {
        ++mGeneration;
        mFocusRequested = false;
        mStartRequested = false;
        mPausedForLoss = false;
        mCurrentFocus = 0;
        mHandler.removeCallbacksAndMessages(null);
        if (mAudioManager != null) mAudioManager.abandonAudioFocus(this);
        VideoView videoView = mWeakVideoView.get();
        if (videoView != null) videoView.restoreVolume();
        Log.i(TAG, "focus abandoned -> state and duck reset");
    }
}
