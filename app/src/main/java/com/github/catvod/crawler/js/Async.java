package com.github.catvod.crawler.js;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Async {

    public static final long DEFAULT_TIMEOUT_MILLIS = 30000;

    // Clearing this bridge disconnects late Promise reactions before removing Java callbacks.
    private static final String OBSERVE = "(function(p, h) { return p.then("
            + "function(v) { if (h.resolve) h.resolve(v); },"
            + "function(e) { if (h.reject) h.reject(String(e && e.stack || e)); }); })";

    private final Result future;
    private QuickJSContext context;
    private JSObject handlers;

    private final JSCallFunction success = this::resolve;
    private final JSCallFunction error = this::reject;

    private Object resolve(Object... args) {
        Object value = args != null && args.length > 0 ? args[0] : null;
        try {
            future.complete(snapshot(value));
        } catch (Throwable error) {
            future.completeExceptionally(error);
        } finally {
            if (value instanceof JSObject) ((JSObject) value).release();
        }
        return null;
    }

    private Object reject(Object... args) {
        String message = args != null && args.length > 0 ? String.valueOf(args[0]) : "Promise rejected";
        future.completeExceptionally(new Exception(message));
        return null;
    }

    public static Result run(JSObject object, String name, Object[] args) {
        return run(object, name, args, new Result());
    }

    // The caller creates Result before submission, so cancellation also covers queued invocations.
    public static Result run(JSObject object, String name, Object[] args, Result result) {
        if (result.isDone()) return result;
        Async async = new Async(result);
        result.async = async;
        return async.call(object, name, args);
    }

    private Async(Result future) {
        this.future = future;
    }

    private Result call(JSObject object, String name, Object[] args) {
        JSFunction function = null;
        try {
            function = object.getJSFunction(name);
            if (function == null) future.complete(null);
            else {
                Object result = function.call(args);
                if (result instanceof JSObject) {
                    JSObject value = (JSObject) result;
                    try {
                        then(value);
                    } finally {
                        value.release();
                    }
                } else future.complete(result);
            }
        } catch (Throwable t) {
            future.completeExceptionally(t);
        } finally {
            if (function != null) function.release();
        }
        return future;
    }

    // Never pass a native JS handle to the waiting caller thread.
    private static Object snapshot(Object value) {
        return value instanceof JSObject ? ((JSObject) value).stringify() : value;
    }

    private void then(JSObject promise) {
        JSFunction then = promise.getJSFunction("then");
        if (then == null) {
            future.complete(snapshot(promise));
            return;
        }
        then.release();
        context = promise.getContext();
        handlers = context.createNewJSObject();
        context.setProperty(handlers, "resolve", success);
        context.setProperty(handlers, "reject", error);
        JSFunction observe = (JSFunction) context.evaluate(OBSERVE);
        try {
            observe.callVoid(promise, handlers);
        } finally {
            observe.release();
        }
    }

    // Called only on the context's executor, before context destruction.
    private void release() {
        if (handlers == null) return;
        context.setProperty(handlers, "resolve", null);
        context.setProperty(handlers, "reject", null);
        context.removeCallFunction(success.hashCode());
        context.removeCallFunction(error.hashCode());
        handlers.release();
        handlers = null;
        context = null;
    }

    public static class Result {
        private final CountDownLatch latch = new CountDownLatch(1);
        private Object value;
        private Throwable error;
        private Async async;

        synchronized void complete(Object value) {
            if (isDone()) return;
            this.value = value;
            latch.countDown();
        }

        synchronized void completeExceptionally(Throwable error) {
            if (isDone()) return;
            this.error = error;
            latch.countDown();
        }

        public boolean isDone() {
            return latch.getCount() == 0;
        }

        public Object get() throws Exception {
            return get(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        public Object get(long timeout, TimeUnit unit) throws Exception {
            try {
                if (!latch.await(Math.max(0, timeout), unit)) {
                    completeExceptionally(new TimeoutException("QuickJS Promise timed out"));
                }
            } catch (InterruptedException interrupted) {
                completeExceptionally(interrupted);
                throw interrupted;
            }
            if (error != null) {
                if (error instanceof Exception) throw (Exception) error;
                throw new Exception(error);
            }
            return value;
        }

        void release() {
            if (async != null) {
                async.release();
                async = null;
            }
        }
    }
}
