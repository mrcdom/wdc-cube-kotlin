# view-compose

UI compartilhada em **Compose Multiplatform** com Material 3. Contém todas as telas e componentes comuns, consumidos pelos módulos de plataforma.

**Plataformas:** JVM, Android, iOS, wasmJs

Veja a [documentação de arquitetura](../../../docs/architecture.md) para detalhes sobre o padrão Cube MVP com Compose.

## Subprojetos por Plataforma

- [compose.web/](compose.web/) — Entrada Web (wasmJs/browser)
- [compose.ios/](compose.ios/) — Entrada iOS (framework nativo)
- [compose.android/](compose.android/) — Entrada Android (app)
- [compose.desktop/](compose.desktop/) — Entrada Desktop (JVM)

## Como rodar

```bash
cd fontes

# Desktop
./gradlew :view-compose-desktop:run

# Web (porta 8082)
./gradlew :view-compose-web:wasmJsBrowserDevelopmentRun

# Android
./gradlew :view-compose-android:assembleDebug

# iOS (via Xcode)
./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64
```

## Telas

| Login | Produtos |
|:---:|:---:|
| ![Login](screenshots/01-login.png) | ![Produtos](screenshots/02-produtos.png) |

| Detalhes do Produto | Compras |
|:---:|:---:|
| ![Detalhes do Produto](screenshots/03-detalhes-produto.png) | ![Compras](screenshots/04-compras.png) |

| Carrinho | Recibo |
|:---:|:---:|
| ![Carrinho](screenshots/05-carrinho.png) | ![Recibo](screenshots/06-recibo.png) |
