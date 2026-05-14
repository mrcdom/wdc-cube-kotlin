# compose.ios

Ponto de entrada **iOS**. Produz o framework estático `ShoppingApp` consumido pelo projeto Xcode em `iosApp/`.

## Pré-requisitos

- **Xcode** instalado (com Command Line Tools)
- **JDK 21** — o build script localiza via `/usr/libexec/java_home -v 21`
- **Backend rodando** na porta 8080 (o app conecta em `http://localhost:8080`)

## Deploy no Simulador

1. Gere o projeto Xcode (se ainda não existir o `.xcodeproj`):

   ```bash
   cd iosApp && xcodegen generate
   ```

2. Abra o projeto no Xcode:

   ```bash
   open iosApp/ShoppingiOS.xcodeproj
   ```

3. Selecione o simulador desejado (ex: **iPhone 17 Pro**) no seletor de destino.

4. Rode com **▶️ (Cmd+R)**.

   O *pre-build script* do Xcode executa automaticamente:
   ```bash
   ./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64 --no-daemon
   ```

Ou, se preferir compilar e instalar **via terminal** sem abrir o Xcode:

```bash
# 1. Compilar o framework Kotlin
cd fontes && ./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64 --no-daemon

# 2. Compilar o app Xcode para o simulador
cd ../iosApp
xcodebuild -project ShoppingiOS.xcodeproj \
  -scheme ShoppingiOS \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  build

# 3. Localizar o .app gerado
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -name "ShoppingiOS.app" -path "*/Debug-iphonesimulator/*" | head -1)

# 4. Bootar o simulador (se não estiver rodando)
xcrun simctl boot "iPhone 17 Pro" 2>/dev/null || true

# 5. Instalar e abrir no simulador
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted br.com.wdc.shopping.ios
```

## Deploy em Dispositivo Real

> **Nota:** requer uma conta Apple Developer (gratuita ou paga) e um *Signing Certificate* configurado no Xcode.

### Configuração única

1. No Xcode, abra **ShoppingiOS.xcodeproj**.
2. Vá em **Signing & Capabilities** do target **ShoppingiOS**.
3. Marque **Automatically manage signing** e selecione seu **Team**.
4. O Xcode criará um provisioning profile automaticamente.

### Ajustar o framework para a arquitetura do dispositivo

O `project.yml` está configurado para o simulador (`iosSimulatorArm64`). Para dispositivo real, é necessário o framework compilado para `iosArm64`. Altere temporariamente em `iosApp/project.yml`:

```yaml
FRAMEWORK_SEARCH_PATHS:
  - "$(SRCROOT)/../fontes/br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.compose/compose.ios/build/bin/iosArm64/debugFramework"
```

E atualize o pre-build script para compilar o target correto:

```yaml
script: |
  cd "$SRCROOT/../fontes"
  export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home")
  ./gradlew :view-compose-ios:linkDebugFrameworkIosArm64 --no-daemon
```

Depois regenere o projeto: `cd iosApp && xcodegen generate`.

### Rodar no dispositivo

1. Conecte o iPhone via **USB** ou configure **Wi-Fi debugging** (Xcode → Window → Devices and Simulators).
2. No seletor de destino do Xcode, escolha o dispositivo conectado.
3. Rode com **▶️ (Cmd+R)**.
4. Na primeira vez, o iPhone pedirá para confiar no certificado do desenvolvedor em **Ajustes → Geral → Gerenciamento de Dispositivo**.

### URL do backend no dispositivo

No simulador, `localhost` aponta para o Mac. No dispositivo real, é necessário usar o **IP da máquina** onde o backend roda. Altere a URL em `iosApp/ShoppingApp/ContentView.swift`:

```swift
MainViewControllerKt.MainViewController(baseUrl: "http://192.168.x.x:8080")
```

## Compilação via terminal (sem Xcode UI)

```bash
# Simulador
cd fontes && ./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64 --no-daemon

# Dispositivo real
cd fontes && ./gradlew :view-compose-ios:linkDebugFrameworkIosArm64 --no-daemon

# Release (dispositivo real)
cd fontes && ./gradlew :view-compose-ios:linkReleaseFrameworkIosArm64 --no-daemon
```

## APIs nativas do dispositivo

Por ser compilado como framework nativo via Kotlin/Native, o módulo tem acesso completo às APIs Apple — incluindo CoreBluetooth, CoreLocation, AVFoundation, HealthKit, etc. No source set `iosMain`, importe diretamente os frameworks da plataforma (ex: `import platform.CoreBluetooth.*`). Use o mecanismo `expect`/`actual` do Kotlin Multiplatform ou o service locator (`AtomicRef`) já usado na arquitetura do projeto para injetar implementações específicas de plataforma.

Veja a documentação oficial: [Use platform-specific APIs (Kotlin Multiplatform)](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
