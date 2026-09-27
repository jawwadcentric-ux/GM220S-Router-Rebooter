GM220-S Android Router Rebooter
===============================

Purpose
-------
Android app for GM220-S XPON (tested design target: V9.0 / software V9.0.10P1T1 UI family).
The user can choose ANY daily restart time from the app.

Main features
-------------
- Router address editable (default http://192.168.1.1)
- Router username/password saved locally using Android Keystore AES/GCM
- Custom daily restart time via TimePicker
- Test Connection button does NOT reboot
- Restart Router Now button
- Enable/Disable daily restart
- Reschedules after phone reboot/app update
- Uses exact alarm where Android permission allows it
- Cleartext HTTP enabled because local router UI uses http://192.168.1.1

Important phone settings
------------------------
1. Keep phone connected to the GM220-S Wi-Fi at the scheduled time.
2. Android 12+: tap "Allow exact alarm permission" in the app.
3. Disable battery optimization/restrictions for the app if your phone vendor aggressively sleeps apps.
4. The router restart will briefly disconnect Wi-Fi; this is expected.

Build
-----
Open this folder in Android Studio (recent version), allow Gradle sync, then:
Build > Build App Bundle(s) / APK(s) > Build APK(s)

The source uses only Android framework APIs; no third-party libraries are required.
