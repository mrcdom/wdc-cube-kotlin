plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":shopping-domain"))
        }
        jvmMain.dependencies {
            implementation(libs.okhttp)
        }
    }
}
