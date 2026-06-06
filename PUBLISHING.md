# Publishing to Google Play & earning from ads

This app ships with Google's **test** AdMob IDs so it runs immediately. To publish and
earn real revenue, do the following.

## 1. Switch to real AdMob IDs

1. Create an account at <https://admob.google.com> and add your app.
2. Create an **Interstitial** ad unit; copy its unit ID.
3. Replace the test ID in [`app/src/main/java/com/rabi/chess/ads/AdIds.kt`](app/src/main/java/com/rabi/chess/ads/AdIds.kt):
   ```kotlin
   const val INTERSTITIAL = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
   ```
4. Replace the test **App ID** in `app/src/main/AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.gms.ads.APPLICATION_ID"
       android:value="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX" />
   ```

> Never click your own live ads — AdMob will suspend the account. Test with the IDs above
> or register your device as a test device while developing.

## 2. Choose a unique application ID

In `app/build.gradle.kts`, change `applicationId` from `com.rabi.chess` to something you
own (e.g. `com.yourname.chess`). This is permanent once published.

## 3. Create a release keystore

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias chess \
  -keyalg RSA -keysize 2048 -validity 10000
```
Keep `release.jks` and its passwords safe and **out of git** (already gitignored).

## 4. Wire up release signing

Create `keystore.properties` in the project root (gitignored):
```
storeFile=release.jks
storePassword=…
keyAlias=chess
keyPassword=…
```
Then in `app/build.gradle.kts`, add a `signingConfigs { create("release") { … } }` block that
reads these properties and set `signingConfig = signingConfigs.getByName("release")` on the
`release` build type (currently it uses the debug signing config as a placeholder).

## 5. Build the App Bundle

```bash
./gradlew bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

## 6. Upload to Google Play

1. Create an app in the [Play Console](https://play.google.com/console) (one-time $25 fee).
2. Complete **Data safety**, **Content rating**, target audience, and a privacy policy.
3. Upload the `.aab` to a testing track, then promote to production.

## 7. Privacy / consent (before EEA release)

For users in the EEA/UK, integrate Google's **User Messaging Platform (UMP)** consent SDK
and request consent before initializing ads. This is required by AdMob policy for those
regions. (Not needed for the test build.)
