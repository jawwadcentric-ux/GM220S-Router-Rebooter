GM220-S REBOOTER PRO v2.0
============================

What changed
------------
- Cleaner, card-based interface
- Clear Wi-Fi and exact-alarm status
- Input validation
- Better success/error messages
- Buttons disable while a router request is running
- Password/username remain stored through SecurePrefs
- Automatic restart survives phone reboot/app update
- Automatic retry: up to 3 retries, 10 minutes apart, when a scheduled restart fails
- Notification after scheduled success/failure
- Battery settings shortcut
- Handles Android 12+ exact-alarm permission
- Handles Android 13+ notification permission
- Keeps the verified GM220-S V9.0.10P1T1 login/reboot implementation

Important
---------
No app can guarantee a restart if Android completely stops the app, the phone is powered off,
or the phone is not connected to the GM220-S network. This build is hardened for those cases
where Android permits the scheduled alarm to run.

GitHub
------
Replace the GM220S_Android_App folder in your repository with this one, or upload the changed
files. Your existing GitHub Actions workflow can then build the APK.
