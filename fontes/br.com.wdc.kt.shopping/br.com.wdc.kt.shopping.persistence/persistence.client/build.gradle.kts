import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidLibrary {
        namespace = "br.com.wdc.shopping.persistence.client"
        compileSdk = 35
        minSdk = 26
    }
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
                // o alvo Android do plugin com.android.kotlin.multiplatform.library
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
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
