plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    jvm()
    androidLibrary {
        namespace = "br.com.wdc.framework.cube"
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

    sourceSets {
        commonMain.dependencies {
            api(project(":framework-commons"))
        }
        jvmMain.dependencies {
            api(libs.gson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
        }
    }
}
