#!/bin/sh
set -eu

android_home=${ANDROID_HOME:-"$HOME/Library/Android/sdk"}
adb="$android_home/platform-tools/adb"
emulator="$android_home/emulator/emulator"
avd=${SPROUTOS_ANDROID_AVD:-Pixel_3a_API_34_extension_level_7_arm64-v8a}

case "${1:-status}" in
  start)
    if "$adb" devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit !found }'; then
      echo "an Android device is already online; leaving it running"
    else
      "$emulator" -avd "$avd" -no-boot-anim &
      "$adb" wait-for-device
      until [ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
        sleep 2
      done
    fi
    ;;
  install-production)
    apk=${2:?usage: tools/emulator.sh install-production /absolute/path/to/signed.apk}
    "$adb" install -r "$apk"
    ;;
  install-debug)
    apk=${2:-app/build/outputs/apk/debug/app-debug.apk}
    "$adb" install -r "$apk"
    ;;
  status)
    "$adb" devices -l
    "$adb" shell pm list packages | grep '^package:com.sproutos.store' || true
    ;;
  *)
    echo "usage: tools/emulator.sh {start|status|install-production APK|install-debug [APK]}" >&2
    exit 2
    ;;
esac
