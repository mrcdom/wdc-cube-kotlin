package br.com.wdc.shopping.test.schema

import br.com.wdc.shopping.domain.ShoppingTransactions
import br.com.wdc.shopping.persistence.ShoppingDSLContext
import br.com.wdc.shopping.persistence.scheme.sequences.SQ_USER
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PRODUCT
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_PURCHASEITEM
import br.com.wdc.shopping.persistence.scheme.tables.references.EN_USER
import br.com.wdc.shopping.scripts.codegen.GenerateJooqSchema
import br.com.wdc.shopping.scripts.sgbd.DBReset
import br.com.wdc.shopping.test.util.TestEnvironment
import br.com.wdc.shopping.test.util.TestEnvironmentExtension
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

/** As classes jOOQ versionadas conferem com o esquema, e o `DSLContext` do módulo funciona com elas. */
class JooqSchemaTest {

    companion object {
        private val env = TestEnvironment("wedocode-shopping-jooq")

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
        // o teste roda com o diretório do módulo :shopping-tests como corrente
        val versioned = kotlinFiles(
            Paths.get("../br.com.wdc.kt.shopping.persistence/persistence.impl/src/main/kotlin").resolve(packagePath).toAbsolutePath().normalize()
        )

        val hint = "esquema e classes jOOQ divergiram — rode ./gradlew :shopping-scripts:generateJooqSchema e faça commit"
        assertEquals(generated.keys.sorted(), versioned.keys.sorted(), hint)
        for ((file, content) in generated) {
            assertEquals(content, versioned[file], "$file: $hint")
        }
    }

    @Test
    fun dslContext_readsTheSeededDataThroughTheGeneratedTables() {
        val dsl = ShoppingDSLContext.BEAN.get()
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
        val dsl = ShoppingDSLContext.BEAN.get()
        val tx = ShoppingTransactions.BEAN.get()

        assertFailsWith<IllegalStateException> {
            tx.required {
                dsl.update(EN_USER).set(EN_USER.NAME, "provisório").where(EN_USER.ID.eq(DBReset.ADMIN_ID)).execute()
                assertEquals("provisório", dsl.select(EN_USER.NAME).from(EN_USER).where(EN_USER.ID.eq(DBReset.ADMIN_ID)).fetchOne()!!.value1())
                throw IllegalStateException("desiste")
            }
        }
        assertEquals("João da Silva", dsl.select(EN_USER.NAME).from(EN_USER).where(EN_USER.ID.eq(DBReset.ADMIN_ID)).fetchOne()!!.value1())

        tx.required { dsl.update(EN_USER).set(EN_USER.NAME, "definitivo").where(EN_USER.ID.eq(DBReset.ADMIN_ID)).execute() }
        assertEquals("definitivo", dsl.select(EN_USER.NAME).from(EN_USER).where(EN_USER.ID.eq(DBReset.ADMIN_ID)).fetchOne()!!.value1())
    }
}
