# Sudoku

Ad-free Android Sudoku with 9×9, 12×12, 15×15 and 18×18 grids, three difficulty levels, a lives
system (5 to start, 1 lost per 3 mistakes, no timed regeneration — more only via Google Play Billing), and Firebase-powered global leaderboards.

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

Debug builds (`BuildConfig.USE_FIREBASE_EMULATOR == true`) connect to the host in `firebaseEmulatorHost`
(`gradle.properties`, currently the developer's LAN IP `192.168.178.86` so a physical device on the same
Wi-Fi can sync scores; the emulators bind `0.0.0.0` in `firebase.json`). Override per machine via
`local.properties` or on the command line — the Android emulator reaches the host through `10.0.2.2`:

```bash
./gradlew :app:installDebug -PfirebaseEmulatorHost=10.0.2.2
```

If the phone cannot connect, allow inbound TCP 8080 and 9099 through the host firewall.

`firestore.rules` enforces one score document per player per category
(`leaderboards/{gridSize}_{DIFFICULTY}/scores/{uid}`) and only allows updates that improve the score (higher points, then faster time, then fewer moves).

### Scoring

`ScoringRules` computes `total = (correct × base − mistakes × 20 + timeBonus) × multiplier`, floored at 0:

- base per correct player entry: 9×9 = 10, 12×12 = 15, 15×15 = 20, 18×18 = 25 (hint-filled cells earn nothing)
- multiplier: Easy ×1, Medium ×1.5, Hard ×2
- time bonus (on completion only): target = empty cells × 12/16/20 s (Easy/Medium/Hard); bonus scales linearly
  from `base × emptyCells` at 0 s down to 0 at the target

The live score is shown during play, itemised on the completion dialog, and synced to Firestore as `points`.

## In-app products

Create these in Play Console (Monetize → Products → In-app products):

| Product ID          | Type           | Price |
|---------------------|----------------|-------|
| `sudoku_lives_5`    | Consumable     | $0.99 |
| `sudoku_unlimited`  | Non-consumable | $4.99 |

Billing only works on a device with Google Play and a license-tested account; in the Android emulator
without Play the store screen shows an "unavailable" state.
