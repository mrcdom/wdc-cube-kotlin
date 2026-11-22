# compose.android

**Android** entry point. An application built on `androidx-activity-compose`.

```bash
cd fontes && ./gradlew :view-compose-android:assembleDebug
```

It can be run straight from Android Studio by selecting the `view-compose-android` module in the run configuration.

## Native device APIs

Being a native Android app, the module has full access to the Android SDK — including Bluetooth, USB, camera, sensors, NFC, etc. Use Kotlin Multiplatform's `expect`/`actual` mechanism or the service locator (`AtomicRef`) already used in the project's architecture to inject platform-specific implementations.

See the official documentation: [Use platform-specific APIs (Kotlin Multiplatform)](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
