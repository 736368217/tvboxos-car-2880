import com.github.tvbox.osc.util.SearchSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Host contract tests: no Android runtime, downloaded scripts or third-party dependencies. */
public final class Car7SearchSessionTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        cancelBeforeCreation();
        registerCancelRace();
        completionRace();
        immediateCompletion();
        interruptIsolation();
        cancelledDoesNotMeanWorkerReturned();
        closeReleasesSession();
        System.out.println("PASS Car7SearchSessionTest assertions=" + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        check(latch.await(3, TimeUnit.SECONDS), "worker/latch deadline exceeded");
    }

    private static void cancelBeforeCreation() {
        SearchSession page = new SearchSession("same-token");
        SearchSession otherPage = new SearchSession("same-token");
        SearchSession fallback = new SearchSession("same-token");
        page.cancelSource("slow");
        SearchSession.Scope slow = page.source("slow");
        AtomicInteger callsCancelled = new AtomicInteger();
        check(!slow.register(new Object(), callsCancelled::incrementAndGet), "late HTTP Call accepted");
        check(callsCancelled.get() == 1, "late HTTP Call not cancelled immediately");
        FutureTask<Void> future = new FutureTask<>(() -> null);
        check(!slow.register(future) && future.isCancelled(), "late Future accepted");
        check(!slow.finish(), "cancelled source published a result");
        check(page.source("fast").isActive(), "single-site cancel affected sibling");
        check(otherPage.source("slow").isActive(), "cancel affected another instance");
        check(fallback.source("slow").isActive(), "cancel affected fallback namespace");
        check(slow.requestTag() != page.source("fast").requestTag(), "sources share HTTP tag");
        check(slow.requestTag() != otherPage.source("slow").requestTag(), "instances share HTTP tag");
        check(slow.requestTag() != fallback.source("slow").requestTag(), "namespaces share HTTP tag");
        page.close(); otherPage.close(); fallback.close();
    }

    private static void registerCancelRace() throws Exception {
        ExecutorService racers = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 500; i++) {
                SearchSession session = new SearchSession("race");
                SearchSession.Scope scope = session.source("site");
                AtomicInteger cancellations = new AtomicInteger();
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> registration = racers.submit(() -> {
                    start.await();
                    scope.register(new Object(), cancellations::incrementAndGet);
                    return null;
                });
                Future<?> cancellation = racers.submit(() -> {
                    start.await();
                    session.cancelSource("site");
                    return null;
                });
                registration.get(3, TimeUnit.SECONDS);
                cancellation.get(3, TimeUnit.SECONDS);
                check(cancellations.get() == 1, "registration/cancel race lost or duplicated cancellation");
                check(!scope.isActive() && !scope.finish(), "cancelled race published");
                session.close();
            }
        } finally {
            racers.shutdownNow();
            check(racers.awaitTermination(3, TimeUnit.SECONDS), "race workers leaked");
        }
    }

    private static void completionRace() throws Exception {
        ExecutorService racers = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 100; i++) {
                SearchSession session = new SearchSession("complete");
                SearchSession.Scope scope = session.source("site");
                CyclicBarrier start = new CyclicBarrier(2);
                Future<Boolean> first = racers.submit(() -> { start.await(); return scope.finish(); });
                Future<Boolean> second = racers.submit(() -> { start.await(); return scope.finish(); });
                boolean a = first.get(3, TimeUnit.SECONDS), b = second.get(3, TimeUnit.SECONDS);
                check(a != b, "duplicate or missing completion");
                check(!scope.finish(), "completion was reopened");
                session.close();
            }
        } finally {
            racers.shutdownNow();
            check(racers.awaitTermination(3, TimeUnit.SECONDS), "completion workers leaked");
        }
    }

    private static void immediateCompletion() {
        SearchSession session = new SearchSession("immediate");
        SearchSession.Scope scope = session.source("site");
        AtomicInteger deliveries = new AtomicInteger();
        FutureTask<Void> future = new FutureTask<>(() -> {
            if (scope.finish()) deliveries.incrementAndGet();
            return null;
        });
        check(scope.register(future), "Future registration failed");
        future.run(); // Direct executor: finishes before execute returns.
        scope.cancel();
        check(future.isDone() && !future.isCancelled(), "completed Future was retroactively cancelled");
        check(deliveries.get() == 1, "instant result lost");
        session.close();
    }

    private static void interruptIsolation() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        SearchSession session = new SearchSession("interrupt");
        CountDownLatch entered = new CountDownLatch(2), interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FutureTask<Void> first = new FutureTask<>(() -> {
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException expected) { interrupted.countDown(); }
            return null;
        });
        FutureTask<Void> second = new FutureTask<>(() -> { entered.countDown(); release.await(); return null; });
        try {
            check(session.source("first").register(first), "first registration failed");
            check(session.source("second").register(second), "second registration failed");
            workers.execute(first); workers.execute(second);
            await(entered);
            session.cancelSource("first");
            await(interrupted);
            check(first.isCancelled(), "running Future did not receive cancel(true)");
            check(!second.isCancelled() && session.source("second").isActive(), "sibling worker was cancelled");
            release.countDown();
            second.get(3, TimeUnit.SECONDS);
        } finally {
            release.countDown(); session.close(); workers.shutdownNow();
            check(workers.awaitTermination(3, TimeUnit.SECONDS), "interrupt workers leaked");
        }
    }

    private static void cancelledDoesNotMeanWorkerReturned() throws Exception {
        ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 2, 50, TimeUnit.MILLISECONDS,
                new SynchronousQueue<Runnable>());
        SearchSession old = new SearchSession("old"), next = new SearchSession("next");
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                FutureTask<Void> stuck = new FutureTask<>(() -> {
                    entered.countDown();
                    boolean done = false;
                    while (!done) {
                        try { release.await(); done = true; }
                        catch (InterruptedException ignored) { /* Finite non-cooperative JAR. */ }
                    }
                    return null;
                });
                check(old.source("slow" + i).register(stuck), "slow registration failed");
                workers.execute(stuck);
            }
            await(entered);
            old.close();
            check(workers.getActiveCount() == 2, "test did not retain physical workers after cancel");
            FutureTask<Void> rejected = new FutureTask<>(() -> null);
            SearchSession.Scope tail = next.source("tail");
            check(tail.register(rejected), "tail registration failed");
            boolean full = false;
            try { workers.execute(rejected); }
            catch (RejectedExecutionException expected) { full = true; }
            check(full, "pool grew past its bound");
            tail.unregister(rejected); rejected.cancel(false);
            check(tail.isActive(), "rejection permanently cancelled the source");
            release.countDown();
            AtomicInteger ran = new AtomicInteger();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            boolean accepted = false;
            while (!accepted && System.nanoTime() < deadline) {
                FutureTask<Void> retry = new FutureTask<>(() -> { ran.incrementAndGet(); return null; });
                tail.register(retry);
                try {
                    workers.execute(retry); accepted = true; retry.get(3, TimeUnit.SECONDS);
                } catch (RejectedExecutionException expected) {
                    tail.unregister(retry); retry.cancel(false); Thread.sleep(5);
                }
            }
            check(accepted && ran.get() == 1, "tail did not progress after worker returned");
            check(workers.getLargestPoolSize() == 2, "retries created extra workers");
        } finally {
            release.countDown(); old.close(); next.close(); workers.shutdownNow();
            check(workers.awaitTermination(3, TimeUnit.SECONDS), "finite slow workers leaked");
        }
    }

    private static void closeReleasesSession() {
        SearchSession session = new SearchSession("closed");
        SearchSession.Scope delayed = session.source("type4");
        for (int i = 0; i < 1000; i++) session.source("site" + i);
        session.close();
        check(session.sourceCount() == 0, "session retained request scopes");
        check(session.source("late") == null, "closed session resurrected");
        check(!delayed.finish(), "late completion escaped session close");
        AtomicInteger sent = new AtomicInteger();
        FutureTask<Void> queuedPrepare = new FutureTask<>(() -> { sent.incrementAndGet(); return null; });
        check(!delayed.register(queuedPrepare), "queued type4 preparation accepted after close");
        queuedPrepare.run();
        check(sent.get() == 0, "cancelled queued type4 sent a request");
        session.close();
    }
}
