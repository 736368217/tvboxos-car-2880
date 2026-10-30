package com.github.tvbox.osc.util;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * 封面缓存的纯逻辑部分：地址归一化、缓存键、候选顺序、文件名校验。
 *
 * <p>不碰 Android，便于宿主机测试。真正的落盘/读取在
 * {@link com.github.tvbox.osc.cache.CoverStore} 里。</p>
 */
public final class CoverKey {

    private CoverKey() {
    }

    /**
     * 把带内联头信息的封面地址收敛成「稳定」的样子，作为缓存键。
     *
     * <p>站点常在地址后追加 {@code @Headers=...} / {@code @Referer=...}，这类后缀会变
     * （例如 token 刷新），直接用会命中不到已有缓存。只截已知的内联键 —— 不能见 {@code @}
     * 就砍：不少图床的地址本身就带 {@code @}（例如 {@code pic@200w.jpg}、{@code .jpg@!c750x375}），
     * 全砍会让不同图片算出同一个键、互相覆盖显示成别人的封面。</p>
     */
    public static String normalize(String pic) {
        if (pic == null) return "";
        String value = pic.trim();
        if (value.length() == 0) return "";
        String[] inlineKeys = {"@Headers=", "@Cookie=", "@User-Agent=", "@Referer="};
        int cut = -1;
        for (String inlineKey : inlineKeys) {
            int at = value.indexOf(inlineKey);
            if (at > 0 && (cut < 0 || at < cut)) cut = at;
        }
        if (cut > 0) value = value.substring(0, cut).trim();
        return value;
    }

    /** 缓存键：SHA-256(normalized) 前 32 个十六进制字符。空地址返回 null。 */
    public static String key(String pic) {
        return hash(CoverKey.normalize(pic));
    }

    /**
     * 内容键：按「片名 + 来源」算，和图片 URL 无关。
     *
     * <p>为什么需要：站点换图床、删图重发时 URL 一定变，纯 URL 键命不中旧缓存 ——
     * 用户看到的还是「已经看过却变成色块」。同一步剧的片名+来源是稳定的，
     * 所以历史/收藏在 URL 未命中时用这个键兜底。</p>
     */
    public static String contentKey(String name, String sourceKey) {
        String title = name == null ? "" : name.trim();
        String source = sourceKey == null ? "" : sourceKey.trim();
        if (title.length() == 0) return null;
        return hash(source + "\u0000" + title);
    }

    private static String hash(String value) {
        if (value == null || value.length() == 0) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(32);
            for (int i = 0; i < 16 && i < bytes.length; i++) {
                builder.append(String.format(Locale.US, "%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Exception error) {
            return null;
        }
    }

    /** 该缓存键对应的文件名（不含目录）。 */
    public static String fileName(String pic) {
        String key = key(pic);
        return key == null ? null : key + ".img";
    }

    /** 内容键对应的文件名。 */
    public static String contentFileName(String name, String sourceKey) {
        String key = contentKey(name, sourceKey);
        return key == null ? null : key + ".img";
    }

    /**
     * 候选加载顺序：**先用本地文件，再回退网络**。
     *
     * <p>历史/收藏这类「已经看过一次」的页面必须离线可用 —— 站点换图床、删图、
     * 或者车机没网时，也应该显示之前缓存下来的封面，而不是随机色块 + 片名首字。</p>
     */
    public static String[] candidates(String localFile, String remoteUrl) {
        boolean hasLocal = localFile != null && localFile.trim().length() > 0;
        boolean hasRemote = remoteUrl != null && remoteUrl.trim().length() > 0;
        if (hasLocal && hasRemote) return new String[]{localFile, remoteUrl};
        if (hasLocal) return new String[]{localFile};
        if (hasRemote) return new String[]{remoteUrl};
        return new String[0];
    }

    /**
     * 缓存文件名是否合法（读取时防止路径穿越 / 误删）。
     * 只接受 32 位小写十六进制 + {@code .img}。
     */
    public static boolean isValidFileName(String name) {
        if (name == null || name.length() != 36) return false;
        if (!name.endsWith(".img")) return false;
        for (int i = 0; i < 32; i++) {
            char c = name.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return false;
        }
        return true;
    }
}