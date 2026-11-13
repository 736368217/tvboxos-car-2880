package com.tvbox.car.updatetest;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Probe which update download URLs are reachable from this device network. */
public final class UrlProbe extends Instrumentation {
    private static final String RAW = "https://github.com/736368217/tvboxos-car-2880/releases/download/v3.1.0-car.7-r82/TVBox-Original-Feature-3.1.0-car.7-r82.apk";
    private static final String[] PREFIXES = {"", "https://ghfast.top/", "https://gh-proxy.com/", "https://ghproxy.net/"};

    @Override public void onCreate(Bundle args) { start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        final StringBuilder report = new StringBuilder();
        try {
            final CountDownLatch done = new CountDownLatch(PREFIXES.length);
            for (String prefix : PREFIXES) {
                final String url = prefix + RAW;
                final String label = prefix.isEmpty() ? "direct" : prefix;
                new Thread(() -> {
                    long started = System.currentTimeMillis();
                    String line;
                    HttpURLConnection c = null;
                    try {
                        c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        c.setRequestProperty("Range", "bytes=0-1023");
                        c.setRequestProperty("User-Agent", "TVBox-Car/probe");
                        c.setInstanceFollowRedirects(true);
                        int code = c.getResponseCode();
                        byte[] head = new byte[2];
                        int read;
                        try (InputStream in = c.getInputStream()) { read = in.read(head); }
                        line = label + " code=" + code + " bytes=" + read + " magic="
                                + String.format("%02x%02x", head[0], head[1])
                                + " ms=" + (System.currentTimeMillis() - started);
                    } catch (Throwable t) {
                        line = label + " ERROR " + t.getClass().getSimpleName() + " " + t.getMessage()
                                + " ms=" + (System.currentTimeMillis() - started);
                    } finally {
                        if (c != null) c.disconnect();
                    }
                    synchronized (report) { report.append(line).append('\n'); }
                    android.util.Log.i("UpdateProbe", line);
                    done.countDown();
                }).start();
            }
            done.await(30, TimeUnit.SECONDS);
            result.putString("report", report.toString());
            finish(Activity.RESULT_OK, result);
        } catch (Throwable t) {
            result.putString("error", String.valueOf(t));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
