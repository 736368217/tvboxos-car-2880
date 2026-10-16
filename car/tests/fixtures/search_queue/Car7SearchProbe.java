package com.github.catvod.spider;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Local Android-only search fixture. It performs no network I/O and never plays media. */
public final class Car7SearchProbe extends Spider {
    private static final String TAG = "Car7SearchFixture";
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final AtomicInteger PEAK = new AtomicInteger();
    private long delayMillis;
    private boolean ignoreInterrupt;
    private String mode = "result";
    private int source;

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
        try {
            JSONObject options = new JSONObject(extend);
            delayMillis = options.optLong("delay_ms", 0);
            ignoreInterrupt = options.optBoolean("ignore_interrupt", false);
            mode = options.optString("mode", "result");
            source = options.optInt("source", 0);
        } catch (Exception error) {
            throw new IllegalArgumentException("fixture_config");
        }
    }

    @Override
    public String homeContent(boolean filter) {
        return "{\"class\":[{\"type_id\":\"probe\",\"type_name\":\"搜索队列验收\"}],\"filters\":{}}";
    }

    @Override
    public String homeVideoContent() {
        return result("队列验收", "home");
    }

    @Override
    public String searchContent(String keyword, boolean quick) {
        int active = ACTIVE.incrementAndGet();
        for (int old = PEAK.get(); active > old && !PEAK.compareAndSet(old, active); old = PEAK.get()) {}
        Log.i(TAG, "event=start site=" + source + " active=" + active + " peak=" + PEAK.get());
        boolean wasInterrupted = false;
        try {
            long deadline = SystemClock.elapsedRealtime() + delayMillis;
            while (SystemClock.elapsedRealtime() < deadline) {
                try {
                    long remaining = deadline - SystemClock.elapsedRealtime();
                    if (remaining <= 0) break;
                    Thread.sleep(Math.min(100L, remaining));
                } catch (InterruptedException interrupted) {
                    wasInterrupted = true;
                    Log.i(TAG, "event=interrupt site=" + source);
                    if (!ignoreInterrupt) {
                        Thread.currentThread().interrupt();
                        return "";
                    }
                }
            }
            if ("empty".equals(mode)) return "";
            if ("invalid".equals(mode)) return "invalid_fixture_result";
            Log.i(TAG, "event=return site=" + source);
            return result(keyword, "search");
        } finally {
            if (wasInterrupted) Thread.currentThread().interrupt();
            Log.i(TAG, "event=exit site=" + source + " active=" + ACTIVE.decrementAndGet());
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        return result("队列验收", "detail");
    }

    private String result(String keyword, String stage) {
        try {
            JSONObject video = new JSONObject();
            video.put("vod_id", "car7-search-" + source);
            video.put("vod_name", keyword);
            video.put("vod_remarks", "本地队列 fixture " + stage + " " + source);
            video.put("vod_pic", "");
            return new JSONObject().put("list", new JSONArray().put(video)).toString();
        } catch (Exception error) {
            throw new IllegalStateException("fixture_result");
        }
    }
}
