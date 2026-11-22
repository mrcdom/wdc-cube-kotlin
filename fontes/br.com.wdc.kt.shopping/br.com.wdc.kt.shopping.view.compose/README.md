# view-compose

Shared UI in **Compose Multiplatform** with Material 3. Holds all the screens and common components, consumed by the platform modules.

**Platforms:** JVM, Android, iOS, wasmJs

See the [Cube + Compose architecture](../../../docs/architecture-cube-compose.md) for details on the integration (ComposeCubeView, revision counter, safeCall, slots, view factories, per-platform startup) and the [architecture overview](../../../docs/architecture.md) for the project context.

## Platform Subprojects

- [compose.web/](compose.web/) — Web entry point (wasmJs/browser)
- [compose.ios/](compose.ios/) — iOS entry point (native framework)
- [compose.android/](compose.android/) — Android entry point (app)
- [compose.desktop/](compose.desktop/) — Desktop entry point (JVM)

## How to run

```bash
cd fontes

# Desktop
./gradlew :view-compose-desktop:run

# Web (port 8082)
./gradlew :view-compose-web:wasmJsBrowserDevelopmentRun

# Android
./gradlew :view-compose-android:assembleDebug

# iOS (through Xcode)
./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64
```

## Screens

| Login | Products |
|:---:|:---:|
| ![Login](screenshots/01-login.png) | ![Products](screenshots/02-produtos.png) |

| Product Details | Purchases |
|:---:|:---:|
| ![Product Details](screenshots/03-detalhes-produto.png) | ![Purchases](screenshots/04-compras.png) |

| Cart | Receipt |
|:---:|:---:|
| ![Cart](screenshots/05-carrinho.png) | ![Receipt](screenshots/06-recibo.png) |
