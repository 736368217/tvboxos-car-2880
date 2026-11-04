package com.github.tvbox.osc.debug;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 仅 debug 源集的诊断探针：加载配置与 spider.jar 后，在后台线程直接调用某个站点，
 * 把被 spider 吞掉的异常/空结果暴露到日志。
 *
 * <p>不进入 release 交付包。用法：
 * {@code adb shell am start -n com.github.tvbox.osc/com.github.tvbox.osc.debug.SpiderProbeActivity
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
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        setContentView(scroll);

        line("key=" + key);
        line("api_url=" + Hawk.get(HawkConfig.API_URL, "<empty>"));
        line("DOH_URL=" + Hawk.get(HawkConfig.DOH_URL, 0));
        line("sites=" + ApiConfig.get().getSourceBeanList().size());
        if (ApiConfig.get().getSourceBeanList().isEmpty()) {
            line("loading config...");
            ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
                @Override public void success() { line("config OK sites=" + ApiConfig.get().getSourceBeanList().size()); loadJar(); }
                @Override public void retry() { line("config retry"); }
                @Override public void error(String msg) { line("config ERROR " + msg); }
            }, this);
        } else {
            loadJar();
        }
    }

    private void loadJar() {
        String url = ApiConfig.get().getSpider();
        if (url == null || url.isEmpty()) { line("no spider url"); new Thread(this::probe).start(); return; }
        line("loading jar=" + url);
        ApiConfig.get().loadJar(false, url, new ApiConfig.LoadConfigCallback() {
            @Override public void success() { line("jar OK"); new Thread(SpiderProbeActivity.this::probe).start(); }
            @Override public void retry() { line("jar retry"); }
            @Override public void error(String msg) { line("jar ERROR " + msg); new Thread(SpiderProbeActivity.this::probe).start(); }
        });
    }

    /** 注意：spider 内部会同步发 HTTP，必须在后台线程调用，否则 NetworkOnMainThreadException。 */
    private void probe() {
        try {
            SourceBean site = ApiConfig.get().getSource(key);
            if (site == null) {
                for (SourceBean bean : ApiConfig.get().getSourceBeanList()) {
                    if (bean.getApi() != null && bean.getApi().equals(key)) { site = bean; break; }
                }
            }
            if (site == null) { line("RESULT site not found"); return; }
            Spider spider = ApiConfig.get().getCSP(site);
            line("spider=" + (spider == null ? "null" : spider.getClass().getName()));
            String home = spider.homeContent(true);
            line("home len=" + (home == null ? -1 : home.length()));
            String category = spider.categoryContent("2", "1", true, null);
            line("category len=" + (category == null ? -1 : category.length()) + " head=" + head(category));
            line("RESULT OK");
        } catch (Throwable error) {
            line("RESULT THROW " + error);
            Log.e(TAG, "probe failed", error);
        }
    }

    private void line(String text) {
        out.append(text).append('\n');
        Log.i(TAG, text);
        main.post(() -> view.setText(out.toString()));
    }

    private String head(String text) {
        if (text == null) return "null";
        return text.length() <= 400 ? text : text.substring(0, 400) + "…";
    }
}
