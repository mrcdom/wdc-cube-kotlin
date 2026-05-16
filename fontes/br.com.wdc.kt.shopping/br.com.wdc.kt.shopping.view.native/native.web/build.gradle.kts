import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    wasmJs {
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
            }
        }
        binaries.executable()
    }

    sourceSets.all {
        languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
                implementation(project(":shopping-presentation"))
                implementation(project(":shopping-persistence-client"))
            }
        }
    }
}
