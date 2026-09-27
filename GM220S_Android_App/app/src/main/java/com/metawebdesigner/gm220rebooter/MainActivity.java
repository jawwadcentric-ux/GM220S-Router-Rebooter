package com.metawebdesigner.gm220rebooter;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {

    private static final int GREEN = Color.rgb(32, 112, 62);
    private static final int GREEN_DARK = Color.rgb(20, 76, 43);
    private static final int BG = Color.rgb(247, 249, 248);
    private static final int TEXT = Color.rgb(31, 41, 35);
    private static final int MUTED = Color.rgb(102, 112, 106);
    private static final int BORDER = Color.rgb(220, 226, 222);

    EditText router, username, password;
    TextView timeText, statusText, scheduleText, connectionChip, alarmChip;
    Button enableBtn, testBtn, rebootBtn, saveBtn, exactBtn;
    int hour, minute;
    SecurePrefs sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = new SecurePrefs(this);
        hour = sp.raw().getInt("hour", 5);
        minute = sp.raw().getInt("minute", 0);
        buildUi();
        load();
        maybeAskNotificationPermission();
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, int radius, int strokeColor, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke > 0) g.setStroke(dp(stroke), strokeColor);
        return g;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(17), dp(18), dp(17));
        c.setBackground(bg(Color.WHITE, 18, BORDER, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(14));
        c.setLayoutParams(lp);
        return c;
    }

    private EditText input(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(145, 151, 148));
        e.setSingleLine(true);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(bg(Color.rgb(250, 251, 250), 12, BORDER, 1));
        if (secret) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(7), 0, dp(12));
        e.setLayoutParams(lp);
        return e;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(bg(GREEN, 14, GREEN, 0));
        b.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackground(bg(Color.rgb(244, 246, 245), 14, BORDER, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView chip(String label) {
        TextView v = text(label, 12, MUTED, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(11), dp(7), dp(11), dp(7));
        v.setBackground(bg(Color.rgb(242, 245, 243), 999, BORDER, 1));
        return v;
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(30));
        sv.addView(root);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(18));

        TextView icon = text("↻", 31, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(bg(GREEN, 16, GREEN, 0));
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout headText = new LinearLayout(this);
        headText.setOrientation(LinearLayout.VERTICAL);
        headText.setPadding(dp(14), 0, 0, 0);
        headText.addView(text("GM220-S Rebooter", 24, GREEN_DARK, true));
        TextView sub = text("Reliable scheduled restart for your router", 13, MUTED, false);
        sub.setPadding(0, dp(3), 0, 0);
        headText.addView(sub);
        header.addView(headText, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        // Quick status row
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(0, 0, 0, dp(14));

        connectionChip = chip("Wi-Fi checking…");
        alarmChip = chip("Schedule checking…");

        LinearLayout.LayoutParams chip1 = new LinearLayout.LayoutParams(0, -2, 1);
        chip1.setMargins(0, 0, dp(6), 0);
        quick.addView(connectionChip, chip1);

        LinearLayout.LayoutParams chip2 = new LinearLayout.LayoutParams(0, -2, 1);
        chip2.setMargins(dp(6), 0, 0, 0);
        quick.addView(alarmChip, chip2);
        root.addView(quick);

        // Router settings card
        LinearLayout settingsCard = card();
        settingsCard.addView(text("Router settings", 18, TEXT, true));

        TextView helper = text("Saved securely on this phone. Nothing is uploaded anywhere.", 12, MUTED, false);
        helper.setPadding(0, dp(4), 0, dp(10));
        settingsCard.addView(helper);

        settingsCard.addView(text("Router address", 13, MUTED, true));
        router = input("http://192.168.1.1", false);
        settingsCard.addView(router);

        settingsCard.addView(text("Username", 13, MUTED, true));
        username = input("admin", false);
        settingsCard.addView(username);

        settingsCard.addView(text("Password", 13, MUTED, true));
        password = input("Router password", true);
        settingsCard.addView(password);

        saveBtn = secondaryButton("Save settings");
        settingsCard.addView(saveBtn);
        saveBtn.setOnClickListener(v -> {
            if (!validateInputs()) return;
            save(true);
            setStatus(true, "Settings saved securely.");
        });

        root.addView(settingsCard);

        // Schedule card
        LinearLayout schedCard = card();
        schedCard.addView(text("Daily schedule", 18, TEXT, true));

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setPadding(0, dp(12), 0, dp(8));

        LinearLayout timeCopy = new LinearLayout(this);
        timeCopy.setOrientation(LinearLayout.VERTICAL);
        timeCopy.addView(text("Restart time", 12, MUTED, true));
        timeText = text("", 25, TEXT, true);
        timeText.setPadding(0, dp(3), 0, 0);
        timeCopy.addView(timeText);
        timeRow.addView(timeCopy, new LinearLayout.LayoutParams(0, -2, 1));

        Button change = secondaryButton("Change");
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(dp(118), dp(46));
        change.setLayoutParams(chLp);
        timeRow.addView(change);
        schedCard.addView(timeRow);

        change.setOnClickListener(v -> new TimePickerDialog(
                this,
                (view, h, m) -> {
                    hour = h;
                    minute = m;
                    updateTime();
                    save(false);
                    if (sp.raw().getBoolean("enabled", false)) {
                        AlarmScheduler.schedule(this);
                    }
                    refreshStatus();
                },
                hour,
                minute,
                android.text.format.DateFormat.is24HourFormat(this)
        ).show());

        enableBtn = primaryButton("Enable daily restart");
        schedCard.addView(enableBtn);
        enableBtn.setOnClickListener(v -> toggleSchedule());

        exactBtn = secondaryButton("Exact alarm permission");
        if (Build.VERSION.SDK_INT >= 31) {
            schedCard.addView(exactBtn);
            exactBtn.setOnClickListener(v -> requestExactAlarm());
        }

        Button batteryBtn = secondaryButton("Open battery settings");
        schedCard.addView(batteryBtn);
        batteryBtn.setOnClickListener(v -> openBatterySettings());

        scheduleText = text("", 13, MUTED, false);
        scheduleText.setPadding(0, dp(10), 0, 0);
        schedCard.addView(scheduleText);

        root.addView(schedCard);

        // Actions card
        LinearLayout actions = card();
        actions.addView(text("Router actions", 18, TEXT, true));

        testBtn = secondaryButton("Test connection");
        rebootBtn = primaryButton("Restart router now");

        actions.addView(testBtn);
        actions.addView(rebootBtn);

        testBtn.setOnClickListener(v -> runRouterAction(false));
        rebootBtn.setOnClickListener(v -> {
            if (!validateInputs()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Restart router?")
                    .setMessage("Internet will disconnect briefly while the router restarts.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restart", (d, w) -> runRouterAction(true))
                    .show();
        });

        root.addView(actions);

        // Status card
        LinearLayout statusCard = card();
        statusCard.addView(text("Status", 18, TEXT, true));
        statusText = text("Ready.", 14, TEXT, false);
        statusText.setPadding(0, dp(10), 0, 0);
        statusCard.addView(statusText);
        root.addView(statusCard);

        TextView footer = text(
                "For automatic restart, keep the phone powered on and connected to this router's Wi-Fi. " +
                "If a scheduled attempt fails temporarily, the app retries up to 3 times.",
                12, MUTED, false
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(2), dp(8), dp(4));
        root.addView(footer);

        setContentView(sv);
    }

    private void load() {
        router.setText(sp.raw().getString("router", "http://192.168.1.1"));
        username.setText(sp.getSecret("username", ""));
        password.setText(sp.getSecret("password", ""));
        updateTime();
        refreshStatus();
    }

    private boolean validateInputs() {
        String r = router.getText().toString().trim();
        String u = username.getText().toString().trim();
        String p = password.getText().toString();

        if (r.isEmpty()) {
            router.setError("Router address is required");
            router.requestFocus();
            return false;
        }

        if (!r.startsWith("http://") && !r.startsWith("https://")) {
            r = "http://" + r;
            router.setText(r);
        }

        if (u.isEmpty()) {
            username.setError("Username is required");
            username.requestFocus();
            return false;
        }

        if (p.isEmpty()) {
            password.setError("Password is required");
            password.requestFocus();
            return false;
        }
        return true;
    }

    private void save(boolean credentials) {
        String r = router.getText().toString().trim();
        if (r.isEmpty()) r = "http://192.168.1.1";
        if (!r.startsWith("http://") && !r.startsWith("https://")) r = "http://" + r;
        r = r.replaceAll("/+$", "");

        sp.raw().edit()
                .putString("router", r)
                .putInt("hour", hour)
                .putInt("minute", minute)
                .apply();

        if (credentials) {
            sp.putSecret("username", username.getText().toString().trim());
            sp.putSecret("password", password.getText().toString());
        }
    }

    private void updateTime() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        String pattern = android.text.format.DateFormat.is24HourFormat(this) ? "HH:mm" : "hh:mm a";
        timeText.setText(new SimpleDateFormat(pattern, Locale.getDefault()).format(c.getTime()));
    }

    private void toggleSchedule() {
        if (!validateInputs()) return;
        save(true);

        boolean on = sp.raw().getBoolean("enabled", false);

        if (on) {
            sp.raw().edit().putBoolean("enabled", false).apply();
            AlarmScheduler.cancelAll(this);
            setStatus(true, "Daily restart disabled.");
        } else {
            sp.raw().edit().putBoolean("enabled", true).apply();
            AlarmScheduler.schedule(this);

            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (!am.canScheduleExactAlarms()) {
                    setStatus(false,
                            "Daily restart enabled, but Android has not granted exact-alarm access. " +
                            "The restart may happen later than the selected time.");
                } else {
                    setStatus(true, "Daily restart enabled.");
                }
            } else {
                setStatus(true, "Daily restart enabled.");
            }
        }
        refreshStatus();
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) return;

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am.canScheduleExactAlarms()) {
            setStatus(true, "Exact alarm permission is already allowed.");
            return;
        }

        try {
            Intent i = new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void runRouterAction(boolean rebootNow) {
        if (!validateInputs()) return;
        save(true);

        testBtn.setEnabled(false);
        rebootBtn.setEnabled(false);
        saveBtn.setEnabled(false);

        setStatus(true, rebootNow ? "Sending reboot command…" : "Testing router connection…");

        String r = router.getText().toString().trim();
        String u = username.getText().toString().trim();
        String p = password.getText().toString();

        new Thread(() -> {
            RouterClient.Result res = rebootNow
                    ? RouterClient.reboot(r, u, p)
                    : RouterClient.test(r, u, p);

            runOnUiThread(() -> {
                testBtn.setEnabled(true);
                rebootBtn.setEnabled(true);
                saveBtn.setEnabled(true);
                setStatus(res.ok, res.message);
                refreshStatus();
            });
        }).start();
    }

    private boolean isOnWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    private void setStatus(boolean ok, String message) {
        statusText.setText((ok ? "✓  " : "⚠  ") + message);
        statusText.setTextColor(ok ? GREEN_DARK : Color.rgb(150, 83, 0));
    }

    private String scheduleSummary() {
        boolean on = sp.raw().getBoolean("enabled", false);
        String last = sp.raw().getString("last_result", "No automatic run yet");
        return "Next run: " + AlarmScheduler.formatNext(this) +
                "\nLast automatic run: " + last +
                "\nAutomatic restart: " + (on ? "ON" : "OFF");
    }

    private void refreshStatus() {
        boolean on = sp.raw().getBoolean("enabled", false);

        enableBtn.setText(on ? "Disable daily restart" : "Enable daily restart");
        if (on) {
            enableBtn.setBackground(bg(Color.rgb(126, 46, 46), 14, Color.rgb(126, 46, 46), 0));
        } else {
            enableBtn.setBackground(bg(GREEN, 14, GREEN, 0));
        }

        connectionChip.setText(isOnWifi() ? "✓ Wi-Fi connected" : "⚠ Wi-Fi not connected");
        connectionChip.setTextColor(isOnWifi() ? GREEN_DARK : Color.rgb(150, 83, 0));

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            boolean exact = am.canScheduleExactAlarms();
            alarmChip.setText(exact ? "✓ Exact timing" : "≈ Flexible timing");
            alarmChip.setTextColor(exact ? GREEN_DARK : Color.rgb(150, 83, 0));
            if (exactBtn != null) {
                exactBtn.setText(exact ? "Exact alarm permission ✓" : "Allow exact alarm permission");
            }
        } else {
            alarmChip.setText("✓ Exact timing");
            alarmChip.setTextColor(GREEN_DARK);
        }

        scheduleText.setText(scheduleSummary());
    }

    private void maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 700);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();

        if (sp.raw().getBoolean("enabled", false)
                && sp.raw().getLong("next_run", 0) == 0) {
            AlarmScheduler.schedule(this);
        }
    }
}
