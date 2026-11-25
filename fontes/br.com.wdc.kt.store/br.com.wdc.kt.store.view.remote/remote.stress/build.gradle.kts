plugins {
    application
}

application {
    mainClass.set("br.com.wdc.store.view.remote.stress.StressRunner_mainKt")
}

dependencies {
    implementation(project(":view-remote-skeleton"))
    implementation(project(":framework-commons"))
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
}
