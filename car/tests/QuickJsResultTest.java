package com.github.catvod.crawler.js;

import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** JVM checks of the Java wait/settlement contract; does not load or execute any JavaScript. */
public final class QuickJsResultTest {
    public static void main(String[] args) throws Exception {
        Async.Result timed = new Async.Result();
        long start = System.nanoTime();
        try {
            timed.get(20, TimeUnit.MILLISECONDS);
            throw new AssertionError("unresolved Result returned a value");
        } catch (TimeoutException expected) {
            require(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000, "unbounded wait");
        }
        timed.complete("late");
        try {
            timed.get(0, TimeUnit.MILLISECONDS);
            throw new AssertionError("late completion replaced timeout");
        } catch (TimeoutException expected) { }

        Async.Result complete = new Async.Result();
        complete.complete("first");
        complete.complete("second");
        complete.completeExceptionally(new Exception("late error"));
        require("first".equals(complete.get(0, TimeUnit.MILLISECONDS)), "settlement was not atomic");

        Async.Result interrupted = new Async.Result();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            entered.countDown();
            try { interrupted.get(10, TimeUnit.SECONDS); }
            catch (Throwable error) { failure.set(error); }
        });
        waiter.start();
        try {
            require(entered.await(1, TimeUnit.SECONDS), "waiter did not start");
            waiter.interrupt();
            waiter.join(1000);
            require(!waiter.isAlive() && failure.get() instanceof InterruptedException, "interruption was swallowed");
            interrupted.complete("late");
            require(interrupted.isDone(), "interrupted Result remained pending");
        } finally {
            waiter.interrupt();
            waiter.join(1000);
        }

        // Simulate an event posted to the JS executor after Async.run returns its Result.
        ExecutorService js = Executors.newSingleThreadExecutor();
        try {
            Async.Result deferred = new Async.Result();
            Future<Async.Result> invocation = js.submit(() -> {
                js.submit(() -> deferred.complete("callback"));
                return deferred;
            });
            require("callback".equals(invocation.get(1, TimeUnit.SECONDS).get(1, TimeUnit.SECONDS)), "executor callback was blocked");
        } finally {
            js.shutdownNow();
            require(js.awaitTermination(1, TimeUnit.SECONDS), "executor leaked");
        }

        AtomicInteger releases = new AtomicInteger();
        JSFunction sync = function(() -> "SYNC_OK", releases);
        require("SYNC_OK".equals(Async.run(object(sync), "test", new Object[0]).get()), "sync result");
        require(releases.get() == 1, "sync function reference leaked");
        JSFunction throwing = function(() -> { throw new IllegalArgumentException("SYNC_FAILURE"); }, releases);
        try {
            Async.run(object(throwing), "test", new Object[0]).get();
            throw new AssertionError("synchronous exception was swallowed");
        } catch (IllegalArgumentException expected) {
            require("SYNC_FAILURE".equals(expected.getMessage()), "wrong error");
        }
        require(releases.get() == 2, "throwing function reference leaked");
        AtomicInteger touched = new AtomicInteger();
        JSObject forbidden = (JSObject) Proxy.newProxyInstance(JSObject.class.getClassLoader(), new Class[]{JSObject.class}, (proxy, method, values) -> {
            touched.incrementAndGet();
            throw new AssertionError("cancelled invocation accessed JS");
        });
        Async.run(forbidden, "test", new Object[0], timed);
        require(touched.get() == 0, "cancelled work ran");
        System.out.println("QuickJsResultTest: PASS (timeout, first settlement, interruption, executor recovery, sync and failure)");
    }

    private interface Invoke { Object call(); }

    private static JSFunction function(Invoke call, AtomicInteger releases) {
        return (JSFunction) Proxy.newProxyInstance(JSFunction.class.getClassLoader(), new Class[]{JSFunction.class}, (proxy, method, args) -> {
            if ("call".equals(method.getName())) return call.call();
            if ("release".equals(method.getName())) { releases.incrementAndGet(); return null; }
            throw new AssertionError(method.getName());
        });
    }

    private static JSObject object(JSFunction function) {
        return (JSObject) Proxy.newProxyInstance(JSObject.class.getClassLoader(), new Class[]{JSObject.class}, (proxy, method, args) -> {
            if ("getJSFunction".equals(method.getName())) return function;
            throw new AssertionError(method.getName());
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
