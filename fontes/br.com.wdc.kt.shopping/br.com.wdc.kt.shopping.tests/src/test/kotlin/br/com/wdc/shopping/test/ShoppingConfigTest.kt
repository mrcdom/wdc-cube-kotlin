package br.com.wdc.shopping.test

import br.com.wdc.shopping.domain.ShoppingConfig
import br.com.wdc.shopping.domain.config.AppConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Todas as pastas de execução saem do mesmo diretório-base. */
class ShoppingConfigTest {

    /** Roda o bloco e devolve a configuração global ao que era: outros testes dependem dela. */
    private fun <T> preservingTheConfiguration(block: () -> T): T {
        val saved = listOf(ShoppingConfig.baseDir, ShoppingConfig.configDir, ShoppingConfig.dataDir, ShoppingConfig.logDir, ShoppingConfig.tempDir, ShoppingConfig.deployDir)
        val jwtSecret = ShoppingConfig.jwtSecret
        val ttl = ShoppingConfig.refreshTokenTtlDays
        try {
            return block()
        } finally {
            val setters: List<(Path) -> Unit> = listOf(
                ShoppingConfig.Internals::setBaseDir, ShoppingConfig.Internals::setConfigDir, ShoppingConfig.Internals::setDataDir,
                ShoppingConfig.Internals::setLogDir, ShoppingConfig.Internals::setTempDir, ShoppingConfig.Internals::setDeployDir,
            )
            saved.zip(setters).forEach { (path, set) -> path?.let(set) }
            ShoppingConfig.Internals.setJwtSecret(jwtSecret)
            ShoppingConfig.Internals.setRefreshTokenTtlDays(ttl)
        }
    }

    private fun emptyConfig(): AppConfig {
        System.setProperty("shopping.config.file", Files.createTempFile("application", ".toml").toString())
        try {
            return AppConfig.load()
        } finally {
            System.clearProperty("shopping.config.file")
        }
    }

    @Test
    fun everyRuntimeDirectory_isUnderTheConfiguredBaseDirectory() = preservingTheConfiguration {
        val base = Files.createTempDirectory("shopping-base").toRealPath()

        ShoppingConfig.Internals.configure(emptyConfig().withOverride("app.basedir", base.toString()))

        assertEquals(base, ShoppingConfig.baseDir!!.toRealPath())
        val directories = mapOf(
            "config" to ShoppingConfig.configDir, "data" to ShoppingConfig.dataDir, "log" to ShoppingConfig.logDir,
            "temp" to ShoppingConfig.tempDir, "deploy" to ShoppingConfig.deployDir,
        )
        for ((name, directory) in directories) {
            assertEquals(base.resolve(name), directory!!.toRealPath(), name)
            assertTrue(Files.isDirectory(directory), "$name não foi criado")
        }
    }

    @Test
    fun withoutBaseDirectory_itIsWorkUnderTheWorkingDirectory() = preservingTheConfiguration {
        val work = Path.of("work").toAbsolutePath().normalize()
        val existed = Files.exists(work)
        try {
            ShoppingConfig.Internals.configure(emptyConfig())

            assertEquals(work, ShoppingConfig.baseDir)
            assertEquals(work.resolve("deploy"), ShoppingConfig.deployDir)
            assertEquals(work.resolve("data"), ShoppingConfig.dataDir)
        } finally {
            // configurar cria as pastas: o teste não deixa um work/ para trás no módulo
            if (!existed) work.toFile().deleteRecursively()
        }
    }
}
