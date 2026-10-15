package xyz.doikki.videoplayer.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * 基于 GLSurfaceView 的 GPU 色彩增强渲染 View，在画面到达屏幕前做饱和度/对比度/亮度调色。
 *
 * 播放器依然解码到 Surface 上（Surface 由 SurfaceTexture 创建），画面先进入
 * GL_TEXTURE_EXTERNAL_OES 外部纹理，再由片元着色器调色后绘制到屏幕，
 * 解码链路不变，适合车机等依赖硬解的场景。
 * 调色档位通过 {@link #setVideoEnhanceMode(int)} 切换。
 */
@SuppressLint("ViewConstructor")
public class GlEnhanceRenderView extends GLSurfaceView implements IRenderView, GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "GlEnhance";

    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTexCoord;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            "    vTexCoord = (uTexMatrix * aTexCoord).xy;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uTexture;\n" +
            "uniform float uSat;\n" +
            "uniform float uCon;\n" +
            "uniform float uBri;\n" +
            "uniform vec3 uRgb;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    vec4 raw = texture2D(uTexture, vTexCoord);\n" +
            "    vec3 color = raw.rgb * uRgb;\n" +
            "    color = (color - 0.5) * uCon + 0.5 + uBri;\n" +
            "    float l = dot(color, vec3(0.2126, 0.7152, 0.0722));\n" +
            "    gl_FragColor = vec4(clamp(mix(vec3(l), color, uSat), 0.0, 1.0), 1.0);\n" +
            "}\n";

    /** 全屏四边形（GL_TRIANGLE_STRIP，4 个顶点），NDC 坐标 */
    private static final FloatBuffer QUAD_POSITION = createFloatBuffer(new float[]{
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
    });

    /** 纹理坐标，实际变换由 SurfaceTexture 的 uTexMatrix 完成 */
    private static final FloatBuffer QUAD_TEX_COORD = createFloatBuffer(new float[]{
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
    });

    private final MeasureHelper mMeasureHelper = new MeasureHelper();

    @Nullable
    private volatile AbstractPlayer mMediaPlayer;

    /** 播放器渲染目标，由 GL 线程在 onSurfaceCreated 时创建 */
    private volatile Surface mPlayerSurface;
    private volatile SurfaceTexture mSurfaceTexture;

    /** 由 SurfaceTexture 回调线程置位，耗尽后由 GL 线程清除 */
    private volatile boolean mFrameAvailable;
    private volatile boolean mReleased;

    /** 视频尺寸，主线程写入、GL 线程读取，用于设置 SurfaceTexture 的缓冲区大小 */
    private volatile int mVideoWidth;
    private volatile int mVideoHeight;

    /** GL 线程内绘制计数，仅用于取证日志 */
    private int mFramesDrawn;
    private boolean mHasFrame;

    /** A single immutable snapshot prevents mixed parameters across UI and GL threads. */
    private volatile VideoEnhancePreset mPreset = VideoEnhancePreset.forMode(VideoEnhancePreset.ORIGINAL);

    /** 以下字段只在 GL 线程访问 */
    private int mProgram;
    private int mTextureId;
    private int aPosition;
    private int aTexCoord;
    private int uTexMatrix;
    private int uSat;
    private int uCon;
    private int uBri;
    private int uRgb;
    private final float[] mTexMatrixValue = new float[16];
    private int mViewportWidth;
    private int mViewportHeight;

    public GlEnhanceRenderView(Context context) {
        this(context, VideoEnhancePreset.ORIGINAL);
    }

    public GlEnhanceRenderView(Context context, int mode) {
        super(context);
        mPreset = VideoEnhancePreset.forMode(mode);
        setEGLContextClientVersion(2);
        // RGBA 配置，保证画面不透明；GL 内容全部自己绘制，无需 depth/stencil
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        // 切页时尽量保留 GL 上下文，避免每次切换都重建纹理
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void attachToPlayer(@NonNull AbstractPlayer player) {
        mMediaPlayer = player;
        bindSurfaceToPlayer();
    }

    /**
     * 把渲染 Surface 交给播放器。
     * 注意：EXO 会校验调用线程（IllegalStateException: Player is accessed on the wrong thread），
     * 而 SurfaceTexture 是在 GL 线程创建的，因此这里必须切回主线程再绑定。
     */
    private void bindSurfaceToPlayer() {
        final AbstractPlayer player = mMediaPlayer;
        final Surface surface = mPlayerSurface;
        if (player == null || surface == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (mReleased || mMediaPlayer != player || mPlayerSurface != surface || !surface.isValid()) return;
                    try {
                        player.setSurface(surface);
                        Log.i(TAG, "player surface bound (main thread)");
                    } catch (Throwable e) {
                        Log.i(TAG, "player.setSurface failed: " + e);
                    }
                }
            });
        } catch (Throwable e) {
            Log.i(TAG, "bindSurfaceToPlayer failed: " + e);
        }
    }

    public void setVideoSize(int videoWidth, int videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            mVideoWidth = videoWidth;
            mVideoHeight = videoHeight;
            mMeasureHelper.setVideoSize(videoWidth, videoHeight);
            applySurfaceTextureBufferSize();
            requestLayout();
            Log.i(TAG, "setVideoSize " + videoWidth + "x" + videoHeight);
        }
    }

    /** 自建 SurfaceTexture 必须显式设定缓冲区大小，否则解码器输出会落到默认(1x1)缓冲上 */
    private void applySurfaceTextureBufferSize() {
        SurfaceTexture surfaceTexture = mSurfaceTexture;
        int width = mVideoWidth;
        int height = mVideoHeight;
        if (surfaceTexture == null) return;
        if (width <= 0 || height <= 0) return;
        try {
            surfaceTexture.setDefaultBufferSize(width, height);
            Log.i(TAG, "surfaceTexture buffer size -> " + width + "x" + height);
        } catch (Throwable e) {
            Log.i(TAG, "setDefaultBufferSize failed: " + e);
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
        // 画面在 GL 线程的 framebuffer 里，截图必须在 GL 线程做 glReadPixels 回读
        // （还要处理线程同步与 Y 翻转），无法同步返回且阻塞主线程会卡死播放，
        // 因此这里返回 null，不提供截图能力。
        return null;
    }

    @Override
    public void release() {
        if (mReleased) {
            return;
        }
        mReleased = true;
        mMediaPlayer = null;
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    releaseGlResources();
                }
            });
        } catch (Throwable ignored) {
            // GL 线程可能已退出，忽略，保证 release 幂等且不抛异常
        }
    }

    public boolean isEnhanceMode() {
        return mPreset.id != VideoEnhancePreset.ORIGINAL;
    }

    /** Applies one immutable preset snapshot on the GL thread; mode 0 is the HDR identity path. */
    public void setVideoEnhanceMode(int mode) {
        mPreset = VideoEnhancePreset.forMode(mode);
        try {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    applyEnhanceUniforms();
                }
            });
        } catch (Throwable ignored) {
            // onSurfaceCreated applies the stored preset if the GL thread is not ready yet.
        }
        requestRender();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int[] measuredSize = mMeasureHelper.doMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(measuredSize[0], measuredSize[1]);
    }

    // ------------------------------ GLSurfaceView.Renderer ------------------------------

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            if (mReleased) {
                return;
            }
            // GL 上下文可能被系统回收后重建（onSurfaceCreated 再次回调），
            // 先释放上一轮的 Surface/SurfaceTexture，避免句柄泄漏和重复挂载
            releaseGlResources();
            if (createProgram()) {
                createSurfaceTexture();
                Log.i(TAG, "GL surface ready, view=" + getWidth() + "x" + getHeight()
                        + ", program=" + mProgram + ", texture=" + mTextureId);
            } else {
                Log.i(TAG, "GL program creation failed");
            }
        } catch (Throwable e) {
            // 车机环境下宁可黑屏也不能崩溃，保持 View 可用
            Log.i(TAG, "onSurfaceCreated error: " + e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        try {
            mViewportWidth = width;
            mViewportHeight = height;
            GLES20.glViewport(0, 0, width, height);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            int width = mViewportWidth > 0 ? mViewportWidth : getWidth();
            int height = mViewportHeight > 0 ? mViewportHeight : getHeight();
            GLES20.glViewport(0, 0, width, height);

            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (mProgram == 0 || mTextureId == 0) return;
            SurfaceTexture surfaceTexture = mSurfaceTexture;
            if (surfaceTexture == null) return;
            if (mFrameAvailable) {
                mFrameAvailable = false;
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(mTexMatrixValue);
                mHasFrame = true;
            }
            // A preset/resize can request a redraw while paused: retain the last decoded frame.
            if (!mHasFrame) return;

            GLES20.glUseProgram(mProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
            GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, mTexMatrixValue, 0);

            QUAD_POSITION.position(0);
            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, QUAD_POSITION);

            QUAD_TEX_COORD.position(0);
            GLES20.glEnableVertexAttribArray(aTexCoord);
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, QUAD_TEX_COORD);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            if (mFramesDrawn < 3 || mFramesDrawn % 100 == 0) {
                VideoEnhancePreset preset = mPreset;
                Log.i(TAG, "drawFrame #" + mFramesDrawn + " preset=" + preset.id + ":" + preset.name
                        + " sat=" + preset.saturation + " con=" + preset.contrast
                        + " bri=" + preset.brightness + " viewport=" + width + "x" + height);
            }
            mFramesDrawn++;

            GLES20.glDisableVertexAttribArray(aPosition);
            GLES20.glDisableVertexAttribArray(aTexCoord);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        } catch (Throwable e) {
            // 绘制异常不能影响后续帧
            Log.i(TAG, "onDrawFrame error: " + e);
        }
    }

    // ------------------------------ SurfaceTexture.OnFrameAvailableListener ------------------------------

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mFrameAvailable = true;
        try {
            if (!post(new Runnable() {
                @Override
                public void run() {
                    requestRender();
                }
            })) {
                requestRender();
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------ 内部实现 ------------------------------

    private void createSurfaceTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        SurfaceTexture surfaceTexture = new SurfaceTexture(mTextureId);
        try {
            // GL 线程没有 Looper：必须显式指定主线程 Handler，否则帧到达回调不会触发
            surfaceTexture.setOnFrameAvailableListener(this, new Handler(Looper.getMainLooper()));
            Log.i(TAG, "frame-available listener bound to main looper");
        } catch (Throwable e) {
            Log.i(TAG, "setOnFrameAvailableListener(main looper) failed: " + e);
            try {
                surfaceTexture.setOnFrameAvailableListener(this);
                Log.i(TAG, "frame-available listener bound to current thread");
            } catch (Throwable e2) {
                Log.i(TAG, "setOnFrameAvailableListener failed: " + e2);
            }
        }
        mFrameAvailable = false;
        mSurfaceTexture = surfaceTexture;
        applySurfaceTextureBufferSize();

        // 播放器渲染到这个 Surface，而不是屏幕 Surface
        mPlayerSurface = new Surface(surfaceTexture);
        // 切回主线程再交给播放器（EXO 校验调用线程）
        bindSurfaceToPlayer();
    }

    private boolean createProgram() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vertexShader == 0 || fragmentShader == 0) {
            return false;
        }
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return false;
        }
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        if (linkStatus[0] == 0) {
            Log.i(TAG, "program link failed: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return false;
        }

        mProgram = program;
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix");
        uSat = GLES20.glGetUniformLocation(program, "uSat");
        uCon = GLES20.glGetUniformLocation(program, "uCon");
        uBri = GLES20.glGetUniformLocation(program, "uBri");
        uRgb = GLES20.glGetUniformLocation(program, "uRgb");

        GLES20.glUseProgram(program);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0);
        applyEnhanceUniforms();
        GLES20.glUseProgram(0);
        return true;
    }

    private void applyEnhanceUniforms() {
        try {
            if (mProgram == 0) {
                return;
            }
            GLES20.glUseProgram(mProgram);
            VideoEnhancePreset preset = mPreset;
            GLES20.glUniform1f(uSat, preset.saturation);
            GLES20.glUniform1f(uCon, preset.contrast);
            GLES20.glUniform1f(uBri, preset.brightness);
            GLES20.glUniform3f(uRgb, preset.red, preset.green, preset.blue);
            Log.i(TAG, "preset applied id=" + preset.id + " name=" + preset.name
                    + " sat=" + preset.saturation + " con=" + preset.contrast
                    + " bri=" + preset.brightness + " rgb=" + preset.red + "," + preset.green + "," + preset.blue);
        } catch (Throwable ignored) {
        }
    }

    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            return 0;
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.i(TAG, "shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    /**
     * 释放 GL 相关资源，幂等，可在 GL 线程反复调用（重建 SurfaceTexture 前也会调用）。
     */
    private void releaseGlResources() {
        try {
            Surface surface = mPlayerSurface;
            mPlayerSurface = null;
            if (surface != null) {
                surface.release();
            }
        } catch (Throwable ignored) {
        }
        try {
            SurfaceTexture surfaceTexture = mSurfaceTexture;
            mSurfaceTexture = null;
            mFrameAvailable = false;
            mHasFrame = false;
            if (surfaceTexture != null) {
                surfaceTexture.setOnFrameAvailableListener(null);
                surfaceTexture.release();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (mTextureId != 0) {
                GLES20.glDeleteTextures(1, new int[]{mTextureId}, 0);
                mTextureId = 0;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (mProgram != 0) {
                GLES20.glDeleteProgram(mProgram);
                mProgram = 0;
            }
        } catch (Throwable ignored) {
        }
    }

    private static FloatBuffer createFloatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values);
        buffer.position(0);
        return buffer;
    }
}
