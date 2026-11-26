plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
}

allprojects {
    group = "br.com.wdc.kt"
    version = "1.0.0"

    repositories {
        google()
        mavenCentral()
    }
}

// Kotlin/Native links run inside the Gradle daemon, and each takes a large part of its heap. With the configuration
// cache, the tasks of one project run in parallel: four framework links of one module at once ran the daemon (4 GB)
// out of memory. One link at a time, as without the cache.
abstract class NativeLinkSlot : org.gradle.api.services.BuildService<org.gradle.api.services.BuildServiceParameters.None>

val nativeLinkSlot = gradle.sharedServices.registerIfAbsent("kotlinNativeLink", NativeLinkSlot::class) {
    maxParallelUsages.set(1)
}

// KMP modules handle their own plugin configuration
val kmpModules = setOf("framework-commons", "framework-cube", "framework-cube-remote-bridge", "framework-cube-remote-bridge-react", "framework-domain", "store-design", "store-domain", "store-presentation", "store-persistence-client", "view-compose", "view-compose-web", "view-compose-ios", "view-compose-android", "view-compose-desktop", "view-native-web", "view-native-ios", "view-native-android", "view-remote-react-kotlin")

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

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
        usesService(nativeLinkSlot)
    }

    // `./gradlew test` also runs the common tests of the multiplatform modules, on the JVM. (With the old Android
    // plugin they came in through the Android unit tests; the new multiplatform plugin does not create that task.)
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
    "framework-rest" to "REST APIs on Javalin: resource routes, pluggable security, exception mapping, per-request context and transactions, OpenAPI",
    "framework-cube-remote-backend" to "Remote Cube MVP host: presenters on the server, views in a client over the remote protocol (sessions, views, dispatch, signed history)",
    "framework-cube-remote-backend-javalin" to "The remote Cube MVP host on Javalin: the WebSocket route and the session cookies of the client page",
    "framework-cube-remote-bridge" to "Kotlin/JS client of the remote Cube MVP host, with no UI technology: session, handshake, WebSocket, scopes and events",
    "framework-cube-remote-bridge-react" to "React bindings (kotlin-wrappers) for the Kotlin/JS client of the remote Cube MVP host",
)

/** Published multiplatform modules that have no Android target: published as soon as the Kotlin plugin is applied. */
val kmpWithoutAndroid = setOf("framework-cube-remote-bridge", "framework-cube-remote-bridge-react")

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
    // The API documentation (KDoc), published as each module's javadoc jar
    apply(plugin = "org.jetbrains.dokka")

    // The public API of each published module is kept in `api/` (a dump per platform); `check` fails when it changes
    // and the dump was not updated (`./gradlew updateKotlinAbi`), so a breaking change is never published by accident.
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
            abiValidation()
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
            abiValidation()
        }
    }

    // Maven Central namespace verified through the GitHub account; "wdc.kt" mirrors br.com.wdc.kt
    group = "io.github.mrcdom.wdc.kt"
    version = "0.5.0"

    // Multiplatform modules: they apply the Kotlin Multiplatform plugin first, then the Android library one
    pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
        publishToCentral(
            com.vanniktech.maven.publish.KotlinMultiplatform(
                javadocJar = com.vanniktech.maven.publish.JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
                sourcesJar = com.vanniktech.maven.publish.SourcesJar.Sources(),
                androidVariantsToPublish = listOf("release"),
            )
        )
    }

    // Multiplatform modules without an Android target (a browser-only library)
    if (name in kmpWithoutAndroid) {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            publishToCentral(
                com.vanniktech.maven.publish.KotlinMultiplatform(
                    javadocJar = com.vanniktech.maven.publish.JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
                    sourcesJar = com.vanniktech.maven.publish.SourcesJar.Sources(),
                )
            )
        }
    }

    // JVM-only modules
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        publishToCentral(
            com.vanniktech.maven.publish.KotlinJvm(
                javadocJar = com.vanniktech.maven.publish.JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
                sourcesJar = com.vanniktech.maven.publish.SourcesJar.Sources(),
            )
        )
    }
}
