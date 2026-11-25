// The contract between the frontend and the backend: the data model and the calls. A call is written here as a
// request over HTTP (through CallHttpClient, which the frontend implements with fetch); the backend extends it with
// an …Action that answers with the repository.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val cube: String by rootProject.extra
val coroutines: String by rootProject.extra
val serialization: String by rootProject.extra

kotlin {
    jvmToolchain(21)

    jvm()
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api("io.github.mrcdom.wdc.kt:framework-commons:$cube")
            api("io.github.mrcdom.wdc.kt:framework-domain:$cube")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization")
        }
    }
}
