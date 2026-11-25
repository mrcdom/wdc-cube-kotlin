plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    // the React bindings of the Kotlin/JS client (the shell brings the react npm package)
    js {
        browser()
    }

    sourceSets {
        jsMain.dependencies {
            api(project(":framework-cube-remote-bridge"))
            api(kotlinWrappers.react)
        }
    }
}
