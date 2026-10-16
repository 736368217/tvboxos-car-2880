package com.github.catvod.crawler.js;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.github.catvod.Proxy;
import com.github.catvod.crawler.js.rsa.RSAEncrypt;
import com.whl.quickjs.wrapper.ContextSetter;
import com.whl.quickjs.wrapper.Function;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.JSUtils;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

import java.util.Collections;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class Global {
    private QuickJSContext runtime;
    public final ExecutorService executor;
    private final Timer timer;
    private final Object networkTag;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Pending> pending = Collections.newSetFromMap(new ConcurrentHashMap<Pending, Boolean>());
    private Async.Result scope; // Accessed only on the JS executor, including nested callbacks.

    public Global(ExecutorService executor, Object networkTag) {
        this.executor = executor;
        this.networkTag = networkTag;
        this.timer = new Timer("QuickJS timers", true);
    }

    void enter(Async.Result result) {
        scope = result;
    }

    void leave() {
        scope = null;
    }

    // These cancellation methods use Java/OkHttp objects only and may run on the caller thread.
    void cancel(Async.Result result) {
        for (Pending item : pending) if (item.owner == result) item.cancel();
    }

    void stop() {
        closed.set(true);
        timer.cancel();
        for (Pending item : pending) item.cancel();
        Connect.cancelByTag(networkTag);
    }

    // Release native function references only on the JS executor, before destroying the context.
    void release(Async.Result result) {
        for (Pending item : pending) if (item.owner == result) item.release();
        timer.purge();
    }

    void release() {
        stop();
        for (Pending item : pending) item.release();
        runtime = null;
    }

    private boolean live(Pending item) {
        return !closed.get() && (item.owner == null || !item.owner.isDone());
    }

    private boolean post(Pending item, Runnable callback) {
        if (closed.get()) return false;
        try {
            executor.submit(() -> {
                Async.Result previous = scope;
                try {
                    if (live(item)) {
                        scope = item.owner;
                        callback.run();
                    }
                } catch (Throwable error) {
                    if (item.owner != null) item.owner.completeExceptionally(error);
                } finally {
                    scope = previous;
                    item.release();
                }
            });
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private final class Pending {
        final Async.Result owner = scope;
        JSFunction function;
        volatile Call call;
        volatile TimerTask task;
        volatile Response response;

        Pending(JSFunction function) {
            this.function = function;
            pending.add(this);
        }

        void cancel() {
            if (call != null) call.cancel();
            if (task != null) task.cancel();
            Response res = response;
            if (res != null) res.close();
        }

        void release() {
            cancel();
            if (!pending.remove(this)) return;
            if (function != null) {
                function.release();
                function = null;
            }
        }
    }

    @Keep
    @Function
    public String getProxy(boolean local) {
        return Proxy.getUrl(local) + "?do=js";
    }

    @Keep
    @Function
    public String js2Proxy(Boolean dynamic, Integer siteType, String siteKey, String url, JSObject headers) {
        boolean local = dynamic == null || !dynamic;
        return getProxy(local) + "&from=catvod" + "&siteType=" + siteType + "&siteKey=" + siteKey + "&header=" + URLEncoder.encode(headers.stringify()) + "&url=" + URLEncoder.encode(url);
    }

    @Keep
    @Function
    public String joinUrl(String parent, String child) {
        return HtmlParser.joinUrl(parent, child);
    }

    @Keep
    @Function
    public String pd(String html, String rule, String add_url) {
        return HtmlParser.parseDomForUrl(html, rule, add_url);
    }

    @Keep
    @Function
    public String pdfh(String html, String rule) {
        return HtmlParser.parseDomForUrl(html, rule, "");
    }

    @Keep
    @Function
    public JSArray pdfa(String html, String rule) {

        return new JSUtils<String>().toArray(runtime, HtmlParser.parseDomForArray(html, rule));
    }

    @Keep
    @Function
    public JSArray pdfla(String html, String p1, String list_text, String list_url, String add_url) {
        return new JSUtils<String>().toArray(runtime, HtmlParser.parseDomForList(html, p1, list_text, list_url, add_url));
    }

    @Keep
    @Function
    public String s2t(String text) {
        try {
            return Trans.s2t(false, text);
        } catch (Exception e) {
            return "";
        }
    }

    @Keep
    @Function
    public String t2s(String text) {
        try {
            return Trans.t2s(false, text);
        } catch (Exception e) {
            return "";
        }
    }

    @Keep
    @Function
    public String aesX(String mode, boolean encrypt, String input, boolean inBase64, String key, String iv, boolean outBase64) {
        String result = Crypto.aes(mode, encrypt, input, inBase64, key, iv, outBase64);
        //LOG.e("aesX",String.format("mode:%s\nencrypt:%s\ninBase64:%s\noutBase64:%s\nkey:%s\niv:%s\ninput:\n%s\nresult:\n%s", mode, encrypt, inBase64, outBase64, key, iv, input, result));
        return result;
    }

    @Keep
    @Function
    public String rsaX(String mode, boolean pub, boolean encrypt, String input, boolean inBase64, String key, boolean outBase64) {
        String result = Crypto.rsa(pub, encrypt, input, inBase64, key, outBase64);
        //LOG.e("aesX",String.format("mode:%s\npub:%s\nencrypt:%s\ninBase64:%s\noutBase64:%s\nkey:\n%s\ninput:\n%s\nresult:\n%s", mode, pub, encrypt, inBase64, outBase64, key, input, result));
        return result;
    }

    @Keep
    @Function
    public String rsaEncrypt(String data, String key) {
        return  rsaEncrypt(data, key, null);
    }
    /**
     * RSA 加密
     *
     * @param data    要加密的数据
     * @param key     密钥，type 为 1 则公钥，type 为 2 则私钥
     * @param options 加密的选项，包含加密配置和类型：{ config: "RSA/ECB/PKCS1Padding", type: 1, long: 1 }
     *                config 加密的配置，默认 RSA/ECB/PKCS1Padding （可选）
     *                type 加密类型，1 公钥加密 私钥解密，2 私钥加密 公钥解密（可选，默认 1）
     *                long 加密方式，1 普通，2 分段（可选，默认 1）
     *                block 分段长度，false 固定117，true 自动（可选，默认 true ）
     * @return 返回加密结果
     */

    @Keep
    @Function
    public String rsaEncrypt(String data, String key, JSObject options) {
        int mLong = 1;
        int mType = 1;
        boolean mBlock = true;
        String mConfig = null;
        if (options != null) {
            JSONObject op = JSUtils.toJsonObject(options);
            if (op.has("config")) {
                try {
                    mConfig = (String) op.get("config");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (op.has("type")) {
                try {
                    mType = ((Double) op.get("type")).intValue();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (op.has("long")) {
                try {
                    mLong = ((Double) op.get("long")).intValue();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (op.has("block")) {
                try {
                    mBlock = (Boolean) op.get("block");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            switch (mType) {
                case 1:
                    if (mConfig != null) {
                        return RSAEncrypt.encryptByPublicKey(data, key, mConfig, mLong, mBlock);
                    } else {
                        return RSAEncrypt.encryptByPublicKey(data, key, mLong, mBlock);
                    }
                case 2:
                    if (mConfig != null) {
                        return RSAEncrypt.encryptByPrivateKey(data, key, mConfig, mLong, mBlock);
                    } else {
                        return RSAEncrypt.encryptByPrivateKey(data, key, mLong, mBlock);
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    @Keep
    @Function
    public String rsaDecrypt(String encryptBase64Data, String key) {
        return  rsaDecrypt(encryptBase64Data, key, null);
    }

    /**
     * RSA 解密
     *
     * @param encryptBase64Data 加密后的 Base64 字符串
     * @param key               密钥，type 为 1 则私钥，type 为 2 则公钥
     * @param options           解密的选项，包含解密配置和类型：{ config: "RSA/ECB/PKCS1Padding", type: 1, long: 1 }
     *                          config 解密的配置，默认 RSA/ECB/PKCS1Padding （可选）
     *                          type 解密类型，1 公钥加密 私钥解密，2 私钥加密 公钥解密（可选，默认 1）
     *                          long 解密方式，1 普通，2 分段（可选，默认 1）
     *                          block 分段长度，false 固定128，true 自动（可选，默认 true ）
     * @return 返回解密结果
     */
    @Keep
    @Function
    public String rsaDecrypt(String encryptBase64Data, String key, JSObject options) {
        int mLong = 1;
        int mType = 1;
        boolean mBlock = true;
        String mConfig = null;
        if (options != null) {
            JSONObject op = JSUtils.toJsonObject(options);
            if (op.has("config")) {
                try {
                    mConfig = (String) op.get("config");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (op.has("type")) {
                try {
                    mType = ((Double) op.get("type")).intValue();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (op.has("long")) {
                try {
                    mLong = ((Double) op.get("long")).intValue();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (op.has("block")) {
                try {
                    mBlock = (Boolean) op.get("block");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            switch (mType) {
                case 1:
                    if (mConfig != null) {
                        return RSAEncrypt.decryptByPrivateKey(encryptBase64Data, key, mConfig, mLong, mBlock);
                    } else {
                        return RSAEncrypt.decryptByPrivateKey(encryptBase64Data, key, mLong, mBlock);
                    }
                case 2:
                    if (mConfig != null) {
                        return RSAEncrypt.decryptByPublicKey(encryptBase64Data, key, mConfig, mLong, mBlock);
                    } else {
                        return RSAEncrypt.decryptByPublicKey(encryptBase64Data, key, mLong, mBlock);
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private JSObject req(String url, Req req) {
        Pending item = new Pending(null);
        try {
            item.call = Connect.to(url, req, networkTag);
            if (!live(item)) throw new IOException("QuickJS call cancelled");
            try (Response res = item.call.execute()) {
                return Connect.success(runtime, req, res);
            }
        } catch (Exception e) {
            return Connect.error(runtime, e);
        } finally {
            item.release();
        }
    }
    @Keep
    @Function
    public JSObject _http(String url, JSObject options) {
        Req req = Req.objectFrom(JSUtils.toJsonObject(options).toString());
        JSFunction complete = options == null ? null : options.getJSFunction("complete");
        if (complete == null) return req(url, req);
        complete.hold();
        complete.release(); // Keep only the held reference owned by Pending.
        Pending item = new Pending(complete);
        try {
            item.call = Connect.to(url, req, networkTag);
            if (!live(item)) {
                item.release();
                return null;
            }
            item.call.enqueue(getCallback(item, req));
        } catch (Exception error) {
            JSObject response = Connect.error(runtime, error);
            try {
                complete.callVoid(response);
            } finally {
                response.release();
                item.release();
            }
        }
        return null;
    }

    @Keep
    @Function
    public void setTimeout(JSFunction func, Integer delay) {
        if (closed.get() || (scope != null && scope.isDone())) return;
        func.hold();
        Pending item = new Pending(func);
        item.task = new TimerTask() {
            @Override
            public void run() {
                post(item, () -> item.function.callVoid());
            }
        };
        try {
            timer.schedule(item.task, Math.max(0, delay == null ? 0 : delay));
        } catch (IllegalStateException error) {
            item.release();
        }
    }

    private Callback getCallback(Pending item, Req req) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response res) {
                item.response = res;
                if (!post(item, () -> {
                    JSObject response = Connect.success(runtime, req, res);
                    try {
                        item.function.callVoid(response);
                    } finally {
                        response.release();
                    }
                })) res.close();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                post(item, () -> {
                    JSObject response = Connect.error(runtime, e);
                    try {
                        item.function.callVoid(response);
                    } finally {
                        response.release();
                    }
                });
            }
        };
    }
    @Keep
    // 声明用于依赖注入的 QuickJSContext
    @ContextSetter
    public void setJSContext(QuickJSContext runtime) {
        this.runtime = runtime;
    }

}
