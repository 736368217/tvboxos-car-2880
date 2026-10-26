package xyz.doikki.videoplayer.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/** OES colour pass -> two source-sized RGBA8 FBOs -> temporal/SGSR1 output pass. */
@SuppressLint("ViewConstructor")
public class GlEnhanceRenderView extends GLSurfaceView implements IRenderView, GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "GlEnhance";
    private static final long OUTPUT_INTERVAL_NS = 16_666_667L;
    private static final long RELEASE_RETRY_DELAY_MS = 50L;
    private static final long BIND_TIMEOUT_MS = 5_000L;
    private final MeasureHelper mMeasureHelper = new MeasureHelper();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mFrameAvailable = new AtomicBoolean();
    private final AtomicLong mSourceFrameArrivals = new AtomicLong();
    private final AtomicBoolean mResetHistory = new AtomicBoolean();
    private final FloatBuffer mPositions = buffer(-1,-1, 1,-1, -1,1, 1,1);
    private final FloatBuffer mCoordinates = buffer(0,0, 1,0, 0,1, 1,1);
    private final float[] mTextureMatrix = new float[16];
    private static final int RING = 4;
    private final int[] mFbos = new int[RING];
    private final int[] mFrameTextures = new int[RING];
    private int mRingHead = -1;
    private int mValidFrames;
    private int mSynthFbo, mSynthTexture;
    private int mMemcProgram;
    private int mMemcPos, mMemcTex, mMemcF0, mMemcF1, mMemcF2, mMemcF3, mMemcAlpha, mMemcTier, mMemcTexel;
    private final int[] mSrPrograms = new int[4];
    private final int[] mSrPos = new int[4];
    private final int[] mSrTex = new int[4];
    private final int[] mSrTexCoordAttr = new int[4];
    private final int[] mSrTexel = new int[4];
    private final int[] mSrViewport = new int[4];
    private final int[] mSrSharpness = new int[4];
    private final VideoPostProcessPolicy mTiming = new VideoPostProcessPolicy();
    private final Object mProducerLock = new Object();
    private final ArrayList<ProducerSurface> mProducers = new ArrayList<>();
    private Surface mBoundSurface;
    private AbstractPlayer mBoundPlayer;

    private volatile AbstractPlayer mMediaPlayer;
    private volatile Surface mPlayerSurface;
    private volatile SurfaceTexture mSurfaceTexture;
    private boolean mProducerDetached;
    private volatile long mAttachGeneration;
    private volatile boolean mBindingEnabled, mSurfaceCreationFailed;
    private Runnable mOnBound, mOnFailure, mBindTimeout;
    private volatile VideoEnhanceConfig mConfiguration;
    private VideoEnhanceConfig mDrawConfiguration;
    private volatile boolean mPlaybackActive;
    private volatile boolean mReleaseRequested;
    private boolean mReleaseRetryScheduled;
    private volatile boolean mReleased;
    private volatile boolean mAttached;
    private volatile boolean mWindowVisible = true;
    private volatile boolean mViewPaused;
    private volatile int mVideoWidth, mVideoHeight;
    private volatile long mFrameArrivalNs;
    private boolean mContinuous;
    private volatile boolean mPostProcessSupported;
    private boolean mHasFrame;
    private boolean mHasPrevious;
    private boolean mFboHasFrame;
    private int mColourProgram, mOutputProgram, mOesTexture;
    private int mFboWidth, mFboHeight, mFailedWidth, mFailedHeight;
    private int mCurrentIndex, mPreviousIndex;
    private int mViewportWidth, mViewportHeight;
    private int cPosition, cCoordinate, cMatrix, cSat, cCon, cBri, cRgb;
    private int oPosition, oCoordinate, oViewport, oParams;
    private long mSourceFrames, mOutputFrames, mMixedOutputs;
    private volatile float mSourceFps, mOutputFps;
    private volatile boolean mRuntimeSrActive, mRuntimeInterpolationActive, mRuntimeColourActive;
    private volatile long mLastBlendNs;
    private volatile String mPostProcessFailure = "";
    private long mLastOutputNs, mStatsStartNs, mStatsSource, mStatsOutput, mStatsMixed;
    private boolean mLoggedSr, mLastSrActive;
    private int mDrawErrors;
    /** 新档位管线（4 帧 ring + 新 shader）是否可用；不可用时自动走回原 2 帧基线管线。 */
    private boolean mTierPipelineOk;

    public GlEnhanceRenderView(Context context) { this(context, 0, false, false); }
    public GlEnhanceRenderView(Context context, int mode) { this(context, mode, false, false); }
    public GlEnhanceRenderView(Context context, int mode, int srTier, int memcTier) {
        this(context, mode, srTier > VideoEnhanceTiers.SR_OFF, memcTier > VideoEnhanceTiers.MEMC_OFF);
        mConfiguration = new VideoEnhanceConfig(mode, srTier, memcTier);
    }
    public GlEnhanceRenderView(Context context, int mode, boolean sr, boolean interp) {
        super(context);
        mConfiguration = new VideoEnhanceConfig(mode, sr, interp);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setEGLContextFactory(new ProducerContextFactory());
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    /** GLSurfaceView stops its GLThread when reparented, even with preserve-on-pause.
     * Detach the consumer while the old context is still alive so MediaCodec keeps
     * the same BufferQueue and output Surface across preview/fullscreen transitions.
     */
    private final class ProducerContextFactory implements EGLContextFactory {
        private EGLConfig mConfig;

        @Override public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            mConfig = config;
            return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                    new int[]{0x3098, 2, EGL10.EGL_NONE});
        }

        @Override public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            // EglHelper unbinds its window before this callback. A 1x1 pbuffer lets
            // SurfaceTexture detach and GL deletion run on the owning context.
            EGLSurface pbuffer = egl.eglCreatePbufferSurface(display, mConfig,
                    new int[]{EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE});
            boolean current = pbuffer != null && pbuffer != EGL10.EGL_NO_SURFACE
                    && egl.eglMakeCurrent(display, pbuffer, pbuffer, context);
            try {
                synchronized (mProducerLock) {
                    if (current) {
                        if (mSurfaceTexture != null && !mReleased && !mReleaseRequested) {
                            try {
                                mSurfaceTexture.detachFromGLContext();
                                mProducerDetached = true;
                                mOesTexture = 0; // detach deletes the old OES name.
                                Log.i(TAG, "producer context detached; surface="
                                        + System.identityHashCode(mPlayerSurface));
                            } catch (RuntimeException error) {
                                Log.e(TAG, "PRODUCER_PRESERVE_FAILED texture detach", error);
                            }
                        }
                        releaseGlResources();
                    } else {
                        Log.e(TAG, "PRODUCER_PRESERVE_FAILED pbuffer current error=" + egl.eglGetError());
                        // Names belonged to this dying context, never delete them in its successor.
                        mOesTexture = mColourProgram = mOutputProgram = 0;
                        mFbos[0] = mFbos[1] = mFrameTextures[0] = mFrameTextures[1] = 0;
                        mFboWidth = mFboHeight = 0;
                        mHasFrame = mHasPrevious = mFboHasFrame = false;
                    }
                }
            } finally {
                if (current) egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE,
                        EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                if (pbuffer != null && pbuffer != EGL10.EGL_NO_SURFACE) egl.eglDestroySurface(display, pbuffer);
                egl.eglDestroyContext(display, context);
            }
        }
    }

    @Override public void attachToPlayer(@NonNull AbstractPlayer player) {
        attachToPlayer(player, null, null);
    }

    /** A bounded, one-shot acknowledgement of the actual main-thread setSurface call. */
    public void attachToPlayer(@NonNull AbstractPlayer player, Runnable onBound, Runnable onFailure) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(() -> attachToPlayer(player, onBound, onFailure));
            return;
        }
        if (mReleased || mReleaseRequested) {
            if (onFailure != null) onFailure.run();
            return;
        }
        if (onBound == null && onFailure == null && mBindTimeout != null && mMediaPlayer == player) {
            bindSurfaceToPlayer();
            return;
        }
        cancelPendingAttach();
        mMediaPlayer = player;
        mBindingEnabled = true;
        mOnBound = onBound;
        mOnFailure = onFailure;
        final long generation = mAttachGeneration;
        if (onBound != null || onFailure != null) {
            mBindTimeout = () -> {
                Log.e(TAG, "player surface bind timed out");
                finishAttach(generation, false);
            };
            mMain.postDelayed(mBindTimeout, BIND_TIMEOUT_MS);
        }
        resetFrameHistory();
        if (mSurfaceCreationFailed) finishAttach(generation, false);
        else bindSurfaceToPlayer();
    }

    private AbstractPlayer mProducerResumePlayer;
    private long mProducerResumeAtMs;

    private void bindSurfaceToPlayer() {
        final AbstractPlayer player = mMediaPlayer;
        final Surface surface = mPlayerSurface;
        final long generation = mAttachGeneration;
        if (player == null || surface == null || !mBindingEnabled) return;
        mMain.post(() -> {
            Throwable failure = null;
            synchronized (mProducerLock) {
                if (mReleased || mReleaseRequested || !mBindingEnabled || generation != mAttachGeneration
                        || mMediaPlayer != player || mPlayerSurface != surface) return;
                if (mProducerResumePlayer == player) {
                    long remaining = mProducerResumeAtMs - android.os.SystemClock.uptimeMillis();
                    if (remaining > 0) { mMain.postDelayed(this::bindSurfaceToPlayer, remaining); return; }
                }
                try {
                    if (!surface.isValid()) throw new IllegalStateException("Invalid GL producer surface");
                    if (player instanceof xyz.doikki.videoplayer.ijk.IjkPlayer
                            && mBoundSurface != null && mBoundSurface != surface && player.isPlaying()) {
                        // Let MediaCodec stop dequeuing the old consumer before reconfiguration.
                        // This is an output handoff, never a decoder/source restart or a seek.
                        player.pause();
                        mProducerResumePlayer = player;
                        mProducerResumeAtMs = android.os.SystemClock.uptimeMillis() + 100L;
                        mMain.postDelayed(this::bindSurfaceToPlayer, 100L);
                        return;
                    }
                    // A new EGL window/context does not require a new decoder output.
                    if (mBoundSurface != surface || mBoundPlayer != player) player.setSurface(surface);
                    mBoundSurface = surface;
                    mBoundPlayer = player;
                    if (mProducerResumePlayer == player) {
                        mProducerResumePlayer = null;
                        if (mPlaybackActive && !mReleaseRequested && !mReleased) player.start();
                    }
                } catch (Throwable error) { failure = error; }
                if (failure == null) {
                    try { releaseRetiredProducers(); }
                    catch (Throwable error) { Log.e(TAG, "retired producer cleanup failed", error); }
                }
            }
            if (failure != null) {
                Log.e(TAG, "player.setSurface failed", failure);
                finishAttach(generation, false);
            } else {
                Log.i(TAG, "player surface bound (main thread)");
                finishAttach(generation, true);
            }
        });
    }

    private void finishAttach(long generation, boolean success) {
        if (generation != mAttachGeneration) return;
        Runnable callback = success ? mOnBound : mOnFailure;
        clearAttachCallbacks();
        if (!success) {
            mBindingEnabled = false;
            mAttachGeneration++;
        }
        if (callback != null) callback.run();
    }

    private void clearAttachCallbacks() {
        if (mBindTimeout != null) mMain.removeCallbacks(mBindTimeout);
        mOnBound = mOnFailure = mBindTimeout = null;
    }

    /** Invalidate queued binds without releasing a possibly decoder-owned producer. */
    public void cancelPendingAttach() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Cancel requires main thread");
        mAttachGeneration++;
        mBindingEnabled = false;
        clearAttachCallbacks();
    }

    /** Another renderer has successfully bound: release must never clear its new output. */
    public void relinquishPlayer() {
        cancelPendingAttach();
        mMediaPlayer = null;
        mProducerResumePlayer = null;
        mProducerResumeAtMs = 0;
        mPlaybackActive = false;
        synchronized (mProducerLock) { mBoundSurface = null; mBoundPlayer = null; }
        updateRenderMode();
    }

    @Override public void setVideoSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        boolean sizeChanged = mVideoWidth != width || mVideoHeight != height;
        if (sizeChanged) {
            mResetHistory.set(true);
            if (mPostProcessFailure.startsWith("FBO ")) {
                mPostProcessFailure = "";
                mFailedWidth = mFailedHeight = 0;
            }
        }
        mVideoWidth = width;
        mVideoHeight = height;
        mMeasureHelper.setVideoSize(width, height);
        synchronized (mProducerLock) {
            SurfaceTexture texture = mSurfaceTexture;
            if (texture != null) {
                try { texture.setDefaultBufferSize(width, height); }
                catch (Throwable error) { Log.w(TAG, "setDefaultBufferSize failed", error); }
            }
        }
        requestLayout();
        requestRender();
        Log.i(TAG, "setVideoSize " + width + "x" + height);
    }

    @Override public void setVideoRotation(int degree) {
        mMeasureHelper.setVideoRotation(degree);
        setRotation(degree);
    }
    @Override public void setScaleType(int scaleType) {
        mMeasureHelper.setScreenScale(scaleType);
        requestLayout();
    }
    @Override public View getView() { return this; }
    @Override public Bitmap doScreenShot() { return null; }

    public void setPlaybackActive(boolean active) {
        if (mPlaybackActive == active) return;
        mPlaybackActive = active;
        resetFrameHistory();
        updateRenderMode();
        requestRender();
        Log.i(TAG, "playback active=" + active + " interp=" + mConfiguration.interpolation);
    }
    public void resetFrameHistory() { mResetHistory.set(true); }
    public boolean isEnhanceMode() { return mConfiguration.preset.id != 0; }
    public void setVideoEnhanceMode(int mode) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mMain.post(() -> setVideoEnhanceMode(mode)); return; }
        setVideoEnhancements(mode, mConfiguration.superResolution, mConfiguration.interpolation);
    }
    public void setVideoSrEnabled(boolean enabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mMain.post(() -> setVideoSrEnabled(enabled)); return; }
        setVideoEnhancements(mConfiguration.preset.id, enabled, mConfiguration.interpolation);
    }
    public void setVideoInterpEnabled(boolean enabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) { mMain.post(() -> setVideoInterpEnabled(enabled)); return; }
        setVideoEnhancements(mConfiguration.preset.id, mConfiguration.superResolution, enabled);
    }
    public void setVideoEnhancements(int mode, boolean sr, boolean interp) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(() -> setVideoEnhancements(mode, sr, interp));
            return;
        }
        VideoEnhanceConfig configuration = new VideoEnhanceConfig(mode, sr, interp);
        if (mReleased || configuration.sameAs(mConfiguration)) return;
        mConfiguration = configuration;
        updateRenderMode();
        requestRender();
        Log.i(TAG, "runtime_config requested " + configuration);
    }

    /** 档位版运行时切换：srTier / memcTier 都是 0..3。 */
    public void setVideoEnhanceTiers(int mode, int srTier, int memcTier) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(() -> setVideoEnhanceTiers(mode, srTier, memcTier));
            return;
        }
        VideoEnhanceConfig configuration = new VideoEnhanceConfig(mode, srTier, memcTier);
        if (mReleased || configuration.sameAs(mConfiguration)) return;
        mConfiguration = configuration;
        updateRenderMode();
        requestRender();
        Log.i(TAG, "runtime_tiers requested " + configuration);
    }
    private boolean isRuntimePostProcessAvailable() {
        return mPostProcessSupported && mPostProcessFailure.length() == 0;
    }
    private boolean shouldAnimate() {
        return mConfiguration.interpolation && isRuntimePostProcessAvailable() && mPlaybackActive && mAttached
                && mWindowVisible && !mViewPaused && !mReleased && !mReleaseRequested;
    }
    private void resetFpsStats() {
        mSourceFps = mOutputFps = 0f;
        mStatsStartNs = mLastOutputNs = 0;
        mStatsSource = mStatsOutput = mStatsMixed = 0;
        mSourceFrameArrivals.set(0);
        mLastBlendNs = 0;
    }
    private void updateRenderMode() {
        if (Looper.myLooper() != Looper.getMainLooper()) { mMain.post(this::updateRenderMode); return; }
        boolean continuous = shouldAnimate();
        if (mContinuous != continuous) {
            mContinuous = continuous;
            setRenderMode(continuous ? RENDERMODE_CONTINUOUSLY : RENDERMODE_WHEN_DIRTY);
            Log.i(TAG, "renderMode=" + (continuous ? "CONTINUOUS_60HZ_MAX" : "WHEN_DIRTY"));
        }
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        updateRenderMode();
    }
    @Override protected void onDetachedFromWindow() {
        mAttached = false;
        updateRenderMode();
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisible = visibility == VISIBLE;
        if (mMain != null) updateRenderMode();
    }
    @Override public void onPause() {
        mViewPaused = true;
        resetFpsStats();
        updateRenderMode();
        super.onPause();
    }
    @Override public void onResume() {
        super.onResume();
        mViewPaused = false;
        resetFpsStats();
        resetFrameHistory();
        updateRenderMode();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int[] size = mMeasureHelper.doMeasure(widthSpec, heightSpec);
        setMeasuredDimension(size[0], size[1]);
    }

    /** Called before the engine's asynchronous release, while it can still detach its output. */
    public void detachFromPlayer() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(this::detachFromPlayer);
            return;
        }
        tryDetachFromPlayer();
    }

    /** Final teardown retains the producer if the decoder rejects the detach. */
    public boolean tryDetachFromPlayer() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Detach requires main thread");
        cancelPendingAttach();
        if (mReleased) return true;
        AbstractPlayer player = mMediaPlayer;
        if (player != null) {
            try {
                player.setSurface(null);
                Log.i(TAG, "player surface detached (main thread)");
            } catch (Throwable error) {
                Log.e(TAG, "player surface detach failed; retaining bound producer", error);
                return false;
            }
        }
        relinquishPlayer();
        return true;
    }

    @Override public void release() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(this::release);
            return;
        }
        if (mReleased) return;
        mReleaseRequested = true;
        mPlaybackActive = false;
        finishRelease();
    }
    private void finishRelease() {
        if (mReleased) return;
        updateRenderMode();
        if (!tryDetachFromPlayer()) {
            Log.e(TAG, "RELEASE_DEFERRED still-bound producer retained");
            scheduleReleaseRetry();
            return;
        }
        // mReleased is terminal only after the decoder no longer targets the producer.
        mReleased = true;
        synchronized (mProducerLock) { releaseProducerSurfaces(); }
        try { queueEvent(this::releaseGlResources); }
        catch (Throwable error) { Log.i(TAG, "GL context already stopped; context owns remaining texture deletion"); }
        Log.i(TAG, "release requested source=" + mSourceFrames + " outputs=" + mOutputFrames);
    }
    private void scheduleReleaseRetry() {
        if (mReleaseRetryScheduled) return;
        mReleaseRetryScheduled = true;
        mMain.postDelayed(() -> {
            mReleaseRetryScheduled = false;
            if (mReleaseRequested && !mReleased) finishRelease();
        }, RELEASE_RETRY_DELAY_MS);
    }
    public boolean isReleased() {
        return mReleased;
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            synchronized (mProducerLock) {
                mSurfaceCreationFailed = false;
                createGlResources();
            }
        } catch (Throwable error) {
            mSurfaceCreationFailed = true;
            Log.e(TAG, "GL producer creation failed", error);
            final long generation = mAttachGeneration;
            mMain.post(() -> finishAttach(generation, false));
        }
    }
    private void createGlResources() {
        if (mReleased || mReleaseRequested) return;
        releaseGlResources();
        mDrawErrors = 0;
        mSourceFrames = mOutputFrames = mMixedOutputs = 0;
        mSourceFps = mOutputFps = 0f;
        mSourceFrameArrivals.set(0);
        mLastBlendNs = 0;
        mRuntimeSrActive = mRuntimeInterpolationActive = mRuntimeColourActive = false;
        mPostProcessFailure = "";
        mStatsStartNs = mLastOutputNs = 0;
        mStatsSource = mStatsOutput = mStatsMixed = 0;
        mFailedWidth = mFailedHeight = 0;
        mPostProcessSupported = false;
        mDrawConfiguration = null;
        mColourProgram = VideoShaders.createProgram(VideoShaders.OES_VERTEX, VideoShaders.colourFragment(true));
        if (mColourProgram == 0) {
            mPostProcessFailure = "OES shader 编译或链接失败";
            throw new IllegalStateException("OES colour shader unavailable");
        }
        cPosition = GLES20.glGetAttribLocation(mColourProgram, "aPosition");
        cCoordinate = GLES20.glGetAttribLocation(mColourProgram, "aTexCoord");
        cMatrix = GLES20.glGetUniformLocation(mColourProgram, "uTexMatrix");
        cSat = GLES20.glGetUniformLocation(mColourProgram, "uSat");
        cCon = GLES20.glGetUniformLocation(mColourProgram, "uCon");
        cBri = GLES20.glGetUniformLocation(mColourProgram, "uBri");
        cRgb = GLES20.glGetUniformLocation(mColourProgram, "uRgb");
        GLES20.glUseProgram(mColourProgram);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(mColourProgram, "uTexture"), 0);
        mMemcProgram = VideoShaders.createProgram(VideoShaders.OUTPUT_VERTEX, VideoTierShaders.MEMC_FRAGMENT);
        if (mMemcProgram != 0) {
            mMemcPos = GLES20.glGetAttribLocation(mMemcProgram, "aPosition");
            mMemcTex = GLES20.glGetAttribLocation(mMemcProgram, "aTexCoord");
            mMemcF0 = GLES20.glGetUniformLocation(mMemcProgram, "uF0");
            mMemcF1 = GLES20.glGetUniformLocation(mMemcProgram, "uF1");
            mMemcF2 = GLES20.glGetUniformLocation(mMemcProgram, "uF2");
            mMemcF3 = GLES20.glGetUniformLocation(mMemcProgram, "uF3");
            mMemcAlpha = GLES20.glGetUniformLocation(mMemcProgram, "uAlpha");
            mMemcTier = GLES20.glGetUniformLocation(mMemcProgram, "uTier");
            mMemcTexel = GLES20.glGetUniformLocation(mMemcProgram, "uTexel");
            GLES20.glUseProgram(mMemcProgram);
            GLES20.glUniform1i(mMemcF0, 0);
            GLES20.glUniform1i(mMemcF1, 1);
            GLES20.glUniform1i(mMemcF2, 2);
            GLES20.glUniform1i(mMemcF3, 3);
        }
        String[] srSources = {
                VideoTierShaders.SR_PASSTHROUGH_FRAGMENT,
                VideoTierShaders.SR_SGSR_FRAGMENT,
                VideoTierShaders.SR_FSR_FRAGMENT,
                VideoTierShaders.SR_ANIME_FRAGMENT
        };
        boolean srOk = true;
        for (int i = 0; i < srSources.length; i++) {
            mSrPrograms[i] = VideoShaders.createProgram(VideoShaders.OUTPUT_VERTEX, srSources[i]);
            if (mSrPrograms[i] == 0) { srOk = false; continue; }
            mSrPos[i] = GLES20.glGetAttribLocation(mSrPrograms[i], "aPosition");
            mSrTexCoordAttr[i] = GLES20.glGetAttribLocation(mSrPrograms[i], "aTexCoord");
            mSrTex[i] = GLES20.glGetUniformLocation(mSrPrograms[i], "uTexture");
            mSrTexel[i] = GLES20.glGetUniformLocation(mSrPrograms[i], "uTexel");
            mSrViewport[i] = GLES20.glGetUniformLocation(mSrPrograms[i], "uViewportInfo");
            mSrSharpness[i] = GLES20.glGetUniformLocation(mSrPrograms[i], "uSharpness");
            GLES20.glUseProgram(mSrPrograms[i]);
            GLES20.glUniform1i(mSrTex[i], 0);
        }
        // 原 2 帧基线管线：任何情况下都保留，1 档/回退都走它
        mOutputProgram = VideoShaders.createProgram(VideoShaders.OUTPUT_VERTEX, VideoShaders.OUTPUT_FRAGMENT);
        if (mOutputProgram != 0) {
            oPosition = GLES20.glGetAttribLocation(mOutputProgram, "aPosition");
            oCoordinate = GLES20.glGetAttribLocation(mOutputProgram, "aTexCoord");
            oViewport = GLES20.glGetUniformLocation(mOutputProgram, "uViewportInfo");
            oParams = GLES20.glGetUniformLocation(mOutputProgram, "uParams");
            GLES20.glUseProgram(mOutputProgram);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(mOutputProgram, "uCurrFrameTex"), 0);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(mOutputProgram, "uPrevFrameTex"), 1);
        }
        mTierPipelineOk = mMemcProgram != 0 && srOk;
        mPostProcessSupported = mOutputProgram != 0 || mTierPipelineOk;
        if (!mPostProcessSupported) {
            mPostProcessFailure = "输出 shader 编译或链接失败";
            Log.w(TAG, "POSTPROCESS_FALLBACK all shaders failed; direct OES colour only");
        } else if (!mTierPipelineOk) {
            Log.w(TAG, "TIER_PIPELINE_UNAVAILABLE new tier shaders failed; fallback to baseline program");
        }
        createProducerSurface();
        Log.i(TAG, "GL ready version=" + GLES20.glGetString(GLES20.GL_VERSION)
                + " renderer=" + GLES20.glGetString(GLES20.GL_RENDERER));
        logConfiguration();
        updateRenderMode();
    }
    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        mViewportWidth = width;
        if (mPostProcessFailure.startsWith("FBO ")) {
            mPostProcessFailure = "";
            mFailedWidth = mFailedHeight = 0;
        }
        mViewportHeight = height;
        mLoggedSr = false;
        requestRender();
    }

    @Override public void onDrawFrame(GL10 gl) {
        // Pacing must not hold the producer lock needed by the main-thread lifecycle.
        if (shouldAnimate() && mLastOutputNs > 0) {
            long remaining = OUTPUT_INTERVAL_NS - (System.nanoTime() - mLastOutputNs);
            if (remaining > 0) LockSupport.parkNanos(remaining);
        }
        synchronized (mProducerLock) { drawFrame(); }
    }
    private void drawFrame() {
        if (mReleased || mColourProgram == 0) return;
        try {
            long now = System.nanoTime();
            VideoEnhanceConfig configuration = mConfiguration;
            boolean changed = !configuration.sameAs(mDrawConfiguration);
            boolean reset = mResetHistory.getAndSet(false) || changed;
            if (changed) {
                mDrawConfiguration = configuration;
                mLoggedSr = false;
                mFboHasFrame = false;
                if (mPostProcessSupported && (mPostProcessFailure.startsWith("FBO ") || "GL 绘制错误".equals(mPostProcessFailure))) {
                    mPostProcessFailure = "";
                    mFailedWidth = mFailedHeight = 0;
                    mDrawErrors = 0;
                }
                Log.i(TAG, "runtime_config applied (GL thread) " + configuration);
                logConfiguration(configuration);
            }
            if (reset) {
                mTiming.reset();
                mHasPrevious = false;
            }
            SurfaceTexture texture = mSurfaceTexture;
            boolean fresh = texture != null && mFrameAvailable.getAndSet(false);
            if (fresh) {
                texture.updateTexImage();
                texture.getTransformMatrix(mTextureMatrix);
                mHasFrame = true;
                mSourceFrames++;
                long arrivalNs = mFrameArrivalNs;
                if (arrivalNs <= 0) arrivalNs = System.nanoTime();
                mTiming.onSourceFrame(texture.getTimestamp(), arrivalNs);
            }
            int width = mViewportWidth, height = mViewportHeight;
            if (width <= 0 || height <= 0) return;
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, width, height);
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (!mHasFrame) return;
            boolean postProcessRequested = configuration.superResolution || configuration.interpolation;
            boolean postProcess = isRuntimePostProcessAvailable() && postProcessRequested;
            mRuntimeColourActive = configuration.preset.id != VideoEnhancePreset.ORIGINAL;
            mRuntimeSrActive = false;
            mRuntimeInterpolationActive = false;
            if (postProcess) {
                int sw = mVideoWidth > 0 ? mVideoWidth : width;
                int sh = mVideoHeight > 0 ? mVideoHeight : height;
                boolean resized = mFboWidth != sw || mFboHeight != sh;
                postProcess = ensureFrameBuffers(sw, sh);
                if (postProcess) {
                    if (fresh || resized) {
                        mRingHead = (mRingHead + 1) % RING;
                        drawColour(mFbos[mRingHead], sw, sh, configuration.preset);
                        mValidFrames = Math.min(mValidFrames + 1, RING);
                        mFboHasFrame = true;
                        mHasPrevious = mValidFrames >= 2;
                    } else if (!mFboHasFrame) {
                        mRingHead = (mRingHead + 1) % RING;
                        drawColour(mFbos[mRingHead], sw, sh, configuration.preset);
                        mValidFrames = 1;
                        mFboHasFrame = true;
                        mHasPrevious = false;
                    }
                    float alpha = mHasPrevious
                            ? mTiming.blendAlpha(System.nanoTime(), configuration.interpolation, shouldAnimate())
                            : 0;
                    boolean sr = VideoPostProcessPolicy.superResolutionActive(configuration.superResolution,
                            sw, sh, width, height);
                    boolean advanced = configuration.srTier > VideoEnhanceTiers.SR_SGSR
                            || configuration.memcTier > VideoEnhanceTiers.MEMC_OVERSAMPLE;
                    boolean tierPath = advanced && mTierPipelineOk;
                    if (tierPath) {
                        int memcTier = configuration.memcTier;
                        int srcTexture;
                        if (memcTier > VideoEnhanceTiers.MEMC_OFF && mValidFrames >= 2 && alpha > 0.002f) {
                            srcTexture = drawMemc(sw, sh, memcTier, alpha);
                        } else {
                            srcTexture = mFrameTextures[mRingHead];
                        }
                        int srTier = sr ? configuration.srTier : VideoEnhanceTiers.SR_OFF;
                        drawSr(width, height, srTier, srcTexture, sw, sh, configuration.preset);
                        mRuntimeSrActive = sr && srTier > VideoEnhanceTiers.SR_OFF;
                        mRuntimeInterpolationActive = memcTier > VideoEnhanceTiers.MEMC_OFF;
                    } else {
                        // 1 档基线（或高级管线不可用）：原 2 帧 ping-pong + 原输出 shader，行为与 r41 前一致
                        int prev = (mRingHead - 1 + RING) % RING;
                        drawOutputBaseline(width, height, sr, alpha, configuration.preset,
                                mFrameTextures[mRingHead],
                                mFrameTextures[mHasPrevious ? prev : mRingHead]);
                        mRuntimeSrActive = sr;
                        mRuntimeInterpolationActive = configuration.interpolation;
                    }
                    if (alpha > 0.001f) {
                        mLastBlendNs = System.nanoTime();
                        mMixedOutputs++;
                    }
                    if (!mLoggedSr || mLastSrActive != sr) {
                        mLoggedSr = true;
                        mLastSrActive = sr;
                        Log.i(TAG, "output srTier=" + configuration.srTier + " memcTier=" + configuration.memcTier
                                + " tierPath=" + tierPath + " active=" + sr + " alpha=" + alpha
                                + " viewport=" + width + "x" + height);
                    }
                }
            }
            if (!postProcess) drawColour(0, width, height, configuration.preset);
            mOutputFrames++;
            mLastOutputNs = now;
            if (mOutputFrames <= 3 || mOutputFrames % 100 == 0) {
                Log.i(TAG, "drawFrame #" + mOutputFrames + " preset=" + configuration.preset.id + ":" + configuration.preset.name
                        + " source=" + mSourceFrames + " fbo=" + postProcess + " sr=" + (postProcess && mLastSrActive)
                        + " interp=" + configuration.interpolation + " viewport=" + width + "x" + height);
            }
            logFrameStats(now);
        } catch (Throwable error) {
            if (mDrawErrors++ < 3) Log.e(TAG, "draw failed", error);
            if (mDrawErrors == 3) {
                mPostProcessSupported = false;
                mPostProcessFailure = "GL 绘制错误";
                releaseFrameBuffers();
                updateRenderMode();
                Log.w(TAG, "POSTPROCESS_FALLBACK repeated draw errors; direct OES colour only");
            }
        }
    }

    @Override public void onFrameAvailable(SurfaceTexture texture) {
        if (mReleased || texture != mSurfaceTexture) return;
        mFrameArrivalNs = System.nanoTime();
        mSourceFrameArrivals.incrementAndGet();
        mFrameAvailable.set(true);
        requestRender();
    }

    private boolean ensureFrameBuffers(int width, int height) {
        if (mFboWidth == width && mFboHeight == height && mFbos[0] != 0) return true;
        if (mFailedWidth == width && mFailedHeight == height) return false;
        releaseFrameBuffers();
        int[] max = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0);
        if (width <= 0 || height <= 0 || width > max[0] || height > max[0]) {
            failFrameBuffers(width, height, "GL_MAX_TEXTURE_SIZE=" + max[0]);
            return false;
        }
        GLES20.glGenTextures(RING, mFrameTextures, 0);
        GLES20.glGenFramebuffers(RING, mFbos, 0);
        for (int i = 0; i < RING; i++) {
            if (!createTarget(mFrameTextures[i], mFbos[i], width, height)) {
                failFrameBuffers(width, height, "ring#" + i);
                return false;
            }
        }
        int[] synthTex = new int[1];
        int[] synthFbo = new int[1];
        GLES20.glGenTextures(1, synthTex, 0);
        GLES20.glGenFramebuffers(1, synthFbo, 0);
        mSynthTexture = synthTex[0];
        mSynthFbo = synthFbo[0];
        if (!createTarget(mSynthTexture, mSynthFbo, width, height)) {
            failFrameBuffers(width, height, "synth");
            return false;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        mFboWidth = width; mFboHeight = height;
        mRingHead = -1;
        mValidFrames = 0;
        mHasPrevious = false;
        mTiming.reset();
        Log.i(TAG, "FBO ring allocated " + RING + "x " + width + "x" + height
                + " + synth, bytes=" + (VideoPostProcessPolicy.frameBufferBytes(width, height) * 3));
        return true;
    }

    private boolean createTarget(int texture, int fbo, int width, int height) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        int error = GLES20.glGetError();
        return status == GLES20.GL_FRAMEBUFFER_COMPLETE && error == GLES20.GL_NO_ERROR;
    }
    private void failFrameBuffers(int width,int height,String reason) {
        releaseFrameBuffers();
        mFailedWidth=width; mFailedHeight=height;
        mPostProcessFailure = "FBO " + width + "x" + height + " " + reason;
        Log.w(TAG,"POSTPROCESS_FALLBACK " + mPostProcessFailure);
        updateRenderMode();
    }
    private void drawColour(int framebuffer,int width,int height,VideoEnhancePreset p) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);
        GLES20.glViewport(0,0,width,height);
        GLES20.glUseProgram(mColourProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,mOesTexture);
        GLES20.glUniformMatrix4fv(cMatrix,1,false,mTextureMatrix,0);
        GLES20.glUniform1f(cSat,p.saturation); GLES20.glUniform1f(cCon,p.contrast);
        GLES20.glUniform1f(cBri,p.brightness); GLES20.glUniform3f(cRgb,p.red,p.green,p.blue);
        drawQuad(cPosition,cCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0);
    }

    /** 4 帧时域合成到源分辨率纹理；返回给超分 Pass 采样的纹理。 */
    private int drawMemc(int width, int height, int tier, float alpha) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mSynthFbo);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glUseProgram(mMemcProgram);
        int f2 = mRingHead;
        int f1 = (mRingHead - 1 + RING) % RING;
        int f0 = (mRingHead - 2 + RING) % RING;
        int f3 = f2; // 没有前瞻帧：用当前帧顶替 t+2
        bindFrameTexture(0, mFrameTextures[f0]);
        bindFrameTexture(1, mFrameTextures[f1]);
        bindFrameTexture(2, mFrameTextures[f2]);
        bindFrameTexture(3, mFrameTextures[f3]);
        GLES20.glUniform1f(mMemcAlpha, tier == VideoEnhanceTiers.MEMC_OVERSAMPLE
                ? alpha : (1f - 2f * alpha));
        GLES20.glUniform1i(mMemcTier, tier);
        GLES20.glUniform2f(mMemcTexel, 1f / width, 1f / height);
        drawQuad(mMemcPos, mMemcTex);
        unbindFrameTextures(4);
        return mSynthTexture;
    }

    /** 原始基线输出：2 帧混合 + 原 SGSR 输出 shader（1 档与回退路径）。 */
    private void drawOutputBaseline(int width, int height, boolean sr, float alpha,
                                    VideoEnhancePreset preset, int currTexture, int prevTexture) {
        if (mOutputProgram == 0) return;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glUseProgram(mOutputProgram);
        bindFrameTexture(0, currTexture);
        bindFrameTexture(1, prevTexture);
        GLES20.glUniform4f(oViewport, 1f / mFboWidth, 1f / mFboHeight, mFboWidth, mFboHeight);
        GLES20.glUniform3f(oParams, sr ? 1f : 0f, preset.sgsrSharpness, alpha);
        drawQuad(oPosition, oCoordinate);
        unbindFrameTextures(2);
    }

    /** 按档位把源纹理放大上屏（0=直通，1=SGSR，2=FSR，3=Anime）。 */
    private void drawSr(int width, int height, int tier, int srcTexture,
                        int srcW, int srcH, VideoEnhancePreset preset) {
        int t = VideoEnhanceTiers.clampSr(tier);
        int program = mSrPrograms[t] != 0 ? mSrPrograms[t] : mSrPrograms[0];
        int idx = program == mSrPrograms[t] ? t : 0;
        if (program == 0) return;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glUseProgram(program);
        bindFrameTexture(0, srcTexture);
        GLES20.glUniform4f(mSrViewport[idx], 1f / srcW, 1f / srcH, srcW, srcH);
        GLES20.glUniform2f(mSrTexel[idx], 1f / srcW, 1f / srcH);
        GLES20.glUniform1f(mSrSharpness[idx], preset.sgsrSharpness);
        drawQuad(mSrPos[idx], mSrTexCoordAttr[idx]);
        unbindFrameTextures(1);
    }

    private void bindFrameTexture(int unit, int texture) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
    }

    private void unbindFrameTextures(int count) {
        for (int i = 0; i < count; i++) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }
    private void drawQuad(int position,int coordinate) {
        mPositions.position(0); mCoordinates.position(0);
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(coordinate);
        GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,mPositions);
        GLES20.glVertexAttribPointer(coordinate,2,GLES20.GL_FLOAT,false,0,mCoordinates);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(coordinate);
    }
    private void createProducerSurface() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mOesTexture = textures[0];
        if (mSurfaceTexture != null && mProducerDetached) {
            // attachToGLContext creates the OES object; do not bind this name beforehand.
            mSurfaceTexture.attachToGLContext(mOesTexture);
            mProducerDetached = false;
            configureProducerTexture();
            mFrameAvailable.set(true);
            Log.i(TAG, "producer context attached; surface=" + System.identityHashCode(mPlayerSurface));
            bindSurfaceToPlayer();
            return;
        }
        configureProducerTexture();
        SurfaceTexture texture = new SurfaceTexture(mOesTexture);
        Surface surface = null;
        try {
            surface = new Surface(texture);
            texture.setOnFrameAvailableListener(this, mMain);
            if (mVideoWidth > 0 && mVideoHeight > 0) texture.setDefaultBufferSize(mVideoWidth, mVideoHeight);
        } catch (Throwable error) {
            if (surface != null) surface.release();
            texture.release();
            throw error;
        }
        if (mSurfaceTexture != null) Log.e(TAG, "PRODUCER_RECREATED context migration unavailable");
        mSurfaceTexture = texture;
        mPlayerSurface = surface;
        mProducerDetached = false;
        mFrameAvailable.set(false);
        mProducers.add(new ProducerSurface(texture, surface));
        bindSurfaceToPlayer();
    }
    private void configureProducerTexture() {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mOesTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }
    private void logConfiguration() { logConfiguration(mConfiguration); }
    private void logConfiguration(VideoEnhanceConfig configuration) {
        VideoEnhancePreset p=configuration.preset;
        Log.i(TAG,"mode="+p.id+"("+p.name+") sat="+p.saturation+" con="+p.contrast+" bri="+p.brightness
                +" rgb="+p.red+","+p.green+","+p.blue+" sr="+configuration.superResolution+" sharp="+p.sgsrSharpness
                +" interp="+configuration.interpolation+" maxAlpha="+VideoPostProcessPolicy.MAX_BLEND_ALPHA);
    }
    private void logFrameStats(long now) {
        if (mStatsStartNs==0) { mStatsStartNs=now; return; }
        long elapsed=now-mStatsStartNs;
        if (elapsed<2_000_000_000L) return;
        float seconds=elapsed/1_000_000_000f;
        long sourceArrivals = mSourceFrameArrivals.get();
        mSourceFps=(sourceArrivals-mStatsSource)/seconds;
        mOutputFps=(mOutputFrames-mStatsOutput)/seconds;
        Log.i(TAG,"frame_stats seconds="+seconds+" sourceFps="+mSourceFps
                +" outputFps="+mOutputFps+" blended="+(mMixedOutputs-mStatsMixed)
                +" intervalNs="+mTiming.frameIntervalNs()+" active="+mPlaybackActive);
        mStatsStartNs=now; mStatsSource=sourceArrivals; mStatsOutput=mOutputFrames; mStatsMixed=mMixedOutputs;
    }
    public float getSourceFrameRate() { return mSourceFps; }
    public float getOutputFrameRate() { return mOutputFps; }
    public boolean isSuperResolutionActive() { return mRuntimeSrActive; }
    public boolean isInterpolationActive() {
        return mRuntimeInterpolationActive || (mLastBlendNs > 0 && System.nanoTime() - mLastBlendNs < 2_000_000_000L);
    }
    public boolean isColourActive() { return mRuntimeColourActive; }
    public boolean isPostProcessSupported() { return isRuntimePostProcessAvailable(); }
    public String getPostProcessFailure() { return mPostProcessFailure; }
    private void releaseFrameBuffers() {
        boolean allocated = mFbos[0] != 0 || mFrameTextures[0] != 0 || mSynthFbo != 0;
        GLES20.glDeleteFramebuffers(RING, mFbos, 0);
        GLES20.glDeleteTextures(RING, mFrameTextures, 0);
        for (int i = 0; i < RING; i++) { mFbos[i] = 0; mFrameTextures[i] = 0; }
        if (mSynthFbo != 0) { int[] f = {mSynthFbo}; GLES20.glDeleteFramebuffers(1, f, 0); mSynthFbo = 0; }
        if (mSynthTexture != 0) { int[] t = {mSynthTexture}; GLES20.glDeleteTextures(1, t, 0); mSynthTexture = 0; }
        mRingHead = -1; mValidFrames = 0;
        mFboWidth = mFboHeight = 0; mHasPrevious = false; mFboHasFrame = false;
        mTiming.reset();
        if (allocated) Log.i(TAG, "FBO ring released");
    }
    private static final class ProducerSurface {
        final SurfaceTexture texture;
        final Surface surface;
        ProducerSurface(SurfaceTexture texture, Surface surface) {
            this.texture = texture;
            this.surface = surface;
        }
        void release() {
            texture.setOnFrameAvailableListener(null);
            surface.release();
            texture.release();
        }
    }
    // Caller holds mProducerLock. Keep both the decoder's bound surface and the newest
    // producer alive until setSurface(newSurface) has returned on the main thread.
    private void releaseRetiredProducers() {
        Iterator<ProducerSurface> iterator = mProducers.iterator();
        while (iterator.hasNext()) {
            ProducerSurface producer = iterator.next();
            if (producer.surface != mPlayerSurface && producer.surface != mBoundSurface) {
                producer.release();
                iterator.remove();
                Log.i(TAG, "retired producer released; live=" + mProducers.size());
            }
        }
    }
    private void releaseProducerSurfaces() {
        for (ProducerSurface producer : mProducers) producer.release();
        mProducers.clear();
        mPlayerSurface = mBoundSurface = null;
        mBoundPlayer = null;
        mSurfaceTexture = null;
        mFrameAvailable.set(false);
        Log.i(TAG, "producer surfaces released; live=0");
    }
    private void releaseGlResources() {
        releaseFrameBuffers();
        if (mOesTexture!=0) GLES20.glDeleteTextures(1,new int[]{mOesTexture},0);
        if (mColourProgram!=0) GLES20.glDeleteProgram(mColourProgram);
        if (mOutputProgram!=0) GLES20.glDeleteProgram(mOutputProgram);
        if (mMemcProgram!=0) GLES20.glDeleteProgram(mMemcProgram);
        for (int i = 0; i < mSrPrograms.length; i++) {
            if (mSrPrograms[i] != 0) { GLES20.glDeleteProgram(mSrPrograms[i]); mSrPrograms[i] = 0; }
        }
        mOesTexture=mColourProgram=mOutputProgram=mMemcProgram=0;
        mHasFrame=false; mLoggedSr=false;
    }
    private static FloatBuffer buffer(float... values) {
        FloatBuffer buffer=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }
}
