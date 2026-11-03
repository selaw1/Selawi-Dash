# The car: what Selawi Dash can and cannot use

Measured on the owner's BYD Seal 05 DM-i, November 2026.

## Head unit

| | |
|---|---|
| System | BYD DiLink 5.0, Android 12 (API 32) |
| Chip | Qualcomm SM7325, 8 cores, 11 GB RAM, Adreno GPU (OpenGL ES 3.2) |
| Screen | 1920 × 1080 at 240 dpi = 1280 × 720 dp, 60 Hz, landscape only |
| BYD bars | 56 dp status bar on top, 60 dp climate bar at the bottom; full screen hides both |
| Apps | Installing APKs from Chrome works; split screen works |

## Works

- Full screen, opening other apps, returning to BYD home.
- GPS location and speed (no Google services needed).
- Microphone (recording works on all sources).
- Music: media-key controls always; song details from the car's Bluetooth media browser, which
  carries the phone's player even while it is on Android Auto.
- Call state from Android's audio mode (ringing / in call), for Bluetooth and Android Auto calls.

## Does not work, or is not available to apps

- Google Play Services: not genuine (ReVanced GmsCore is installed), so no Google Maps SDK.
- Notification access: BYD answers the settings screen with "multimedia system does not support
  this operation", so other apps' media sessions and notifications cannot be read directly.
- Speech recognition and English/Arabic text-to-speech: none on the car; the assistant must use
  online services.
- Cover art over Bluetooth: the car does not fetch it, even with the phone on AVRCP 1.6.
- Car data (battery, EV/HEV mode, indicators, real speedometer): only through BYD's private
  services, which this app does not use.
