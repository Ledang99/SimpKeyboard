# Project

Hacker's Keyboard (fork of `org.pocketworkstation.pckeyboard`) is an Android soft-keyboard
app with a big native C++ core (`app/src/main/cpp`) for dictionary and layout processing,
plus per-language XML layouts under `app/src/main/res/xml*/`.

## Goals

- Fix on-device keyboard behavior (e.g. dot/space key ordering in vertical layouts).
- Modernize the app so it targets current Android SDKs and stops being flagged as "insecure"
  by Play Protect while retaining full functionality on modern devices (minSdk 14 +).
- Keep the app buildable on this repo's old toolchain (AGP 3.3.3 / Gradle 5.6.4 / JDK 8)
  until the full modernization is complete.

## Non-Goals

- Rewriting the C++ core.
- Redesigning the keyboard UI.
- Dropping support for older Android versions prematurely (minSdk stays 14 until decided).

## Key Directories

- `app/` — Android app module.
- `app/src/main/cpp/` — native dictionary/layout engine.
- `app/src/main/res/xml*/kbd_qwerty.xml` — per-language vertical keyboard layouts.
- `app/src/main/res/xml/kbd_full*.xml` — horizontal/full layouts (must stay unchanged).
- `specs/` — this openspec.

## Build

- JDK 8 (`C:\Program Files\Java\OpenJdk8`) for `gradlew`.
- sdkmanager needs JDK 17+ for installing SDK platforms.
- `.\gradlew.bat clean assembleDebug/Release --no-daemon`

## Signing

- Keystore: `my-release-key.keystore`, alias `myalias`.
- Debug builds install without a password; release requires the keystore password.

## Current State (2026-08-30)

- Dot/space swap applied to all 33 per-language `kbd_qwerty.xml` files; verified.
- `compileSdkVersion`/`targetSdkVersion` bumped 26 -> 28 (max for AGP 3.3.3).
- APK builds and installs on Android 15.
- Modernization to current SDKs (34/35+) is planned but not started.
