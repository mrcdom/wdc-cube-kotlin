package br.com.wdc.shopping.domain

import br.com.wdc.shopping.domain.config.AppConfig
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The service's work directory and the general settings from the configuration.
 *
 * **The work directory is given by whoever starts the service** — there is no default, nor is it inferred from
 * where the process runs. All runtime folders come from it:
 *
 * | Folder | Purpose |
 * |---|---|
 * | `config/` | the configuration (`application.toml`) and its supporting files, including the log's |
 * | `data/` | local data the application reuses across restarts (the default H2 database) |
 * | `log/` | the log file and its rotations, as the log configuration says |
 * | `tmp/` | temporary files created while running |
 * | `deployment/` | each subfolder is a static resource context the server publishes |
 */
object ShoppingConfig {

    const val CONFIG_FILE_NAME = "application.toml"

    var workDir: Path? = null
        private set

    var configDir: Path? = null
        private set

    var dataDir: Path? = null
        private set

    var logDir: Path? = null
        private set

    var tmpDir: Path? = null
        private set

    var deploymentDir: Path? = null
        private set

    var jwtSecret: String? = null
        private set

    var refreshTokenTtlDays: Int = 7
        private set

    object Internals {

        fun setWorkDir(path: Path) { ShoppingConfig.workDir = path }
        fun setConfigDir(path: Path) { ShoppingConfig.configDir = path }
        fun setDataDir(path: Path) { ShoppingConfig.dataDir = path }
        fun setLogDir(path: Path) { ShoppingConfig.logDir = path }
        fun setTmpDir(path: Path) { ShoppingConfig.tmpDir = path }
        fun setDeploymentDir(path: Path) { ShoppingConfig.deploymentDir = path }
        fun setJwtSecret(secret: String?) { ShoppingConfig.jwtSecret = secret }
        fun setRefreshTokenTtlDays(days: Int) { ShoppingConfig.refreshTokenTtlDays = days }

        /**
         * Sets the runtime folders from the work directory, creating the missing ones.
         *
         * @param workDir work directory; **must exist** — a wrong path does not silently become a new, empty
         *                environment
         */
        fun useWorkDir(workDir: Path) {
            val work = workDir.toAbsolutePath().normalize()
            require(Files.isDirectory(work)) { "The work directory does not exist: $work" }
            try {
                setWorkDir(work)
                setConfigDir(createDirectory(work.resolve("config")))
                setDataDir(createDirectory(work.resolve("data")))
                setLogDir(createDirectory(work.resolve("log")))
                setTmpDir(createDirectory(work.resolve("tmp")))
                setDeploymentDir(createDirectory(work.resolve("deployment")))
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }

        /** Loads the configuration from `config/application.toml` in the work directory already set. */
        fun loadConfig(): AppConfig {
            val configDir = ShoppingConfig.configDir ?: throw IllegalStateException("Work directory not given")
            val config = AppConfig.load(configDir.resolve(CONFIG_FILE_NAME))
            setJwtSecret(config.get("security.jwt.secret"))
            setRefreshTokenTtlDays(config.getInt("security.refresh.token.ttl.days", 7))
            return config
        }

        private fun createDirectory(dir: Path): Path {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir)
            }
            return dir
        }
    }
}
