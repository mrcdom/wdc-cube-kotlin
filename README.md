# WDC Cube Kotlin — Storefront Demo

Demo application for the **Cube MVP** framework in Kotlin Multiplatform, with three view modalities:

- **Compose Multiplatform** — presenters on the client (Desktop, Android, iOS, Web/WASM)
- **Native** — presenters on the client with native UI per platform (Android Views, UIKit, React/MUI)
- **React (Remote View)** — presenters on the server over WebSocket

The application was first called "Shopping". Its code identifiers keep that name — packages (`br.com.wdc.shopping`), Gradle modules (`:shopping-*`), classes (`ShoppingApplication`), the `SHOPPING_WORKDIR` variable and the database names — so existing builds, configurations and databases keep working.

**Documentation:** [Overview](docs/architecture.md) · [Cube Framework](docs/architecture-cube.md) · [Cube + Compose](docs/architecture-cube-compose.md) · [React View](docs/architecture-react.md) · [Persistence](docs/architecture-persistence.md)

## Structure

```
wdc-cube-kotlin/
├── docs/                  ← Architecture documentation
├── fontes/                ← Source code (Gradle project)
└── iosApp/                ← Xcode project (native iOS host)
```

- [fontes/](fontes/) — Gradle project with all modules
- [docs/](docs/) — Architecture documentation
- [iosApp/](iosApp/) — Xcode project to run on iOS

## Prerequisites

- JDK 21
- Gradle 9.8 (via wrapper)
- Android Studio (for Android and Compose Web)
- Xcode 16+ (for iOS)
- Node.js (for the React client)

## How to run

```bash
cd fontes

# React client bundle, served by the backend (not versioned: build it once, and again after changing the client)
(cd br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.remote/remote.react/remote.react.client && npm install && npm run build)

# Backend (port 8080)
./gradlew :backend:run

# Desktop
./gradlew :view-compose-desktop:run

# Web (port 8082)
./gradlew :view-compose-web:wasmJsBrowserDevelopmentRun

# Android
./gradlew :view-compose-android:assembleDebug

# Tests
./gradlew test
```

For **iOS**, open `iosApp/ShoppingiOS.xcodeproj` in Xcode and run it on the simulator.
