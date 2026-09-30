# SimpKeyboard

SimpKeyboard is a lightweight Android input method based on the Android 2.3.3
AOSP LatinIME. This repository now builds as a standalone Android Studio project
instead of requiring a full Android platform source tree.

## Android support

- `compileSdk` / `targetSdk`: Android 16 (API 36), the latest stable Android SDK
- `minSdk`: Android 6.0 (API 23)
- Android Gradle Plugin 8.13.2 and Gradle 8.13
- Java 17
- Current NDK/CMake build for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`

Android 17 is still a preview. API 37 should be adopted after its SDK and
targeting behavior are stable and the keyboard has been tested on an Android 17
emulator or device.

## Build

Install Android Studio with Android SDK 36, CMake 3.22.1, and NDK
27.0.12077973, then run:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To install and enable it on a connected test device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable com.android.inputmethod.latin/.LatinIME
adb shell ime set com.android.inputmethod.latin/.LatinIME
```

You can also enable SimpKeyboard from **Settings → System → Keyboard →
On-screen keyboard**.

## Checks

```bash
./gradlew test lint
```

## Privacy and permissions

- Keyboard preferences and learned bigrams are excluded from cloud backup and
  device transfer.
- Contact suggestions from the historical AOSP version are disabled, so the app
  does not request contacts access.
- Voice input is off by default. Enabling it from SimpKeyboard settings requests
  microphone access at that point.
- Legacy voice telemetry is restricted to this application package; no typed
  text metadata is broadcast to other applications.

## Modernization status

The current migration makes the original keyboard installable on current
Android versions and fixes modern manifest, permission, resource, and 64-bit JNI
requirements. The keyboard rendering and settings layers intentionally remain
close to the AOSP implementation.

Future work should replace deprecated `Keyboard`/`KeyboardView`-era APIs and
`PreferenceActivity`, move learned words to an app-owned storage layer, add
instrumented IME lifecycle tests, and provide maintained language dictionaries.
