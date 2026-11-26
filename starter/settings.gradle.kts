pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("kotlinWrappers") {
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:2026.11.1")
        }
    }
}

rootProject.name = "cube-starter"

// The framework, built from this repository's sources until 0.5.0 is on Maven Central. Then remove this block: the
// coordinates in build.gradle.kts already name the published artifacts.
includeBuild("../fontes") {
    dependencySubstitution {
        substitute(module("io.github.mrcdom.wdc.kt:framework-commons")).using(project(":framework-commons"))
        substitute(module("io.github.mrcdom.wdc.kt:framework-cube")).using(project(":framework-cube"))
        substitute(module("io.github.mrcdom.wdc.kt:framework-domain")).using(project(":framework-domain"))
        substitute(module("io.github.mrcdom.wdc.kt:framework-rest")).using(project(":framework-rest"))
    }
}

// the modules live under src/; their Gradle names stay short
include(":shared", ":backend", ":frontend")
project(":shared").projectDir = file("src/shared")
project(":backend").projectDir = file("src/backend")
project(":frontend").projectDir = file("src/frontend")
