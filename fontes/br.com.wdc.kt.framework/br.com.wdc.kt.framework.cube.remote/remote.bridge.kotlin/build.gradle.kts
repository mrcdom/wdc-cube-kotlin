plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    // a browser library: the client side of framework-cube-remote-backend, with no UI technology
    js {
        browser()
    }

    sourceSets {
        jsMain.dependencies {
            // the handshake's textbook RSA
            api(project(":framework-commons"))
            implementation(libs.kotlinx.coroutines.core)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
