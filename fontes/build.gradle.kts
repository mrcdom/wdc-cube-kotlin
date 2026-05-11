plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

allprojects {
    group = "br.com.wdc.kt"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

// KMP modules handle their own plugin configuration
val kmpModules = setOf("framework-commons", "framework-cube", "shopping-domain", "shopping-presentation", "shopping-api-client")

subprojects {
    if (name !in kmpModules) {
        apply(plugin = "org.jetbrains.kotlin.jvm")

        configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
