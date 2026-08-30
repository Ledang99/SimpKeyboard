# Project Summary

Hacker's Keyboard is an Android soft keyboard app. Its source is an older port relying on
`com.android.support` (appcompat-v7 27.1.1), AGP 3.3.3, Gradle 5.6.4 and JDK 8. It still
compiles and installs, but only targets SDK 28 (the ceiling of the old toolchain), which
triggers Play Protect warnings on current Android.

An active branch (`fix/swap-dot-space-keys`) contains a keyboard layout fix (dot/space swap
in vertical layouts) already merged and installed, plus the SDK 26 -> 28 bump.

The modernization effort will carry the app to a current `targetSdk` (and ideally the
androidx migration) while preserving behavior and remaining buildable in CI. It is tracked
in `specs/` via capability specs below.
