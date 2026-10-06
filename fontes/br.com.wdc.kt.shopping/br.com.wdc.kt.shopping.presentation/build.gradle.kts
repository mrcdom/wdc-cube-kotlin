plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidLibrary {
        namespace = "br.com.wdc.shopping.presentation"
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
