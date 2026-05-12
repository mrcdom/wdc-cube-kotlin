plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.agp.library)
}

android {
    namespace = "br.com.wdc.framework.commons"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}

kotlin {
    jvmToolchain(21)

    jvm()
    androidTarget()
    wasmJs {
        browser()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.bignum)
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                api(libs.gson)
                compileOnly(libs.slf4j.api)
            }
        }
        val jvmMain by getting {
            dependsOn(jvmCommonMain)
        }
        val androidMain by getting {
            dependsOn(jvmCommonMain)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
        }
    }
}
