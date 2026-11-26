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
        compileSdk = 37
        minSdk = 26
    }
    wasmJs {
        browser()
        // Compose 1.12 checks that a Wasm test compilation that sees Compose UI is bundled with an executable, so that
        // Skiko loads (CMP-4906); the app itself is in :view-compose-web
        binaries.executable()
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
            implementation(project(":store-design"))
            implementation(project(":store-presentation"))
            implementation(project(":store-i18n"))
            implementation(project(":store-persistence-client"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.compose.ui:ui-test:${libs.versions.compose.multiplatform.get()}")
            implementation(compose.desktop.currentOs)
        }


    }
}
