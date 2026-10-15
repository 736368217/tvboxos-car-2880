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
import java.util.concurrent.locks.LockSupport;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/** OES colour pass -> two source-sized RGBA8 FBOs -> temporal/SGSR1 output pass. */
@SuppressLint("ViewConstructor")
public class GlEnhanceRenderView extends GLSurfaceView implements IRenderView, GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "GlEnhance";
    private static final long OUTPUT_INTERVAL_NS = 16_666_667L;
    private final MeasureHelper mMeasureHelper = new MeasureHelper();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mFrameAvailable = new AtomicBoolean();
    private final AtomicBoolean mResetHistory = new AtomicBoolean();
    private final FloatBuffer mPositions = buffer(-1,-1, 1,-1, -1,1, 1,1);
    private final FloatBuffer mCoordinates = buffer(0,0, 1,0, 0,1, 1,1);
    private final float[] mTextureMatrix = new float[16];
    private final int[] mFbos = new int[2];
    private final int[] mFrameTextures = new int[2];
    private final VideoPostProcessPolicy mTiming = new VideoPostProcessPolicy();
    private final Object mProducerLock = new Object();
    private final ArrayList<ProducerSurface> mProducers = new ArrayList<>();
    private Surface mBoundSurface;

    private volatile AbstractPlayer mMediaPlayer;
    private volatile Surface mPlayerSurface;
    private volatile SurfaceTexture mSurfaceTexture;
    private volatile VideoEnhancePreset mPreset;
    private volatile boolean mSrEnabled;
    private volatile boolean mInterpEnabled;
    private volatile boolean mPlaybackActive;
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
    private int mColourProgram, mOutputProgram, mOesTexture;
    private int mFboWidth, mFboHeight, mFailedWidth, mFailedHeight;
    private int mCurrentIndex, mPreviousIndex;
    private int mViewportWidth, mViewportHeight;
    private int cPosition, cCoordinate, cMatrix, cSat, cCon, cBri, cRgb;
    private int oPosition, oCoordinate, oViewport, oParams;
    private long mSourceFrames, mOutputFrames, mMixedOutputs;
    private long mLastOutputNs, mStatsStartNs, mStatsSource, mStatsOutput, mStatsMixed;
    private boolean mLoggedSr, mLastSrActive;
    private int mDrawErrors;

    public GlEnhanceRenderView(Context context) { this(context, 0, false, false); }
    public GlEnhanceRenderView(Context context, int mode) { this(context, mode, false, false); }
    public GlEnhanceRenderView(Context context, int mode, boolean sr, boolean interp) {
        super(context);
        mPreset = VideoEnhancePreset.forMode(mode);
        mSrEnabled = sr;
        mInterpEnabled = interp;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override public void attachToPlayer(@NonNull AbstractPlayer player) {
        mMediaPlayer = player;
        resetFrameHistory();
        bindSurfaceToPlayer();
    }

    private void bindSurfaceToPlayer() {
        final AbstractPlayer player = mMediaPlayer;
        final Surface surface = mPlayerSurface;
        if (player == null || surface == null) return;
        mMain.post(() -> {
            if (mReleased || mMediaPlayer != player || mPlayerSurface != surface || !surface.isValid()) return;
            try {
                player.setSurface(surface);
                synchronized (mProducerLock) {
                    mBoundSurface = surface;
                    releaseRetiredProducers();
                }
                Log.i(TAG, "player surface bound (main thread)");
            } catch (Throwable error) { Log.e(TAG, "player.setSurface failed", error); }
        });
    }

    @Override public void setVideoSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (mVideoWidth != width || mVideoHeight != height) mResetHistory.set(true);
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
        Log.i(TAG, "playback active=" + active + " interp=" + mInterpEnabled);
    }
    public void resetFrameHistory() { mResetHistory.set(true); }
    public boolean isEnhanceMode() { return mPreset.id != 0; }
    public void setVideoEnhanceMode(int mode) {
        mPreset = VideoEnhancePreset.forMode(mode);
        mResetHistory.set(true);
        requestRender();
        logConfiguration();
    }

    private boolean shouldAnimate() {
        return mInterpEnabled && mPostProcessSupported && mPlaybackActive && mAttached
                && mWindowVisible && !mViewPaused && !mReleased;
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
        updateRenderMode();
        super.onPause();
    }
    @Override public void onResume() {
        super.onResume();
        mViewPaused = false;
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
        AbstractPlayer player = mMediaPlayer;
        mMediaPlayer = null;
        mPlaybackActive = false;
        updateRenderMode();
        if (player != null) {
            try {
                player.setSurface(null);
                Log.i(TAG, "player surface detached (main thread)");
            } catch (Throwable error) { Log.w(TAG, "player surface detach failed", error); }
        }
        synchronized (mProducerLock) { mBoundSurface = null; }
    }

    @Override public void release() {
        if (mReleased) return;
        mReleased = true;
        if (Looper.myLooper() == Looper.getMainLooper()) finishRelease();
        else mMain.post(this::finishRelease);
    }
    private void finishRelease() {
        detachFromPlayer();
        // Wait for updateTexImage and the complete draw to finish before releasing its consumer.
        synchronized (mProducerLock) { releaseProducerSurfaces(); }
        try { queueEvent(this::releaseGlResources); }
        catch (Throwable error) { Log.i(TAG, "GL context already stopped; context owns remaining texture deletion"); }
        Log.i(TAG, "release requested source=" + mSourceFrames + " outputs=" + mOutputFrames);
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        synchronized (mProducerLock) { createGlResources(); }
    }
    private void createGlResources() {
        if (mReleased) return;
        releaseGlResources();
        mDrawErrors = 0;
        mSourceFrames = mOutputFrames = mMixedOutputs = 0;
        mStatsStartNs = mLastOutputNs = 0;
        mStatsSource = mStatsOutput = mStatsMixed = 0;
        mFailedWidth = mFailedHeight = 0;
        mColourProgram = VideoShaders.createProgram(VideoShaders.OES_VERTEX, VideoShaders.colourFragment(true));
        if (mColourProgram == 0) { Log.e(TAG, "OES colour shader unavailable"); return; }
        cPosition = GLES20.glGetAttribLocation(mColourProgram, "aPosition");
        cCoordinate = GLES20.glGetAttribLocation(mColourProgram, "aTexCoord");
        cMatrix = GLES20.glGetUniformLocation(mColourProgram, "uTexMatrix");
        cSat = GLES20.glGetUniformLocation(mColourProgram, "uSat");
        cCon = GLES20.glGetUniformLocation(mColourProgram, "uCon");
        cBri = GLES20.glGetUniformLocation(mColourProgram, "uBri");
        cRgb = GLES20.glGetUniformLocation(mColourProgram, "uRgb");
        GLES20.glUseProgram(mColourProgram);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(mColourProgram, "uTexture"), 0);
        mOutputProgram = VideoShaders.createProgram(VideoShaders.OUTPUT_VERTEX, VideoShaders.OUTPUT_FRAGMENT);
        mPostProcessSupported = mOutputProgram != 0;
        if (mPostProcessSupported) {
            oPosition = GLES20.glGetAttribLocation(mOutputProgram, "aPosition");
            oCoordinate = GLES20.glGetAttribLocation(mOutputProgram, "aTexCoord");
            oViewport = GLES20.glGetUniformLocation(mOutputProgram, "uViewportInfo");
            oParams = GLES20.glGetUniformLocation(mOutputProgram, "uParams");
            GLES20.glUseProgram(mOutputProgram);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(mOutputProgram, "uCurrFrameTex"), 0);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(mOutputProgram, "uPrevFrameTex"), 1);
        } else Log.w(TAG, "POSTPROCESS_FALLBACK output shader failed; direct OES colour only");
        createProducerSurface();
        Log.i(TAG, "GL ready version=" + GLES20.glGetString(GLES20.GL_VERSION)
                + " renderer=" + GLES20.glGetString(GLES20.GL_RENDERER));
        logConfiguration();
        updateRenderMode();
    }
    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        mViewportWidth = width;
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
            if (mResetHistory.getAndSet(false)) {
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
                mTiming.onSourceFrame(texture.getTimestamp(), System.nanoTime());
            }
            int width = mViewportWidth, height = mViewportHeight;
            if (width <= 0 || height <= 0) return;
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, width, height);
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (!mHasFrame) return;
            boolean postProcess = mPostProcessSupported && (mSrEnabled || mInterpEnabled);
            if (postProcess) {
                int sw = mVideoWidth > 0 ? mVideoWidth : width;
                int sh = mVideoHeight > 0 ? mVideoHeight : height;
                boolean resized = mFboWidth != sw || mFboHeight != sh;
                postProcess = ensureFrameBuffers(sw, sh);
                if (postProcess) {
                    if (fresh || resized) {
                        boolean hadCurrent = !resized && mFboWidth > 0 && mSourceFrames > 1;
                        int oldIndex = mCurrentIndex;
                        mCurrentIndex = 1 - oldIndex;
                        mPreviousIndex = oldIndex;
                        drawColour(mFbos[mCurrentIndex], sw, sh);
                        mHasPrevious = hadCurrent;
                    }
                    float alpha = mHasPrevious ? mTiming.blendAlpha(System.nanoTime(), mInterpEnabled, shouldAnimate()) : 0;
                    boolean sr = VideoPostProcessPolicy.superResolutionActive(mSrEnabled,
                            mVideoWidth, mVideoHeight, width, height);
                    drawOutput(width, height, sr, alpha);
                    if (alpha > 0.001f) mMixedOutputs++;
                    if (!mLoggedSr || mLastSrActive != sr) {
                        mLoggedSr = true;
                        mLastSrActive = sr;
                        Log.i(TAG, "output srRequested=" + mSrEnabled + " srActive=" + sr
                                + " interp=" + mInterpEnabled + " viewport=" + width + "x" + height);
                    }
                }
            }
            if (!postProcess) drawColour(0, width, height);
            mOutputFrames++;
            mLastOutputNs = now;
            if (mOutputFrames <= 3 || mOutputFrames % 100 == 0) {
                Log.i(TAG, "drawFrame #" + mOutputFrames + " preset=" + mPreset.id + ":" + mPreset.name
                        + " source=" + mSourceFrames + " fbo=" + postProcess + " sr=" + mLastSrActive
                        + " interp=" + mInterpEnabled + " viewport=" + width + "x" + height);
            }
            logFrameStats(now);
        } catch (Throwable error) {
            if (mDrawErrors++ < 3) Log.e(TAG, "draw failed", error);
            if (mDrawErrors == 3) {
                mPostProcessSupported = false;
                releaseFrameBuffers();
                updateRenderMode();
                Log.w(TAG, "POSTPROCESS_FALLBACK repeated draw errors; direct OES colour only");
            }
        }
    }

    @Override public void onFrameAvailable(SurfaceTexture texture) {
        if (mReleased || texture != mSurfaceTexture) return;
        mFrameArrivalNs = System.nanoTime();
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
        GLES20.glGenTextures(2, mFrameTextures, 0);
        GLES20.glGenFramebuffers(2, mFbos, 0);
        for (int i=0; i<2; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameTextures[i]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,mFbos[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,mFrameTextures[i],0);
            int status=GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            int error=GLES20.glGetError();
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE || error != GLES20.GL_NO_ERROR) {
                failFrameBuffers(width,height,"status="+status+" error="+error);
                return false;
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
        mFboWidth=width; mFboHeight=height;
        mCurrentIndex=0; mPreviousIndex=1;
        mHasPrevious=false;
        mTiming.reset();
        long bytes=VideoPostProcessPolicy.frameBufferBytes(width,height);
        Log.i(TAG,"FBO ping-pong allocated 2x "+width+"x"+height+" RGBA8 bytes="+bytes);
        return true;
    }
    private void failFrameBuffers(int width,int height,String reason) {
        releaseFrameBuffers();
        mFailedWidth=width; mFailedHeight=height;
        Log.w(TAG,"POSTPROCESS_FALLBACK FBO "+width+"x"+height+" "+reason);
        mPostProcessSupported = false;
        updateRenderMode();
    }
    private void drawColour(int framebuffer,int width,int height) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer);
        GLES20.glViewport(0,0,width,height);
        GLES20.glUseProgram(mColourProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,mOesTexture);
        GLES20.glUniformMatrix4fv(cMatrix,1,false,mTextureMatrix,0);
        VideoEnhancePreset p=mPreset;
        GLES20.glUniform1f(cSat,p.saturation); GLES20.glUniform1f(cCon,p.contrast);
        GLES20.glUniform1f(cBri,p.brightness); GLES20.glUniform3f(cRgb,p.red,p.green,p.blue);
        drawQuad(cPosition,cCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,0);
    }
    private void drawOutput(int width,int height,boolean sr,float alpha) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
        GLES20.glViewport(0,0,width,height);
        GLES20.glUseProgram(mOutputProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,mFrameTextures[mCurrentIndex]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,mFrameTextures[mHasPrevious?mPreviousIndex:mCurrentIndex]);
        GLES20.glUniform4f(oViewport,1f/mFboWidth,1f/mFboHeight,mFboWidth,mFboHeight);
        GLES20.glUniform3f(oParams,sr?1f:0f,mPreset.sgsrSharpness,alpha);
        drawQuad(oPosition,oCoordinate);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
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
        int[] textures=new int[1];
        GLES20.glGenTextures(1,textures,0); mOesTexture=textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,mOesTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        SurfaceTexture texture=new SurfaceTexture(mOesTexture);
        mSurfaceTexture=texture;
        texture.setOnFrameAvailableListener(this,mMain);
        if (mVideoWidth>0 && mVideoHeight>0) texture.setDefaultBufferSize(mVideoWidth,mVideoHeight);
        mFrameAvailable.set(false);
        mPlayerSurface=new Surface(texture);
        mProducers.add(new ProducerSurface(texture, mPlayerSurface));
        bindSurfaceToPlayer();
    }
    private void logConfiguration() {
        VideoEnhancePreset p=mPreset;
        Log.i(TAG,"mode="+p.id+"("+p.name+") sat="+p.saturation+" con="+p.contrast+" bri="+p.brightness
                +" rgb="+p.red+","+p.green+","+p.blue+" sr="+mSrEnabled+" sharp="+p.sgsrSharpness
                +" interp="+mInterpEnabled+" maxAlpha="+VideoPostProcessPolicy.MAX_BLEND_ALPHA);
    }
    private void logFrameStats(long now) {
        if (mStatsStartNs==0) { mStatsStartNs=now; return; }
        long elapsed=now-mStatsStartNs;
        if (elapsed<2_000_000_000L) return;
        float seconds=elapsed/1_000_000_000f;
        Log.i(TAG,"frame_stats seconds="+seconds+" sourceFps="+(mSourceFrames-mStatsSource)/seconds
                +" outputFps="+(mOutputFrames-mStatsOutput)/seconds+" blended="+(mMixedOutputs-mStatsMixed)
                +" intervalNs="+mTiming.frameIntervalNs()+" active="+mPlaybackActive);
        mStatsStartNs=now; mStatsSource=mSourceFrames; mStatsOutput=mOutputFrames; mStatsMixed=mMixedOutputs;
    }
    private void releaseFrameBuffers() {
        boolean allocated=mFbos[0]!=0 || mFrameTextures[0]!=0;
        GLES20.glDeleteFramebuffers(2,mFbos,0); GLES20.glDeleteTextures(2,mFrameTextures,0);
        mFbos[0]=mFbos[1]=mFrameTextures[0]=mFrameTextures[1]=0;
        mFboWidth=mFboHeight=0; mHasPrevious=false;
        mTiming.reset();
        if (allocated) Log.i(TAG,"FBO ping-pong released");
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
        mSurfaceTexture = null;
        mFrameAvailable.set(false);
        Log.i(TAG, "producer surfaces released; live=0");
    }
    private void releaseGlResources() {
        releaseFrameBuffers();
        if (mOesTexture!=0) GLES20.glDeleteTextures(1,new int[]{mOesTexture},0);
        if (mColourProgram!=0) GLES20.glDeleteProgram(mColourProgram);
        if (mOutputProgram!=0) GLES20.glDeleteProgram(mOutputProgram);
        mOesTexture=mColourProgram=mOutputProgram=0;
        mHasFrame=false; mLoggedSr=false;
    }
    private static FloatBuffer buffer(float... values) {
        FloatBuffer buffer=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }
}
