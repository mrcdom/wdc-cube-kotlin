# compose.android

Ponto de entrada **Android**. Aplicação com `androidx-activity-compose`.

```bash
cd fontes && ./gradlew :view-compose-android:assembleDebug
```

Pode ser rodado diretamente pelo Android Studio selecionando o módulo `view-compose-android` na configuração de run.

## APIs nativas do dispositivo

Por ser um app Android nativo, o módulo tem acesso completo ao Android SDK — incluindo Bluetooth, USB, câmera, sensores, NFC, etc. Use o mecanismo `expect`/`actual` do Kotlin Multiplatform ou o service locator (`AtomicRef`) já usado na arquitetura do projeto para injetar implementações específicas de plataforma.

Veja a documentação oficial: [Use platform-specific APIs (Kotlin Multiplatform)](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
