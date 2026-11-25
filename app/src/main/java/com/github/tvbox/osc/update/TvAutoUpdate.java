package com.github.tvbox.osc.update;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.github.tvbox.osc.ui.dialog.TvUpdateDialog;
import com.orhanobut.hawk.Hawk;

/**
 * 启动后自动检查一次更新（R2 主通道，GitHub Release 兜底）。
 *
 * <p>TV 端的取舍：</p>
 * <ul>
 *   <li><b>延迟几秒再查</b>：开机瞬间首页要先拉订阅和封面，抢带宽会让首屏更慢。</li>
 *   <li><b>只在首页查</b>：检查由 {@code HomeActivity} 发起，播放页不会被打扰。</li>
 *   <li><b>同一个版本只自动提示一次</b>：用户选了「暂不更新」或直接关掉，下次启动
 *       不再重复弹；要检查随时可以从「设置 → 检查更新」手动点。</li>
 *   <li><b>任何失败都静默</b>：自动检查失败不该让用户看到报错，手动检查才显示原因。</li>
 * </ul>
 */
public final class TvAutoUpdate {

    public static final String TAG = "TvAutoUpdate";

    private static final String PROMPTED_VERSION_CODE = "tv_auto_prompted_vc";
    private static final long START_DELAY_MS = 6000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean scheduled;

    private TvAutoUpdate() {
    }

    /** 首页调用：每个进程只排一次，重复调用无副作用。 */
    public static void schedule(final Activity activity) {
        if (activity == null || scheduled) return;
        scheduled = true;
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                check(activity);
            }
        }, START_DELAY_MS);
    }

    private static void check(final Activity activity) {
        if (activity.isFinishing()) return;
        final Context app = activity.getApplicationContext();
        TvUpdate.check(app, new TvUpdate.ProgressHost() {
            @Override
            public void post(Runnable runnable) {
                MAIN.post(runnable);
            }
        }, new TvUpdate.CheckCallback() {
            @Override
            public void onResult(final TvUpdateInfo info, String error) {
                if (info == null) {
                    // 自动检查失败或无更新：静默，不打扰看片。
                    Log.i(TAG, "auto check done, nothing to prompt. error=" + error);
                    return;
                }
                int prompted = Hawk.get(PROMPTED_VERSION_CODE, 0);
                if (prompted >= info.versionCode) {
                    Log.i(TAG, "auto check: vc=" + info.versionCode + " already prompted");
                    return;
                }
                if (TvUpdate.isSkipped(info.tag)) {
                    Log.i(TAG, "auto check: vc=" + info.versionCode + " skipped by user");
                    return;
                }
                if (activity.isFinishing()) return;
                Hawk.put(PROMPTED_VERSION_CODE, info.versionCode);
                Log.i(TAG, "auto check: prompting vc=" + info.versionCode
                        + " name=" + info.versionName);
                try {
                    TvUpdateDialog dialog = new TvUpdateDialog(activity);
                    dialog.setInfo(info);
                    dialog.show();
                } catch (Throwable th) {
                    Log.w(TAG, "auto check: dialog failed " + th);
                }
            }
        });
    }
}
