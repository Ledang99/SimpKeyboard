# Migrate to AndroidX

## Overview

Replace the deprecated `com.android.support` dependencies (appcompat-v7 27.1.1,
support-compat 26.0.0, support test runner) with the current AndroidX artefacts so the app
complies with modern Android packaging and can be released against current SDKs.

## Requirements

- All `android.support.*` imports migrated to `androidx.*`.
- `androidx.appcompat`, `androidx.core`, and AndroidX test runner/espresso replace legacy
  support artefacts.
- No behavior change to keyboard input, dictionary, or gesture handling.
- `android:exported` attributes set where components are declared (required from SDK 31+).

## Current State

- Not started. Legacy `com.android.support` is still in use. This migration is a
  prerequisite only for the strongest "modern" status; the minimal in-place SDK bump
  (to 28) did not require it.

## Task List

- [ ] Run/verify AndroidX migration on `app/` (rewrite imports + `build.gradle` deps).
- [ ] Resolve any overlapping AndroidX/support-classpath conflicts.
- [ ] Rebuild, install on Android 15, and regression-test layouts/languages.
