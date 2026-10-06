plugins {
    alias(libs.plugins.agp)
}

val baseUrl = project.findProperty("baseUrl")?.toString() ?: "http://10.0.2.2:8080"

android {
    namespace = "br.com.wdc.shopping.nativeui.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.wdc.shopping.nativeui.android"
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

// O Kotlin vem embutido no plugin Android (AGP 9): não se aplica plugin Kotlin à parte.
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shopping-presentation"))
    implementation(project(":shopping-persistence-client"))
    implementation(project(":shopping-domain"))
    implementation(project(":framework-commons"))
    implementation(project(":framework-cube"))
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("io.coil-kt:coil:2.7.0")
}
