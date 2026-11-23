package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

/** Shared GET/HEAD failover; the caller retains response bytes, cancellation and source identity. */
public final class GitHubCdnInterceptor implements Interceptor {
    private final GitHubCdn policy;
    private static boolean initialized;
    private static final String TAG = "GitHubCdn";
    private static final int CONNECT_MS = 2500, READ_MS = 5000, DIRECT_CONNECT_MS = 6000;

    public GitHubCdnInterceptor() { this(GitHubCdn.shared()); }
    public GitHubCdnInterceptor(GitHubCdn policy) { this.policy = policy; }

    /** Called once at app init; DNS/proxy reloads keep the same catalogue and retired nodes. */
    public static synchronized void init(Context context) {
        if (initialized || context == null) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences("github-cdn-health", Context.MODE_PRIVATE);
        GitHubCdn.shared().useStore(new GitHubCdn.Store() {
            public String get(String key) { return prefs.getString(key, null); }
            public void put(String key, String value) { prefs.edit().putString(key, value).apply(); }
        });
        initialized = true;
    }
    public static OkHttpClient.Builder install(OkHttpClient.Builder builder) {
        for (Interceptor i : builder.interceptors()) if (i instanceof GitHubCdnInterceptor) return builder;
        builder.addInterceptor(new GitHubCdnInterceptor());
        builder.addNetworkInterceptor(new Guard());
        return builder;
    }
    public static boolean eligible(Request request) {
        if (!(request.method().equals("GET") || request.method().equals("HEAD"))) return false;
        if (GitHubCdn.canonical(request.url().toString()) == null) return false;
        for (String name : request.headers().names()) if (GitHubCdn.sensitiveHeader(name)) return false;
        return true;
    }
    private static final class Route {
        final String nodeHost;
        Route(String host) { nodeHost = host; }
    }
    private static final class RetiredNode extends IOException {
        RetiredNode() { super("CDN redirect left the public GitHub route"); }
    }
    private static int bound(int caller, int limit) { return caller == 0 ? limit : Math.min(caller, limit); }

    @Override public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        if (!eligible(original)) return chain.proceed(original);
        String origin = GitHubCdn.canonical(original.url().toString());
        Request logical = original.newBuilder().url(origin).build();
        List<String> candidates = policy.candidates(origin);
        IOException failure = null;
        for (String url : candidates) {
            if (chain.call().isCanceled() || Thread.currentThread().isInterrupted()) {
                throw new IOException("Canceled");
            }
            String host = HttpUrl.get(url).host();
            boolean mirror = GitHubCdn.node(host) != null;
            if (mirror && !policy.available(host, origin)) continue;
            Request request = logical.newBuilder().url(url)
                    .tag(Route.class, mirror ? new Route(host) : null)
                    .removeHeader("Host").build();
            if (mirror) request = request.newBuilder().header("Cache-Control", "no-cache").build();
            Response response = null;
            try {
                // Mirrors get a short budget because a dead node must not stall playback; the direct
                // attempt only bounds CONNECT (a blocked host fails fast, downloads keep full read timeout).
                Chain transport = mirror
                        ? chain.withConnectTimeout(bound(chain.connectTimeoutMillis(), CONNECT_MS), TimeUnit.MILLISECONDS)
                                .withReadTimeout(bound(chain.readTimeoutMillis(), READ_MS), TimeUnit.MILLISECONDS)
                        : chain.withConnectTimeout(bound(chain.connectTimeoutMillis(), DIRECT_CONNECT_MS), TimeUnit.MILLISECONDS);
                response = transport.proceed(request);
                if (mirror) {
                    String redirect = response.header("Location");
                    if (redirect != null && response.code() >= 300 && response.code() < 400) {
                        HttpUrl target = response.request().url().resolve(redirect);
                        if (target == null || !target.isHttps() || !GitHubCdn.allowedRedirect(target.host(), host)) {
                            throw new RetiredNode();
                        }
                    }
                    if (response.isSuccessful() && response.body() != null && original.method().equals("GET")) {
                        String prefix = response.peekBody(512).string();
                        if (GitHubCdn.unexpectedHtml(origin, response.header("Content-Type"), prefix)) {
                            throw new RetiredNode();
                        }
                    }
                    if (response.code() >= 400) {
                        policy.failure(host, origin, response.code(), false);
                        log("skip node=" + host + " http=" + response.code());
                        response.close();
                        response = null;
                        continue;
                    }
                    policy.success(host, origin);
                    log("ok node=" + host + " origin=" + URI.create(origin).getHost() + " http=" + response.code());
                }
                Response.Builder result = response.newBuilder().request(logical);
                if (mirror) {
                    // Never let a third-party node set cookies for the logical GitHub origin.
                    result.removeHeader("Set-Cookie").removeHeader("Set-Cookie2");
                    String location = response.header("Location");
                    HttpUrl target = location == null ? null : response.request().url().resolve(location);
                    String canonical = target == null ? null : GitHubCdn.canonical(target.toString());
                    if (canonical != null) result.header("Location", canonical);
                    if (response.body() != null) result.body(track(response.body(), host, origin, chain.call()));
                }
                return result.build();
            } catch (IOException error) {
                if (response != null) response.close();
                if (chain.call().isCanceled() || Thread.currentThread().isInterrupted()) throw error;
                if (!mirror) {
                    // Direct failed (blocked network): keep the error and fall through to mirrors.
                    failure = error;
                    continue;
                }
                if (!callerDeadline(error)) policy.failure(host, origin, 0, retired(error));
                log("disabled node=" + host + " reason=" + error.getClass().getSimpleName());
                failure = error;
            }
        }
        throw failure == null ? new IOException("No GitHub route completed") : failure;
    }
    private static boolean retired(Throwable error) {
        // Only TLS/identity failures justify a long retirement. Parking pages and unexpected
        // redirects get the ordinary cooldown so a single bad response can't remove a node.
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof CertificateException || cause instanceof SSLPeerUnverifiedException) return true;
        }
        return false;
    }
    /** A caller-level deadline is not evidence that the mirror is unhealthy. */
    private static boolean callerDeadline(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException) return false;
            if (cause instanceof java.io.InterruptedIOException) return true;
        }
        return false;
    }
    private ResponseBody track(ResponseBody body, String host, String origin, okhttp3.Call call) {
        BufferedSource source = Okio.buffer(new ForwardingSource(body.source()) {
            @Override public long read(Buffer sink, long count) throws IOException {
                try { return super.read(sink, count); }
                catch (IOException error) {
                    // Don't quarantine a node when the user cancels a streaming download.
                    if (!call.isCanceled()) policy.failure(host, origin, 0, retired(error));
                    throw error;
                }
            }
        });
        return new ResponseBody() {
            @Override public MediaType contentType() { return body.contentType(); }
            @Override public long contentLength() { return body.contentLength(); }
            @Override public BufferedSource source() { return source; }
        };
    }
    private static void log(String text) {
        try { Log.i(TAG, text); } catch (RuntimeException hostJvm) { /* host test */ }
    }
    /**
     * The legacy app stack trusts some site certificates. CDN hops always use platform trust,
     * including expiry/hostname checks, and cannot follow redirects to a parked/unrelated domain.
     */
    public static final class Guard implements Interceptor {
        @Override public Response intercept(Chain chain) throws IOException {
            Route route = chain.request().tag(Route.class);
            if (route == null) return chain.proceed(chain.request());
            HttpUrl url = chain.request().url();
            if (!url.isHttps() || !GitHubCdn.allowedRedirect(url.host(), route.nodeHost)) throw new RetiredNode();
            if (chain.connection() == null || chain.connection().handshake() == null) {
                throw new SSLPeerUnverifiedException("Missing CDN TLS handshake");
            }
            try {
                List<Certificate> peers = chain.connection().handshake().peerCertificates();
                X509Certificate[] certificates = peers.toArray(new X509Certificate[0]);
                for (X509Certificate certificate : certificates) certificate.checkValidity();
                StrictTrust.MANAGER.checkServerTrusted(certificates, certificates[0].getPublicKey().getAlgorithm());
                if (!StrictTrust.CLIENT.hostnameVerifier().verify(url.host(),
                        chain.connection().socket() instanceof javax.net.ssl.SSLSocket
                                ? ((javax.net.ssl.SSLSocket) chain.connection().socket()).getSession()
                                : null)) {
                    throw new SSLPeerUnverifiedException("CDN hostname mismatch");
                }
            } catch (CertificateException invalid) {
                throw new IOException("Invalid CDN certificate", invalid);
            }
            return chain.proceed(chain.request());
        }
    }
    private static final class StrictTrust {
        static final OkHttpClient CLIENT = new OkHttpClient();
        static final X509TrustManager MANAGER = manager();
        private static X509TrustManager manager() {
            try {
                TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init((KeyStore) null);
                for (TrustManager manager : factory.getTrustManagers()) {
                    if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
                }
                throw new IllegalStateException("No platform trust manager");
            } catch (Exception unavailable) { throw new IllegalStateException(unavailable); }
        }
    }
}
