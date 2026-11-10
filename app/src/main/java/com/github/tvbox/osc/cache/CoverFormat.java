package com.github.tvbox.osc.cache;

import java.nio.charset.StandardCharsets;

/**
 * r74：封面文件头嗅探（纯 Java、不依赖 Android，可在宿主机单测）。
 *
 * <p>现场日志里同一批 {@code .img} 反复报 {@code cover: decode failed} 132 次，紧跟
 * {@code skia --- Failed to create image decoder with message 'unimplemented'}。原因是
 * CoverStore 把网络原始字节原样落盘：动画/扩展 WebP、AVIF、以及图床返回的 HTML 错误页，
 * BitmapFactory 在某些 skia 版本上根本建不出解码器，而 Glide（API 28+ 走 ImageDecoder）
 * 能显示 —— 于是「本地永远失败、每次都回退网络、remember 又认为已缓存」形成死循环。</p>
 *
 * <p>这里只做头 32 字节分类，用来决定下载完成后是否需要转码；不做解码、不分配位图。</p>
 */
public final class CoverFormat {

    public enum Kind {
        JPEG, PNG, GIF,
        WEBP_SIMPLE, WEBP_LOSSLESS, WEBP_EXTENDED,
        AVIF,
        /** 无法识别（含 HTML/JSON 错误页、BMP 等）；保守地当成需要转码/拒收。 */
        UNKNOWN,
        TOO_SHORT
    }

    private CoverFormat() {
    }

    public static Kind of(byte[] b) {
        if (b == null || b.length < 12) return Kind.TOO_SHORT;

        if (u8(b, 0) == 0xFF && u8(b, 1) == 0xD8 && u8(b, 2) == 0xFF) return Kind.JPEG;

        if (u8(b, 0) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && u8(b, 4) == 0x0D && u8(b, 5) == 0x0A
                && u8(b, 6) == 0x1A && u8(b, 7) == 0x0A) return Kind.PNG;

        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F'
                && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a') return Kind.GIF;

        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            // RIFF(4) + size(4) + WEBP(4) + chunk fourcc(4)
            if (b.length < 16) return Kind.WEBP_EXTENDED;  // 头被截断，保守处理
            String fourcc = ascii(b, 12, 4);
            if ("VP8 ".equals(fourcc)) return Kind.WEBP_SIMPLE;
            if ("VP8L".equals(fourcc)) return Kind.WEBP_LOSSLESS;
            // VP8X = 扩展 WebP（动画 / 带 alpha / 元数据），旧 skia 常报 unimplemented。
            return Kind.WEBP_EXTENDED;
        }

        if (ascii(b, 4, 4).equals("ftyp")
                && (ascii(b, 0, 32).contains("avif") || ascii(b, 0, 32).contains("avis"))) {
            return Kind.AVIF;
        }

        return Kind.UNKNOWN;
    }

    /**
     * 这些格式不能保证被 {@code BitmapFactory.decodeFile} 解出，落盘前必须转成通用 JPEG。
     * UNKNOWN/TOO_SHORT 也计入：图床返回 HTTP 200 的 HTML 错误页不该被当成封面缓存。
     */
    public static boolean bitmapFactoryRisky(Kind kind) {
        return kind == Kind.WEBP_EXTENDED || kind == Kind.AVIF
                || kind == Kind.UNKNOWN || kind == Kind.TOO_SHORT;
    }

    public static boolean bitmapFactoryRisky(byte[] head) {
        return bitmapFactoryRisky(of(head));
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static String ascii(byte[] b, int off, int len) {
        if (off >= b.length) return "";
        int n = Math.min(len, b.length - off);
        if (n <= 0) return "";
        return new String(b, off, n, StandardCharsets.US_ASCII);
    }
}
