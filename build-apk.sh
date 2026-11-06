#!/usr/bin/env bash
# Builds Arc Launcher on Linux and installs it on a USB-connected phone if one is found.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[ -f local.properties ] || echo "sdk.dir=$SDK" > local.properties
./gradlew assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
echo "Built: $(pwd)/$APK"
ADB="$SDK/platform-tools/adb"
if [ "${1:-}" = "--install" ]; then
  "$ADB" install -r "$APK"
  echo "Installed. Press Home on the phone and pick Arc Launcher (or Settings > Apps > Default apps > Home app)."
fi
