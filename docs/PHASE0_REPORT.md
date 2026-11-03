# Phase 0 report: BYD Seal 05 DM-i compatibility

Status: **test APK built, not yet run on the car.** Nothing below is confirmed on the
Seal 05 DM-i until the probe results are filled in at the end.

## What we already know from the owner

| Question | Answer |
|---|---|
| Installing APKs from Chrome | Works, many apps installed |
| Third-party apps | Run normally (Spotify, Waze, YouTube ReVanced, other map apps) |
| Screen orientation | Landscape only, does not rotate |
| Vehicle software | 23.1.22 |
| Android version / API level | Unknown, the probe reads it |
| Default launcher | **Not needed.** The cockpit is a normal app the driver opens; it then fills the screen |

## Research summary

### DiPlay (github.com/shihabal3amri/DiPlay)

- License: **GPL-3.0** (parts adapted from AGPL-3.0 DiAuto). Copying its code would force our
  app to be GPL as well. We use it only as a source of facts, and copy no code.
- Useful facts it documents for BYD DiLink head units:
  - DiLink 5.0 runs Android 12, DiLink 5.1 runs Android 13; older DiLink 3/4 run Android 10.
    BYD head units are arm64.
  - BYD's own home screen is `com.android.launcher3/.home.MainActivity`. Opening the standard
    HOME intent from our app returns the driver there.
  - BYD's settings app answers many standard `android.settings.*` screens with a
    "not supported" page (documented on DiLink 1.0). Permission screens we open
    (notification access, for example) may hit this. The probe tests it.
  - BYD shows its status and navigation bars in split screen, so split screen exists on at least
    some DiLink versions.
  - A normal app can run full screen without system privileges.

### Android APIs the cockpit relies on

| Feature | API | Needs |
|---|---|---|
| Full screen | `WindowInsetsControllerCompat.hide(systemBars())` | Nothing; firmware may ignore it |
| App list and launching | `PackageManager.queryIntentActivities`, `startActivity` | `QUERY_ALL_PACKAGES` (Android 11+) |
| Back to BYD screen | `ACTION_MAIN` + `CATEGORY_HOME` | Nothing |
| Embedded free map | MapLibre Android SDK + OpenFreeMap tiles | Internet, OpenGL ES 2/3. No key, no Google |
| Embedded Google map | Google Maps SDK for Android | Google Play Services on the car, plus an API key |
| Turn-by-turn in our own map | Google Navigation SDK | Separate paid license; not planned |
| Real Google Maps next to the cockpit | Split screen (`FLAG_ACTIVITY_LAUNCH_ADJACENT`) | Firmware must support split screen |
| Navigation handoff | `google.navigation:`, `geo:`, Waze links | The app being installed |
| Now playing + artwork + controls | `MediaSessionManager.getActiveSessions` | Notification access granted by the driver |
| Play/pause/next without permission | `AudioManager.dispatchMediaKeyEvent` | Nothing, but no track info |
| Speech to text | `SpeechRecognizer` | A recognition service (usually the Google app) |
| Text to speech | `TextToSpeech` | A TTS engine installed |
| Microphone | `AudioRecord` | `RECORD_AUDIO`; the car may hold the mic |

### Maps: what "Google Maps inside the cockpit" can mean

1. **Google map drawn inside our app (Maps SDK).** Looks native and fits our layout. It needs Google
   Play Services on the car and an API key. It gives a map, not Google Maps' turn-by-turn guidance.
   Routes and search are separate paid Google APIs with a free monthly allowance.
2. **Real Google Maps app beside the cockpit (split screen).** Full Google Maps navigation, but
   only if BYD allows split screen for third-party apps.
3. **Free map inside our app (MapLibre + OpenStreetMap data).** Works without Google services. Good
   fallback for a map panel.
4. An app cannot embed another app's live screen (for example the Google Maps app) inside its own
   window without system privileges. This is an Android restriction, not a BYD one.

The probe tests 1, 2 and 3 on the car.

## Technical decisions (provisional, confirm with probe results)

| Decision | Choice | Why |
|---|---|---|
| Minimum Android | API 24 (Android 7.0) for the probe | Installs on every DiLink generation. Raise for the cockpit once the car's level is known |
| Target / compile SDK | 35 | Current Play-era requirement; does not affect sideloading |
| CPU architecture | arm64-v8a + armeabi-v7a | Head units are ARM. MapLibre ships native code per ABI; keep only what the probe reports |
| Package names | `com.selawi.cockpit.probe` (probe), `com.selawi.cockpit` (cockpit) | Both can be installed side by side |
| Build | GitHub Actions | This development sandbox cannot download the Android SDK |
| Signing | Fixed test key in the repo for the probe only | Lets new probe builds install over old ones. The cockpit release will use a private key |
| Permissions | Internet, network state, Wi-Fi state, microphone, location, query all packages | Each one is tested by the probe |
| AI keys | Never in the APK; a backend proxy holds them | Requirement from the plan |

## Unknowns the probe answers

- Android version, API level, CPU, RAM, screen size and density, refresh rate.
- Whether Google Play Services and the Google Maps app are installed and usable.
- Whether the free map renders (OpenGL, internet).
- Whether full screen hides BYD's bars.
- Whether split screen with Google Maps works.
- Whether the notification access screen exists or BYD shows "not supported".
- Whether the microphone is usable by apps, or held by the car's own voice assistant.
- Whether speech recognition and text to speech exist, in English and Arabic.
- Animation smoothness (frames per second).
- Which BYD packages and system properties exist.

## Build

GitHub Actions builds the APK on every push that touches the probe
(`.github/workflows/probe.yml`).

- Download link, after logging in to GitHub in the car's Chrome, because the repository is private:
  `https://github.com/selaw1/Selawi-Dash/releases/download/probe-latest/cockpit-probe.apk`
- The same APK is attached to each workflow run as the `cockpit-probe` artifact.
- Local build in Android Studio: open the repository root, then run `./gradlew :probe:assembleDebug`.
  Output: `probe/build/outputs/apk/debug/probe-debug.apk`.
- Optional Google map test: add a repository secret `MAPS_API_KEY` (Google Cloud → Maps SDK for
  Android key) and re-run the workflow.

## Test checklist on the car (parked)

1. Download and install `cockpit-probe.apk` through Chrome. Open **Cockpit Probe**.
2. **Device** tab: wait for the report, then tap **Save to Downloads** and **Share** (or photograph
   each section).
3. **Screen** tab: tap **Go full screen**. Do the BYD bars disappear? Note the fps.
4. **Apps** tab: open Spotify, come back; tap **Open BYD home**, then reopen the probe.
5. **Maps** tab: allow location. Try **Free map**, **Google map**, **Google Maps: split screen**,
   **Google Maps: navigate**, **Waze: navigate**.
6. **Media** tab: play Spotify, try the media keys. Tap **Allow access**: does a real settings
   screen open? If access is granted, does the track and artwork show?
7. **Voice** tab: allow the microphone, run each mic test while speaking, try **Listen** in English
   and Arabic, try **Speak** in English and Arabic.
8. Close the app fully, reopen it. Turn the car off and on, then reopen it.

## Results

To be filled in from the car.
