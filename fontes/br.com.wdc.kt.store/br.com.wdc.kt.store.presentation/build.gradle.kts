plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "br.com.wdc.store.presentation"
        compileSdk = 36
        minSdk = 26
    }
    js {
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
        getByName("commonMain") {
            dependencies {
                api(project(":store-domain"))
                api(project(":framework-cube"))
            }
        }
    }
}
