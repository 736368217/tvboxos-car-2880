package com.github.tvbox.osc.car7.test;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class Car7Acceptance extends Instrumentation {
    private final JSONArray events = new JSONArray();
    private int passed, failed;
    private File out;
    private Activity home, advanced;
    private Bundle arguments;
    private static JSONObject mapping;
    private interface Work<T> { T run() throws Exception; }
    private interface Job { void run() throws Exception; }

    @Override public void onCreate(Bundle args) { arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            try (java.io.InputStream input = getContext().getAssets().open("mapping.json")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] b = new byte[65536]; int n;
                while ((n = input.read(b)) > 0) bytes.write(b, 0, n);
                mapping = new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
            }
            String tag = arguments == null ? "ui" : arguments.getString("tag", "ui");
            out = new File(getTargetContext().getFilesDir(), "car7-native-evidence/" + tag);
            if (!out.mkdirs() && !out.isDirectory()) throw new IllegalStateException("evidence directory");
            JSONObject identity = new JSONObject();
            identity.put("sourceApkSha256", apkHash());
            identity.put("pid", Process.myPid());
            identity.put("startedAt", System.currentTimeMillis());
            identity.put("tag", tag);
            identity.put("versionName", getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0).versionName);
            write("identity.json", identity.toString(2));
            if (arguments != null && "python".equals(arguments.getString("mode"))) {
                section("python", this::pythonChecks);
            } else {
                if (arguments != null && arguments.getString("api") != null) {
                    Class<?> hawk = type("com.orhanobut.hawk.Hawk");
                    main(() -> { });
                    if (!Boolean.TRUE.equals(call(hawk, "isBuilt"))) throw new IllegalStateException("Application preferences not initialized");
                    call(hawk, "put", mapping.getJSONObject("constants").getString("API_URL"), arguments.getString("api"));
                    call(hawk, "put", mapping.getJSONObject("constants").getString("HOME_API"), arguments.getString("homeSite", "py_maple"));
                    if ("pages".equals(arguments.getString("mode")) || "ux3k".equals(arguments.getString("mode"))) putPref("HOME_REC", 1);
                }
                Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                home = startActivitySync(launch);
                pause(15000);
                if (arguments != null && "subscription".equals(arguments.getString("mode"))) {
                    section("subscription", this::subscriptionChecks);
                } else if (arguments != null && "playback".equals(arguments.getString("mode"))) {
                    section("playback", this::playbackChecks);
                } else if (arguments != null && "gestures".equals(arguments.getString("mode"))) {
                    section("gestures", this::gestureChecks);
                } else if (arguments != null && "pages".equals(arguments.getString("mode"))) {
                    section("pages", this::pagesChecks);
                } else if (arguments != null && "ux3k".equals(arguments.getString("mode"))) {
                    section("ux3k", this::uxChecks);
                } else if (arguments != null && "states".equals(arguments.getString("mode"))) {
                    section("states", this::stateChecks);
                } else if (arguments != null && "searchdiag".equals(arguments.getString("mode"))) {
                    section("searchdiag", this::searchDiagnosticChecks);
                } else if (arguments != null && "journey".equals(arguments.getString("mode"))) {
                    section("journey", this::journeyChecks);
                } else if (arguments != null && "navigation".equals(arguments.getString("mode"))) {
                    section("back", this::backChecks); section("dialogs", this::dialogChecks);
                } else if (arguments != null && "quickjs".equals(arguments.getString("mode"))) {
                    section("quickjs", this::quickJsChecks);
                } else if (arguments != null && "searchqueue".equals(arguments.getString("mode"))) {
                    section("searchqueue", this::searchQueueChecks);
                } else {
                    section("home", this::homeChecks);
                    section("back", this::backChecks);
                    section("dialogs", this::dialogChecks);
                }
            }
        } catch (Throwable error) {
            record(false, "harness", Log.getStackTraceString(error));
        } finally {
            try {
                JSONObject summary = new JSONObject();
                summary.put("passed", passed); summary.put("failed", failed); summary.put("events", events);
                if (out != null) write("result.json", summary.toString(2));
                result.putInt("passed", passed); result.putInt("failed", failed);
                result.putString("evidence", out == null ? "not-created" : out.getAbsolutePath());
                result.putString("summary", "CAR7_NATIVE passed=" + passed + " failed=" + failed);
            } catch (Exception error) { result.putString("writeError", error.toString()); }
            finish(failed == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }
    }
    private <T> T bounded(Work<T> work, int seconds) throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try { return executor.submit(() -> work.run()).get(seconds, java.util.concurrent.TimeUnit.SECONDS); }
        finally { executor.shutdownNow(); }
    }
    private static final class LocalHttp implements AutoCloseable {
        final java.net.ServerSocket server;
        final java.util.List<Thread> workers = new java.util.concurrent.CopyOnWriteArrayList<>();
        final Thread accept;
        volatile boolean closed;
        LocalHttp() throws Exception {
            server = new java.net.ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"));
            accept = new Thread(() -> {
                while (!closed) try {
                    final java.net.Socket socket = server.accept();
                    Thread worker = new Thread(() -> {
                        try (java.net.Socket client = socket) {
                            client.setSoTimeout(3000);
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream(), "UTF-8"));
                            String line = reader.readLine();
                            if (line != null && line.contains("/slow")) Thread.sleep(2500);
                            client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".getBytes("UTF-8"));
                        } catch (Exception ignored) { }
                    }, "Car7LocalHttp");
                    workers.add(worker); worker.start();
                } catch (java.io.IOException error) { if (!closed) Log.e("Car7Native", "local HTTP accept failed"); }
            }, "Car7LocalAccept");
            accept.start();
        }
        String base() { return "http://127.0.0.1:" + server.getLocalPort(); }
        @Override public void close() throws Exception {
            closed = true; server.close(); accept.join(1000);
            for (Thread worker : workers) { worker.interrupt(); worker.join(1000); }
        }
    }
    private boolean hasCause(Throwable error, Class<?> kind) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) if (kind.isInstance(cause)) return true;
        return false;
    }
    private void quickJsChecks() throws Exception {
        try (LocalHttp server = new LocalHttp()) {
            Object loader = type("com.github.catvod.crawler.JsLoader").getConstructor().newInstance();
            String ext = new JSONObject().put("base", server.base()).toString();
            Object spider = bounded(() -> call(loader, "getSpider", "car7-async-local", "http://127.0.0.1:9978/file/TVBoxCar5/quickjs_runtime.js", ext, ""), 20);
            try {
                for (String[] sample : new String[][]{{"sync","SYNC_OK"},{"promise","PROMISE_OK"},{"timer","TIMER_OK"},{"req","REQ_OK"}}) {
                    long start = SystemClock.elapsedRealtime();
                    String value = bounded(() -> String.valueOf(call(spider, "searchContent", sample[0], false)), 8);
                    record(sample[1].equals(value), "QuickJS " + sample[0], "elapsed_ms=" + (SystemClock.elapsedRealtime() - start));
                }
                String object = bounded(() -> String.valueOf(call(spider, "searchContent", "object", false)), 5);
                check("QuickJS object snapshot crosses thread safely", "OBJECT_OK".equals(new JSONObject(object).optString("value")));
                java.util.HashMap<String,String> markers = new java.util.HashMap<>(); markers.put("marker", "BRIDGE");
                String category = bounded(() -> String.valueOf(call(spider, "categoryContent", "local", "1", true, markers)), 5);
                check("QuickJS category arguments created on owner thread", category.contains("CATEGORY_BRIDGE"));
                for (String mode : new String[]{"reject", "throw", "req-timeout"}) {
                    long start = SystemClock.elapsedRealtime(); boolean failedExpected = false;
                    try { bounded(() -> String.valueOf(call(spider, "searchContent", mode, false)), 5); }
                    catch (Exception error) { failedExpected = !hasCause(error, java.util.concurrent.TimeoutException.class); }
                    record(failedExpected, "QuickJS " + mode + " propagates failure", "elapsed_ms=" + (SystemClock.elapsedRealtime() - start));
                }
                long start = SystemClock.elapsedRealtime(); boolean timedOut = false;
                try { bounded(() -> String.valueOf(call(spider, "searchContent", "timeout", false)), 34); }
                catch (Exception error) { timedOut = hasCause(error, java.util.concurrent.TimeoutException.class); }
                long elapsed = SystemClock.elapsedRealtime() - start;
                record(timedOut && elapsed >= 29000 && elapsed < 33000, "QuickJS Promise has bounded wait", "elapsed_ms=" + elapsed);
                String recovered = bounded(() -> String.valueOf(call(spider, "searchContent", "recover", false)), 5);
                check("QuickJS recovers after timeout and late resolve", "RECOVER_OK".equals(recovered));
                SystemClock.sleep(1200);
                JSONObject event = new JSONObject(String.valueOf(call(spider, "searchContent", "events", false)));
                check("QuickJS timeout cancels pending timer", event.optInt("lateTimers") == 0);
                java.util.concurrent.ExecutorService outer = java.util.concurrent.Executors.newSingleThreadExecutor();
                java.util.concurrent.CountDownLatch exit = new java.util.concurrent.CountDownLatch(1);
                try {
                    java.util.concurrent.Future<?> pending = outer.submit(() -> {
                        try { call(spider, "searchContent", "pending-network", false); }
                        catch (Exception ignored) { } finally { exit.countDown(); }
                    });
                    SystemClock.sleep(150); pending.cancel(true);
                    check("QuickJS interrupted caller exits promptly", exit.await(2, java.util.concurrent.TimeUnit.SECONDS));
                    check("QuickJS next call survives cancellation", "RECOVER_OK".equals(String.valueOf(call(spider, "searchContent", "recover", false))));
                } finally { outer.shutdownNow(); }
            } finally { call(spider, "destroy"); }
        }
    }
    private void searchQueueChecks() throws Exception {
        Object api = call(type("com.github.tvbox.osc.api.ApiConfig"), "get");
        List<?> sources = (List<?>) call(api, "getSourceBeanList");
        check("search fixture has more sites than worker cap", sources.size() == 35);
        for (String name : new String[]{"SearchActivity", "FastSearchActivity"}) {
            main(() -> putPref("FAST_SEARCH_MODE", false));
            Bundle extras = new Bundle(); if (name.equals("FastSearchActivity")) extras.putString("title", "car7-search");
            Activity page = openPage(name, extras);
            try {
                if (name.equals("SearchActivity")) main(() -> call(page, "search", "car7-search"));
                long end = SystemClock.elapsedRealtime() + 80000;
                while (SystemClock.elapsedRealtime() < end && ((java.util.concurrent.atomic.AtomicInteger) ui(() -> field(page, "allRunCount"))).get() > 0) SystemClock.sleep(200);
                check(name + " all sites converge", ((java.util.concurrent.atomic.AtomicInteger) ui(() -> field(page, "allRunCount"))).get() == 0);
                List<?> rows = (List<?>) ui(() -> field(field(page, "searchAdapter"), "mData"));
                boolean tail = false;
                for (Object row : rows) tail |= String.valueOf(field(row, "id")).equals("car7-search-33");
                check(name + " tail site after full queue has result", tail);
                capturePage(page, "search-queue-" + name.replace("Activity", ""));
                Object source = call(api, "getSource", "car7-tail-33");
                Object spider = call(api, "getCSP", source);
                java.util.concurrent.atomic.AtomicInteger active = (java.util.concurrent.atomic.AtomicInteger) field(spider, "ACTIVE");
                java.util.concurrent.atomic.AtomicInteger peak = (java.util.concurrent.atomic.AtomicInteger) field(spider, "PEAK");
                end = SystemClock.elapsedRealtime() + 10000;
                while (active.get() > 0 && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
                check(name + " workers stop after timeout", active.get() == 0);
                check(name + " bounded worker cap", peak.get() <= 24);
                if (name.equals("SearchActivity")) main(() -> call(page, "search", "car7-next"));
                else main(() -> { });
                if (name.equals("SearchActivity")) {
                    SystemClock.sleep(200); main(page::finish); SystemClock.sleep(700);
                    check("search exit cancels active work", active.get() == 0);
                }
            } finally { if (!ui(page::isFinishing)) main(page::finish); pause(500); }
        }
    }
    private void subscriptionChecks() throws Exception {
        Object api = call(type("com.github.tvbox.osc.api.ApiConfig"), "get");
        List<?> sources = (List<?>) call(api, "getSourceBeanList");
        record(sources.size() > 0, "subscription parsed sites", "unique_sites=" + sources.size());
        JSONArray observations = new JSONArray();
        for (String key : arguments.getString("sites", "热播影视,枫叶影视,爱动漫").split(",")) {
            JSONObject entry = new JSONObject(); entry.put("key", key); observations.put(entry);
            write("subscription-results.json", observations.toString(2));
            long started = SystemClock.elapsedRealtime();
            try {
                Object source = call(api, "getSource", key);
                if (source == null) throw new IllegalStateException("missing source");
                String sourceApi = String.valueOf(call(source, "getApi"));
                entry.put("runtime", sourceApi.contains(".js") ? "QuickJS" : sourceApi.contains(".py") ? "Python" : "JAR");
                Object spider = bounded(() -> call(api, "getCSP", source), 45);
                JSONObject first = new JSONObject(bounded(() -> String.valueOf(call(spider, "homeContent", true)), 30));
                JSONArray categories = first.optJSONArray("class");
                entry.put("categories", categories == null ? 0 : categories.length());
                check("subscription-" + key + "-home", categories != null && categories.length() > 0);
                String positiveTitle = "", positiveId = "";
                if (categories != null && categories.length() > 0) {
                    String tid = categories.getJSONObject(0).getString("type_id");
                    entry.put("category_tid", tid);
                    JSONObject category = new JSONObject(bounded(() -> String.valueOf(call(spider, "categoryContent", tid, "1", true, new java.util.HashMap<String,String>())), 30));
                    JSONArray videos = category.optJSONArray("list");
                    entry.put("category_items", videos == null ? 0 : videos.length());
                    check("subscription-" + key + "-category", videos != null && videos.length() > 0);
                    if (videos != null && videos.length() > 0) {
                        positiveTitle = videos.getJSONObject(0).optString("vod_name");
                        positiveId = videos.getJSONObject(0).optString("vod_id");
                        List<String> ids = new ArrayList<>(); ids.add(videos.getJSONObject(0).getString("vod_id"));
                        JSONObject detail = new JSONObject(bounded(() -> String.valueOf(call(spider, "detailContent", ids)), 30));
                        JSONArray details = detail.optJSONArray("list");
                        entry.put("detail_items", details == null ? 0 : details.length());
                        check("subscription-" + key + "-detail", details != null && details.length() > 0);
                    }
                }
                JSONObject search = new JSONObject(bounded(() -> String.valueOf(call(spider, "searchContent", "潜伏", false)), 32));
                JSONArray found = search.optJSONArray("list");
                entry.put("search_items", found == null ? 0 : found.length());
                if (!positiveTitle.isEmpty()) {
                    final String query = positiveTitle;
                    JSONObject positive = new JSONObject(bounded(() -> String.valueOf(call(spider, "searchContent", query, false)), 32));
                    JSONArray matches = positive.optJSONArray("list");
                    entry.put("positive_search_items", matches == null ? 0 : matches.length());
                    boolean matched = false;
                    if (matches != null) for (int j = 0; j < matches.length(); j++) {
                        JSONObject match = matches.getJSONObject(j);
                        matched |= positiveId.equals(match.optString("vod_id")) || query.trim().equals(match.optString("vod_name").trim());
                    }
                    entry.put("positive_search_known_item", matched);
                    check("subscription-" + key + "-positive-search", matched);
                } else check("subscription-" + key + "-search", found != null && found.length() > 0);
            } catch (Throwable error) {
                entry.put("error_class", error.getClass().getSimpleName());
                Throwable cause = error;
                JSONArray chain = new JSONArray();
                while (cause != null) { chain.put(cause.getClass().getSimpleName()); if (cause.getCause() == null) break; cause = cause.getCause(); }
                entry.put("cause_classes", chain);
                String message = cause == null ? "" : String.valueOf(cause.getMessage());
                message = message.replaceAll("(?i)https?://[^\\s]+", "[url]");
                if (message.matches("(?is).*(?:token|secret|password|authorization|api.?key)\\s*[:=].*")) message = "sensitive diagnostic withheld";
                entry.put("cause_message", message.substring(0, Math.min(500, message.length())));
                record(false, "subscription-" + key, error.getClass().getSimpleName());
                if (error instanceof java.util.concurrent.TimeoutException) throw new Exception("Spider timeout; aborting outstanding work");
            } finally {
                entry.put("elapsed_ms", SystemClock.elapsedRealtime() - started);
                write("subscription-results.json", observations.toString(2));
            }
        }
    }
    private void putPref(String constant, Object value) throws Exception {
        call(type("com.orhanobut.hawk.Hawk"), "put", mapping.getJSONObject("constants").getString(constant), value);
    }
    private Object staticField(String originalClass, String name) throws Exception {
        Class<?> c = type(originalClass);
        Field f = c.getDeclaredField(members(c, "fields").optString(name, name));
        f.setAccessible(true); return f.get(null);
    }
    private int state(Object video) throws Exception { return ((Number) ui(() -> call(video, "getCurrentPlayState"))).intValue(); }
    private long position(Object video) throws Exception { return ((Number) ui(() -> call(video, "getCurrentPosition"))).longValue(); }
    private boolean waitPlaying(Object video, long timeout) throws Exception {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < end) {
            if (Boolean.TRUE.equals(ui(() -> call(video, "isPlaying"))) && position(video) > 100) return true;
            if (state(video) == -1) return false;
            SystemClock.sleep(150);
        }
        return false;
    }
    private long frames(Object renderer) throws Exception {
        if (!(renderer instanceof android.opengl.GLSurfaceView)) return -1;
        java.util.concurrent.FutureTask<Long> task = new java.util.concurrent.FutureTask<>(() -> ((Number) field(renderer, "mSourceFrames")).longValue());
        ((android.opengl.GLSurfaceView) renderer).queueEvent(task);
        return task.get(4, java.util.concurrent.TimeUnit.SECONDS);
    }
    private void settings(Object video, int mode, boolean sr, boolean interp) throws Exception {
        main(() -> {
            putPref("VIDEO_ENHANCE", mode); putPref("VIDEO_SR", sr ? 1 : 0); putPref("VIDEO_INTERP", interp ? 1 : 0);
            call(video, "setVideoEnhancements", mode, sr, interp);
        });
        SystemClock.sleep(500);
    }
    private void progressing(Object video, String label, JSONArray observations) throws Exception {
        Object renderer = ui(() -> field(video, "mRenderView"));
        long first = frames(renderer), p1 = position(video);
        SystemClock.sleep(1800);
        long second = frames(renderer), p2 = position(video);
        JSONObject sample = new JSONObject(); sample.put("stage", label); sample.put("source_before", first);
        sample.put("source_after", second); sample.put("position_before", p1); sample.put("position_after", p2);
        sample.put("state", state(video)); observations.put(sample);
        write("playback-observations.json", observations.toString(2));
        check(label + " advancing decoder and source", p2 > p1 + 500 && second > first + 10);
    }

    private void playbackChecks() throws Exception {
        JSONArray observations = new JSONArray();
        for (int engineType : ("true".equals(arguments.getString("pixelDiagnostic")) ? new int[]{1} : new int[]{2, 1})) {
            final String name = engineType == 2 ? "EXO" : "IJK";
            final int force = engineType;
            final Object video = ui(() -> {
                boolean initialGl = "true".equals(arguments.getString("initialGl"));
                putPref("VIDEO_ENHANCE", initialGl ? 1 : 0); putPref("VIDEO_SR", initialGl ? 1 : 0); putPref("VIDEO_INTERP", initialGl ? 1 : 0); putPref("HIFI_AUDIO", 0);
                Object v = type("com.github.tvbox.osc.player.MyVideoView").getConstructor(Context.class).newInstance(home);
                FrameLayout content = home.findViewById(android.R.id.content);
                FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(1280, 720);
                layout.leftMargin = 800; layout.topMargin = 450; content.addView((View) v, layout);
                JSONObject cfg = new JSONObject(); cfg.put("pl", force); cfg.put("pr", 0); cfg.put("ijk", arguments.getString("codec", "硬解码")); cfg.put("sc", 0);
                call(type("com.github.tvbox.osc.util.PlayerHelper"), "updateCfg", v, cfg, force);
                call(v, "setMute", true); call(v, "setUrl", "/sdcard/TVBoxCar5/motion.mp4"); call(v, "start");
                return v;
            });
            Object lastGl = null;
            try {
                boolean playing = waitPlaying(video, 20000); check(name + " local video playing", playing);
                if (!playing) throw new IllegalStateException(name + " local start failed");
                Object player = ui(() -> call(video, "getMediaPlayer"));
                check(name + " actual engine", type(engineType == 2 ? "com.github.tvbox.osc.player.ExoPlayer" : "com.github.tvbox.osc.player.IjkMediaPlayer").isInstance(player));
                boolean initialGl = engineType == 1 || "true".equals(arguments.getString("initialGl"));
                check(name + (initialGl ? " initial GL" : " initial Texture"), type(initialGl ? "xyz.doikki.videoplayer.render.GlEnhanceRenderView" : "xyz.doikki.videoplayer.render.TextureRenderView").isInstance(ui(() -> field(video, "mRenderView"))));
                if ("true".equals(arguments.getString("pixelDiagnostic"))) {
                    SystemClock.sleep(1200); shot(name + "-Texture-raw");
                    Object codec = ui(() -> field(player, "codec"));
                    JSONObject info = new JSONObject(); info.put("requested_codec", arguments.getString("codec", "硬解码"));
                    info.put("actual_codec", String.valueOf(field(codec, "name")));
                    info.put("options", String.valueOf(field(codec, "option")));
                    write("codec.json", info.toString(2));
                }
                long beforeSwitch = position(video);
                settings(video, 1, true, true);
                lastGl = ui(() -> field(video, "mRenderView"));
                check(name + " Texture to GL without restart", player == ui(() -> call(video, "getMediaPlayer")) && position(video) >= beforeSwitch - 150 && type("xyz.doikki.videoplayer.render.GlEnhanceRenderView").isInstance(lastGl));
                progressing(video, name + " initial GL", observations); shot(name + "-playing");
                if ("true".equals(arguments.getString("pixelDiagnostic"))) continue;
                for (int m = 0; m < 11; m++) {
                    settings(video, m, true, true);
                    Object snapshot = ui(() -> field(field(video, "mRenderView"), "mConfiguration"));
                    int effective = ((Number) field(field(snapshot, "preset"), "id")).intValue();
                    check(name + " video preset " + m, effective == m && player == ui(() -> call(video, "getMediaPlayer")));
                }
                int session = ((Number) ui(() -> call(video, "getAudioSessionId"))).intValue();
                check(name + " audio session valid", session > 0);
                for (int a = 0; a < 9; a++) {
                    final int mode = a;
                    main(() -> { putPref("HIFI_AUDIO", mode); call(type("com.github.tvbox.osc.car.CarEnhance"), "applyAudio", video); });
                    SystemClock.sleep(600);
                    boolean active = Boolean.TRUE.equals(staticField("com.github.tvbox.osc.car.AudioEffectController", "active"));
                    check(name + " audio preset " + a, (a == 0 ? !active : active) && session == ((Number) ui(() -> call(video, "getAudioSessionId"))).intValue());
                }
                main(() -> call(video, "pause")); SystemClock.sleep(450);
                long paused = position(video);
                main(() -> { call(video, "onInfo", 701, 0); call(video, "onInfo", 702, 0); call(video, "onInfo", 3, 0); });
                check(name + " late callbacks keep explicit pause", state(video) == 4 && !Boolean.TRUE.equals(ui(() -> call(video, "isPlaying"))) && !Boolean.TRUE.equals(ui(() -> field(field(video, "mRenderView"), "mPlaybackActive"))));
                settings(video, 0, false, false);
                check(name + " paused GL to Texture preserves state", state(video) == 4 && Math.abs(position(video) - paused) < 200 && player == ui(() -> call(video, "getMediaPlayer")));
                if (engineType == 1) {
                    Object off = ui(() -> field(field(video,"mRenderView"),"mConfiguration"));
                    check("IJK all-off is identity without SR or interpolation", ((Number)field(field(off,"preset"),"id")).intValue()==0 && !Boolean.TRUE.equals(field(off,"superResolution")) && !Boolean.TRUE.equals(field(off,"interpolation")));
                }
                settings(video, 8, true, true);
                check(name + " paused Texture to GL preserves state", state(video) == 4 && Math.abs(position(video) - paused) < 200 && player == ui(() -> call(video, "getMediaPlayer")));
                main(() -> call(video, "resume")); check(name + " paused switch resumes", waitPlaying(video, 8000));
                progressing(video, name + " after paused switch", observations);
                final Object fullscreenRenderer = ui(() -> field(video, "mRenderView"));
                final Object fullscreenSurface = ui(() -> field(fullscreenRenderer, "mPlayerSurface"));
                final Object fullscreenConsumer = ui(() -> field(fullscreenRenderer, "mSurfaceTexture"));
                main(() -> call(video, "startFullScreen")); SystemClock.sleep(1500);
                check(name + " fullscreen active", Boolean.TRUE.equals(ui(() -> call(video, "isFullScreen"))));
                check(name + " fullscreen preserves decoder producer", player == ui(() -> call(video, "getMediaPlayer"))
                        && fullscreenSurface == ui(() -> field(fullscreenRenderer, "mPlayerSurface"))
                        && fullscreenConsumer == ui(() -> field(fullscreenRenderer, "mSurfaceTexture")));
                progressing(video, name + " fullscreen", observations); shot(name + "-fullscreen");
                main(() -> call(video, "stopFullScreen")); SystemClock.sleep(1200);
                check(name + " fullscreen exit preserves decoder producer", fullscreenSurface == ui(() -> field(fullscreenRenderer, "mPlayerSurface"))
                        && fullscreenConsumer == ui(() -> field(fullscreenRenderer, "mSurfaceTexture")));
                progressing(video, name + " fullscreen exit", observations); shot(name + "-fullscreen-exit");
                main(() -> { call(video, "pause"); home.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); });
                SystemClock.sleep(2500);
                Object bgRenderer = ui(() -> field(video, "mRenderView"));
                check(name + " background not continuous", !Boolean.TRUE.equals(ui(() -> field(bgRenderer, "mContinuous"))));
                Intent front = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
                front.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                getTargetContext().startActivity(front); SystemClock.sleep(2000);
                main(() -> call(video, "resume")); check(name + " background resumes", waitPlaying(video, 8000));
                progressing(video, name + " foreground window1", observations);
                progressing(video, name + " foreground window2", observations);
                shot(name + "-foreground");
                main(() -> { call(video,"setVideoEnhancements",0,false,false); call(video,"setVideoEnhancements",1,true,true); call(video,"setVideoEnhancements",0,false,false); call(video,"setVideoEnhancements",1,true,true); });
                SystemClock.sleep(1700);
                check(name + " rapid settings preserve decoder", player == ui(() -> call(video,"getMediaPlayer")) && type("xyz.doikki.videoplayer.render.GlEnhanceRenderView").isInstance(ui(() -> field(video,"mRenderView"))));
                progressing(video,name + " rapid settings",observations);
                beforeSwitch = position(video); settings(video, 0, false, false);
                check(name + " playing GL to Texture no restart", position(video) >= beforeSwitch - 200 && player == ui(() -> call(video, "getMediaPlayer")));
                settings(video, 1, true, true); progressing(video, name + " final GL", observations);
                lastGl = ui(() -> field(video, "mRenderView"));
                main(() -> call(video, "release")); SystemClock.sleep(650);
                check(name + " release terminal producer empty", Boolean.TRUE.equals(field(lastGl, "mReleased")) && ((List<?>) field(lastGl, "mProducers")).isEmpty());
                main(() -> { call(video, "release"); call(video, "release"); });
                check(name + " repeated release idempotent", ui(() -> call(video, "getMediaPlayer")) == null && ui(() -> field(video, "mRenderView")) == null);
                main(() -> call(video, "start")); check(name + " reentry plays", waitPlaying(video, 15000));
                progressing(video, name + " reentry", observations);
                shot(name + "-reentry");
                Object beforePending = ui(() -> field(video,"mRenderView"));
                main(() -> { call(video,"setVideoEnhancements",0,false,false); call(video,"release"); });
                SystemClock.sleep(800);
                check(name + " release during handoff cleans old producer", Boolean.TRUE.equals(field(beforePending,"mReleased")) && ((List<?>)field(beforePending,"mProducers")).isEmpty());
                check(name + " pending handoff engine released", ui(() -> call(video,"getMediaPlayer")) == null && ui(() -> field(video,"mRenderView")) == null);
            } finally {
                main(() -> { call(video, "stopFullScreen"); call(video, "release"); if (((View) video).getParent() instanceof ViewGroup) ((ViewGroup) ((View) video).getParent()).removeView((View) video); });
                SystemClock.sleep(400);
            }
        }
    }

    private void gestureChecks() throws Exception {
        final Object video = ui(() -> {
            Object v = type("com.github.tvbox.osc.player.MyVideoView").getConstructor(Context.class).newInstance(home);
            FrameLayout content = home.findViewById(android.R.id.content);
            FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(1280, 720);
            layout.leftMargin = 800; layout.topMargin = 450;
            content.addView((View) v, layout);
            JSONObject cfg = new JSONObject(); cfg.put("pl", 2); cfg.put("pr", 0); cfg.put("ijk", "硬解码"); cfg.put("sc", 0);
            call(type("com.github.tvbox.osc.util.PlayerHelper"), "updateCfg", v, cfg, 2);
            call(v, "setMute", true); call(v, "setUrl", "/sdcard/TVBoxCar5/motion.mp4"); call(v, "start");
            return v;
        });
        try {
            check("gesture fixture playback", waitPlaying(video, 15000));
            final Object controller = ui(() -> {
                Object c = type("com.github.tvbox.osc.player.controller.VodController").getConstructor(Context.class).newInstance(home);
                JSONObject controllerCfg = new JSONObject(); controllerCfg.put("pl", 2); controllerCfg.put("pr", 0); controllerCfg.put("ijk", "硬解码"); controllerCfg.put("sc", 0); controllerCfg.put("sp", 1.0f); controllerCfg.put("st", 0); controllerCfg.put("et", 0);
                call(c, "setPlayerConfig", controllerCfg);
                call(video, "setVideoController", c);
                call(c, "setEnableInNormal", true); call(c, "setGestureEnabled", true);
                call(c, "setPlayState", 3);
                return c;
            });
            final android.media.AudioManager audio = (android.media.AudioManager) home.getSystemService(Context.AUDIO_SERVICE);
            final int maxVolume = audio == null ? 0 : audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            final int initialVolume = audio == null ? 0 : Math.min(maxVolume, 4);
            main(() -> {
                if (audio != null) audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, initialVolume, 0);
                android.view.WindowManager.LayoutParams lp = home.getWindow().getAttributes();
                lp.screenBrightness = .35f; home.getWindow().setAttributes(lp);
            });
            float brightnessBefore = ui(() -> home.getWindow().getAttributes().screenBrightness);
            int volumeBefore = audio == null ? 0 : audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            long t = SystemClock.uptimeMillis();
            main(() -> {
                MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 180, 520, 0);
                MotionEvent move = MotionEvent.obtain(t, t + 120, MotionEvent.ACTION_MOVE, 180, 160, 0);
                MotionEvent up = MotionEvent.obtain(t, t + 240, MotionEvent.ACTION_UP, 180, 160, 0);
                call(controller, "onTouchEvent", down); call(controller, "onTouchEvent", move); call(controller, "onTouchEvent", up);
                down.recycle(); move.recycle(); up.recycle();
            });
            pause(350);
            float brightnessAfter = ui(() -> home.getWindow().getAttributes().screenBrightness);
            check("left gesture changes window brightness", brightnessAfter > brightnessBefore + .03f);
            long t2 = SystemClock.uptimeMillis();
            main(() -> {
                MotionEvent down = MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 2200, 520, 0);
                MotionEvent move = MotionEvent.obtain(t2, t2 + 120, MotionEvent.ACTION_MOVE, 2200, 160, 0);
                MotionEvent up = MotionEvent.obtain(t2, t2 + 240, MotionEvent.ACTION_UP, 2200, 160, 0);
                call(controller, "onTouchEvent", down); call(controller, "onTouchEvent", move); call(controller, "onTouchEvent", up);
                down.recycle(); move.recycle(); up.recycle();
            });
            pause(350);
            int volumeAfter = audio == null ? volumeBefore : audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            check("right gesture changes music volume", volumeAfter > volumeBefore);
            write("gesture-values.json", new JSONObject().put("brightnessBefore", brightnessBefore).put("brightnessAfter", brightnessAfter).put("volumeBefore", volumeBefore).put("volumeAfter", volumeAfter).toString(2));
            String defaultMode = String.valueOf(ui(() -> call(type("com.github.tvbox.osc.car.InAppBackButton"), "getMode")));
            String defaultSkin = String.valueOf(ui(() -> call(type("com.github.tvbox.osc.car.InAppBackButton"), "getEdgeSkin")));
            check("default back mode is edge", "edge".equals(defaultMode));
            check("default back skin is fluid teardrop", "teardrop".equals(defaultSkin));
            View edgeView = edge(home);
            View backHost = host(home);
            main(() -> call(type("com.github.tvbox.osc.car.InAppBackButton"), "setPlaybackHidden", home, true));
            check("fullscreen hides edge back strip", ui(() -> edgeView.getVisibility()) != View.VISIBLE);
            main(() -> call(type("com.github.tvbox.osc.car.InAppBackButton"), "setPlaybackHidden", home, false));
            check("leaving fullscreen restores edge back strip", ui(() -> edgeView.getVisibility()) == View.VISIBLE);
        } finally {
            main(() -> { call(video, "release"); if (((View) video).getParent() instanceof ViewGroup) ((ViewGroup) ((View) video).getParent()).removeView((View) video); });
        }
    }

    private void pythonChecks() throws Exception {
        Object loader = call(type("com.undcover.freedom.pyramid.PythonLoader"), "getInstance");
        call(loader, "setApplication", getTargetContext().getApplicationContext());
        Object python = call(type("com.chaquo.python.Python"), "getInstance");
        Object builtins = call(python, "getModule", "builtins");
        Method attr = type("com.chaquo.python.PyObject").getMethod("callAttr", String.class, Object[].class);
        Object scope = attr.invoke(builtins, new Object[]{"dict", new Object[0]});
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        try (java.io.InputStream input = getContext().getAssets().open("python_probe.py")) {
            byte[] bytes = new byte[65536]; int n;
            while ((n = input.read(bytes)) > 0) source.write(bytes, 0, n);
        }
        String keys = arguments.getString("sites", "py_douban,py_maple,py_juzong,py_apples,py_guazi");
        String program = "OUT = __import__('base64').b64decode('"
                + android.util.Base64.encodeToString(out.getAbsolutePath().getBytes("UTF-8"), android.util.Base64.NO_WRAP)
                + "').decode('utf-8')\nSITE_KEYS = "
                + new JSONArray(java.util.Arrays.asList(keys.split(","))).toString() + "\n"
                + new String(source.toByteArray(), "UTF-8");
        attr.invoke(builtins, new Object[]{"exec", new Object[]{program, scope}});
        File report = new File(out, "python-results.json");
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(report)) {
            byte[] bytes = new byte[65536]; int n;
            while ((n = input.read(bytes)) > 0) data.write(bytes, 0, n);
        }
        JSONObject summary = new JSONObject(new String(data.toByteArray(), "UTF-8"));
        JSONObject transport = summary.getJSONObject("transport");
        for (String name : new String[]{"form", "binary_post", "binary_response", "redirect_cookies", "response_contract"}) {
            check("python-transport-" + name, transport.optBoolean(name));
        }
        check("python-transport-real-https", transport.optInt("real_https_status") == 200 && transport.optInt("real_https_bytes") > 1000);
        JSONArray sites = summary.getJSONArray("sites");
        for (int i = 0; i < sites.length(); i++) {
            JSONObject site = sites.getJSONObject(i);
            boolean meaningful = site.optBoolean("load_success") && site.optInt("categories")>0 && site.optInt("category_items")>0;
            if (!"py_douban".equals(site.optString("key"))) meaningful &= site.optInt("detail_items")>0 && site.optInt("search_items")>0;
            record(meaningful,
                    "real-site-" + site.optString("key"), site.toString());
        }
        check("python-probe-finished", !summary.has("probe_error") && !summary.has("aborted_outstanding_call"));
        Object targetValue = attr.invoke(builtins, new Object[]{"eval", new Object[]{"__import__('json').dumps(PLAYBACK_TARGET)", scope}});
        String targetText = String.valueOf(targetValue);
        if (targetText.startsWith("{")) {
            JSONObject target = new JSONObject(targetText);
            Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            home = startActivitySync(launch); SystemClock.sleep(15000);
            Object video = ui(() -> {
                putPref("VIDEO_ENHANCE", 0); putPref("VIDEO_SR", 0); putPref("VIDEO_INTERP", 0); putPref("HIFI_AUDIO", 0);
                Object v = type("com.github.tvbox.osc.player.MyVideoView").getConstructor(Context.class).newInstance(home);
                FrameLayout content = home.findViewById(android.R.id.content);
                FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(1280,720); layout.leftMargin=800; layout.topMargin=450;
                content.addView((View)v,layout);
                JSONObject cfg = new JSONObject(); cfg.put("pl",2); cfg.put("pr",0); cfg.put("ijk","硬解码"); cfg.put("sc",0);
                call(type("com.github.tvbox.osc.util.PlayerHelper"),"updateCfg",v,cfg,2);
                java.util.HashMap<String,String> headers = new java.util.HashMap<>();
                JSONObject h = target.optJSONObject("headers");
                if (h != null) { java.util.Iterator<String> headerKeys=h.keys(); while(headerKeys.hasNext()){String key=headerKeys.next();headers.put(key,h.optString(key));} }
                call(v,"setMute",true); call(v,"setUrl",target.getString("url"),headers); call(v,"start"); return v;
            });
            JSONObject playback = new JSONObject(); playback.put("site",target.getString("key"));
            try {
                boolean active = waitPlaying(video,30000); playback.put("playing",active);
                long p1=position(video); SystemClock.sleep(2500); long p2=position(video);
                playback.put("position_before",p1); playback.put("position_after",p2); playback.put("state",state(video));
                int[] size=(int[])ui(()->call(video,"getVideoSize")); playback.put("video_width",size[0]);playback.put("video_height",size[1]);
                check("python-real-EXO-playback",active && p2>p1+500 && size[0]>0);
                shot("python-real-EXO");
            } finally {
                main(()->{call(video,"release");((ViewGroup)((View)video).getParent()).removeView((View)video);});
                write("python-playback.json",playback.toString(2));
            }
        }
    }

    private void section(String name, Job job) {
        try { job.run(); } catch (Throwable error) { record(false, name, Log.getStackTraceString(error)); }
    }
    private void pause(long ms) { SystemClock.sleep(ms); waitForIdleSync(); }
    private <T> T ui(Work<T> work) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        runOnMainSync(() -> { try { value.set(work.run()); } catch (Throwable e) { error.set(e); } });
        if (error.get() != null) throw new Exception(error.get());
        return value.get();
    }
    private void main(Job job) throws Exception { ui(() -> { job.run(); return null; }); }
    private void check(String name, boolean value) { record(value, name, ""); }
    private void record(boolean ok, String name, String detail) {
        if (ok) passed++; else failed++;
        try {
            JSONObject event = new JSONObject(); event.put("pass", ok); event.put("test", name);
            event.put("detail", detail); event.put("at", System.currentTimeMillis()); events.put(event);
        } catch (Exception ignored) { }
        Log.i("Car7Native", (ok ? "PASS " : "FAIL ") + name + " " + detail);
    }
    private Class<?> type(String name) throws Exception { return getTargetContext().getClassLoader().loadClass(mapping.getJSONObject("classes").optString(name, name)); }
    private static JSONObject members(Class<?> c, String kind) {
        JSONObject item = mapping == null ? null : mapping.optJSONObject("members").optJSONObject(c.getName());
        return item == null ? new JSONObject() : item.optJSONObject(kind);
    }
    private static boolean named(Class<?> c, String original, String alias) {
        if (original.equals(alias)) return true;
        JSONArray names = members(c, "methods").optJSONArray(original);
        if (names != null) for (int i = 0; i < names.length(); i++) if (alias.equals(names.optString(i))) return true;
        return false;
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(members(c, "fields").optString(name, name)); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name);
    }
    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class; if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class; if (type == float.class) return Float.class;
        if (type == double.class) return Double.class; if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class; if (type == char.class) return Character.class;
        return type;
    }
    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> root = target instanceof Class ? (Class<?>) target : target.getClass();
        for (Class<?> c = root; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!named(c, name, m.getName()) || m.getParameterTypes().length != args.length) continue;
                Class<?>[] params = m.getParameterTypes(); boolean match = true;
                for (int i = 0; i < args.length; i++) if (args[i] != null && !box(params[i]).isInstance(args[i])) match = false;
                if (!match) continue;
                m.setAccessible(true); return m.invoke(target instanceof Class ? null : target, args);
            }
        }
        throw new NoSuchMethodException(root.getName() + "." + name);
    }
    private int id(String name) { return getTargetContext().getResources().getIdentifier(name, "id", getTargetContext().getPackageName()); }
    private View view(Activity a, String name) { View v = a.findViewById(id(name)); if (v == null) throw new IllegalStateException("missing view " + name); return v; }
    private View host(Activity a) { View v = a.getWindow().getDecorView().findViewWithTag("tvbox.car.back.host"); if (v == null) throw new IllegalStateException("missing back host"); return v; }
    private View ball(Activity a) throws Exception { return (View) field(host(a), "ball"); }
    private View edge(Activity a) throws Exception { return (View) field(host(a), "edge"); }
    private void config(Activity a, String method, Object value) throws Exception {
        String constant;
        switch (method) {
            case "setEnabled": constant = "IN_APP_BACK"; break;
            case "setMode": constant = "BACK_MODE"; break;
            case "setSide": constant = "BACK_SIDE"; break;
            case "setLarge": constant = "BACK_LARGE"; break;
            case "setHideSeconds": constant = "BACK_HIDE_SECONDS"; break;
            case "setHideOnRoot": constant = "BACK_HIDE_ON_ROOT"; break;
            case "setEdgeSkin": constant = "BACK_EDGE_SKIN"; break;
            default: throw new IllegalArgumentException(method);
        }
        String key = mapping.getJSONObject("constants").getString(constant);
        main(() -> {
            Class<?> hawk = type("com.orhanobut.hawk.Hawk");
            if (method.equals("setSide") && !value.equals("both")) call(hawk, "put", "inapp_back_x", value.equals("right") ? 10000 : 0);
            call(hawk, "put", key, value);
            call(type("com.github.tvbox.osc.car.InAppBackButton"), "update", a, 3);
        }); pause(160);
    }
    private Object pref(String key) throws Exception {
        Class<?> hawk = type("com.orhanobut.hawk.Hawk");
        Object actual = mapping.getJSONObject("constants").getString(key);
        return ui(() -> call(hawk, "get", actual));
    }
    private Rect rect(View view) throws Exception {
        return ui(() -> { int[] p = new int[2]; view.getLocationOnScreen(p); return new Rect(p[0], p[1], p[0] + view.getWidth(), p[1] + view.getHeight()); });
    }
    private void point(Activity a, long down, int action, float x, float y) throws Exception {
        main(() -> { MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); e.setSource(InputDevice.SOURCE_TOUCHSCREEN); a.dispatchTouchEvent(e); e.recycle(); });
    }
    private void tap(Activity a, float x, float y) throws Exception {
        long t = SystemClock.uptimeMillis(); point(a, t, MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(45); point(a, t, MotionEvent.ACTION_UP, x, y); pause(180);
    }
    private void tapBall(Activity a) throws Exception { Rect r = rect(ball(a)); tap(a, r.exactCenterX(), r.exactCenterY()); pause(450); }
    private void swipe(Activity a, float x, float y, float x2, float y2, long ms) throws Exception {
        long t = SystemClock.uptimeMillis(); point(a, t, MotionEvent.ACTION_DOWN, x, y);
        for (int i = 1; i <= 12; i++) { SystemClock.sleep(ms / 12); point(a, t, MotionEvent.ACTION_MOVE, x + (x2 - x) * i / 12, y + (y2 - y) * i / 12); }
        point(a, t, MotionEvent.ACTION_UP, x2, y2); pause(450);
    }
    private Activity openAdvanced() throws Exception {
        Intent i = new Intent(); i.setClassName(getTargetContext().getPackageName(), "com.github.tvbox.osc.ui.activity.CarAdvancedActivity"); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity a = startActivitySync(i); pause(500); advanced = a; return a;
    }
    private void assertTargetWindow(String name) throws Exception {
        String targetPackage = getTargetContext().getPackageName();
        String packageName = "null";
        for (int attempt = 0; attempt < 20; attempt++) {
            waitForIdleSync();
            android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            packageName = root == null || root.getPackageName() == null ? "null" : root.getPackageName().toString();
            if (targetPackage.equals(packageName)) {
                check(name + " foreground belongs to target", true);
                return;
            }
            SystemClock.sleep(200);
        }
        check(name + " foreground belongs to target", false);
        throw new IllegalStateException(name + " foreground package=" + packageName);
    }
    private void shot(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new IllegalStateException("empty screenshot");
        try (FileOutputStream f = new FileOutputStream(new File(out, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, f); }
        image.recycle();
    }
    private void accessibilityNode(android.view.accessibility.AccessibilityNodeInfo node, org.xmlpull.v1.XmlSerializer xml) throws Exception {
        if (!node.isVisibleToUser()) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        xml.startTag(null, "node");
        xml.attribute(null, "class", String.valueOf(node.getClassName()));
        xml.attribute(null, "package", String.valueOf(node.getPackageName()));
        xml.attribute(null, "resource-id", String.valueOf(node.getViewIdResourceName()));
        xml.attribute(null, "text", node.isPassword() ? "" : String.valueOf(node.getText() == null ? "" : node.getText()));
        xml.attribute(null, "content-desc", String.valueOf(node.getContentDescription() == null ? "" : node.getContentDescription()));
        xml.attribute(null, "clickable", String.valueOf(node.isClickable()));
        xml.attribute(null, "scrollable", String.valueOf(node.isScrollable()));
        xml.attribute(null, "checked", String.valueOf(node.isChecked()));
        xml.attribute(null, "bounds", "[" + bounds.left + "," + bounds.top + "][" + bounds.right + "," + bounds.bottom + "]");
        for (int i = 0; i < node.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) accessibilityNode(child, xml);
        }
        xml.endTag(null, "node");
    }
    private void tree(Activity a, String name) throws Exception {
        JSONArray nodes = ui(() -> { JSONArray arr = new JSONArray(); addNodes(a.getWindow().getDecorView(), arr); return arr; });
        write(name + ".json", nodes.toString(2));
    }
    private void addNodes(View v, JSONArray nodes) throws Exception {
        if (!v.isShown()) return;
        JSONObject n = new JSONObject(); int[] p = new int[2]; v.getLocationOnScreen(p);
        n.put("class", v.getClass().getName()); n.put("id", v.getId());
        if (v.getId() > 0) try { n.put("resource", v.getResources().getResourceEntryName(v.getId())); } catch (Exception ignored) { }
        n.put("x", p[0]); n.put("y", p[1]); n.put("w", v.getWidth()); n.put("h", v.getHeight()); n.put("alpha", v.getAlpha());
        n.put("clickable", v.isClickable());
        if (v instanceof TextView && !(v instanceof EditText)) n.put("text", ((TextView) v).getText().toString());
        if (v.getContentDescription() != null) n.put("description", v.getContentDescription().toString());
        nodes.put(n);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) addNodes(((ViewGroup) v).getChildAt(i), nodes);
    }
    private Activity openPage(String name, Bundle extras) throws Exception {
        Intent intent = new Intent();
        intent.setClassName(getTargetContext().getPackageName(), "com.github.tvbox.osc.ui.activity." + name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) intent.putExtras(extras);
        Activity page = startActivitySync(intent); pause(900);
        check(name + " has in-app Back host", host(page).isAttachedToWindow());
        return page;
    }
    private void clickView(Activity page, String resource) throws Exception {
        View target = view(page, resource);
        main(() -> target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), true));
        pause(250);
        Rect bounds = rect(target);
        check(resource + " reachable", bounds.width() > 0 && bounds.height() > 0 && bounds.centerY() >= 0 && bounds.centerY() < 1620);
        tap(page, bounds.exactCenterX(), bounds.exactCenterY());
    }
    private android.view.accessibility.AccessibilityNodeInfo optionNode(android.view.accessibility.AccessibilityNodeInfo node, String prefix) {
        if (node == null) return null;
        CharSequence text = node.getText();
        if (node.isVisibleToUser() && text != null && text.toString().startsWith(prefix)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo found = optionNode(node.getChild(i), prefix);
            if (found != null) return found;
        }
        return null;
    }
    private void selectOption(String prefix) throws Exception {
        android.view.accessibility.AccessibilityNodeInfo node = optionNode(getUiAutomation().getRootInActiveWindow(), prefix);
        if (node == null) throw new IllegalStateException("missing visible option " + prefix);
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sendPointerSync(down); sendPointerSync(up); down.recycle(); up.recycle(); pause(450);
    }
    private void capturePage(Activity page, String name) throws Exception { assertTargetWindow(name); tree(page, name); shot(name); }
    private void wakePlayerControls(Activity page) throws Exception {
        if (!ui(() -> view(page, "bottom_container").isShown())) { tap(page, 1440, 600); pause(400); }
        if (!ui(() -> view(page, "bottom_container").isShown())) throw new IllegalStateException("player touch did not reveal controls");
    }

    private View textView(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText()) && root.isShown()) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = textView(((ViewGroup) root).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private void tapText(Activity page, String value) throws Exception {
        View target = ui(() -> textView(page.getWindow().getDecorView(), value));
        if (target == null) throw new IllegalStateException("missing visible text " + value);
        Rect bounds = rect(target); tap(page, bounds.exactCenterX(), bounds.exactCenterY());
    }

    private void uxChecks() throws Exception {
        config(home, "setHideSeconds", 0);
        config(home, "setHideOnRoot", true);
        for (String nav : new String[]{"uxNavHome", "uxNavCategories", "uxNavLive", "uxNavHistory", "uxNavCollect", "uxNavPush", "uxNavSettings"}) {
            View target = view(home, nav); Rect box = rect(target);
            check(nav + " native touch geometry", ui(target::isShown) && box.width() >= 180 && box.height() >= 100 && box.bottom < 1620);
        }
        check("home native 3K scene", ui(() -> view(home, "uxHomeRoot").getWidth()) == 2880);
        capturePage(home, "ux-home");
        clickView(home, "uxNavCategories"); pause(800);
        check("category rail selects category", ((Number) ui(() -> field(home, "currentSelected"))).intValue() == 1);
        capturePage(home, "ux-category");
        clickView(home, "uxNavHome");
        check("recommendation rail returns home", ((Number) ui(() -> field(home, "currentSelected"))).intValue() == 0);

        section("ux-settings", () -> {
            Activity settings = openPage("SettingActivity", null);
            try {
                Object menu = ui(() -> field(settings, "sortAdapter"));
                check("settings seven real categories", ((Number) ui(() -> call(menu, "getItemCount"))).intValue() == 7);
                capturePage(settings, "ux-settings-picture");
                Object manager = ui(() -> call(view(settings, "mGridView"), "getLayoutManager"));
                for (int category = 0; category < 7; category++) {
                    final int index = category;
                    View item = (View) ui(() -> call(manager, "findViewByPosition", index));
                    if (item == null) throw new IllegalStateException("settings category not visible " + index);
                    Rect box = rect(item); tap(settings, box.exactCenterX(), box.exactCenterY()); pause(200);
                    check("settings category " + index + " selected", ((Number) ui(() -> field(settings, "defaultSelected"))).intValue() == index);
                    capturePage(settings, "ux-settings-category-" + index);
                    View scroll = view(settings, "ux_setting_scroll");
                    for (int step = 0; step < 4 && ui(() -> scroll.canScrollVertically(1)); step++) {
                        swipe(settings, 2300, 1330, 2300, 600, 500);
                        capturePage(settings, "ux-settings-category-" + index + "-scroll-" + step);
                    }
                    main(() -> call(scroll, "scrollTo", 0, 0)); pause(150);
                    if (index == 2) {
                        android.widget.CompoundButton toggle = (android.widget.CompoundButton) view(settings, "uxBackgroundSwitch");
                        boolean before = ui(toggle::isChecked); clickView(settings, "uxBackgroundRow");
                        check("background switch persists", ui(toggle::isChecked) != before);
                        clickView(settings, "uxBackgroundRow");
                    }
                    if (index == 3) {
                        android.widget.CompoundButton toggle = (android.widget.CompoundButton) view(settings, "uxLongPressSwitch");
                        boolean before = ui(toggle::isChecked); clickView(settings, "uxLongPressRow");
                        check("long press switch toggles", ui(toggle::isChecked) != before);
                        clickView(settings, "uxLongPressRow"); capturePage(settings, "ux-settings-touch");
                    }
                }
            } finally { main(settings::finish); pause(300); }
        });

        section("ux-search", () -> {
            Activity search = openPage("SearchActivity", null);
            try {
                check("search native split panels", ui(() -> view(search, "etSearch").getWidth()) == 2100);
                View keyboard = view(search, "keyBoardRoot");
                for (String key : new String[]{"A", "Z", "0", "9"}) check("keyboard includes " + key, ui(() -> textView(keyboard, key)) != null);
                main(() -> ((EditText) view(search, "etSearch")).requestFocus());
                tapText(search, "Q");
                check("native keyboard inserts at cursor", ui(() -> ((EditText) view(search, "etSearch")).getText().toString()).contains("Q"));
                main(() -> ((EditText) view(search, "etSearch")).setText("车机界面验收"));
                clickView(search, "tvSearch");
                Object adapter = ui(() -> field(search, "searchAdapter"));
                long deadline = SystemClock.elapsedRealtime() + 20000;
                while (SystemClock.elapsedRealtime() < deadline && ((List<?>) ui(() -> field(adapter, "mData"))).size() < 5) pause(300);
                int count = ((List<?>) ui(() -> field(adapter, "mData"))).size();
                record(count == 5, "search positive fixture returns five results", "results=" + count);
                check("search completed pending sources", ((java.util.Set<?>) ui(() -> field(search, "pendingSearchKeys"))).isEmpty());
                capturePage(search, "ux-search-results");
                if (count > 0) {
                    ActivityMonitor monitor = addMonitor("com.github.tvbox.osc.ui.activity.DetailActivity", null, false);
                    try {
                        Object layout = ui(() -> call(view(search, "mGridView"), "getLayoutManager"));
                        View first = (View) ui(() -> call(layout, "findViewByPosition", 0));
                        if (first == null) throw new IllegalStateException("search result row not visible");
                        Rect row = rect(first); tap(search, row.exactCenterX(), row.exactCenterY());
                        Activity detail = waitForMonitorWithTimeout(monitor, 8000);
                        check("search result opens detail by touch", detail != null);
                        if (detail != null) { pause(1800); check("search detail metadata loaded", ui(() -> field(detail, "vodInfo")) != null); capturePage(detail, "ux-search-opened-detail"); main(() -> call(detail, "onBackButtonStepUp")); pause(350); }
                    } finally { removeMonitor(monitor); }
                }
            } finally { main(search::finish); pause(300); }
        });

        section("ux-detail-player", () -> {
            main(() -> { putPref("SHOW_PREVIEW", true); putPref("VIDEO_ENHANCE", 1); putPref("VIDEO_SR", 1); putPref("VIDEO_INTERP", 1); putPref("PLAY_TYPE", 2); });
            Bundle extras = new Bundle(); extras.putString("sourceKey", "car7_ui"); extras.putString("id", "car7-0");
            Activity detail = openPage("DetailActivity", extras);
            try {
                pause(3500);
                Object fragment = ui(() -> field(detail, "playFragment"));
                Object video = ui(() -> field(fragment, "mVideoView"));
                Object controller = ui(() -> field(fragment, "mController"));
                check("detail fixture preview playing", waitPlaying(video, 15000));
                View preview = view(detail, "previewPlayer");
                check("detail true 16:9 preview", ui(preview::getWidth) * 9 == ui(preview::getHeight) * 16);
                Object manager = ui(() -> call(view(detail, "mGridView"), "getLayoutManager"));
                check("detail eight-column episodes", ((Number) ui(() -> call(manager, "getSpanCount"))).intValue() == 8);
                check("detail no raw address in metadata", !ui(() -> ((TextView) view(detail, "tvPlayUrl")).getText().toString()).contains("://"));
                capturePage(detail, "ux-detail");
                Object decoder = ui(() -> call(video, "getMediaPlayer"));
                clickView(detail, "tvPlay"); pause(800); wakePlayerControls(detail);
                check("fullscreen keeps decoder", decoder == ui(() -> call(video, "getMediaPlayer")));
                pause(1500);
                check("source frame-rate badge present", ui(() -> ((TextView) view(detail, "tv_framerate")).getText().toString()).contains("fps"));
                check("primary playback targets 124px", ui(() -> view(detail, "uxPlayPause").getHeight()) == 124);
                capturePage(detail, "ux-player");
                clickView(detail, "uxPlayPause"); pause(300);
                check("pause primary control", !Boolean.TRUE.equals(ui(() -> call(video, "isPlaying"))));
                capturePage(detail, "ux-player-paused");
                long before = position(video); clickView(detail, "uxForward"); pause(400);
                check("forward 15s primary control", position(video) >= before + 12000);
                clickView(detail, "uxRewind"); pause(400);
                check("rewind 15s primary control", Math.abs(position(video) - before) < 2500);
                clickView(detail, "uxPlayPause"); check("resume primary control", waitPlaying(video, 5000));
                wakePlayerControls(detail); clickView(detail, "uxMore");
                check("legacy advanced controls reachable", ui(() -> view(detail, "uxAdvancedControls").isShown()));
                capturePage(detail, "ux-player-more");
                clickView(detail, "uxExpertToggle"); pause(350);
                capturePage(detail, "ux-player-advanced");
                clickView(detail, "uxMoreClose"); pause(250);
                main(() -> call(type("com.orhanobut.hawk.Hawk"), "put", "car_ux_long_press", true));
                long down = SystemClock.uptimeMillis();
                main(() -> { MotionEvent press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 1440, 600, 0); call(controller, "onLongPress", press); press.recycle(); });
                check("long press speed 2x", Math.abs(((Number) ui(() -> call(video, "getSpeed"))).floatValue() - 2f) < .05f);
                main(() -> { MotionEvent cancel = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 1440, 600, 0); call(controller, "onTouchEvent", cancel); cancel.recycle(); });
                check("cancel restores speed", Math.abs(((Number) ui(() -> call(video, "getSpeed"))).floatValue() - 1f) < .05f);
                tap(detail, 1440, 600); pause(400);
                capturePage(detail, "ux-player-hidden");
                wakePlayerControls(detail);
                main(() -> call(detail, "onBackButtonStepUp")); pause(500);
                check("Back leaves fullscreen before detail", !Boolean.TRUE.equals(ui(() -> field(detail, "fullWindows"))) && !ui(detail::isFinishing));
                main(() -> call(detail, "onBackButtonStepUp")); pause(500);
                check("Back leaves detail", ui(detail::isFinishing));
            } finally { if (!ui(detail::isFinishing)) main(detail::finish); pause(500); }
        });

        section("ux-live", () -> {
            Activity live = openPage("LivePlayActivity", null);
            try {
                pause(5000);
                Object video = ui(() -> field(live, "mVideoView"));
                check("live fixture playing", waitPlaying(video, 15000));
                View player = (View) video;
                check("live 16:9 preview", ui(player::getWidth) * 9 == ui(player::getHeight) * 16);
                capturePage(live, "ux-live");
                Object decoder = ui(() -> field(video, "mMediaPlayer"));
                long before = position(video);
                clickView(live, "ux_live_fullscreen"); pause(1000);
                check("live fullscreen keeps same VideoView and decoder", video == ui(() -> field(live, "mVideoView")) && decoder == ui(() -> field(video, "mMediaPlayer")) && ui(player::getWidth) == 2880);
                check("live fullscreen playback advances", position(video) > before + 300);
                capturePage(live, "ux-live-fullscreen");
                main(live::onBackPressed); pause(500);
                check("live Back returns preview without decoder restart", ui(player::getWidth) == 1632 && !ui(live::isFinishing) && decoder == ui(() -> field(video, "mMediaPlayer")));
                capturePage(live, "ux-live-after-back");
            } finally { main(live::finish); pause(400); }
        });
    }
    private void selectSettingsCategory(Activity settings, int index) throws Exception {
        Object manager = ui(() -> call(view(settings, "mGridView"), "getLayoutManager"));
        View item = (View) ui(() -> call(manager, "findViewByPosition", index));
        Rect box = rect(item); tap(settings, box.exactCenterX(), box.exactCenterY()); pause(250);
    }
    private void journeyChecks() throws Exception {
        main(() -> { putPref("SHOW_PREVIEW", true); putPref("PLAY_TYPE", 2); call(type("com.orhanobut.hawk.Hawk"), "put", "car_ux_background_audio", true); });
        Bundle extras = new Bundle(); extras.putString("sourceKey", "car7_ui"); extras.putString("id", "car7-0");
        Activity detail = openPage("DetailActivity", extras);
        try {
            Object fragment = ui(() -> field(detail, "playFragment"));
            Object video = ui(() -> field(fragment, "mVideoView"));
            check("journey playback starts", waitPlaying(video, 20000));
            main(() -> call(video, "seekTo", 35000L)); pause(1500);
            check("journey seek reached 35s", position(video) >= 33000);
            Activity coverOn = openPage("SettingActivity", null);
            try {
                long bg = position(video); SystemClock.sleep(1800);
                check("background audio ON keeps position progressing", position(video) > bg + 800 && Boolean.TRUE.equals(ui(() -> call(video, "isPlaying"))));
                check("background audio owns foreground service", staticField("com.github.tvbox.osc.player.MusicPlaybackService", "instance") != null);
            } finally { main(coverOn::finish); pause(800); }
            Activity settingsOff = openPage("SettingActivity", null);
            try {
                selectSettingsCategory(settingsOff, 2);
                boolean beforeToggle = (Boolean) ui(() -> call(view(settingsOff, "uxBackgroundSwitch"), "isChecked"));
                if (beforeToggle) clickView(settingsOff, "uxBackgroundRow");
                check("background preference OFF stored", !((Boolean) ui(() -> call(view(settingsOff, "uxBackgroundSwitch"), "isChecked"))));
                long stopped = position(video); SystemClock.sleep(1200);
                record(!Boolean.TRUE.equals(ui(() -> call(video, "isPlaying"))) && Math.abs(position(video) - stopped) < 300,
                        "background audio OFF pauses playback",
                        "before=" + stopped + " after=" + position(video) + " lifecyclePaused=" + ui(() -> field(fragment, "carLifecyclePaused")));
            } finally { main(settingsOff::finish); pause(800); }
            check("foreground resumes lifecycle-paused video", waitPlaying(video, 5000));
            capturePage(detail, "journey-foreground-detail");
            main(() -> call(detail, "onBackButtonStepUp")); pause(800);
            check("journey detail closes", ui(detail::isFinishing));
        } finally { if (!ui(detail::isFinishing)) main(detail::finish); }
    }
    private void searchDiagnosticChecks() throws Exception {
        Activity search = openPage("SearchActivity", null);
        try {
            EditText input = (EditText) view(search, "etSearch");
            main(() -> { input.setText(""); input.requestFocus(); });
            tapText(search, "Q");
            check("diagnostic focused keyboard inserts", ui(() -> input.getText().toString()).contains("Q"));
            main(() -> input.setText("车机界面验收"));
            main(() -> input.setSelection(input.length()));
            check("diagnostic input exact text", "车机界面验收".equals(ui(() -> input.getText().toString())));
            capturePage(search, "searchdiag-before");
            clickView(search, "tvSearch");
            Object adapter = ui(() -> field(search, "searchAdapter"));
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (SystemClock.elapsedRealtime() < deadline && !((java.util.Set<?>) ui(() -> field(search, "pendingSearchKeys"))).isEmpty()) pause(300);
            int count = ((List<?>) ui(() -> field(adapter, "mData"))).size();
            int pending = ((java.util.Set<?>) ui(() -> field(search, "pendingSearchKeys"))).size();
            write("searchdiag.json", new JSONObject().put("input", ui(() -> input.getText().toString())).put("resultCount", count).put("pending", pending).toString(2));
            record(count == 5, "diagnostic search returns five fixture results", "count=" + count + " pending=" + pending);
            capturePage(search, "searchdiag-after");
        } finally { main(search::finish); pause(300); }
    }
    private void stateChecks() throws Exception {
        Activity search = openPage("SearchActivity", null);
        try {
            pause(600);
            View empty = view(search, "uxSearchEmpty");
            View list = view(search, "llLayout");
            main(() -> { empty.setVisibility(View.VISIBLE); list.setVisibility(View.GONE); });
            setStateCopy(empty, "没有找到内容", "试试完整片名或首字母简拼");
            capturePage(search, "state-empty");
            setStateCopy(empty, "搜索失败", "检查网络连接后重试");
            capturePage(search, "state-error");
            setStateCopy(empty, "网络未连接", "连接网络后返回此页重试");
            capturePage(search, "state-offline");
            main(() -> { empty.setVisibility(View.GONE); list.setVisibility(View.VISIBLE); });
            main(() -> call(search, "showLoading")); pause(300);
            capturePage(search, "state-loading");
            main(() -> call(search, "showSuccess"));
        } finally { main(search::finish); pause(300); }
    }

    private void setStateCopy(View empty, String title, String message) throws Exception {
        main(() -> {
            ((TextView) ((ViewGroup) empty).getChildAt(1)).setText(title);
            ((TextView) ((ViewGroup) empty).getChildAt(2)).setText(message);
        });
        pause(180);
    }

    private void pagesChecks() throws Exception {
        config(home, "setEnabled", true); config(home, "setMode", "floating");
        config(home, "setHideOnRoot", true); config(home, "setHideSeconds", 4);
        clickView(home, "uxNavCategories");
        long categoryDeadline = SystemClock.elapsedRealtime() + 6000;
        while (SystemClock.elapsedRealtime() < categoryDeadline && ((Number) ui(() -> field(home, "currentSelected"))).intValue() != 1) pause(250);
        capturePage(home, "page-category");
        check("gallery category is selected", ((Number) ui(() -> field(home, "currentSelected"))).intValue() == 1);
        config(home, "setHideOnRoot", false); config(home, "setHideSeconds", 0); tapBall(home); pause(500);
        long homeDeadline = SystemClock.elapsedRealtime() + 3000;
        while (SystemClock.elapsedRealtime() < homeDeadline && ((Number) ui(() -> field(home, "currentSelected"))).intValue() != 0) pause(200);
        section("gallery-detail-player", () -> {
            main(() -> { putPref("SHOW_PREVIEW", true); putPref("VIDEO_ENHANCE", 0); putPref("VIDEO_SR", 0); putPref("VIDEO_INTERP", 0); putPref("HIFI_AUDIO", 0); });
            Bundle extras = new Bundle(); extras.putString("sourceKey", "car7_ui"); extras.putString("id", "car7-0");
            Activity detail = openPage("DetailActivity", extras);
            try {
                pause(2500); capturePage(detail, "page-detail");
                check("detail fixture metadata loaded", ui(() -> field(detail, "vodInfo")) != null);
                Object fragment = ui(() -> field(detail, "playFragment"));
                Object video = ui(() -> field(fragment, "mVideoView"));
                check("detail preview plays fixture", waitPlaying(video, 15000));
                clickView(detail, "tvPlay"); pause(1000);
                Object controller = ui(() -> field(fragment, "mController"));
                wakePlayerControls(detail);
                capturePage(detail, "page-player-controls");
                Object decoder = ui(() -> call(video, "getMediaPlayer"));
                clickView(detail, "uxMore");
                int session = ((Number) ui(() -> call(video, "getAudioSessionId"))).intValue();
                clickView(detail, "play_car_sr");
                check("SR button updates saved preference", ((Number) pref("VIDEO_SR")).intValue() == 1);
                check("SR button updates current renderer", Boolean.TRUE.equals(ui(() -> field(field(field(video, "mRenderView"), "mConfiguration"), "superResolution"))));
                wakePlayerControls(detail); clickView(detail, "play_car_interp");
                check("interpolation button updates current renderer", Boolean.TRUE.equals(ui(() -> field(field(field(video, "mRenderView"), "mConfiguration"), "interpolation"))));
                wakePlayerControls(detail); clickView(detail, "play_car_audio"); selectOption("影院");
                check("audio button uses same current session", ((Number) pref("HIFI_AUDIO")).intValue() == 1
                        && session == ((Number) ui(() -> call(video, "getAudioSessionId"))).intValue());
                wakePlayerControls(detail); clickView(detail, "play_car_video");
                shot("page-player-presets"); selectOption("臻彩 ·");
                check("video preset button updates current renderer", ((Number) pref("VIDEO_ENHANCE")).intValue() == 1
                        && ((Number) ui(() -> field(field(field(field(video, "mRenderView"), "mConfiguration"), "preset"), "id"))).intValue() == 1);
                check("four quick controls preserve decoder", decoder == ui(() -> call(video, "getMediaPlayer")));
                wakePlayerControls(detail); capturePage(detail, "page-player-controls-enabled");
                JSONArray observation = new JSONArray(); progressing(video, "detail quick controls", observation);
                write("page-playback.json", observation.toString(2));
                if (ui(() -> view(detail, "bottom_container").isShown())) { tap(detail, 1440, 600); pause(350); }
                config(detail, "setHideSeconds", 0); main(() -> call(detail, "onBackButtonStepUp")); pause(500);
                check("player Back returns to detail before finishing", !ui(detail::isFinishing) && !Boolean.TRUE.equals(ui(() -> field(detail, "fullWindows"))));
                capturePage(detail, "page-detail-after-back");
                config(detail, "setMode", "floating"); config(detail, "setHideSeconds", 0); tapBall(detail);
                if (!ui(detail::isFinishing)) { main(() -> call(detail, "onBackButtonStepUp")); pause(350); }
                check("detail Back finishes one page", ui(detail::isFinishing));
            } finally { if (!ui(detail::isFinishing)) main(detail::finish); pause(400); }
        });
        section("gallery-settings", () -> {
            Activity settings = openPage("SettingActivity", null);
            capturePage(settings, "page-settings-top");
            Object settingsManager = ui(() -> call(view(settings, "mGridView"), "getLayoutManager"));
            View backCategory = (View) ui(() -> call(settingsManager, "findViewByPosition", 4));
            Rect tab = rect(backCategory); tap(settings, tab.exactCenterX(), tab.exactCenterY()); pause(250);
            View entry = view(settings, "llCarAdvanced");
            main(() -> entry.requestRectangleOnScreen(new Rect(0, 0, entry.getWidth(), entry.getHeight()), true)); pause(350);
            capturePage(settings, "page-settings-car");
            Activity a = openAdvanced();
            try {
                capturePage(a, "page-advanced-top");
                int srBefore = ((Number) pref("VIDEO_SR")).intValue();
                int interpBefore = ((Number) pref("VIDEO_INTERP")).intValue();
                clickView(a, "llAdvancedSr");
                check("advanced SR switch toggles saved value", ((Number) pref("VIDEO_SR")).intValue() == 1 - srBefore);
                clickView(a, "llAdvancedInterp");
                check("advanced interpolation switch toggles saved value", ((Number) pref("VIDEO_INTERP")).intValue() == 1 - interpBefore);
                clickView(a, "ux_setting_audio_tab"); capturePage(a, "page-advanced-audio");
                clickView(a, "ux_setting_return_tab"); capturePage(a, "page-advanced-return-top");
                clickView(a, "llAdvancedBackHide"); shot("page-hide-options"); selectOption("6 秒");
                check("advanced hide duration stored", ((Number) pref("BACK_HIDE_SECONDS")).intValue() == 6);
                clickView(a, "llAdvancedBackMode"); shot("page-back-mode-options"); selectOption("双模共存");
                check("advanced dual mode stored", "both".equals(pref("BACK_MODE")));
                clickView(a, "llAdvancedBackSkin"); selectOption("胶囊抽屉");
                check("advanced capsule skin stored", "capsule".equals(pref("BACK_EDGE_SKIN")));
                View last = view(a, "llAdvancedRootHide");
                main(() -> last.requestRectangleOnScreen(new Rect(0, 0, last.getWidth(), last.getHeight()), true)); pause(350);
                capturePage(a, "page-advanced-back");
                config(a, "setHideSeconds", 0); tapBall(a); check("advanced Back returns one page", ui(a::isFinishing));
            } finally { if (!ui(a::isFinishing)) main(a::finish); }
            config(settings, "setHideSeconds", 0); tapBall(settings); check("settings Back returns home", ui(settings::isFinishing));
        });
        for (String name : new String[]{"SearchActivity", "FastSearchActivity", "HistoryActivity", "CollectActivity", "LocalFileActivity", "PushActivity"}) {
            final String pageName = name;
            section("gallery-" + name, () -> {
                Bundle extras = new Bundle(); if (pageName.equals("FastSearchActivity")) extras.putString("title", "车机界面验收");
                Activity page = openPage(pageName, extras);
                try {
                    pause(pageName.equals("FastSearchActivity") ? 2500 : 400);
                    capturePage(page, "page-" + pageName.replace("Activity", "").toLowerCase(java.util.Locale.US));
                    config(page, "setMode", "floating"); config(page, "setHideSeconds", 0); tapBall(page);
                    check(pageName + " in-app Back finishes one level", ui(page::isFinishing));
                } finally { if (!ui(page::isFinishing)) main(page::finish); pause(300); }
            });
        }
        section("gallery-live", () -> {
            String liveApi = arguments == null ? "http://127.0.0.1:9978/file/TVBoxCar5/car7-live.m3u"
                    : arguments.getString("liveApi", "http://127.0.0.1:9978/file/TVBoxCar5/car7-live.m3u");
            main(() -> { putPref("LIVE_API_URL", liveApi); putPref("PLAY_TYPE", 2); });
            Activity live = openPage("LivePlayActivity", null);
            try {
                Object video = ui(() -> field(live, "mVideoView"));
                check("live fixture actually playing", waitPlaying(video, 20000));
                JSONArray observation = new JSONArray(); progressing(video, "local live", observation); write("page-live-playback.json", observation.toString(2));
                capturePage(live, "page-live"); main(() -> call(live, "showChannelList")); pause(300); capturePage(live, "page-live-channels");
                clickView(live, "ux_live_favorite");
                check("live channel favorite stores and labels", ui(() -> ((TextView) view(live, "ux_live_favorite")).getText().toString()).contains("已收藏"));
                clickView(live, "ux_live_favorites"); shot("page-live-favorites"); sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); pause(200);
                clickView(live, "ux_live_favorite");
                check("live favorite removes again", !ui(() -> ((TextView) view(live, "ux_live_favorite")).getText().toString()).contains("已收藏"));
                clickView(live, "ux_live_settings"); capturePage(live, "page-live-settings");
                clickView(live, "ux_live_close_settings"); check("live settings closes", !ui(() -> view(live, "ux_live_close_settings").isShown()));
                config(live, "setHideSeconds", 0); tapBall(live); if (!ui(live::isFinishing)) tapBall(live);
                check("live in-app Back exits one page", ui(live::isFinishing));
            } finally { if (!ui(live::isFinishing)) main(live::finish); pause(400); }
        });
        check("page gallery retains home", !ui(home::isFinishing));
    }
    private void homeChecks() throws Exception {
        tree(home, "home-cold"); shot("home-cold");
        record(ui(() -> ball(home).getWidth()) == 243, "cold-start ball has car-scale hit box",
                "width=" + ui(() -> ball(home).getWidth()) + " token=" + ui(() -> field(host(home), "tokenScale")));
        config(home, "setEnabled", true); config(home, "setMode", "floating"); config(home, "setHideOnRoot", false); config(home, "setHideSeconds", 0);
        Rect title = rect(view(home, "tvName"));
        check("home header safe top>=90", title.top >= 89);
        check("home header safe left>=79", title.left >= 78);
        Rect setting = rect(view(home, "tvSetting"));
        check("home setting complete inside safe right", setting.right <= 2802 && setting.width() > 300);
        tree(home, "home-initial"); shot("home-initial");
        View tabs = view(home, "mGridView"); Object manager = ui(() -> call(tabs, "getLayoutManager"));
        int count = (Integer) ui(() -> call(call(tabs, "getAdapter"), "getItemCount"));
        Rect tabRect = rect(tabs);
        int before = (Integer) ui(() -> call(manager, "findFirstVisibleItemPosition"));
        boolean overflow = ui(() -> tabs.canScrollHorizontally(1));
        swipe(home, 2600, tabRect.exactCenterY(), 400, tabRect.exactCenterY(), 500);
        int after = (Integer) ui(() -> call(manager, "findFirstVisibleItemPosition"));
        record(!overflow || after > before, "category horizontal viewport", "items=" + count + " overflow=" + overflow + " firstBefore=" + before + " firstAfter=" + after);
        for (int i = 0; i < 10 && ui(() -> tabs.canScrollHorizontally(1)); i++) swipe(home, 2600, tabRect.exactCenterY(), 400, tabRect.exactCenterY(), 400);
        int last = (Integer) ui(() -> call(manager, "findLastCompletelyVisibleItemPosition"));
        check("last category reachable", last == count - 1);
        tree(home, "home-tabs-end"); shot("home-tabs-end");
        main(() -> call(tabs, "scrollToPosition", 0)); pause(500);
        View category = (View) ui(() -> call(manager, "findViewByPosition", 1));
        if (category != null) {
            Rect r = rect(category); tap(home, r.exactCenterX(), r.exactCenterY()); pause(800);
            check("category touch selects page", ((Number) ui(() -> field(home, "currentSelected"))).intValue() == 1);
            check("collapsed category top respects safe inset", rect(tabs).top >= 89);
            tapBall(home); pause(600);
            check("category back returns home", ((Number) ui(() -> field(home, "currentSelected"))).intValue() == 0);
            check("home header restored safely", rect(view(home, "tvName")).top >= 89);
        }
        View rec = view(home, "tvHotList"); Object lm = ui(() -> call(rec, "getLayoutManager"));
        int cards = (Integer) ui(() -> call(call(rec, "getAdapter"), "getItemCount"));
        if (cards > 0) {
            Rect r = rect(rec);
            for (int i = 0; i < 16 && ui(() -> rec.canScrollHorizontally(1)); i++) swipe(home, 2500, r.exactCenterY(), 450, r.exactCenterY(), 400);
            int end = (Integer) ui(() -> call(lm, "findLastCompletelyVisibleItemPosition"));
            check("last recommendation reachable", end == cards - 1);
            View endView = (View) ui(() -> call(lm, "findViewByPosition", cards - 1));
            if (endView != null) {
                Rect endBox = rect(endView); check("last card safe right", endBox.right <= 2802);
                View label = endView.findViewById(id("tvName"));
                check("recommendation title independent full height", label != null && label.getHeight() >= 150);
            }
            tree(home, "home-cards-end"); shot("home-cards-end");
        } else record(false, "recommendations loaded", "zero items");
        tapBall(home); tapBall(home);
        check("home root ball never exits", !ui(home::isFinishing));
        config(home, "setHideOnRoot", true);
        check("root hide removes ball hit target", ui(() -> ball(home).getVisibility()) != View.VISIBLE);
        config(home, "setHideOnRoot", false);
        check("root hide disabled restores ball", ui(() -> ball(home).getVisibility()) == View.VISIBLE);
    }
    private void backChecks() throws Exception {
        Activity a = openAdvanced();
        clickView(a, "ux_setting_return_tab");
        config(a, "setEnabled", true); config(a, "setMode", "floating"); config(a, "setSide", "left"); config(a, "setLarge", false); config(a, "setHideSeconds", 0);
        float d = ((Number) ui(() -> field(host(a), "tokenScale"))).floatValue();
        check("standard ball 72dp hit box", Math.abs(ui(() -> ball(a).getWidth()) - 72 * d) <= 1);
        check("standard ball 56dp visual", Math.abs(((Number) ui(() -> field(ball(a), "visualSize"))).floatValue() - 56 * d) < 1);
        main(() -> view(a, "switchAdvancedBackSize").performClick()); pause(250);
        check("native size switch updates immediately", Math.abs(ui(() -> ball(a).getWidth()) - 80 * d) <= 1);
        tree(a, "ball-large"); shot("ball-large");
        main(() -> view(a, "switchAdvancedBackSize").performClick()); pause(250);
        Rect b = rect(ball(a));
        long dragTime = SystemClock.uptimeMillis();
        point(a, dragTime, 0, b.exactCenterX(), b.exactCenterY());
        for (int i = 1; i <= 8; i++) {
            SystemClock.sleep(60);
            point(a, dragTime, 2, b.exactCenterX() + (2100 - b.exactCenterX()) * i / 8f,
                    b.exactCenterY() + (450 - b.exactCenterY()) * i / 8f);
        }
        pause(120); shot("ball-dragging");
        check("dragging ball uses1.08 scale", Math.abs(((Number) ui(() -> field(ball(a), "drawScale"))).floatValue() - 1.08f) < .02f);
        point(a, dragTime, 1, 2100, 450); pause(500);
        check("ball drag docks right", (Boolean) ui(() -> field(ball(a), "dockLeft")) == false);
        check("ball normalized x persisted", ((Number) pref("BACK_POSITION_X")).intValue() == 10000);
        check("ball dock remains in safe bounds", rect(ball(a)).right <= 2802 && rect(ball(a)).top >= 89);
        int yNorm = ((Number) pref("BACK_POSITION_Y")).intValue();
        tree(a, "ball-right-dock"); shot("ball-right-dock");
        config(a, "setHideSeconds", 4); pause(4500);
        check("idle alpha becomes .38", Math.abs(ui(() -> ball(a).getAlpha()) - .38f) < .02f);
        check("idle is stable relative to dock", Math.abs(((Number) ui(() -> field(ball(a), "idleFraction"))).floatValue() - 1) < .01f);
        tree(a, "ball-idle"); shot("ball-idle");
        final int[] touches = {0}; final View[] marker = {null};
        main(() -> {
            FrameLayout content = a.findViewById(android.R.id.content);
            marker[0] = new View(a); FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(160, 160); p.leftMargin = 600; p.topMargin = 650;
            marker[0].setOnTouchListener((v, e) -> { if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_UP) touches[0]++; return true; });
            content.addView(marker[0], p);
        }); pause(100);
        tap(a, 680, 730); pause(180);
        check("blank host passes original DOWN and UP", touches[0] == 2);
        check("page touch wakes ball in 120ms", Math.abs(ui(() -> ball(a).getAlpha()) - .9f) < .02f);
        main(() -> ((ViewGroup) marker[0].getParent()).removeView(marker[0]));
        config(a, "setHideSeconds", 0);
        tapBall(a); pause(400);
        check("advanced ball back finishes one page", ui(a::isFinishing));
        Activity next = openAdvanced();
        check("ball y normalization survives page change", ((Number) pref("BACK_POSITION_Y")).intValue() == yNorm);
        check("ball dock survives page change", (Boolean) ui(() -> field(ball(next), "dockLeft")) == false);
        View oldHost = host(next);
        config(next, "setEnabled", false);
        config(next, "setEdgeSkin", "teardrop");
        check("disable removes decor host", ui(() -> next.getWindow().getDecorView().findViewWithTag("tvbox.car.back.host")) == null);
        check("disabled host is disposed", (Boolean) ui(() -> field(oldHost, "disposed")));
        config(next, "setEnabled", true);
        config(next, "setMode", "both");
        check("dual mode keeps both controls", ui(() -> ball(next).isShown() && edge(next).isShown()));
        main(() -> call(type("com.orhanobut.hawk.Hawk"), "put", "inapp_back_edge_y", 4000));
        config(next, "setMode", "edge");
        config(next, "setMode", "edge"); config(next, "setSide", "left");
        check("edge-only hides ball", ui(() -> ball(next).getVisibility()) != View.VISIBLE && ui(() -> edge(next).isShown()));
        float cy = ((Number) ui(() -> field(edge(next), "centerY"))).floatValue();
        tap(next, 10 * d, cy); check("edge tap never returns", !ui(next::isFinishing));
        swipe(next, 10 * d, cy, 40 * d, cy, 130); check("subthreshold edge pull never returns", !ui(next::isFinishing));
        long t = SystemClock.uptimeMillis(); point(next, t, 0, 10 * d, cy); point(next, t, 2, 85 * d, cy);
        check("edge arms beyond 48dp", (Boolean) ui(() -> field(edge(next), "armed")));
        main(() -> edge(next).invalidate()); pause(300);
        record((Boolean) ui(() -> field(edge(next), "armed")), "teardrop remains armed at capture",
                "skin=" + ui(() -> field(edge(next), "skin")) + " pull=" + ui(() -> field(edge(next), "pullDp"))
                        + " progress=" + ui(() -> field(edge(next), "progress")));
        Bitmap direct = ui(() -> {
            View control = edge(next);
            Bitmap image = Bitmap.createBitmap(control.getWidth(), control.getHeight(), Bitmap.Config.ARGB_8888);
            control.draw(new android.graphics.Canvas(image)); return image;
        });
        try (FileOutputStream stream = new FileOutputStream(new File(out, "edge-teardrop-direct.png"))) {
            direct.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        direct.recycle();
        shot("edge-armed-teardrop"); point(next, t, 2, 30 * d, cy); point(next, t, 1, 30 * d, cy); pause(240);
        check("edge pull reversal cancels", !ui(next::isFinishing));
        t = SystemClock.uptimeMillis(); point(next, t, 0, 10 * d, cy); point(next, t, 2, 85 * d, cy); point(next, t, 3, 85 * d, cy); pause(240);
        check("edge ACTION_CANCEL never returns", !ui(next::isFinishing));
        swipe(next, 10 * d, cy, 40 * d, cy + 90 * d, 120); check("edge vertical gesture rejected", !ui(next::isFinishing));
        float before = ((Number) ui(() -> field(edge(next), "centerY"))).floatValue();
        t = SystemClock.uptimeMillis(); point(next, t, 0, 10 * d, before); pause(470); point(next, t, 2, 10 * d, before + 140 * d); point(next, t, 1, 10 * d, before + 140 * d); pause(240);
        float moved = ((Number) ui(() -> field(edge(next), "centerY"))).floatValue();
        check("400ms edge long press moves height", moved > before + 100 * d && !ui(next::isFinishing));
        int edgeNorm = ((Number) pref("BACK_EDGE_POSITION_Y")).intValue();
        check("edge height independent from ball", ((Number) pref("BACK_POSITION_Y")).intValue() == yNorm);
        t = SystemClock.uptimeMillis(); point(next, t, 0, 10 * d, moved); pause(470); point(next, t, 2, 10 * d, moved - 180 * d); point(next, t, 3, 10 * d, moved - 180 * d); pause(240);
        check("cancelled edge reposition restores height", Math.abs(((Number) ui(() -> field(edge(next), "centerY"))).floatValue() - moved) < 1);
        check("cancelled reposition does not persist", ((Number) pref("BACK_EDGE_POSITION_Y")).intValue() == edgeNorm);
        config(next, "setEdgeSkin", "capsule");
        check("capsule skin applies immediately", "capsule".equals(ui(() -> field(edge(next), "skin"))));
        t = SystemClock.uptimeMillis(); point(next, t, 0, 10 * d, moved); point(next, t, 2, 90 * d, moved); pause(150); shot("edge-armed-capsule"); point(next, t, 1, 90 * d, moved); pause(500);
        check("armed edge release navigates one level", ui(next::isFinishing));
        Activity right = openAdvanced(); config(right, "setSide", "right");
        check("edge height survives page change", ((Number) pref("BACK_EDGE_POSITION_Y")).intValue() == edgeNorm);
        float ry = ((Number) ui(() -> field(edge(right), "centerY"))).floatValue();
        float width = ui(() -> right.getWindow().getDecorView().getWidth());
        swipe(right, width - 10 * d, ry, width - 90 * d, ry, 150); pause(400);
        check("right edge pull navigates", ui(right::isFinishing));
        Activity both = openAdvanced(); config(both, "setSide", "both"); config(both, "setMode", "both");
        check("both edge handles enabled", "both".equals(ui(() -> field(edge(both), "side"))) && ui(() -> edge(both).isShown()));
        tree(both, "advanced-dual"); shot("advanced-dual");
        config(both, "setMode", "floating"); config(both, "setSide", "left"); config(both, "setHideSeconds", 4); config(both, "setLarge", false);
        advanced = both;
    }
    private void dialogPoint(Dialog dialog, long down, int action, float x, float y) throws Exception {
        main(() -> { int[] p = new int[2]; dialog.getWindow().getDecorView().getLocationOnScreen(p); MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); e.offsetLocation(-p[0], -p[1]); dialog.dispatchTouchEvent(e); e.recycle(); });
    }
    private void dialogTap(Dialog dialog, float x, float y) throws Exception {
        long t = SystemClock.uptimeMillis(); dialogPoint(dialog, t, 0, x, y); SystemClock.sleep(40); dialogPoint(dialog, t, 1, x, y); pause(150);
    }
    private void dialogChecks() throws Exception {
        Activity a = advanced != null && !advanced.isFinishing() ? advanced : openAdvanced();
        Object saved = pref("API_URL");
        Dialog api = ui(() -> (Dialog) type("com.github.tvbox.osc.ui.dialog.ApiDialog").getConstructor(Context.class).newInstance(a));
        main(() -> { ((EditText) api.findViewById(id("input"))).setText("http://127.0.0.1:9978/car7-must-not-save.json"); api.show(); }); pause(350);
        View card = dialogCard(api); Rect r = rect(card);
        dialogTap(api, r.left + 8, r.top + 8); check("API inside panel tap does not cancel", ui(api::isShowing));
        float ox = Math.max(1, r.left - 25), oy = Math.max(1, r.top - 25);
        long t = SystemClock.uptimeMillis(); dialogPoint(api, t, 0, ox, oy); dialogPoint(api, t, 2, ox + 90, oy); dialogPoint(api, t, 1, ox + 90, oy); pause(100);
        check("outside drag does not cancel", ui(api::isShowing));
        if (!api.isShowing()) main(api::show);
        t = SystemClock.uptimeMillis(); dialogPoint(api, t, 0, ox, oy); dialogPoint(api, t, 2, ox + 90, oy); dialogPoint(api, t, 2, ox, oy); dialogPoint(api, t, 1, ox, oy); pause(100);
        check("outside drag returning to origin does not cancel", ui(api::isShowing));
        if (!api.isShowing()) main(api::show);
        t = SystemClock.uptimeMillis(); dialogPoint(api, t, 0, ox, oy); dialogPoint(api, t, 3, ox, oy); pause(100);
        check("outside CANCEL keeps dialog", ui(api::isShowing));
        shot("api-dialog"); dialogTap(api, ox, oy);
        check("outside tap cancels API dialog", !ui(api::isShowing));
        check("API cancellation does not save input", saved == null ? pref("API_URL") == null : saved.equals(pref("API_URL")));
        final int[] calls = {0, 0, 0};
        Dialog tip = ui(() -> (Dialog) type("com.github.tvbox.osc.ui.dialog.BaseDialog").getConstructor(Context.class).newInstance(a));
        main(() -> {
            int layout = a.getResources().getIdentifier("dialog_tip", "layout", a.getPackageName());
            tip.setContentView(layout);
            ((TextView) tip.findViewById(id("tipInfo"))).setText("Car7 cancel assertion");
            tip.findViewById(id("leftBtn")).setOnClickListener(v -> calls[0]++);
            tip.findViewById(id("rightBtn")).setOnClickListener(v -> calls[1]++);
            tip.setOnCancelListener(dialog -> calls[2]++);
        });
        main(tip::show); pause(200);
        Rect tr = rect(dialogCard(tip));
        dialogTap(tip, Math.max(1, tr.left - 25), Math.max(1, tr.top - 25));
        check("confirmation outside cancel runs cancel only", calls[0] == 0 && calls[1] == 0 && calls[2] == 1 && !ui(tip::isShowing));
        main(tip::show); pause(150); sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); pause(200);
        check("system Back cancels rather than confirms", calls[0] == 0 && calls[1] == 0 && calls[2] == 2 && !ui(tip::isShowing));
    }
    private View dialogCard(Dialog dialog) throws Exception {
        return ui(() -> {
            ViewGroup content = dialog.getWindow().findViewById(android.R.id.content);
            View first = content.getChildAt(0);
            return first instanceof FrameLayout && ((FrameLayout) first).getChildCount() == 1 ? ((FrameLayout) first).getChildAt(0) : first;
        });
    }
    private String apkHash() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(getTargetContext().getApplicationInfo().sourceDir)) { byte[] b = new byte[65536]; int n; while ((n = input.read(b)) > 0) digest.update(b, 0, n); }
        StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.US, "%02x", b & 255)); return hex.toString();
    }
    private void write(String name, String body) throws Exception { try (FileOutputStream f = new FileOutputStream(new File(out, name))) { f.write(body.getBytes("UTF-8")); } }
}
