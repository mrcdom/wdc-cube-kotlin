dependencies {
    api(project(":framework-persistence"))
    api(libs.javalin)
    api(libs.gson)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.logback.classic)
}
