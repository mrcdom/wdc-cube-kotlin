dependencies {
    api(project(":shopping-domain"))
    api(project(":framework-commons"))
    api(project(":framework-jooq"))
    api(libs.gson)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
