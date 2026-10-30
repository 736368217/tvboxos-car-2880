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
    /** DNS 解析硬超时：单域名最多等这么久，超时按解析失败处理。 */
    private static final long DNS_TIMEOUT_MS = 2500L;

    private PlainHttp() {
    }

    /** 系统 DNS + 短超时 + 跟随重定向（默认查询/下载客户端）。 */
    public static OkHttpClient systemDns() {
        return Holder.SYSTEM;
    }

    /** 系统 DNS + 不跟随重定向：调用方自己读 302 的 Location。 */
    public static OkHttpClient systemDnsNoRedirect() {
        return Holder.SYSTEM_NO_REDIRECT;
    }

    /** 对任意现成 client（例如 App 网络栈）只包一层 DNS 硬超时，其余配置不动。 */
    public static OkHttpClient withDnsTimeout(OkHttpClient base) {
        return base.newBuilder().dns(new TimeoutDns(base.dns(), DNS_TIMEOUT_MS)).build();
    }

    private static final class Holder {
        static final OkHttpClient SYSTEM = build(true);
        static final OkHttpClient SYSTEM_NO_REDIRECT = build(false);
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
