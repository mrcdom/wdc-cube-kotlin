dependencies {
    api(project(":shopping-presentation"))
    api(project(":shopping-persistence"))
    api(project(":shopping-scripts"))
    api(project(":framework-commons"))
    api(libs.javalin)
    api(libs.gson)
    implementation(libs.slf4j.api)
}

dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.logback.classic)
}
