package com.metawebdesigner.gm220rebooter;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    EditText router, username, password;
    TextView timeText, status;
    Button enableBtn;
    int hour, minute;
    SecurePrefs sp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = new SecurePrefs(this);
        hour = sp.raw().getInt("hour", 5);
        minute = sp.raw().getInt("minute", 0);
        buildUi();
        load();
    }

    private TextView label(String s) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(14); v.setTextColor(Color.DKGRAY);
        v.setPadding(0,18,0,6); return v;
    }

    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b;
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(42,42,42,42);
        sv.addView(root);

        TextView title = new TextView(this); title.setText("GM220-S Router Rebooter"); title.setTextSize(25); title.setTextColor(Color.rgb(27,94,32)); title.setTypeface(null,1);
        root.addView(title);
        TextView sub = new TextView(this); sub.setText("Automatic daily router restart from your Android phone"); sub.setTextSize(14); sub.setPadding(0,5,0,16); root.addView(sub);

        root.addView(label("Router address"));
        router = new EditText(this); router.setSingleLine(true); root.addView(router);
        root.addView(label("Router username"));
        username = new EditText(this); username.setSingleLine(true); root.addView(username);
        root.addView(label("Router password"));
        password = new EditText(this); password.setSingleLine(true); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(password);

        root.addView(label("Daily restart time"));
        LinearLayout tr = new LinearLayout(this); tr.setOrientation(LinearLayout.HORIZONTAL); tr.setGravity(Gravity.CENTER_VERTICAL);
        timeText = new TextView(this); timeText.setTextSize(20); timeText.setPadding(0,8,20,8); tr.addView(timeText, new LinearLayout.LayoutParams(0,-2,1));
        Button change = button("Change time"); tr.addView(change); root.addView(tr);
        change.setOnClickListener(v -> new TimePickerDialog(this, (view,h,m)-> {hour=h; minute=m; updateTime(); save(false); if (sp.raw().getBoolean("enabled",false)) AlarmScheduler.schedule(this); refreshStatus();}, hour, minute, false).show());

        Button save = button("Save settings"); root.addView(save); save.setOnClickListener(v -> { save(true); toast("Settings saved"); });
        Button test = button("Test connection (no reboot)"); root.addView(test); test.setOnClickListener(v -> runTest(false));
        Button reboot = button("Restart router now"); root.addView(reboot); reboot.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Restart router?").setMessage("Internet will disconnect briefly while the router reboots.").setNegativeButton("Cancel",null).setPositiveButton("Restart",(d,w)->runTest(true)).show());

        enableBtn = button("Enable daily restart"); root.addView(enableBtn); enableBtn.setOnClickListener(v -> toggleSchedule());

        if (Build.VERSION.SDK_INT >= 31) {
            Button exact = button("Allow exact alarm permission"); root.addView(exact); exact.setOnClickListener(v -> requestExactAlarm());
        }

        status = new TextView(this); status.setTextSize(14); status.setPadding(0,25,0,20); root.addView(status);
        TextView note = new TextView(this); note.setText("Important: phone must be ON and connected to this router's Wi-Fi at the scheduled time. Disable battery restrictions for this app for best reliability."); note.setTextSize(13); note.setTextColor(Color.DKGRAY); root.addView(note);
        setContentView(sv);
    }

    private void load() {
        router.setText(sp.raw().getString("router", "http://192.168.1.1"));
        username.setText(sp.getSecret("username", ""));
        password.setText(sp.getSecret("password", ""));
        updateTime(); refreshStatus();
    }

    private void save(boolean credentials) {
        String r = router.getText().toString().trim();
        if (r.isEmpty()) r = "http://192.168.1.1";
        sp.raw().edit().putString("router", r).putInt("hour",hour).putInt("minute",minute).apply();
        if (credentials) {
            sp.putSecret("username", username.getText().toString());
            sp.putSecret("password", password.getText().toString());
        }
    }

    private void updateTime() {
        java.util.Calendar c = java.util.Calendar.getInstance(); c.set(java.util.Calendar.HOUR_OF_DAY,hour); c.set(java.util.Calendar.MINUTE,minute);
        timeText.setText(new java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(c.getTime()));
    }

    private void toggleSchedule() {
        save(true);
        boolean on = sp.raw().getBoolean("enabled", false);
        if (on) {
            sp.raw().edit().putBoolean("enabled", false).apply(); AlarmScheduler.cancel(this); toast("Daily restart disabled");
        } else {
            sp.raw().edit().putBoolean("enabled", true).apply(); AlarmScheduler.schedule(this); toast("Daily restart enabled");
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
                if (!am.canScheduleExactAlarms()) toast("Allow Exact Alarm permission for precise timing.");
            }
        }
        refreshStatus();
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) return;
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
        if (am.canScheduleExactAlarms()) { toast("Exact alarm permission already allowed"); return; }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private void runTest(boolean rebootNow) {
        save(true);
        status.setText(rebootNow ? "Sending reboot command..." : "Testing router connection...");
        String r=router.getText().toString().trim(), u=username.getText().toString(), p=password.getText().toString();
        new Thread(() -> {
            RouterClient.Result res = rebootNow ? RouterClient.reboot(r,u,p) : RouterClient.test(r,u,p);
            runOnUiThread(() -> { status.setText((res.ok?"✓ ":"✗ ") + res.message + "\n\n" + scheduleText()); toast(res.message); });
        }).start();
    }

    private String scheduleText() {
        boolean on=sp.raw().getBoolean("enabled",false);
        String last=sp.raw().getString("last_result","No automatic run yet");
        return "Daily restart: " + (on?"ON":"OFF") + "\nNext run: " + AlarmScheduler.formatNext(this) + "\nLast run: " + last;
    }

    private void refreshStatus() {
        boolean on=sp.raw().getBoolean("enabled",false);
        enableBtn.setText(on ? "Disable daily restart" : "Enable daily restart");
        status.setText(scheduleText());
    }

    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() { super.onResume(); refreshStatus(); }
}
