# WDC Cube Kotlin — WDC Store Demo

[![Build](https://github.com/mrcdom/wdc-cube-kotlin/actions/workflows/build.yml/badge.svg)](https://github.com/mrcdom/wdc-cube-kotlin/actions/workflows/build.yml)

Demo application for the **Cube MVP** framework in Kotlin Multiplatform, with three view modalities:

- **Compose Multiplatform** — presenters on the client (Desktop, Android, iOS, Web/WASM)
- **Native** — presenters on the client with native UI per platform (Android Views, UIKit, React/MUI)
- **React (Remote View)** — presenters on the server over WebSocket

The demo is the **WDC Store**; its code follows the name — packages `br.com.wdc.store`, Gradle modules `:store-*`, classes `Store*`, the `STORE_WORKDIR` variable and the `wedocode-store` database. Until 0.5.0 it was the Storefront, with identifiers in `shopping`: a database from then (`wedocode-shopping.mv.db`) is used again by renaming the file, and the mobile apps install as new apps (their ids changed).

**Documentation:** [Overview](docs/architecture.md) · [Cube Framework](docs/architecture-cube.md) · [Cube + Compose](docs/architecture-cube-compose.md) · [React View](docs/architecture-react.md) · [Persistence](docs/architecture-persistence.md) · [Security](docs/security-architecture.md) · [Several instances](docs/running-several-instances.md) · [Decision records](docs/adr/README.md)

**Contributing:** [CONTRIBUTING.md](CONTRIBUTING.md) · **Security:** [SECURITY.md](SECURITY.md) · **Changes:** [CHANGELOG.md](CHANGELOG.md) · **License:** [MIT](LICENSE)

**Starting your own application?** Copy [starter/](starter/README.md): the smallest Cube application (a task list with a detail, in the browser, and a small backend), with its tests.

## Structure

```
wdc-cube-kotlin/
├── docs/                  ← Architecture documentation
├── fontes/                ← Source code (Gradle project)
├── iosApp/                ← Xcode project (native iOS host)
└── starter/               ← The smallest Cube application, to copy (Gradle project of its own)
```

- [fontes/](fontes/) — Gradle project with all modules
- [docs/](docs/) — Architecture documentation
- [iosApp/](iosApp/) — Xcode project to run on iOS
- [starter/](starter/) — A starting point for a new application

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
(cd br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript && npm install && npm run build)

# Backend (port 8080): http://localhost:8080/ is a landing page that introduces the clients and links to the deployed ones
./gradlew :backend:run

# Desktop
./gradlew :view-compose-desktop:run

# Web (port 8082)
./gradlew :view-compose-web:wasmJsBrowserDevelopmentRun

# Android
./gradlew :view-compose-android:assembleDebug

# Tests (the whole check: ./gradlew build)
./gradlew test

# Browser tests of the web clients against a real backend (see br.com.wdc.kt.store.e2e/README.md)
(cd br.com.wdc.kt.store/br.com.wdc.kt.store.e2e && ./prepare.sh && npm ci && npx playwright install chromium && npx playwright test)
```

The CI ([`.github/workflows/build.yml`](.github/workflows/build.yml)) runs `./gradlew build`, the TypeScript checks, the browser tests and the starter's build on every push and pull request, and the iOS simulator tests on the main branch, weekly and on demand.

For **iOS**, open `iosApp/StoreiOS.xcodeproj` in Xcode and run it on the simulator.
