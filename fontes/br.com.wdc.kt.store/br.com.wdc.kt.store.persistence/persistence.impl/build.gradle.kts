dependencies {
    api(project(":store-domain"))
    api(project(":framework-commons"))
    api(project(":framework-jooq"))
    api(libs.gson)
    // the session events between instances on PostgreSQL (LISTEN/NOTIFY); the driver itself comes with the backend
    compileOnly(libs.postgresql)
    // counts sign-ins and sessions in Micrometer's global registry; the backend decides where they go
    implementation(libs.micrometer.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
