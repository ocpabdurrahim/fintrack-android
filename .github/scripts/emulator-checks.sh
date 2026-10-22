#!/usr/bin/env bash
# Runs inside the emulator job:
#   1. the instrumented UI tests (debug build) and their screenshots;
#   2. a smoke test of the minified, signed release APK, driven through
#      uiautomator, to catch anything R8 shrinking broke.
set -uo pipefail

APP=io.github.ocpabdurrahim.fin
DEBUG_APP=$APP.debug
OUT=emulator-out
mkdir -p "$OUT/screens" "$OUT/release"
status=0

adb install -r apks/debug/app-debug.apk
adb install -r apks/androidTest/debug/app-debug-androidTest.apk

echo "::group::Instrumented tests"
adb shell am instrument -w "$DEBUG_APP.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrumentation.txt"
echo "::endgroup::"
if ! grep -q "^OK (" "$OUT/instrumentation.txt"; then
  echo "::error::Instrumented tests failed"
  adb logcat -d > "$OUT/logcat-tests.txt"
  status=1
fi

# Screenshots live in the debug app's private files; run-as can read them.
for f in $(adb shell run-as "$DEBUG_APP" ls files/screens 2>/dev/null | tr -d '\r'); do
  adb exec-out run-as "$DEBUG_APP" cat "files/screens/$f" > "$OUT/screens/$f"
done
ls -la "$OUT/screens"

echo "::group::Release APK smoke test"
adb install -r apks/release/app-release.apk
adb logcat -c

alive() { [ -n "$(adb shell pidof "$APP" | tr -d '\r')" ]; }
shot() { adb exec-out screencap -p > "$OUT/release/$1.png"; }
dump() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/release/ui.xml" > /dev/null 2>&1
}
tap() {
  dump
  local xy
  xy=$(python3 .github/scripts/find_node.py "$OUT/release/ui.xml" "$1")
  if [ -z "$xy" ]; then
    echo "::error::'$1' was not found on screen"
    cp "$OUT/release/ui.xml" "$OUT/release/missing-$1.xml"
    return 1
  fi
  # shellcheck disable=SC2086
  adb shell input tap $xy
  sleep 2
}
check() {
  if ! alive; then
    echo "::error::The release app is not running after: $1"
    status=1
  fi
}

adb shell am start -W -n "$APP/$APP.MainActivity"
sleep 6
shot 01_home
check "launch"

n=2
for label in History Stats Budgets More Home; do
  tap "$label" || status=1
  shot "0${n}_${label}"
  check "$label"
  n=$((n + 1))
done

tap Add || status=1
adb shell input text 250
sleep 1
tap Food || status=1
tap Save || status=1
sleep 2
shot 08_after_adding
dump
if [ -z "$(python3 .github/scripts/find_node.py "$OUT/release/ui.xml" "250" --contains)" ]; then
  echo "::error::The new expense did not show up on the home screen"
  status=1
fi
check "adding an expense"

if adb logcat -d -b crash | grep -q "$APP"; then
  echo "::error::The release build crashed"
  adb logcat -d -b crash
  status=1
fi
echo "::endgroup::"

exit $status
