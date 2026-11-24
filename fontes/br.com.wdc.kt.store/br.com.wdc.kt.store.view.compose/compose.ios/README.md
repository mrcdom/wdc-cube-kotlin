# compose.ios

**iOS** entry point. Produces the static `StoreApp` framework consumed by the Xcode project in `iosApp/`.

## Prerequisites

- **Xcode** installed (with Command Line Tools)
- **JDK 21** — the build script finds it through `/usr/libexec/java_home -v 21`
- **Backend running** on port 8080 (the app connects to `http://localhost:8080`)

## Deploying to the Simulator

1. Generate the Xcode project (if the `.xcodeproj` does not exist yet):

   ```bash
   cd iosApp && xcodegen generate
   ```

2. Open the project in Xcode:

   ```bash
   open iosApp/StoreiOS.xcodeproj
   ```

3. Select the simulator you want (e.g. **iPhone 17 Pro**) in the destination picker.

4. Run with **▶️ (Cmd+R)**.

   The Xcode *pre-build script* automatically runs:
   ```bash
   ./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64 --no-daemon
   ```

Or, if you prefer to build and install **from the terminal** without opening Xcode:

```bash
# 1. Build the Kotlin framework
cd fontes && ./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64 --no-daemon

# 2. Build the Xcode app for the simulator
cd ../iosApp
xcodebuild -project StoreiOS.xcodeproj \
  -scheme StoreiOS \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  build

# 3. Find the generated .app
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -name "StoreiOS.app" -path "*/Debug-iphonesimulator/*" | head -1)

# 4. Boot the simulator (if it is not running)
xcrun simctl boot "iPhone 17 Pro" 2>/dev/null || true

# 5. Install and launch on the simulator
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted br.com.wdc.store.ios
```

## Deploying to a Real Device

> **Note:** requires an Apple Developer account (free or paid) and a *Signing Certificate* set up in Xcode.

### One-time setup

1. In Xcode, open **StoreiOS.xcodeproj**.
2. Go to **Signing & Capabilities** of the **StoreiOS** target.
3. Check **Automatically manage signing** and select your **Team**.
4. Xcode will create a provisioning profile automatically.

### Point the framework at the device architecture

`project.yml` is set up for the simulator (`iosSimulatorArm64`). A real device needs the framework built for `iosArm64`. Change it temporarily in `iosApp/project.yml`:

```yaml
FRAMEWORK_SEARCH_PATHS:
  - "$(SRCROOT)/../fontes/br.com.wdc.kt.store/br.com.wdc.kt.store.view.compose/compose.ios/build/bin/iosArm64/debugFramework"
```

And update the pre-build script to build the right target:

```yaml
script: |
  cd "$SRCROOT/../fontes"
  export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home")
  ./gradlew :view-compose-ios:linkDebugFrameworkIosArm64 --no-daemon
```

Then regenerate the project: `cd iosApp && xcodegen generate`.

### Running on the device

1. Connect the iPhone through **USB** or set up **Wi-Fi debugging** (Xcode → Window → Devices and Simulators).
2. In the Xcode destination picker, choose the connected device.
3. Run with **▶️ (Cmd+R)**.
4. The first time, the iPhone will ask you to trust the developer certificate in **Settings → General → VPN & Device Management**.

### Backend URL on the device

On the simulator, `localhost` points to the Mac. On a real device you need the **IP address of the machine** running the backend. Change the URL in `iosApp/StoreApp/ContentView.swift`:

```swift
MainViewControllerKt.MainViewController(baseUrl: "http://192.168.x.x:8080")
```

## Building from the terminal (without the Xcode UI)

```bash
# Simulator
cd fontes && ./gradlew :view-compose-ios:linkDebugFrameworkIosSimulatorArm64 --no-daemon

# Real device
cd fontes && ./gradlew :view-compose-ios:linkDebugFrameworkIosArm64 --no-daemon

# Release (real device)
cd fontes && ./gradlew :view-compose-ios:linkReleaseFrameworkIosArm64 --no-daemon
```

## Native device APIs

Since it is compiled as a native framework through Kotlin/Native, the module has full access to the Apple APIs — including CoreBluetooth, CoreLocation, AVFoundation, HealthKit, etc. In the `iosMain` source set, import the platform frameworks directly (e.g. `import platform.CoreBluetooth.*`). Use Kotlin Multiplatform's `expect`/`actual` mechanism or the service locator (`AtomicRef`) already used in the project's architecture to inject platform-specific implementations.

See the official documentation: [Use platform-specific APIs (Kotlin Multiplatform)](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html)
