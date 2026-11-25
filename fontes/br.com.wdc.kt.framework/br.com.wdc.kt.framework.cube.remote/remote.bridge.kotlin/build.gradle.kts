import java.util.Base64

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    // a browser library: the client side of framework-cube-remote-backend, with no UI technology
    js {
        browser()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// The shared conformance fixtures (../conformance/client), embedded in a test source: the tests run in a browser,
// which cannot read files. Base64, so nothing in a fixture needs escaping.
val generateConformanceFixtures by tasks.registering {
    val fixtures = layout.projectDirectory.dir("../conformance/client")
    val output = layout.buildDirectory.dir("generated/conformance/kotlin")
    inputs.dir(fixtures).withPropertyName("fixtures")
    outputs.dir(output)
    doLast {
        val files = fixtures.asFile.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        val entries = files.joinToString(",\n") { f ->
            val encoded = Base64.getEncoder().encodeToString(f.readBytes())
            "    \"${f.name}\" to \"$encoded\""
        }
        val file = output.get().file("br/com/wdc/framework/cube/remote/bridge/ConformanceFixtures.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package br.com.wdc.framework.cube.remote.bridge\n\n" +
                "/** Generated from ../conformance/client: each client fixture, by file name, in Base64. */\n" +
                "internal val CLIENT_FIXTURES: List<Pair<String, String>> = listOf(\n$entries,\n)\n",
        )
    }
}

kotlin.sourceSets.named("jsTest") {
    kotlin.srcDir(generateConformanceFixtures)
}
