Architecture: the menu (Java + native lib) must be loaded INSIDE the game process (patched APK / System.loadLibrary
from the game's own code). A separate app cannot call the game's functions or read its save on Android 11+.

AndroidManifest (of the patched game):
  <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
  <service android:name="com.fstudio.arkmenu.FloatingMenu"/>
Start FloatingMenu after the user grants "Display over other apps".

Build: NDK + CMake; add Dobby static lib at native/libs/arm64-v8a/libdobby.a.
To finish: set PATTERN_CONSOLE_EXEC and PATTERN_PLAYER_TICK in native/arkmenu.cpp from your own libUE4.so.
Check `adb logcat -s ArkMenu` to see whether the patterns matched.
