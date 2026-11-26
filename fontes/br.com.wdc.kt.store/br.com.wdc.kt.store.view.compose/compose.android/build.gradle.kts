plugins {
    alias(libs.plugins.agp)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val baseUrl = project.findProperty("baseUrl")?.toString() ?: "http://10.0.2.2:8080"

android {
    namespace = "br.com.wdc.store.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.wdc.store.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

// Kotlin comes built into the Android plugin (AGP 9): no separate Kotlin plugin is applied.
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(project(":view-compose"))
    implementation(project(":store-presentation"))
    implementation(project(":store-persistence-client"))
    implementation(project(":store-domain"))
    implementation(project(":framework-commons"))
    implementation(project(":framework-cube"))
    implementation(libs.okhttp)
    implementation(libs.gson)
}
