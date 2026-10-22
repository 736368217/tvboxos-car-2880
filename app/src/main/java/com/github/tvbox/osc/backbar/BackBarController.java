package com.github.tvbox.osc.backbar;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * 应用内返回杠（只有杠，没有悬浮球）。
 *
 * <ul>
 *   <li>控件挂在 Activity 的 DecorView 最上层，不申请悬浮窗/无障碍权限；</li>
 *   <li>页面进入或用户触摸屏幕时出现，默认 4 秒后自动淡出；</li>
 *   <li>点击杠返回上一页；空白区域永远不拦截触摸；</li>
 *   <li>隐藏状态下第一次触摸只唤醒，不会误触返回。</li>
 * </ul>
 *
 * <p>接入方式：在 Application.onCreate() 中调用一次
 * {@link #install(Application)}，然后可按需调用
 * {@link #setSide(int)}、{@link #setAutoHideMillis(long)} 等配置方法。
 * 不需要修改任何布局 XML 或 AndroidManifest 权限。
 *
 * <p>注意：这是 Activity 窗口内的 View。独立的 Dialog / PopupWindow
 * 属于另一个 Window，默认不会被它覆盖；如果必须覆盖这类窗口，需要把
 * 同样的 Host 注入对应 Dialog 的 DecorView，或改用系统悬浮窗（不推荐）。
 */
public final class BackBarController {

    /** 标记：启动页、引导页等不需要返回杠的 Activity 实现此接口即可。 */
    public interface IgnoreBackBar {
    }

    /**
     * 可选：Activity 可以在执行系统返回前先关闭自己的浮层/面板。
     * 返回 true 表示已消费本次返回。
     */
    public interface BackInterceptor {
        boolean onInterceptBack();
    }

    private static final String HOST_TAG = "app.backbar.host";

    private static final long DEFAULT_AUTO_HIDE_MS = BackBarInteractionPolicy.DEFAULT_AUTO_HIDE_MS;
    private static final int DEFAULT_SIDE = Gravity.START;
    private static final float DEFAULT_BAR_WIDTH_DP = 10f;
    private static final float DEFAULT_BAR_HEIGHT_DP = 84f;
    private static final float DEFAULT_HIT_WIDTH_DP = 32f;
    private static final float DEFAULT_HIT_HEIGHT_DP = 120f;
    private static final float DEFAULT_EDGE_MARGIN_DP = 2f;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, BackBarHost> HOSTS = new WeakHashMap<>();
    private static WeakReference<Application> application = new WeakReference<>(null);
    private static WeakReference<Activity> lastResumed = new WeakReference<>(null);

    private static boolean enabled = true;
    private static int side = DEFAULT_SIDE;
    private static long autoHideMs = DEFAULT_AUTO_HIDE_MS;
    private static float barWidthDp = DEFAULT_BAR_WIDTH_DP;
    private static float barHeightDp = DEFAULT_BAR_HEIGHT_DP;
    private static float hitWidthDp = DEFAULT_HIT_WIDTH_DP;
    private static float hitHeightDp = DEFAULT_HIT_HEIGHT_DP;
    private static float edgeMarginDp = DEFAULT_EDGE_MARGIN_DP;

    private static final Application.ActivityLifecycleCallbacks LIFECYCLE =
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {
                    attach(activity, false);
                }

                @Override public void onActivityStarted(Activity activity) {
                }

                @Override public void onActivityResumed(Activity activity) {
                    lastResumed = new WeakReference<>(activity);
                    attach(activity, true);
                }

                @Override public void onActivityPaused(Activity activity) {
                    BackBarHost host = find(activity);
                    if (host != null) host.pause();
                }

                @Override public void onActivityStopped(Activity activity) {
                }

                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {
                }

                @Override public void onActivityDestroyed(Activity activity) {
                    if (lastResumed.get() == activity) lastResumed = new WeakReference<>(null);
                    detach(activity);
                }
            };

    private BackBarController() {
    }

    /** 幂等；建议在自定义 Application.onCreate() 中调用一次。 */
    public static void install(Application app) {
        if (app == null) return;
        if (application.get() == app) return;
        Application previous = application.get();
        if (previous != null) previous.unregisterActivityLifecycleCallbacks(LIFECYCLE);
        app.registerActivityLifecycleCallbacks(LIFECYCLE);
        application = new WeakReference<>(app);
    }

    /** 全局开关；关闭后会移除所有已挂载的返回杠。 */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            for (Activity activity : new ArrayList<>(HOSTS.keySet())) detach(activity);
        } else {
            Activity activity = lastResumed.get();
            if (activity != null) attach(activity, true);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 只接受 Gravity.START 或 Gravity.END（默认 START）。 */
    public static void setSide(int gravity) {
        side = gravity == Gravity.END ? Gravity.END : Gravity.START;
        refreshAll();
    }

    /** 4 seconds by default. Zero/invalid legacy values normalize to 4 s, never permanent. */
    public static void setAutoHideMillis(long millis) {
        autoHideMs = BackBarInteractionPolicy.normalizeAutoHideMillis(millis);
        refreshAll();
    }

    public static long getAutoHideMillis() {
        return autoHideMs;
    }

    /** 单位 dp。视觉杠的宽高。 */
    public static void setBarSizeDp(float widthDp, float heightDp) {
        barWidthDp = Math.max(2f, widthDp);
        barHeightDp = Math.max(24f, heightDp);
        refreshAll();
    }

    /** 单位 dp。可点击热区，建议不小于 32 x 96 dp。 */
    public static void setHitSizeDp(float widthDp, float heightDp) {
        hitWidthDp = Math.max(24f, widthDp);
        hitHeightDp = Math.max(64f, heightDp);
        refreshAll();
    }

    /** 杠距离屏幕边缘的视觉留白，单位 dp。 */
    public static void setEdgeMarginDp(float marginDp) {
        edgeMarginDp = Math.max(0f, marginDp);
        refreshAll();
    }

    /** 立即显示并重置自动隐藏计时。 */
    public static void show(Activity activity) {
        BackBarHost host = find(activity);
        if (host == null) {
            attach(activity, true);
            host = find(activity);
        }
        if (host != null) {
            host.bringToFront();
            host.resume();
        }
    }

    /** 立即隐藏，直到下一次 show() 或页面重新 resume。 */
    public static void hide(Activity activity) {
        BackBarHost host = find(activity);
        if (host != null) host.hideNow();
    }

    /** 应用配置变化（侧边、尺寸、自动隐藏时间等）。 */
    public static void refresh(Activity activity) {
        BackBarHost host = find(activity);
        if (host != null) applyConfig(host);
    }

    private static void refreshAll() {
        for (BackBarHost host : new ArrayList<>(HOSTS.values())) applyConfig(host);
    }

    private static void applyConfig(BackBarHost host) {
        host.applyConfig(side, autoHideMs, barWidthDp, barHeightDp,
                hitWidthDp, hitHeightDp, edgeMarginDp);
    }

    /** 供 Activity.onResume() 调用；幂等。 */
    public static void attach(Activity activity) {
        attach(activity, true);
    }

    /** 触摸/按键时唤醒返回杠；没有挂载时不做任何事。 */
    public static void wake(Activity activity) {
        BackBarHost host = find(activity);
        if (host != null) host.wake();
    }

    private static void attach(Activity activity, boolean resume) {
        if (activity == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> attach(activity, resume));
            return;
        }
        if (!enabled || activity instanceof IgnoreBackBar) {
            detach(activity);
            return;
        }
        BackBarHost host = find(activity);
        if (host == null) {
            View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            host = new BackBarHost(activity);
            host.setTag(HOST_TAG);
            ((ViewGroup) decor).addView(host, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            HOSTS.put(activity, host);
            host.applyConfig(side, autoHideMs, barWidthDp, barHeightDp,
                    hitWidthDp, hitHeightDp, edgeMarginDp);
        }
        host.bringToFront();
        if (Build.VERSION.SDK_INT >= 21) host.setTranslationZ(1000f);
        if (resume) host.resume();
    }

    /** Shared dispatcher so the bar uses the same modal/fragment/WebView order as a Back key. */
    static void performBack(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            if (activity instanceof BackInterceptor
                    && ((BackInterceptor) activity).onInterceptBack()) return;
            WebView web = focusedBackWebView(activity.getWindow().getDecorView());
            if (web != null) {
                web.goBack();
                return;
            }
            FragmentManager stack = fragmentBackStack(activity);
            if (stack != null) {
                if (!stack.isStateSaved()) stack.popBackStackImmediate();
                return;
            }
            activity.onBackPressed();
        } catch (RuntimeException error) {
            Log.w("BackBar", "Back handler rejected navigation", error);
        }
    }

    private static WebView focusedBackWebView(View view) {
        if (view == null || view instanceof BackBarHost || !view.isShown()) return null;
        if (view instanceof WebView && view.hasFocus() && ((WebView) view).canGoBack()) {
            return (WebView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView web = focusedBackWebView(group.getChildAt(i));
                if (web != null) return web;
            }
        }
        return null;
    }

    private static FragmentManager fragmentBackStack(Activity activity) {
        return activity instanceof FragmentActivity
                ? fragmentBackStack(((FragmentActivity) activity).getSupportFragmentManager()) : null;
    }

    private static FragmentManager fragmentBackStack(FragmentManager manager) {
        if (manager == null) return null;
        Fragment primary = manager.getPrimaryNavigationFragment();
        if (primary != null && primary.isAdded()) {
            FragmentManager child = fragmentBackStack(primary.getChildFragmentManager());
            if (child != null) return child;
        }
        return manager.getBackStackEntryCount() > 0 ? manager : null;
    }

    private static void detach(Activity activity) {
        if (activity == null) return;
        BackBarHost host = HOSTS.remove(activity);
        if (host == null) host = find(activity);
        if (host == null) return;
        host.dispose();
        if (host.getParent() instanceof ViewGroup) {
            ((ViewGroup) host.getParent()).removeView(host);
        }
    }

    private static BackBarHost find(Activity activity) {
        if (activity == null || activity.getWindow() == null) return null;
        View decor = activity.getWindow().getDecorView();
        View tagged = decor.findViewWithTag(HOST_TAG);
        return tagged instanceof BackBarHost ? (BackBarHost) tagged : null;
    }
}
