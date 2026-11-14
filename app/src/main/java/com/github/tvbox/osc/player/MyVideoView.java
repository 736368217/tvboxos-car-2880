package com.github.tvbox.osc.player;

import android.content.Context;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.util.Log;
import android.view.View;
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
import xyz.doikki.videoplayer.render.VideoEnhanceConfig;

public class MyVideoView extends VideoView implements DrawHandler.Callback,
        xyz.doikki.videoplayer.controller.VolumeBoostCapable {
    public interface DanmuSeekListener {
        void onDanmuSeek(long position);
    }

    public interface VolumeBoostRequestListener {
        boolean onRequestVolumeBoost(float requestedGain);
    }

    private DanmakuView danmuView;
    private ImageView artworkView;
    private DanmuSeekListener danmuSeekListener;
    private final Handler mEnhanceMain = new Handler(Looper.getMainLooper());
    /** 诊断用：上一次真正生效的应用内增益，避免拖动时刷屏。 */
    private float mLastLoggedGain = -1f;
    private volatile float mAppAudioGain = 1f;
    private VolumeBoostRequestListener mVolumeBoostRequestListener;

    public void setVolumeBoostRequestListener(VolumeBoostRequestListener listener) {
        mVolumeBoostRequestListener = listener;
    }

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
    public float getSourceFrameRate() {
        AbstractPlayer player = mMediaPlayer;
        if (player instanceof IjkMediaPlayer) {
            return ((IjkMediaPlayer) player).getVideoFrameRate();
        }
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getVideoFrameRate();
        }
        return 0f;
    }

    public float getRendererSourceFrameRate() {
        if (mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView) {
            return ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).getSourceFrameRate();
        }
        return getSourceFrameRate();
    }

    public float getRendererOutputFrameRate() {
        if (mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView) {
            return ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).getOutputFrameRate();
        }
        return 0f;
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

    public String getVideoEnhancementRuntimeStatus() {
        VideoEnhanceConfig effective = getVideoEnhancementConfig();
        boolean gl = mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView;
        xyz.doikki.videoplayer.render.GlEnhanceRenderView renderer = gl
                ? (xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView : null;
        boolean hdrBypass = isHdrVideo() && CarEnhance.videoMode() != xyz.doikki.videoplayer.render.VideoEnhancePreset.ORIGINAL;
        String colourState = effective.preset.id == xyz.doikki.videoplayer.render.VideoEnhancePreset.ORIGINAL
                ? "原画"
                : renderer != null && renderer.isColourActive() ? "已生效" : "等待画面";
        String srState;
        if (!CarEnhance.videoSrEnabled()) srState = "关闭";
        else if (renderer == null) srState = "renderer 不支持";
        else if (!renderer.isPostProcessSupported()) {
            String failure = renderer.getPostProcessFailure();
            srState = failure.isEmpty() ? "等待 GL 初始化" : "失败：" + failure;
        }
        else if (renderer.isSuperResolutionActive()) srState = "已生效";
        else srState = "等待视频帧";
        String interpState;
        if (!CarEnhance.videoInterpEnabled()) interpState = "关闭";
        else if (renderer == null) interpState = "renderer 不支持";
        else if (!renderer.isPostProcessSupported()) {
            String failure = renderer.getPostProcessFailure();
            interpState = failure.isEmpty() ? "等待 GL 初始化" : "失败：" + failure;
        }
        else if (renderer.isInterpolationActive()) interpState = "已生效";
        else interpState = "等待第二个源帧";
        return "滤镜：" + colourState
                + " · 请求：" + xyz.doikki.videoplayer.render.VideoEnhancePreset.forMode(CarEnhance.videoMode()).name
                + " · 实际 renderer：" + effective.preset.name + (hdrBypass ? "（HDR 旁路调色）" : "")
                + " · SGSR1：" + srState + " · 时域平滑：" + interpState;
    }

    public String getRendererClassName() {
        return mRenderView == null ? "none" : mRenderView.getClass().getSimpleName();
    }

    public android.view.View getRenderViewForDiagnostics() {
        return mRenderView == null ? null : mRenderView.getView();
    }

    /** True when the current output is the GL post-process renderer, not a plain TextureView. */
    public boolean hasEnhancedRenderer() {
        return mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView;
    }

    public boolean isSuperResolutionRuntimeActive() {
        return hasEnhancedRenderer()
                && ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView)
                .isSuperResolutionActive();
    }

    public boolean isInterpolationRuntimeActive() {
        return hasEnhancedRenderer()
                && ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView)
                .isInterpolationActive();

    }
    public String getRenderPath() {
        return hasEnhancedRenderer()
                ? ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).getRenderPath() : "-";
    }

    public long getLastFrameGpuNanos() {
        return hasEnhancedRenderer()
                ? ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).getLastFrameGpuNanos() : 0L;
    }

    public String getSrTargetSizeText() {
        return hasEnhancedRenderer()
                ? ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).getSrTargetSizeText()
                : null;
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
        CarEnhance.releaseVideo(this);
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
        CarEnhance.applyVideo(this);
        if (isHdrVideo() && CarEnhance.videoMode() != 0) {
            CarEnhance.toast(getContext(), "HDR 视频已自动关闭画面调色");
        }
        CarEnhance.applyAudio(this);
    }

    @Override
    public void onPrepared() {
        // Real track metadata is now available, including for controllers without a prepared hook.
        CarEnhance.applyVideo(this);
        super.onPrepared();
    }

    @Override
    public void setVideoEnhancements(int mode, boolean sr, boolean interp) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mEnhanceMain.post(() -> setVideoEnhancements(mode, sr, interp));
            return;
        }
        boolean hdr = isHdrVideo();
        VideoEnhanceConfig configuration = new VideoEnhanceConfig(mode, sr, interp).forHdr(hdr);
        super.setVideoEnhancements(configuration.preset.id, configuration.superResolution, configuration.interpolation);
        Log.i("CarVideo", "runtime_apply requestedMode=" + mode + " effectiveMode=" + configuration.preset.id
                + " hdrBypass=" + hdr + " sr=" + sr + " interp=" + interp
                + " state=" + getCurrentPlayState() + " player=" + System.identityHashCode(mMediaPlayer));
    }

    /** 档位版：直接按 srTier / memcTier 下发，HDR 仍然只旁路调色不改档位。 */
    public void setVideoEnhanceTiers(int mode, int srTier, int memcTier) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mEnhanceMain.post(() -> setVideoEnhanceTiers(mode, srTier, memcTier));
            return;
        }
        boolean hdr = isHdrVideo();
        VideoEnhanceConfig configuration = new VideoEnhanceConfig(mode, srTier, memcTier).forHdr(hdr);
        super.setVideoEnhanceTiers(configuration.preset.id, configuration.srTier, configuration.memcTier);
        Log.i("CarVideo", "runtime_tiers requestedMode=" + mode + " effectiveMode=" + configuration.preset.id
                + " hdrBypass=" + hdr + " srTier=" + configuration.srTier + " memcTier=" + configuration.memcTier
                + " state=" + getCurrentPlayState() + " player=" + System.identityHashCode(mMediaPlayer));
    }
    @Override
    protected void setPlayState(int state) {
        super.setPlayState(state);
        if (state == STATE_PREPARED || state == STATE_PLAYING || state == STATE_BUFFERED) {
            CarEnhance.applyAudio(this);
        }
        if (mRenderView instanceof xyz.doikki.videoplayer.render.GlEnhanceRenderView) {
            boolean active = state == STATE_PLAYING || (state == STATE_BUFFERED && isPlaying());
            ((xyz.doikki.videoplayer.render.GlEnhanceRenderView) mRenderView).setPlaybackActive(active);
        }
    }
    /** User gain is stored once; PCM and AudioTrack must not both attenuate it. */
    @Override
    public void setVolume(float v1, float v2) {
        setAppAudioGain(Math.max(v1, v2));
        // Keep the base view's saved channels in sync, but route output through applyVolume().
        float base = com.github.tvbox.osc.car.VolumeBoostMath.playerVolume(mAppAudioGain);
        super.setVolume(base, base);
        // 手势每 5% 一档，日志只在跨过 10% 时打，避免滑动时每档拼字符串。
        if (Math.abs(mAppAudioGain - mLastLoggedGain) >= 0.25f) {
            mLastLoggedGain = mAppAudioGain;
            Log.i("CarAudio", "app_gain request=" + Math.round(mAppAudioGain * 100)
                    + "% pcmCapable=" + usesPcmGain());
        }
    }

    @Override
    protected void setOptions() {
        super.setOptions();
        applyVolume();
    }

    @Override
    protected void applyVolume() {
        CarEnhance.applyAudio(this);
    }

    /** Used on prepare, focus changes, foreground resume and window reattachment. */
    public void syncAudioVolume() {
        if (mMediaPlayer == null) return;
        float factor = getAudioVolumeFactor();
        boolean pcm = usesPcmGain();
        float base = com.github.tvbox.osc.car.VolumeBoostMath.outputVolume(mAppAudioGain, factor, pcm);
        mMediaPlayer.setVolume(base, base);
        if (pcm) applyPcmGain(com.github.tvbox.osc.car.VolumeBoostMath.pcmGain(mAppAudioGain, factor));
    }

    @Override
    protected void setPlayerState(int state) {
        super.setPlayerState(state);
        applyVolume();
    }

    /** ExoPlayer 走 PCM 采样增益（不依赖车机系统音效）；其它引擎返回 false。 */
    public boolean usesPcmGain() {
        return mMediaPlayer instanceof com.github.tvbox.osc.player.ExoPlayer;
    }

    /** {@link xyz.doikki.videoplayer.controller.VolumeBoostCapable}: PCM-backed engine capability. */
    @Override
    public boolean supportsVolumeBoost() {
        return usesPcmGain();
    }

    @Override
    public boolean requestVolumeBoost(float requestedGain) {
        if (supportsVolumeBoost()) return true;
        VolumeBoostRequestListener listener = mVolumeBoostRequestListener;
        return listener != null && listener.onRequestVolumeBoost(requestedGain);
    }
    /** 只对 PCM 增益引擎生效；IJK 等走 LoudnessEnhancer。 */
    public void applyPcmGain(float gain) {
        ((com.github.tvbox.osc.player.ExoPlayer) mMediaPlayer).setAudioGain(gain);
    }

    /**
     * 运行中切换"视频音量自动对齐"。默认开启。
     * 关闭后退回 r77 行为：只应用用户固定增益，不做片源电平补偿。
     */
    public void setSourceLevelMatchEnabled(boolean enabled) {
        if (mMediaPlayer instanceof com.github.tvbox.osc.player.ExoPlayer) {
            ((com.github.tvbox.osc.player.ExoPlayer) mMediaPlayer)
                    .setSourceLevelMatchEnabled(enabled);
        }
    }

    /** 运行中切换"音频通道对齐音乐"（显式 USAGE_MEDIA）。默认开启。 */
    public void setAudioUsageMedia(boolean enabled) {
        if (mMediaPlayer instanceof com.github.tvbox.osc.player.ExoPlayer) {
            ((com.github.tvbox.osc.player.ExoPlayer) mMediaPlayer)
                    .setAudioUsageMedia(enabled);
        }
    }

    public float getAppAudioGain() {
        return mAppAudioGain;
    }

    public void setAppAudioGain(float gain) {
        mAppAudioGain = com.github.tvbox.osc.car.VolumeBoostMath.clampGain(gain);
    }

    public float getAudioVolumeFactor() {
        return getVolumeFactor();
    }

    public float getPcmGain() {
        return mMediaPlayer instanceof com.github.tvbox.osc.player.ExoPlayer
                ? ((com.github.tvbox.osc.player.ExoPlayer) mMediaPlayer).getAudioGain() : -1f;
    }

    public long getPcmGainQueueCount() {
        return mMediaPlayer instanceof com.github.tvbox.osc.player.ExoPlayer
                ? ((com.github.tvbox.osc.player.ExoPlayer) mMediaPlayer).getAudioGainQueueCount() : -1L;
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
