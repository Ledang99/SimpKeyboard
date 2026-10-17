# SimpKeyboard

SimpKeyboard is a modernized Android input method based on
[Hacker's Keyboard](https://github.com/klausw/hackerskeyboard) by Klaus Weidner.

It keeps the original PC-style 5-row layout (number row, Tab/Ctrl/Esc, arrow
keys, and punctuation in the usual places) and updates the project so it builds
and runs on current Android versions.

The original Hacker's Keyboard was written around 2011 against Android 2.3 APIs
and last targeted SDK 26. This project brings that same keyboard to:

- Android Gradle Plugin 8.7
- Gradle 8.11
- Java 17
- `compileSdk` / `targetSdk` 35 (Android 15)
- `minSdk` 23 (Android 6)

The Java package remains `org.pocketworkstation.pckeyboard` for compatibility
with the original layouts, JNI dictionary, and dictionary plugins. The installed
application id is `com.ledang.simpkeyboard`.

## Features

- 5-row PC-style QWERTY keyboard by default in portrait and landscape
- Separate number row, punctuation in familiar places, and arrow keys
- Tab, Ctrl, Alt, Esc, and function keys for terminals and SSH clients
- Multitouch modifier keys
- Language layouts from the original Hacker's Keyboard
- Optional word suggestions and dictionaries
- Setup screen to enable the IME in Android system settings

## Building

Requirements:

- JDK 17 or 21
- Android SDK with platform 35, Build-Tools 35, NDK 27.0.12077973, and CMake 3.22.1

From the repository root:

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release:

```bash
./gradlew assembleRelease
```

## Installing

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open **SimpKeyboard**, tap **Enable keyboard**, turn it on in the system
input-method list, and choose it with **Set input method**.

If an older keyboard layout is still showing after an update, clear app data:

```bash
adb shell pm clear com.ledang.simpkeyboard
```

## Relationship to the original project

This is not an official continuation by the original author. It is based on
Hacker's Keyboard (`klausw/hackerskeyboard`) and the AOSP Gingerbread LatinIME
from which that project was derived.

Upstream clone: [Ledang99/SimpKeyboard](https://github.com/Ledang99/SimpKeyboard)

## License

Apache License 2.0. See [LICENSE](LICENSE).
