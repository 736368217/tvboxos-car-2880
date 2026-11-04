package com.github.tvbox.osc.cache;

import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.CoverKey;
import com.github.tvbox.osc.util.DefaultConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 历史记录封面的「一次性补齐」：用户点进历史记录页时，对还没有封面的旧记录
 * 重新向站源拉一次详情/搜索，把 pic 写回数据库，并立刻落盘 CoverStore。
 *
 * <p>为什么必须做：r54 之前写入的历史压根没存 pic，r61 只能靠「片名+来源」内容键
 * 命中之前碰巧缓存过的图；没缓存过的就永远是色块。用户明确要求「点进历史记录时
 * 进行一次获取，如果能获取到也补上封面」。这里只补数据库里 pic 为空的记录，
 * 拿到的图片 URL 会持久化到历史里，之后所有历史页（包括首页「观看历史」推荐）
 * 都能直接显示。</p>
 */
public final class HistoryCoverRepair {

    public static final String TAG = "CoverCache";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "history-cover-repair");
        thread.setDaemon(true);
        return thread;
    });
    /** 单次进入页面最多补这么多条，避免老用户几百条历史一起打爆站源。 */
    private static final int MAX_PER_VISIT = 30;
    private static final Set<String> IN_FLIGHT = Collections.synchronizedSet(new HashSet<>());
    private static volatile long lastRunMs;

    private HistoryCoverRepair() {
    }

    /** 历史记录页进入时调用，成功后通过 EventBus 让列表刷新。返回 true 表示启动了补齐任务。 */
    public static boolean repairMissing(final android.content.Context context, final List<VodInfo> records) {
        if (context == null || records == null || records.isEmpty()) return false;
        // 同一个页面生命周期内只跑一轮：onResume / RefreshEvent 都会触发，别反复打站源。
        long now = System.currentTimeMillis();
        if (now - lastRunMs < 60_000L) return false;
        final android.content.Context app = context.getApplicationContext();
        final java.util.List<VodInfo> todo = new java.util.ArrayList<>();
        for (VodInfo info : records) {
            if (info == null || TextUtils.isEmpty(info.id) || TextUtils.isEmpty(info.sourceKey)) continue;
            if (!TextUtils.isEmpty(info.pic)) continue;
            String key = info.sourceKey + "\u0000" + info.id;
            if (IN_FLIGHT.add(key)) todo.add(cloneKey(info));
            if (todo.size() >= MAX_PER_VISIT) break;
        }
        if (todo.isEmpty()) return false;
        lastRunMs = now;
        Log.i(TAG, "history cover repair: start missing=" + todo.size());
        POOL.execute(() -> {
            int fixed = 0;
            for (VodInfo info : todo) {
                String key = info.sourceKey + "\u0000" + info.id;
                try {
                    String pic = fetchCover(info.sourceKey, info.id, info.name);
                    if (TextUtils.isEmpty(pic)) {
                        Log.i(TAG, "history cover repair: no result name=" + info.name
                                + " source=" + info.sourceKey);
                        continue;
                    }
                    VodRecordDao dao = com.github.tvbox.osc.data.AppDataManager.get().getVodRecordDao();
                    VodRecord record = dao.getVodRecord(info.sourceKey, info.id);
                    if (record == null) continue;
                    // 重新读一次记录再改 pic，避免覆盖期间用户看新剧产生的集数进度。
                    VodInfo latest = RoomDataManger.getVodInfo(info.sourceKey, info.id);
                    if (latest == null) continue;
                    if (!TextUtils.isEmpty(latest.pic)) continue;  // 别的路径已经补过了
                    latest.pic = pic;
                    String json = new com.google.gson.GsonBuilder()
                            .addSerializationExclusionStrategy(new com.google.gson.ExclusionStrategy() {
                                @Override public boolean shouldSkipField(com.google.gson.FieldAttributes f) {
                                    return f.getDeclaringClass() == VodInfo.class
                                            && ("seriesFlags".equals(f.getName()) || "seriesMap".equals(f.getName()));
                                }
                                @Override public boolean shouldSkipClass(Class<?> c) { return false; }
                            }).create().toJson(latest);
                    record.dataJson = json;
                    dao.insert(record);
                    String resolved = DefaultConfig.checkReplaceProxy(pic);
                    CoverStore.remember(app, resolved, latest.name, info.sourceKey);
                    fixed++;
                    Log.i(TAG, "history cover repair: fixed name=" + info.name
                            + " url=" + CoverKey.normalize(resolved).substring(0,
                                    Math.min(96, CoverKey.normalize(resolved).length())));
                } catch (Throwable error) {
                    Log.w(TAG, "history cover repair: failed name=" + info.name + " : " + error);
                } finally {
                    IN_FLIGHT.remove(key);
                }
            }
            Log.i(TAG, "history cover repair: done fixed=" + fixed + "/" + todo.size());
            if (fixed > 0) {
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.post(() -> org.greenrobot.eventbus.EventBus.getDefault()
                        .post(new com.github.tvbox.osc.event.RefreshEvent(
                                com.github.tvbox.osc.event.RefreshEvent.TYPE_HISTORY_REFRESH)));
            }
        });
        return true;
    }

    private static VodInfo cloneKey(VodInfo source) {
        VodInfo item = new VodInfo();
        item.id = source.id;
        item.sourceKey = source.sourceKey;
        item.name = source.name;
        return item;
    }

    /**
     * 用站源自身的分类接口取一张图：先按 id 取详情（最准），失败再按片名搜索。
     * 不走 OkGo 的回调链，用同步 HTTP + 短超时，避免和 ViewModel 的 LiveData 抢状态。
     */
    private static String fetchCover(String sourceKey, String id, String name) {
        SourceBean bean = ApiConfig.get().getSource(sourceKey);
        if (bean == null || TextUtils.isEmpty(bean.getApi())) return null;
        try {
            String raw = get(bean.getApi(), params("ac", bean.getType() == 0 ? "videolist" : "detail", "ids", id));
            String pic = firstPic(raw, bean.getType() == 0);
            if (!TextUtils.isEmpty(pic)) return pic;
        } catch (Throwable ignored) {
        }
        if (!TextUtils.isEmpty(name)) {
            try {
                String raw = get(bean.getApi(), params("wd", name,
                        "ac", bean.getType() == 1 ? "detail" : null, "quick", "false"));
                String pic = firstPic(raw, bean.getType() == 0);
                if (!TextUtils.isEmpty(pic)) return pic;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String params(String... pairs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] == null) continue;
            if (out.length() > 0) out.append('&');
            try {
                out.append(URLEncoder.encode(pairs[i], "UTF-8")).append('=')
                        .append(URLEncoder.encode(pairs[i + 1], "UTF-8"));
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        return out.toString();
    }

    private static String get(String api, String query) throws Exception {
        String url = api + (api.contains("?") ? "&" : "?") + query;
        okhttp3.OkHttpClient client = com.github.tvbox.osc.util.PlainHttp.systemDns();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url)
                .header("User-Agent", "TVBox-Car/HistoryCoverRepair").build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return response.body().string();
        }
    }

    /** 从 XML / JSON 站源响应里取第一张非空封面；兼容 {@code vod_pic}、{@code pic}、{@code img}。 */
    static String firstPic(String raw, boolean xml) {
        if (TextUtils.isEmpty(raw)) return null;
        String text = raw.trim();
        if (xml || text.charAt(0) == '<') {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("<pic>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</pic>|<pic>\\s*([^<]+?)\\s*</pic>",
                            java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(text);
            while (matcher.find()) {
                String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (!TextUtils.isEmpty(value) && value.contains("://")) return value.trim();
            }
            return null;
        }
        try {
            Object parsed = parseJsonLoose(text);
            return findJsonPic(parsed);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object parseJsonLoose(String text) throws Exception {
        JSONObject root = new JSONObject(text);
        if (root.has("list")) return root.get("list");
        if (root.has("data")) return root.get("data");
        return root;
    }

    private static String findJsonPic(Object node) {
        try {
            if (node instanceof org.json.JSONArray) {
                org.json.JSONArray array = (org.json.JSONArray) node;
                for (int i = 0; i < Math.min(array.length(), 30); i++) {
                    String pic = findJsonPic(array.get(i));
                    if (!TextUtils.isEmpty(pic)) return pic;
                }
                return null;
            }
            if (!(node instanceof JSONObject)) return null;
            JSONObject object = (JSONObject) node;
            for (String key : new String[]{"vod_pic", "pic", "img", "cover", "vod_pic_thumb"}) {
                String value = object.optString(key, "");
                if (!TextUtils.isEmpty(value) && value.contains("://")) return value.trim();
            }
            org.json.JSONArray nested = object.optJSONArray("list");
            if (nested != null) return findJsonPic(nested);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
