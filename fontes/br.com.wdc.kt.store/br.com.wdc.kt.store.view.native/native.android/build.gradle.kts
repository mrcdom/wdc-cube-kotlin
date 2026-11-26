plugins {
    alias(libs.plugins.agp)
}

val baseUrl = project.findProperty("baseUrl")?.toString() ?: "http://10.0.2.2:8080"

android {
    namespace = "br.com.wdc.store.nativeui.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.wdc.store.nativeui.android"
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
    implementation(project(":store-presentation"))
    implementation(project(":store-persistence-client"))
    implementation(project(":store-domain"))
    implementation(project(":store-design"))
    implementation(project(":framework-commons"))
    implementation(project(":framework-cube"))
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("io.coil-kt:coil:2.7.0")
}
