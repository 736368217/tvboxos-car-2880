package com.tvbox.car.r82test;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Separate same-certificate native acceptance APK. No hooks in the delivered app. */
public final class Acceptance extends Instrumentation {
    private Bundle options;
    private volatile Activity current;
    private File out;
    private int checks;
    private Dialog settings;
    private final StringBuilder report = new StringBuilder();
    interface Check { boolean get() throws Exception; }
    interface Work { void run() throws Exception; }
    @Override public void onCreate(Bundle args) { options = args == null ? new Bundle() : args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            out = new File(getTargetContext().getExternalFilesDir(null), "r82-" + options.getString("label", "test"));
            out.mkdirs();
            Application app = (Application) getTargetContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityResumed(Activity a) { current = a; }
                public void onActivityCreated(Activity a, Bundle b) { }
                public void onActivityStarted(Activity a) { }
                public void onActivityPaused(Activity a) { if(current == a) current = null; }
                public void onActivityStopped(Activity a) { }
                public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                public void onActivityDestroyed(Activity a) { }
            });
            main(() -> {
                if (!options.getString("scenario", "").equals("restore") && !options.getString("scenario", "").equals("no_restore")) {
                    getTargetContext().getSharedPreferences("car_playback_session",0).edit().putBoolean("active",false).putBoolean("user_exit",true).commit();
                    getTargetContext().getSharedPreferences("car_playback_snapshot",0).edit().putBoolean("valid",false).commit();
                }
                pref("api_url", "https://clun.top/box.json");
                pref("play_type", 2);
                pref("show_preview", true);
                pref("audio_usage_media", options.getBoolean("usage", true));
                pref("inapp_back_mode", "edge");
                pref("inapp_back_mode_explicit", true);
                pref("inapp_back_side", "left");
                pref("hide_seconds", 0);
            });
            final long expectedPosition = getTargetContext().getSharedPreferences("car_playback_session",0).getLong("position",0);
            Activity home = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.github.tvbox.osc.ui.activity.HomeActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if (options.getString("scenario", "").equals("restore")) {
                await("cold_restored_detail", () -> current != null && current.getClass().getSimpleName().equals("DetailActivity") && field(current,"playFragment")!=null,90000);
                final Activity restored=current;
                note("cold_intent_full="+restored.getIntent().getBooleanExtra("restore_fullscreen",false)+" has_extra="+restored.getIntent().hasExtra("restore_fullscreen")+" actual="+field(restored,"fullWindows"));
                assertTrue("cold_restored_fullscreen", Boolean.TRUE.equals(field(restored,"fullWindows")));
                await("cold_position_restored", () -> videoView(restored)!=null
                        && ((Number)call(videoView(restored),"getCurrentPosition")).longValue()>=Math.max(3000,expectedPosition-1500),30000);
                note("cold_position expected="+expectedPosition+" actual="+call(videoView(restored),"getCurrentPosition"));
                shot("11-cold-restored");
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(600);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                await("cold_exit_home", () -> current!=null && current.getClass().getSimpleName().equals("HomeActivity") && !active(),10000);
                note("COLD_RESTORE_PASS");result.putString("result","PASS cold_restore");writeReport();finish(Activity.RESULT_OK,result);return;
            }
            if (options.getString("scenario", "").equals("no_restore")) {
                await("cold_home", () -> current!=null && current.getClass().getSimpleName().equals("HomeActivity"),10000);
                SystemClock.sleep(6000);
                assertTrue("cold_explicit_exit_stays_home", current!=null && current.getClass().getSimpleName().equals("HomeActivity") && !active());
                shot("12-cold-no-restore");note("COLD_NO_RESTORE_PASS");result.putString("result","PASS no_restore");writeReport();finish(Activity.RESULT_OK,result);return;
            }
            await("home_resumed", () -> current != null && current.getClass().getSimpleName().equals("HomeActivity"), 10000);
            Object config = call(type("com.github.tvbox.osc.api.ApiConfig"), "get");
            await("subscription_sources", () -> ((List<?>)call(config, "getSourceBeanList")).size() > 2, 90000);
            note("sources=" + ((List<?>)call(config, "getSourceBeanList")).size());
            SystemClock.sleep(5000);
            shot("01-home");
            String scenario = options.getString("scenario", "suite");
            if (scenario.equals("setup")) {
                note("SETUP_PASS");
            } else if (scenario.equals("visual")) {
                showSettings(home);
            } else {
                Object selected = call(config, "getHomeSourceBean");
                for (Object s : (List<?>)call(config, "getSourceBeanList")) {
                    String key = (String)call(s, "getKey");
                    note("site=" + key);
                    if (key.equals(options.getString("source", ""))) selected = s;
                }
                final Object source = selected;
                String sourceKey = (String)call(source, "getKey");
                Object spider = call(config, "getCSP", source);
                JSONObject content = new JSONObject((String)call(spider, "homeContent", true));
                JSONArray categories = content.optJSONArray("class");
                assertTrue("real_categories", categories != null && categories.length() > 0);
                JSONArray videos = content.optJSONArray("list");
                if (videos == null || videos.length() == 0) {
                    videos = new JSONObject((String)call(spider, "homeVideoContent")).optJSONArray("list");
                }
                assertTrue("real_videos", videos != null && videos.length() > 0);
                JSONObject video = videos.getJSONObject(0);
                note("source=" + sourceKey + " categories=" + categories.length() + " title=" + video.optString("vod_name"));
                if (options.getString("source", "").equals("fixture")) {
                    sourceKey = "push_agent";
                    video = new JSONObject().put("vod_id", "file:///sdcard/Download/r82-quiet-test.mp4")
                            .put("vod_name", "PCM playback acceptance").put("vod_pic", "");
                    note("playback_fixture=local_generated_pcm");
                }
                Intent detailIntent = new Intent().setClassName(getTargetContext(), "com.github.tvbox.osc.ui.activity.DetailActivity")
                        .putExtra("id", video.getString("vod_id")).putExtra("sourceKey", sourceKey)
                        .putExtra("title", video.optString("vod_name")).putExtra("picture", video.optString("vod_pic"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivitySync(detailIntent);
                await("detail_loaded", () -> current != null && current.getClass().getSimpleName().equals("DetailActivity")
                        && field(current, "playFragment") != null
                        && field(field(current, "playFragment"), "mVodInfo") != null, 60000);
                final Activity detail = current;
                await("video_initialized", () -> videoView(detail) != null, 10000);
                if (options.getString("source", "").equals("fixture")) {
                    SystemClock.sleep(6500);
                    main(() -> {
                        settings = (Dialog)type("com.github.tvbox.osc.ui.dialog.AdvancedSettingsDialog").getConstructor(Context.class,Runnable.class).newInstance(detail,null);
                        settings.show();
                    });
                    SystemClock.sleep(500);
                    String status = allText(settings.getWindow().getDecorView());
                    note("runtime_compensation=" + status);
                    assertTrue("runtime_pcm_locked", status.contains("已锁定"));
                    shot("02a-sound-runtime");
                    main(() -> settings.dismiss());
                }
                await("actual_playing", () -> Boolean.TRUE.equals(call(videoView(detail), "isPlaying")), 60000);
                long p1 = ((Number)call(videoView(detail), "getCurrentPosition")).longValue();
                SystemClock.sleep(2500);
                long p2 = ((Number)call(videoView(detail), "getCurrentPosition")).longValue();
                assertTrue("position_advances", p2 > p1 + 500);
                note("position=" + p1 + "->" + p2);
                final int[] previewPoint=new int[2];
                main(() -> {
                    int id=getTargetContext().getResources().getIdentifier("previewPlayer", "id", getTargetContext().getPackageName());
                    View preview=detail.findViewById(id);
                    preview.getLocationOnScreen(previewPoint);
                    previewPoint[0]+=preview.getWidth()/2;previewPoint[1]+=preview.getHeight()/2;
                });
                long tapTime=SystemClock.uptimeMillis();
                pointer(tapTime,MotionEvent.ACTION_DOWN,previewPoint[0],previewPoint[1]);
                pointer(tapTime,MotionEvent.ACTION_UP,previewPoint[0],previewPoint[1]);
                await("entered_fullscreen", () -> Boolean.TRUE.equals(field(detail,"fullWindows")),10000);
                shot("02-fullscreen");
                getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                SystemClock.sleep(1800);
                assertTrue("background_session_active", active());
                if (scenario.equals("arm")) {
                    long saved=getTargetContext().getSharedPreferences("car_playback_session",0).getLong("position",0);
                    assertTrue("cold_saved_position",saved>3000);
                    boolean savedFull=getTargetContext().getSharedPreferences("car_playback_session",0).getBoolean("fullscreen",false);
                    note("cold_saved_fullscreen="+savedFull);
                    assertTrue("cold_saved_fullscreen",savedFull);
                    note("COLD_ARMED position="+saved);result.putString("result","PASS armed");writeReport();finish(Activity.RESULT_OK,result);return;
                }
                Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getTargetContext().startActivity(launch);
                await("foreground_detail", () -> current != null && current.getClass().getSimpleName().equals("DetailActivity"), 15000);
                final Activity returned = current;
                shot("03-background-return");
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                SystemClock.sleep(1000);
                boolean collapsed = !((Boolean)field(returned, "fullWindows"));
                note("first_back_collapsed=" + collapsed);
                shot("04-back-detail");
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                SystemClock.sleep(3500);
                boolean homeAfter = current != null && current.getClass().getSimpleName().equals("HomeActivity");
                boolean stoppedSession = !active();
                note("second_back_home=" + homeAfter + " session_inactive=" + stoppedSession);
                shot("05-back-home");
                if (scenario.equals("baseline")) {
                    note("BASELINE_OBSERVED");
                } else {
                    assertTrue("back_one_collapses", collapsed);
                    assertTrue("back_two_home", homeAfter);
                    assertTrue("explicit_exit_clears_session", stoppedSession);
                    SystemClock.sleep(5000);
                    assertTrue("no_delayed_resurrection", current != null && current.getClass().getSimpleName().equals("HomeActivity") && !active());
                    note("NATIVE_SUITE_PASS checks=" + checks);
                    showSettings(current);
                    backTouches();
                }
            }
            result.putString("result", "PASS checks=" + checks);
            writeReport();
            finish(Activity.RESULT_OK, result);
        } catch (Throwable t) {
            Throwable actual = t instanceof InvocationTargetException ? ((InvocationTargetException)t).getTargetException() : t;
            StringWriter trace = new StringWriter(); actual.printStackTrace(new PrintWriter(trace));
            note("FAIL " + trace);
            try { shot("99-failure"); writeReport(); } catch (Throwable ignored) { }
            result.putString("result", "FAIL " + actual);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void backTouches() throws Exception {
        startActivitySync(new Intent().setClassName(getTargetContext(), "com.github.tvbox.osc.ui.activity.HistoryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await("history_for_edge", () -> current != null && current.getClass().getSimpleName().equals("HistoryActivity"),10000);
        long down=SystemClock.uptimeMillis();
        pointer(down,MotionEvent.ACTION_DOWN,2,810);
        pointer(down,MotionEvent.ACTION_MOVE,140,810);
        SystemClock.sleep(200);shot("09-edge-pull");
        pointer(down,MotionEvent.ACTION_UP,140,810);
        await("edge_returns_home", () -> current != null && current.getClass().getSimpleName().equals("HomeActivity"),10000);
        main(() -> pref("inapp_back_mode", "bar"));
        startActivitySync(new Intent().setClassName(getTargetContext(), "com.github.tvbox.osc.ui.activity.HistoryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await("history_for_bar", () -> current != null && current.getClass().getSimpleName().equals("HistoryActivity"),10000);
        shot("10-back-bar");
        down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,24,810);pointer(down,MotionEvent.ACTION_UP,24,810);
        await("bar_returns_home", () -> current != null && current.getClass().getSimpleName().equals("HomeActivity"),10000);
    }
    private void pointer(long down,int action,float x,float y) {
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        sendPointerSync(event);event.recycle();
    }
    private Object videoView(Activity a) throws Exception { return field(field(a, "playFragment"), "mVideoView"); }
    private boolean active() {
        android.content.SharedPreferences p = getTargetContext().getSharedPreferences("car_playback_session", 0);
        return p.getBoolean("active", false) && !p.getBoolean("user_exit", false);
    }
    private void showSettings(Activity a) throws Exception {
        main(() -> {
            settings = (Dialog)type("com.github.tvbox.osc.ui.dialog.AdvancedSettingsDialog").getConstructor(Context.class, Runnable.class).newInstance(a, null);
            settings.show();
        });
        SystemClock.sleep(500); shot("06-settings-sound");
        main(() -> findText(settings.getWindow().getDecorView(), "返回操作").performClick());
        SystemClock.sleep(500); shot("07-settings-back");
        main(() -> findText(settings.getWindow().getDecorView(), "其他").performClick());
        SystemClock.sleep(500); shot("08-settings-other");
        main(() -> settings.dismiss());
    }
    private String allText(View view) {
        StringBuilder value=new StringBuilder();
        if(view instanceof TextView)value.append(((TextView)view).getText()).append(' ');
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)value.append(allText(((ViewGroup)view).getChildAt(i)));
        return value.toString();
    }
    private void checkTextLayout(View view) {
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView) {
            TextView t=(TextView)view;android.text.Layout l=t.getLayout();
            if(l!=null && t.getText().length()>0) {
                checkLayout(l.getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom()+1,"text height: "+t.getText());
                for(int i=0;i<l.getLineCount();i++)checkLayout(l.getEllipsisCount(i)==0 && l.getLineWidth(i)<=l.getWidth()+1,"text width: "+t.getText());
            }
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)checkTextLayout(((ViewGroup)view).getChildAt(i));
    }
    private void checkLayout(boolean good,String name){if(!good)throw new AssertionError(name);}
    private View findText(View v, String text) {
        if (v instanceof TextView && text.equals(((TextView)v).getText().toString())) return v;
        if (v instanceof ViewGroup) for (int i=0; i<((ViewGroup)v).getChildCount(); i++) {
            View got = findText(((ViewGroup)v).getChildAt(i), text); if(got != null) return got;
        }
        return null;
    }
    private void await(String name, Check c, long timeout) throws Exception {
        long end = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < end) { if (c.get()) { assertTrue(name, true); return; } SystemClock.sleep(250); }
        throw new AssertionError("timeout " + name);
    }
    private void assertTrue(String name, boolean yes) { if(!yes) throw new AssertionError(name); checks++; note("OK " + name); }
    private void note(String s) { report.append(s).append('\n'); android.util.Log.i("CarAcceptance",s); try{writeReport();}catch(Exception ignored){} Bundle b = new Bundle(); b.putString("stream", s + "\n"); sendStatus(0,b); }
    private void writeReport() throws IOException { try(FileOutputStream f=new FileOutputStream(new File(out,"report.txt"))) { f.write(report.toString().getBytes("UTF-8")); } }
    private void shot(String name) throws Exception {
        if(settings!=null && settings.isShowing())main(() -> checkTextLayout(settings.getWindow().getDecorView()));
        waitForIdleSync(); Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap==null) throw new AssertionError("screenshot unavailable");
        assertTrue("screen_"+name,bitmap.getWidth()==2880 && bitmap.getHeight()==1620);
        try(FileOutputStream f=new FileOutputStream(new File(out,name+".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,f); }
        bitmap.recycle();
    }
    private void main(Work work) throws Exception {
        final Throwable[] error={null};
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {try{work.run();}catch(Throwable t){error[0]=t;}finally{done.countDown();}});
        if(!done.await(15, java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("main_thread_timeout");
        if(error[0]!=null)throw new Exception(error[0]);
    }
    private Class<?> type(String n) throws ClassNotFoundException { return getTargetContext().getClassLoader().loadClass(Names.klass(n)); }
    private void pref(String key,Object value) throws Exception { call(type("com.orhanobut.hawk.Hawk"), "put",key,value); }
    private Object field(Object o,String n) throws Exception {
        if(o==null)return null;
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()) for(String mapped:Names.member(c.getName(),n))
            try{Field f=c.getDeclaredField(mapped);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(n);
    }
    private Object call(Object o,String n,Object... args) throws Exception {
        if (Looper.myLooper() != Looper.getMainLooper() && Arrays.asList("isPlaying", "getCurrentPosition", "getMediaPlayer").contains(n)) {
            final Object[] value = {null};
            main(() -> value[0] = call(o, n, args));
            return value[0];
        }
        Class<?> cls=o instanceof Class?(Class<?>)o:o.getClass();
        for(Class<?> c=cls;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()){
            if(!Names.member(c.getName(),n).contains(m.getName())||m.getParameterTypes().length!=args.length)continue;
            Class<?>[] p=m.getParameterTypes(); boolean compatible=true;
            for(int i=0;i<p.length;i++)if(args[i]!=null&&!p[i].isInstance(args[i])&&!p[i].isPrimitive())compatible=false;
            if(!compatible)continue;
            m.setAccessible(true); return m.invoke(o instanceof Class?null:o,args);
        }
        throw new NoSuchMethodException(cls.getName()+"."+n);
    }
}
