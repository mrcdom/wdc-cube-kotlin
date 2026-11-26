plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    // The remote view's client: it only paints the scopes the server sends (no presenters, domain or persistence)
    js {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled = true
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        getByName("jsMain") {
            dependencies {
                implementation(project(":store-design"))
                implementation(project(":framework-cube-remote-bridge-react"))
                implementation(libs.kotlinx.coroutines.core)

                implementation(kotlinWrappers.react)
                implementation(kotlinWrappers.reactDom)
                implementation(kotlinWrappers.emotion.react)
                implementation(kotlinWrappers.emotion.styled)
                implementation(kotlinWrappers.mui.material)
                implementation(kotlinWrappers.mui.iconsMaterial)

                implementation(npm("react", "19.2.0"))
                implementation(npm("react-dom", "19.2.0"))
                implementation(npm("@emotion/react", "11.14.0"))
                implementation(npm("@emotion/styled", "11.14.0"))
                implementation(npm("@emotion/css", "11.13.5"))
                implementation(npm("@mui/material", "5.18.0"))
                implementation(npm("@mui/icons-material", "5.18.0"))
            }
        }
    }
}
