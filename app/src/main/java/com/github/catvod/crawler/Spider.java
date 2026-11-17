package com.github.catvod.crawler;

import android.content.Context;

import com.github.tvbox.osc.util.OkGoHelper;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Dns;
import okhttp3.OkHttpClient;

/**
 * 爬虫基类。
 *
 * <p><b>为什么与上游 main 对齐</b>：远程 spider.jar 是照着新版宿主编译的，会用
 * {@link #siteKey}、{@link #initApi(SpiderApi)}、{@link #client()}、{@link #empty}
 * 等成员。老 oldTv 的 Spider 是 abstract、缺这些成员，jar 里的站点在 init/homeContent
 * 阶段就抛 {@code NoSuchFieldError}/{@code NoSuchMethodError}（Error 而非 Exception），
 * 被各站点自己的 catch 吞掉后返回空 —— 表现就是「订阅加载完，列表一片空白、进不到播放」。</p>
 *
 * <p>这里只补契约、不改行为：{@link #safeDns()} 仍返回本工程既有的 DoH 实例，
 * 网络栈不换。</p>
 */
public class Spider {

    /** 空 JSON，供站点复用。 */
    public static JSONObject empty = new JSONObject();

    /** 当前站点在配置里的 key。 */
    public String siteKey;

    protected static Context mContext;

    public void init(Context context) throws Exception {
        mContext = context;
    }

    public void init(Context context, String extend) throws Exception {
        init(context);
    }

    /** 宿主注入的回调（getAddress / multiReq / webParse 等）。 */
    public void initApi(SpiderApi api) {
    }

    public String homeContent(boolean filter) throws Exception {
        return "";
    }

    public String homeVideoContent() throws Exception {
        return "";
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String detailContent(List<String> ids) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick);
    }

    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }

    public boolean isVideoFormat(String url) throws Exception {
        return false;
    }

    public boolean manualVideoCheck() throws Exception {
        return false;
    }

    public String liveContent(String url) throws Exception {
        return "";
    }

    /** 沿用本工程既有的 DoH 实例（老 TV 上没有 OkHttp.dns()）。 */
    public static Dns safeDns() {
        return OkGoHelper.dnsOverHttps;
    }

    /** 站点自建 HTTP 客户端时常用；给一个不依赖 DoH 的默认实现。 */
    public static OkHttpClient client() {
        return OkGoHelper.getDefaultClient();
    }

    public void cancelByTag() {
    }

    public void destroy() {
    }

    public Object[] proxyLocal(Map<String, String> params) throws Exception {
        return null;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        return proxyLocal(params);
    }

    public String action(String action) throws Exception {
        return null;
    }
}
