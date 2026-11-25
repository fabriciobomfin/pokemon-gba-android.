#!/usr/bin/env bash
set -uo pipefail
mkdir -p app/build/reports/device
# A fresh emulator presents Android's immersive-mode tutorial over the framebuffer.
adb shell settings put secure immersive_mode_confirmations confirmed || exit 1
adb shell svc wifi disable
adb shell svc data disable
gradle assembleDebugAndroidTest --no-daemon --stacktrace || exit 1
adb install -r app/build/upgrade/app-baseline.apk || exit 1
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit 1
test_result=0
adb shell am instrument -w -r com.fabricio.firegame.stable.testbuild.test/com.fabricio.gbaoffline.OfflineSmokeRunner | tee app/build/reports/device/instrumentation.txt
if ! grep -Fq 'OK (1 test)' app/build/reports/device/instrumentation.txt; then test_result=1; fi
adb exec-out run-as com.fabricio.firegame.stable.testbuild cat cache/smoke.png > app/build/reports/device/framebuffer.png || true
adb exec-out run-as com.fabricio.firegame.stable.testbuild cat cache/controls-wide.png > app/build/reports/device/controls-wide.png || true
adb exec-out run-as com.fabricio.firegame.stable.testbuild cat cache/editor.png > app/build/reports/device/editor.png || true
if [ "$test_result" -eq 0 ]; then
  adb shell am force-stop com.fabricio.firegame.stable.testbuild
  adb shell am instrument -w -r -e phase battery-reopen com.fabricio.firegame.stable.testbuild.test/com.fabricio.gbaoffline.OfflineSmokeRunner | tee app/build/reports/device/cold-boot.txt
  if ! grep -Fq 'OK (1 test)' app/build/reports/device/cold-boot.txt; then test_result=1; fi
fi
adb exec-out run-as com.fabricio.firegame.stable.testbuild cat cache/portrait.png > app/build/reports/device/portrait.png || true
adb exec-out run-as com.fabricio.firegame.stable.testbuild cat cache/battery-reopen.png > app/build/reports/device/battery-reopen.png || true
if [ "$test_result" -eq 0 ]; then
  for phase in ds ds-reopen; do
    adb shell am force-stop com.fabricio.firegame.stable.testbuild
    adb shell am instrument -w -r -e phase "$phase" com.fabricio.firegame.stable.testbuild.test/com.fabricio.gbaoffline.OfflineSmokeRunner | tee "app/build/reports/device/$phase.txt"
    if ! grep -Fq 'OK (1 test)' "app/build/reports/device/$phase.txt"; then test_result=1; break; fi
  done
fi
for image in studio-library studio-empty studio-themes studio-settings studio-fullscreen studio-integer cover-crop game-indicators full-backup library-covers library-redesign save-manager ds-wide ds-portrait ds-reopen ds-single ds-overlay-wide ds-overlay-portrait ds-touch-failure; do
  adb exec-out run-as com.fabricio.firegame.stable.testbuild cat "cache/$image.png" > "app/build/reports/device/$image.png" || true
done
adb logcat -d > app/build/reports/device/logcat.txt || true
if [ "$test_result" -eq 0 ]; then
  # Install a higher version without clearing data or uninstalling.
  adb shell am force-stop com.fabricio.firegame.stable.testbuild
  adb shell am instrument -w -r -e phase upgrade-seed com.fabricio.firegame.stable.testbuild.test/com.fabricio.gbaoffline.OfflineSmokeRunner | tee app/build/reports/device/upgrade-seed.txt
  grep -Fq 'OK (1 test)' app/build/reports/device/upgrade-seed.txt || exit 1
  adb shell am force-stop com.fabricio.firegame.stable.testbuild
  adb install -r app/build/upgrade/app-upgrade.apk || exit 1
  adb shell am instrument -w -r -e phase upgrade-check com.fabricio.firegame.stable.testbuild.test/com.fabricio.gbaoffline.OfflineSmokeRunner | tee app/build/reports/device/upgrade-check.txt
  grep -Fq 'OK (1 test)' app/build/reports/device/upgrade-check.txt || exit 1
fi
exit "$test_result"

