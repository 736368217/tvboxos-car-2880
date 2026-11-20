package com.tvbox.car.cdntest;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Same-certificate test APK, built with the target release's exact R8 mapping. */
public final class CdnProbe extends Instrumentation {
    private volatile Activity current;
    private final JSONArray events = new JSONArray();
    private File output;
    private interface Check { boolean get() throws Exception; }

    @Override public void onCreate(Bundle options) { start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean ok = false;
        try {
            output = new File(getTargetContext().getExternalFilesDir(null), "b01-cdn-qa");
            output.mkdirs();
            Application app=(Application)getTargetContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                public void onActivityResumed(Activity a){current=a;}
                public void onActivityPaused(Activity a){if(current==a)current=null;}
                public void onActivityCreated(Activity a,Bundle b){}
                public void onActivityStarted(Activity a){}
                public void onActivityStopped(Activity a){}
                public void onActivitySaveInstanceState(Activity a,Bundle b){}
                public void onActivityDestroyed(Activity a){}
            });
            event("target_version",true,getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName);
            open("HomeActivity"); shot("01-home.png");
            Activity settings=open("SettingActivity");
            final boolean[] clicked={false};
            main(() -> {View v=settings.findViewById(settings.getResources().getIdentifier("llCheckUpdate","id",settings.getPackageName())); clicked[0]=v!=null && v.isEnabled() && v.performClick();});
            event("manual_check_clicked",clicked[0],"native settings view");
            await(() -> hasText("已是最新版本"),45000);
            event("r2_current_version_completes",true,"latest-version dialog visible"); shot("02-update.png");
            ClassLoader loader=getTargetContext().getClassLoader();
            Class<?> update=loader.loadClass(Names.UPDATE);
            Class<?> channel=loader.loadClass(Names.CHANNEL);
            Constructor<?> ctor=channel.getDeclaredConstructor(String.class,String.class,String.class,boolean.class);
            ctor.setAccessible(true);
            Method query=update.getDeclaredMethod(Names.QUERY,channel,String.class,boolean.class); query.setAccessible(true);
            String raw="https://github.com/736368217/tvboxos-car-2880/releases/download/v3.1.0-car.7-r90/TVBox-Original-Feature-3.1.0-car.7-r90.apk";
            int successful=0;
            for(String host:new String[]{"gh-proxy.org","cdn.gh-proxy.org"}) {
                try {
                    String prefix="https://"+host+"/";
                    String api=prefix+"api.github.com/repos/736368217/tvboxos-car-2880/releases/latest";
                    Object info=query.invoke(null,ctor.newInstance(prefix,api,host,false),"3.1.0-car.7-r89",true);
                    String version=(String)field(info,Names.VERSION);
                    String asset=(String)field(info,Names.ASSET);
                    boolean updateAvailable=(Boolean)field(info,Names.HAS_UPDATE);
                    event("metadata_"+host,version.contains("-car.7-r") && updateAvailable,version);
                    event("asset_route_"+host,asset.startsWith(prefix+"github.com/"),asset.split("\\?")[0]);
                    successful++;
                    HttpURLConnection conn=(HttpURLConnection)new URL(asset).openConnection();
                    try {
                        conn.setConnectTimeout(8000); conn.setReadTimeout(12000);
                        conn.setInstanceFollowRedirects(true); conn.setRequestProperty("Range","bytes=0-31");
                        conn.setRequestProperty("User-Agent","TVBox-Car/B01-acceptance");
                        int code=conn.getResponseCode(); byte[] head=new byte[4]; int got=0;
                        try(InputStream stream=conn.getInputStream()){while(got<4){int n=stream.read(head,got,4-got);if(n<0)break;got+=n;}}
                        event("apk_range_"+host,code==206 && got==4 && head[0]==80 && head[1]==75,
                              "http="+code+" contentRange="+conn.getHeaderField("Content-Range")+" zip="+(got==4 && head[0]==80 && head[1]==75));
                    } finally {conn.disconnect();}
                } catch(Throwable e) {event("cdn_error_"+host,false,e.toString());}
            }
            event("cdn_metadata_available",successful>0,"queries="+successful);
            ok=successful>0;
            for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i); if(!e.getBoolean("pass"))ok=false;}
        } catch(Throwable error) {
            try {event("probe_error",false,error.toString());}catch(Exception ignored){}
        }
        try {
            JSONObject report=new JSONObject().put("pass",ok).put("events",events);
            try(FileOutputStream stream=new FileOutputStream(new File(output,"result.json"))){stream.write(report.toString(2).getBytes("UTF-8"));}
            result.putString("result",ok?"B01_CDN_PASS":"B01_CDN_FAILED");
        } catch(Exception error) {result.putString("result","REPORT_FAILED "+error);}
        finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private Object field(Object target,String name)throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void event(String name,boolean pass,String detail)throws Exception {
        events.put(new JSONObject().put("name",name).put("pass",pass).put("detail",detail));
        Bundle status=new Bundle();status.putString("stream",name+"="+pass+" "+detail+"\n");sendStatus(0,status);
    }
    private Activity open(String page)throws Exception {
        getTargetContext().startActivity(new Intent().setClassName(getTargetContext(),"com.github.tvbox.osc.ui.activity."+page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> current!=null && current.getClass().getSimpleName().equals(page),15000);return current;
    }
    private void await(Check check,long ms)throws Exception {long end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(check.get())return;SystemClock.sleep(250);}throw new AssertionError("condition timed out");}
    private boolean hasText(String text){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();return root!=null && !root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private void main(Runnable work)throws Exception {CountDownLatch done=new CountDownLatch(1);final Throwable[] error={null};new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{try{work.run();}catch(Throwable t){error[0]=t;}finally{done.countDown();}});if(!done.await(10,TimeUnit.SECONDS))throw new AssertionError("main timeout");if(error[0]!=null)throw new Exception(error[0]);}
    private void shot(String name)throws Exception {Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("no screenshot");try(FileOutputStream f=new FileOutputStream(new File(output,name))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
}
