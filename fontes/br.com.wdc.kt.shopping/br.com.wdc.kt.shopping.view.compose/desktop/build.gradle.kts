plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.desktop.currentOs)
    implementation(project(":view-compose"))
    implementation(project(":shopping-presentation"))
    implementation(project(":shopping-persistence-client"))
    implementation(project(":shopping-domain"))
    implementation(project(":framework-commons"))
    implementation(project(":framework-cube"))
    implementation(libs.okhttp)
    implementation(libs.gson)
}

compose.desktop {
    application {
        mainClass = "br.com.wdc.shopping.desktop.MainKt"
    }
}
