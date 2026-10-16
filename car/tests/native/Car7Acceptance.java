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
                }
                Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                home = startActivitySync(launch);
                pause(15000);
                if (arguments != null && "subscription".equals(arguments.getString("mode"))) {
                    section("subscription", this::subscriptionChecks);
                } else if (arguments != null && "playback".equals(arguments.getString("mode"))) {
                    section("playback", this::playbackChecks);
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
    private void subscriptionChecks() throws Exception {
        Object api = call(type("com.github.tvbox.osc.api.ApiConfig"), "get");
        List<?> sources = (List<?>) call(api, "getSourceBeanList");
        record(sources.size() >= 100, "subscription parsed site count", "unique_sites=" + sources.size());
        JSONArray observations = new JSONArray();
        for (String key : arguments.getString("sites", "热播影视,枫叶影视,爱动漫").split(",")) {
            JSONObject entry = new JSONObject(); entry.put("key", key); observations.put(entry);
            write("subscription-results.json", observations.toString(2));
            long started = SystemClock.elapsedRealtime();
            try {
                Object source = call(api, "getSource", key);
                if (source == null) throw new IllegalStateException("missing source");
                String sourceApi = String.valueOf(call(source, "getApi"));
                entry.put("runtime", sourceApi.contains(".js") ? "QuickJS" : "JAR");
                Object spider = bounded(() -> call(api, "getCSP", source), 45);
                JSONObject first = new JSONObject(bounded(() -> String.valueOf(call(spider, "homeContent", true)), 30));
                JSONArray categories = first.optJSONArray("class");
                entry.put("categories", categories == null ? 0 : categories.length());
                check("subscription-" + key + "-home", categories != null && categories.length() > 0);
                if (categories != null && categories.length() > 0) {
                    String tid = categories.getJSONObject(0).getString("type_id");
                    JSONObject category = new JSONObject(bounded(() -> String.valueOf(call(spider, "categoryContent", tid, "1", true, new java.util.HashMap<String,String>())), 30));
                    JSONArray videos = category.optJSONArray("list");
                    entry.put("category_items", videos == null ? 0 : videos.length());
                    check("subscription-" + key + "-category", videos != null && videos.length() > 0);
                    if (videos != null && videos.length() > 0) {
                        List<String> ids = new ArrayList<>(); ids.add(videos.getJSONObject(0).getString("vod_id"));
                        JSONObject detail = new JSONObject(bounded(() -> String.valueOf(call(spider, "detailContent", ids)), 30));
                        JSONArray details = detail.optJSONArray("list");
                        entry.put("detail_items", details == null ? 0 : details.length());
                        check("subscription-" + key + "-detail", details != null && details.length() > 0);
                    }
                }
                JSONObject search = new JSONObject(bounded(() -> String.valueOf(call(spider, "searchContent", "潜伏", false)), 30));
                JSONArray found = search.optJSONArray("list");
                entry.put("search_items", found == null ? 0 : found.length());
                check("subscription-" + key + "-search", found != null && found.length() > 0);
            } catch (Throwable error) {
                entry.put("error_class", error.getClass().getSimpleName());
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
                main(() -> call(video, "startFullScreen")); SystemClock.sleep(1500);
                check(name + " fullscreen active", Boolean.TRUE.equals(ui(() -> call(video, "isFullScreen"))));
                progressing(video, name + " fullscreen", observations); shot(name + "-fullscreen");
                main(() -> call(video, "stopFullScreen")); SystemClock.sleep(1200);
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
    private void shot(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new IllegalStateException("empty screenshot");
        try (FileOutputStream f = new FileOutputStream(new File(out, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, f); }
        image.recycle();
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
