dependencies {
    api(project(":framework-cube"))
    // the answers are written as JSON with Gson's writer
    api(libs.gson)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.logback.classic)
}
