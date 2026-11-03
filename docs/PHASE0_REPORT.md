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
| Google services | Installed through ReVanced, most likely **ReVanced GmsCore** (a microG build), not real Google Play Services. The Google Maps SDK needs real Play Services, so an embedded Google map probably will not work; the free map does not need Google |
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

## Results (probe 0.1.4, on the car, parked)

### Device

| Item | Value |
|---|---|
| Head unit | BYD DiLink 5.0, Qualcomm SM7325 (Snapdragon 778G class), 8 cores, 11 GB RAM |
| Android | 12 (API 32), security patch 2025-03-05, build `eng.build.20260320.231810` |
| ABIs | arm64-v8a (+ 32-bit) |
| Screen | 1920 x 1080, 240 dpi (1.5x) = **1280 x 720 dp**, 60 Hz, landscape only |
| BYD bars | Top 84 px (56 dp) status bar, bottom 90 px (60 dp) bar with climate controls and a split-screen button |
| GPU | Adreno, OpenGL ES 3.2, Vulkan |
| Network | Wi-Fi, validated internet, ~450 ms to google.com and openfreemap.org |
| Other displays | Two 1280x480 BYD projection displays (cluster-related). Not used |
| Home apps | `com.android.launcher3` (BYD home), `com.byd.launchermap` (BYD map home) |

### Feature results

| Test | Result |
|---|---|
| Install from Chrome | ✅ Works |
| Full screen | ✅ Both BYD bars hide; exiting restores them |
| Animation | ✅ 60 fps, slowest frame 16 ms |
| App list and launching | ✅ 44 apps listed, apps open |
| Back to BYD home | ✅ Works |
| Free map inside the app (MapLibre) | ✅ Renders Amman with Arabic and English names, pan and zoom work |
| Google map inside the app (Maps SDK) | ❌ `SERVICE_INVALID`: a `com.google.android.gms` package exists but is not genuine Play Services; ReVanced GmsCore 7.3.0 is installed |
| Google Maps handoff / split screen | ❌ The **Google Maps app is not installed**, so both buttons had nothing to open |
| Waze handoff | ✅ Opens |
| Any map app (`geo:`) | ✅ Shows the app chooser |
| Media keys (play/pause/next/previous) | ✅ Control the playing app with no permission |
| Now playing (title, artwork) | ❌ Notification access not granted (whether a settings screen opened is still to confirm) |
| Microphone (MIC, VOICE_RECOGNITION, VOICE_COMMUNICATION) | ✅ All three record real audio (peak -28.5 dBFS) |
| Android speech recognition | ❌ No recognition service on the car |
| Text to speech | ❌ Only engine is `com.byd.autovoice`; English not supported, speak fails |
| Vendor package list | ⚠️ `getInstalledPackages` returned 0 on this firmware; the launcher query works |
| Reboot behaviour | Not tested yet |

### Conclusions

- The car is fast and modern (Android 12, 778G, 11 GB RAM, 60 fps). Compose is smooth. **Phase 1 can start.**
- Cockpit targets: minSdk 31 is safe for this car, but keep **minSdk 26** so other BYD models can install it. arm64 only.
- Layout target: 1280 x 720 dp full screen, 1280 x 604 dp with BYD bars. The bottom BYD bar holds climate
  controls, so the cockpit should let the driver show it again easily.
- **Maps (owner's decision):** navigation comes from the phone. The driver navigates in Google Maps on the
  phone and the car shows it through Android Auto projection (DiAuto today). No Waze. The cockpit sits beside
  DiAuto in BYD's split screen, and a navigation button opens DiAuto. The free MapLibre map stays available as
  a map panel when no phone is connected. DiAuto (AGPL-3.0) exposes no interface for other apps to show its map,
  so embedding it inside the cockpit is not possible today. Building our own Android Auto receiver is a large,
  licence-sensitive project and is not planned.
- **Media:** controls work today through media keys. Track title and artwork need notification access.
- **Voice:** the microphone works, but the car has no usable speech recognition or English/Arabic TTS. The
  assistant must record audio itself and use cloud speech-to-text and text-to-speech through our backend
  (or an offline engine bundled in the app). This does not depend on BYD or Google services.

### Still to test

1. **Media → Allow access**: does a settings screen open, or a "not supported" message?
2. The car's split-screen button with the probe on one side and DiAuto on the other.
3. Reboot: turn the car off and on, then open the probe again.
