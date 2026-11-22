dependencies {
    api(project(":framework-persistence"))
    api(libs.javalin)
    api(libs.gson)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.h2)
    // the Swagger page (openApi { ui { } }) reads the webjar from the classpath; whoever turns it on declares the webjar
    testRuntimeOnly(libs.swagger.ui)
    testRuntimeOnly(libs.logback.classic)
}

// Reading bodies must not write final fields by reflection: JDK 26 warns it will block that (JEP 500).
// This task runs the same suite on JDK 26 with the block already on. The JDK comes from the toolchain (downloaded if missing).
val testFinalFields = tasks.register<Test>("testFinalFields") {
    description = "Runs the tests on JDK 26 with reflective mutation of final fields denied."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(26) }
    jvmArgs("--illegal-final-field-mutation=deny")
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") { dependsOn(testFinalFields) }
