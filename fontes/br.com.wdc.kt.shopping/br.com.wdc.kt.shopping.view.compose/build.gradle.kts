plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    jvm()
    androidLibrary {
        namespace = "br.com.wdc.shopping.view.compose"
        compileSdk = 35
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
            implementation(project(":shopping-presentation"))
            implementation(project(":shopping-persistence-client"))
        }


    }
}
