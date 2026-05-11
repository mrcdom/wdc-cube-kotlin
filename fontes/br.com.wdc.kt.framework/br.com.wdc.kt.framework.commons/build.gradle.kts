dependencies {
    api(libs.gson)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
