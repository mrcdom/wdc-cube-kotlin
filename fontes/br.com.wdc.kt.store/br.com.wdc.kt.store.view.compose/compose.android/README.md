# compose.android

**Android** entry point. An application built on `androidx-activity-compose`.

```bash
cd fontes && ./gradlew :view-compose-android:assembleDebug
```

It can be run straight from Android Studio by selecting the `view-compose-android` module in the run configuration.

The backend URL is baked into the APK as `BuildConfig.BASE_URL` at build time (`-PbaseUrl=<url>`; default
`http://10.0.2.2:8080`, the host as seen from the emulator).

To build, install and launch in one step, use `deploy.sh` in this directory:

```bash
./deploy.sh emulator --emulator-name Pixel   # or: ./deploy.sh device
```

- `--backend-url <url>` — passed to the build as `-PbaseUrl` (default `http://10.0.2.2:8080` on the emulator,
  `http://<host-ip>:8080` on a device); it has no effect with `--skip-build`, since the APK is not rebuilt;
- `--emulator-name <name>` — the AVD (default: interactive selection);
- `--skip-build` — installs the APK already built;
- `--cold-boot` — restarts the emulator from scratch: shuts it down if it is running, then boots it without loading or saving a snapshot (the cure when the emulator's System UI stops responding);
- `--fresh` — restarts the launcher to clear its icon cache;
- `--release` (default: debug), `--clean` (see `./deploy.sh --help`).

## Native device APIs

Being a native Android app, the module has full access to the Android SDK — including Bluetooth, USB, camera, sensors, NFC, etc. Use Kotlin Multiplatform's `expect`/`actual` mechanism or the service locator (`AtomicRef`) already used in the project's architecture to inject platform-specific implementations.

See the official documentation: [Use platform-specific APIs (Kotlin Multiplatform)](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
