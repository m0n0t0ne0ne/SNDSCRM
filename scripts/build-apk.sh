#!/usr/bin/env bash
# Builds the optimised arm64 APK. Needs JDK 17+ and an Android SDK with platform 37 (ANDROID_HOME).
# Signs with ~/.android/debug.keystore. The key is not in this repository: an APK signed with
# another key will not install over a copy signed with the original one.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT/base"
./gradlew --no-daemon --no-configuration-cache --max-workers=1 --no-parallel \
  --init-script "$ROOT/scripts/mirror.init.gradle.kts" -PoverDev -Pabis=arm64-v8a :app:assembleRelease
echo "built: $ROOT/base/app/build/outputs/apk/release/app-release.apk"
