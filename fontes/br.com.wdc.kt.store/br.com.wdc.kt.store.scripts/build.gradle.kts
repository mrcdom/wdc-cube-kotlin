dependencies {
    api(project(":store-persistence"))
    implementation(libs.jooq.codegen)
    implementation(libs.h2)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}

// Regenerates the jOOQ classes of :store-persistence from the DBCreate schema.
// The result is versioned: run it after changing the DDL and commit what changes.
tasks.register<JavaExec>("generateJooqSchema") {
    group = "codegen"
    description = "Generates the jOOQ classes of :store-persistence from the DBCreate schema"
    classpath = project.the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("br.com.wdc.store.scripts.codegen.GenerateJooqSchema")
    args(project(":store-persistence").projectDir.resolve("src/main/kotlin").absolutePath)
}

// Writes the design tokens of tokens.json (in :store-design) as Kotlin, TypeScript and CSS.
// The result is versioned: run it after changing tokens.json and commit what changes (DesignTokensTest checks).
tasks.register<JavaExec>("generateDesignTokens") {
    group = "codegen"
    description = "Generates the design tokens (Kotlin, TypeScript, CSS) from :store-design's tokens.json"
    classpath = project.the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("br.com.wdc.store.scripts.codegen.GenerateDesignTokens")
    args(projectDir.parentFile.absolutePath)
}
