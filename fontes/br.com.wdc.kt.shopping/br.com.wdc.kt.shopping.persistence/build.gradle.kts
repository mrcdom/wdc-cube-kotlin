dependencies {
    api(project(":shopping-domain"))
    api(project(":framework-commons"))
    api(libs.gson)
    api(libs.jdbi3.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
