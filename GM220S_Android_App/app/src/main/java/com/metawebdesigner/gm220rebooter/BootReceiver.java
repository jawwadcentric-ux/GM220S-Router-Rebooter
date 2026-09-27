package com.metawebdesigner.gm220rebooter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SecurePrefs sp = new SecurePrefs(context);
        AlarmScheduler.cancelRetry(context);
        if (sp.raw().getBoolean("enabled", false)) {
            AlarmScheduler.schedule(context);
        }
    }
}
