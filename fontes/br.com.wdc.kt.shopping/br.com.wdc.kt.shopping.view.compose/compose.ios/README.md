# compose.ios

Ponto de entrada **iOS**. Produz o framework estático `ShoppingApp` consumido pelo projeto Xcode em `iosApp/`.

```bash
cd fontes && ./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64
```

Para rodar, abra `iosApp/ShoppingiOS.xcodeproj` no Xcode. O build phase executa o Gradle automaticamente.

## APIs nativas do dispositivo

Por ser compilado como framework nativo via Kotlin/Native, o módulo tem acesso completo às APIs Apple — incluindo CoreBluetooth, CoreLocation, AVFoundation, HealthKit, etc. No source set `iosMain`, importe diretamente os frameworks da plataforma (ex: `import platform.CoreBluetooth.*`). Use o mecanismo `expect`/`actual` do Kotlin Multiplatform ou o service locator (`AtomicRef`) já usado na arquitetura do projeto para injetar implementações específicas de plataforma.

Veja a documentação oficial: [Use platform-specific APIs (Kotlin Multiplatform)](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
