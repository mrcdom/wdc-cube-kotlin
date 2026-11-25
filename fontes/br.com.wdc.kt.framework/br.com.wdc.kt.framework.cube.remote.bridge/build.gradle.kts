plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    // a browser library: the client side of framework-cube-remote
    js {
        browser()
    }

    sourceSets {
        jsMain.dependencies {
            // the handshake's textbook RSA
            api(project(":framework-commons"))
            implementation(libs.kotlinx.coroutines.core)
            // the bindings for React shells (the shell brings the react npm package)
            api(kotlinWrappers.react)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
