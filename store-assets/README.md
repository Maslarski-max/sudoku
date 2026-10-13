# Google Play listing assets

| File | Size | Use |
|---|---|---|
| `app_icon_512.png` | 512×512 | Play Console app icon |
| `feature_graphic_1024x500.png` | 1024×500 | Feature graphic |
| `screenshot_1_home.png` | 1080×2340 | Grid size / difficulty selection |
| `screenshot_2_game_18x18.png` | 1080×2340 | 18×18 Hard gameplay (dark theme) |
| `screenshot_3_leaderboard.png` | 1080×2340 | Global leaderboard |
| `screenshot_4_store.png` | 1080×2340 | Store (5 Lives $0.99, Unlimited $4.99) |

Screenshots were captured from the debug build on a Pixel-class AVD (Android 16) with
`./gradlew :app:installDebug -PfirebaseEmulatorHost=10.0.2.2 -PfakeBilling=true`, against the local Firebase
emulator seeded with sample scores. Prices shown come from the fake billing stand-in; Play shows localized prices.
