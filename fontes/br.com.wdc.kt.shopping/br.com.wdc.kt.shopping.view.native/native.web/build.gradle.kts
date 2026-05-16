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
            }
        }
    }
}
