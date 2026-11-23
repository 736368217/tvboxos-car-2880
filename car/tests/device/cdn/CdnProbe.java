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
import java.lang.reflect.InvocationTargetException;
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
            // App-stack reads only: the product query/Channel contract is covered by host tests;
            // this probe must not depend on reflection into R8-obfuscated constructors.
            // Same OkHttp stack that loads subscription/config files, called through the target
            // app's own classloader: proof the built-in acceleration is active in-app.
            Class<?> okHttp=getTargetContext().getClassLoader().loadClass("com.github.catvod.net.OkHttp");
            Method string=okHttp.getMethod("string",String.class);
            String rawBody=(String)string.invoke(null,"https://raw.githubusercontent.com/octocat/Hello-World/master/README");
            event("app_stack_github_raw",rawBody!=null&&rawBody.trim().equals("Hello World!"),
                  "bytes="+(rawBody==null?0:rawBody.length())+" first_line="+(rawBody==null?"":rawBody.trim()));
            String checksum=(String)string.invoke(null,"https://github.com/736368217/tvboxos-car-2880/releases/download/v3.1.0-car.7-r93/TVBox-Original-Feature-3.1.0-car.7-r93.apk.sha256");
            event("app_stack_release_asset",checksum!=null&&checksum.trim().matches("[0-9a-fA-F]{64}.*"),
                  "bytes="+(checksum==null?0:checksum.length()));
            ok=rawBody!=null&&rawBody.trim().equals("Hello World!")
                    &&checksum!=null&&checksum.trim().matches("[0-9a-fA-F]{64}.*");
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
