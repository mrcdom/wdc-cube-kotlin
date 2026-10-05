dependencies {
    api(project(":shopping-persistence"))
    implementation(libs.jooq.codegen)
    implementation(libs.h2)
    implementation(libs.logback.classic)
}

// Regenera as classes jOOQ de :shopping-persistence a partir do esquema de DBCreate.
// O resultado é versionado: rode depois de mudar o DDL e faça commit do que mudar.
tasks.register<JavaExec>("generateJooqSchema") {
    group = "codegen"
    description = "Generates the jOOQ classes of :shopping-persistence from the DBCreate schema"
    classpath = project.the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("br.com.wdc.shopping.scripts.codegen.GenerateJooqSchema")
    args(project(":shopping-persistence").projectDir.resolve("src/main/kotlin").absolutePath)
}
