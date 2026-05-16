plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.agp.library)
}

android {
    namespace = "br.com.wdc.shopping.presentation"
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

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":shopping-domain"))
                api(project(":framework-cube"))
            }
        }
    }
}
