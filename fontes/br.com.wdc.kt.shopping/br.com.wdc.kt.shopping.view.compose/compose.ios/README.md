# compose.ios

Ponto de entrada **iOS**. Produz o framework estático `ShoppingApp` consumido pelo projeto Xcode em `iosApp/`.

```bash
cd fontes && ./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64
```

Para rodar, abra `iosApp/ShoppingiOS.xcodeproj` no Xcode. O build phase executa o Gradle automaticamente.
