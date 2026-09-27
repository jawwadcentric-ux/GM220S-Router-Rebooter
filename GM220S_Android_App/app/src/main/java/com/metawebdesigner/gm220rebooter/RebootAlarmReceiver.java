package com.metawebdesigner.gm220rebooter;

import android.app.*;
import android.content.*;
import android.os.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RebootAlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "router_reboot_status";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();
        final Context app = context.getApplicationContext();

        String action = intent == null ? null : intent.getAction();
        final boolean isRetry = AlarmScheduler.ACTION_RETRY.equals(action);
        final int retryCount = intent == null ? 0 : intent.getIntExtra("retry_count", 0);

        if (!isRetry) {
            AlarmScheduler.schedule(app); // schedule tomorrow immediately
        }

        new Thread(() -> {
            PowerManager.WakeLock lock = null;

            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                lock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "GM220S:RebootLock"
                );
                lock.acquire(90_000);

                SecurePrefs sp = new SecurePrefs(app);

                if (!sp.raw().getBoolean("enabled", false)) {
                    AlarmScheduler.cancelRetry(app);
                    return;
                }

                String base = sp.raw().getString("router", "http://192.168.1.1");
                String user = sp.getSecret("username", "");
                String pass = sp.getSecret("password", "");

                RouterClient.Result res;

                if (user == null || user.trim().isEmpty() || pass == null || pass.isEmpty()) {
                    res = new RouterClient.Result(false, "Missing saved router username/password.");
                } else {
                    res = RouterClient.reboot(base, user, pass);
                }

                String stamp = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                ).format(new Date());

                sp.raw().edit()
                        .putString("last_result", stamp + " - " + res.message)
                        .apply();

                if (res.ok) {
                    AlarmScheduler.cancelRetry(app);
                    notifyResult(app, true, "Router restart sent successfully");
                } else {
                    int nextRetry = isRetry ? retryCount + 1 : 1;

                    if (nextRetry <= 3 && sp.raw().getBoolean("enabled", false)) {
                        AlarmScheduler.scheduleRetry(app, nextRetry);
                        notifyResult(
                                app,
                                false,
                                "Restart failed. Retry " + nextRetry + " scheduled in 10 minutes."
                        );
                    } else {
                        AlarmScheduler.cancelRetry(app);
                        notifyResult(
                                app,
                                false,
                                "Restart failed after retries. Open the app to test the connection."
                        );
                    }
                }

            } catch (Exception e) {
                try {
                    SecurePrefs sp = new SecurePrefs(app);
                    String stamp = new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()
                    ).format(new Date());

                    sp.raw().edit()
                            .putString("last_result", stamp + " - Background error: " + e.getMessage())
                            .apply();

                    notifyResult(app, false, "Background error. Open the app for details.");
                } catch (Exception ignored) {}

            } finally {
                if (lock != null && lock.isHeld()) lock.release();
                pr.finish();
            }
        }).start();
    }

    private void notifyResult(Context c, boolean ok, String text) {
        try {
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID,
                        "Router restart status",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                ch.setDescription("Shows results of automatic router restart attempts.");
                nm.createNotificationChannel(ch);
            }

            Intent open = new Intent(c, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(
                    c,
                    901,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(c, CHANNEL_ID)
                    : new Notification.Builder(c);

            b.setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle(ok ? "GM220-S restart complete" : "GM220-S restart needs attention")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            nm.notify(2205, b.build());

        } catch (Exception ignored) {}
    }
}
