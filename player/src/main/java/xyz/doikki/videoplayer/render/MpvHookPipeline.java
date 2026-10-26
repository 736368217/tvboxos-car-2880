package xyz.doikki.videoplayer.render;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行 {@link MpvHookShaderCompiler} 转译出来的多 Pass 模型（方案 v2.0 §3.2）。
 *
 * <p>用途：超分 3 档「动漫神经网络」。把 {@code assets/shaders/acnet_f8b4_hdn.glsl}
 * （ACNetGLSL v3.2, MIT）这类单文件模型按 SAVE/BIND 链依次渲染，
 * 中间结果放在各 SAVE 名字对应的纹理里；最后一个 Pass 输出到调用方给的 FBO/屏幕。</p>
 *
 * <p>只支持 GPU 端纯卷积类模型；含 {@code //!WHEN 0} 之类的条件 Pass 一律按恒真处理，
 * 与本项目「只对 SDR 视频做超分、HDR 旁路」的前提一致。</p>
 */
public final class MpvHookPipeline {

    private static final String TAG = "MpvHook";
    private static final int MAX_TARGETS = 8;

    private final List<MpvHookShaderCompiler.PassSpec> passes;
    private final Map<String, Target> targets = new LinkedHashMap<>();
    private int finalFbo, finalTexture, finalW, finalH;
    private boolean released;
    private boolean loggedVram;
    private final FloatBuffer positions;
    private final FloatBuffer coordinates;

    private static final class Target {
        int fbo, texture, width, height;
    }

    private MpvHookPipeline(List<MpvHookShaderCompiler.PassSpec> passes) {
        this.passes = passes;
        this.positions = buffer(-1, -1, 1, -1, -1, 1, 1, 1);
        this.coordinates = buffer(0, 0, 1, 0, 0, 1, 1, 1);
    }

    /** 从 assets 读取模型；失败返回 null（调用方回退到上一档）。 */
    public static MpvHookPipeline load(Context context, String assetName) {
        if (context == null) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(assetName), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        } catch (Throwable error) {
            Log.w(TAG, "model not loaded: " + assetName, error);
            return null;
        }
        List<MpvHookShaderCompiler.PassSpec> parsed = MpvHookShaderCompiler.parse(sb.toString());
        int compiled = 0;
        for (MpvHookShaderCompiler.PassSpec spec : parsed) {
            spec.program = VideoShaders.createProgram(VideoShaders.OUTPUT_VERTEX, spec.fragmentShaderSource);
            if (spec.program == 0) {
                Log.e(TAG, "pass failed: " + spec.desc);
                for (MpvHookShaderCompiler.PassSpec done : parsed) {
                    if (done.program != 0) GLES20.glDeleteProgram(done.program);
                }
                return null;
            }
            compiled++;
        }
        Log.i(TAG, "model ready " + assetName + " passes=" + compiled);
        return compiled == 0 ? null : new MpvHookPipeline(parsed);
    }

    public int passCount() {
        return passes.size();
    }

    /**
     * 依次执行所有 Pass。{@code srcTexture} 作为 LUMA/MAIN 输入，结果写入内部最终目标，
     * 返回该纹理 id；失败返回 0。
     */
    public int draw(int srcTexture, int srcW, int srcH, int outW, int outH) {
        if (released || srcW <= 0 || srcH <= 0 || outW <= 0 || outH <= 0) return 0;
        if (!ensureFinal(outW, outH)) return 0;
        for (int i = 0; i < passes.size(); i++) {
            MpvHookShaderCompiler.PassSpec spec = passes.get(i);
            boolean last = i == passes.size() - 1;
            int dstW = Math.min(outW, srcW * spec.scaleX);
            int dstH = Math.min(outH, srcH * spec.scaleY);
            Target dst;
            if (last) {
                dst = new Target();
                dst.fbo = finalFbo;
                dst.texture = finalTexture;
                dst.width = outW;
                dst.height = outH;
            } else {
                dst = ensureTarget(spec.save, dstW, dstH);
                if (dst == null) return 0;
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, dst.fbo);
            GLES20.glViewport(0, 0, dst.width, dst.height);
            GLES20.glUseProgram(spec.program);
            int unit = 0;
            for (String bind : spec.binds) {
                Target src = isSourceBind(bind) ? null : targets.get(bind);
                int texture = src == null ? srcTexture : src.texture;
                int w = src == null ? srcW : src.width;
                int h = src == null ? srcH : src.height;
                String safe = bind.replaceAll("[^A-Za-z0-9_]", "_");
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
                GLES20.glUniform1i(GLES20.glGetUniformLocation(spec.program, "uTex_" + safe), unit);
                GLES20.glUniform2f(GLES20.glGetUniformLocation(spec.program, "uPt_" + safe),
                        1f / w, 1f / h);
                GLES20.glUniform2f(GLES20.glGetUniformLocation(spec.program, "uSize_" + safe), w, h);
                unit++;
            }
            drawQuad(spec.program);
            for (int u = 0; u < unit; u++) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + u);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (!loggedVram) {
            loggedVram = true;
            long bytes = (long) finalW * finalH * 4L;
            for (Target target : targets.values()) bytes += (long) target.width * target.height * 4L;
            Log.i(TAG, "vram_alloc anime_model passes=" + passes.size() + " targets=" + targets.size()
                    + " = " + (bytes / 1048576f) + " MB");
        }
        return finalTexture;
    }

    private boolean isSourceBind(String bind) {
        return "LUMA".equals(bind) || "MAIN".equals(bind) || "HOOKED".equals(bind)
                || "OUTPUT".equals(bind) || "PREKERNEL".equals(bind) || "POSTKERNEL".equals(bind);
    }

    private Target ensureTarget(String name, int width, int height) {
        if (name == null || name.isEmpty()) name = "TMP_" + targets.size();
        Target target = targets.get(name);
        if (target != null && target.width == width && target.height == height) return target;
        if (target == null) {
            if (targets.size() >= MAX_TARGETS) {
                Log.w(TAG, "too many SAVE targets");
                return null;
            }
            target = new Target();
            targets.put(name, target);
        } else {
            releaseTarget(target);
        }
        return allocate(target, width, height) ? target : null;
    }

    private boolean ensureFinal(int width, int height) {
        if (finalFbo != 0 && finalW == width && finalH == height) return true;
        if (finalFbo != 0) {
            int[] f = {finalFbo};
            int[] t = {finalTexture};
            GLES20.glDeleteFramebuffers(1, f, 0);
            GLES20.glDeleteTextures(1, t, 0);
            finalFbo = 0;
            finalTexture = 0;
        }
        int[] t = new int[1];
        int[] f = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glGenFramebuffers(1, f, 0);
        finalTexture = t[0];
        finalFbo = f[0];
        finalW = width;
        finalH = height;
        return allocateRaw(finalTexture, finalFbo, width, height);
    }

    private boolean allocate(Target target, int width, int height) {
        int[] t = new int[1];
        int[] f = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glGenFramebuffers(1, f, 0);
        target.texture = t[0];
        target.fbo = f[0];
        target.width = width;
        target.height = height;
        if (allocateRaw(target.texture, target.fbo, width, height)) return true;
        releaseTarget(target);
        return false;
    }

    private boolean allocateRaw(int texture, int fbo, int width, int height) {
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
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE || error != GLES20.GL_NO_ERROR) {
            Log.w(TAG, "target " + width + "x" + height + " status=" + status + " error=" + error);
            return false;
        }
        return true;
    }

    private void releaseTarget(Target target) {
        if (target.fbo != 0) {
            int[] f = {target.fbo};
            GLES20.glDeleteFramebuffers(1, f, 0);
            target.fbo = 0;
        }
        if (target.texture != 0) {
            int[] t = {target.texture};
            GLES20.glDeleteTextures(1, t, 0);
            target.texture = 0;
        }
    }

    public void release() {
        if (released) return;
        released = true;
        for (Target target : new ArrayList<>(targets.values())) releaseTarget(target);
        targets.clear();
        if (finalFbo != 0) {
            int[] f = {finalFbo};
            GLES20.glDeleteFramebuffers(1, f, 0);
            finalFbo = 0;
        }
        if (finalTexture != 0) {
            int[] t = {finalTexture};
            GLES20.glDeleteTextures(1, t, 0);
            finalTexture = 0;
        }
        for (MpvHookShaderCompiler.PassSpec spec : passes) {
            if (spec.program != 0) {
                GLES20.glDeleteProgram(spec.program);
                spec.program = 0;
            }
        }
    }

    private void drawQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int coordinate = GLES20.glGetAttribLocation(program, "aTexCoord");
        positions.position(0);
        coordinates.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glEnableVertexAttribArray(coordinate);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, positions);
        GLES20.glVertexAttribPointer(coordinate, 2, GLES20.GL_FLOAT, false, 0, coordinates);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(coordinate);
    }

    private static FloatBuffer buffer(float... values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }
}
