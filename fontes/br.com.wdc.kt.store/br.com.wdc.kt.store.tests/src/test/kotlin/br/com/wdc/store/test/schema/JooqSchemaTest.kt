package br.com.wdc.store.test.schema

import br.com.wdc.store.domain.StoreTransactions
import br.com.wdc.store.persistence.StoreDSLContext
import br.com.wdc.store.persistence.scheme.sequences.SQ_USER
import br.com.wdc.store.persistence.scheme.tables.references.EN_PRODUCT
import br.com.wdc.store.persistence.scheme.tables.references.EN_PURCHASEITEM
import br.com.wdc.store.persistence.scheme.tables.references.EN_USER
import br.com.wdc.store.scripts.codegen.GenerateJooqSchema
import br.com.wdc.store.scripts.sgbd.DBReset
import br.com.wdc.store.test.util.TestEnvironment
import br.com.wdc.store.test.util.TestEnvironmentExtension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir

/** The versioned jOOQ classes match the schema, and the module's `DSLContext` works with them. */
class JooqSchemaTest {

    companion object {
        private val env = TestEnvironment("wedocode-store-jooq")

        @JvmField
        @RegisterExtension
        val envExtension = TestEnvironmentExtension(env)
    }

    private fun kotlinFiles(root: Path): Map<String, String> =
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList().associate { it.relativeTo(root).toString() to it.readText() }
        }

    @Test
    fun versionedClasses_matchWhatTheGeneratorProducesFromDBCreate(@TempDir tempDir: Path) {
        GenerateJooqSchema.generate(tempDir)

        val packagePath = GenerateJooqSchema.PACKAGE_NAME.replace('.', '/')
        val generated = kotlinFiles(tempDir.resolve(packagePath))
        // the test runs with the :store-tests module directory as the current one
        val versioned = kotlinFiles(
            Paths.get("../br.com.wdc.kt.store.persistence/persistence.impl/src/main/kotlin").resolve(packagePath).toAbsolutePath().normalize()
        )

        val hint = "schema and jOOQ classes diverged — run ./gradlew :store-scripts:generateJooqSchema and commit"
        assertEquals(generated.keys.sorted(), versioned.keys.sorted(), hint)
        for ((file, content) in generated) {
            assertEquals(content, versioned[file], "$file: $hint")
        }
    }

    @Test
    fun dslContext_readsTheSeededDataThroughTheGeneratedTables() {
        val dsl = StoreDSLContext.BEAN.get()
        assertEquals(3, dsl.fetchCount(EN_USER))
        assertEquals("admin", dsl.select(EN_USER.USERNAME).from(EN_USER).where(EN_USER.ID.eq(DBReset.ADMIN_ID)).fetchOne()!!.value1())
        assertEquals(4, dsl.fetchCount(EN_PRODUCT))
        assertTrue(dsl.fetchCount(EN_PURCHASEITEM) > 0)
        assertNotNull(dsl.nextval(SQ_USER))
    }

    @Test
    fun generatedTables_exposeTheirPrimaryKey() {
        assertEquals(listOf("ID"), EN_USER.primaryKey.fields.map { it.name })
        assertEquals(listOf("ID"), EN_PURCHASEITEM.`as`("x1").primaryKey.fields.map { it.name })
    }

    @Test
    fun moduleTransactionService_rollsBackWritesMadeThroughTheDslContext() = runBlocking {
        val dsl = StoreDSLContext.BEAN.get()
        val tx = StoreTransactions.BEAN.get()

        assertFailsWith<IllegalStateException> {
            tx.required {
                dsl.update(EN_USER).set(EN_USER.NAME, "temporary").where(EN_USER.ID.eq(DBReset.ADMIN_ID)).execute()
                assertEquals("temporary", dsl.select(EN_USER.NAME).from(EN_USER).where(EN_USER.ID.eq(DBReset.ADMIN_ID)).fetchOne()!!.value1())
                throw IllegalStateException("desiste")
            }
        }
        assertEquals("John Smith", dsl.select(EN_USER.NAME).from(EN_USER).where(EN_USER.ID.eq(DBReset.ADMIN_ID)).fetchOne()!!.value1())

        tx.required { dsl.update(EN_USER).set(EN_USER.NAME, "definitivo").where(EN_USER.ID.eq(DBReset.ADMIN_ID)).execute() }
        assertEquals("definitivo", dsl.select(EN_USER.NAME).from(EN_USER).where(EN_USER.ID.eq(DBReset.ADMIN_ID)).fetchOne()!!.value1())
    }
}
