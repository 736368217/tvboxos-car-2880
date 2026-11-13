package com.tvbox.car.updatetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.List;

/**
 * Resolve update hosts through the app's own DoH DNS layer.
 *
 * <p>R8 inlines {@code PlainHttp.systemDnsWithDohFallback()} and renames everything, so this
 * probe constructs the obfuscated inner {@code okhttp3.Dns} implementations directly. Pass the
 * obfuscated outer name with {@code -e plain_class androidx.base.c20}; {@code $b} is the
 * DoH-fallback DNS (see mapping.txt), {@code $d} is the plain timeout DNS used as a control.</p>
 */
public final class DnsProbe extends Instrumentation {
    private static final String[] HOSTS = {
            "github.com", "ghfast.top", "gh-proxy.com", "ghproxy.net", "api.github.com"
    };
    private Bundle opts;

    @Override public void onCreate(Bundle args) { opts = args == null ? new Bundle() : args; start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        StringBuilder report = new StringBuilder();
        try {
            String outer = opts.getString("plain_class", "androidx.base.c20");
            String inner = opts.getString("dns_inner", "b");
            ClassLoader loader = getTargetContext().getClassLoader();
            Class<?> dnsClass = loader.loadClass(outer + "$" + inner);
            Constructor<?> ctor = dnsClass.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            Class<?> dnsInterface = loader.loadClass("okhttp3.Dns");
            Object system = dnsInterface.getField("SYSTEM").get(null);
            Object dnsObj = ctor.getParameterCount() == 1
                    ? ctor.newInstance(system)
                    : ctor.newInstance(system, 6000L);
            log("doh class=" + dnsClass.getName() + " ctor=" + ctor.toGenericString());

            Method lookup = dnsClass.getMethod("lookup", String.class);
            lookup.setAccessible(true);
            for (String host : HOSTS) {
                long started = System.currentTimeMillis();
                String line;
                try {
                    @SuppressWarnings("unchecked")
                    List<InetAddress> list = (List<InetAddress>) lookup.invoke(dnsObj, host);
                    line = host + " -> " + list + " ms=" + (System.currentTimeMillis() - started);
                } catch (Throwable t) {
                    Throwable cause = t.getCause() == null ? t : t.getCause();
                    line = host + " ERROR " + cause + " ms=" + (System.currentTimeMillis() - started);
                }
                log(line);
                report.append(line).append('\n');
            }
            result.putString("report", report.toString());
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
