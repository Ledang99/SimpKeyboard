# Render / Target SDK

## Overview

Raise `targetSdkVersion` (and `compileSdkVersion`) so the app registers as "current" and is
no longer flagged as insecure by Android/Play Protect, while behaving correctly under modern
Android platform rules (notification permission, scoped storage, gesture nav, etc.).

## Requirements

- The published APK's `targetSdkVersion` reaches at least the highest level that keeps the
  app installable and warning-free on current Android (34/35/36 depending on acceptance).
- `minSdkVersion` policy is decided and documented (currently 14).
- No regression in keyboard behavior across the languages in `res/xml*/`.
- Build artifacts reproducible from a clean checkout.

## Current State

- Done: `compileSdkVersion`/`targetSdkVersion` 26 -> 28 (ceiling of AGP 3.3.3). Verified
  installed on Android 15 with `aapt dump badging` and `dumpsys package`.
- Blocked: reaching SDK 34/35+ requires the toolchain upgrade (see modernize-toolchain)
  because AGP 3.3.3 cannot compile against SDK > 28.

## Task List

- [x] Bump compile/target SDK to 28 within AGP 3.3.3.
- [x] Rebuild and install on Android 15; confirm `targetSdk=28`.
- [ ] After toolchain upgrade, bump to current targetSdk (34+) and fix listed deprecations.
- [ ] Add/refresh notification permission and any `exported` component attributes as required.
