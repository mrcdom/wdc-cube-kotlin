package br.com.wdc.shopping.test

import br.com.wdc.shopping.domain.ShoppingConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O diretório de trabalho é dado de fora, e todas as pastas de execução saem dele. */
class ShoppingConfigTest {

    /** Roda o bloco e devolve a configuração global ao que era: outros testes dependem dela. */
    private fun <T> preservingTheConfiguration(block: () -> T): T {
        val saved = listOf(
            ShoppingConfig.workDir, ShoppingConfig.configDir, ShoppingConfig.dataDir,
            ShoppingConfig.logDir, ShoppingConfig.tmpDir, ShoppingConfig.deploymentDir,
        )
        val jwtSecret = ShoppingConfig.jwtSecret
        val ttl = ShoppingConfig.refreshTokenTtlDays
        try {
            return block()
        } finally {
            val setters: List<(Path) -> Unit> = listOf(
                ShoppingConfig.Internals::setWorkDir, ShoppingConfig.Internals::setConfigDir, ShoppingConfig.Internals::setDataDir,
                ShoppingConfig.Internals::setLogDir, ShoppingConfig.Internals::setTmpDir, ShoppingConfig.Internals::setDeploymentDir,
            )
            saved.zip(setters).forEach { (path, set) -> path?.let(set) }
            ShoppingConfig.Internals.setJwtSecret(jwtSecret)
            ShoppingConfig.Internals.setRefreshTokenTtlDays(ttl)
        }
    }

    @Test
    fun everyRuntimeDirectory_comesFromTheWorkDirectory_andIsCreated() = preservingTheConfiguration {
        val work = Files.createTempDirectory("shopping-work").toRealPath()

        ShoppingConfig.Internals.useWorkDir(work)

        assertEquals(work, ShoppingConfig.workDir!!.toRealPath())
        val directories = mapOf(
            "config" to ShoppingConfig.configDir, "data" to ShoppingConfig.dataDir, "log" to ShoppingConfig.logDir,
            "tmp" to ShoppingConfig.tmpDir, "deployment" to ShoppingConfig.deploymentDir,
        )
        for ((name, directory) in directories) {
            assertEquals(work.resolve(name), directory!!.toRealPath(), name)
            assertTrue(Files.isDirectory(directory), "$name não foi criado")
        }
    }

    @Test
    fun aWorkDirectoryThatDoesNotExist_isRefused_andNotCreated() = preservingTheConfiguration {
        val missing = Files.createTempDirectory("shopping-work").resolve("nao-existe")

        val e = assertFailsWith<IllegalArgumentException> { ShoppingConfig.Internals.useWorkDir(missing) }

        assertTrue("não existe" in e.message!!, e.message)
        assertTrue(Files.notExists(missing))
    }

    @Test
    fun configuration_isReadFromConfigInTheWorkDirectory() = preservingTheConfiguration {
        val work = Files.createTempDirectory("shopping-work")
        ShoppingConfig.Internals.useWorkDir(work)

        // sem o arquivo, vale o padrão
        assertNull(ShoppingConfig.Internals.loadConfig().get("server.port"))
        assertNull(ShoppingConfig.jwtSecret)

        Files.writeString(work.resolve("config/application.toml"), "[server]\nport = 9123\n\n[security]\njwt.secret = \"abc\"\n")
        val config = ShoppingConfig.Internals.loadConfig()
        assertEquals(9123, config.getInt("server.port", 8080))
        assertEquals("abc", ShoppingConfig.jwtSecret)
    }
}
