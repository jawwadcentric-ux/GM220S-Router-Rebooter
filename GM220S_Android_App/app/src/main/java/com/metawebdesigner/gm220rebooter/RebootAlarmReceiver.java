package com.metawebdesigner.gm220rebooter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RebootAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AlarmScheduler.schedule(context); // schedule tomorrow first
        final PendingResult pr = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            PowerManager.WakeLock lock = null;
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GM220S:RebootLock");
                lock.acquire(60_000);

                SecurePrefs sp = new SecurePrefs(app);
                String base = sp.raw().getString("router", "http://192.168.1.1");
                String user = sp.getSecret("username", "");
                String pass = sp.getSecret("password", "");
                RouterClient.Result res = RouterClient.reboot(base, user, pass);
                String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                sp.raw().edit().putString("last_result", stamp + " - " + res.message).apply();
            } finally {
                if (lock != null && lock.isHeld()) lock.release();
                pr.finish();
            }
        }).start();
    }
}
