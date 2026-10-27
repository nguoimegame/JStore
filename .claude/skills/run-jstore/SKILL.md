---
name: run-jstore
description: Build, install, launch and log in jstore (Aurora Store fork for the Jaecoo head unit) on the tall portrait Android emulator (otoAutomative, 1280x1606, microG) to check UI. Use when asked to run, test UI, or screenshot jstore on the emulator.
---

# Run jstore on the emulator

Windows + Git Bash. `adb` is NOT on PATH; use the full path in every call
(shell state does not persist between Bash calls):

```bash
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
EMU="$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe"
```

## 1. Pick the AVD

"Emulator cao" = the tall portrait head-unit screen, 1280x1606 @160dpi.

| AVD | Image | Use |
|---|---|---|
| `otoAutomative` | android-30 default (no Google), **microG already installed with account `quangit22@gmail.com`** | Use this one, login works |
| `Automotive_Large_Portrait` | android-33 automotive Google Play | Same size, but real GMS, no microG account |

Only one emulator at a time (`$A emu kill` first). Boot and wait:

```bash
(nohup "$EMU" -avd otoAutomative > /tmp/emu.log 2>&1 &)
timeout 200 "$A" wait-for-device
until [ "$("$A" shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 5; done
```

If screenshots are black/white after boot: `$A shell input keyevent KEYCODE_WAKEUP`.

## 2. Build and install

```bash
./gradlew.bat :app:assembleVanillaDebug -q      # ~ several minutes cold
"$A" install -r app/build/outputs/apk/vanilla/debug/app-vanilla-debug.apk
```

Package: `com.example.jstore` (debug has no suffix here).

## 3. Launch

The app has TWO launcher activities, so `monkey -p` opens LeakCanary by mistake.
Start the real one explicitly:

```bash
"$A" shell am start -n com.example.jstore/com.aurora.store.ComposeActivity
```

## 4. Onboarding and login (fresh data: `$A shell pm clear com.example.jstore`)

Grant permissions with adb BEFORE walking the onboarding (changing appops
restarts the app, so do not do it mid-flow):

```bash
"$A" shell appops set com.example.jstore REQUEST_INSTALL_PACKAGES allow
"$A" shell appops set com.example.jstore MANAGE_EXTERNAL_STORAGE allow
"$A" shell cmd deviceidle whitelist +com.example.jstore
```

Then tap (coordinates for 1280x1606 with the otoAutomative nav bar):

| Step | Tap |
|---|---|
| Welcome -> Next, Permissions -> Finish | `input tap 1155 1506` (twice, 2 s apart) |
| Login screen "Google" button | `input tap 640 963` |
| microG "Choose an account" radio, then OK | `input tap 873 751`, `input tap 870 858` |
| microG "Allow jstore privileged access" -> Allow | `input tap 1020 840` (appears after ~10 s) |

Done when `dumpsys window | grep mCurrentFocus` is back on `ComposeActivity` and
the Apps tab shows content. On the Google Play automotive AVD the nav bar is
different, re-measure taps from a screenshot.

## 5. Screenshot and inspect

```bash
"$A" exec-out screencap -p > "$TEMP/shot.png"     # then Read the PNG
"$A" logcat -d -s AndroidRuntime:E | tail          # crashes
```

Always look at the screenshot; a blank frame means it did not launch.

## Known UI observations (portrait 1280x1606)

- Welcome and Permissions onboarding screens use the two-column landscape layout:
  content hugs the top, left column holds only the logo, lower ~40% is empty.
- Login screen and the Apps tab render fine.
