package com.github.tvbox.osc.cache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * r74：封面落盘的「可解码性」守门人。
 *
 * <p>历史列表走 {@code BitmapFactory.decodeFile}（绕开 Glide/OkHttp/DNS 是 r68 刻意的
 * 设计）。但现场日志显示：图床返回的扩展/动画 WebP、AVIF、甚至 HTTP 200 的 HTML 错误页
 * 被原样存成 {@code .img} 后，某些 skia 会返回
 * {@code Failed to create image decoder with message 'unimplemented'}；Glide 能显示，
 * 本地路径永远失败，于是每次绑定都「本地失败 → 回退网络 → remember 认为已缓存」死循环。</p>
 *
 * <p>对策：下载完成先在后台验证 BitmapFactory 能否解码；不行就用
 * {@link android.graphics.ImageDecoder}（API 28+，车机 32 / MuMu 35 都覆盖）取首帧并
 * 压成通用 JPEG。老版本遗留的坏文件在 App 启动时后台自愈一次。API &lt; 28 无法转码时
 * 直接拒收，交给 Glide 网络显示，不再写入永远解不开的缓存。</p>
 *
 * <p>「有风险文件头、但本机 BitmapFactory 实测能解」的少数情况用同名 {@code .ok} 标记
 * 保留，避免为了规避旧 skia 而把本来正常的封面误删。</p>
 */
@SuppressWarnings("NewApi")
public final class CoverImageIo {

    static final String TAG = CoverStore.TAG;

    public static final int HEAL_ABSENT = 0;
    public static final int HEAL_OK = 1;
    public static final int HEAL_NORMALIZED = 2;
    public static final int HEAL_REMOVED = 3;

    private CoverImageIo() {
    }

    /** 只读头 32 字节的廉价判断：明显不可解码的格式/错误页，主线程也可调用。 */
    public static boolean hasRiskyHeader(File file) {
        return CoverFormat.bitmapFactoryRisky(readHead(file));
    }

    /** 用与 {@link com.github.tvbox.osc.cache.CoverBinder} 相同的采样参数做一次真实解码。 */
    public static boolean isDecodable(File file) {
        Bitmap bitmap = decodeSampled(file);
        if (bitmap == null) return false;
        try {
            if (bitmap.isMutable()) bitmap.recycle();
        } catch (Throwable ignored) {
        }
        return true;
    }

    /**
     * 下载落盘前调用：通用格式且可解就保留；风险格式尽量转成 JPEG；
     * 本机能解但转码失败的仍允许保留（调用方须写 {@code .ok} 标记）。失败让调用方拒收。
     * 必须在后台线程调用（会真实解码位图）。
     */
    public static boolean ensureDecodable(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return false;
        boolean risky = hasRiskyHeader(file);
        boolean decodable = isDecodable(file);
        if (!risky && decodable) return true;
        // 普通 WebP 头也可能因 skia 版本缺解码器而 decodeFile 失败；只要没解出来就转一次。
        if ((risky || !decodable) && normalizeToJpeg(file)
                && !hasRiskyHeader(file) && isDecodable(file)) return true;
        return isDecodable(file);
    }

    /**
     * 老版本遗留坏文件的自愈：可解就保留，能转码就转，都不行就删除。
     * 必须在后台线程调用。
     */
    public static int heal(File file) {
        if (file == null) return HEAL_ABSENT;
        if (!file.isFile() || file.length() <= 0) {
            deleteQuietly(file);
            deleteValidationMarker(file);
            deleteQuietly(new File(file.getAbsolutePath() + ".part"));
            return HEAL_ABSENT;
        }
        boolean risky = hasRiskyHeader(file);
        boolean decodable = isDecodable(file);
        if (!risky && decodable) return HEAL_OK;
        if (risky && isValidated(file) && decodable) return HEAL_OK;
        if ((risky || !decodable) && normalizeToJpeg(file)
                && !hasRiskyHeader(file) && isDecodable(file)) {
            deleteValidationMarker(file);
            Log.i(TAG, "cover: normalized " + file.getName() + " to jpeg");
            return HEAL_NORMALIZED;
        }
        if (isDecodable(file)) {
            markValidated(file);
            return HEAL_OK;
        }
        Log.w(TAG, "cover: removed undecodable " + file.getName());
        deleteQuietly(file);
        deleteValidationMarker(file);
        deleteQuietly(new File(file.getAbsolutePath() + ".part"));
        return HEAL_REMOVED;
    }

    /** 风险头但实测可解码的标记，随文件复制/删除一起维护。 */
    private static File marker(File file) {
        return file == null ? null : new File(file.getAbsolutePath() + ".ok");
    }

    public static boolean isValidated(File file) {
        File mark = marker(file);
        return mark != null && mark.isFile();
    }

    public static void markValidated(File file) {
        File mark = marker(file);
        if (mark == null) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            mark.createNewFile();
        } catch (Throwable ignored) {
        }
    }

    public static void deleteValidationMarker(File file) {
        deleteQuietly(marker(file));
    }

    /** 调用前已由 {@link Build.VERSION#SDK_INT} 守门，类级 {@code NewApi} 抑制 lint。 */
    private static boolean normalizeToJpeg(File file) {
        if (Build.VERSION.SDK_INT < 28) return false;
        File norm = new File(file.getAbsolutePath() + ".norm");
        deleteQuietly(norm);
        Bitmap bitmap = null;
        try {
            android.graphics.ImageDecoder.Source source =
                    android.graphics.ImageDecoder.createSource(file);
            bitmap = android.graphics.ImageDecoder.decodeBitmap(source,
                    new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
                        @Override
                        public void onHeaderDecoded(android.graphics.ImageDecoder decoder,
                                                    android.graphics.ImageDecoder.ImageInfo info,
                                                    android.graphics.ImageDecoder.Source src) {
                            // 必须软件分配：硬件 Bitmap 不能读像素，compress 会失败。
                            decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                            int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                            int sample = 1;
                            while (max / sample > 1024 && sample < 16) sample *= 2;
                            decoder.setTargetSampleSize(sample);
                        }
                    });
            if (bitmap == null) return false;
            boolean written;
            try (FileOutputStream out = new FileOutputStream(norm)) {
                written = bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out);
            }
            if (!written || norm.length() <= 0) return false;
            // Linux/Android 的 renameTo 会覆盖已存在文件；个别 ROM 不覆盖时退回字节复制。
            if (norm.renameTo(file)) return true;
            try (InputStream in = new FileInputStream(norm);
                 FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            }
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "cover: normalize failed " + file.getName() + " : " + error);
            return false;
        } finally {
            // ImageDecoder 产出的 bitmap 可能不可变，recycle() 在部分实现上会抛异常；
            // 只回收可变位图，其余交给 GC，避免 finally 把成功转码变成失败。
            if (bitmap != null && bitmap.isMutable()) bitmap.recycle();
            deleteQuietly(norm);
        }
    }

    private static Bitmap decodeSampled(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = sampleSize(file.length());
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable error) {
            return null;
        }
    }

    static int sampleSize(long bytes) {
        if (bytes > 1_500_000L) return 4;
        if (bytes > 600_000L) return 2;
        return 1;
    }

    private static byte[] readHead(File file) {
        byte[] head = new byte[32];
        if (file == null || !file.isFile()) return new byte[0];
        try (InputStream in = new FileInputStream(file)) {
            int read = in.read(head);
            if (read <= 0) return new byte[0];
            return read == head.length ? head : java.util.Arrays.copyOf(head, read);
        } catch (IOException error) {
            return new byte[0];
        }
    }

    static void deleteQuietly(File file) {
        if (file == null) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Throwable ignored) {
        }
    }
}
