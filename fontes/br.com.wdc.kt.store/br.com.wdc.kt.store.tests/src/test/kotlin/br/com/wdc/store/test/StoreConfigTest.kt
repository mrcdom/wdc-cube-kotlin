package br.com.wdc.store.test

import br.com.wdc.store.domain.StoreConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The work directory is given from outside, and all the runtime folders come from it. */
class StoreConfigTest {

    /** Runs the block and restores the global configuration to what it was: other tests depend on it. */
    private fun <T> preservingTheConfiguration(block: () -> T): T {
        val saved = listOf(
            StoreConfig.workDir, StoreConfig.configDir, StoreConfig.dataDir,
            StoreConfig.logDir, StoreConfig.tmpDir, StoreConfig.deploymentDir,
        )
        val jwtSecret = StoreConfig.jwtSecret
        val ttl = StoreConfig.refreshTokenTtlDays
        try {
            return block()
        } finally {
            val setters: List<(Path) -> Unit> = listOf(
                StoreConfig.Internals::setWorkDir, StoreConfig.Internals::setConfigDir, StoreConfig.Internals::setDataDir,
                StoreConfig.Internals::setLogDir, StoreConfig.Internals::setTmpDir, StoreConfig.Internals::setDeploymentDir,
            )
            saved.zip(setters).forEach { (path, set) -> path?.let(set) }
            StoreConfig.Internals.setJwtSecret(jwtSecret)
            StoreConfig.Internals.setRefreshTokenTtlDays(ttl)
        }
    }

    @Test
    fun everyRuntimeDirectory_comesFromTheWorkDirectory_andIsCreated() = preservingTheConfiguration {
        val work = Files.createTempDirectory("store-work").toRealPath()

        StoreConfig.Internals.useWorkDir(work)

        assertEquals(work, StoreConfig.workDir!!.toRealPath())
        val directories = mapOf(
            "config" to StoreConfig.configDir, "data" to StoreConfig.dataDir, "log" to StoreConfig.logDir,
            "tmp" to StoreConfig.tmpDir, "deployment" to StoreConfig.deploymentDir,
        )
        for ((name, directory) in directories) {
            assertEquals(work.resolve(name), directory!!.toRealPath(), name)
            assertTrue(Files.isDirectory(directory), "$name was not created")
        }
    }

    @Test
    fun aWorkDirectoryThatDoesNotExist_isRefused_andNotCreated() = preservingTheConfiguration {
        val missing = Files.createTempDirectory("store-work").resolve("missing-dir")

        val e = assertFailsWith<IllegalArgumentException> { StoreConfig.Internals.useWorkDir(missing) }

        assertTrue("does not exist" in e.message!!, e.message)
        assertTrue(Files.notExists(missing))
    }

    @Test
    fun configuration_isReadFromConfigInTheWorkDirectory() = preservingTheConfiguration {
        val work = Files.createTempDirectory("store-work")
        StoreConfig.Internals.useWorkDir(work)

        // without the file, the default applies
        assertNull(StoreConfig.Internals.loadConfig().get("server.port"))
        assertNull(StoreConfig.jwtSecret)

        Files.writeString(work.resolve("config/application.toml"), "[server]\nport = 9123\n\n[security]\njwt.secret = \"abc\"\n")
        val config = StoreConfig.Internals.loadConfig()
        assertEquals(9123, config.getInt("server.port", 8080))
        assertEquals("abc", StoreConfig.jwtSecret)
    }
}
