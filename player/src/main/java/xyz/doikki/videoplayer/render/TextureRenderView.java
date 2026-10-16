package xyz.doikki.videoplayer.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.doikki.videoplayer.player.AbstractPlayer;

@SuppressLint("ViewConstructor")
public class TextureRenderView extends TextureView implements IRenderView, TextureView.SurfaceTextureListener {
    private static final long BIND_TIMEOUT_MS = 5_000L;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private MeasureHelper mMeasureHelper;
    private SurfaceTexture mSurfaceTexture;

    @Nullable
    private AbstractPlayer mMediaPlayer;
    private Surface mSurface;
    private Runnable mOnBound, mOnFailure, mBindTimeout;
    private long mAttachGeneration;
    private boolean mBindingEnabled, mSurfaceCreationFailed;
    private boolean mReleaseRequested, mReleased, mReleaseRetryScheduled;

    public TextureRenderView(Context context) {
        super(context);
    }

    {
        mMeasureHelper = new MeasureHelper();
        setSurfaceTextureListener(this);
    }

    @Override
    public void attachToPlayer(@NonNull AbstractPlayer player) {
        attachToPlayer(player, null, null);
    }

    /** One-shot completion means setSurface has returned, not merely that a View was attached. */
    public void attachToPlayer(@NonNull AbstractPlayer player, @Nullable Runnable onBound,
                               @Nullable Runnable onFailure) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(() -> attachToPlayer(player, onBound, onFailure));
            return;
        }
        if (mReleased || mReleaseRequested) {
            if (onFailure != null) onFailure.run();
            return;
        }
        // Resume/reset may reattach while a handoff is waiting for its producer.
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
                Log.e("TextureRender", "player surface bind timed out");
                finishAttach(generation, false);
            };
            mMain.postDelayed(mBindTimeout, BIND_TIMEOUT_MS);
        }
        if (mSurfaceCreationFailed) finishAttach(generation, false);
        else bindSurfaceToPlayer();
    }

    private void bindSurfaceToPlayer() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(this::bindSurfaceToPlayer);
            return;
        }
        if (!mBindingEnabled || mReleaseRequested || mReleased || mMediaPlayer == null || mSurface == null) return;
        long generation = mAttachGeneration;
        try {
            if (!mSurface.isValid()) throw new IllegalStateException("Invalid Texture surface");
            mMediaPlayer.setSurface(mSurface);
        } catch (Throwable error) {
            Log.e("TextureRender", "player.setSurface failed", error);
            finishAttach(generation, false);
            return;
        }
        finishAttach(generation, true);
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

    /** Cancel callbacks and future binds while retaining any decoder-owned producer. */
    public void cancelPendingAttach() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Cancel requires main thread");
        mAttachGeneration++;
        mBindingEnabled = false;
        clearAttachCallbacks();
    }

    /** The caller has already bound another output, so retiring this View must not detach it. */
    public void relinquishPlayer() {
        cancelPendingAttach();
        mMediaPlayer = null;
    }

    public boolean tryDetachFromPlayer() {
        cancelPendingAttach();
        if (mMediaPlayer != null) {
            try { mMediaPlayer.setSurface(null); }
            catch (Throwable error) {
                Log.e("TextureRender", "detach failed; retaining producer", error);
                return false;
            }
        }
        mMediaPlayer = null;
        return true;
    }

    @Override
    public void setVideoSize(int videoWidth, int videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            mMeasureHelper.setVideoSize(videoWidth, videoHeight);
            requestLayout();
        }
    }

    @Override
    public void setVideoRotation(int degree) {
        mMeasureHelper.setVideoRotation(degree);
        setRotation(degree);
    }

    @Override
    public void setScaleType(int scaleType) {
        mMeasureHelper.setScreenScale(scaleType);
        requestLayout();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public Bitmap doScreenShot() {
        return getBitmap();
    }

    @Override
    public void release() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMain.post(this::release);
            return;
        }
        if (mReleased) return;
        mReleaseRequested = true;
        finishRelease();
    }

    private void finishRelease() {
        if (mReleased) return;
        if (!tryDetachFromPlayer()) {
            if (!mReleaseRetryScheduled) {
                mReleaseRetryScheduled = true;
                mMain.postDelayed(() -> {
                    mReleaseRetryScheduled = false;
                    finishRelease();
                }, 50L);
            }
            return;
        }
        mReleased = true;
        setSurfaceTextureListener(null);
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int[] measuredSize = mMeasureHelper.doMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(measuredSize[0], measuredSize[1]);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (mReleased || mReleaseRequested) return;
        try {
            if (mSurfaceTexture != null) {
                setSurfaceTexture(mSurfaceTexture);
            } else {
                mSurfaceTexture = surfaceTexture;
                mSurface = new Surface(surfaceTexture);
            }
            mSurfaceCreationFailed = false;
        } catch (Throwable error) {
            mSurfaceCreationFailed = true;
            Log.e("TextureRender", "surface creation failed", error);
            finishAttach(mAttachGeneration, false);
            return;
        }
        bindSurfaceToPlayer();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
