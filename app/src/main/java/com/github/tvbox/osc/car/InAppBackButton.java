package com.github.tvbox.osc.car;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.lang.ref.WeakReference;
import java.util.Locale;

/** Ordinary Activity-decor children; no overlay window, service or permission is involved. */
public final class InAppBackButton {
    private static final String HOST_TAG = "tvbox.car.back.host";
    private static final int ATTACH = 0, WAKE = 1, RELEASE = 2, REFRESH = 3;
    private static WeakReference<Application> application = new WeakReference<>(null);

    /** Optional opt-out for splash or other noninteractive Activities. */
    public interface IgnoreInAppBack { }

    /** An Activity can close an in-window panel before the normal navigation chain. */
    public interface BackInterceptor {
        boolean onInterceptInAppBack();
    }

    private static final Application.ActivityLifecycleCallbacks LIFECYCLE =
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, Bundle state) { attach(a); }
                @Override public void onActivityResumed(Activity a) { attach(a); }
                @Override public void onActivityPaused(Activity a) {
                    InAppBackHostLayout host = find(a);
                    if (host != null) host.pause();
                }
                @Override public void onActivityDestroyed(Activity a) { release(a); }
                @Override public void onActivityStarted(Activity a) { }
                @Override public void onActivityStopped(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle state) { }
            };

    private InAppBackButton() { }

    /** Idempotent; the first call also enables automatic mounting/cleanup for later Activities. */
    public static void attach(Activity activity) { update(activity, ATTACH); }

    /** Wake only: never synchronously move a hit box under the initiating page touch. */
    public static void wake(Activity activity) { update(activity, WAKE); }
    public static void setPlaybackHidden(Activity activity, boolean hidden) {
        if (activity == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> setPlaybackHidden(activity, hidden));
            return;
        }
        InAppBackHostLayout host = find(activity);
        if (host != null) host.setPlaybackHidden(hidden);
    }

    public static void release(Activity activity) { update(activity, RELEASE); }

    /** Apply Hawk changes immediately, including size, mode, side, skin and root visibility. */
    public static void refresh(Activity activity) { update(activity, REFRESH); }

    private static void update(Activity activity, int operation) {
        if (activity == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final WeakReference<Activity> owner = new WeakReference<>(activity);
            new Handler(Looper.getMainLooper()).post(() -> update(owner.get(), operation));
            return;
        }
        InAppBackHostLayout host = find(activity);
        if (operation == RELEASE) {
            if (host != null) {
                host.dispose();
                if (host.getParent() instanceof ViewGroup) ((ViewGroup) host.getParent()).removeView(host);
            }
            return;
        }
        if (!alive(activity)) return;
        if (operation == WAKE) {
            if (host != null) host.wake();
            return;
        }
        installLifecycle(activity.getApplication());
        if (!isEnabled() || activity instanceof IgnoreInAppBack) {
            release(activity);
            return;
        }
        boolean created = host == null;
        if (host == null) {
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            host = new InAppBackHostLayout(activity);
            host.setTag(HOST_TAG);
            ((ViewGroup) decor).addView(host, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        host.bringToFront();
        if (operation == ATTACH || created) host.resume();
        else host.refresh();
    }

    private static void installLifecycle(Application app) {
        if (application.get() == app) return;
        Application previous = application.get();
        if (previous != null) previous.unregisterActivityLifecycleCallbacks(LIFECYCLE);
        app.registerActivityLifecycleCallbacks(LIFECYCLE);
        application = new WeakReference<>(app);
    }

    private static InAppBackHostLayout find(Activity activity) {
        if (activity == null || activity.getWindow() == null) return null;
        View host = activity.getWindow().getDecorView().findViewWithTag(HOST_TAG);
        return host instanceof InAppBackHostLayout ? (InAppBackHostLayout) host : null;
    }

    static boolean alive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    static void performBack(Activity activity) {
        if (!alive(activity)) return;
        try {
            if (activity instanceof BackInterceptor && ((BackInterceptor) activity).onInterceptInAppBack()) return;
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
            if (activity instanceof BaseActivity) {
                BaseActivity base = (BaseActivity) activity;
                if (base.onBackButtonStepUp()) return;
                if (base.onBackButtonAtRoot()) {
                    hintRoot(activity);
                    return;
                }
            } else if (activity.isTaskRoot()) {
                hintRoot(activity);
                return;
            }
            // Preserve player overrides, then the inherited ComponentActivity dispatcher.
            // Root hooks have already blocked the legacy HomeActivity exit path; no finish fallback.
            activity.onBackPressed();
        } catch (RuntimeException error) {
            Log.w("InAppBack", "Back handler rejected navigation", error);
        } finally {
            InAppBackHostLayout host = find(activity);
            if (host != null) host.recheckRootAfterNavigation();
        }
    }

    static boolean atRoot(Activity activity) {
        if (!alive(activity)) return false;
        try {
            if (activity instanceof BackInterceptor || fragmentBackStack(activity) != null) return false;
            if (activity instanceof ComponentActivity
                    && ((ComponentActivity) activity).getOnBackPressedDispatcher().hasEnabledCallbacks()) return false;
            View decor = activity.getWindow().getDecorView();
            if (focusedBackWebView(decor) != null || hasSecondaryPager(decor)) return false;
            return activity instanceof BaseActivity
                    ? ((BaseActivity) activity).onBackButtonAtRoot() : activity.isTaskRoot();
        } catch (RuntimeException error) {
            // A root that cannot be proven must keep its navigation affordance.
            return false;
        }
    }

    private static void hintRoot(Activity activity) {
        Toast.makeText(activity, "\u5df2\u5728\u9996\u9875", Toast.LENGTH_SHORT).show();
    }

    private static FragmentManager fragmentBackStack(Activity activity) {
        return activity instanceof FragmentActivity
                ? fragmentBackStack(((FragmentActivity) activity).getSupportFragmentManager()) : null;
    }

    private static FragmentManager fragmentBackStack(FragmentManager manager) {
        Fragment primary = manager.getPrimaryNavigationFragment();
        if (primary != null && primary.isAdded()) {
            FragmentManager child = fragmentBackStack(primary.getChildFragmentManager());
            if (child != null) return child;
        }
        return manager.getBackStackEntryCount() > 0 ? manager : null;
    }

    private static WebView focusedBackWebView(View view) {
        if (view instanceof InAppBackHostLayout || !view.isShown()) return null;
        if (view instanceof WebView && view.hasFocus() && ((WebView) view).canGoBack()) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView web = focusedBackWebView(group.getChildAt(i));
                if (web != null) return web;
            }
        }
        return null;
    }

    private static boolean hasSecondaryPager(View view) {
        if (view instanceof InAppBackHostLayout || !view.isShown()) return false;
        // Older HomeActivity hooks mark the entire Activity as root, including category pages.
        // Read pager state without invoking the mutating step-up hook to probe visibility.
        if (view instanceof ViewPager && ((ViewPager) view).getCurrentItem() > 0) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasSecondaryPager(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    public static boolean isEnabled() { return bool(HawkConfig.IN_APP_BACK, true); }
    public static String getMode() {
        String raw = text(HawkConfig.BACK_MODE, "");
        String normalized = mode(raw);
        if (!bool(HawkConfig.BACK_MODE_EXPLICIT, false)
                && (raw.length() == 0 || HawkConfig.BACK_MODE_FLOATING.equals(normalized))) {
            return HawkConfig.BACK_MODE_EDGE;
        }
        return raw.length() == 0 ? HawkConfig.BACK_MODE_EDGE : normalized;
    }
    public static String getSide() { return side(text(HawkConfig.BACK_SIDE, HawkConfig.BACK_SIDE_LEFT)); }
    public static boolean isLarge() { return bool(HawkConfig.BACK_LARGE, false); }
    public static boolean isHapticEnabled() { return bool(HawkConfig.BACK_HAPTIC, true); }
    public static boolean isHideOnRoot() { return bool(HawkConfig.BACK_HIDE_ON_ROOT, false); }
    public static String getEdgeSkin() {
        String raw = text(HawkConfig.BACK_EDGE_SKIN, "");
        String normalized = skin(raw);
        if (!bool(HawkConfig.BACK_EDGE_SKIN_EXPLICIT, false)
                && (raw.length() == 0 || HawkConfig.BACK_SKIN_CAPSULE.equals(normalized))) {
            return HawkConfig.BACK_SKIN_TEARDROP;
        }
        return raw.length() == 0 ? HawkConfig.BACK_SKIN_TEARDROP : normalized;
    }
    public static int getHideSeconds() { return validHideSeconds(integer(HawkConfig.BACK_HIDE_SECONDS, 4)); }

    public static void setEnabled(Activity a, boolean value) { put(a, HawkConfig.IN_APP_BACK, value); }
    public static void setMode(Activity a, String value) {
        Hawk.put(HawkConfig.BACK_MODE_EXPLICIT, true);
        put(a, HawkConfig.BACK_MODE, mode(value));
    }
    public static void setSide(Activity a, String value) {
        String next = side(value);
        if (!HawkConfig.BACK_SIDE_BOTH.equals(next)) {
            Hawk.put(HawkConfig.BACK_POSITION_X, HawkConfig.BACK_SIDE_RIGHT.equals(next) ? 10000 : 0);
        }
        put(a, HawkConfig.BACK_SIDE, next);
    }
    public static void setLarge(Activity a, boolean value) { put(a, HawkConfig.BACK_LARGE, value); }
    public static void setHapticEnabled(Activity a, boolean value) { put(a, HawkConfig.BACK_HAPTIC, value); }
    public static void setHideOnRoot(Activity a, boolean value) { put(a, HawkConfig.BACK_HIDE_ON_ROOT, value); }
    public static void setEdgeSkin(Activity a, String value) {
        Hawk.put(HawkConfig.BACK_EDGE_SKIN_EXPLICIT, true);
        put(a, HawkConfig.BACK_EDGE_SKIN, skin(value));
    }
    public static void setHideSeconds(Activity a, int value) { put(a, HawkConfig.BACK_HIDE_SECONDS, validHideSeconds(value)); }

    private static <T> void put(Activity activity, String key, T value) {
        Hawk.put(key, value);
        refresh(activity);
    }

    private static int validHideSeconds(int value) {
        return value == 0 || value == 4 || value == 6 || value == 8 ? value : 4;
    }

    private static String mode(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if ("1".equals(value) || "edge_pull_only".equals(value) || HawkConfig.BACK_MODE_EDGE.equals(value)) return HawkConfig.BACK_MODE_EDGE;
        if ("2".equals(value) || "dual_enabled".equals(value) || HawkConfig.BACK_MODE_BOTH.equals(value)) return HawkConfig.BACK_MODE_BOTH;
        return HawkConfig.BACK_MODE_FLOATING;
    }

    private static String side(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if ("1".equals(value) || HawkConfig.BACK_SIDE_RIGHT.equals(value)) return HawkConfig.BACK_SIDE_RIGHT;
        if ("2".equals(value) || HawkConfig.BACK_SIDE_BOTH.equals(value)) return HawkConfig.BACK_SIDE_BOTH;
        return HawkConfig.BACK_SIDE_LEFT;
    }

    private static String skin(String raw) {
        return HawkConfig.BACK_SKIN_CAPSULE.equalsIgnoreCase(raw) || "capsule_drawer".equalsIgnoreCase(raw)
                ? HawkConfig.BACK_SKIN_CAPSULE : HawkConfig.BACK_SKIN_TEARDROP;
    }

    private static String text(String key, String fallback) {
        try { return String.valueOf(Hawk.get(key, (Object) fallback)); }
        catch (RuntimeException error) { return fallback; }
    }

    static int integer(String key, int fallback) {
        try {
            Object value = Hawk.get(key, (Object) fallback);
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException error) { return fallback; }
    }

    private static boolean bool(String key, boolean fallback) {
        try {
            Object value = Hawk.get(key, (Object) fallback);
            if (value instanceof Boolean) return (Boolean) value;
            String text = String.valueOf(value);
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) return true;
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) return false;
            return fallback;
        } catch (RuntimeException error) { return fallback; }
    }
}
