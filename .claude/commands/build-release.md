---
name: build-release
description: Guided pre-publish checklist — confirms everything is production-ready, then builds the signed App Bundle.
---

Walk through the release checklist for Kotlin Chess before producing a signed App Bundle.

## Pre-flight checks

1. Read CLAUDE.md to confirm project context.

2. Check the AdMob IDs:
   - Read `app/src/main/java/com/rabi/chess/ads/AdIds.kt`
   - If `INTERSTITIAL` still contains `ca-app-pub-3940256099942544/...` (the test ID),
     STOP and warn: "Test ad IDs detected — replace with real AdMob unit IDs before release."
   - Read `app/src/main/AndroidManifest.xml`
   - If the APPLICATION_ID meta-data still contains `~3347511713` (test App ID), same warning.

3. Run engine tests: `./gradlew :app:testDebugUnitTest`
   All 9 must pass. If any fail, STOP — do not build release until tests are green.

4. Check that `keystore.properties` exists in the project root:
   `ls -la keystore.properties`
   If missing, STOP and point to PUBLISHING.md Step 3-4 for keystore setup.

5. Check `versionCode` and `versionName` in `app/build.gradle.kts`.
   Remind: Play Store requires versionCode to increment with every upload.

## Build

6. Only if all checks pass, build the release bundle:
   `./gradlew bundleRelease`

7. Confirm the .aab was produced:
   `ls -lh app/build/outputs/bundle/release/app-release.aab`

## Report

Print a summary:
- Test result (9/9 pass or failures)
- Ad IDs: test or real (red flag if test)
- versionCode / versionName
- Path to the .aab, or what blocked the build
- Next step: see PUBLISHING.md Section 6 for Play Console upload
