# Sudoku

Ad-free Android Sudoku with 9×9, 12×12, 15×15 and 18×18 grids, three difficulty levels, a lives
system backed by Google Play Billing, and Firebase-powered global leaderboards.

- Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt
- compileSdk/targetSdk 36 · minSdk 26
- Room (auto-save, leaderboard cache) · DataStore (lives) · Firebase Auth (anonymous) + Firestore
- Google Play Billing Library 9

## Build

```bash
./gradlew :app:assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # engine + game logic unit tests
./gradlew :app:lintDebug
```

`google-services.json` is git-ignored. Without it, debug builds initialise Firebase programmatically
against the local emulator (see `FirebaseInitializer`). Release builds require the real file from the
Firebase console.

## Firebase emulator

```bash
npm install -g firebase-tools
firebase emulators:start            # Auth :9099, Firestore :8080, UI :4000
```

Debug builds (`BuildConfig.USE_FIREBASE_EMULATOR == true`) talk to `10.0.2.2` (the Android emulator's
alias for the host). For a physical device, pass your machine's LAN IP:

```bash
./gradlew :app:installDebug -PfirebaseEmulatorHost=192.168.1.20
```

`firestore.rules` enforces one score document per player per category
(`leaderboards/{gridSize}_{DIFFICULTY}/scores/{uid}`) and only allows updates that improve the score.

## In-app products

Create these in Play Console (Monetize → Products → In-app products):

| Product ID          | Type           | Price |
|---------------------|----------------|-------|
| `sudoku_lives_5`    | Consumable     | $0.99 |
| `sudoku_unlimited`  | Non-consumable | $4.99 |

Billing only works on a device with Google Play and a license-tested account; in the Android emulator
without Play the store screen shows an "unavailable" state.
