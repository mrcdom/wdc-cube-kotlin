dependencies {
    api(project(":framework-persistence"))
    api(libs.javalin)
    api(libs.gson)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.logback.classic)
}

// A leitura dos corpos não pode gravar campo final por reflexão: o JDK 26 avisa que vai bloquear isso (JEP 500).
// Esta tarefa roda a mesma suíte no JDK 26 com o bloqueio já ligado. O JDK vem da toolchain (baixado se faltar).
val testFinalFields by tasks.registering(Test::class) {
    description = "Runs the tests on JDK 26 with reflective mutation of final fields denied."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(26) }
    jvmArgs("--illegal-final-field-mutation=deny")
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") { dependsOn(testFinalFields) }
