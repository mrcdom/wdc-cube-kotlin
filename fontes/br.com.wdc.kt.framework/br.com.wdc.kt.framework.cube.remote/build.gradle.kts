dependencies {
    api(project(":framework-cube"))
    api(libs.javalin)
    api(libs.gson)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.logback.classic)
}
