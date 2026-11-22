plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    jvm()
    android {
        namespace = "br.com.wdc.framework.cube"
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

    sourceSets {
        commonMain.dependencies {
            api(project(":framework-commons"))
            // a navegação marca o próprio contexto e faz o pedido de fora esperar a navegação em curso
            implementation(libs.kotlinx.coroutines.core)
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
