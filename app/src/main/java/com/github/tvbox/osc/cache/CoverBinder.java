package com.github.tvbox.osc.cache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.github.tvbox.osc.util.CoverKey;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.ImgUtil;

import java.io.File;

/**
 * 历史/收藏/首页「观看历史」封面的**唯一**绑定入口。
 *
 * <p>r68 之前这些页面各写一份 Glide 逻辑，问题是：网络请求失败时
 * {@code ImgUtil.getListener().onLoadFailed} 会无条件 {@code setImageDrawable(fallback)}，
 * 可能把 CoverStore 已经显示好的本地真图重新盖成随机色块；而首页观看历史压根没读
 * CoverStore。现在统一为：</p>
 *
 * <ol>
 *   <li>bind 前 {@code Glide.with(view).clear(view)}，让旧请求的失败回调不再落到新条目；</li>
 *   <li>本地缓存（内容键优先、URL 键次之）命中 → **直接 decodeFile + setImageBitmap**，
 *       彻底绕开 Glide / OkHttp / DNS，这是"一定显示"的路径；</li>
 *   <li>没命中才走 Glide 网络，同时发起 CoverStore 落盘；落盘成功用同样的 decodeFile
 *       立刻换成真图，并用 token 防止 RecyclerView 复用错位；</li>
 *   <li>网络失败只在该条目仍绑定同一张图时显示色块。</li>
 * </ol>
 */
public final class CoverBinder {

    public static final String TAG = "CoverCache";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CoverBinder() {
    }

    /**
     * 修正订阅返回的坏封面地址。
     *
     * <p>「爱动漫」订阅的详情接口把 vod_pic 返回成相对路径，旧版 JS 统一补到 API 域名
     * m3u8.girigirilove.com，实测 404；真图在 jpg.girigirilove.com。列表页因为返回的是
     * 完整 jpg URL 所以一直有图，但写进历史的详情 pic 是坏地址 —— 这解释了「软件没变、
     * 另一客户端最近的历史也全没封面」。这里兼容已经写进数据库的旧记录，订阅侧同步修 JS。</p>
     */
    public static String repairUrl(String pic) {
        if (TextUtils.isEmpty(pic)) return "";
        String value = pic.trim();
        String clean = CoverKey.normalize(value);
        if (clean.matches("(?i)^https?://(?:m3u8\\.)?girigirilove\\.com/(?:upload/vod|vod)/.+$")) {
            String path = clean.replaceFirst("(?i)^https?://(?:m3u8\\.)?girigirilove\\.com/", "");
            String suffix = value.substring(clean.length());
            return "https://jpg.girigirilove.com/" + path + suffix;
        }
        return value;
    }

    /** 绑定到 item_grid 风格的卡片。width/height 为像素，<=0 时用 ImgUtil 默认值。 */
    public static void bind(ImageView view, String rawPic, String name, String sourceKey,
                            int widthPx, int heightPx) {
        if (view == null) return;
        final String token = CoverKey.normalize(rawPic == null ? "" : rawPic)
                + "\u0000" + (name == null ? "" : name) + "\u0000" + (sourceKey == null ? "" : sourceKey);
        view.setTag(com.github.tvbox.osc.R.id.ivThumb, token);
        try {
            Glide.with(view).clear(view);
        } catch (Throwable ignored) {
        }

        final String pic = repairUrl(DefaultConfig.checkReplaceProxy(
                rawPic == null ? "" : rawPic));
        final String contentPath = CoverStore.findLocalPathByContent(
                view.getContext(), name, sourceKey);
        final String localPath = TextUtils.isEmpty(contentPath)
                ? CoverStore.findLocalPath(view.getContext(), pic, name, sourceKey) : contentPath;

        if (!TextUtils.isEmpty(localPath) && showLocal(view, localPath, token, name)) {
            Log.i(TAG, "cover: local bitmap hit name=" + name + " path=" + localPath);
            // 本地已显示：仍然补一次 URL 键，站点换图床后下次更快；不阻塞 UI。
            if (!TextUtils.isEmpty(pic) && !TextUtils.isEmpty(contentPath)) {
                CoverStore.reindex(view.getContext(), pic, contentPath);
            }
            return;
        }

        if (TextUtils.isEmpty(pic)) {
            Log.i(TAG, "cover: empty pic and no local name=" + name);
            view.setImageDrawable(ImgUtil.createTextDrawable(name));

            return;
        }

        Log.i(TAG, "cover: network fallback name=" + name + " url="
                + CoverKey.normalize(pic).substring(0, Math.min(96, CoverKey.normalize(pic).length())));
        final int w = widthPx;
        final int h = heightPx;
        ImgUtil.load(pic, view, 10, w, h, name);
        CoverStore.remember(view.getContext(), pic, name, sourceKey, () -> {
            if (!token.equals(view.getTag(com.github.tvbox.osc.R.id.ivThumb))) return;
            String ready = CoverStore.findLocalPath(view.getContext(), pic, name, sourceKey);
            if (TextUtils.isEmpty(ready)) return;
            if (showLocal(view, ready, token, name)) {
                Log.i(TAG, "cover: resolved from disk name=" + name);
            }
        });
    }

    /** 直接解码本地文件并显示；文件损坏时返回 false，交给调用方走网络。 */
    private static boolean showLocal(ImageView view, String path, String token, String name) {
        if (!token.equals(view.getTag(com.github.tvbox.osc.R.id.ivThumb))) return false;
        try {
            File file = new File(path);
            if (!file.isFile() || file.length() <= 0) return false;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = sampleSize(file.length());
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            if (bitmap == null) {
                Log.w(TAG, "cover: decode failed path=" + path);
                return false;
            }
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(bitmap);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "cover: local load error " + error);
            return false;
        }
    }

    private static int sampleSize(long bytes) {
        // 封面最大 2MB；>600KB 降采样，避免历史列表一次解几十张大图 OOM。
        if (bytes > 1_500_000L) return 4;
        if (bytes > 600_000L) return 2;
        return 1;
    }
}
