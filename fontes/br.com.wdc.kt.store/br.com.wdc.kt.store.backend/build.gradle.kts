plugins {
    application
}

application {
    mainClass.set("br.com.wdc.store.backend.JavalinApplication")
}

// The backend requires the work directory; in development it is fontes/work (configuration, database, logs and
// the published frontends). Another one can be given with -Pworkdir=/path.
tasks.named<JavaExec>("run") {
    val workDir = providers.gradleProperty("workdir").orElse(rootProject.projectDir.resolve("work").absolutePath)
    args("--workdir=${workDir.get()}")
}

dependencies {
    api(project(":view-remote-host"))
    // the remote view's routes: the WebSocket and the session cookies of the client pages
    implementation(project(":framework-cube-remote-backend-javalin"))
    api(project(":persistence-rest"))
    api(project(":store-scripts"))
    api(project(":store-persistence"))
    api(project(":store-domain"))
    api(project(":framework-commons"))
    api(libs.javalin)
    api(libs.gson)
    implementation(libs.h2)
    implementation(libs.postgresql)
    implementation(libs.agroal.pool)
    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.embedded.postgres)
}
