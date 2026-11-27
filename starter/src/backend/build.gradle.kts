// The server: one route per call, answered by the JVM side of the shared calls, the tasks in an H2 database
// (framework-jooq, on an Agroal pool), and the frontend's bundle.
plugins {
    kotlin("jvm")
    application
}

val cube: String by rootProject.extra
val h2: String by rootProject.extra
val agroal: String by rootProject.extra

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation("io.github.mrcdom.wdc.kt:framework-rest:$cube")
    // the repository: framework-jooq brings framework-persistence (transactions) and jOOQ
    implementation("io.github.mrcdom.wdc.kt:framework-jooq:$cube")
    implementation("com.h2database:h2:$h2")
    implementation("io.agroal:agroal-pool:$agroal")
    implementation("org.slf4j:slf4j-simple:2.0.20")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.example.tasks.backend.MainKt")
}

// the frontend's production bundle goes in the backend's resources, under static/: one artifact serves both
tasks.processResources {
    from(project(":frontend").tasks.named("jsBrowserDistribution")) {
        into("static")
    }
}

tasks.test {
    useJUnitPlatform()
}
