package com.metawebdesigner.gm220rebooter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.*;

public class AlarmScheduler {

    public static final String ACTION_DAILY = "com.metawebdesigner.gm220rebooter.DAILY";
    public static final String ACTION_RETRY = "com.metawebdesigner.gm220rebooter.RETRY";

    private static PendingIntent dailyIntent(Context c) {
        Intent i = new Intent(c, RebootAlarmReceiver.class);
        i.setAction(ACTION_DAILY);
        i.putExtra("retry_count", 0);
        return PendingIntent.getBroadcast(
                c, 220, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent retryIntent(Context c, int retryCount) {
        Intent i = new Intent(c, RebootAlarmReceiver.class);
        i.setAction(ACTION_RETRY);
        i.putExtra("retry_count", retryCount);
        return PendingIntent.getBroadcast(
                c, 221, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static long nextMillis(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis();
    }

    private static void setAlarm(Context c, long when, PendingIntent pi) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    public static void schedule(Context c) {
        SecurePrefs sp = new SecurePrefs(c);
        if (!sp.raw().getBoolean("enabled", false)) return;

        int hour = sp.raw().getInt("hour", 5);
        int minute = sp.raw().getInt("minute", 0);
        long when = nextMillis(hour, minute);

        setAlarm(c, when, dailyIntent(c));
        sp.raw().edit().putLong("next_run", when).apply();
    }

    public static void scheduleRetry(Context c, int retryCount) {
        if (retryCount < 1 || retryCount > 3) return;
        long when = System.currentTimeMillis() + (10L * 60L * 1000L);
        setAlarm(c, when, retryIntent(c, retryCount));
        new SecurePrefs(c).raw().edit()
                .putLong("retry_run", when)
                .putInt("retry_count", retryCount)
                .apply();
    }

    public static void cancelRetry(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(retryIntent(c, 1));
        new SecurePrefs(c).raw().edit()
                .remove("retry_run")
                .remove("retry_count")
                .apply();
    }

    public static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(dailyIntent(c));
        am.cancel(retryIntent(c, 1));
        new SecurePrefs(c).raw().edit()
                .remove("next_run")
                .remove("retry_run")
                .remove("retry_count")
                .apply();
    }

    public static String formatNext(Context c) {
        SecurePrefs sp = new SecurePrefs(c);
        long daily = sp.raw().getLong("next_run", 0);
        long retry = sp.raw().getLong("retry_run", 0);

        long x = 0;
        if (daily > 0 && retry > 0) x = Math.min(daily, retry);
        else if (daily > 0) x = daily;
        else if (retry > 0) x = retry;

        if (x == 0) return "Not scheduled";

        return new SimpleDateFormat(
                "EEE, dd MMM yyyy - hh:mm a",
                Locale.getDefault()
        ).format(new Date(x));
    }
}
