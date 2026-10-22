#!/usr/bin/env bash
# Runs inside the emulator job:
#   1. the instrumented UI tests (debug build) and their screenshots;
#   2. a smoke test of the minified, signed release APK, driven through
#      uiautomator, to catch anything R8 shrinking broke.
set -uo pipefail

APP=io.github.ocpabdurrahim.fintrack
DEBUG_APP=$APP.debug
OUT=emulator-out
mkdir -p "$OUT/screens" "$OUT/release"
status=0

# A slow system app (e.g. the launcher) can pop an "isn't responding" dialog over
# everything after a long run. Hide those; real crashes are still caught below
# from the crash log and by checking the app process.
adb shell settings put global hide_error_dialogs 1 || true

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
# Dismisses a system "isn't responding" dialog if one is showing anyway.
dismiss_system_dialog() {
  local xy
  xy=$(python3 .github/scripts/find_node.py "$OUT/release/ui.xml" "android:id/aerr_wait" --id)
  if [ -n "$xy" ] && [ "$xy" != "None" ]; then
    echo "Dismissing a system 'not responding' dialog"
    # shellcheck disable=SC2086
    adb shell input tap $xy
    sleep 2
    dump
  fi
}

# Taps the element with the given Compose test tag (exposed as a resource id).
tap() {
  dump
  dismiss_system_dialog
  local xy
  xy=$(python3 .github/scripts/find_node.py "$OUT/release/ui.xml" "$1" --id)
  if [ -z "$xy" ] || [ "$xy" = "None" ]; then
    echo "::error::'$1' was not found on screen"
    cp "$OUT/release/ui.xml" "$OUT/release/missing-$1.xml"
    return 1
  fi
  # shellcheck disable=SC2086
  adb shell input tap $xy
  sleep 2
}
text_of() {
  dump
  python3 .github/scripts/find_node.py "$OUT/release/ui.xml" "$1" --text-of
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
for tab in transactions stats budgets more home; do
  tap "nav_$tab" || status=1
  shot "0${n}_${tab}"
  check "$tab"
  n=$((n + 1))
done

tap fab_add || status=1
adb shell input text 250
sleep 1
tap cat_Food || status=1
tap save_button || status=1
sleep 2
shot 08_after_adding
check "adding an expense"
balance=$(text_of balance_total)
echo "Balance after adding a 250 expense: $balance"
if [ "$balance" != "-৳250" ]; then
  echo "::error::Expected the balance to read -৳250, got '$balance'"
  status=1
fi

if adb logcat -d -b crash | grep -q "$APP"; then
  echo "::error::The release build crashed"
  adb logcat -d -b crash
  status=1
fi
echo "::endgroup::"

exit $status
