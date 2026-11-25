// The browser: the presentation (presenters, scopes, keys), its React views, the HTTP client of the calls, and the
// entry point. The presenter tests run here too, in a headless Chrome.
plugins {
    kotlin("multiplatform")
}

val cube: String by rootProject.extra
val coroutines: String by rootProject.extra

kotlin {
    jvmToolchain(21)

    js {
        browser {
            commonWebpackConfig {
                cssSupport { enabled = true }
                // jsBrowserDevelopmentRun: the calls go to the backend running on its own (./gradlew :backend:run)
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    port = 8091
                    proxy = mutableListOf(
                        org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer.Proxy(
                            mutableListOf("/api"),
                            "http://localhost:8090",
                        ),
                    )
                }
            }
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared"))
            implementation("io.github.mrcdom.wdc.kt:framework-cube:$cube")
            implementation(kotlinWrappers.react)
            implementation(kotlinWrappers.reactDom)
            implementation(npm("react", "19.2.0"))
            implementation(npm("react-dom", "19.2.0"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines")
        }
    }
}
