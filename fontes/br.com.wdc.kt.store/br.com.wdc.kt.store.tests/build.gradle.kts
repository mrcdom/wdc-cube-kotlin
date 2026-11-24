dependencies {
    testImplementation(project(":store-persistence"))
    testImplementation(project(":store-persistence-client"))
    testImplementation(project(":persistence-rest"))
    testImplementation(project(":store-scripts"))
    testImplementation(project(":store-presentation"))

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.logback.classic)
    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    testImplementation(libs.embedded.postgres)
    testImplementation(libs.jooq)
    testImplementation(libs.javalin)
    testImplementation(libs.gson)
    testImplementation(libs.okhttp)
}

// The same suite, on PostgreSQL. Uses an embedded PostgreSQL, which starts on its own (the first run downloads the
// binaries); with STORE_TEST_PG_URL (+ _USER, _PASSWORD) in the environment, uses that server — and deletes its data.
val testPostgres = tasks.register<Test>("testPostgres") {
    description = "Runs the test suite against PostgreSQL."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("store.test.db", "postgres")
    // they check the H2 schema and the migration of old H2 databases: they do not change with the suite's database
    filter {
        excludeTestsMatching("br.com.wdc.store.test.schema.SchemaMigrationTest")
        excludeTestsMatching("br.com.wdc.store.test.schema.JooqSchemaTest")
    }
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") { dependsOn(testPostgres) }
