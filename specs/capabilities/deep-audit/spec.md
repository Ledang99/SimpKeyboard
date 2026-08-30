# Deep Audit (security / backdoor)

## Overview

Establish that the built-and-installed APK originates from this repository's own sources and
contains no tampering, injected code, or backdoor, with a focus on the permissions and
native components an attacker could abuse.

## Current Findings (2026-08-30 — PASS)

- Installed `base.apk` SHA-256 matches the locally-built APK byte-for-byte
  (`d564d67ec1b9ba9dc4f17777c53f281fb950caadc01c113c7b2c5e36043861d1`).
- Contents are minimal and expected: one `classes.dex` + `libjni_pckeyboard.so` for the 4
  ABIs. No injected dex/jar/asset.
- Permissions are benign and minimal: `POST_NOTIFICATIONS`, `WRITE_USER_DICTIONARY`,
  `VIBRATE`, `READ_USER_DICTIONARY`. No network/SMS/location/contacts/storage/media, and
  critically **no `INTERNET`** permission — the app cannot exfiltrate data.
- The Play Protect "insecure / untrusted" label is the standard warning for
  adb-installed, old-targetSdk sideloaded apps, not evidence of malware.

## Task List

- [x] Compare on-device APK hash to the local build hash.
- [x] Enumerate APK contents (dex/native/asset) for anomalies.
- [x] Enumerate requested/granted permissions and confirm no `INTERNET` or data access.
- [ ] Re-run the audit on each future release build to keep the guarantee current.
