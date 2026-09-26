---
name: sudoku-android-runtime
description: Run Sudoku Android emulator UI tests against local Firebase with native recording.
---

# Sudoku Android runtime tests

## Devin Secrets Needed
None for the local debug Firebase-emulator flows. Real billing needs a Play-enabled licensed test environment.

## Setup
- Use Java 17 for Gradle and Java 21 for firebase-tools.
- SDK is normally `/home/ubuntu/android-sdk`. Reuse the `sudoku_api36` AVD if present.
- Check `/dev/kvm` access and wait for `adb shell getprop sys.boot_completed` to return `1` before installing.
- Boot with software graphics if needed (`-gpu swiftshader_indirect -no-audio`).
- For an AVD, always install with `./gradlew :app:installDebug -PfirebaseEmulatorHost=10.0.2.2`. A checked-in host override may instead target a physical phone.
- Run Firebase Auth and Firestore emulators for project `sudoku-local` (9099/8080, UI4000); use the repo's `.firebaserc` as source of truth.
- Launch `com.maslarski.sudoku/.MainActivity`.

## Interaction and evidence
- Prefer native emulator mouse input. UIAutomator may stall while the gameplay timer updates.
- Record with `adb shell screenrecord --time-limit 180 /sdcard/NAME.mp4`, not obsolete scrcpy 1.21 on Android 16.
- Stop with SIGINT, wait for the recording process to exit, then pull the MP4. Validate duration with ffprobe; pulling too early may leave an invalid MP4.
- Use `adb exec-out screencap -p` for full-resolution Android evidence.
- For completion under limited hints, transcribe visible givens, solve locally, then enter missing digits through actual UI cell/keypad clicks. Do not modify saved state.
- Confirm remote score fields in Firebase Emulator UI, not only the app's cached leaderboard.
- Capture crash-buffer logcat and activity exit history; distinguish intentional force-stop/data-clear exits from app failures.
- Unlimited-lives purchase paths cannot be tested on an image without Play Billing.
