#!/bin/bash
TARGET="/sdcard/Android/data/com.cachekid.companion/files/navigation-input-sideload"
adb shell "rm -f $TARGET/*.json"
adb shell "rm -rf $TARGET/imported/*"

NOW_MS=$(($(date +%s) * 1000))

for i in 1 2 3 4 5 6; do
  MS=$((NOW_MS + i - 1))
  case $i in
    1) echo '{"latitude": 52.40, "longitude": 13.20, "headingDegrees": 45.0, "capturedAtEpochMillis": '$MS'}' ;;
    2) echo '{"latitude": 52.519, "longitude": 13.404, "headingDegrees": 0.0, "capturedAtEpochMillis": '$MS'}' ;;
    3) echo '{"latitude": 52.519, "longitude": 13.404, "headingDegrees": 180.0, "capturedAtEpochMillis": '$MS'}' ;;
    4) echo '{"latitude": 52.519, "longitude": 13.404, "headingDegrees": 90.0, "capturedAtEpochMillis": '$MS'}' ;;
    5) echo '{"latitude": 52.505, "longitude": 13.390, "headingDegrees": 30.0, "capturedAtEpochMillis": '$MS'}' ;;
    6) echo '{"latitude": 52.5199, "longitude": 13.4049, "headingDegrees": 0.0, "capturedAtEpochMillis": '$MS'}' ;;
  esac | adb shell "cat > $TARGET/test${i}.json"
done

echo "Tests injected"
