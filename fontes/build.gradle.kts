plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "br.com.wdc.kt"
    version = "1.0.0"

    repositories {
        google()
        mavenCentral()
    }
}

// KMP modules handle their own plugin configuration
val kmpModules = setOf("framework-commons", "framework-cube", "framework-domain", "shopping-domain", "shopping-presentation", "shopping-persistence-client", "view-compose", "view-compose-web", "view-compose-ios", "view-compose-android", "view-compose-desktop", "view-native-web", "view-native-ios", "view-native-android")

subprojects {
    if (name !in kmpModules) {
        apply(plugin = "org.jetbrains.kotlin.jvm")

        configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions {
                freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // `./gradlew test` também roda os testes comuns dos módulos multiplataforma, na JVM. (Com o plugin Android
    // antigo eles entravam pelos testes unitários do Android; o plugin multiplataforma novo não cria essa tarefa.)
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        afterEvaluate {
            if (tasks.findByName("test") == null && tasks.findByName("jvmTest") != null) {
                tasks.register("test") {
                    group = "verification"
                    description = "Runs the common tests on the JVM."
                    dependsOn("jvmTest")
                }
            }
        }
    }
}

// Framework modules published to Maven Central (artifactId = project name)
val publishedModules = mapOf(
    "framework-commons" to "Kotlin Multiplatform base utilities: serialization, logging, concurrency and coercion helpers",
    "framework-cube" to "Cube MVP: transactional navigation and presenter lifecycle for Kotlin Multiplatform",
    "framework-domain" to "Data access contracts for Kotlin Multiplatform: repository, criteria, projection, codecs and transactions",
    "framework-persistence" to "JDBC transactions with propagation, coroutine-aware, and a coordinator for client-driven remote transactions",
    "framework-jooq" to "Declarative jOOQ queries that load an object graph in one statement, for H2 and PostgreSQL",
)

/** Publishing to Maven Central, the same for every published module; only the kind of artifact changes. */
fun Project.publishToCentral(platform: com.vanniktech.maven.publish.Platform) {
    apply(plugin = "com.vanniktech.maven.publish")

    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        configure(platform)

        publishToMavenCentral()

        // Maven Central requires signatures; local publishing works without a key
        if (providers.gradleProperty("signingInMemoryKey").isPresent ||
            providers.gradleProperty("signing.keyId").isPresent
        ) {
            signAllPublications()
        }

        pom {
            name.set(project.name)
            description.set(publishedModules.getValue(project.name))
            url.set("https://github.com/mrcdom/wdc-cube-kotlin")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("mrcdom")
                    name.set("Marcelo Domingos")
                    url.set("https://github.com/mrcdom")
                }
            }
            scm {
                url.set("https://github.com/mrcdom/wdc-cube-kotlin")
                connection.set("scm:git:https://github.com/mrcdom/wdc-cube-kotlin.git")
                developerConnection.set("scm:git:ssh://git@github.com/mrcdom/wdc-cube-kotlin.git")
            }
        }
    }
}

configure(subprojects.filter { it.name in publishedModules }) {
    // Maven Central namespace verified through the GitHub account; "wdc.kt" mirrors br.com.wdc.kt
    group = "io.github.mrcdom.wdc.kt"
    version = "0.2.0"

    // Multiplatform modules: they apply the Kotlin Multiplatform plugin first, then the Android library one
    pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
        publishToCentral(
            com.vanniktech.maven.publish.KotlinMultiplatform(
                javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
                sourcesJar = true,
                androidVariantsToPublish = listOf("release"),
            )
        )
    }

    // JVM-only modules
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        publishToCentral(
            com.vanniktech.maven.publish.KotlinJvm(
                javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
                sourcesJar = true,
            )
        )
    }
}
