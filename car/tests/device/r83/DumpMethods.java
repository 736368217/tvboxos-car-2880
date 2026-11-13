package com.tvbox.car.updatetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Dump the obfuscated PlainHttp inner classes and their constructors (read-only). */
public final class DumpMethods extends Instrumentation {
    @Override public void onCreate(Bundle args) { start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            ClassLoader loader = getTargetContext().getClassLoader();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                String name = "androidx.base.c20" + (i == 0 ? "" : "$" + (char) ('a' + i - 1));
                Class<?> c;
                try {
                    c = loader.loadClass(name);
                } catch (Throwable missing) {
                    sb.append(name).append(" MISSING\n");
                    continue;
                }
                sb.append("== ").append(name).append(" implements=");
                for (Class<?> iface : c.getInterfaces()) sb.append(iface.getName()).append(',');
                sb.append('\n');
                for (Constructor<?> ctor : c.getDeclaredConstructors()) {
                    sb.append("   ctor ").append(ctor.toGenericString()).append('\n');
                }
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("lookup")) {
                        sb.append("   lookup ").append(m.toGenericString()).append('\n');
                    }
                }
            }
            log(sb.toString());
            result.putString("dump", sb.toString());
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
