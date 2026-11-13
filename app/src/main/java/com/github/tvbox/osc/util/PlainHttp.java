package com.github.tvbox.osc.util;

import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * 「系统 DNS 直连」HTTP 客户端工厂。
 *
 * <p>为什么存在：App 自己的网络栈（{@code com.github.catvod.net.OkHttp}）带用户的
 * DoH/代理规则，在部分车机上实测 DNS 全挂 —— 系统 ping 能解析、OkHttp 却抛
 * UnknownHostException，而且每个域名要卡满 20 秒。封面落盘、更新检查这类
 * 「必须能出去」的请求需要一条绕开 App 栈的路：系统 DNS + 自带硬超时。</p>
 *
 * <p>用法：先试 {@link #systemDns()}，失败再试 App 栈 —— 有些网络只有后者能出去。
 * 302 探测（GitHub releases/latest → tag）用 {@link #systemDnsNoRedirect()}。</p>
 */
public final class PlainHttp {

    private static final String TAG = "PlainHttp";
    /**
     * DNS 解析硬超时：单域名最多等这么久，超时按解析失败处理。
     *
     * <p>实测 2500ms 太短：车机/MuMu 冷启动或刚切网时，第一次 getaddrinfo 常要 3-6 秒
     * 才通（十秒后同一域名 <100ms 返回）。按 2.5 秒判死会把「通但慢」误判成「不可达」，
     * 于是官方更新通道被整段跳过。这里放到 6 秒，仍远小于系统默认的 20 秒级等待。</p>
     */
    private static final long DNS_TIMEOUT_MS = 6000L;

    private PlainHttp() {
    }

    /** 系统 DNS + 短超时 + 跟随重定向（默认查询/下载客户端）。 */
    public static OkHttpClient systemDns() {
        return Holder.SYSTEM;
    }

    /** 系统 DNS + 不跟随重定向 + DoH 兜底：读 302 的通道同样需要能解析域名。 */
    public static OkHttpClient systemDnsNoRedirectWithDohFallback() {
        return Holder.SYSTEM_NO_REDIRECT_DOH;
    }

    /** 系统 DNS + 不跟随重定向：调用方自己读 302 的 Location。 */
    public static OkHttpClient systemDnsNoRedirect() {
        return Holder.SYSTEM_NO_REDIRECT;
    }

    /**
     * 带公网 DoH 兜底的 DNS：系统 DNS 解析失败时改用阿里/腾讯公共 DNS 的 HTTPS 解析。
     *
     * <p>车机实测：网络已连接且 ping 通，但 system DNS 对 github.com / gh 镜像全部返回
     * UnknownHostException，导致更新检查与下载在解析阶段就失败（进度永远 0%）。这里只在
     * 系统解析失败时兜底，不改变能正常解析的网络。</p>
     */
    public static OkHttpClient systemDnsWithDohFallback() {
        return Holder.SYSTEM_DOH;
    }

    /** 对任意现成 client（例如 App 网络栈）只包一层 DNS 硬超时，其余配置不动。 */
    public static OkHttpClient withDnsTimeout(OkHttpClient base) {
        return base.newBuilder().dns(new TimeoutDns(base.dns(), DNS_TIMEOUT_MS)).build();
    }

    /**
     * 图片用：保留 App 栈的代理规则/超时，但把 DNS 换成系统 DNS。
     *
     * <p>为什么要这层混搭：App 栈的 {@code OkDns} 在部分车机上全挂（系统能解析、它抛
     * UnknownHostException），图片加载排在首页/详情/历史/收藏所有列表上，走它就会整片
     * 变随机色块；而用户配的代理规则又必须保留，否则那些只能走代理的网络反而不通。
     * 所以只替换 DNS，代理选择器、认证器、超时全部沿用 App 栈。</p>
     */
    public static OkHttpClient systemDnsWithAppStack() {
        return AppStackImage.HOLDER;
    }

    private static final class AppStackImage {
        static final OkHttpClient HOLDER = com.github.catvod.net.OkHttp.client(15000)
                .newBuilder()
                .dns(new TimeoutDns(Dns.SYSTEM, DNS_TIMEOUT_MS))
                .build();
    }

    private static final class Holder {
        static final OkHttpClient SYSTEM = build(true);
        static final OkHttpClient SYSTEM_NO_REDIRECT = build(false);
        static final OkHttpClient SYSTEM_DOH = buildWithDohFallback(true);
        static final OkHttpClient SYSTEM_NO_REDIRECT_DOH = buildWithDohFallback(false);
    }

    private static OkHttpClient build(boolean followRedirects) {
        return new OkHttpClient.Builder()
                .dns(new TimeoutDns(Dns.SYSTEM, DNS_TIMEOUT_MS))
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .build();
    }

    private static OkHttpClient buildWithDohFallback(boolean followRedirects) {
        return new OkHttpClient.Builder()
                .dns(new DohFallbackDns(Dns.SYSTEM, DNS_TIMEOUT_MS))
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .build();
    }

    /**
     * 系统 DNS 失败时改用公共 DNS 的 HTTPS JSON 解析。
     *
     * <p>端点必须写 IP：{@code doh.pub} 这类域名端点自己也要系统 DNS 才能解析，
     * 在「系统 DNS 全挂」的车机上等于没有兜底。这里只用证书带 IP SAN 的
     * {@code 223.5.5.5} / {@code 1.12.12.12} / {@code 223.6.6.6}，
     * 解析过程完全不依赖系统 DNS。</p>
     *
     * <p>系统 DNS 与 DoH 并发发起：正常网络下系统 DNS 通常 200ms 内返回并立即采信，
     * 行为与旧版一致；系统 DNS 挂掉时不必等满 6 秒才想起 DoH。</p>
     */
    private static final class DohFallbackDns implements Dns {
        /** 系统 DNS 的优先窗口：这段时间内返回就用它，超过就接受先到的 DoH 结果。 */
        private static final long SYSTEM_PREFERENCE_MS = 800L;
        /** 解析结果缓存：一次更新检查 + 下载会对同一批主机反复解析。 */
        private static final long CACHE_TTL_MS = 300000L;
        private static final String[] ENDPOINTS = {
                "https://223.5.5.5/resolve?name=",
                "https://1.12.12.12/dns-query?name=",
                "https://223.6.6.6/resolve?name="
        };
        private final Dns system;
        private final long timeoutMs;
        private final OkHttpClient doh;
        /** hostname -> {List<InetAddress>, 写入时刻} */
        private final java.util.Map<String, Object[]> cache =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final ExecutorService pool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "plain-doh");
            thread.setDaemon(true);
            return thread;
        });

        DohFallbackDns(Dns system, long timeoutMs) {
            this.system = system == null ? Dns.SYSTEM : system;
            this.timeoutMs = timeoutMs;
            // 端点全是 IP 字面量，这里不会被调用到；DoH 请求本身仍然带硬超时。
            this.doh = new OkHttpClient.Builder()
                    .dns(Dns.SYSTEM)
                    .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }

        @Override
        public List<java.net.InetAddress> lookup(String hostname) throws java.net.UnknownHostException {
            Object[] cached = cache.get(hostname);
            if (cached != null
                    && System.currentTimeMillis() - ((Long) cached[1]) < CACHE_TTL_MS) {
                @SuppressWarnings("unchecked")
                List<java.net.InetAddress> hit = (List<java.net.InetAddress>) cached[0];
                return hit;
            }
            java.util.concurrent.CompletionService<List<java.net.InetAddress>> dohService =
                    new java.util.concurrent.ExecutorCompletionService<>(pool);
            for (String endpoint : ENDPOINTS) {
                dohService.submit(() -> resolveDoh(endpoint, hostname));
            }
            java.util.concurrent.Future<List<java.net.InetAddress>> systemFuture =
                    pool.submit(() -> system.lookup(hostname));
            long deadline = System.currentTimeMillis() + timeoutMs;
            // ① 正常网络：系统 DNS 通常 <200ms 返回，优先采信，与旧行为一致。
            try {
                List<java.net.InetAddress> direct = systemFuture.get(
                        Math.min(SYSTEM_PREFERENCE_MS, timeoutMs),
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                if (direct != null && !direct.isEmpty()) return remember(hostname, direct);
            } catch (Exception systemSlowOrFailed) {
                Log.w(TAG, "system dns unavailable, waiting for doh: " + hostname);
            }
            // ② 系统 DNS 慢或失败：用最先返回的 DoH 结果。
            while (System.currentTimeMillis() < deadline) {
                java.util.concurrent.Future<List<java.net.InetAddress>> done;
                try {
                    done = dohService.poll(
                            Math.max(1L, deadline - System.currentTimeMillis()),
                            java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (done == null) break;
                try {
                    List<java.net.InetAddress> addresses = done.get();
                    if (addresses != null && !addresses.isEmpty()) {
                        Log.i(TAG, "doh resolved " + hostname + " (" + addresses.size() + " addr)");
                        return remember(hostname, addresses);
                    }
                } catch (Exception error) {
                    Log.w(TAG, "doh failed " + hostname + " : "
                            + error.getClass().getSimpleName());
                }
            }
            // ③ 兜底：DoH 全挂，但系统 DNS 在优先窗口之后其实解析成功了。
            try {
                List<java.net.InetAddress> late =
                        systemFuture.get(1, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (late != null && !late.isEmpty()) return remember(hostname, late);
            } catch (Exception ignored) {
                // 系统 DNS 已确定失败
            }
            throw new java.net.UnknownHostException(hostname + "（系统 DNS 与 DoH 均失败）");
        }

        private List<java.net.InetAddress> remember(String hostname,
                                                   List<java.net.InetAddress> addresses) {
            cache.put(hostname, new Object[]{addresses, System.currentTimeMillis()});
            return addresses;
        }

        private List<java.net.InetAddress> resolveDoh(String endpoint, String hostname) throws Exception {
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(endpoint + java.net.URLEncoder.encode(hostname, "UTF-8") + "&type=A")
                    .header("Accept", "application/dns-json")
                    .build();
            try (okhttp3.Response response = doh.newCall(request).execute()) {
                okhttp3.ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) return java.util.Collections.emptyList();
                org.json.JSONObject json = new org.json.JSONObject(body.string());
                org.json.JSONArray answers = json.optJSONArray("Answer");
                List<java.net.InetAddress> out = new java.util.ArrayList<>();
                if (answers == null) return out;
                for (int i = 0; i < answers.length(); i++) {
                    String data = answers.optJSONObject(i) == null ? ""
                            : answers.optJSONObject(i).optString("data", "");
                    if (data.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
                        out.add(java.net.InetAddress.getByName(data));
                    }
                }
                return out;
            }
        }
    }

    /** 给任意 {@link Dns} 加硬超时：connectTimeout 管不到 DNS，OkHttp 会阻塞在 lookup 上。 */
    private static final class TimeoutDns implements Dns {
        private final Dns delegate;
        private final long timeoutMs;
        private final ExecutorService pool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "plain-dns");
            thread.setDaemon(true);
            return thread;
        });

        TimeoutDns(Dns delegate, long timeoutMs) {
            this.delegate = delegate == null ? Dns.SYSTEM : delegate;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public List<java.net.InetAddress> lookup(final String hostname) throws java.net.UnknownHostException {
            Future<List<java.net.InetAddress>> future = pool.submit(() -> delegate.lookup(hostname));
            try {
                return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException timeout) {
                future.cancel(true);
                Log.w(TAG, "dns timeout after " + timeoutMs + "ms: " + hostname);
                throw new java.net.UnknownHostException(hostname + "（解析超时 " + timeoutMs + "ms）");
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new java.net.UnknownHostException(hostname + "（解析被中断）");
            } catch (Exception error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                if (cause instanceof java.net.UnknownHostException) {
                    throw (java.net.UnknownHostException) cause;
                }
                throw new java.net.UnknownHostException(hostname + "（" + cause + "）");
            }
        }
    }
}
