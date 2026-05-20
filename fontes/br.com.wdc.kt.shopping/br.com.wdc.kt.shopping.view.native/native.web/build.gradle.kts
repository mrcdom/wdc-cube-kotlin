import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    js(IR) {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    port = 8083
                    proxy = mutableListOf(
                        KotlinWebpackConfig.DevServer.Proxy(
                            mutableListOf("/api"),
                            "http://localhost:8080"
                        )
                    )
                }
                cssSupport {
                    enabled = true
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":shopping-presentation"))
                implementation(project(":shopping-persistence-client"))

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
