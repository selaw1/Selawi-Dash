# Selawi Cockpit

A native Android (Kotlin + Jetpack Compose) cockpit app for the BYD Seal 05 DM-i head unit
(DiLink 5.0, Android 12): a full-screen, customizable dashboard with maps, media, app shortcuts
and, later, an AI voice assistant. It is a normal app the driver opens. It does not replace or
modify BYD's own software.

## Download (log in to GitHub first; the repository is private)

- Cockpit: https://github.com/selaw1/Selawi-Dash/releases/download/cockpit-latest/selawi-cockpit.apk
- Device probe: https://github.com/selaw1/Selawi-Dash/releases/download/probe-latest/cockpit-probe.apk

Every push rebuilds both through GitHub Actions; the links stay the same.

## Modules

- `app/`: the cockpit (`com.selawi.cockpit`). Features live under `features/` (dashboard, apps, media,
  navigation, weather, assistant, settings); shared design tokens and components under `core/design`.
- `probe/`: the Phase 0 device probe. Results are in [docs/PHASE0_REPORT.md](docs/PHASE0_REPORT.md).

## Decisions so far

- Navigation comes from the phone: Google Maps on the phone, shown on the car by DiAuto, with the
  cockpit beside it in BYD's split screen. The cockpit only opens DiAuto and contains none of its code.
- The in-app map is MapLibre with OpenFreeMap tiles: free and independent of Google services, which
  this car does not have.
- Weather comes from Open-Meteo (free, no key).
- Song title and artwork: BYD blocks the notification access screen. The cockpit tries Android's real
  screen directly and also reads Spotify's "Device Broadcast Status". Controls always work.
- The assistant will capture audio itself and use cloud speech services through a backend, because the
  car has no speech recognition or usable English/Arabic voice.

## Build locally

Open the repository in Android Studio, or run `./gradlew :app:assembleDebug`.
