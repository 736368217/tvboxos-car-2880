package car.http;

import com.github.tvbox.osc.util.GitHubCdn;
import com.github.tvbox.osc.util.GitHubCdnInterceptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLPeerUnverifiedException;
import okhttp3.*;

/** Offline real Request/Response/ResponseBody contracts; no mock-server dependency. */
public final class GitHubCdnTransportTest {
    static int checks;
    static void check(boolean value, String text) { checks++; if (!value) throw new AssertionError(text); }
    interface Handler { Response respond(Request request, int index) throws IOException; }
    static Response response(Request r, int code, String type, byte[] body) {
        return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(ResponseBody.create(MediaType.parse(type), body)).header("Content-Type", type).build();
    }
    static final class FakeChain implements Interceptor.Chain {
        final Request request;
        final List<Request> requests = new ArrayList<>();
        final Handler handler;
        final Call call;
        int connect = 20000, read = 20000;
        FakeChain(Request r, Handler handler) { request = r; this.handler = handler; call = new OkHttpClient().newCall(r); }
        public Request request() { return request; }
        public Response proceed(Request r) throws IOException { requests.add(r); return handler.respond(r, requests.size() - 1); }
        public Connection connection() { return null; }
        public Call call() { return call; }
        public int connectTimeoutMillis() { return connect; }
        public int readTimeoutMillis() { return read; }
        public int writeTimeoutMillis() { return 20000; }
        public Interceptor.Chain withConnectTimeout(int value, TimeUnit unit) { connect = (int) unit.toMillis(value); return this; }
        public Interceptor.Chain withReadTimeout(int value, TimeUnit unit) { read = (int) unit.toMillis(value); return this; }
        public Interceptor.Chain withWriteTimeout(int value, TimeUnit unit) { return this; }
    }
    static GitHubCdn fresh() { return new GitHubCdn(new GitHubCdn.MemoryStore(), () -> 1000000); }
    public static void main(String[] args) throws Exception {
        String raw = "https://raw.githubusercontent.com/o/r/main/config.json";
        Request request = new Request.Builder().url(raw).header("Range", "bytes=0-3").tag("subscription").build();
        byte[] bytes = new byte[]{'P','K',3,4,0,1,2,3};
        GitHubCdn policy = fresh();
        FakeChain chain = new FakeChain(request, (r, i) -> {
            if (i == 0) throw new SSLPeerUnverifiedException("direct route blocked on this network");
            if (i == 1) throw new SSLPeerUnverifiedException("expired certificate");
            if (i == 2) return response(r, 200, "text/html", "<html>domain for sale</html>".getBytes());
            return response(r, 206, "application/octet-stream", bytes).newBuilder().header("Set-Cookie", "evil=1").build();
        });
        try (Response got = new GitHubCdnInterceptor(policy).intercept(chain)) {
            check(Arrays.equals(got.body().bytes(), bytes), "binary bytes unchanged after failed-node failover");
            check(got.request().url().toString().equals(raw), "original URL retained for relative paths");
            check(got.code() == 206 && got.header("Set-Cookie") == null, "Range status retained; proxy cookies removed");
        }
        check(chain.requests.size() == 4, "direct attempt plus bounded mirror attempts");
        check(!policy.available("v4.gh-proxy.org", raw) && !policy.available("gh-proxy.com", raw), "expired and parked nodes leave rotation");
        for (Request r : chain.requests) {
            check("bytes=0-3".equals(r.header("Range")) && r.tag().equals("subscription"), "range and cancellation tag preserved");
        }
        check(chain.connect <= 4000 && chain.read <= 8000, "per node headers/connection deadline");
        FakeChain again = new FakeChain(request, (r, i) -> response(r, 200, "text/plain", "ok".getBytes()));
        new GitHubCdnInterceptor(policy).intercept(again).close();
        check(again.requests.size() == 1 && again.requests.get(0).url().host().equals("raw.githubusercontent.com"),
                "healthy direct wins and disabled hosts are not retried");
        for (Request bypass : new Request[]{request.newBuilder().header("Authorization", "private-token").build(),
                request.newBuilder().header("Cookie", "session=secret").build(),
                request.newBuilder().post(RequestBody.create(null, "write")).build(),
                request.newBuilder().url("https://dl.736.de5.net/p/app.apk").build(),
                request.newBuilder().url(raw + "?token=secret").build()}) {
            FakeChain untouched = new FakeChain(bypass, (r, i) -> response(r, 200, "text/plain", "ok".getBytes()));
            new GitHubCdnInterceptor(fresh()).intercept(untouched).close();
            check(untouched.requests.size() == 1 && untouched.requests.get(0) == bypass, "protected/R2/write request bypasses CDN");
        }
        FakeChain canceled = new FakeChain(request, (r, i) -> { throw new AssertionError("must not dispatch canceled call"); });
        canceled.call.cancel();
        try { new GitHubCdnInterceptor(fresh()).intercept(canceled); throw new AssertionError("cancel ignored"); }
        catch (IOException expected) { check(canceled.requests.isEmpty(), "cancellation stops retry"); }
        FakeChain fallback = new FakeChain(request, (r, i) -> {
            if (r.url().host().equals("raw.githubusercontent.com")) return response(r, 404, "text/plain", "source missing".getBytes());
            return response(r, 404, "text/plain", "source missing".getBytes());
        });
        try (Response got = new GitHubCdnInterceptor(fresh()).intercept(fallback)) {
            check(got.code() == 404 && fallback.requests.size() == 1,
                    "a direct 404 is returned as-is without consulting mirrors");
        }
        OkHttpClient.Builder b = new OkHttpClient.Builder();
        GitHubCdnInterceptor.install(b); GitHubCdnInterceptor.install(b);
        check(b.interceptors().size() == 1 && b.networkInterceptors().size() == 1, "reload/derived builder cannot double install");
        System.out.println("GitHubCdnTransportTest PASS checks=" + checks);
    }
}
