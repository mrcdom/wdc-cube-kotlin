// The versions, in one place. Each module applies what it needs.
plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
}

allprojects {
    group = "com.example"
    version = "1.0.0"
}

/** The framework's version: one for all its modules. */
extra["cube"] = "0.5.0"
extra["coroutines"] = "1.11.0"
extra["serialization"] = "1.13.0"
extra["h2"] = "2.5.252"
extra["agroal"] = "3.3"

// The npm packages of the JS side (React, webpack…) are pinned in a yarn.lock, which the build checks. They are the
// frontend's: the lock lives in its folder, versioned, so whoever copies the starter gets the same versions.
// After changing an npm dependency or the Kotlin version: ./gradlew kotlinUpgradeYarnLock
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().lockFileDirectory = file("src/frontend")
}
