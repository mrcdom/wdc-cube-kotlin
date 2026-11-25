dependencies {
    api(project(":framework-cube"))
    // the answers are written as JSON with Gson's writer
    api(libs.gson)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.logback.classic)
}

// the shared conformance fixtures (../conformance): the host's runner reads them from there
tasks.withType<Test>().configureEach {
    val conformance = layout.projectDirectory.dir("../conformance")
    inputs.dir(conformance).withPropertyName("conformance")
    systemProperty("conformance.dir", conformance.asFile.absolutePath)
}
