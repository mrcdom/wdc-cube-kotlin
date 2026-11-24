# compose.desktop

**Desktop (JVM)** entry point. Opens a native window with Compose.

```bash
cd fontes && ./gradlew :view-compose-desktop:run
```

## Native device APIs

Since it runs on the JVM, the module has access to every Java/Kotlin API — including `javax.usb`, `jSerialComm` (serial ports), `java.awt` (clipboard, system tray, printing), `ProcessBuilder` (OS processes), etc. Use Kotlin Multiplatform's `expect`/`actual` mechanism or the service locator (`AtomicRef`) already used in the project's architecture to inject platform-specific implementations.

See the official documentation: [Use platform-specific APIs (Kotlin Multiplatform)](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
