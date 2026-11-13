package com.tvbox.car.updatetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import java.lang.reflect.Method;

/**
 * Exercise r83's real candidate ordering + probe from the shipped APK.
 *
 * <p>{@code orderByProbe} survives R8 as {@code androidx.base.n1.g(String[])}; the per-candidate
 * probe is inlined but reachable through it. This proves which URL the copy path would hand the
 * user, using the app's own DoH-backed OkHttp client rather than the host network.</p>
 */
public final class CopyProbe extends Instrumentation {
    private static final String RAW =
            "https://github.com/736368217/tvboxos-car-2880/releases/download/"
                    + "v3.1.0-car.7-r83/TVBox-Original-Feature-3.1.0-car.7-r83.apk";

    @Override public void onCreate(Bundle args) { start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String[] candidates = {
                    "https://ghfast.top/" + RAW,
                    "https://gh-proxy.com/" + RAW,
                    "https://ghproxy.net/" + RAW,
                    RAW
            };
            Class<?> update = getTargetContext().getClassLoader().loadClass("androidx.base.n1");
            Method order = null;
            for (Method m : update.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == String[].class
                        && m.getReturnType() == String[].class) {
                    order = m;
                    break;
                }
            }
            if (order == null) throw new NoSuchMethodException("orderByProbe");
            order.setAccessible(true);
            log("orderByProbe=" + order.getName());
            long started = System.currentTimeMillis();
            String[] ordered = (String[]) order.invoke(null, (Object) candidates);
            log("probe_ms=" + (System.currentTimeMillis() - started));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ordered.length; i++) {
                String line = "COPY_ORDER[" + i + "]=" + ordered[i];
                log(line);
                sb.append(line).append('\n');
            }
            result.putString("report", sb.toString());
            finish(Activity.RESULT_OK, result);
        } catch (Throwable t) {
            log("FAIL " + t);
            result.putString("error", String.valueOf(t));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void log(String text) {
        android.util.Log.i("UpdateProbe", text);
        Bundle b = new Bundle();
        b.putString("stream", text + "\n");
        sendStatus(0, b);
    }
}
