package br.com.wdc.store.scripts.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** The generated token files are what tokens.json says: run `./gradlew :store-scripts:generateDesignTokens` if not. */
class DesignTokensTest {

    @Test
    fun theGeneratedFiles_matchTokensJson() {
        // the tests run in the module's folder; the store's folder is its parent
        val storeDir = Path.of("").toAbsolutePath().parent
        for (output in GenerateDesignTokens.generate(storeDir)) {
            val committed = if (Files.exists(output.path)) Files.readString(output.path) else ""
            assertEquals(
                output.text,
                committed,
                "${storeDir.relativize(output.path)} is out of date: run ./gradlew :store-scripts:generateDesignTokens",
            )
        }
    }
}
