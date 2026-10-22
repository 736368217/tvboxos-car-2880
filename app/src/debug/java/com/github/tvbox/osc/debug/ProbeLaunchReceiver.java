package com.github.tvbox.osc.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-only entry point for the adb-driven playback probe. */
public class ProbeLaunchReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent probe = new Intent(context, FeatureProbeActivity.class);
        probe.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        probe.putExtra("file", intent.getStringExtra("file"));
        probe.putExtra("player", intent.getStringExtra("player"));
        probe.putExtra("filter", intent.getIntExtra("filter", 0));
        probe.putExtra("audio", intent.getIntExtra("audio", 0));
        probe.putExtra("sr", intent.getIntExtra("sr", 1));
        probe.putExtra("interp", intent.getIntExtra("interp", 1));
        probe.putExtra("duration", intent.getIntExtra("duration", 12));
        context.startActivity(probe);
    }
}
