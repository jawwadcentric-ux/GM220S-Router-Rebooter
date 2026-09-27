package com.metawebdesigner.gm220rebooter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AlarmScheduler {
    private static PendingIntent pendingIntent(Context c) {
        Intent i = new Intent(c, RebootAlarmReceiver.class);
        return PendingIntent.getBroadcast(c, 220, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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

    public static void schedule(Context c) {
        SecurePrefs sp = new SecurePrefs(c);
        if (!sp.raw().getBoolean("enabled", false)) return;
        int hour = sp.raw().getInt("hour", 5);
        int minute = sp.raw().getInt("minute", 0);
        long when = nextMillis(hour, minute);
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pendingIntent(c));
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pendingIntent(c));
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pendingIntent(c));
        }
        sp.raw().edit().putLong("next_run", when).apply();
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingIntent(c));
        new SecurePrefs(c).raw().edit().remove("next_run").apply();
    }

    public static String formatNext(Context c) {
        long x = new SecurePrefs(c).raw().getLong("next_run", 0);
        if (x == 0) return "Not scheduled";
        return new SimpleDateFormat("EEE, dd MMM yyyy - hh:mm a", Locale.getDefault()).format(new Date(x));
    }
}
