plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    jvm()
    androidLibrary {
        namespace = "br.com.wdc.framework.domain"
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
        commonMain.dependencies {
            api(project(":framework-commons"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
        }
    }
}
