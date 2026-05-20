# WDC Cube Kotlin — Shopping Demo

Aplicação de demonstração do framework **Cube MVP** em Kotlin Multiplatform, com três modalidades de view:

- **Compose Multiplatform** — presenters no cliente (Desktop, Android, iOS, Web/WASM)
- **Native** — presenters no cliente com UI nativa por plataforma (Android Views, UIKit, React/MUI)
- **React (View Remota)** — presenters no servidor via WebSocket

**Documentação:** [Visão geral](docs/architecture.md) · [Framework Cube](docs/architecture-cube.md) · [Cube + Compose](docs/architecture-cube-compose.md) · [View React](docs/architecture-react.md) · [Persistência](docs/architecture-persistence.md)

## Estrutura

```
wdc-cube-kotlin/
├── docs/                  ← Documentação de arquitetura
├── fontes/                ← Código-fonte (projeto Gradle)
└── iosApp/                ← Projeto Xcode (host iOS nativo)
```

- [fontes/](fontes/) — Projeto Gradle com todos os módulos
- [docs/](docs/) — Documentação de arquitetura
- [iosApp/](iosApp/) — Projeto Xcode para rodar no iOS

## Pré-requisitos

- JDK 21
- Gradle 8.14 (via wrapper)
- Android Studio (para Android e Compose Web)
- Xcode 16+ (para iOS)
- Node.js (para o cliente React)

## Como rodar

```bash
cd fontes

# Backend (porta 8080)
./gradlew :backend:run

# Desktop
./gradlew :view-compose-desktop:run

# Web (porta 8082)
./gradlew :view-compose-web:wasmJsBrowserDevelopmentRun

# Android
./gradlew :view-compose-android:assembleDebug

# Testes
./gradlew test
```

Para **iOS**, abra `iosApp/ShoppingiOS.xcodeproj` no Xcode e rode no simulador.
