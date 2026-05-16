# native.ios

Entry point iOS usando **UIKit** via Kotlin/Native interop Objective-C.

## Como compilar

```bash
cd fontes
./gradlew :view-native-ios:linkDebugFrameworkIosSimulatorArm64
```

O framework gerado (`ShoppingNative.framework`) deve ser integrado ao projeto Xcode.
