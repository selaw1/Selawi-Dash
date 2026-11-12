# Selawi Dash

A full-screen dashboard for the BYD Seal 05 DM-i head unit, written in Kotlin with Jetpack Compose.
It is a normal app the driver opens; it does not replace or change BYD's software.

**Download:** https://selawi-dash.pages.dev

DiAuto does the heavy lifting (Android Auto, Google Maps on the instrument cluster, voice guidance,
the phone's music). Selawi Dash is the display on the centre screen.

## What it shows

- **Car panel:** driving status and heading, speed from GPS, a 3D car that turns under your finger
  when parked and drives down an animated road when moving, and today's trip.
- **Music:** song, artist, cover, progress and controls for whatever is playing, including the
  phone over Bluetooth / Android Auto. Covers are looked up online when the player sends none.
- **Calls:** a card while a call rings or runs, with a button to the car's phone app.
- **Time and weather:** current conditions, the next six hours, sunrise and sunset (Open-Meteo).
- **Dock:** home and all apps on the left, pinned apps in the middle, settings on the right.
- **Settings:** automatic day/night theme, accent colour wheel, full screen, keep screen on, a
  custom 3D car model, and a problem report.

## Install

Open **https://selawi-dash.pages.dev** in the car's browser, tap **Download APK** and open the file.
CI also publishes the same APK as the `cockpit-latest` release.

## Build

- Android Studio: open the repository and run the `app` configuration.
- Terminal: `./gradlew :app:assembleDebug` (output: `app/build/outputs/apk/debug/app-debug.apk`).
- Screenshots of every screen: `./gradlew :app:recordPaparazziDebug`
  (output: `app/src/test/snapshots/images/`). CI publishes them as the `screenshots-latest` release.

Release builds are signed in CI with a private key kept only in the repository's Actions secrets
(`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`).
The key is never committed. Local release builds without it are unsigned; debug builds use the
Android SDK's debug key.

## Code layout

`app/src/main/java/com/selawi/cockpit/`

| Folder | What is in it |
|---|---|
| `core/design` | Colours (day/night palettes, accent), type, icons, shared components |
| `core/platform` | Opening apps, location, crash log |
| `data/local` | Saved settings |
| `features/dashboard` | The home screen layout |
| `features/vehicle` | GPS driving state, the 3D car (Filament) and its road scene |
| `features/media` | Reading and controlling the playing music |
| `features/calls` | Call detection and the call card |
| `features/weather` | Forecast and weather drawing |
| `features/apps` | App list and pinned shortcuts |
| `features/settings` | Settings screen and accent colour picker |

Car facts the code relies on are in [docs/CAR.md](docs/CAR.md).

## Credits

- 3D car: "Car Concept" from the Khronos glTF Sample Assets, © 2024 Darmstadt Graphics Group GmbH,
  model and textures by Eric Chadwick, CC BY 4.0 (logo parts hidden).
- Fonts: Space Grotesk and Inter, SIL Open Font License 1.1 (`app/src/main/assets/licenses`).
- Weather: Open-Meteo. Covers: iTunes Search API.
