package com.github.tvbox.osc.util;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Built-in public GitHub routing. No settings, remote catalogue, or dependency on Android. */
public final class GitHubCdn {
    public static final String CATALOGUE = "https://lancelotrar.github.io/cdn-url/";
    public static final int MAX_MIRROR_ATTEMPTS = 3;
    private static final long MINUTE = 60_000L;
    private static final String KEY = "catalogue-v1:";

    public interface Store {
        String get(String key);
        void put(String key, String value);
    }
    public interface Clock { long now(); }
    public static final class MemoryStore implements Store {
        private final Map<String, String> values = new HashMap<>();
        public synchronized String get(String key) { return values.get(key); }
        public synchronized void put(String key, String value) { values.put(key, value); }
    }
    public static final class Node {
        public final String host;
        final boolean stripScheme, api;
        Node(String host, boolean stripScheme, boolean api) {
            this.host = host;
            this.stripScheme = stripScheme;
            this.api = api;
        }
        public String apply(String origin) {
            return "https://" + host + "/" + (stripScheme ? origin.substring(8) : origin);
        }
    }
    // Only nodes verified with public bytes are active. API capability is measured separately.
    private static final Node[] NODES = {
            new Node("v4.gh-proxy.org", true, true), new Node("gh-proxy.com", true, true),
            new Node("gh-proxy.org", true, true), new Node("cdn.gh-proxy.org", true, true),
            new Node("ghproxy.net", false, false), new Node("ghfast.top", false, false)
    };
    private volatile Store store;
    private final Clock clock;
    private static final GitHubCdn SHARED = new GitHubCdn(new MemoryStore(), System::currentTimeMillis);

    public GitHubCdn(Store store, Clock clock) { this.store = store; this.clock = clock; }
    public static GitHubCdn shared() { return SHARED; }
    public void useStore(Store persistent) { if (persistent != null) store = persistent; }

    /** Exact-host admission, with original query/path encoding kept intact. */
    public static String canonical(String input) {
        if (input == null) return null;
        String url = input;
        for (int depth = 0; depth < 3; depth++) {
            URI uri;
            try { uri = URI.create(url); } catch (IllegalArgumentException bad) { return null; }
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getFragment() != null) return null;
            host = host.toLowerCase(Locale.ROOT);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (!https && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            if (uri.getPort() != -1 && uri.getPort() != (https ? 443 : 80)) return null;
            if (sensitiveQuery(uri.getRawQuery())) return null;
            String path = uri.getRawPath();
            if (path == null) return null;
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            String inner = unwrap(host, path, query);
            if (inner != null) { url = inner; continue; }
            String[] p = path.split("/", -1);
            if ("raw.githubusercontent.com".equals(host) && p.length >= 5 && validRepo(p[1], p[2])) {
                return "https://" + host + path + query;
            }
            if ("api.github.com".equals(host) && (path.startsWith("/repos/") || path.startsWith("/gists"))) {
                return "https://" + host + path + query;
            }
            if ("gist.githubusercontent.com".equals(host) && p.length >= 4 && "raw".equals(p[3])) {
                return "https://" + host + path + query;
            }
            if ("gist.github.com".equals(host) && p.length == 3 && !p[1].isEmpty() && !p[2].isEmpty()) {
                return "https://gist.githubusercontent.com" + path + "/raw" + query;
            }
            if (!"github.com".equals(host) || p.length < 5 || !validRepo(p[1], p[2])) return null;
            if ("blob".equals(p[3]) || "raw".equals(p[3])) {
                if (p.length < 6) return null;
                return "https://raw.githubusercontent.com/" + p[1] + "/" + p[2]
                        + path.substring(path.indexOf("/" + p[3] + "/") + p[3].length() + 1) + query;
            }
            if (("releases".equals(p[3]) && ("download".equals(p[4])
                    || ("latest".equals(p[4]) && p.length >= 7 && "download".equals(p[5]))))
                    || "archive".equals(p[3])) return "https://github.com" + path + query;
            return null; // Repository home/tree/login/HTML pages are not file-download endpoints.
        }
        return null; // No recursive/nested proxy chains.
    }

    private static boolean validRepo(String owner, String repo) {
        return owner.matches("[A-Za-z0-9_.-]+") && repo.matches("[A-Za-z0-9_.-]+")
                && !owner.equals(".") && !owner.equals("..") && !repo.equals(".") && !repo.equals("..");
    }
    private static String unwrap(String host, String path, String query) {
        boolean prefix = node(host) != null || host.equals("v6.gh-proxy.org")
                || host.equals("axisnow.gh-proxy.org") || host.equals("ghproxy.icu")
                || host.equals("fastgit.cc") || host.equals("github.chenc.dev");
        if (prefix && path.length() > 1) {
            String nested = path.substring(1);
            if (nested.startsWith("https://") || nested.startsWith("http://")) return nested + query;
            if (nested.startsWith("github.com/") || nested.startsWith("raw.githubusercontent.com/")
                    || nested.startsWith("gist.githubusercontent.com/") || nested.startsWith("api.github.com/")) {
                return "https://" + nested + query;
            }
        }
        // Recover a prewrapped jsDelivr subscription, but don't choose moving-branch CDN caches.
        if ((host.equals("cdn.jsdelivr.net") || host.equals("fastly.jsdelivr.net")
                || host.equals("gcore.jsdelivr.net") || host.equals("testingcf.jsdelivr.net")
                || host.equals("cdn.jsdmirror.cn")) && path.startsWith("/gh/")) {
            String[] p = path.substring(4).split("/", 3);
            if (p.length == 3) {
                int at = p[1].indexOf('@');
                if (at > 0) return "https://raw.githubusercontent.com/" + p[0] + "/"
                        + p[1].substring(0, at) + "/" + p[1].substring(at + 1) + "/" + p[2] + query;
            }
        }
        return null;
    }
    private static boolean sensitiveQuery(String query) {
        if (query == null) return false;
        for (String pair : query.split("&")) {
            String name = pair.split("=", 2)[0];
            try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception invalid) { return true; }
            name = name.toLowerCase(Locale.ROOT);
            if (name.contains("token") || name.contains("signature") || name.contains("credential")
                    || name.startsWith("x-amz-") || name.equals("auth") || name.equals("key")
                    || name.equals("access_key") || name.equals("jwt")) return true;
        }
        return false;
    }
    public static boolean sensitiveHeader(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("authorization") || n.equals("proxy-authorization") || n.equals("cookie")
                || n.equals("cookie2") || n.contains("token") || n.equals("x-api-key");
    }
    public static Node node(String host) {
        for (Node n : NODES) if (n.host.equalsIgnoreCase(host)) return n;
        return null;
    }
    public static String[] prefixes() {
        String[] values = new String[NODES.length];
        for (int i = 0; i < values.length; i++) values[i] = "https://" + NODES[i].host + "/";
        return values;
    }
    private static String kind(String origin) {
        return URI.create(origin).getHost().equals("api.github.com") ? "api" : "file";
    }
    /** Snapshot at request start; disabled nodes are checked again before each actual attempt. */
    public List<String> candidates(String input) {
        String origin = canonical(input);
        if (origin == null) return Collections.singletonList(input);
        String preferred = store.get(KEY + "preferred:" + kind(origin));
        List<String> out = new ArrayList<>();
        Node first = node(preferred);
        if (first != null && available(first.host, origin)) out.add(first.apply(origin));
        for (Node n : NODES) {
            if (out.size() >= MAX_MIRROR_ATTEMPTS) break;
            if (n == first || !available(n.host, origin)) continue;
            out.add(n.apply(origin));
        }
        out.add(origin); // Always retain a direct, unwrapped escape route.
        return out;
    }
    /** Expired/retired nodes stay out for six hours, then a single request may re-qualify them. */
    private static final long RETIRED_RETRY_MS = 6L * 60 * 60 * 1000;
    public boolean available(String host, String origin) {
        Node n = node(host);
        if (n == null || (kind(origin).equals("api") && !n.api)) return false;
        long now = clock.now();
        long retiredUntil = readLong(KEY + host + ":retiredUntil");
        if (retiredUntil > now) return false;
        return readLong(KEY + host + ":" + kind(origin) + ":until") <= now;
    }
    public synchronized void success(String host, String origin) {
        if (node(host) == null) return;
        // A good answer is the only proof a previously suspect domain now works.
        store.put(KEY + host + ":retiredUntil", "0");
        String key = KEY + host + ":" + kind(origin);
        store.put(key + ":until", "0");
        store.put(key + ":failures", "0");
        store.put(KEY + "preferred:" + kind(origin), host);
    }
    public synchronized void failure(String host, String origin, int status, boolean retired) {
        if (node(host) == null) return;
        if (retired) {
            store.put(KEY + host + ":retiredUntil", String.valueOf(clock.now() + RETIRED_RETRY_MS));
            return;
        }
        // A missing/deleted file is not evidence that a whole service is down.
        if (status == 404 || status == 410 || status == 416) return;
        String key = KEY + host + ":" + kind(origin);
        long failures = Math.min(8, readLong(key + ":failures") + 1);
        long minutes = status == 429 ? 15 : Math.min(1440, 5L << (int) (failures - 1));
        store.put(key + ":failures", String.valueOf(failures));
        store.put(key + ":until", String.valueOf(clock.now() + minutes * MINUTE));
    }
    private long readLong(String key) {
        try { return Long.parseLong(store.get(key)); } catch (Exception absent) { return 0; }
    }
    /** HTML error/parking pages must not become a JSON subscription, Python plugin or binary. */
    public static boolean unexpectedHtml(String origin, String contentType, String prefix) {
        String path = URI.create(origin).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".svg")) return false;
        String body = prefix == null ? "" : prefix.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
        return (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html"))
                || body.startsWith("<!doctype html") || body.startsWith("<html");
    }
    public static boolean allowedRedirect(String host, String nodeHost) {
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equalsIgnoreCase(nodeHost)) return true;
        for (String github : new String[]{"github.com", "raw.githubusercontent.com",
                "gist.githubusercontent.com", "api.github.com", "codeload.github.com"}) {
            if (lower.equals(github)) return true;
        }
        // LFS media, release objects and registry blobs are first-party asset hosts.
        return lower.endsWith(".githubusercontent.com");
    }
}
