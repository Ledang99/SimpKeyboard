# Modernize Toolchain

## Overview

Upgrade the build toolchain from the deprecated stack (AGP 3.3.3, Gradle 5.6.4, JDK 8,
`com.android.support`) to a current one (AGP 8.x, Gradle 8.x, JDK 17+) so the project can
compile against modern SDKs. This is the hard prerequisite for reaching targetSdk 34+.

## Requirements

- Gradle + AGP versions chosen that support the required `compileSdk` and JDK.
- The native CMake build (`app/src/main/cpp`, `CMakeLists.txt`) keeps building without
  behavioral change.
- `compileSdkVersion`/`targetSdkVersion` can be raised to current after the upgrade.
- Developer build instructions (`COME COMPILARE.txt`) updated to the new toolchain.

## Constraints / Risks

- Raising AGP/Gradle requires JDK 17+; local SDK installs and `sdkmanager` already need
  JDK 17+ (observed).
- Some deprecated APIs may require `android:exported` or migration to `androidx` first.
- Higher risk than the in-place SDK bump; must be validated by installing on a real device.

## Task List

- [ ] Record current versions and lock in a target AGP/Gradle/JDK combo.
- [ ] Upgrade Gradle wrapper and AGP in `build.gradle`.
- [ ] Migrate to a current JDK for `gradlew` (update `COME COMPILARE.txt`).
- [ ] Rebuild native + Java, fix compile errors, install on Android 15 and regression-test
      keyboard layouts.
- [ ] Bump targetSdk to current (see render-target-sdk).
