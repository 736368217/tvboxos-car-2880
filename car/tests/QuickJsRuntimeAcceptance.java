package com.github.tvbox.osc.car7.test;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Run in the installed Android target process. No production/test-APK build is performed here. */
public final class QuickJsRuntimeAcceptance {
    private static final String SPIDER = "com.github.catvod.crawler.Spider";

    /** jsSpider is resolved from the FINAL APK's R8 map; fixture is quickjs_runtime.js's UTF-8 text. */
    public static JSONObject run(Context app, Class<?> jsSpider, String fixture) throws Exception {
        long started = System.nanoTime();
        int checks = 0;
        Object spider = null;
        Object peer = null;
        Object retiring = null;
        try (Loopback server = new Loopback(fixture)) {
            spider = create(app, jsSpider, server);
            equals("SYNC_OK", invoke(spider, "action", new Class[]{String.class}, "sync")); checks++;
            equals("OBJECT_OK", new JSONObject((String) invoke(spider, "action", new Class[]{String.class}, "object")).getString("value")); checks++;
            equals("PROMISE_OK", invoke(spider, "searchContent", new Class[]{String.class, boolean.class}, "promise", false)); checks++;
            equals("TIMER_OK", invoke(spider, "searchContent", new Class[]{String.class, boolean.class}, "timer", false)); checks++;
            equals("REQ_OK", invoke(spider, "searchContent", new Class[]{String.class, boolean.class}, "req", false)); checks++;
            expectFailure(spider, "reject", "FIXTURE_REJECT"); checks++;
            expectFailure(spider, "throw", "FIXTURE_THROW"); checks++;
            long requestStarted = System.nanoTime();
            expectFailure(spider, "req-timeout", "FIXTURE_REQ");
            require(elapsed(requestStarted) < 2500, "req.timeout was not applied"); checks++;
            equals("RECOVER_OK", invoke(spider, "action", new Class[]{String.class}, "recover")); checks++;
            require(new JSONObject((String) invoke(spider, "homeContent", new Class[]{boolean.class}, true)).getJSONArray("class").length() == 1, "home"); checks++;
            HashMap<String, String> extend = new HashMap<>();
            extend.put("marker", "ARGS_OK");
            JSONObject category = new JSONObject((String) invoke(spider, "categoryContent", new Class[]{String.class, String.class, boolean.class, HashMap.class}, "local", "1", false, extend));
            equals("CATEGORY_ARGS_OK", category.getJSONArray("list").getJSONObject(0).getString("vod_id")); checks++;
            JSONObject detail = new JSONObject((String) invoke(spider, "detailContent", new Class[]{List.class}, Collections.singletonList("local")));
            equals("DETAIL_OK", detail.getJSONArray("list").getJSONObject(0).getString("vod_id")); checks++;
            JSONObject player = new JSONObject((String) invoke(spider, "playerContent", new Class[]{String.class, String.class, List.class}, "local", "local.mp4", Collections.singletonList("vip")));
            require(player.getInt("vipCount") == 1, "player argument conversion"); checks++;

            long timeoutStarted = System.nanoTime();
            Throwable timeout = expectFailure(spider, "timeout", null);
            require(hasCause(timeout, TimeoutException.class), "timeout must expose TimeoutException");
            require(elapsed(timeoutStarted) >= 29000 && elapsed(timeoutStarted) < 35000, "total deadline"); checks++;
            equals("RECOVER_OK", invoke(spider, "action", new Class[]{String.class}, "recover")); checks++;
            Thread.sleep(1300); // The cancelled 31-second timer would have fired by now.
            require(new JSONObject((String) invoke(spider, "action", new Class[]{String.class}, "events")).getInt("lateTimers") == 0, "late timer was not cancelled"); checks++;

            final Object interruptTarget = spider;
            server.slowEntered = new CountDownLatch(1);
            CountDownLatch interruptEntered = server.slowEntered;
            AtomicReference<Throwable> interruptedFailure = new AtomicReference<>();
            AtomicReference<Boolean> interruptedFlag = new AtomicReference<>(false);
            Thread interrupted = new Thread(() -> {
                try {
                    invoke(interruptTarget, "searchContent", new Class[]{String.class, boolean.class}, "pending", false);
                } catch (Throwable error) {
                    interruptedFailure.set(error);
                    interruptedFlag.set(Thread.currentThread().isInterrupted());
                }
            }, "QuickJS interruption fixture");
            interrupted.start();
            try {
                require(interruptEntered.await(3, TimeUnit.SECONDS), "pending request did not start");
                interrupted.interrupt();
                interrupted.join(2000);
                require(!interrupted.isAlive() && hasCause(interruptedFailure.get(), InterruptedException.class) && interruptedFlag.get(), "caller interruption not propagated"); checks++;
            } finally {
                interrupted.interrupt();
                interrupted.join(2000);
            }
            equals("TIMER_OK", invoke(spider, "searchContent", new Class[]{String.class, boolean.class}, "timer", false)); checks++;

            // Identical logical keys must still have separate network cancellation tags.
            peer = create(app, jsSpider, server);
            retiring = create(app, jsSpider, server);
            final Object peerTarget = peer;
            final Object retiringTarget = retiring;
            server.slowEntered = new CountDownLatch(2);
            CountDownLatch bothEntered = server.slowEntered;
            AtomicReference<Object> peerValue = new AtomicReference<>();
            AtomicReference<Throwable> peerFailure = new AtomicReference<>();
            AtomicReference<Throwable> retiringFailure = new AtomicReference<>();
            Thread peerCall = new Thread(() -> {
                try { peerValue.set(invoke(peerTarget, "action", new Class[]{String.class}, "pending-network")); }
                catch (Throwable error) { peerFailure.set(error); }
            }, "QuickJS peer fixture");
            Thread retiringCall = new Thread(() -> {
                try { invoke(retiringTarget, "action", new Class[]{String.class}, "pending"); }
                catch (Throwable error) { retiringFailure.set(error); }
            }, "QuickJS release fixture");
            peerCall.start();
            retiringCall.start();
            try {
                require(bothEntered.await(3, TimeUnit.SECONDS), "parallel requests did not start");
                invoke(retiring, "destroy", new Class[0]);
                retiringCall.join(2000);
                peerCall.join(3000);
                require(!retiringCall.isAlive() && retiringFailure.get() != null, "release did not wake pending caller"); checks++;
                require(peerFailure.get() == null, "release cancelled another context");
                equals("REQ_OK", peerValue.get()); checks++;
                Thread.sleep(1100); // Late timer/network callbacks must be harmless after destruction.
                equals("TIMER_OK", invoke(peer, "action", new Class[]{String.class}, "timer")); checks++;
            } finally {
                peerCall.interrupt();
                retiringCall.interrupt();
                peerCall.join(2000);
                retiringCall.join(2000);
            }
            return new JSONObject().put("passed", true).put("checks", checks).put("elapsedMs", elapsed(started));
        } finally {
            destroy(retiring);
            destroy(peer);
            destroy(spider);
        }
    }

    private static Object create(Context app, Class<?> type, Loopback server) throws Exception {
        Object spider = type.getConstructor(String.class, String.class, Class.class).newInstance("quickjs-local-fixture", server.base + "/runtime.js", null);
        try {
            invoke(spider, "init", new Class[]{Context.class, String.class}, app, new JSONObject().put("base", server.base).toString());
            return spider;
        } catch (Exception error) {
            destroy(spider);
            throw error;
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] parameters, Object... args) throws Exception {
        Method method = Class.forName(SPIDER, true, target.getClass().getClassLoader()).getMethod(name, parameters);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new Exception(cause);
        }
    }

    private static Throwable expectFailure(Object spider, String mode, String marker) throws Exception {
        try {
            invoke(spider, "searchContent", new Class[]{String.class, boolean.class}, mode, false);
        } catch (Exception error) {
            if (marker != null) require(messages(error).contains(marker), mode + " lost its error");
            return error;
        }
        throw new AssertionError(mode + " returned data instead of failing");
    }

    private static String messages(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (; error != null; error = error.getCause()) text.append(error.getMessage()).append('\n');
        return text.toString();
    }

    private static boolean hasCause(Throwable error, Class<?> type) {
        for (; error != null; error = error.getCause()) if (type.isInstance(error)) return true;
        return false;
    }

    private static void destroy(Object spider) throws Exception {
        if (spider != null) invoke(spider, "destroy", new Class[0]);
    }

    private static void equals(Object expected, Object actual) {
        require(expected.equals(actual), "expected " + expected + ", got " + actual);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static final class Loopback implements AutoCloseable {
        final ServerSocket listener;
        final String base;
        final byte[] fixture;
        final Set<Socket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());
        final ExecutorService workers = Executors.newCachedThreadPool();
        final Thread accept;
        volatile boolean closed;
        volatile CountDownLatch slowEntered;

        Loopback(String fixture) throws Exception {
            this.fixture = fixture.getBytes(StandardCharsets.UTF_8);
            listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            base = "http://127.0.0.1:" + listener.getLocalPort();
            accept = new Thread(() -> {
                while (!closed) {
                    try {
                        Socket socket = listener.accept();
                        sockets.add(socket);
                        workers.submit(() -> serve(socket));
                    } catch (Exception error) {
                        if (!closed) throw new AssertionError(error);
                    }
                }
            }, "QuickJS fixture loopback");
            accept.start();
        }

        void serve(Socket socket) {
            try (Socket connection = socket) {
                connection.setSoTimeout(2500);
                BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                String request = input.readLine();
                if (request == null) return;
                for (int i = 0; i < 100; i++) {
                    String header = input.readLine();
                    if (header == null || header.isEmpty()) break;
                }
                String path = request.split(" ")[1];
                if ("/slow".equals(path)) {
                    CountDownLatch entered = slowEntered;
                    if (entered != null) entered.countDown();
                    Thread.sleep(900);
                }
                byte[] body = "/runtime.js".equals(path) ? fixture : "OK".getBytes(StandardCharsets.UTF_8);
                OutputStream output = connection.getOutputStream();
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(body);
                output.flush();
            } catch (Exception ignored) {
                // Request cancellation closes sockets before delayed responses are written.
            } finally {
                sockets.remove(socket);
            }
        }

        @Override
        public void close() throws Exception {
            closed = true;
            listener.close();
            for (Socket socket : sockets) socket.close();
            workers.shutdownNow();
            workers.awaitTermination(3, TimeUnit.SECONDS);
            accept.join(3000);
        }
    }
}
