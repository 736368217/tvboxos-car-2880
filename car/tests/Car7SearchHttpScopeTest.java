import com.github.tvbox.osc.util.SearchSession;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Real OkHttp cancellation against a loopback server, using the production request scope. */
public final class Car7SearchHttpScopeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        ExecutorService serverWorkers = Executors.newFixedThreadPool(3);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverWorkers);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger lateRequests = new AtomicInteger(), fastSuccess = new AtomicInteger();
        server.createContext("/slow", exchange -> {
            entered.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException cancelledClient) {
                // Expected: the client disconnects before the slow site replies.
            } finally {
                exchange.close();
            }
        });
        server.createContext("/fast", exchange -> {
            byte[] bytes = "{\"list\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/late", exchange -> {
            lateRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(3, TimeUnit.SECONDS).build();
        SearchSession page = new SearchSession("page"), anotherPage = new SearchSession("page");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            SearchSession.Scope slow = page.source("slow");
            Call slowCall = client.newCall(new Request.Builder().url(base + "/slow").tag(slow.requestTag()).build());
            check(slow.register(slowCall, slowCall::cancel), "slow Call registration failed");
            CountDownLatch slowFailed = new CountDownLatch(1);
            slowCall.enqueue(new Callback() {
                @Override public void onFailure(Call call, java.io.IOException error) { slowFailed.countDown(); }
                @Override public void onResponse(Call call, Response response) { response.close(); }
            });
            check(entered.await(3, TimeUnit.SECONDS), "slow HTTP request did not start");

            SearchSession.Scope fast = anotherPage.source("slow");
            Call fastCall = client.newCall(new Request.Builder().url(base + "/fast").tag(fast.requestTag()).build());
            check(fast.register(fastCall, fastCall::cancel), "sibling Call registration failed");
            page.cancelSource("slow");
            check(slowFailed.await(3, TimeUnit.SECONDS), "source cancellation did not interrupt actual HTTP Call");
            check(slowCall.isCanceled() && !fastCall.isCanceled(), "cancellation crossed page/source boundary");
            try (Response response = fastCall.execute()) {
                check(response.isSuccessful() && response.body().string().contains("list"), "sibling search failed");
                fastSuccess.incrementAndGet();
            }
            check(fast.finish(), "sibling result was suppressed");

            SearchSession.Scope late = page.source("late");
            Call lateCall = client.newCall(new Request.Builder().url(base + "/late").tag(late.requestTag()).build());
            check(late.register(lateCall, lateCall::cancel), "late Call registration failed");
            // Cancel in the exact gap between registration and enqueue.
            page.cancelSource("late");
            CountDownLatch lateFailed = new CountDownLatch(1);
            lateCall.enqueue(new Callback() {
                @Override public void onFailure(Call call, java.io.IOException error) { lateFailed.countDown(); }
                @Override public void onResponse(Call call, Response response) { response.close(); }
            });
            check(lateFailed.await(3, TimeUnit.SECONDS), "cancel-before-enqueue callback missing");
            check(lateRequests.get() == 0, "cancel-before-enqueue still sent HTTP traffic");
            check(!slow.finish() && !late.finish(), "cancelled HTTP completion was delivered");
            System.out.println("PASS Car7SearchHttpScopeTest fast_results=" + fastSuccess.get()
                    + " late_requests=" + lateRequests.get());
        } finally {
            release.countDown(); page.close(); anotherPage.close();
            client.dispatcher().cancelAll();
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
            server.stop(0); serverWorkers.shutdownNow();
            check(serverWorkers.awaitTermination(3, TimeUnit.SECONDS), "server worker leak");
            check(client.dispatcher().executorService().awaitTermination(3, TimeUnit.SECONDS), "client worker leak");
        }
    }
}
