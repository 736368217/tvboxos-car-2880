package com.github.tvbox.osc.debug;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 仅存在于测试源集的探针：加载配置与 spider.jar 后直接调用某个站点并打印结果。
 *
 * <p>为什么需要：老 TV 版是 32 位包（armeabi-v7a），x86_64 模拟器装不上；放开 ABI 的
 * 测试变体里 IJK 又缺 64 位 so。要在本机验证「订阅里的某个站点到底通不通」，只能绕过
 * TV 遥控界面直接调 spider。本类不进入正式交付包。</p>
 *
 * <p>用法：{@code adb shell am start -n com.github.tvbox.osc/com.github.tvbox.osc.debug.SpiderProbeActivity
 * --es key csp_AnimeGiri}</p>
 */
public class SpiderProbeActivity extends Activity {

    private static final String TAG = "SpiderProbe";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder out = new StringBuilder();
    private String key;
    private TextView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        key = getIntent() == null ? null : getIntent().getStringExtra("key");
        view = new TextView(this);
        view.setTextSize(14f);
        view.setGravity(Gravity.START);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        setContentView(scroll);

        line("ask key=" + key);
        line("hawk api_url=" + Hawk.get(HawkConfig.API_URL, "<empty>"));
        line("sites(before)=" + ApiConfig.get().getSourceBeanList().size());
        if (ApiConfig.get().getSourceBeanList().isEmpty()) {
            line("loading config...");
            ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    line("loadConfig success, sites=" + ApiConfig.get().getSourceBeanList().size());
                    loadJarThenProbe();
                }

                @Override
                public void retry() {
                    line("loadConfig retry");
                }

                @Override
                public void error(String msg) {
                    line("loadConfig ERROR " + msg + ", sites=" + ApiConfig.get().getSourceBeanList().size());
                    flush();
                }
            }, this);
            return;
        }
        loadJarThenProbe();
    }

    private void loadJarThenProbe() {
        final String spiderUrl = ApiConfig.get().getSpider();
        if (spiderUrl == null || spiderUrl.isEmpty()) {
            line("no spider url, probing directly");
            probe();
            return;
        }
        line("loading jar: " + spiderUrl);
        ApiConfig.get().loadJar(false, spiderUrl, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                line("loadJar success");
                main.postDelayed(SpiderProbeActivity.this::probe, 500);
            }

            @Override
            public void retry() {
                line("loadJar retry");
            }

            @Override
            public void error(String msg) {
                line("loadJar ERROR " + msg);
                probe();
            }
        });
    }

    private void line(String text) {
        out.append(text).append('\n');
        Log.i(TAG, text);
        flush();
    }

    private void flush() {
        if (view != null) view.setText(out.toString());
    }

    private void probe() {
        try {
            ApiConfig config = ApiConfig.get();
            line("sites=" + config.getSourceBeanList().size());
            line("spider=" + config.getSpider());
            SourceBean site = config.getSource(key);
            if (site == null) {
                for (SourceBean bean : config.getSourceBeanList()) {
                    if (bean.getApi() != null && bean.getApi().equals(key)) {
                        site = bean;
                        break;
                    }
                }
            }
            if (site == null) {
                line("RESULT: site not found");
                return;
            }
            line("site=" + site.getKey() + " api=" + site.getApi() + " type=" + site.getType());
            Spider spider = config.getCSP(site);
            line("spider class=" + (spider == null ? "null" : spider.getClass().getName()));
            if (spider == null || spider.getClass().getName().contains("SpiderNull")) {
                line("RESULT: spider null (加载失败)");
                return;
            }
            long start = System.currentTimeMillis();
            String home = spider.homeContent(true);
            line("homeContent cost=" + (System.currentTimeMillis() - start) + "ms len="
                    + (home == null ? -1 : home.length()));
            line("body: " + head(home));
            String search = spider.searchContent("火影", false);
            line("searchContent len=" + (search == null ? -1 : search.length()));
            line("search body: " + head(search));
        // 分类列表：首页/站点页真正加载的就是它，比 homeContent 更接近用户看到的结果
        try {
            org.json.JSONObject homeJson = new org.json.JSONObject(home == null ? "{}" : home);
            org.json.JSONArray classes = homeJson.optJSONArray("class");
            if (classes != null && classes.length() > 0) {
                String tid = classes.getJSONObject(0).optString("type_id", "");
                long cstart = System.currentTimeMillis();
                String category = spider.categoryContent(tid, "1", true, null);
                line("categoryContent tid=" + tid + " cost=" + (System.currentTimeMillis() - cstart)
                        + "ms len=" + (category == null ? -1 : category.length()));
                line("category body: " + head(category));
            }
            line("RESULT: OK");
        } catch (Throwable error) {
            line("RESULT: THROW " + error);
            Log.e(TAG, "probe failed", error);
        }
            } catch (Throwable error) {
                line("category THROW " + error);
            }
    }

    private String head(String text) {
        if (text == null) return "null";
        return text.length() <= 500 ? text : text.substring(0, 500) + "…";
    }
}
