package com.github.catvod.crawler.js;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;

import com.whl.quickjs.wrapper.ContextSetter;
import com.whl.quickjs.wrapper.Function;
import com.whl.quickjs.wrapper.JSArray;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSMethod;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.JSUtils;
import com.whl.quickjs.wrapper.QuickJSContext;
import com.whl.quickjs.wrapper.UriUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;


public class JsSpider extends Spider {

    private static final byte BYTECODE_VERSION = 67;
    private static final String EMPTY_MODULE_CODE =
            "const empty = null;\n" +
            "export default empty;\n" +
            "export const JSEncrypt = empty;\n" +
            "export const NodeRSA = empty;\n" +
            "export const pako = empty;\n" +
            "export const JSON5 = empty;\n" +
            "export const mb = empty;\n" +
            "export const parse = empty;\n" +
            "export const stringify = empty;\n" +
            "export const inflate = empty;\n" +
            "export const deflate = empty;\n" +
            "export const gzip = empty;\n" +
            "export const ungzip = empty;\n" +
            "export const encrypt = empty;\n" +
            "export const decrypt = empty;";

    private final ExecutorService executor;
    private final Class<?> dex;
    private QuickJSContext ctx;
    private JSObject jsObject;
    private final String key;
    private final String api;
    private boolean cat;
    private byte[] emptyModuleBytecode;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final Object lifecycle = new Object();
    private final Object networkTag = new Object();
    private final Map<Async.Result, Future<Async.Result>> calls = new ConcurrentHashMap<>();
    private volatile Global global;

    public JsSpider(String key, String api, Class<?> cls) throws Exception {
        this.key = "J" + MD5.encode(key);
        this.executor = Executors.newSingleThreadExecutor();
        this.api = api;
        this.dex = cls;
        try {
            initializeJS();
        } catch (Exception error) {
            destroy();
            throw error;
        }
    }
    public void cancelByTag() {
        synchronized (lifecycle) {
            cancelCalls(new CancellationException("QuickJS calls cancelled"));
        }
        Connect.cancelByTag(networkTag);
    }

    private JSObject createObject() {
        return ctx.createNewJSObject();
    }

    private JSArray createArray() {
        return ctx.createNewJSArray();
    }

    private void set(JSObject object, String name, Object value) {
        ctx.setProperty(object, name, value);
    }

    private Object get(JSObject object, String name) {
        return ctx.getProperty(object, name);
    }

    private JSONArray toJsonArray(JSArray array) {
        return JSUtils.toJsonArray(array);
    }

    private void bind(JSObject target, Object receiver) {
        for (Method method : receiver.getClass().getMethods()) {
            if (method.isAnnotationPresent(ContextSetter.class)) {
                try {
                    method.invoke(receiver, ctx);
                } catch (Throwable ignored) {
                }
            }
        }
        for (Method method : receiver.getClass().getMethods()) {
            if (!isQuickJsMethod(method)) continue;
            String name = methodName(method);
            set(target, name, new JSCallFunction() {
                @Override
                public Object call(Object... args) {
                    try {
                        return method.invoke(receiver, args);
                    } catch (Throwable error) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        ctx.evaluate("throw new Error(" + JSONObject.quote(cause.toString()) + ")");
                        return null;
                    } finally {
                        if (args != null) {
                            for (Object arg : args) if (arg instanceof JSObject) ((JSObject) arg).release();
                        }
                    }
                }
            });
        }
    }

    private boolean isQuickJsMethod(Method method) {
        return method.isAnnotationPresent(Function.class) || method.isAnnotationPresent(JSMethod.class);
    }

    private String methodName(Method method) {
        Function function = method.getAnnotation(Function.class);
        if (function != null && !TextUtils.isEmpty(function.name())) return function.name();
        return method.getName();
    }

    private Object call(String func, Object... args) {
        return callWithArgs(func, () -> args);
    }

    private Object callWithArgs(String func, Callable<Object[]> arguments) {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Async.DEFAULT_TIMEOUT_MILLIS);
        final Async.Result result = new Async.Result();
        final Future<Async.Result> invocation;
        synchronized (lifecycle) {
            if (destroyed.get()) throw new IllegalStateException("QuickJS spider is destroyed");
            invocation = executor.submit(() -> {
                if (result.isDone()) return result;
                Object[] args = null;
                try {
                    if (jsObject == null) throw new IllegalStateException("QuickJS spider is not initialized");
                    global.enter(result);
                    args = arguments.call();
                    return Async.run(jsObject, func, args, result);
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                    return result;
                } finally {
                    if (args != null) {
                        for (Object arg : args) if (arg instanceof JSObject) ((JSObject) arg).release();
                    }
                    if (global != null) global.leave();
                }
            });
            calls.put(result, invocation);
        }
        try {
            // Only the calling thread waits; callbacks can now run on the JS executor.
            Async.Result pending = invocation.get(remaining(deadline), TimeUnit.NANOSECONDS);
            return pending.get(remaining(deadline), TimeUnit.NANOSECONDS);
        } catch (InterruptedException error) {
            result.completeExceptionally(error);
            invocation.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(func, error);
        } catch (TimeoutException error) {
            result.completeExceptionally(error);
            invocation.cancel(true);
            throw failure(func, error);
        } catch (ExecutionException error) {
            result.completeExceptionally(error.getCause());
            throw failure(func, error.getCause());
        } catch (Exception error) {
            result.completeExceptionally(error);
            throw failure(func, error);
        } finally {
            Global current = global;
            if (current != null) current.cancel(result);
            synchronized (lifecycle) {
                if (!destroyed.get()) executor.submit(() -> release(result));
            }
        }
    }

    private static long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }

    private static RuntimeException failure(String func, Throwable cause) {
        return new IllegalStateException("QuickJS " + func + " failed", cause);
    }

    private void release(Async.Result result) {
        try {
            try {
                if (global != null) global.release(result);
            } finally {
                result.release();
            }
        } finally {
            calls.remove(result);
        }
    }

    private void cancelCalls(Throwable error) {
        for (Map.Entry<Async.Result, Future<Async.Result>> entry : calls.entrySet()) {
            entry.getKey().completeExceptionally(error);
            entry.getValue().cancel(true);
            if (global != null) global.cancel(entry.getKey());
        }
    }

    private JSObject cfg(String ext) {
        JSObject cfg = createObject();
        set(cfg, "stype", 3);
        set(cfg, "skey", TextUtils.isEmpty(siteKey) ? key : siteKey);
        if (Json.invalid(ext)) set(cfg, "ext", ext);
        else {
            JSObject value = (JSObject) ctx.parse(ext);
            try {
                set(cfg, "ext", value);
            } finally {
                value.release();
            }
        }
        return cfg;
    }

    @Override
    public void init(Context context, String extend) {
        callWithArgs("init", () -> new Object[]{cat ? cfg(extend) : Json.valid(extend) ? ctx.parse(extend) : extend});
    }

    @Override
    public String homeContent(boolean filter) {
        return (String) call("home", filter);
    }

    @Override
    public String homeVideoContent() {
        return (String) call("homeVod");
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        return (String) callWithArgs("category", () -> new Object[]{tid, pg, filter, new JSUtils<String>().toObj(ctx, extend)});
    }

    @Override
    public String detailContent(List<String> ids) {
        return (String) call("detail", ids.get(0));
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return (String) call("search", key, quick);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return (String) callWithArgs("play", () -> new Object[]{flag, id, new JSUtils<String>().toArray(ctx, vipFlags)});
    }

    @Override
    public String liveContent(String url) {
        return (String) call("live", url);
    }

    @Override
    public boolean manualVideoCheck() {
        return Boolean.TRUE.equals(call("sniffer"));
    }

    @Override
    public boolean isVideoFormat(String url) {
        return Boolean.TRUE.equals(call("isVideo", url));
    }

    @Override
    public Object[] proxyLocal(Map<String, String> params) {
        try {
            return "catvod".equals(params.get("from")) ? proxy2(params) : proxy1(params);
        } catch (Exception error) {
            throw failure("proxy", error);
        }
    }

    @Override
    public String action(String action) {
        return (String) call("action", action);
    }

    @Override
    public void destroy() {
        synchronized (lifecycle) {
            if (!destroyed.compareAndSet(false, true)) return;
            cancelCalls(new CancellationException("QuickJS spider destroyed"));
            if (global != null) global.stop();
            executor.submit(() -> {
                try {
                    try {
                        if (global != null) global.release();
                        for (Async.Result result : calls.keySet()) result.release();
                        if (jsObject != null) jsObject.release();
                    } finally {
                        calls.clear();
                        jsObject = null;
                        if (ctx != null) ctx.destroy();
                        ctx = null;
                    }
                } catch (Throwable error) {
                    LOG.i("echo-js-destroy-error " + error.getMessage());
                } finally {
                    executor.shutdown();
                }
            });
        }
    }

    private static final String SPIDER_STRING_CODE = "import * as spider from '%s'\n\n" +
            "if (!globalThis.__JS_SPIDER__) {\n" +
            "    if (spider.__jsEvalReturn) {\n" +
            "        globalThis.req = http\n" +
            "        globalThis.__JS_SPIDER__ = spider.__jsEvalReturn()\n" +
            "        globalThis.__JS_SPIDER__.is_cat = true\n" +
            "    } else if (spider.default) {\n" +
            "        globalThis.__JS_SPIDER__ = typeof spider.default === 'function' ? spider.default() : spider.default\n" +
            "    }\n" +
            "}\n";
    private void initializeJS() throws Exception {
        Future<?> initialization = executor.submit(() -> {
            if (ctx == null) createCtx();
            if (dex != null) createDex();

            String content = FileUtils.loadModule(api);            
            if (isInvalidModuleContent(content)) {return null;}
            
            if(content.startsWith("//bb")){
                cat = true;
                byte[] b = Base64.decode(content.replace("//bb",""), 0);
                try {
                    ctx.execute(byteFF(b));
                    ctx.evaluateModule(String.format(SPIDER_STRING_CODE, key + ".js") + "globalThis." + key + " = globalThis.__JS_SPIDER__;", "tv_box_root.js");
                } catch (Throwable th) {
                    LOG.i("echo-bytecode-execute-error " + api + ", msg=" + th.getMessage());
                    return null;
                }
                //ctx.execute(byteFF(b), key + ".js","__jsEvalReturn");
                //ctx.evaluate("globalThis." + key + " = __JS_SPIDER__;");
            } else {
                if (content.contains("__JS_SPIDER__")) {
                    content = content.replaceAll("__JS_SPIDER__\\s*=", "export default ");
                }
                String moduleExtName = "default";
                if (content.contains("__jsEvalReturn") && !content.contains("export default")) {
                    moduleExtName = "__jsEvalReturn";
                    cat = true;
                }
                try {
                    ctx.evaluateModule(content, api);
                    ctx.evaluateModule(String.format(SPIDER_STRING_CODE, api) + "globalThis." + key + " = globalThis.__JS_SPIDER__;", "tv_box_root.js");
                } catch (Throwable th) {
                    LOG.i("echo-evaluateModule-error " + api + ", msg=" + th.getMessage());
                    return null;
                }
                //ctx.evaluateModule(content, api, moduleExtName);
                //ctx.evaluate("globalThis." + key + " = __JS_SPIDER__;");                
            }
            jsObject = (JSObject) get(ctx.getGlobalObject(), key);
            return null;
        });
        try {
            initialization.get(Async.DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            initialization.cancel(true);
            Thread.currentThread().interrupt();
            throw error;
        } catch (TimeoutException error) {
            initialization.cancel(true);
            throw error;
        }
    }

    public static byte[] byteFF(byte[] bytes) {
        byte[] newBt = new byte[bytes.length - 4];
        newBt[0] = BYTECODE_VERSION;
        System.arraycopy(bytes, 5, newBt, 1, bytes.length - 5);
        return newBt;
    }

    private void createCtx() {
        ctx = QuickJSContext.create();
        emptyModuleBytecode = ctx.compileModule(EMPTY_MODULE_CODE, "empty.js");
        ctx.setModuleLoader(new QuickJSContext.BytecodeModuleLoader() {
            @Override
            public byte[] getModuleBytecode(String moduleName) {
                String ss = FileUtils.loadModule(moduleName);
                if (isInvalidModuleContent(ss)) {
                    return compileEmptyModule(moduleName);
                }
                if(ss.startsWith("//DRPY")){
                    try {
                        byte[] bytes = bytecode(Base64.decode(ss.replace("//DRPY",""), Base64.URL_SAFE));
                        return bytes == null ? compileEmptyModule(moduleName) : bytes;
                    } catch (Throwable th) {
                        LOG.i("echo-bytecode-module-error " + moduleName + ", msg=" + th.getMessage());
                        return compileEmptyModule(moduleName);
                    }
                } else if(ss.startsWith("//bb")){
                    try {
                        byte[] b = Base64.decode(ss.replace("//bb",""), 0);
                        return byteFF(b);
                    } catch (Throwable th) {
                        LOG.i("echo-bytecode-module-error " + moduleName + ", msg=" + th.getMessage());
                        return compileEmptyModule(moduleName);
                    }
                } else {
                    return compileModule(moduleName, ss);
                }
            }

            @Override
            public String moduleNormalizeName(String moduleBaseName, String moduleName) {
                return UriUtil.resolve(moduleBaseName, moduleName);
            }
        });
        ctx.setConsole(new QuickJSContext.Console() {
            @Override
            public void log(String s) {
                LOG.i("echo-QuJs " + s);
            }
            @Override
            public void info(String s) {
                LOG.i("echo-QuJs " + s);
            }
            @Override
            public void warn(String s) {
                LOG.i("echo-QuJs " + s);
            }
            @Override
            public void error(String s) {
                LOG.i("echo-QuJs " + s);
            }
        });

        global = new Global(executor, networkTag);
        bind(ctx.getGlobalObject(), global);

        JSObject local = createObject();
        set(ctx.getGlobalObject(), "local", local);
        bind(local, new local());

        String net = FileUtils.loadModule("net.js");
        if (!isInvalidModuleContent(net)) ctx.getGlobalObject().getContext().evaluate(net);
        preloadTemplate();
    }

    private byte[] compileEmptyModule(String moduleName) {
        LOG.i("echo-getModuleBytecode empty :" + moduleName);
        return emptyModuleBytecode;
    }

    private static byte[] bytecode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        if (bytes[0] == BYTECODE_VERSION) return bytes;
        LOG.i("echo-bytecode-version-mismatch actual=" + bytes[0] + ", expected=" + BYTECODE_VERSION);
        return null;
    }

    private byte[] compileModule(String moduleName, String content) {
        try {
            if (moduleName != null && moduleName.contains("cheerio.min.js")) {
                byte[] bytecode = ctx.compileModule(content, "cheerio.min.js");
                FileUtils.setCacheByte("cheerio.min", bytecode);
                return bytecode;
            } else if (moduleName != null && moduleName.contains("crypto-js.js")) {
                byte[] bytecode = ctx.compileModule(content, "crypto-js.js");
                FileUtils.setCacheByte("crypto-js", bytecode);
                return bytecode;
            }
            return ctx.compileModule(content, moduleName);
        } catch (Throwable th) {
            LOG.i("echo-compileModule-error " + moduleName + ", msg=" + th.getMessage());
            return compileEmptyModule(moduleName);
        }
    }

    private boolean isInvalidModuleContent(String content) {
        if (TextUtils.isEmpty(content)) return true;
        String trim = content.trim();
        if (trim.startsWith("\uFEFF")) trim = trim.substring(1).trim();
        String lower = trim.toLowerCase();
        return lower.startsWith("<")
                || lower.startsWith("{\"code\":404")
                || lower.startsWith("404")
                || lower.startsWith("not found");
    }

    private void preloadTemplate() {
        try {
            String template = "import tpl from '模板.js';\n"
                    + "globalThis.muban = tpl.muban;\n"
                    + "globalThis.getMubans = tpl.getMubans;";
            ctx.evaluateModule(template, "tv_box_template.js");
        } catch (Throwable th) {
            LOG.i("echo-preloadTemplate-error " + th.getMessage());
        }
    }

    private void createDex() {
        try {
            JSObject obj = createObject();
            Class<?> clz = dex;
            Class<?>[] classes = clz.getDeclaredClasses();
            set(ctx.getGlobalObject(), "jsapi", obj);
            if (classes.length == 0) invokeSingle(clz, obj);
            if (classes.length >= 1) invokeMultiple(clz, obj);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void invokeSingle(Class<?> clz, JSObject jsObj) throws Throwable {
        invoke(clz, jsObj, clz.getDeclaredConstructor(QuickJSContext.class).newInstance(ctx));
    }

    private void invokeMultiple(Class<?> clz, JSObject jsObj) throws Throwable {
        for (Class<?> subClz : clz.getDeclaredClasses()) {
            Object javaObj = subClz.getDeclaredConstructor(clz).newInstance(clz.getDeclaredConstructor(QuickJSContext.class).newInstance(ctx));
            JSObject subObj = createObject();
            invoke(subClz, subObj, javaObj);
            set(jsObj, subClz.getSimpleName(), subObj);
        }
    }

    private void invoke(Class<?> clz, JSObject jsObj, Object javaObj) {
        for (Method method : clz.getMethods()) {
            if (!isQuickJsMethod(method)) continue;
            invoke(jsObj, method, javaObj);
        }
    }

    private void invoke(JSObject jsObj, Method method, Object javaObj) {
        set(jsObj, methodName(method), new JSCallFunction() {
            @Override
            public Object call(Object... objects) {
                try {
                    return method.invoke(javaObj, objects);
                } catch (Throwable e) {
                    return null;
                }
            }
        });
    }

    private String getContent() {
        String global = "globalThis." + key;
        String content = FileUtils.loadModule(api);
        if (isInvalidModuleContent(content)) {return null;}
        if (content.contains("__jsEvalReturn")) {
            ctx.evaluate("req = http");
            return content.concat(global).concat(" = __jsEvalReturn()");
        } else if (content.contains("__JS_SPIDER__")) {
            return content.replace("__JS_SPIDER__", global);
        } else {
            return content.replaceAll("export default.*?[{]", global + " = {");
        }
    }

    private Object[] proxy1(Map<String, String> params) throws Exception {
        String json = (String) callWithArgs("proxy", () -> new Object[]{new JSUtils<String>().toObj(ctx, params)});
        JSONArray array = new JSONArray(json);
        boolean headerAvailable = array.length() > 3 && array.opt(3) != null;
        Object[] result = new Object[4];
        result[0] = array.opt(0);
        result[1] = array.opt(1);
        result[2] = getStream(array.opt(2));
        result[3] = headerAvailable ? getHeader(array.opt(3)) : null;
        if (array.length() > 4) {
            try {
                if ( array.optInt(4) == 1) {
                    String content = array.optString(2);
                    if (content.contains("base64,")) content = content.substring(content.indexOf("base64,") + 7);
                    result[2] = new ByteArrayInputStream(Base64.decode(content, Base64.DEFAULT));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private Map<String, String> getHeader(Object headerRaw) {
        Map<String, String> headers = new HashMap<>();
        if (headerRaw instanceof JSONObject) {
            JSONObject json = (JSONObject) headerRaw;
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                headers.put(key, json.optString(key));
            }
        } else if (headerRaw instanceof String) {
            try {
                JSONObject json = new JSONObject((String) headerRaw);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    headers.put(key, json.optString(key));
                }
            } catch (JSONException e) {
                LOG.i("getHeader: 无法解析 String 为 JSON"+ e);
            }
        } else if (headerRaw instanceof Map) {
            //noinspection unchecked
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) headerRaw).entrySet()) {
                headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return headers;
    }
    
    private Object[] proxy2(Map<String, String> params) throws Exception {
        String url = params.get("url");
        String header = params.get("header");
        String json = (String) callWithArgs("proxy", () -> new Object[]{
                new JSUtils<String>().toArray(ctx, Arrays.asList(url.split("/"))), ctx.parse(header)});
        Res res = Res.objectFrom(json);
        String contentType = res.getContentType();
        if (TextUtils.isEmpty(contentType)) contentType = "application/octet-stream";
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = contentType;
        if (res.getBuffer() == 2) {
            result[2] = new ByteArrayInputStream(Base64.decode(res.getContent(), Base64.DEFAULT));
        } else {
            result[2] = new ByteArrayInputStream(res.getContent().getBytes());
        }
        return result;
    }
    private ByteArrayInputStream getStream(Object o) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            byte[] bytes = new byte[a.length()];
            for (int i = 0; i < a.length(); i++) bytes[i] = (byte) a.optInt(i);
            return new ByteArrayInputStream(bytes);
        } else {
            return new ByteArrayInputStream(o.toString().getBytes());
        }
    }
}
