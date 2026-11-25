pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("kotlinWrappers") {
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:2025.12.5")
        }
    }
}

rootProject.name = "wdc-cube-kotlin"

// Framework modules
include(":framework-commons")
include(":framework-cube")
include(":framework-domain")
include(":framework-persistence")
include(":framework-jooq")
include(":framework-rest")
include(":framework-cube-remote-host")
include(":framework-cube-remote-host-javalin")
include(":framework-cube-remote-bridge")
include(":framework-cube-remote-bridge-react")

// Store modules
include(":store-domain")
include(":store-persistence")
include(":persistence-rest")
include(":store-scripts")
include(":store-presentation")
include(":store-persistence-client")
include(":store-tests")
include(":view-remote-skeleton")
include(":view-remote-stress")
include(":view-remote-react-kotlin")
include(":view-compose")
include(":view-compose-web")
include(":view-compose-ios")
include(":view-compose-android")
include(":view-compose-desktop")
include(":view-native-web")
include(":view-native-ios")
include(":view-native-android")
include(":backend")

// Map module names to actual directory paths
project(":framework-commons").projectDir      = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.commons")
project(":framework-cube").projectDir         = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube")
project(":framework-domain").projectDir       = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.domain")
project(":framework-persistence").projectDir  = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.persistence")
project(":framework-jooq").projectDir         = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.jooq")
project(":framework-rest").projectDir         = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.rest")
project(":framework-cube-remote-host").projectDir          = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/remote.host")
project(":framework-cube-remote-host-javalin").projectDir  = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/remote.host.javalin")
project(":framework-cube-remote-bridge").projectDir        = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/remote.bridge.kotlin")
project(":framework-cube-remote-bridge-react").projectDir  = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube.remote/remote.bridge.kotlin.react")
project(":store-domain").projectDir        = file("br.com.wdc.kt.store/br.com.wdc.kt.store.domain")
project(":store-persistence").projectDir   = file("br.com.wdc.kt.store/br.com.wdc.kt.store.persistence/persistence.impl")
project(":persistence-rest").projectDir       = file("br.com.wdc.kt.store/br.com.wdc.kt.store.persistence/persistence.rest")
project(":store-scripts").projectDir       = file("br.com.wdc.kt.store/br.com.wdc.kt.store.scripts")
project(":store-presentation").projectDir  = file("br.com.wdc.kt.store/br.com.wdc.kt.store.presentation")
project(":store-persistence-client").projectDir = file("br.com.wdc.kt.store/br.com.wdc.kt.store.persistence/persistence.client")
project(":store-tests").projectDir         = file("br.com.wdc.kt.store/br.com.wdc.kt.store.tests")
project(":view-remote-skeleton").projectDir = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.skeleton")
project(":view-remote-stress").projectDir = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.stress")
project(":view-remote-react-kotlin").projectDir = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.remote/remote.views/remote.react.kotlin")
project(":view-compose").projectDir             = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.compose")
project(":view-compose-web").projectDir        = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.compose/compose.web")
project(":view-compose-ios").projectDir        = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.compose/compose.ios")
project(":view-compose-android").projectDir    = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.compose/compose.android")
project(":view-compose-desktop").projectDir    = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.compose/compose.desktop")
project(":view-native-web").projectDir         = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.native/native.web")
project(":view-native-ios").projectDir         = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.native/native.ios")
project(":view-native-android").projectDir     = file("br.com.wdc.kt.store/br.com.wdc.kt.store.view.native/native.android")
project(":backend").projectDir                = file("br.com.wdc.kt.store/br.com.wdc.kt.store.backend")
