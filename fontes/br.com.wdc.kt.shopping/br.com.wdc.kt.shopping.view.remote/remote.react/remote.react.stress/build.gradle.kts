plugins {
    application
}

application {
    mainClass.set("br.com.wdc.shopping.view.react.stress.StressRunner_mainKt")
}

dependencies {
    implementation(project(":view-remote-react-skeleton"))
    implementation(project(":framework-commons"))
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
}
