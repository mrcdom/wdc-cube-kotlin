# compose.desktop

Ponto de entrada **Desktop (JVM)**. Abre uma janela nativa com Compose.

```bash
cd fontes && ./gradlew :view-compose-desktop:run
```

## APIs nativas do dispositivo

Por rodar na JVM, o módulo tem acesso a todas as APIs Java/Kotlin — incluindo `javax.usb`, `jSerialComm` (portas seriais), `java.awt` (clipboard, system tray, impressão), `ProcessBuilder` (processos do SO), etc. Use o mecanismo `expect`/`actual` do Kotlin Multiplatform ou o service locator (`AtomicRef`) já usado na arquitetura do projeto para injetar implementações específicas de plataforma.

Veja a documentação oficial: [Use platform-specific APIs (Kotlin Multiplatform)](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
