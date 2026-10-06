dependencies {
    api(project(":framework-persistence"))
    api(libs.jooq)
    api(libs.gson)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    testImplementation(libs.embedded.postgres)
    testRuntimeOnly(libs.logback.classic)
}
