import com.github.tvbox.osc.util.GitHubCdn;
import java.util.List;

/** Pure routing/health tests: public origins, exact hosts, retirement persistence, bounded retry. */
public final class GitHubCdnHostTest {
    private static int checks;
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static void eq(Object a, Object b, String label) { check(a == null ? b == null : a.equals(b), label + ": " + a + " != " + b); }
    public static void main(String[] args) {
        String raw = "https://raw.githubusercontent.com/o/r/main/config.json";
        eq(GitHubCdn.canonical(raw), raw, "raw stays logical origin");
        eq(GitHubCdn.canonical("https://github.com/o/r/blob/main/config.json"), raw, "blob becomes raw content");
        eq(GitHubCdn.canonical("https://github.com/o/r/raw/refs/heads/main/config.json?q=a%20b"),
                "https://raw.githubusercontent.com/o/r/refs/heads/main/config.json?q=a%20b", "refs/query encoding retained");
        eq(GitHubCdn.canonical("https://gh-proxy.com/https://raw.githubusercontent.com/o/r/main/config.json"), raw, "legacy com wrapping recovered");
        eq(GitHubCdn.canonical("https://cdn.jsdelivr.net/gh/o/r@main/config.json"), raw, "prewrapped CDN recovered");
        eq(GitHubCdn.canonical("https://gist.github.com/u/abcd"), "https://gist.githubusercontent.com/u/abcd/raw", "gist public raw");
        for (String input : new String[]{"https://dl.736.de5.net/p/app.apk", "https://github.com/o/r/tree/main",
                "https://github.com/o/r", "https://github.com/login", "https://github.com.evil.test/o/r/blob/main/a",
                "https://github.com@evil.test/o/r/blob/main/a", "https://u:secret@github.com/o/r/blob/main/a",
                "https://raw.githubusercontent.com/o/r/main/a?%74oken=secret", "https://raw.githubusercontent.com/o/r/main/a?x-amz-signature=secret",
                "https://github.com:444/o/r/releases/download/v1/a.apk", "https://v4.gh-proxy.org", "file:///x"}) {
            eq(GitHubCdn.canonical(input), null, "unsupported/sensitive input unchanged: " + input);
        }
        check(GitHubCdn.sensitiveHeader("AUTHORIZATION") && GitHubCdn.sensitiveHeader("Cookie"), "credentials excluded");
        check(!GitHubCdn.sensitiveHeader("Range"), "range retained");
        String asset = "https://github.com/o/r/releases/download/v1/app.apk";
        eq(GitHubCdn.node("gh-proxy.com").apply(asset), "https://gh-proxy.com/github.com/o/r/releases/download/v1/app.apk", "current com syntax");
        eq(GitHubCdn.node("ghfast.top").apply(asset), "https://ghfast.top/https://github.com/o/r/releases/download/v1/app.apk", "full URL syntax");
        GitHubCdn.MemoryStore store = new GitHubCdn.MemoryStore();
        long[] now = {1_000_000};
        GitHubCdn policy = new GitHubCdn(store, () -> now[0]);
        List<String> first = policy.candidates(raw);
        eq(first.size(), 4, "at most three nodes plus direct");
        eq(first.get(3), raw, "unwrapped direct escape route");
        policy.success("gh-proxy.com", raw);
        check(policy.candidates(raw).get(0).startsWith("https://gh-proxy.com/"), "success sticky");
        policy.failure("gh-proxy.com", raw, 404, false);
        check(policy.available("gh-proxy.com", raw), "resource 404 doesn't blacklist service");
        policy.failure("gh-proxy.com", raw, 0, false);
        check(!policy.available("gh-proxy.com", raw), "timeout enters cooldown");
        now[0] += 300_001;
        check(policy.available("gh-proxy.com", raw), "temporary outage eligible after cooldown");
        policy.failure("v4.gh-proxy.org", raw, 0, true);
        check(!policy.available("v4.gh-proxy.org", raw), "expired node retired");
        GitHubCdn restarted = new GitHubCdn(store, () -> now[0]);
        check(!restarted.available("v4.gh-proxy.org", raw), "retirement persists across process restart");
        now[0] += 6L * 60 * 60 * 1000 + 1;
        check(restarted.available("v4.gh-proxy.org", raw), "retired node may re-qualify after the 6h window");
        restarted.success("v4.gh-proxy.org", raw);
        check(restarted.available("v4.gh-proxy.org", raw), "a good answer re-admits the node");
        eq(GitHubCdn.canonical("https://github.com/o/r/releases/latest"),
                "https://github.com/o/r/releases/latest", "release probe page routed");
        check(GitHubCdn.allowedRedirect("media.githubusercontent.com", "gh-proxy.org"), "LFS media redirect allowed");
        check(GitHubCdn.allowedRedirect("github-releases.githubusercontent.com", "gh-proxy.org"), "release object redirect allowed");
        String api = "https://api.github.com/repos/o/r/releases/latest";
        check(!policy.available("ghproxy.net", api) && !policy.available("ghfast.top", api), "file-only services excluded for APIs");
        for (String prefix : GitHubCdn.prefixes()) policy.failure(java.net.URI.create(prefix).getHost(), raw, 0, true);
        eq(policy.candidates(raw), java.util.Collections.singletonList(raw), "all retired nodes are never retried");
        check(GitHubCdn.unexpectedHtml(raw, "text/html", "<html>domain for sale"), "parking HTML rejected");
        check(GitHubCdn.unexpectedHtml(raw, "text/plain", "\uFEFF <!doctype html>error"), "disguised HTML rejected");
        check(!GitHubCdn.unexpectedHtml("https://raw.githubusercontent.com/o/r/main/page.html", "text/html", "<html>ok"), "real HTML file supported");
        check(GitHubCdn.allowedRedirect("release-assets.githubusercontent.com", "gh-proxy.org"), "signed GitHub redirect allowed");
        check(!GitHubCdn.allowedRedirect("github.com.evil.test", "gh-proxy.org"), "foreign redirect rejected");
        System.out.println("GitHubCdnHostTest PASS checks=" + checks);
    }
}
