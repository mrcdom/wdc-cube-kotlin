plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    jvm()
    android {
        namespace = "br.com.wdc.store.view.compose"
        compileSdk = 36
        minSdk = 26
    }
    wasmJs {
        browser()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets.all {
        languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(project(":store-presentation"))
            implementation(project(":store-persistence-client"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.compose.ui:ui-test:${libs.versions.compose.multiplatform.get()}")
            implementation(compose.desktop.currentOs)
        }


    }
}
