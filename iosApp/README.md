# iosApp

Native Xcode project that hosts the `StoreApp` framework produced by the `:view-compose-ios` module.

Generated with **XcodeGen** from `project.yml`.

## How to run

1. Open `StoreiOS.xcodeproj` in Xcode
2. Select the simulator (e.g. iPhone 16 Pro)
3. Run with ▶️ (Cmd+R)

The build phase compiles the Kotlin framework through Gradle automatically.

The app talks to the backend at `http://localhost:8080` unless the `BACKEND_URL` environment variable says otherwise (`deploy.sh --backend-url` passes it to the simulator as `SIMCTL_CHILD_BACKEND_URL`).
