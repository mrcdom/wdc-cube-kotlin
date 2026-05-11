plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":shopping-domain"))
                api(project(":framework-cube"))
            }
        }
    }
}
