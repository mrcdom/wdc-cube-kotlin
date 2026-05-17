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

// Shopping modules
include(":shopping-domain")
include(":shopping-persistence")
include(":persistence-rest")
include(":shopping-scripts")
include(":shopping-presentation")
include(":shopping-persistence-client")
include(":shopping-tests")
include(":view-react-skeleton")
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
project(":shopping-domain").projectDir        = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.domain")
project(":shopping-persistence").projectDir   = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.persistence")
project(":persistence-rest").projectDir       = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.persistence.rest")
project(":shopping-scripts").projectDir       = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.scripts")
project(":shopping-presentation").projectDir  = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.presentation")
project(":shopping-persistence-client").projectDir = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.persistence.client")
project(":shopping-tests").projectDir         = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.tests")
project(":view-react-skeleton").projectDir    = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.react/react.skeleton")
project(":view-compose").projectDir             = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.compose")
project(":view-compose-web").projectDir        = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.compose/compose.web")
project(":view-compose-ios").projectDir        = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.compose/compose.ios")
project(":view-compose-android").projectDir    = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.compose/compose.android")
project(":view-compose-desktop").projectDir    = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.compose/compose.desktop")
project(":view-native-web").projectDir         = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.native/native.web")
project(":view-native-ios").projectDir         = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.native/native.ios")
project(":view-native-android").projectDir     = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.native/native.android")
project(":backend").projectDir                = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.backend")
