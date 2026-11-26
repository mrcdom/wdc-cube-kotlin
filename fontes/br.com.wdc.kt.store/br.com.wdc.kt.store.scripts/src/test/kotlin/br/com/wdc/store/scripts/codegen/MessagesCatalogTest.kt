package br.com.wdc.store.scripts.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every language has the same keys, with the same placeholders, and the generated files are what the catalogs say:
 * run `./gradlew :store-scripts:generateMessages` if not.
 */
class MessagesCatalogTest {

    private val storeDir = Path.of("").toAbsolutePath().parent

    @Test
    fun everyLanguage_hasTheSameKeys_andPlaceholders() {
        assertEquals(emptyList(), GenerateMessages.check(GenerateMessages.catalogs(storeDir)))
    }

    @Test
    fun theGeneratedFiles_matchTheCatalogs() {
        for (output in GenerateMessages.generate(storeDir)) {
            val committed = if (Files.exists(output.path)) Files.readString(output.path) else ""
            assertEquals(
                output.text,
                committed,
                "${storeDir.relativize(output.path)} is out of date: run ./gradlew :store-scripts:generateMessages",
            )
        }
    }
}
