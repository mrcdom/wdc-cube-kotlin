plugins {
    alias(libs.plugins.agp)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "br.com.wdc.shopping.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.wdc.shopping.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(21)

    androidTarget()

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.androidx.activity.compose)
                implementation(project(":view-compose"))
                implementation(project(":shopping-presentation"))
                implementation(project(":shopping-persistence-client"))
                implementation(project(":shopping-domain"))
                implementation(project(":framework-commons"))
                implementation(project(":framework-cube"))
                implementation(libs.okhttp)
                implementation(libs.gson)
            }
        }
    }
}
