# Badminton Ledger

Native Android app (Kotlin + Jetpack Compose) for tracking a badminton group's shared costs: venue credit pool, weekly session splits, member balances, cash settlements, and shareable settlement posters.

A ground-up rebuild of a WeChat mini program, structured KMP-ready (pure-Kotlin `domain/` module) so an iOS target can be added later. Data migrates between the two apps via a shared backup JSON contract.

- Design: [docs/superpowers/specs/2026-07-11-android-app-design.md](docs/superpowers/specs/2026-07-11-android-app-design.md)
- Status: v1.0.0 released — see [Releases](https://github.com/maxwang/badmintion_android/releases) for a downloadable APK, or build your own below.

## Modules

- `domain/` — pure Kotlin: money math, report builders, mutations/validation, backup schema. No Android dependencies.
- `app/` — Android: Compose UI, DataStore persistence, poster rendering, share/import.

## Installing on a phone

Connect the phone with USB debugging enabled (or wireless debugging paired via Android Studio) and confirm it's visible:

```
adb devices
```

**Recommended: release build.** This is signed with the project's release key, so it updates in place over any previously installed release build without losing app data:

```
gradlew.bat assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

A release build needs `keystore.properties` + `release.jks` at the repo root (gitignored, generated once and kept only locally / in a private backup — see `docs/superpowers/plans/2026-07-15-m5-export-polish.md` for how they were created). Without them, `assembleRelease` still succeeds but produces an unsigned APK that can't be installed as an update.

Alternatively, grab the signed APK straight from the [latest GitHub release](https://github.com/maxwang/badmintion_android/releases/latest) instead of building it yourself.

**Debug build** (`gradlew.bat installDebug`, or `assembleDebug` + manual `adb install`) is faster for local iteration, but Android debug and release builds use different signing keys. If the phone already has a release build installed, installing debug fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — either uninstall the existing app first (exports a backup from Settings first, since this wipes app data) or stick to release builds for on-phone testing.
