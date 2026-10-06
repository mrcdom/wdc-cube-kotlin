dependencies {
    testImplementation(project(":shopping-persistence"))
    testImplementation(project(":shopping-persistence-client"))
    testImplementation(project(":persistence-rest"))
    testImplementation(project(":shopping-scripts"))
    testImplementation(project(":shopping-presentation"))

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

// A mesma suíte, em PostgreSQL. Usa um PostgreSQL embutido, que sobe sozinho (a primeira execução baixa os
// binários); com SHOPPING_TEST_PG_URL (+ _USER, _PASSWORD) no ambiente, usa esse servidor — e apaga os dados dele.
val testPostgres by tasks.registering(Test::class) {
    description = "Runs the test suite against PostgreSQL."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("shopping.test.db", "postgres")
    // conferem o esquema do H2 e a migração dos bancos H2 antigos: não mudam com o banco da suíte
    filter {
        excludeTestsMatching("br.com.wdc.shopping.test.schema.SchemaMigrationTest")
        excludeTestsMatching("br.com.wdc.shopping.test.schema.JooqSchemaTest")
    }
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") { dependsOn(testPostgres) }
