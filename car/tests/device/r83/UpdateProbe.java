package com.tvbox.car.updatetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Read-only probe of the installed updater's real check/download path (R8 names). */
public final class UpdateProbe extends Instrumentation {
    private Bundle opts;

    @Override public void onCreate(Bundle args) { opts = args == null ? new Bundle() : args; start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context ctx = getTargetContext();
            Class<?> update = ctx.getClassLoader().loadClass("androidx.base.n1");
            Method check = null;
            for (Method m : update.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == Context.class && p[2].isInterface()) { check = m; break; }
            }
            if (check == null) throw new NoSuchMethodException("check");
            check.setAccessible(true);
            Class<?> callbackClass = check.getParameterTypes()[2];
            Class<?> infoClass = check.getParameterTypes()[1];

            final Object[] info = {null};
            final String[] error = {null};
            final CountDownLatch latch = new CountDownLatch(1);
            Object callback = Proxy.newProxyInstance(update.getClassLoader(), new Class[]{callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onResult")) {
                            info[0] = args[0];
                            error[0] = (String) args[1];
                            latch.countDown();
                        }
                        return null;
                    });
            check.invoke(null, ctx, true, callback);
            if (!latch.await(90, TimeUnit.SECONDS)) throw new AssertionError("check timeout");
            if (error[0] != null) throw new AssertionError("check error: " + error[0]);

            String tag = (String) field(info[0], "tag");
            String channel = (String) field(info[0], "channelLabel");
            String apkUrl = (String) field(info[0], "apkUrl");
            String raw = (String) field(info[0], "apkUrlRaw");
            long size = (Long) field(info[0], "apkSize");
            log("CHECK tag=" + tag + " channel=" + channel + " size=" + size);
            log("APK_URL=" + apkUrl);
            log("RAW_URL=" + raw);
            if (!opts.getBoolean("download", false)) { finish(Activity.RESULT_OK, result); return; }

            Method download = null;
            for (Method m : update.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == Context.class && p[1] == infoClass && p[2].isInterface()) { download = m; break; }
            }
            if (download == null) throw new NoSuchMethodException("download");
            download.setAccessible(true);
            Class<?> dlClass = download.getParameterTypes()[2];

            final long[] last = {0};
            final long[] total = {0};
            final String[] done = {null};
            final CountDownLatch finished = new CountDownLatch(1);
            Object dl = Proxy.newProxyInstance(update.getClassLoader(), new Class[]{dlClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onProgress")) {
                            last[0] = (Long) args[0];
                            total[0] = (Long) args[1];
                            log("PROGRESS " + last[0] + "/" + total[0]);
                        } else if (method.getName().equals("onDone")) {
                            done[0] = "done:" + args[0];
                            finished.countDown();
                        } else if (method.getName().equals("onError")) {
                            done[0] = "error:" + args[0];
                            finished.countDown();
                        }
                        return null;
                    });
            download.invoke(null, ctx, info[0], dl);
            if (!finished.await(240, TimeUnit.SECONDS)) {
                throw new AssertionError("download timeout at " + last[0] + "/" + total[0]);
            }
            log("DOWNLOAD_RESULT=" + done[0]);
            result.putString("result", "CHECK " + tag + " channel=" + channel + " size=" + size + " download=" + done[0]);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable t) {
            StringWriter trace = new StringWriter();
            t.printStackTrace(new PrintWriter(trace));
            log("FAIL " + trace);
            result.putString("result", "FAIL " + t);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private Object field(Object o, String name) throws Exception {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private void log(String text) {
        android.util.Log.i("UpdateProbe", text);
        Bundle b = new Bundle();
        b.putString("stream", text + "\n");
        sendStatus(0, b);
    }
}
