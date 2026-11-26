plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "StoreNative"
            isStatic = true
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":store-presentation"))
            implementation(project(":store-persistence-client"))
            implementation(project(":store-design"))
            implementation(project(":store-i18n"))
        }
    }
}
