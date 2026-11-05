#!/usr/bin/env bash
# Installs the release APK on the CI emulator, set up like the car's screen, opens it and fails
# if the app crashes or is not running after 25 seconds. Saves a screenshot and the log.
set -u
PKG=com.selawi.cockpit

adb shell wm size 1920x1080
adb shell wm density 240
adb install -r -g selawi-dash.apk
adb logcat -G 16M || true
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 25
adb exec-out screencap -p > emulator.png
adb logcat -d > logcat.txt
echo "--- Crash log ---"
adb logcat -d -b crash | head -80
echo "--- 3D engine messages ---"
adb logcat -d -s Filament:* gltfio:* libc:* DEBUG:* AndroidRuntime:* | grep -v -E "^\s*$" | head -80

if grep -E "FATAL EXCEPTION|Fatal signal" -A 25 logcat.txt | grep -q "$PKG"; then
  echo "The app crashed:"
  grep -E "FATAL EXCEPTION|Fatal signal" -A 40 logcat.txt
  echo "--- 3D engine and graphics messages ---"
  grep -i -E "filament|abort message|panic|egl|gles|opengl|precondition|postcondition" logcat.txt | head -60
  exit 1
fi
if ! adb shell pidof "$PKG" >/dev/null; then
  echo "The app is not running after start."
  tail -n 200 logcat.txt
  exit 1
fi
echo "The app started and kept running."
