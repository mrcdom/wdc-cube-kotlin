dependencies {
    api(project(":store-presentation"))
    api(project(":store-persistence"))
    api(project(":store-scripts"))
    api(project(":framework-commons"))
    api(project(":framework-cube-remote-host"))
    api(libs.javalin)
    api(libs.gson)
    implementation(libs.slf4j.api)
}

dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.logback.classic)
}
