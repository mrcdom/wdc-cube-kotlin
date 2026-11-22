dependencies {
    api(project(":shopping-persistence"))
    implementation(libs.jooq.codegen)
    implementation(libs.h2)
    implementation(libs.logback.classic)
}

// Regenerates the jOOQ classes of :shopping-persistence from the DBCreate schema.
// The result is versioned: run it after changing the DDL and commit what changes.
tasks.register<JavaExec>("generateJooqSchema") {
    group = "codegen"
    description = "Generates the jOOQ classes of :shopping-persistence from the DBCreate schema"
    classpath = project.the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("br.com.wdc.shopping.scripts.codegen.GenerateJooqSchema")
    args(project(":shopping-persistence").projectDir.resolve("src/main/kotlin").absolutePath)
}
