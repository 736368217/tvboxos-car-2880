package com.github.tvbox.osc.player;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.car.CarEnhance;
import com.github.tvbox.osc.util.ImgUtil;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.ui.widget.DanmakuView;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

public class MyVideoView extends VideoView implements DrawHandler.Callback {
    public interface DanmuSeekListener {
        void onDanmuSeek(long position);
    }

    private DanmakuView danmuView;
    private ImageView artworkView;
    private DanmuSeekListener danmuSeekListener;


    public MyVideoView(@NonNull Context context) {
        super(context, null);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AbstractPlayer getMediaPlayer() {
        return mMediaPlayer;
    }

    /**
     * Car edition: audio session id of the current engine (EXO or IJK), 0 when the engine is not
     * running or does not expose one (the system player is intentionally unsupported).
     */
    public int getAudioSessionId() {
        AbstractPlayer player = mMediaPlayer;
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getAudioSessionId();
        }
        if (player instanceof IjkMediaPlayer) {
            return ((IjkMediaPlayer) player).getAudioSessionId();
        }
        return 0;
    }

    /**
     * Car edition: true when the current video track is HDR/Dolby Vision, in which case the GPU
     * colour grading must stay off (EXO reads real colour metadata, IJK only the codec name).
     */
    public boolean isHdrVideo() {
        AbstractPlayer player = mMediaPlayer;
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).isHdrVideo();
        }
        if (player instanceof IjkMediaPlayer) {
            return ((IjkMediaPlayer) player).isDolbyVision();
        }
        return false;
    }

    public void setArtwork(String url) {
        if (TextUtils.isEmpty(url)) {
            clearArtwork();
            return;
        }
        if (artworkView == null) {
            artworkView = new ImageView(getContext());
            artworkView.setBackgroundColor(android.graphics.Color.BLACK);
            artworkView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            artworkView.setClickable(false);
            artworkView.setFocusable(false);
            int index = mRenderView == null ? 0 : Math.min(1, mPlayerContainer.getChildCount());
            mPlayerContainer.addView(artworkView, index, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        }
        artworkView.setVisibility(VISIBLE);
        ImgUtil.load(url, artworkView, 0, 0, 0, "", ImageView.ScaleType.FIT_CENTER);
    }

    public void clearArtwork() {
        if (artworkView != null) {
            artworkView.setVisibility(GONE);
            artworkView.setImageDrawable(null);
        }
    }

    public int[] getVideoSize() {
        return mVideoSize;
    }

    public long getPlaybackPosition() {
        return mCurrentPosition;
    }

    @Override
    public void seekTo(long pos) {
        if (mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView) {
            ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).resetFrameHistory();
        }
        super.seekTo(pos);
        if (danmuSeekListener != null) {
            danmuSeekListener.onDanmuSeek(pos);
        } else if (haveDanmu()) {
            danmuView.seekTo(pos);
        }
    }

    @Override
    public void resume() {
        super.resume();
        if (haveDanmu()) danmuView.resume();
    }

    @Override
    public void start() {
        super.start();
        if (haveDanmu()) danmuView.resume();
    }

    @Override
    public void pause() {
        super.pause();
        if (haveDanmu()) danmuView.pause();
    }

    @Override
    public void release() {
        super.release();
        if (haveDanmu()) danmuView.release();
        // Car edition: the audio-effect chain is bound to this engine's session, so it dies with it.
        CarEnhance.releaseAudio();
    }

    private boolean haveDanmu() {
        return danmuView != null && danmuView.isPrepared();
    }

    public void setDanmuView(DanmakuView view) {
        danmuView = view;
        if (danmuView != null) danmuView.setCallback(this);
    }

    public DanmakuView getDanmuView() {
        return danmuView;
    }

    public void setDanmuSeekListener(DanmuSeekListener listener) {
        danmuSeekListener = listener;
    }

    @Override
    public void prepared() {
        post(() -> {
            if (danmuView == null) return;
            if (isPlaying() && danmuView.isPrepared()) {
                danmuView.start(getCurrentPosition());
            }
        });
        // Track metadata is available now: HDR always keeps an identity colour transform.
        int mode = CarEnhance.videoMode();
        if (isHdrVideo()) {
            setVideoEnhanceMode(0);
            if (mode != 0) CarEnhance.toast(getContext(), "HDR 视频已自动关闭画面调色");
        } else {
            setVideoEnhanceMode(mode);
        }
        CarEnhance.applyAudio(this);
        if (mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView) {
            ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).setPlaybackActive(isPlaying());
        }
    }

    /**
     * Car edition: turn the GPU colour grading on/off on the active render view. Texture and surface
     * render views do not implement it, so this is a safe no-op unless 臻彩 is in use.
     */
    public void setVideoEnhanceMode(int mode) {
        if (mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView) {
            ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).setVideoEnhanceMode(mode);
        }
    }

    @Override
    protected void setPlayState(int state) {
        super.setPlayState(state);
        if (mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView) {
            boolean active = state == STATE_PLAYING || (state == STATE_BUFFERED && isPlaying());
            ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).setPlaybackActive(active);
        }
    }

    @Override
    public void updateTimer(DanmakuTimer timer) {
    }

    @Override
    public void danmakuShown(BaseDanmaku danmaku) {
    }

    @Override
    public void drawingFinished() {
    }
}
