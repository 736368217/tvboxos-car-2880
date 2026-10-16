package com.github.tvbox.osc.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Future;

/** One search round. Closed sessions and cancelled sources can never be reopened. */
public final class SearchSession {
    private final String token;
    private final Map<String, Scope> sources = new HashMap<>();
    private volatile boolean closed;

    public SearchSession(String token) {
        this.token = token;
    }

    public boolean matches(String token) {
        return this.token.equals(token);
    }

    public synchronized Scope source(String key) {
        if (closed) return null;
        Scope scope = sources.get(key);
        if (scope == null) {
            scope = new Scope();
            sources.put(key, scope);
        }
        return scope;
    }

    public void cancelSource(String key) {
        // Keep the cancelled scope until close, even when cancellation precedes creation.
        Scope scope = source(key);
        if (scope != null) scope.cancel();
    }

    public void close() {
        ArrayList<Scope> scopes;
        synchronized (this) {
            closed = true;
            scopes = new ArrayList<>(sources.values());
            sources.clear();
        }
        for (Scope scope : scopes) scope.cancel();
    }

    public synchronized int sourceCount() {
        return sources.size();
    }

    public final class Scope {
        // Object identity is the HTTP tag: unique to this instance/session/source/namespace.
        private final Object requestTag = new Object();
        private final Map<Object, Runnable> cancellations = new IdentityHashMap<>();
        private boolean cancelled;
        private boolean finished;

        public Object requestTag() {
            return requestTag;
        }

        public synchronized boolean isActive() {
            return !closed && !cancelled && !finished;
        }

        public boolean register(final Future<?> future) {
            return register(future, new Runnable() {
                @Override
                public void run() {
                    future.cancel(true);
                }
            });
        }

        /** Register before enqueue/execute; a late registration is cancelled immediately. */
        public boolean register(Object key, Runnable cancellation) {
            synchronized (this) {
                if (isActive()) {
                    cancellations.put(key, cancellation);
                    return true;
                }
            }
            cancellation.run();
            return false;
        }

        public synchronized void unregister(Object key) {
            cancellations.remove(key);
        }

        /** Exactly one completion may publish. Call on the result delivery thread. */
        public synchronized boolean finish() {
            if (!isActive()) return false;
            finished = true;
            cancellations.clear();
            return true;
        }

        public void cancel() {
            ArrayList<Runnable> actions;
            synchronized (this) {
                if (cancelled || finished) return;
                cancelled = true;
                actions = new ArrayList<>(cancellations.values());
                cancellations.clear();
            }
            for (Runnable action : actions) {
                try {
                    action.run();
                } catch (RuntimeException ignored) {
                    // One failed transport cancellation must not skip the remaining workers.
                }
            }
        }
    }
}
