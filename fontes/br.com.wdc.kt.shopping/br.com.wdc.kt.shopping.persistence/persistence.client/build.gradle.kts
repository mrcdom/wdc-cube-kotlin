import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.agp.library)
}

android {
    namespace = "br.com.wdc.shopping.persistence.client"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidTarget()
    js(IR) {
        browser()
    }
    wasmJs {
        browser()
    }
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shopping-domain"))
        }

        val jvmCommonMain by getting {
            dependencies {
                implementation(libs.okhttp)
            }
        }
    }
}
