# Working on Selawi Dash

Selawi Dash is a display-only dashboard for the BYD Seal 05 DM-i head unit. It shows the car,
music, weather and calls on the centre screen. Read `README.md` and `docs/CAR.md` before changing anything.

## The car

- Check `docs/CAR.md` for what the head unit allows. Do not invent BYD APIs or assume access to
  car data (speed, battery, indicators): speed, braking and turns come from GPS.
- No root, no firmware changes, no extra hardware. The app must keep working when a permission or
  service is missing.
- A feature works on the car only once the owner has tested it there. Say what was checked and
  what was not.

## Code

- Kotlin and Jetpack Compose only. Keep the feature folders in `README.md`.
- Avoid new dependencies. Use services that need no API key; never put a key or secret in the app.
- Keep the release APK under 25 MiB (CI enforces this), 64-bit only, with the 3D engine whole.
- Comments are short and plain: say what the code does and why.

## Signing and secrets

- Never commit a keystore, password or token. Release signing comes only from the Actions secrets
  listed in `README.md`; `*.jks` and `*.keystore` stay in `.gitignore`.

## CI and git

- Actions minutes are limited. Batch app changes into one build. Page-only changes go through
  `site.yml`. Docs-only commits do not trigger a build.
- No emulator tests: its software GPU cannot compile the 3D engine's shaders.
- Clear commit messages that say why. No model names in commits, code or docs.
- Open a pull request only when asked. Never rewrite history.
